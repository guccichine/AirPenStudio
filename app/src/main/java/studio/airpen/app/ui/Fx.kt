package studio.airpen.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import studio.airpen.app.data.BoundAction
import studio.airpen.app.data.GestureId
import studio.airpen.app.data.TrailPrefs
import studio.airpen.app.data.TrailStyle
import studio.airpen.app.ui.theme.Gold
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun Modifier.goldHoverGlow(): Modifier {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val hot = hovered || pressed
    return this
        .hoverable(source)
        .shadow(
            elevation = if (hot) 18.dp else 0.dp,
            shape = RoundedCornerShape(14.dp),
            ambientColor = Gold,
            spotColor = Gold,
        )
        .border(
            width = if (hot) 1.5.dp else 0.dp,
            color = if (hot) Gold.copy(alpha = 0.85f) else Color.Transparent,
            shape = RoundedCornerShape(14.dp),
        )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrailPicker() {
    Text("Cursor glow trail", fontWeight = FontWeight.Medium)
    Text("Pick a style. Air mouse draws this trail across any app.", fontSize = 13.sp)
    FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        TrailStyle.entries.forEach { s ->
            FilterChip(
                selected = TrailPrefs.style == s.id,
                onClick = {
                    TrailPrefs.style = s.id
                    TrailPrefs.show = s != TrailStyle.OFF
                },
                modifier = Modifier.goldHoverGlow(),
                label = { Text(s.label) },
            )
        }
    }
    Text("Trail thickness")
    Slider(value = TrailPrefs.thickness, onValueChange = { TrailPrefs.thickness = it }, valueRange = 0.4f..2.2f)
    Text("Trail length")
    Slider(value = TrailPrefs.length, onValueChange = { TrailPrefs.length = it }, valueRange = 0.4f..2f)
    Text("Trail brightness")
    Slider(value = TrailPrefs.intensity, onValueChange = { TrailPrefs.intensity = it }, valueRange = 0.3f..1.8f)
}

@Composable
fun PeekCard(title: String, body: String, clip: GestureId? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .shadow(20.dp, RoundedCornerShape(16.dp), ambientColor = Gold, spotColor = Gold),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Gold)
            Text(body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (clip != null) {
                Spacer(Modifier.height(8.dp))
                GestureClip(clip, Modifier.fillMaxWidth().height(92.dp))
            }
        }
    }
}

@Composable
fun GestureClip(id: GestureId, modifier: Modifier = Modifier) {
    val tick by rememberInfiniteTransition(label = "clip").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val pts = remember(id) { clipPath(id) }
    Canvas(modifier.background(Color(0xFF101217), RoundedCornerShape(12.dp)).padding(8.dp)) {
        if (pts.size < 2) return@Canvas
        val shown = (pts.size * tick).toInt().coerceIn(2, pts.size)
        val minX = pts.minOf { it.first }
        val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }
        val maxY = pts.maxOf { it.second }
        val sx = size.width / (maxX - minX).coerceAtLeast(0.001f)
        val sy = size.height / (maxY - minY).coerceAtLeast(0.001f)
        val s = min(sx, sy) * 0.82f
        val ox = (size.width - (maxX - minX) * s) / 2f - minX * s
        val oy = (size.height - (maxY - minY) * s) / 2f - minY * s
        val path = Path()
        path.moveTo(pts[0].first * s + ox, pts[0].second * s + oy)
        for (i in 1 until shown) path.lineTo(pts[i].first * s + ox, pts[i].second * s + oy)
        drawPath(path, Gold, style = Stroke(width = 6f, cap = StrokeCap.Round))
        val last = pts[shown - 1]
        drawCircle(Gold, 5f, Offset(last.first * s + ox, last.second * s + oy))
    }
}

fun clipPath(id: GestureId): List<Pair<Float, Float>> {
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 16) =
        (0..n).map { i -> val t = i / n.toFloat(); x0 + (x1 - x0) * t to y0 + (y1 - y0) * t }
    return when (id) {
        GestureId.FLICK_UP -> line(0.5f, 1f, 0.5f, 0f)
        GestureId.FLICK_DOWN -> line(0.5f, 0f, 0.5f, 1f)
        GestureId.FLICK_LEFT -> line(1f, 0.5f, 0f, 0.5f)
        GestureId.FLICK_RIGHT -> line(0f, 0.5f, 1f, 0.5f)
        GestureId.CIRCLE_CW, GestureId.CIRCLE_CCW -> {
            val dir = if (id == GestureId.CIRCLE_CW) -1 else 1
            (0..36).map { i ->
                val t = dir * 2.0 * Math.PI * i / 36.0
                (0.5 + 0.38 * cos(t)).toFloat() to (0.5 + 0.38 * sin(t)).toFloat()
            }
        }
        GestureId.CHECK -> line(0f, 0.55f, 0.35f, 0.9f, 8) + line(0.35f, 0.9f, 1f, 0.15f, 12)
        GestureId.CROSS -> line(0.1f, 0.1f, 0.9f, 0.9f, 10) + line(0.9f, 0.1f, 0.1f, 0.9f, 10)
        GestureId.HEART -> (0..28).map { i ->
            val t = Math.PI - i / 28.0 * Math.PI
            val x = 16 * sin(t).let { it * it * it } / 18.0
            val y = (13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)) / 18.0
            (0.5 + x).toFloat() to (0.42 - y).toFloat()
        }
        else -> line(0.15f, 0.5f, 0.85f, 0.5f)
    }
}

fun destCopy(action: BoundAction): String {
    val extra = action.arg.trim().let { if (it.isBlank()) "" else "\n$it" }
    return "Opens / runs: ${action.id.label}$extra"
}
