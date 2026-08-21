package studio.airpen.app.spen

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.samsung.android.sdk.penremote.AirMotionEvent
import com.samsung.android.sdk.penremote.ButtonEvent
import com.samsung.android.sdk.penremote.SpenEventListener
import com.samsung.android.sdk.penremote.SpenRemote
import com.samsung.android.sdk.penremote.SpenUnit
import com.samsung.android.sdk.penremote.SpenUnitManager
import studio.airpen.app.data.GestureSettings

/**
 * All Samsung S Pen Remote SDK types live in this file so [SpenHub] can load
 * without touching Samsung classes. Instantiating this class is what first
 * loads `SpenRemote` — keep that behind a user tap.
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

    fun attach(context: Context) {
        activityContext = context
    }

    fun connect() {
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
                postStatus(SpenStatus.CONNECTED)
                registerListeners()
                return
            }
        } catch (t: Throwable) {
            Log.w(TAG, "isConnected", t)
        }
        postStatus(SpenStatus.CONNECTING)
        val ctx = activityContext ?: appContext
        try {
            remote.connect(ctx, object : SpenRemote.ConnectionResultCallback {
                override fun onSuccess(manager: SpenUnitManager) {
                    try {
                        unitManager = manager
                        postStatus(SpenStatus.CONNECTED)
                        registerListeners()
                        Log.i(TAG, "S Pen connected")
                    } catch (t: Throwable) {
                        Log.e(TAG, "onSuccess", t)
                        postStatus(SpenStatus.ERROR)
                    }
                }

                override fun onFailure(error: Int) {
                    val st = if (error == SpenRemote.Error.UNSUPPORTED_DEVICE) {
                        SpenStatus.UNSUPPORTED
                    } else {
                        SpenStatus.ERROR
                    }
                    postStatus(st)
                    Log.w(TAG, "S Pen connect failed: $error")
                }
            })
            remote.setConnectionStateChangeListener { state ->
                try {
                    if (state == SpenRemote.State.DISCONNECTED ||
                        state == SpenRemote.State.DISCONNECTED_BY_UNKNOWN_REASON
                    ) {
                        postStatus(SpenStatus.DISCONNECTED)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "state listener", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "connect threw", t)
            postStatus(SpenStatus.ERROR)
        }
    }

    fun disconnect() {
        try {
            unregister()
            SpenRemote.getInstance().disconnect(activityContext ?: appContext)
        } catch (t: Throwable) {
            Log.w(TAG, "disconnect", t)
        }
        unitManager = null
        postStatus(SpenStatus.DISCONNECTED)
    }

    fun registerListeners() {
        val mgr = unitManager ?: return
        try {
            val button = mgr.getUnit(SpenUnit.TYPE_BUTTON)
            if (button != null) mgr.registerSpenEventListener(buttonListenerImpl, button)
        } catch (t: Throwable) {
            Log.e(TAG, "button listener", t)
        }
        try {
            val motion = mgr.getUnit(SpenUnit.TYPE_AIR_MOTION)
            if (motion != null) mgr.registerSpenEventListener(motionListenerImpl, motion)
        } catch (t: Throwable) {
            Log.e(TAG, "motion listener", t)
        }
    }

    fun unregisterMotion() {
        try {
            val mgr = unitManager ?: return
            val motion = mgr.getUnit(SpenUnit.TYPE_AIR_MOTION)
            if (motion != null) mgr.unregisterSpenEventListener(motion)
        } catch (_: Throwable) {
        }
    }

    private fun unregister() {
        try {
            val mgr = unitManager ?: return
            mgr.getUnit(SpenUnit.TYPE_BUTTON)?.let { mgr.unregisterSpenEventListener(it) }
            mgr.getUnit(SpenUnit.TYPE_AIR_MOTION)?.let { mgr.unregisterSpenEventListener(it) }
        } catch (_: Throwable) {
        }
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
            if (kotlin.math.abs(dx) < settings.deadZone && kotlin.math.abs(dy) < settings.deadZone) return@SpenEventListener
            val event = PenMotion(dx, dy, me.timeStamp)
            main.post { onMotion(event) }
        } catch (t: Throwable) {
            Log.w(TAG, "motion event", t)
        }
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
