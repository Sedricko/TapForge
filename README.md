# TapForge

Auto clicker e macros para Android: toques e gestos, loops, condições, variáveis, detecção de imagem, OCR e cor.
Sem anúncios, sem conta, sem internet e sem cobrança. Veja `docs/ANALISE.md` e `docs/ROADMAP.md`.

## O que ele faz
- **Painel flutuante:** executar/parar, pausar, passo a passo, trocar de macro, marcar na tela
  (toque, deslize, imagem, texto, cor), gravar toques e recolher em bolha.
- **Editor:** ações em cartões, blocos aninhados (repetir, se/senão, procurar imagem, tentar de novo),
  catálogo com explicação de cada ação, formulário descrito por ação, teste de uma ação só,
  JSON completo e versões anteriores.
- **Ações:** tocar (ponto ou área aleatória), deslizar, gesto com caminho, multitoque/pinça, esperar,
  esperar a tela parar, repetir N vezes / enquanto / até, se/senão, tentar de novo, sub-macro, parar,
  procurar imagem (todas as ocorrências, alternativas, tamanhos diferentes), ler texto ou número,
  variáveis e contas, botões do sistema, digitar, copiar, abrir app, alerta e registro.
- **Condições:** variável, texto na tela, imagem visível, cor do pixel, tela mudou, app aberto,
  acaso, E / OU / NÃO.
- **Início automático:** ao abrir um app, ao conectar o carregador, em um horário, por atalho na
  tela inicial, pelo bloco das Configurações rápidas ou por intent (Tasker/Automate).
- **Segurança:** parar com volume para baixo, ao chacoalhar ou pela notificação; apps bloqueados
  pausam o macro.
- **Dados:** macros em JSON aberto, backup/restauração, pastas, favoritos, estatísticas e registro.

## Estrutura
- `model/` macro em JSON aberto, árvore de ações, catálogo e modelos prontos
- `engine/` motor de execução (independente de Android; testes JVM em `src/test`)
- `service/` acessibilidade (gestos, painel, gravação, gatilhos), captura de tela, agendamento, atalhos
- `vision/` template matching próprio e OCR (ML Kit)
- `data/` persistência, ajustes, registro e estatísticas
- `ui/` telas em Material 3

## Rodar
`./gradlew :app:testDebugUnitTest :app:assembleDebug` (precisa do Android SDK) ou abra no Android Studio.
O CI do GitHub gera o APK de debug a cada push (aba Actions › artefato `tapforge-debug-apk`).

## Intents para automação
Com "Permitir outros apps" ligado nos Ajustes:
`am start -a com.tapforge.RUN --es macro <id>` inicia um macro; `com.tapforge.STOP` para.
