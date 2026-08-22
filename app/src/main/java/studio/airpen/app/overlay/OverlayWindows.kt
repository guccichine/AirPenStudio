package studio.airpen.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import studio.airpen.app.service.AirPenAccessibilityService

/**
 * Adds overlay views using whatever token this phone will actually accept.
 * TYPE_ACCESSIBILITY_OVERLAY from the app process is rejected on One UI,
 * which is why the mouse cursor never appeared.
 */
object OverlayWindows {
    data class Handle(val wm: WindowManager, val view: View) {
        fun remove() {
            runCatching { wm.removeView(view) }
        }
    }

    fun add(appContext: Context, view: View, lp: WindowManager.LayoutParams): Handle? {
        val appWm = appContext.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val a11y = AirPenAccessibilityService.instance
        val a11yWm = a11y?.let { it.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
        val overlayOk = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(appContext)

        val tries = ArrayList<Pair<WindowManager, Int>>()
        if (overlayOk) {
            tries += appWm to applicationOverlayType()
        }
        if (a11yWm != null) {
            tries += a11yWm to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            if (overlayOk) tries += a11yWm to applicationOverlayType()
        }
        if (Build.VERSION.SDK_INT >= 26) {
            tries += appWm to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }

        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lp.format = PixelFormat.TRANSLUCENT

        val seen = HashSet<String>()
        for ((wm, type) in tries) {
            val key = System.identityHashCode(wm).toString() + ":" + type
            if (!seen.add(key)) continue
            lp.type = type
            try {
                wm.addView(view, lp)
                Log.i(TAG, "overlay added type=$type")
                return Handle(wm, view)
            } catch (t: Throwable) {
                Log.w(TAG, "overlay type=$type failed", t)
            }
        }
        Log.e(TAG, "could not add overlay (overlayPerm=$overlayOk a11y=${a11y != null})")
        return null
    }

    private fun applicationOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private const val TAG = "OverlayWindows"
}
