# TapForge

Auto clicker e macros para Android: toque/gesto, loops, condições, variáveis, detecção de imagem e OCR.
Sem anúncios, sem conta, sem rede e sem cobrança. Veja `docs/ANALISE.md` e `docs/ROADMAP.md`.

## Estrutura
- `model/` macro em JSON aberto (ações e condições)
- `engine/` motor de execução (independente de Android; testes JVM em `src/test`)
- `service/` serviço de acessibilidade (gestos + painel flutuante) e captura de tela
- `vision/` template matching próprio e OCR (ML Kit)
- `data/` persistência dos macros

## Rodar
Abra no Android Studio, sincronize o Gradle, rode `./gradlew :app:testDebugUnitTest` e instale em um aparelho.
