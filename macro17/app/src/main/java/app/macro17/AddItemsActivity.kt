package app.macro17

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView

/** "Adicionar Itens" do base: linhas com ícone, título e descrição; devolve o tipo escolhido em "type". */
class AddItemsActivity : Activity() {
    private class Entry(val type: Type, val ic: Ic, val title: String, val desc: String, val mono: Boolean = false)

    // Ordem do base para o que o Macro17 já executa; os extras do Macro17 ficam no fim. Faltam (ainda sem executor): Grupo,
    // Grupo de Pesquisa de Imagem, Detecção de Mudança de Imagem e Parar Macro.
    private val entries = listOf(
        Entry(Type.GROUP, Ic.REPEAT, "Grupo", "Agrupa itens para repeti-los juntos."),
        Entry(Type.GROUP, Ic.IMAGE_SEARCH, "Grupo de Pesquisa de Imagem", "Procura várias imagens ou textos no mesmo quadro capturado.", mono = true),
        Entry(Type.TEXT, Ic.NUM, "Reconhecimento de Texto", "Reconhece texto na tela e encontra o mesmo texto."),
        Entry(Type.CHANGE, Ic.IMAGE_SEARCH, "Detecção de Mudança de Imagem", "Captura uma imagem na primeira execução e verifica continuamente se ela mudou da imagem previamente capturada."),
        Entry(Type.GOTO, Ic.JUMP, "Ir para", "Ir para uma macro específica"),
        Entry(Type.COUNTER, Ic.REPEAT, "Contador de Execuções", "Conta as execuções da macro e aciona ações específicas quando a contagem de execuções excede um número definido."),
        Entry(Type.RESET, Ic.REPEAT, "Redefinir Contador de Execuções", "Selecione o contador de execuções para redefinir."),
        Entry(Type.STOP, Ic.STOP, "Parar Macro", "Parar reprodução da macro"),
        Entry(Type.CALL, Ic.ARCHIVE, "Arquivo de Macro", "Abrir e reproduzir novo arquivo"),
        Entry(Type.STATUS, Ic.WARN, "Alertas de status", "Progresso da macro de alerta via som, vibração ou caixa de diálogo pop-up."),
        Entry(Type.KEY, Ic.KEY, "Evento de Teclado", "Realiza ações de captura de tela, tecla de voltar, tecla home e tecla de recentes."),
        Entry(Type.TAP, Ic.TAP, "Toque", "Toca em um ponto da tela."),
        Entry(Type.SWIPE, Ic.SWIPE, "Deslizar", "Desliza de um ponto a outro."),
        Entry(Type.WAIT, Ic.WAIT, "Esperar", "Aguarda um tempo antes do próximo item."),
        Entry(Type.PINCH_IN, Ic.SWIPE, "Pinça para dentro", "Aproxima dois dedos."),
        Entry(Type.PINCH_OUT, Ic.SWIPE, "Pinça para fora", "Afasta dois dedos."),
        Entry(Type.NODE, Ic.SEARCH, "Achar por texto", "Procura um elemento da tela pelo texto ou id."),
        Entry(Type.NUMBER, Ic.NUM, "Ler número", "Lê um número da tela com o leitor treinado."),
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        entries.forEach { e ->
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                foreground = ripple(0, 0x1F000000)
                addView(LinearLayout(this@AddItemsActivity).apply {
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    addView(glyph(e.ic, 0xFF607D8B.toInt(), 24))
                    addView(LinearLayout(this@AddItemsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(32), 0, 0, 0)
                        addView(label(e.title, 16f, Color.BLACK))
                        addView(label(e.desc, 16f, 0xFF9E9E9E.toInt()))
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                })
                addView(View(this@AddItemsActivity).apply { setBackgroundColor(0x1E000000) }, LinearLayout.LayoutParams(MATCH_PARENT, 1))
                setOnClickListener { setResult(RESULT_OK, Intent().putExtra("type", e.type.name).putExtra("mono", e.mono)); finish() }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(B.BG)
            addView(appBar("Adicionar Itens", Ic.BACK, { finish() }))
            addView(ScrollView(this@AddItemsActivity).apply {
                addView(col)
                setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
            }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }
}
