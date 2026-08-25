package studio.airpen.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import studio.airpen.app.engine.AirPenEngine

object KeyboardOverlay {
    private var view: AirKeyboardView? = null
    private var wm: WindowManager? = null

    fun attach(context: Context, engine: AirPenEngine) {
        if (view != null) return
        val v = AirKeyboardView(context, engine)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.BOTTOM
        val handle = OverlayWindows.add(context, v, lp)
        if (handle != null) {
            view = v
            wm = handle.wm
        }
    }

    fun detach() {
        view?.let { v -> runCatching { wm?.removeView(v) } }
        view = null
        wm = null
    }

    fun hit(x: Float, y: Float): String? = view?.keyAt(x, y)

    fun highlight(x: Float, y: Float) {
        view?.highlight(x, y)
    }
}

class AirKeyboardView(
    context: Context,
    private val engine: AirPenEngine,
) : View(context) {
    private val rows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
        listOf(",", " ", " ", " ", ".", "⏎"),
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rects = ArrayList<Pair<RectF, String>>()
    private var hx = -1f
    private var hy = -1f

    init {
        val h = (220 * resources.displayMetrics.density).toInt()
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            h,
        )
        setBackgroundColor(0xE6101014.toInt())
        setPadding(8, 8, 8, 8)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (228 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuild(w, h)
    }

    private fun rebuild(w: Int, h: Int) {
        rects.clear()
        val pad = 6f
        val rowH = (h - pad * 2) / rows.size
        rows.forEachIndexed { ri, row ->
            val weights = row.map { k ->
                when (k) {
                    " " -> 3f
                    "⇧", "⌫", "⏎" -> 1.4f
                    else -> 1f
                }
            }
            val total = weights.sum()
            var x = pad
            val y = pad + ri * rowH
            row.forEachIndexed { i, key ->
                val kw = (w - pad * 2) * (weights[i] / total)
                rects += RectF(x, y, x + kw - 4, y + rowH - 4) to key
                x += kw
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        for ((r, key) in rects) {
            val hit = r.contains(hx, hy)
            paint.style = Paint.Style.FILL
            paint.color = if (hit) 0xFFD4A84B.toInt() else 0xFF2A2A30.toInt()
            canvas.drawRoundRect(r, 10f, 10f, paint)
            paint.color = if (hit) 0xFF111111.toInt() else 0xFFEEEEEE.toInt()
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 14f * density
            val label = when (key) {
                " " -> "space"
                else -> if (engine.typer.shift || engine.typer.capsLock) key.uppercase() else key
            }
            canvas.drawText(label, r.centerX(), r.centerY() + 5 * density, paint)
        }
    }

    fun highlight(x: Float, y: Float) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        hx = x - loc[0]
        hy = y - loc[1]
        invalidate()
    }

    fun keyAt(x: Float, y: Float): String? {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val lx = x - loc[0]
        val ly = y - loc[1]
        return rects.firstOrNull { it.first.contains(lx, ly) }?.second
    }
}
