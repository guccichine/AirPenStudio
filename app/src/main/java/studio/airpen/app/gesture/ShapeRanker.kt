package studio.airpen.app.gesture

import studio.airpen.app.data.GestureId
import kotlin.math.abs
import kotlin.math.max

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
            val gate = gate(id, closed, circ, turns, corners, bent, aspect)
            if (gate <= 0f) continue
            var score = 0f
            for (pts in candidates) {
                val dollar = Unistroke.recognize(pts, listOf(named.template))
                val cloud = Unistroke.recognizeCloud(pts, listOf(named.template))
                score = max(score, max(dollar?.second ?: 0f, (cloud?.second ?: 0f) * 0.94f))
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
            GestureId.DIAMOND ->
                if (closed && corners >= 3 && circ in 0.28f..0.70f) 1.05f else 0f
            GestureId.TRIANGLE ->
                if (closed && corners in 2..4 && circ < 0.78f) 1.10f else 0f
            GestureId.HEART ->
                if (closed && circ in 0.28f..0.72f) 1.05f else 0f
            GestureId.STAR ->
                if (corners >= 3) 1.05f else 0f
            GestureId.INFINITY ->
                if (spin >= 0.65f) 1.08f else 0f
            GestureId.SPIRAL ->
                if (spin >= 0.85f) 1.10f else 0f
            GestureId.PIGTAIL ->
                if (spin >= 0.35f) 1.0f else 0f
            GestureId.CHECK ->
                if (!closed && (bent || corners >= 1)) 1.12f else 0f
            GestureId.CROSS, GestureId.PLUS ->
                if (corners >= 1) 1.0f else 0.15f
            GestureId.ZIGZAG, GestureId.WAVE ->
                if (!closed && corners >= 2) 1.08f else 0f
            GestureId.HOOK ->
                if (!closed && (bent || corners >= 1)) 1.08f else 0f
            GestureId.CARET, GestureId.ARROW ->
                if (!closed && corners <= 3) 1.05f else 0f
            GestureId.BRACKET_LEFT, GestureId.BRACKET_RIGHT ->
                if (corners >= 2) 1.05f else 0f
            else -> 0.4f
        }
    }
}
