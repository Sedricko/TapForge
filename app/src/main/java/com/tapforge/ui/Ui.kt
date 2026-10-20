package com.tapforge.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tapforge.R
import com.tapforge.model.Macro
import com.tapforge.model.Row
import com.tapforge.model.Trigger
import com.tapforge.model.flatten

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun ViewGroup.section(text: String): TextView =
    (LayoutInflater.from(context).inflate(R.layout.item_section, this, false) as TextView).also { it.text = text; addView(it) }

/** Cartão com ícone (emoji), título, descrição, botão de ação e botão de ícone no fim, todos opcionais. */
fun ViewGroup.card(
    icon: String, title: String, desc: String? = null,
    action: String? = null, onAction: (() -> Unit)? = null,
    endIcon: Int? = null, endLabel: String? = null, onEnd: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
): View {
    val v = LayoutInflater.from(context).inflate(R.layout.item_card, this, false)
    v.findViewById<TextView>(R.id.icon).text = icon
    v.findViewById<TextView>(R.id.title).text = title
    v.findViewById<TextView>(R.id.desc).apply { text = desc; visibility = if (desc.isNullOrBlank()) View.GONE else View.VISIBLE }
    if (action != null) v.findViewById<MaterialButton>(R.id.action).apply { visibility = View.VISIBLE; text = action; setOnClickListener { onAction?.invoke() } }
    if (endIcon != null) v.findViewById<MaterialButton>(R.id.end).apply {
        visibility = View.VISIBLE; setIconResource(endIcon); contentDescription = endLabel; setOnClickListener { onEnd?.invoke() }
    }
    if (onClick != null) v.setOnClickListener { onClick() }
    addView(v)
    return v
}

/** Linha de ajuste com título, explicação e, se [checked] não for nulo, um interruptor. */
fun ViewGroup.setting(title: String, desc: String, checked: Boolean? = null, onToggle: ((Boolean) -> Unit)? = null, onClick: (() -> Unit)? = null): View {
    val v = LayoutInflater.from(context).inflate(R.layout.item_setting, this, false)
    v.findViewById<TextView>(R.id.title).text = title
    v.findViewById<TextView>(R.id.desc).text = desc
    if (checked != null) {
        val sw = v.findViewById<MaterialSwitch>(R.id.toggle)
        sw.visibility = View.VISIBLE; sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, b -> onToggle?.invoke(b) }
        v.setOnClickListener { sw.toggle() }
        sw.contentDescription = title
    } else if (onClick != null) v.setOnClickListener { onClick() }
    addView(v)
    return v
}

/** Campo de texto com rótulo e explicação. */
fun ViewGroup.field(label: String, value: String, help: String? = null, type: Int = InputType.TYPE_CLASS_TEXT, onChange: (String) -> Unit = {}): TextInputEditText {
    val til = TextInputLayout(context).apply {
        hint = label; helperText = help
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) }
    }
    val et = TextInputEditText(til.context).apply {
        setText(value); inputType = type
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { onChange(s?.toString() ?: "") }
        })
    }
    til.addView(et)
    addView(til)
    return et
}

fun Context.vertical(padding: Int = 20) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; setPadding(dp(padding), dp(8), dp(padding), 0)
}

fun Context.scrolling(inner: View) = androidx.core.widget.NestedScrollView(this).apply { addView(inner) }

/** Texto monoespaçado para JSON e registro. */
fun Context.mono(text: String) = TextView(this).apply {
    this.text = text; typeface = Typeface.MONOSPACE; textSize = 12f; setTextIsSelectable(true)
}

fun Context.info(title: String, message: String) =
    MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()

fun Context.confirm(title: String, message: String, yes: String, onYes: () -> Unit) =
    MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message)
        .setNegativeButton("Cancelar", null).setPositiveButton(yes) { _, _ -> onYes() }.show()

fun Context.appLabel(pkg: String): String = runCatching {
    @Suppress("DEPRECATION") packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

/** Apps que aparecem no inicializador: (pacote, nome). */
fun Context.launchableApps(): List<Pair<String, String>> {
    @Suppress("DEPRECATION")
    val list = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
    return list.map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
        .distinctBy { it.first }.filter { it.first != packageName }.sortedBy { it.second.lowercase() }
}

fun Context.pickApp(onPick: (String) -> Unit) {
    val apps = launchableApps()
    MaterialAlertDialogBuilder(this).setTitle("Escolher app")
        .setItems(apps.map { it.second }.toTypedArray()) { _, i -> onPick(apps[i].first) }
        .setNegativeButton("Cancelar", null).show()
}

fun Context.pickApps(selected: Set<String>, onDone: (Set<String>) -> Unit) {
    val apps = launchableApps()
    val checked = BooleanArray(apps.size) { apps[it].first in selected }
    MaterialAlertDialogBuilder(this).setTitle("Apps bloqueados")
        .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, b -> checked[i] = b }
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Salvar") { _, _ -> onDone(apps.filterIndexed { i, _ -> checked[i] }.map { it.first }.toSet()) }.show()
}

fun actionCount(m: Macro) = flatten(m.actions).count { it is Row.Item }

fun Context.triggerText(t: Trigger?): String? = when (t) {
    null -> null
    is Trigger.AppOpened -> "Ao abrir ${appLabel(t.packageName)}"
    Trigger.Charging -> "Ao conectar o carregador"
    is Trigger.Schedule -> "Todo dia às ${t.time}"
}

fun limitsText(m: Macro): String = listOf(
    if (m.maxIterations > 0) "${m.maxIterations} repetições" else null,
    if (m.maxRuntimeMs > 0) "para após ${m.maxRuntimeMs / 1000}s" else null,
).filterNotNull().ifEmpty { listOf("repete até você parar") }.joinToString(" · ")
