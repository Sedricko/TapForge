package app.macro17

import android.app.Activity
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Paleta do app de referência (colorPrimary, colorAccent, fundo, cinza) mais tons derivados para superfícies. */
object C {
    val PRIMARY = 0xFF008577.toInt()
    val DARK = 0xFF00574B.toInt()
    val ACCENT = 0xFFD81B60.toInt()
    val BG = 0xFFFAFAFA.toInt()
    val INK = 0xFF212121.toInt()
    val GRAY = 0xFF616161.toInt()
    val LINE = 0xFFE0E0E0.toInt()
    val RED = 0xFFF44336.toInt()
    val HANDLE = 0xFF9E9E9E.toInt()
    val TINT = 0xFFE0F2F1.toInt()      // teal bem claro: fundo de ícones e chips
    val TINT_RED = 0xFFFDECEA.toInt()
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.rounded(fill: Int, radiusDp: Int, stroke: Int = 0) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = dp(radiusDp).toFloat()
    if (stroke != 0) setStroke(dp(1), stroke)
}

/** Feedback de toque. Vai em `foreground` e não em `background`, para a sombra continuar vindo do fundo. */
fun Context.ripple(radiusDp: Int, tint: Int = 0x1F000000) =
    RippleDrawable(ColorStateList.valueOf(tint), null, rounded(Color.WHITE, radiusDp))

fun Context.card() = rounded(Color.WHITE, 12, C.LINE)

// "bold" usa o peso medium: mais refinado que o negrito cheio nos títulos pequenos.
fun Context.label(s: String, sp: Float = 14f, color: Int = C.INK, bold: Boolean = false) = TextView(this).apply {
    text = s; textSize = sp; setTextColor(color)
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

// --- ícones desenhados em Canvas: sem drawables, sem glifos Unicode (que viram emoji em alguns aparelhos) ---

enum class Ic { PLAY, STOP, REC, ADD, BACK, MOVE, OK, TAP, SWIPE, WAIT, KEY, WARN }

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

fun Context.circleBtn(ic: Ic, bg: Int, sizeDp: Int, desc: String, click: () -> Unit) = ImageView(this).apply {
    setImageDrawable(Icon(ic))
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
    foreground = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
    contentDescription = desc
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
    setOnClickListener { click() }
}

/** Troca ícone e cor de um botão criado por [circleBtn]. */
fun ImageView.restyle(ic: Ic, bg: Int) {
    setImageDrawable(Icon(ic))
    (background as GradientDrawable).setColor(bg)
}

/** Círculo com ícone, só decorativo (não clicável). */
fun Context.badge(ic: Ic, bg: Int, fg: Int, sizeDp: Int) = ImageView(this).apply {
    setImageDrawable(Icon(ic, fg))
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

fun Context.chip(text: String) = label(text, 11f, C.DARK, bold = true).apply {
    letterSpacing = 0.06f
    background = rounded(C.TINT, 12)
    setPadding(dp(10), dp(4), dp(10), dp(4))
}

// --- componentes de tela ---

/** targetSdk 35+ força edge-to-edge: sem isto o conteúdo fica sob a barra de status e a de navegação. */
fun View.padForSystemBars(extra: Int, top: Boolean = true) {
    setOnApplyWindowInsetsListener { v, i ->
        val b = i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
        v.setPadding(extra + b.left, extra + if (top) b.top else 0, extra + b.right, extra + b.bottom)
        i
    }
}

/** Barra teal com título; o teal desce até atrás da barra de status. */
fun Activity.appBar(title: String, back: Boolean, action: Pair<Ic, () -> Unit>? = null): View {
    val row = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        if (back) addView(circleBtn(Ic.BACK, Color.TRANSPARENT, 48, "Voltar") { finish() })
        addView(label(title, 20f, Color.WHITE, bold = true).apply {
            setPadding(if (back) dp(4) else dp(16), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        action?.let { (ic, fn) -> addView(circleBtn(ic, Color.TRANSPARENT, 48, "Salvar", fn)) }
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(C.PRIMARY)
        elevation = dp(4).toFloat()
        addView(row, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))
        setOnApplyWindowInsetsListener { v, i ->
            val b = i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, 0)
            i
        }
    }
}

/** Botão flutuante estendido (ícone + texto): diz o que faz sem precisar de dica. */
fun Activity.fab(text: String, click: () -> Unit): View = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(16), 0, dp(22), 0)
    background = rounded(C.ACCENT, 28)
    foreground = ripple(28, 0x33FFFFFF)
    elevation = dp(6).toFloat()
    contentDescription = text
    addView(ImageView(this@fab).apply { setImageDrawable(Icon(Ic.ADD)); layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)) })
    addView(label(text, 15f, Color.WHITE, bold = true).apply { setPadding(dp(6), 0, 0, 0) })
    layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, dp(56), Gravity.BOTTOM or Gravity.END)
    setOnApplyWindowInsetsListener { v, i ->
        val b = i.getInsets(WindowInsets.Type.systemBars())
        (v.layoutParams as FrameLayout.LayoutParams).setMargins(0, 0, dp(16) + b.right, dp(16) + b.bottom)
        v.requestLayout()
        i
    }
    setOnClickListener { click() }
}

/** Ação secundária em texto rosa, como o "Change" do app de referência. */
fun Activity.textAction(text: String, click: () -> Unit) = label(text, 14f, C.ACCENT, bold = true).apply {
    setPadding(dp(10), dp(8), dp(10), dp(8))
    foreground = ripple(8, 0x22D81B60)
    setOnClickListener { click() }
}

/** Campo com rótulo pequeno em cima, dentro de uma caixa branca com borda. */
fun Activity.labeled(title: String, field: View, weight: Float = 0f): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(Color.WHITE, 8, C.LINE)
    setPadding(dp(12), dp(8), dp(12), dp(2))
    addView(label(title, 12f, C.GRAY, bold = true))
    addView(field)
    layoutParams = if (weight > 0) LinearLayout.LayoutParams(0, WRAP_CONTENT, weight).also { it.setMargins(dp(3), dp(4), dp(3), dp(4)) }
    else LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(0, dp(4), 0, dp(4)) }
}

fun Activity.field(value: String, hint: String, numeric: Boolean) = EditText(this).apply {
    background = null; setTextColor(C.INK); textSize = 16f; this.hint = hint
    setPadding(0, dp(4), 0, dp(6))
    if (numeric) inputType = InputType.TYPE_CLASS_NUMBER else setSingleLine()
    setText(value)
}
