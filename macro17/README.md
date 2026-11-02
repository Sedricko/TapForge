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

## Busca de imagem
Passo **Buscar imagem**: procura um recorte na tela a cada intervalo, até o prazo. Achou: toca no centro (opcional) e segue o destino "se achar". Não achou no prazo: segue o destino "se não achar". Área de busca opcional.
- **Criar:** deixe a macro na lista como selecionada, abra o leque do orbe e toque na lupa. Em 2 s a tela congela; arraste sobre o que quer achar e confirme. O passo entra no fim da macro.
- **Captura de tela** pelo próprio serviço de acessibilidade (`takeScreenshot`), sem a janela de consentimento de gravação de tela. Intervalo mínimo do sistema entre capturas: ~0,3 s.
- **Como compara (igual ao app de referência):** dentro da área escolhida, compara os pixels com o modelo por **correlação normalizada** (`TM_CCOEFF_NORMED`, conferido no bytecode do app base) e aceita se a semelhança em % for **maior** que a configurada (mínimo 20%; 100 vale 99%). Tolera brilho e contraste diferentes. Alternativa: **diferença de pixels** (método 1), que exige a tela idêntica. Opção de tolerar escala (mais lenta).
- **Limites:** a busca grossa reduz a imagem, então texturas muito finas e desalinhadas podem passar batido; ícones e botões comuns, não. Sem medição de velocidade no aparelho. Telas protegidas (`FLAG_SECURE`) não podem ser capturadas. Os recortes ficam no aparelho e **não vão junto na exportação** da macro.
- **Sem OCR**, de propósito (ver `THIRD_PARTY.md`).

## Achar por texto (acessibilidade)
Passo **Achar por texto**: varre a janela ativa e acha o elemento pelo texto ou descrição (contém, ou igual), ou pelo fim do id de recurso (`id:btn_ok`). Toca no centro e segue os destinos "se achar" e "se não achar", com prazo e intervalo.
- É o mais exato para apps comuns: não depende de resolução, tema nem brilho. Em jogos e telas desenhadas (Unity, Flutter) a tela não expõe elementos e isso não funciona; use imagem.
- O serviço passa a poder **ler o texto dos elementos** da tela (`canRetrieveWindowContent`). Nada é gravado nem enviado: o app não tem permissão de internet.
- Só a janela ativa e até 4000 elementos por busca.

## Ler número (leitor por amostra, sem OCR)
Passo **Ler número**: lê o valor numa área da tela e compara (≥, ≤, =, entre). Valendo dentro do prazo, segue "se a condição valer"; senão, "se não valer".
- **Ensinar:** no orbe, lupa, arraste sobre um número e escolha **Amostra**; em Opções › Leitor de números, digite o que a amostra mostra. Repita até cobrir 0 a 9. Fontes e tamanhos diferentes convivem.
- **Criar o passo:** no orbe, lupa, arraste sobre o número e escolha **Número**. A área já vem preenchida; ajuste a condição no editor.
- **Como funciona:** separa os símbolos por colunas vazias e compara cada um com os dígitos aprendidos. Serve a fontes fixas (jogos, contadores). Se um símbolo for desconhecido ou ambíguo, **não chuta**: conta como "não valeu".
- **Limites:** dígitos colados uns nos outros não separam; só dígitos (sem vírgula, ponto ou sinal); não lê texto livre. Validado com uma fonte de teste em vários tamanhos, contrastes e ruído, não com fontes reais de jogos.

## Dependências
Nenhuma biblioteca externa. O que precisou vir de fora está copiado em `libs/` e documentado em `THIRD_PARTY.md`.

## Build
```
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Exige Android SDK com plataforma 37 e acesso ao Gradle e ao plugin Android (cadeia de build, ver `THIRD_PARTY.md`). AGP 8.13 só suporta oficialmente até a API 36: `android.suppressUnsupportedCompileSdk=37` em `gradle.properties`.
