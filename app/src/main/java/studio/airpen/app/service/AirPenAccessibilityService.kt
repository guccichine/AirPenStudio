package studio.airpen.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import studio.airpen.app.AirPen

class AirPenAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            // Wire the engine but do NOT connect the S Pen SDK from here.
            // Connecting from an accessibility process start is a common crash on One UI.
            AirPen.ensure(applicationContext)
        } catch (t: Throwable) {
            Log.e("AirPenA11y", "onServiceConnected", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AirPen.isReady) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        try {
            val mapped = AirPen.store.current.appProfileMap[pkg] ?: return
            if (mapped != AirPen.store.current.activeProfileId) {
                AirPen.store.update { it.copy(activeProfileId = mapped) }
            }
        } catch (t: Throwable) {
            Log.e("AirPenA11y", "event", t)
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
