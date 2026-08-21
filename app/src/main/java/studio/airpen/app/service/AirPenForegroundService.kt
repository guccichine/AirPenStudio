package studio.airpen.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import studio.airpen.app.AirPen
import studio.airpen.app.MainActivity
import studio.airpen.app.R
import studio.airpen.app.data.AppMode

class AirPenForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        goForeground("AirPen is ready")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (AirPen.isReady) {
            when (intent?.action) {
                ACTION_GESTURE -> runCatching { AirPen.engine.setMode(AppMode.GESTURE) }
                ACTION_MOUSE -> runCatching { AirPen.engine.setMode(AppMode.MOUSE) }
                ACTION_TYPE -> runCatching { AirPen.engine.setMode(AppMode.TYPE) }
                ACTION_STOP -> {
                    runCatching { AirPen.engine.stop() }
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        val mode = if (AirPen.isReady) runCatching { AirPen.engine.mode.name }.getOrElse { "Idle" } else "Idle"
        goForeground("Mode: $mode")
        return START_STICKY
    }

    private fun goForeground(text: String) {
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, 42, buildNotification(text), type)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(id: Int, label: String, action: String): NotificationCompat.Action {
            val pi = PendingIntent.getService(this, id, Intent(this, AirPenForegroundService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return NotificationCompat.Action(0, label, pi)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AirPen Studio")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(action(2, "Gestures", ACTION_GESTURE))
            .addAction(action(3, "Mouse", ACTION_MOUSE))
            .addAction(action(4, "Type", ACTION_TYPE))
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "AirPen", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        private const val TAG = "AirPenFg"
        const val CHANNEL = "airpen_live"
        const val ACTION_GESTURE = "studio.airpen.app.GESTURE"
        const val ACTION_MOUSE = "studio.airpen.app.MOUSE"
        const val ACTION_TYPE = "studio.airpen.app.TYPE"
        const val ACTION_STOP = "studio.airpen.app.STOP"
    }
}
