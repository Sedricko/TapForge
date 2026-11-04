package app.macro17

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Apresentação do registro, comum à aba de monitoramento e ao painel flutuante. */
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

fun stampOf(e: Entry): String = timeFmt.format(Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()))

fun toneColor(e: Entry, light: Boolean = false): Int = when (LogRules.tone(e)) {
    Tone.ERROR -> if (light) 0xFFF44336.toInt() else 0xFFEF5350.toInt()
    Tone.WARN -> if (light) 0xFFEF6C00.toInt() else 0xFFFFB74D.toInt()
    Tone.VALUE -> if (light) 0xFF1976D2.toInt() else 0xFF64B5F6.toInt()
    Tone.SYSTEM -> if (light) 0xFF2E7D32.toInt() else 0xFF81C784.toInt()
    Tone.PLAIN -> if (light) 0xDE000000.toInt() else C.TEXT
}

/** Texto para copiar ou compartilhar: ordem cronológica (as [rows] vêm da mais recente para a mais antiga). */
fun plainText(rows: List<Row>): String = rows.asReversed().joinToString("\n") {
    "${stampOf(it.entry)} [${it.entry.tag}] ${it.entry.text}" + if (it.count > 1) "  (×${it.count})" else ""
}

/** [light] = aparência das telas do app (fundo claro); o painel flutuante usa a escura. */
fun Context.logRow(r: Row, light: Boolean = false): View = LinearLayout(this).apply {
    setPadding(dp(4), dp(5), dp(4), dp(5))
    addView(label(stampOf(r.entry), 12f, if (light) 0xFF757575.toInt() else C.TEXT2).apply { typeface = Typeface.MONOSPACE })
    addView(label(r.entry.text + if (r.count > 1) "  ×${r.count}" else "", 14f, toneColor(r.entry, light)).apply {
        maxLines = 4; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.marginStart = dp(12) }
    })
}
