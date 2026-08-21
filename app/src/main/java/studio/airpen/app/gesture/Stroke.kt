package studio.airpen.app.gesture

import kotlin.math.hypot

data class Pt(val x: Float, val y: Float, val t: Long = 0L) {
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y, t)
    fun length() = hypot(x.toDouble(), y.toDouble()).toFloat()
}

class StrokeBuffer {
    private val pts = ArrayList<Pt>(128)

    val points: List<Pt> get() = pts
    val size: Int get() = pts.size
    val isEmpty: Boolean get() = pts.isEmpty()

    fun clear() = pts.clear()

    fun add(x: Float, y: Float, t: Long) {
        if (pts.isNotEmpty()) {
            val last = pts.last()
            if (x == last.x && y == last.y) {
                pts[pts.lastIndex] = last.copy(t = t)
                return
            }
        }
        pts += Pt(x, y, t)
    }

    fun snapshot(): List<Pt> = ArrayList(pts)

    fun durationMs(): Long {
        if (pts.size < 2) return 0
        return (pts.last().t - pts.first().t).coerceAtLeast(0)
    }

    fun pathLength(): Float {
        var s = 0f
        for (i in 1 until pts.size) s += hypot(
            (pts[i].x - pts[i - 1].x).toDouble(),
            (pts[i].y - pts[i - 1].y).toDouble(),
        ).toFloat()
        return s
    }

    fun endToEnd(): Float {
        if (pts.size < 2) return 0f
        val a = pts.first()
        val b = pts.last()
        return hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
    }
}
