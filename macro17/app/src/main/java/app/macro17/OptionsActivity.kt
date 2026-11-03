package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Configurações no formato do app base (mesmos títulos e descrições, mesma ordem). Cada opção grava na hora e o serviço
 * relê. O que o Macro17 ainda não tem aparece esmaecido e sem resposta, com "(indisponível)", em vez de fingir que funciona.
 */
class OptionsActivity : Activity() {
    private lateinit var store: Store
    private var battery: Switch? = null

    private fun pushOptions() { MacroService.instance?.applyOptions() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val p = Prefs(this)

        val sizes = intArrayOf(40, 60, 80, 100, 120, 160)
        var sizeTxt: TextView? = null
        sizeTxt = p.valueRow("Tamanho da imagem a salvar", "${store.thumbPx} Pixel") {
            AlertDialog.Builder(this).setTitle("Tamanho da imagem a salvar").setSingleChoiceItems(sizes.map { "$it Pixel" }.toTypedArray(), sizes.indexOf(store.thumbPx)) { d, w ->
                store.thumbPx = sizes[w]; sizeTxt?.text = "${sizes[w]} Pixel"; d.dismiss()
            }.setNegativeButton("Cancelar", null).show()
        }
        p.switchRow("Parar Reprodução com Tecla de Volume", "Qualquer tecla de volume, ao soltar, para a macro em execução.", store.stopVolume) { store.stopVolume = it }
        p.switchRow("Iniciar macro com tecla de volume.", "Com o app ocioso, soltar a tecla de volume inicia a macro selecionada.", store.startVolume) { store.startVolume = it }
        p.switchRow("Mostrar Toques", "Mostrar feedback visual para reproduções", store.showTouches) { store.showTouches = it }
        p.switchRow("Manter Tela Ligada", "A tela permanecerá ligada enquanto o Macro17 estiver em execução.", store.keepOn) { store.keepOn = it }
        p.switchRow("Depurar", "Exibir informações de depuração na tela: abre o registro ao vivo ao iniciar uma macro.", store.debugOverlay) { store.debugOverlay = it }
        p.switchRow("Alarme", "Um alarme soará quando a macro terminar.", store.endAlarm) { store.endAlarm = it }
        p.switchRow("Parar ao bloquear a tela", "Encerra a macro quando a tela apaga.", store.stopOnLock) { store.stopOnLock = it }
        p.switchRow("Monitoramento contínuo da tela", "Mantém a captura de tela em ociosidade para reduzir o atraso de inicialização. (indisponível: o Macro17 captura sob demanda)", false, enabled = false)
        battery = p.switchRow("Desativar otimização de bateria", "Permita o uso em segundo plano sem restrições para uma operação estável.", ignoringBattery()) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        p.category("Janela Pop-up", Ic.FRAME)
        val tr = p.valueRow("Transparência da janela flutuante (%)", "${100 - store.opacity} %") { transparencyDialog() }
        transparencyText = tr
        p.switchRow("Ocultar janela pop-up durante a reprodução", "Pressione a tecla de volume para parar a reprodução da macro.", store.hidePopup) { store.hidePopup = it; pushOptions() }
        // Exclusivos, como no base: ligar um desliga o outro.
        var playSw: Switch? = null
        val simpleSw = p.switchRow("Visualização Simples", "Simplifica os botões de controle exibidos na tela.", store.simpleView) { store.simpleView = it; if (it) playSw?.isChecked = false; pushOptions() }
        playSw = p.switchRow("Modo de Reprodução", "Apenas o botão de reprodução é exibido.", store.playMode) { store.playMode = it; if (it) simpleSw.isChecked = false; pushOptions() }

        p.category("Gravação de tela", Ic.REC)
        p.valueRow("Configurações de gravação de áudio", "Sem áudio (indisponível)", enabled = false)
        p.valueRow("Taxa de bits de vídeo", "Defina a qualidade visual da gravação. (indisponível)", enabled = false)
        p.valueRow("Taxa de quadros de vídeo", "Defina os quadros por segundo (FPS). (indisponível)", enabled = false)

        p.category("Macro17", Ic.GEAR)
        p.valueRow("Leitor de números", "Ensine os dígitos de um jogo ou contador para o passo “Ler número”.") { startActivity(Intent(this, ReaderActivity::class.java)) }
        p.valueRow("Monitor e logs", "Registro de eventos, relatório do sistema e exportação para quem corrige.") { startActivity(Intent(this, DiagActivity::class.java)) }

        setContentView(p.page("Configurações", Ic.BACK, { finish() }))
    }

    private var transparencyText: TextView? = null

    override fun onResume() { super.onResume(); battery?.isChecked = ignoringBattery() }

    private fun ignoringBattery() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    /** 0 a 50%, como no base: mais transparente que isso o painel some e não dá para tocar nele. */
    private fun transparencyDialog() {
        val value = TextView(this).apply { text = "${100 - store.opacity} %"; textSize = 16f; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)) }
        val bar = SeekBar(this).apply {
            max = 50; progress = 100 - store.opacity; contentDescription = "Transparência da janela flutuante"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                    store.opacity = 100 - v; value.text = "$v %"; transparencyText?.text = "$v %"; pushOptions()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        AlertDialog.Builder(this).setTitle("Transparência da janela flutuante (%)")
            .setView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(4)); addView(value); addView(bar) })
            .setPositiveButton("OK", null).show()
    }
}
