package studio.airpen.app.ui

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.MutableStateFlow
import studio.airpen.app.AirPen
import studio.airpen.app.data.ActionId
import studio.airpen.app.data.AppMode
import studio.airpen.app.data.BoundAction

object HoverBus {
    val stylusHeld = MutableStateFlow(false)
    val hovering = MutableStateFlow(false)

    fun onKey(event: KeyEvent): Boolean {
        val stylus = event.keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON ||
            event.source and android.view.InputDevice.SOURCE_STYLUS != 0
        if (stylus) {
            stylusHeld.value = event.action != KeyEvent.ACTION_UP
        } else if (AirPen.isReady && AirPen.hub.buttonDown.value) {
            stylusHeld.value = event.action != KeyEvent.ACTION_UP
        }
        return false
    }

    fun onGeneric(event: MotionEvent): Boolean {
        val held = event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY) ||
            (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
            (AirPen.isReady && AirPen.hub.buttonDown.value)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hovering.value = true
                stylusHeld.value = held
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hovering.value = false
                if (!held) stylusHeld.value = AirPen.isReady && AirPen.hub.buttonDown.value
            }
        }
        return false
    }
}

private const val SAMPLE_VIDEO =
    "https://commondatastorage.googleapis.com/gtv-videos-library/sample/ForBiggerBlazes.mp4"

@Composable
fun HoverLab() {
    val host = remember { HoverLabHost() }
    DisposableEffect(Unit) { onDispose { host.release() } }
    AndroidView(
        factory = { host.build(it) },
        modifier = Modifier.fillMaxWidth().height(420.dp),
    )
}

private class HoverLabHost {
    private var video: VideoView? = null
    private var videoBox: FrameLayout? = null
    private var expandHost: FrameLayout? = null
    private var previewPlaying = false
    private var expanded = false
    private var destCard: TextView? = null

    fun release() {
        runCatching {
            video?.stopPlayback()
            video?.suspend()
        }
        video = null
    }

    @SuppressLint("ClickableViewAccessibility")
    fun build(ctx: android.content.Context): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF181A20.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val status = TextView(ctx).apply {
            text = "Hover the S Pen over the box, buttons or tabs"
            setTextColor(0xFFD4A84B.toInt())
            textSize = 13f
        }
        root.addView(status)

        expandHost = FrameLayout(ctx)
        videoBox = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160))
            background = boxDrawable(false)
        }
        video = VideoView(ctx).apply {
            setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
            setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                if (previewPlaying) start() else pause()
            }
            setVideoURI(Uri.parse(SAMPLE_VIDEO))
        }
        val videoLabel = TextView(ctx).apply {
            text = "VIDEO  ·  hover to preview  ·  hold side button to expand"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(0x66000000)
        }
        videoBox!!.addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        videoBox!!.addView(videoLabel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM })
        expandHost!!.addView(videoBox)
        root.addView(expandHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)))

        attachHover(videoBox!!) { ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    status.text = "Hover ENTER · muted preview"
                    videoBox!!.background = boxDrawable(true)
                    startPreview()
                }
                MotionEvent.ACTION_HOVER_MOVE -> {
                    val held = stylusHeld(ev)
                    status.text = if (held) "Air Action HOLD · expanding" else "Hover MOVE · preview"
                    expand(held)
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    status.text = "Hover EXIT · preview reset"
                    videoBox!!.background = boxDrawable(false)
                    expand(false)
                    stopPreview()
                }
            }
        }

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        listOf("Connect", "Cycle mode", "Pointer").forEachIndexed { i, label ->
            val btn = glowButton(ctx, label, dp)
            attachHover(btn) { ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER -> {
                        btn.background = glowDrawable(true)
                        status.text = "Button outline · $label"
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> btn.background = glowDrawable(false)
                }
            }
            btn.setOnClickListener {
                if (!AirPen.isReady) return@setOnClickListener
                when (i) {
                    1 -> AirPen.executor.execute(BoundAction(ActionId.MODE_CYCLE))
                    2 -> AirPen.engine.setMode(AppMode.POINTER)
                }
            }
            btnRow.addView(btn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        }
        root.addView(btnRow)

        destCard = TextView(ctx).apply {
            visibility = View.GONE
            setTextColor(0xFFE8E6E1.toInt())
            textSize = 13f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = cardDrawable()
        }
        val tabRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        data class Dest(val tab: String, val title: String, val body: String)
        listOf(
            Dest("Gestures", "Gestures tab", "Map flicks and the six live shapes."),
            Dest("Mouse", "Mouse tab", "Air-mouse sensitivity, trail, and pointer cursor."),
            Dest("Home", "Home tab", "Connect S Pen, practice pad, hover lab."),
        ).forEach { dest ->
            val tab = glowButton(ctx, dest.tab, dp)
            attachHover(tab) { ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                        tab.background = glowDrawable(true)
                        destCard!!.visibility = View.VISIBLE
                        destCard!!.text = "${dest.title}\n${dest.body}"
                        status.text = "Destination card · ${dest.tab}"
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        tab.background = glowDrawable(false)
                        destCard!!.visibility = View.GONE
                    }
                }
            }
            tabRow.addView(tab, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        }
        root.addView(tabRow)
        root.addView(destCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        return root
    }

    private fun stylusHeld(ev: MotionEvent): Boolean {
        val fromEvent = ev.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY) ||
            (ev.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        return fromEvent || HoverBus.stylusHeld.value || (AirPen.isReady && AirPen.hub.buttonDown.value)
    }

    private fun startPreview() {
        previewPlaying = true
        video?.let { v -> runCatching { if (!v.isPlaying) v.start() } }
    }

    private fun stopPreview() {
        previewPlaying = false
        video?.let { v -> runCatching { v.pause(); v.seekTo(1) } }
    }

    private fun expand(on: Boolean) {
        if (on == expanded) return
        if (on && !previewPlaying) return
        expanded = on
        val box = videoBox ?: return
        val host = expandHost ?: return
        val lp = box.layoutParams
        lp.height = if (on) host.resources.displayMetrics.heightPixels / 3 else (160 * host.resources.displayMetrics.density).toInt()
        box.layoutParams = lp
        box.background = boxDrawable(true)
        if (on) startPreview()
    }

    private fun attachHover(view: View, block: (MotionEvent) -> Unit) {
        view.setOnHoverListener { _, event ->
            HoverBus.onGeneric(event)
            block(event)
            true
        }
    }

    private fun glowButton(ctx: android.content.Context, label: String, dp: (Int) -> Int): TextView {
        return TextView(ctx).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(0xFFE8E6E1.toInt())
            textSize = 13f
            background = glowDrawable(false)
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    private fun boxDrawable(hot: Boolean) = GradientDrawable().apply {
        setColor(0xFF101217.toInt())
        cornerRadius = 28f
        setStroke(if (hot) 8 else 2, if (hot) 0xE6D4A84B.toInt() else 0x66D4A84B)
    }

    private fun glowDrawable(hot: Boolean) = GradientDrawable().apply {
        setColor(if (hot) 0x3324C36A.toInt() else 0xFF22242C.toInt())
        cornerRadius = 22f
        setStroke(if (hot) 8 else 2, if (hot) 0xFFD4A84B.toInt() else 0xFF3A3D48.toInt())
    }

    private fun cardDrawable() = GradientDrawable().apply {
        setColor(0xFF22242C.toInt())
        cornerRadius = 22f
        setStroke(4, 0xFFD4A84B.toInt())
    }
}
