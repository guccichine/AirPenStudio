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
import studio.airpen.app.data.BoundAction
import studio.airpen.app.data.GestureId
import studio.airpen.app.gesture.GestureRecognizer
import studio.airpen.app.gesture.MotionFilter
import studio.airpen.app.gesture.Recognition
import studio.airpen.app.gesture.StrokeBuffer
import studio.airpen.app.mouse.AirMouseController
import studio.airpen.app.overlay.HudController
import studio.airpen.app.overlay.KeepAliveOverlay
import studio.airpen.app.overlay.KeyboardOverlay
import studio.airpen.app.spen.PenButton
import studio.airpen.app.spen.PenMotion
import studio.airpen.app.spen.SpenHub
import studio.airpen.app.spen.SpenStatus
import studio.airpen.app.type.AirTypeEngine

class AirPenEngine(
    private val context: Context,
    val store: AppStore,
    val hub: SpenHub,
    val executor: ActionExecutor,
) {
    val recognizer = GestureRecognizer()
    val motionFilter = MotionFilter()
    val mouse = AirMouseController(context, executor)
    val hud = HudController(context)
    val typer = AirTypeEngine()
    val stroke = StrokeBuffer()

    private val main = Handler(Looper.getMainLooper())
    private val _mode = MutableStateFlow(store.current.general.lastMode)
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

    @Volatile private var wired = false

    fun start(connectPen: Boolean = false) {
        if (!wired) {
            wired = true
            hub.settings = store.current.gesture
            mouse.settings = store.current.mouse
            typer.settings = store.current.type
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
                    SpenStatus.DISCONNECTED -> "S Pen disconnected"
                    SpenStatus.UNKNOWN -> "Tap Connect S Pen"
                }
                when (st) {
                    SpenStatus.CONNECTED -> {
                        KeepAliveOverlay.show(context)
                        hub.registerListeners()
                    }
                    SpenStatus.DISCONNECTED, SpenStatus.ERROR -> {
                        if (store.current.general.runInBackground) {
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
            executor.shiftToggler = { typer.shift = !typer.shift }
            executor.capsToggler = { typer.capsLock = !typer.capsLock; hud.show("Caps", if (typer.capsLock) "ON" else "off") }
            executor.textInjector = { /* accessibility path in executor */ }
            _live.value = "Tap Connect S Pen"
        }
        if (connectPen) {
            try {
                hub.connect()
            } catch (t: Throwable) {
                Log.e(TAG, "hub.connect", t)
                _live.value = "S Pen SDK failed to load"
            }
        }
    }

    fun stop() {
        hub.motionListener = null
        hub.buttonListener = null
        mouse.detach()
        hud.detach()
        KeyboardOverlay.detach()
    }

    fun setMode(mode: AppMode) {
        if (_mode.value == mode) {
            applyMode(mode)
            return
        }
        _mode.value = mode
        store.update { it.copy(general = it.general.copy(lastMode = mode)) }
        applyMode(mode)
        buzz(30)
        hud.show(mode.name.lowercase().replaceFirstChar { it.titlecase() } + " mode", "AirPen")
    }

    private fun applyMode(mode: AppMode) {
        mouse.settings = store.current.mouse
        when (mode) {
            AppMode.MOUSE, AppMode.POINTER, AppMode.SCROLL -> {
                mouse.detach()
                mouse.attach()
                mouse.show()
                KeyboardOverlay.detach()
                if (!mouse.overlayReady) {
                    _live.value = "Cursor needs Display over other apps + Accessibility"
                }
            }
            AppMode.TYPE -> {
                if (store.current.type.engine != "write") {
                    mouse.detach()
                    mouse.attach()
                    mouse.show()
                    KeyboardOverlay.detach()
                    KeyboardOverlay.attach(context, this)
                } else {
                    mouse.detach()
                    KeyboardOverlay.detach()
                }
            }
            else -> {
                mouse.detach(); KeyboardOverlay.detach()
            }
        }
        hub.registerListeners()
        _live.value = "Mode: ${mode.name}"
    }

    fun onMotion(m: PenMotion) {
        lastMotionAt = SystemClock.uptimeMillis()
        scheduleIdle()
        val g = store.current.gesture
        val filtered = motionFilter.step(m.dx, m.dy, m.t, g, drawing) ?: return
        val dx = filtered.first
        val dy = filtered.second
        absX += dx
        absY += dy
        when (mode) {
            AppMode.MOUSE, AppMode.POINTER, AppMode.CAMERA -> {
                mouse.move(dx, dy)
                if (drawing) stroke.add(absX, absY, m.t)
            }
            AppMode.SCROLL -> {
                mouse.attach()
                val gain = store.current.mouse.scrollGain
                if (kotlin.math.abs(dy) > g.deadZone || kotlin.math.abs(dx) > g.deadZone) {
                    if (hub.buttonDown.value) {
                        mouse.scroll(-dx * gain, -dy * gain * 1.4f)
                    } else {
                        mouse.move(dx, dy)
                    }
                }
            }
            AppMode.MEDIA -> {
                if (drawing || !g.requireButton) stroke.add(absX, absY, m.t)
            }
            AppMode.GESTURE -> {
                if (drawing || !g.requireButton) {
                    stroke.add(absX, absY, m.t)
                }
            }
            AppMode.TYPE -> {
                if (store.current.type.engine != "write") {
                    mouse.move(dx, dy)
                    KeyboardOverlay.highlight(mouse.x, mouse.y)
                }
                if (drawing || !g.requireButton) {
                    stroke.add(absX, absY, m.t)
                }
            }
        }
    }

    fun onButton(b: PenButton) {
        val now = b.t
        val g = store.current.gesture
        if (b.down) {
            hub.registerListeners()
            hub.passAllMotion = true
            drawing = true
            motionFilter.reset()
            stroke.clear()
            absX = 0f
            absY = 0f
            stroke.add(0f, 0f, now)
            longPosted = true
            main.removeCallbacks(longPress)
            main.postDelayed(longPress, store.current.general.longPressMs)
            if (mode == AppMode.MOUSE && mouse.dragLock) {
                // keep dragging
            }
        } else {
            drawing = false
            hub.passAllMotion = false
            main.removeCallbacks(longPress)
            val held = longPosted
            longPosted = false
            val len = stroke.pathLength()
            val moved = len > g.minFlickLength * 0.5f
            when (mode) {
                AppMode.MOUSE, AppMode.POINTER -> {
                    if (!moved) registerClick(now)
                    else if (store.current.mouse.clickOnRelease && held) {
                        mouse.click(ActionExecutor.ClickKind.LEFT)
                    }
                }
                AppMode.SCROLL -> if (!moved) registerClick(now)
                AppMode.TYPE -> finishStroke(typeMode = true)
                AppMode.GESTURE, AppMode.MEDIA, AppMode.CAMERA -> {
                    if (moved) finishStroke(typeMode = false)
                    else registerClick(now)
                }
            }
        }
    }

    fun feedPractice(points: List<studio.airpen.app.gesture.Pt>, typeMode: Boolean = false) {
        lastStroke = points
        val rec = recognizer.recognizeStroke(
            points,
            store.current.gesture,
            typeMode,
            store.current.letterSamples,
            typer.buffer.toString(),
            store.current.type.minConfidence,
        )
        handleRecognition(rec, typeMode)
    }

    private var lastStroke: List<studio.airpen.app.gesture.Pt> = emptyList()

    fun lastDrawn(): List<studio.airpen.app.gesture.Pt> = lastStroke

    fun trainLastLetter(letter: String) {
        if (lastStroke.size >= 4 && letter.isNotBlank()) {
            store.addLetterSample(letter, lastStroke)
            hud.show(letter, "trained")
        }
    }

    private fun finishStroke(typeMode: Boolean) {
        var pts = stroke.snapshot()
        if (typeMode && store.current.type.invertAirY) {
            pts = pts.map { it.copy(y = -it.y) }
        }
        lastStroke = pts
        val rec = recognizer.recognizeStroke(
            pts,
            store.current.gesture,
            typeMode,
            store.current.letterSamples,
            typer.buffer.toString(),
            store.current.type.minConfidence,
        )
        handleRecognition(rec, typeMode)
        stroke.clear()
    }

    private fun handleRecognition(rec: Recognition, typeMode: Boolean) {
        _last.value = rec
        if (typeMode) {
            val letter = rec.letter
            if (letter != null) {
                when (letter) {
                    "⌫" -> {
                        typer.backspace()
                        executor.execute(BoundAction(ActionId.TYPE_BACKSPACE))
                    }
                    " " -> executor.execute(BoundAction(ActionId.TYPE_SPACE))
                    "\n" -> executor.execute(BoundAction(ActionId.TYPE_ENTER))
                    "⇧" -> {
                        typer.shift = !typer.shift
                        hud.show("Shift", if (typer.shift) "ON" else "off")
                    }
                    else -> {
                        val out = typer.consumeLetter(letter)
                        if (out.isNotEmpty()) executor.injectText(out)
                    }
                }
                hud.show(letter, "score ${(rec.score * 100).toInt()}%" +
                    rec.alternatives.drop(1).take(2).joinToString("") { " · ${it.first}" })
                buzz(20)
                return
            }
        }
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
        if (mode == AppMode.TYPE && id == GestureId.BUTTON_CLICK) {
            val key = KeyboardOverlay.hit(mouse.x, mouse.y)
            if (key != null) {
                typeKey(key)
                return
            }
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

    private fun typeKey(key: String) {
        when (key) {
            "⌫" -> executor.execute(BoundAction(ActionId.TYPE_BACKSPACE))
            " " -> executor.execute(BoundAction(ActionId.TYPE_SPACE))
            "⏎" -> executor.execute(BoundAction(ActionId.TYPE_ENTER))
            "⇧" -> {
                typer.shift = !typer.shift
                hud.show("Shift", if (typer.shift) "ON" else "off")
            }
            else -> {
                val out = typer.consumeLetter(key)
                if (out.isNotEmpty()) executor.injectText(out)
                hud.show(out.ifBlank { key }, "key")
            }
        }
        buzz(18)
    }

    private fun onLongPress() {
        longPosted = false
        if (stroke.pathLength() > store.current.gesture.minFlickLength) return
        val bound = store.actionFor(GestureId.BUTTON_LONG)
        hud.show("Long press", bound.id.label)
        executor.execute(bound)
    }

    private fun scheduleIdle() {
        // Never drop air-motion while the S Pen should stay connected.
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
    }
}
