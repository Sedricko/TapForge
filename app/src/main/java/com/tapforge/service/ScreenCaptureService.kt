package com.tapforge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.tapforge.R
import java.nio.ByteBuffer

/** Mantém uma MediaProjection ativa e expõe o último quadro da tela. */
class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    @Volatile private var last: Bitmap? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        // Android 14+: o serviço em primeiro plano deve existir ANTES de obter a projeção.
        startInForeground()
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(code, data) ?: run { stopSelf(); return START_NOT_STICKY }
        projection = mp
        val handler = Handler(Looper.getMainLooper())
        mp.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, handler)

        // Tamanho real (com barras do sistema): as mesmas coordenadas usadas pelos gestos.
        val (w, h) = realScreenSize(this)
        width = w; height = h
        val r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader = r
        display = mp.createVirtualDisplay("tapforge", width, height, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
        instance = this
        return START_NOT_STICKY
    }

    /** Último quadro disponível (reusa o anterior se a tela não mudou). */
    @Synchronized fun latestFrame(): Bitmap? {
        val img = runCatching { reader?.acquireLatestImage() }.getOrNull() ?: return last
        img.use {
            val p = it.planes[0]
            val rowPixels = p.rowStride / p.pixelStride
            // Alguns aparelhos entregam o buffer sem o preenchimento da última linha: completa com zeros.
            val need = p.rowStride * height
            val buf = if (p.buffer.remaining() >= need) p.buffer
            else ByteBuffer.allocate(need).put(p.buffer).apply { rewind() }
            val full = Bitmap.createBitmap(rowPixels, height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(buf)
            last = if (rowPixels == width) full else Bitmap.createBitmap(full, 0, 0, width, height)
        }
        return last
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        val b = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Captura de tela", NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(this, CHANNEL)
        } else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = b.setContentTitle("TapForge").setContentText("Captura de tela ativa para imagem, texto e cor")
            .setSmallIcon(R.drawable.ic_notify).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        instance = null
        display?.release(); reader?.close(); projection?.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.tapforge.STOP_CAPTURE"
        private const val CHANNEL = "capture"
        @Volatile var instance: ScreenCaptureService? = null

        fun stop(c: Context) { c.startService(Intent(c, ScreenCaptureService::class.java).setAction(ACTION_STOP)) }
    }
}

/** Largura × altura reais da tela, em pixels. */
fun realScreenSize(c: Context): Pair<Int, Int> {
    val wm = c.getSystemService(android.view.WindowManager::class.java)
    return if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    else android.util.DisplayMetrics().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
}
