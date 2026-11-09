package dev.dicta.prototype

import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.provider.ProviderConfig
import dev.dicta.prototype.security.CredentialStore
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import java.security.spec.MGF1ParameterSpec

/** Test APK only. A fresh RSA public key receives ciphertext; no plaintext ADB or disk input. */
class SecureProvisionTest {
    @Test fun provisionThroughEphemeralPublicKey() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("dictaProvision") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val publicFile = File(context.filesDir,"m2-provision.pub")
        val encryptedFile = File(context.filesDir,"m2-provision.enc")
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        try {
            encryptedFile.delete()
            publicFile.writeBytes(pair.public.encoded)
            val deadline = System.currentTimeMillis()+60000
            while (!encryptedFile.exists() && System.currentTimeMillis()<deadline) Thread.sleep(100)
            assertTrue("Encrypted provisioning input missing",encryptedFile.exists())
            // Wait for the single 2048-bit ciphertext write to finish.
            while (encryptedFile.length()!=256L && System.currentTimeMillis()<deadline) Thread.sleep(100)
            val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
            cipher.init(Cipher.DECRYPT_MODE,pair.private,OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT))
            val clear = cipher.doFinal(encryptedFile.readBytes())
            try { CredentialStore(context).save(ProviderConfig(apiKey=clear.toString(Charsets.UTF_8))) }
            finally { clear.fill(0) }
            assertTrue(CredentialStore(context).load().apiKey.isNotBlank())
        } finally { publicFile.delete(); encryptedFile.delete() }
    }
}
