package app.macro17

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ListView
import android.widget.Toast

/** Seleção das macros do backup (tela própria, como no base): checkboxes pré-marcados, menu Selecionar/Desmarcar tudo e rodapé com dois botões. */
class BackupPickActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val ms = Store(this).list()
        val lv = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = ArrayAdapter(this@BackupPickActivity, android.R.layout.simple_list_item_multiple_choice, ms.map { it.name })
            divider = android.graphics.drawable.ColorDrawable(0x1E000000); dividerHeight = 1
            for (k in ms.indices) setItemChecked(k, true)
        }
        val more = glyph(Ic.MORE, Color.WHITE, 40).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12)); foreground = ripple(24, 0x33FFFFFF); contentDescription = "Mais opções"; layoutParams = LinearLayout.LayoutParams(dp(48), dp(56))
            setOnClickListener {
                val pm = PopupMenu(this@BackupPickActivity, this)
                pm.menu.add(0, 0, 0, "Selecionar tudo"); pm.menu.add(0, 1, 1, "Desmarcar tudo")
                pm.setOnMenuItemClickListener { for (k in ms.indices) lv.setItemChecked(k, it.itemId == 0); true }; pm.show()
            }
        }
        fun pill(text: String, click: () -> Unit) = label(text, 16f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER; background = rounded(B.PRIMARY, 28); setOnClickListener { click() } }
        val footer = LinearLayout(this).apply {
            setPadding(dp(15), dp(15), dp(15), dp(15))
            addView(pill("Backup de Dados") {
                val ids = ms.filterIndexed { k, _ -> lv.isItemChecked(k) }.map { it.id }
                if (ids.isEmpty()) Toast.makeText(this@BackupPickActivity, "Por favor, selecione um arquivo.", Toast.LENGTH_LONG).show()
                else { setResult(RESULT_OK, Intent().putExtra("ids", ids.toTypedArray())); finish() }
            }, LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(5), dp(5), dp(5), dp(5)) })
            addView(pill("Cancelar") { finish() }, LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(5), dp(5), dp(5), dp(5)) })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
            addView(appBar("Selecionar Arquivos de Backup", Ic.BACK, { finish() }, more))
            addView(lv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).also { it.topMargin = dp(10) })
            addView(footer.also { f -> f.setOnApplyWindowInsetsListener { v, i -> v.setPadding(dp(15), dp(15), dp(15), dp(15) + i.getInsets(WindowInsets.Type.navigationBars()).bottom); i } })
        })
    }
}
