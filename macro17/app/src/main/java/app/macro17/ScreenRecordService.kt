package app.macro17

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.widget.Toast
import java.io.File

/** Gravação de tela em vídeo (o botão "gravar tela" do painel do base): MediaProjection + MediaRecorder, salva em Filmes/Macro17. */
class ScreenRecordService : Service() {
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var file: File? = null

    companion object {
        @Volatile var running = false
        const val ACTION_STOP = "app.macro17.STOP_RECORD"
        private const val CH = "screen_record"
        fun stop(c: Context) { c.startService(Intent(c, ScreenRecordService::class.java).setAction(ACTION_STOP)) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        val intent = i ?: return START_NOT_STICKY
        if (intent.action == ACTION_STOP) { finish(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Gravação de tela", NotificationManager.IMPORTANCE_LOW))
        val stopPi = android.app.PendingIntent.getService(this, 1, Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP), android.app.PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH).setSmallIcon(R.drawable.ic_stop).setContentTitle("Gravando a tela").setContentText("Toque para parar")
            .setContentIntent(stopPi).setOngoing(true).build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        val audio = intent.getIntExtra("audio", 0) == 1
        if (audio) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            startForeground(1, n, type)
            val data = intent.getParcelableExtra<Intent>("data")!!
            val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("code", 0), data)
            projection = mp
            mp.registerCallback(object : MediaProjection.Callback() { override fun onStop() { finish() } }, null)
            val m = resources.displayMetrics
            val w = m.widthPixels and 1.inv(); val h = m.heightPixels and 1.inv()
            val out = File(cacheDir, "rec_${System.currentTimeMillis()}.mp4"); file = out
            val r = MediaRecorder(this).apply {
                if (audio) setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (audio) setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoSize(w, h)
                setVideoFrameRate(intent.getIntExtra("fps", 24))
                setVideoEncodingBitRate(intent.getIntExtra("bitrate", 5_000_000))
                setOutputFile(out.path)
                prepare()
            }
            recorder = r
            display = mp.createVirtualDisplay("macro17-rec", w, h, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
            r.start()
            running = true
            Diag.d("vídeo", "gravação de tela iniciada ${w}x$h, áudio=$audio")
        } catch (e: Exception) {
            Diag.e("vídeo", "não foi possível gravar a tela", e)
            Toast.makeText(this, "Não foi possível gravar a tela: ${e.message}", Toast.LENGTH_LONG).show()
            release(); stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun release() {
        runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null
        runCatching { display?.release() }; display = null
        runCatching { projection?.stop() }; projection = null
        running = false
    }

    private fun finish() {
        val f = file
        release()
        if (f != null && f.exists() && f.length() > 0) {
            val v = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "Macro17_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Macro17")
            }
            runCatching {
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)!!
                contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
                Toast.makeText(this, "Gravação salva em Filmes/Macro17", Toast.LENGTH_LONG).show()
                Diag.d("vídeo", "gravação salva: $uri")
            }.onFailure { Diag.e("vídeo", "falha ao salvar o vídeo", it); Toast.makeText(this, "Falha ao salvar o vídeo", Toast.LENGTH_LONG).show() }
            f.delete()
        }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() { release(); super.onDestroy() }
}
