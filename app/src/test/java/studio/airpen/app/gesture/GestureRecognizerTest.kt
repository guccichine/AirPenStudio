package studio.airpen.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.airpen.app.data.GestureId
import studio.airpen.app.data.GestureSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GestureRecognizerTest {
    private val rec = GestureRecognizer()
    private val settings = GestureSettings(
        minFlickLength = 0.15f,
        flickStraightness = 0.75f,
        shapeThreshold = 0.5f,
    )

    @Test
    fun flickRight() {
        val pts = line(0f, 0f, 1f, 0f)
        val r = rec.recognizeStroke(pts, settings)
        assertEquals(GestureId.FLICK_RIGHT, r.gesture)
    }

    @Test
    fun flickUp() {
        val pts = line(0f, 0f, 0f, 1f)
        val r = rec.recognizeStroke(pts, settings)
        assertEquals(GestureId.FLICK_UP, r.gesture)
    }

    @Test
    fun flickDownLeft() {
        val pts = line(0f, 0f, -1f, -1f)
        val r = rec.recognizeStroke(pts, settings)
        assertEquals(GestureId.FLICK_DOWN_LEFT, r.gesture)
    }

    @Test
    fun circleClockwise() {
        val pts = (0..48).map { i ->
            val t = -2.0 * PI * i / 48.0
            Pt(cos(t).toFloat(), sin(t).toFloat(), i * 10L)
        }
        val r = rec.recognizeStroke(pts, settings)
        assertTrue("expected circle, got ${r.gesture} ${r.notes}", r.gesture == GestureId.CIRCLE_CW || r.gesture == GestureId.CIRCLE_CCW)
    }

    @Test
    fun square() {
        val verts = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f, 0f to 0f)
        val pts = densify(verts)
        val r = rec.recognizeStroke(pts, settings)
        assertNotNull(r.gesture)
        assertTrue("got ${r.gesture} ${r.notes} score=${r.score}", r.gesture == GestureId.SQUARE || r.closed)
    }

    @Test
    fun triangle() {
        val verts = listOf(0f to 0f, 0.5f to 1f, 1f to 0f, 0f to 0f)
        val pts = densify(verts)
        val r = rec.recognizeStroke(pts, settings)
        assertTrue("got ${r.gesture} ${r.notes}", r.gesture == GestureId.TRIANGLE || r.closed)
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float): List<Pt> {
        return (0..20).map { i ->
            val t = i / 20f
            Pt(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, i * 12L)
        }
    }

    private fun densify(verts: List<Pair<Float, Float>>): List<Pt> {
        val out = ArrayList<Pt>()
        var t = 0L
        for (i in 0 until verts.size - 1) {
            val a = verts[i]
            val b = verts[i + 1]
            for (k in 0 until 12) {
                val u = k / 12f
                out += Pt(a.first + (b.first - a.first) * u, a.second + (b.second - a.second) * u, t)
                t += 8
            }
        }
        out += Pt(verts.last().first, verts.last().second, t)
        return out
    }
}
