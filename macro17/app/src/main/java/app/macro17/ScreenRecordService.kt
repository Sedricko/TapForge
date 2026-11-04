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
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
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
    private var audioThread: Thread? = null
    @Volatile private var audioStop = false
    private var audioFile: File? = null
    @Volatile private var finishing = false

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
        finishing = false
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Gravação de tela", NotificationManager.IMPORTANCE_LOW))
        val stopPi = android.app.PendingIntent.getService(this, 1, Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP), android.app.PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH).setSmallIcon(R.drawable.ic_stop).setContentTitle("Gravando a tela").setContentText("Toque para parar")
            .setContentIntent(stopPi).setOngoing(true).build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        val audioMode = intent.getIntExtra("audio", 0) // 0 nenhum, 1 microfone, 2 áudio interno
        val audio = audioMode == 1
        if (audioMode != 0) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            startForeground(1, n, type)
            val data = intent.getParcelableExtra<Intent>("data")!!
            val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("code", 0), data)!!
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
            if (audioMode == 2) runCatching { startInternalAudio(mp) }.onFailure { Diag.e("vídeo", "áudio interno indisponível", it); Toast.makeText(this, "A gravação de áudio interno não é suportada nesta versão do Android.", Toast.LENGTH_LONG).show() }
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

    /** Áudio dos apps (AudioPlaybackCapture): PCM -> AAC -> .m4a; depois do fim junta com o vídeo. */
    private fun startInternalAudio(mp: MediaProjection) {
        val cfg = AudioPlaybackCaptureConfiguration.Builder(mp).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
        val fmt = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        val rec = AudioRecord.Builder().setAudioPlaybackCaptureConfig(cfg).setAudioFormat(fmt)
            .setBufferSizeInBytes(AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 4).build()
        val af = File(cacheDir, "rec_audio_${System.currentTimeMillis()}.m4a"); audioFile = af
        audioStop = false
        audioThread = Thread({
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC); setInteger(MediaFormat.KEY_BIT_RATE, 128_000); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val mux = MediaMuxer(af.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1; var started = false; var inputDone = false; var eos = false; var pts = 0L
            val info = MediaCodec.BufferInfo(); val buf = ByteArray(8192)
            try {
                rec.startRecording()
                while (!eos) {
                    if (!inputDone) {
                        val ii = codec.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val ib = codec.getInputBuffer(ii)!!
                            if (audioStop) { codec.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                            else {
                                val n = rec.read(buf, 0, minOf(buf.size, ib.capacity()))
                                if (n > 0) { ib.clear(); ib.put(buf, 0, n); codec.queueInputBuffer(ii, 0, n, pts, 0); pts += n * 1_000_000L / (44100 * 4) }
                                else codec.queueInputBuffer(ii, 0, 0, pts, 0)
                            }
                        }
                    }
                    val oi = codec.dequeueOutputBuffer(info, 10_000)
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = mux.addTrack(codec.outputFormat); mux.start(); started = true }
                    else if (oi >= 0) {
                        val ob = codec.getOutputBuffer(oi)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) { ob.position(info.offset); ob.limit(info.offset + info.size); mux.writeSampleData(track, ob, info) }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
                    }
                }
            } finally {
                runCatching { rec.stop() }; rec.release(); runCatching { codec.stop() }; codec.release()
                if (started) runCatching { mux.stop() }; mux.release()
            }
        }, "macro17-audio-interno").also { it.start() }
    }

    /** Junta o vídeo (sem áudio) com o .m4a num MP4 só; false se algo falhar (então fica só o vídeo). */
    private fun mergeAv(video: File, audio: File, out: File): Boolean = runCatching {
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val exs = listOf(video to "video/", audio to "audio/").map { (f, pre) -> MediaExtractor().apply { setDataSource(f.path) } to pre }
        val tracks = exs.map { (ex, pre) ->
            val i = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith(pre) }
            ex.selectTrack(i); mux.addTrack(ex.getTrackFormat(i))
        }
        mux.start()
        val bb = ByteBuffer.allocate(1 shl 20); val bi = MediaCodec.BufferInfo()
        exs.forEachIndexed { k, (ex, _) ->
            while (true) { val n = ex.readSampleData(bb, 0); if (n < 0) break; bi.set(0, n, ex.sampleTime, ex.sampleFlags); mux.writeSampleData(tracks[k], bb, bi); ex.advance() }
            ex.release()
        }
        mux.stop(); mux.release(); true
    }.getOrDefault(false)

    private fun finish() {
        if (finishing) return // o onStop da projeção volta a chamar depois do release
        finishing = true
        val f = file; val af = audioFile; val at = audioThread
        audioStop = true
        release()
        val ui = Handler(Looper.getMainLooper())
        fun say(t: String) = ui.post { Toast.makeText(this, t, Toast.LENGTH_LONG).show() }
        // Junção e cópia fora da thread principal: vídeo grande não congela o app (ANR).
        Thread({
            at?.join(4000)
            var src = f
            if (f != null && af != null && af.exists() && af.length() > 0) {
                val merged = File(cacheDir, "rec_m_${System.currentTimeMillis()}.mp4")
                if (mergeAv(f, af, merged)) { f.delete(); src = merged } else Diag.d("vídeo", "não foi possível juntar o áudio; salvando só o vídeo")
            }
            af?.delete()
            if (src != null && src.exists() && src.length() > 0) {
                val v = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "Macro17_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Macro17")
                }
                runCatching {
                    val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)!!
                    contentResolver.openOutputStream(uri)!!.use { o -> src.inputStream().use { it.copyTo(o) } }
                    say("Gravação salva em Filmes/Macro17")
                    Diag.d("vídeo", "gravação salva: $uri")
                    ui.post { runCatching { startActivity(android.content.Intent(this, VideoPlayerActivity::class.java).setData(uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } } // pode ser barrado em segundo plano: o aviso acima continua
                }.onFailure { Diag.e("vídeo", "falha ao salvar o vídeo", it); say("Falha ao salvar o vídeo") }
                src.delete()
            }
            ui.post { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }, "macro17-rec-save").start()
    }

    override fun onDestroy() { release(); super.onDestroy() }
}
