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
            override fun onActivityCreated(a: Activity, b: Bundle?) = log(a, "criada")
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
}
