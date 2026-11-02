# Terceiros no Macro17

Regra do projeto: o que vem de terceiros é **copiado para este repositório** (com origem, versão e licença) e o build usa a cópia: o build não baixa
biblioteca de repositório remoto. (A restrição "sem bibliotecas externas" do pedido original foi suspensa; a de ter tudo no repositório continua.)

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

## Repositório Maven local `maven-repo/` (OpenCV e ML Kit)
Montado por `tools/resolve-deps.py` a partir do Maven Central e do Maven do Google, com POM e Gradle Module Metadata de cada módulo. O Gradle lê
os 39 grupos listados em `settings.gradle.kts` **somente daqui** (`exclusiveContent`): faltando qualquer peça, o build falha. Isso é o que prova que a cópia está completa.
Para regenerar: `python3 tools/resolve-deps.py maven-repo org.opencv:opencv:4.9.0 com.google.mlkit:text-recognition:16.0.1`.

Os AARs foram **reempacotados**: só a arquitetura `arm64-v8a` e, no OpenCV, sem o pacote C++ `prefab/` (cabeçalhos e bibliotecas de todas as arquiteturas, que o app não usa). Por isso os checksums deles diferem dos do Maven.

| Módulo | Uso | Licença |
|---|---|---|
| `org.opencv:opencv:4.9.0` | busca de imagem (`matchTemplate`) | Apache-2.0 |
| `com.google.mlkit:text-recognition:16.0.1` e dependências (`com.google.mlkit`, `com.google.android.gms`, `com.google.android.odml`, `com.google.firebase`, `com.google.android.datatransport`) | texto na tela (OCR) | **Termos do ML Kit / Google APIs** (proprietário; não é código aberto) |
| `androidx.*`, `com.google.guava:listenablefuture`, `javax.inject` | dependências transitivas | Apache-2.0 |

**Atenção sobre o ML Kit:** os módulos `com.google.mlkit` e `com.google.android.gms` são distribuídos pelo Google sob termos próprios, não sob licença de código aberto.
Guardar cópias deles no repositório é uma escolha sua; confira se os termos permitem isso para o seu uso, principalmente se o repositório for público ou for distribuído.

## O que continua vindo de fora (ferramentas, não código do app)
Gradle (baixado pelo wrapper), plugin Android do Gradle, compilador do Kotlin e Android SDK. São a cadeia de build: nada disso entra no APK além do que
o compilador gera. Copiar a cadeia de build inteira para o repositório (centenas de MB) não vale o custo; fixar as versões já está feito.


