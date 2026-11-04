package app.macro17

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Tela de preferências no formato do app base: barra verde, linhas brancas com título e descrição, switch verde-escuro
 * à direita, categorias com título verde-escuro e um filete entre elas. O texto começa a 72dp, deixando a coluna da
 * esquerda para o ícone da categoria.
 */
class Prefs(private val a: Activity) {
    val column = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
    private var first = true

    private val switchTrack = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0x8000574B.toInt(), 0xFFBDBDBD.toInt()))
    private val switchThumb = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0xFF00574B.toInt(), 0xFFECECEC.toInt()))

    private fun divider() = View(a).apply { setBackgroundColor(0x1F000000); layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1) }

    /** Título de categoria (cor de destaque do base) com ícone opcional na coluna da esquerda; [iconRes] é um vetor com cor própria (ex.: new_icon). */
    fun category(title: String, ic: Ic? = null, iconRes: Int? = null) {
        if (!first) column.addView(divider())
        first = false
        column.addView(LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(a.dp(16), a.dp(8), a.dp(16), a.dp(8))
            addView(FrameLayout(a).apply {
                layoutParams = LinearLayout.LayoutParams(a.dp(56), WRAP_CONTENT)
                if (iconRes != null) addView(android.widget.ImageView(a).apply { setImageResource(iconRes) }, FrameLayout.LayoutParams(a.dp(48), a.dp(48), Gravity.CENTER_VERTICAL))
                else if (ic != null) addView(a.glyph(ic, B.TEXT2, 24), FrameLayout.LayoutParams(a.dp(24), a.dp(24), Gravity.CENTER_VERTICAL))
            })
            addView(a.label(title, 14f, 0xFF00574B.toInt(), medium = true)) // colorAccent do tema do base (#00574B)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = a.dp(16) })
    }

    private fun texts(title: String, summary: String, enabled: Boolean) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        addView(a.label(title, 16f, B.TEXT))
        if (summary.isNotEmpty()) addView(a.label(summary, 14f, B.TEXT2))
        alpha = if (enabled) 1f else 0.45f
    }

    private fun row(enabled: Boolean = true, click: (() -> Unit)? = null) = LinearLayout(a).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(a.dp(72), a.dp(16), a.dp(16), a.dp(16))
        if (enabled && click != null) { foreground = a.ripple(0, 0x1F00897B); setOnClickListener { Diag.d("config", "linha: ${(getChildAt(0) as? android.view.ViewGroup)?.getChildAt(0).let { (it as? android.widget.TextView)?.text }}"); click() } }
        column.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    /** Linha com switch. Desabilitada: aparece esmaecida e não responde (função que o Macro17 ainda não tem). */
    fun switchRow(title: String, summary: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit = {}): Switch {
        val sw = Switch(a).apply {
            isChecked = checked; this.isEnabled = enabled; contentDescription = title
            thumbTintList = switchThumb; trackTintList = switchTrack
            setOnCheckedChangeListener { _, on -> Diag.d("config", "$title = $on"); onChange(on) }
        }
        val r = row(enabled) { sw.toggle() }
        r.addView(texts(title, summary, enabled)); r.addView(sw, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.marginStart = a.dp(16) })
        return sw
    }

    /** Linha que mostra um valor e abre um diálogo ao tocar. Devolve o TextView da descrição para atualizar. */
    fun valueRow(title: String, summary: String, enabled: Boolean = true, click: (() -> Unit)? = null): TextView {
        val t = texts(title, summary, enabled)
        val r = row(enabled, click)
        r.addView(t)
        return (t.getChildAt(1) as? TextView) ?: a.label("", 14f, B.TEXT2).also { t.addView(it) }
    }

    fun note(text: String) {
        column.addView(a.label(text, 13f, B.TEXT2).apply { setPadding(a.dp(72), a.dp(10), a.dp(20), a.dp(16)) })
    }

    /** Página completa: barra verde + rolagem. [onNav] atende a seta ou o hambúrguer. */
    lateinit var scrollView: ScrollView

    fun page(title: String, nav: Ic, onNav: () -> Unit, drawer: Drawer? = null): View {
        val scroll = ScrollView(a).apply {
            scrollView = this
            addView(column)
            setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
        }
        return FrameLayout(a).apply {
            setBackgroundColor(Color.WHITE)
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(a.appBar(title, nav, onNav))
                addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            drawer?.attach(this)
        }
    }
}

/** Menu lateral comum às telas principais; [selected] é o item em destaque. Só lista telas que existem. */
fun Activity.mainDrawer(selected: Int): Drawer {
    fun go(c: Class<*>, top: Boolean = false) {
        val i = android.content.Intent(this, c)
        if (top) i.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
    }
    return Drawer(this, listOf(
        Triple(Ic.HOME, "Home") { if (this !is MainActivity) go(MainActivity::class.java, top = true) },
        Triple(Ic.ANDROID, "Permissões") { if (this !is PermissionsActivity) go(PermissionsActivity::class.java) },
        Triple(Ic.ALARM, "Agendamento") { if (Store(this).list().isEmpty()) android.widget.Toast.makeText(this, "Nenhum arquivo de macro.", android.widget.Toast.LENGTH_LONG).show() else if (this !is AlarmListActivity) go(AlarmListActivity::class.java) },
        Triple(Ic.ARCHIVE, "Backup e Restauração") { if (this !is BackupActivity) go(BackupActivity::class.java) },
        Triple(Ic.GEAR, "Configurações") { if (this !is OptionsActivity) go(OptionsActivity::class.java) },
        Triple(Ic.SEARCH, "Monitor e logs") { if (this !is DiagActivity) go(DiagActivity::class.java) },
        Triple(Ic.ARTICLE, "Sobre o Aplicativo") { if (this !is AboutActivity) go(AboutActivity::class.java) },
    ), selected)
}
