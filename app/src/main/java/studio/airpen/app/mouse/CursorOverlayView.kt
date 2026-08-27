package studio.airpen.app.mouse

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.SystemClock
import android.widget.FrameLayout
import studio.airpen.app.data.TrailStyle
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class CursorOverlayView(context: Context) : FrameLayout(context) {
    var cursorDp: Float = 48f
    var style: String = "crosshair"
    var trailStyle: String = "comet"
    var trailThickness: Float = 1f
    var trailLength: Float = 1f
    var trailIntensity: Float = 1f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    }
    private var cx = 0f
    private var cy = 0f
    private var pressed = false
    private val trail = ArrayList<PointF>(64)
    private val sparks = ArrayList<Spark>(96)
    private var flashUntil = 0L
    private val path = Path()
    private val rng = Random(7)

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setCursor(x: Float, y: Float, pressed: Boolean = this.pressed, trailOn: Boolean = true) {
        val dx = x - cx
        val dy = y - cy
        cx = x
        cy = y
        this.pressed = pressed
        val kind = TrailStyle.fromId(trailStyle)
        val cap = (10 + trailLength * 28).toInt().coerceIn(8, 48)
        if (trailOn && kind != TrailStyle.OFF) {
            trail.add(PointF(x, y))
            while (trail.size > cap) trail.removeAt(0)
            spawn(dx, dy, kind)
        } else {
            trail.clear()
            sparks.clear()
        }
        stepSparks()
        invalidate()
    }

    fun flash() {
        flashUntil = SystemClock.uptimeMillis() + 140
        invalidate()
        postDelayed({ invalidate() }, 150)
    }

    private fun spawn(dx: Float, dy: Float, kind: TrailStyle) {
        val n = when (kind) {
            TrailStyle.SPARKLER, TrailStyle.STARDUST, TrailStyle.FIREFLY, TrailStyle.DUST -> 5
            TrailStyle.BUBBLES, TrailStyle.PETAL, TrailStyle.PIXEL -> 3
            TrailStyle.OFF -> 0
            else -> 2
        }
        val speed = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        repeat(n) {
            if (sparks.size > 80) sparks.removeAt(0)
            sparks += Spark(
                x = cx + rng.nextFloat() * 8f - 4f,
                y = cy + rng.nextFloat() * 8f - 4f,
                vx = -dx * 0.08f + (rng.nextFloat() - 0.5f) * (4f + speed * 0.04f),
                vy = -dy * 0.08f + (rng.nextFloat() - 0.5f) * (4f + speed * 0.04f),
                life = 1f,
                size = 2f + rng.nextFloat() * 5f,
                hue = rng.nextFloat(),
            )
        }
    }

    private fun stepSparks() {
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.x += s.vx
            s.y += s.vy
            s.vx *= 0.92f
            s.vy *= 0.92f
            s.life -= 0.045f
            if (s.life <= 0f) it.remove()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val r = cursorDp * density / 2f
        val kind = TrailStyle.fromId(trailStyle)
        val thick = (3.2f + trailThickness * 7f) * density / 2.6f
        val glowA = (110 * trailIntensity).toInt().coerceIn(20, 220)

        if (kind != TrailStyle.OFF && trail.size > 1) {
            drawTrail(canvas, kind, thick, glowA)
        }
        for (s in sparks) {
            paint.style = Paint.Style.FILL
            paint.color = sparkColor(kind, s)
            canvas.drawCircle(s.x, s.y, s.size * s.life * trailThickness.coerceAtLeast(0.5f), paint)
        }

        val flashing = SystemClock.uptimeMillis() < flashUntil
        val gold = if (pressed || flashing) 0xFFFFE08A.toInt() else 0xFFD4A84B.toInt()
        glow.style = Paint.Style.FILL
        glow.color = Color.argb(if (pressed || flashing) 160 else 90, 212, 168, 75)
        canvas.drawCircle(cx, cy, r * 1.7f, glow)

        when (style) {
            "dot" -> {
                paint.style = Paint.Style.FILL
                paint.color = gold
                canvas.drawCircle(cx, cy, r * 0.55f, paint)
            }
            "pen" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.color = gold
                canvas.drawLine(cx - r, cy + r, cx + r * 0.2f, cy - r * 0.2f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx + r * 0.2f, cy - r * 0.2f, 5f, paint)
            }
            else -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                paint.color = gold
                canvas.drawCircle(cx, cy, r, paint)
                canvas.drawLine(cx - r * 1.35f, cy, cx + r * 1.35f, cy, paint)
                canvas.drawLine(cx, cy - r * 1.35f, cx, cy + r * 1.35f, paint)
                paint.style = Paint.Style.FILL
                paint.color = 0xEE111111.toInt()
                canvas.drawCircle(cx, cy, 5f, paint)
                paint.color = gold
                canvas.drawCircle(cx, cy, 3f, paint)
            }
        }
    }

    private fun drawTrail(canvas: Canvas, kind: TrailStyle, thick: Float, glowA: Int) {
        path.reset()
        path.moveTo(trail[0].x, trail[0].y)
        for (i in 1 until trail.size) path.lineTo(trail[i].x, trail[i].y)

        when (kind) {
            TrailStyle.LASER -> {
                glow.strokeWidth = thick * 3.2f
                glow.style = Paint.Style.STROKE
                glow.color = Color.argb(glowA, 80, 220, 255)
                canvas.drawPath(path, glow)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = thick * 0.7f
                paint.color = Color.argb(230, 220, 255, 255)
                canvas.drawPath(path, paint)
            }
            TrailStyle.RIBBON, TrailStyle.AURORA, TrailStyle.PRISM, TrailStyle.RAINBOW -> {
                for (i in 1 until trail.size) {
                    val a = i / trail.size.toFloat()
                    val hue = when (kind) {
                        TrailStyle.AURORA -> 140 + a * 80
                        TrailStyle.PRISM, TrailStyle.RAINBOW -> a * 300f
                        else -> 42f
                    }
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = thick * (0.6f + a)
                    paint.color = hsv(hue, 0.55f, 1f, (glowA * a).toInt())
                    canvas.drawLine(trail[i - 1].x, trail[i - 1].y, trail[i].x, trail[i].y, paint)
                }
            }
            TrailStyle.LIGHTNING, TrailStyle.GLITCH, TrailStyle.CIRCUIT -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = thick * 0.8f
                for (i in 1 until trail.size) {
                    val jx = if (kind == TrailStyle.GLITCH) (rng.nextFloat() - 0.5f) * 10f else 0f
                    val jy = if (kind == TrailStyle.LIGHTNING) (rng.nextFloat() - 0.5f) * 8f else 0f
                    paint.color = Color.argb(200, 180, 220, 255)
                    canvas.drawLine(trail[i - 1].x + jx, trail[i - 1].y + jy, trail[i].x, trail[i].y, paint)
                }
            }
            TrailStyle.PIXEL -> {
                paint.style = Paint.Style.FILL
                for (i in trail.indices) {
                    val a = i / trail.size.toFloat()
                    paint.color = Color.argb((200 * a).toInt(), 212, 168, 75)
                    val s = 6f + thick
                    canvas.drawRect(trail[i].x - s / 2, trail[i].y - s / 2, trail[i].x + s / 2, trail[i].y + s / 2, paint)
                }
            }
            TrailStyle.HALO, TrailStyle.PULSE, TrailStyle.ORBIT -> {
                val last = trail.last()
                glow.style = Paint.Style.STROKE
                glow.strokeWidth = thick
                glow.color = Color.argb(glowA, 212, 168, 75)
                val pulse = 10f + 8f * sin(SystemClock.uptimeMillis() / 180.0).toFloat()
                canvas.drawCircle(last.x, last.y, pulse + thick * 4f, glow)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = thick
                paint.color = Color.argb(180, 212, 168, 75)
                canvas.drawPath(path, paint)
            }
            TrailStyle.HELIX -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = thick * 0.7f
                for (i in 1 until trail.size) {
                    val t = i / 3f
                    val ox = cos(t.toDouble()).toFloat() * 10f
                    val oy = sin(t.toDouble()).toFloat() * 10f
                    paint.color = Color.argb(160, 212, 168, 75)
                    canvas.drawLine(trail[i - 1].x + ox, trail[i - 1].y + oy, trail[i].x + ox, trail[i].y + oy, paint)
                }
            }
            TrailStyle.VOID -> {
                glow.style = Paint.Style.STROKE
                glow.strokeWidth = thick * 2.4f
                glow.color = Color.argb(min(200, glowA + 40), 40, 20, 80)
                canvas.drawPath(path, glow)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = thick
                paint.color = Color.argb(220, 180, 140, 255)
                canvas.drawPath(path, paint)
            }
            else -> {
                glow.style = Paint.Style.STROKE
                glow.strokeWidth = thick * 2.6f
                glow.color = Color.argb(glowA, 212, 168, 75)
                canvas.drawPath(path, glow)
                for (i in 1 until trail.size) {
                    val a = i / trail.size.toFloat()
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = thick * (0.45f + a)
                    paint.color = Color.argb((90 + 140 * a * trailIntensity).toInt().coerceAtMost(255), 212, 168, 75)
                    canvas.drawLine(trail[i - 1].x, trail[i - 1].y, trail[i].x, trail[i].y, paint)
                }
            }
        }
    }

    private fun sparkColor(kind: TrailStyle, s: Spark): Int {
        val a = (200 * s.life * trailIntensity).toInt().coerceIn(0, 255)
        return when (kind) {
            TrailStyle.ICE, TrailStyle.LASER -> Color.argb(a, 160, 220, 255)
            TrailStyle.EMBER, TrailStyle.METEOR -> Color.argb(a, 255, (80 + 100 * s.hue).toInt(), 40)
            TrailStyle.FIREFLY, TrailStyle.STARDUST -> Color.argb(a, 255, 230, 140)
            TrailStyle.PETAL -> Color.argb(a, 255, 140, 180)
            TrailStyle.PLASMA, TrailStyle.NEON -> Color.argb(a, 80, 255, 200)
            TrailStyle.BUBBLES -> Color.argb(a / 2, 200, 230, 255)
            TrailStyle.RAINBOW, TrailStyle.PRISM, TrailStyle.AURORA -> hsv(s.hue * 360f, 0.6f, 1f, a)
            TrailStyle.VOID -> Color.argb(a, 160, 120, 255)
            else -> Color.argb(a, 212, 168, 75)
        }
    }

    private fun hsv(h: Float, s: Float, v: Float, a: Int): Int {
        val c = Color.HSVToColor(floatArrayOf(h % 360f, s, v))
        return Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
    }

    private class Spark(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        var size: Float,
        var hue: Float,
    )
}
