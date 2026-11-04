package app.macro17

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.SystemClock
import android.graphics.Rect
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import java.util.Random
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit

/**
 * Executa uma macro numa thread própria. A mecânica está em [Runner]; aqui ficam só gestos, teclas e a thread.
 * [report] recebe o motivo do fim; [say] recebe mensagens durante a execução (alerta de status, saltos).
 */
class Engine(
    private val svc: AccessibilityService,
    private val report: (String) -> Unit,
    private val say: (String) -> Unit,
    // Esconde o painel de registro durante a captura; devolve true se escondeu (então esperamos o redesenho).
    private val hideOverlay: ((Boolean) -> Boolean)? = null,
) {
    @Volatile private var thread: Thread? = null
    val running get() = thread?.isAlive == true
    /** "Testar item" da lista flutuante: compara a imagem do item com a tela agora e devolve a semelhança (chamar fora da thread principal). */
    fun probeItem(s: Step): String {
        val id = if (s.type == Type.IMAGE) s.text else s.img
        val needle = ports.template(id) ?: return "Este item não tem imagem"
        val fr = ports.screen() ?: return "Captura falhou"
        val pr = TemplateMatcher.Probe()
        TemplateMatcher.findNcc(fr, needle, 0f, pr)
        return "Semelhança: ${(pr.best * 100).toInt()}% (exigido ${s.thr}%)"
    }

    /** Alerta de status: se definido, mostra a mensagem por [ms] em janela própria (como a mensagem instantânea do base) em vez de um toast. */
    @Volatile var onStatus: ((String, Int) -> Unit)? = null
    /** Pausa entre os passos (o Runner dorme ao menos 1 ms por passo); parar a macro também solta a pausa. */
    @Volatile var paused = false
    /** Anterior/Próximo pedidos pelo painel; o Runner lê no próximo passo. */
    @Volatile var jump = 0
    /** Progresso para o painel (volta, voltas, passo, total, nome); chamado na thread da macro. */
    @Volatile var onProgress: ((Int, Int, Int, Int, String) -> Unit)? = null

    @Synchronized fun start(m: Macro): Boolean {
        if (running) return false
        paused = false; jump = 0
        thread = Thread({ run(m) }, "macro17-engine").also { it.start() }
        return true
    }

    // interrupt() acorda sleep() e await(); um gesto já despachado ainda termina (<= 60 s).
    @Synchronized fun stop() { thread?.interrupt() }

    private fun run(m: Macro) {
        // Throwable (ex.: OutOfMemoryError ao decodificar quadro): o report sempre roda, para soltar a tela e a bolha.
        val t0 = SystemClock.elapsedRealtime()
        Diag.d("run", "início \"${m.name}\" passos=${m.steps.size} repetições=${m.loops}")
        val msg = try {
            when (val o = Runner(ports).run(m)) {
                Outcome.Done -> "Macro concluída"
                Outcome.Stopped -> "Macro parada"
                is Outcome.Failed -> "Erro: ${o.why}"
            }
        } catch (t: Throwable) { Diag.e("run", "Throwable no executor", t); "Erro inesperado: ${t.javaClass.simpleName}" }
        Diag.d("run", "fim: $msg (${SystemClock.elapsedRealtime() - t0} ms)")
        report(msg)
    }

    private companion object { const val NODE_LIMIT = 4000 }

    private val rnd = Random()
    private val store by lazy { Store(svc) }

    private val ports = object : Ports {
        private val bounds get() = svc.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        override val width get() = bounds.width()
        override val height get() = bounds.height()

        override fun sleep(ms: Long) {
            // Fatias de 100 ms: a pausa vale no meio de esperas longas, não só depois delas.
            var left = ms
            while (left > 0) { val n = minOf(left, 100L); Thread.sleep(n); left -= n; while (paused) Thread.sleep(50) }
            while (paused) Thread.sleep(50)
        }

        // Modos ADB e root: toques, teclas e captura saem pelo shell (cada comando relê o modo, que pode mudar entre execuções).
        private fun sh(): Shell? = if (store.controlMode == MODE_ACCESS) null else store.shell()

        override fun gesture(strokes: List<Stroke>, ms: Int) {
            sh()?.let { s -> (svc as? MacroService)?.showTouches(strokes.map { it.x1 to it.y1 }, ms); Diag.d("gesto", "shell ${strokes.size} dedo(s) @${ms}ms"); ShellInput.gesture(s, strokes, ms); return }
            val b = GestureDescription.Builder()
            for (s in strokes) {
                val path = Path().apply {
                    moveTo(s.x1.toFloat(), s.y1.toFloat())
                    if (s.x1 != s.x2 || s.y1 != s.y2) lineTo(s.x2.toFloat(), s.y2.toFloat())
                }
                b.addStroke(GestureDescription.StrokeDescription(path, 0, ms.toLong()))
            }
            dispatch(b.build(), strokes.map { it.x1 to it.y1 }, ms, strokes.joinToString(" | ") { "${it.x1},${it.y1}>${it.x2},${it.y2}" })
        }

        override fun path(points: List<Pair<Int, Int>>, ms: Int) {
            sh()?.let { s -> ShellInput.path(s, points, ms); return }
            val path = Path().apply { moveTo(points[0].first.toFloat(), points[0].second.toFloat()); for (k in 1 until points.size) lineTo(points[k].first.toFloat(), points[k].second.toFloat()) }
            dispatch(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms.toLong())).build(), listOf(points[0]), ms, "gesto livre ${points.size} pontos")
        }

        private fun dispatch(g: GestureDescription, starts: List<Pair<Int, Int>>, ms: Int, label: String) {
            (svc as? MacroService)?.showTouches(starts, ms)
            val done = CountDownLatch(1)
            var ok = false // escrito na thread principal antes do countDown; lido depois do await
            Diag.d("gesto", "$label @${ms}ms")
            val accepted = svc.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription) { ok = true; done.countDown() }
                override fun onCancelled(d: GestureDescription) { done.countDown() }
            }, null)
            if (!accepted) Diag.e("gesto", "dispatchGesture recusou")
            check(accepted) { "gesto rejeitado (serviço sem permissão de gestos?)" }
            // Como no base: gesto cancelado ou sem resposta não derruba a macro; só a recusa do despacho é erro.
            if (!done.await(ms + 3000L, TimeUnit.MILLISECONDS)) Diag.e("gesto", "gesto sem resposta do sistema")
            else if (!ok) Diag.e("gesto", "gesto cancelado pelo sistema")
        }

        override fun key(code: Int) {
            sh()?.let { s -> runCatching { s.run(ShellInput.key(code)) }.onFailure { Diag.e("tecla", "shell recusou", it) }; return }
            if (!svc.performGlobalAction(code)) Diag.e("tecla", "ação global $code recusada pelo sistema") }

        override fun alert(vib: Boolean, snd: Boolean) {
            runCatching {
                if (snd) android.media.RingtoneManager.getRingtone(svc, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)).play()
                if (vib) svc.getSystemService(android.os.VibratorManager::class.java).defaultVibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }.onFailure { Diag.e("alerta", "som ou vibração falhou", it) }
        }
        override fun status(text: String, ms: Int) { Diag.d("alerta", text); onStatus?.invoke(text, ms) ?: say(text); sleep(ms.toLong()) }

        override fun resolve(id: String) = store.get(id)
        override fun glyphs() = store.glyphs()
        override fun matchNcc(hay: Gray, needle: Gray, th: Float, probe: TemplateMatcher.Probe): TemplateMatcher.Hit? {
            val t0 = SystemClock.elapsedRealtime()
            val r = if (Cv.ok) Cv.ncc(hay, needle, th, probe) else TemplateMatcher.findNcc(hay, needle, th, probe)
            val dt = SystemClock.elapsedRealtime() - t0
            if (dt > 250) Diag.d("visão", "casamento lento: $dt ms (${if (Cv.ok) "OpenCV" else "Kotlin"}) área ${hay.w}x${hay.h} modelo ${needle.w}x${needle.h}")
            return r
        }
        override fun now() = SystemClock.elapsedRealtime()
        override fun progress(lap: Int, loops: Int, step: Int, total: Int, name: String) { onProgress?.invoke(lap, loops, step, total, name) }
        override fun takeJump(): Int { val j = jump; jump = 0; return j }

        // Cache: o PNG não precisa ser decodificado a cada tentativa. Ids são únicos por captura, então nunca fica velho.
        private val templates = HashMap<String, Gray?>()
        override fun template(id: String) = templates.getOrPut(id) { store.template(id) }

        /**
         * Varre a janela ativa em largura e devolve o centro do primeiro elemento visível que casa.
         * "id:xyz" compara o fim do id de recurso; senão compara texto ou descrição (contém, ou igual se [exact]).
         * shortcut: só a janela ativa e no máximo NODE_LIMIT nós; subir se faltar em tela muito aninhada.
         */
        override fun findNode(query: String, exact: Boolean): Pair<Int, Int>? {
            val root = svc.rootInActiveWindow ?: return null
            val byId = query.startsWith("id:")
            val q = if (byId) query.substring(3) else query
            val rect = Rect()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var seen = 0
            while (queue.isNotEmpty() && seen++ < NODE_LIMIT) {
                val n = queue.removeFirst()
                val hit = if (byId) q.isNotEmpty() && n.viewIdResourceName?.let { it == q || it.substringAfterLast('/') == q } == true else
                    listOfNotNull(n.text, n.contentDescription).any { if (exact) it.toString().equals(q, ignoreCase = true) else it.contains(q, ignoreCase = true) }
                if (hit && n.isVisibleToUser) {
                    n.getBoundsInScreen(rect)
                    if (!rect.isEmpty) return rect.centerX() to rect.centerY()
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
            return null
        }

        /** Captura a tela como Bitmap ARGB_8888 (o chamador faz recycle); null só no intervalo mínimo entre capturas. */
        private fun capture(): Bitmap? {
            val hid = hideOverlay?.invoke(true) == true
            try { if (hid) Thread.sleep(90); return captureRaw() } // 90 ms: um quadro para o painel sumir; dentro do try para Parar não deixá-lo invisível
             finally { if (hid) hideOverlay?.invoke(false) }
        }

        private fun captureRaw(): Bitmap? {
            sh()?.let { s -> return runCatching { ShellInput.screenshot(s) }.onFailure { Diag.e("captura", "shell recusou", it) }.getOrNull() }
            val t0 = SystemClock.elapsedRealtime()
            val latch = CountDownLatch(1)
            var shot: AccessibilityService.ScreenshotResult? = null
            var err = 0
            // O callback roda na thread principal; a conversão pesada fica nesta thread, fora dela.
            svc.takeScreenshot(Display.DEFAULT_DISPLAY, svc.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) { shot = r; latch.countDown() }
                override fun onFailure(code: Int) { err = code; latch.countDown() }
            })
            if (!latch.await(3, TimeUnit.SECONDS)) Diag.e("captura", "sem resposta em 3 s")
            if (latch.count != 0L) return null // como no base, captura sem resposta = "não achou"; a busca tenta de novo
            SystemClock.elapsedRealtime().minus(t0).let { if (it > 400) Diag.d("captura", "lenta: $it ms") }
            val r = shot
            if (r == null) {
                // Intervalo mínimo entre capturas se resolve na próxima tentativa; os outros erros não.
                if (err != AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) Diag.e("captura", "recusada, código $err")
                check(err != AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) { "captura de tela recusada: o serviço perdeu a permissão" }
                return null
            }
            val hb = r.hardwareBuffer
            try {
                val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace) ?: return null
                return hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() }
            } finally { hb.close() }
        }

        @Volatile private var sharing = false
        private var cached: Gray? = null
        private var cachedAt = 0L
        override fun shareFrames(on: Boolean) { sharing = on; if (!on) cached = null }

        override fun screen(): Gray? {
            if (sharing) { val c = cached; if (c != null && SystemClock.elapsedRealtime() - cachedAt < 200) return c }
            val g = screenNow()
            if (sharing && g != null) { cached = g; cachedAt = SystemClock.elapsedRealtime() }
            return g
        }

        private fun screenNow(): Gray? {
            val bmp = capture() ?: return null
            val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
            return Gray.fromArgb(px, bmp.width, bmp.height).also { bmp.recycle() }
        }

        private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

        /**
         * OCR (ML Kit, alfabeto latino, no aparelho). Alvos separados por vírgula ou "|"; palavras são testadas antes das linhas.
         * [exact]: texto inteiro igual, diferenciando maiúsculas; senão "contém" sem diferenciar.
         * shortcut: só alfabeto latino; para outros idiomas trocar o reconhecedor.
         */
        override fun findText(query: String, exact: Boolean, area: IntArray?, minConf: Int): Pair<Int, Int>? {
            val targets = query.split(',', '|').map { it.trim() }.filter { it.isNotEmpty() }
            if (targets.isEmpty()) return null
            val full = capture() ?: return null
            var crop: Bitmap? = null
            try {
                val x0 = (area?.get(0) ?: 0).coerceIn(0, full.width - 1)
                val y0 = (area?.get(1) ?: 0).coerceIn(0, full.height - 1)
                val x1 = (area?.get(2) ?: full.width).coerceIn(x0 + 1, full.width)
                val y1 = (area?.get(3) ?: full.height).coerceIn(y0 + 1, full.height)
                crop = Bitmap.createBitmap(full, x0, y0, x1 - x0, y1 - y0)
                val text = try {
                    Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)), 10, TimeUnit.SECONDS)
                } catch (e: ExecutionException) { Diag.e("ocr", "falhou: ${e.cause?.message ?: e.message}"); return null }
                  catch (e: TimeoutException) { Diag.e("ocr", "sem resposta em 10 s"); return null }
                val hit = { t: String -> targets.any { if (exact) t == it else t.contains(it, ignoreCase = true) } }
                val lines = text.textBlocks.flatMap { it.lines }
                // Confiança mínima do manual do base: abaixo dela a leitura é descartada (0 = sem filtro).
                val min = minConf / 100f
                val cands = lines.flatMap { l -> l.elements.filter { it.confidence >= min }.map { e -> e.text to e.boundingBox } } +
                    lines.filter { it.confidence >= min }.map { it.text to it.boundingBox }
                for ((t, box) in cands) if (box != null && hit(t)) return (x0 + box.centerX()) to (y0 + box.centerY())
                return null
            } finally { crop?.recycle(); full.recycle() }
        }
        override fun nextInt(bound: Int) = rnd.nextInt(bound)
        private var lastJump = 0L
        // Laço GOTO rápido gera milhares de "Passo N → Passo M"; no máximo um por segundo vai à tela (diagnósticos passam sempre).
        override fun trace(line: String) = Diag.d("passo", line)
        override fun log(line: String) {
            Diag.d("aviso", line)
            if (line.contains(" → Passo ")) { val t = now(); if (t - lastJump < 1000) return; lastJump = t }
            if (store.debugOverlay) say(line)
        }
    }
}
