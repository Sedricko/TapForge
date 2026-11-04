package app.macro17

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/** Transparente: pede o microfone (se o áudio estiver ligado) e o diálogo "Compartilhar a tela" do sistema, e inicia a gravação. */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class ScreenRecordPermissionActivity : Activity() {
    private lateinit var store: Store

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        if (store.recAudio == 1 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7)
        } else ask()
    }

    private fun ask() = startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 8)

    override fun onRequestPermissionsResult(req: Int, p: Array<out String>, r: IntArray) {
        if (req == 7) { if (r.firstOrNull() != PackageManager.PERMISSION_GRANTED) store.recAudio = 0; ask() }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (ScreenRecordService.running) { finish(); return } // um segundo toque em gravar não pode iniciar outro serviço em primeiro plano
        if (req == 8 && res == RESULT_OK && data != null) {
            startForegroundService(Intent(this, ScreenRecordService::class.java).putExtra("code", res).putExtra("data", data)
                .putExtra("audio", store.recAudio).putExtra("bitrate", store.recBitrate).putExtra("fps", store.recFps))
        } else Diag.d("vídeo", "compartilhamento de tela não permitido")
        finish()
    }
}
