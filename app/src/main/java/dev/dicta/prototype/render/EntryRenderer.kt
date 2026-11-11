package dev.dicta.prototype.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dicta.prototype.profile.RenderNode
import dev.dicta.prototype.profile.RenderedEntry

/** Shared, data-agnostic native renderer. All values and ordering come from the template. */
@Composable
fun EntryRenderer(entry: RenderedEntry, speech: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Custom templates may omit a headline; their declared speech action remains reachable.
        if (!entry.nodes.any { hasHeadline(it) }) speech?.invoke()
        entry.nodes.forEach { Node(it, speech=speech) }
    }
}

private fun hasHeadline(node:RenderNode):Boolean =
    node.style=="headline" || node.children.any(::hasHeadline) || node.items.any {it.any(::hasHeadline)}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Node(node: RenderNode, depth: Int = 0, speech: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    when (node.type) {
        "row" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            node.children.forEach { Node(it, depth + 1, speech) }
        }
        "column" -> {
            val etymology = node.children.any { it.style == "etymology" }
            Column(Modifier.fillMaxWidth().background(colors.surfaceContainerHighest.copy(alpha = .45f), RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (node.label != null && !etymology) Text(node.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                node.children.forEach { Node(it, depth + 1, speech) }
            }
        }
        "repeat" -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            node.items.forEachIndexed { index, children ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (depth == 0) Text((index+1).toString(), color=colors.secondary,
                        style=MaterialTheme.typography.labelMedium,
                        modifier=Modifier.background(colors.secondary.copy(alpha=.12f), RoundedCornerShape(50)).padding(horizontal=7.dp,vertical=3.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) { children.forEach { Node(it, depth + 1, speech) } }
                }
            }
        }
        "badge" -> Surface(color = colors.tertiary.copy(alpha=.13f), shape=RoundedCornerShape(50)) {
            Text(node.value.orEmpty(), Modifier.padding(horizontal=9.dp,vertical=5.dp), color=colors.tertiary, style=MaterialTheme.typography.labelMedium)
        }
        "chips" -> FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            node.label?.let { Text(it, Modifier.padding(top=4.dp), color=colors.onSurfaceVariant, style=MaterialTheme.typography.labelMedium) }
            node.values.forEach { value -> Surface(color=colors.surfaceContainerHighest,shape=RoundedCornerShape(50)) {
                Text(value,Modifier.padding(horizontal=9.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
            } }
        }
        "text" -> {
            val value = (node.label?.let { "$it  " } ?: "") + node.value.orEmpty()
            when(node.style) {
                "headline" -> Column { Text(value,style=MaterialTheme.typography.headlineLarge,fontFamily=FontFamily.Serif);speech?.invoke() }
                "ipa" -> Text(value,style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant,
                    modifier=Modifier.background(colors.surfaceContainerHighest,RoundedCornerShape(50)).padding(horizontal=10.dp,vertical=6.dp))
                "grammar" -> Text(value,style=MaterialTheme.typography.labelMedium,color=colors.tertiary)
                "definition" -> Text(value,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.SemiBold)
                "quote" -> Row(Modifier.fillMaxWidth().padding(top=3.dp)) {
                    Box(Modifier.width(2.dp).height(32.dp).background(colors.primary.copy(alpha=.6f)))
                    Text(value,Modifier.padding(start=10.dp),fontFamily=FontFamily.Serif,fontStyle=FontStyle.Italic,fontSize=18.sp,lineHeight=26.sp,color=colors.onSurfaceVariant)
                }
                "etymology" -> Text(value,style=MaterialTheme.typography.bodyMedium,color=colors.secondary)
                "muted" -> Text(value,style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                else -> Text(value,style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
            }
        }
    }
}
