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
) {
    @Volatile private var thread: Thread? = null
    val running get() = thread?.isAlive == true

    @Synchronized fun start(m: Macro): Boolean {
        if (running) return false
        thread = Thread({ run(m) }, "macro17-engine").also { it.start() }
        return true
    }

    // interrupt() acorda sleep() e await(); um gesto já despachado ainda termina (<= 60 s).
    @Synchronized fun stop() { thread?.interrupt() }

    private fun run(m: Macro) {
        report(
            when (val o = Runner(ports).run(m)) {
                Outcome.Done -> "Macro concluída"
                Outcome.Stopped -> "Macro parada"
                is Outcome.Failed -> "Erro: ${o.why}"
            }
        )
    }

    private companion object { const val NODE_LIMIT = 4000 }

    private val rnd = Random()
    private val store by lazy { Store(svc) }

    private val ports = object : Ports {
        private val bounds get() = svc.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        override val width get() = bounds.width()
        override val height get() = bounds.height()

        override fun sleep(ms: Long) = Thread.sleep(ms)

        override fun gesture(strokes: List<Stroke>, ms: Int) {
            val b = GestureDescription.Builder()
            for (s in strokes) {
                val path = Path().apply {
                    moveTo(s.x1.toFloat(), s.y1.toFloat())
                    if (s.x1 != s.x2 || s.y1 != s.y2) lineTo(s.x2.toFloat(), s.y2.toFloat())
                }
                b.addStroke(GestureDescription.StrokeDescription(path, 0, ms.toLong()))
            }
            val done = CountDownLatch(1)
            var ok = false // escrito na thread principal antes do countDown; lido depois do await
            val accepted = svc.dispatchGesture(b.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription) { ok = true; done.countDown() }
                override fun onCancelled(d: GestureDescription) { done.countDown() }
            }, null)
            check(accepted) { "gesto rejeitado (serviço sem permissão de gestos?)" }
            check(done.await(ms + 3000L, TimeUnit.MILLISECONDS)) { "gesto sem resposta do sistema" }
            check(ok) { "gesto cancelado (tela bloqueada ou app protegido)" }
        }

        override fun key(code: Int) { check(svc.performGlobalAction(code)) { "tecla recusada pelo sistema" } }

        override fun status(text: String, ms: Int) { say(text); Thread.sleep(ms.toLong()) }

        override fun resolve(id: String) = store.get(id)
        override fun glyphs() = store.glyphs()
        override fun matchNcc(hay: Gray, needle: Gray, th: Float, probe: TemplateMatcher.Probe) =
            if (Cv.ok) Cv.ncc(hay, needle, th, probe) else TemplateMatcher.findNcc(hay, needle, th, probe)
        override fun now() = SystemClock.elapsedRealtime()

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
                val hit = if (byId) n.viewIdResourceName?.endsWith(q) == true else
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
            val latch = CountDownLatch(1)
            var shot: AccessibilityService.ScreenshotResult? = null
            var err = 0
            // O callback roda na thread principal; a conversão pesada fica nesta thread, fora dela.
            svc.takeScreenshot(Display.DEFAULT_DISPLAY, svc.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) { shot = r; latch.countDown() }
                override fun onFailure(code: Int) { err = code; latch.countDown() }
            })
            check(latch.await(3, TimeUnit.SECONDS)) { "captura de tela sem resposta do sistema" }
            val r = shot
            if (r == null) {
                // Intervalo mínimo entre capturas se resolve na próxima tentativa; os outros erros não.
                check(err == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) { "captura de tela recusada (código $err)" }
                return null
            }
            val hb = r.hardwareBuffer
            try {
                val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace) ?: return null
                return hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() }
            } finally { hb.close() }
        }

        override fun screen(): Gray? {
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
        override fun findText(query: String, exact: Boolean, area: IntArray?): Pair<Int, Int>? {
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
                } catch (e: ExecutionException) { throw IllegalStateException("OCR falhou: ${e.cause?.message ?: e.message}") }
                  catch (e: TimeoutException) { throw IllegalStateException("OCR sem resposta em 10 s") }
                val hit = { t: String -> targets.any { if (exact) t == it else t.contains(it, ignoreCase = true) } }
                val lines = text.textBlocks.flatMap { it.lines }
                val cands = lines.flatMap { it.elements.map { e -> e.text to e.boundingBox } } + lines.map { it.text to it.boundingBox }
                for ((t, box) in cands) if (box != null && hit(t)) return (x0 + box.centerX()) to (y0 + box.centerY())
                return null
            } finally { crop?.recycle(); full.recycle() }
        }
        override fun nextInt(bound: Int) = rnd.nextInt(bound)
        override fun log(line: String) = say(line)
    }
}
