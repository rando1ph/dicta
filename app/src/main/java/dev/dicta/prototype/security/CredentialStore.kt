package dev.dicta.prototype.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.dicta.prototype.provider.ProviderConfig
import dev.dicta.prototype.provider.ProviderFailure
import dev.dicta.prototype.provider.FailureKind
import kotlinx.serialization.json.*
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM ciphertext only, in noBackupFilesDir. The wrapping key never leaves Keystore. */
class CredentialStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "provider.enc"))
    private val alias = "dicta.byok.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun load(): ProviderConfig {
        if (!file.baseFile.exists()) return ProviderConfig()
        try {
            val bytes = file.readFully()
            require(bytes.size in 29..8192)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val json = Json.parseToJsonElement(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)).jsonObject
            return ProviderConfig(json.getValue("endpoint").jsonPrimitive.content, json.getValue("model").jsonPrimitive.content, json.getValue("key").jsonPrimitive.content).validated()
        } catch (_: Exception) { throw ProviderFailure("无法读取安全配置，请在设置中重新保存密钥。", FailureKind.CONFIGURATION) }
    }
    @Synchronized fun save(config: ProviderConfig) {
        val active = config.validated()
        try {
            val raw = buildJsonObject { put("endpoint", active.endpoint); put("model", active.model); put("key", active.apiKey) }.toString().toByteArray()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val stream = file.startWrite()
            try { stream.write(cipher.iv + cipher.doFinal(raw)); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
            finally { raw.fill(0) }
        } catch (_: Exception) { throw ProviderFailure("无法安全保存配置，请重试。", FailureKind.CONFIGURATION) }
    }
    @Synchronized fun clear() { file.delete() }
}
