package studio.airpen.app.spen

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import studio.airpen.app.data.CameraSettings
import java.util.concurrent.Executors
import kotlin.math.abs

class CameraTracker(
    private val context: Context,
    private val hub: SpenHub,
) : ImageAnalysis.Analyzer {
    private val exec = Executors.newSingleThreadExecutor()
    private var lastCx = -1f
    private var lastCy = -1f
    private var running = false
    var settings: CameraSettings = CameraSettings()

    fun start(owner: LifecycleOwner) {
        if (running) return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(exec, this)
                val selector = if (settings.useFront) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, analysis)
                running = true
            } catch (t: Throwable) {
                Log.e("CameraTracker", "start failed", t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        running = false
        runCatching {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching { future.get().unbindAll() }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buf = plane.buffer
            val data = ByteArray(buf.remaining())
            buf.get(data)
            val w = image.width
            val h = image.height
            val rowStride = plane.rowStride
            var sx = 0L
            var sy = 0L
            var n = 0L
            var i = 0
            val step = 8
            while (i < h) {
                var j = 0
                val row = i * rowStride
                while (j < w) {
                    val y = data.getOrNull(row + j)?.toInt()?.and(0xFF) ?: 0
                    if (y > 210) {
                        sx += j
                        sy += i
                        n++
                    }
                    j += step
                }
                i += step
            }
            if (n > 8) {
                var cx = sx.toFloat() / n / w
                val cy = sy.toFloat() / n / h
                if (settings.mirror) cx = 1f - cx
                if (lastCx >= 0) {
                    var dx = (cx - lastCx) * settings.gain
                    var dy = (lastCy - cy) * settings.gain
                    if (abs(dx) + abs(dy) > 0.002f) {
                        hub.injectDemoMotion(dx, dy)
                    }
                }
                lastCx = cx
                lastCy = cy
            }
        } catch (t: Throwable) {
            Log.w("CameraTracker", "frame", t)
        } finally {
            image.close()
        }
    }
}
