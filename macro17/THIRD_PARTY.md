# Terceiros no Macro17

Regra do projeto: **nenhuma biblioteca externa como dependência**. Quando algo externo é necessário, ele é **copiado para este repositório**
(com origem, versão, licença e checksum) e o build usa a cópia. O build não baixa nenhuma biblioteca de repositório remoto.

## Código copiado para o repositório
| Item | Onde | Origem | Licença |
|---|---|---|---|
| Buscador de imagem (`Gray`, `TemplateMatcher`) | `app/src/main/java/app/macro17/Vision.kt` | `app/src/main/java/com/tapforge/vision/TemplateMatcher.kt`, neste mesmo repositório | do próprio projeto |

## Binários copiados para `libs/` (conferidos pelo SHA-1 publicado no Maven Central)
| Arquivo | Uso | Licença | SHA-1 |
|---|---|---|---|
| `kotlin-stdlib-2.1.0.jar` | runtime do Kotlin no app | Apache-2.0 | `85f8b81009cda5890e54ba67d64b5e599c645020` |
| `annotations-13.0.jar` | anotações exigidas pelo stdlib | Apache-2.0 | `919f0dfe192fb4e063e7dacadee7f8bb9a2672a9` |
| `junit-4.13.2.jar` | só testes | EPL-1.0 | `8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12` |
| `hamcrest-core-1.3.jar` | só testes (dependência do JUnit) | BSD-3-Clause | `42a25dc3219429f0e5d060061f71acb49bf010a0` |

Origem: `https://repo1.maven.org/maven2/<grupo>/<artefato>/<versão>/`. Os `.pom` ao lado dos jars declaram licença e coordenadas.
Textos completos das licenças: https://www.apache.org/licenses/LICENSE-2.0 · https://www.eclipse.org/legal/epl-v10.html · https://opensource.org/license/bsd-3-clause

Para conferir: `sha1sum libs/*.jar` e comparar com a tabela.

## O que continua vindo de fora (ferramentas, não código do app)
Gradle (baixado pelo wrapper), plugin Android do Gradle, compilador do Kotlin e Android SDK. São a cadeia de build: nada disso entra no APK além do que
o compilador gera. Copiar a cadeia de build inteira para o repositório (centenas de MB) não vale o custo; fixar as versões já está feito.

## Não usado, de propósito
OpenCV, ML Kit e OCR de terceiros: fora, pela regra acima. A busca de imagem usa o buscador em Kotlin puro. OCR só entraria como código-fonte copiado
para o repositório (ex.: uma implementação própria ou open source com licença compatível), e hoje não está no escopo.
