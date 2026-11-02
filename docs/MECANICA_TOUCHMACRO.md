# Mecânica de execução do Touch Macro Pro 4.2.7 (para replicar no Macro17)

Escopo: **comportamento**, para reimplementação própria (clean-room). Nenhum código, recurso ou formato de arquivo dele é copiado.
Fora de escopo, de propósito: verificação de licença, limites de uso por assinatura (tempo máximo, contagem de execuções), integridade do app.

Método: decompilação dos métodos `l2/c.s(I2/m)` (executa um item) e `M2/M.run()` (laço de reprodução), mais strings das bibliotecas nativas.
O código está ofuscado e o decompilador mistura tipos e constantes de ponto flutuante; por isso cada ponto leva um rótulo.
**[O]** observado no código, **[I]** inferido, **[?]** não resolvido.

## 1. Duas formas de reproduzir

| Caminho | Como funciona | Observação |
|---|---|---|
| **Macro por itens** | Lista de itens interpretada em Java. Cada item vira um gesto de acessibilidade (`dispatchGesture`) ou uma ação global. | É o núcleo e o que vamos replicar. **[O]** |
| **Replay de gravação bruta** | `libreplay_exec` lê um arquivo de eventos de entrada gravados (`Replay Action`, `Gesture={"Motion":…}`) e reproduz com velocidade e atraso configuráveis. | Exige privilégio de shell/ADB (`Fail to open key device`). Fora do escopo. **[O]** |

Visão: captura de tela por socket local, OpenCV para casamento de imagem e um modelo ONNX para OCR (`crnn_cs.onnx`). Fica para a fase 2.

## 2. Modelo do item **[O]**
Campos lidos pelo executor: tipo, coordenadas `(i,j)` e `(k,l)`, duração `n` (ms), atraso `m` (s) com chave liga/desliga, faixa de atraso aleatório `(min,max)` com flag, raio de jitter `D` com flag, **pular** (`B`), **relativo à imagem achada** (`q`), alvo em caso de sucesso (`z`) e de falha (`y`), id do contador (`F`), lista de casos do contador, nome de arquivo de macro e flag **voltar ao anterior**.

## 3. Executar um item (`l2/c.s`) **[O]**
1. **Jitter.** Se ligado e `D>0`, sorteia **um** deslocamento `(dx,dy)` em `[-D, D)` e soma aos **dois** pontos do item. O gesto inteiro se move, o comprimento não muda.
2. **Relativo à imagem.** Se ligado e há acerto anterior, soma o deslocamento do acerto às coordenadas. Em toque, o ponto final vira o inicial.
3. **Pular.** Se `B`, não faz nada e devolve "sem ação".
4. **Por tipo:**
   - *toque* (tipos 0, 6, 17): caminho de um ponto, duração `n`.
   - *deslizar* (1, 2): `(i,j)→(k,l)`, duração `n`.
   - *pinça para dentro* (4): dois dedos, de cada ponta até o ponto médio.
   - *pinça para fora* (5): dois dedos, do ponto médio até cada ponta.
   - *tecla* (15): Home, Voltar, Recentes e captura de tela por ação global.
   - *alerta de status* (3): mostra mensagem e espera `n` ms.
   - *arquivo de macro* (13): troca de arquivo; sinaliza código 6.
   - *OCR* (14): assíncrono; sinaliza código 9 e espera retorno.
   - tipos 10/11/19: não são ação; o laço trata.
   - tipo desconhecido: erro.
5. **Códigos de retorno:** 0 ok · 1 pulado/sem ação · 6 trocar arquivo · 9 aguardar assíncrono · `-2` tipo desconhecido · `-4` gesto rejeitado · `-5` exceção · `-6` falha ao abrir arquivo · `-7` tipo não suportado.

## 4. Laço de reprodução (`M2/M.run`) **[O]** com estados **[I]**
Máquina de estados; o fluxo por item é:

```
mover para o item  →  [busca, se o item tem imagem/texto]  →  atraso antes da ação  →  ação  →  espera a duração  →  escolher o próximo
```

- **Atraso antes da ação** (`config_switch_delay`): fatiado em passos de 100 ms, checando parada e pausa a cada fatia. Com faixa aleatória ligada, o atraso é sorteado entre `min` e `max` e **substitui** o fixo (a tela diz: "o atraso acima será ignorado").
- **Depois da ação (código 0):** dorme `n` ms, a duração do gesto. Para código 9, espera o retorno por até `n` ms (0 = sem limite).
- **Busca (imagem/texto):** repete a comparação a cada `X` ms (padrão 200). Pontuação em % = `(1 − diferença normalizada) × 100`. Limiar `A`: se `A ≤ 99` vale `max(A, 20)`, senão 100. Sucesso quando a pontuação passa o limiar. Duração `x` em segundos limita a busca; sem sucesso no prazo, é falha.
- **Escolher o próximo item:**
  - item normal: sucesso segue `z`, falha segue `y`, ausência de alvo segue o próximo da lista;
  - `goto` ("ir para o item"): segue `z`;
  - **contador de execução:** soma 1 ao contador `F`; percorre a lista de casos `(limiar, alvo)` e fica com o último cujo limiar o contador alcançou; sem caso válido usa o alvo padrão; **zera** o contador ao atingir o limiar do primeiro caso da lista **[I: ordem da lista não confirmada]**;
  - **zerar contador:** põe `F` em 0;
  - alvo inválido: encerra com erro (`-1`);
  - **fim da lista:** conta uma volta; se há limite de repetições e foi atingido, encerra com sucesso; senão recomeça no primeiro item.
- **Arquivo de macro (13):** com "voltar ao anterior" ligado, **empilha** um novo executor (com contadores próprios); ao terminar, desempilha e retoma o chamador. Desligado, **substitui** a macro atual.
- **Parada e pausa:** checadas entre fatias de espera e entre itens. Interrupção da thread encerra com motivo "parado pelo usuário".
- **Log de salto:** cada salto registra `nome do item → nome do destino`.

## 5. Gestos e entrada **[O]**
- Até **dois dedos** simultâneos por gesto (quatro pontos = duas trilhas).
- Toque = trilha de um ponto; toque longo e deslizar diferem só na duração.
- Duração de 500 ms ou mais desenha o marcador "mostrar toques" em outra thread; menos que isso, direto.
- Se o modo shell está ativo, o mesmo item vira evento de entrada de shell em vez de gesto de acessibilidade.

## 6. O que o Macro17 replica agora (sem visão)
| Mecânica | Decisão |
|---|---|
| Toque, deslizar, pinça dentro/fora, tecla, alerta | **Replicar** |
| Atraso **antes** da ação, fixo ou faixa aleatória (a faixa substitui o fixo) | **Replicar** (muda de "pausa depois" para "atraso antes") |
| Jitter: um `(dx,dy)` para o gesto inteiro | **Replicar** |
| Pular ação | **Replicar** |
| Ir para o passo, contador (casos e zerar), repetições da lista | **Replicar** |
| Arquivo de macro com voltar/substituir e pilha, contadores por executor | **Replicar** |
| Motivos de parada tipados (concluída, parada, alvo inválido, gesto rejeitado) | **Replicar** |
| Busca de imagem/texto, relativo à imagem, sucesso/falha | **Fase 2** (campos `z`/`y` ficam para ela) |
| Replay bruto por shell, modo ADB | **Fora** |
| Licença, limites de assinatura | **Fora** |

Itens **[?]**: ordem exata dos casos do contador; semântica de `x ≤ 0` na duração da busca; o significado dos tipos 12 e 16 (grupo e captura).
