package studio.airpen.app.gesture

import studio.airpen.app.data.GestureId
import studio.airpen.app.data.GestureSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

data class Recognition(
    val gesture: GestureId? = null,
    val letter: String? = null,
    val score: Float = 0f,
    val headingDeg: Float = 0f,
    val closed: Boolean = false,
    val notes: String = "",
)

class GestureRecognizer {
    fun recognizeStroke(
        points: List<Pt>,
        settings: GestureSettings,
        typeMode: Boolean = false,
    ): Recognition {
        if (points.size < 4) return Recognition(notes = "too-short")
        val len = Unistroke.pathLength(points)
        val end = hypot(
            (points.last().x - points.first().x).toDouble(),
            (points.last().y - points.first().y).toDouble(),
        ).toFloat()
        if (len < settings.minFlickLength * 0.35f && end < settings.minFlickLength * 0.35f) {
            return Recognition(notes = "dead-zone")
        }

        val straight = if (len <= 1e-4f) 0f else (end / len)
        val netHeading = Unistroke.heading(points)
        val finish = Unistroke.finishHeading(points)
        val heading = if (angleDiff(netHeading, finish) < 0.7f) finish else netHeading
        val deg = ((heading * 180f / PI.toFloat()) + 360f) % 360f
        val closed = Unistroke.isClosed(points)
        val circ = Unistroke.circularity(points)
        val turns = Unistroke.windingTurns(points)
        val aspect = Unistroke.boundingAspect(points)

        if (!typeMode && !closed && end >= settings.minFlickLength * 0.55f &&
            straight >= settings.flickStraightness * 0.72f && len >= settings.minFlickLength * 0.7f
        ) {
            val flick = headingToFlick(deg)
            return Recognition(gesture = flick, score = straight, headingDeg = deg, notes = "flick")
        }

        if (typeMode) {
            val ax = kotlin.math.abs(kotlin.math.cos(heading.toDouble())).toFloat()
            val horizontalDart = straight >= 0.88f && ax > 0.82f && end >= settings.minFlickLength * 0.55f
            if (horizontalDart) {
                val flick = headingToFlick(deg)
                if (flick == GestureId.FLICK_LEFT || flick == GestureId.FLICK_DOWN_LEFT || flick == GestureId.FLICK_UP_LEFT) {
                    return Recognition(gesture = GestureId.FLICK_LEFT, letter = "⌫", score = straight, headingDeg = deg, notes = "bs")
                }
                if (flick == GestureId.FLICK_RIGHT || flick == GestureId.FLICK_DOWN_RIGHT || flick == GestureId.FLICK_UP_RIGHT) {
                    return Recognition(gesture = GestureId.FLICK_RIGHT, letter = " ", score = straight, headingDeg = deg, notes = "sp")
                }
            }
            val match = Unistroke.recognizeOriented(points, Templates.allType)
            if (match != null && match.second >= settings.minLet()) {
                return Recognition(letter = match.first.name, score = match.second, headingDeg = deg, closed = closed, notes = "letter")
            }
            val ay = kotlin.math.abs(kotlin.math.sin(heading.toDouble())).toFloat()
            val verticalDart = straight >= 0.90f && ay > 0.85f && end >= settings.minFlickLength * 0.7f
            if (verticalDart) {
                return if (headingToFlick(deg) == GestureId.FLICK_UP) {
                    Recognition(gesture = GestureId.FLICK_UP, letter = "⇧", score = straight, headingDeg = deg, notes = "sh")
                } else {
                    Recognition(gesture = GestureId.FLICK_DOWN, letter = "\n", score = straight, headingDeg = deg, notes = "nl")
                }
            }
            return Recognition(score = match?.second ?: 0f, headingDeg = deg, notes = if (match == null) "no-letter" else "low-letter")
        }

        if (closed && circ >= 0.72f && abs(turns) >= 0.55f) {
            val g = if (turns < 0) GestureId.CIRCLE_CW else GestureId.CIRCLE_CCW
            return Recognition(gesture = g, score = circ.coerceAtMost(1f), headingDeg = deg, closed = true, notes = "geo-circle")
        }

        val shapeMatch = Unistroke.recognize(points, Templates.shapes.map { it.template })
        if (shapeMatch != null && shapeMatch.second >= settings.shapeThreshold) {
            val named = Templates.shapes.first { it.template.name == shapeMatch.first.name }
            var id = named.id
            if (id == GestureId.CIRCLE_CW || id == GestureId.CIRCLE_CCW) {
                id = if (turns < 0) GestureId.CIRCLE_CW else GestureId.CIRCLE_CCW
            }
            return Recognition(gesture = id, score = shapeMatch.second, headingDeg = deg, closed = closed, notes = "template")
        }

        if (closed && aspect > 0.72f && circ in 0.45f..0.78f) {
            return Recognition(gesture = GestureId.SQUARE, score = aspect, headingDeg = deg, closed = true, notes = "geo-square")
        }
        if (closed && circ in 0.35f..0.7f) {
            return Recognition(gesture = GestureId.TRIANGLE, score = circ, headingDeg = deg, closed = true, notes = "geo-tri")
        }

        if (straight >= settings.flickStraightness * 0.9f && len >= settings.minFlickLength) {
            return Recognition(gesture = headingToFlick(deg), score = straight, headingDeg = deg, notes = "flick-fallback")
        }

        return Recognition(score = shapeMatch?.second ?: 0f, headingDeg = deg, closed = closed, notes = "unrecognized")
    }

    fun headingToFlick(deg: Float): GestureId {
        val d = ((deg % 360f) + 360f) % 360f
        return when {
            d < 30f || d >= 330f -> GestureId.FLICK_RIGHT
            d < 60f -> GestureId.FLICK_UP_RIGHT
            d < 120f -> GestureId.FLICK_UP
            d < 150f -> GestureId.FLICK_UP_LEFT
            d < 210f -> GestureId.FLICK_LEFT
            d < 240f -> GestureId.FLICK_DOWN_LEFT
            d < 300f -> GestureId.FLICK_DOWN
            else -> GestureId.FLICK_DOWN_RIGHT
        }
    }

    private fun angleDiff(a: Float, b: Float): Float {
        var d = abs(a - b)
        if (d > PI.toFloat()) d = 2f * PI.toFloat() - d
        return d
    }

    private fun GestureSettings.minLet(): Float = 0.46f
}
