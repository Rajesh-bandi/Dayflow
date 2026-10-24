package com.dayflow

import com.dayflow.data.NeuralVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The voice download and unpacking path is the riskiest part of on-device speech: it runs on a phone
 * where a wrong byte order or a bad archive entry is silent until playback. These cover both.
 */
class NeuralVoiceTest {

    @Test
    fun writesAPlayable16BitMonoWav() {
        val file = File.createTempFile("lumi", ".wav")
        try {
            NeuralVoice.writeWav(floatArrayOf(0f, 1f, -1f, 0.5f), 22050, file)
            val bytes = file.readBytes()
            assertEquals("header plus four samples", 44 + 8, bytes.size)
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
            assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))

            // Absolute offsets on purpose: ByteBuffer.wrap(array, offset, length) sets the position, not the base index.
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("mono", 1, header.getShort(22).toInt())
            assertEquals("sample rate", 22050, header.getInt(24))
            assertEquals("byte rate", 22050 * 2, header.getInt(28))
            assertEquals("block align", 2, header.getShort(32).toInt())
            assertEquals("16 bit", 16, header.getShort(34).toInt())
            assertEquals("payload size", 8, header.getInt(40))

            val samples = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0, samples.getShort(44).toInt())
            assertEquals(32767, samples.getShort(46).toInt())
            assertEquals(-32767, samples.getShort(48).toInt())
            assertEquals(16383, samples.getShort(50).toInt())
        } finally {
            file.delete()
        }
    }

    @Test
    fun clampsOutOfRangeSamplesInsteadOfWrappingThem() {
        val file = File.createTempFile("lumi", ".wav")
        try {
            NeuralVoice.writeWav(floatArrayOf(4f, -4f), 16000, file)
            val samples = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("a clipped loud sample stays loud", 32767, samples.getShort(44).toInt())
            assertEquals("a clipped quiet sample stays quiet", -32767, samples.getShort(46).toInt())
        } finally {
            file.delete()
        }
    }

    @Test
    fun everyOfferedVoiceIsADownloadableModel() {
        assertTrue("voices are missing", NeuralVoice.MODELS.size >= 2)
        NeuralVoice.MODELS.forEach { model ->
            assertTrue("voice must come over https: ${model.id}", model.url.startsWith("https://"))
            assertTrue("voice must be a real archive: ${model.id}", model.url.endsWith(".tar.bz2"))
            assertTrue("voice needs a size the user can judge: ${model.id}", model.archiveMegabytes > 0)
            assertTrue("voice needs a label: ${model.id}", model.label.isNotBlank())
        }
        assertTrue("the default voice is not offered", NeuralVoice.model(NeuralVoice.DEFAULT_MODEL_ID) != null)
    }

    @Test
    fun unpacksAVoiceArchiveAndRefusesToEscapeTheTargetDirectory() {
        val archive = File(javaClass.classLoader!!.getResource("sample_voice.tar.bz2")!!.toURI())
        val target = File.createTempFile("voices", "").let { it.delete(); it.mkdirs(); it }
        val escaped = File(target.parentFile, "escaped.txt")
        try {
            NeuralVoice.extract(archive, target)
            assertTrue("tokens were not unpacked", File(target, "piper-amy/tokens.txt").isFile)
            assertTrue("the model was not unpacked", File(target, "piper-amy/model.onnx").isFile)
            assertTrue("the phoneme data was not unpacked", File(target, "piper-amy/espeak-ng-data/en_dict").isFile)
            assertEquals("a file was altered in transit", "FAKE-ONNX-BYTES", File(target, "piper-amy/model.onnx").readText())
            assertFalse("an entry escaped the target directory", escaped.exists())
        } finally {
            target.deleteRecursively()
            escaped.delete()
        }
    }

    @Test
    fun flattensNestedDirectorySoTokensAndModelAreAtRoot() {
        val archive = File(javaClass.classLoader!!.getResource("sample_voice.tar.bz2")!!.toURI())
        val target = File.createTempFile("voices", "").let { it.delete(); it.mkdirs(); it }
        try {
            NeuralVoice.extract(archive, target)
            NeuralVoice.flattenNestedDirectory(target)
            assertTrue("tokens should be at target root", File(target, "tokens.txt").isFile)
            assertTrue("model should be at target root", File(target, "model.onnx").isFile)
            assertTrue("espeak-ng-data should be at target root", File(target, "espeak-ng-data/en_dict").isFile)
            assertFalse("nested directory should be removed", File(target, "piper-amy").exists())
        } finally {
            target.deleteRecursively()
        }
    }
}
