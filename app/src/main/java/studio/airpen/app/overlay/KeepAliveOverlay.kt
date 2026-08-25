package studio.airpen.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 1×1 invisible overlay that stays up while the S Pen is connected.
 * Samsung's S Pen Remote SDK drops the BLE session a few seconds after the
 * connecting Activity leaves the foreground unless the process still owns a
 * window. This is that window.
 */
object KeepAliveOverlay {
    private var handle: OverlayWindows.Handle? = null

    fun show(context: Context) {
        if (handle != null) return
        val view = View(context.applicationContext)
        view.setBackgroundColor(0x01000000)
        val lp = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = 0
        handle = OverlayWindows.add(context.applicationContext, view, lp)
    }

    fun hide() {
        handle?.remove()
        handle = null
    }
}
