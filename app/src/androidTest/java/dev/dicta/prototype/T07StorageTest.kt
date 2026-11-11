package dev.dicta.prototype

import dev.dicta.prototype.provider.*
import dev.dicta.prototype.security.CredentialStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey

class T07StorageTest:T07MockFixture() {
    @Test fun v100EncryptedConfigurationLoadsWithSafeDefaultsWithoutRewritingCiphertext() {
        val key=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}.getKey("dicta.byok.v1",null) as SecretKey
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key)}
        val legacy="""{"endpoint":"https://api.deepseek.com/v1","model":"deepseek-flash","key":"legacy-test-only"}"""
        val bytes=cipher.iv+cipher.doFinal(legacy.toByteArray())
        val file=File(context.noBackupFilesDir,"provider.enc");file.writeBytes(bytes)
        val restored=CredentialStore(context).load()
        assertEquals("legacy-test-only",restored.apiKey)
        assertEquals("https://api.deepseek.com/v1",restored.endpoint)
        assertEquals(ProviderProtocol.AUTO,restored.protocol)
        assertEquals(JsonOutput.JSON_OBJECT,restored.output)
        assertArrayEquals(bytes,file.readBytes())
    }
    @Test fun protocolOutputAndBlankModelCanBeSavedBeforeDiscoveryAndRemainEncrypted() {
        val store=CredentialStore(context)
        store.save(ProviderConfig("https://example.org/v1","","placeholder-only",ProviderProtocol.OPENAI,JsonOutput.PROMPT_ONLY))
        val restored=store.load()
        assertEquals("",restored.model);assertEquals(ProviderProtocol.OPENAI,restored.protocol);assertEquals(JsonOutput.PROMPT_ONLY,restored.output)
        assertFalse(File(context.noBackupFilesDir,"provider.enc").readBytes().toString(Charsets.UTF_8).contains("placeholder-only"))
    }
}
