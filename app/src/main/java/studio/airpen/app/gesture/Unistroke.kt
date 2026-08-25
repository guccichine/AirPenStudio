package studio.airpen.app.gesture

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Unistroke {
    const val N = 64
    const val SQUARE = 250f
    private val PHI = (0.5 * (-1 + sqrt(5.0))).toFloat()
    private val HALF_PI = (PI / 2.0).toFloat()

    data class Template(val name: String, val points: List<Pt>)

    fun resample(points: List<Pt>, n: Int = N): List<Pt> {
        if (points.size < 2) return points
        val interval = pathLength(points) / (n - 1)
        if (interval <= 1e-6f) return List(n) { points.first() }
        val out = ArrayList<Pt>(n)
        out += points.first()
        var dist = 0f
        val buf = ArrayList(points)
        var i = 1
        while (i < buf.size && out.size < n) {
            val d = distance(buf[i - 1], buf[i])
            if (dist + d >= interval) {
                val t = (interval - dist) / d
                val nx = buf[i - 1].x + t * (buf[i].x - buf[i - 1].x)
                val ny = buf[i - 1].y + t * (buf[i].y - buf[i - 1].y)
                val np = Pt(nx, ny, buf[i].t)
                out += np
                buf.add(i, np)
                dist = 0f
                i++
            } else {
                dist += d
                i++
            }
        }
        while (out.size < n) out += buf.last()
        return out
    }

    fun indicativeAngle(points: List<Pt>): Float {
        val c = centroid(points)
        return atan2(c.y - points[0].y, c.x - points[0].x)
    }

    fun rotateBy(points: List<Pt>, radians: Float): List<Pt> {
        val c = centroid(points)
        val cos = cos(radians)
        val sin = sin(radians)
        return points.map { p ->
            val dx = p.x - c.x
            val dy = p.y - c.y
            Pt((dx * cos - dy * sin + c.x), (dx * sin + dy * cos + c.y), p.t)
        }
    }

    fun scaleUniform(points: List<Pt>, size: Float = SQUARE): List<Pt> {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        val w = (maxX - minX).coerceAtLeast(1e-3f)
        val h = (maxY - minY).coerceAtLeast(1e-3f)
        val scale = size / max(w, h)
        val ox = (size - w * scale) * 0.5f
        val oy = (size - h * scale) * 0.5f
        return points.map { p ->
            Pt((p.x - minX) * scale + ox, (p.y - minY) * scale + oy, p.t)
        }
    }

    fun scaleToSquare(points: List<Pt>, size: Float = SQUARE): List<Pt> {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        val w = (maxX - minX).coerceAtLeast(1e-3f)
        val h = (maxY - minY).coerceAtLeast(1e-3f)
        return points.map { p ->
            Pt(((p.x - minX) / w) * size, ((p.y - minY) / h) * size, p.t)
        }
    }

    fun translateToOrigin(points: List<Pt>): List<Pt> {
        val c = centroid(points)
        return points.map { p -> Pt(p.x - c.x, p.y - c.y, p.t) }
    }

    fun normalize(points: List<Pt>): List<Pt> {
        val r = resample(points)
        val rotated = rotateBy(r, -indicativeAngle(r))
        return translateToOrigin(scaleToSquare(rotated))
    }

    fun normalizeKeepAspect(points: List<Pt>): List<Pt> {
        val r = resample(points)
        return translateToOrigin(scaleUniform(r))
    }

    fun recognize(
        points: List<Pt>,
        templates: List<Template>,
        angleRange: Float = 45f * (PI / 180f).toFloat(),
    ): Pair<Template, Float>? {
        if (points.size < 5 || templates.isEmpty()) return null
        val candidate = normalize(points)
        var best: Template? = null
        var bestDist = Float.POSITIVE_INFINITY
        for (t in templates) {
            val d = distanceAtBestAngle(candidate, t.points, -angleRange, angleRange)
            if (d < bestDist) {
                bestDist = d
                best = t
            }
        }
        best ?: return null
        val score = (1f - bestDist / (0.5f * sqrt((SQUARE * SQUARE * 2).toDouble()).toFloat()))
            .coerceIn(0f, 1f)
        return best to score
    }

    fun recognizeOriented(
        points: List<Pt>,
        templates: List<Template>,
        angleRange: Float = 14f * (PI / 180f).toFloat(),
    ): Pair<Template, Float>? {
        if (points.size < 5 || templates.isEmpty()) return null
        val candidate = normalizeKeepAspect(points)
        var best: Template? = null
        var bestDist = Float.POSITIVE_INFINITY
        for (t in templates) {
            val d = distanceAtBestAngle(candidate, t.points, -angleRange, angleRange)
            if (d < bestDist) {
                bestDist = d
                best = t
            }
        }
        best ?: return null
        val score = (1f - bestDist / (0.5f * sqrt((SQUARE * SQUARE * 2).toDouble()).toFloat()))
            .coerceIn(0f, 1f)
        return best to score
    }

    fun densify(verts: List<Pt>, per: Int): List<Pt> {
        if (verts.size < 2) return verts
        val out = ArrayList<Pt>()
        for (i in 0 until verts.size - 1) {
            val a = verts[i]
            val b = verts[i + 1]
            for (k in 0 until per) {
                val t = k / per.toFloat()
                out += Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.t)
            }
        }
        out += verts.last()
        return out
    }

    fun smooth(points: List<Pt>): List<Pt> {
        if (points.size < 5) return points
        val out = ArrayList<Pt>(points.size)
        out += points.first()
        for (i in 1 until points.size - 1) {
            val a = points[i - 1]
            val b = points[i]
            val c = points[i + 1]
            out += Pt(
                (a.x + b.x * 2f + c.x) / 4f,
                (a.y + b.y * 2f + c.y) / 4f,
                b.t,
            )
        }
        out += points.last()
        return out
    }

    fun flipY(points: List<Pt>) = points.map { p -> Pt(p.x, -p.y, p.t) }

    fun flipX(points: List<Pt>) = points.map { p -> Pt(-p.x, p.y, p.t) }

    fun recognizeCloud(
        points: List<Pt>,
        templates: List<Template>,
    ): Pair<Template, Float>? {
        if (points.size < 5 || templates.isEmpty()) return null
        val candidate = normalizeKeepAspect(resample(points, 32))
        var best: Template? = null
        var bestDist = Float.POSITIVE_INFINITY
        for (t in templates) {
            val tmpl = if (t.points.size == 32) t.points else normalizeKeepAspect(resample(t.points, 32))
            val d = min(cloudDistance(candidate, tmpl), cloudDistance(tmpl, candidate))
            if (d < bestDist) {
                bestDist = d
                best = t
            }
        }
        best ?: return null
        val score = (1f - bestDist / (0.5f * sqrt((SQUARE * SQUARE * 2).toDouble()).toFloat()))
            .coerceIn(0f, 1f)
        return best to score
    }

    private fun cloudDistance(a: List<Pt>, b: List<Pt>): Float {
        val n = min(a.size, b.size)
        if (n == 0) return Float.POSITIVE_INFINITY
        val used = BooleanArray(n)
        var sum = 0f
        for (i in 0 until n) {
            var best = Float.POSITIVE_INFINITY
            var bestJ = 0
            for (j in 0 until n) {
                if (used[j]) continue
                val d = distance(a[i], b[j])
                if (d < best) {
                    best = d
                    bestJ = j
                }
            }
            used[bestJ] = true
            sum += best
        }
        return sum / n
    }

    fun pathLength(points: List<Pt>): Float {
        var s = 0f
        for (i in 1 until points.size) s += distance(points[i - 1], points[i])
        return s
    }

    fun centroid(points: List<Pt>): Pt {
        var sx = 0f
        var sy = 0f
        for (p in points) {
            sx += p.x
            sy += p.y
        }
        val n = points.size.coerceAtLeast(1)
        return Pt(sx / n, sy / n)
    }

    fun distance(a: Pt, b: Pt) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    fun boundingAspect(points: List<Pt>): Float {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        val w = (maxX - minX).coerceAtLeast(1e-3f)
        val h = (maxY - minY).coerceAtLeast(1e-3f)
        return min(w, h) / max(w, h)
    }

    fun isClosed(points: List<Pt>, frac: Float = 0.28f): Boolean {
        if (points.size < 6) return false
        val len = pathLength(points).coerceAtLeast(1e-3f)
        return distance(points.first(), points.last()) / len < frac
    }

    fun signedArea(points: List<Pt>): Float {
        if (points.size < 3) return 0f
        var a = 0f
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            a += p.x * q.y - q.x * p.y
        }
        return a / 2f
    }

    fun circularity(points: List<Pt>): Float {
        val peri = pathLength(points) + distance(points.last(), points.first())
        val area = abs(signedArea(points))
        if (peri <= 1e-3f) return 0f
        return ((4f * Math.PI.toFloat() * area) / (peri * peri)).coerceIn(0f, 1.2f)
    }

    fun heading(points: List<Pt>): Float {
        if (points.size < 2) return 0f
        val a = points.first()
        val b = points.last()
        return atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()).toFloat()
    }

    fun finishHeading(points: List<Pt>): Float {
        if (points.size < 6) return heading(points)
        val from = (points.size * 0.4f).toInt().coerceIn(1, points.size - 2)
        return heading(points.subList(from, points.size))
    }

    fun windingTurns(points: List<Pt>): Float {
        if (points.size < 6) return 0f
        var ang = 0.0
        for (i in 1 until points.size - 1) {
            val a = points[i - 1]
            val b = points[i]
            val c = points[i + 1]
            val a1 = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
            val a2 = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble())
            var d = a2 - a1
            while (d > PI) d -= 2 * PI
            while (d < -PI) d += 2 * PI
            ang += d
        }
        return (ang / (2 * PI)).toFloat()
    }

    private fun distanceAtBestAngle(
        pts: List<Pt>,
        template: List<Pt>,
        from: Float,
        to: Float,
        precision: Float = 2f * (PI / 180f).toFloat(),
    ): Float {
        var a = from
        var b = to
        var x1 = PHI * a + (1 - PHI) * b
        var f1 = pathDistance(rotateBy(pts, x1), template)
        var x2 = (1 - PHI) * a + PHI * b
        var f2 = pathDistance(rotateBy(pts, x2), template)
        while (abs(b - a) > precision) {
            if (f1 < f2) {
                b = x2
                x2 = x1
                f2 = f1
                x1 = PHI * a + (1 - PHI) * b
                f1 = pathDistance(rotateBy(pts, x1), template)
            } else {
                a = x1
                x1 = x2
                f1 = f2
                x2 = (1 - PHI) * a + PHI * b
                f2 = pathDistance(rotateBy(pts, x2), template)
            }
        }
        return min(f1, f2)
    }

    private fun pathDistance(a: List<Pt>, b: List<Pt>): Float {
        val n = min(a.size, b.size)
        if (n == 0) return Float.POSITIVE_INFINITY
        var d = 0f
        for (i in 0 until n) d += distance(a[i], b[i])
        return d / n
    }

    @Suppress("unused")
    private val unusedHalfPi = HALF_PI
}
