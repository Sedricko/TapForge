package com.tapforge.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.tapforge.R
import com.tapforge.data.MacroStore
import com.tapforge.model.*
import com.tapforge.service.ClickerAccessibilityService
import com.tapforge.service.realScreenSize
import com.tapforge.vision.Templates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Editor de um macro: configurações, lista de ações em blocos, catálogo descrito, teste e histórico. */
class EditorActivity : AppCompatActivity() {
    private lateinit var macro: Macro
    private lateinit var content: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private var dirty = false
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        macro = intent.getStringExtra("id")?.let { MacroStore.load(this, it) } ?: return finish()
        setContentView(R.layout.activity_editor)
        content = findViewById(R.id.content)
        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.setNavigationContentDescription("Voltar")
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.act_test -> { save(); test(macro.actions, whole = true) }
                R.id.act_save -> save(true)
                R.id.act_json -> jsonDialog()
                R.id.act_history -> historyDialog()
                R.id.act_templates -> info("Imagens recortadas", Templates.list(this).ifEmpty { listOf("Nenhuma. Use ✚ › Imagem no painel flutuante.") }.joinToString("\n"))
            }
            true
        }
        findViewById<View>(R.id.fab).setOnClickListener { catalog(emptyList()) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!dirty) return finish()
                MaterialAlertDialogBuilder(this@EditorActivity).setTitle("Salvar alterações?")
                    .setNegativeButton("Descartar") { _, _ -> finish() }
                    .setPositiveButton("Salvar") { _, _ -> save(); finish() }.show()
            }
        })
        render()
    }

    private fun change(m: Macro) { macro = m; dirty = true; render() }

    private fun save(announce: Boolean = false) {
        MacroStore.save(this, macro); dirty = false; render()
        if (announce) Snackbar.make(content, "Salvo", Snackbar.LENGTH_SHORT).show()
    }

    private fun render() {
        toolbar.title = macro.name
        toolbar.subtitle = if (dirty) "Alterações não salvas" else "Editor"
        content.removeAllViews()

        content.section("Configurações")
        content.card("⚙️", "Configurações do macro",
            listOfNotNull(limitsText(macro), triggerText(macro.trigger)?.let { "⚡ $it" },
                macro.folder.takeIf { it.isNotBlank() }?.let { "Pasta: $it" }, if (macro.favorite) "★ Favorito" else null,
                if (macro.screenW > 0) "Ajusta coordenadas para outras telas (${macro.screenW}×${macro.screenH})" else null).joinToString("\n"),
            action = "Alterar", onAction = { settingsDialog() })

        content.section("Ações (${actionCount(macro)})")
        if (macro.actions.isEmpty()) {
            content.card("💡", "Nenhuma ação", "Toque em Adicionar ação, ou use ✚ no painel flutuante para marcar pontos, imagens e textos direto na tela.")
            return
        }
        val descs = actionCatalog(1080, 2400).reversed().associate { it.action::class to it.desc }
        for (row in flatten(macro.actions)) {
            val indent = dp(20) * row.depth
            when (row) {
                is Row.Item -> {
                    val v = content.card(iconOf(row.action), describe(row.action), descs[row.action::class],
                        endIcon = R.drawable.ic_more, endLabel = "Opções", onEnd = { actionSheet(row) }, onClick = { edit(row) })
                    (v.layoutParams as LinearLayout.LayoutParams).marginStart = indent
                }
                is Row.Slot -> {
                    val r = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(indent, 0, 0, 0) }
                    r.addView(TextView(this).apply {
                        text = "↳ ${row.label}"
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    r.addView(MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                        text = "Adicionar aqui"; setIconResource(R.drawable.ic_add); setOnClickListener { catalog(row.list) }
                    })
                    content.addView(r)
                }
            }
        }
    }

    // ---------- Ações ----------
    private fun edit(row: Row.Item) = ActionForm(this, row.action) { a ->
        change(macro.copy(actions = updateList(macro.actions, row.list) { it.toMutableList().apply { set(row.index, a) } }))
    }.show()

    private fun actionSheet(row: Row.Item) {
        val sheet = BottomSheetDialog(this)
        val box = vertical(16)
        box.section("${iconOf(row.action)} ${describe(row.action)}")
        fun op(t: String, d: String, f: () -> Unit) = box.setting(t, d) { sheet.dismiss(); f() }
        fun upd(f: (MutableList<Action>) -> Unit) = change(macro.copy(actions = updateList(macro.actions, row.list) { it.toMutableList().apply(f) }))
        op("Editar", "Mudar posição, tempos e demais opções desta ação.") { edit(row) }
        op("Testar só esta ação", "Executa uma vez daqui a 3 segundos, no app que estiver aberto.") { test(listOf(row.action)) }
        op("Duplicar", "Cria uma cópia logo abaixo.") { upd { it.add(row.index + 1, row.action) } }
        op("Mover para cima", "Troca de lugar com a ação anterior.") { upd { if (row.index > 0) it.add(row.index - 1, it.removeAt(row.index)) } }
        op("Mover para baixo", "Troca de lugar com a próxima ação.") { upd { if (row.index < it.size - 1) it.add(row.index + 1, it.removeAt(row.index)) } }
        op("Excluir", "Remove esta ação" + if (slots(row.action).isNotEmpty()) " e tudo que está dentro dela." else ".") { upd { it.removeAt(row.index) } }
        sheet.setContentView(scrolling(box))
        sheet.show()
    }

    /** Catálogo descrito, agrupado; insere no fim da lista [target]. */
    private fun catalog(target: ListPath) {
        val sheet = BottomSheetDialog(this)
        val box = vertical(16)
        val (w, h) = if (macro.screenW > 0) macro.screenW to macro.screenH else realScreenSize(this)
        var group = ""
        for (item in actionCatalog(w, h)) {
            if (item.group != group) { box.section(item.group); group = item.group }
            box.card(item.icon, item.title, item.desc) {
                sheet.dismiss()
                change(macro.copy(actions = updateList(macro.actions, target) { it + item.action }))
                val rows = flatten(macro.actions).filterIsInstance<Row.Item>().filter { it.list == target }
                rows.lastOrNull()?.let { edit(it) }
            }
        }
        sheet.setContentView(scrolling(box))
        sheet.show()
    }

    private fun test(actions: List<Action>, whole: Boolean = false) {
        val svc = ClickerAccessibilityService.instance
            ?: return Snackbar.make(content, "Ative o serviço de acessibilidade para testar", Snackbar.LENGTH_LONG).show()
        Snackbar.make(content, "Testando em 3 s: vá para o app desejado", Snackbar.LENGTH_SHORT).show()
        moveTaskToBack(true)
        main.postDelayed({ if (whole) svc.start(macro, "teste") else svc.test(actions, macro) }, 3000)
    }

    // ---------- Configurações ----------
    private fun settingsDialog() {
        val box = vertical()
        var m = macro
        box.field("Nome", m.name) { m = m.copy(name = it.ifBlank { "Sem nome" }) }
        box.field("Pasta", m.folder, "Agrupa macros na lista (opcional).") { m = m.copy(folder = it.trim()) }
        box.field("Parar após (segundos)", (m.maxRuntimeMs / 1000).toString(), "0 = sem limite de tempo.", InputType.TYPE_CLASS_NUMBER) {
            m = m.copy(maxRuntimeMs = (it.toLongOrNull() ?: 0) * 1000)
        }
        box.field("Repetições do macro", m.maxIterations.toString(), "Quantas vezes a lista inteira roda. 0 = até parar.", InputType.TYPE_CLASS_NUMBER) {
            m = m.copy(maxIterations = it.toIntOrNull() ?: 0)
        }
        box.addView(MaterialSwitch(this).apply {
            text = "Favorito"; isChecked = m.favorite; setOnCheckedChangeListener { _, b -> m = m.copy(favorite = b) }
        })
        box.addView(MaterialSwitch(this).apply {
            text = "Ajustar coordenadas em telas diferentes"; isChecked = m.screenW > 0
            setOnCheckedChangeListener { _, b ->
                val (w, h) = realScreenSize(this@EditorActivity)
                m = if (b) m.copy(screenW = w, screenH = h) else m.copy(screenW = 0, screenH = 0)
            }
        })
        box.addView(TextView(this).apply {
            text = "Gravado nesta tela, o macro se adapta proporcionalmente se for usado em outra resolução."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        })

        box.section("Início automático")
        val value = box.field("Valor", "", null) {}
        val pickBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "Escolher app" }
        val chips = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        val kinds = listOf("Nenhum", "Ao abrir um app", "Ao carregar", "Horário")
        var kind = when (m.trigger) { null -> 0; is Trigger.AppOpened -> 1; Trigger.Charging -> 2; is Trigger.Schedule -> 3 }
        fun refresh() {
            val til = value.parent.parent as View
            til.visibility = if (kind == 1 || kind == 3) View.VISIBLE else View.GONE
            pickBtn.visibility = if (kind == 1) View.VISIBLE else View.GONE
            (til as? com.google.android.material.textfield.TextInputLayout)?.apply {
                hint = if (kind == 1) "Pacote do app" else "Horário (HH:mm)"
                helperText = if (kind == 1) "O macro inicia quando este app abrir." else "Todo dia neste horário (o Android pode atrasar alguns minutos)."
            }
        }
        value.setText(when (val t = m.trigger) { is Trigger.AppOpened -> t.packageName; is Trigger.Schedule -> t.time; else -> "" })
        kinds.forEachIndexed { i, t ->
            chips.addView(Chip(this).apply { text = t; isCheckable = true; isChecked = i == kind; setOnClickListener { kind = i; value.setText(""); refresh() } })
        }
        box.addView(chips, box.childCount - 1)
        pickBtn.setOnClickListener { pickApp { value.setText(it) } }
        box.addView(pickBtn)
        refresh()

        MaterialAlertDialogBuilder(this).setTitle("Configurações do macro").setView(scrolling(box))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar") { _, _ ->
                val v = value.text.toString().trim()
                val trigger = when (kind) {
                    1 -> v.takeIf { it.isNotEmpty() }?.let { Trigger.AppOpened(it) }
                    2 -> Trigger.Charging
                    3 -> v.takeIf { com.tapforge.service.Scheduler.next(it) != null }?.let { Trigger.Schedule(it) }
                        ?: run { info("Horário inválido", "Use o formato HH:mm, por exemplo 08:30."); null }
                    else -> null
                }
                change(m.copy(trigger = trigger))
            }.show()
    }

    // ---------- JSON e histórico ----------
    private fun jsonDialog() {
        val box = vertical()
        val et = box.field("JSON do macro", MacroStore.encode(macro), "Formato aberto: dá para copiar, colar e versionar.",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        et.typeface = android.graphics.Typeface.MONOSPACE
        MaterialAlertDialogBuilder(this).setTitle("JSON completo").setView(scrolling(box))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar") { _, _ ->
                runCatching { MacroStore.decode(et.text.toString()) }
                    .onSuccess { change(it.copy(id = macro.id)) }
                    .onFailure { info("JSON inválido", it.message ?: "") }
            }.show()
    }

    private fun historyDialog() {
        val versions = MacroStore.history(this, macro.id)
        if (versions.isEmpty()) { info("Versões anteriores", "Ainda não há versões anteriores. Cada vez que você salva, a versão antiga fica guardada aqui (até 15)."); return }
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        MaterialAlertDialogBuilder(this).setTitle("Restaurar versão")
            .setItems(versions.map { (t, m) -> "${fmt.format(Date(t))} · ${actionCount(m)} ações" }.toTypedArray()) { _, i ->
                change(versions[i].second.copy(id = macro.id))
                Snackbar.make(content, "Versão restaurada. Salve para manter.", Snackbar.LENGTH_LONG).show()
            }.setNegativeButton("Fechar", null).show()
    }
}
