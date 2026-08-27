package studio.airpen.app.gesture

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

fun Unistroke.velocityVector(points: List<Pt>): Pt {
    var sx = 0f
    var sy = 0f
    for (i in 1 until points.size) {
        val dx = points[i].x - points[i - 1].x
        val dy = points[i].y - points[i - 1].y
        val dt = (points[i].t - points[i - 1].t).coerceAtLeast(1L).toFloat()
        val speed = hypot(dx.toDouble(), dy.toDouble()).toFloat() / dt
        val w = speed * speed
        sx += dx * w
        sy += dy * w
    }
    if (sx == 0f && sy == 0f && points.size >= 2) {
        val a = points.first()
        val b = points.last()
        return Pt(b.x - a.x, b.y - a.y)
    }
    return Pt(sx, sy)
}

fun Unistroke.velocityHeading(points: List<Pt>): Float {
    val v = velocityVector(points)
    return atan2(v.y.toDouble(), v.x.toDouble()).toFloat()
}

fun Unistroke.ballistic(points: List<Pt>): Float {
    if (points.size < 3) return 1f
    val speeds = ArrayList<Float>(points.size)
    for (i in 1 until points.size) {
        val dt = (points[i].t - points[i - 1].t).coerceAtLeast(1L).toFloat()
        speeds += hypot(
            (points[i].x - points[i - 1].x).toDouble(),
            (points[i].y - points[i - 1].y).toDouble(),
        ).toFloat() / dt
    }
    val peak = speeds.maxOrNull() ?: 0f
    val mean = speeds.sum() / speeds.size.coerceAtLeast(1)
    return if (mean <= 1e-8f) 1f else peak / mean
}

/** Keep the high-speed core of an air flick; drop settle-in and post-flick droop. */
fun Unistroke.ballisticWindow(points: List<Pt>): List<Pt> {
    if (points.size < 6) return points
    val speeds = FloatArray(points.size)
    var peak = 0f
    for (i in 1 until points.size) {
        val dt = (points[i].t - points[i - 1].t).coerceAtLeast(1L).toFloat()
        speeds[i] = hypot(
            (points[i].x - points[i - 1].x).toDouble(),
            (points[i].y - points[i - 1].y).toDouble(),
        ).toFloat() / dt
        if (speeds[i] > peak) peak = speeds[i]
    }
    if (peak <= 1e-8f) return points
    val cut = peak * 0.40f
    var i0 = 1
    while (i0 < points.size - 1 && speeds[i0] < cut) i0++
    var i1 = points.size - 1
    while (i1 > i0 && speeds[i1] < cut) i1--
    i0 = (i0 - 1).coerceAtLeast(0)
    i1 = (i1 + 1).coerceAtMost(points.size - 1)
    return if (i1 - i0 + 1 >= 4) points.subList(i0, i1 + 1) else points
}

fun Unistroke.cornerCount(points: List<Pt>, minDeg: Float = 52f): Int {
    if (points.size < 5) return 0
    val minCos = cos((minDeg * PI / 180.0).toFloat())
    val len = pathLength(points)
    val minSeg = len * 0.04f
    var corners = 0
    var last = -8
    val step = max(1, points.size / 28)
    var i = step
    while (i < points.size - step) {
        val a = points[i - step]
        val b = points[i]
        val c = points[i + step]
        val d1x = b.x - a.x
        val d1y = b.y - a.y
        val d2x = c.x - b.x
        val d2y = c.y - b.y
        val m1 = hypot(d1x.toDouble(), d1y.toDouble()).toFloat()
        val m2 = hypot(d2x.toDouble(), d2y.toDouble()).toFloat()
        if (m1 >= minSeg && m2 >= minSeg) {
            val cosang = (d1x * d2x + d1y * d2y) / (m1 * m2)
            if (cosang < minCos && i - last > step) {
                corners++
                last = i
            }
        }
        i++
    }
    return corners
}

/**
 * True L / hook / check: two substantial arms meeting at a real corner.
 * Noisy flicks and 180° reverse-settles do not match.
 */
fun Unistroke.bentStroke(points: List<Pt>): Boolean {
    if (points.size < 8) return false
    val len = pathLength(points)
    if (len <= 1e-4f) return false
    val step = max(1, points.size / 22)
    var bestI = -1
    var bestCos = 1f
    var i = step
    while (i < points.size - step) {
        val a = points[i - step]
        val b = points[i]
        val c = points[i + step]
        val d1x = b.x - a.x
        val d1y = b.y - a.y
        val d2x = c.x - b.x
        val d2y = c.y - b.y
        val m1 = hypot(d1x.toDouble(), d1y.toDouble()).toFloat()
        val m2 = hypot(d2x.toDouble(), d2y.toDouble()).toFloat()
        if (m1 >= len * 0.16f && m2 >= len * 0.16f) {
            val cosang = ((d1x * d2x + d1y * d2y) / (m1 * m2)).coerceIn(-1f, 1f)
            if (cosang < bestCos) {
                bestCos = cosang
                bestI = i
            }
        }
        i++
    }
    if (bestI < 0) return false
    val frac = bestI.toFloat() / (points.size - 1).coerceAtLeast(1)
    if (frac < 0.18f || frac > 0.82f) return false
    val deg = acos(bestCos.toDouble()) * 180.0 / PI
    return deg in 52.0..128.0
}

object GestureRanker {
    fun ranked(
        points: List<Pt>,
        templates: List<Unistroke.Template>,
    ): List<Pair<Unistroke.Template, Float>> {
        if (points.size < 5 || templates.isEmpty()) return emptyList()
        return templates.map { t ->
            val dollar = Unistroke.recognize(points, listOf(t))
            val cloud = Unistroke.recognizeCloud(points, listOf(t))
            val score = max(dollar?.second ?: 0f, (cloud?.second ?: 0f) * 1.03f)
            t to score
        }.sortedByDescending { it.second }
    }
}
