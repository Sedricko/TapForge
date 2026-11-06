package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.EditText
import android.widget.LinearLayout
import android.text.InputType

/**
 * "Editar Itens Selecionados" do base (pref_touch_edit_selected_config): tela de preferências com "Selecionar o que editar." e só a(s)
 * linha(s) da opção, rodapé ALTERAR TUDO / CANCELAR. Devolve em "v" os campos da opção escolhida; o editor aplica aos itens marcados.
 */
class EditSelectedActivity : Activity() {
    companion object {
        val OPTIONS = arrayOf("Tempo de atraso", "Pesquisa de imagem", "Pesquisa de imagem/Área de pesquisa", "Pesquisa de imagem/Taxa de correspondência",
            "Pesquisa de imagem/Velocidade de pesquisa", "Pesquisa de imagem/Duração da pesquisa", "Pesquisa de imagem/Mover", "Ação")
        val SPEEDS = listOf("Muito rápido" to 50, "Rápido" to 200, "1 Seg" to 1000, "2 Seg" to 2000, "3 Seg" to 3000)
        // 5 áreas (o vídeo do base mostra as 5), cada uma com a 2ª linha de descrição (search_area_*_info do base)
        val AREAS = arrayOf("Posição Fixa", "Tela Cheia", "Tela Cheia (Escala de Cinza)", "Área Personalizada", "Área Personalizada (Escala de Cinza)")
        val AREA_INFO = arrayOf("Compara imagens em uma posição fixa.", "Procura imagens na tela cheia.", "Procura imagens na tela cheia. A velocidade de pesquisa de imagem é mais rápida.", "Pesquisa de imagem em área personalizada.", "Pesquisa de imagem em área personalizada. A velocidade de pesquisa de imagem é mais rápida.")
    }

    private var opt = 0
    private var delayOn = true; private var delayMs = 3000 // atraso nasce ligado com 3,0 s, como no base
    private var matchOn = true
    private var area = 0 // Posição Fixa
    private val ca = intArrayOf(0, 0, 500, 500)
    private var thr = 100; private var cycle = 200; private var sms = 10_000
    private var fail = 0; private var ok = 0
    private var skipOn = false
    private var total = 1

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        total = intent.getIntExtra("total", 1); names = intent.getStringArrayExtra("names") ?: emptyArray()
        render()
    }

    private fun secs(ms: Int) = "%.1f Sec".format(java.util.Locale.US, ms / 1000.0)
    /** "N" quando inteiro em segundos, senão "N.N" (resumo "N Segundos" do base). */
    private fun trim(ms: Int) = if (ms % 1000 == 0) "${ms / 1000}" else "${ms / 1000.0}"
    private var names: Array<String> = emptyArray()
    private fun goLabel(n: Int) = if (n == 0) "0. Próximo" else "$n. ${names.getOrNull(n - 1).orEmpty()}".trim()

    private fun render() {
        val p = Prefs(this)
        p.category("Editar Itens Selecionados", iconRes = R.drawable.bp_ic_menu_edit)
        p.valueRow("Selecione o que editar.", OPTIONS[opt]) {
            AlertDialog.Builder(this).setTitle("Selecione o que editar.").setNegativeButton("Cancelar", null).setSingleChoiceItems(OPTIONS, opt) { d, w -> opt = w; d.dismiss(); render() }.show()
        }
        when (opt) {
            0 -> {
                p.category("Atraso", iconRes = R.drawable.bp_alarm)
                p.switchRow("Atraso", "Tocar/Deslizar será processado após o tempo de atraso.", delayOn) { delayOn = it; render() }
                p.valueRow("Tempo de Atraso", secs(delayMs), delayOn) { numDialog("Tempo de Atraso (segundos)", delayMs / 1000.0) { delayMs = (it * 1000).toInt().coerceIn(0, MAX_DELAY_MS); render() } }
            }
            1 -> {
                p.category("Correspondência de Imagem", iconRes = R.drawable.bp_outline_image_search_24)
                p.switchRow("Correspondência de Imagem", "Tocar/Deslizar será processado se a imagem corresponder.", matchOn) { matchOn = it; render() }
            }
            2 -> {
                p.category("Correspondência de Imagem", iconRes = R.drawable.bp_outline_image_search_24)
                p.valueRow("Área de Pesquisa", AREAS[area] + "\n" + AREA_INFO[area]) {
                    // lista de 2 linhas (título + descrição), sem botões, como no base
                    val adapter = object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, AREAS.toList()) {
                        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View =
                            (super.getView(position, convertView, parent) as android.widget.CheckedTextView).also { it.text = AREAS[position] + "\n" + AREA_INFO[position] }
                    }
                    AlertDialog.Builder(this).setTitle("Área de Pesquisa").setSingleChoiceItems(adapter, area) { d, w -> area = w; d.dismiss(); render() }.show()
                }
                if (area >= 3) p.valueRow("Área Personalizada", "X:${ca[0]} Y:${ca[1]} W:${ca[2] - ca[0]} H:${ca[3] - ca[1]}") {
                    startActivityForResult(Intent(this, PositionPickerActivity::class.java).putExtra("mode", PositionPickerActivity.AREA).putExtra("v", ca), 78)
                }
            }
            3 -> {
                p.category("Correspondência de Imagem", iconRes = R.drawable.bp_outline_image_search_24)
                p.switchRow("Correspondência de Imagem", "Tocar/Deslizar será processado se a imagem corresponder.", true) // exibido como no base; o valor não é aplicado
                p.valueRow("Taxa de correspondência de imagem (%)", "$thr%") { numDialog("Taxa de correspondência de imagem (%)", thr.toDouble()) { thr = it.toInt().coerceIn(0, 100); render() } }
            }
            4 -> {
                p.category("Correspondência de Imagem", iconRes = R.drawable.bp_outline_image_search_24)
                p.valueRow("Velocidade de Pesquisa de Imagem", SPEEDS.firstOrNull { it.second == cycle }?.first ?: "$cycle ms") {
                    AlertDialog.Builder(this).setTitle("Velocidade de Pesquisa de Imagem").setMessage("Defina uma velocidade mais rápida para detectar imagens que mudam rapidamente.")
                        .setSingleChoiceItems(SPEEDS.map { it.first }.toTypedArray(), SPEEDS.indexOfFirst { it.second == cycle }) { d, w -> cycle = SPEEDS[w].second; d.dismiss(); render() }.show()
                }
            }
            5 -> {
                p.category("Correspondência de Imagem", iconRes = R.drawable.bp_outline_image_search_24)
                p.valueRow("Tempo de Pesquisa", if (sms == 0) "Infinito" else "${trim(sms)} Segundos") { numDialog("Tempo de Pesquisa (segundos)", sms / 1000.0) { sms = (it * 1000).toInt().coerceIn(0, MAX_DELAY_MS); render() } }
            }
            6 -> {
                p.category("Correspondência de Imagem", iconRes = R.drawable.bp_outline_image_search_24)
                p.valueRow("Mover em Caso de Falha", goLabel(fail)) { pickStep("Mover em Caso de Falha") { fail = it; render() } }
                p.valueRow("Mover em Caso de Sucesso", goLabel(ok)) { pickStep("Mover em Caso de Sucesso") { ok = it; render() } }
            }
            else -> {
                p.category("Ação de Toque", iconRes = R.drawable.bp_click_traffic)
                p.switchRow("Tocar/Deslizar/Pressionar não funcionará. Usado para atraso e comparação de imagens.", "", skipOn) { skipOn = it }
            }
        }
        val page = p.page("Editor de Macro", Ic.BACK, { finish() })
        fun pill(text: String, click: () -> Unit) = label(text, 16f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER; background = rounded(B.PRIMARY, 28); setOnClickListener { click() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
            addView(page, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(LinearLayout(this@EditSelectedActivity).apply {
                setPadding(dp(15), dp(20), dp(15), dp(20))
                addView(pill("ALTERAR TUDO") { apply() }, LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(5), 0, dp(5), 0) })
                addView(pill("CANCELAR") { finish() }, LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(5), 0, dp(5), 0) })
            }, LinearLayout.LayoutParams(MATCH_PARENT, -2))
        })
    }

    private fun apply() {
        val v = when (opt) {
            0 -> intArrayOf(if (delayOn) 1 else 0, delayMs)
            1 -> intArrayOf(if (matchOn) 1 else 0)
            2 -> intArrayOf(area, ca[0], ca[1], ca[2], ca[3])
            3 -> intArrayOf(thr)
            4 -> intArrayOf(cycle)
            5 -> intArrayOf(sms)
            6 -> intArrayOf(fail, ok)
            else -> intArrayOf(if (skipOn) 1 else 0)
        }
        setResult(RESULT_OK, Intent().putExtra("opt", opt).putExtra("v", v)); finish()
    }

    private fun pickStep(title: String, then: (Int) -> Unit) {
        val items = Array(total + 1) { goLabel(if (it < total) it + 1 else 0) }
        AlertDialog.Builder(this).setTitle(title).setItems(items) { _, w -> then(if (w < total) w + 1 else 0) }.show()
    }

    private fun numDialog(title: String, init: Double, then: (Double) -> Unit) {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(init.toString()); setPadding(dp(24), dp(16), dp(24), dp(8)) }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> et.text.toString().toDoubleOrNull()?.let(then) }.setNegativeButton("Cancelar", null).show()
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 78 && res == RESULT_OK) data?.getIntArrayExtra("v")?.copyInto(ca, 0, 0, 4)?.let { render() }
    }
}
