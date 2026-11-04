package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast

/** Tela acesa por 1 s ao disparar um alarme (o base mostra "INICIAR" em vermelho). */
class FullScreenActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(label("INICIAR", 70f, 0xFFCC0000.toInt(), bold = true).apply {
                setShadowLayer(8f, 0f, 0f, 0xFFFF0000.toInt())
            }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        })
        window.decorView.postDelayed({ finish() }, 1000)
    }
}

class AlarmListActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var store: Store

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(80)) }
        val fab = glyph(Ic.ADD, Color.WHITE, 24).apply {
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(B.PINK) }
            elevation = dp(6).toFloat(); setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { startActivity(Intent(this@AlarmListActivity, AlarmEditActivity::class.java)) }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(0xFFF5F5F5.toInt())
            addView(LinearLayout(this@AlarmListActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(appBar("Agendamento", Ic.BACK, { finish() }))
                addView(ScrollView(this@AlarmListActivity).apply {
                    addView(list)
                    setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
                }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(fab, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END or Gravity.BOTTOM).also { it.setMargins(0, 0, dp(16), dp(16)) })
        })
    }

    override fun onResume() { super.onResume(); render() }

    private fun permissionDialog() = AlertDialog.Builder(this).setTitle("Permissão necessária")
        .setMessage("Para iniciar no horário exato, permita “Alarmes e lembretes” para este app.")
        .setPositiveButton("Abrir") { _, _ -> startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
        .setNegativeButton("Cancelar", null).show()

    private fun render() {
        list.removeAllViews()
        val all = store.alarms()
        list.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(0xFFFFF3C8.toInt(), 12); elevation = dp(2).toFloat()
            addView(glyph(Ic.WARN, 0xFFFFB300.toInt(), 32))
            addView(label("O serviço de agendamento precisa ser executado em segundo plano quando a tela estiver desligada. Permita que o aplicativo use mais energia em segundo plano.", 14f, B.TEXT).apply { setPadding(dp(12), 0, 0, 0) })
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(8) }
        })
        for (a in all) {
            val macro = store.get(a.macroId)
            val bad = a.enabled && Alarms.conflict(a, all)
            list.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(8), dp(12))
                background = rounded(Color.WHITE, 12, 0xFFE0E0E0.toInt()); alpha = if (a.enabled) 1f else 0.5f
                addView(LinearLayout(this@AlarmListActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(macro?.name ?: "(macro removida)", 18f, B.TEXT, bold = true))
                    addView(label(a.label(), 16f, if (bad) B.RED else 0xDE000000.toInt()))
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(Switch(this@AlarmListActivity).apply {
                    isChecked = a.enabled
                    setOnCheckedChangeListener { _, on ->
                        val upd = all.map { if (it.id == a.id) it.copy(enabled = on) else it }
                        if (on && Alarms.conflict(a, all)) { Toast.makeText(context, "Este cronograma se sobrepõe a outro. Ajuste o horário para ativá-lo.", Toast.LENGTH_LONG).show(); isChecked = false; return@setOnCheckedChangeListener }
                        store.saveAlarms(upd); if (!Alarms.rearm(context)) permissionDialog(); render()
                    }
                })
                addView(glyph(Ic.EDIT, 0xFF00574B.toInt(), 40).apply { setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { startActivity(Intent(context, AlarmEditActivity::class.java).putExtra("id", a.id)) } })
                addView(glyph(Ic.TRASH, 0xFFD32F2F.toInt(), 40).apply {
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setOnClickListener {
                        AlertDialog.Builder(context).setTitle("Excluir agendamento").setMessage("Tem certeza de que deseja excluir este agendamento?")
                            .setPositiveButton("Excluir") { _, _ -> Alarms.cancel(context, a.id); store.saveAlarms(all.filter { it.id != a.id }); Alarms.rearm(context); render() }
                            .setNegativeButton("Cancelar", null).show()
                    }
                })
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
            })
        }
    }
}

class AlarmEditActivity : Activity() {
    private lateinit var store: Store
    private var macroId: String? = null
    private var start = 0
    private lateinit var macroTxt: android.widget.TextView
    private lateinit var timeTxt: android.widget.TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val old = intent.getStringExtra("id")?.let { id -> store.alarms().firstOrNull { it.id == id } }
        macroId = old?.macroId ?: (store.selected ?: store.list().firstOrNull()?.id)
        start = old?.start ?: java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
        val up = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; hint = "Tempo de Atividade do Script (min)"; setText((old?.uptime ?: 10).toString())
        }
        macroTxt = label("", 16f, B.TEXT); timeTxt = label("", 16f, B.TEXT)
        /** Caixa de 60dp com borda cinza, rótulo flutuante sobre a borda e botão verde 36x48 à direita (como no base). */
        fun outlined(title: String, content: android.view.View, ic: Ic?, click: (() -> Unit)?) = FrameLayout(this).apply {
            val box = LinearLayout(this@AlarmEditActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Color.WHITE, 8, 0xFFBBBBBB.toInt())
                setPadding(dp(12), 0, 0, 0)
                addView(content, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                if (ic != null) addView(glyph(ic, Color.WHITE, 24).apply { background = rounded(B.PRIMARY, 4); setPadding(dp(6), dp(12), dp(6), dp(12)) }, LinearLayout.LayoutParams(dp(36), dp(48)).also { it.marginEnd = dp(6) })
                if (click != null) setOnClickListener { click() }
            }
            addView(box, FrameLayout.LayoutParams(MATCH_PARENT, dp(60)).also { it.topMargin = dp(8) })
            addView(label(title, 12f, 0xFF616161.toInt()).apply { setBackgroundColor(Color.WHITE); setPadding(dp(4), 0, dp(4), 0) }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.marginStart = dp(10) })
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(10) }
        }
        macroTxt.textSize = 18f; timeTxt.textSize = 18f
        up.background = null; up.textSize = 18f; up.hint = null
        val save = label("Salvar", 16f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER; background = rounded(B.PRIMARY, 28)
            setOnClickListener {
                val u = up.text.toString().toIntOrNull()
                val m = macroId
                if (m == null) return@setOnClickListener Toast.makeText(context, "Nenhum arquivo de macro", Toast.LENGTH_SHORT).show()
                if (u == null || u < 1 || u > 1440) return@setOnClickListener Toast.makeText(context, "Tempo de atividade: 1 a 1440 minutos", Toast.LENGTH_SHORT).show()
                val a = (old ?: Alarm(macroId = m, start = start, uptime = u)).copy(macroId = m, start = start, uptime = u)
                val rest = store.alarms().filter { it.id != a.id }
                if (a.enabled && Alarms.conflict(a, rest)) return@setOnClickListener Toast.makeText(context, "Este cronograma se sobrepõe a outro. Ajuste o horário.", Toast.LENGTH_LONG).show()
                store.saveAlarms(rest + a)
                if (!Alarms.rearm(context)) Toast.makeText(context, "Permita “Alarmes e lembretes” para o horário exato", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        val cancel = label("Cancelar", 16f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER; background = rounded(B.PRIMARY, 28); setOnClickListener { finish() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F5F5.toInt())
            addView(appBar("Agendamento", Ic.BACK, { finish() }))
            addView(ScrollView(this@AlarmEditActivity).apply {
                setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
                addView(LinearLayout(this@AlarmEditActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(card().apply {
                        setPadding(dp(16), dp(8), dp(16), dp(16)); background = rounded(Color.WHITE, 20); elevation = dp(4).toFloat()
                        (layoutParams as LinearLayout.LayoutParams).setMargins(dp(12), dp(14), dp(12), dp(14))
                        addView(outlined("Arquivo de Macro", macroTxt, Ic.FOLDER) {
                            val ms = store.list()
                            if (ms.isEmpty()) Toast.makeText(context, "Nenhum arquivo de macro", Toast.LENGTH_SHORT).show()
                            else AlertDialog.Builder(context).setTitle("Arquivos de Macro").setItems(ms.map { it.name }.toTypedArray()) { _, w -> macroId = ms[w].id; refresh() }.show()
                        })
                        addView(outlined("Hora de Início", timeTxt, Ic.CLOCK) {
                            TimePickerDialog(context, { _, h, mi -> start = h * 60 + mi; refresh() }, start / 60, start % 60, true).show()
                        })
                        addView(outlined("Tempo de Atividade do Script", up, null, null))
                    })
                    addView(LinearLayout(this@AlarmEditActivity).apply {
                        addView(save, LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(5), 0, dp(5), 0) })
                        addView(cancel, LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(5), 0, dp(5), 0) })
                    }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(dp(10), dp(20), dp(10), dp(20)) })
                })
            }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
        refresh()
    }

    private fun refresh() {
        macroTxt.text = macroId?.let { store.get(it)?.name } ?: "Nenhum arquivo de macro"
        timeTxt.text = "%d:%02d".format(start / 60, start % 60)
    }
}
