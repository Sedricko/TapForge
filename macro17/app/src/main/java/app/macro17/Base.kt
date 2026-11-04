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
    val PRIMARY = 0xFF008577.toInt()
    val DARK = 0xFF00574B.toInt()      // faixa da barra de status
    val ON = 0xFF004D40.toInt()        // switch ligado
    val BG = 0xFFFAFAFA.toInt()
    val TEXT = 0xFF212121.toInt()
    val TEXT2 = 0xFF757575.toInt()
    val BLUE = 0xFF2196F3.toInt()      // botões de cartão
    val PINK = 0xFFD81B60.toInt()      // FAB
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
        addView(glyph(nav, Color.WHITE, 56).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16)); foreground = ripple(24, 0x33FFFFFF)
            contentDescription = if (nav == Ic.BACK) "Voltar" else "Menu"; setOnClickListener { onNav() }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        })
        addView(label(title, 20f, Color.WHITE, medium = true).apply { setPadding(dp(16), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) }) // título a 72dp
        actions.forEach { addView(it) }
    }, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))
}

/** Cartão branco de cantos pouco arredondados, como os do base. */
fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(Color.WHITE, 2)
    elevation = dp(2).toFloat()
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(dp(5), dp(4), dp(5), dp(4)) }
}

/** Cartão-botão: ícone verde à esquerda, texto azul em caixa alta no centro. */
fun Context.bigButton(ic: Ic, text: String, click: () -> Unit): View = FrameLayout(this).apply {
    background = rounded(Color.WHITE, 2)
    foreground = ripple(2, 0x1F000000)
    elevation = dp(2).toFloat()
    addView(glyph(ic, B.PRIMARY, 25), FrameLayout.LayoutParams(dp(25), dp(25), Gravity.START or Gravity.CENTER_VERTICAL).also { it.marginStart = dp(60) })
    addView(label(text.uppercase(), 20f, B.BLUE, bold = true), FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER).also { it.topMargin = dp(12); it.bottomMargin = dp(12) })
    minimumHeight = dp(49)
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(dp(5), dp(4), dp(5), dp(4)) }
    setOnClickListener { click() }
}

/** Menu lateral: painel de 280dp sobre um véu. [items] = (ícone, texto, ação); o primeiro é a tela atual. */
class Drawer(private val a: Activity, private val items: List<Triple<Ic, String, () -> Unit>>, private val selected: Int = 0) {
    private val panelW = a.dp(280)
    private val scrim = View(a).apply { setBackgroundColor(0x99000000.toInt()); visibility = View.GONE; setOnClickListener { close() } }
    private val panel = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE); elevation = a.dp(10).toFloat(); visibility = View.GONE
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            background = GradientDrawable(GradientDrawable.Orientation.BR_TL, intArrayOf(0xFF4DB6AC.toInt(), 0xFF009688.toInt(), 0xFF00695C.toInt()))
            setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(16))
            setOnApplyWindowInsetsListener { v, i -> v.setPadding(a.dp(16), a.dp(16) + i.getInsets(WindowInsets.Type.statusBars()).top, a.dp(16), a.dp(16)); i }
            addView(android.widget.ImageView(a).apply { setImageDrawable(a.packageManager.getApplicationIcon(a.packageName)) }, LinearLayout.LayoutParams(a.dp(72), a.dp(72)))
            addView(a.label("Macro17", 14f, Color.WHITE).apply { setPadding(0, a.dp(8), 0, 0) })
        }, LinearLayout.LayoutParams(MATCH_PARENT, a.dp(176)))
        items.forEachIndexed { n, (ic, text, go) ->
            addView(LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(a.dp(22), 0, a.dp(22), 0)
                // Fundo e ripple com recuo (8,4,8,4), como o NavigationView do base; ícone e texto sempre #616161.
                if (n == selected) background = android.graphics.drawable.InsetDrawable(a.rounded(0x1F008577, 4), a.dp(8), a.dp(4), a.dp(8), a.dp(4))
                foreground = android.graphics.drawable.InsetDrawable(a.ripple(4, 0x1F000000), a.dp(8), a.dp(4), a.dp(8), a.dp(4))
                addView(a.glyph(ic, 0xFF616161.toInt(), 24))
                addView(a.label(text, 14f, 0xFF616161.toInt(), medium = true).apply { setPadding(a.dp(14), 0, 0, 0) })
                setOnClickListener { close(); go() }
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, a.dp(48))
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
