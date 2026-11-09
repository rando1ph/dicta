package dev.dicta.prototype

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.speech.SpeechController
import dev.dicta.prototype.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class M4SpeechUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun leavingPageAndPausingStopSpeechAndDisposeShutsEngineDown() {
        val owner=object:LifecycleOwner {
            val registry=LifecycleRegistry(this)
            override val lifecycle:Lifecycle get()=registry
        }
        val visible=mutableStateOf(true)
        lateinit var speech:SpeechController
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("speech",android.content.Context.MODE_PRIVATE)
        val original=prefs.getString("country",null)
        try {
            compose.runOnIdle {owner.registry.currentState=Lifecycle.State.RESUMED}
            compose.setContent {CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                DictaTheme {if(visible.value)WithSpeech {speech=requireNotNull(LocalSpeech.current);SpeechControls("A little discovery. ".repeat(20))}}
            }}
            compose.waitUntil(15000) {runCatching {speech.state.value.initialized}.getOrDefault(false)}
            compose.onNodeWithTag("speakUK").assertIsEnabled().performClick()
            compose.waitUntil(5000) {speech.state.value.activeId!=null}
            compose.runOnIdle {owner.registry.currentState=Lifecycle.State.CREATED}
            compose.runOnIdle {assertNull(speech.state.value.activeId);owner.registry.currentState=Lifecycle.State.RESUMED}
            compose.onNodeWithTag("speakUS").performClick()
            compose.waitUntil(5000) {speech.state.value.activeId!=null}
            compose.runOnIdle {visible.value=false}
            compose.waitForIdle()
            compose.runOnIdle {assertNull(speech.state.value.activeId);assertNull(speech.speak("Closed"))}
        } finally {prefs.edit().apply {if(original==null)remove("country") else putString("country",original)}.commit()}
    }
    @Test fun pronunciationUsesProfileSpeechBindingInsteadOfDisplayTitle() {
        lateinit var speech:SpeechController
        val rendered=dev.dicta.prototype.profile.RenderedEntry(mapOf("title" to "Display label"),emptyList(),"Configured pronunciation.")
        val entry=dev.dicta.prototype.data.EntrySnapshot("test","test",0,rendered,"{}","{}","""{"actions":{"home":["speak"]}}""")
        val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("speech",android.content.Context.MODE_PRIVATE)
        val original=prefs.getString("country",null)
        try {
        compose.setContent {DictaTheme {WithSpeech {speech=requireNotNull(LocalSpeech.current);dev.dicta.prototype.render.EntryRenderer(rendered) {Pronunciation(entry,"home")}}}}
        compose.waitUntil(15000) {runCatching {speech.state.value.initialized}.getOrDefault(false)}
        compose.onNodeWithTag("speakUK").performClick()
        compose.runOnIdle {assertEquals("Configured pronunciation.",speech.state.value.text)}
        } finally {prefs.edit().apply {if(original==null)remove("country") else putString("country",original)}.commit()}

    }

    @Test fun longWordAndLongDefinitionWrapAtTwoHundredPercentFont() {
        val word="pneumonoultramicroscopicsilicovolcanoconiosis"
        val definition="一段较长的中英释义。 A long definition remains readable. ".repeat(12)
        val entry=dev.dicta.prototype.profile.RenderedEntry(mapOf("title" to word),listOf(
            dev.dicta.prototype.profile.RenderNode("text",style="headline",value=word),
            dev.dicta.prototype.profile.RenderNode("text",style="definition",value=definition)),word)
        compose.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f)) {
                DictaTheme {androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier
                    .fillMaxSize().verticalScroll(rememberScrollState())) {
                    EntryCard {dev.dicta.prototype.render.EntryRenderer(entry)}
                }}
            }
        }
        for(text in listOf(word,definition)) {
            val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
            assertTrue(layouts.isNotEmpty());assertFalse(layouts.first().hasVisualOverflow);assertTrue(layouts.first().lineCount>1)
        }
    }

}
