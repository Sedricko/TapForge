package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Aba de monitoramento: contadores ao vivo (serviço, macro, memória, erros, avisos), registro ao vivo com filtros,
 * linhas repetidas agrupadas (×N) e botões para copiar ou compartilhar. O relatório completo (aparelho, saídas do
 * processo, logcat, root opcional) fica no botão "Relatório"; é montado numa thread própria porque lê arquivo e roda `logcat`.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class DiagActivity : Activity() {
    private lateinit var store: Store
    private lateinit var list: LinearLayout
    private lateinit var chipRow: LinearLayout
    private lateinit var stats: Map<String, TextView>
    private lateinit var health: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var cat = Cat.ALL
    private var scheduled = false
    @Volatile private var pending: String? = null
    private val onFeed: () -> Unit = { schedule() }
    private val tick = object : Runnable { override fun run() { updateStats(); ui.postDelayed(this, 1000) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)

        val names = listOf("Serviço", "Macro", "Memória", "Erros", "Avisos")
        stats = names.associateWith { label("–", 15f, B.TEXT, bold = true).apply { gravity = Gravity.CENTER } }
        val statRow = LinearLayout(this).apply {
            setPadding(0, dp(8), 0, dp(4))
            names.forEach { n ->
                addView(LinearLayout(this@DiagActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(Color.WHITE, 4, 0xFFE0E0E0.toInt()); elevation = dp(1).toFloat()
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.setMargins(dp(2), 0, dp(2), 0) }
                    addView(stats.getValue(n), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                    addView(label(n, 11f, B.TEXT2).apply { gravity = Gravity.CENTER })
                })
            }
        }
        health = label("", 13f, B.TEXT, bold = true).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8)); background = rounded(Color.WHITE, 4, 0xFFE0E0E0.toInt()); elevation = dp(1).toFloat()
            setOnClickListener { cat = Cat.ERRORS; chips(); render() } // toque leva direto aos erros
        }
        val actions = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(this@DiagActivity).apply {
                setPadding(0, dp(4), 0, dp(4))
                addView(pillBtn("Copiar", B.PRIMARY) { copy(visibleText()) })
                addView(pillBtn("Compartilhar", B.PRIMARY) { share(visibleText()) })
                addView(pillBtn("Salvar relatório", B.PRIMARY) { saveReport() })
                addView(pillBtn("Enviar tudo", B.PRIMARY) { reportMenu() })
                addView(pillBtn("Limpar", B.PINK) { Diag.feed.clear(); Diag.clear() })
                addView(pillBtn("Autoteste", B.PRIMARY) { selfTest() })
                addView(pillBtn("Arquivo do log", B.PRIMARY) { showFileLog() })
                addView(pillBtn("Painel flutuante", B.PRIMARY) { MacroService.instance?.toggleLog() ?: toast("Ative o serviço de acessibilidade primeiro") })
                addView(pillBtn("Opções", B.PRIMARY) { options() })
            })
        }
        chipRow = LinearLayout(this).apply { setPadding(0, dp(4), 0, dp(4)) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chips()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(B.BG)
            addView(appBar("Monitor e logs", Ic.BACK, { finish() }))
            addView(LinearLayout(this@DiagActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), 0, dp(8), 0)
                addView(statRow); addView(health, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(dp(2), dp(4), dp(2), dp(4)) }); addView(actions)
                addView(HorizontalScrollView(this@DiagActivity).apply { isHorizontalScrollBarEnabled = false; addView(chipRow) })
            })
            addView(ScrollView(this@DiagActivity).apply {
                addView(list); setPadding(dp(12), 0, dp(12), 0); clipToPadding = false
                setOnApplyWindowInsetsListener { v, i -> v.setPadding(dp(12), 0, dp(12), i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
            }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }

    /** Últimas linhas do arquivo de log (sobrevive a reinício do app, ao contrário do registro ao vivo). */
    private fun showFileLog() {
        Thread({
            val text = Diag.tail(80_000).ifEmpty { "Arquivo vazio (o diagnóstico estava desligado?)" }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val tv = TextView(this).apply { this.text = text; textSize = 11f; typeface = android.graphics.Typeface.MONOSPACE; setPadding(dp(12), dp(8), dp(12), dp(8)); setTextIsSelectable(true) }
                AlertDialog.Builder(this).setTitle("Arquivo do log").setView(ScrollView(this).apply { addView(tv) })
                    .setPositiveButton("Copiar") { _, _ -> copy(text) }.setNegativeButton("Fechar", null).show()
            }
        }, "macro17-arquivo-log").start()
    }

    private fun selfTest() {
        toast("Executando o autoteste…")
        Thread({
            val checks = SelfTest.run(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread // a tela pode ter sido fechada ou girada durante o teste
                val text = checks.joinToString("\n") { it.toString() }
                AlertDialog.Builder(this).setTitle("Autoteste: ${checks.count { it.ok == true }} ok, ${checks.count { it.ok == null }} avisos, ${checks.count { it.ok == false }} falhas")
                    .setMessage(text).setPositiveButton("Copiar") { _, _ -> copy(text) }.setNeutralButton("Repetir") { _, _ -> selfTest() }.setNegativeButton("Fechar", null).show()
            }
        }, "macro17-autoteste").start()
    }

    override fun onResume() {
        super.onResume()
        Diag.feed.addListener(onFeed)
        render(); ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        Diag.feed.removeListener(onFeed)
        ui.removeCallbacksAndMessages(null); scheduled = false
    }

    private fun schedule() = ui.post {
        if (scheduled) return@post
        scheduled = true
        ui.postDelayed({ scheduled = false; render() }, 250) // junta rajadas de eventos numa só atualização
    }

    private fun rows() = collapse(Diag.feed.snapshot(cat, 500))
    private fun visibleText() = plainText(rows())

    private fun render() {
        list.removeAllViews()
        val rs = rows().take(200)
        if (rs.isEmpty()) list.addView(label("Nenhum evento ainda neste uso do app.", 14f, B.TEXT2).apply { setPadding(dp(8), dp(12), 0, 0) })
        rs.forEach { list.addView(logRow(it, light = true)) }
        updateStats()
    }

    private fun chips() {
        chipRow.removeAllViews()
        Cat.values().forEach { c ->
            val on = c == cat
            val t = if (c == Cat.ERRORS || c == Cat.WARNINGS) "${c.label} · ${Diag.feed.count(c)}" else c.label
            chipRow.addView(label(t, 13f, if (on) Color.WHITE else B.TEXT, bold = on).apply {
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = rounded(if (on) B.PRIMARY else Color.WHITE, 16, if (on) B.PRIMARY else 0xFFE0E0E0.toInt())
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.setMargins(dp(2), 0, dp(2), 0) }
                setOnClickListener { cat = c; chips(); render() }
            })
        }
    }

    private fun updateStats() {
        val svc = MacroService.instance
        val rt = Runtime.getRuntime()
        val err = Diag.feed.count(Cat.ERRORS); val warn = Diag.feed.count(Cat.WARNINGS)
        fun set(k: String, text: String, color: Int) { stats.getValue(k).apply { this.text = text; setTextColor(color) } }
        set("Serviço", if (svc != null) "ativo" else "desligado", if (svc != null) 0xFF2E7D32.toInt() else 0xFFF44336.toInt())
        set("Macro", if (svc?.isRunning() == true) "rodando" else "parada", if (svc?.isRunning() == true) 0xFF1976D2.toInt() else B.TEXT2)
        set("Memória", "${(rt.totalMemory() - rt.freeMemory()) shr 20} MB", B.TEXT)
        set("Erros", err.toString(), if (err > 0) 0xFFF44336.toInt() else B.TEXT2)
        set("Avisos", warn.toString(), if (warn > 0) 0xFFEF6C00.toInt() else B.TEXT2)
        val last = Diag.feed.snapshot(Cat.ERRORS, 1).firstOrNull()
        when {
            svc == null -> { health.text = "Atenção: o serviço de acessibilidade está desligado"; health.setTextColor(0xFFEF6C00.toInt()) }
            err > 0 -> { health.text = "Problema: $err erro(s). Último: ${last?.text.orEmpty().replace("ERRO ", "").lineSequence().firstOrNull().orEmpty().take(90)} (toque para ver)"; health.setTextColor(0xFFF44336.toInt()) }
            warn > 0 -> { health.text = "Atenção: $warn aviso(s) neste uso"; health.setTextColor(0xFFEF6C00.toInt()) }
            else -> { health.text = "Tudo certo: nenhum erro ou aviso neste uso"; health.setTextColor(0xFF2E7D32.toInt()) }
        }
        // Os chips de Erros e Avisos mostram a contagem: atualiza o texto sem refazer a lista.
        for (n in 0 until chipRow.childCount) (chipRow.getChildAt(n) as? TextView)?.let { v ->
            val c = Cat.values()[n]
            if (c == Cat.ERRORS || c == Cat.WARNINGS) v.text = "${c.label} · ${Diag.feed.count(c)}"
        }
    }

    // EXTRA_TEXT e a área de transferência passam pelo Binder (~1 MB): manda só o final, que é o mais recente.
    private fun copy(text: String) {
        if (text.isEmpty()) return toast("Nada para copiar")
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Registro Macro17", text.takeLast(150_000)))
        toast("Copiado" + if (text.length > 150_000) " (últimos 150 mil caracteres)" else "")
    }

    private fun share(text: String) {
        if (text.isEmpty()) return toast("Nada para compartilhar")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Registro Macro17").putExtra(Intent.EXTRA_TEXT, text.takeLast(150_000)), "Enviar registro"))
    }

    private fun options() {
        val items = arrayOf("Registrar eventos em arquivo", "Incluir log do sistema no relatório (root)")
        val on = booleanArrayOf(store.diag, store.diagRoot)
        checkDialog("Opções do monitor", "O relatório com root roda “su” para juntar logcat completo, dmesg e a acessibilidade. Sem root: adb shell pm grant app.macro17 android.permission.READ_LOGS", items, on) { i, v -> if (i == 0) store.diag = v else store.diagRoot = v }
            .setPositiveButton("OK", null).show()
    }

    /** Gera o relatório completo e abre o seletor do sistema para escolher onde guardar (pasta e nome). */
    private fun saveReport() = build { r ->
        pending = r
        val name = "macro17-relatorio-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt"
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, name)
                // Sugere Downloads; o usuário pode navegar para qualquer outra pasta.
                .putExtra("android.provider.extra.INITIAL_URI", Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")),
            SAVE,
        )
    }

    private fun reportMenu() {
        val acts = listOf<Pair<String, () -> Unit>>(
            "Compartilhar relatório completo" to { build { share(it) } },
            "Copiar relatório completo" to { build { copy(it) } },
        )
        AlertDialog.Builder(this).setTitle("Relatório completo").setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    /** Monta o relatório fora da thread principal e entrega o texto a [then] na principal. */
    private fun build(then: (String) -> Unit) {
        toast("Gerando relatório…")
        Thread({
            val r = try { Diag.report(this) } catch (t: Throwable) { "falha ao gerar o relatório: ${t.javaClass.simpleName}: ${t.message}" }
            runOnUiThread { if (!isDestroyed) then(r) }
        }, "macro17-report").start()
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != SAVE) return
        if (res != RESULT_OK) { pending = null; return toast("Salvar cancelado") }
        val uri: Uri = data?.data ?: return
        val text = pending ?: return
        try {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            toast("Relatório salvo")
        } catch (e: Exception) { toast("Falhou: ${e.message ?: e.javaClass.simpleName}") }
        pending = null
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private companion object { const val SAVE = 7 }
}
