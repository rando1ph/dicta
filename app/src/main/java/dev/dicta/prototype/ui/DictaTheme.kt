package dev.dicta.prototype.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.content.edit

enum class ThemeMode { System, Light, Dark }
object Appearance {
    val mode = MutableStateFlow(ThemeMode.Dark)
    val accent = MutableStateFlow(0)
    private var preferences:android.content.SharedPreferences?=null
    fun initialize(context:android.content.Context) {
        preferences=context.getSharedPreferences("appearance",android.content.Context.MODE_PRIVATE)
        mode.value=runCatching {ThemeMode.valueOf(preferences?.getString("mode","Dark") ?: "Dark")}.getOrDefault(ThemeMode.Dark)
        accent.value=(preferences?.getInt("accent",0) ?: 0).coerceIn(0,4)
    }
    fun selectMode(value:ThemeMode) {mode.value=value;preferences?.edit {putString("mode",value.name)}}
    fun selectAccent(value:Int) {accent.value=value.coerceIn(0,4);preferences?.edit {putInt("accent",accent.value)}}
}
val accentColors = listOf(Color(0xFFC6BFFF), Color(0xFF76D1FF), Color(0xFF8ED5AD), Color(0xFFF5BD6C), Color(0xFFECA6C3))

@Composable
fun DictaTheme(content: @Composable () -> Unit) {
    val mode by Appearance.mode.collectAsState()
    val accent by Appearance.accent.collectAsState()
    val dark = when (mode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Dark -> true; ThemeMode.Light -> false }
    val colors = if (dark) darkColorScheme(
        primary = accentColors[accent], onPrimary = Color(0xFF28049D), primaryContainer = Color(0xFF403755),
        secondary = Color(0xFFF5BD6C), tertiary = Color(0xFF76D1FF),
        background = Color(0xFF141218), surface = Color(0xFF211F25),
        surfaceContainer = Color(0xFF211F25), surfaceContainerHigh = Color(0xFF2B292F),
        surfaceContainerHighest = Color(0xFF36343A),
        onBackground = Color(0xFFE7E0E9), onSurface = Color(0xFFE7E0E9), onSurfaceVariant = Color(0xFFC8C4D6),
        outline = Color(0xFF928F9F), outlineVariant = Color(0xFF474554)
    ) else lightColorScheme(primary = listOf(Color(0xFF594ACB),Color(0xFF00658A),Color(0xFF246B48),Color(0xFF815300),Color(0xFF984366))[accent],
        secondary = Color(0xFF815300), tertiary = Color(0xFF00658A), background = Color(0xFFFFF8FF), surface = Color(0xFFF7F1FA))
    MaterialTheme(colorScheme = colors, typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 28.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp)
    ), content = content)
}
