package dev.dicta.prototype

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.platform.MainActivity
import dev.dicta.prototype.security.CredentialStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun editSaveReplaceClearAndTestSavedConnectionInSecureSettings() {
        val store=CredentialStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val original=store.load()
        try {
            compose.onNodeWithTag("navSettings").performClick()
            compose.onNodeWithTag("endpointInput").performTextReplacement("https://api.deepseek.com/v1")
            compose.onNodeWithTag("modelInput").performTextReplacement("deepseek-flash")
            compose.onNodeWithText("保存",useUnmergedTree=true).performScrollTo().performClick()
            compose.waitUntil(5000) { compose.activity.model.configMessage.value == "配置已安全保存。" }
            assertEquals("https://api.deepseek.com/v1",store.load().endpoint)
            assertTrue(store.load().apiKey == original.apiKey)
            compose.runOnUiThread { assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
            compose.onNodeWithText("测试连接",useUnmergedTree=true).performScrollTo().performClick()
            compose.waitUntil(65000) { !compose.activity.model.configBusy.value }
            assertTrue(compose.activity.model.configMessage.value.contains("连接成功"))
            compose.onNodeWithTag("keyInput").performScrollTo().performTextReplacement("test-credential-replacement")
            compose.onNodeWithText("保存",useUnmergedTree=true).performScrollTo().performClick()
            compose.waitUntil(5000) { !compose.activity.model.configBusy.value }
            assertTrue(store.load().apiKey == "test-credential-replacement")
            compose.onNodeWithText("清除配置",useUnmergedTree=true).performScrollTo().performClick()
            compose.waitUntil(5000) { !compose.activity.model.configBusy.value }
            assertTrue(store.load().apiKey.isBlank())
            compose.onNodeWithTag("navSearch").performClick()
            compose.waitUntil(5000) { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0 }
        } finally { store.save(original) }
    }
}
