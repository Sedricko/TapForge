package app.macro17

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.text.InputType

/** Paleta do app de referência (colorPrimary, colorAccent, fundo, cinza de texto). */
object C {
    val PRIMARY = 0xFF008577.toInt()
    val DARK = 0xFF00574B.toInt()
    val ACCENT = 0xFFD81B60.toInt()
    val BG = 0xFFFAFAFA.toInt()
    val FILL = 0xFFF5F5F5.toInt()
    val LINE = 0xFFE0E0E0.toInt()
    val GRAY = 0xFF616161.toInt()
    val RED = 0xFFF44336.toInt()
    val HANDLE = 0xFF9E9E9E.toInt()
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.rounded(fill: Int, radiusDp: Int, stroke: Int = 0) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = dp(radiusDp).toFloat()
    if (stroke != 0) setStroke(dp(1), stroke)
}

fun Context.label(s: String, sp: Float = 14f, color: Int = Color.BLACK, bold: Boolean = false) = TextView(this).apply {
    text = s; textSize = sp; setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

// --- ícones desenhados em Canvas: sem drawables, sem glifos Unicode (que viram emoji em alguns aparelhos) ---

enum class Ic { PLAY, STOP, REC, ADD, BACK, MOVE, OK }

class Icon(private val ic: Ic) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

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
    contentDescription = desc
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
    setOnClickListener { click() }
}

/** Troca ícone e cor de um botão criado por [circleBtn]. */
fun ImageView.restyle(ic: Ic, bg: Int) {
    setImageDrawable(Icon(ic))
    (background as GradientDrawable).setColor(bg)
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

fun Activity.fab(desc: String, click: () -> Unit) = circleBtn(Ic.ADD, C.ACCENT, 56, desc, click).apply {
    elevation = dp(6).toFloat()
    layoutParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END)
    setOnApplyWindowInsetsListener { v, i ->
        val b = i.getInsets(WindowInsets.Type.systemBars())
        (v.layoutParams as FrameLayout.LayoutParams).setMargins(0, 0, dp(16) + b.right, dp(16) + b.bottom)
        v.requestLayout()
        i
    }
}

/** Botão cinza com borda e texto rosa, como o "Change" do app de referência. */
fun Activity.outlineBtn(label: String, click: () -> Unit) = Button(this).apply {
    text = label; isAllCaps = false; textSize = 13f; setTextColor(C.ACCENT); stateListAnimator = null
    background = rounded(C.FILL, 4, C.LINE)
    minHeight = dp(40); minimumHeight = dp(40)
    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.setMargins(dp(3), 0, dp(3), 0) }
    setOnClickListener { click() }
}

/** Seção em caixa cinza: rótulo em negrito e o campo logo abaixo. */
fun Activity.labeled(title: String, field: View): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(C.FILL, 6, C.LINE)
    setPadding(dp(12), dp(10), dp(12), dp(6))
    addView(label(title, 14f, bold = true))
    addView(field)
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(0, dp(4), 0, dp(4)) }
}

fun Activity.field(value: String, hint: String, numeric: Boolean) = EditText(this).apply {
    background = null; setTextColor(C.GRAY); textSize = 15f; this.hint = hint
    setPadding(0, dp(4), 0, dp(6))
    if (numeric) inputType = InputType.TYPE_CLASS_NUMBER else setSingleLine()
    setText(value)
}
