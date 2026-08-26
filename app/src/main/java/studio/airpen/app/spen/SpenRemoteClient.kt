package studio.airpen.app.spen

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.samsung.android.sdk.penremote.AirMotionEvent
import com.samsung.android.sdk.penremote.ButtonEvent
import com.samsung.android.sdk.penremote.SpenEventListener
import com.samsung.android.sdk.penremote.SpenRemote
import com.samsung.android.sdk.penremote.SpenUnit
import com.samsung.android.sdk.penremote.SpenUnitManager
import studio.airpen.app.data.GestureSettings
import studio.airpen.app.overlay.KeepAliveOverlay
import studio.airpen.app.service.AirPenAccessibilityService

/**
 * All Samsung S Pen Remote SDK types live in this file so [SpenHub] can load
 * without touching Samsung classes. Instantiating this class is what first
 * loads `SpenRemote` — keep that behind a user tap.
 *
 * The S22 Ultra drops the BLE session a couple of seconds after the Activity
 * that called [SpenRemote.connect] leaves the foreground. We keep the session
 * alive with: a 1px overlay, a partial wake lock, listener re-registration,
 * and automatic reconnect while [wanted] is true.
 */
class SpenRemoteClient(
    private val appContext: Context,
    private val onStatus: (SpenStatus) -> Unit,
    private val onButton: (PenButton) -> Unit,
    private val onMotion: (PenMotion) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var unitManager: SpenUnitManager? = null
    private var activityContext: Context? = null
    var settings: GestureSettings = GestureSettings()
    @Volatile var passAllMotion: Boolean = false

    @Volatile var wanted: Boolean = false
        private set

    @Volatile private var connecting = false
    @Volatile private var buttonRegistered = false
    @Volatile private var motionRegistered = false
    private var stateListenerInstalled = false
    private var wakeLock: PowerManager.WakeLock? = null

    fun attach(context: Context) {
        activityContext = context
    }

    fun connect() {
        wanted = true
        acquireWake()
        KeepAliveOverlay.show(appContext)
        main.removeCallbacks(keepAliveTick)
        main.post(keepAliveTick)
        doConnect()
    }

    fun keepAlive() {
        if (!wanted) return
        acquireWake()
        KeepAliveOverlay.show(appContext)
        main.removeCallbacks(keepAliveTick)
        main.post(keepAliveTick)
        try {
            val remote = SpenRemote.getInstance()
            if (remote.isConnected) {
                registerListeners()
            } else if (!connecting) {
                doConnect()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "keepAlive", t)
            if (!connecting) doConnect()
        }
    }

    fun disconnect() {
        wanted = false
        main.removeCallbacks(keepAliveTick)
        main.removeCallbacks(reconnectSoon)
        connecting = false
        try {
            unregister()
            val ctx = activityContext ?: appContext
            SpenRemote.getInstance().disconnect(ctx)
        } catch (t: Throwable) {
            Log.w(TAG, "disconnect", t)
        }
        unitManager = null
        buttonRegistered = false
        motionRegistered = false
        releaseWake()
        KeepAliveOverlay.hide()
        postStatus(SpenStatus.DISCONNECTED)
    }

    fun registerListeners() {
        val mgr = unitManager ?: return
        try {
            if (!buttonRegistered) {
                val button = mgr.getUnit(SpenUnit.TYPE_BUTTON)
                if (button != null) {
                    mgr.registerSpenEventListener(buttonListenerImpl, button)
                    buttonRegistered = true
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "button listener", t)
            buttonRegistered = false
        }
        try {
            if (!motionRegistered) {
                val motion = mgr.getUnit(SpenUnit.TYPE_AIR_MOTION)
                if (motion != null) {
                    mgr.registerSpenEventListener(motionListenerImpl, motion)
                    motionRegistered = true
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "motion listener", t)
            motionRegistered = false
        }
    }

    fun unregisterMotion() {
        if (wanted) return
        try {
            val mgr = unitManager ?: return
            val motion = mgr.getUnit(SpenUnit.TYPE_AIR_MOTION)
            if (motion != null) mgr.unregisterSpenEventListener(motion)
            motionRegistered = false
        } catch (_: Throwable) {
        }
    }

    private fun doConnect() {
        if (!wanted) return
        val remote = try {
            SpenRemote.getInstance()
        } catch (t: Throwable) {
            Log.e(TAG, "SpenRemote.getInstance", t)
            postStatus(SpenStatus.UNSUPPORTED)
            return
        }
        val supported = try {
            remote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_BUTTON) ||
                remote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_AIR_MOTION)
        } catch (t: Throwable) {
            Log.w(TAG, "feature check", t)
            false
        }
        if (!supported) {
            postStatus(SpenStatus.UNSUPPORTED)
            return
        }
        try {
            if (remote.isConnected) {
                connecting = false
                postStatus(SpenStatus.CONNECTED)
                registerListeners()
                return
            }
        } catch (t: Throwable) {
            Log.w(TAG, "isConnected", t)
        }
        if (connecting) return
        connecting = true
        postStatus(SpenStatus.CONNECTING)
        val ctx = connectContext()
        try {
            installStateListener(remote)
            remote.connect(ctx, object : SpenRemote.ConnectionResultCallback {
                override fun onSuccess(manager: SpenUnitManager) {
                    try {
                        unitManager = manager
                        buttonRegistered = false
                        motionRegistered = false
                        connecting = false
                        postStatus(SpenStatus.CONNECTED)
                        registerListeners()
                        KeepAliveOverlay.show(appContext)
                        acquireWake()
                        Log.i(TAG, "S Pen connected")
                    } catch (t: Throwable) {
                        Log.e(TAG, "onSuccess", t)
                        connecting = false
                        postStatus(SpenStatus.ERROR)
                        scheduleReconnect()
                    }
                }

                override fun onFailure(error: Int) {
                    connecting = false
                    val st = if (error == SpenRemote.Error.UNSUPPORTED_DEVICE) {
                        SpenStatus.UNSUPPORTED
                    } else {
                        SpenStatus.ERROR
                    }
                    postStatus(st)
                    Log.w(TAG, "S Pen connect failed: $error")
                    if (wanted && error != SpenRemote.Error.UNSUPPORTED_DEVICE) {
                        scheduleReconnect()
                    }
                }
            })
        } catch (t: Throwable) {
            connecting = false
            Log.e(TAG, "connect threw", t)
            postStatus(SpenStatus.ERROR)
            scheduleReconnect()
        }
    }

    private fun connectContext(): Context {
        val act = activityContext
        if (act != null) {
            if (act is android.app.Activity) {
                if (!act.isDestroyed) return act
            } else {
                return act
            }
        }
        AirPenAccessibilityService.instance?.let { return it }
        return appContext
    }

    private fun installStateListener(remote: SpenRemote) {
        if (stateListenerInstalled) return
        stateListenerInstalled = true
        try {
            remote.setConnectionStateChangeListener { state ->
                try {
                    if (state == SpenRemote.State.DISCONNECTED ||
                        state == SpenRemote.State.DISCONNECTED_BY_UNKNOWN_REASON
                    ) {
                        unitManager = null
                        buttonRegistered = false
                        motionRegistered = false
                        connecting = false
                        if (wanted) {
                            postStatus(SpenStatus.CONNECTING)
                            scheduleReconnect()
                        } else {
                            postStatus(SpenStatus.DISCONNECTED)
                        }
                    } else if (
                        state != SpenRemote.State.DISCONNECTED &&
                        state != SpenRemote.State.DISCONNECTED_BY_UNKNOWN_REASON
                    ) {
                        postStatus(SpenStatus.CONNECTED)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "state listener", t)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "setConnectionStateChangeListener", t)
            stateListenerInstalled = false
        }
    }

    private val reconnectSoon = Runnable {
        connecting = false
        if (wanted) doConnect()
    }

    private fun scheduleReconnect() {
        if (!wanted) return
        main.removeCallbacks(reconnectSoon)
        main.postDelayed(reconnectSoon, 700L)
    }

    private val keepAliveTick = object : Runnable {
        override fun run() {
            if (!wanted) return
            try {
                val remote = SpenRemote.getInstance()
                if (remote.isConnected) {
                    if (unitManager == null) {
                        connecting = false
                        doConnect()
                    } else {
                        registerListeners()
                    }
                } else if (!connecting) {
                    doConnect()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "keepAliveTick", t)
            }
            main.postDelayed(this, 2000L)
        }
    }

    private fun unregister() {
        try {
            val mgr = unitManager ?: return
            mgr.getUnit(SpenUnit.TYPE_BUTTON)?.let { mgr.unregisterSpenEventListener(it) }
            mgr.getUnit(SpenUnit.TYPE_AIR_MOTION)?.let { mgr.unregisterSpenEventListener(it) }
        } catch (_: Throwable) {
        }
        buttonRegistered = false
        motionRegistered = false
    }

    private val buttonListenerImpl = SpenEventListener { ev ->
        try {
            val be = ButtonEvent(ev)
            val down = be.action == ButtonEvent.ACTION_DOWN
            val event = PenButton(down, be.timeStamp)
            main.post { onButton(event) }
        } catch (t: Throwable) {
            Log.w(TAG, "button event", t)
        }
    }

    private val motionListenerImpl = SpenEventListener { ev ->
        try {
            val me = AirMotionEvent(ev)
            var dx = me.deltaX
            var dy = me.deltaY
            if (settings.invertMotionX) dx = -dx
            if (settings.invertMotionY) dy = -dy
            if (!passAllMotion && !wanted && kotlin.math.abs(dx) < settings.deadZone && kotlin.math.abs(dy) < settings.deadZone) return@SpenEventListener
            val event = PenMotion(dx, dy, me.timeStamp)
            main.post { onMotion(event) }
        } catch (t: Throwable) {
            Log.w(TAG, "motion event", t)
        }
    }

    private fun acquireWake() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "airpen:spen")
            lock.setReferenceCounted(false)
            lock.acquire()
            wakeLock = lock
        } catch (t: Throwable) {
            Log.w(TAG, "wakelock", t)
        }
    }

    private fun releaseWake() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Throwable) {
        }
        wakeLock = null
    }

    private fun postStatus(st: SpenStatus) {
        main.post {
            try {
                onStatus(st)
            } catch (t: Throwable) {
                Log.w(TAG, "status", t)
            }
        }
    }

    companion object {
        private const val TAG = "SpenRemoteClient"
    }
}
