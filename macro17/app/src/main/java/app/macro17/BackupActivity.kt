package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Backup e Restauração em ZIP, como no base: um aviso e dois botões. O seletor de arquivos é o do sistema (SAF). */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class BackupActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 0)
            addView(card().apply {
                setPadding(dp(12), dp(12), dp(12), dp(12))
                addView(LinearLayout(this@BackupActivity).apply {
                    gravity = Gravity.CENTER
                    addView(glyph(Ic.ARCHIVE, 0xFFD81B60.toInt(), 25))
                    addView(label("Backup e Restauração", 14f, 0xFF212121.toInt(), bold = true).apply { setPadding(dp(8), 0, 0, 0); minWidth = dp(225) })
                })
                addView(label("O local de armazenamento de dados foi alterado. Se você excluir o aplicativo, os dados da macro também serão excluídos. Faça um backup dos seus dados agora.", 14f, 0xFF212121.toInt(), bold = true).apply { setPadding(0, dp(8), 0, 0); gravity = Gravity.CENTER })
            })
            addView(bigText("Backup de Dados") { chooseMacros() })
            addView(note("Formato Macro17 (ZIP com macros, imagens e capturas): restaura aqui. Para levar uma macro ao app base, use Arquivo → Exportar."))
            addView(note("Restaura o ZIP do Macro17 e também o ZIP do app base (TouchMacroPro/*.tmc)."))
            addView(bigText("Restaurar Dados") {
                // Como no base: primeiro o seletor (application/zip); o aviso vem depois de escolher o arquivo.
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip"), LOAD)
            })
        }
        val scroll = ScrollView(this).apply {
            addView(content)
            setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(B.BG)
            addView(LinearLayout(this@BackupActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(appBar("Backup e Restauração", Ic.BACK, { finish() }))
                addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        })
    }

    private var chosen: Set<String>? = null
    private var withSamples = false

    /** A seleção de macros é uma tela própria (BackupPickActivity); depois vem a pergunta sobre as capturas de tela. */
    private fun chooseMacros() {
        if (Store(this).list().isEmpty()) return Toast.makeText(this, "Por favor, selecione um arquivo.", Toast.LENGTH_SHORT).show()
        startActivityForResult(Intent(this, BackupPickActivity::class.java), PICK)
    }

    private fun askSamples() = AlertDialog.Builder(this).setTitle("Backup da Captura de Tela")
        .setMessage("Você quer incluir os dados da captura de tela no backup? As capturas de tela podem conter informações pessoais sensíveis.")
        .setPositiveButton("Sim") { _, _ -> withSamples = true; pickTarget() }
        .setNegativeButton("Não") { _, _ -> withSamples = false; pickTarget() }.show()

    private fun pickTarget() = startActivityForResult(
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
            .putExtra(Intent.EXTRA_TITLE, "macro17-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip"), SAVE)

    private fun note(t: String) = label(t, 12f, 0xFF616161.toInt()).apply { setPadding(dp(10), 0, dp(10), dp(6)); gravity = Gravity.CENTER }

    private fun bigText(text: String, click: () -> Unit) = label(text, 20f, B.BLUE, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(Color.WHITE, 2); elevation = dp(2).toFloat()
        foreground = ripple(2, 0x1F000000)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(dp(5), dp(4), dp(5), dp(4)) }
        setOnClickListener { click() }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == PICK) { if (res == RESULT_OK) {
                chosen = data?.getStringArrayExtra("ids")?.toSet()
                // Sem seleção não há o que gravar (antes, chosen nulo exportava TUDO sem avisar).
                if (chosen.isNullOrEmpty()) Toast.makeText(this, "Por favor, selecione um arquivo.", Toast.LENGTH_LONG).show() else askSamples()
            }; return }
        val uri = data?.data ?: return
        if (res != RESULT_OK) return
        if (req == LOAD) {
            AlertDialog.Builder(this).setTitle("Aviso!")
                .setMessage("Sobrescreve ou substitui arquivos existentes. Os dados da macro podem ser perdidos.")
                .setPositiveButton("Continuar") { _, _ -> runBackup(req, uri) }.setNegativeButton("Cancelar", null).show()
            return
        }
        if (req == SAVE && chosen.isNullOrEmpty()) return Toast.makeText(this, "Seleção perdida; escolha as macros de novo.", Toast.LENGTH_LONG).show()
        runBackup(req, uri)
    }

    @Volatile private var busy = false
    override fun onSaveInstanceState(o: android.os.Bundle) { super.onSaveInstanceState(o); o.putStringArray("chosen", chosen?.toTypedArray()); o.putBoolean("samples", withSamples) }
    override fun onRestoreInstanceState(b: android.os.Bundle) { super.onRestoreInstanceState(b); chosen = b.getStringArray("chosen")?.toSet(); withSamples = b.getBoolean("samples", false) }

    private fun runBackup(req: Int, uri: android.net.Uri) {
        if (busy) return Toast.makeText(this, "Aguarde, operação em andamento…", Toast.LENGTH_SHORT).show()
        busy = true
        val sel = chosen; val samples = withSamples // cópias: a Activity pode mudar enquanto o thread grava
        Toast.makeText(this, if (req == SAVE) "Gerando o backup…" else "Restaurando…", Toast.LENGTH_SHORT).show()
        Thread({
            val msg = try {
                if (req == SAVE) {
                    val n = try {
                        (contentResolver.openOutputStream(uri, "wt") ?: throw IOException("não foi possível abrir o arquivo de destino")).use { BackupZip.write(it, filesDir, sel, samples) }
                    } catch (e: Throwable) { runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, uri) }; throw e } // não deixa ZIP vazio/pela metade
                    Diag.d("backup", "gravou $n arquivos")
                    if (n == 0) { runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, uri) }; "Nada para salvar: as macros escolhidas não foram encontradas" }
                    else "Backup do Macro17 salvo ($n arquivos)"
                } else {
                    // Backup do app base (.tmc)? Importa convertendo; senão é backup do Macro17.
                    val base = contentResolver.openInputStream(uri)!!.use { TmcZip.import(it, Store(this)) }
                    if (base != null) {
                        Diag.d("backup", "importou do app base: ${base.macros} macros, ${base.images} imagens; avisos: ${base.notes.take(8)}")
                        "Importadas ${base.macros} macros do app base (${base.images} imagens)" + if (base.notes.isEmpty()) "" else "; ${base.notes.size} avisos (veja Monitor e logs)"
                    } else {
                        val r = contentResolver.openInputStream(uri)!!.use { BackupZip.read(it, filesDir) }
                        Diag.d("backup", "restaurou ${r.restored}, ignorou ${r.skipped.size}: ${r.skipped.take(5)}")
                        "Restaurados ${r.restored} arquivos" + if (r.skipped.isEmpty()) "" else "; ${r.skipped.size} ignorados (veja Monitor e logs)"
                    }
                }
            } catch (e: Throwable) { Diag.e("backup", "falhou", e); "Falhou: ${e.message ?: e.javaClass.simpleName}" }
            busy = false
            runOnUiThread { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
        }, "macro17-backup").start()
    }

    private companion object { const val SAVE = 11; const val LOAD = 12; const val PICK = 13 }
}
