package studio.airpen.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import studio.airpen.app.data.AppMode

class HudController(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var view: LinearLayout? = null
    private val hide = Runnable { hide() }
    var enabled: Boolean = true

    fun show(title: String, subtitle: String = "", mode: AppMode = AppMode.GESTURE) {
        if (!enabled) return
        main.post {
            ensure()
            val v = view ?: return@post
            (v.getChildAt(0) as TextView).text = title
            (v.getChildAt(1) as TextView).text = subtitle
            v.visibility = android.view.View.VISIBLE
            main.removeCallbacks(hide)
            main.postDelayed(hide, 1300)
        }
    }

    fun hide() {
        view?.visibility = android.view.View.GONE
    }

    fun detach() {
        main.removeCallbacks(hide)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun ensure() {
        if (view != null) return
        val density = context.resources.displayMetrics.density
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (12 * density).toInt(), (18 * density).toInt(), (12 * density).toInt())
            setBackgroundColor(0xEE141414.toInt())
            elevation = 12f
        }
        val title = TextView(context).apply {
            setTextColor(0xFFD4A84B.toInt())
            textSize = 16f
            paint.isFakeBoldText = true
        }
        val sub = TextView(context).apply {
            setTextColor(0xCCEEEEEE.toInt())
            textSize = 13f
        }
        layout.addView(title)
        layout.addView(sub)
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (72 * density).toInt()
        try {
            wm.addView(layout, lp)
            view = layout
        } catch (_: Throwable) {
        }
    }
}
