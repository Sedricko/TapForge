package app.macro17

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import java.util.Random
import java.util.concurrent.CountDownLatch
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
        override fun now() = SystemClock.elapsedRealtime()

        // Cache: o PNG não precisa ser decodificado a cada tentativa. Ids são únicos por captura, então nunca fica velho.
        private val templates = HashMap<String, Gray?>()
        override fun template(id: String) = templates.getOrPut(id) { store.template(id) }

        override fun screen(): Gray? {
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
                val bmp = hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() } ?: return null
                val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                return Gray.fromArgb(px, bmp.width, bmp.height).also { bmp.recycle() }
            } finally { hb.close() }
        }
        override fun nextInt(bound: Int) = rnd.nextInt(bound)
        override fun log(line: String) = say(line)
    }
}
