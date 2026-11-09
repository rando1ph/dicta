package dev.dicta.prototype.speech

/** Exact country matching prevents an arbitrary English voice being labelled UK/US. */
data class VoiceOption(val name:String,val language:String,val country:String,val network:Boolean,val installed:Boolean,val quality:Int)
object VoicePolicy {
    fun choose(voices:List<VoiceOption>,country:String):VoiceOption? = voices
        .filter {it.language=="en" && it.country==country && it.installed}
        .sortedWith(compareBy<VoiceOption> {it.network}.thenByDescending {it.quality}.thenBy {it.name}).firstOrNull()
    fun fallback(voices:List<VoiceOption>,country:String):VoiceOption? = choose(voices,country)
        ?: choose(voices,if(country=="GB") "US" else "GB")
}
