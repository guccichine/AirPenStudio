package studio.airpen.app.gesture

import studio.airpen.app.data.GestureId
import studio.airpen.app.data.GestureSettings
import studio.airpen.app.data.LetterSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class Recognition(
    val gesture: GestureId? = null,
    val letter: String? = null,
    val score: Float = 0f,
    val headingDeg: Float = 0f,
    val closed: Boolean = false,
    val notes: String = "",
    val alternatives: List<Pair<String, Float>> = emptyList(),
)

class GestureRecognizer {
    fun recognizeStroke(
        points: List<Pt>,
        settings: GestureSettings,
        typeMode: Boolean = false,
        userSamples: List<LetterSample> = emptyList(),
        prefix: String = "",
        minLetter: Float = 0.40f,
    ): Recognition {
        if (points.size < 4) return Recognition(notes = "too-short")
        val cleaned = StrokePrep.prepare(points, settings)
        if (cleaned.size < 4) return Recognition(notes = "too-short")
        val len = Unistroke.pathLength(cleaned)
        val end = hypot(
            (cleaned.last().x - cleaned.first().x).toDouble(),
            (cleaned.last().y - cleaned.first().y).toDouble(),
        ).toFloat()
        if (len < settings.minFlickLength * 0.35f && end < settings.minFlickLength * 0.35f) {
            return Recognition(notes = "dead-zone")
        }

        val straight = if (len <= 1e-4f) 0f else (end / len)
        val vh = Unistroke.velocityHeading(cleaned)
        val netHeading = Unistroke.heading(cleaned)
        val heading = if (angleDiff(vh, netHeading) < 0.85f) vh else netHeading
        val deg = (((heading * 180f / PI.toFloat()) + settings.headingOffsetDeg) + 360f) % 360f
        val closed = Unistroke.isClosed(cleaned)
        val circ = Unistroke.circularity(cleaned)
        val turns = Unistroke.windingTurns(cleaned)
        val aspect = Unistroke.boundingAspect(cleaned)
        val corners = Unistroke.cornerCount(cleaned)
        val ballistic = Unistroke.ballistic(cleaned)
        val duration = cleaned.last().t - cleaned.first().t

        val flickLike = !closed &&
            corners <= 1 &&
            abs(turns) < 0.32f &&
            straight >= settings.flickStraightness &&
            (ballistic >= settings.flickMinVelocity * 0.72f || duration <= 0L || duration < 520L) &&
            end >= len * 0.55f

        if (typeMode) {
            val ax = abs(kotlin.math.cos(heading.toDouble())).toFloat()
            val ay = abs(kotlin.math.sin(heading.toDouble())).toFloat()
            val horizontalDart = straight >= 0.88f && ax > 0.82f && flickLike
            if (horizontalDart) {
                val flick = headingToFlick(deg, cleaned, settings.cardinalBias)
                if (flick == GestureId.FLICK_LEFT || flick == GestureId.FLICK_DOWN_LEFT || flick == GestureId.FLICK_UP_LEFT) {
                    return Recognition(gesture = GestureId.FLICK_LEFT, letter = "⌫", score = straight, headingDeg = deg, notes = "bs")
                }
                if (flick == GestureId.FLICK_RIGHT || flick == GestureId.FLICK_DOWN_RIGHT || flick == GestureId.FLICK_UP_RIGHT) {
                    return Recognition(gesture = GestureId.FLICK_RIGHT, letter = " ", score = straight, headingDeg = deg, notes = "sp")
                }
            }
            val ranked = LetterRecognizer.ranked(cleaned, userSamples, prefix, minLetter)
            val top = ranked.firstOrNull()
            if (top != null && top.score >= minLetter) {
                return Recognition(
                    letter = top.letter,
                    score = top.score,
                    headingDeg = deg,
                    closed = closed,
                    notes = "letter",
                    alternatives = ranked.map { it.letter to it.score },
                )
            }
            val verticalDart = straight >= 0.90f && ay > 0.85f && flickLike
            if (verticalDart) {
                return if (headingToFlick(deg, cleaned, settings.cardinalBias) == GestureId.FLICK_UP) {
                    Recognition(gesture = GestureId.FLICK_UP, letter = "⇧", score = straight, headingDeg = deg, notes = "sh")
                } else {
                    Recognition(gesture = GestureId.FLICK_DOWN, letter = "\n", score = straight, headingDeg = deg, notes = "nl")
                }
            }
            return Recognition(
                score = top?.score ?: 0f,
                headingDeg = deg,
                notes = if (top == null) "no-letter" else "low-letter",
                alternatives = ranked.map { it.letter to it.score },
            )
        }

        if (flickLike) {
            val flick = headingToFlick(deg, cleaned, settings.cardinalBias)
            val score = (straight * 0.7f + min(1f, ballistic / 3f) * 0.3f).coerceIn(0f, 1f)
            return Recognition(gesture = flick, score = score, headingDeg = deg, notes = "flick")
        }

        if (closed && circ >= 0.72f && abs(turns) >= 0.55f) {
            val g = if (turns < 0) GestureId.CIRCLE_CW else GestureId.CIRCLE_CCW
            return Recognition(gesture = g, score = circ.coerceAtMost(1f), headingDeg = deg, closed = true, notes = "geo-circle")
        }

        val ranked = GestureRanker.ranked(cleaned, Templates.shapes.map { it.template })
        val top = ranked.firstOrNull()
        val second = ranked.getOrNull(1)
        if (top != null && top.second >= settings.shapeThreshold) {
            val margin = top.second - (second?.second ?: 0f)
            if (margin >= settings.templateMargin || top.second >= settings.shapeThreshold + 0.08f) {
                val named = Templates.shapes.first { it.template.name == top.first.name }
                var id = named.id
                if (id == GestureId.CIRCLE_CW || id == GestureId.CIRCLE_CCW) {
                    id = if (turns < 0) GestureId.CIRCLE_CW else GestureId.CIRCLE_CCW
                }
                return Recognition(
                    gesture = id,
                    score = top.second,
                    headingDeg = deg,
                    closed = closed,
                    notes = "template",
                    alternatives = ranked.drop(1).take(3).map { it.first.name to it.second },
                )
            }
        }

        if (closed && aspect > 0.72f && circ in 0.45f..0.78f) {
            return Recognition(gesture = GestureId.SQUARE, score = aspect, headingDeg = deg, closed = true, notes = "geo-square")
        }
        if (closed && circ in 0.35f..0.7f && corners >= 2) {
            return Recognition(gesture = GestureId.TRIANGLE, score = circ, headingDeg = deg, closed = true, notes = "geo-tri")
        }
        if (closed && aspect > 0.55f && circ in 0.28f..0.55f) {
            return Recognition(gesture = GestureId.DIAMOND, score = aspect, headingDeg = deg, closed = true, notes = "geo-diamond")
        }

        if (straight >= settings.flickStraightness * 0.92f && corners <= 1 && abs(turns) < 0.28f) {
            return Recognition(
                gesture = headingToFlick(deg, cleaned, settings.cardinalBias),
                score = straight,
                headingDeg = deg,
                notes = "flick-fallback",
            )
        }

        return Recognition(
            score = top?.second ?: 0f,
            headingDeg = deg,
            closed = closed,
            notes = "unrecognized",
            alternatives = ranked.take(3).map { it.first.name to it.second },
        )
    }

    fun headingToFlick(deg: Float, points: List<Pt> = emptyList(), bias: Float = 0.72f): GestureId {
        if (points.size >= 2) {
            val v = Unistroke.velocityVector(points)
            val ax = abs(v.x)
            val ay = abs(v.y)
            val dominance = max(ax, ay) / max(min(ax, ay), 1e-6f)
            val threshold = 1.22f + bias * 0.95f
            if (dominance >= threshold) {
                return if (ax > ay) {
                    if (v.x >= 0f) GestureId.FLICK_RIGHT else GestureId.FLICK_LEFT
                } else {
                    if (v.y >= 0f) GestureId.FLICK_UP else GestureId.FLICK_DOWN
                }
            }
        }
        val d = ((deg % 360f) + 360f) % 360f
        return when {
            d < 22f || d >= 338f -> GestureId.FLICK_RIGHT
            d < 68f -> GestureId.FLICK_UP_RIGHT
            d < 112f -> GestureId.FLICK_UP
            d < 158f -> GestureId.FLICK_UP_LEFT
            d < 202f -> GestureId.FLICK_LEFT
            d < 248f -> GestureId.FLICK_DOWN_LEFT
            d < 292f -> GestureId.FLICK_DOWN
            else -> GestureId.FLICK_DOWN_RIGHT
        }
    }

    private fun angleDiff(a: Float, b: Float): Float {
        var d = abs(a - b)
        if (d > PI.toFloat()) d = 2f * PI.toFloat() - d
        return d
    }
}
