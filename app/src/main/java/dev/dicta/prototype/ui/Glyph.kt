package dev.dicta.prototype.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

@Composable
fun Glyph(name: String, modifier: Modifier = Modifier, description: String? = null) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(24.dp).semantics { if (description != null) contentDescription = description }) {
        val k = size.width / 24f
        fun line(x:Float,y:Float,a:Float,b:Float) = drawLine(color, Offset(x*k,y*k),Offset(a*k,b*k),2*k)
        when(name) {
            "search" -> { drawCircle(color,6*k,Offset(10*k,10*k),style=Stroke(2*k)); line(15f,15f,21f,21f) }
            "book" -> { val p=Path().apply { moveTo(12*k,6*k); cubicTo(8*k,3*k,4*k,4*k,3*k,5*k); lineTo(3*k,20*k); cubicTo(6*k,18*k,9*k,18*k,12*k,20*k); cubicTo(15*k,18*k,18*k,18*k,21*k,20*k); lineTo(21*k,5*k); cubicTo(18*k,3*k,15*k,4*k,12*k,6*k); lineTo(12*k,20*k) }; drawPath(p,color,style=Stroke(1.7f*k)) }
            "history" -> { drawCircle(color,8*k,Offset(12*k,12*k),style=Stroke(1.8f*k)); line(12f,7f,12f,12f); line(12f,12f,16f,14f) }
            "settings" -> { drawCircle(color,8*k,Offset(12*k,12*k),style=Stroke(2*k)); drawCircle(color,3*k,Offset(12*k,12*k),style=Stroke(2*k)); line(12f,1f,12f,4f);line(12f,20f,12f,23f);line(1f,12f,4f,12f);line(20f,12f,23f,12f) }
            "close" -> {line(6f,6f,18f,18f);line(6f,18f,18f,6f)}
            "arrow" -> {line(4f,12f,20f,12f);line(14f,6f,20f,12f);line(14f,18f,20f,12f)}
        }
    }
}
