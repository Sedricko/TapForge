# Funções do Touch Macro Pro 4.2.7 e a diferença para o Macro17 hoje

## Abertura

Este documento reúne, área por área, o que o app base (Touch Macro Pro 4.2.7, pacote `com.jake.touchmacro.pro`) faz, como faz, e o que o Macro17 (`/home/user/TapForge/macro17`) tem hoje. É a consolidação dos quatro relatórios de análise estática do APK, da medição de paridade anterior (158 itens, refeita aqui contra o código atual), das observações de tela do usuário e dos docs `ANALISE_TOUCHMACRO.md` e `MECANICA_TOUCHMACRO.md`.

**Três tipos de evidência, sempre separados na tabela:**

1. **APK** — análise estática do código do base (relatórios A configurações, B modelo, C painel e gravação, D visão). Citado como `APK A §1.4`, `APK B §6` etc. Marcas dos relatórios mantidas: **[O]** observado, **[I]** inferido, **[?]** não resolvido.
2. **print/vídeo** — prints e gravação de 25 s do app base enviados pelo usuário (`observacoes_telas.md`). É o comportamento real na tela.
3. **código do Macro17** — leitura dos arquivos `macro17/app/src/main/java/app/macro17/*.kt` (formato `Arquivo.kt:linha`).

**Regra de honestidade.** Nada do Macro17 foi testado por mim, nem fora do aparelho do usuário (Pixel 6 Pro, Android 12). Não compilei, não rodei testes, não rodei o app. Cada status vem da leitura do código (e o código foi lido, não os comentários). A marca **[A]** indica o que o usuário comprovou no aparelho: Home, painel flutuante, gravação por marcadores com 4 toques, execução de macro, captura de imagem, OpenCV carregado e relatório de logs. Nenhuma outra linha tem essa marca: sem **[A]**, o status é só leitura de código. O repositório tem testes de unidade (`macro17/app/src/test/...`), que não executei.

**Código em movimento.** Durante esta análise os arquivos `Prefs.kt`, `BackupZip.kt`, `BackupActivity.kt`, `PermissionsActivity.kt`, `AboutActivity.kt`, `OptionsActivity.kt`, `Store.kt`, `MainActivity.kt` e `MacroService.kt` foram criados ou alterados (última alteração vista: 02/11/2026 23:55 UTC). Os status e números de linha abaixo valem para o código nesse estado.

**Como ler as tabelas.** Coluna "No Macro17 hoje": **IGUAL** (o código implementa o comportamento), **PARCIAL** (implementa parte ou de outro jeito), **AUSENTE**, **EXTRA** (existe no Macro17 e não no base; não entra na conta). `#N` é o número do item na medição de paridade anterior (158 itens); `H`, `M`, `E`, `N`, `G` são linhas novas desta rodada, que a medição anterior não tinha. Linhas com "ver #N" ou "não somar" repetem uma função já contada em outra seção, só para a leitura ficar completa.

---

## 1. Home e permissões

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| H1 Cartão de status de permissão | Mostra "Permissão de Acessibilidade Concedida" com ícone verde; sem a permissão, "A permissão de acessibilidade deve ser concedida." O cartão reflete o modo de controle escolhido (no modo ADB sem ele ativo mostra "ADB não ativado" em vermelho). | print Home, Permissões; APK C §5.1 | **IGUAL [A]** `MainActivity.kt:75-87` | Mesmos textos. Extra: dica "Permitir configurações restritas" (`:84`) e toque que abre as configurações de acessibilidade (`:86`). Não reflete modo ADB porque só existe acessibilidade. |
| H2 Cartão da macro atual | Nome da macro atual e "Repetir reprodução:N". | print Home | **IGUAL [A]** `MainActivity.kt:90-103` | 0 repetições aparece como ∞. Extras: "Agendada: HH:mm" (`:99`) e toque no cartão abre o menu da macro (`:101`, `:138-148`). |
| H3 Botão EDITOR DE MACRO | Abre o editor da macro atual. | print Home | **IGUAL** `MainActivity.kt:105` | Sem macro, abre o editor com macro nova. |
| H4 Botão ARQUIVO | Abre o gerenciador de macros: Criar, Copiar para, Renomear, Excluir, Copiar (mesclar), Editar, Exportar; escolher a macro atual. | print Home; APK A §5.1 | **PARCIAL** `MainActivity.kt:113-120` | Só um diálogo com a lista (toque seleciona), "Nova macro" e "Importar…". As demais ações ficam no menu do cartão da macro (`:138-148`): executar, editar, agendar, exportar, excluir. Faltam duplicar, renomear fora do editor e mesclar (#127, #130). |
| H5 Botão INICIAR/PARAR | INICIAR liga o painel flutuante e vira PARAR; o app não vai para segundo plano; o painel aparece no canto superior esquerdo. Não inicia a macro: o Play do painel é que inicia. | print/vídeo Home e Painel | **IGUAL [A]** `MainActivity.kt:109,122-132`; `MacroService.kt:110-122` | Mesmo comportamento observado. Parar fecha o painel e deixa só o orbe. |
| H6 Tela Permissões | Aviso "Por favor, selecione uma permissão." e três cartões: Acessibilidade, ADB, Root. Tocar escolhe o modo de controle (reflete no cartão da Home). | print Permissões; APK C §5.1 | **PARCIAL** `PermissionsActivity.kt:26-82` | Mesmo visual e textos dos cartões. Acessibilidade abre as configurações do sistema; ADB e Root só abrem um texto explicando que não são usados (`:73-78`). Não há escolha de modo: existe um só. |
| Permissão de captura de tela (diálogo "Compartilhar a tela") | O Play/Record passa pelo diálogo do sistema de MediaProjection; o painel e a captura dependem dele. | print Painel; APK C §4.4 | **EXTRA (mecanismo diferente)** `Engine.kt:147-172`, `accessibility_config.xml` (`canTakeScreenshot`) | O Macro17 captura pelo `takeScreenshot` do serviço de acessibilidade: não pede diálogo de compartilhamento. |
| Permissão de sobreposição | O base usa `TYPE_APPLICATION_OVERLAY`. | APK C §1.1 | **EXTRA** `MacroService.kt:276` | O Macro17 usa `TYPE_ACCESSIBILITY_OVERLAY`, que dispensa a permissão "exibir sobre outros apps". |

---

## 2. Menu lateral

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| M1 Home (com cabeçalho em degradê verde, logo e nome) | Item destacado do menu; volta à tela principal. | print Menu lateral | **IGUAL** `Prefs.kt:104-111`, `Base.kt:68` (classe `Drawer`) | Cabeçalho em degradê verde com ícone e "Macro17". |
| M2 Configurações | Abre a tela de configurações globais. | print Menu lateral, Configurações | **IGUAL** (navegação) `Prefs.kt:114` | O conteúdo da tela é contado na seção 16. |
| M3 Sobre o Aplicativo | Tela "Sobre". Conteúdo não analisado nos relatórios [?]. | print Menu lateral | **PARCIAL [?]** `AboutActivity.kt:8-21` | Existe: versão, descrição e licenças de terceiros. Não dá para comparar o conteúdo porque o base não foi analisado nessa tela. |
| Permissões | Ver H6. | print Menu lateral | contado em H6, `Prefs.kt:112` | não somar |
| Backup e Restauração | Ver #132 e #133. | print Menu lateral, Backup e Restauração | contado em #132/#133, `Prefs.kt:113` | não somar |
| Agendamento | Tela com a lista de alarmes (ver #111). | print Menu lateral; APK A §2 | **AUSENTE** no menu | Não há entrada nem tela de agendamento; o agendamento é por macro, no menu do cartão da Home. não somar (contado em #111) |
| Assinaturas, Gerenciar Assinatura | Compras. | print Menu lateral | fora de escopo | ver "Fora de escopo" |
| Monitor e logs | Não existe no base. | código | **EXTRA** `Prefs.kt:115` | Ver "Extras do Macro17". |

---

## 3. Painel flutuante

Base: janela de overlay não focável, translúcida (padrão 30% de transparência), arrastável, com dois grupos de botões por estado (ocioso, reproduzindo, gravando, pausado, aguardando agenda) e segunda linha recolhível (APK C §1). Macro17: um orbe que abre o painel; o painel tem a linha 1 (alça, ▶/■, REC, enquadrar, seta) e a linha 2 (lápis, engrenagem, registro, X) (`MacroService.kt:272-308`).

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #59 Janela flutuante não focável, translúcida, arrastável | `FLAG_NOT_FOCUSABLE`, formato translúcido, `alpha = 1 − transparência/100`; arraste em qualquer botão após 10 dp, clique suprimido após o arraste. | APK C §1.1; print Painel | **IGUAL [A]** `MacroService.kt:272-278,252-270` | Arrasta só pela alça e pelo orbe (limiar `scaledTouchSlop`, clique suprimido `:266`), não por qualquer botão. Janela de acessibilidade em vez de `TYPE_APPLICATION_OVERLAY`. Não limita o arraste à tela. |
| #60 Botão Play | Inicia a macro corrente. | APK C §1.2 | **IGUAL [A]** `MacroService.kt:236-239,131-148` | Sem o desvio pelo diálogo de captura de tela. |
| #61 Botão Stop (age no ACTION_DOWN) | Para a macro no toque, não no clique, para ser rápido com a janela sobreposta. | APK C §1.2 [O] | **PARCIAL** `MacroService.kt:226,242` | O mesmo botão vira ■ durante a execução, mas age no clique. Extra: durante a gravação, ■ salva e segurar descarta (`:212-214`). |
| #62 Botão Pause | Estado 5; o laço respeita a pausa entre fatias de espera. | APK C §1.2 | **AUSENTE** `MacroService.kt:208-234` | Não há pausa no executor nem no painel. |
| #63 Botão Resume | Espera 500 ms e retoma o estado salvo. | APK C §1.2 | **AUSENTE** | idem |
| #64 Botões Anterior / Próximo (pausado) | Movem o ponteiro de item respeitando saltos; atualizam o texto. | APK C §1.2 | **AUSENTE** | idem |
| #65 Botão Record | Inicia a gravação (pede captura se não houver). | APK C §1.2; print Painel | **IGUAL [A]** `MacroService.kt:200-206,230` | Disco "REC" que chama `startRecording` (`:409`). |
| #66 Botão Adicionar (+) durante a gravação | Esconde os marcadores, espera 500 ms, monta o item e **executa a ação no ponto** para o app avançar; reabre os marcadores. | APK C §3.5 [O]; print Gravação | **PARCIAL [A]** `MacroService.kt:215,422-431` | O + acrescenta o item na posição dos marcadores. Não esconde os marcadores, não espera 500 ms e não executa a ação no ponto: o app de baixo só avança se o usuário tocar nele. |
| #67 Botão Alternar tipo de alvo | Gira Tap, Press, Swipe, Gesture, Image (na API < 26 pula Gesture); troca o ícone e reconfigura os marcadores. | APK C §1.2; print Gravação | **PARCIAL** `MacroService.kt:216-224`, `Recorder.kt:21-24` | Três tipos: Tocar, Deslizar, Imagem. Faltam Pressionar e Gesto. Rótulo do tipo igual ao do print. |
| #68 Botão Visibilidade (lista flutuante da macro) | Liga a lista flutuante dos itens da macro na borda direita. | APK C §1.2, §1.4; print Painel (olho riscado) | **AUSENTE** | Na linha 2 do Macro17 o ícone de informação abre o registro ao vivo (EXTRA), não a lista da macro. |
| #69 Botão Gravar tela (vídeo) | Contagem regressiva, grava com áudio/bitrate/FPS das configurações; salva na galeria. | APK C §4.7 | **AUSENTE** | O ícone de enquadrar do print abre "Compartilhar a tela" e, pelo comando de permissão, é provavelmente este botão **[I]** (o olho é outro). No Macro17 o enquadrar captura um recorte de imagem (ver Extras). |
| #70 Botão Editar macro | Abre o editor da macro atual (bloqueado durante a gravação). | APK C §1.2; print Painel (lápis) | **IGUAL** `MacroService.kt:285-287` | Durante a gravação a linha 2 fica inacessível. |
| #71 Configurações do painel (macro, repetição, tamanho) | Engrenagem abre uma janela com escolha da macro, repetições, tamanho da imagem e monitoramento contínuo. | APK A §1.2; print Painel | **PARCIAL** `MacroService.kt:288` | A engrenagem abre as Configurações globais. Não escolhe macro nem repetições pelo painel. |
| #72 Fechar (X) e expandir a segunda linha | A seta alterna a linha 2 (agendar, editar, configurações, fechar). O X envia `service_stop` e encerra tudo. | APK C §1.1, §1.2; print Painel | **PARCIAL** `MacroService.kt:232,290`, `:244-249` | A seta expande (`:232`). O X só recolhe o painel para o orbe: o serviço e o orbe continuam. A linha 2 é lápis, engrenagem, registro, X (sem agendar). |
| #73 Escolher macro atual pelo painel | Pela janela de configurações do painel. | APK A §1.2 | **AUSENTE** `MacroService.kt:238` | A macro vem de `store.selected`; só se troca na Home/Arquivo. |
| #74 Estados do painel e botões por estado | 1 ocioso, 2 reproduzindo (só Stop/Pause), 3 gravando (Stop, Adicionar, Tipo), 5 pausado, 6 agendado. | APK C §1.2 [O] | **PARCIAL** `MacroService.kt:208-234` | Ocioso, executando (só troca ▶/■, o REC continua visível) e gravando. Sem pausado nem agendado. Modos "Visualização Simples" e "Modo de Reprodução" removem botões no ocioso (`:230-232`). |
| #75 Textos do painel (passo atual, progresso, ciclo, próximo agendamento) | Atualizados a cada 1 s: nome do passo, tempo restante ou %, ciclo. | APK C §1.1, §2.3 | **AUSENTE** | O painel não mostra o passo; o acompanhamento é pelo registro ao vivo. |
| #76 Lista flutuante da macro | Lista dos itens na borda direita, com edição por toque e "testar item" por toque longo. | APK C §1.4 | **AUSENTE** | |
| #77 Mostrar toques | Círculos em overlay não tocável, até 4 simultâneos, animados nos gestos ≥ 500 ms. | APK C §4.6 [O] | **AUSENTE** | A opção aparece em Configurações desabilitada, "(indisponível)" (`OptionsActivity.kt:34`). |
| #78 Mensagem instantânea em overlay | Substitui o toast (% do teste de item, avisos). | APK C §4.9 | **AUSENTE** `MacroService.kt:128` | Usa `Toast`. |
| #79 Notificação de foreground com ação Parar | Notificação id 1000 com `notification.action.STOP`. | APK A §1.7 | **AUSENTE** `AndroidManifest.xml` | Sem serviço em primeiro plano; o serviço de acessibilidade já é persistente. |
| #80 Painel volta após reinício; mostra o motivo da morte | Intent nulo recria o painel e explica por `getHistoricalProcessExitReasons`. | APK C §2.1 | **PARCIAL** `MacroService.kt:55-66`, `Diag.kt:111` | O painel volta ao reconectar o serviço. O motivo da morte só aparece no relatório de diagnóstico, sem diálogo. |

---

## 4. Gravação

Base (modo acessibilidade): gravação **por marcadores**, porque o Android não deixa o serviço observar os toques reais em outros apps (APK C §3.1). O usuário arrasta o alvo, escolhe o tipo e aperta Adicionar; ao encerrar, pergunta se quer ir ao editor. Macro17: o mesmo desenho, com Tocar, Deslizar e Imagem (`Recorder.kt`, `MacroService.kt:409-485`).

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #82 Gravar Tap/Swipe e salvar como itens | Cada Adicionar vira um item (Tap 5 ms, Press 1000 ms, Swipe 1000 ms) anexado à macro corrente. | APK C §3.5; print Gravação | **PARCIAL [A]** `MacroService.kt:409-475`, `Recorder.kt:142-148` | Grava Tocar (50 ms) e Deslizar (400 ms) com atraso fixo de 1000 ms, sempre numa macro nova. Sem Pressionar. |
| #83 Marcador de alvo arrastável (ponto, par com seta e linha, retângulo) | Tap/Press: 1 marcador redondo; Swipe: inicial + final com seta que gira + linha tracejada; Image: retângulo com alça. Arraste limitado à tela. | APK C §1.3; print Gravação | **IGUAL [A]** `Recorder.kt:92-133` | Tocar (marcador com mira), Deslizar (marcador inicial, marcador final com seta, linha `:99-103`) e Imagem (caixa com alça de redimensionar). A seta não gira (ver G2). |
| #84 Atraso gravado = intervalo desde a ação anterior | `atraso = tempo desde o último Add − 5 ms`, 1 casa decimal; o cronômetro reinicia a cada Add. | APK C §3.5 [O] | **AUSENTE** `MacroService.kt:37,426` | **Regressão** em relação à medição anterior (quando a gravação era por toque real). Hoje é fixo em 1000 ms (`DEFAULT_DELAY`). |
| #85 Gravar Gesto (traço multi-dedo) | Janela de tela cheia desenha o traço e serializa `MotionEvent`s em `.raw`; reproduzido como traços encadeados. | APK C §3.4; print Gravação | **AUSENTE** | Sem modo Gesto e sem tipo de item para ele. |
| #86 Gravar item Imagem (recorte, só compara) | Caixa de área; salva o recorte; item tipo 6 com `skip_action`, busca 5 s, ciclo 1000 ms. | APK C §3.5, APK D §2 | **PARCIAL** `MacroService.kt:433-458`; `:316-...` | Recorte da caixa vira passo IMAGE (semelhança 95, 5000 ms, atraso 1000). O passo grava com `tap=true` (toca); no base só compara. Também há a captura pelo enquadrar (Extras). |
| #87 Recorte centrado no toque, 5..160 px, só se cabe | Em todo Tap gravado salva um quadrado do tamanho configurado. | APK D §2 | **AUSENTE** | O Tocar não salva recorte. (`Capture.kt:51`, `MIN=16`, é do seletor manual.) |
| #88 Miniatura do ponto em cada toque gravado | Aparece na lista do editor. | APK B §2 (`f0`); print Editor | **AUSENTE** | |
| #89 Captura de tela inteira por item (para o editor) | `screen_capture/<macro>/` usada pelos seletores de ponto e área. | APK B §3 | **AUSENTE** `Store.kt:14-15` | Só o recorte é guardado. |
| #90 Anexar à macro existente ao gravar | Se a macro tem itens, anexa ao fim. | APK C §3.5 | **AUSENTE** `MacroService.kt:468` | Sempre cria "Gravação dd/MM HH:mm:ss". |
| #91 Diálogo "ir para o editor" ao parar / instrução inicial | Ao parar: "Gravação de macro encerrada. Ir para o Editor de Macro?" (CANCELAR/OK). Na 1ª vez, diálogo de instrução com "não mostrar de novo". | APK C §2.3; print Gravação | **PARCIAL** `MacroService.kt:477-485` | O diálogo final tem o texto do print. A instrução inicial é só um toast. |
| #92 Duração padrão por tipo (Tap 5, Press/Swipe 1000) | Fixas na gravação. | APK C §3.5 | **PARCIAL** `Recorder.kt:144-148` | Tocar 50 ms e Deslizar 400 ms. |
| G1 Rotação: recentrar marcadores ao girar o aparelho | Refaz marcadores, linha e retângulo no novo tamanho; guarda a rotação em cada item. | APK C §1.3, §2.5 | **AUSENTE** `Recorder.kt:30` | O tamanho da tela é fixado na criação do gravador. |
| G2 Seta final que gira apontando a direção | `atan2` entre os marcadores, em tempo real. | APK C §1.3 | **AUSENTE** `Recorder.kt:101` | `Ic.ARROW` fixo. |
| G3 Marcadores limitados à tela | Limites mínimos/máximos por marcador. | APK C §1.3 | **IGUAL** `Recorder.kt:61-62` | |
| G4 Painel durante a gravação: ■, lista com +, seletor de tipo com rótulo | Stop, Adicionar, Alternar alvo (com rótulo "Tocar", "Deslizar", "Gesto", "Imagem"). | print Painel (gravação) | **IGUAL [A]** `MacroService.kt:211-224` | Rótulos Tocar, Deslizar, Imagem. |

---

## 5. Tipos de item

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #1 Toque (p0) | Trilha de um ponto por `dispatchGesture`, duração `n` ms. | APK B §1; MECANICA §3 | **IGUAL [A]** `Runner.kt:91,223-241`, `Engine.kt:72-93` | Toque duplo é outro campo (#27). |
| #2 Deslizar (p1) | `(i,j)→(k,l)` em `n` ms. | APK B §1 | **IGUAL** `Runner.kt:91,223-241` | |
| #3 Pressionar (p2) | Toque longo; ao ler força `(k,l)=(i,j)`; gravado com 1000 ms. | APK B §1 | **IGUAL** `Model.kt:26` (TAP com `ms` = tempo pressionado) | Sem tipo próprio; o toque longo é um Toque com duração maior (até 60 s). O gravador não tem modo Pressionar (#67). |
| #4 Gesto gravado `.raw` (p3) | Reproduz traços multidedo de um arquivo, com velocidade `I`. | APK B §1, APK C §3.4 | **AUSENTE** `Model.kt:7` | |
| #5 Pinça para dentro (p4) | Dois dedos, das pontas ao centro. Nenhum modo de gravação cria (origem [?]). | APK B §1, §9; MECANICA §3 | **IGUAL** `Runner.kt:223-241` | O editor cria o tipo (`EditorActivity.kt:338`). |
| #6 Pinça para fora (p5) | Do centro às pontas. | idem | **IGUAL** `Runner.kt:223-241` | |
| #7 Item Imagem (p6) | Toque com busca de imagem ligada (`q=1`); gravado com `skip_action=1`, só compara. | APK B §1, APK D §2 | **IGUAL** `Model.kt:37-41`, `Runner.kt:98-104` | O passo IMAGE gravado toca no centro (`tap=true`); o do base só compara. Semelhança gravada 95 (base: 100). A tela de edição tem "Tocar no centro" no lugar de "pular ação". |
| #8 Ir para (p10) | Segue `z`. | APK B §1 | **IGUAL** `Runner.kt:95`, `Model.kt:65` | |
| #9 Contador de execução (p11) | Até 10 casos `limiar:alvo` e alvo padrão; contador por executor, chaveado pelo ID do item. | APK B §1, §4 | **PARCIAL** `Runner.kt:215-221`, `Model.kt:13` | 5 casos (base 10). Contador escolhido por número 1..10, não pelo item. A regra de zerar ao atingir o maior limiar (`Runner.kt:217-218`) segue a leitura do MECANICA; a ordem dos casos no base é **[?]** (APK B §9). |
| #10 Parar macro (p12) | O executor devolve -7 e o laço encerra. | APK B §1 | **AUSENTE** `Model.kt:7` | |
| #11 Arquivo de macro (p13) | Chama outro arquivo: `N=0` empilha e volta; `N=1` substitui. Pilha de 10, descarta o mais antigo; toque longo abre o alvo. | APK B §1, §4.10 | **PARCIAL** `Runner.kt:130-142,243` | Pilha de 8; estouro vira erro (`:135`), não descarte. Sem toque longo para abrir o alvo. Chama por id interno da macro. |
| #12 Alerta de status (p14) | Bitmask som (1), vibração (2), pop-up (4); duração, som escolhido, texto. | APK B §1, §2 (cols 31, 43, 45); APK A §1.3 | **PARCIAL** `Engine.kt:97`, `Model.kt:35` | Só mensagem (toast) e espera. Sem som, vibração, pop-up nem toque escolhido. |
| #13 Tecla/navegação (p15) | 4 voltar, 3 início, 187 recentes, 120 captura de tela; ação global. | APK B §1, APK C §4.8 | **PARCIAL** `Model.kt:61`, `Engine.kt:95` | Voltar, início, recentes e notificações. Falta captura de tela (120). |
| #14 Zerar contador (p18) | Zera o contador do item indicado. | APK B §1 | **IGUAL** `Runner.kt:96` | Contador por número. |
| Esperar (passo `WAIT`) | Não existe como tipo no base (o atraso é campo de cada item). | código | **EXTRA** `Runner.kt:93` | |
| Achar por texto de elemento (`NODE`) | Não existe no base. | código | **EXTRA** `Engine.kt:119-138` | Ver Extras. |
| Ler número (`NUMBER`) | Não existe como tipo no base (o base compara número pelo OCR do texto). | código | **EXTRA** `Runner.kt:105-116` | Ver seção 9 e Extras. |

---

## 6. Campos do item

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #15 Nome do item (Title) | Texto livre; padrão "Tap", "Image" etc.; aparece como "N. nome". | APK B §2 (col 0); print Editor | **AUSENTE** `Model.kt:23-53` | A lista mostra "N. Tipo" (`EditorActivity.kt:275-310`). |
| #16 Ponto inicial X/Y | `(i,j)` em px. | APK B §2 | **IGUAL** `Model.kt:25` | |
| #17 Ponto final X/Y | `(k,l)` (deslizar, pinça). | APK B §2 | **IGUAL** `Model.kt:25` | |
| #18 Duração do gesto (ms) | `n`; o laço dorme `n` após a ação. | APK B §2 | **IGUAL** `Model.kt:26`, `Runner.kt:91` | |
| #19 Atraso antes da ação (liga/desliga, 0..300 s) | `m` s, usado se `r`; fatiado em 100 ms. | APK B §2 (cols 3, 11); MECANICA §4 | **PARCIAL** `Runner.kt:84-87`, `Model.kt:10,27` | Máximo de 60 s (base 300 s). Sem chave liga/desliga (0 = desligado). Sempre espera ao menos 1 ms. Roda antes da busca (ver #145). |
| #20 Atraso aleatório min~max que substitui o fixo | `U`; se ligado, ignora `m`. | APK B §2 (cols 21, 37) | **IGUAL** `Runner.kt:212`, `Model.kt:28-29` | |
| #21 Jitter de pixel (um deslocamento por gesto) | Um `(dx,dy)` em `[-D,D)` somado aos dois pontos. | MECANICA §3.1; APK B §2 | **IGUAL** `Runner.kt:223-232`, `Model.kt:30` | Limite 0..500 px. |
| #22 Pular ação (`skip_action`) | Não executa o gesto; só espera/compara. | APK B §2 (col 20) | **IGUAL** `Runner.kt:91-94,102,120,127,131` | Nos passos de busca o equivalente é desligar "Tocar no centro". |
| #23 Item desabilitado (`w`) | Não executa, é pulado, inclusive como alvo de salto; grupo desabilitado desabilita filhos. | APK B §2 (col 15), §4 | **AUSENTE** `Model.kt:31` | O `skip` do Macro17 é o `skip_action`. Atenção: o "Alternar Pular" do editor (`EditorActivity.kt:222-226`) inverte esse `skip`, enquanto o "Alternar Pular" do base inverte o `w`. Mesmo nome, efeito diferente. A linha esmaecida (`:286`) imita o aspecto, mas o passo ainda roda o atraso. |
| #24 Saltos por ID estável | Alvos são IDs com bit de marca; mover/duplicar/excluir não renumera. | APK B §0.5, §3 | **PARCIAL** `Model.kt:144-172`, `EditorActivity.kt:194,202,209` | Alvos são números de passo 1-based, renumerados a cada operação da lista. Funciona na lista plana; não há ID. |
| #25 Alvos de sucesso/falha (`z`/`y`) | 0 = próximo. | APK B §2; APK D §7 | **IGUAL** `Model.kt:32,40`, `Runner.kt:104,115,121,128` | Só nos passos de busca. |
| #26 Rotação em que o item foi gravado (`o`) | Guarda `Display.getRotation`. Uso no replay não lido [I]. | APK B §2 (col 4) | **AUSENTE** `Runner.kt:223-226` | Usa o tamanho atual da tela. |
| #27 Toque duplo (`e0`) | Dois toques (API ≥ 26). | APK B §2 (col 46) | **AUSENTE** | |
| #28 Condição de imagem em qualquer toque/deslizar/pressionar | `q` liga a busca antes do gesto. | APK B §2 (col 11) | **PARCIAL** `Runner.kt:98-104` | Só o passo IMAGE, que toca no centro. |
| #29 Deslocamento de coordenada em relação ao achado (`Y`) | `(dx,dy)` somado ao centro achado, depois do jitter. | APK D §7 [O] | **AUSENTE** `Runner.kt:102` | Toca no centro exato. |
| #30 Velocidade do gesto / pular atraso do gesto (`I`,`J`) | Só para o tipo Gesto. | APK B §2 (cols 26, 27) | **AUSENTE** | Depende de #4. |
| #31 Repetição da macro inteira (`Replay`, 0 = infinito) | Fim da lista conta uma volta. | APK B §3, §6 | **IGUAL** `Runner.kt:205-208`, `Model.kt:120`, `EditorActivity.kt:106-109` | |

---

## 7. Execução e controle de fluxo

Base: laço `M2/M.run` com fluxo "mover → busca → atraso → ação → espera da duração → escolher próximo" (APK D §7, MECANICA §4). O fluxo do Macro17 é `Runner.kt:74-146`: atraso, ação/busca, salto.

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #37 Alvo órfão zerado / alvo inválido na execução | Ao salvar, alvos que não existem mais viram 0; alvo inválido na execução encerra com erro (-1). | APK B §3 | **IGUAL** `Model.kt:65,147-150` | Zera na hora da exclusão e recusa salvar alvo inválido. |
| #38 Contadores por executor, sem zerar entre rodadas | Zeram só ao carregar o executor. | APK B §4.10 [I] | **IGUAL** `Runner.kt:56-60,205-208` | |
| #81 Fim da macro avisa o motivo | Códigos tipados (encerrada, parada, sem permissão, salto falhou...); diálogo no modo debug. | APK C §2.3 | **IGUAL** `Engine.kt:45-56`, `MacroService.kt:60-63` | Texto: "Macro concluída", "Macro parada", "Erro: motivo". Sem códigos tipados nem diálogo. |
| E1 Log de cada salto "origem → destino" | Registrado a cada salto. | MECANICA §4 | **IGUAL** `Runner.kt:143`, `Engine.kt:213-218` | Na tela no máximo um por segundo; no registro, todos. |
| E2 Parada interrompe entre fatias e itens | A interrupção encerra com "parado pelo usuário"; um gesto já despachado termina. | MECANICA §4 | **IGUAL** `Engine.kt:43`, `Runner.kt:63-72,79` | |
| Pausa e retomada, Anterior/Próximo | Ver #62 a #64. | APK C §1.2 | contado em #62-#64 | não somar |
| Parar macro (tipo 12) | Ver #10. | APK B §1 | contado em #10 | não somar |
| Item desabilitado, grupos que repetem | Ver #23 e #32 a #34. | APK B §4 | contado em #23, #32-#34 | não somar |
| Atraso mínimo de 1 ms por passo | Não há no base. | código | **EXTRA** `Runner.kt:84-87` | Evita laço quente com macro infinita sem espera. |
| Parar ao bloquear a tela | O base **não** implementa: o filtro registra SCREEN_OFF mas nenhum ramo age (APK C §2.2 [O]; APK A §1.5 [?]). | APK A, C | **EXTRA** `MacroService.kt:67-72`, `Store.kt:47` | Opção "Parar ao bloquear a tela" (ligada por padrão). |
| Falha de gesto ou captura vira motivo de parada | Base: código -4 (gesto rejeitado). | MECANICA §3.5 | **EXTRA** `Engine.kt:89-92`, `Runner.kt:63-72` | Mensagens: gesto rejeitado, cancelado, captura recusada, OCR sem resposta. |

---

## 8. Busca de imagem

Base: OpenCV `matchTemplate` com `TM_CCOEFF_NORMED` (constante 5), sobre tons de cinza (`RGBA2GRAY`), sem multi-escala; limiar efetivo `max(A,20)` se `A ≤ 99`, senão 99; sucesso **estritamente maior**; posição = canto + metade do modelo (APK D §1, §2). Macro17: o mesmo método via OpenCV (`Cv.kt:29`), com reserva em Kotlin.

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #135 Captura de tela sob demanda | MediaProjection + `ImageReader`, espera até 66 ms por quadro novo, cache de 200 ms (API 30+). Falha sem cache = tentativa perdida. | APK D §3 | **PARCIAL** `Engine.kt:141-172` | `takeScreenshot` do serviço de acessibilidade, sem cache e sem diálogo de projeção. O erro "intervalo mínimo" devolve nulo e a tentativa se perde (`:163-166`). O painel de controle só some da captura com "Ocultar janela" ligada; o painel de registro some sempre (`:142-144`). |
| #136 Casamento `TM_CCOEFF_NORMED`, limiar `max(A,20)`/99, estritamente maior | `Imgproc.b` com constante 5; pontuação = `maxVal × 100`. | APK D §0, §1 [O] | **IGUAL [A: OpenCV carregado]** `Cv.kt:29,33-34`, `Vision.kt:195`, `Runner.kt:162` | O usuário comprovou o carregamento do OpenCV, não o acerto em campo. Se a biblioteca não carregar, ou o modelo for liso, usa o buscador em Kotlin (`Vision.kt:298`), grosso e fino, que pode perder acerto pequeno. A conversão para cinza usa pesos inteiros 77/151/28 (`Vision.kt:65-67`), aproximação do `RGBA2GRAY`. |
| #137 Centro do acerto = canto + metade do modelo | `maxLoc + (w/2, h/2)`. | APK D §1 [O] | **IGUAL** `Cv.kt:34` | |
| #138 Área: tela cheia em cinza (L2) | Mat do quadro inteiro. | APK D §7, §8 | **IGUAL** `Runner.kt:164-167` | |
| #139 Área: custom em cinza (L4) | Recorte `T`, exige recorte estritamente maior que o modelo. | APK D §8 | **IGUAL** `Runner.kt:165-168`, `Cv.kt:24` | Aceita recorte do mesmo tamanho do modelo. |
| #140 Área: tela cheia em cor (L1) | Correlação em RGBA. | APK D §7 | **PARCIAL** `Vision.kt:212-236` | O método 0 só compara em cinza. A cor existe no método 1 (diferença de pixels, outra métrica). |
| #141 Área: custom em cor (L3) | idem | idem | **PARCIAL** `Runner.kt:164-168` | idem |
| #142 Posição fixa por RMSE no ponto (L0) | Recorta o tamanho do modelo em `(i,j)` e compara pixel a pixel; não procura. | APK D §8 | **PARCIAL** `Runner.kt:164-168`, `Model.kt:77` | Não há modo "posição fixa". Dá para imitar digitando a área com o tamanho do modelo e usando o método 1, que não é RMSE. |
| #143 Ciclo de busca 0/200/1000/2000/3000 ms | Fatias de 10 ms; 0 = o mais rápido. | APK D §7 | **PARCIAL** `Model.kt:39,76`, `Runner.kt:195-201` | Qualquer valor de 50 ms a 60 s (padrão 200). Não existe 0. |
| #144 Duração da busca (0 = infinita) e falha por tempo | Verificada após cada comparação. | APK D §7 | **PARCIAL** `Model.kt:76`, `Runner.kt:195-201` | Mínimo de 100 ms; não há busca infinita. |
| #145 Atraso só após sucesso; falha pula atraso e ação | Busca → atraso → ação; falha vai direto ao destino de falha. | APK D §7; MECANICA §4 | **PARCIAL** `Runner.kt:84-87,102-104` | O atraso roda antes da busca, sempre. A ação só ocorre no sucesso. |
| #154 Teste de similaridade do item (% em overlay) | Toque longo na lista flutuante compara depois de 500 ms e mostra "87%". | APK C §1.4 | **AUSENTE** | Ao falhar, o registro diz "Melhor semelhança N%" (`Runner.kt:178-182`); não é o teste interativo. |
| Método 1 (diferença de pixels), cinza, confirmações em quadros seguidos, escalas | Não existem no base (sem multi-escala, APK D §1). | código | **EXTRA** `Model.kt:44-53`, `Runner.kt:157-193`, `Vision.kt:287` | Ver Extras. |
| Aviso de busca sem sucesso com a melhor semelhança | Não existe no base. | código | **EXTRA** `Runner.kt:178-182` | |

---

## 9. OCR e texto

Base: ML Kit Text Recognition v2 embutido; alvos separados por vírgula (ou `|`), sem `trim`; palavra x linha conforme o alvo tem espaço; igual vence contém; limiar de confiança 0..90; 38 idiomas em 5 reconhecedores; comparação numérica por faixa (APK D §6).

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #150 Texto (p17) com OCR; tocar no centro | Espera até 10 s pelo reconhecedor; toca no centro da caixa (palavra ou linha). | APK D §6 | **PARCIAL** `Engine.kt:187-208`, `Runner.kt:123-129` | ML Kit latino; alvos por vírgula ou `|` **com** `trim` (o base não faz). Palavras são testadas antes das linhas. "Igual" é sensível a maiúsculas; "contém" ignora maiúsculas (o base é sensível). Não há a regra "igual sobrescreve contém" nem a escolha palavra x linha pelo espaço. Mesmo limite de 10 s (`:199`). |
| #151 Idiomas por script (38 códigos) | Latino, devanágari, japonês, coreano, chinês. | APK D §6 | **AUSENTE** `Engine.kt:180` | Só `TextRecognizerOptions.DEFAULT_OPTIONS` (latino). |
| #152 Limiar de confiança 0..90 | Filtra símbolos por confiança. | APK D §6 | **AUSENTE** | |
| #153 Faixa numérica por OCR [mín, máx] | `V=2`: números por regex em cada palavra, vírgula = milhar, ponto = decimal, faixa inclusiva. | APK D §6 | **PARCIAL** `Runner.kt:105-116,187-193`, `Digits.kt:100` | Passo "Ler número" usa um leitor de dígitos que o usuário treina (Opções › Leitor de números), não o ML Kit. Condições ≥, ≤, =, entre; só inteiros; área obrigatória. |
| Achar por texto de elemento (`NODE`) | Não existe no base. | código | **EXTRA** `Engine.kt:119-138` | Varre a árvore de acessibilidade da janela ativa; aceita `id:nome`. |

---

## 10. Mudança de imagem e grupo de imagens

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #146 Mudança de imagem (p19) | Base = 1ª captura da execução do item (não rolante); RMSE por canal com divisão inteira; sucesso se diferença > D% (padrão 5); sentinelas 254/255 para sem base/erro. | APK D §4 [O] | **AUSENTE** `Model.kt:7` | Sem tipo. |
| #147 Grupo de busca de imagens (grupo + filho p16) | Pai com duração (5 s) e destino ao expirar; 1º filho captura um quadro; filhos comparam nele. | APK D §5; APK B §5 | **AUSENTE** | |
| #148 Quadro único compartilhado entre filhos, cache ligado | Filhos com duração (0,1) fazem uma tentativa só, com cache de 200 ms. | APK D §5, §7 | **AUSENTE** | |
| #149 Duração do grupo e destino ao expirar | `x` e `y` do cabeçalho do grupo; repetição infinita do grupo. | APK D §5 [?] retomada do relógio | **AUSENTE** | |

---

## 11. Editor e lista

Base: `MacroListActivity` com barra verde, menu ⋮, barra de ícones (selecionar tudo, subir, descer, duplicar, excluir, salvar), nome e repetições, linhas com checkbox, miniatura, "N. nome", dois valores e seta de salto, FAB rosa (APK B §6; print Editor). Macro17: reescrito nesse visual (`EditorActivity.kt:93-167`).

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #42 Seleção múltipla / selecionar tudo | Caixa por linha; o botão alterna todos/nenhum. | APK B §6; print Editor | **IGUAL** `EditorActivity.kt:183-186,287-290` | |
| #43 Subir/descer em bloco | Bloco contíguo sobe junto; se o 1º já está no topo, **para tudo**. Sem renumerar. | APK B §6 [O] | **PARCIAL** `EditorActivity.kt:189-198`, `Model.kt:153` | O bloco encostado na borda fica parado, mas selecionados soltos mais adiante ainda se movem (o base para tudo). Os saltos são renumerados. |
| #44 Duplicar com remapeamento de saltos internos (até 500) | Cópia profunda, novos IDs, saltos das cópias para itens copiados são remapeados; cópias entram após o último selecionado. | APK B §6 [O] | **PARCIAL** `EditorActivity.kt:200-204`, `Model.kt:172` | Cada cópia entra logo depois do seu original. Os saltos das cópias acompanham a renumeração mas não são remapeados para as cópias. Limite 5000. |
| #45 Excluir selecionados | Sem confirmação; saltos de outros itens ficam pendentes até salvar. | APK B §6 | **IGUAL** `EditorActivity.kt:206-211`, `Model.kt:147-150` | Pede confirmação (extra); saltos viram 0 na hora. |
| #46 Salvar com backup datado (10 versões) | Cópia do `.tmc`, `image/` e `screen_capture/` em `filesDir/<macro>/<data>/`. | APK B §6 | **AUSENTE** `Store.kt:153-158` | Só grava de forma atômica (arquivo temporário e renomeia). |
| #47 Histórico de edição / restaurar versão | "Select Edit History" restaura um backup. | APK B §6 | **AUSENTE** | Ver "Recarregar" (N7). |
| #48 Confirmar ao sair sem salvar | "Do you want to exit editing?" e descarta. | APK B §6 | **AUSENTE** `EditorActivity.kt:160` | Voltar chama `finish()` sem aviso e descarta. |
| #51 Editor por tipo de item | Fragmento por tipo (gesto, ir para, contador, arquivo, alerta, tecla, OCR, zerar, mudança de imagem, grupo, toque). | APK B §6 | **IGUAL** `EditorActivity.kt:56-72,377-449` | Um diálogo com campos por tipo, todos digitados. |
| #52 Catálogo "adicionar item" com regras por contexto | Grupo, Grupo de busca, Ir para, Contador, Parar, Arquivo, Alerta, Tecla, Texto, Mudança de imagem, Zerar; some o que não cabe no contexto. | APK B §5 | **PARCIAL** `EditorActivity.kt:338-361` | Oferece Toque, Deslizar, Pinça, Esperar, Tecla, Alerta, Ir para, Contador, Zerar, Chamar macro, Achar por texto, Ler texto, Ler número. IMAGE só nasce da captura (`:340`). Sem Grupo, Grupo de busca, Mudança de imagem, Parar macro. Sem regras por contexto (não há grupos). |
| #53 Cabeçalho: repetição do replay (0 = infinito) | Campo `Replay`. | APK B §3, §6 | **IGUAL** `EditorActivity.kt:106-109,458-462` | |
| #54 Seletor visual de ponto sobre a captura | Tela cheia com a captura do item e marcador arrastável. | APK C §4.1 | **AUSENTE** `EditorActivity.kt:56-60` | Coordenadas em px digitadas. |
| #55 Seletor visual de área | `CustomAreaSelectionActivity`: retângulo sobre a captura. | APK C §4.2 | **PARCIAL** `MacroService.kt:342-...`, `Capture.kt:12` | A área é escolhida só na captura de imagem; ao editar, é digitada. |
| #56 Seletor de desvio de coordenada | Dois marcadores sobre a imagem. | APK C §4.3 | **AUSENTE** | |
| #57 Testar item (toque longo, mostra %) | Compara depois de 500 ms e mostra a similaridade. | APK C §1.4 | **AUSENTE** | |
| #58 Editar item em overlay sobre o app | Diálogos de overlay (4 variantes). | APK C §1.4 | **AUSENTE** | |
| N1 Barra de ícones | Selecionar tudo, subir, descer, duplicar, excluir, salvar. | print Editor | **IGUAL** `EditorActivity.kt:131-139` | Salvar fecha a tela; no base fica nela (APK B §6). |
| N2 Cabeçalho com nome e contador de repetições | Nome da macro e "⟲ N". | print Editor | **IGUAL** `EditorActivity.kt:140-147` | |
| N3 Linha da lista | Checkbox, miniatura, "N. nome", dois valores com ícone (duração da busca e atraso), seta de salto com o número (vermelha em toque, azul em imagem). | print Editor | **PARCIAL** `EditorActivity.kt:275-310` | "N. Tipo" (sem nome do item). Os dois valores são a duração do passo e o atraso. Cores da seta como no base (`:281`). Destino além do fim aparece como "fim". Linha esmaecida quando `skip`. |
| N4 Miniaturas do alvo (Tap e Image) | Miniatura do ponto em cada toque gravado. | print Editor; APK B §2 | **PARCIAL** `EditorActivity.kt:277`, `Store.kt:64` | Só o passo IMAGE tem miniatura; os demais mostram um ícone do tipo. |
| N5 Botão + flutuante rosa | Abre o catálogo. | print Editor | **IGUAL** `EditorActivity.kt:148-153` | Catálogo em #52. |
| N7 Menu ⋮ > Recarregar | Aparece no print. A mecânica não foi analisada nos relatórios **[?]**. | print Editor | **PARCIAL [?]** `EditorActivity.kt:256-263` | Descarta as alterações e relê a macro salva, com confirmação. O menu ⋮ do Macro17 tem Alternar Pular, Editar Itens Selecionados e Recarregar (`:213-220`); falta "Grupo de Macro" (#35). |
| Toque longo na linha | No base, em item tipo 13 abre o arquivo alvo. | APK B §6 | **EXTRA** `EditorActivity.kt:363-374` | No Macro17 abre um menu: duplicar, mover, excluir. |

---

## 12. Edição em lote

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #49 Alternar skip (desabilitar) em lote | Inverte `w` de cada selecionado; grupo desabilitado desabilita os filhos. | APK B §6 | **PARCIAL** `EditorActivity.kt:222-226` | Inverte o `skip` (pular ação), não desabilita o item (ver #23). |
| #50 Edição em lote (8 grupos de campos) | Uma opção por vez, aplicada a todos os selecionados: atraso; pesquisa de imagem liga/desliga; área; taxa; velocidade; duração; mover (sucesso/falha); ação (pular). Só itens de toque, deslizar, pressionar, pinça, imagem. Mostra "N items changed". Botões ALTERAR TUDO e CANCELAR. | APK B §6; print Editor (Editar Itens Selecionados) | **PARCIAL** `EditorActivity.kt:228-253` | Quatro opções: Tempo de atraso (ms), Duração (ms), Pular ligar, Pular desligar. Cobre atraso, duração e ação; faltam pesquisa de imagem, área, taxa, velocidade e mover. Vale para qualquer tipo selecionado (o base filtra) e não informa quantos mudaram. Botão "Alterar tudo" como no print. |

---

## 13. Grupos

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #32 Grupo comum com N voltas | Grupo de 1 nível; ao fim dos filhos soma 1 e repete se `contador < m` ou `m = 0`. | APK B §4 | **AUSENTE** `Model.kt:23-53`, `Runner.kt:74-146` | A macro é lista plana. |
| #33 Percurso em grupo: descer, pular vazios e desabilitados | Primeira folha habilitada em profundidade. | APK B §4 | **AUSENTE** | |
| #34 Salto que cruza níveis de grupo | Procura o ID na árvore inteira. | APK B §4 | **AUSENTE** | |
| #35 Criar grupo a partir da seleção | Menu "Grupo de Macro"; novos IDs; saltos de fora para os movidos ficam sem destino. | APK B §6 | **AUSENTE** `EditorActivity.kt:213-220` | |
| #36 Desagrupar | Devolve os filhos selecionados ao fim da raiz. | APK B §6 | **AUSENTE** | |

---

## 14. Arquivos, importação e backup

Base: pasta `getExternalFilesDir/TouchMacroPro/` com `<nome>.tmc` (CSV de 47 colunas), `image/<nome>/`, `screen_capture/<nome>/` (APK A §4.1; APK B §3). Macro17: JSON próprio em `filesDir/macros/<uuid>.json`, modelos em `templates/<uuid>.png` (global), amostras em `samples/`, `glyphs.json` (`Store.kt:12-16`).

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #39 Formato de arquivo versionado e tolerante | O base não tem versão (CSV com cabeçalho); leitura tolerante a colunas ausentes. | APK B §2, §3 | **IGUAL** `Model.kt:129-137` | JSON `v:2`; campos ausentes recebem padrão. A versão é gravada mas não conferida na leitura. |
| #40 Importar o `.tmc` original | Importar na prática é "Copiar/mesclar". | APK B §7 | **AUSENTE** `MainActivity.kt:184-189` | Só o JSON próprio (limite 1 MB, `Store.kt:169`). |
| #41 Recursos de imagem por macro; órfãos apagados ao salvar | `image/<macro>/`; ao salvar apaga o que não é referenciado. | APK B §3 | **PARCIAL** `Store.kt:14,161` | Modelos em pasta global; excluir a macro não apaga os modelos e nada limpa os órfãos. |
| #125 Criar macro vazia | Cria `<nome>.tmc` de 0 bytes e a seleciona. | APK A §5.1 | **IGUAL** `MainActivity.kt:116` | "Nova macro" abre o editor; o arquivo nasce ao salvar. |
| #126 Lista plana, escolher a atual | `play_status/macro_file`. | APK A §5.1 | **IGUAL** `MainActivity.kt:113-120` | |
| #127 Duplicar macro (arquivo + imagens + capturas) | "Copy to". | APK A §5.1 | **AUSENTE** `MainActivity.kt:138-148` | |
| #128 Renomear macro | Renomeia arquivo e pastas. | APK A §5.1 | **IGUAL** `EditorActivity.kt:102-105,458-462` | Pelo nome no editor. |
| #129 Excluir macro com confirmação (arquivo + imagens + capturas) | Sem lixeira. | APK A §5.1 | **PARCIAL** `MainActivity.kt:150-154`, `Store.kt:161` | Confirma, apaga o JSON, o horário e o alarme; os modelos de imagem ficam. |
| #130 Mesclar / copiar para outra macro | Anexa itens com novos IDs e remapeia saltos. | APK B §7 | **AUSENTE** | |
| #131 Exportar macro (ZIP com imagens, opção de capturas) | Pergunta sobre capturas; ZIP sem senha. | APK A §4.2 | **PARCIAL** `MainActivity.kt:165-168,179-182` | O "Exportar…" da macro grava só o JSON, sem imagens. As imagens vão no backup ZIP (#132). |
| #132 Backup de várias macros | Seleção de macros, pergunta sobre capturas, ZIP via SAF (`TouchMacroPro/...`). | APK A §4.2; print Backup | **PARCIAL** `BackupActivity.kt:33-35,71-76`, `BackupZip.kt:30-42` | ZIP com **tudo** (macros, modelos, amostras e o leitor de números), via SAF. Sem seleção de macros e sem opção de capturas (não há capturas). |
| #133 Restaurar ZIP (aviso, zip-slip, sobrescreve) | Aviso "Warning!", valida prefixo e caminho canônico, sobrescreve sem comparar. | APK A §4.3; print Backup | **IGUAL** `BackupActivity.kt:36-41`, `BackupZip.kt:24,51-87` | Aviso antes; lista branca de nomes e checagem de caminho; valida macro e cabeçalho PNG; sobrescreve por identificador; relata ignorados. Só lê backups do próprio Macro17. |
| #134 Gerenciador de logs de debug (abrir, excluir) | Um `.txt` por macro em `debug/`; abrir por `ACTION_VIEW`, excluir. | APK A §5.2 | **PARCIAL [A relatório]** `DiagActivity.kt:68-72`, `DiagLog.kt:33` | Um registro único com rotação de 1 MB, com copiar, compartilhar, salvar relatório e limpar. Não há arquivo por macro. |
| Importar e exportar JSON próprio | O base usa `.tmc`/ZIP. | código | **EXTRA** `MainActivity.kt:165-189` | Valida a macro, gera id novo, limita a 1 MB. |

---

## 15. Agendamento e auto-início

Base: alarmes diários em HH:MM com "uptime" em minutos, lista própria, detecção de sobreposição, toggle "Schedule" no painel, disparo em duas fases, notificação id 9001, `FullScreenActivity` para acordar a tela (APK A §2). Não sobrevive a reinício (APK A §2.8). Macro17: um horário por macro, que dispara uma vez (`Alarms.kt`).

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #110 Alarme diário em HH:MM, repete todo dia | Próximo disparo = hoje ou amanhã; reagenda sempre. | APK A §2.1, §2.5 | **PARCIAL** `Alarms.kt:38-52`, `:14` | Agenda a próxima ocorrência, mas o receptor apaga o horário ao disparar (uma vez só). |
| #111 Lista de alarmes (nome, macro, hora, uptime, ativo, erro) com editar e excluir | `AlarmSettingActivity`. | APK A §2.1, §2.2 | **PARCIAL** `Store.kt:130-135`, `MainActivity.kt:99,138-148` | Um horário por macro, mostrado no cartão da Home; "Agendar…" e "Cancelar agendamento". Sem tela de lista. |
| #112 Uptime em minutos e alarme de parada | Alarme de parada em início + uptime. | APK A §2.5 | **AUSENTE** | |
| #113 Detecção de sobreposição entre alarmes | `error` impede ligar o alarme. | APK A §2.2 | **AUSENTE** | |
| #114 Toggle "Schedule" no painel e diálogos | Pede permissão exata, avisa se não há alarme, confirma "Iniciar agenda?". | APK A §2.3 | **AUSENTE** `MainActivity.kt:156-163` | |
| #115 Permissão de alarme exato com pedido | Abre `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. | APK A §2.3, §2.4 | **PARCIAL** `Alarms.kt:45-49`, `MainActivity.kt:161` | Cai para alarme aproximado e avisa; não abre a tela de permissão. |
| #116 Disparo em duas fases | Reajuste 10 min antes quando faltam ≥ 30 min. | APK A §2.4 | **AUSENTE** | |
| #117 Notificação persistente do próximo disparo | Id 9001, "chave de vida" do agendamento. | APK A §2.5 | **AUSENTE** | |
| #118 Acordar a tela sobre o bloqueio | `FullScreenActivity` com `TURN_SCREEN_ON`/`SHOW_WHEN_LOCKED`. | APK A §2.6 | **AUSENTE** | |
| #119 Ao disparar: parar a macro em curso, ~1 s, selecionar a do alarme e iniciar | `macro.stop`, espera, grava `macro_file`, `play.start`. | APK A §2.6 | **PARCIAL** `Alarms.kt:10-17`, `MacroService.kt:131-148` | Inicia a macro do alarme, mas recusa se já há uma rodando (`:137`), não para a atual, não espera 1 s e não troca a macro selecionada. |
| #120 Reagendar o próximo disparo automaticamente | Sempre rola para o dia seguinte. | APK A §2.5 | **AUSENTE** `Alarms.kt:14` | |
| #121 Status de espera no painel (estado 6) | Mostra a próxima data/hora. | APK A §2.6 | **AUSENTE** | |
| #122 Macro excluída deixa de ter alarme | Alarmes sem macro viram desabilitados. | APK A §2.1 | **IGUAL** `MainActivity.kt:150-154`, `Store.kt:161` | Excluir cancela o alarme e apaga o horário. |
| #123 Auto-início: mapa app → macro | `auto_start.xml`; lista de apps. | APK A §3.1 | **AUSENTE** | |
| #124 Auto-início: gatilho por troca de app, debounce de 1 s | O `onAccessibilityEvent` arma a mensagem 14 após 1 s. **O consumidor da mensagem 14 não foi encontrado: pode ser função incompleta no 4.2.7 [?]** (APK A §3.3). | APK A §3 | **AUSENTE** `MacroService.kt:91` | `onAccessibilityEvent` vazio. Como o próprio base pode não entregar a função, não é lacuna firme. |

---

## 16. Configurações globais

Base: uma tela com título e descrição por opção (print Configurações). Macro17: mesma ordem, títulos e descrições; as opções sem função aparecem esmaecidas com "(indisponível)" em vez de fingir (`OptionsActivity.kt:31-58`, `Prefs.kt`).

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #93 Tamanho da imagem a salvar (5..160, padrão 80) | Lado do recorte do Tap gravado. | APK A §1.1; print Configurações | **AUSENTE** | Não há a opção nem o recorte (ver #87). |
| #94 Parar reprodução com tecla de volume | `onKeyEvent`, só `ACTION_UP`, volume ±, não consome a tecla. | APK A §1.4 [O]; print Configurações | **IGUAL** `MacroService.kt:79-89`, `OptionsActivity.kt:32`, `accessibility_config.xml` | Filtro de teclas declarado. |
| #95 Iniciar macro com tecla de volume | Só com o app ocioso. | APK A §1.4 [O] | **IGUAL** `MacroService.kt:84-87`, `OptionsActivity.kt:33` | Não inicia durante a gravação (extra). |
| #96 Mostrar toques | Padrão ligado no base. | APK C §4.6 | **AUSENTE** `OptionsActivity.kt:34` | Linha desabilitada, "(indisponível)". |
| #97 Manter tela ligada | Também em repouso com o painel visível. | APK A §1.5 | **PARCIAL** `MacroService.kt:169-175`, `OptionsActivity.kt:35` | Só durante a execução. |
| #98 Depurar | Log por macro em `debug/`, toasts de mensagens, diálogo do motivo, botão "Debug File". | APK A §1.6 | **PARCIAL** `OptionsActivity.kt:36`, `MacroService.kt:144` | Abre o registro ao vivo ao iniciar uma macro. O motivo do fim sai em toast; não há arquivo por macro nem botão "Debug File". |
| #99 Alarme sonoro ao fim da macro | Som e vibração por 5 s, só no fim natural. | APK A §2.7 [O] | **IGUAL** `MacroService.kt:62,151-158`, `OptionsActivity.kt:37` | Toque de alarme por 5 s no fim natural ("Macro concluída"); a vibração dura 800 ms (base: 5 s). |
| #100 Monitoramento contínuo da tela | Mantém a projeção viva em repouso. | APK A §1.7 | **AUSENTE** `OptionsActivity.kt:39` | Linha desabilitada; sem projeção no Macro17, o conceito não se aplica. |
| #101 Atalho para ignorar a otimização de bateria | Switch reflete `isIgnoringBatteryOptimizations`; tocar leva às configurações. | APK A §1.1 [O] | **IGUAL** `OptionsActivity.kt:39-42,67` | Abre a lista de otimização de bateria do sistema; o base mostra um diálogo de instrução antes. |
| #102 Transparência da janela flutuante | 0..50%, padrão 30%, `alpha = 1 − t/100`. | APK A §1.1; print Configurações (30%) | **IGUAL** `OptionsActivity.kt:45,69-85`, `Store.kt:51-53`, `MacroService.kt:161-166` | Mesmo intervalo, padrão e fórmula; aplicada na hora. |
| #103 Ocultar painel durante a reprodução | Painel some; a tecla de volume passa a parar a macro. | APK A §1.1, §1.4 | **IGUAL** `MacroService.kt:144,83`, `OptionsActivity.kt:47` | Esconde o painel ao iniciar e o devolve ao fim (`:61`). Também faz a tecla de volume parar. |
| #104 Visualização simples | Ocioso: só Play e Record; durante a reprodução: só Stop. Exclusiva com o modo reprodução. | APK C §1.1; print Configurações | **PARCIAL** `MacroService.kt:230-232`, `OptionsActivity.kt:48` | Remove o enquadrar e a segunda linha; o REC continua visível durante a execução. As duas opções não são mutuamente exclusivas. |
| #105 Modo de reprodução (só Play) | Só o botão de reprodução. | APK C §1.1 | **IGUAL** `MacroService.kt:230-232`, `OptionsActivity.kt:49` | Fica ▶/■ e a alça. Sem a exclusão mútua com a visualização simples. |
| #106 Áudio da gravação de vídeo | none/mic/interno. | APK A §1.1 | **AUSENTE** `OptionsActivity.kt:52` | Linha desabilitada. |
| #107 Bitrate do vídeo | 10/5/2 Mbps. | APK A §1.1 | **AUSENTE** `OptionsActivity.kt:53` | idem |
| #108 Frame rate do vídeo | 30/24/15. | APK A §1.1 | **AUSENTE** `OptionsActivity.kt:54` | idem |
| #109 Configuração do alerta de status | Duração, som, vibração, pop-up, toque, mensagem. | APK A §1.3 | **PARCIAL** `Model.kt:35`, `EditorActivity.kt:63` | Só mensagem e tempo. |
| Parar ao bloquear a tela | O base não tem. | APK A §1.5 [?], APK C §2.2 [O] | **EXTRA** `OptionsActivity.kt:38` | |
| Leitor de números, Monitor e logs | Não existem no base. | código | **EXTRA** `OptionsActivity.kt:57-58` | |

---

## 17. Modos ADB e root

O próprio achado manda **não replicar** (APK C §5.3). Entram na conta (ficam no total com área 12, como na medição anterior) mas o total "sem ADB/root" é mostrado à parte.

| Função do app base | Como funciona (mecânica) | Evidência | No Macro17 hoje | Diferença |
|---|---|---|---|---|
| #155 Modo ADB (`touch_permission` 2) | `TouchMacroService` injeta toques com privilégio de shell; assistente de 3 passos, `adb tcpip 7777`. | APK C §5 | **AUSENTE** | `PermissionsActivity.kt:73-75` explica que não é usado. |
| #156 Modo root / captura nativa (3 e 4) | Captura por processo nativo, sem MediaProjection. | APK C §5.1; APK D §3 | **AUSENTE** | `PermissionsActivity.kt:76-78` explica. O root só serve ao relatório de diagnóstico (`Diag.kt:104-106`, EXTRA). |
| #157 Companheiro Touch Mate (porta 5552) | App separado com socket TCP local. | APK C §5.1 | **AUSENTE** | |
| #158 Gravar toques reais e teclas físicas via shell | Lê dispositivos de entrada. | APK C §3.2, §3.3 | **AUSENTE** | Sem shell, "toque real" não existe: a alternativa é a gravação por marcadores (APK C, decisão de produto). |

---

## Fora de escopo (não entram na conta)

- **Licença, assinatura e compras:** telas Assinaturas e Gerenciar Assinatura; limites "gated" (transparência acima de 50%, gravação de vídeo de 3 min, uptime etc.).
- **Anti-adulteração e integridade:** `pairip.licensecheck`, `AppIntegrity`, `SubscriptionUpdate`, detecção de emulador (`detector_emu`, `libemu_exec`).
- **Código morto do base:** `BubbleActivity`, `BubbleMacroEditActivity`, `BubblesButton`; layout órfão `touch_import_macro_edit_dlg`; `app_control_settings.xml`; `delay_screen_cap` (lido e descartado).
- **Defeitos do base que não se copiam:** chinês (`zh`) quebrado no OCR; posição fixa sem arquivo de modelo contada como sucesso; apagar pixels do quadro no modo 4; mensagem enganosa de "Mudança de imagem" em "criar grupo".
- **Pontos não resolvidos no próprio base [?]:** consumidor do auto-início (mensagem 14); origem dos tipos pinça 4 e 5; ordem e zeragem dos casos do contador; estado 4 do painel; retorno do `CustomAreaSelectionActivity`; conteúdo da tela Sobre; mecânica do "Recarregar" do editor; retomada do relógio do grupo de imagens.

---

## Fechamento

### (a) Contagem por área e total

Conta: IGUAL = 1, PARCIAL = 0,5, AUSENTE = 0. EXTRA não entra. Cada item pesa o mesmo, seja "Botão Fechar" ou "Mudança de imagem": o percentual mede quantidade de funções, não cobertura do uso real.

| Área | Itens | IGUAL | PARCIAL | AUSENTE | Conta | % |
|---|---|---|---|---|---|---|
| 1 Home e permissões | 6 | 4 | 2 | 0 | 4 + 0,5×2 = 5 / 6 | 83,3% |
| 2 Menu lateral | 3 | 2 | 1 | 0 | 2 + 0,5×1 = 2,5 / 3 | 83,3% |
| 3 Painel flutuante | 22 | 4 | 7 | 11 | 4 + 0,5×7 = 7,5 / 22 | 34,1% |
| 4 Gravação | 15 | 3 | 4 | 8 | 3 + 0,5×4 = 5 / 15 | 33,3% |
| 5 Tipos de item | 14 | 8 | 4 | 2 | 8 + 0,5×4 = 10 / 14 | 71,4% |
| 6 Campos do item | 17 | 8 | 3 | 6 | 8 + 0,5×3 = 9,5 / 17 | 55,9% |
| 7 Execução e controle de fluxo | 5 | 5 | 0 | 0 | 5 / 5 | 100% |
| 8 Busca de imagem | 12 | 4 | 7 | 1 | 4 + 0,5×7 = 7,5 / 12 | 62,5% |
| 9 OCR e texto | 4 | 0 | 2 | 2 | 0,5×2 = 1 / 4 | 25,0% |
| 10 Mudança de imagem e grupo de imagens | 4 | 0 | 0 | 4 | 0 / 4 | 0% |
| 11 Editor e lista | 21 | 7 | 7 | 7 | 7 + 0,5×7 = 10,5 / 21 | 50,0% |
| 12 Edição em lote | 2 | 0 | 2 | 0 | 0,5×2 = 1 / 2 | 50,0% |
| 13 Grupos | 5 | 0 | 0 | 5 | 0 / 5 | 0% |
| 14 Arquivos, importação e backup | 13 | 5 | 5 | 3 | 5 + 0,5×5 = 7,5 / 13 | 57,7% |
| 15 Agendamento e auto-início | 15 | 1 | 4 | 10 | 1 + 0,5×4 = 3 / 15 | 20,0% |
| 16 Configurações globais | 17 | 7 | 4 | 6 | 7 + 0,5×4 = 9 / 17 | 52,9% |
| 17 Modos ADB e root | 4 | 0 | 0 | 4 | 0 / 4 | 0% |
| **TOTAL (com área 17)** | **179** | **58** | **52** | **69** | **58 + 0,5×52 = 84 / 179** | **46,9%** |
| **TOTAL sem área 17** | **175** | **58** | **52** | **65** | **84 / 175** | **48,0%** |

A área 7 dá 100% porque as outras funções de fluxo (pausa, grupos, item desabilitado, Parar macro) estão contadas nas áreas 3, 5, 6 e 13; ela não significa que o fluxo está completo.

**Comparação com a medição anterior (33,5%).** A medição anterior usava 158 itens: 35 IGUAL, 36 PARCIAL, 87 AUSENTE, conta 53 / 158 = **33,5%**. Refeita contra o código atual **sobre os mesmos 158 itens**: 45 IGUAL, 46 PARCIAL, 67 AUSENTE, conta 45 + 23 = **68 / 158 = 43,0%** (44,2% sem ADB/root, 68 / 154). Os 21 itens novos desta rodada (linhas H, M, E, N, G: 13 IGUAL, 6 PARCIAL, 2 AUSENTE = 16) elevam o total a 84 / 179 = **46,9%**. Os itens novos existem porque as observações de tela (menu lateral, Home, editor, painel de gravação) mostraram funções que a lista de 158 não tinha; não são "ganho do código".

O que mudou nos 158 itens (11 passaram a IGUAL, 11 a PARCIAL, 1 regrediu):

- **Passaram a IGUAL (11):** #42 seleção múltipla, #45 excluir selecionados, #70 editar pelo painel, #83 marcadores arrastáveis, #94 parar com volume, #95 iniciar com volume, #99 alarme ao fim, #101 atalho da bateria, #103 ocultar painel, #105 modo reprodução, #133 restaurar ZIP.
- **Passaram a PARCIAL (11):** #49 alternar pular, #50 edição em lote, #66 botão Adicionar, #67 alternar tipo, #71 configurações no painel, #72 fechar/expandir, #91 diálogo "ir para o editor", #98 depurar, #104 visualização simples, #132 backup, #134 logs.
- **Regrediu (1):** #84 atraso gravado = intervalo desde a ação anterior. Era IGUAL quando a gravação usava o toque real; com a gravação por marcadores o atraso é fixo em 1000 ms.
- #102 transparência continua IGUAL e agora tem a mesma escala do base (0..50%, padrão 30%).

### (b) As 10 lacunas de maior impacto para uso real, em ordem

1. **Gravação fiel ao base.** O + só acrescenta o item e não executa a ação no ponto, então o app de baixo não avança durante a gravação (`MacroService.kt:422-431`); o atraso é sempre 1000 ms (#84); não há Pressionar nem Gesto (#67, #85); o Tocar não guarda recorte nem miniatura (#87, #88); sempre cria uma macro nova (#90). É o caminho de entrada de quase todo uso real.
2. **Ver e controlar a execução: pausa, retomada, Anterior/Próximo, passo atual, Mostrar toques.** O painel só tem ▶/■ (#62-#64, #75, #77). Sem eles não se acompanha nem se corrige uma macro longa; a "Mostrar Toques" aparece em Configurações desabilitada.
3. **Grupos com repetição e item desabilitado de verdade, mais Parar macro.** O modelo é lista plana (`Model.kt:23-53`). O "Alternar Pular" do Macro17 inverte `skip_action` (#23, #49), enquanto o do base desabilita o item; o nome igual engana.
4. **Edição segura da lista.** Voltar no editor descarta sem avisar (`EditorActivity.kt:160`, #48); sem backup datado nem histórico (#46, #47); duplicar não remapeia os saltos internos (#44); itens sem nome (#15).
5. **Edição visual de ponto, área e desvio, e "testar item" com %.** Hoje as coordenadas são digitadas em px (#54-#58), e não existe o deslocamento relativo ao achado (#29; `Runner.kt:102` toca no centro exato) nem a condição de imagem em qualquer gesto (#28).
6. **Agendamento.** Um horário por macro que dispara uma vez e se apaga (`Alarms.kt:14`), sem repetição diária, sem uptime e parada, sem acordar a tela, recusa se já há macro rodando (`MacroService.kt:137`), sem tela de lista nem entrada no menu (#110-#121).
7. **Busca de imagem completa.** Mudança de imagem e grupo de busca de imagens (#146-#149), atraso só após o sucesso (#145; `Runner.kt:84-87`), busca infinita e ciclo 0 (#143, #144), área em cor e posição fixa (#140-#142).
8. **Arquivos.** Exportar uma macro grava só o JSON sem imagens (#131); não há duplicar nem mesclar (#127, #130); excluir deixa modelos órfãos (#41, #129); sem importar `.tmc` (#40). O backup ZIP cobre o conjunto, mas não uma macro isolada.
9. **Captura de tela robusta.** Sem cache; o erro de "intervalo mínimo" perde a tentativa (`Engine.kt:163-166`); o painel de controle aparece nos quadros se "Ocultar janela" estiver desligada (#135, #103).
10. **OCR e alerta.** OCR só em alfabeto latino, sem limiar de confiança e com a regra de casamento diferente (#150-#152); alerta de status só com mensagem, sem som, vibração nem pop-up (#12, #109); sem tamanho de recorte configurável (#93) e sem notificação de primeiro plano com ação Parar (#79).

### (c) Extras do Macro17 (o que o base não tem), verificados no código

| Extra | Onde está no código | Observação |
|---|---|---|
| Passo **Esperar** (`WAIT`) | `Model.kt:7`, `Runner.kt:93` | O base usa o campo de atraso de cada item. |
| Passo **Achar por texto de elemento** (`NODE`) | `Engine.kt:119-138` | Varre a árvore de acessibilidade da janela ativa (até 4000 nós), por texto, descrição ou `id:nome`. |
| Passo **Ler número** com **leitor de dígitos treinável** | `Runner.kt:105-116,187-193`, `Digits.kt`, `ReaderActivity.kt`, `Store.kt:83-127` | Não é o OCR do ML Kit: o usuário ensina os dígitos (amostra pela lupa, rótulo em Opções › Leitor de números); nunca "chuta" (limites de confiança e margem em `Digits.kt:10-11`); condições ≥, ≤, =, entre. O backup ZIP leva as amostras e o leitor (`BackupZip.kt:33-39`). |
| **Captura de imagem pelo enquadrar do painel**: Imagem, Número ou Amostra | `MacroService.kt:316-...` (`captureImage`, `pick`), `Capture.kt` | **[A]** captura de imagem comprovada. Congela a tela após 3 s, deixa arrastar a área e acrescenta o passo à macro selecionada. No base esse ícone é provavelmente Gravar tela (#69). |
| **Método 1 de comparação** (diferença de pixels), **cinza**, **confirmação em N quadros seguidos**, **tolerância a escala** | `Model.kt:44-53`, `Runner.kt:157-193`, `Vision.kt:205-236,287` | O base só tem correlação, sem multi-escala. |
| **OpenCV com reserva em Kotlin** | `Cv.kt:16`, `Engine.kt:101-107` | **[A]** OpenCV carregado. Registra "casamento lento" acima de 250 ms. |
| **Aviso de imagem não achada com a melhor semelhança** | `Runner.kt:178-182` | |
| **Orbe** (painel recolhido em bolha) | `MacroService.kt:301-302` | O base não recolhe em ícone (APK C §1.1). |
| **Segurar ■ na gravação descarta** | `MacroService.kt:212-214` | |
| **Parar ao bloquear a tela** | `MacroService.kt:67-72`, `Store.kt:47` | O base não implementa. |
| **Monitor e logs** (aba) | `DiagActivity.kt:28-90` | Contadores ao vivo (serviço, macro, memória, erros, avisos), registro com filtros por categoria (Tudo, Passos, Busca, Gestos, Sistema, Avisos, Erros; `LogFeed.kt`), linhas repetidas agrupadas (×N), copiar, compartilhar, salvar relatório, limpar. |
| **Relatório de diagnóstico** | `Diag.kt:61-110`, `DiagLog.kt:13-31` | **[A]** relatório de logs comprovado. Aparelho, saídas anteriores do processo (`ApplicationExitInfo`), memória, permissões, logcat filtrado (`filterLogcat`), e, com root, logcat completo, dmesg e `dumpsys accessibility` (`Diag.kt:104-106`). |
| **Painel de registro ao vivo** flutuante | `LogPanel.kt:31-177`, `MacroService.kt:124,289` | Filtros, pausar, parar a macro, minimizar, fechar; deixa os toques passarem durante a macro e some no instante de cada captura (`captureHide`). Abre sozinho com "Depurar" ligado. |
| **Registro em arquivo com rotação** e **gancho de falha fatal** | `DiagLog.kt:33-47`, `App.kt:17` | 1 MB por arquivo, mais o anterior. |
| **Exportar e importar JSON próprio**, com validação | `MainActivity.kt:165-189`, `Store.kt:169-181` | Id novo, limite de 1 MB. |
| **Backup ZIP com leitor de números e amostras** e restauração validada | `BackupZip.kt` | Lista branca de nomes, validação de macro e de cabeçalho PNG, limites de tamanho. |
| **Janela de acessibilidade em vez de permissão de sobreposição**; captura sem diálogo | `MacroService.kt:276`, `Engine.kt:147-172` | |
| **Telas de configuração que mostram o que não existe como "(indisponível)"** | `Prefs.kt:61-71`, `OptionsActivity.kt:34,39,52-54` | |
| **Menu de linha** (toque longo): duplicar, mover, excluir | `EditorActivity.kt:363-374` | |
| **Tecla "Notificações"** como ação global | `Model.kt:61`, `Engine.kt:95` | |
| Atraso mínimo de 1 ms por passo; falhas de gesto, captura e OCR viram motivo de parada | `Runner.kt:84-87,63-72`, `Engine.kt:89-92,163-166,199-201` | |

---

*Estado do código lido em 02/11/2026 até 23:57 UTC. Não foi rodado nada (build, testes, app) e nada foi alterado fora deste arquivo.*
