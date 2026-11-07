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
    @Volatile private var recording = false
    private val rec = ArrayList<Step>()
    private lateinit var recorder: Recorder
    private var lock: BroadcastReceiver? = null

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        store = Store(this)
        // Ao terminar (qualquer motivo) a tela volta a poder apagar.
        logPanel = LogPanel(this, wm) { engine.stop() }
        engine = Engine(this, { msg ->
            if (!testing) toast(if (msg.contains("macro vazia")) "Nenhuma macro." else "Macro encerrada."); ui.post { awake(false); if (!testing) { bubble?.visibility = View.VISIBLE; if (listWasOpen) { listWasOpen = false; showList() } } }; logPanel.setThrough(false); setPlayIcon(false)
            if (!testing) {
                // Como no base: erro abre um diálogo só com a mensagem; com "Depurar" o fim normal também (Macro encerrada. / Parar).
                val txt = if (msg.startsWith("Erro")) msg.substringAfter(": ", msg).trim().ifEmpty { "Erro" } else if (store.debugOverlay && !msg.contains("macro vazia")) (if (msg == "Macro parada") "Parar" else "Macro encerrada.") else null
                if (txt != null) ui.post { overlayCard("", label(txt, 16f, Color.BLACK), listOf("OK" to {})) }
            }
            if (msg == "Macro concluída" && store.endAlarm && !testing) playEndAlarm() // só no fim natural, não em parada manual nem em erro
        }, ::toast, logPanel::captureHide)
        engine.onStatus = { t, ms -> instant(t, ms) }
        engine.onTick = { t -> lastTitle = t; ui.post { tvTitle?.text = t } }
        engine.onProgress = { lap, loops, step, total, name ->
            lastInfo = name.ifBlank { "$step/$total" }; lastCycle = "$lap"; lastTitle = "-"; lastStepIdx = step - 1
            if (!testing) ui.post {
                val cur = engine.currentId
                if (listWin != null && cur != null && (cur != listMacroId || (listCol?.childCount ?: 0) / 2 != store.get(cur)?.steps?.size)) { hideList(); toggleList() }
                tvInfo?.text = lastInfo; tvCycle?.text = lastCycle; tvTitle?.text = lastTitle; highlightStep(step - 1) }
        }
        instance = this
        Alarms.rearm(this)
        Diag.d("serviço", "acessibilidade conectada (gestos/captura/janelas conforme accessibility_config)")
        showBubble()
        lock = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { Diag.d("sistema", "tela apagou; parar ao bloquear=${store.stopOnLock}"); if (store.stopOnLock) engine.stop() }
        }
        pickReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { i.getIntArrayExtra("v")?.let { v -> val cb = pendingPick; pendingPick = null; ui.post { cb?.invoke(v) } } }
        }.also { val f = IntentFilter("app.macro17.PICKED"); if (Build.VERSION.SDK_INT >= 33) registerReceiver(it, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(it, f) }
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
                store.autoOpen[pkg]?.let { id -> ui.postDelayed({ if (lastPkg == pkg && !engine.running && !recording) { Diag.d("início automático", "$pkg -> $id"); runMacro(id) } }, 1000) } // debounce de 1 s, como no base
            }
        }
    }
    override fun onInterrupt() { Diag.d("serviço", "onInterrupt (sistema interrompeu o feedback de acessibilidade)"); engine.stop() }

    override fun onUnbind(intent: Intent?): Boolean {
        Diag.d("serviço", "acessibilidade desconectada")
        hideList()
        itemOverlay.dismiss(); pickReceiver?.let { runCatching { unregisterReceiver(it) } }; pickReceiver = null
        instantWin?.let { runCatching { wm.removeView(it) } }; instantWin = null
        guideWin?.let { runCatching { wm.removeView(it) } }; guideWin = null
        if (::logPanel.isInitialized) logPanel.hide()
        if (::engine.isInitialized) engine.stop()
        stopRecording(save = false)
        lock?.let { runCatching { unregisterReceiver(it) } }
        lock = null
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        instance = null
        return super.onUnbind(intent)
    }

    fun isRunning() = engine.running

    /** Miniatura do modelo de imagem de um item (para a janela de edição). */
    fun thumb(id: String, px: Int): Bitmap? = store.templateThumb(id, px)

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

    @Volatile private var testing = false // autoteste em curso: sem toast, alarme de fim nem painel reaparecendo

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
            testing = true
            val ok = engine.start(Macro("autoteste", "Autoteste", 1, listOf(Step(Type.WAIT, ms = 100), Step(Type.TAP, 1, 1, ms = 10))))
            var t = 0
            while (ok && engine.running && t++ < 50) Thread.sleep(100)
            testing = false
            out += SelfTest.Check("Macro de 2 passos", ok && !engine.running, if (!ok) "não iniciou" else if (engine.running) "não terminou em 5 s" else "executada")
        }
    }

    private var instantWin: View? = null
    /** Mensagem instantânea do base (overlay_instant_message): janela branca arredondada, 80% opaca, embaixo, some após [ms]. */
    fun instant(text: String, ms: Int) = ui.post {
        instantWin?.let { runCatching { wm.removeView(it) } }; instantWin = null
        if (ms == 900 && text.length == 1 && text[0] in '1'..'3') { showCountdown(text); return@post } // contagem do vídeo (ScreenRecordService)
        val v = android.widget.LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); alpha = 0.8f
            background = rounded(Color.WHITE, 8, 0xFFBBBBBB.toInt())
            addView(ImageView(this@MacroService).apply { setImageDrawable(packageManager.getApplicationIcon(packageName)) }, android.widget.LinearLayout.LayoutParams(dp(24), dp(24)).also { it.marginEnd = dp(8) })
            addView(label(text, 14f, 0xFF111827.toInt()), android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        }
        val lp = WindowManager.LayoutParams(-1, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.BOTTOM; y = 100 } // barra de largura total (raiz match_parent do base); segue sem receber toque para não cobrir a macro
        runCatching { wm.addView(v, lp) }.onSuccess {
            instantWin = v
            ui.postDelayed({ if (instantWin === v) { runCatching { wm.removeView(v) }; instantWin = null } }, ms.coerceAtLeast(500).toLong())
        }
    }

    /** Contagem do vídeo do base: dígito vermelho de 80sp com sombra, no centro, sem toque. O vídeo já está "running" aqui, então o ícone do painel troca. */
    private fun showCountdown(n: String) {
        if (::row1.isInitialized && !recording) fillRow1()
        val v = label(n, 80f, Color.RED).apply { setShadowLayer(5f, 0f, 0f, Color.BLACK); gravity = Gravity.CENTER }
        val lp = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.CENTER }
        runCatching { wm.addView(v, lp) }.onSuccess {
            instantWin = v
            // animação do base a cada dígito: escala 2->1, alpha 0.3->1, 400 ms, overshoot
            v.scaleX = 2f; v.scaleY = 2f; v.alpha = 0.3f
            v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(400).setInterpolator(android.view.animation.OvershootInterpolator()).start()
            ui.postDelayed({ if (instantWin === v) { runCatching { wm.removeView(v) }; instantWin = null } }, 900)
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

    /** A cada 30 s de execução: heap e memória livre no monitor; heap acima de 80% do limite vira ERRO (vazamento ou macro grande demais). */
    private val heartbeat = object : Runnable {
        override fun run() {
            if (!engine.running) return
            val rt = Runtime.getRuntime(); val used = (rt.totalMemory() - rt.freeMemory()) shr 20; val max = rt.maxMemory() shr 20
            val mi = android.app.ActivityManager.MemoryInfo().also { getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }
            if (used * 100 / maxOf(1, max) > 80) Diag.e("memória", "heap em ${used} MB de $max MB (acima de 80%)") else Diag.d("memória", "execução: heap $used MB de $max MB · sistema livre ${mi.availMem shr 20} MB")
            ui.postDelayed(this, 30_000)
        }
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
                setPlayIcon(true) // antes do start: uma macro que termina na hora posta o ▶ depois deste ■
                ui.postDelayed(heartbeat, 30_000)
                if (engine.start(m)) {
                    logPanel.setThrough(true) // o base não mostra aviso ao iniciar
                    // Ocultar: o painel some durante a reprodução (a tecla de volume para a macro, ver onKeyEvent). Depurar: abre o registro.
                    ui.post { bubble?.visibility = View.VISIBLE; openPanel(true); if (store.hidePopup) { bubble?.visibility = View.INVISIBLE; listWasOpen = listWin != null; hideList() }; if (store.debugOverlay && !logPanel.shown) logPanel.show() }
                } else { setPlayIcon(false); ui.post { awake(false) } }
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
            awake(playing || recording || store.scheduleOn)
            if (::row1.isInitialized && !recording) { if (store.playMode || store.simpleView) { expanded = false; row2.visibility = View.GONE }; fillRow1() }
        }
    }

    // A janela da bolha é a única que o serviço mantém: o flag mantém a tela ligada enquanto ela existe.
    private fun awake(on: Boolean) {
        val v = bubble ?: return
        val lp = v.layoutParams as WindowManager.LayoutParams
        val keep = on || (store.keepOn && panelOpen()) // como no base: ligada ao reproduzir/gravar; ociosa só com a opção
        lp.flags = if (keep) lp.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        runCatching { wm.updateViewLayout(v, lp) }
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

    /** Tamanhos do base (PN1): Play/Rec/Stop/Pause/Prev/Next 42 m0, Retomar 42x35, Olho 38 m2, Gravar tela 36 m4, Expandir 40 m2. */
    private fun pbtn(ic: Ic, desc: String, w: Int = 42, h: Int = w, m: Int = 0, tint: Int = teal, click: () -> Unit) = pres(baseIcons[ic], ic, desc, w, h, m, tint, click)

    private fun pres(res: Int?, ic: Ic?, desc: String, w: Int, h: Int, m: Int, tint: Int = teal, click: () -> Unit) = ImageView(this).apply {
        if (res != null) { setImageResource(res); scaleType = ImageView.ScaleType.FIT_XY; setColorFilter(tint) }
        else { setImageDrawable(Icon(ic ?: Ic.PLAY, teal)); setPadding(dp(5), dp(5), dp(5), dp(5)) }
        contentDescription = desc
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(w), dp(h)).also { it.setMargins(dp(m), dp(m), dp(m), dp(m)) }
        setOnClickListener { Diag.d("painel", "botão: $desc"); click() }
        dragger(this) // como no base, qualquer botão arrasta o painel
    }

    private fun fillRow1() {
        row1.removeAllViews()
        val sched = store.scheduleOn && !recording && !playing
        if (::row2.isInitialized) { // linha 2: Agendar (cor segue o estado) + demais botões; agendado esconde os demais
            row2.removeViewAt(0); row2.addView(pbtn(Ic.ALARM, "Agendar", 38, 38, 2, tint = if (store.scheduleOn) 0xFF002626.toInt() else teal) { toggleSchedule() }, 0)
            for (i in 1 until row2.childCount) row2.getChildAt(i).visibility = if (sched) View.GONE else View.VISIBLE
        }
        if (sched) { // estado "agendado" do base: linha 1 só com o próximo horário; linha 2 aberta só com o Agendar
            row1.addView(label(scheduleText().substringAfter(" • ", ""), 12f, 0xFF607D8B.toInt()).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = dp(80); minWidth = dp(42); minHeight = dp(54); gravity = Gravity.CENTER; setPadding(dp(4), 0, dp(8), 0) }) // base: só a data, centralizada
            expanded = true; if (::row2.isInitialized) row2.visibility = View.VISIBLE
            return
        }
        if (recording) {
            // Gravando (base): [Parar, Adicionar 38 m2, Alternar alvo 38 m2 só com o ícone do tipo, divisor, rótulo do tipo]. Parar age ao tocar (DOWN) e não espera o Adicionar.
            row1.addView(pbtn(Ic.STOP, "Encerrar a gravação") { }.also { b ->
                b.setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_DOWN && !adding) stopRecording(save = true); true }
            })
            row1.addView(pbtn(Ic.ADDLIST, "Adicionar o item na posição dos marcadores", 38, 38, 2) { addItem() })
            row1.addView(pres(recorder.mode.res, null, "Alternar alvo: ${recorder.mode.label}", 38, 38, 2) { recorder.cycle(); panelToFront(); fillRow1() })
            row1.addView(android.view.View(this).apply { setBackgroundColor(0x1E000000) }, android.widget.LinearLayout.LayoutParams(dp(1), -1).also { it.setMargins(0, dp(6), 0, dp(6)) })
            row1.addView(label(recorder.mode.label, 14f, Color.BLACK, bold = true).apply { gravity = Gravity.CENTER; minWidth = dp(42); setPadding(dp(4), 0, dp(8), 0) })
        } else {
            val pb = pbtn(if (playing) Ic.STOP else Ic.PLAY, "Executar ou parar a macro") { togglePlay() }
            playBtn = pb
            if (!(playing && engine.paused)) row1.addView(pb) // pausado: sem o Parar (só Retomar, olho, Anterior e Próximo), como no base
            if (playing && !engine.paused) pb.setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_DOWN) togglePlay(); true } // o Parar age no DOWN, como no base
            if (playing) { // reproduzindo: Parar + Pausar; pausado: Retomar + Anterior + Próximo (como no base), mais o feedback do passo
                if (engine.paused) {
                    row1.addView(pbtn(Ic.PLAY, "Retomar", 42, 35) { ui.postDelayed({ engine.paused = false; fillRow1() }, 500) })
                    row1.addView(pbtn(if (listWin != null) Ic.EYE else Ic.EYE_OFF, "Lista da macro", 38, 38, 2, tint = if (listWin != null) 0xFF002626.toInt() else teal) { toggleList() })
                    row1.addView(pres(R.drawable.bp_ic_media_previous, null, "Anterior", 42, 42, 0) { engine.jump = -1 })
                    row1.addView(pres(R.drawable.bp_ic_media_next, null, "Próximo", 42, 42, 0) { engine.jump = 1 })
                } else if (!store.simpleView) row1.addView(pbtn(Ic.PAUSE, "Pausar") { engine.paused = true; fillRow1() }) // visão simples: só o Parar
                row1.addView(android.view.View(this).apply { setBackgroundColor(0x1E000000) }, android.widget.LinearLayout.LayoutParams(dp(1), -1).also { it.setMargins(0, dp(6), 0, dp(6)) })
                // ciclo (volta) e, ao lado, título (tempo restante ou %) e nome do item, como ll_control_cycle / ll_top do base
                tvCycle = label(lastCycle, 12f, 0xFF607D8B.toInt()).apply { gravity = Gravity.CENTER }
                if (!store.simpleView && !store.playMode) row1.addView(android.widget.LinearLayout(this).apply { // visão simples e modo de reprodução: sem o ciclo
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
            if (!store.playMode && !store.simpleView) row1.addView(pres(if (ScreenRecordService.running) R.drawable.bp_ic_center_focus_strong_24px else R.drawable.bp_ic_crop_free_24px, null, if (ScreenRecordService.running) "Parar a gravação de tela" else "Gravar a tela", 36, 36, 4, if (ScreenRecordService.running) 0xFF002626.toInt() else teal) { toggleVideo() })
            if (!store.playMode && !store.simpleView) row1.addView(pres(if (expanded) R.drawable.bp_expand_less_24 else R.drawable.bp_expand_more_24, null, if (expanded) "Recolher" else "Mais opções", 40, 40, 2) { expanded = !expanded; row2.visibility = if (expanded) View.VISIBLE else View.GONE; fillRow1() })
        }
    }

    private fun toggleVideo() {
        if (ScreenRecordService.running) { ScreenRecordService.stop(this); ui.postDelayed({ fillRow1() }, 600); return }
        openScreen(Intent(this, ScreenRecordPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
    private var listMacroId: String? = null
    private var lastStepIdx = -1
    private var listWasOpen = false // "Ocultar janela pop-up" fecha a lista durante a execução e a reabre no fim, se estava aberta
    private var listX: Int? = null // posição X da lista nesta sessão (o Store não guarda o X)

    // Nomes pt-BR e vetores por tipo: os mesmos tabelas do EditorActivity (titles/typeRes), para a lista flutuante.
    private val listTitles = mapOf(
        Type.TAP to "Tap", Type.SWIPE to "Swipe", Type.PINCH_IN to "Pinça para dentro", Type.PINCH_OUT to "Pinça para fora",
        Type.WAIT to "Esperar", Type.KEY to "Evento de Teclado", Type.STATUS to "Alertas de status", Type.GOTO to "GOTO",
        Type.COUNTER to "Contador de Execuções", Type.RESET to "Redefinir Contador de Execuções", Type.CALL to "Arquivo de Macro", Type.IMAGE to "Image",
        Type.NODE to "Achar por texto", Type.TEXT to "Reconhecimento de Texto", Type.NUMBER to "Ler número", Type.STOP to "Parar Macro",
        Type.CHANGE to "Detecção de Mudança de Imagem", Type.GESTURE to "Gesture", Type.GROUP to "Grupo",
    )
    private val listTypeRes = mapOf(
        Type.TAP to R.drawable.bp_touch_app, Type.SWIPE to R.drawable.bp_touch_app, Type.PINCH_IN to R.drawable.bp_touch_app, Type.PINCH_OUT to R.drawable.bp_touch_app,
        Type.IMAGE to R.drawable.bp_touch_app, Type.NODE to R.drawable.bp_format_text, Type.NUMBER to R.drawable.bp_format_text, Type.TEXT to R.drawable.bp_format_text,
        Type.WAIT to R.drawable.bp_ic_timer, Type.KEY to R.drawable.bp_outline_keyboard_24, Type.STATUS to R.drawable.bp_outline_notifications_active,
        Type.GOTO to R.drawable.bp_action_goto, Type.COUNTER to R.drawable.bp_action_repeat_count, Type.RESET to R.drawable.bp_action_repeat_counter_zero,
        Type.CALL to R.drawable.bp_file_replace_outline, Type.STOP to R.drawable.bp_stop_macro, Type.CHANGE to R.drawable.bp_image_compare,
        Type.GESTURE to R.drawable.bp_ic_gesture, Type.GROUP to R.drawable.bp_ic_loop,
    )

    private fun listSecs(ms: Int): String = if (ms >= 60000) "${ms / 60000}m ${((ms % 60000) / 1000f)}s" else "${ms / 1000f}s"
    private fun listLimit(ms: Int): String = if (ms == 0) "Infinito" else listSecs(ms)

    /** Informação curta do item (tv_string_info do base), igual à do editor. */
    private fun listInfo(s: Step): String = when (s.type) {
        Type.KEY -> mapOf(1 to "Voltar", 2 to "Início", 3 to "Recentes", 4 to "Notificações", KEY_SCREENSHOT to "Captura de tela")[s.x1] ?: ""
        Type.STATUS -> s.text
        Type.CALL -> store.get(s.text)?.name ?: "Nenhum arquivo alocado."
        Type.COUNTER -> s.cases.joinToString(" ") { "${it.n}:${it.to}" }
        Type.RESET -> "Contador ${s.counter}"
        Type.TEXT, Type.NODE -> s.text.replace(",", ", ")
        Type.NUMBER -> "${s.lo}"
        Type.GROUP -> if (s.rep == 0) "Infinito" else "${s.rep} vezes"
        Type.GESTURE -> "${gesturePoints(s.text).size} pontos"
        else -> ""
    }

    /** Teste do item pela lista: o base mostra só "NN%" (ou "-" se não houve semelhança); mensagens de erro ficam como estão. */
    private fun probeShort(r: String): String {
        val n = Regex("(\\d+)%").find(r)?.groupValues?.get(1)?.toIntOrNull() ?: return r
        return if (n <= 0) "-" else "$n%"
    }

    private fun toggleList() {
        if (listWin != null) { hideList(); fillRow1(); return }
        showList()
    }

    /** Lista da gravação em andamento (itens já adicionados) ou da macro em execução/selecionada. */
    private fun listSource(): Macro? =
        if (recording) Macro(recBase?.id ?: "rec", recBase?.name ?: "Gravação", 1, rec.toList())
        else (engine.currentId ?: store.selected)?.let { store.get(it) }

    private fun showList() {
        if (listWin != null) return
        val m = listSource() ?: return toast("Por favor, selecione um arquivo.")
        listMacroId = m.id
        val col = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val collapsed = HashSet<Int>() // cabeçalhos de grupo recolhidos
        fun applyCollapse() {
            for (k in m.steps.indices) {
                val hidden = m.steps.indices.any { g -> g in collapsed && m.steps[g].type == Type.GROUP && k > g && k <= g + m.steps[g].grp }
                col.getChildAt(k * 2)?.visibility = if (hidden) View.GONE else View.VISIBLE
                col.getChildAt(k * 2 + 1)?.visibility = if (hidden) View.GONE else View.VISIBLE
            }
        }
        val grey = 0xFF9E9E9E.toInt()
        fun chip(res: Int, t: String) = android.widget.LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@MacroService).apply { setImageResource(res); setColorFilter(grey) }, android.widget.LinearLayout.LayoutParams(dp(14), dp(14)))
            addView(label(t, 14f, grey).apply { setPadding(dp(2), 0, dp(6), 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        }
        val thumbCache = HashMap<String, Bitmap?>()
        m.steps.forEachIndexed { i, st ->
            val par = m.steps.parentOf(i)
            val child = par >= 0
            val num = if (child) "${par + 1}-${i - par}" else "${i + 1}" // filhos: "pai-filho", como no base
            val nm = "$num. ${st.name.ifBlank { if (st.type == Type.TAP && st.ms >= 500) "Press" else (listTitles[st.type] ?: st.type.name) }}"
            val search = st.type in Step.SEARCHES || st.useImg
            val thumbId = if (st.type == Type.IMAGE) st.text else st.img
            val thumbBmp = if (thumbId.isNotEmpty()) thumbCache.getOrPut(thumbId) { runCatching { store.templateThumb(thumbId, 56) }.getOrNull() } else null
            val typeIc = if (st.type == Type.GROUP && st.mono) R.drawable.bp_ic_loop_image else (listTypeRes[st.type] ?: 0)
            val showGoto = (search || st.type == Type.GOTO || st.type == Type.COUNTER) && st.goto > 0
            val showFail = search && st.fail > 0
            val infoText = listInfo(st)
            col.addView(android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(2), dp(6))
                if (child) setBackgroundColor(0xFFD6D7D7.toInt()) // filho de grupo, como no base (mesma margem, sem recuo extra)
                addView(android.widget.LinearLayout(this@MacroService).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    addView(android.widget.LinearLayout(this@MacroService).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(label(nm, 16f, Color.BLACK).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                        // contadores do base: destino de sucesso (azul) e de falha (vermelho)
                        if (showGoto) addView(label("${st.goto}", 16f, if (child) 0xFF2196F3.toInt() else 0xFF2121FF.toInt(), bold = true).apply { setPadding(dp(4), 0, 0, 0) })
                        if (showFail) addView(label("${st.fail}", 16f, 0xFFF44336.toInt(), bold = true).apply { setPadding(dp(4), 0, 0, 0) })
                    }, android.widget.LinearLayout.LayoutParams(-1, -2).also { it.marginEnd = dp(4) })
                    addView(android.widget.LinearLayout(this@MacroService).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        // miniatura (14dp) quando o item tem imagem; senão o vetor do tipo, sem tint (cores próprias)
                        if (thumbBmp != null) addView(ImageView(this@MacroService).apply { setImageBitmap(thumbBmp); scaleType = ImageView.ScaleType.FIT_CENTER }, android.widget.LinearLayout.LayoutParams(dp(14), dp(14)).also { it.marginEnd = dp(6) })
                        else if (typeIc != 0) addView(ImageView(this@MacroService).apply { setImageResource(typeIc) }, android.widget.LinearLayout.LayoutParams(dp(14), dp(14)).also { it.marginEnd = dp(6) })
                        if (search) addView(chip(R.drawable.bp_outline_image_search_24, listLimit(if (st.type in Step.GESTURES) st.sms else st.ms)))
                        if (st.delay > 0 || st.rnd > 0) addView(chip(R.drawable.bp_ic_timer, if (st.rnd > 0) "${listSecs(st.rnd)}~" else listSecs(st.delay)))
                        if (st.type == Type.STATUS) {
                            val alert = if (st.snd && st.vib) R.drawable.bp_volume_vibrate else if (st.snd) R.drawable.bp_volume_high else if (st.vib) R.drawable.bp_vibrate else 0
                            if (alert != 0) addView(ImageView(this@MacroService).apply { setImageResource(alert) }, android.widget.LinearLayout.LayoutParams(dp(14), dp(14)))
                            addView(label(listSecs(st.ms), 14f, grey).apply { setPadding(dp(2), 0, dp(6), 0) })
                        }
                        if (st.skip) addView(ImageView(this@MacroService).apply { setImageResource(R.drawable.bp_ic_do_not_touch_24px); setColorFilter(grey) }, android.widget.LinearLayout.LayoutParams(dp(14), dp(14)).also { it.setMargins(dp(2), 0, dp(2), 0) })
                        if (infoText.isNotEmpty()) addView(label(infoText, 14f, grey).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(2), 0, dp(6), 0) })
                    })
                }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                if (st.off) addView(ImageView(this@MacroService).apply { setImageResource(R.drawable.bp_remove_circle) }, android.widget.LinearLayout.LayoutParams(dp(18), dp(18)).also { it.setMargins(dp(3), 0, dp(3), 0) })
                // Como no base: toque curto edita o item numa janela por cima do app (só os tipos que o base permite; grupo recolhe/expande);
                // toque longo testa o item (espera 500 ms, compara a imagem com a tela e mostra "NN%"). Gravando, os dois só avisam.
                setOnClickListener {
                    Diag.d("lista", "toque no item ${i + 1} (${st.type.name}); gravando=$recording")
                    if (recording) toast("A edição não é possível durante a gravação de uma macro.") // como no base
                    else if (st.type == Type.GROUP) { if (i in collapsed) collapsed.remove(i) else collapsed.add(i); applyCollapse() }
                    else if (ItemOverlay.editable(st.type)) editFromList(m, i)
                }
                setOnLongClickListener {
                    if (recording) toast("A edição não é possível durante a gravação de uma macro.")
                    else if (st.type != Type.GROUP) Thread { Thread.sleep(500); instant(probeShort(runCatching { engine.probeItem(st) }.getOrElse { "Falha ao testar o item" }), 2000) }.start()
                    true
                }
            })
            col.addView(View(this).apply { setBackgroundColor(0x1F000000) }, android.widget.LinearLayout.LayoutParams(-1, 1))
        }
        // Como no base: título de 36dp verde com o nome da macro (arrasta a lista na vertical) e corpo branco de 170x240dp na borda direita.
        val lp = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = listX ?: (resources.displayMetrics.widthPixels - dp(170)); y = store.listY.coerceIn(0, resources.displayMetrics.heightPixels - dp(100)) }
        val title = android.widget.LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xFF008577.toInt())
            addView(ImageView(this@MacroService).apply { setImageResource(R.drawable.bp_ic_move_resize_variant); setColorFilter(0xFFFAFAFA.toInt()) }, android.widget.LinearLayout.LayoutParams(dp(24), dp(24)).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
            addView(label(m.name, 18f, 0xFFFAFAFA.toInt(), bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            var oy = 0f; var sy = 0; var ox = 0f; var sx = 0
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { oy = e.rawY; sy = lp.y; ox = e.rawX; sx = lp.x }
                    MotionEvent.ACTION_UP -> { store.listY = lp.y; listX = lp.x }
                    MotionEvent.ACTION_MOVE -> { lp.x = (sx + (e.rawX - ox).toInt()).coerceIn(0, resources.displayMetrics.widthPixels - dp(60)); lp.y = (sy + (e.rawY - oy).toInt()).coerceIn(0, resources.displayMetrics.heightPixels - dp(100)); listWin?.let { v -> runCatching { wm.updateViewLayout(v, lp) } } }
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
        runCatching { wm.addView(root, lp) }.onSuccess { listWin = root; listCol = col; listSv = sv; if (playing && lastStepIdx >= 0) highlightStep(lastStepIdx) } // abre já com o passo atual em rosa
        fillRow1()
    }

    /** Toque na linha da lista flutuante: abre o editor da macro já no item escolhido. */
    /** Abre uma tela a partir do serviço; sem "Exibir sobre outros apps" o Android pode barrar em silêncio, então avisa. */
    private fun openScreen(i: Intent) {
        if (!android.provider.Settings.canDrawOverlays(this)) toast("Permita \"Exibir sobre outros apps\" para abrir telas pelo painel")
        startActivity(i)
    }

    private val itemOverlay by lazy { ItemOverlay(this, wm) }
    @Volatile private var pendingPick: ((IntArray) -> Unit)? = null
    private var pickReceiver: BroadcastReceiver? = null

    /** Abre o seletor em tela cheia; o resultado volta por broadcast interno (uma Activity não devolve resultado a um serviço). */
    fun requestPick(mode: Int, init: IntArray, tpl: String?, swipe: Boolean, cb: (IntArray) -> Unit) {
        pendingPick = cb
        openScreen(Intent(this, PositionPickerActivity::class.java).putExtra("mode", mode).putExtra("v", init).putExtra("tpl", tpl).putExtra("swipe", swipe).putExtra("reply", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Lista de escolha em overlay (o diálogo comum não abre a partir de um serviço). */
    fun chooseOverlay(title: String, items: Array<String>, checked: Int, cb: (Int) -> Unit) {
        var close: () -> Unit = {}
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            items.forEachIndexed { w, t -> addView(label(t, 16f, if (w == checked) 0xFFD81B60.toInt() else Color.BLACK, bold = w == checked).apply { setPadding(0, dp(10), 0, dp(10)); setOnClickListener { close(); cb(w) } }) }
        }
        ui.post { close = overlayCard(title, android.widget.ScrollView(this).apply { addView(col) }, listOf("Cancelar" to {})) }
    }

    private fun editFromList(m: Macro, i: Int) {
        Diag.d("lista", "editar item ${i + 1} (${m.steps[i].type.name}) pela lista flutuante")
        runCatching { itemOverlay.show(m.steps[i], m.steps.size, onSave = { ns -> applyListEdit(m, i, ns) }, onDelete = { applyListEdit(m, i, null) }) }
            .onFailure { Diag.e("lista", "falha ao abrir a janela de edição", it); toast("Não foi possível abrir a edição: ${it.javaClass.simpleName} ${it.message.orEmpty().take(80)}") }
    }

    /** Grava a edição feita na janela: na macro salva ou, durante a gravação, na lista da gravação (itens já salvos também vão para o arquivo). */
    private fun applyListEdit(m: Macro, i: Int, ns: Step?) {
        if (recording && i >= recBaseCount) { if (i in rec.indices) { if (ns == null) rec.removeAt(i) else rec[i] = ns } }
        else {
            val steps = if (ns == null) m.steps.afterDelete(i) else m.steps.toMutableList().also { it[i] = ns }
            val nm = (store.get(m.id) ?: m).copy(steps = steps)
            nm.validate()?.let { return toast(it) }
            runCatching { store.save(nm) }.onFailure { return toast("Falha ao salvar: ${it.message}") }
            if (recording) { if (i in rec.indices) { if (ns == null) { rec.removeAt(i); recBaseCount-- } else rec[i] = ns } }
        }
        ui.post { if (listWin != null) { hideList(); showList() } }
    }

    private fun hideList() { listWin?.let { runCatching { wm.removeView(it) } }; listWin = null; listCol = null; listSv = null }

    private fun highlightStep(i: Int) {
        val col = listCol ?: return
        for (k in 0 until col.childCount / 2) ((((col.getChildAt(k * 2) as? android.widget.LinearLayout)?.getChildAt(0) as? android.widget.LinearLayout)?.getChildAt(0) as? android.widget.LinearLayout)?.getChildAt(0) as? android.widget.TextView)?.apply {
            setTextColor(if (k == i) 0xFFD81B60.toInt() else Color.BLACK) // passo atual em rosa (colorAccent), sem negrito, como no base
        }
        col.getChildAt(i * 2)?.let { v -> listSv?.post { listSv?.smoothScrollTo(0, (v.top - dp(60)).coerceAtLeast(0)) } }
    }

    private var tvInfo: android.widget.TextView? = null
    private var tvTitle: android.widget.TextView? = null
    private var tvCycle: android.widget.TextView? = null
    private var lastInfo = ""
    private var lastTitle = "-"
    private var lastCycle = "1"
    @Volatile private var playing = false // estado mostrado no painel (engine.running ainda é true dentro do callback de fim)
    private fun setPlayIcon(running: Boolean) = ui.post { playing = running; listWin?.alpha = if (running) 0.5f else 1f; if (running) { expanded = false; if (::row2.isInitialized) row2.visibility = View.GONE }; if (::row1.isInitialized && !recording) fillRow1() else playBtn?.setImageResource(if (running) R.drawable.ic_stop else R.drawable.bp_play_arrow) }

    /** A camada do Gesto cobre a tela inteira e é criada depois do painel: recria o painel por cima, senão ele fica sem toque e o modo "não desativa". */
    private fun panelToFront() {
        val v = bubble ?: return
        runCatching { wm.removeView(v) }
        // Se o addView falhar, tenta de novo uma vez: sem isso o painel sumiria e o usuário ficaria sem como parar a gravação.
        if (runCatching { wm.addView(v, bubbleLp) }.onFailure { Diag.e("painel", "não foi possível trazer o painel para frente", it) }.isFailure) runCatching { wm.addView(v, bubbleLp) }
    }

    private fun openPanel(open: Boolean) {
        if (!::panel.isInitialized) return
        panel.visibility = if (open) View.VISIBLE else View.GONE
        awake(playing || recording || store.scheduleOn) // "Manter tela ligada" vale já ao abrir o painel e só com ele aberto
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
                    if (!drag && (kotlin.math.abs(e.rawX - dx) > slop || kotlin.math.abs(e.rawY - dy) > slop)) { drag = true; view.cancelLongPress() } // arrastar não pode virar toque longo (descartaria a gravação)
                    if (drag) {
                        val dm = resources.displayMetrics; val bw = bubble?.width ?: dp(42); val bh = bubble?.height ?: dp(42)
                        bubbleLp.x = (sx + (e.rawX - dx).toInt()).coerceIn(0, maxOf(0, dm.widthPixels - bw)); bubbleLp.y = (sy + (e.rawY - dy).toInt()).coerceIn(0, maxOf(0, dm.heightPixels - bh)) // o painel não se perde fora da tela
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

        row1 = android.widget.LinearLayout(this).apply { gravity = Gravity.CENTER }
        row2 = android.widget.LinearLayout(this).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(pbtn(Ic.ALARM, "Agendar", 38, 38, 2, tint = if (store.scheduleOn) 0xFF002626.toInt() else teal) { toggleSchedule() })
            addView(pbtn(Ic.EDIT, "Abrir o editor da macro", 38, 38, 2) {
                openScreen(Intent(this@MacroService, EditorActivity::class.java).putExtra("id", store.selected ?: "new").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            addView(pbtn(Ic.GEAR, "Configurações", 38, 38, 2) { openScreen(Intent(this@MacroService, PanelSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            addView(pbtn(Ic.CANCEL, "Fechar o painel", 38, 38, 2) { hideControls() })
            addView(android.view.View(this@MacroService), android.widget.LinearLayout.LayoutParams(dp(38), dp(38)).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }) // Space do base (iconSpace)
        }
        panel = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = rounded(0xFFFFFFFF.toInt(), 8, 0xFFE0E0E0.toInt()) // fundo branco, raio 8dp, traço 1dp, sem sombra
            visibility = View.GONE
            addView(row1, android.widget.LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(1); it.marginEnd = dp(1) })
            addView(row2, android.widget.LinearLayout.LayoutParams(-1, -2).also { it.marginStart = dp(1); it.marginEnd = dp(1) })
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
    @Volatile private var capturing = false

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
        runCatching { wm.addView(root, lp) }.onFailure { Diag.e("captura", "não foi possível abrir o seletor", it); close(); return }
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
            addView(ImageView(this@MacroService).apply { setImageResource(res)}, android.widget.LinearLayout.LayoutParams(dp(24), dp(24)).also { it.marginEnd = dp(4) })
            addView(label(text, 14f, Color.BLACK))
            layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(8) }
        }
        val again = android.widget.CheckBox(this).apply { text = "Não mostrar novamente"; setTextColor(0xFF00574B.toInt()); buttonTintList = android.content.res.ColorStateList.valueOf(0xFF7F7F7F.toInt()) }
        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; background = rounded(Color.WHITE, 8, 0xFFE0E0E0.toInt()); setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(label("Guia para gravar locais de toque.", 20f, Color.BLACK, bold = true).apply { setPadding(0, 0, 0, dp(12)) })
            addView(row(R.drawable.bp_ic_target, "Mova o ícone para o local que deseja tocar."))
            addView(row(R.drawable.bp_playlist_add, "Salva o local selecionado."))
            addView(row(R.drawable.bp_ic_tap, "Altera o método de toque."))
            addView(again)
            addView(label("OK", 14f, 0xFFD81B60.toInt(), bold = true).apply {
                gravity = Gravity.CENTER; setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener {
                    if (again.isChecked) store.recGuide = false
                    guideWin?.let { runCatching { wm.removeView(it) } }; guideWin = null
                    beginRecordingNow()
                }
            }, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.END })
        }
        val lp = WindowManager.LayoutParams(minOf(dp(280), resources.displayMetrics.widthPixels - dp(32)), WindowManager.LayoutParams.WRAP_CONTENT,
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
        recording = true; ui.post { awake(true) } // gravando: a tela não apaga; a lista NÃO abre sozinha (como no base)
        recorder.show(Recorder.Mode.TAP)
        panelToFront()
        expanded = false; row2.visibility = View.GONE
        fillRow1()
        // o base não mostra nenhum aviso durante a gravação (um toast entraria na miniatura do próximo item)
    }

    /** Atraso medido, como no base: tempo desde o último "+" menos 5 ms fixos, em passos de 0,1 s. */
    private fun measuredDelay(pressedAt: Long, @Suppress("UNUSED_PARAMETER") dur: Int): Int =
        ((((pressedAt - recLast - 5).coerceAtLeast(0) + 50) / 100) * 100).toInt().coerceAtMost(MAX_MS)

    private fun addItem() {
        if (!recording || adding) return
        if (rec.size >= MAX_STEPS) return Diag.e("gravação", "limite de $MAX_STEPS passos")
        if (recorder.mode == Recorder.Mode.IMAGE && recorder.region() == null) return Diag.d("gravação", "marque a área da imagem")
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

    private fun commitItem(@Suppress("UNUSED_PARAMETER") clickedAt: Long, bmp: Bitmap?) {
        if (!recording) return // Stop durante o Add: nada de gravar nem tocar depois de salvar
        bmp?.let { rememberShot(it) }
        val pressedAt = SystemClock.elapsedRealtime() // o atraso conta a partir daqui (depois dos 500 ms e da captura), como no base
        var restoreAfter = 0L
        try {
            val mode = recorder.mode
            var st: Step? = null
            if (mode == Recorder.Mode.IMAGE) {
                val r = recorder.region()
                if (bmp == null) Diag.e("gravação", "sem captura: o item de imagem não foi gravado")
                else if (r != null) {
                    val x = r.left.coerceIn(0, bmp.width - 1); val y = r.top.coerceIn(0, bmp.height - 1)
                    val w = r.width().coerceAtMost(bmp.width - x); val h = r.height().coerceAtMost(bmp.height - y)
                    if (w < RegionPicker.MIN || h < RegionPicker.MIN) Diag.d("gravação", "área pequena demais (mínimo ${RegionPicker.MIN} px)")
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
                    restoreAfter = if (st.type == Type.GESTURE) st.ms + 100L else st.ms.toLong() // Gesto +100 ms; Toque/Pressionar/Deslizar a duração
                } else if (mode == Recorder.Mode.GESTURE) Diag.d("gravação", "desenhe o gesto antes de adicionar")
            }
            if (st != null) {
                rec += st; recLast = pressedAt
                ui.post { if (listWin != null) { hideList(); showList() } } // a lista acompanha cada item adicionado
                Diag.d("gravação", "item ${rec.size}: ${st.type} ${st.x1},${st.y1}" + (if (st.type == Type.SWIPE) ">${st.x2},${st.y2}" else "") + " ms=${st.ms} atraso=${st.delay}ms" + (if (st.img.isNotEmpty()) " miniatura" else ""))
            }
        } catch (e: Exception) { Diag.e("gravação", "falha ao gravar o item", e) }
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
        ui.post { awake(false); listWin?.alpha = 1f; itemOverlay.dismiss() } // a lista não fecha ao parar (como no base)
        runCatching { recorder.hide() }
        if (::panel.isInitialized) fillRow1()
        if (!save) return
        if (rec.size == recBaseCount) return askEditor(recBase?.id ?: "new") // o base pergunta sempre, mesmo sem itens
        val base = recBase
        // Se a macro foi editada durante a gravação, não sobrescreve: acrescenta só o que foi gravado agora.
        val fresh = base?.let { store.get(it.id) } ?: base
        val m = fresh?.copy(steps = if (fresh === base) rec.toList() else fresh.steps + rec.drop(recBaseCount))
            ?: Macro(UUID.randomUUID().toString(), "Gravação " + SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date()), 1, rec.toList())
        val err = m.validate()
        if (err != null) return toast("Gravação inválida: $err")
        runCatching { store.save(m) }
            .onSuccess { store.selected = m.id; askEditor(m.id) }
            .onFailure { toast("Falha ao salvar: ${it.message}") }
    }

    /** "<nome> • MMM dd(E) HH:mm" do próximo agendamento ativo (llSchedule do base). */
    private fun scheduleText(): String {
        val next = store.alarms().filter { it.enabled }.minByOrNull { Alarms.nextStart(it.start) } ?: return "Nenhum agendamento"
        return "${store.get(next.macroId)?.name ?: "Macro"} • " + SimpleDateFormat("MMM dd(E) HH:mm", Locale.getDefault()).format(Date(Alarms.nextStart(next.start)))
    }

    /** Diálogo do base (b1.y2.a): 280dp, branco arredondado, título 20sp e botões de texto em #D81B60. Devolve a função que o fecha. */
    private fun overlayCard(title: String, content: View, buttons: List<Pair<String, () -> Unit>>): () -> Unit {
        var win: View? = null
        val close = { win?.let { runCatching { wm.removeView(it) } }; win = null; Unit }
        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; background = rounded(Color.WHITE, 8, 0xFFE0E0E0.toInt()); setPadding(dp(24), dp(20), dp(16), dp(8))
            if (title.isNotEmpty()) addView(label(title, 20f, Color.BLACK, bold = true))
            addView(content, android.widget.LinearLayout.LayoutParams(-1, -2).also { it.topMargin = if (title.isEmpty()) 0 else dp(16) })
            addView(android.widget.LinearLayout(this@MacroService).apply {
                gravity = Gravity.END
                buttons.forEach { (t, act) -> addView(label(t, 14f, 0xFFD81B60.toInt(), bold = true).apply { setPadding(dp(12), dp(12), dp(12), dp(12)); setOnClickListener { close(); act() } }) }
            }, android.widget.LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(8) })
        }
        val lp = WindowManager.LayoutParams(dp(280), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        runCatching { wm.addView(card, lp) }.onSuccess { win = card }.onFailure { Diag.e("painel", "não foi possível abrir o diálogo", it) }
        return close
    }

    private fun askOverlay(title: String, msg: String, ok: () -> Unit) {
        ui.post { overlayCard(title, label(msg, 14f, 0xFF616161.toInt()), listOf("Cancelar" to {}, "OK" to ok)) }
    }

    /** Botão Agendar do painel (AU3): sem agenda pergunta se cria; com agenda confirma e entra no estado "agendado"; ligado, desliga e para. */
    private fun toggleSchedule() {
        if (store.scheduleOn) {
            store.scheduleOn = false; stopMacro(); ui.post { awake(false); fillRow1() }
            return
        }
        if (!Alarms.canExact(this)) {
            askOverlay("Permissão necessária", "O serviço de agendamento precisa ser executado em segundo plano quando a tela estiver desligada. Permita que o aplicativo use mais energia em segundo plano.") {
                openScreen(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        val on = store.alarms().filter { it.enabled }
        if (on.isEmpty()) askOverlay("Agendamento", "Nenhum agendamento. Criar um novo?") { openScreen(Intent(this, AlarmListActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        else askOverlay("Iniciar o agendamento?", scheduleText()) { store.scheduleOn = true; Alarms.rearm(this); ui.post { awake(true); fillRow1() } }
    }

    /** Fim da gravação de tela: diálogo Reproduzir/Fechar como no base; Reproduzir abre a pré-visualização. */
    fun askPlay(uri: android.net.Uri) {
        val name = runCatching { contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: "Movies/Macro17"
        ui.post {
            overlayCard("Gravação de tela concluída", label("Vídeo salvo na Galeria ($name).\nO que você gostaria de fazer?", 14f, 0xFF616161.toInt()),
                listOf("Fechar" to {}, "Reproduzir" to { openScreen(Intent(this, VideoPlayerActivity::class.java).setData(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }))
            if (::row1.isInitialized && !recording) fillRow1() // o ícone de gravar tela volta ao normal
        }
    }

    /** Como no base: ao encerrar (mesmo sem itens), pergunta se quer ir direto para o editor. */
    private fun askEditor(id: String) {
        ui.post {
            overlayCard("Gravação de macro encerrada.", label("Ir para o Editor de Macro?", 14f, 0xFF616161.toInt()),
                listOf("Cancelar" to {}, "OK" to { openScreen(Intent(this, EditorActivity::class.java).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }))
        }
    }

}
