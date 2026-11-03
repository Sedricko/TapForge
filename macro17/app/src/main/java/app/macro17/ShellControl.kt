package app.macro17

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Modos de controle do app base (tela Permissões): 1 acessibilidade, 2 ADB, 3 root. */
const val MODE_ACCESS = 1
const val MODE_ADB = 2
const val MODE_ROOT = 3

/** Executa um comando de shell e devolve a saída; lança IOException se o modo não está disponível. */
interface Shell { fun run(command: String): ByteArray }

/** Root: `su -c <comando>`. */
class RootShell : Shell {
    override fun run(command: String): ByteArray {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(false).start()
        val out = p.inputStream.readBytes()
        if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); throw java.io.IOException("su sem resposta") }
        if (p.exitValue() != 0 && out.isEmpty()) throw java.io.IOException("su recusado ou comando falhou (código ${p.exitValue()})")
        return out
    }
}

/** ADB local (adbd em 127.0.0.1): cada comando vira `shell:<comando>`. */
class AdbShell(private val client: AdbClient) : Shell {
    override fun run(command: String): ByteArray = client.run(if (command.startsWith("exec:")) command else "shell:$command")
}

/**
 * Entrada pelo shell (root ou ADB): `input` para toques, deslizes e teclas, `screencap` para a tela. Estas funções só montam os
 * comandos (testáveis na JVM); quem executa é um [Shell].
 */
object ShellInput {
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Int) = "input touchscreen swipe $x1 $y1 $x2 $y2 ${ms.coerceAtLeast(1)}"

    /** Teclas do Macro17: 1 Voltar, 2 Início, 3 Recentes, 4 Notificações, 9 captura de tela. */
    fun key(code: Int) = when (code) {
        1 -> "input keyevent 4"; 2 -> "input keyevent 3"; 3 -> "input keyevent 187"
        4 -> "cmd statusbar expand-notifications"; KEY_SCREENSHOT -> "input keyevent 120"; else -> "input keyevent 4"
    }

    const val SCREENCAP = "exec:screencap -p"

    fun gesture(sh: Shell, strokes: List<Stroke>, ms: Int) {
        if (strokes.size == 1) { val s = strokes[0]; sh.run(swipe(s.x1, s.y1, s.x2, s.y2, ms)); return }
        // Dois dedos (pinça): um `input` por dedo, ao mesmo tempo.
        val ts = strokes.map { s -> Thread { runCatching { sh.run(swipe(s.x1, s.y1, s.x2, s.y2, ms)) } }.also { it.start() } }
        ts.forEach { it.join() }
    }

    /** Gesto livre: o `input` só faz retas; vai do primeiro ao último ponto (limite do modo shell). */
    fun path(sh: Shell, points: List<Pair<Int, Int>>, ms: Int) {
        val a = points.first(); val b = points.last(); sh.run(swipe(a.first, a.second, b.first, b.second, ms))
    }

    fun screenshot(sh: Shell): Bitmap? = sh.run(SCREENCAP).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
}
