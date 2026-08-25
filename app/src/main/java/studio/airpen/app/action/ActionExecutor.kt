package studio.airpen.app.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
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
    var capsToggler: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var torchOn = false
    private var muted = false
    private var lastPageAt = 0L

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
            ActionId.CONTACTS -> launchAction(Intent.ACTION_VIEW, data = "content://contacts/people/")
            ActionId.GALLERY -> openFirst("com.sec.android.gallery3d", "com.google.android.apps.photos", "com.android.gallery3d")
            ActionId.FILES -> openFirst("com.sec.android.app.myfiles", "com.google.android.apps.nbu.files", "com.android.documentsui")
            ActionId.PLAY_STORE -> openFirst("com.android.vending")
            ActionId.YOUTUBE -> openFirst("com.google.android.youtube")
            ActionId.MAPS -> openFirst("com.google.android.apps.maps")
            ActionId.GMAIL -> openFirst("com.google.android.gm")
            ActionId.CHROME -> openFirst("com.android.chrome", "com.sec.android.app.sbrowser")
            ActionId.SPOTIFY -> openFirst("com.spotify.music")
            ActionId.WHATSAPP -> openFirst("com.whatsapp")
            ActionId.INSTAGRAM -> openFirst("com.instagram.android")
            ActionId.TIKTOK -> openFirst("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
            ActionId.CLOCK -> openFirst("com.sec.android.app.clockpackage", "com.google.android.deskclock")
            ActionId.NOTES -> openFirst("com.samsung.android.app.notes", "com.samsung.android.snote", "com.google.android.keep")
            ActionId.DOWNLOADS -> openFirst("com.android.providers.downloads.ui", "com.samsung.android.downloads")
            ActionId.WIFI_SETTINGS -> settingsPage(Settings.ACTION_WIFI_SETTINGS)
            ActionId.BLUETOOTH_SETTINGS -> settingsPage(Settings.ACTION_BLUETOOTH_SETTINGS)
            ActionId.NFC_SETTINGS -> settingsPage(Settings.ACTION_NFC_SETTINGS)
            ActionId.SOUND_SETTINGS -> settingsPage(Settings.ACTION_SOUND_SETTINGS)
            ActionId.DISPLAY_SETTINGS -> settingsPage(Settings.ACTION_DISPLAY_SETTINGS)
            ActionId.BATTERY_SETTINGS -> settingsPage(Intent.ACTION_POWER_USAGE_SUMMARY)
            ActionId.LOCATION_SETTINGS -> settingsPage(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
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
            // Exactly one viewport in the air-gesture direction. No extra fling.
            ActionId.SCROLL_UP -> scrollOnePage(flickUp = true)
            ActionId.SCROLL_DOWN -> scrollOnePage(flickUp = false)
            ActionId.SCROLL_LEFT -> scrollOnePageHorizontal(flickLeft = true)
            ActionId.SCROLL_RIGHT -> scrollOnePageHorizontal(flickLeft = false)
            ActionId.PAGE_UP -> scrollOnePage(flickUp = true)
            ActionId.PAGE_DOWN -> scrollOnePage(flickUp = false)
            ActionId.SCROLL_TOP -> jumpScroll(toTop = true)
            ActionId.SCROLL_BOTTOM -> jumpScroll(toTop = false)
            ActionId.REFRESH -> pullRefresh()
            ActionId.ZOOM_IN -> pinch(zoomIn = true)
            ActionId.ZOOM_OUT -> pinch(zoomIn = false)
            ActionId.SHARE -> shareCurrent()
            ActionId.CLOSE_APP -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            ActionId.DPAD_UP -> dpad(16)
            ActionId.DPAD_DOWN -> dpad(17)
            ActionId.DPAD_LEFT -> dpad(18)
            ActionId.DPAD_RIGHT -> dpad(19)
            ActionId.DPAD_CENTER -> dpad(20)
            ActionId.DISMISS_SHADE -> if (Build.VERSION.SDK_INT >= 31) svc?.performGlobalAction(15)
            ActionId.ACCESSIBILITY_ALL_APPS -> if (Build.VERSION.SDK_INT >= 31) svc?.performGlobalAction(14)
            ActionId.MODE_GESTURE -> modeChanger?.invoke(AppMode.GESTURE)
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
            ActionId.TYPE_EXCLAIM -> injectText("!")
            ActionId.TYPE_AT -> injectText("@")
            ActionId.TYPE_HASH -> injectText("#")
            ActionId.TYPE_APOSTROPHE -> injectText("'")
            ActionId.TYPE_QUOTE -> injectText("\"")
            ActionId.TYPE_COLON -> injectText(":")
            ActionId.TYPE_SLASH -> injectText("/")
            ActionId.TYPE_DASH -> injectText("-")
            ActionId.TYPE_CAPS_LOCK -> capsToggler?.invoke()
            ActionId.TYPE_DELETE_WORD -> deleteWord()
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

    /**
     * Flick-driven scroll. dirY < 0 is flick up (finger swipe up — later content).
     * Always exactly one page — Accessibility PAGE/SCROLL action, else one viewport drag
     * slow enough that the system will not fling extra pages.
     */
    fun scrollScreen(dirX: Float, dirY: Float) {
        val vertical = kotlin.math.abs(dirY) >= kotlin.math.abs(dirX)
        if (vertical) scrollOnePage(flickUp = dirY < 0) else scrollOnePageHorizontal(flickLeft = dirX < 0)
    }

    private fun scrollOnePage(flickUp: Boolean) {
        if (AirPenAccessibilityService.instance == null) {
            toast("Turn on Accessibility so AirPen can scroll")
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastPageAt < 300L) return
        lastPageAt = now
        if (pageViaNode(vertical = true, flickPositive = flickUp)) return
        swipeOneViewport(vertical = true, flickPositive = flickUp)
    }

    private fun scrollOnePageHorizontal(flickLeft: Boolean) {
        if (AirPenAccessibilityService.instance == null) {
            toast("Turn on Accessibility so AirPen can scroll")
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastPageAt < 300L) return
        lastPageAt = now
        if (pageViaNode(vertical = false, flickPositive = flickLeft)) return
        swipeOneViewport(vertical = false, flickPositive = flickLeft)
    }

    private fun pageViaNode(vertical: Boolean, flickPositive: Boolean): Boolean {
        val svc = AirPenAccessibilityService.instance ?: return false
        val root = svc.rootInActiveWindow ?: return false
        try {
            val pageId = pageActionId(vertical, flickPositive)
            val scrollId = if (flickPositive) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                try {
                    if (tryPageActions(focused, pageId)) return true
                } finally {
                    focused.recycle()
                }
            }
            val node = findBestScrollable(root, pageId, scrollId) ?: return false
            try {
                return tryPageActions(node, pageId)
            } finally {
                node.recycle()
            }
        } catch (t: Throwable) {
            return false
        } finally {
            root.recycle()
        }
    }

    private fun pageActionId(vertical: Boolean, flickPositive: Boolean): Int {
        if (Build.VERSION.SDK_INT < 29) return 0
        val action = when {
            vertical && flickPositive -> AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN
            vertical && !flickPositive -> AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP
            !vertical && flickPositive -> AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT
            else -> AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT
        }
        return action.id
    }

    private fun tryPageActions(node: AccessibilityNodeInfo, pageId: Int): Boolean {
        // PAGE_* is a real viewport. ACTION_SCROLL_* is often one list item — skip it.
        if (pageId != 0 && node.actionList.any { it.id == pageId }) {
            if (node.performAction(pageId)) return true
        }
        return false
    }

    private fun findBestScrollable(root: AccessibilityNodeInfo, pageId: Int, scrollId: Int): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val rect = Rect()
        fun walk(n: AccessibilityNodeInfo) {
            val can = n.isScrollable ||
                n.actionList.any { it.id == scrollId || (pageId != 0 && it.id == pageId) }
            if (can) {
                n.getBoundsInScreen(rect)
                val area = rect.width() * rect.height()
                if (area > bestArea) {
                    best?.recycle()
                    best = AccessibilityNodeInfo.obtain(n)
                    bestArea = area
                }
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c)
                c.recycle()
            }
        }
        walk(root)
        return best
    }

    /**
     * One full viewport drag. Long duration so velocity stays below the fling
     * threshold — otherwise Chrome / RecyclerView skip extra pages.
     * flickPositive (up / left) = finger moves toward the start of the axis.
     */
    private fun swipeOneViewport(vertical: Boolean, flickPositive: Boolean) {
        val metrics = appContext.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val padV = h * 0.10f
        val padH = w * 0.10f
        val cx = w / 2f
        val cy = h * 0.50f
        val x1: Float
        val y1: Float
        val x2: Float
        val y2: Float
        if (vertical) {
            x1 = cx
            x2 = cx
            if (flickPositive) {
                y1 = h - padV
                y2 = padV
            } else {
                y1 = padV
                y2 = h - padV
            }
        } else {
            y1 = cy
            y2 = cy
            if (flickPositive) {
                x1 = w - padH
                x2 = padH
            } else {
                x1 = padH
                x2 = w - padH
            }
        }
        swipe(x1, y1, x2, y2, 560L)
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

    private fun openFirst(vararg pkgs: String) {
        for (p in pkgs) {
            val launch = appContext.packageManager.getLaunchIntentForPackage(p) ?: continue
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { appContext.startActivity(launch); return }
        }
        toast("App not installed")
    }

    private fun settingsPage(action: String) {
        runCatching {
            appContext.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { toast("Can't open settings") }
    }

    private fun jumpScroll(toTop: Boolean) {
        repeat(5) { i ->
            main.postDelayed({
                lastPageAt = 0L
                swipeOneViewport(vertical = true, flickPositive = toTop)
            }, i * 200L)
        }
    }

    private fun pullRefresh() {
        val metrics = appContext.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        swipe(w / 2f, h * 0.22f, w / 2f, h * 0.72f, 420L)
    }

    private fun pinch(zoomIn: Boolean) {
        val svc = AirPenAccessibilityService.instance ?: return
        val metrics = appContext.resources.displayMetrics
        val cx = metrics.widthPixels / 2f
        val cy = metrics.heightPixels / 2f
        val near = 48f
        val far = 180f
        val a0 = if (zoomIn) near else far
        val a1 = if (zoomIn) far else near
        val p1 = Path().apply { moveTo(cx - a0, cy); lineTo(cx - a1, cy) }
        val p2 = Path().apply { moveTo(cx + a0, cy); lineTo(cx + a1, cy) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0, 280))
            .addStroke(GestureDescription.StrokeDescription(p2, 0, 280))
            .build()
        svc.dispatchGesture(g, null, null)
    }

    private fun shareCurrent() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast("Share failed") }
    }

    private fun dpad(action: Int) {
        if (Build.VERSION.SDK_INT >= 33) {
            AirPenAccessibilityService.instance?.performGlobalAction(action)
        }
    }

    private fun deleteWord() {
        val svc = AirPenAccessibilityService.instance ?: return
        val node = svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val text = node.text?.toString().orEmpty()
        if (text.isNotEmpty()) {
            val trimmed = text.trimEnd()
            val cut = trimmed.lastIndexOf(' ').let { if (it < 0) "" else trimmed.substring(0, it + 1) }
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cut)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        node.recycle()
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
