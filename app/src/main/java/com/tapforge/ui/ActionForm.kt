package com.tapforge.ui

import android.content.Context
import android.text.InputType
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.tapforge.data.MacroStore
import com.tapforge.model.*
import com.tapforge.vision.Templates
import kotlinx.serialization.json.*

/** Rótulo e explicação de cada campo do JSON das ações. */
private val LABELS = mapOf(
    "x" to ("Posição X (px)" to null), "y" to ("Posição Y (px)" to null),
    "x1" to ("Início X" to null), "y1" to ("Início Y" to null), "x2" to ("Fim X" to null), "y2" to ("Fim Y" to null),
    "w" to ("Largura" to null), "h" to ("Altura" to null),
    "holdMs" to ("Tempo pressionado (ms)" to "Quanto tempo o dedo fica na tela. Toque normal: 50."),
    "durationMs" to ("Duração (ms)" to "Tempo do gesto do início ao fim."),
    "posPx" to ("Variação de posição (px)" to "Desloca o toque ao acaso até este valor, para não ficar mecânico."),
    "timeMs" to ("Variação de tempo (ms)" to "Pausa extra aleatória depois do gesto."),
    "ms" to ("Tempo (ms)" to "1000 ms = 1 segundo."),
    "jitterMs" to ("Variação (± ms)" to "Soma ou subtrai um valor aleatório até este limite."),
    "count" to ("Repetições" to "0 = sem limite de vezes (use outra condição de parada)."),
    "untilMs" to ("Tempo máximo (ms)" to "0 = sem limite de tempo."),
    "templateId" to ("Imagem" to "Nome da imagem recortada (✚ › Imagem no painel)."),
    "alternatives" to ("Imagens alternativas" to "Outros nomes separados por vírgula; vale a primeira achada (ex.: modo claro e escuro)."),
    "threshold" to ("Semelhança mínima (0 a 1)" to "0,9 = quase idêntica. Diminua se não achar; aumente se achar errado."),
    "timeoutMs" to ("Esperar até (ms)" to "Continua procurando por este tempo antes de desistir. 0 = olha uma vez."),
    "into" to ("Guardar em" to "Nome da variável. Para imagem, grava <nome>.x, <nome>.y e <nome>.count."),
    "all" to ("Todas as ocorrências" to "Executa \"se achar\" para cada cópia encontrada."),
    "maxResults" to ("Máximo de ocorrências" to null),
    "scaleTolerant" to ("Aceitar tamanhos diferentes" to "Procura a imagem um pouco maior ou menor (outra resolução). Mais lento."),
    "numbersOnly" to ("Só números" to "\"Ouro: 1.250\" vira 1250."),
    "name" to ("Variável" to null), "value" to ("Valor" to "Pode usar \${variavel}."),
    "from" to ("Variável da imagem" to "A mesma usada em \"Guardar em\" ao procurar a imagem."),
    "text" to ("Texto" to "Pode usar \${variavel}."), "append" to ("Adicionar ao texto existente" to null),
    "packageName" to ("App (pacote)" to null), "waitMs" to ("Esperar abrir (ms)" to "0 = não espera."),
    "stableMs" to ("Tempo parado (ms)" to "Quanto tempo a tela precisa ficar sem mudar."),
    "message" to ("Mensagem" to "Aparece na tela e no Registro. Pode usar \${variavel}."),
    "vibrateMs" to ("Vibrar (ms)" to "0 = não vibra."), "sound" to ("Tocar som" to null),
    "macroId" to ("Macro" to null), "times" to ("Tentativas" to null), "delayMs" to ("Intervalo entre tentativas (ms)" to null),
    "probability" to ("Probabilidade (0 a 1)" to "0,3 = 30% das vezes."), "ignoreCase" to ("Ignorar maiúsculas" to null),
    "color" to ("Cor (#RRGGBB)" to null), "tolerance" to ("Tolerância por canal (0 a 255)" to null),
    "key" to ("Identificador" to "Use nomes diferentes para vigiar regiões diferentes."),
    "jitter" to ("Humanização" to null), "area" to ("Área aleatória" to null), "region" to ("Região da tela" to null),
    "cond" to ("Condição" to null), "while" to ("Enquanto" to "Repete só enquanto for verdadeira."),
    "until" to ("Até" to "Para quando ficar verdadeira."), "inner" to ("Condição negada" to null),
)

private val ENUMS: Map<String, List<Pair<String, String>>> = mapOf(
    "Op" to listOf("EQ" to "= igual", "NE" to "≠ diferente", "LT" to "< menor", "LE" to "≤ menor ou igual", "GT" to "> maior", "GE" to "≥ maior ou igual", "CONTAINS" to "contém"),
    "MathOp" to listOf("SET" to "= definir", "ADD" to "+ somar", "SUB" to "− subtrair", "MUL" to "× multiplicar", "DIV" to "÷ dividir", "MOD" to "resto da divisão", "MIN" to "menor valor", "MAX" to "maior valor"),
    "GlobalKind" to listOf("BACK" to "Voltar", "HOME" to "Início", "RECENTS" to "Recentes", "NOTIFICATIONS" to "Notificações", "QUICK_SETTINGS" to "Configurações rápidas", "LOCK_SCREEN" to "Bloquear tela (Android 9+)"),
)

private val CONDITIONS: List<Pair<String, Condition>> = listOf(
    "Sempre" to Condition.Always,
    "Ao acaso (probabilidade)" to Condition.Random(0.5),
    "Variável compara com valor" to Condition.Var("n", Op.GT, "0"),
    "Texto aparece na região (OCR)" to Condition.TextContains(Region(0, 0, 600, 300), "texto"),
    "Imagem está visível" to Condition.ImageVisible("alvo"),
    "Cor do pixel" to Condition.Pixel(100, 100, "#FFFFFF"),
    "A tela mudou" to Condition.Changed(),
    "App está aberto" to Condition.AppOpen("com.android.chrome"),
    "NÃO (inverte)" to Condition.Not(Condition.Always),
    "Todas (E)" to Condition.AllOf(listOf(Condition.Always, Condition.Always)),
    "Qualquer uma (OU)" to Condition.AnyOf(listOf(Condition.Always, Condition.Always)),
)

private val NON_NULL_REGION = setOf("read_text", "text_contains")

private fun JsonObject.with(path: List<String>, v: JsonElement): JsonObject {
    val k = path.first()
    val child = if (path.size == 1) v else (this[k] as JsonObject).with(path.drop(1), v)
    return JsonObject(toMutableMap().apply { put(k, child) })
}

/** Formulário gerado a partir do JSON da ação, com rótulos e explicações. */
class ActionForm(private val ctx: Context, action: Action, private val onSave: (Action) -> Unit) {
    private val json = MacroStore.json
    private var obj = json.encodeToJsonElement(Action.serializer(), action).jsonObject
    private lateinit var box: LinearLayout

    fun show() {
        box = ctx.vertical()
        build()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("${iconOf(action())} ${describe(action())}")
            .setView(ctx.scrolling(box))
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("JSON") { _, _ -> jsonDialog() }
            .setPositiveButton("Salvar") { _, _ ->
                runCatching { onSave(action()) }.onFailure { ctx.info("Valor inválido", it.message ?: "") }
            }.show()
    }

    private fun action() = json.decodeFromJsonElement(Action.serializer(), obj)

    private fun set(path: List<String>, v: JsonElement) { obj = obj.with(path, v) }

    private fun build() {
        box.removeAllViews()
        fields(obj, emptyList())
        if (obj["type"]?.jsonPrimitive?.content in setOf("gesture")) note("Os caminhos dos dedos são editados no JSON, ou grave com ⏺ no painel.")
        if (slots(action()).isNotEmpty()) note("As ações de dentro dos blocos são editadas na lista do editor.")
    }

    private fun note(t: String) = box.addView(TextView(ctx).apply {
        text = t; setPadding(0, ctx.dp(12), 0, 0); setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
    })

    private fun sub(t: String) = box.addView(TextView(ctx).apply {
        text = t; setPadding(0, ctx.dp(16), 0, 0); setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
    })

    private fun label(k: String) = LABELS[k]?.first ?: k
    private fun help(k: String) = LABELS[k]?.second

    private fun fields(o: JsonObject, path: List<String>) {
        val type = o["type"]?.jsonPrimitive?.content
        for ((k, v) in o) {
            if (k == "type") continue
            val p = path + k
            when {
                k in setOf("cond", "while", "until", "inner") -> {
                    condPicker(p, v, nullable = k == "while" || k == "until")
                    if (v is JsonObject) fields(v, p)
                }
                k == "area" || k == "region" -> regionFields(p, v, nullable = type !in NON_NULL_REGION)
                k == "alternatives" -> ctx.run {
                    box.field(label(k), (v as? JsonArray)?.joinToString(", ") { it.jsonPrimitive.content } ?: "", help(k)) { s ->
                        set(p, JsonArray(s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) }))
                    }
                }
                v is JsonObject -> { sub(label(k)); fields(v, p) }
                v is JsonPrimitive && v !is JsonNull -> primitive(k, p, v)
                // Listas de ações/condições: blocos do editor ou JSON.
            }
        }
    }

    private fun primitive(k: String, p: List<String>, v: JsonPrimitive) {
        val enumKind = when {
            k == "kind" -> "GlobalKind"
            k == "op" && ENUMS["Op"]!!.any { it.first == v.content } -> "Op"
            k == "op" -> "MathOp"
            else -> null
        }
        when {
            enumKind != null -> choice(label(k), ENUMS[enumKind]!!, v.content) { set(p, JsonPrimitive(it)) }
            !v.isString && v.booleanOrNull != null -> box.addView(MaterialSwitch(ctx).apply {
                text = label(k); isChecked = v.boolean
                setOnCheckedChangeListener { _, b -> set(p, JsonPrimitive(b)) }
            })
            k == "macroId" -> {
                val macros = MacroStore.list(ctx).map { it.id to it.name }
                choice(label(k), macros, v.content) { set(p, JsonPrimitive(it)) }
            }
            v.isString -> {
                val extra = when (k) {
                    "templateId" -> Templates.list(ctx).takeIf { it.isNotEmpty() }?.let { "Disponíveis: ${it.joinToString(", ")}" }
                    else -> null
                }
                val et = box.field(label(k), v.content, listOfNotNull(help(k), extra).joinToString("\n").ifEmpty { null }) { set(p, JsonPrimitive(it)) }
                if (k == "packageName") box.addView(MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    text = "Escolher app (${ctx.appLabel(v.content)})"
                    setOnClickListener { ctx.pickApp { pkg -> et.setText(pkg); text = "Escolher app (${ctx.appLabel(pkg)})" } }
                })
            }
            else -> {
                val decimal = v.content.contains('.') || v.longOrNull == null
                val type = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
                box.field(label(k), v.content, help(k), type) { s ->
                    set(p, if (decimal) JsonPrimitive(s.replace(',', '.').toDoubleOrNull() ?: 0.0) else JsonPrimitive(s.toLongOrNull() ?: 0L))
                }
            }
        }
    }

    /** Botão que abre uma lista de opções. */
    private fun choice(title: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
        val b = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun caption(v: String) = "$title: ${options.firstOrNull { it.first == v }?.second ?: v}"
        b.text = caption(current)
        b.setOnClickListener {
            MaterialAlertDialogBuilder(ctx).setTitle(title)
                .setSingleChoiceItems(options.map { it.second }.toTypedArray(), options.indexOfFirst { it.first == current }) { d, i ->
                    onPick(options[i].first); b.text = caption(options[i].first); d.dismiss()
                }.show()
        }
        box.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(8) })
    }

    private fun condPicker(p: List<String>, v: JsonElement, nullable: Boolean) {
        val current = (v as? JsonObject)?.let { json.decodeFromJsonElement(Condition.serializer(), it) }
        val options = (if (nullable) listOf("Nenhuma" to null) else emptyList()) + CONDITIONS
        val b = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonTonalStyle)
        b.text = "${label(p.last())}: ${current?.let { describe(it) } ?: "nenhuma"}"
        help(p.last())?.let { sub(it) }
        b.setOnClickListener {
            MaterialAlertDialogBuilder(ctx).setTitle("Tipo de condição")
                .setItems(options.map { it.first }.toTypedArray()) { _, i ->
                    val c = options[i].second
                    set(p, if (c == null) JsonNull else json.encodeToJsonElement(Condition.serializer(), c))
                    build()
                }.show()
        }
        box.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(12) })
    }

    private fun regionFields(p: List<String>, v: JsonElement, nullable: Boolean) {
        sub(label(p.last()))
        if (nullable) box.addView(MaterialSwitch(ctx).apply {
            text = if (p.last() == "area") "Tocar num ponto aleatório da área" else "Limitar a uma parte da tela"
            isChecked = v is JsonObject
            setOnCheckedChangeListener { _, b ->
                set(p, if (b) json.encodeToJsonElement(Region.serializer(), Region(0, 0, 400, 300)) else JsonNull)
                build()
            }
        })
        if (v is JsonObject) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in listOf("x", "y", "w", "h")) {
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                col.field(label(k).substringBefore(" ("), v[k]!!.jsonPrimitive.content, type = InputType.TYPE_CLASS_NUMBER) { s ->
                    set(p + k, JsonPrimitive(s.toLongOrNull() ?: 0L))
                }
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ctx.dp(4) })
            }
            box.addView(row)
            note("Dica: ✚ › Texto no painel marca uma região direto na tela.")
        }
    }

    private fun jsonDialog() {
        val box = ctx.vertical()
        val et = box.field("JSON da ação", json.encodeToString(JsonObject.serializer(), obj),
            "Edição avançada: listas de condições, caminhos de gestos, etc.", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        et.typeface = android.graphics.Typeface.MONOSPACE
        MaterialAlertDialogBuilder(ctx).setTitle("Editar JSON").setView(ctx.scrolling(box))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                runCatching { onSave(json.decodeFromString(Action.serializer(), et.text.toString())) }
                    .onFailure { ctx.info("JSON inválido", it.message ?: "") }
            }.show()
    }
}

