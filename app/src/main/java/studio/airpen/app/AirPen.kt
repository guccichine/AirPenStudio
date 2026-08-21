package studio.airpen.app

import android.content.Context
import android.util.Log
import studio.airpen.app.action.ActionExecutor
import studio.airpen.app.data.AppStore
import studio.airpen.app.engine.AirPenEngine
import studio.airpen.app.spen.SpenHub

object AirPen {
    lateinit var store: AppStore
        private set
    lateinit var hub: SpenHub
        private set
    lateinit var executor: ActionExecutor
        private set
    lateinit var engine: AirPenEngine
        private set

    @Volatile
    private var ready = false

    val isReady: Boolean get() = ready

    fun ensure(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val app = context.applicationContext
            try {
                store = AppStore(app)
                hub = SpenHub(app)
                executor = ActionExecutor(app)
                engine = AirPenEngine(app, store, hub, executor)
                engine.start(connectPen = false)
                ready = true
            } catch (t: Throwable) {
                Log.e("AirPen", "init failed", t)
                CrashLog.write(app, t)
            }
        }
    }
}

class AirPenApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // Do not touch the S Pen SDK or start a foreground service here.
        // Loading Samsung classes / FGS at process start is what killed the
        // previous builds on Galaxy S22 Ultra (One UI).
    }
}
