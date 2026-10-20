package com.tapforge.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tapforge.data.MacroStore
import com.tapforge.model.*
import com.tapforge.service.ScreenCaptureService

/** Tela mínima de onboarding (Fase 1). O editor visual chega na Fase 2. */
class MainActivity : Activity() {
    private val reqCapture = 1

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        fun add(t: String, onClick: () -> Unit) = col.addView(Button(this).apply { text = t; setOnClickListener { onClick() } })
        col.addView(TextView(this).apply {
            text = "TapForge\n1) Ative o serviço de acessibilidade.\n2) (Opcional) Ative a captura de tela para imagem/OCR.\n3) Crie o macro de exemplo e use o botão ▶ flutuante."
        })
        add("1. Abrir acessibilidade") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        add("2. Ativar captura de tela") {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
        }
        add("3. Criar macro de exemplo (toque no centro, 60 s)") {
            val m = resources.displayMetrics
            val demo = Macro(
                id = "demo", name = "Toque no centro",
                actions = listOf(
                    Action.Tap(m.widthPixels / 2, m.heightPixels / 2, 40, Jitter(posPx = 6, timeMs = 30)),
                    Action.Wait(100, jitterMs = 40),
                ),
                maxRuntimeMs = 60_000,
            )
            MacroStore.save(this, demo); MacroStore.select(this, demo.id)
            Toast.makeText(this, "Macro de exemplo selecionado", Toast.LENGTH_SHORT).show()
        }
        setContentView(col)
    }

    @Deprecated("API simples para o esqueleto")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == reqCapture && res == RESULT_OK && data != null) {
            startForegroundService(Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_CODE, res).putExtra(ScreenCaptureService.EXTRA_DATA, data))
        }
    }
}
