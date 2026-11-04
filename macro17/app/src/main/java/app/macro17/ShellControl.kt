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
        val p = ProcessBuilder("su", "-c", command.removePrefix("exec:")).redirectErrorStream(false).start()
        // A leitura vai para outra thread: o waitFor é interrompível, então Parar a macro também solta um `su` que não responde.
        val out = java.io.ByteArrayOutputStream()
        val reader = Thread { runCatching { p.inputStream.copyTo(out) } }.also { it.isDaemon = true; it.start() }
        try {
            if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); throw java.io.IOException("su sem resposta") }
            reader.join(2000)
        } catch (e: InterruptedException) { p.destroyForcibly(); throw e }
        val bytes = out.toByteArray()
        if (p.exitValue() != 0 && bytes.isEmpty()) throw java.io.IOException("su recusado ou comando falhou (código ${p.exitValue()})")
        return bytes
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

    @Volatile private var dev: TouchEvents.Dev? = null
    @Volatile private var touchBroken = false

    /** Toque por eventos de entrada (multitoque e gesto livre de verdade). false = não deu; chamar o `input swipe`. */
    fun touch(sh: Shell, paths: List<List<Pair<Int, Int>>>, ms: Int, w: Int, h: Int, is64: Boolean): Boolean {
        if (touchBroken) return false
        val d = dev ?: TouchEvents.parse(String(sh.run("getevent -p"))).also { dev = it } ?: run { touchBroken = true; return false }
        val out = String(sh.run(TouchEvents.script(d, paths, ms, w, h, is64)))
        if (out.contains("denied", true) || out.contains("No such", true) || out.contains("not permitted", true)) { touchBroken = true; return false }
        return true
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


/**
 * Toque real por /dev/input (multitoque e gesto livre no modo ADB/Root, como o base com o "dispositivo de toque"):
 * acha o touchscreen em `getevent -p` e escreve eventos do protocolo B (SLOT, TRACKING_ID, X, Y, SYN) com `printf` em um só processo
 * por quadro. Se o aparelho não deixar (permissão, formato), quem chama volta ao `input swipe`.
 */
object TouchEvents {
    class Dev(val path: String, val maxX: Int, val maxY: Int)

    /** Primeiro dispositivo com ABS_MT_POSITION_X (0035) e _Y (0036) e slots (002f); null se não achar. */
    fun parse(text: String): Dev? {
        for (block in text.split(Regex("(?m)^add device ")).drop(1)) {
            val path = Regex("/dev/input/event\\d+").find(block)?.value ?: continue
            fun max(code: String) = Regex("(?m)^\\s*$code\\s*:.*?max (\\d+)").find(block)?.groupValues?.get(1)?.toIntOrNull()
            val mx = max("0035"); val my = max("0036")
            if (mx != null && my != null && Regex("(?m)^\\s*002f\\s*:").containsMatchIn(block)) return Dev(path, mx, my)
        }
        return null
    }

    private fun esc(b: Int) = "\\" + "%03o".format(b and 255)
    private fun le(v: Int, n: Int) = (0 until n).joinToString("") { esc(v shr (8 * it)) }
    /** struct input_event: tempo zerado (o kernel carimba), tipo, código, valor; 24 bytes em 64 bits, 16 em 32. */
    private fun ev(type: Int, code: Int, value: Int, is64: Boolean) = esc(0).repeat(if (is64) 16 else 8) + le(type, 2) + le(code, 2) + le(value, 4)

    private const val ABS = 3; private const val KEY = 1; private const val SYN = 0
    private const val SLOT = 0x2f; private const val TRACK = 0x39; private const val MTX = 0x35; private const val MTY = 0x36; private const val BTN_TOUCH = 0x14a

    /** Um quadro por passo de tempo; cada caminho é um dedo (lista de pontos em px da tela). */
    fun script(d: Dev, paths: List<List<Pair<Int, Int>>>, ms: Int, w: Int, h: Int, is64: Boolean): String {
        val steps = (ms / 25).coerceIn(2, 60)
        fun at(path: List<Pair<Int, Int>>, t: Double): Pair<Int, Int> {
            if (path.size == 1) return path[0]
            val f = t * (path.size - 1); val i = f.toInt().coerceAtMost(path.size - 2); val r = f - i
            return (path[i].first + (path[i + 1].first - path[i].first) * r).toInt() to (path[i].second + (path[i + 1].second - path[i].second) * r).toInt()
        }
        fun sx(x: Int) = (x.toLong() * d.maxX / maxOf(1, w - 1)).toInt().coerceIn(0, d.maxX)
        fun sy(y: Int) = (y.toLong() * d.maxY / maxOf(1, h - 1)).toInt().coerceIn(0, d.maxY)
        val frames = ArrayList<String>()
        for (k in 0..steps) {
            val sb = StringBuilder()
            paths.forEachIndexed { i, path ->
                val (x, y) = at(path, k.toDouble() / steps)
                sb.append(ev(ABS, SLOT, i, is64))
                if (k == 0) sb.append(ev(ABS, TRACK, 100 + i, is64))
                sb.append(ev(ABS, MTX, sx(x), is64)).append(ev(ABS, MTY, sy(y), is64))
            }
            if (k == 0) sb.append(ev(KEY, BTN_TOUCH, 1, is64))
            frames += sb.append(ev(SYN, 0, 0, is64)).toString()
        }
        val up = StringBuilder()
        paths.indices.forEach { i -> up.append(ev(ABS, SLOT, i, is64)).append(ev(ABS, TRACK, -1, is64)) }
        frames += up.append(ev(KEY, BTN_TOUCH, 0, is64)).append(ev(SYN, 0, 0, is64)).toString()
        val pause = "sleep %.3f".format(java.util.Locale.US, ms / 1000.0 / steps)
        return frames.joinToString("; $pause; ") { "printf '$it' > ${d.path}" }
    }
}
