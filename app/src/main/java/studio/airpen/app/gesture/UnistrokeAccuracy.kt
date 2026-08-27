package studio.airpen.app.gesture

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

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
