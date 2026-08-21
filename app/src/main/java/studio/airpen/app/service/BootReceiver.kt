package studio.airpen.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // Do not start a foreground service from boot. That is a process-kill
        // on Android 14+ (S22 Ultra / One UI 6) when the FGS type is rejected.
        Log.i("AirPenBoot", "boot ignored — open AirPen Studio to connect the S Pen")
    }
}
