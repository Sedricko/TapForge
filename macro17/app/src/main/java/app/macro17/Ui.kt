package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

/** Conceito: vidro escuro translúcido (navy ~90%) com tiles brancos a 12%, ícones de cor chapada e acento azul. */
object C {
    val ACCENT = 0xFF5B9DFF.toInt()
    val BLUE = 0xFF2D8CFF.toInt()
    val PURPLE = 0xFF7E57C2.toInt()
    val ORANGE = 0xFFFFA726.toInt()
    val TEAL = 0xFF26A69A.toInt()
    val RED = 0xFFEF5350.toInt()
    val PANEL = 0xE6161C2B.toInt()
    val TILE = 0x1FFFFFFF
    val EDGE = 0x2EFFFFFF
    val TEXT = Color.WHITE
    val TEXT2 = 0xB3FFFFFF.toInt()
    val NIGHT = 0xFF0D1226.toInt()     // texto sobre o acento (chip ativo)
    val DARK = 0xFF1E2640.toInt()      // fundo sólido dos botões do leque
}

private val PALETTE = intArrayOf(C.BLUE, C.PURPLE, C.ORANGE, C.TEAL, C.RED, 0xFF8D6E63.toInt())

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.rounded(fill: Int, radiusDp: Int, stroke: Int = 0) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = dp(radiusDp).toFloat()
    if (stroke != 0) setStroke(dp(1), stroke)
}

/** Feedback de toque. Vai em `foreground`, para o fundo continuar dono do contorno e da sombra. */
fun Context.ripple(radiusDp: Int, tint: Int = 0x26FFFFFF) =
    RippleDrawable(ColorStateList.valueOf(tint), null, rounded(Color.WHITE, radiusDp))

fun Context.glass(radiusDp: Int = 24) = rounded(C.PANEL, radiusDp, C.EDGE)
fun Context.tileBg() = rounded(C.TILE, 16, C.EDGE)

// "bold" usa o peso medium: mais refinado que o negrito cheio em texto pequeno.
fun Context.label(s: String, sp: Float = 14f, color: Int = C.TEXT, bold: Boolean = false) = TextView(this).apply {
    text = s; textSize = sp; setTextColor(color)
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

// --- ícones desenhados em Canvas: sem drawables, sem glifos Unicode (que viram emoji em alguns aparelhos) ---

enum class Ic { PLAY, STOP, REC, ADD, BACK, MOVE, OK, TAP, SWIPE, WAIT, KEY, WARN, GEAR, IMPORT, SEARCH, CHEVRON, EDIT, NUM, MENU, HOME, BOX, INFO, FOLDER, LOCK }

class Icon(private val ic: Ic, color: Int = Color.WHITE) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    override fun draw(c: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val s = minOf(bounds.width(), bounds.height()) * 0.28f // meia-largura do glifo
        p.style = Paint.Style.FILL
        when (ic) {
            Ic.PLAY -> c.drawPath(Path().apply { moveTo(cx - s * .8f, cy - s); lineTo(cx + s, cy); lineTo(cx - s * .8f, cy + s); close() }, p)
            Ic.STOP -> c.drawRect(cx - s * .85f, cy - s * .85f, cx + s * .85f, cy + s * .85f, p)
            Ic.REC -> c.drawCircle(cx, cy, s, p)
            Ic.MOVE -> for (dx in -1..1 step 2) for (dy in -1..1) c.drawCircle(cx + dx * s * .5f, cy + dy * s * .7f, s * .22f, p)
            else -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * .35f
                when (ic) {
                    Ic.ADD -> { c.drawLine(cx - s, cy, cx + s, cy, p); c.drawLine(cx, cy - s, cx, cy + s, p) }
                    Ic.BACK -> {
                        c.drawLine(cx - s, cy, cx + s, cy, p)
                        c.drawLine(cx - s, cy, cx - s * .1f, cy - s * .9f, p)
                        c.drawLine(cx - s, cy, cx - s * .1f, cy + s * .9f, p)
                    }
                    Ic.TAP -> { c.drawCircle(cx, cy, s, p); p.style = Paint.Style.FILL; c.drawCircle(cx, cy, s * .35f, p) }
                    Ic.SWIPE -> {
                        c.drawLine(cx - s, cy, cx + s, cy, p)
                        c.drawLine(cx + s, cy, cx + s * .1f, cy - s * .9f, p)
                        c.drawLine(cx + s, cy, cx + s * .1f, cy + s * .9f, p)
                    }
                    Ic.WAIT -> { c.drawCircle(cx, cy, s, p); c.drawLine(cx, cy, cx, cy - s * .65f, p); c.drawLine(cx, cy, cx + s * .5f, cy, p) }
                    Ic.KEY -> c.drawRoundRect(cx - s, cy - s * .8f, cx + s, cy + s * .8f, s * .3f, s * .3f, p)
                    Ic.WARN -> { c.drawLine(cx, cy - s, cx, cy + s * .2f, p); p.style = Paint.Style.FILL; c.drawCircle(cx, cy + s * .75f, s * .2f, p) }
                    Ic.GEAR -> {
                        c.drawCircle(cx, cy, s * .6f, p)
                        for (k in 0 until 8) {
                            val a = k * Math.PI / 4
                            val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
                            c.drawLine(cx + dx * s * .85f, cy + dy * s * .85f, cx + dx * s * 1.15f, cy + dy * s * 1.15f, p)
                        }
                    }
                    Ic.IMPORT -> {
                        c.drawLine(cx, cy - s, cx, cy + s * .4f, p)
                        c.drawLine(cx, cy + s * .4f, cx - s * .6f, cy - s * .2f, p)
                        c.drawLine(cx, cy + s * .4f, cx + s * .6f, cy - s * .2f, p)
                        c.drawLine(cx - s, cy + s, cx + s, cy + s, p)
                    }
                    Ic.SEARCH -> { c.drawCircle(cx - s * .25f, cy - s * .25f, s * .7f, p); c.drawLine(cx + s * .3f, cy + s * .3f, cx + s, cy + s, p) }
                    Ic.CHEVRON -> c.drawPath(Path().apply { moveTo(cx - s * .4f, cy - s); lineTo(cx + s * .4f, cy); lineTo(cx - s * .4f, cy + s) }, p)
                    Ic.NUM -> {
                        c.drawLine(cx - s * .35f, cy - s, cx - s * .35f, cy + s, p); c.drawLine(cx + s * .35f, cy - s, cx + s * .35f, cy + s, p)
                        c.drawLine(cx - s, cy - s * .35f, cx + s, cy - s * .35f, p); c.drawLine(cx - s, cy + s * .35f, cx + s, cy + s * .35f, p)
                    }
                    Ic.MENU -> for (k in -1..1) c.drawLine(cx - s, cy + k * s * .7f, cx + s, cy + k * s * .7f, p)
                    Ic.HOME -> c.drawPath(Path().apply {
                        moveTo(cx - s, cy); lineTo(cx, cy - s); lineTo(cx + s, cy)
                        moveTo(cx - s * .7f, cy - s * .1f); lineTo(cx - s * .7f, cy + s); lineTo(cx + s * .7f, cy + s); lineTo(cx + s * .7f, cy - s * .1f)
                    }, p)
                    Ic.BOX -> {
                        c.drawRoundRect(cx - s, cy - s, cx + s, cy + s, s * .2f, s * .2f, p)
                        c.drawLine(cx, cy - s * .5f, cx, cy + s * .4f, p)
                        c.drawLine(cx, cy + s * .4f, cx - s * .45f, cy - s * .05f, p); c.drawLine(cx, cy + s * .4f, cx + s * .45f, cy - s * .05f, p)
                    }
                    Ic.INFO -> { c.drawCircle(cx, cy, s, p); c.drawLine(cx, cy - s * .1f, cx, cy + s * .55f, p); p.style = Paint.Style.FILL; c.drawCircle(cx, cy - s * .5f, s * .15f, p) }
                    Ic.FOLDER -> c.drawPath(Path().apply {
                        moveTo(cx - s, cy + s * .8f); lineTo(cx - s, cy - s * .8f); lineTo(cx - s * .2f, cy - s * .8f); lineTo(cx + s * .1f, cy - s * .45f)
                        lineTo(cx + s, cy - s * .45f); lineTo(cx + s, cy + s * .8f); close()
                    }, p)
                    Ic.LOCK -> { c.drawRoundRect(cx - s * .8f, cy - s * .1f, cx + s * .8f, cy + s, s * .2f, s * .2f, p); c.drawArc(cx - s * .5f, cy - s, cx + s * .5f, cy + s * .1f, 180f, 180f, false, p) }
                    Ic.EDIT -> { c.drawLine(cx - s, cy + s, cx + s * .8f, cy - s * .8f, p); c.drawLine(cx - s, cy + s, cx - s * .2f, cy + s, p) }
                    else -> c.drawPath(Path().apply { moveTo(cx - s, cy); lineTo(cx - s * .25f, cy + s * .7f); lineTo(cx + s, cy - s * .7f) }, p)
                }
            }
        }
    }

    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(f: ColorFilter?) { p.colorFilter = f }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun Context.circleBtn(ic: Ic, bg: Int, sizeDp: Int, desc: String, fg: Int = Color.WHITE, click: () -> Unit) = ImageView(this).apply {
    setImageDrawable(Icon(ic, fg))
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
    foreground = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
    contentDescription = desc
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
    setOnClickListener { click() }
}

/** Ícone de app como no conceito: quadrado arredondado de cor chapada. */
fun Context.squareBtn(ic: Ic, bg: Int, sizeDp: Int, desc: String, click: () -> Unit) = ImageView(this).apply {
    setImageDrawable(Icon(ic))
    background = rounded(bg, sizeDp / 4)
    foreground = ripple(sizeDp / 4, 0x33FFFFFF)
    contentDescription = desc
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) }
    setOnClickListener { click() }
}

/** Troca ícone e cores de um botão criado por [circleBtn]. */
fun ImageView.restyle(ic: Ic, bg: Int, fg: Int = Color.WHITE) {
    setImageDrawable(Icon(ic, fg))
    (background as GradientDrawable).setColor(bg)
}

/** Quadrado colorido com ícone, só decorativo. */
fun Context.badge(ic: Ic, bg: Int, sizeDp: Int) = ImageView(this).apply {
    setImageDrawable(Icon(ic))
    background = rounded(bg, sizeDp / 4)
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

fun Context.glyph(ic: Ic, color: Int, sizeDp: Int) = ImageView(this).apply {
    setImageDrawable(Icon(ic, color))
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** Iniciais sobre quadrado colorido (cor estável pelo nome), como os ícones de app da lista do conceito. */
fun Context.monogram(name: String, sizeDp: Int) =
    label(name.trim().take(2).replaceFirstChar { it.uppercase() }, if (sizeDp > 36) 16f else 12f, Color.WHITE, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(PALETTE[(name.hashCode() and 0x7fffffff) % PALETTE.size], sizeDp / 4)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

// --- componentes de tela ---

/** targetSdk 35+ força edge-to-edge: sem isto o conteúdo fica sob a barra de status e a de navegação. */
fun View.padForSystemBars(extra: Int) {
    setOnApplyWindowInsetsListener { v, i ->
        val b = i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
        v.setPadding(extra + b.left, extra + b.top, extra + b.right, extra + b.bottom)
        i
    }
}

fun Activity.textAction(text: String, click: () -> Unit) = label(text, 14f, C.ACCENT, bold = true).apply {
    setPadding(dp(10), dp(8), dp(10), dp(8))
    foreground = ripple(8, 0x225B9DFF)
    setOnClickListener { click() }
}

/** Botão em pílula com texto, para barras de poucas escolhas. */
fun Context.pillBtn(text: String, bg: Int, click: () -> Unit) = label(text, 13f, Color.WHITE, bold = true).apply {
    background = rounded(bg, 18)
    foreground = ripple(18, 0x33FFFFFF)
    setPadding(dp(12), dp(8), dp(12), dp(8))
    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.setMargins(dp(3), 0, dp(3), 0) }
    setOnClickListener { click() }
}

/** Chip de filtro: ativo com acento azul, inativo em vidro. */
fun Activity.chipBtn(text: String, active: Boolean, click: () -> Unit) = label(text, 13f, if (active) C.NIGHT else C.TEXT, bold = active).apply {
    background = rounded(if (active) C.ACCENT else C.TILE, 16, if (active) C.ACCENT else C.EDGE)
    foreground = ripple(16)
    setPadding(dp(14), dp(6), dp(14), dp(6))
    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.setMargins(dp(2), 0, dp(4), 0) }
    setOnClickListener { click() }
}

/** Tile com rótulo pequeno em cima e o campo logo abaixo. */
fun Activity.labeled(title: String, field: View, weight: Float = 0f): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = tileBg()
    setPadding(dp(12), dp(8), dp(12), dp(2))
    addView(label(title, 12f, C.TEXT2, bold = true))
    addView(field)
    layoutParams = if (weight > 0) LinearLayout.LayoutParams(0, WRAP_CONTENT, weight).also { it.setMargins(dp(3), dp(4), dp(3), dp(4)) }
    else LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(0, dp(4), 0, dp(4)) }
}

fun Activity.field(value: String, hint: String, numeric: Boolean) = EditText(this).apply {
    background = null; setTextColor(C.TEXT); setHintTextColor(0x80FFFFFF.toInt()); textSize = 16f; this.hint = hint
    setPadding(0, dp(4), 0, dp(6))
    if (numeric) inputType = InputType.TYPE_CLASS_NUMBER else setSingleLine()
    setText(value)
}

/** Diálogo no mesmo vidro escuro do painel. */
fun AlertDialog.Builder.showGlass(c: Context): AlertDialog = create().also {
    it.window?.setBackgroundDrawable(c.rounded(0xF2161C2B.toInt(), 24, C.EDGE))
    it.show()
}
