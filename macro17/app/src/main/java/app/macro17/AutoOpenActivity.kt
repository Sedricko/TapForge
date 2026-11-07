package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Início Automático (list_row_auto_open do base): app à esquerda, nome da macro em verde, marca rosa quando há macro associada. */
class AutoOpenActivity : Activity() {
    private lateinit var store: Store
    private lateinit var apps: List<android.content.pm.ResolveInfo>

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val pm = packageManager
        apps = pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val list = ListView(this).apply {
            setBackgroundColor(0xFFFAFAFA.toInt()); dividerHeight = 1
            adapter = object : BaseAdapter() {
                override fun getCount() = apps.size
                override fun getItem(i: Int) = apps[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, v: View?, p: ViewGroup): View {
                    val ri = apps[i]; val pkg = ri.activityInfo.packageName
                    val mac = store.autoOpen[pkg]?.let { store.get(it) }
                    return LinearLayout(this@AutoOpenActivity).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(ImageView(this@AutoOpenActivity).apply { setImageDrawable(ri.loadIcon(pm)) }, LinearLayout.LayoutParams(dp(50), dp(50)).also { it.setMargins(dp(10), dp(10), 0, dp(10)) })
                        addView(LinearLayout(this@AutoOpenActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(label(ri.loadLabel(pm).toString(), 16f, Color.BLACK))
                            if (mac != null) addView(label(mac.name, 16f, 0xFF008577.toInt()))
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.marginStart = dp(16) })
                        if (mac != null) addView(ImageView(this@AutoOpenActivity).apply { setImageResource(R.drawable.bp_ic_done_24px); setColorFilter(0xFFD81B60.toInt()) }, LinearLayout.LayoutParams(dp(24), dp(24)).also { it.marginEnd = dp(10) })
                    }
                }
            }
            setOnItemClickListener { _, _, i, _ -> choose(apps[i].activityInfo.packageName) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
            addView(appBar("Início Automático", Ic.BACK, { finish() }))
            addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }

    private fun choose(pkg: String) {
        val ms = store.list()
        if (ms.isEmpty()) { Toast.makeText(this, "Nenhum arquivo de macro. Crie um novo.", Toast.LENGTH_LONG).show(); return }
        val names = (listOf("Nenhum") + ms.map { it.name }).toTypedArray()
        var sel = ms.indexOfFirst { it.id == store.autoOpen[pkg] }.let { if (it < 0) 0 else it + 1 }
        // Diálogo do base: título, mensagem no corpo acima da lista de escolha única (atual marcada) e Cancelar/Salvar (só grava em Salvar).
        // Não usa choiceDialog: ele fecha ao escolher, e aqui a escolha só vale no Salvar.
        val group = RadioGroup(this)
        names.forEachIndexed { i, t -> group.addView(RadioButton(this).apply { id = 1000 + i; text = t; textSize = 16f; isChecked = i == sel; setPadding(dp(8), dp(10), 0, dp(10)) }) }
        group.setOnCheckedChangeListener { _, cid -> sel = cid - 1000 }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(TextView(this@AutoOpenActivity).apply { text = "Selecione a macro padrão para usar com o programa."; textSize = 16f; setTextColor(0xFF9E9E9E.toInt()); setPadding(dp(10), dp(15), 0, dp(8)) })
            addView(group)
        }
        AlertDialog.Builder(this).setTitle("Início Automático").setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                store.autoOpen = store.autoOpen.toMutableMap().also { if (sel == 0) it.remove(pkg) else it[pkg] = ms[sel - 1].id }
                recreate()
            }.show()
    }
}
