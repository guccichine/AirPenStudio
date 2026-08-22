package studio.airpen.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AirPenBackground {
    @Volatile
    var running: Boolean = false

    fun start(context: Context) {
        try {
            val i = Intent(context, AirPenForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
            running = true
        } catch (t: Throwable) {
            Log.e("AirPenBg", "start failed", t)
            running = false
        }
    }

    fun stop(context: Context) {
        try {
            val i = Intent(context, AirPenForegroundService::class.java)
                .setAction(AirPenForegroundService.ACTION_STOP)
            context.startService(i)
        } catch (t: Throwable) {
            Log.w("AirPenBg", "stop", t)
        }
        running = false
    }
}
