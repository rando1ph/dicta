package dev.dicta.prototype

import android.view.WindowManager
import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Test

class SettingsDeviceTest:T07MockFixture() {
    @Test fun editSaveReplaceClearAndTestSavedConnectionInScreenshotableSettings() {
            compose.onNodeWithTag("navSettings").performClick()
            compose.onNodeWithTag("modelInput").performScrollTo().performTextReplacement("alternate-model")
            compose.onNodeWithTag("saveConfig").performScrollTo().performClick()
            compose.waitUntil(5000) { !compose.activity.model.configBusy.value && compose.activity.model.configuration.value.model=="alternate-model" }
            assertEquals("alternate-model",repo.credentials.load().model)
            assertEquals("test-only-not-a-real-key",repo.credentials.load().apiKey)
            compose.runOnUiThread { assertEquals(0,compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
            assertEquals(0,posts())
            compose.onNodeWithTag("testLookup").performScrollTo().performClick()
            compose.waitUntil(65000) { !compose.activity.model.configBusy.value }
            assertTrue(compose.activity.model.configMessage.value.contains("测试查词成功"))
            assertEquals(1,posts())
            compose.onNodeWithTag("replaceKey").performScrollTo().performClick()
            compose.onNodeWithTag("keyInput").performScrollTo().performTextReplacement("test-credential-replacement")
            compose.onNodeWithTag("saveConfig").performScrollTo().performClick()
            compose.waitUntil(5000) { !compose.activity.model.configBusy.value }
            assertEquals("test-credential-replacement",repo.credentials.load().apiKey)
            compose.onNodeWithTag("providerMore").performScrollTo().performClick()
            compose.onNodeWithTag("clearConfig").performClick()
            compose.onNodeWithTag("confirmClearConfig").performClick()
            compose.waitUntil(5000) { !compose.activity.model.configBusy.value && !compose.activity.model.configuration.value.hasKey }
            assertTrue(repo.credentials.load().apiKey.isBlank())
            compose.onNodeWithTag("navSearch").performClick()
            compose.waitUntil(5000) { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0 }
    }
}
