package com.tapforge.model

/** Item do catálogo do editor: grupo, ícone, título, explicação e ação com valores iniciais. */
data class CatalogItem(val group: String, val icon: String, val title: String, val desc: String, val action: Action)

/** Ações que o editor oferece em "Adicionar", com valores iniciais para uma tela w×h. */
fun actionCatalog(w: Int, h: Int): List<CatalogItem> {
    val cx = w / 2; val cy = h / 2
    val center = Region(cx - w / 4, cy - h / 8, w / 2, h / 4)
    val t = "Toques e gestos"; val tm = "Tempo"; val l = "Lógica"; val v = "Visão"; val vr = "Variáveis"; val s = "Sistema"
    return listOf(
        CatalogItem(t, "👆", "Tocar", "Toca num ponto da tela. Ajuste a posição, quanto tempo segura e a variação aleatória.", Action.Tap(cx, cy)),
        CatalogItem(t, "🎯", "Tocar em área aleatória", "Toca num ponto sorteado dentro de um retângulo, cada vez num lugar diferente.", Action.Tap(cx, cy, area = center)),
        CatalogItem(t, "↕️", "Deslizar", "Arrasta o dedo de um ponto a outro, como rolar uma lista.", Action.Swipe(cx, h * 3 / 4, cx, h / 4)),
        CatalogItem(t, "〰️", "Gesto com caminho", "Desenha um caminho com vários pontos e curvas.",
            Action.Gesture(listOf(Stroke(listOf(Point(cx - 200, cy), Point(cx, cy - 200), Point(cx + 200, cy)), durationMs = 600)))),
        CatalogItem(t, "🤏", "Pinça (zoom)", "Dois dedos ao mesmo tempo, afastando ou aproximando, para dar zoom.", Action.Gesture(listOf(
            Stroke(listOf(Point(cx - 50, cy), Point(cx - 300, cy)), durationMs = 400),
            Stroke(listOf(Point(cx + 50, cy), Point(cx + 300, cy)), durationMs = 400),
        ))),
        CatalogItem(tm, "⏱️", "Esperar", "Pausa por um tempo, com variação aleatória opcional.", Action.Wait(1000)),
        CatalogItem(tm, "🧘", "Esperar a tela parar", "Espera a tela (ou uma região) ficar parada, por exemplo até uma animação terminar.", Action.WaitStable()),
        CatalogItem(l, "🔁", "Repetir N vezes", "Repete as ações de dentro um número fixo de vezes.", Action.Loop(count = 5, body = emptyList())),
        CatalogItem(l, "🔂", "Repetir até uma condição", "Repete até a condição ficar verdadeira, por exemplo até uma imagem aparecer.",
            Action.Loop(count = 100, untilCond = Condition.ImageVisible("alvo"), body = emptyList())),
        CatalogItem(l, "🔀", "Se / senão", "Executa um bloco se a condição for verdadeira e outro se for falsa.",
            Action.If(Condition.Var("n", Op.GT, "0"), then = emptyList())),
        CatalogItem(l, "♻️", "Tentar de novo", "Repete um bloco até dar certo; se falhar N vezes, executa o plano B.",
            Action.Retry(Condition.ImageVisible("ok"), body = emptyList(), onFail = listOf(Action.Global(GlobalKind.HOME)))),
        CatalogItem(l, "🧩", "Executar outro macro", "Roda outro macro aqui dentro, para reaproveitar partes prontas.", Action.Call("demo")),
        CatalogItem(l, "⏹️", "Parar", "Encerra o macro imediatamente.", Action.Stop),
        CatalogItem(v, "🖼️", "Procurar imagem", "Procura uma imagem na tela e executa ações se achar ou se não achar.",
            Action.FindImage("alvo", timeoutMs = 3000, onFound = listOf(Action.TapFound()))),
        CatalogItem(v, "🧲", "Tocar em todas as imagens", "Acha todas as cópias de uma imagem e toca em cada uma.",
            Action.FindImage("alvo", all = true, onFound = listOf(Action.TapFound(), Action.Wait(300)))),
        CatalogItem(v, "🔤", "Ler texto (OCR)", "Lê o texto de uma região da tela e guarda numa variável.", Action.ReadText(center, "texto")),
        CatalogItem(v, "🔢", "Ler número (OCR)", "Lê só o número de uma região: \"Ouro: 1.250\" vira 1250.", Action.ReadText(center, "numero", numbersOnly = true)),
        CatalogItem(vr, "📝", "Definir variável", "Guarda um valor com um nome. Use \${nome} em textos para inserir o valor.", Action.SetVar("n", "0")),
        CatalogItem(vr, "➕", "Conta com variável", "Soma, subtrai, multiplica ou divide; por exemplo, contador + 1.", Action.Calc("n", MathOp.ADD, "1")),
        CatalogItem(s, "📱", "Botão do sistema", "Voltar, Início, Recentes, Notificações, Configurações rápidas ou Bloquear a tela.", Action.Global(GlobalKind.BACK)),
        CatalogItem(s, "⌨️", "Digitar texto", "Escreve no campo de texto que estiver selecionado.", Action.TypeText("olá")),
        CatalogItem(s, "📋", "Copiar texto", "Coloca um texto na área de transferência.", Action.Clipboard("\${texto}")),
        CatalogItem(s, "🚀", "Abrir app", "Abre um app instalado e espera ele aparecer.", Action.OpenApp("com.android.settings", waitMs = 3000)),
        CatalogItem(s, "🔔", "Alerta", "Vibra, toca um som e mostra uma mensagem.", Action.Alert("Pronto", vibrateMs = 300, sound = true)),
        CatalogItem(s, "🧾", "Registrar no log", "Escreve uma linha no Registro; útil para depurar.", Action.Log("n = \${n}")),
    )
}

/** Ícone de uma ação já criada (o mesmo do catálogo). */
fun iconOf(a: Action): String = when (a) {
    is Action.Tap -> if (a.area != null) "🎯" else "👆"
    is Action.Swipe -> "↕️"
    is Action.Gesture -> if (a.strokes.size > 1) "🤏" else "〰️"
    is Action.Wait -> "⏱️"
    is Action.WaitStable -> "🧘"
    is Action.Loop -> "🔁"
    is Action.If -> "🔀"
    is Action.Retry -> "♻️"
    is Action.Call -> "🧩"
    Action.Stop -> "⏹️"
    is Action.FindImage -> if (a.all) "🧲" else "🖼️"
    is Action.TapFound -> "👉"
    is Action.ReadText -> if (a.numbersOnly) "🔢" else "🔤"
    is Action.SetVar -> "📝"
    is Action.Calc -> "➕"
    is Action.Global -> "📱"
    is Action.TypeText -> "⌨️"
    is Action.Clipboard -> "📋"
    is Action.OpenApp -> "🚀"
    is Action.Alert -> "🔔"
    is Action.Log -> "🧾"
}

/** Modelo pronto: título, explicação e o macro. */
data class Template(val title: String, val desc: String, val macro: Macro)

fun macroTemplates(w: Int, h: Int, id: () -> String): List<Template> {
    val cx = w / 2; val cy = h / 2
    fun m(name: String, actions: List<Action>, runtime: Long = 0) =
        Macro(id(), name, actions = actions, maxRuntimeMs = runtime, screenW = w, screenH = h)
    return listOf(
        Template("Toque repetido", "Toca no centro da tela sem parar, com leve variação, por 1 minuto.",
            m("Toque repetido", listOf(Action.Tap(cx, cy, 40, Jitter(6, 30)), Action.Wait(100, 40)), 60_000)),
        Template("Vários alvos", "Toca em três pontos em sequência. Ajuste as posições no editor.",
            m("Vários alvos", listOf(
                Action.Tap(w / 4, cy), Action.Wait(200), Action.Tap(cx, cy), Action.Wait(200), Action.Tap(w * 3 / 4, cy), Action.Wait(500),
            ), 60_000)),
        Template("Rolagem infinita", "Rola a tela para baixo a cada 1,5 s, como num feed.",
            m("Rolagem infinita", listOf(Action.Swipe(cx, h * 3 / 4, cx, h / 4, 400, Jitter(10, 50)), Action.Wait(1500, 300)))),
        Template("Fechar anúncio", "Quando aparecer o botão X (recorte a imagem \"fechar\"), toca nele.",
            m("Fechar anúncio", listOf(
                Action.FindImage("fechar", threshold = 0.85f, scaleTolerant = true, onFound = listOf(Action.TapFound(), Action.Log("anúncio fechado"))),
                Action.Wait(1000),
            ))),
        Template("Tocar quando um texto aparecer", "Lê o topo da tela e toca no centro quando aparecer \"Coletar\".",
            m("Tocar quando um texto aparecer", listOf(
                Action.If(Condition.TextContains(Region(0, 0, w, h / 3), "Coletar"), then = listOf(Action.Tap(cx, cy), Action.Wait(1000))),
                Action.Wait(500),
            ))),
        Template("Contador com OCR", "Lê um número no topo da tela e avisa quando chegar a 500.",
            m("Contador com OCR", listOf(
                Action.ReadText(Region(0, 0, w, h / 8), "ouro", numbersOnly = true),
                Action.If(Condition.Var("ouro", Op.GE, "500"), then = listOf(Action.Alert("Ouro: \${ouro}", 300, true), Action.Stop)),
                Action.Tap(cx, cy), Action.Wait(1000),
            ))),
    )
}
