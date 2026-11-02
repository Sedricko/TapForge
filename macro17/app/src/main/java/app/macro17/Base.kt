package app.macro17

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Visual claro do app base (Material verde-azulado). Convive com [C] (noturno) até as telas migrarem. */
object B {
    val PRIMARY = 0xFF00897B.toInt()
    val DARK = 0xFF00574B.toInt()      // faixa da barra de status
    val ON = 0xFF004D40.toInt()        // switch ligado
    val BG = 0xFFFAFAFA.toInt()
    val TEXT = 0xFF212121.toInt()
    val TEXT2 = 0xFF757575.toInt()
    val BLUE = 0xFF2196F3.toInt()      // botões de cartão
    val PINK = 0xFFE91E63.toInt()      // FAB
    val RED = 0xFFF44336.toInt()
}

fun Activity.appBar(title: String, nav: Ic, onNav: () -> Unit, vararg actions: View): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setBackgroundColor(B.DARK)
    // edge-to-edge (targetSdk 35+): a barra de status vira faixa escura sobre a qual a barra verde assenta.
    setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, i.getInsets(WindowInsets.Type.statusBars()).top, 0, 0); i }
    addView(LinearLayout(this@appBar).apply {
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(B.PRIMARY)
        elevation = dp(4).toFloat()
        addView(glyph(nav, Color.WHITE, 40).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8)); foreground = ripple(24, 0x33FFFFFF)
            contentDescription = "Menu"; setOnClickListener { onNav() }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).also { it.setMargins(dp(4), 0, dp(12), 0) }
        })
        addView(label(title, 20f, Color.WHITE, bold = true).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
        actions.forEach { addView(it) }
    }, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))
}

/** Cartão branco de cantos pouco arredondados, como os do base. */
fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(Color.WHITE, 4)
    elevation = dp(2).toFloat()
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) }
}

/** Cartão-botão: ícone verde à esquerda, texto azul em caixa alta no centro. */
fun Context.bigButton(ic: Ic, text: String, click: () -> Unit): View = FrameLayout(this).apply {
    background = rounded(Color.WHITE, 4)
    foreground = ripple(4, 0x1F00897B)
    elevation = dp(2).toFloat()
    minimumHeight = dp(64)
    addView(glyph(ic, B.PRIMARY, 28), FrameLayout.LayoutParams(dp(28), dp(28), Gravity.START or Gravity.CENTER_VERTICAL).also { it.marginStart = dp(48) })
    addView(label(text.uppercase(), 20f, B.BLUE, bold = true), FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER).also { it.topMargin = dp(18); it.bottomMargin = dp(18) })
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) }
    setOnClickListener { click() }
}

/** Menu lateral: painel de 280dp sobre um véu. [items] = (ícone, texto, ação); o primeiro é a tela atual. */
class Drawer(private val a: Activity, private val items: List<Triple<Ic, String, () -> Unit>>, private val selected: Int = 0) {
    private val panelW = a.dp(280)
    private val scrim = View(a).apply { setBackgroundColor(0x66000000); visibility = View.GONE; setOnClickListener { close() } }
    private val panel = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE); elevation = a.dp(16).toFloat(); visibility = View.GONE
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF009688.toInt(), 0xFF4DB6AC.toInt()))
            setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(16))
            setOnApplyWindowInsetsListener { v, i -> v.setPadding(a.dp(16), a.dp(16) + i.getInsets(WindowInsets.Type.statusBars()).top, a.dp(16), a.dp(16)); i }
            addView(a.badge(Ic.TAP, 0xFF2196F3.toInt(), 64))
            addView(a.label("Macro17", 16f, Color.WHITE).apply { setPadding(0, a.dp(12), 0, 0) })
        }, LinearLayout.LayoutParams(MATCH_PARENT, a.dp(170)))
        items.forEachIndexed { n, (ic, text, go) ->
            addView(LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(a.dp(16), 0, a.dp(16), 0)
                if (n == selected) background = a.rounded(0x1F00897B, 4)
                foreground = a.ripple(4, 0x1F00897B)
                addView(a.glyph(ic, B.TEXT2, 24))
                addView(a.label(text, 15f, if (n == selected) B.PRIMARY else B.TEXT2, bold = true).apply { setPadding(a.dp(24), 0, 0, 0) })
                setOnClickListener { close(); go() }
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, a.dp(48)).also { it.setMargins(a.dp(8), if (n == 0) a.dp(8) else a.dp(4), a.dp(8), 0) }
            })
        }
    }

    /** Camadas a empilhar acima do conteúdo da tela. */
    fun attach(root: FrameLayout) {
        root.addView(scrim, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(panel, FrameLayout.LayoutParams(panelW, MATCH_PARENT))
    }

    fun open() {
        scrim.visibility = View.VISIBLE; panel.visibility = View.VISIBLE
        scrim.alpha = 0f; scrim.animate().alpha(1f).setDuration(180).start()
        panel.translationX = -panelW.toFloat(); panel.animate().translationX(0f).setDuration(180).start()
    }

    fun close() {
        scrim.animate().alpha(0f).setDuration(150).withEndAction { scrim.visibility = View.GONE }.start()
        panel.animate().translationX(-panelW.toFloat()).setDuration(150).withEndAction { panel.visibility = View.GONE }.start()
    }

    val isOpen get() = panel.visibility == View.VISIBLE
}
