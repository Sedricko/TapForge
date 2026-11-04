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
import android.graphics.Typeface
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
        engine.onStatus = { t, ms -> instant(t, ms) }
        engine.onTick = { t -> lastTitle = t; ui.post { tvTitle?.text = t } }
        engine.onProgress = { lap, loops, step, total, name ->
            lastInfo = name.ifBlank { "$step/$total" }; lastCycle = "$lap"; lastTitle = "-"
            ui.post { tvInfo?.text = lastInfo; tvCycle?.text = lastCycle; tvTitle?.text = lastTitle; highlightStep(step - 1) }
        }
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

    private var lastWindow = ""
    private var lastPkg: String? = null
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        // Janela em primeiro plano mudou (qual app/tela está embaixo da macro); só pacote e classe, nunca o conteúdo.
        if (e?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val w = "${e.packageName}/${e.className}"
            if (w != lastWindow) { lastWindow = w; Diag.d("janela", w) }
            val pkg = e.packageName?.toString()
            if (pkg != null && pkg != packageName && !pkg.contains("systemui") && !pkg.contains("inputmethod") && !pkg.contains("honeyboard") && pkg != lastPkg) {
                lastPkg = pkg
                if (!engine.running && !recording) store.autoOpen[pkg]?.let { id -> Diag.d("início automático", "$pkg -> $id"); runMacro(id) }
            }
        }
    }
    override fun onInterrupt() { Diag.d("serviço", "onInterrupt (sistema interrompeu o feedback de acessibilidade)"); engine.stop() }

    override fun onUnbind(intent: Intent?): Boolean {
        Diag.d("serviço", "acessibilidade desconectada")
        hideList()
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
        ui.post { stopRecording(save = false); if (engine.running) engine.stop(); hideList(); guideWin?.let { runCatching { wm.removeView(it) } }; guideWin = null; openPanel(false) }
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

    /** Autoteste: gesto, captura e uma macro real de 2 passos. Chamar fora da thread principal. */
    fun selfTest(): List<SelfTest.Check> {
        val out = ArrayList<SelfTest.Check>()
        val shown = bubble?.visibility == View.VISIBLE
        if (shown) ui.post { bubble?.visibility = View.INVISIBLE } // o toque de teste em (1,1) não pode cair num botão do painel
        try { runSelfTest(out) } finally { if (shown) ui.post { bubble?.visibility = View.VISIBLE } }
        return out
    }

    private fun runSelfTest(out: MutableList<SelfTest.Check>) {
        val l1 = java.util.concurrent.CountDownLatch(1); var gok = false
        val path = Path().apply { moveTo(1f, 1f) }
        val sent = dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 10)).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { gok = true; l1.countDown() }
                override fun onCancelled(g: GestureDescription?) { l1.countDown() }
            }, null)
        if (sent) l1.await(4, java.util.concurrent.TimeUnit.SECONDS)
        out += SelfTest.Check("Gesto de teste", gok, if (gok) "toque em (1,1) concluído" else "não concluído")
        val l2 = java.util.concurrent.CountDownLatch(1); var cok = false; var cdetail = "sem resposta"
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(r: ScreenshotResult) { cok = true; cdetail = "${r.hardwareBuffer.width}x${r.hardwareBuffer.height}"; r.hardwareBuffer.close(); l2.countDown() }
                override fun onFailure(code: Int) { cdetail = "falhou, código $code"; l2.countDown() }
            })
            l2.await(4, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) { cdetail = e.toString() }
        out += SelfTest.Check("Captura de tela", if (cok) true else if (cdetail.contains("código 3")) null else false, cdetail)
        if (engine.running || recording) {
            out += SelfTest.Check("Macro de 2 passos", null, "ignorado: há macro rodando ou gravação")
        } else {
            val ok = engine.start(Macro("autoteste", "Autoteste", 1, listOf(Step(Type.WAIT, ms = 100), Step(Type.TAP, 1, 1, ms = 10))))
            var t = 0
            while (ok && engine.running && t++ < 50) Thread.sleep(100)
            out += SelfTest.Check("Macro de 2 passos", ok && !engine.running, if (!ok) "não iniciou" else if (engine.running) "não terminou em 5 s" else "executada")
        }
    }

    private var instantWin: View? = null
    /** Mensagem instantânea do base (overlay_instant_message): janela branca arredondada, 80% opaca, embaixo, some após [ms]. */
    fun instant(text: String, ms: Int) = ui.post {
        instantWin?.let { runCatching { wm.removeView(it) } }; instantWin = null
        val v = android.widget.LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); alpha = 0.8f
            background = rounded(Color.WHITE, 8, 0xFFBBBBBB.toInt())
            addView(ImageView(this@MacroService).apply { setImageDrawable(packageManager.getApplicationIcon(packageName)) }, android.widget.LinearLayout.LayoutParams(dp(24), dp(24)).also { it.marginEnd = dp(8) })
            addView(label(text, 14f, 0xFF111827.toInt()))
        }
        val lp = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 100 }
        runCatching { wm.addView(v, lp) }.onSuccess {
            instantWin = v
            ui.postDelayed({ if (instantWin === v) { runCatching { wm.removeView(v) }; instantWin = null } }, ms.coerceAtLeast(500).toLong())
        }
    }

    fun toast(msg: String) { ui.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }

    /** Alarme: para o que estiver rodando, espera 1 s e roda a macro agendada. Recusa se estiver gravando. */
    fun fireAlarm(id: String) {
        if (recording) return toast("Agendamento ignorado: gravação em andamento")
        if (engine.running) engine.stop()
        // Espera a macro anterior terminar de verdade (gesto em voo, alerta): tenta a cada 200 ms por até 10 s.
        var tries = 0
        val go = object : Runnable { override fun run() {
            if (!engine.running || tries++ >= 50) { store.selected = id; runMacro(id) } else ui.postDelayed(this, 200)
        } }
        ui.postDelayed(go, 500)
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
        val keep = on || (store.keepOn && panelOpen()) // como no base: ligada ao reproduzir/gravar; ociosa só com a opção
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
        Ic.FRAME to R.drawable.bp_ic_crop_free_24px, Ic.EYE to R.drawable.bp_ic_visibility_on, Ic.EYE_OFF to R.drawable.bp_ic_visibility_off, Ic.STOP to R.drawable.bp_play_stop, Ic.PLAY to R.drawable.bp_play_arrow, Ic.PAUSE to R.drawable.bp_ic_media_pause,
    )

    /** Tamanhos do base (PN1): Play/Rec/Stop/Pause/Prev/Next 42 m1, Retomar 42x35, Olho 38 m2, Gravar tela 36 m4, Expandir 40 m2. */
    private fun pbtn(ic: Ic, desc: String, w: Int = 42, h: Int = w, m: Int = 1, tint: Int = teal, click: () -> Unit) = pres(baseIcons[ic], ic, desc, w, h, m, tint, click)

    private fun pres(res: Int?, ic: Ic?, desc: String, w: Int, h: Int, m: Int, tint: Int = teal, click: () -> Unit) = ImageView(this).apply {
        if (res != null) { setImageResource(res); scaleType = ImageView.ScaleType.FIT_XY; setColorFilter(tint) }
        else { setImageDrawable(Icon(ic ?: Ic.PLAY, teal)); setPadding(dp(5), dp(5), dp(5), dp(5)) }
        contentDescription = desc
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(w), dp(h)).also { it.setMargins(dp(m), dp(m), dp(m), dp(m)) }
        setOnClickListener { Diag.d("painel", "botão: $desc"); click() }
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
                setOnClickListener { recorder.cycle(); panelToFront(); fillRow1() }
            })
        } else {
            val pb = pbtn(if (playing) Ic.STOP else Ic.PLAY, "Executar ou parar a macro") { togglePlay() }
            playBtn = pb
            row1.addView(pb)
            if (playing && (store.simpleView || store.playMode)) return // visão simples / modo de reprodução: só o Parar, como no base
            if (playing) { // reproduzindo: Parar + Pausar; pausado: Retomar + Anterior + Próximo (como no base), mais o feedback do passo
                if (engine.paused) {
                    row1.addView(pbtn(Ic.PLAY, "Retomar", 42, 35) { ui.postDelayed({ engine.paused = false; fillRow1() }, 500) })
                    row1.addView(pres(R.drawable.bp_ic_media_previous, null, "Anterior", 42, 42, 1) { engine.jump = -1 })
                    row1.addView(pres(R.drawable.bp_ic_media_next, null, "Próximo", 42, 42, 1) { engine.jump = 1 })
                } else row1.addView(pbtn(Ic.PAUSE, "Pausar") { engine.paused = true; fillRow1() })
                if (engine.paused) row1.addView(pbtn(if (listWin != null) Ic.EYE else Ic.EYE_OFF, "Lista da macro", 38, 38, 2, tint = if (listWin != null) 0xFF002626.toInt() else teal) { toggleList() })
                row1.addView(android.view.View(this).apply { setBackgroundColor(0x1E000000) }, android.widget.LinearLayout.LayoutParams(dp(1), -1).also { it.setMargins(dp(4), dp(6), dp(4), dp(6)) })
                // ciclo (volta) e, ao lado, título (tempo restante ou %) e nome do item, como ll_control_cycle / ll_top do base
                tvCycle = label(lastCycle, 12f, 0xFF607D8B.toInt()).apply { gravity = Gravity.CENTER }
                row1.addView(android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumWidth = dp(42)
                    addView(ImageView(this@MacroService).apply { setImageResource(R.drawable.bp_repeat); setColorFilter(teal) }, android.widget.LinearLayout.LayoutParams(dp(20), dp(20)))
                    addView(tvCycle)
                })
                tvTitle = label(lastTitle, 14f, Color.BLACK, bold = true).apply { gravity = Gravity.CENTER; maxLines = 1; filters = arrayOf(android.text.InputFilter.LengthFilter(8)) }
                tvInfo = label(lastInfo, 12f, 0xFF607D8B.toInt()).apply { gravity = Gravity.CENTER; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = dp(80) }
                row1.addView(android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumWidth = dp(42)
                    addView(tvTitle, android.widget.LinearLayout.LayoutParams(-2, -2).also { it.setMargins(0, dp(2), 0, dp(2)) })
                    addView(tvInfo)
                }, android.widget.LinearLayout.LayoutParams(-2, -2).also { it.setMargins(dp(1), 0, dp(1), 0) })
                return
            }
            // "Modo de Reprodução": só o ▶. "Visualização Simples": sem enquadrar e sem a segunda linha.
            if (!store.playMode) row1.addView(pbtn(Ic.REC, "Gravar") { startRecording() })
            if (!store.playMode && !store.simpleView) row1.addView(pbtn(if (listWin != null) Ic.EYE else Ic.EYE_OFF, "Lista da macro", 38, 38, 2, tint = if (listWin != null) 0xFF002626.toInt() else teal) { toggleList() })
            if (!store.playMode && !store.simpleView) row1.addView(pbtn(Ic.FRAME, if (ScreenRecordService.running) "Parar a gravação de tela" else "Gravar a tela", 36, 36, 4, tint = if (ScreenRecordService.running) 0xFF002626.toInt() else teal) { toggleVideo() })
            if (!store.playMode && !store.simpleView) row1.addView(pres(if (expanded) R.drawable.bp_expand_less_24 else R.drawable.bp_expand_more_24, null, if (expanded) "Recolher" else "Mais opções", 40, 40, 2) { expanded = !expanded; row2.visibility = if (expanded) View.VISIBLE else View.GONE; fillRow1() })
        }
    }

    private fun toggleVideo() {
        if (ScreenRecordService.running) { ScreenRecordService.stop(this); ui.postDelayed({ fillRow1() }, 600); return }
        startActivity(Intent(this, ScreenRecordPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun togglePlay() {
        if (engine.running) { engine.stop(); return }
        store.selected?.let { runMacro(it) } ?: toast("Crie ou selecione uma macro no app")
    }

    /** Atualiza o ▶/■ do painel; chamável de qualquer thread. */
    // --- lista flutuante da macro ("olho"): 170x240dp com o passo atual em rosa, como no base ---
    private var listWin: View? = null
    private var listCol: android.widget.LinearLayout? = null
    private var listSv: android.widget.ScrollView? = null

    private fun toggleList() {
        if (listWin != null) { hideList(); fillRow1(); return }
        val m = store.selected?.let { store.get(it) } ?: return toast("Por favor, selecione um arquivo.")
        val col = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val collapsed = HashSet<Int>() // cabeçalhos de grupo recolhidos
        fun applyCollapse() {
            for (k in m.steps.indices) {
                val hidden = m.steps.indices.any { g -> g in collapsed && m.steps[g].type == Type.GROUP && k > g && k <= g + m.steps[g].grp }
                col.getChildAt(k * 2)?.visibility = if (hidden) View.GONE else View.VISIBLE
                col.getChildAt(k * 2 + 1)?.visibility = if (hidden) View.GONE else View.VISIBLE
            }
        }
        fun chip(res: Int, t: String) = android.widget.LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@MacroService).apply { setImageResource(res); setColorFilter(0xFF9E9E9E.toInt()) }, android.widget.LinearLayout.LayoutParams(dp(14), dp(14)))
            addView(label(t, 14f, 0xFF9E9E9E.toInt()).apply { setPadding(dp(2), 0, dp(6), 0) })
        }
        m.steps.forEachIndexed { i, st ->
            val nm = "${i + 1}. ${st.name.ifBlank { st.type.name.lowercase().replaceFirstChar { it.uppercase() } }}"
            val search = st.type in Step.SEARCHES || st.useImg
            col.addView(android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL; setPadding(dp(8) + if (m.steps.indices.any { g -> m.steps[g].type == Type.GROUP && i > g && i <= g + m.steps[g].grp }) dp(12) else 0, dp(6), dp(6), dp(6))
                addView(android.widget.LinearLayout(this@MacroService).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(nm, 16f, B.TEXT).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                    // contadores do base: destino de sucesso (azul) e de falha (vermelho)
                    if (search && st.goto > 0) addView(label("${st.goto}", 16f, 0xFF2121FF.toInt(), bold = true).apply { setPadding(dp(4), 0, 0, 0) })
                    if (search && st.fail > 0) addView(label("${st.fail}", 16f, 0xFFF44336.toInt(), bold = true).apply { setPadding(dp(4), 0, 0, 0) })
                })
                addView(android.widget.LinearLayout(this@MacroService).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    if (st.type == Type.GROUP) addView(chip(R.drawable.bp_repeat, if (st.rep == 0) "Infinito" else "${st.rep}"))
                    if (search && st.ms > 0) addView(chip(R.drawable.bp_outline_image_search_24, "${st.ms / 1000f}s"))
                    if (st.delay > 0) addView(chip(R.drawable.bp_ic_timer, "${st.delay / 1000f}s"))
                    if (st.skip) addView(chip(R.drawable.bp_ic_do_not_touch_24px, ""))
                })
                // Toque curto: abre o editor desse item (grupo: recolhe/expande). Toque longo: testa o item (grupo: abre o editor).
                setOnClickListener {
                    if (st.type == Type.GROUP) { if (i in collapsed) collapsed.remove(i) else collapsed.add(i); applyCollapse() }
                    else openItemFromList(m.id, i)
                }
                setOnLongClickListener {
                    if (st.type == Type.GROUP) openItemFromList(m.id, i)
                    else Thread { instant(engine.probeItem(st), 3000) }.start()
                    true
                }
            })
            col.addView(View(this).apply { setBackgroundColor(0x1F000000) }, android.widget.LinearLayout.LayoutParams(-1, 1))
        }
        // Como no base: título de 36dp verde com o nome da macro (arrasta a lista na vertical) e corpo branco de 170x240dp na borda direita.
        val lp = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = resources.displayMetrics.widthPixels - dp(171); y = store.listY.coerceIn(0, resources.displayMetrics.heightPixels - dp(100)) }
        val title = android.widget.LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xFF008577.toInt())
            addView(ImageView(this@MacroService).apply { setImageResource(R.drawable.bp_ic_move_resize_variant); setColorFilter(0xFFFAFAFA.toInt()) }, android.widget.LinearLayout.LayoutParams(dp(24), dp(24)).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
            addView(label(m.name, 18f, 0xFFFAFAFA.toInt(), bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            var oy = 0f; var sy = 0
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { oy = e.rawY; sy = lp.y }
                    MotionEvent.ACTION_UP -> store.listY = lp.y
                    MotionEvent.ACTION_MOVE -> { lp.y = (sy + (e.rawY - oy).toInt()).coerceIn(0, resources.displayMetrics.heightPixels - dp(100)); listWin?.let { v -> runCatching { wm.updateViewLayout(v, lp) } } }
                }
                true
            }
        }
        val sv = android.widget.ScrollView(this).apply { setBackgroundColor(Color.WHITE); addView(col) }
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; alpha = if (playing) 0.5f else 1f
            addView(title, android.widget.LinearLayout.LayoutParams(dp(170), dp(36)))
            addView(sv, android.widget.LinearLayout.LayoutParams(dp(170), dp(240)))
        }
        runCatching { wm.addView(root, lp) }.onSuccess { listWin = root; listCol = col; listSv = sv }
        fillRow1()
    }

    /** Toque na linha da lista flutuante: abre o editor da macro já no item escolhido. */
    private fun openItemFromList(macroId: String, index: Int) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra("id", macroId).putExtra("open", index).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun hideList() { listWin?.let { runCatching { wm.removeView(it) } }; listWin = null; listCol = null; listSv = null }

    private fun highlightStep(i: Int) {
        val col = listCol ?: return
        for (k in 0 until col.childCount / 2) (((col.getChildAt(k * 2) as? android.widget.LinearLayout)?.getChildAt(0) as? android.widget.LinearLayout)?.getChildAt(0) as? android.widget.TextView)?.apply {
            setTextColor(if (k == i) 0xFFD81B60.toInt() else B.TEXT); typeface = if (k == i) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        col.getChildAt(i * 2)?.let { v -> listSv?.post { listSv?.smoothScrollTo(0, (v.top - dp(60)).coerceAtLeast(0)) } }
    }

    private var tvInfo: android.widget.TextView? = null
    private var tvTitle: android.widget.TextView? = null
    private var tvCycle: android.widget.TextView? = null
    private var lastInfo = ""
    private var lastTitle = "-"
    private var lastCycle = "1"
    private var playing = false // estado mostrado no painel (engine.running ainda é true dentro do callback de fim)
    private fun setPlayIcon(running: Boolean) = ui.post { playing = running; listWin?.alpha = if (running) 0.5f else 1f; if (running) { expanded = false; if (::row2.isInitialized) row2.visibility = View.GONE }; if (::row1.isInitialized && !recording) fillRow1() else playBtn?.setImageResource(if (running) R.drawable.ic_stop else R.drawable.bp_play_arrow) }

    /** A camada do Gesto cobre a tela inteira e é criada depois do painel: recria o painel por cima, senão ele fica sem toque e o modo "não desativa". */
    private fun panelToFront() {
        val v = bubble ?: return
        runCatching { wm.removeView(v); wm.addView(v, bubbleLp) }.onFailure { Diag.e("painel", "não foi possível trazer o painel para frente", it) }
    }

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
            addView(pbtn(Ic.IMAGE, "Capturar imagem para a macro") { captureImage() })
            addView(pbtn(Ic.ALARM, "Agendamento", 38, 38, 2) { startActivity(Intent(this@MacroService, AlarmListActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            addView(pbtn(Ic.EDIT, "Abrir o editor da macro", 38, 38, 2) {
                startActivity(Intent(this@MacroService, EditorActivity::class.java).putExtra("id", store.selected ?: "new").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            addView(pbtn(Ic.GEAR, "Configurações", 38, 38, 2) { startActivity(Intent(this@MacroService, PanelSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            addView(pbtn(Ic.INFO, "Registro ao vivo") { toggleLog() })
            addView(pbtn(Ic.CANCEL, "Fechar o painel", 38, 38, 2) { hideControls() })
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

    /** Guarda a última captura para servir de fundo aos seletores de posição/área/desvio. */
    private fun rememberShot(b: Bitmap) { runCatching { java.io.File(cacheDir, "last_shot.jpg").outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 70, it) } } }

    private fun pick(macroId: String, shot: Bitmap) {
        rememberShot(shot)
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
        if (store.recGuide) { showGuide(); return }
        beginRecording()
    }

    private var guideWin: View? = null

    /** "Guia para gravar locais de toque.": janela de overlay no centro (não uma Activity, que ficava por cima de outras telas). */
    private fun showGuide() {
        if (guideWin != null) return
        fun row(res: Int, text: String) = android.widget.LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@MacroService).apply { setImageResource(res); setColorFilter(0xFF00574B.toInt()) }, android.widget.LinearLayout.LayoutParams(dp(28), dp(28)).also { it.marginEnd = dp(8) })
            addView(label(text, 15f, Color.BLACK))
            layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(8) }
        }
        val again = android.widget.CheckBox(this).apply { text = "Não mostrar novamente"; setTextColor(0xFF00574B.toInt()) }
        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; background = rounded(Color.WHITE, 8, 0xFFE0E0E0.toInt()); setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(label("Guia para gravar locais de toque.", 18f, Color.BLACK, bold = true).apply { setPadding(0, 0, 0, dp(12)) })
            addView(row(R.drawable.bp_ic_target, "Mova o ícone para o local que deseja tocar."))
            addView(row(R.drawable.bp_playlist_add, "Guarda a localização selecionada."))
            addView(row(R.drawable.bp_ic_tap, "Muda o método de toque."))
            addView(again)
            addView(label("OK", 15f, 0xFFD81B60.toInt(), bold = true).apply {
                gravity = Gravity.CENTER; setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener {
                    if (again.isChecked) store.recGuide = false
                    guideWin?.let { runCatching { wm.removeView(it) } }; guideWin = null
                    beginRecordingNow()
                }
            }, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.END })
        }
        val lp = WindowManager.LayoutParams(minOf(dp(320), resources.displayMetrics.widthPixels - dp(32)), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.CENTER }
        runCatching { wm.addView(card, lp) }.onSuccess { guideWin = card }
    }

    /** Chamado pelo guia (OK) ou direto quando ele está desligado. */
    fun beginRecording() { ui.post { beginRecordingNow() } }

    private fun beginRecordingNow() {
        Diag.d("gravação", "início (marcadores)")
        if (engine.running) return toast("Pare a macro antes de gravar")
        if (recording) return
        val base = store.selected?.let { store.get(it) }
        recBase = base; recBaseCount = base?.steps?.size ?: 0
        rec.clear(); base?.let { rec.addAll(it.steps) }
        recLast = SystemClock.elapsedRealtime()
        recording = true
        recorder.show(Recorder.Mode.TAP)
        panelToFront()
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
        bmp?.let { rememberShot(it) }
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
                        st = Step(Type.IMAGE, ms = 5000, text = tid, thr = 100, cycle = 1000, tap = false, delay = 0) // como no base: sem atraso, limiar 100, ciclo de 1 s
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
        val path = Path().apply {
            if (st.type == Type.GESTURE) { val g = gesturePoints(st.text); moveTo(g[0].first.toFloat(), g[0].second.toFloat()); for (k in 1 until g.size) lineTo(g[k].first.toFloat(), g[k].second.toFloat()) }
            else { moveTo(st.x1.toFloat(), st.y1.toFloat()); if (st.type == Type.SWIPE) lineTo(st.x2.toFloat(), st.y2.toFloat()) }
        }
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
        // Se a macro foi editada durante a gravação, não sobrescreve: acrescenta só o que foi gravado agora.
        val fresh = base?.let { store.get(it.id) } ?: base
        val m = fresh?.copy(steps = if (fresh === base) rec.toList() else fresh.steps + rec.drop(recBaseCount))
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
