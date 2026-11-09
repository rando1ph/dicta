package dev.dicta.prototype.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.dicta.prototype.profile.*
import dev.dicta.prototype.query.LookupViewModel
import dev.dicta.prototype.render.EntryRenderer
import kotlinx.coroutines.*

object ProfileFiles {
    fun read(context:android.content.Context,uri:Uri):String {
        val bytes=context.contentResolver.openInputStream(uri)?.use {input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(output.size()<=262144) {val count=input.read(buffer,0,minOf(buffer.size,262145-output.size()));if(count<0) break;output.write(buffer,0,count)}
            output.toByteArray()
        } ?: throw ProfileFailure("invalid_profile")
        if(bytes.size>262144) throw ProfileFailure("profile_too_large")
        return bytes.toString(Charsets.UTF_8)
    }
    fun write(context:android.content.Context,uri:Uri,draft:ProfileDraft) {
        draft.preview()
        context.contentResolver.openOutputStream(uri,"wt")?.use {it.write(draft.export().toByteArray())} ?: throw ProfileFailure("invalid_profile")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(model:LookupViewModel,onBack:()->Unit) {
    val choices by model.profiles.collectAsState()
    val active by model.activeProfile.collectAsState()
    val message by model.profileMessage.collectAsState()
    val busy by model.profileBusy.collectAsState()
    var selectedId by rememberSaveable {mutableStateOf<String?>(null)}
    val selected=choices.firstOrNull {it.revision.id==selectedId} ?: active ?: choices.firstOrNull()
    if(selected==null) {Text("正在读取 Profile…");return}
    var creating by remember {mutableStateOf(false)}
    var pendingSave by remember {mutableStateOf(false)}
    var draft by remember(selected.revision.id,creating) {mutableStateOf(selected.draft.let {if(creating) it.copy(name=it.name+" · 自定义") else it})}
    val readOnly=selected.builtin && !creating
    var tab by rememberSaveable {mutableStateOf("Prompt")}
    var preview by remember {mutableStateOf<RenderedEntry?>(null)}
    var validation by remember {mutableStateOf("")}
    var validating by remember {mutableStateOf(false)}
    var importDialog by remember {mutableStateOf(false)}
    var importText by remember {mutableStateOf("")}
    val context=LocalContext.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri-> if(uri!=null) {
        selectedId=null;creating=false
        model.profileAction {val imported=ProfileDraft.import(ProfileFiles.read(context,uri));model.importValidatedProfile(imported);"Profile 已导入并启用。"}
    } }
    // The saved snapshot of the draft is used even if edits continue while the picker is open.
    var exportDraft by remember {mutableStateOf(draft)}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->if(uri!=null) model.profileAction {ProfileFiles.write(context,uri,exportDraft);"Profile 已导出（不含密钥或设备端点）。"}}
    LaunchedEffect(message,busy) {
        if(pendingSave && !busy) {
            if(message=="Profile 已保存并启用。") {creating=false;selectedId=null}
            pendingSave=false
        }
    }
    LaunchedEffect(draft) {
        validating=true;preview=null;validation="正在校验…"
        delay(300)
        try {preview=withContext(Dispatchers.Default) {draft.preview()};validation="校验通过 · 本地即时预览"}
        catch(e:Exception) {validation=profileMessage(e)}
        finally {validating=false}
    }
    Column(Modifier.verticalScroll(rememberScrollState()).testTag("profileScroll"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("词典方案",style=MaterialTheme.typography.headlineMedium)
            TextButton(onClick=onBack) {Text("返回设置")}
        }
        Text("PROFILE",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            choices.forEach {choice->FilterChip(selected=!creating && selected.revision.id==choice.revision.id,onClick={creating=false;selectedId=choice.revision.id},label={Text(choice.revision.name)},modifier=Modifier.testTag("profileChoice${choice.revision.profileId}"))}
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={creating=true;selectedId="builtin"},modifier=Modifier.testTag("newProfile")) {Text("新建")}
            OutlinedButton(onClick={creating=true},modifier=Modifier.testTag("copyProfile")) {Text("复制")}
            OutlinedButton(onClick={model.activateProfile(selected)},enabled=!creating && !busy) {Text("启用")}
            TextButton(onClick={model.restoreDefault();selectedId="builtin";creating=false},enabled=!busy,modifier=Modifier.testTag("restoreDefault")) {Text("恢复预置默认")}
        }
        if(readOnly) Text("预置默认为只读。复制后即可编辑；恢复默认不会删除用户方案或旧词条。",color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(draft.name,{draft=draft.copy(name=it)},readOnly=readOnly,label={Text("Profile 名称")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("profileName"))
        OutlinedTextField(draft.model,{draft=draft.copy(model=it)},readOnly=readOnly,label={Text("Model（留空沿用 BYOK 设置）")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("profileModel"))
        Text("使用设备当前 BYOK 端点和密钥；导入不改变端点或关联凭据。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Prompt","Schema","Template","Example").forEach {name->FilterChip(selected=tab==name,onClick={tab=name},label={Text(name)},modifier=Modifier.testTag("edit$name"))}
        }
        Text(when(tab) {"Schema"->"Draft-07 受控子集；无远程引用、脚本或组合逻辑。";"Template"->"原生声明式节点、JSON Pointer 绑定；顺序由 nodes 决定。";"Example"->"用于本地预览，须匹配 Schema；不会自动调用 AI。";else->"保留 {{schema_json}} 和 {{example_json}} 占位符。"},style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val value=when(tab) {"Schema"->draft.schema;"Template"->draft.template;"Example"->draft.example;else->draft.prompt}
        OutlinedTextField(value,{text->draft=when(tab) {"Schema"->draft.copy(schema=text);"Template"->draft.copy(template=text);"Example"->draft.copy(example=text);else->draft.copy(prompt=text)}},readOnly=readOnly,label={Text(tab)},textStyle=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace),minLines=6,maxLines=10,keyboardOptions=KeyboardOptions(autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth().testTag("profileEditor"))
        Text(validation,color=if(preview!=null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,modifier=Modifier.testTag("profileValidation"))
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={model.saveProfile(draft,if(creating) null else selected.revision.profileId);pendingSave=true},enabled=!readOnly && !busy && !validating && preview!=null,modifier=Modifier.testTag("saveProfile")) {Text("保存并启用")}
            OutlinedButton(onClick={exportDraft=draft;exporter.launch("Dicta-profile.json")},enabled=preview!=null && !busy,modifier=Modifier.testTag("exportProfile")) {Text("导出文件")}
            OutlinedButton(onClick={picker.launch(arrayOf("application/json","text/plain"))},enabled=!busy) {Text("导入文件")}
            TextButton(onClick={importDialog=true},enabled=!busy,modifier=Modifier.testTag("importProfile")) {Text("导入 JSON")}
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank()) Text(message,modifier=Modifier.testTag("profileMessage"),color=MaterialTheme.colorScheme.secondary)
        Text("PREVIEW · 即时预览",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        preview?.let {EntryCard {EntryRenderer(it)}}
        Spacer(Modifier.height(16.dp))
    }
    if(importDialog) AlertDialog(onDismissRequest={importDialog=false},title={Text("导入 Profile JSON")},text={OutlinedTextField(importText,{importText=it},label={Text("formatVersion 1 配置包")},minLines=6,maxLines=12,modifier=Modifier.testTag("importJson"))},confirmButton={TextButton(onClick={model.importProfile(importText);importDialog=false;selectedId=null;creating=false},modifier=Modifier.testTag("confirmImport")) {Text("导入并启用")}},dismissButton={TextButton(onClick={importDialog=false}) {Text("取消")}})
}
