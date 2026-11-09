package dev.dicta.prototype.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dicta.prototype.data.EntrySnapshot
import dev.dicta.prototype.query.LookupViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BookmarkButton(entry:EntrySnapshot,model:LookupViewModel,tag:String="entrySave") {
    val vocabulary by model.vocabulary.collectAsState()
    val saved=vocabulary.any {it.id==entry.id}
    val modifier=Modifier.testTag(tag).semantics {stateDescription=if(saved) "已收藏" else "未收藏"}
    val click={model.bookmarks(listOf(entry.id),!saved)}
    if(saved) Button(onClick=click,modifier=modifier,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary)) {Text("✓ 已收藏")}
    else OutlinedButton(onClick=click,modifier=modifier,colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.primary)) {Text("收藏")}
}

@Composable
fun SavedEntryRow(entry:EntrySnapshot,selected:Boolean?=null,onSelect:()->Unit={},onOpen:()->Unit) {
    Surface(onClick=if(selected==null) onOpen else onSelect,shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth().testTag("row${entry.historyEventId}")) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            if(selected!=null) Checkbox(checked=selected,onCheckedChange={onSelect()})
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(entry.rendered.summary["title"].orEmpty(),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(entry.rendered.summary["badge"].orEmpty(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary)
                }
                Text(entry.rendered.summary["subtitle"].orEmpty(),style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(selected==null) Text(entryTime(entry.queriedAt),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(selected==null) Glyph("arrow",Modifier.size(18.dp))
        }
    }
}
private fun entryTime(time:Long):String {
    val seconds=((System.currentTimeMillis()-time)/1000).coerceAtLeast(0)
    return when {seconds<60->"刚刚";seconds<3600->"${seconds/60} 分钟前";seconds<86400->"${seconds/3600} 小时前";else->DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionScreen(model:LookupViewModel,vocabulary:Boolean,onOpen:(EntrySnapshot)->Unit) {
    val history by model.history.collectAsState()
    val bookmarks by model.vocabulary.collectAsState()
    val entries=if(vocabulary) bookmarks else history
    var filter by rememberSaveable {mutableStateOf("")}
    var managing by rememberSaveable {mutableStateOf(false)}
    var selected by remember {mutableStateOf(setOf<String>())}
    var confirm by remember {mutableStateOf(false)}
    val visible=entries.filter {it.query.contains(filter,true) || it.rendered.summary.values.any {v->v.contains(filter,true)}}
    fun key(entry:EntrySnapshot)=if(vocabulary) entry.id else entry.historyEventId
    LaunchedEffect(entries) { selected=selected.intersect(entries.map(::key).toSet()) }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if(vocabulary) "生词本" else "查询历史",style=MaterialTheme.typography.headlineMedium)
            TextButton(onClick={managing=!managing;selected=emptySet()},modifier=Modifier.testTag("manageCollection")) {Text(if(managing) "完成" else "管理")}
        }
        Text(if(vocabulary) "Vocabulary · ${entries.size} entries" else "${entries.size} 条查询记录",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary)
        if(vocabulary) Text("已保存词条可再次查看。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(filter,{filter=it;selected=emptySet()},singleLine=true,label={Text(if(vocabulary) "搜索生词本" else "搜索历史")},shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().testTag("collectionSearch"))
        if(managing) TextButton(onClick={selected=if(selected.size==visible.size) emptySet() else visible.map(::key).toSet()},enabled=visible.isNotEmpty()) {Text("全选当前结果")}
        LazyColumn(Modifier.weight(1f).testTag("collectionList"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=12.dp)) {
            if(visible.isEmpty()) item {Text(if(entries.isNotEmpty()) "没有匹配词条" else if(vocabulary) "留住值得重读的词 · 在词条中点击收藏" else "暂无查询记录。",Modifier.padding(vertical=32.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(visible,key={key(it)}) { entry->SavedEntryRow(entry,if(managing) key(entry) in selected else null,onSelect={val id=key(entry);selected=if(id in selected) selected-id else selected+id},onOpen={onOpen(entry)}) }
        }
        if(managing) Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerHighest) {
            FlowRow(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("已选 ${selected.size}",Modifier.padding(12.dp))
                if(!vocabulary) TextButton(onClick={model.bookmarks(visible.filter {key(it) in selected}.map {it.id},true);selected=emptySet()},enabled=selected.isNotEmpty(),modifier=Modifier.testTag("bulkBookmark")) {Text("收藏")}
                TextButton(onClick={confirm=true},enabled=selected.isNotEmpty(),modifier=Modifier.testTag("bulkRemove")) {Text(if(vocabulary) "移出" else "删除")}
            }
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(if(vocabulary) "移出所选收藏？" else "删除所选历史？")},text={Text(if(vocabulary) "仅取消收藏，查询历史仍会保留。" else "仅删除查询记录，已收藏的词条仍会保留。")},confirmButton={TextButton(onClick={if(vocabulary) model.bookmarks(selected.toList(),false) else model.deleteHistory(selected.toList());selected=emptySet();confirm=false},modifier=Modifier.testTag("confirmRemove")) {Text("确认")}},dismissButton={TextButton(onClick={confirm=false}) {Text("取消")}})
}
