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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.view.WindowManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.serialization.json.*
import dev.dicta.prototype.data.EntrySnapshot
import dev.dicta.prototype.query.LookupState
import dev.dicta.prototype.query.LookupViewModel
import dev.dicta.prototype.render.EntryRenderer

@Composable
fun MainScreen(model: LookupViewModel) {
    var page by rememberSaveable { mutableStateOf("Search") }
    androidx.activity.compose.BackHandler(enabled=page=="Profile") {page="Settings"}
    val state by model.state.collectAsState()
    val history by model.history.collectAsState()
    val warning by model.storageMessage.collectAsState()
    val action by model.actionMessage.collectAsState()
    Scaffold(containerColor=MaterialTheme.colorScheme.background,
        bottomBar = { NavigationBar(containerColor=MaterialTheme.colorScheme.background) {
            listOf("Search" to "search","Vocabulary" to "book","History" to "history","Settings" to "settings").forEach { (name,glyph) ->
                NavigationBarItem(selected=page==name || (page=="Profile" && name=="Settings"),onClick={page=name},icon={Glyph(glyph)},label={Text(name)},modifier=Modifier.testTag("nav$name"))
            }
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Dicta",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
                Column(horizontalAlignment=Alignment.End) {
                    Text(if(page=="Search") "DICTA AI LEXICON" else page.uppercase(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(page=="Search") Text("Less is More",fontFamily=FontFamily.Serif,style=MaterialTheme.typography.titleMedium)
                }
            }
            if(warning.isNotBlank()) Text(warning,color=MaterialTheme.colorScheme.error)
            if(action.isNotBlank()) Text(action,color=MaterialTheme.colorScheme.error)
            when(page) {
                "Search" -> Home(state,history,model, onHistory={page="History"}, onSettings={page="Settings"})
                "History" -> CollectionScreen(model,false) { model.openSnapshot(it.id);page="Search" }
                "Vocabulary" -> CollectionScreen(model,true) { model.openSnapshot(it.id);page="Search" }
                "Settings" -> Settings(model) {page="Profile"}
                "Profile" -> ProfileScreen(model) {page="Settings"}
            }
        }
    }
}

@Composable
private fun Home(state: LookupState, history: List<EntrySnapshot>, model: LookupViewModel, onHistory:()->Unit, onSettings:()->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var clearHistory by remember {mutableStateOf(false)}
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide();model.lookup(query) }
    val daily by model.daily.collectAsState()
    val active by model.activeProfile.collectAsState()
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle,active?.revision?.id) {
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

        val template = (state as? LookupState.Success)?.entry?.takeIf {it.compatibilityWarning==null}?.templateJson
        val homeCorner = remember(template) { template?.let { runCatching { Json.parseToJsonElement(it).jsonObject["containers"]?.jsonObject?.get("home")?.jsonObject?.get("cornerDp")?.jsonPrimitive?.int }.getOrNull() } ?: 28 }
        if(state!=LookupState.Idle) EntryCard(corner=homeCorner.dp) {
            LookupContent(state, model::retry, model::cancel)
            if(state is LookupState.Success) BookmarkButton(state.entry,model)
        }
        EntryCard {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("WORD OF THE DAY",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                Text(daily.localDate,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            daily.entry?.let { entry->
                entry.compatibilityWarning?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                EntryRenderer(entry.rendered)
                BookmarkButton(entry,model,"dailySave")
            }
            if(daily.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("dailyLoading"))
            if(daily.message.isNotBlank()) Text(daily.message,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(daily.entry==null && !daily.busy && daily.message.isBlank()) Text("每日一个值得发现的英文词。")
            OutlinedButton(onClick={model.daily(true)},enabled=!daily.busy,modifier=Modifier.testTag("dailyShuffle")) {Text(if(daily.entry==null) "获取每日词" else "换一换")}
        }
        if(state == LookupState.Idle || state is LookupState.Error) TextButton(onClick={onSettings()}) { Text("模型设置") }
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
            EntryRenderer(state.entry.rendered)

        }
        is LookupState.Error -> Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("暂时无法展示",style=MaterialTheme.typography.titleLarge)
            Text(state.message,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.query.isNotBlank()) TextButton(onClick=retry) {Text("重试")}
            else Text("请重新输入或选择文本。",style=MaterialTheme.typography.bodyMedium)
        }
        LookupState.Cancelled -> Text("已取消。可输入词条重新查询。",color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntryRow(entry: EntrySnapshot, onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth()) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Settings(model:LookupViewModel,onProfiles:()->Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE); model.cancelConfig() }
    }
    val active by model.activeProfile.collectAsState()
    val saved by model.configuration.collectAsState()
    val message by model.configMessage.collectAsState()
    val busy by model.configBusy.collectAsState()
    var endpoint by remember(saved.endpoint) { mutableStateOf(saved.endpoint) }
    var modelName by remember(saved.model) { mutableStateOf(saved.model) }
    var key by remember { mutableStateOf("") }
    val mode by Appearance.mode.collectAsState()
    val accent by Appearance.accent.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).testTag("settingsScroll"),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("AI CONFIGURATION · 模型配置",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        EntryCard {
            Text("DeepSeek · BYOK",style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(endpoint,{endpoint=it},label={Text("Endpoint")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("endpointInput"))
            OutlinedTextField(modelName,{modelName=it},label={Text("Model")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("modelInput"))
            OutlinedTextField(key,{key=it},label={Text("API Key")},placeholder={Text(if(saved.hasKey) "已保存 · 留空保留" else "输入密钥")},singleLine=true,
                visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth().testTag("keyInput"))
            Text(if(saved.hasKey) "密钥已安全保存" else "尚未配置密钥",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={model.saveConfig(endpoint,modelName,key);key=""},enabled=!busy) {Text("保存")}
                OutlinedButton(onClick={key="";model.clearConfig()},enabled=!busy) {Text("清除配置")}
                OutlinedButton(onClick={model.testConfig()},enabled=!busy) {Text("测试连接")}
            }
            Text("测试使用已保存配置生成一条词条，会产生少量 API 用量。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick=model::cancelConfig) {Text("取消测试")} }
            if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.secondary)
        }
        Text("DICTIONARY PROFILE · 词典方案",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        EntryCard {
            Text(active?.revision?.name ?: "Standard Literary · 文学精读",style=MaterialTheme.typography.titleLarge)
            Text("Prompt / Schema / Popup Template",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick=onProfiles,modifier=Modifier.testTag("openProfiles")) {Text("管理 Profile")}

        }
        Text("APPEARANCE · 外观",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        EntryCard {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                ThemeMode.entries.forEach { m -> FilterChip(selected=mode==m,onClick={Appearance.selectMode(m)},label={Text(m.name)}) }
            }
            Text("Accent Colour / 强调色",style=MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                accentColors.forEachIndexed {index,color ->
                    Box(Modifier.size(48.dp).clickable {Appearance.selectAccent(index)}.semantics {contentDescription="Accent ${index+1}"},contentAlignment=Alignment.Center) {
                        Box(Modifier.size(if(accent==index) 38.dp else 30.dp).background(color,CircleShape),contentAlignment=Alignment.Center) {
                            if(accent==index) Text("✓",color=androidx.compose.ui.graphics.Color(0xFF141218))
                        }
                    }
                }
            }
            Text("外观选择已保存在此设备。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EntryCard {
            Text("发音 · Android TTS",style=MaterialTheme.typography.titleLarge)
            Text("语音暂不可用",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
    }
}
