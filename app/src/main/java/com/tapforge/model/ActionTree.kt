package com.tapforge.model

/** Endereço de uma lista de ações: cada passo é (índice da ação, bloco filho). Vazio = lista principal. */
typealias ListPath = List<Pair<Int, Int>>

/** Blocos filhos de uma ação (nome, lista). */
fun slots(a: Action): List<Pair<String, List<Action>>> = when (a) {
    is Action.Loop -> listOf("repetir" to a.body)
    is Action.If -> listOf("então" to a.then, "senão" to a.otherwise)
    is Action.FindImage -> listOf("se achar" to a.onFound, "se não achar" to a.onNotFound)
    is Action.Retry -> listOf("tentar" to a.body, "se falhar" to a.onFail)
    else -> emptyList()
}

fun withSlot(a: Action, slot: Int, list: List<Action>): Action = when (a) {
    is Action.Loop -> a.copy(body = list)
    is Action.If -> if (slot == 0) a.copy(then = list) else a.copy(otherwise = list)
    is Action.FindImage -> if (slot == 0) a.copy(onFound = list) else a.copy(onNotFound = list)
    is Action.Retry -> if (slot == 0) a.copy(body = list) else a.copy(onFail = list)
    else -> a
}

fun getList(root: List<Action>, p: ListPath): List<Action> = p.fold(root) { l, (i, s) -> slots(l[i])[s].second }

fun updateList(root: List<Action>, p: ListPath, f: (List<Action>) -> List<Action>): List<Action> {
    if (p.isEmpty()) return f(root)
    val (i, s) = p[0]
    return root.toMutableList().also { it[i] = withSlot(root[i], s, updateList(slots(root[i])[s].second, p.drop(1), f)) }
}

/** Linha do editor: uma ação ou o cabeçalho de um bloco filho. */
sealed interface Row {
    val depth: Int
    data class Item(val list: ListPath, val index: Int, override val depth: Int, val action: Action) : Row
    data class Slot(val list: ListPath, override val depth: Int, val label: String) : Row
}

fun flatten(root: List<Action>, list: ListPath = emptyList(), depth: Int = 0): List<Row> = root.flatMapIndexed { i, a ->
    listOf<Row>(Row.Item(list, i, depth, a)) + slots(a).flatMapIndexed { s, (label, children) ->
        val p = list + (i to s)
        listOf<Row>(Row.Slot(p, depth + 1, label)) + flatten(children, p, depth + 2)
    }
}

private fun r(g: Region?) = g?.let { " em [${it.x},${it.y} ${it.w}×${it.h}]" } ?: ""

fun describe(c: Condition): String = when (c) {
    Condition.Always -> "sempre"
    is Condition.Random -> "acaso ${(c.probability * 100).toInt()}%"
    is Condition.Var -> "${c.name} ${c.op} ${c.value}"
    is Condition.TextContains -> "texto contém \"${c.text}\"${r(c.region)}"
    is Condition.ImageVisible -> "imagem ${c.templateId} visível${r(c.region)}"
    is Condition.Not -> "não (${describe(c.inner)})"
    is Condition.AllOf -> c.conds.joinToString(" E ", "(", ")") { describe(it) }
    is Condition.AnyOf -> c.conds.joinToString(" OU ", "(", ")") { describe(it) }
    is Condition.Pixel -> "pixel (${c.x},${c.y}) = ${c.color}"
    is Condition.Changed -> "tela mudou${r(c.region)}"
    is Condition.AppOpen -> "app aberto: ${c.packageName}"
}

fun describe(a: Action): String = when (a) {
    is Action.Tap -> if (a.area != null) "Tocar em área${r(a.area)}" else "Tocar (${a.x}, ${a.y})"
    is Action.Swipe -> "Deslizar (${a.x1}, ${a.y1}) → (${a.x2}, ${a.y2})"
    is Action.Gesture -> "Gesto com ${a.strokes.size} dedo(s)"
    is Action.Wait -> "Esperar ${a.ms} ms" + if (a.jitterMs > 0) " ±${a.jitterMs}" else ""
    is Action.Loop -> "Repetir" + listOfNotNull(
        a.count.takeIf { it > 0 }?.let { " $it×" }, a.untilMs.takeIf { it > 0 }?.let { " por ${it / 1000}s" },
        a.whileCond?.let { " enquanto ${describe(it)}" }, a.untilCond?.let { " até ${describe(it)}" },
    ).joinToString("")
    is Action.If -> "Se ${describe(a.cond)}"
    is Action.FindImage -> "Procurar imagem ${a.templateId}" + (if (a.all) " (todas)" else "") + r(a.region)
    is Action.ReadText -> "Ler texto${r(a.region)} → ${a.into}"
    is Action.SetVar -> "${a.name} = ${a.value}"
    is Action.Calc -> "${a.name} ${a.op} ${a.value}"
    is Action.TapFound -> "Tocar onde achou (${a.from})"
    is Action.Global -> "Botão do sistema: ${a.kind}"
    is Action.TypeText -> "Digitar \"${a.text}\""
    is Action.Clipboard -> "Copiar \"${a.text}\""
    is Action.OpenApp -> "Abrir app ${a.packageName}"
    is Action.WaitStable -> "Esperar tela parar${r(a.region)}"
    is Action.Alert -> "Alerta \"${a.message}\""
    is Action.Log -> "Registrar \"${a.message}\""
    is Action.Call -> "Executar macro ${a.macroId}"
    is Action.Retry -> "Tentar até ${describe(a.until)} (${a.times}×)"
    Action.Stop -> "Parar"
}
