package studio.airpen.app.gesture

import studio.airpen.app.data.GestureId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Templates {
    data class Named(val id: GestureId, val template: Unistroke.Template)

    val shapes: List<Named> by lazy { buildShapes() }
    val letters: List<Unistroke.Template> by lazy { buildLetters() }
    val digits: List<Unistroke.Template> by lazy { buildDigits() }

    val allType: List<Unistroke.Template> by lazy { letters + digits + extraType() }

    private fun buildShapes(): List<Named> {
        val out = ArrayList<Named>()
        fun add(id: GestureId, name: String, raw: List<Pt>) {
            out += Named(id, Unistroke.Template(name, Unistroke.normalize(raw)))
        }
        add(GestureId.CIRCLE_CW, "circle_cw", circle(true))
        add(GestureId.CIRCLE_CCW, "circle_ccw", circle(false))
        add(GestureId.SQUARE, "square", square())
        add(GestureId.TRIANGLE, "triangle", triangle())
        add(GestureId.ZIGZAG, "zigzag", zigzag())
        add(GestureId.CHECK, "check", check())
        add(GestureId.CROSS, "cross", cross())
        add(GestureId.PLUS, "plus", plus())
        add(GestureId.HEART, "heart", heart())
        add(GestureId.INFINITY, "infinity", infinity())
        add(GestureId.ARROW, "arrow", arrow())
        add(GestureId.CARET, "caret", caret())
        add(GestureId.STAR, "star", star())
        add(GestureId.SPIRAL, "spiral", spiral())
        add(GestureId.PIGTAIL, "pigtail", pigtail())
        add(GestureId.BRACKET_LEFT, "bracket_l", bracket(true))
        add(GestureId.BRACKET_RIGHT, "bracket_r", bracket(false))
        add(GestureId.WAVE, "wave", wave())
        add(GestureId.DIAMOND, "diamond", diamond())
        add(GestureId.HOOK, "hook", hook())
        add(GestureId.L_SHAPE, "l_shape", lShape())
        add(GestureId.U_SHAPE, "u_shape", uShape())
        add(GestureId.LIGHTNING, "lightning", lightning())
        add(GestureId.SEMICIRCLE, "semicircle", semicircle())
        add(GestureId.QUESTION, "question", question())
        return out
    }

    private fun circle(cw: Boolean): List<Pt> {
        val n = 72
        return (0..n).map { i ->
            val t = (if (cw) -1 else 1) * 2.0 * PI * i / n
            Pt(cos(t).toFloat(), sin(t).toFloat())
        }
    }

    private fun square(): List<Pt> {
        val s = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f, 0f to 0f)
        return densify(s, 12)
    }

    private fun triangle(): List<Pt> {
        val s = listOf(0f to 0f, 0.5f to 1f, 1f to 0f, 0f to 0f)
        return densify(s, 16)
    }

    private fun zigzag(): List<Pt> {
        val s = listOf(0f to 0.2f, 0.25f to 0.8f, 0.5f to 0.2f, 0.75f to 0.8f, 1f to 0.2f)
        return densify(s, 10)
    }

    private fun check(): List<Pt> {
        val s = listOf(0f to 0.55f, 0.35f to 0.05f, 1f to 1f)
        return densify(s, 16)
    }

    private fun cross(): List<Pt> {
        val s = listOf(0f to 1f, 1f to 0f, 0.5f to 0.5f, 0f to 0f, 1f to 1f)
        return densify(s, 8)
    }

    private fun plus(): List<Pt> {
        val s = listOf(0.5f to 0f, 0.5f to 1f, 0.5f to 0.5f, 0f to 0.5f, 1f to 0.5f)
        return densify(s, 8)
    }

    private fun heart(): List<Pt> {
        return (0..64).map { i ->
            val t = PI - i * (2 * PI) / 64
            val x = 16 * sin(t).let { it * it * it }
            val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
            Pt((x / 16.0).toFloat(), (y / 16.0).toFloat())
        }
    }

    private fun infinity(): List<Pt> {
        return (0..72).map { i ->
            val t = i * 2.0 * PI / 72
            val s = sin(t)
            val c = cos(t)
            val d = 1 + s * s
            Pt((c / d).toFloat(), (s * c / d).toFloat())
        }
    }

    private fun arrow(): List<Pt> {
        val s = listOf(0f to 0.5f, 1f to 0.5f, 0.65f to 0.85f, 1f to 0.5f, 0.65f to 0.15f)
        return densify(s, 10)
    }

    private fun caret(): List<Pt> {
        val s = listOf(0f to 0.1f, 0.5f to 0.9f, 1f to 0.1f)
        return densify(s, 16)
    }

    private fun star(): List<Pt> {
        val pts = ArrayList<Pt>()
        for (i in 0..5) {
            val t = -PI / 2 + i * 4.0 * PI / 5
            pts += Pt(cos(t).toFloat(), sin(t).toFloat())
        }
        return densifyPairs(pts, 8)
    }

    private fun spiral(): List<Pt> {
        return (0..80).map { i ->
            val t = i / 80.0 * 3.2 * PI
            val r = 0.15 + 0.85 * i / 80.0
            Pt((r * cos(t)).toFloat(), (r * sin(t)).toFloat())
        }
    }

    private fun pigtail(): List<Pt> {
        val s = ArrayList<Pt>()
        s += densify(listOf(0f to 0.2f, 0.7f to 0.2f), 10)
        s += circle(true).map { Pt(0.7f + it.x * 0.25f, 0.45f + it.y * 0.25f) }
        return s
    }

    private fun bracket(left: Boolean): List<Pt> {
        val x0 = if (left) 0.8f else 0.2f
        val x1 = if (left) 0.2f else 0.8f
        return densify(listOf(x0 to 1f, x1 to 1f, x1 to 0f, x0 to 0f), 12)
    }

    private fun wave(): List<Pt> {
        val s = listOf(0f to 0.5f, 0.2f to 1f, 0.4f to 0.5f, 0.6f to 0f, 0.8f to 0.5f, 1f to 1f)
        return densify(s, 10)
    }

    private fun diamond(): List<Pt> {
        val s = listOf(0.5f to 1f, 1f to 0.5f, 0.5f to 0f, 0f to 0.5f, 0.5f to 1f)
        return densify(s, 12)
    }

    private fun hook(): List<Pt> {
        val s = listOf(0.15f to 1f, 0.15f to 0.2f, 0.45f to 0f, 0.8f to 0.25f)
        return densify(s, 12)
    }

    private fun lShape(): List<Pt> {
        return densify(listOf(0f to 1f, 0f to 0f, 1f to 0f), 16)
    }

    private fun uShape(): List<Pt> {
        return densify(listOf(0f to 1f, 0f to 0.1f, 0.5f to 0f, 1f to 0.1f, 1f to 1f), 12)
    }

    private fun lightning(): List<Pt> {
        return densify(listOf(0.65f to 1f, 0.2f to 0.52f, 0.72f to 0.48f, 0.28f to 0f), 10)
    }

    private fun semicircle(): List<Pt> {
        val n = 36
        return (0..n).map { i ->
            val t = PI * i / n
            Pt((-cos(t)).toFloat(), sin(t).toFloat())
        }
    }

    private fun question(): List<Pt> {
        return densify(listOf(0.1f to 0.8f, 0.5f to 1f, 0.9f to 0.8f, 0.5f to 0.45f, 0.5f to 0.25f), 12)
    }

    private fun buildLetters(): List<Unistroke.Template> {
        val paths = mapOf(
            "a" to "0,0 0.5,1 1,0 0.75,0.4 0.25,0.4",
            "b" to "0,1 0,0 0.7,0.15 0.15,0.5 0.75,0.7 0,1",
            "c" to "1,0.8 0.2,1 0,0.5 0.2,0 1,0.2",
            "d" to "0,0 0,1 0.7,0.8 0.7,0.2 0,0",
            "e" to "1,0.55 0,0.55 0.15,1 0.9,0.95 0.95,0 0.1,0.1",
            "f" to "1,1 0,1 0,0 0,0.55 0.7,0.55",
            "g" to "0.9,0.7 0.2,1 0,0.5 0.25,0 0.9,0.15 0.9,0.45 0.5,0.45",
            "h" to "0,1 0,0 0,0.55 1,0.55 1,0",
            "i" to "0.5,1 0.5,0",
            "j" to "0.7,1 0.7,0.15 0.4,0 0,0.2",
            "k" to "0,1 0,0 0,0.5 1,1 0,0.5 1,0",
            "l" to "0,1 0,0 1,0",
            "m" to "0,0 0,1 0.5,0.45 1,1 1,0",
            "n" to "0,1 0,0 1,1 1,0",
            "o" to "0.5,1 0,0.5 0.5,0 1,0.5 0.5,1",
            "p" to "0,0 0,1 0.75,0.85 0.75,0.55 0,0.45",
            "q" to "0.5,1 0,0.5 0.5,0 1,0.5 0.5,1 0.8,0.2 1,0",
            "r" to "0,0 0,1 0.7,0.85 0.15,0.5 1,0",
            "s" to "1,0.85 0.2,1 0,0.7 1,0.35 0.2,0",
            "t" to "0,1 1,1 0.5,1 0.5,0",
            "u" to "0,1 0,0.15 0.5,0 1,0.15 1,1",
            "v" to "0,1 0.5,0 1,1",
            "w" to "0,1 0.25,0 0.5,0.55 0.75,0 1,1",
            "x" to "0,1 1,0 0.5,0.5 0,0 1,1",
            "y" to "0,1 0.5,0.45 1,1 0.5,0.45 0.5,0",
            "z" to "0,1 1,1 0,0 1,0",
        )
        return paths.map { (name, spec) ->
            Unistroke.Template(name, Unistroke.normalizeKeepAspect(parse(spec)))
        }
    }

    private fun buildDigits(): List<Unistroke.Template> {
        val paths = mapOf(
            "0" to "0.5,1 0,0.5 0.5,0 1,0.5 0.5,1",
            "1" to "0.2,0.75 0.5,1 0.5,0",
            "2" to "0.1,0.8 0.5,1 0.95,0.75 0.15,0 1,0",
            "3" to "0.1,1 1,0.75 0.3,0.5 1,0.25 0.1,0",
            "4" to "0.7,1 0,0.4 1,0.4 0.7,0.4 0.7,0",
            "5" to "1,1 0,1 0,0.55 1,0.5 0.8,0 0.1,0.1",
            "6" to "0.8,1 0.1,0.6 0.5,0 1,0.3 0.2,0.45",
            "7" to "0,1 1,1 0.3,0",
            "8" to "0.5,0.5 0,0.8 0.5,1 1,0.8 0.5,0.5 1,0.2 0.5,0 0,0.2 0.5,0.5",
            "9" to "0.2,0.4 0.5,1 1,0.6 0.2,0.55 0.7,0",
        )
        return paths.map { (name, spec) ->
            Unistroke.Template(name, Unistroke.normalizeKeepAspect(parse(spec)))
        }
    }

    private fun extraType(): List<Unistroke.Template> {
        val paths = mapOf(
            "." to "0.5,0.1 0.52,0.12",
            "," to "0.5,0.2 0.4,0",
            "?" to "0.1,0.8 0.5,1 0.9,0.8 0.5,0.45 0.5,0.25",
            "!" to "0.5,1 0.5,0.3",
            "-" to "0,0.5 1,0.5",
            "_" to "0,0 1,0",
            "/" to "0,0 1,1",
            "@" to "0.7,0.5 0.3,0.5 0.3,0.2 0.7,0.2 0.8,0.8 0.2,0.9 0.1,0.4",
        )
        return paths.map { (name, spec) ->
            Unistroke.Template(name, Unistroke.normalizeKeepAspect(parse(spec)))
        }
    }

    private fun parse(spec: String): List<Pt> {
        val verts = spec.trim().split(Regex("\\s+")).map { pair ->
            val (x, y) = pair.split(",")
            Pt(x.toFloat(), y.toFloat())
        }
        return densifyPairs(verts, 10)
    }

    private fun densify(verts: List<Pair<Float, Float>>, per: Int): List<Pt> {
        return densifyPairs(verts.map { Pt(it.first, it.second) }, per)
    }

    private fun densifyPairs(verts: List<Pt>, per: Int): List<Pt> {
        if (verts.size < 2) return verts
        val out = ArrayList<Pt>()
        for (i in 0 until verts.size - 1) {
            val a = verts[i]
            val b = verts[i + 1]
            for (k in 0 until per) {
                val t = k / per.toFloat()
                out += Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        out += verts.last()
        return out
    }
}
