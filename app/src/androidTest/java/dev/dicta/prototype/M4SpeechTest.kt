package dev.dicta.prototype

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.platform.MainActivity
import dev.dicta.prototype.speech.SpeechController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class M4SpeechTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun await(label:String,condition:()->Boolean) {
        val until=System.currentTimeMillis()+25000
        while(System.currentTimeMillis()<until) {if(condition())return;Thread.sleep(50)}
        assertTrue(label,condition())
    }
    @Test fun actualVoicesSynthesisSpeakStopAndSpeakerRoute() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val dir=File(context.filesDir,"m4-tts").apply {mkdirs()}
            val events=CopyOnWriteArrayList<JSONObject>()
            val prefs=context.getSharedPreferences("speech",Context.MODE_PRIVATE)
            val original=prefs.getString("country",null)
            lateinit var speech:SpeechController
            main {speech=SpeechController(context) {kind,id,code->events.add(JSONObject().put("event",kind).put("id",id).put("code",code))}}
            try {
                await("TTS initialization failed") {speech.state.value.initialized}
                val report=JSONObject().put("engine",speech.state.value.engine)
                val inventory=JSONArray()
                speech.state.value.voices.filter {it.language=="en" && it.country in listOf("GB","US")}.forEach {inventory.put(JSONObject().put("name",it.name).put("country",it.country).put("network",it.network).put("installed",it.installed).put("quality",it.quality))}
                report.put("voices",inventory)
                val selected=JSONObject()
                for(country in listOf("GB","US")) {
                    main {speech.select(country)}
                    assertEquals(country,speech.state.value.country)
                    selected.put(country,speech.state.value.voice)
                    var id:String?=null
                    val file=File(dir,"$country.wav")
                    main {id=speech.synthesize("Serendipity. A little discovery, a brighter day.",file)}
                    assertNotNull(id)
                    await("Synthesis did not complete: $country") {events.any {it.optString("event")=="done" && it.optString("id")==id}}
                    assertTrue(file.length()>1000)
                    assertTrue(events.any {it.optString("event")=="start" && it.optString("id")==id})
                    main {id=speech.speak("Serendipity.")}
                    assertNotNull(id)
                    await("Speak did not complete") {events.any {it.optString("event")=="done" && it.optString("id")==id}}
                }
                report.put("selected",selected).put("languageSupport",JSONObject(speech.languageSupport()))
                var stopped:String?=null
                main {stopped=speech.speak("A little discovery brightens the day. ".repeat(20))}
                await("No start before stop") {events.any {it.optString("event")=="start" && it.optString("id")==stopped}}
                main {speech.stop()}
                await("onStop missing") {events.any {it.optString("event")=="stop" && it.optString("id")==stopped}}
                // Rapid replacement must not let old onStop clear the current utterance.
                var latest:String?=null
                main {speech.speak("An older request. ".repeat(20));latest=speech.speak("A brighter day.")}
                await("Replacement did not finish") {events.any {it.optString("event")=="done" && it.optString("id")==latest}}
                main {assertNull(speech.speak("x".repeat(5000)));assertTrue(speech.state.value.message.isNotBlank())}
                val audio=context.getSystemService(AudioManager::class.java)
                assertFalse(audio.isStreamMute(AudioManager.STREAM_MUSIC))
                assertTrue(audio.getStreamVolume(AudioManager.STREAM_MUSIC)>0)
                lateinit var player:MediaPlayer
                var completed=false
                var route=0
                main {
                    player=MediaPlayer().apply {
                        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        setDataSource(File(dir,"GB.wav").absolutePath)
                        setOnCompletionListener {completed=true}
                        prepare();start()
                    }
                }
                try {
                    await("No routed output") {main {route=player.routedDevice?.type ?: 0};route!=0}
                    assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,route)
                    await("Speaker playback did not complete") {completed}
                } finally {main {player.release()}}
                report.put("speaker",JSONObject().put("routedDeviceType",route).put("completed",completed).put("muted",false).put("volume",audio.getStreamVolume(AudioManager.STREAM_MUSIC)))
                report.put("events",JSONArray(events.toList())).put("userListeningApproval","pending")
                File(dir,"report.json").writeText(report.toString(2))
            } finally {
                main {speech.close();prefs.edit().apply {if(original==null)remove("country") else putString("country",original)}.commit()}
            }
        }
    }
    @Test fun injectedErrorRejectsStaleCallbacksAndClosedController() {
        ActivityScenario.launch(MainActivity::class.java).use {
            lateinit var speech:SpeechController
            main {speech=SpeechController(context)}
            try {
                await("TTS not initialized") {speech.state.value.initialized}
                var id:String?=null
                main {id=speech.speak("A little discovery. ".repeat(20));speech.callback("error","stale",-3)}
                assertEquals(id,speech.state.value.activeId)
                main {speech.callback("error",requireNotNull(id),-3)}
                instrumentation.waitForIdleSync()
                assertNull(speech.state.value.activeId)
                assertTrue(speech.state.value.message.contains("失败"))
                main {speech.close();assertNull(speech.speak("Closed"))}
            } finally {main {speech.close()}}
        }
    }

}
