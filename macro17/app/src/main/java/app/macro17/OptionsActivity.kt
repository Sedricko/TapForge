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
    private var resetting = false

    private fun pushOptions() { MacroService.instance?.applyOptions() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val p = Prefs(this)

        val sizes = intArrayOf(5, 10, 20, 40, 80, 120, 160)
        var sizeTxt: TextView? = null
        sizeTxt = p.valueRow("Tamanho da imagem a salvar", "${store.thumbPx} Pixel") {
            AlertDialog.Builder(this).setTitle("Tamanho da imagem a salvar").setSingleChoiceItems(sizes.map { "$it Pixel" }.toTypedArray(), sizes.indexOf(store.thumbPx)) { d, w ->
                store.thumbPx = sizes[w]; sizeTxt?.text = "${sizes[w]} Pixel"; d.dismiss()
            }.setNegativeButton("Cancelar", null).show()
        }
        p.switchRow("Parar Reprodução com Tecla de Volume", "", store.stopVolume) { store.stopVolume = it }
        p.switchRow("Iniciar macro com tecla de volume.", "", store.startVolume) { store.startVolume = it }
        p.switchRow("Mostrar Toques", "Mostrar feedback visual para reproduções", store.showTouches) { store.showTouches = it }
        p.switchRow("Manter Tela Ligada", "A tela permanecerá ligada enquanto o Macro17 estiver em execução.", store.keepOn) { store.keepOn = it }
        p.switchRow("Depurar", "Exibir informações de depuração na tela", store.debugOverlay) { store.debugOverlay = it }
        p.switchRow("Alarme", "Um alarme soará quando a macro terminar.", store.endAlarm) { store.endAlarm = it }
        p.switchRow("Monitoramento contínuo da tela", "Mantém a captura de tela em ociosidade para reduzir o atraso de inicialização. (indisponível: o Macro17 captura sob demanda)", false, enabled = false)
        battery = p.switchRow("Desativar otimização de bateria", "Permita o uso em segundo plano sem restrições para uma operação estável.", ignoringBattery()) {
            // Como no base: o toque não alterna; explica e leva às informações do app, onde está "Bateria". O switch reflete o estado real.
            if (resetting) return@switchRow
            resetting = true; battery?.isChecked = ignoringBattery(); resetting = false
            AlertDialog.Builder(this).setTitle("Garantir a operação estável")
                .setMessage("Para executar macros de forma estável em segundo plano, você precisa alterar as configurações de bateria. Permita o uso em segundo plano sem restrições.\n\n" +
                    if (ignoringBattery()) "Na próxima tela, toque em Bateria e escolha Otimizado ou Restrito." else "Na próxima tela, toque em Bateria e escolha Sem restrições.")
                .setPositiveButton("Ir para Configurações") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                .setNegativeButton("Agora não", null).show()
        }

        p.category("Janela Pop-up")
        val tr = p.valueRow("Transparência da janela flutuante (%)", "${100 - store.opacity}%") { transparencyDialog() }
        transparencyText = tr
        p.switchRow("Ocultar janela pop-up durante a reprodução", "Pressione a tecla de volume para parar a reprodução da macro.", store.hidePopup) { store.hidePopup = it; pushOptions() }
        // Exclusivos, como no base: ligar um desliga o outro.
        var playSw: Switch? = null
        val simpleSw = p.switchRow("Visualização Simples", "Simplifica os botões de controle exibidos na tela.", store.simpleView) { store.simpleView = it; if (it) playSw?.isChecked = false; pushOptions() }
        playSw = p.switchRow("Modo de Reprodução", "Apenas o botão de reprodução é exibido.", store.playMode) { store.playMode = it; if (it) simpleSw.isChecked = false; pushOptions() }

        p.category("Gravação de tela", iconRes = R.drawable.bp_new_icon)
        var audioTxt: TextView? = null; var bitTxt: TextView? = null; var fpsTxt: TextView? = null
        val audioNames = arrayOf("Sem áudio", "Gravar áudio externo (Microfone)", "Gravar áudio interno")
        audioTxt = p.valueRow("Configurações de gravação de áudio", audioNames[store.recAudio.coerceIn(0, 2)]) {
            AlertDialog.Builder(this).setTitle("Configurações de gravação de áudio").setSingleChoiceItems(audioNames, store.recAudio.coerceIn(0, 2)) { d, w -> store.recAudio = w; if (w == 2) android.widget.Toast.makeText(this, "Devido às políticas do Google, o áudio de alguns apps (como o YouTube) pode não ser gravado.", android.widget.Toast.LENGTH_LONG).show(); audioTxt?.text = audioNames[w]; d.dismiss() }.setNegativeButton("Cancelar", null).show()
        }
        val bits = intArrayOf(10_485_760, 5_242_880, 2_097_152); val bitNames = arrayOf("Alta (10 Mbps)", "Média (5 Mbps)", "Baixa (2 Mbps)")
        bitTxt = p.valueRow("Taxa de bits de vídeo", "Defina a qualidade visual da gravação. Valores mais altos evitam a pixelização, mas aumentam o tamanho do arquivo.\n\n" + bitNames[bits.indexOf(store.recBitrate).let { if (it < 0) 1 else it }]) {
            AlertDialog.Builder(this).setTitle("Taxa de bits de vídeo").setSingleChoiceItems(bitNames, bits.indexOf(store.recBitrate).let { if (it < 0) 1 else it }) { d, w -> store.recBitrate = bits[w]; bitTxt?.text = "Defina a qualidade visual da gravação. Valores mais altos evitam a pixelização, mas aumentam o tamanho do arquivo.\n\n" + bitNames[w]; d.dismiss() }.setNegativeButton("Cancelar", null).show()
        }
        val fpsList = intArrayOf(30, 24, 15); val fpsNames = arrayOf("30 FPS (Mais fluido, arquivo maior)", "24 FPS (Padrão, recomendado)", "15 FPS (Economiza espaço)")
        fpsTxt = p.valueRow("Taxa de quadros de vídeo", "Defina os quadros por segundo (FPS). Valores maiores tornam o vídeo mais fluido, mas resultam em arquivos maiores.\n\n${fpsNames[fpsList.indexOf(store.recFps).let { if (it < 0) 1 else it }]}") {
            AlertDialog.Builder(this).setTitle("Taxa de quadros de vídeo").setSingleChoiceItems(fpsNames, fpsList.indexOf(store.recFps).let { if (it < 0) 1 else it }) { d, w -> store.recFps = fpsList[w]; fpsTxt?.text = "Defina os quadros por segundo (FPS). Valores maiores tornam o vídeo mais fluido, mas resultam em arquivos maiores.\n\n${fpsNames[w]}"; d.dismiss() }.setNegativeButton("Cancelar", null).show()
        }

        p.category("Macro17")
        p.switchRow("Parar ao bloquear a tela", "Encerra a macro quando a tela apaga.", store.stopOnLock) { store.stopOnLock = it }
        p.valueRow("Início Automático", "Selecione a macro padrão para usar com o programa.") { if (Store(this).list().isEmpty()) android.widget.Toast.makeText(this, "Nenhum arquivo de macro.", android.widget.Toast.LENGTH_LONG).show() else startActivity(android.content.Intent(this, AutoOpenActivity::class.java)) }
        p.valueRow("Leitor de números", "Ensine os dígitos de um jogo ou contador para o passo “Ler número”.") { startActivity(Intent(this, ReaderActivity::class.java)) }
        p.valueRow("Monitor e logs", "Registro de eventos, relatório do sistema e exportação para quem corrige.") { startActivity(Intent(this, DiagActivity::class.java)) }

        setContentView(p.page("Configurações", Ic.BACK, { finish() }))
    }

    private var transparencyText: TextView? = null

    override fun onResume() { super.onResume(); resetting = true; battery?.isChecked = ignoringBattery(); resetting = false }

    private fun ignoringBattery() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    /** 0 a 50%, como no base: mais transparente que isso o painel some e não dá para tocar nele. */
    private fun transparencyDialog() {
        val value = TextView(this).apply { text = "${100 - store.opacity}%"; textSize = 16f; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)) }
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
