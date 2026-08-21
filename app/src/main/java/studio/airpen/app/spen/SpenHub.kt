package studio.airpen.app.spen

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.airpen.app.data.GestureSettings

data class PenMotion(val dx: Float, val dy: Float, val t: Long)
data class PenButton(val down: Boolean, val t: Long)

enum class SpenStatus { UNKNOWN, UNSUPPORTED, DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Public S Pen facade. Stored as [Any] so constructing this class cannot load
 * Samsung SDK types. [SpenRemoteClient] is first referenced inside [connect].
 */
class SpenHub(private val appContext: Context) {
    private var activityContext: Context? = null
    private var client: Any? = null

    private val _status = MutableStateFlow(SpenStatus.UNKNOWN)
    val status: StateFlow<SpenStatus> = _status.asStateFlow()

    private val _buttonDown = MutableStateFlow(false)
    val buttonDown: StateFlow<Boolean> = _buttonDown.asStateFlow()

    var motionListener: ((PenMotion) -> Unit)? = null
    var buttonListener: ((PenButton) -> Unit)? = null
    var connectionListener: ((SpenStatus) -> Unit)? = null

    @Volatile var settings: GestureSettings = GestureSettings()

    fun attachActivity(context: Context) {
        activityContext = context
    }

    fun connect() {
        if (_status.value == SpenStatus.CONNECTING) return
        try {
            // First mention of SpenRemoteClient in this class — Samsung types load here.
            val existing = client
            val c = if (existing != null) {
                existing as SpenRemoteClient
            } else {
                SpenRemoteClient(
                    appContext,
                    onStatus = { st ->
                        _status.value = st
                        connectionListener?.invoke(st)
                    },
                    onButton = { b ->
                        _buttonDown.value = b.down
                        buttonListener?.invoke(b)
                    },
                    onMotion = { m ->
                        motionListener?.invoke(m)
                    },
                ).also { client = it }
            }
            activityContext?.let { c.attach(it) }
            c.settings = settings
            c.connect()
        } catch (t: Throwable) {
            Log.e(TAG, "connect / SDK load failed", t)
            _status.value = SpenStatus.ERROR
            connectionListener?.invoke(SpenStatus.ERROR)
        }
    }

    fun disconnect() {
        try {
            val existing = client ?: return
            (existing as SpenRemoteClient).disconnect()
        } catch (t: Throwable) {
            Log.w(TAG, "disconnect", t)
        }
        _status.value = SpenStatus.DISCONNECTED
    }

    fun registerListeners() {
        try {
            val existing = client ?: return
            (existing as SpenRemoteClient).registerListeners()
        } catch (t: Throwable) {
            Log.w(TAG, "registerListeners", t)
        }
    }

    fun unregisterMotion() {
        try {
            val existing = client ?: return
            (existing as SpenRemoteClient).unregisterMotion()
        } catch (_: Throwable) {
        }
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
    }
}
