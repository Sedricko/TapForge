# Análise estática: Touch Macro Pro 4.2.7 (referência para o projeto novo)

Escopo: **somente funções e arquitetura**. Nenhum código, layout ou recurso será copiado.
O projeto novo é clean-room. Licenciamento, compras e anti-tamper do app original ficam fora.

Método: unzip do XAPK, androguard (manifesto, recursos, `strings.xml`, lista de classes do DEX).
Não houve decompilação de métodos nem execução. Tudo abaixo é **observado** salvo marcação `[inferido]`.

## 1. Identidade
| Item | Valor |
|---|---|
| Pacote | `com.jake.touchmacro.pro`, versionName 4.2.7 (code 427) |
| minSdk / targetSdk | 24 / 36 |
| Splits | base, `config.arm64_v8a`, `config.xxhdpi` |
| DEX | 1 arquivo (`classes.dex`, 4097 classes, 57 em `com.jake.*`) |
| Libs nativas (arm64) | `libopencv_java4`, `libmlkit_google_ocr_pipeline`, `libadb_exec`, `libreplay_exec`, `libemu_exec`, `libnative-lib`, `libjson_lib`, `liblanguage_id_l2c_jni` |

## 2. Arquitetura (observada)
- **Execução de toque, dois caminhos:**
  1. `MyAccessibilityService`: `canPerformGestures=true`, `canRequestFilterKeyEvents=true`, eventos `0x28`, flags `0x20`. Usa `dispatchGesture`.
  2. **Modo USB debugging / ADB**: `USBDebugSetupActivity`, string `adb tcpip 7777`, `libadb_exec.so` e `assets/input.jar`. O script `assets/input` roda `app_process /data/local/tmp/.tmc com.script.input.Main`, ou seja, injeção de input com privilégio de shell. É o caminho "Touch Mate". As strings dizem que serve para pular limitações do accessibility (ex.: apps que bloqueiam gestos).
- `TouchMacroService`: serviço do motor de replay `[inferido]`. `ServiceJNI` faz a ponte com `libreplay_exec.so`, então o loop de replay roda em C++.
- **Visão:** OpenCV (`libopencv_java4`) para casamento de imagem e detecção de mudança. ML Kit OCR offline para reconhecer texto. Captura via MediaProjection (`ScreenRecordPermissionActivity`, FGS `mediaProjection`).
- **UI:** bolha flutuante (`BubbleActivity`, `BubbleMacroEditActivity`, `BubblesButton`) via `SYSTEM_ALERT_WINDOW`. Editor com Preferences customizadas (`pref_*_config.xml`).
- **Persistência:** arquivos de macro (`MacroFileManagerActivity`) e backup/restauração (`BackupAndRestoreActivity`, criptografia `encryption/AES`) `[inferido: formato do arquivo não lido]`.
- **Agendamento:** `AlarmSettingActivity`, `AlarmAddActivity`, `AlarmReceiver`, `FullScreenActivity`, com `SCHEDULE_EXACT_ALARM`.
- **Fora do escopo (licença e proteção):** `pairip.licensecheck`, `AppIntegrity`, `SubscriptionUpdate`, `detector_emu`/`libemu_exec`. Existem, não serão reproduzidos nem analisados.

## 3. Modelo de macro (tipos de ação, extraídos dos `pref_*_config.xml`)
Cada item tem `config_name` e, na maioria, delay (`config_delay`, 0-300 s) mais um switch de delay.

| Tipo | Campos |
|---|---|
| **Toque / swipe / press** (`pref_touch_pos`) | posição (x,y), segunda posição (swipe), duração do gesto (ms), `skip` (não toca, só espera ou compara), desvio de coordenada (X%, Y%), posição aleatória (raio), pixel aleatório, delay aleatório (mín/máx) |
| **Gesto/botão** (`pref_gesture`) | gestos de sistema gravados, duração |
| **Navegação** (`pref_navigation_action`) | tecla (Back/Home/Recents etc.), delay |
| **Imagem** (casamento) | área de busca (tela cheia ou custom), taxa de match %, velocidade de busca, duração da busca, **ir para item em sucesso / falha** |
| **Mudança de imagem** (`pref_image_change_detection`) | área custom, "diferença de imagens" %, velocidade, duração, ir para em sucesso/falha; 1ª execução captura a referência |
| **Grupo de busca de imagens** (`pref_image_group`) | várias imagens/textos no mesmo frame, duração, script ao expirar |
| **Texto/OCR** (`pref_text_recognition`) | texto (vários separados por vírgula), idioma, limiar de confiança, **comparação numérica por faixa**, área, duração, ir para em sucesso/falha |
| **Controle: delay / ir para** (`pref_control`) | ir para item N, delay |
| **Controle: contador de execução** (`pref_control_execution_counter`) | até 10 casos (N execuções -> ir para item), caso padrão |
| **Controle: resetar contador** | seleciona qual contador |
| **Controle: arquivo de macro** (`pref_control_file`) | chama outro arquivo, "voltar ao arquivo anterior" ao fim |
| **Alerta de status / notificação** | som, vibração, pop-up, duração, texto |
| **Edição em lote** (`pref_touch_edit_selected`) | altera delay / imagem / skip de itens selecionados |
| **Grupo / loop** | "Loop within group"; erros: grupo selecionado, nenhum item, script sem detecção de imagem |

Conclusão de modelo: é uma **lista linear de itens com saltos condicionais** (`goto` por índice em sucesso/falha/contador). Não é uma árvore.

## 4. Configurações globais e UX (observado)
Iniciar macro com tecla de volume, parar ao bloquear a tela, manter tela ligada, ocultar o overlay durante o replay, mostrar toques, modo debug na tela, gravação de tela (limite de 3 min sem assinatura), screenshot, "monitoramento contínuo de tela" para reduzir a latência de partida, auto-start de macro padrão, transparência do overlay, backup incluindo ou não capturas.

## 5. Permissões -> equivalente no app novo
| Original | Decisão |
|---|---|
| Accessibility | **manter** (núcleo) |
| SYSTEM_ALERT_WINDOW | manter (bolha) |
| MediaProjection + FGS | manter, **opcional** (só visão) |
| RECORD_AUDIO + FGS microfone | **omitir** (sem uso confirmado) |
| SCHEDULE_EXACT_ALARM | manter, com fallback se negada |
| READ/WRITE_EXTERNAL_STORAGE | **trocar por SAF** (`ACTION_OPEN_DOCUMENT`) |
| INTERNET / BILLING | **omitir** |
| POST_NOTIFICATIONS | manter (FGS) |
| ADB tcpip / `app_process` | **omitir** (ver riscos) |

## 6. Riscos e pontos a verificar para Android 17 (API 37)
Não verificável offline: **confirmar no SDK 37 antes de afirmar**.
1. Accessibility: restrições para serviços que não se declaram ferramenta de acessibilidade (`isAccessibilityTool`) e modo de proteção avançada. Pode bloquear o app inteiro.
2. Foreground service types e MediaProjection: o consentimento por captura é re-pedido (single-app / token de uso único). Persistir o token não funciona.
3. Exact alarm: `SCHEDULE_EXACT_ALARM` é negado por padrão em apps novos. Tratar `canScheduleExactAlarms()`.
4. Overlay `TYPE_APPLICATION_OVERLAY` sobre apps com `FLAG_SECURE`/telas protegidas: o toque via gesto pode ser ignorado.
5. Caminho ADB/`app_process` de pasta gravável em `/data/local/tmp`: frágil e perigoso em versões novas. Fora do escopo.

## 7. Escopo proposto do projeto novo (sem bibliotecas externas)
MVP: toque/swipe/long-press, delay (fixo e aleatório), navegação, `goto` condicional, contador, loop em grupo, gravação, bolha, JSON via `org.json`, import/export via SAF, agendamento exato com fallback.
Fase 2 (decisão sua): casamento de imagem **sem OpenCV** (SAD/NCC sobre `Bitmap`, 1 escala) e OCR (**sem equivalente nativo**; não faremos).
