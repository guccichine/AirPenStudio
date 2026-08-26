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
    val alternatives: List<Pair<String, Float>> = emptyList(),
)

class GestureRecognizer {
    fun recognizeStroke(
        points: List<Pt>,
        settings: GestureSettings,
    ): Recognition {
        if (points.size < 2) return Recognition(notes = "too-short")
        val len = Unistroke.pathLength(points)
        val end = hypot(
            (points.last().x - points.first().x).toDouble(),
            (points.last().y - points.first().y).toDouble(),
        ).toFloat()
        if (len < settings.minFlickLength * 0.18f && end < settings.minFlickLength * 0.18f) {
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
        val duration = (points.last().t - points.first().t).coerceAtLeast(8L)
        val speed = end / duration.toFloat()

        val flickOk = (!closed && end >= settings.minFlickLength * 0.32f &&
            straight >= settings.flickStraightness * 0.62f && len >= settings.minFlickLength * 0.35f) ||
            (speed > 0.00035f && end >= settings.minFlickLength * 0.18f && straight >= 0.45f)

        if (flickOk) {
            val flick = headingToFlick(deg)
            return Recognition(gesture = flick, score = straight, headingDeg = deg, notes = "flick")
        }

        if (closed && circ >= 0.62f && abs(turns) >= 0.45f) {
            val g = if (turns < 0) GestureId.CIRCLE_CW else GestureId.CIRCLE_CCW
            return Recognition(gesture = g, score = circ.coerceAtMost(1f), headingDeg = deg, closed = true, notes = "geo-circle")
        }
        if (!closed && circ >= 0.42f && abs(turns) in 0.25f..0.9f) {
            return Recognition(gesture = GestureId.SEMICIRCLE, score = circ, headingDeg = deg, notes = "geo-semi")
        }

        val shapeMatch = Unistroke.recognize(points, Templates.shapes.map { it.template })
        if (shapeMatch != null && shapeMatch.second >= settings.shapeThreshold * 0.88f) {
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
        if (closed && aspect > 0.55f && circ in 0.28f..0.55f) {
            return Recognition(gesture = GestureId.DIAMOND, score = aspect, headingDeg = deg, closed = true, notes = "geo-diamond")
        }
        if (!closed && abs(turns) < 0.35f && aspect < 0.55f && end >= settings.minFlickLength * 0.4f) {
            val dx = points.last().x - points.first().x
            val dy = points.last().y - points.first().y
            if (abs(dx) > settings.minFlickLength * 0.2f && abs(dy) > settings.minFlickLength * 0.2f) {
                return Recognition(gesture = GestureId.L_SHAPE, score = 0.7f, headingDeg = deg, notes = "geo-L")
            }
        }

        if (straight >= settings.flickStraightness * 0.78f && len >= settings.minFlickLength * 0.28f) {
            return Recognition(gesture = headingToFlick(deg), score = straight, headingDeg = deg, notes = "flick-fallback")
        }
        if (end >= settings.minFlickLength * 0.22f) {
            return Recognition(gesture = headingToFlick(deg), score = straight.coerceAtLeast(0.4f), headingDeg = deg, notes = "flick-loose")
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
}
