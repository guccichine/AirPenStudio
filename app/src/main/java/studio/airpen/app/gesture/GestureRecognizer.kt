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
        val heading = Unistroke.heading(points)
        val deg = ((heading * 180f / PI.toFloat()) + 360f) % 360f
        val closed = Unistroke.isClosed(points)
        val circ = Unistroke.circularity(points)
        val turns = Unistroke.windingTurns(points)
        val aspect = Unistroke.boundingAspect(points)

        if (!typeMode && straight >= settings.flickStraightness && !closed && len >= settings.minFlickLength) {
            val flick = headingToFlick(deg)
            return Recognition(gesture = flick, score = straight, headingDeg = deg, notes = "flick")
        }

        if (typeMode) {
            val shortFlick = straight >= 0.86f && len >= settings.minFlickLength * 0.7f
            if (shortFlick) {
                val flick = headingToFlick(deg)
                val mapped = when (flick) {
                    GestureId.FLICK_LEFT, GestureId.FLICK_DOWN_LEFT, GestureId.FLICK_UP_LEFT ->
                        Recognition(gesture = GestureId.FLICK_LEFT, letter = "⌫", score = straight, headingDeg = deg, notes = "bs")
                    GestureId.FLICK_RIGHT, GestureId.FLICK_DOWN_RIGHT, GestureId.FLICK_UP_RIGHT ->
                        Recognition(gesture = GestureId.FLICK_RIGHT, letter = " ", score = straight, headingDeg = deg, notes = "sp")
                    GestureId.FLICK_DOWN ->
                        Recognition(gesture = GestureId.FLICK_DOWN, letter = "\n", score = straight, headingDeg = deg, notes = "nl")
                    GestureId.FLICK_UP ->
                        Recognition(gesture = GestureId.FLICK_UP, letter = "⇧", score = straight, headingDeg = deg, notes = "sh")
                    else -> null
                }
                if (mapped != null) return mapped
            }
            val match = Unistroke.recognize(points, Templates.allType) ?: return Recognition(notes = "no-letter")
            if (match.second < settings.minLet()) return Recognition(score = match.second, notes = "low-letter")
            return Recognition(letter = match.first.name, score = match.second, headingDeg = deg, closed = closed, notes = "letter")
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
        val d = ((deg + 22.5f) % 360f + 360f) % 360f
        return when {
            d < 45f -> GestureId.FLICK_RIGHT
            d < 90f -> GestureId.FLICK_UP_RIGHT
            d < 135f -> GestureId.FLICK_UP
            d < 180f -> GestureId.FLICK_UP_LEFT
            d < 225f -> GestureId.FLICK_LEFT
            d < 270f -> GestureId.FLICK_DOWN_LEFT
            d < 315f -> GestureId.FLICK_DOWN
            else -> GestureId.FLICK_DOWN_RIGHT
        }
    }

    private fun GestureSettings.minLet(): Float = 0.42f
}
