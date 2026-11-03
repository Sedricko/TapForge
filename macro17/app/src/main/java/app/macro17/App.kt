package app.macro17

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.os.Process

/** Liga o diagnóstico antes de qualquer tela ou serviço e registra falhas fatais e o ciclo de vida das telas. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        IconRes.ctx = applicationContext
        Diag.init(this)
        Diag.d("app", "processo iniciado pid=${Process.myPid()}")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Diag.fatal(t, e)
            prev?.uncaughtException(t, e)
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private fun log(a: Activity, what: String) = Diag.d("tela", "${a.javaClass.simpleName} $what")
            override fun onActivityCreated(a: Activity, b: Bundle?) { log(a, "criada"); watchInput(a) }
            override fun onActivityStarted(a: Activity) = log(a, "visível")
            override fun onActivityResumed(a: Activity) = log(a, "em primeiro plano")
            override fun onActivityPaused(a: Activity) = log(a, "pausada")
            override fun onActivityStopped(a: Activity) = log(a, "parada")
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) = log(a, "destruída")
        })
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = Diag.d("memória", "onTrimMemory nível $level")
            override fun onConfigurationChanged(c: Configuration) = Diag.d("sistema", "configuração mudou (rotação/idioma/tema) orientação=${c.orientation}")
            @Deprecated("Deprecated in Java") override fun onLowMemory() = Diag.d("memória", "onLowMemory")
        })
    }

    /** Registra cada toque e tecla que chega a uma tela do app, com o que foi tocado (texto, descrição ou id da vista). */
    private fun watchInput(a: Activity) {
        val orig = a.window.callback ?: return
        a.window.callback = java.lang.reflect.Proxy.newProxyInstance(orig.javaClass.classLoader, arrayOf(android.view.Window.Callback::class.java)) { _, m, args ->
            runCatching {
                when (m.name) {
                    "dispatchTouchEvent" -> (args[0] as android.view.MotionEvent).let { e ->
                        if (e.actionMasked == android.view.MotionEvent.ACTION_UP) Diag.d("toque", "${a.javaClass.simpleName} (${e.rawX.toInt()},${e.rawY.toInt()}) → ${describe(a.window.decorView, e.rawX.toInt(), e.rawY.toInt())}")
                    }
                    "dispatchKeyEvent" -> (args[0] as android.view.KeyEvent).let { k -> if (k.action == android.view.KeyEvent.ACTION_UP) Diag.d("tecla", "${a.javaClass.simpleName} ${android.view.KeyEvent.keyCodeToString(k.keyCode)}") }
                }
            }
            try { if (args == null) m.invoke(orig) else m.invoke(orig, *args) } catch (t: java.lang.reflect.InvocationTargetException) { throw t.targetException }
        } as android.view.Window.Callback
    }

    private fun describe(root: android.view.View, x: Int, y: Int): String {
        var v: android.view.View = root; val loc = IntArray(2)
        while (v is android.view.ViewGroup) {
            val g = v as android.view.ViewGroup
            val child = (g.childCount - 1 downTo 0).map { g.getChildAt(it) }.firstOrNull { c ->
                c.visibility == android.view.View.VISIBLE && c.getLocationOnScreen(loc).let { x >= loc[0] && x < loc[0] + c.width && y >= loc[1] && y < loc[1] + c.height }
            } ?: break
            v = child
        }
        val txt = (v as? android.widget.TextView)?.text?.toString()?.take(40)
        val id = runCatching { if (v.id != android.view.View.NO_ID) v.resources.getResourceEntryName(v.id) else null }.getOrNull()
        return v.javaClass.simpleName + listOfNotNull(txt?.let { "“$it”" }, v.contentDescription?.let { "[$it]" }, id?.let { "#$it" }).joinToString("")
    }
}
