package dev.dicta.prototype.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DailyPolicyTest {
    @Test fun slotsReuseSameDayButChangeAtDateZoneOrProfile() {
        val date=LocalDate.of(2026,11,10);val zone=ZoneId.of("Asia/Shanghai")
        val slot=DailyPolicy.slot(date,zone,"profile-one")
        assertEquals(slot,DailyPolicy.slot(date,zone,"profile-one"))
        assertNotEquals(slot,DailyPolicy.slot(date.plusDays(1),zone,"profile-one"))
        assertNotEquals(slot,DailyPolicy.slot(date,ZoneId.of("UTC"),"profile-one"))
        assertNotEquals(slot,DailyPolicy.slot(date,zone,"profile-two"))
    }
    @Test fun blankAndDuplicateWordsAreRejectedWithoutRetrying() {
        assertThrows(dev.dicta.prototype.provider.ProviderFailure::class.java) {DailyPolicy.requireNew("  VERDANT ",listOf("verdant"))}
        assertThrows(dev.dicta.prototype.provider.ProviderFailure::class.java) {DailyPolicy.requireNew(" ",emptyList())}
        DailyPolicy.requireNew("petrichor",listOf("verdant"))
    }
    @Test fun excludesNormalizeDeduplicateAndBoundRecentWords() {
        val words=DailyPolicy.excludes(listOf("  Verdant ","VERDANT","two   words")+(1..30).map {"word$it"},"Petrichor")
        assertEquals("petrichor",words.first());assertEquals(21,words.size)
        assertEquals(1,words.count {it=="verdant"});assertTrue("two words" in words)
    }
}
