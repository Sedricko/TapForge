package app.macro17

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
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
    private var recView: View? = null
    private val rec = ArrayList<Step>()
    private lateinit var recBtn: ImageView
    private var lock: BroadcastReceiver? = null

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        store = Store(this)
        // Ao terminar (qualquer motivo) a tela volta a poder apagar.
        logPanel = LogPanel(this, wm) { engine.stop() }
        engine = Engine(this, { toast(it); ui.post { awake(false) }; logPanel.setThrough(false) }, ::toast, logPanel::captureHide)
        instance = this
        Diag.d("serviço", "acessibilidade conectada (gestos/captura/janelas conforme accessibility_config)")
        showBubble()
        lock = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { Diag.d("sistema", "tela apagou; parar ao bloquear=${store.stopOnLock}"); if (store.stopOnLock) engine.stop() }
        }
        val off = IntentFilter(Intent.ACTION_SCREEN_OFF)
        // A versão com flags só existe na API 33; nas 31 e 32 o registro simples já basta para broadcast do sistema.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(lock, off, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(lock, off)
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

    /** INICIAR da Home: mostra a bolha de controle com o leque aberto (gravar, executar, parar, capturar). */
    fun showControls() {
        Diag.d("serviço", "controles abertos pela Home")
        ui.post { bubble?.visibility = View.VISIBLE; setOrb(true) }
    }

    /** Abre ou fecha o painel de registro ao vivo. Chamável de qualquer thread. */
    fun toggleLog() { ui.post { if (logPanel.shown) logPanel.hide() else logPanel.show() } }

    fun stopMacro() { Diag.d("serviço", "parar macro pedido"); engine.stop() }

    fun toast(msg: String) { ui.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }

    /** Chamável de qualquer thread (alarme, app). */
    fun runMacro(id: String) {
        val m = store.get(id)
        Diag.d("serviço", "executar macro id=$id -> ${m?.name ?: "NÃO ENCONTRADA"} (rodando=${engine.running}, gravando=${recView != null})")
        when {
            m == null -> toast("Macro não encontrada")
            recView != null -> toast("Termine a gravação primeiro")
            engine.running -> toast("Já existe uma macro em execução")
            else -> {
                // awake(true) entra na fila antes de a thread da macro poder postar o awake(false) do fim.
                ui.post { awake(true) }
                if (engine.start(m)) { toast("Executando: ${m.name}"); logPanel.setThrough(true) } else ui.post { awake(false) }
            }
        }
    }

    /** Opções que valem na hora (a opacidade); as demais são lidas a cada execução. */
    fun applyOptions() { ui.post { bubble?.alpha = store.opacity / 100f } }

    // A janela da bolha é a única que o serviço mantém: o flag mantém a tela ligada enquanto ela existe.
    private fun awake(on: Boolean) {
        val v = bubble ?: return
        val lp = v.layoutParams as WindowManager.LayoutParams
        val keep = on && store.keepOn
        lp.flags = if (keep) lp.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        wm.updateViewLayout(v, lp)
    }

    // --- bolha ---------------------------------------------------------------------------

    // Orbe que abre em leque (opção D). Fechado, a janela é só o orbe: uma janela de overlay captura todo o toque na sua área.
    private var orbOpen = false
    private lateinit var orbBtn: View
    private var fan = emptyList<View>()

    private fun <T : View> T.placed(size: Int, x: Int, y: Int): T {
        layoutParams = FrameLayout.LayoutParams(dp(size), dp(size)).also { it.leftMargin = dp(x); it.topMargin = dp(y) }
        return this
    }

    private fun setOrb(open: Boolean) {
        val f = bubble ?: return
        if (open == orbOpen) return
        orbOpen = open
        (orbBtn.layoutParams as FrameLayout.LayoutParams).topMargin = dp(if (open) 44 else 0)
        orbBtn.requestLayout()
        fan.forEach { it.visibility = if (open) View.VISIBLE else View.GONE }
        // Aberto, o leque ocupa 44dp acima do orbe; sobe a janela na mesma medida para o orbe não pular.
        val lp = f.layoutParams as WindowManager.LayoutParams
        lp.y += dp(if (open) -44 else 44)
        wm.updateViewLayout(f, lp)
    }

    private fun showBubble() {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            // TYPE_ACCESSIBILITY_OVERLAY dispensa a permissão "exibir sobre outros apps".
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 360 }

        val play = circleBtn(Ic.PLAY, C.BLUE, 38, "Executar macro selecionada") {
            store.selected?.let { runMacro(it) } ?: toast("Selecione uma macro no app")
            setOrb(false)
        }.placed(38, 57, 15)
        val stop = circleBtn(Ic.STOP, C.DARK, 38, "Parar macro") { engine.stop(); setOrb(false) }.placed(38, 77, 49)
        recBtn = circleBtn(Ic.REC, C.DARK, 38, "Gravar", C.RED) { if (recView == null) startRecording() else stopRecording(save = true) }.placed(38, 57, 83)
        val capture = circleBtn(Ic.SEARCH, C.DARK, 38, "Capturar imagem para a macro selecionada") { captureImage() }.placed(38, 5, 100)
        fan = listOf(play, stop, recBtn, capture)
        fan.forEach { it.visibility = View.GONE }
        orbBtn = circleBtn(Ic.TAP, C.BLUE, 48, "Controle de macros") { setOrb(!orbOpen) }.placed(48, 0, 0)

        // Arrastar pelo próprio orbe; toque curto abre/fecha. Consumir só o arrasto deixa o clique (e o TalkBack) funcionando.
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var drag = false
        orbBtn.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; drag = false; false }
                MotionEvent.ACTION_MOVE -> {
                    if (!drag && hypot(e.rawX - dx, e.rawY - dy) > slop) drag = true
                    if (drag) {
                        lp.x = sx + (e.rawX - dx).toInt(); lp.y = sy + (e.rawY - dy).toInt()
                        bubble?.let { wm.updateViewLayout(it, lp) }
                    }
                    drag
                }
                MotionEvent.ACTION_UP -> { if (drag) v.isPressed = false; drag }
                else -> false
            }
        }

        val frame = FrameLayout(this).apply { fan.forEach { addView(it) }; addView(orbBtn) }
        bubble = frame
        frame.alpha = store.opacity / 100f
        wm.addView(frame, lp)
    }

    // --- captura de imagem ---------------------------------------------------------------

    /** Orbe → Capturar: congela a tela, deixa recortar e acrescenta um passo IMAGE ao fim da macro selecionada. */
    private fun captureImage() {
        val m = store.selected?.let { store.get(it) } ?: return toast("Selecione uma macro no app antes de capturar")
        if (recView != null || engine.running) return toast("Pare a gravação ou a macro antes de capturar")
        setOrb(false)
        bubble?.visibility = View.INVISIBLE // o orbe não pode aparecer no quadro
        toast("Capturando em 2 s: deixe visível o que quer achar")
        ui.postDelayed({ shoot(m.id) }, 2000)
    }

    private fun shotFailed(msg: String) { Diag.e("captura", msg); toast(msg); bubble?.visibility = View.VISIBLE }

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

    // --- gravação ------------------------------------------------------------------------
    // Captura sem repassar o toque ao app de baixo (injeção de volta reentraria no overlay).

    private fun startRecording() {
        Diag.d("gravação", "início")
        if (engine.running) return toast("Pare a macro antes de gravar")
        rec.clear()
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var downT = 0L; var lastUp = 0L; var moved = 0f
        val v = object : View(this) {
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) { // só o primeiro dedo
                    MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; downT = e.eventTime; moved = 0f }
                    MotionEvent.ACTION_MOVE -> moved = maxOf(moved, hypot(e.rawX - downX, e.rawY - downY))
                    MotionEvent.ACTION_UP -> {
                        // post: não remover a janela dentro do próprio dispatch de toque.
                        if (rec.size >= MAX_STEPS) { ui.post { stopRecording(save = true) }; return true }
                        val dur = (e.eventTime - downT).toInt().coerceIn(1, MAX_MS)
                        // Atraso ANTES da ação: o intervalo desde o último toque pertence ao passo novo.
                        val gap = if (rec.isEmpty()) 0 else (downT - lastUp).toInt().coerceIn(0, MAX_MS)
                        lastUp = e.eventTime
                        rec += if (moved > slop) Step(Type.SWIPE, downX.toInt(), downY.toInt(), e.rawX.toInt(), e.rawY.toInt(), dur, delay = gap)
                        else Step(Type.TAP, downX.toInt(), downY.toInt(), ms = dur, delay = gap)
                    }
                }
                return true
            }
        }.apply { setBackgroundColor(0x33FF0000) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT,
        ).apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        wm.addView(v, lp)
        recView = v
        // Reanexa a bolha: janelas do mesmo tipo empilham pela ordem de adição.
        bubble?.let { b -> val p = b.layoutParams; wm.removeView(b); wm.addView(b, p) }
        recBtn.restyle(Ic.STOP, C.RED)
        toast("Gravando: toque nos pontos. Os toques não chegam ao app.")
    }

    private fun stopRecording(save: Boolean) {
        Diag.d("gravação", "fim (salvar=$save)")
        val v = recView ?: return
        runCatching { wm.removeView(v) }
        recView = null
        recBtn.restyle(Ic.REC, C.DARK, C.RED)
        if (!save) return
        if (rec.isEmpty()) return toast("Nada gravado")
        val m = Macro(UUID.randomUUID().toString(), "Gravação " + SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date()), 1, rec.toList())
        val err = m.validate()
        if (err != null) return toast("Gravação inválida: $err")
        runCatching { store.save(m) }
            .onSuccess { store.selected = m.id; toast("Salva: ${m.name} (${m.steps.size} passos)") }
            .onFailure { toast("Falha ao salvar: ${it.message}") }
    }
}
