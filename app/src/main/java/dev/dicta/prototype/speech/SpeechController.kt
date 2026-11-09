package dev.dicta.prototype.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity/composition scoped. No speech text, credentials or engine error bodies are logged. */
class SpeechController(context:Context,private val event:(String,String,Int?)->Unit={_,_,_->}) {
    private val handler=Handler(Looper.getMainLooper())
    private val preferences=context.applicationContext.getSharedPreferences("speech",Context.MODE_PRIVATE)
    data class State(val initialized:Boolean=false,val engine:String="",val voices:List<VoiceOption> = emptyList(),
        val country:String="GB",val voice:String="",val activeId:String?=null,val text:String="",val message:String="正在初始化语音…")
    private val mutable=MutableStateFlow(State(country=preferences.getString("country","GB").let {if(it=="US") "US" else "GB"}))
    val state=mutable.asStateFlow()
    private var closed=false
    private var tts:TextToSpeech?=null
    private val initTimeout=Runnable {if(!mutable.value.initialized && !closed) {mutable.value=mutable.value.copy(message="语音初始化超时，请重新打开页面。");closed=true;tts?.shutdown()}}
    private val speechTimeout=Runnable {if(mutable.value.activeId!=null) {stop();mutable.value=mutable.value.copy(message="发音超时，请检查系统语音引擎。")}}
    init {
        handler.postDelayed(initTimeout,10000)
        tts=TextToSpeech(context.applicationContext) {status->handler.post {initialized(status)}}
    }
    private fun initialized(status:Int) {
        if(closed)return
        handler.removeCallbacks(initTimeout)
        event("init","",status)
        val engine=tts ?: return
        if(status!=TextToSpeech.SUCCESS) {mutable.value=mutable.value.copy(message="系统语音引擎不可用，请检查系统 TTS 设置。");return}
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        engine.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(id:String) = callback("start",id)
            override fun onDone(id:String) = callback("done",id)
            @Deprecated("Legacy engine callback") override fun onError(id:String) = callback("error",id,TextToSpeech.ERROR)
            override fun onError(id:String,code:Int) = callback("error",id,code)
            override fun onStop(id:String,interrupted:Boolean) = callback("stop",id,if(interrupted) 1 else 0)
        })
        val voices=runCatching {engine.voices.orEmpty().map {VoiceOption(it.name,it.locale.language,it.locale.country,it.isNetworkConnectionRequired,
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features,it.quality)}}.getOrDefault(emptyList())
        mutable.value=mutable.value.copy(initialized=true,engine=engine.defaultEngine.orEmpty(),voices=voices)
        select(mutable.value.country,persist=false)
    }
    internal fun callback(kind:String,id:String,code:Int?=null) {handler.post {
        if(closed)return@post
        event(kind,id,code)
        if(mutable.value.activeId==id && kind!="start") {
            handler.removeCallbacks(speechTimeout)
            mutable.value=mutable.value.copy(activeId=null,text="",message=if(kind=="error") "发音失败，请检查语音数据或网络后重试。" else "")
        }
    }}
    internal fun languageSupport() = mapOf("GB" to tts?.isLanguageAvailable(java.util.Locale.UK),"US" to tts?.isLanguageAvailable(java.util.Locale.US))
    fun select(country:String,persist:Boolean=true) {
        if(closed || !mutable.value.initialized)return
        stop()
        val requested=if(country=="US") "US" else "GB"
        val selected=VoicePolicy.fallback(mutable.value.voices,requested)
        if(selected==null) {mutable.value=mutable.value.copy(voice="",message="未安装 UK / US 英语语音，请在系统 TTS 设置下载。");return}
        val voice=tts?.voices?.firstOrNull {it.name==selected.name}
        if(voice==null || tts?.setVoice(voice)!=TextToSpeech.SUCCESS) {
            mutable.value=mutable.value.copy(voice="",message="无法切换语音，请检查系统 TTS 设置。");return
        }
        val fallback=selected.country!=requested
        mutable.value=mutable.value.copy(country=selected.country,voice=selected.name,message=if(fallback) "所选口音不可用，已使用 ${if(selected.country=="GB") "UK" else "US"}。" else if(selected.network) "此语音需要网络。" else "")
        if(persist)preferences.edit().putString("country",selected.country).apply()
    }
    fun speak(text:String):String? = enqueue(text,null)
    internal fun synthesize(text:String,file:File):String? = enqueue(text,file)
    private fun enqueue(text:String,file:File?):String? {
        if(closed)return null
        val s=mutable.value
        if(!s.initialized || s.voice.isBlank())return null
        if(text.isBlank() || text.length>TextToSpeech.getMaxSpeechInputLength()) {
            mutable.value=s.copy(message="文本为空或过长，无法发音。");return null
        }
        stop()
        val id=UUID.randomUUID().toString()
        mutable.value=mutable.value.copy(activeId=id,text=text,message="")
        val result=if(file==null)tts?.speak(text,TextToSpeech.QUEUE_FLUSH,Bundle(),id) else tts?.synthesizeToFile(text,Bundle(),file,id)
        event(if(file==null) "speakReturn" else "synthesizeReturn",id,result)
        if(result!=TextToSpeech.SUCCESS) {mutable.value=mutable.value.copy(activeId=null,text="",message="语音引擎未接受请求，请重试。");return null}
        handler.postDelayed(speechTimeout,20000)
        return id
    }
    fun stop() {
        handler.removeCallbacks(speechTimeout)
        mutable.value=mutable.value.copy(activeId=null,text="")
        tts?.stop()
    }
    fun close() {if(!closed) {stop();closed=true;handler.removeCallbacks(initTimeout);tts?.shutdown()}}
}
