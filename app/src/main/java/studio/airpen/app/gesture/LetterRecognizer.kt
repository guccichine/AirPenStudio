package studio.airpen.app.gesture

import studio.airpen.app.data.LetterSample
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Hybrid air-letter recognizer.
 *
 * $1 (oriented) + $P point-cloud + geometric features + English next-letter
 * boost + user-trained samples. Also tries Y/X flips because S Pen IMU
 * axes often do not match paper handwriting.
 */
object LetterRecognizer {
    data class Guess(val letter: String, val score: Float)

    fun ranked(
        raw: List<Pt>,
        userSamples: List<LetterSample>,
        prefix: String,
        minScore: Float,
    ): List<Guess> {
        if (raw.size < 5) return emptyList()
        val smoothed = Unistroke.smooth(raw)
        val variants = listOf(
            smoothed,
            Unistroke.flipY(smoothed),
            Unistroke.flipX(smoothed),
        )
        val templates = builtIns + userTemplates(userSamples)
        val acc = HashMap<String, Float>(48)
        for (variant in variants) {
            if (Unistroke.pathLength(variant) < 0.02f) continue
            val dollar = Unistroke.recognizeOriented(variant, templates, angleRange = 0.40f)
            val cloud = Unistroke.recognizeCloud(variant, templates)
            fun consider(name: String, score: Float) {
                if (score <= 0.08f) return
                acc[name] = max(acc[name] ?: 0f, score)
            }
            if (dollar != null) consider(dollar.first.name, dollar.second)
            if (cloud != null) consider(cloud.first.name, cloud.second * 1.04f)
            geometricHints(variant).forEach { consider(it.letter, it.score) }
        }
        if (acc.isEmpty()) return emptyList()
        val boosted = acc.map { (name, score) ->
            Guess(name, (score * languageBoost(prefix, name)).coerceIn(0f, 1f))
        }.sortedByDescending { it.score }
        val best = boosted.first()
        if (best.score < minScore) return boosted.take(3)
        return disambiguate(best, boosted, Unistroke.smooth(raw)).take(4)
    }

    fun best(
        raw: List<Pt>,
        userSamples: List<LetterSample>,
        prefix: String,
        minScore: Float,
    ): Guess? = ranked(raw, userSamples, prefix, minScore).firstOrNull()
        ?.takeIf { it.score >= minScore }

    private val builtIns: List<Unistroke.Template> by lazy {
        Templates.allType + extraVariants()
    }

    private fun userTemplates(samples: List<LetterSample>): List<Unistroke.Template> {
        if (samples.isEmpty()) return emptyList()
        return samples.mapNotNull { s ->
            if (s.x.size < 4 || s.x.size != s.y.size) return@mapNotNull null
            val pts = s.x.indices.map { i -> Pt(s.x[i], s.y[i]) }
            Unistroke.Template(s.letter.lowercase(), Unistroke.normalizeKeepAspect(pts))
        }
    }

    private fun extraVariants(): List<Unistroke.Template> {
        val paths = listOf(
            "a" to "0.85,0.55 0.45,0.7 0.2,0.35 0.5,0.05 0.9,0.3 0.85,0",
            "a" to "0,0 0.5,1 1,0",
            "b" to "0,1 0,0 0,0.7 0.7,0.85 0.15,0.5 0.75,0.2 0,0.1",
            "c" to "0.95,0.85 0.4,1 0.05,0.5 0.4,0 0.95,0.15",
            "d" to "1,1 1,0 1,0.15 0.2,0 0,0.5 0.25,1 1,0.85",
            "d" to "0.15,0 0.15,1 0.8,0.75 0.8,0.25 0.15,0.1",
            "e" to "0.15,0.5 0.9,0.55 0.7,0.95 0.15,0.8 0.2,0.15 0.9,0.2",
            "e" to "1,0.5 0,0.5 0.2,1 1,1 1,0 0,0",
            "f" to "0.2,0 0.2,0.85 0.7,1 0.2,0.55 0.7,0.55",
            "g" to "0.85,0.75 0.3,1 0.05,0.5 0.4,0.15 0.9,0.35 0.9,0 0.4,-0.15",
            "h" to "0,1 0,0 0,0.5 0.9,0.55 0.9,0",
            "i" to "0.5,0.85 0.5,0",
            "i" to "0.5,1 0.5,0.15",
            "j" to "0.65,0.95 0.65,0.2 0.35,0 0.05,0.2",
            "k" to "0,1 0,0 0,0.45 0.9,0.95 0,0.45 0.9,0",
            "l" to "0.35,1 0.35,0",
            "l" to "0.2,1 0.2,0.05 0.85,0.05",
            "m" to "0,0 0,1 0.5,0.4 1,1 1,0",
            "n" to "0,0 0,1 1,0 1,1",
            "n" to "0,1 0,0.15 0.15,0.7 1,0.15",
            "o" to "0.5,1 0.05,0.5 0.5,0 0.95,0.5 0.5,1",
            "o" to "0.2,0.2 0.2,0.8 0.8,0.8 0.8,0.2 0.2,0.2",
            "p" to "0,1 0,-0.1 0,0.9 0.75,0.85 0.75,0.5 0,0.45",
            "q" to "0.5,1 0,0.5 0.5,0 1,0.5 0.5,1 0.85,-0.1",
            "r" to "0,0 0,1 0.25,0.7 0.85,0.95",
            "r" to "0,0 0,1 0.8,0.85 0.2,0.5 1,0",
            "s" to "0.9,0.9 0.2,1 0,0.65 0.9,0.4 0.15,0",
            "s" to "1,0.8 0,0.8 0,0.5 1,0.5 1,0.2 0,0.2",
            "t" to "0.5,1 0.5,0 0.15,0.7 0.85,0.7",
            "t" to "0,1 1,1 0.5,1 0.5,0",
            "u" to "0,1 0.05,0.2 0.5,0 0.95,0.2 1,1",
            "v" to "0,1 0.5,0.05 1,1",
            "w" to "0,1 0.2,0 0.5,0.55 0.8,0 1,1",
            "x" to "0,1 1,0",
            "x" to "0,0 1,1",
            "y" to "0,1 0.5,0.4 1,1 0.5,0.4 0.45,-0.05",
            "z" to "0.05,1 0.95,1 0.05,0 0.95,0",
            "0" to "0.5,1 0,0.5 0.5,0 1,0.5 0.5,1",
            "1" to "0.5,1 0.5,0",
            "2" to "0.1,0.85 0.5,1 0.95,0.7 0.15,0 1,0",
            "3" to "0.15,1 0.95,0.75 0.35,0.5 0.95,0.25 0.15,0",
            "4" to "0.75,1 0.1,0.4 1,0.4 0.7,0.4 0.7,0",
            "5" to "0.9,1 0.1,1 0.1,0.55 0.9,0.5 0.7,0 0.15,0.1",
            "6" to "0.85,0.95 0.15,0.55 0.45,0 0.95,0.25 0.2,0.4",
            "7" to "0.05,1 0.95,1 0.35,0",
            "8" to "0.5,0.5 0.1,0.8 0.5,1 0.9,0.8 0.5,0.5 0.9,0.2 0.5,0 0.1,0.2 0.5,0.5",
            "9" to "0.2,0.45 0.5,1 0.95,0.6 0.2,0.5 0.65,0",
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
        return Unistroke.densify(verts, 8)
    }

    private fun geometricHints(points: List<Pt>): List<Guess> {
        val f = features(points)
        val out = ArrayList<Guess>(6)
        if (f.closed && f.circ >= 0.62f) {
            out += Guess("o", 0.72f + 0.15f * f.circ)
            if (f.tall) out += Guess("0", 0.64f)
        }
        if (f.straight >= 0.90f && f.tall) {
            out += Guess("l", 0.70f)
            out += Guess("i", 0.62f)
            out += Guess("1", 0.58f)
        }
        if (f.corners >= 3 && f.wide && f.startTop && f.endBottom) {
            out += Guess("z", 0.66f)
            out += Guess("2", 0.58f)
        }
        if (f.corners <= 1 && f.wide && f.straight >= 0.88f) {
            out += Guess("-", 0.7f)
        }
        if (!f.closed && f.startTop && f.endTop && f.turns in 0.4f..1.3f) {
            out += Guess("n", 0.55f)
            out += Guess("h", 0.52f)
        }
        if (!f.closed && f.startTop && f.endTop && f.turns < 0.35f && f.bottomDip) {
            out += Guess("u", 0.62f)
            out += Guess("v", 0.55f)
        }
        if (f.peaks >= 2) {
            out += Guess("w", 0.60f)
            out += Guess("m", 0.52f)
        }
        if (f.ess) out += Guess("s", 0.64f)
        return out
    }

    private data class Feat(
        val closed: Boolean,
        val circ: Float,
        val straight: Float,
        val tall: Boolean,
        val wide: Boolean,
        val corners: Int,
        val turns: Float,
        val startTop: Boolean,
        val endTop: Boolean,
        val endBottom: Boolean,
        val bottomDip: Boolean,
        val peaks: Int,
        val ess: Boolean,
    )

    private fun features(points: List<Pt>): Feat {
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
        val len = Unistroke.pathLength(points).coerceAtLeast(1e-3f)
        val end = hypot(
            (points.last().x - points.first().x).toDouble(),
            (points.last().y - points.first().y).toDouble(),
        ).toFloat()
        val ny = { y: Float -> (y - minY) / h }
        val nx = { x: Float -> (x - minX) / w }
        var corners = 0
        var peaks = 0
        var lastPeak = false
        for (i in 2 until points.size - 2) {
            val a = points[i - 2]
            val b = points[i]
            val c = points[i + 2]
            val d1x = b.x - a.x
            val d1y = b.y - a.y
            val d2x = c.x - b.x
            val d2y = c.y - b.y
            val dot = d1x * d2x + d1y * d2y
            val m = (hypot(d1x.toDouble(), d1y.toDouble()) * hypot(d2x.toDouble(), d2y.toDouble())).toFloat().coerceAtLeast(1e-4f)
            if (dot / m < 0.15f) corners++
            val isPeak = b.y >= a.y && b.y >= c.y && ny(b.y) > 0.55f
            if (isPeak && !lastPeak) peaks++
            lastPeak = isPeak
        }
        val mid = points[points.size / 2]
        val ess = nx(points.first().x) > 0.55f && nx(points.last().x) < 0.45f && ny(mid.y) in 0.3f..0.7f
        val bottomDip = points.minBy { it.y }.let { ny(it.y) < 0.25f }
        return Feat(
            closed = Unistroke.isClosed(points),
            circ = Unistroke.circularity(points),
            straight = (end / len).coerceIn(0f, 1f),
            tall = h > w * 1.35f,
            wide = w > h * 1.25f,
            corners = corners,
            turns = abs(Unistroke.windingTurns(points)),
            startTop = ny(points.first().y) > 0.6f,
            endTop = ny(points.last().y) > 0.6f,
            endBottom = ny(points.last().y) < 0.35f,
            bottomDip = bottomDip,
            peaks = peaks,
            ess = ess,
        )
    }

    private fun disambiguate(best: Guess, ranked: List<Guess>, points: List<Pt>): List<Guess> {
        if (ranked.size < 2) return ranked
        val f = features(points)
        val names = ranked.map { it.letter }
        fun prefer(keep: String, drop: String): List<Guess> {
            if (keep !in names || drop !in names) return ranked
            return ranked.map {
                when (it.letter) {
                    keep -> it.copy(score = (it.score + 0.08f).coerceAtMost(1f))
                    drop -> it.copy(score = it.score * 0.86f)
                    else -> it
                }
            }.sortedByDescending { it.score }
        }
        var out = ranked
        if ("o" in names && "a" in names && f.closed) out = prefer("o", "a")
        if ("u" in names && "v" in names && f.bottomDip && !f.closed) out = prefer("u", "v")
        if ("l" in names && "i" in names && f.tall && f.straight > 0.88f) out = prefer("l", "i")
        if ("n" in names && "h" in names && f.startTop) out = prefer("n", "h")
        if ("s" in names && "5" in names && f.ess) out = prefer("s", "5")
        if ("z" in names && "2" in names && f.corners >= 3) out = prefer("z", "2")
        return listOf(out.first()) + out.drop(1)
    }

    private fun languageBoost(prefix: String, letter: String): Float {
        if (letter.length != 1) return 1f
        val ch = letter[0]
        if (!ch.isLetter()) return 1f
        if (prefix.isBlank()) return START[ch] ?: 1f
        val last = prefix.last().lowercaseChar()
        val row = BIGRAM[last] ?: return 1f
        val idx = row.indexOf(ch)
        return if (idx < 0) 0.90f else (1.16f - idx * 0.035f).coerceAtLeast(0.95f)
    }

    private val START = hashMapOf(
        't' to 1.16f, 'a' to 1.14f, 'i' to 1.12f, 's' to 1.12f, 'o' to 1.10f,
        'w' to 1.10f, 'c' to 1.08f, 'b' to 1.06f, 'h' to 1.06f, 'm' to 1.05f,
        'p' to 1.04f, 'f' to 1.04f, 'd' to 1.03f,
    )

    private val BIGRAM = hashMapOf(
        't' to "hieora",
        'h' to "eiao",
        'e' to "arnsdl",
        'a' to "ntrls",
        'i' to "ntso",
        'n' to "gdeto",
        's' to "tehia",
        'o' to "nurf",
        'r' to "eoaist",
        'l' to "eiyloa",
        'd' to "eioa",
        'c' to "oheak",
        'u' to "trns",
        'm' to "eaoi",
        'w' to "haoie",
        'f' to "oirae",
        'g' to "ehoa",
        'y' to " ouie",
        'b' to "euloya",
        'p' to "eolar",
        'k' to "eina",
        'v' to "eia",
        'q' to "u",
        'x' to "pate",
        'j' to "uoea",
        'z' to "eia",
    )
}
