package app.macro17

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.WindowManager
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Executa uma macro numa thread própria; [report] recebe o motivo do fim (de qualquer thread). */
class Engine(private val svc: AccessibilityService, private val report: (String) -> Unit) {
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
        val rnd = Random()
        report(
            try {
                if (m.steps.isEmpty()) "Macro vazia"
                else {
                    var i = 0
                    while (m.loops == 0 || i < m.loops) {
                        for (s in m.steps) {
                            exec(s, rnd)
                            pause(s.delay + if (s.rnd > 0) rnd.nextInt(s.rnd + 1) else 0)
                        }
                        i++
                    }
                    "Macro concluída"
                }
            } catch (e: InterruptedException) {
                "Macro parada"
            } catch (e: Exception) {
                "Erro: ${e.message}"
            }
        )
    }

    // Mínimo de 1 ms por passo: macro infinita só com esperas de 0 ms não pode virar laço quente.
    private fun pause(ms: Int) = Thread.sleep(maxOf(1, ms).toLong())

    private fun exec(s: Step, r: Random) {
        when (s.type) {
            Type.WAIT -> pause(s.ms)
            Type.KEY -> check(svc.performGlobalAction(s.x1)) { "tecla recusada pelo sistema" }
            else -> gesture(s, r)
        }
    }

    private fun gesture(s: Step, r: Random) {
        // Reavalia a cada gesto: a tela pode ter girado desde o passo anterior.
        val b = svc.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        fun px(v: Int, max: Int) = (v + if (s.jitter > 0) r.nextInt(2 * s.jitter + 1) - s.jitter else 0).coerceIn(0, max - 1)
        val p = Path().apply {
            moveTo(px(s.x1, b.width()).toFloat(), px(s.y1, b.height()).toFloat())
            if (s.type == Type.SWIPE) lineTo(px(s.x2, b.width()).toFloat(), px(s.y2, b.height()).toFloat())
        }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, s.ms.toLong())).build()
        val done = CountDownLatch(1)
        var ok = false // escrito na thread principal antes do countDown; lido depois do await
        val accepted = svc.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription) { ok = true; done.countDown() }
            override fun onCancelled(d: GestureDescription) { done.countDown() }
        }, null)
        check(accepted) { "gesto rejeitado (serviço sem permissão de gestos?)" }
        check(done.await(s.ms + 3000L, TimeUnit.MILLISECONDS)) { "gesto sem resposta do sistema" }
        check(ok) { "gesto cancelado (tela bloqueada ou app protegido)" }
    }
}
