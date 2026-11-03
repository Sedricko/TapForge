package app.macro17

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.hypot

// Lado do recorte de miniatura ao redor do ponto gravado (opção "tamanho da imagem a salvar" do base, padrão 80 px).

class MacroService : AccessibilityService() {
    companion object {
        @Volatile var instance: MacroService? = null
    }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private lateinit var engine: Engine
    private lateinit var logPanel: LogPanel
    private var bubble: View? = null
    private var recording = false
    private val rec = ArrayList<Step>()
    private lateinit var recorder: Recorder
    private var lock: BroadcastReceiver? = null

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        store = Store(this)
        // Ao terminar (qualquer motivo) a tela volta a poder apagar.
        logPanel = LogPanel(this, wm) { engine.stop() }
        engine = Engine(this, { msg ->
            toast(msg); ui.post { awake(false); bubble?.visibility = View.VISIBLE }; logPanel.setThrough(false); setPlayIcon(false)
            if (msg == "Macro concluída" && store.endAlarm) playEndAlarm() // só no fim natural, não em parada manual nem em erro
        }, ::toast, logPanel::captureHide)
        instance = this
        Alarms.rearm(this)
        Diag.d("serviço", "acessibilidade conectada (gestos/captura/janelas conforme accessibility_config)")
        showBubble()
        lock = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { Diag.d("sistema", "tela apagou; parar ao bloquear=${store.stopOnLock}"); if (store.stopOnLock) engine.stop() }
        }
        val off = IntentFilter(Intent.ACTION_SCREEN_OFF)
        // A versão com flags só existe na API 33; nas 31 e 32 o registro simples já basta para broadcast do sistema.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(lock, off, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(lock, off)
    }

    /**
     * Teclas de volume, como no base: só no ACTION_UP e sem consumir a tecla (o volume continua mudando).
     * Parar: opção de parar OU "ocultar janela" (sem painel visível a tecla é o único jeito). Iniciar: só com o app ocioso.
     */
    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (!::store.isInitialized || e.action != KeyEvent.ACTION_UP) return false
        if (e.keyCode != KeyEvent.KEYCODE_VOLUME_UP && e.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (engine.running) {
            if (store.stopVolume || store.hidePopup) { Diag.d("serviço", "tecla de volume: parar"); engine.stop() }
        } else if (store.startVolume && !recording) {
            Diag.d("serviço", "tecla de volume: iniciar")
            store.selected?.let { runMacro(it) }
        }
        return false
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) = Unit
    override fun onInterrupt() { Diag.d("serviço", "onInterrupt (sistema interrompeu o feedback de acessibilidade)"); engine.stop() }

    override fun onUnbind(intent: Intent?): Boolean {
        Diag.d("serviço", "acessibilidade desconectada")
        logPanel.hide()
        engine.stop()
        stopRecording(save = false)
        lock?.let { runCatching { unregisterReceiver(it) } }
        lock = null
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        instance = null
        return super.onUnbind(intent)
    }

    fun isRunning() = engine.running

    /** INICIAR da Home: mostra o painel de controle. */
    fun showControls() {
        Diag.d("serviço", "painel aberto pela Home")
        ui.post { bubble?.visibility = View.VISIBLE; openPanel(true) }
    }

    /** PARAR da Home ou Fechar: fecha o painel e some com a janela. */
    fun hideControls() {
        Diag.d("serviço", "painel fechado pela Home")
        ui.post { stopRecording(save = false); if (engine.running) engine.stop(); openPanel(false) }
    }

    fun panelOpen() = ::panel.isInitialized && panel.visibility == View.VISIBLE

    /** Abre ou fecha o painel de registro ao vivo. Chamável de qualquer thread. */
    fun toggleLog() { ui.post { if (logPanel.shown) logPanel.hide() else logPanel.show() } }

    /** "Mostrar Toques": um ponto claro sob cada dedo durante o gesto; janela sem toque, some logo depois. */
    fun showTouches(points: List<Pair<Int, Int>>, ms: Int) {
        if (!store.showTouches) return
        ui.post {
            val size = dp(36)
            points.take(4).forEach { (x, y) ->
                val v = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFD5DDFF.toInt()) }; alpha = 0.8f }
                val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; this.x = x - size / 2; this.y = y - size / 2 }
                runCatching { wm.addView(v, lp) }.onSuccess { ui.postDelayed({ runCatching { wm.removeView(v) } }, ms + 120L) }
            }
        }
    }

    fun stopMacro() { Diag.d("serviço", "parar macro pedido"); engine.stop() }

    fun toast(msg: String) { ui.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }

    /** Alarme: para o que estiver rodando, espera 1 s e roda a macro agendada. Recusa se estiver gravando. */
    fun fireAlarm(id: String) {
        if (recording) return toast("Agendamento ignorado: gravação em andamento")
        if (engine.running) engine.stop()
        ui.postDelayed({ store.selected = id; runMacro(id) }, 1000)
    }

    /** Chamável de qualquer thread (alarme, app). */
    fun runMacro(id: String) {
        val m = store.get(id)
        Diag.d("serviço", "executar macro id=$id -> ${m?.name ?: "NÃO ENCONTRADA"} (rodando=${engine.running}, gravando=$recording)")
        when {
            m == null -> toast("Macro não encontrada")
            recording -> toast("Termine a gravação primeiro")
            engine.running -> toast("Já existe uma macro em execução")
            else -> {
                // awake(true) entra na fila antes de a thread da macro poder postar o awake(false) do fim.
                ui.post { awake(true) }
                if (engine.start(m)) {
                    toast("Executando: ${m.name}"); logPanel.setThrough(true); setPlayIcon(true)
                    // Ocultar: o painel some durante a reprodução (a tecla de volume para a macro, ver onKeyEvent). Depurar: abre o registro.
                    ui.post { bubble?.visibility = View.VISIBLE; openPanel(true); if (store.hidePopup) bubble?.visibility = View.INVISIBLE; if (store.debugOverlay && !logPanel.shown) logPanel.show() }
                } else ui.post { awake(false) }
            }
        }
    }

    /** Toque de alarme por 5 s e uma vibração curta ("Alarme" nas configurações). */
    private fun playEndAlarm() = ui.post {
        runCatching {
            val r = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            r.play(); ui.postDelayed({ runCatching { r.stop() } }, 5000)
            getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE))
        }.onFailure { Diag.e("serviço", "alarme de fim falhou", it) }
    }

    /** Opções que valem na hora (a opacidade); as demais são lidas a cada execução. */
    fun applyOptions() {
        ui.post {
            bubble?.alpha = store.opacity / 100f
            if (::row1.isInitialized && !recording) { if (store.playMode || store.simpleView) { expanded = false; row2.visibility = View.GONE }; fillRow1() }
        }
    }

    // A janela da bolha é a única que o serviço mantém: o flag mantém a tela ligada enquanto ela existe.
    private fun awake(on: Boolean) {
        val v = bubble ?: return
        val lp = v.layoutParams as WindowManager.LayoutParams
        val keep = on && store.keepOn
        lp.flags = if (keep) lp.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        wm.updateViewLayout(v, lp)
    }

    // --- bolha e painel --------------------------------------------------------------------

    // Como no app base: a janela é um orbe pequeno (fechado) ou o painel de controle (aberto). Uma janela de overlay
    // captura todo o toque na sua área, então fechada ela é só o orbe. O painel tem duas linhas; a segunda abre pela seta.
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private lateinit var orbBtn: View
    private lateinit var panel: android.widget.LinearLayout
    private lateinit var row1: android.widget.LinearLayout
    private lateinit var row2: android.widget.LinearLayout
    private var playBtn: ImageView? = null
    private var expanded = false
    private val teal = 0xFF00574B.toInt() // cor dos ícones do painel do base

    /** Vetores do próprio painel do base, esticados até o tamanho do botão (FIT_XY, sem padding), como lá. */
    private val baseIcons = mapOf(
        Ic.REC to R.drawable.bp_ic_menu_record, Ic.ADDLIST to R.drawable.bp_playlist_add, Ic.ALARM to R.drawable.bp_schedule_fill,
        Ic.EDIT to R.drawable.bp_ic_menu_edit, Ic.GEAR to R.drawable.bp_ic_settings, Ic.CANCEL to R.drawable.bp_ic_cancel_circle_fill_24,
        Ic.FRAME to R.drawable.bp_ic_crop_free_24px, Ic.STOP to R.drawable.ic_stop, Ic.PLAY to R.drawable.bp_play_arrow, Ic.PAUSE to R.drawable.bp_ic_media_pause,
    )

    private fun pbtn(ic: Ic, desc: String, click: () -> Unit) = ImageView(this).apply {
        val res = baseIcons[ic]
        if (res != null) { setImageResource(res); scaleType = ImageView.ScaleType.FIT_XY; setColorFilter(teal) }
        else { setImageDrawable(Icon(ic, teal)); setPadding(dp(5), dp(5), dp(5), dp(5)) }
        contentDescription = desc
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(42), dp(42)).also { it.setMargins(dp(1), dp(1), dp(1), dp(1)) }
        setOnClickListener { click() }
        dragger(this) // como no base, qualquer botão arrasta o painel
    }

    /** "REC" em disco, como no base. */
    private fun recBadge(click: () -> Unit) = label("REC", 11f, Color.WHITE, bold = true).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(teal) }
        contentDescription = "Gravar"
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(30), dp(30)).also { it.setMargins(dp(8), dp(8), dp(8), dp(8)) }
        setOnClickListener { click() }
    }

    private fun fillRow1() {
        row1.removeAllViews()
        if (recording) {
            row1.addView(pbtn(Ic.STOP, "Encerrar a gravação (segure para descartar)") { stopRecording(save = true) }.also {
                it.setOnLongClickListener { stopRecording(save = false); toast("Gravação descartada"); true }
            })
            row1.addView(pbtn(Ic.ADDLIST, "Adicionar o item na posição dos marcadores") { addItem() })
            val lbl = label(recorder.mode.label, 15f, B.TEXT, bold = true).apply { setPadding(dp(4), 0, dp(10), 0) }
            row1.addView(android.widget.LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                foreground = ripple(22, 0x2200796B)
                contentDescription = "Trocar o tipo do item"
                addView(ImageView(this@MacroService).apply { setImageDrawable(Icon(recorder.mode.ic, teal)); layoutParams = android.widget.LinearLayout.LayoutParams(dp(40), dp(40)); setPadding(dp(8), dp(8), dp(8), dp(8)) })
                addView(lbl)
                setOnClickListener { recorder.cycle(); fillRow1() }
            })
        } else {
            val pb = pbtn(if (playing) Ic.STOP else Ic.PLAY, "Executar ou parar a macro") { togglePlay() }
            playBtn = pb
            row1.addView(pb)
            if (playing) { // reproduzindo, como no base: só Parar e Pausar/Retomar
                row1.addView(pbtn(if (engine.paused) Ic.PLAY else Ic.PAUSE, if (engine.paused) "Retomar" else "Pausar") {
                    if (engine.paused) ui.postDelayed({ engine.paused = false; fillRow1() }, 500) else { engine.paused = true; fillRow1() }
                })
                return
            }
            // "Modo de Reprodução": só o ▶. "Visualização Simples": sem enquadrar e sem a segunda linha.
            if (!store.playMode) row1.addView(pbtn(Ic.REC, "Gravar") { startRecording() })
            if (!store.playMode && !store.simpleView) row1.addView(pbtn(Ic.FRAME, "Capturar imagem para a macro") { captureImage() })
            if (!store.playMode && !store.simpleView) row1.addView(pbtn(Ic.CHEVRON, if (expanded) "Recolher" else "Mais opções") { expanded = !expanded; row2.visibility = if (expanded) View.VISIBLE else View.GONE; fillRow1() }.also { it.rotation = if (expanded) -90f else 90f })
        }
    }

    private fun togglePlay() {
        if (engine.running) { engine.stop(); return }
        store.selected?.let { runMacro(it) } ?: toast("Crie ou selecione uma macro no app")
    }

    /** Atualiza o ▶/■ do painel; chamável de qualquer thread. */
    private var playing = false // estado mostrado no painel (engine.running ainda é true dentro do callback de fim)
    private fun setPlayIcon(running: Boolean) = ui.post { playing = running; if (running) { expanded = false; if (::row2.isInitialized) row2.visibility = View.GONE }; if (::row1.isInitialized && !recording) fillRow1() else playBtn?.setImageResource(if (running) R.drawable.ic_stop else R.drawable.bp_play_arrow) }

    private fun openPanel(open: Boolean) {
        if (!::panel.isInitialized) return
        panel.visibility = if (open) View.VISIBLE else View.GONE
        orbBtn.visibility = View.GONE // o base não tem orbe: PARAR/Fechar some com tudo
        bubble?.let { runCatching { wm.updateViewLayout(it, bubbleLp) } }
    }

    /** Arrasta a janela da bolha inteira por [v]; toque curto continua sendo clique. */
    private fun dragger(v: View) {
        val slop = dp(10) // limiar do base: 10dp por eixo
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var drag = false
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = bubbleLp.x; sy = bubbleLp.y; drag = false; false }
                MotionEvent.ACTION_MOVE -> {
                    if (!drag && (kotlin.math.abs(e.rawX - dx) > slop || kotlin.math.abs(e.rawY - dy) > slop)) drag = true
                    if (drag) {
                        bubbleLp.x = sx + (e.rawX - dx).toInt(); bubbleLp.y = sy + (e.rawY - dy).toInt()
                        bubble?.let { wm.updateViewLayout(it, bubbleLp) }
                    }
                    drag
                }
                MotionEvent.ACTION_UP -> { if (drag) view.isPressed = false; drag }
                else -> false
            }
        }
    }

    private fun showBubble() {
        bubbleLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            // TYPE_ACCESSIBILITY_OVERLAY dispensa a permissão "exibir sobre outros apps".
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 } // canto superior esquerdo, como no base
        recorder = Recorder(this, wm)

        row1 = android.widget.LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row2 = android.widget.LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(pbtn(Ic.ALARM, "Agendamento") { startActivity(Intent(this@MacroService, AlarmListActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            addView(pbtn(Ic.EDIT, "Abrir o editor da macro") {
                startActivity(Intent(this@MacroService, EditorActivity::class.java).putExtra("id", store.selected ?: "new").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            addView(pbtn(Ic.GEAR, "Configurações") { startActivity(Intent(this@MacroService, OptionsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            addView(pbtn(Ic.INFO, "Registro ao vivo") { toggleLog() })
            addView(pbtn(Ic.CANCEL, "Fechar o painel") { hideControls() })
        }
        panel = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = rounded(0xFFFFFFFF.toInt(), 8, 0xFFE0E0E0.toInt()) // fundo branco, raio 8dp, traço 1dp, sem sombra
            setPadding(dp(2), dp(2), dp(2), dp(2))
            visibility = View.GONE
            addView(row1); addView(row2)
        }
        fillRow1()
        orbBtn = circleBtn(Ic.TAP, C.BLUE, 48, "Abrir o painel de controle") { openPanel(true) }.also { it.visibility = View.GONE }
        dragger(orbBtn)

        val frame = FrameLayout(this).apply { addView(orbBtn); addView(panel) }
        bubble = frame
        frame.alpha = store.opacity / 100f
        wm.addView(frame, bubbleLp)
    }

    // --- captura de imagem ---------------------------------------------------------------

    /** Orbe → Capturar: congela a tela, deixa recortar e acrescenta um passo IMAGE ao fim da macro selecionada. */
    // Uma captura de cada vez: um segundo toque no enquadrar durante a contagem abria dois seletores empilhados (visto no log: "iniciada ×2").
    private var capturing = false

    private fun captureImage() {
        if (capturing) return toast("Captura em andamento")
        val m = store.selected?.let { store.get(it) } ?: return toast("Selecione uma macro no app antes de capturar")
        if (recording || engine.running) return toast("Pare a gravação ou a macro antes de capturar")
        capturing = true
        Diag.d("captura", "iniciada para \"${m.name}\"")
        bubble?.visibility = View.INVISIBLE // o orbe não pode aparecer no quadro
        // O aviso tem de sumir antes do quadro: um toast ainda na tela entra na imagem capturada (visto em aparelho).
        val hint = Toast.makeText(this, "Capturando em 3 s: deixe visível o que quer achar", Toast.LENGTH_SHORT).also { it.show() }
        ui.postDelayed({ hint.cancel() }, 1500)
        ui.postDelayed({ shoot(m.id) }, 3000)
    }

    private fun shotFailed(msg: String) { capturing = false; Diag.e("captura", msg); toast(msg); bubble?.visibility = View.VISIBLE }

    private fun shoot(macroId: String) {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                val hb = r.hardwareBuffer
                val bmp = try { Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) } finally { hb.close() }
                if (bmp == null) shotFailed("Captura falhou") else pick(macroId, bmp)
            }
            override fun onFailure(code: Int) = shotFailed("Captura recusada (código $code)")
        })
    }

    private fun pick(macroId: String, shot: Bitmap) {
        val picker = RegionPicker(this, shot)
        val root = FrameLayout(this)
        fun close() {
            capturing = false
            runCatching { wm.removeView(root) }
            shot.recycle()
            bubble?.visibility = View.VISIBLE
        }
        fun save() {
            val r = picker.region() ?: return toast("Marque uma área de pelo menos ${RegionPicker.MIN} px")
            val m = store.get(macroId) ?: run { close(); return toast("Macro não encontrada") }
            try {
                val tid = store.newTemplateId()
                val next = m.copy(steps = m.steps.afterInsert(m.steps.size, Step(Type.IMAGE, ms = 5000, text = tid, thr = 95)))
                next.validate()?.let { throw IllegalArgumentException(it) } // antes de gravar o PNG, para não deixar arquivo solto
                store.saveTemplate(tid, Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height()))
                store.save(next)
                Diag.d("captura", "imagem salva: passo ${next.steps.size} de \"${m.name}\", área ${r.left},${r.top},${r.right},${r.bottom} (${r.width()}x${r.height()}), modelo $tid")
                toast("Imagem adicionada como passo ${next.steps.size} de “${m.name}”")
            } catch (e: Exception) { toast("Falha: ${e.message}") }
            close()
        }
        // Número: um passo que lê o valor naquela área. Amostra: o recorte vai para o Leitor de números, onde você digita o que ele mostra.
        fun number() {
            val r = picker.region() ?: return toast("Marque uma área de pelo menos ${RegionPicker.MIN} px")
            val m = store.get(macroId) ?: run { close(); return toast("Macro não encontrada") }
            try {
                val next = m.copy(steps = m.steps.afterInsert(m.steps.size, Step(Type.NUMBER, r.left, r.top, r.right, r.bottom, ms = 5000, cycle = 300)))
                next.validate()?.let { throw IllegalArgumentException(it) }
                store.save(next)
                Diag.d("captura", "passo Ler número: ${next.steps.size} de \"${m.name}\", área ${r.left},${r.top},${r.right},${r.bottom}")
                toast("Passo “Ler número” adicionado (${next.steps.size}). Ajuste a condição no editor e ensine o leitor em Opções.")
            } catch (e: Exception) { toast("Falha: ${e.message}") }
            close()
        }
        fun sample() {
            val r = picker.region() ?: return toast("Marque uma área de pelo menos ${RegionPicker.MIN} px")
            try {
                store.saveSample(Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height()))
                toast("Amostra salva. Digite o número que ela mostra em Opções › Leitor de números.")
            } catch (e: Exception) { toast("Falha: ${e.message}") }
            close()
        }
        val bar = android.widget.LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = glass(28)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(pillBtn("Cancelar", C.DARK) { close() })
            addView(pillBtn("Amostra", C.DARK) { sample() })
            addView(pillBtn("Número", C.PURPLE) { number() })
            addView(pillBtn("Imagem", C.BLUE) { save() })
        }
        root.addView(picker, FrameLayout.LayoutParams(-1, -1))
        root.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also { it.bottomMargin = dp(64) })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        wm.addView(root, lp)
        toast("Arraste sobre a área e escolha: Imagem, Número ou Amostra")
    }

    // --- gravação por marcadores, como no base ----------------------------------------------
    // "+": esconde os marcadores, espera 500 ms, tira um quadro, guarda 80 px ao redor do ponto (miniatura), grava o item
    // com o atraso medido desde o "+" anterior e EXECUTA a ação no ponto, para o app avançar enquanto se grava.

    private var recBase: Macro? = null   // macro atual: a gravação anexa ao fim dela (sem macro, cria uma)
    private var recBaseCount = 0
    private var recLast = 0L
    private var adding = false

    private fun startRecording() {
        Diag.d("gravação", "início (marcadores)")
        if (engine.running) return toast("Pare a macro antes de gravar")
        if (recording) return
        val base = store.selected?.let { store.get(it) }
        recBase = base; recBaseCount = base?.steps?.size ?: 0
        rec.clear(); base?.let { rec.addAll(it.steps) }
        recLast = SystemClock.elapsedRealtime()
        recording = true
        recorder.show(Recorder.Mode.TAP)
        expanded = false; row2.visibility = View.GONE
        fillRow1()
        toast(if (base != null) "Gravando em “${base.name}”: os itens entram no fim. Arraste o marcador e toque em +." else "Gravando: arraste o marcador e toque em +. Toque no tipo para trocar.")
    }

    /** Atraso medido, como no base: tempo desde o último "+" menos 5 ms fixos, em passos de 0,1 s. */
    private fun measuredDelay(pressedAt: Long, @Suppress("UNUSED_PARAMETER") dur: Int): Int =
        ((((pressedAt - recLast - 5).coerceAtLeast(0) + 50) / 100) * 100).toInt().coerceAtMost(MAX_MS)

    private fun addItem() {
        if (!recording || adding) return
        if (rec.size >= MAX_STEPS) return toast("Limite de $MAX_STEPS passos")
        if (recorder.mode == Recorder.Mode.IMAGE && recorder.region() == null) return toast("Marque a área da imagem")
        val pressedAt = SystemClock.elapsedRealtime()
        adding = true
        recorder.setVisible(false) // os marcadores não podem aparecer no recorte
        ui.postDelayed({
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(res: AccessibilityService.ScreenshotResult) {
                    val hb = res.hardwareBuffer
                    val bmp = try { Bitmap.wrapHardwareBuffer(hb, res.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) } finally { hb.close() }
                    commitItem(pressedAt, bmp)
                }
                override fun onFailure(code: Int) { Diag.e("gravação", "captura recusada (código $code): o item entra sem miniatura"); commitItem(pressedAt, null) }
            })
        }, 500)
    }

    /** Recorte quadrado de [side] px centrado em ([cx],[cy]); null se não couber no quadro (como no base). */
    private fun cropAround(bmp: Bitmap, cx: Int, cy: Int, side: Int): Bitmap? {
        val x = cx - side / 2; val y = cy - side / 2
        return if (x < 0 || y < 0 || x + side > bmp.width || y + side > bmp.height) null else Bitmap.createBitmap(bmp, x, y, side, side)
    }

    private fun commitItem(pressedAt: Long, bmp: Bitmap?) {
        if (!recording) return // Stop durante o Add: nada de gravar nem tocar depois de salvar
        var restoreAfter = 0L
        try {
            val mode = recorder.mode
            var st: Step? = null
            if (mode == Recorder.Mode.IMAGE) {
                val r = recorder.region()
                if (bmp == null) toast("Sem captura: o item de imagem não foi gravado")
                else if (r != null) {
                    val x = r.left.coerceIn(0, bmp.width - 1); val y = r.top.coerceIn(0, bmp.height - 1)
                    val w = r.width().coerceAtMost(bmp.width - x); val h = r.height().coerceAtMost(bmp.height - y)
                    if (w < RegionPicker.MIN || h < RegionPicker.MIN) toast("Área pequena demais (mínimo ${RegionPicker.MIN} px)")
                    else {
                        val tid = store.newTemplateId()
                        store.saveTemplate(tid, Bitmap.createBitmap(bmp, x, y, w, h))
                        // Como no base: só busca a imagem, sem tocar (tap = false), com busca de 5 s.
                        st = Step(Type.IMAGE, ms = 5000, text = tid, thr = 95, tap = false, delay = measuredDelay(pressedAt, 5))
                    }
                }
            } else {
                val base = recorder.step(0)
                if (base != null) {
                    var thumb = ""
                    recorder.point()?.let { (px, py) ->
                        bmp?.let { b -> cropAround(b, px, py, store.thumbPx)?.let { c -> thumb = store.newTemplateId(); store.saveTemplate(thumb, c); c.recycle() } }
                    }
                    st = base.copy(delay = measuredDelay(pressedAt, base.ms), img = thumb,
                        // Como no base: a miniatura de 80 px só ilustra a lista; a busca por imagem fica desligada no item gravado.
                        useImg = false)
                    perform(st) // a ação roda no ponto; os marcadores voltam depois da duração
                    restoreAfter = st.ms + 100L
                }
            }
            if (st != null) {
                rec += st; recLast = pressedAt
                Diag.d("gravação", "item ${rec.size}: ${st.type} ${st.x1},${st.y1}" + (if (st.type == Type.SWIPE) ">${st.x2},${st.y2}" else "") + " ms=${st.ms} atraso=${st.delay}ms" + (if (st.img.isNotEmpty()) " miniatura" else ""))
                toast("Item ${rec.size - recBaseCount} adicionado: ${recorder.mode.label}")
            }
        } catch (e: Exception) { Diag.e("gravação", "falha ao gravar o item", e); toast("Falha: ${e.message}") }
        finally {
            bmp?.recycle()
            ui.postDelayed({ recorder.setVisible(true); adding = false }, restoreAfter)
        }
    }

    /** Executa o item gravado agora, no ponto, para o app de baixo reagir enquanto se grava. */
    private fun perform(st: Step) {
        val path = Path().apply { moveTo(st.x1.toFloat(), st.y1.toFloat()); if (st.type == Type.SWIPE) lineTo(st.x2.toFloat(), st.y2.toFloat()) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, st.ms.toLong().coerceAtLeast(1))).build()
        if (!dispatchGesture(g, null, null)) Diag.e("gravação", "gesto executado na gravação foi recusado")
    }

    private fun stopRecording(save: Boolean) {
        Diag.d("gravação", "fim (salvar=$save, itens=${rec.size - recBaseCount})")
        if (!recording) return
        recording = false; adding = false
        runCatching { recorder.hide() }
        if (::panel.isInitialized) fillRow1()
        if (!save) return
        if (rec.size == recBaseCount) return toast("Nada gravado")
        val base = recBase
        val m = base?.copy(steps = rec.toList())
            ?: Macro(UUID.randomUUID().toString(), "Gravação " + SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date()), 1, rec.toList())
        val err = m.validate()
        if (err != null) return toast("Gravação inválida: $err")
        runCatching { store.save(m) }
            .onSuccess { store.selected = m.id; askEditor(m) }
            .onFailure { toast("Falha ao salvar: ${it.message}") }
    }

    /** Como no base: ao encerrar, pergunta se quer ir direto para o editor. */
    private fun askEditor(m: Macro) {
        val d = android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert))
            .setTitle("Gravação de macro encerrada.")
            .setMessage("Ir para o Editor de Macro?\n(${m.steps.size} itens em “${m.name}”)")
            .setPositiveButton("OK") { _, _ -> startActivity(Intent(this, EditorActivity::class.java).putExtra("id", m.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .setNegativeButton("Cancelar", null).create()
        d.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        d.show()
    }

}
