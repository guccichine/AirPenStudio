package studio.airpen.app.gesture

import studio.airpen.app.data.GestureSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/** One Euro Filter — keeps flicks sharp while killing S Pen IMU tremor. */
class OneEuro(
    private var minCutoff: Float = 1.2f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f,
) {
    private var xHat = 0f
    private var dxHat = 0f
    private var t = 0L
    private var primed = false

    fun setMinCutoff(v: Float) {
        minCutoff = v
    }

    fun reset() {
        primed = false
        xHat = 0f
        dxHat = 0f
        t = 0L
    }

    fun filter(x: Float, tMs: Long): Float {
        if (!primed) {
            primed = true
            xHat = x
            dxHat = 0f
            t = tMs
            return x
        }
        val dt = max(0.001f, (tMs - t) / 1000f)
        t = tMs
        val dx = (x - xHat) / dt
        val aD = alpha(dt, dCutoff)
        dxHat = aD * dx + (1 - aD) * dxHat
        val cutoff = minCutoff + beta * abs(dxHat)
        val a = alpha(dt, cutoff)
        xHat = a * x + (1 - a) * xHat
        return xHat
    }

    private fun alpha(dt: Float, cutoff: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }
}

class MotionFilter {
    private val fx = OneEuro()
    private val fy = OneEuro()
    private val idle = ArrayList<Float>(96)
    private var noiseFloor = 0f

    fun reset() {
        fx.reset()
        fy.reset()
    }

    fun noteIdle(mag: Float) {
        idle += mag
        if (idle.size > 90) idle.removeAt(0)
        if (idle.size >= 20) {
            val mean = idle.sum() / idle.size
            var v = 0f
            for (s in idle) v += (s - mean) * (s - mean)
            noiseFloor = mean + sqrt(v / idle.size) * 2.2f
        }
    }

    fun effectiveDeadZone(settings: GestureSettings): Float {
        if (!settings.adaptiveDeadZone) return settings.deadZone
        return max(settings.deadZone, noiseFloor * 1.15f)
    }

    fun step(dx: Float, dy: Float, t: Long, settings: GestureSettings, armed: Boolean): Pair<Float, Float>? {
        val x = dx * settings.gainX
        val y = dy * settings.gainY
        val mag = hypot(x.toDouble(), y.toDouble()).toFloat()
        val dz = effectiveDeadZone(settings)
        if (!armed && mag < dz) {
            noteIdle(mag)
            return null
        }
        val sx = 0.18f + settings.motionSmoothing * 1.6f
        fx.setMinCutoff(sx)
        fy.setMinCutoff(sx)
        val fxv = fx.filter(x, t)
        val fyv = fy.filter(y, t)
        val fmag = hypot(fxv.toDouble(), fyv.toDouble()).toFloat()
        if (!armed && fmag < dz) return null
        return fxv to fyv
    }
}

object StrokePrep {
    fun prepare(points: List<Pt>, settings: GestureSettings): List<Pt> {
        if (points.size < 4) return points
        val sm = Unistroke.smooth(points)
        val trimmed = trimSettle(sm, settings.settleTrim)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in trimmed) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        val eps = hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()).toFloat() *
            (0.012f + settings.motionSmoothing * 0.02f)
        val simplified = rdp(trimmed, eps)
        val out = if (simplified.size >= 6) simplified else trimmed
        val minLen = Unistroke.pathLength(out) * 0.004f
        val dedup = ArrayList<Pt>(out.size)
        dedup += out.first()
        for (i in 1 until out.size) {
            val last = dedup.last()
            val d = hypot((out[i].x - last.x).toDouble(), (out[i].y - last.y).toDouble()).toFloat()
            if (d >= minLen) dedup += out[i] else dedup[dedup.lastIndex] = last.copy(t = out[i].t)
        }
        return dedup
    }

    fun trimSettle(points: List<Pt>, frac: Float): List<Pt> {
        if (points.size < 8 || frac <= 0f) return points
        val speeds = FloatArray(points.size)
        for (i in 1 until points.size) {
            val dt = (points[i].t - points[i - 1].t).coerceAtLeast(1L).toFloat()
            speeds[i] = hypot(
                (points[i].x - points[i - 1].x).toDouble(),
                (points[i].y - points[i - 1].y).toDouble(),
            ).toFloat() / dt
        }
        var peak = 0f
        for (s in speeds) if (s > peak) peak = s
        val cut = peak * 0.22f
        var i0 = 0
        while (i0 < points.size * 0.22f && speeds[i0] < cut) i0++
        var i1 = points.size - 1
        while (i1 > points.size * 0.78f && speeds[i1] < cut) i1--
        val keep = points.subList(i0, i1 + 1)
        return if (keep.size >= max(6, (points.size * (1 - frac * 1.4f)).toInt())) keep else points
    }

    fun rdp(points: List<Pt>, epsilon: Float): List<Pt> {
        if (points.size < 3) return points
        var maxD = 0f
        var idx = 0
        val a = points.first()
        val b = points.last()
        val dx = b.x - a.x
        val dy = b.y - a.y
        val den = max(1e-8f, dx * dx + dy * dy)
        for (i in 1 until points.size - 1) {
            val p = points[i]
            val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / den
            val px = a.x + t * dx
            val py = a.y + t * dy
            val d = hypot((p.x - px).toDouble(), (p.y - py).toDouble()).toFloat()
            if (d > maxD) {
                maxD = d
                idx = i
            }
        }
        if (maxD > epsilon) {
            val left = rdp(points.subList(0, idx + 1), epsilon)
            val right = rdp(points.subList(idx, points.size), epsilon)
            return left.dropLast(1) + right
        }
        return listOf(a, b)
    }
}
