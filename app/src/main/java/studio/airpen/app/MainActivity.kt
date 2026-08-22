package studio.airpen.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import studio.airpen.app.service.AirPenBackground
import studio.airpen.app.ui.AirPenAppUi
import studio.airpen.app.ui.theme.AirPenTheme

class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        connectPenNow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(applicationContext)
        try {
            AirPen.ensure(applicationContext)
            setContent {
                AirPenTheme {
                    Surface(Modifier.fillMaxSize()) {
                        if (AirPen.isReady) {
                            AirPenAppUi(activity = this)
                        } else {
                            Text(
                                "AirPen failed to start.\n\n" +
                                    (CrashLog.read(this) ?: "No crash log yet. Force-stop and open again."),
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            CrashLog.write(this, t)
            Log.e(TAG, "onCreate", t)
            showFatalView(t)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AirPen.isReady) return
        try {
            AirPen.hub.attachActivity(this)
            val mode = AirPen.engine.mode
            if (mode == studio.airpen.app.data.AppMode.MOUSE ||
                mode == studio.airpen.app.data.AppMode.POINTER ||
                mode == studio.airpen.app.data.AppMode.SCROLL ||
                mode == studio.airpen.app.data.AppMode.TYPE
            ) {
                AirPen.engine.setMode(mode)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onResume", t)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!AirPen.isReady) return
        try {
            // Hand the S Pen SDK an Application context so leaving the UI
            // does not tear the BLE session down with the activity.
            AirPen.hub.attachActivity(applicationContext)
            if (AirPen.store.current.general.runInBackground) {
                AirPenBackground.start(this)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onStop", t)
        }
    }

    override fun onDestroy() {
        if (AirPen.isReady) {
            runCatching { AirPen.hub.attachActivity(applicationContext) }
        }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!AirPen.isReady) return super.onKeyDown(keyCode, event)
        return try {
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
            true
        } catch (t: Throwable) {
            Log.e(TAG, "key", t)
            super.onKeyDown(keyCode, event)
        }
    }

    /** Called from the Home screen. Does not run at launch. */
    fun requestConnect() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) needed += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permLauncher.launch(missing.toTypedArray())
        } else {
            connectPenNow()
        }
    }

    private fun connectPenNow() {
        if (!AirPen.isReady) return
        try {
            AirPen.hub.attachActivity(this)
            AirPen.engine.start(connectPen = true)
            AirPenBackground.start(this)
        } catch (t: Throwable) {
            CrashLog.write(this, t)
            Log.e(TAG, "connectPen", t)
        }
    }

    fun startBackground() {
        AirPenBackground.start(this)
    }

    fun stopBackground() {
        AirPenBackground.stop(this)
    }

    fun openBatterySettings() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName")),
                )
            }
        } catch (_: Throwable) {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun showFatalView(t: Throwable) {
        val tv = TextView(this).apply {
            text = "AirPen hit an error on this phone. Screenshot this:\n\n${t.stackTraceToString()}\n\n" +
                (CrashLog.read(this@MainActivity) ?: "")
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF0E0F12.toInt())
            setPadding(48, 96, 48, 48)
            textSize = 14f
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0E0F12.toInt())
            addView(tv)
        }
        setContentView(scroll)
    }

    fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }
    }

    fun openWriteSettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")),
            )
        }
    }

    fun openImeSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
