package app.macro17

import android.app.AlertDialog
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/**
 * AlertDialog com mensagem E lista: o AlertDialog padrão esconde a lista quando há mensagem (setMessage + setSingleChoiceItems
 * mostra só o texto), então a mensagem e as opções entram juntas num corpo próprio.
 */
fun Context.choiceDialog(title: String, message: String?, items: Array<String>, checked: Int, onPick: (Int) -> Unit): AlertDialog.Builder {
    var dlg: AlertDialog? = null
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8))
        if (message != null) addView(TextView(this@choiceDialog).apply { text = message; textSize = 14f; setTextColor(0xFF212121.toInt()); setPadding(0, 0, 0, dp(12)) })
        val group = RadioGroup(this@choiceDialog)
        items.forEachIndexed { i, t ->
            group.addView(RadioButton(this@choiceDialog).apply { id = 1000 + i; text = t; textSize = 16f; isChecked = i == checked; setPadding(dp(8), dp(10), 0, dp(10)) })
        }
        group.setOnCheckedChangeListener { _, id -> dlg?.dismiss(); onPick(id - 1000) }
        addView(group)
    }
    return object : AlertDialog.Builder(this) {
        override fun show(): AlertDialog = super.show().also { dlg = it }
    }.setTitle(title).setView(ScrollView(this).apply { addView(body) })
}

/** Caixas de marcação com mensagem; [onToggle] recebe o índice e o novo estado. */
fun Context.checkDialog(title: String, message: String?, items: Array<String>, on: BooleanArray, onToggle: (Int, Boolean) -> Unit): AlertDialog.Builder {
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8))
        items.forEachIndexed { i, t ->
            addView(CheckBox(this@checkDialog).apply { text = t; textSize = 16f; isChecked = on[i]; setPadding(dp(8), dp(10), 0, dp(10)); setOnCheckedChangeListener { _, v -> onToggle(i, v) } })
        }
        if (message != null) addView(TextView(this@checkDialog).apply { text = message; textSize = 13f; setTextColor(0xFF757575.toInt()); setPadding(0, dp(12), 0, 0) })
    }
    return AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(body) })
}
