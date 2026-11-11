package dev.dicta.prototype.platform

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.dicta.prototype.query.LookupState
import dev.dicta.prototype.query.LookupViewModel
import dev.dicta.prototype.ui.*
import kotlinx.serialization.json.*

class DictaApplication : Application() {
    override fun onCreate() {super.onCreate();Appearance.initialize(this)}
}

class MainActivity : ComponentActivity() {
    val model: LookupViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryId = if(savedInstanceState!=null) savedInstanceState.getString("snapshotId") else intent.getStringExtra("snapshotId")
        if (model.state.value == LookupState.Idle) {
            when {
                entryId != null -> model.openSnapshot(entryId)
                savedInstanceState?.getBoolean("detailOpen")==true -> model.restoredWithoutRequest(savedInstanceState.getString("query").orEmpty())
                else -> Unit
            }
        }
        setContent { DictaTheme { WithSpeech { MainScreen(model,if(intent.getBooleanExtra("fromPopup",false)) ({finish()}) else null) } } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        saveQueryState(outState, model.state.value)
        super.onSaveInstanceState(outState)
    }
}

/** Normal Activity in Chrome's task; no overlay permission or text replacement. */
class ProcessTextActivity : ComponentActivity() {
    val model: LookupViewModel by viewModels()
    var sourceReadOnly: Boolean = true
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        if (!validIntent(intent)) { finish(); return }
        sourceReadOnly = readOnly(intent)
        if (model.state.value == LookupState.Idle) {
            if (savedInstanceState != null) {
                val id = savedInstanceState.getString("snapshotId")
                if (id != null) model.openSnapshot(id) else model.restoredWithoutRequest(savedInstanceState.getString("query").orEmpty())
            } else model.lookup(selectedText(intent), "popup")
        }
        setContent { DictaTheme { WithSpeech { Popup(model, ::dismiss) { id ->
            startActivity(Intent(this, MainActivity::class.java).putExtra("snapshotId",id).putExtra("fromPopup",true))
            dismiss()
        } } } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!validIntent(intent)) { dismiss();return }
        setIntent(intent)
        sourceReadOnly = readOnly(intent)
        model.lookup(selectedText(intent), "popup")
    }
    private fun readOnly(intent:Intent):Boolean = try { intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true) } catch (_: RuntimeException) { true }
    private fun validIntent(intent:Intent) = intent.action == Intent.ACTION_PROCESS_TEXT && intent.type == "text/plain"
    private fun selectedText(intent:Intent):String = try {
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
    } catch (_: RuntimeException) { "" }
    fun dismiss() { model.cancel();setResult(RESULT_CANCELED);finish() }
    override fun onDestroy() {
        if (isFinishing) model.cancel()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        saveQueryState(outState,model.state.value)
        super.onSaveInstanceState(outState)
    }
}

private fun saveQueryState(bundle:Bundle,state:LookupState) {
    bundle.putBoolean("detailOpen",state!=LookupState.Idle)
    when(state) {
        is LookupState.Success -> { bundle.putString("snapshotId",state.entry.id);bundle.putString("query",state.entry.query) }
        is LookupState.Loading -> bundle.putString("query",state.query)
        is LookupState.Error -> bundle.putString("query",state.query)
        else -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Popup(model:LookupViewModel, dismiss:()->Unit, details:(String)->Unit) {
    val state by model.state.collectAsState()
    val template = (state as? LookupState.Success)?.entry?.takeIf {it.compatibilityWarning==null}?.templateJson
    val config = remember(template) { template?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } }
    val popup = config?.get("containers")?.jsonObject?.get("popup")?.jsonObject
    val actions = config?.get("actions")?.jsonObject?.get("popup")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val safeVertical = with(density) { (WindowInsets.safeDrawing.getTop(this) + WindowInsets.safeDrawing.getBottom(this)).toDp() }
    // Preserve readable host context above and below even for long entries.
    val availableHeight = (windowHeight - safeVertical - 32.dp).coerceAtLeast(0.dp) * .78f
    val maxWidth = (popup?.get("maxWidthDp")?.jsonPrimitive?.int ?: 480).dp
    val margin = (popup?.get("marginDp")?.jsonPrimitive?.int ?: 16).dp
    val corner = (popup?.get("cornerDp")?.jsonPrimitive?.int ?: 28).dp
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=true,dismissOnClickOutside=true)) {
        // Native outside-window dismissal does not include padding INSIDE the window.
        // Hit-test the transparent gutters/corners explicitly; leave card gestures alone.
        Box(Modifier.widthIn(max=maxWidth).fillMaxWidth().testTag("popupTouchBounds")
            .pointerInput(margin, corner, dismiss) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val inset = margin.toPx()
                    val width = size.width - inset * 2
                    val height = size.height.toFloat()
                    val radius = minOf(corner.toPx(), width / 2, height / 2)
                    val x = down.position.x - inset
                    val y = down.position.y
                    val centerX = x.coerceIn(radius, (width - radius).coerceAtLeast(radius))
                    val centerY = y.coerceIn(radius, (height - radius).coerceAtLeast(radius))
                    val inside = x >= 0 && x <= width && y >= 0 && y <= height &&
                        (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY) <= radius * radius
                    if (!inside) {
                        down.consume()
                        if (waitForUpOrCancellation() != null) dismiss()
                    }
                }
            }.padding(horizontal=margin)) {
        Surface(
            modifier=Modifier.fillMaxWidth().heightIn(max=availableHeight).testTag("dictaPopup"),
            shape=RoundedCornerShape(corner),color=MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation=6.dp,shadowElevation=12.dp
        ) {
            Column(Modifier.padding(horizontal=22.dp,vertical=12.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("Dicta · Popup",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    IconButton(onClick=dismiss,modifier=Modifier.testTag("closePopup").semantics { contentDescription="关闭 Dicta" }) {Glyph("close")}
                }
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("popupScroll"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    LookupContent(state,model::retry,dismiss)
                    Spacer(Modifier.height(8.dp))
                }
                if(state is LookupState.Success) {
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
                    FlowRow(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(0.dp)) {
                        if("save" in actions) BookmarkButton((state as LookupState.Success).entry, model, "popupSave")
                        if("details" in actions) Button(onClick={details((state as LookupState.Success).entry.id)},modifier=Modifier.testTag("popupDetails")) {Text("打开")}
                    }

                }
            }
        }
        }
    }
}
