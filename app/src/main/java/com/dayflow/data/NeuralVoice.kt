package com.dayflow.data

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** A downloadable neural voice. Piper is small; Kokoro is larger and the most natural. */
data class NeuralVoiceModel(
    val id: String,
    val label: String,
    val detail: String,
    val url: String,
    val archiveMegabytes: Int,
    val speakerId: Int = 0,
    val kokoro: Boolean = false,
    /** Which voices this model can present as. Kokoro carries both; a Piper model carries one. */
    val genders: List<AgentGender> = listOf(AgentGender.FEMININE)
)

/**
 * Natural speech generated on the phone itself with a neural voice model (sherpa-onnx).
 *
 * Why this exists: Android's built-in TextToSpeech is a formant synthesiser from a different era, and
 * the user's gateway may have no speech provider at all. A neural model runs offline, needs no account,
 * no key and no server, and sounds like a person. The model is fetched once on request and then lives
 * in the app's private storage.
 */
object NeuralVoice {

    /** Voices offered in Settings. Sizes are the download size the user sees before agreeing to it. */
    val MODELS = listOf(
        NeuralVoiceModel(
            id = "piper-amy",
            label = "Amy · warm, natural",
            detail = "Feminine voice, about 63 MB download, speaks offline.",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium.tar.bz2",
            archiveMegabytes = 63,
            genders = listOf(AgentGender.FEMININE)
        ),
        NeuralVoiceModel(
            id = "piper-lessac",
            label = "Lessac · clear, natural",
            detail = "Masculine voice, about 63 MB download, speaks offline.",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2",
            archiveMegabytes = 63,
            genders = listOf(AgentGender.MASCULINE)
        ),
        NeuralVoiceModel(
            id = "kokoro",
            label = "Kokoro · most human",
            detail = "Speaks as either voice, about 320 MB download. Best sounding, slower to fetch.",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2",
            archiveMegabytes = 320,
            kokoro = true,
            genders = listOf(AgentGender.FEMININE, AgentGender.MASCULINE)
        )
    )

    /**
     * Kokoro ships eleven speakers behind one model, so the gender has to select one:
     * 2 is af_nicole and 6 is am_michael in the model's documented speaker list.
     */
    fun speakerId(id: String, gender: AgentGender): Int = when {
        id == "kokoro" -> if (gender == AgentGender.MASCULINE) KOKORO_MASCULINE_SPEAKER else KOKORO_FEMININE_SPEAKER
        else -> model(id)?.speakerId ?: 0
    }

    private const val KOKORO_FEMININE_SPEAKER = 2
    private const val KOKORO_MASCULINE_SPEAKER = 6

    /**
     * The voice to suggest for a gender: an installed voice that already speaks this way, otherwise the
     * smallest matching download. This is why changing gender does not silently leave a mismatched voice.
     */
    fun recommendedModel(gender: AgentGender, installed: (String) -> Boolean): String {
        val preferred = when (gender) {
            AgentGender.FEMININE -> "piper-amy"
            AgentGender.MASCULINE -> "piper-lessac"
        }
        if (installed(preferred)) return preferred
        if (installed("kokoro")) return "kokoro"
        return preferred
    }

    /** Voices that can present as this gender, for the pickers. */
    fun modelsFor(gender: AgentGender): List<NeuralVoiceModel> = MODELS.filter { gender in it.genders }

    fun model(id: String): NeuralVoiceModel? = MODELS.firstOrNull { it.id == id }

    /** Default voice offered first when the user has never chosen one. */
    val DEFAULT_MODEL_ID = "piper-amy"

    fun voiceDir(context: Context, id: String): File = File(File(context.filesDir, "voices"), id)

    /**
     * Resolves the directory holding model files, auto-flattening archives that were
     * unpacked inside a nested subdirectory.
     */
    fun modelDir(context: Context, id: String): File {
        val directory = voiceDir(context, id)
        if (!File(directory, "tokens.txt").isFile) {
            flattenNestedDirectory(directory)
        }
        return directory
    }

    internal fun flattenNestedDirectory(target: File) {
        if (!target.isDirectory) return
        if (File(target, "tokens.txt").isFile) return
        val nested = target.listFiles()?.firstOrNull { it.isDirectory && File(it, "tokens.txt").isFile } ?: return
        nested.listFiles()?.forEach { file ->
            val dest = File(target, file.name)
            if (dest.exists()) dest.deleteRecursively()
            if (!file.renameTo(dest)) {
                file.copyRecursively(dest, overwrite = true)
                file.deleteRecursively()
            }
        }
        nested.deleteRecursively()
    }

    /** True once the model files are on disk, so speech can start without any waiting or network. */
    fun isInstalled(context: Context, id: String): Boolean {
        val directory = modelDir(context, id)
        if (!directory.isDirectory) return false
        val hasEspeakData = File(directory, "espeak-ng-data").isDirectory
        val hasTokens = File(directory, "tokens.txt").isFile
        val hasModel = directory.listFiles()?.any { it.isFile && it.name.endsWith(".onnx") && it.length() > 0 } == true
        val hasVoices = if (model(id)?.kokoro == true) File(directory, "voices.bin").isFile else true
        return hasModel && hasTokens && hasVoices && (hasEspeakData || model(id)?.kokoro == true)
    }

    fun anyInstalled(context: Context): Boolean = MODELS.any { isInstalled(context, it.id) }

    /**
     * Downloads and unpacks a voice. Progress is reported so the Settings screen can show movement
     * during a long download instead of a frozen button.
     */
    fun install(context: Context, model: NeuralVoiceModel, onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit): File {
        // A reinstall replaces the files under a live engine, so drop it first.
        releaseEngine()
        val target = voiceDir(context, model.id)
        val archive = File(context.cacheDir, "${model.id}.tar.bz2")
        if (archive.exists()) archive.delete()
        download(model.url, archive, onProgress)
        try {
            target.deleteRecursively()
            target.mkdirs()
            extract(archive, target)
            flattenNestedDirectory(target)
        } finally {
            archive.delete()
        }
        check(isInstalled(context, model.id)) { "The voice files did not unpack correctly. Try downloading again." }
        return target
    }

    private fun download(url: String, destination: File, onProgress: (Long, Long) -> Unit) {
        var currentUrl = url
        var redirects = 0
        var connection: HttpURLConnection? = null
        while (redirects++ < 8) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "Dayflow/1.0")
            }
            val code = conn.responseCode
            if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (!location.isNullOrBlank()) {
                    currentUrl = URL(URL(currentUrl), location).toExternalForm()
                    continue
                }
            }
            connection = conn
            break
        }
        val conn = connection ?: throw IllegalStateException("Too many redirects downloading voice model.")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("The voice download failed with HTTP $code.")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            conn.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var copied = 0L
                    var lastReported = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        // Report at most every 512 KB; a per-byte callback would flood the UI thread.
                        if (copied - lastReported >= 524_288L) {
                            lastReported = copied
                            onProgress(copied, total)
                        }
                    }
                    output.flush()
                    onProgress(copied, total)
                }
            }
        } finally {
            conn.disconnect()
        }
    }    /** Unpacks a .tar.bz2 voice archive, refusing entries that try to escape the target directory. */
    internal fun extract(archive: File, target: File) {
        val root = target.canonicalFile
        BZip2CompressorInputStream(BufferedInputStream(archive.inputStream(), 1 shl 16), true).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val entry: TarArchiveEntry = tar.nextEntry ?: break
                    val name = entry.name.trimStart('/', '.')
                    if (name.isEmpty() || entry.isSymbolicLink || entry.isLink) continue
                    val output = File(root, name)
                    if (!output.canonicalPath.startsWith(root.canonicalPath + File.separator)) continue
                    if (entry.isDirectory) {
                        output.mkdirs()
                        continue
                    }
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { stream ->
                        while (true) {
                            val read = tar.read(buffer)
                            if (read <= 0) break
                            stream.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    }


    /**
     * Renders text to a 16-bit PCM WAV file with the installed model. Returns null when the voice is
     * not installed or the native engine refuses to load, which lets the caller fall back honestly.
     */
    @Volatile private var cachedEngine: OfflineTts? = null
    @Volatile private var cachedModelId: String? = null

    /**
     * Loading a voice model costs seconds, so the engine is built once and reused. Rebuilding it for
     * every sentence would make Lumi sound natural but answer slowly.
     */
    @Synchronized private fun engine(context: Context, model: NeuralVoiceModel): OfflineTts? {
        cachedEngine?.let { if (cachedModelId == model.id) return it }
        releaseEngine()
        val directory = modelDir(context, model.id)
        val onnx = File(directory, onnxFileName(directory)).absolutePath
        val tokens = File(directory, "tokens.txt").absolutePath
        val espeakDir = File(directory, "espeak-ng-data")
        val dataDir = if (espeakDir.isDirectory) espeakDir.absolutePath else ""
        val engineConfig = OfflineTtsModelConfig(
            vits = if (model.kokoro) OfflineTtsVitsModelConfig() else OfflineTtsVitsModelConfig(
                model = onnx, tokens = tokens, dataDir = dataDir
            ),
            kokoro = if (model.kokoro) OfflineTtsKokoroModelConfig(
                model = onnx, voices = File(directory, "voices.bin").absolutePath, tokens = tokens, dataDir = dataDir
            ) else OfflineTtsKokoroModelConfig(),
            numThreads = 2,
            debug = false,
            provider = "cpu"
        )
        val config = OfflineTtsConfig(
            model = engineConfig,
            maxNumSentences = 1,
            // A short breath between sentences is what stops concatenated clips sounding mechanical.
            silenceScale = 0.2f
        )
        val created = runCatching { OfflineTts(config = config) }.getOrNull() ?: return null
        cachedEngine = created
        cachedModelId = model.id
        return created
    }

    /** Frees the loaded model. Called when the voice changes or the files are replaced. */
    @Synchronized fun releaseEngine() {
        runCatching { cachedEngine?.release() }
        cachedEngine = null
        cachedModelId = null
    }

    /** Loads the voice ahead of time, so the first spoken reply is not the slow one. */
    fun warmUp(context: Context, id: String): Boolean {
        val model = model(id) ?: return false
        if (!isInstalled(context, id)) return false
        return engine(context, model) != null
    }

    @Synchronized fun synthesize(
        context: Context,
        id: String,
        text: String,
        speed: Float = 1.0f,
        gender: AgentGender = AgentGender.FEMININE
    ): File? {
        val model = model(id) ?: return null
        if (!isInstalled(context, id)) return null
        val spoken = text.trim()
        if (spoken.isEmpty()) return null
        val engine = engine(context, model) ?: return null
        return try {
            val numSpeakers = engine.numSpeakers()
            val sid = if (numSpeakers > 0) speakerId(id, gender).coerceIn(0, numSpeakers - 1) else 0
            val audio = engine.generate(spoken.take(1200), sid, speed)
            val samples = audio.samples
            if (samples.isEmpty()) return null
            val file = File(context.cacheDir, "lumi_neural_${System.currentTimeMillis()}.wav")
            writeWav(samples, audio.sampleRate, file)
            file.takeIf { it.length() > 44 }
        } catch (failure: Throwable) {
            // A failed generation can leave the native engine unusable, so drop it and let the next
            // attempt rebuild from the model files rather than staying silent forever.
            releaseEngine()
            null
        }
    }

    private fun onnxFileName(directory: File): String =
        directory.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".onnx") }?.name ?: "model.onnx"

    /** Mono 16-bit PCM WAV, the format MediaPlayer reads without transcoding. */
    internal fun writeWav(samples: FloatArray, sampleRate: Int, file: File) {
        val dataBytes = samples.size * 2
        java.io.DataOutputStream(java.io.BufferedOutputStream(FileOutputStream(file), 1 shl 16)).use { out ->
            out.writeBytes("RIFF"); out.writeIntLE(36 + dataBytes); out.writeBytes("WAVE")
            out.writeBytes("fmt "); out.writeIntLE(16); out.writeShortLE(1); out.writeShortLE(1)
            out.writeIntLE(sampleRate); out.writeIntLE(sampleRate * 2); out.writeShortLE(2); out.writeShortLE(16)
            out.writeBytes("data"); out.writeIntLE(dataBytes)
            for (sample in samples) {
                val clamped = sample.coerceIn(-1f, 1f)
                out.writeShortLE((clamped * 32767f).toInt())
            }
        }
    }

    private fun java.io.DataOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF); write((value shr 8) and 0xFF); write((value shr 16) and 0xFF); write((value shr 24) and 0xFF)
    }

    private fun java.io.DataOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF); write((value shr 8) and 0xFF)
    }

    /** True when the sherpa-onnx native library is present and loadable on this device. */
    fun nativeEngineLoads(): Boolean = runCatching {
        System.loadLibrary("sherpa-onnx-jni")
        true
    }.getOrDefault(false)
}
