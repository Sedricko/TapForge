package app.macro17

/**
 * Item que o catálogo "Adicionar Itens" do base insere DIRETO na lista (sem abrir a edição), com os padrões dele
 * (AddMacroItems, all_src ~62488-62800). null = o Macro17 abre a edição (extras do Macro17: Toque, Deslizar, Esperar, Pinça, Achar por texto, Ler número).
 * [w]/[h] = tamanho da tela em px (a área padrão da Detecção de Mudança é um retângulo em tela/3). Todo item devolvido passa em [Step.validate].
 */
fun catalogDefault(type: Type, mono: Boolean, w: Int, h: Int): Step? = when (type) {
    Type.KEY -> Step(type, x1 = 1, delay = 5000)                                  // Voltar, atraso 5 s
    Type.STATUS -> Step(type, ms = 5000, snd = true, text = "Alerta de status")   // som ligado, 5 s
    Type.GOTO -> Step(type, goto = 0)
    Type.RESET, Type.COUNTER -> Step(type, counter = 1)
    Type.CALL, Type.STOP -> Step(type)
    // Reconhecimento de Texto: busca 5 s, ciclo 200 ms (item "Rápido" da lista), Pular desligado (tap = true; padrão do base). O base nasce com texto vazio; o Macro17 exige texto, então entra um marcador.
    Type.TEXT -> Step(type, text = "texto", ms = 5000, cycle = 200, tap = true)
    // Detecção de Mudança de Imagem: diferença 5% (A = 95), tempo 0 (infinito), área [w/3, h/3]–[2w/3, 2h/3]
    Type.CHANGE -> Step(type, w / 3, h / 3, (w / 3) * 2, (h / 3) * 2, ms = 0, thr = 95, cycle = 500)
    // Grupo comum entra vazio (o diálogo "Grupo" define nome e repetições; os membros vêm da lista); o de imagem tem duração 5 s
    Type.GROUP -> if (mono) Step(type, mono = true, gms = 5000, name = "Grupo de Pesquisa de Imagem") else Step(type, name = "Grupo", rep = 1)
    else -> null
}.takeIf { it == null || it.validate(Int.MAX_VALUE) == null }
