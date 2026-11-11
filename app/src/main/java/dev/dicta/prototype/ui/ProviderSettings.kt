package dev.dicta.prototype.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.dicta.prototype.provider.*
import dev.dicta.prototype.query.LookupViewModel

private fun ProviderProtocol.label() = when(this) {
    ProviderProtocol.AUTO -> "自动识别"
    ProviderProtocol.DEEPSEEK -> "DeepSeek"
    ProviderProtocol.OPENAI -> "OpenAI"
    ProviderProtocol.COMPATIBLE -> "Compatible"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Settings(model: LookupViewModel, onProfiles:()->Unit) {
    DisposableEffect(model) { onDispose { model.cancelConfig(); model.cancelDiscovery() } }
    val active by model.activeProfile.collectAsState()
    val saved by model.configuration.collectAsState()
    val message by model.configMessage.collectAsState()
    val busy by model.configBusy.collectAsState()
    val discovery by model.modelDiscovery.collectAsState()
    var endpoint by rememberSaveable(saved.endpoint) { mutableStateOf(saved.endpoint) }
    var modelName by rememberSaveable(saved.model) { mutableStateOf(saved.model) }
    var protocol by rememberSaveable(saved.protocol) { mutableStateOf(saved.protocol) }
    var output by rememberSaveable(saved.output) { mutableStateOf(saved.output) }
    // Only newly typed text is held here. Stored plaintext never enters Compose or saved state.
    var key by remember { mutableStateOf("") }
    var editingKey by remember { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var protocolMenu by remember { mutableStateOf(false) }
    val mode by Appearance.mode.collectAsState()
    val accent by Appearance.accent.collectAsState()
    val dirty = endpoint.trim().trimEnd('/') != saved.endpoint || modelName.trim() != saved.model ||
        protocol != saved.protocol || output != saved.output || key.isNotBlank()
    val connectionDirty = endpoint.trim().trimEnd('/') != saved.endpoint || protocol != saved.protocol || key.isNotBlank()
    LaunchedEffect(saved) {
        if(saved.revision>0) {key="";editingKey=false}
        if(saved.hasKey) model.discoverModels()
    }
    Column(Modifier.verticalScroll(rememberScrollState()).testTag("settingsScroll"),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("AI CONFIGURATION · 模型配置",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        EntryCard {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("AI Provider / 模型服务",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                Box {
                    IconButton(onClick={more=true},modifier=Modifier.testTag("providerMore").semantics {contentDescription="模型服务更多操作"}) {Text("⋮")}
                    DropdownMenu(expanded=more,onDismissRequest={more=false}) {
                        DropdownMenuItem(text={Text("清除配置")},onClick={more=false;showClear=true},enabled=!busy,modifier=Modifier.testTag("clearConfig"))
                    }
                }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                TextButton(onClick={endpoint="https://api.deepseek.com";modelName="deepseek-flash";protocol=ProviderProtocol.DEEPSEEK}) {Text("DeepSeek")}
                TextButton(onClick={endpoint="https://api.openai.com/v1";modelName="";protocol=ProviderProtocol.OPENAI}) {Text("OpenAI")}
                TextButton(onClick={protocol=ProviderProtocol.COMPATIBLE},modifier=Modifier.testTag("compatiblePreset")) {Text("Compatible")}
            }
            OutlinedTextField(endpoint,{endpoint=it},label={Text("Endpoint / Base URL")},supportingText={Text("HTTPS 基础地址；也可填写完整 chat/completions 路径。")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("endpointInput"))
            OutlinedTextField(if(saved.hasKey && !editingKey) "••••••••" else key,{key=it},readOnly=saved.hasKey && !editingKey,
                label={Text("API Key")},placeholder={Text("输入密钥")},singleLine=true,visualTransformation=PasswordVisualTransformation(),
                trailingIcon={if(saved.hasKey) TextButton(onClick={key="";editingKey=!editingKey},modifier=Modifier.testTag("replaceKey")) {Text(if(editingKey) "保留" else "更换")}},
                keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth().testTag("keyInput"))
            Text(if(saved.hasKey) "●●●●●●●● · 已保存" else "尚未配置密钥",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary,modifier=Modifier.testTag("keyStatus"))
            Box {
                OutlinedButton(onClick={protocolMenu=true},modifier=Modifier.testTag("protocolSelect")) {Text("请求协议 · ${protocol.label()}")}
                DropdownMenu(expanded=protocolMenu,onDismissRequest={protocolMenu=false}) {
                    ProviderProtocol.entries.forEach {item->DropdownMenuItem(text={Text(item.label())},onClick={protocol=item;protocolMenu=false})}
                }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=output==JsonOutput.JSON_OBJECT,onClick={output=JsonOutput.JSON_OBJECT},label={Text("JSON mode")})
                FilterChip(selected=output==JsonOutput.PROMPT_ONLY,onClick={output=JsonOutput.PROMPT_ONLY},label={Text("仅提示词 JSON")},modifier=Modifier.testTag("promptOnly"))
            }
            Text("接口不支持 JSON mode 时可选仅提示词；两种方式均执行客户端 Schema 校验。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(modelName,{modelName=it},label={Text("全局 Model ID")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("modelInput"))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick={showModels=true},enabled=discovery.models.isNotEmpty() && !connectionDirty,modifier=Modifier.testTag("chooseModel")) {Text("选择模型")}
                TextButton(onClick=model::discoverModels,enabled=saved.hasKey && !discovery.busy && !connectionDirty,modifier=Modifier.testTag("refreshModels")) {Text("刷新列表")}
            }
            if(connectionDirty) Text("先保存新的连接配置，再获取模型列表。",style=MaterialTheme.typography.labelSmall)
            if(discovery.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(discovery.message.isNotBlank()) Text(discovery.message,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("discoveryStatus"))
            Text("模型列表不保证每个模型均支持文本查词；也可直接填写 Model ID。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val profileModel=active?.draft?.model.orEmpty()
            Text("当前生效：${effectiveModel(saved.model,profileModel).ifBlank { "请选择模型" }}\n"+if(profileModel.isBlank()) "来自全局模型；保存后生效。" else "来自 Profile，优先于全局模型。可在 Profile 中清空覆盖。",
                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("effectiveModel"))
            Button(onClick={model.saveConfig(endpoint,modelName,key,protocol,output)},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("saveConfig")) {Text("保存配置")}
            OutlinedButton(onClick=model::testConfig,enabled=!busy && saved.hasKey && !dirty && effectiveModel(saved.model,profileModel).isNotBlank(),modifier=Modifier.fillMaxWidth().testTag("testLookup")) {Text("测试查词")}
            Text("测试使用已保存配置和当前 Profile 生成词条，可能产生 API 用量。保存及获取模型列表不会自动查词。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(busy) {LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=model::cancelConfig) {Text("取消操作")}}
            if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.secondary,modifier=Modifier.testTag("configStatus"))
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
                ThemeMode.entries.forEach {m->FilterChip(selected=mode==m,onClick={Appearance.selectMode(m)},label={Text(m.name)})}
            }
            Text("Accent Colour / 强调色",style=MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                accentColors.forEachIndexed {index,color->
                    Box(Modifier.size(48.dp).clickable {Appearance.selectAccent(index)}.semantics {contentDescription="Accent ${index+1}"},contentAlignment=Alignment.Center) {
                        Box(Modifier.size(if(accent==index) 38.dp else 30.dp).background(color,CircleShape),contentAlignment=Alignment.Center) {
                            if(accent==index) Text("✓",color=androidx.compose.ui.graphics.Color(0xFF141218))
                        }
                    }
                }
            }
            Text("外观选择已保存在此设备。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpeechSettings()
        Spacer(Modifier.height(8.dp))
    }
    if(showClear) AlertDialog(onDismissRequest={showClear=false},title={Text("清除模型服务配置？")},text={Text("将移除此设备保存的 Endpoint、Model 和 API Key。历史、收藏与 Profile 会保留。")},
        confirmButton={TextButton(onClick={model.clearConfig();key="";editingKey=false;showClear=false},modifier=Modifier.testTag("confirmClearConfig")) {Text("确认清除")}},dismissButton={TextButton(onClick={showClear=false}) {Text("取消")}})
    if(showModels) ModelPicker(discovery.models,modelName,{modelName=it;showModels=false},{showModels=false})
}

@Composable
private fun ModelPicker(models:List<String>,selected:String,onSelect:(String)->Unit,onDismiss:()->Unit) {
    var filter by rememberSaveable {mutableStateOf("")}
    val visible=remember(models,filter) {models.filter {it.contains(filter,true)}}
    AlertDialog(onDismissRequest=onDismiss,title={Text("选择模型 · ${models.size}")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(filter,{filter=it},label={Text("搜索 Model ID")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("modelSearch"))
            LazyColumn(Modifier.weight(1f,fill=false).testTag("modelList")) {
                if(visible.isEmpty()) item {Text("没有匹配模型；可关闭后手动输入。")}
                items(visible,key={it}) {id->TextButton(onClick={onSelect(id)},modifier=Modifier.fillMaxWidth().testTag("modelOption$id")) {Text((if(id==selected) "✓ " else "")+id,modifier=Modifier.fillMaxWidth())}}
            }
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text("关闭")}})
}
