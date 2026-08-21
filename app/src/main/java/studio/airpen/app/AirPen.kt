package studio.airpen.app

import android.content.Context
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

    fun ensure(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val app = context.applicationContext
            store = AppStore(app)
            hub = SpenHub(app)
            executor = ActionExecutor(app)
            engine = AirPenEngine(app, store, hub, executor)
            ready = true
        }
    }
}

class AirPenApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        AirPen.ensure(this)
    }
}
