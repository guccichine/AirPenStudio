package studio.airpen.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import studio.airpen.app.service.AirPenBackground

/**
 * Always-on-top STOP control while the S Pen engine is running.
 * Tapping it disconnects the pen, drops overlays, and kills the
 * background service — including from any other app.
 */
object StopOverlay {
    private var handle: OverlayWindows.Handle? = null

    fun show(context: Context) {
        if (handle != null) return
        val app = context.applicationContext
        val density = app.resources.displayMetrics.density
        val btn = TextView(app).apply {
            text = "STOP"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(
                (18 * density).toInt(),
                (12 * density).toInt(),
                (18 * density).toInt(),
                (12 * density).toInt(),
            )
            setBackgroundColor(0xF0C4453C.toInt())
            elevation = 18f
            isClickable = true
            isFocusable = true
            minWidth = (88 * density).toInt()
            minHeight = (48 * density).toInt()
            contentDescription = "Stop AirPen"
            setOnClickListener { AirPenBackground.stop(app) }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.BOTTOM or Gravity.END
        lp.x = (16 * density).toInt()
        lp.y = (28 * density).toInt()
        handle = OverlayWindows.add(app, btn, lp)
    }

    fun hide() {
        handle?.remove()
        handle = null
    }
}
