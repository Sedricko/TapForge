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

fun toneColor(e: Entry): Int = when (LogRules.tone(e)) {
    Tone.ERROR -> 0xFFEF5350.toInt()
    Tone.WARN -> 0xFFFFB74D.toInt()
    Tone.VALUE -> 0xFF64B5F6.toInt()
    Tone.SYSTEM -> 0xFF81C784.toInt()
    Tone.PLAIN -> C.TEXT
}

/** Texto para copiar ou compartilhar: ordem cronológica (as [rows] vêm da mais recente para a mais antiga). */
fun plainText(rows: List<Row>): String = rows.asReversed().joinToString("\n") {
    "${stampOf(it.entry)} [${it.entry.tag}] ${it.entry.text}" + if (it.count > 1) "  (×${it.count})" else ""
}

fun Context.logRow(r: Row): View = LinearLayout(this).apply {
    setPadding(dp(4), dp(5), dp(4), dp(5))
    addView(label(stampOf(r.entry), 12f, C.TEXT2).apply { typeface = Typeface.MONOSPACE })
    addView(label(r.entry.text + if (r.count > 1) "  ×${r.count}" else "", 14f, toneColor(r.entry)).apply {
        maxLines = 4; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.marginStart = dp(12) }
    })
}
