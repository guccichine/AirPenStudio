package studio.airpen.app.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import studio.airpen.app.MainActivity
import studio.airpen.app.data.ActionId
import studio.airpen.app.data.AppMode
import studio.airpen.app.data.BoundAction
import studio.airpen.app.data.Macro
import studio.airpen.app.service.AirPenAccessibilityService

class ActionExecutor(private val appContext: Context) {
    var modeChanger: ((AppMode) -> Unit)? = null
    var profileChanger: ((String) -> Unit)? = null
    var macroRunner: ((String) -> Unit)? = null
    var hudToggler: (() -> Unit)? = null
    var cursorCenter: (() -> Unit)? = null
    var cursorHider: (() -> Unit)? = null
    var precisionToggler: (() -> Unit)? = null
    var clickDispatcher: ((ClickKind) -> Unit)? = null
    var scrollDispatcher: ((Float, Float) -> Unit)? = null
    var textInjector: ((String) -> Unit)? = null
    var shiftToggler: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var torchOn = false
    private var muted = false

    enum class ClickKind { LEFT, DOUBLE, RIGHT, DRAG_TOGGLE }

    fun execute(bound: BoundAction) {
        if (bound.id == ActionId.NONE) return
        main.post { runCatching { dispatch(bound) }.onFailure { t -> toast("Action failed: ${t.message}") } }
    }

    fun runMacro(macro: Macro) {
        var delay = 0L
        repeat(macro.repeat.coerceIn(1, 20)) {
            for (step in macro.steps) {
                delay += step.delayMs
                val action = step.action
                main.postDelayed({ execute(action) }, delay)
            }
        }
    }

    private fun dispatch(bound: BoundAction) {
        val svc = AirPenAccessibilityService.instance
        when (bound.id) {
            ActionId.NONE -> Unit
            ActionId.BACK -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            ActionId.HOME -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            ActionId.RECENTS -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            ActionId.NOTIFICATIONS -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            ActionId.QUICK_SETTINGS -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            ActionId.SPLIT_SCREEN -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            ActionId.SCREENSHOT -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            ActionId.POWER_DIALOG -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            ActionId.LOCK_SCREEN -> if (Build.VERSION.SDK_INT >= 28) svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            ActionId.OPEN_APP -> openPackage(bound.arg)
            ActionId.OPEN_URL -> openUrl(bound.arg.ifBlank { "https://www.google.com" })
            ActionId.OPEN_AIRPEN -> {
                val i = Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                appContext.startActivity(i)
            }
            ActionId.ASSISTANT -> launchAction(Intent.ACTION_VOICE_COMMAND)
            ActionId.SEARCH -> launchAction(Intent.ACTION_WEB_SEARCH)
            ActionId.PHONE -> launchAction(Intent.ACTION_DIAL)
            ActionId.MESSAGES -> launchAction(Intent.ACTION_MAIN, "android.intent.category.APP_MESSAGING")
            ActionId.CAMERA_APP -> launchAction("android.media.action.IMAGE_CAPTURE")
            ActionId.BROWSER -> launchAction(Intent.ACTION_VIEW, data = "https://")
            ActionId.SETTINGS_APP -> appContext.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionId.CALCULATOR -> launchAction(Intent.ACTION_MAIN, "android.intent.category.APP_CALCULATOR")
            ActionId.CALENDAR -> launchAction(Intent.ACTION_MAIN, "android.intent.category.APP_CALENDAR")
            ActionId.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            ActionId.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            ActionId.VOLUME_MUTE -> toggleMute()
            ActionId.MEDIA_PLAY_PAUSE -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ActionId.MEDIA_NEXT -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            ActionId.MEDIA_PREV -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            ActionId.MEDIA_STOP -> media(KeyEvent.KEYCODE_MEDIA_STOP)
            ActionId.MEDIA_FAST_FORWARD -> media(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            ActionId.MEDIA_REWIND -> media(KeyEvent.KEYCODE_MEDIA_REWIND)
            ActionId.BRIGHTNESS_UP -> nudgeBrightness(40)
            ActionId.BRIGHTNESS_DOWN -> nudgeBrightness(-40)
            ActionId.FLASHLIGHT -> toggleTorch()
            ActionId.DND_TOGGLE -> dndHint()
            ActionId.ROTATION_TOGGLE -> rotationHint()
            ActionId.MOUSE_CLICK -> clickDispatcher?.invoke(ClickKind.LEFT)
            ActionId.MOUSE_DOUBLE -> clickDispatcher?.invoke(ClickKind.DOUBLE)
            ActionId.MOUSE_RIGHT -> clickDispatcher?.invoke(ClickKind.RIGHT)
            ActionId.MOUSE_DRAG_TOGGLE -> clickDispatcher?.invoke(ClickKind.DRAG_TOGGLE)
            ActionId.SCROLL_UP -> scrollDispatcher?.invoke(0f, -1f)
            ActionId.SCROLL_DOWN -> scrollDispatcher?.invoke(0f, 1f)
            ActionId.SCROLL_LEFT -> scrollDispatcher?.invoke(-1f, 0f)
            ActionId.SCROLL_RIGHT -> scrollDispatcher?.invoke(1f, 0f)
            ActionId.PAGE_UP -> scrollDispatcher?.invoke(0f, -3.2f)
            ActionId.PAGE_DOWN -> scrollDispatcher?.invoke(0f, 3.2f)
            ActionId.MODE_GESTURE -> modeChanger?.invoke(AppMode.GESTURE)
            ActionId.MODE_MOUSE -> modeChanger?.invoke(AppMode.MOUSE)
            ActionId.MODE_TYPE -> modeChanger?.invoke(AppMode.TYPE)
            ActionId.MODE_SCROLL -> modeChanger?.invoke(AppMode.SCROLL)
            ActionId.MODE_POINTER -> modeChanger?.invoke(AppMode.POINTER)
            ActionId.MODE_MEDIA -> modeChanger?.invoke(AppMode.MEDIA)
            ActionId.MODE_CAMERA -> modeChanger?.invoke(AppMode.CAMERA)
            ActionId.MODE_CYCLE -> cycleMode()
            ActionId.TYPE_TEXT -> injectText(bound.arg)
            ActionId.TYPE_SPACE -> injectText(" ")
            ActionId.TYPE_BACKSPACE -> performEdit("backspace")
            ActionId.TYPE_ENTER -> injectText("\n")
            ActionId.TYPE_TAB -> injectText("\t")
            ActionId.TYPE_SHIFT -> shiftToggler?.invoke()
            ActionId.TYPE_COPY -> performEdit("copy")
            ActionId.TYPE_PASTE -> performEdit("paste")
            ActionId.TYPE_CUT -> performEdit("cut")
            ActionId.TYPE_SELECT_ALL -> performEdit("selectAll")
            ActionId.TYPE_UNDO -> injectKey(KeyEvent.KEYCODE_Z, meta = KeyEvent.META_CTRL_ON)
            ActionId.TYPE_REDO -> injectKey(KeyEvent.KEYCODE_Z, meta = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            ActionId.TYPE_PERIOD -> injectText(".")
            ActionId.TYPE_COMMA -> injectText(",")
            ActionId.TYPE_QUESTION -> injectText("?")
            ActionId.KEY_COMBO -> combo(bound.arg)
            ActionId.SWITCH_PROFILE -> profileChanger?.invoke(bound.arg)
            ActionId.RUN_MACRO -> macroRunner?.invoke(bound.arg)
            ActionId.HUD_TOGGLE -> hudToggler?.invoke()
            ActionId.CURSOR_CENTER -> cursorCenter?.invoke()
            ActionId.HIDE_CURSOR -> cursorHider?.invoke()
            ActionId.PRECISION_TOGGLE -> precisionToggler?.invoke()
            ActionId.NOTIFICATION_EXPAND -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    fun injectText(text: String) {
        if (text.isEmpty()) return
        textInjector?.invoke(text)
        val svc = AirPenAccessibilityService.instance ?: return
        val node = svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val existing = node.text?.toString().orEmpty()
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + text)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            val clip = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText("airpen", text))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        node.recycle()
    }

    fun tapAt(x: Float, y: Float, duration: Long = 40L, hold: Long = 40L) {
        val svc = AirPenAccessibilityService.instance ?: return
        val path = Path().apply { moveTo(x, y) }
        svc.dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build(), null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 280L) {
        val svc = AirPenAccessibilityService.instance ?: return
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        svc.dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build(), null, null)
    }

    fun longPressAt(x: Float, y: Float, holdMs: Long = 700L) {
        val svc = AirPenAccessibilityService.instance ?: return
        val path = Path().apply { moveTo(x, y) }
        svc.dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, holdMs)).build(), null, null)
    }

    private fun performEdit(kind: String) {
        val svc = AirPenAccessibilityService.instance ?: return
        val node = svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        when (kind) {
            "copy" -> node.performAction(AccessibilityNodeInfo.ACTION_COPY)
            "paste" -> node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            "cut" -> node.performAction(AccessibilityNodeInfo.ACTION_CUT)
            "selectAll" -> node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
            "backspace" -> {
                val text = node.text?.toString().orEmpty()
                if (text.isNotEmpty()) {
                    val args = android.os.Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text.dropLast(1))
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
            }
        }
        node.recycle()
    }

    private fun cycleMode() {
        val order = AppMode.entries
        val cur = studio.airpen.app.AirPen.engine.mode
        modeChanger?.invoke(order[(order.indexOf(cur) + 1) % order.size])
    }

    private fun openPackage(pkg: String) {
        if (pkg.isBlank()) { toast("No app selected for this gesture"); return }
        val launch = appContext.packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) { toast("App not found: $pkg"); return }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(launch)
    }

    private fun openUrl(url: String) {
        val fixed = if (url.startsWith("http")) url else "https://$url"
        appContext.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fixed)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun launchAction(action: String, category: String? = null, data: String? = null) {
        val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (category != null) i.addCategory(category)
        if (data != null) i.data = android.net.Uri.parse(data)
        runCatching { appContext.startActivity(i) }.onFailure { toast("Can't open ${it.message}") }
    }

    private fun adjustVolume(dir: Int) {
        (appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustVolume(dir, AudioManager.FLAG_SHOW_UI)
    }

    private fun toggleMute() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        muted = !muted
        am.adjustVolume(if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
    }

    private fun media(code: Int) {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun nudgeBrightness(delta: Int) {
        if (!Settings.System.canWrite(appContext)) {
            toast("Grant write-settings to control brightness")
            val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.data = android.net.Uri.parse("package:${appContext.packageName}")
            appContext.startActivity(i)
            return
        }
        val cur = Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (cur + delta).coerceIn(8, 255))
    }

    private fun toggleTorch() {
        val cam = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cam.cameraIdList.firstOrNull() ?: return
        torchOn = !torchOn
        runCatching { cam.setTorchMode(id, torchOn) }.onFailure { torchOn = false; toast("Flashlight unavailable") }
    }

    private fun dndHint() {
        toast("Toggle Do Not Disturb from Quick Settings")
        AirPenAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    private fun rotationHint() {
        toast("Toggle auto-rotate from Quick Settings")
        AirPenAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    private fun combo(spec: String) {
        val parts = spec.uppercase().split("+", " ").filter { it.isNotBlank() }
        var meta = 0
        var key = KeyEvent.KEYCODE_UNKNOWN
        for (p in parts) {
            when (p) {
                "CTRL", "CONTROL", "CTRL_LEFT" -> meta = meta or KeyEvent.META_CTRL_ON
                "ALT", "ALT_LEFT" -> meta = meta or KeyEvent.META_ALT_ON
                "SHIFT", "SHIFT_LEFT" -> meta = meta or KeyEvent.META_SHIFT_ON
                "META", "WIN", "CMD" -> meta = meta or KeyEvent.META_META_ON
                else -> {
                    val f = KeyEvent::class.java.fields.firstOrNull { it.name == "KEYCODE_$p" }
                    if (f != null) key = f.getInt(null)
                }
            }
        }
        if (key != KeyEvent.KEYCODE_UNKNOWN) injectKey(key, meta)
    }

    private fun injectKey(code: Int, meta: Int = 0) {
        when (code) {
            KeyEvent.KEYCODE_ENTER -> injectText("\n")
            KeyEvent.KEYCODE_SPACE -> injectText(" ")
            KeyEvent.KEYCODE_DEL -> performEdit("backspace")
            else -> textInjector?.invoke("")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
    }
}
