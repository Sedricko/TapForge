# Análise comparativa dos três apps

Método: leitura estática de manifesto, permissões, configs XML, strings de recursos e assets
dos três pacotes. O código (DEX) está ofuscado e **não foi decompilado**: o que segue é o que
os pacotes declaram, mais inferências marcadas como tal. Nada aqui foi copiado deles.

## 1. Auto Clicker – Automatic Tap (v2.3.0, `com.truedevelopersstudio.automatictap.autoclicker`)
Observado:
- Serviço de acessibilidade com gestos; `WorkerService` para a execução.
- Modo **único** e modo **multi-alvo** (telas de configurações e instruções próprias).
- **Configurações salvas** (tela de gerenciamento), tutorial de ativação e tela de solução de problemas.
- Intervalo entre cliques e **condição de parada** escolhida pelo usuário; início automático.
- Não declara `SYSTEM_ALERT_WINDOW` (inferência: usa overlay de acessibilidade).
- Monetização: AdMob, Firebase Analytics, UMP (consentimento), Play Billing.

Ponto forte: simplicidade e onboarding (tutorial + troubleshooting). Fraqueza: sem visão, sem lógica.

## 2. Macrorify – Image Auto Clicker (v1.6.0.2, `com.kok_emm.mobile`)
Observado:
- **Modo Edição** e **Modo Execução** como serviços separados; captura de tela via MediaProjection.
- **Detecção de imagem** (template, "diferença quadrática", limiar/score, imagem binária, tempo máximo de espera).
- **OCR** com ML Kit embutido (offline).
- **Variáveis com escopo**, condições (incluindo aleatória), loops (com limite de tempo), "flavors" (variantes de macro), macro pai.
- Ações: toque/gesto, colar texto, travar tela, etc. Gerenciador de recursos (imagens, gravações).
- Loja de macros com login Google; macros "Pro" bloqueadas; Play Billing; OkHttp; Firebase.
- Texto sobre "Native Service" para Android 6 (mecanismo não verificado).

Ponto forte: motor de macro (lógica + visão). Fraqueza: curva de aprendizado, dependência de conta/rede.

## 3. Touch Macro Pro (v4.2.5, `com.jake.touchmacro.pro`)
Observado:
- **Gravação** de macros (tela/microfone), editor de itens, editor de posição/toque selecionado.
- **Agendamento por alarme** (`SCHEDULE_EXACT_ALARM`, receiver, tela cheia), **início automático**.
- **Bolha flutuante**, parar com **tecla de volume**, alerta de status.
- Gestos configuráveis, **detecção de mudança de imagem**, **grupos de imagens**, reconhecimento de texto (ML Kit), ação de navegação.
- **Contador de execuções / reset do contador de repetição**.
- **Backup e restauração**, gerenciador de arquivos de macro, arquivos de debug.
- Assets não explicados: `input.jar` e `detector_emu` (hipóteses não verificadas: auxiliar de injeção
  de entrada e detecção de ambiente; **não adotamos nada disso**).
- Assinatura (Play Billing).

Ponto forte: ergonomia de uso real (gravar, agendar, backup, parar por volume).

## Matriz
| Capacidade | Auto Clicker | Macrorify | Touch Macro | TapForge (alvo) |
|---|:--:|:--:|:--:|:--:|
| Toque/gesto por acessibilidade | ✔ | ✔ | ✔ | ✔ |
| Multi-alvo | ✔ | ✔ | ✔ | ✔ |
| Condição de parada | ✔ | ✔ | ✔ | ✔ |
| Config salva / backup | ✔ | ✔ | ✔ | ✔ (JSON aberto) |
| Detecção de imagem | – | ✔ | ✔ | ✔ |
| OCR offline | – | ✔ | ✔ | ✔ |
| Variáveis, if, loop | – | ✔ | parcial | ✔ |
| Gravação | – | ✔ | ✔ | Fase 3 |
| Agendamento | – | – | ✔ | Fase 3 |
| Parar com tecla de volume | – | – | ✔ | Fase 2 |
| Overlay sem permissão extra | ✔(inf.) | – | – | ✔ |
| Sem anúncios/conta/rede/cobrança | – | – | – | ✔ |

## O que levamos de cada um
- **Auto Clicker** → onboarding guiado, troubleshooting, overlay via acessibilidade, simplicidade do modo único.
- **Macrorify** → modelo de macro (ações, condições, variáveis com escopo, loops com limite de tempo), visão + OCR.
- **Touch Macro** → gravação, agendamento, bolha flutuante, parar por volume, contadores, backup.

## Melhorias além dos três
- Humanização opcional (jitter de posição e tempo) em todas as ações.
- Macros em JSON aberto, versionado e exportável; sem conta, sem nuvem.
- Permissões mínimas: sem `INTERNET`, sem billing, sem anúncios, sem analytics.
- Motor testável na JVM (interfaces para gestos, tela e OCR).
- Um único serviço em vez de dois (edição/execução) para reduzir consumo.

## Sobre "sem travas de licenciamento"
TapForge nasce sem paywall, sem verificação de licença e sem anúncios, porque é um app novo
e todo o código é nosso. Não removo nem contorno a cobrança dos apps originais, nem reaproveito
código ou assets deles (modelos de OCR, imagens, textos). As bibliotecas usadas aqui são abertas
(Kotlin, kotlinx.serialization, ML Kit de OCR via Google).
