package studio.airpen.app.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.airpen.app.action.ActionExecutor
import studio.airpen.app.data.ActionId
import studio.airpen.app.data.AppMode
import studio.airpen.app.data.AppStore
import studio.airpen.app.data.GestureId
import studio.airpen.app.gesture.GestureRecognizer
import studio.airpen.app.gesture.Recognition
import studio.airpen.app.gesture.StrokeBuffer
import studio.airpen.app.mouse.AirMouseController
import studio.airpen.app.overlay.HudController
import studio.airpen.app.overlay.KeepAliveOverlay
import studio.airpen.app.overlay.StopOverlay
import studio.airpen.app.spen.PenButton
import studio.airpen.app.spen.PenMotion
import studio.airpen.app.spen.SpenHub
import studio.airpen.app.spen.SpenStatus

class AirPenEngine(
    private val context: Context,
    val store: AppStore,
    val hub: SpenHub,
    val executor: ActionExecutor,
) {
    val recognizer = GestureRecognizer()
    val mouse = AirMouseController(context, executor)
    val hud = HudController(context)
    val stroke = StrokeBuffer()

    private val main = Handler(Looper.getMainLooper())
    private val _mode = MutableStateFlow(sanitizeMode(store.current.general.lastMode))
    val modeFlow: StateFlow<AppMode> = _mode.asStateFlow()
    val mode: AppMode get() = _mode.value

    private val _last = MutableStateFlow(Recognition())
    val lastRecognition: StateFlow<Recognition> = _last.asStateFlow()

    private val _live = MutableStateFlow("Idle")
    val live: StateFlow<String> = _live.asStateFlow()

    private var lastClickAt = 0L
    private var clickCount = 0
    private var longPosted = false
    private var drawing = false
    private var absX = 0f
    private var absY = 0f
    private var lastMotionAt = 0L
    private val clickReset = Runnable { flushClicks() }
    private val longPress = Runnable { onLongPress() }
    private val idleSleep = Runnable {
        if (store.current.general.runInBackground) return@Runnable
        if (hub.status.value == SpenStatus.CONNECTED) return@Runnable
        hub.unregisterMotion()
    }
    private val reconnectPen = Runnable {
        if (!store.current.general.runInBackground) return@Runnable
        try {
            hub.connect()
        } catch (t: Throwable) {
            Log.w(TAG, "reconnect", t)
        }
    }
    private val strokeIdle = Runnable {
        if (!drawing) return@Runnable
        val pts = stroke.snapshot()
        drawing = false
        if (pts.size >= 2) {
            lastStroke = pts
            handleRecognition(recognizer.recognizeStroke(pts, store.current.gesture))
        }
        stroke.clear()
    }

    @Volatile private var wired = false
    @Volatile private var halted = true

    fun start(connectPen: Boolean = false) {
        if (!wired) {
            wired = true
            hub.settings = store.current.gesture
            mouse.settings = store.current.mouse
            hud.enabled = store.current.gesture.showHud
            hub.motionListener = { m ->
                try {
                    onMotion(m)
                } catch (t: Throwable) {
                    Log.e(TAG, "onMotion", t)
                }
            }
            hub.buttonListener = { b ->
                try {
                    onButton(b)
                } catch (t: Throwable) {
                    Log.e(TAG, "onButton", t)
                }
            }
            hub.connectionListener = { st ->
                _live.value = when (st) {
                    SpenStatus.CONNECTED -> "S Pen connected"
                    SpenStatus.UNSUPPORTED -> "S Pen remote not on this device — use the Practice pad"
                    SpenStatus.ERROR -> "S Pen connection error — reconnecting…"
                    SpenStatus.CONNECTING -> "Connecting…"
                    SpenStatus.DISCONNECTED -> if (halted) "Stopped" else "S Pen disconnected"
                    SpenStatus.UNKNOWN -> "Tap Connect S Pen"
                }
                when (st) {
                    SpenStatus.CONNECTED -> {
                        halted = false
                        KeepAliveOverlay.show(context)
                        StopOverlay.show(context)
                        hub.registerListeners()
                    }
                    SpenStatus.DISCONNECTED, SpenStatus.ERROR -> {
                        if (!halted && store.current.general.runInBackground) {
                            main.removeCallbacks(reconnectPen)
                            main.postDelayed(reconnectPen, 800L)
                        }
                    }
                    else -> Unit
                }
            }
            executor.modeChanger = { setMode(it) }
            executor.profileChanger = { id -> store.update { it.copy(activeProfileId = id) } }
            executor.macroRunner = { id ->
                store.current.macros.firstOrNull { it.id == id }?.let { executor.runMacro(it) }
            }
            executor.hudToggler = {
                hud.enabled = !hud.enabled
                store.update { it.copy(gesture = it.gesture.copy(showHud = hud.enabled)) }
            }
            executor.cursorCenter = { mouse.center() }
            executor.cursorHider = { mouse.hide() }
            executor.precisionToggler = { mouse.precision = !mouse.precision }
            executor.clickDispatcher = { mouse.click(it) }
            executor.scrollDispatcher = { dx, dy -> mouse.scroll(dx, dy) }
            executor.stopEngine = {
                studio.airpen.app.service.AirPenBackground.stop(context)
            }
            _live.value = "Tap Connect S Pen"
        }
        if (connectPen) {
            halted = false
            hub.passAllMotion = true
            try {
                hub.connect()
            } catch (t: Throwable) {
                Log.e(TAG, "hub.connect", t)
                _live.value = "S Pen SDK failed to load"
            }
        }
    }

    /** Hard stop: disconnect the pen, drop overlays, cancel in-flight flicks. */
    fun halt() {
        halted = true
        drawing = false
        longPosted = false
        clickCount = 0
        main.removeCallbacks(clickReset)
        main.removeCallbacks(longPress)
        main.removeCallbacks(idleSleep)
        main.removeCallbacks(reconnectPen)
        main.removeCallbacks(strokeIdle)
        try {
            hub.disconnect()
        } catch (t: Throwable) {
            Log.w(TAG, "halt disconnect", t)
        }
        mouse.detach()
        hud.detach()
        KeepAliveOverlay.hide()
        StopOverlay.hide()
        _mode.value = AppMode.GESTURE
        _live.value = "Stopped"
        try {
            store.update { it.copy(general = it.general.copy(lastMode = AppMode.GESTURE, runInBackground = false)) }
        } catch (t: Throwable) {
            Log.w(TAG, "halt persist", t)
        }
    }

    fun stop() = halt()

    fun setMode(mode: AppMode) {
        val next = sanitizeMode(mode)
        if (_mode.value == next) {
            applyMode(next)
            return
        }
        _mode.value = next
        store.update { it.copy(general = it.general.copy(lastMode = next)) }
        applyMode(next)
        buzz(30)
        hud.show(next.name.lowercase().replaceFirstChar { it.titlecase() } + " mode", "AirPen")
    }

    private fun applyMode(mode: AppMode) {
        mouse.settings = store.current.mouse
        when (sanitizeMode(mode)) {
            AppMode.MOUSE, AppMode.POINTER, AppMode.SCROLL -> {
                mouse.detach()
                mouse.attach()
                mouse.show()
                if (!mouse.overlayReady) {
                    _live.value = "Cursor needs Display over other apps + Accessibility"
                }
            }
            else -> {
                mouse.detach()
            }
        }
        hub.registerListeners()
        hub.passAllMotion = true
        _live.value = "Mode: ${sanitizeMode(mode).name}"
    }

    fun onMotion(m: PenMotion) {
        if (halted) return
        lastMotionAt = SystemClock.uptimeMillis()
        scheduleIdle()
        val g = store.current.gesture
        val mag = kotlin.math.abs(m.dx) + kotlin.math.abs(m.dy)
        val gain = if (g.motionGain <= 0.01f) 2.4f else g.motionGain
        when (mode) {
            AppMode.MOUSE, AppMode.POINTER, AppMode.CAMERA -> {
                mouse.move(m.dx, m.dy)
                if (drawing) {
                    absX += m.dx * gain
                    absY += m.dy * gain
                    stroke.add(absX, absY, m.t)
                }
            }
            AppMode.SCROLL -> {
                mouse.attach()
                val sGain = store.current.mouse.scrollGain
                if (kotlin.math.abs(m.dy) > g.deadZone || kotlin.math.abs(m.dx) > g.deadZone) {
                    if (hub.buttonDown.value) {
                        mouse.scroll(-m.dx * sGain, -m.dy * sGain * 1.4f)
                    } else {
                        mouse.move(m.dx, m.dy)
                    }
                }
            }
            AppMode.MEDIA, AppMode.GESTURE, AppMode.TYPE -> {
                val allow = drawing || !g.requireButton || g.autoArm || hub.buttonDown.value
                if (!drawing && mag > g.deadZone && allow) {
                    drawing = true
                    stroke.clear()
                    absX = 0f
                    absY = 0f
                    stroke.add(0f, 0f, m.t)
                }
                if (drawing && allow) {
                    absX += m.dx * gain
                    absY += m.dy * gain
                    stroke.add(absX, absY, m.t)
                    main.removeCallbacks(strokeIdle)
                    main.postDelayed(strokeIdle, g.strokeIdleMs.coerceIn(80L, 800L))
                }
            }
        }
    }

    fun onButton(b: PenButton) {
        if (halted) return
        val now = b.t
        val g = store.current.gesture
        if (b.down) {
            hub.registerListeners()
            hub.passAllMotion = true
            drawing = true
            stroke.clear()
            absX = 0f
            absY = 0f
            stroke.add(0f, 0f, now)
            longPosted = true
            main.removeCallbacks(longPress)
            main.postDelayed(longPress, store.current.general.longPressMs)
        } else {
            drawing = false
            hub.passAllMotion = true
            main.removeCallbacks(longPress)
            main.removeCallbacks(strokeIdle)
            val held = longPosted
            longPosted = false
            val len = stroke.pathLength()
            val moved = len > g.minFlickLength * 0.35f
            when (mode) {
                AppMode.MOUSE, AppMode.POINTER -> {
                    if (!moved) registerClick(now)
                    else if (store.current.mouse.clickOnRelease && held) {
                        mouse.click(ActionExecutor.ClickKind.LEFT)
                    }
                }
                AppMode.SCROLL -> if (!moved) registerClick(now)
                AppMode.GESTURE, AppMode.MEDIA, AppMode.CAMERA, AppMode.TYPE -> {
                    if (moved) finishStroke()
                    else registerClick(now)
                }
            }
        }
    }

    fun feedPractice(points: List<studio.airpen.app.gesture.Pt>) {
        lastStroke = points
        val rec = recognizer.recognizeStroke(points, store.current.gesture)
        handleRecognition(rec)
    }

    private var lastStroke: List<studio.airpen.app.gesture.Pt> = emptyList()

    fun lastDrawn(): List<studio.airpen.app.gesture.Pt> = lastStroke

    private fun finishStroke() {
        main.removeCallbacks(strokeIdle)
        val pts = stroke.snapshot()
        lastStroke = pts
        drawing = false
        if (pts.size < 2) {
            stroke.clear()
            return
        }
        val rec = recognizer.recognizeStroke(pts, store.current.gesture)
        handleRecognition(rec)
        stroke.clear()
    }

    private fun handleRecognition(rec: Recognition) {
        _last.value = rec
        val g = rec.gesture ?: run {
            hud.show("?", rec.notes)
            return
        }
        val bound = store.actionFor(g)
        hud.show("${g.symbol}  ${g.label}", bound.id.label)
        _live.value = "${g.label} → ${bound.id.label}"
        buzz(25)
        executor.execute(bound)
    }

    private fun registerClick(now: Long) {
        val window = store.current.general.doubleClickMs
        if (now - lastClickAt <= window) {
            clickCount += 1
        } else {
            clickCount = 1
        }
        lastClickAt = now
        main.removeCallbacks(clickReset)
        main.postDelayed(clickReset, window + 20)
    }

    private fun flushClicks() {
        val n = clickCount
        clickCount = 0
        val id = when (n) {
            1 -> GestureId.BUTTON_CLICK
            2 -> GestureId.BUTTON_DOUBLE
            else -> GestureId.BUTTON_TRIPLE
        }
        val bound = store.actionFor(id)
        if (mode == AppMode.MOUSE && id == GestureId.BUTTON_CLICK && bound.id == ActionId.MOUSE_CLICK) {
            mouse.click(ActionExecutor.ClickKind.LEFT)
            hud.show("Click", "left")
            return
        }
        hud.show(id.label, bound.id.label)
        executor.execute(bound)
    }

    private fun onLongPress() {
        longPosted = false
        if (stroke.pathLength() > store.current.gesture.minFlickLength) return
        val bound = store.actionFor(GestureId.BUTTON_LONG)
        hud.show("Long press", bound.id.label)
        executor.execute(bound)
    }

    private fun scheduleIdle() {
        if (store.current.general.runInBackground) return
        if (!store.current.gesture.batterySaver) return
        main.removeCallbacks(idleSleep)
        main.postDelayed(idleSleep, store.current.gesture.idleSleepMs)
    }

    private fun buzz(ms: Long) {
        if (!store.current.gesture.haptic) return
        try {
            val v = if (android.os.Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(VibratorManager::class.java)).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "AirPenEngine"

        fun sanitizeMode(mode: AppMode): AppMode =
            if (mode == AppMode.TYPE) AppMode.GESTURE else mode
    }
}
