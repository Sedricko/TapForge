# Macro17

App de macros para Android 17 (API 37). Kotlin, só APIs da plataforma: sem AndroidX, Material, coroutines ou OCR.
Projeto novo e independente do TapForge. Referência de funções: `../docs/ANALISE_TOUCHMACRO.md`.

## Visual
Opção D do conceito de vidro: tema noturno (azul-marinho e violeta), grade de cards com botão de executar, filtros Todas/Agendadas, card tracejado para criar macro e controle flutuante em orbe que abre em leque (executar, parar, gravar). Acento azul `#5B9DFF`. O vidro é simulado (translucidez e borda), sem blur real. Ícones desenhados em `Canvas` (`Ui.kt`); nenhum recurso de terceiros.

## Uso
1. Instale, abra e ative **Macro17** em Acessibilidade (app fora da loja: Informações do app > ⋮ > *Permitir configurações restritas*).
2. O orbe aparece: toque abre o leque (`▶` executar a macro selecionada, `■` parar, `●` gravar/parar gravação); arraste o orbe para mover.
3. Gravação: toque nos pontos desejados. **Os toques não chegam ao app de baixo** (só são capturados).
4. No app: a grade 2×2 executa, para, importa e abre os ajustes; a busca filtra a lista; tocar numa macro abre Selecionar, Executar, Editar, Agendar, Exportar (JSON via SAF), Excluir. *Importar* lê JSON.

## Opções
Tela acessada pela engrenagem do painel: manter a tela ligada durante a execução, parar ao bloquear a tela e opacidade da bolha (30% a 100%).

## Passos
Toque, deslizar, esperar e tecla global (voltar, início, recentes, notificações). Cada passo tem pausa fixa depois, pausa extra aleatória e jitter de coordenada. Macro tem repetições (0 = infinito).

## Limites conhecidos
- Sem detecção de imagem/texto, sem condicionais: fica para uma fase 2.
- A bolha é uma janela real: um toque da macro no mesmo ponto da bolha bate nela. Mova-a antes de executar.
- Coordenadas são em pixels da orientação em que foram gravadas.
- Agendamento é de uma vez só e não sobrevive a reinício. Sem a permissão de alarmes exatos o alarme é aproximado.
- Telas protegidas (`FLAG_SECURE`, bancos) e tela bloqueada cancelam gestos: a macro para com mensagem.
- Android 17: o serviço não declara `isAccessibilityTool` (é automação). Se o sistema restringir serviços que não são ferramenta de acessibilidade (ex.: Proteção Avançada), o serviço será bloqueado.

## Build
```
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Exige Android SDK com plataforma 37. AGP 8.13 só suporta oficialmente até a API 36: `android.suppressUnsupportedCompileSdk=37` em `gradle.properties`.
