package dev.dicta.prototype.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.dicta.prototype.speech.SpeechController
import dev.dicta.prototype.speech.VoicePolicy
import dev.dicta.prototype.data.EntrySnapshot
import kotlinx.serialization.json.*

val LocalSpeech=staticCompositionLocalOf<SpeechController?> {null}
@Composable fun WithSpeech(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val speech=remember {SpeechController(context)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,speech) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_PAUSE)speech.stop()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);speech.close()}
    }
    CompositionLocalProvider(LocalSpeech provides speech,content=content)
}
@Composable fun Pronunciation(entry:EntrySnapshot,container:String) {
    val enabled=remember(entry.templateJson,container) {runCatching {
        Json.parseToJsonElement(entry.templateJson).jsonObject["actions"]?.jsonObject?.get(container)?.jsonArray?.any {it.jsonPrimitive.content=="speak"}==true
    }.getOrDefault(false)}
    if(enabled) SpeechControls(entry.rendered.speech)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SpeechControls(text:String) {
    val speech=LocalSpeech.current ?: return
    val state by speech.state.collectAsState()
    DisposableEffect(text) {onDispose {speech.stop()}}
    Column {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            for((country,label) in listOf("GB" to "UK","US" to "US")) {
                val playing=state.activeId!=null && state.text==text && state.country==country
                OutlinedButton(onClick={if(playing)speech.stop() else {speech.select(country);speech.speak(text)}},
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.primary),
                    enabled=state.initialized && VoicePolicy.choose(state.voices,country)!=null && text.isNotBlank(),modifier=Modifier.testTag("speak$label")) {
                    Text(if(playing) "$label · 停止" else "$label · 发音")
                }
            }
        }
        if(state.message.isNotBlank()) Text(state.message,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SpeechSettings() {
    val speech=LocalSpeech.current ?: return
    val state by speech.state.collectAsState()
    EntryCard {
        Text("发音 · Android TTS",style=MaterialTheme.typography.titleLarge)
        Text(state.engine.ifBlank {"系统语音引擎"},style=MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            for((country,label) in listOf("GB" to "UK English","US" to "US English")) {
                val available=VoicePolicy.choose(state.voices,country)!=null
                FilterChip(selected=state.country==country && state.voice.isNotBlank(),onClick={speech.select(country)},enabled=available,
                    label={Text(label+if(available) "" else " · 不可用")})
            }
        }
        if(state.voice.isNotBlank())Text(state.voice,style=MaterialTheme.typography.labelSmall)
        SpeechControls("Serendipity. A little discovery, a brighter day.")
    }
}
