package dev.dicta.prototype
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import dev.dicta.prototype.platform.MainActivity
import dev.dicta.prototype.ui.Appearance
import dev.dicta.prototype.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
class M4AppearanceTest {
    @Test fun systemBarIconsFollowAppTheme() {
        val original=Appearance.mode.value
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            try {
                for(mode in listOf(ThemeMode.Light,ThemeMode.Dark)) {
                    scenario.onActivity {Appearance.mode.value=mode}
                    val deadline=System.currentTimeMillis()+5000
                    var correct=false
                    while(!correct && System.currentTimeMillis()<deadline) {
                        scenario.onActivity {activity->
                            val bars=WindowCompat.getInsetsController(activity.window,activity.window.decorView)
                            correct=bars.isAppearanceLightStatusBars==(mode==ThemeMode.Light) && bars.isAppearanceLightNavigationBars==(mode==ThemeMode.Light)
                        }
                        if(!correct)Thread.sleep(50)
                    }
                    assertEquals("System icon appearance must match app theme",true,correct)
                }
            } finally {scenario.onActivity {Appearance.mode.value=original}}
        }
    }
}
