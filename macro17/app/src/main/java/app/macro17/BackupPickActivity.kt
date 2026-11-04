package app.macro17

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Toast

/** Seleção das macros do backup (tela própria, como no base): checkboxes pré-marcados, menu Selecionar/Desmarcar tudo e rodapé com dois botões. */
class BackupPickActivity : Activity() {
    private val boxes = ArrayList<Pair<String, CheckBox>>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val ms = Store(this).list()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ms.forEach { m ->
            val cb = CheckBox(this).apply { text = m.name; textSize = 16f; setTextColor(B.TEXT); isChecked = true; buttonTintList = android.content.res.ColorStateList.valueOf(0xFFD81B60.toInt()); setPadding(dp(16), dp(12), dp(16), dp(12)) }
            boxes += m.id to cb; col.addView(cb, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        val more = glyph(Ic.MORE, Color.WHITE, 40).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12)); foreground = ripple(24, 0x33FFFFFF); contentDescription = "Mais opções"; layoutParams = LinearLayout.LayoutParams(dp(48), dp(56))
            setOnClickListener {
                val pm = PopupMenu(this@BackupPickActivity, this)
                pm.menu.add(0, 0, 0, "Selecionar tudo"); pm.menu.add(0, 1, 1, "Desmarcar tudo")
                pm.setOnMenuItemClickListener { boxes.forEach { (_, c) -> c.isChecked = it.itemId == 0 }; true }; pm.show()
            }
        }
        fun pill(text: String, click: () -> Unit) = label(text, 16f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER; background = rounded(B.PRIMARY, 28); setOnClickListener { click() } }
        val footer = LinearLayout(this).apply {
            setPadding(dp(5), dp(5), dp(5), dp(5))
            addView(pill("Backup de Dados") {
                val ids = boxes.filter { it.second.isChecked }.map { it.first }
                if (ids.isEmpty()) Toast.makeText(this@BackupPickActivity, "Por favor, selecione um arquivo.", Toast.LENGTH_SHORT).show()
                else { setResult(RESULT_OK, Intent().putExtra("ids", ids.toTypedArray())); finish() }
            }, LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(5), dp(5), dp(5), dp(5)) })
            addView(pill("Cancelar") { finish() }, LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(5), dp(5), dp(5), dp(5)) })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
            addView(appBar("Backup de Dados", Ic.BACK, { finish() }, more))
            addView(ScrollView(this@BackupPickActivity).apply { addView(col) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(footer.also { f -> f.setOnApplyWindowInsetsListener { v, i -> v.setPadding(dp(5), dp(5), dp(5), dp(5) + i.getInsets(WindowInsets.Type.navigationBars()).bottom); i } })
        })
    }
}
