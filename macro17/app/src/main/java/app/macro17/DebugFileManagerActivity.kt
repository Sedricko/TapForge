package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** "Arquivo de Depuração" do base: lista dos arquivos de depuração (um por registro) com Abrir, Compartilhar e Eliminar. */
class DebugFileManagerActivity : Activity() {
    private lateinit var list: LinearLayout
    private var marked: File? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0) }
        val more = glyph(Ic.MORE, Color.WHITE, 40).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12)); foreground = ripple(24, 0x33FFFFFF); contentDescription = "Mais opções"
            setOnClickListener { menu(this) }; layoutParams = LinearLayout.LayoutParams(dp(48), dp(56))
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(LinearLayout(this@DebugFileManagerActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(appBar("Arquivo de Depuração", Ic.BACK, { finish() }, more))
                addView(ScrollView(this@DebugFileManagerActivity).apply {
                    addView(list)
                    setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
                }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        })
    }

    override fun onResume() { super.onResume(); render() }

    private fun files() = File(filesDir, "diag").listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }

    private fun render() {
        list.removeAllViews()
        val fs = files()
        if (marked !in fs) marked = null
        if (fs.isEmpty()) list.addView(label("Nenhum arquivo de depuração.", 14f, B.TEXT2).apply { setPadding(dp(16), dp(16), dp(16), 0) })
        fs.forEach { f ->
            list.addView(RadioButton(this).apply {
                text = "${f.name}  (${f.length() / 1024} KB)"; textSize = 16f; setTextColor(B.TEXT); isChecked = f == marked
                layoutDirection = View.LAYOUT_DIRECTION_RTL; textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                buttonTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0xFF00574B.toInt(), 0x8A000000.toInt()))
                setPadding(dp(16), dp(12), dp(16), dp(12)); setOnClickListener { marked = f; render() }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            list.addView(View(this).apply { setBackgroundColor(0x1E000000) }, LinearLayout.LayoutParams(MATCH_PARENT, 1))
        }
    }

    private fun menu(anchor: View) {
        val acts = listOf<Pair<String, () -> Unit>>("Abrir" to { needMark { open(it) } }, "Compartilhar" to { needMark { share(it) } }, "Excluir" to { needMark { delete(it) } }, "Monitor ao vivo" to { startActivity(Intent(this, DiagActivity::class.java)) })
        val pm = android.widget.PopupMenu(this, anchor)
        acts.forEachIndexed { k, a -> pm.menu.add(0, k, k, a.first) }
        pm.setOnMenuItemClickListener { acts[it.itemId].second(); true }
        pm.show()
    }

    private fun needMark(go: (File) -> Unit) { val f = marked; if (f == null) AlertDialog.Builder(this).setMessage("Por favor, selecione um arquivo.").setPositiveButton("OK", null).show() else go(f) }

    private fun open(f: File) {
        val tv = TextView(this).apply { text = f.readText().takeLast(60_000); textSize = 11f; typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        AlertDialog.Builder(this).setTitle(f.name).setView(ScrollView(this).apply { addView(tv) }).setPositiveButton("OK", null).show()
    }

    private fun share(f: File) {
        val text = f.readText().takeLast(200_000)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, f.name).putExtra(Intent.EXTRA_TEXT, text), "Compartilhar"))
    }

    private fun delete(f: File) = AlertDialog.Builder(this).setMessage("Deseja excluir?")
        .setPositiveButton("Excluir") { _, _ -> if (!f.delete()) Toast.makeText(this, "Não foi possível excluir", Toast.LENGTH_SHORT).show(); marked = null; render() }
        .setNegativeButton("Cancelar", null).show()
}
