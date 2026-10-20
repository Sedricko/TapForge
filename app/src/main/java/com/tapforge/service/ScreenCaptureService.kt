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
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        // Android 14+: o serviço em primeiro plano deve existir ANTES de obter a projeção.
        startInForeground()
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(code, data)
        projection = mp
        val handler = Handler(Looper.getMainLooper())
        mp.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, handler)

        val m = resources.displayMetrics
        width = m.widthPixels; height = m.heightPixels
        val r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader = r
        display = mp.createVirtualDisplay("tapforge", width, height, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
        instance = this
        return START_NOT_STICKY
    }

    /** Último quadro disponível (reusa o anterior se não houver quadro novo). */
    fun latestFrame(): Bitmap? {
        val img = reader?.acquireLatestImage() ?: return last
        img.use {
            val p = it.planes[0]
            val rowPixels = p.rowStride / p.pixelStride
            val full = Bitmap.createBitmap(rowPixels, height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(p.buffer)
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
        val n = b.setContentTitle("TapForge").setContentText("Captura de tela ativa")
            .setSmallIcon(android.R.drawable.ic_menu_camera).setOngoing(true).build()
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
        private const val CHANNEL = "capture"
        @Volatile var instance: ScreenCaptureService? = null
    }
}
