package dev.dicta.prototype.provider

import org.junit.Assert.*
import org.junit.Test

class CredentialBoundaryTest {
    private val old=ProviderConfig(apiKey="old-test-only-key")
    @Test fun presetSwitchAndHostOrPortChangeRequireNewKey() {
        for(endpoint in listOf("https://api.openai.com/v1","https://api.deepseek.com.example.org","https://api.deepseek.com:8443")) {
            try {ProviderConfig(endpoint,"model","").retainingKeyFrom(old);fail("Must require a new key")}
            catch(e:ProviderFailure) {assertEquals(FailureKind.CONFIGURATION,e.kind)}
        }
    }
    @Test fun sameServiceModelProtocolOutputAndResourceEditsRetainKey() {
        val next=ProviderConfig(" https://API.DEEPSEEK.COM:443/v1/chat/completions/ ","different"," ",ProviderProtocol.COMPATIBLE,JsonOutput.PROMPT_ONLY).retainingKeyFrom(old)
        assertEquals(old.apiKey,next.apiKey)
        assertEquals("different",next.model)
        assertEquals(JsonOutput.PROMPT_ONLY,next.output)
    }
    @Test fun explicitReplacementUsesOnlyNewKeyOnNewService() {
        val next=ProviderConfig("https://api.openai.com/v1","","new-test-only-key",ProviderProtocol.OPENAI).retainingKeyFrom(old)
        assertEquals("new-test-only-key",next.apiKey)
        assertEquals("",next.model)
    }
    @Test fun invalidEndpointsNeverClaimToShareCredentials() {
        for(endpoint in listOf("broken","http://api.deepseek.com","https://user@api.deepseek.com","https://api.deepseek.com?key=1")) {
            assertFalse(sameCredentialService(endpoint,endpoint))
            assertFalse(sameCredentialService(endpoint,old.endpoint))
        }
    }
}
