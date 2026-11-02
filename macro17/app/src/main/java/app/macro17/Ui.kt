package app.macro17

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout

fun Activity.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** targetSdk 35+ força edge-to-edge: sem isto o conteúdo fica sob a barra de status e a de navegação. */
fun View.padForSystemBars(extra: Int) {
    setOnApplyWindowInsetsListener { v, i ->
        val b = i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
        v.setPadding(extra + b.left, extra + b.top, extra + b.right, extra + b.bottom)
        WindowInsets.CONSUMED
    }
}

fun Activity.button(label: String, click: () -> Unit) = Button(this).apply {
    text = label
    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    setOnClickListener { click() }
}
