package studio.airpen.app.gesture

import studio.airpen.app.data.GestureId
import studio.airpen.app.data.LIVE_SHAPES
import kotlin.math.abs
import kotlin.math.max

/** Narrow ranker used only as a fallback for the six live shapes. */
object ShapeRanker {
    fun best(
        points: List<Pt>,
        closed: Boolean,
        circ: Float,
        turns: Float,
        corners: Int,
        bent: Boolean,
        aspect: Float,
    ): Pair<GestureId, Float>? {
        val candidates = listOf(points, Unistroke.flipY(points))
        var bestId: GestureId? = null
        var bestScore = 0f
        for (named in Templates.shapes) {
            val id = named.id
            if (id !in LIVE_SHAPES) continue
            val gate = gate(id, closed, circ, turns, corners, bent, aspect)
            if (gate <= 0f) continue
            var score = 0f
            for (pts in candidates) {
                val dollar = Unistroke.recognize(pts, listOf(named.template))
                score = max(score, dollar?.second ?: 0f)
            }
            score *= gate
            if (score > bestScore) {
                bestScore = score
                bestId = id
            }
        }
        val id = bestId ?: return null
        if (id == GestureId.CIRCLE_CW || id == GestureId.CIRCLE_CCW) {
            val circled = if (turns < 0) GestureId.CIRCLE_CW else GestureId.CIRCLE_CCW
            return circled to bestScore.coerceAtMost(1f)
        }
        return id to bestScore.coerceAtMost(1f)
    }

    private fun gate(
        id: GestureId,
        closed: Boolean,
        circ: Float,
        turns: Float,
        corners: Int,
        bent: Boolean,
        aspect: Float,
    ): Float {
        val spin = abs(turns)
        return when (id) {
            GestureId.CIRCLE_CW, GestureId.CIRCLE_CCW ->
                if (closed && circ >= 0.52f && spin >= 0.40f) 1.15f else 0f
            GestureId.SQUARE ->
                if (closed && corners >= 3 && circ in 0.32f..0.82f && aspect > 0.62f) 1.08f else 0f
            GestureId.CHECK ->
                if (!closed && (bent || corners >= 1)) 1.12f else 0f
            GestureId.CROSS ->
                if (corners >= 1) 1.0f else 0f
            GestureId.WAVE ->
                if (!closed && corners >= 2) 1.08f else 0f
            else -> 0f
        }
    }
}
