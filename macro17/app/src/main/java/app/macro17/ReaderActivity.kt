package app.macro17

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Ensina o leitor de números: cada amostra capturada pelo orbe aparece aqui; você digita o número que ela mostra
 * e o leitor aprende o formato de cada dígito daquela fonte.
 */
class ReaderActivity : Activity() {
    private lateinit var store: Store
    private lateinit var body: LinearLayout
    private lateinit var summary: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        summary = label("", 13f, C.TEXT2)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(circleBtn(Ic.BACK, Color.TRANSPARENT, 40, "Voltar") { finish() })
            addView(label("Leitor de números", 16f, bold = true).apply { setPadding(dp(4), 0, 0, 0) })
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = tileBg()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
            addView(label("Como ensinar", 14f, bold = true))
            addView(label("1. No orbe, toque na lupa, arraste sobre um número e escolha “Amostra”.\n2. Aqui, digite o número que a amostra mostra.\n3. Repita com números diferentes até cobrir 0 a 9. Fontes e tamanhos diferentes convivem.\nDígitos colados uns nos outros não separam.", 12f, C.TEXT2))
            addView(summary.apply { setPadding(0, dp(8), 0, 0) })
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass()
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(bar); addView(info)
            addView(ScrollView(this@ReaderActivity).apply { addView(body) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).also { it.topMargin = dp(8) })
            addView(textAction("Esquecer tudo o que o leitor aprendeu") { store.clearGlyphs(); toast("Leitor limpo"); refresh() }.apply {
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.gravity = Gravity.END }
            })
        }
        setContentView(LinearLayout(this).apply {
            addView(panel, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            padForSystemBars(dp(12))
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        summary.text = "Aprendido (símbolos por dígito): " + store.glyphs().summary()
        body.removeAllViews()
        val ids = store.samples()
        if (ids.isEmpty()) body.addView(label("Nenhuma amostra pendente.", 13f, C.TEXT2).apply { setPadding(dp(4), dp(12), 0, 0) })
        ids.forEach { body.addView(row(it)) }
    }

    private fun row(id: String): View {
        val bmp = store.sample(id) ?: run { store.deleteSample(id); return View(this) } // arquivo ilegível: descarta
        val digits = field("", "Dígitos do recorte", numeric = true)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = tileBg()
            setPadding(dp(12), dp(10), dp(12), dp(4))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
            addView(ImageView(this@ReaderActivity).apply {
                setImageBitmap(bmp); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_START
                contentDescription = "Amostra capturada"
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(72))
            })
            addView(labeled("Número que aparece no recorte", digits))
            addView(LinearLayout(this@ReaderActivity).apply {
                gravity = Gravity.END
                addView(textAction("Descartar") { store.deleteSample(id); refresh() })
                addView(textAction("Ensinar") { teach(id, digits.text.toString().trim()) })
            })
        }
    }

    private fun teach(id: String, digits: String) {
        val bmp = store.sample(id) ?: return toast("Amostra não encontrada")
        val set = store.glyphs()
        Digits.learn(store.gray(bmp), digits, set)?.let { return toast(it) }
        try {
            store.saveGlyphs(set)
            store.deleteSample(id)
            toast("Aprendido: $digits")
        } catch (e: Exception) { toast("Falha ao salvar: ${e.message}") }
        refresh()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
