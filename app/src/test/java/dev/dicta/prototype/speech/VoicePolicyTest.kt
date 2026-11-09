package dev.dicta.prototype.speech
import org.junit.Assert.*
import org.junit.Test
class VoicePolicyTest {
    private fun voice(name:String,country:String,network:Boolean=false,installed:Boolean=true,quality:Int=300)=VoiceOption(name,"en",country,network,installed,quality)
    @Test fun exactAccentRequired() {assertNull(VoicePolicy.choose(listOf(voice("au","AU")),"GB"))}
    @Test fun installedLocalPreferredOverNetwork() {assertEquals("local",VoicePolicy.choose(listOf(voice("network","GB",true,quality=500),voice("local","GB")),"GB")?.name)}
    @Test fun missingDataNeverAdvertised() {assertNull(VoicePolicy.choose(listOf(voice("missing","US",installed=false)),"US"))}
    @Test fun fallbackRetainsActualCountry() {assertEquals("US",VoicePolicy.fallback(listOf(voice("us","US")),"GB")?.country)}
    @Test fun noEnglishReturnsUnavailable() {assertNull(VoicePolicy.fallback(listOf(VoiceOption("fr","fr","FR",false,true,500)),"US"))}
    @Test fun deterministicBestQuality() {assertEquals("a",VoicePolicy.choose(listOf(voice("z","US",quality=100),voice("b","US"),voice("a","US")),"US")?.name)}
}
