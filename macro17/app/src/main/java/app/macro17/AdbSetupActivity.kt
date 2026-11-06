package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Tela do ADB do base (fragment_setup): vantagens, procedimento com 3 marcas conferidas a cada 3 s, "Como configurar" e INICIAR. */
class AdbSetupActivity : Activity() {
    private lateinit var chkUsb: CheckBox
    private lateinit var chkPc: CheckBox
    private lateinit var chkOk: CheckBox
    private lateinit var start: LinearLayout
    private lateinit var port: EditText
    private var polling = false
    private val tick = object : Runnable { override fun run() { if (polling) { check(); start.postDelayed(this, 3000) } } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(card().apply {
            gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(16), dp(15), dp(16), dp(15))
            addView(LinearLayout(this@AdbSetupActivity).apply {
                gravity = Gravity.CENTER
                addView(glyph(Ic.INFO, 0xFF178F46.toInt(), 25))
                addView(label("Vantagens da Depuração USB", 14f, 0xDE000000.toInt(), bold = true).apply { setPadding(dp(6), dp(6), dp(6), dp(6)); gravity = Gravity.CENTER })
            })
            addView(label("Gravar movimentos dos dedos, como toque, deslize e pressão", 14f, 0xFF212121.toInt()).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
        })
        chkUsb = box("Ative a depuração USB nas Opções do Desenvolvedor.", bold = true)
        chkPc = box("")
        chkOk = box("Sucesso.")
        port = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(Store(this@AdbSetupActivity).adbPort.toString()); hint = "Porta" }
        col.addView(card().apply {
            setPadding(dp(16), dp(15), dp(16), dp(15))
            addView(label("Procedimento de Configuração", 14f, 0xDE000000.toInt(), bold = true).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(10)) }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(chkUsb)
            addView(LinearLayout(this@AdbSetupActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(chkPc)
                addView(LinearLayout(this@AdbSetupActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label("Conecte o dispositivo ao computador host com um cabo USB.", 14f, 0xFF212121.toInt()))
                    addView(label("Digite adb tcpip 7777 ou configure usando uma ferramenta de configuração do computador.", 14f, 0xFF212121.toInt()))
                    addView(port)
                })
            })
            addView(chkOk)
        })
        col.addView(bigText("Como configurar", B.BLUE) { help() })
        start = bigText("INICIAR", 0xFF9E9E9E.toInt()) { go() }
        col.addView(start)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(B.BG)
            addView(appBar("Android Debug Bridge (ADB)", Ic.BACK, { finish() }))
            addView(ScrollView(this@AdbSetupActivity).apply {
                addView(col)
                setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom); i }
            }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }

    private fun box(t: String, bold: Boolean = false) = CheckBox(this).apply {
        text = t; isClickable = false; textSize = 14f; setTextColor(0xFF212121.toInt())
        setTypeface(null, if (bold) Typeface.BOLD else Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    }

    private fun bigText(t: String, color: Int, click: () -> Unit) = card().apply {
        foreground = ripple(2, 0x1F000000); isClickable = true; setOnClickListener { click() }
        addView(label(t, 20f, color, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)); tag = "t" }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    override fun onResume() { super.onResume(); polling = true; tick.run() }
    override fun onPause() { super.onPause(); polling = false }

    private fun portNum() = port.text.toString().toIntOrNull() ?: 7777

    /** Mesma checagem do base: depuração USB ligada e porta TCP do adbd definida (lida fora da thread principal). */
    private fun check() {
        val p = portNum()
        val usb = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        chkUsb.isChecked = usb; chkUsb.setTypeface(null, if (usb) Typeface.NORMAL else Typeface.BOLD)
        Thread({
            val pc = try { Runtime.getRuntime().exec(arrayOf("getprop", "service.adb.tcp.port")).inputStream.bufferedReader().readText().contains(p.toString()) } catch (e: Exception) { false }
            runOnUiThread {
                chkPc.isChecked = pc; chkOk.isChecked = usb && pc
                start.isClickable = usb && pc
                start.findViewWithTag<TextView>("t").setTextColor(if (usb && pc) B.BLUE else 0xFF9E9E9E.toInt())
            }
        }, "macro17-adb-check").start()
    }

    private fun help() = AlertDialog.Builder(this).setTitle("Como configurar")
        .setMessage("1. Ative as opções do desenvolvedor e a Depuração USB.\n2. Conecte o aparelho ao computador e rode:\n    adb tcpip ${portNum()}\n3. Volte a esta tela e toque em INICIAR; permita a depuração neste aparelho.\n\nOs toques e a captura passam a sair pelo ADB; o painel continua usando a acessibilidade.")
        .setPositiveButton("OK", null).show()

    /** Mesmo teste de conexão do antigo diálogo: grava porta e modo, roda um shell e, se falhar, restaura o modo anterior. */
    private fun go() {
        if (!chkOk.isChecked) return
        val st = Store(this); val before = st.controlMode
        st.adbPort = portNum(); st.controlMode = MODE_ADB
        Toast.makeText(this, "Testando ADB…", Toast.LENGTH_SHORT).show()
        Thread({
            val err = try {
                Diag.d("controle", "ADB ok: " + String(st.shell()!!.run("echo macro17")).trim().take(60)); null
            } catch (e: Exception) { st.controlMode = if (before == MODE_ADB) MODE_ACCESS else before; Diag.e("controle", "ADB falhou", e); e.message ?: e.javaClass.simpleName }
            runOnUiThread {
                if (err == null) { Toast.makeText(this, "ADB pronto.", Toast.LENGTH_LONG).show(); setResult(RESULT_OK); finish() }
                else Toast.makeText(this, "ADB indisponível: $err", Toast.LENGTH_LONG).show()
            }
        }, "macro17-shell-test").start()
    }
}
