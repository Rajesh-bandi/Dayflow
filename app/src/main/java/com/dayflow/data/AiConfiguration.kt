package com.dayflow.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val searchApiKey: String = "",
    /** Optional voice model on the same gateway, e.g. a speech model name. Empty uses the on-device voice. */
    val ttsModel: String = "",
    val ttsVoice: String = ""
)

/** Keeps the gateway credential encrypted with an Android Keystore key. */
class AiConfiguration(context: Context) {
    private val prefs = context.getSharedPreferences("lumi_ai", Context.MODE_PRIVATE)
    private val alias = "dayflow_lumi_gateway"
    fun read(): AiConfig = AiConfig(
        prefs.getString("url", "") ?: "",
        decrypt(prefs.getString("key", null)),
        prefs.getString("model", "") ?: "",
        decrypt(prefs.getString("search_key", null)),
        prefs.getString("tts_model", "") ?: "",
        prefs.getString("tts_voice", "") ?: ""
    )
    fun save(config: AiConfig) {
        require(config.baseUrl.startsWith("https://") || config.baseUrl.startsWith("http://10.") || config.baseUrl.startsWith("http://192.168.") || config.baseUrl.startsWith("http://172.") || config.baseUrl.startsWith("http://localhost") || config.baseUrl.startsWith("http://127.0.0.1")) { "Use HTTPS, or a URL on your own local network." }
        prefs.edit().putString("url", config.baseUrl.trim().trimEnd('/')).putString("key", encrypt(config.apiKey))
            .putString("model", config.model.trim()).putString("search_key", encrypt(config.searchApiKey))
            .putString("tts_model", config.ttsModel.trim()).putString("tts_voice", config.ttsVoice.trim()).apply()
    }
    private fun key(create: Boolean): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }
    private fun decrypt(value: String?): String = runCatching {
        if (value.isNullOrEmpty()) return ""
        val bytes = Base64.decode(value, Base64.NO_WRAP); val iv = bytes.copyOfRange(0, 12)
        Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, iv)); doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8) }
    }.getOrDefault("")
}
