package studio.airpen.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import studio.airpen.app.AirPen

class AirPenAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AirPen.ensure(applicationContext)
        AirPen.engine.start()
        val fg = Intent(this, AirPenForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(fg) else startService(fg)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val mapped = AirPen.store.current.appProfileMap[pkg] ?: return
        if (mapped != AirPen.store.current.activeProfileId) {
            AirPen.store.update { it.copy(activeProfileId = mapped) }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AirPenAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }
}
