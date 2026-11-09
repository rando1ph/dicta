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
    val state by model.state.collectAsState()
    val history by model.history.collectAsState()
    Scaffold(containerColor=MaterialTheme.colorScheme.background,
        bottomBar = { NavigationBar(containerColor=MaterialTheme.colorScheme.background) {
            listOf("Search" to "search","Vocabulary" to "book","History" to "history","Settings" to "settings").forEach { (name,glyph) ->
                NavigationBarItem(selected=page==name,onClick={page=name},icon={Glyph(glyph)},label={Text(name)},modifier=Modifier.testTag("nav$name"))
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
            when(page) {
                "Search" -> Home(state,history,model, onHistory={page="History"})
                "History" -> History(history) { model.openSnapshot(it.id);page="Search" }
                "Vocabulary" -> Vocabulary()
                "Settings" -> Settings(model) {page="Search"}
            }
        }
    }
}

@Composable
private fun Home(state: LookupState, history: List<EntrySnapshot>, model: LookupViewModel, onHistory:()->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide();model.lookup(query) }
    Column(Modifier.verticalScroll(rememberScrollState()).testTag("homeScroll"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth().testTag("queryInput"),
            singleLine=true,shape=RoundedCornerShape(50),placeholder={Text("Search a word / 查词…")},leadingIcon={Glyph("search")},
            trailingIcon={IconButton(onClick=submit,modifier=Modifier.semantics { contentDescription="查询" }) { Glyph("arrow") }},
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit()}))
        Text("M1 演示 · luminous / ephemeral / qzxvplmnonce",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
        val template = (state as? LookupState.Success)?.entry?.templateJson
        val homeCorner = remember(template) { template?.let { Json.parseToJsonElement(it).jsonObject["containers"]?.jsonObject?.get("home")?.jsonObject?.get("cornerDp")?.jsonPrimitive?.int } ?: 28 }
        EntryCard(corner=homeCorner.dp) { LookupContent(state, model::retry, model::cancel) }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("最近查询",style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onHistory) {Text("查看历史")}
        }
        if(history.isEmpty()) Text("暂无会话记录。试着查询 luminous。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        history.take(3).forEach { entry -> EntryRow(entry) {model.openSnapshot(entry.id)} }
        Text("会话内演示记录；每日词、收藏和持久保存将在后续阶段提供。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun EntryCard(corner:Dp=28.dp, content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(corner),color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}

@Composable
fun LookupContent(state: LookupState, retry:()->Unit, cancel:()->Unit, showPrototypeActions:Boolean=true) {
    when(state) {
        LookupState.Idle -> Text("输入词条，开始演示。")
        is LookupState.Loading -> Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("正在载入演示词条",style=MaterialTheme.typography.titleLarge)
            Text(state.query,style=MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick=cancel) { Text("取消") }
        }
        is LookupState.Success -> {
            Text("演示数据 · M0 已验证样例",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
            EntryRenderer(state.entry.rendered)
            if(showPrototypeActions) {
                val actions = remember(state.entry.templateJson) { Json.parseToJsonElement(state.entry.templateJson).jsonObject["actions"]?.jsonObject?.get("home")?.jsonArray?.map {it.jsonPrimitive.content}.orEmpty() }
                val unavailable = listOfNotNull(if("save" in actions) "收藏待 M3" else null,if("speak" in actions) "TTS 待 M4" else null)
                if(unavailable.isNotEmpty()) Text(unavailable.joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

@Composable
private fun History(history: List<EntrySnapshot>, onOpen:(EntrySnapshot)->Unit) {
    var filter by rememberSaveable {mutableStateOf("")}
    val visible=history.filter { it.rendered.summary.values.any {value->value.contains(filter,true)} }
    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("查询历史",style=MaterialTheme.typography.headlineMedium)
        Text("本次会话 · ${history.size} 条",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary)
        OutlinedTextField(value=filter,onValueChange={filter=it},singleLine=true,label={Text("搜索历史")},shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth())
        Text("M1 原型：可重开会话快照；持久保存与批量管理待后续阶段。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(visible.isEmpty()) Text(if(history.isEmpty()) "尚无会话记录" else "没有匹配词条",Modifier.padding(vertical=32.dp))
        visible.forEach {entry->EntryRow(entry){onOpen(entry)}}
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Vocabulary() {
    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("生词本",style=MaterialTheme.typography.headlineMedium)
        Text("Vocabulary · 0 entries",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary)
        EntryCard {
            Glyph("book",Modifier.size(36.dp))
            Text("留住值得重读的词",style=MaterialTheme.typography.titleLarge)
            Text("已保存词条可再次查看。",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("M1 原型入口 · 收藏与持久管理将在 M3 提供。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Settings(model:LookupViewModel, onDemo:()->Unit) {
    val mode by Appearance.mode.collectAsState()
    val accent by Appearance.accent.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).testTag("settingsScroll"),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("AI CONFIGURATION · 模型配置",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        EntryCard {
            Text("Fake Provider",style=MaterialTheme.typography.titleLarge)
            Text("M1 仅加载本地演示样例，不连接 AI 服务。BYOK 配置将在 M2 开放。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("DICTIONARY PROFILE · 词典方案",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        EntryCard {
            Text("Standard Literary · 文学精读",style=MaterialTheme.typography.titleLarge)
            Text("默认 Profile v1 · Prompt / Schema / Template",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("内置只读配置 · 编辑器待 M3",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary)
        }
        Text("APPEARANCE · 外观",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        EntryCard {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                ThemeMode.entries.forEach { m -> FilterChip(selected=mode==m,onClick={Appearance.mode.value=m},label={Text(m.name)}) }
            }
            Text("Accent Colour / 强调色",style=MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                accentColors.forEachIndexed {index,color ->
                    Box(Modifier.size(48.dp).clickable {Appearance.accent.value=index}.semantics {contentDescription="Accent ${index+1}"},contentAlignment=Alignment.Center) {
                        Box(Modifier.size(if(accent==index) 38.dp else 30.dp).background(color,CircleShape),contentAlignment=Alignment.Center) {
                            if(accent==index) Text("✓",color=androidx.compose.ui.graphics.Color(0xFF141218))
                        }
                    }
                }
            }
            Text("外观选择仅在本次会话生效。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EntryCard {
            Text("发音 · Android TTS",style=MaterialTheme.typography.titleLarge)
            Text("M4 接入系统语音；IPA 为音标，UK / US 为发音口音。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EntryCard {
            Text("原型状态演示",style=MaterialTheme.typography.titleLarge)
            Text("测试输入：:error、:timeout、:slow。仅模拟状态，无网络请求。",style=MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("失败" to ":error","超时" to ":timeout","慢速" to ":slow").forEach {(label,query)->
                    OutlinedButton(onClick={model.lookup(query);onDemo()}) {Text(label)}
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
