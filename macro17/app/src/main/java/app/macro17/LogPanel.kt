package app.macro17

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Painel de registro ao vivo, por cima de qualquer app (janela de acessibilidade): filtros por categoria, linhas coloridas,
 * pausar, parar a macro, minimizar e fechar. Durante uma macro o painel deixa os toques passarem ([setThrough]) e some
 * no instante de cada captura de tela ([captureHide]), para não poluir a busca de imagem.
 */
class LogPanel(private val svc: AccessibilityService, private val wm: WindowManager, private val stopMacro: () -> Unit) {
    private val ui = Handler(Looper.getMainLooper())
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    private var root: FrameLayout? = null
    private var list: LinearLayout? = null
    private var body: View? = null
    private var chipRow: LinearLayout? = null
    private lateinit var lp: WindowManager.LayoutParams
    private var cat = Cat.ALL
    private var paused = false
    private var mini = false
    @Volatile private var hideOnShot = true
    private var scheduled = false
    private val onFeed: () -> Unit = { schedule() }

    val shown get() = root != null

    fun show() {
        if (root != null) return
        val h = wm.maximumWindowMetrics.bounds.height()
        lp = WindowManager.LayoutParams(
            MATCH_PARENT, (h * 0.55).toInt(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }
        val v = build()
        root = v
        wm.addView(v, lp)
        Diag.feed.addListener(onFeed)
        Diag.d("painel", "registro ao vivo aberto")
        render()
    }

    fun hide() {
        Diag.feed.removeListener(onFeed)
        root?.let { runCatching { wm.removeView(it) } }
        root = null; list = null; body = null; chipRow = null
    }

    /** Com [on], os toques atravessam o painel (para os gestos da macro caírem no app de baixo). */
    fun setThrough(on: Boolean) = ui.post {
        val r = root ?: return@post
        lp.flags = if (on) lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        r.alpha = if (on) 0.8f else 1f
        runCatching { wm.updateViewLayout(r, lp) }
    }

    /** Chamado pela thread da macro em volta de cada captura. Devolve true se escondeu (o chamador espera o redesenho). */
    fun captureHide(hide: Boolean): Boolean {
        val r = root ?: return false
        if (!hideOnShot) return false
        ui.post { r.visibility = if (hide) View.INVISIBLE else View.VISIBLE }
        return true
    }

    private fun schedule() {
        ui.post {
            if (scheduled) return@post
            scheduled = true
            ui.postDelayed({ scheduled = false; if (!paused && root != null) render() }, 250)
        }
    }

    private fun render() {
        val l = list ?: return
        l.removeAllViews()
        val rows = collapse(Diag.feed.snapshot(cat, 150))
        if (rows.isEmpty()) l.addView(svc.label("Nenhum evento ainda.", 14f, C.TEXT2).apply { setPadding(svc.dp(8), svc.dp(12), 0, 0) })
        rows.forEach { l.addView(svc.logRow(it)) }
    }

    private fun chips() {
        val row = chipRow ?: return
        row.removeAllViews()
        Cat.values().forEach { c ->
            val on = c == cat
            row.addView(svc.label(c.label, 13f, if (on) Color.WHITE else C.TEXT2, bold = on).apply {
                background = svc.rounded(if (on) 0x335B9DFF else 0x00000000, 16, if (on) C.ACCENT else C.EDGE)
                setPadding(svc.dp(14), svc.dp(6), svc.dp(14), svc.dp(6))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.marginEnd = svc.dp(6) }
                setOnClickListener { cat = c; chips(); render() }
            })
        }
    }

    private fun sq(ic: Ic, desc: String, bg: Int = C.TILE, click: (ImageView) -> Unit): ImageView {
        lateinit var b: ImageView
        b = svc.squareBtn(ic, bg, 40, desc) { click(b) }
        return b
    }

    private fun share() {
        val txt = plainText(collapse(Diag.feed.snapshot(cat, 500))).takeLast(150_000)
        svc.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Enviar registro")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun build(): FrameLayout {
        val eye = sq(Ic.EYE, "Esconder o painel durante cada captura de tela", 0x445B9DFF) { b ->
            hideOnShot = !hideOnShot
            b.background = svc.rounded(if (hideOnShot) 0x445B9DFF else C.TILE, 10)
        }
        val pause = sq(Ic.PAUSE, "Pausar a atualização") { b ->
            paused = !paused
            b.setImageDrawable(Icon(if (paused) Ic.PLAY else Ic.PAUSE))
            if (!paused) render()
        }
        val stop = sq(Ic.STOP, "Parar a macro") { stopMacro() }
        val min = sq(Ic.MINUS, "Minimizar") {
            mini = !mini
            body?.visibility = if (mini) View.GONE else View.VISIBLE
            lp.height = if (mini) WRAP_CONTENT else (wm.maximumWindowMetrics.bounds.height() * 0.55).toInt()
            root?.let { runCatching { wm.updateViewLayout(it, lp) } }
        }
        val close = sq(Ic.CLOSE, "Fechar") { hide() }

        val header = LinearLayout(svc).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(svc.label("Registro", 20f, C.TEXT, bold = true).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
            listOf(eye, pause, stop, min, close).forEach { addView(it) }
        }
        chipRow = LinearLayout(svc)
        chips()
        list = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
        val content = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(svc).apply {
                setPadding(0, svc.dp(8), 0, svc.dp(8))
                addView(svc.pillBtn("Limpar", C.TILE) { Diag.feed.clear() }.apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.setMargins(svc.dp(3), 0, svc.dp(3), 0) }; gravity = Gravity.CENTER })
                addView(svc.pillBtn("Compartilhar", C.BLUE) { share() }.apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.setMargins(svc.dp(3), 0, svc.dp(3), 0) }; gravity = Gravity.CENTER })
            })
            addView(HorizontalScrollView(svc).apply { isHorizontalScrollBarEnabled = false; addView(chipRow) })
            addView(ScrollView(svc).apply { addView(list) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        body = content
        return FrameLayout(svc).apply {
            background = svc.rounded(0xF2161C2B.toInt(), 24, C.EDGE)
            setPadding(svc.dp(14), svc.dp(10), svc.dp(14), svc.dp(10))
            addView(LinearLayout(svc).apply {
                orientation = LinearLayout.VERTICAL
                addView(header)
                addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }
}
