package studio.airpen.app.spen

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import studio.airpen.app.data.CameraSettings

/**
 * Camera fallback is disabled in this build so the APK does not ship CameraX
 * native libraries (a common crash/install-size problem). S22 Ultra has BLE S Pen.
 */
class CameraTracker(
    @Suppress("unused") private val context: Context,
    @Suppress("unused") private val hub: SpenHub,
) {
    var settings: CameraSettings = CameraSettings()

    fun start(@Suppress("unused") owner: LifecycleOwner) {
        Log.i("CameraTracker", "camera fallback not bundled in this build")
    }

    fun stop() = Unit
}
