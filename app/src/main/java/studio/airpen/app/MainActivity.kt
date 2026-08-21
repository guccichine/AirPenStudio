package studio.airpen.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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

    private var engineStarted = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        startForegroundSafe()
        connectPenSafe()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { AirPen.ensure(this) }
        setContent {
            AirPenTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (AirPen.isReady) {
                        AirPenAppUi(activity = this)
                    } else {
                        androidx.compose.material3.Text("AirPen failed to start. Force-stop the app and try again.")
                    }
                }
            }
        }
        requestStartupPermissions()
        window.decorView.post {
            startForegroundSafe()
            connectPenSafe()
        }
    }

    override fun onResume() {
        super.onResume()
        connectPenSafe()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!AirPen.isReady) return super.onKeyDown(keyCode, event)
        return try {
            when (keyCode) {
                KeyEvent.KEYCODE_PAGE_UP -> AirPen.engine.setMode(studio.airpen.app.data.AppMode.MOUSE)
                KeyEvent.KEYCODE_PAGE_DOWN -> AirPen.engine.setMode(studio.airpen.app.data.AppMode.GESTURE)
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> AirPen.executor.execute(studio.airpen.app.data.BoundAction(studio.airpen.app.data.ActionId.MEDIA_PLAY_PAUSE))
                KeyEvent.KEYCODE_MEDIA_NEXT -> AirPen.executor.execute(studio.airpen.app.data.BoundAction(studio.airpen.app.data.ActionId.MEDIA_NEXT))
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> AirPen.executor.execute(studio.airpen.app.data.BoundAction(studio.airpen.app.data.ActionId.MEDIA_PREV))
                KeyEvent.KEYCODE_VOLUME_UP -> AirPen.executor.execute(studio.airpen.app.data.BoundAction(studio.airpen.app.data.ActionId.VOLUME_UP))
                KeyEvent.KEYCODE_VOLUME_DOWN -> AirPen.executor.execute(studio.airpen.app.data.BoundAction(studio.airpen.app.data.ActionId.VOLUME_DOWN))
                else -> return super.onKeyDown(keyCode, event)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "key", t)
            super.onKeyDown(keyCode, event)
        }
    }

    private fun connectPenSafe() {
        if (!AirPen.isReady) return
        try {
            AirPen.hub.attachActivity(this)
            if (!engineStarted) {
                AirPen.engine.start()
                engineStarted = true
            } else {
                AirPen.hub.connect()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "connectPen", t)
        }
    }

    private fun startForegroundSafe() {
        try {
            val i = Intent(this, AirPenForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (t: Throwable) {
            Log.e(TAG, "foreground service", t)
        }
    }

    private fun requestStartupPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) needed += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    fun openAccessibilitySettings() { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }
    fun openWriteSettings() {
        if (Build.VERSION.SDK_INT >= 23) startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
    }
    fun openImeSettings() { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }

    companion object { private const val TAG = "MainActivity" }
}
