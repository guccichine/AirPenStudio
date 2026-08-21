package studio.airpen.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import studio.airpen.app.service.AirPenForegroundService
import studio.airpen.app.ui.AirPenAppUi
import studio.airpen.app.ui.theme.AirPenTheme

class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AirPen.ensure(this)
        AirPen.hub.attachActivity(this)
        AirPen.hub.connect()
        AirPen.engine.start()
        startForeground()
        requestStartupPermissions()
        setContent {
            AirPenTheme {
                Surface(Modifier.fillMaxSize()) {
                    AirPenAppUi(activity = this)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AirPen.hub.attachActivity(this)
        if (!AirPen.hub.status.value.name.contains("CONNECT")) {
            AirPen.hub.connect()
        }
        AirPen.hub.registerListeners()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> AirPen.engine.setMode(studio.airpen.app.data.AppMode.MOUSE)
            KeyEvent.KEYCODE_PAGE_DOWN -> AirPen.engine.setMode(studio.airpen.app.data.AppMode.GESTURE)
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> AirPen.executor.execute(
                studio.airpen.app.data.BoundAction(studio.airpen.app.data.ActionId.MEDIA_PLAY_PAUSE),
            )
            KeyEvent.KEYCODE_MEDIA_NEXT -> AirPen.executor.execute(
                studio.airpen.app.data.BoundAction(studio.airpen.app.data.ActionId.MEDIA_NEXT),
            )
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> AirPen.executor.execute(
                studio.airpen.app.data.BoundAction(studio.airpen.app.data.ActionId.MEDIA_PREV),
            )
            KeyEvent.KEYCODE_VOLUME_UP -> AirPen.executor.execute(
                studio.airpen.app.data.BoundAction(studio.airpen.app.data.ActionId.VOLUME_UP),
            )
            KeyEvent.KEYCODE_VOLUME_DOWN -> AirPen.executor.execute(
                studio.airpen.app.data.BoundAction(studio.airpen.app.data.ActionId.VOLUME_DOWN),
            )
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun startForeground() {
        val i = Intent(this, AirPenForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun requestStartupPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) needed += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        needed += Manifest.permission.CAMERA
        needed += Manifest.permission.VIBRATE
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            val i = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            startActivity(i)
        }
    }

    fun openWriteSettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            val i = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:$packageName"),
            )
            startActivity(i)
        }
    }

    fun openImeSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }
}
