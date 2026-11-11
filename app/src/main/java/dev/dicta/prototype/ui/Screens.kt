package dev.dicta.prototype.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.serialization.json.*
import dev.dicta.prototype.data.EntrySnapshot
import dev.dicta.prototype.query.LookupState
import dev.dicta.prototype.query.LookupViewModel
import dev.dicta.prototype.render.EntryRenderer

@Composable
fun MainScreen(model: LookupViewModel, onExternalDetailBack:(()->Unit)?=null) {
    var page by rememberSaveable { mutableStateOf("Search") }
    val pages=rememberSaveableStateHolder()
    val density=LocalDensity.current
    val window=LocalWindowInfo.current
    val compactLabels=with(density) {window.containerSize.width.toDp().value / fontScale < 280f}
    val state by model.state.collectAsState()
    val detail=state!=LookupState.Idle
    val back={model.closeDetail();onExternalDetailBack?.invoke();Unit}
    androidx.activity.compose.BackHandler(enabled=detail || page=="Profile") {if(detail) back() else page="Settings"}
    val history by model.history.collectAsState()
    val warning by model.storageMessage.collectAsState()
    val action by model.actionMessage.collectAsState()
    Scaffold(containerColor=MaterialTheme.colorScheme.background,
        bottomBar = { if(!detail) NavigationBar(containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.testTag("bottomNavigation")) {
            listOf("Search" to "search","Vocabulary" to "book","History" to "history","Settings" to "settings").forEach { (name,glyph) ->
                NavigationBarItem(selected=page==name || (page=="Profile" && name=="Settings"),onClick={page=name},icon={Glyph(glyph)},
                    label=if(compactLabels) null else ({Text(name,maxLines=1,overflow=TextOverflow.Ellipsis)}),
                    modifier=Modifier.testTag("nav$name").semantics {contentDescription=name})
            }
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)) {
            if(detail) {
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=back,modifier=Modifier.testTag("detailBack").semantics {contentDescription="返回"}) {Text("‹ 返回")}
                    Text("Word Detail · 词条详情",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                }
            } else Row(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Dicta",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
                Column(horizontalAlignment=Alignment.End) {
                    Text(if(page=="Search") "DICTA AI LEXICON" else page.uppercase(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(page=="Search") Text("Less is More",fontFamily=FontFamily.Serif,style=MaterialTheme.typography.titleMedium)
                }
            }
            if(warning.isNotBlank()) Text(warning,color=MaterialTheme.colorScheme.error)
            if(action.isNotBlank()) Text(action,color=MaterialTheme.colorScheme.error)
            if(detail) {
                // A new entry gets its own scroll state; the source page is saved separately.
                key((state as? LookupState.Success)?.entry?.id) {
                    val template=(state as? LookupState.Success)?.entry?.takeIf {it.compatibilityWarning==null}?.templateJson
                    val corner=remember(template) {template?.let {runCatching {Json.parseToJsonElement(it).jsonObject["containers"]?.jsonObject?.get("home")?.jsonObject?.get("cornerDp")?.jsonPrimitive?.int}.getOrNull()} ?: 28}
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("wordDetail"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        EntryCard(corner=corner.dp) {
                            LookupContent(state,model::retry,model::cancel)
                            if(state is LookupState.Success) BookmarkButton((state as LookupState.Success).entry,model)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            } else pages.SaveableStateProvider(page) {
                when(page) {
                    "Search" -> Home(history,model, onHistory={page="History"}, onSettings={page="Settings"})
                    "History" -> CollectionScreen(model,false) { model.openSnapshot(it.id) }
                    "Vocabulary" -> CollectionScreen(model,true) { model.openSnapshot(it.id) }
                    "Settings" -> Settings(model) {page="Profile"}
                    "Profile" -> ProfileScreen(model) {page="Settings"}
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Home(history: List<EntrySnapshot>, model: LookupViewModel, onHistory:()->Unit, onSettings:()->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var clearHistory by remember {mutableStateOf(false)}
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide();model.lookup(query) }
    val daily by model.daily.collectAsState()
    val active by model.activeProfile.collectAsState()
    val configuration by model.configuration.collectAsState()
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle,active?.revision?.id,configuration) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) model.daily()}
        lifecycle.lifecycle.addObserver(observer)
        if(lifecycle.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) model.daily()
        onDispose {lifecycle.lifecycle.removeObserver(observer)}
    }
    Column(Modifier.verticalScroll(rememberScrollState()).testTag("homeScroll"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth().testTag("queryInput"),
            singleLine=true,shape=RoundedCornerShape(50),placeholder={Text("Search a word / 查词…")},leadingIcon={Glyph("search")},
            trailingIcon={IconButton(onClick=submit,modifier=Modifier.semantics { contentDescription="查询" }) { Glyph("arrow") }},
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit()}))

        EntryCard {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("WORD OF THE DAY",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                Text(daily.localDate,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            daily.entry?.let { entry->
                entry.compatibilityWarning?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                EntryRenderer(entry.rendered) {Pronunciation(entry,"home")}
            }
            if(daily.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("dailyLoading"))
            if(daily.message.isNotBlank()) Text(daily.message,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(daily.entry==null && !daily.busy && daily.message.isBlank()) Text("每日一个值得发现的英文词。")
            FlowRow(Modifier.fillMaxWidth().testTag("dailyActions"),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                daily.entry?.let {BookmarkButton(it,model,"dailySave")}
                OutlinedButton(onClick={model.daily(true)},enabled=!daily.busy,modifier=Modifier.testTag("dailyShuffle")) {Text(if(daily.message.isNotBlank()) "重试" else if(daily.entry==null) "获取每日词" else "换一换")}
            }
        }
        TextButton(onClick={onSettings()}) { Text("模型设置") }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("最近查询",style=MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick=onHistory) {Text("管理")}
                TextButton(onClick={clearHistory=true},enabled=history.isNotEmpty()) {Text("清空")}
            }
        }
        if(history.isEmpty()) Text("暂无查询记录。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        history.take(3).forEach { entry -> EntryRow(entry) {model.openSnapshot(entry.id)} }

        Spacer(Modifier.height(8.dp))
    }
    if(clearHistory) AlertDialog(onDismissRequest={clearHistory=false},title={Text("清空查询历史？")},text={Text("已收藏词条和每日词仍会保留。")},confirmButton={TextButton(onClick={model.deleteHistory(history.map {it.historyEventId});clearHistory=false}) {Text("确认清空")}},dismissButton={TextButton(onClick={clearHistory=false}) {Text("取消")}})
}

@Composable
fun EntryCard(corner:Dp=28.dp, content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(corner),color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}

@Composable
fun LookupContent(state: LookupState, retry:()->Unit, cancel:()->Unit) {
    when(state) {
        LookupState.Idle -> Text("输入词或短语，开始查询。")
        is LookupState.Loading -> Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("正在查询",style=MaterialTheme.typography.titleLarge)
            Text(state.query,style=MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick=cancel) { Text("取消") }
        }
        is LookupState.Success -> {
            state.entry.compatibilityWarning?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            EntryRenderer(state.entry.rendered) {Pronunciation(state.entry,if(LocalContext.current is dev.dicta.prototype.platform.ProcessTextActivity) "popup" else "home")}

        }
        is LookupState.Error -> Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("暂时无法展示",style=MaterialTheme.typography.titleLarge)
            Text(state.message,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.query.isNotBlank()) TextButton(onClick=retry) {Text("重试")}
            else Text("请重新输入或选择文本。",style=MaterialTheme.typography.bodyMedium)
        }
        LookupState.Cancelled -> Column {
            Text("已取消。",color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick=retry) {Text("重试")}
        }
    }
}

@Composable
private fun EntryRow(entry: EntrySnapshot, onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth().testTag("recent${entry.id}")) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(entry.rendered.summary["title"].orEmpty(),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(entry.rendered.summary["badge"].orEmpty(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary)
                Glyph("arrow",Modifier.size(18.dp))
            }
            Text(entry.rendered.summary["subtitle"].orEmpty(),style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
