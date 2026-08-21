package studio.airpen.app.spen

import android.content.Context
import android.util.Log
import com.samsung.android.sdk.penremote.AirMotionEvent
import com.samsung.android.sdk.penremote.ButtonEvent
import com.samsung.android.sdk.penremote.SpenEventListener
import com.samsung.android.sdk.penremote.SpenRemote
import com.samsung.android.sdk.penremote.SpenUnit
import com.samsung.android.sdk.penremote.SpenUnitManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.airpen.app.data.GestureSettings

data class PenMotion(val dx: Float, val dy: Float, val t: Long)
data class PenButton(val down: Boolean, val t: Long)

enum class SpenStatus { UNKNOWN, UNSUPPORTED, DISCONNECTED, CONNECTING, CONNECTED, ERROR }

class SpenHub(private val appContext: Context) {
    private var unitManager: SpenUnitManager? = null
    private var activityContext: Context? = null

    private val _status = MutableStateFlow(SpenStatus.UNKNOWN)
    val status: StateFlow<SpenStatus> = _status.asStateFlow()

    private val _buttonDown = MutableStateFlow(false)
    val buttonDown: StateFlow<Boolean> = _buttonDown.asStateFlow()

    var motionListener: ((PenMotion) -> Unit)? = null
    var buttonListener: ((PenButton) -> Unit)? = null
    var connectionListener: ((SpenStatus) -> Unit)? = null

    @Volatile var settings: GestureSettings = GestureSettings()

    fun attachActivity(context: Context) { activityContext = context }

    fun connect() {
        if (_status.value == SpenStatus.CONNECTING) return
        if (!isDeviceSupported()) {
            _status.value = SpenStatus.UNSUPPORTED
            connectionListener?.invoke(SpenStatus.UNSUPPORTED)
            return
        }
        try {
            val remote = SpenRemote.getInstance()
            if (remote.isConnected) {
                _status.value = SpenStatus.CONNECTED
                registerListeners()
                return
            }
            _status.value = SpenStatus.CONNECTING
            val ctx = activityContext ?: appContext
            remote.connect(ctx, object : SpenRemote.ConnectionResultCallback {
                override fun onSuccess(manager: SpenUnitManager) {
                    unitManager = manager
                    _status.value = SpenStatus.CONNECTED
                    registerListeners()
                    connectionListener?.invoke(SpenStatus.CONNECTED)
                    Log.i(TAG, "S Pen connected")
                }
                override fun onFailure(error: Int) {
                    val st = if (error == SpenRemote.Error.UNSUPPORTED_DEVICE) SpenStatus.UNSUPPORTED else SpenStatus.ERROR
                    _status.value = st
                    connectionListener?.invoke(st)
                    Log.w(TAG, "S Pen connect failed: $error")
                }
            })
            remote.setConnectionStateChangeListener { state ->
                if (state == SpenRemote.State.DISCONNECTED || state == SpenRemote.State.DISCONNECTED_BY_UNKNOWN_REASON) {
                    _status.value = SpenStatus.DISCONNECTED
                    connectionListener?.invoke(SpenStatus.DISCONNECTED)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "connect threw", t)
            _status.value = SpenStatus.ERROR
            connectionListener?.invoke(SpenStatus.ERROR)
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
        _status.value = SpenStatus.DISCONNECTED
    }

    fun registerListeners() {
        val mgr = unitManager ?: return
        try {
            val button = mgr.getUnit(SpenUnit.TYPE_BUTTON)
            if (button != null) mgr.registerSpenEventListener(buttonListenerImpl, button)
        } catch (t: Throwable) { Log.e(TAG, "button listener", t) }
        try {
            val motion = mgr.getUnit(SpenUnit.TYPE_AIR_MOTION)
            if (motion != null) mgr.registerSpenEventListener(motionListenerImpl, motion)
        } catch (t: Throwable) { Log.e(TAG, "motion listener", t) }
    }

    fun unregisterMotion() {
        try {
            val mgr = unitManager ?: return
            val motion = mgr.getUnit(SpenUnit.TYPE_AIR_MOTION) ?: return
            mgr.unregisterSpenEventListener(motion)
        } catch (_: Throwable) {}
    }

    private fun unregister() {
        try {
            val mgr = unitManager ?: return
            mgr.getUnit(SpenUnit.TYPE_BUTTON)?.let { mgr.unregisterSpenEventListener(it) }
            mgr.getUnit(SpenUnit.TYPE_AIR_MOTION)?.let { mgr.unregisterSpenEventListener(it) }
        } catch (_: Throwable) {}
    }

    private val buttonListenerImpl = SpenEventListener { ev ->
        try {
            val be = ButtonEvent(ev)
            val down = be.action == ButtonEvent.ACTION_DOWN
            _buttonDown.value = down
            buttonListener?.invoke(PenButton(down, be.timeStamp))
        } catch (t: Throwable) { Log.e(TAG, "button event", t) }
    }

    private val motionListenerImpl = SpenEventListener { ev ->
        try {
            val me = AirMotionEvent(ev)
            var dx = me.deltaX
            var dy = me.deltaY
            if (settings.invertMotionX) dx = -dx
            if (settings.invertMotionY) dy = -dy
            if (kotlin.math.abs(dx) < settings.deadZone && kotlin.math.abs(dy) < settings.deadZone) return@SpenEventListener
            motionListener?.invoke(PenMotion(dx, dy, me.timeStamp))
        } catch (t: Throwable) { Log.e(TAG, "motion event", t) }
    }

    fun injectDemoMotion(dx: Float, dy: Float) {
        motionListener?.invoke(PenMotion(dx, dy, System.currentTimeMillis()))
    }

    fun injectDemoButton(down: Boolean) {
        _buttonDown.value = down
        buttonListener?.invoke(PenButton(down, System.currentTimeMillis()))
    }

    companion object {
        private const val TAG = "SpenHub"
        fun isDeviceSupported(): Boolean = try {
            val remote = SpenRemote.getInstance()
            remote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_BUTTON) || remote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_AIR_MOTION)
        } catch (t: Throwable) {
            Log.w(TAG, "support check failed (expected on non-Samsung)", t)
            false
        }
    }
}
