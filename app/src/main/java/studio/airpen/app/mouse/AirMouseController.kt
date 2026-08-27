package studio.airpen.app.mouse

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import studio.airpen.app.action.ActionExecutor
import studio.airpen.app.data.MouseSettings
import studio.airpen.app.data.TrailPrefs
import studio.airpen.app.overlay.OverlayWindows
import kotlin.math.abs
import kotlin.math.pow

class AirMouseController(
    private val context: Context,
    private val executor: ActionExecutor,
) {
    private val main = Handler(Looper.getMainLooper())
    private var overlay: CursorOverlayView? = null
    private var handle: OverlayWindows.Handle? = null
    var overlayReady: Boolean = false
        private set

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
        view.trailStyle = TrailPrefs.style
        view.trailThickness = TrailPrefs.thickness
        view.trailLength = TrailPrefs.length
        view.trailIntensity = TrailPrefs.intensity
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val added = OverlayWindows.add(context, view, lp)
        if (added == null) {
            Log.e("AirMouse", "cursor overlay failed — enable Display over other apps + Accessibility")
            overlayReady = false
            return
        }
        handle = added
        overlay = view
        overlayReady = true
        view.setCursor(x, y)
        visible = true
    }

    fun detach() {
        main.removeCallbacks(hideRunnable)
        handle?.remove()
        handle = null
        overlay = null
        overlayReady = false
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
        overlay?.cursorDp = settings.cursorSizeDp
        overlay?.style = settings.cursorStyle
        overlay?.trailStyle = TrailPrefs.style
        overlay?.trailThickness = TrailPrefs.thickness
        overlay?.trailLength = TrailPrefs.length
        overlay?.trailIntensity = TrailPrefs.intensity
        overlay?.setCursor(x, y, pressed = dragLock, trailOn = TrailPrefs.show && TrailPrefs.style != "off")
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
        val metrics = context.resources.displayMetrics
        val startX = if (overlayReady && x > 8f) x else metrics.widthPixels / 2f
        val startY = if (overlayReady && y > 8f) y else metrics.heightPixels * 0.52f
        val g = settings.scrollGain * 360f
        val x2 = (startX + dirX * g * 0.15f).coerceIn(24f, metrics.widthPixels - 24f)
        val y2 = (startY + dirY * g).coerceIn(48f, metrics.heightPixels - 48f)
        executor.swipe(startX, startY, x2, y2, 240)
        bumpIdle()
    }

    private fun bumpIdle() {
        main.removeCallbacks(hideRunnable)
        if (settings.hideAfterMs > 0 && !settings.alwaysShowCursor) {
            main.postDelayed(hideRunnable, settings.hideAfterMs)
        }
    }
}
