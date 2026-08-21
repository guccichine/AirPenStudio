package studio.airpen.app.type

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import studio.airpen.app.AirPen
import studio.airpen.app.data.AppMode

class AirImeService : InputMethodService() {
    private var status: TextView? = null

    override fun onCreateInputView(): View {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF16161A.toInt())
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
        }
        status = TextView(this).apply {
            text = "Air Type — hold the S Pen button and write a letter in the air"
            setTextColor(0xFFD4A84B.toInt())
            textSize = 14f
        }
        root.addView(status)
        val row = LinearLayout(this)
        fun chip(label: String, onClick: () -> Unit) {
            val b = Button(this).apply {
                text = label
                setOnClickListener { onClick() }
            }
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        chip("⌫") { currentInputConnection?.deleteSurroundingText(1, 0) }
        chip("space") { currentInputConnection?.commitText(" ", 1) }
        chip("⏎") { currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE) }
        chip("Mouse") { if (AirPen.isReady) AirPen.engine.setMode(AppMode.MOUSE) }
        root.addView(row)
        val hint = TextView(this).apply {
            text = "Flicks: ← backspace  → space  ↓ enter  ↑ shift\nShapes: circle = undo   check = enter   X = backspace"
            setTextColor(0xCCEEEEEE.toInt())
            textSize = 12f
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        root.addView(hint)
        try {
            AirPen.ensure(applicationContext)
            if (AirPen.isReady) {
                AirPen.engine.setMode(AppMode.TYPE)
                AirPen.engine.executor.textInjector = { text ->
                    currentInputConnection?.commitText(text, 1)
                }
            }
        } catch (_: Throwable) {
        }
        return root
    }

    override fun onFinishInput() {
        super.onFinishInput()
        if (AirPen.isReady) {
            AirPen.engine.executor.textInjector = null
        }
    }
}
