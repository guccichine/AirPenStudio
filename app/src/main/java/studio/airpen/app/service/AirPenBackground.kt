package studio.airpen.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.airpen.app.AirPen

object AirPenBackground {
    private val _running = MutableStateFlow(false)
    val runningFlow: StateFlow<Boolean> = _running.asStateFlow()

    var running: Boolean
        get() = _running.value
        set(value) {
            _running.value = value
        }

    fun start(context: Context) {
        persistWanted(true)
        try {
            val i = Intent(context.applicationContext, AirPenForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.applicationContext.startForegroundService(i)
            } else {
                context.applicationContext.startService(i)
            }
            running = true
        } catch (t: Throwable) {
            Log.e("AirPenBg", "start failed", t)
            running = false
        }
    }

    fun stop(context: Context) {
        persistWanted(false)
        try {
            if (AirPen.isReady) AirPen.engine.halt()
        } catch (t: Throwable) {
            Log.w("AirPenBg", "halt", t)
        }
        try {
            val i = Intent(context.applicationContext, AirPenForegroundService::class.java)
                .setAction(AirPenForegroundService.ACTION_STOP)
            context.applicationContext.startService(i)
        } catch (t: Throwable) {
            Log.w("AirPenBg", "stop", t)
        }
        running = false
    }

    fun wanted(): Boolean {
        return try {
            AirPen.isReady && AirPen.store.current.general.runInBackground
        } catch (_: Throwable) {
            true
        }
    }

    private fun persistWanted(on: Boolean) {
        if (!AirPen.isReady) return
        try {
            AirPen.store.update { it.copy(general = it.general.copy(runInBackground = on)) }
            AirPen.store.saveNow()
        } catch (t: Throwable) {
            Log.w("AirPenBg", "persist", t)
        }
    }
}
