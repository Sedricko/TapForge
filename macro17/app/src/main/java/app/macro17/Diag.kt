package app.macro17

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Diagnóstico: grava eventos do app em arquivo (também no logcat) e monta um relatório com o que o sistema sabe
 * sobre o app (saídas anteriores do processo, memória, permissões) mais o logcat visível ao app.
 * Limite do Android: um app comum só lê os próprios logs. Para o log do sistema inteiro, conceda uma vez por ADB:
 * adb shell pm grant app.macro17 android.permission.READ_LOGS
 */
object Diag {
    @Volatile private var log: DiagLog? = null
    @Volatile var enabled = true
    val feed = LogFeed() // painel ao vivo (LogPanel): últimas entradas em memória
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "macro17-diag").apply { isDaemon = true } }
    private val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    fun init(ctx: Context) {
        val c = ctx.applicationContext
        log = DiagLog(File(c.filesDir, "diag/diag.log"))
        enabled = Store(c).diag
    }

    private fun line(tag: String, msg: String) =
        fmt.format(LocalDateTime.now()) + " [" + Thread.currentThread().name + "] " + tag + " " + msg.replace("\n", "\n    ")

    fun d(tag: String, msg: String) {
        if (!enabled) return
        val l = log ?: return
        Log.d("Macro17/$tag", msg)
        feed.add(Entry(System.currentTimeMillis(), tag, msg, LogRules.level(tag, msg)))
        val s = line(tag, msg)
        io.execute { runCatching { l.append(s) } } // E/S fora da thread de quem chamou (pode ser a principal)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) = d(tag, "ERRO $msg" + (t?.let { "\n" + Log.getStackTraceString(it) } ?: ""))

    /** Falha fatal: síncrono, porque o processo morre logo depois. */
    fun fatal(t: Thread, e: Throwable) {
        if (!enabled) return
        val l = log ?: return
        runCatching { l.append(fmt.format(LocalDateTime.now()) + " [${t.name}] FATAL " + Log.getStackTraceString(e).replace("\n", "\n    ")) }
    }

    fun tail(maxChars: Int = 60_000) = log?.tail(maxChars) ?: ""
    fun clear() { log?.clear() }

    /** Relatório completo; chame fora da thread principal (lê arquivo e roda `logcat`). */
    fun report(ctx: Context): String {
        val c = ctx.applicationContext
        val sb = StringBuilder()
        fun section(title: String, body: () -> String) {
            sb.append("\n==== ").append(title).append(" ====\n")
            sb.append(runCatching(body).getOrElse { "indisponível: ${it.javaClass.simpleName}: ${it.message}" }).append('\n')
        }
        sb.append("Relatório Macro17 · ").append(fmt.format(LocalDateTime.now())).append('\n')
        section("App e aparelho") {
            val pi = c.packageManager.getPackageInfo(c.packageName, 0)
            "app ${c.packageName} ${pi.versionName} (${pi.longVersionCode})\n" +
                "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · patch ${Build.VERSION.SECURITY_PATCH}\n" +
                "ABIs ${Build.SUPPORTED_ABIS.joinToString()}\n" +
                "tela ${c.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.flattenToString()} · ${c.resources.displayMetrics.densityDpi} dpi"
        }
        section("Memória") {
            val mi = ActivityManager.MemoryInfo().also { c.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            val rt = Runtime.getRuntime()
            "sistema livre ${mi.availMem shr 20} MB de ${mi.totalMem shr 20} MB · pouca memória: ${mi.lowMemory}\n" +
                "heap do app ${(rt.totalMemory() - rt.freeMemory()) shr 20} MB de ${rt.maxMemory() shr 20} MB"
        }
        section("Permissões e estado") {
            val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            "acessibilidade ligada nas configurações: ${enabled.contains(c.packageName)} · serviço conectado: ${MacroService.instance != null}\n" +
                "ignora otimização de bateria: ${c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)}\n" +
                "alarme exato permitido: ${c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()}\n" +
                "OpenCV carregado: ${Cv.ok}"
        }
        section("Opções e dados") {
            val st = Store(c)
            val ms = st.list()
            "manter tela ligada ${st.keepOn} · parar ao bloquear ${st.stopOnLock} · opacidade ${st.opacity}% · diagnóstico ${st.diag}\n" +
                "macros ${ms.size} · passos ${ms.sumOf { it.steps.size }} · amostras ${st.samples().size} · dígitos aprendidos ${st.glyphs().count()}"
        }
        section("Saídas anteriores do processo (o sistema registra quedas, ANR e mortes por memória)") {
            c.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(c.packageName, 0, 15).joinToString("\n") {
                fmt.format(java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault())) + " " + reason(it.reason) +
                    " status=${it.status} · ${it.description.orEmpty()} · importância ${it.importance}"
            }.ifEmpty { "nenhuma" }
        }
        section("Eventos do app") { log?.readAll()?.takeLast(400_000).orEmpty().ifEmpty { "vazio (o diagnóstico estava desligado?)" } }
        section("logcat visível ao app, filtrado (sem READ_LOGS: só os logs do próprio app; avisos e erros sempre ficam)") { logcat() }
        if (Store(c).diagRoot) {
            section("ROOT: logcat do sistema inteiro (todos os buffers)") { su("logcat -d -b all -v threadtime -t 8000", 300_000) }
            section("ROOT: dmesg (kernel, últimas 400 linhas)") { su("dmesg | tail -n 400", 100_000) }
            section("ROOT: serviço de acessibilidade") { su("dumpsys accessibility | head -n 150", 60_000) }
        }
        return sb.toString()
    }

    private fun reason(r: Int) = when (r) {
        ApplicationExitInfo.REASON_CRASH -> "QUEDA (exceção)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "QUEDA NATIVA"
        ApplicationExitInfo.REASON_ANR -> "ANR (app travado)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "MORTO POR FALTA DE MEMÓRIA"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "USO EXCESSIVO DE RECURSOS"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "parado pelo usuário"
        ApplicationExitInfo.REASON_USER_STOPPED -> "forçado a parar"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "mudança de permissão"
        ApplicationExitInfo.REASON_SIGNALED -> "sinal"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependência morreu"
        ApplicationExitInfo.REASON_EXIT_SELF -> "saiu sozinho"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "falha ao iniciar"
        else -> "outro ($r)"
    }

    /** Roda um comando como root (o gerenciador de root pode pedir confirmação). Falha vira texto, nunca exceção. */
    private fun su(cmd: String, maxChars: Int): String = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = StringBuilder()
        val reader = Thread { runCatching { p.inputStream.bufferedReader().forEachLine { l -> if (out.length < 2_000_000) out.append(l).append('\n') } } }.also { it.start() }
        if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); "su: sem resposta em 20 s (negado ou travado)" }
        else { reader.join(2000); (if (p.exitValue() != 0 && out.isEmpty()) "su saiu com código ${p.exitValue()} (root negado ou ausente)" else out.toString().takeLast(maxChars)) }
    } catch (e: Exception) { "su indisponível: ${e.message}" }

    private fun logcat(): String = try {
        val p = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "3000").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.destroy()
        filterLogcat(out).takeLast(300_000).ifEmpty { "vazio" }
    } catch (e: Exception) { "indisponível: ${e.message}" }
}
