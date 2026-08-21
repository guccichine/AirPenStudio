package studio.airpen.app.mouse

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import studio.airpen.app.action.ActionExecutor
import studio.airpen.app.data.MouseSettings
import kotlin.math.abs
import kotlin.math.pow

class AirMouseController(
    private val context: Context,
    private val executor: ActionExecutor,
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var overlay: CursorOverlayView? = null
    private var params: WindowManager.LayoutParams? = null

    var x: Float = 0f
        private set
    var y: Float = 0f
        private set
    var visible: Boolean = false
        private set
    var precision: Boolean = false
    var dragLock: Boolean = false

    private var vx = 0f
    private var vy = 0f
    private var lastMove = 0L
    private val hideRunnable = Runnable { if (!settings.alwaysShowCursor) hide() }

    var settings: MouseSettings = MouseSettings()

    fun attach() {
        if (overlay != null) return
        val metrics = context.resources.displayMetrics
        x = metrics.widthPixels / 2f
        y = metrics.heightPixels / 2f
        val view = CursorOverlayView(context)
        view.style = settings.cursorStyle
        view.cursorDp = settings.cursorSizeDp
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        try {
            wm.addView(view, lp)
            overlay = view
            params = lp
            view.setCursor(x, y)
            visible = true
        } catch (_: Throwable) {
        }
    }

    fun detach() {
        main.removeCallbacks(hideRunnable)
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        params = null
        visible = false
    }

    fun show() {
        if (overlay == null) attach()
        overlay?.visibility = android.view.View.VISIBLE
        visible = true
        bumpIdle()
    }

    fun hide() {
        overlay?.visibility = android.view.View.GONE
        visible = false
    }

    fun center() {
        val metrics = context.resources.displayMetrics
        x = metrics.widthPixels / 2f
        y = metrics.heightPixels / 2f
        overlay?.setCursor(x, y)
        show()
    }

    fun move(dx: Float, dy: Float) {
        val metrics = context.resources.displayMetrics
        var mx = if (settings.invertX) -dx else dx
        var my = if (settings.invertY) -dy else dy
        if (settings.swapXY) {
            val t = mx; mx = my; my = t
        }
        val mag = abs(mx) + abs(my)
        val accel = 1f + (mag * settings.acceleration).toDouble().pow(1.15).toFloat()
        val scale = settings.sensitivity * 1400f * accel * (if (precision) settings.precisionScale else 1f)
        val nx = mx * scale
        val ny = -my * scale
        val a = settings.smoothing.coerceIn(0f, 0.95f)
        vx = vx * a + nx * (1 - a)
        vy = vy * a + ny * (1 - a)
        x = (x + vx)
        y = (y + vy)
        if (settings.edgeBounce) {
            x = x.coerceIn(8f, metrics.widthPixels - 8f)
            y = y.coerceIn(8f, metrics.heightPixels - 8f)
        } else {
            x = x.coerceIn(0f, metrics.widthPixels.toFloat())
            y = y.coerceIn(0f, metrics.heightPixels.toFloat())
        }
        overlay?.setCursor(x, y, pressed = dragLock, trail = settings.showTrail)
        overlay?.cursorDp = settings.cursorSizeDp
        overlay?.style = settings.cursorStyle
        show()
        bumpIdle()
        lastMove = SystemClock.uptimeMillis()
    }

    fun click(kind: ActionExecutor.ClickKind) {
        show()
        overlay?.flash()
        when (kind) {
            ActionExecutor.ClickKind.LEFT -> executor.tapAt(x, y)
            ActionExecutor.ClickKind.DOUBLE -> {
                executor.tapAt(x, y)
                main.postDelayed({ executor.tapAt(x, y) }, 70)
            }
            ActionExecutor.ClickKind.RIGHT -> executor.longPressAt(x, y)
            ActionExecutor.ClickKind.DRAG_TOGGLE -> {
                dragLock = !dragLock
                overlay?.setCursor(x, y, pressed = dragLock)
            }
        }
        bumpIdle()
    }

    fun scroll(dirX: Float, dirY: Float) {
        val g = settings.scrollGain * 280f
        val x2 = (x + dirX * g * 0.15f)
        val y2 = (y + dirY * g)
        executor.swipe(x, y, x2, y2, 220)
        bumpIdle()
    }

    private fun bumpIdle() {
        main.removeCallbacks(hideRunnable)
        if (settings.hideAfterMs > 0 && !settings.alwaysShowCursor) {
            main.postDelayed(hideRunnable, settings.hideAfterMs)
        }
    }
}

class CursorOverlayView(context: Context) : FrameLayout(context) {
    var cursorDp: Float = 36f
    var style: String = "crosshair"
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var cx = 0f
    private var cy = 0f
    private var pressed = false
    private val trail = ArrayList<android.graphics.PointF>(24)
    private var flashUntil = 0L

    init {
        setWillNotDraw(false)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    fun setCursor(x: Float, y: Float, pressed: Boolean = this.pressed, trail: Boolean = true) {
        cx = x
        cy = y
        this.pressed = pressed
        if (trail) {
            this.trail.add(android.graphics.PointF(x, y))
            while (this.trail.size > 18) this.trail.removeAt(0)
        }
        invalidate()
    }

    fun flash() {
        flashUntil = SystemClock.uptimeMillis() + 140
        invalidate()
        postDelayed({ invalidate() }, 150)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val r = cursorDp * density / 2f
        if (trail.size > 1) {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 3f
            for (i in 1 until trail.size) {
                val a = i / trail.size.toFloat()
                paint.color = android.graphics.Color.argb((80 * a).toInt(), 212, 168, 75)
                canvas.drawLine(trail[i - 1].x, trail[i - 1].y, trail[i].x, trail[i].y, paint)
            }
        }
        val flashing = SystemClock.uptimeMillis() < flashUntil
        val gold = if (pressed || flashing) 0xFFFFE08A.toInt() else 0xFFD4A84B.toInt()
        when (style) {
            "dot" -> {
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = gold
                canvas.drawCircle(cx, cy, r * 0.55f, paint)
            }
            "pen" -> {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.color = gold
                canvas.drawLine(cx - r, cy + r, cx + r * 0.2f, cy - r * 0.2f, paint)
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawCircle(cx + r * 0.2f, cy - r * 0.2f, 5f, paint)
            }
            else -> {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = gold
                canvas.drawCircle(cx, cy, r, paint)
                canvas.drawLine(cx - r * 1.35f, cy, cx + r * 1.35f, cy, paint)
                canvas.drawLine(cx, cy - r * 1.35f, cx, cy + r * 1.35f, paint)
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = 0xEE111111.toInt()
                canvas.drawCircle(cx, cy, 5f, paint)
                paint.color = gold
                canvas.drawCircle(cx, cy, 3f, paint)
            }
        }
    }
}
