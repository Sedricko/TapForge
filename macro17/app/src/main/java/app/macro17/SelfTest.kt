package app.macro17

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import java.io.File

/** Autoteste: confere o que o app precisa para funcionar e diz o que falhou. Roda fora da thread principal; cada linha também vai para o registro. */
object SelfTest {
    class Check(val name: String, val ok: Boolean?, val detail: String) {
        // ok == null: aviso (não impede de usar, mas convém ver)
        val mark get() = when (ok) { true -> "✓"; false -> "✗"; null -> "!" }
        override fun toString() = "$mark $name — $detail"
    }

    fun run(ctx: Context): List<Check> {
        val out = ArrayList<Check>()
        fun add(name: String, ok: Boolean?, detail: String) { out += Check(name, ok, detail).also { Diag.d("autoteste", it.toString()) } }
        fun safe(name: String, f: () -> Check) = try { out += f().also { Diag.d("autoteste", it.toString()) } } catch (e: Throwable) { add(name, false, "erro: ${e.javaClass.simpleName}: ${e.message}") }
        val st = Store(ctx)
        Diag.d("autoteste", "início")

        // --- serviço e execução (precisam da acessibilidade) ---
        val svc = MacroService.instance
        if (svc == null) {
            add("Serviço de acessibilidade", false, "desligado. Em Permissões, toque em Acessibilidade (se estiver acinzentado: Informações do app › ⋮ › Permitir configurações restritas)")
        } else {
            add("Serviço de acessibilidade", true, "conectado")
            svc.selfTest().forEach { out += it.also { c -> Diag.d("autoteste", c.toString()) } }
        }

        // --- bibliotecas ---
        safe("OpenCV") { if (Cv.ok) Check("OpenCV", true, "carregado (busca por imagem rápida)") else Check("OpenCV", null, "não carregou; a busca usa o buscador em Kotlin (mais lento)") }
        safe("Reconhecimento de texto (ML Kit)") {
            com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS).close()
            Check("Reconhecimento de texto (ML Kit)", true, "disponível")
        }

        // --- dados ---
        safe("Armazenamento") {
            val f = File(ctx.filesDir, "autoteste.tmp"); f.writeText("ok"); val ok = f.readText() == "ok"; f.delete()
            Check("Armazenamento", ok, if (ok) "gravar e ler no armazenamento do app" else "não leu o que gravou")
        }
        safe("Macros salvas") {
            val ms = st.list(); val ids = File(ctx.filesDir, "macros").listFiles().orEmpty().count { it.extension == "json" }
            if (ms.size == ids) Check("Macros salvas", true, "${ms.size} válidas") else Check("Macros salvas", null, "${ms.size} válidas de $ids arquivos (as inválidas não aparecem na lista)")
        }
        safe("Registro (monitor)") { Check("Registro (monitor)", Diag.enabled, if (Diag.enabled) "ligado; ${Diag.feed.snapshot(Cat.ALL, 5000).size} entradas em memória" else "desligado em Opções do monitor") }

        // --- permissões ---
        val granted = { p: String -> ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED }
        add("Notificações", if (granted(Manifest.permission.POST_NOTIFICATIONS)) true else null, if (granted(Manifest.permission.POST_NOTIFICATIONS)) "permitidas" else "negadas (a gravação de tela mostra a notificação só no gerenciador)")
        add("Exibir sobre outros apps", if (Settings.canDrawOverlays(ctx)) true else null, if (Settings.canDrawOverlays(ctx)) "permitido" else "não permitido (o painel usa janela de acessibilidade e funciona mesmo assim)")
        val exact = ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        add("Alarmes exatos (agendamento)", if (exact) true else null, if (exact) "permitidos" else "negados: o agendamento dispara aproximado")
        val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        add("Bateria sem restrições", if (battery) true else null, if (battery) "sem otimização" else "otimizada: o Android pode atrasar o agendamento com a tela apagada")
        add("Microfone (gravação de tela com áudio)", if (st.recAudio == 0 || granted(Manifest.permission.RECORD_AUDIO)) true else false, if (st.recAudio == 0) "áudio desligado" else if (granted(Manifest.permission.RECORD_AUDIO)) "permitido" else "áudio ligado, mas o microfone foi negado")

        // --- modo de controle ---
        safe("Modo de controle") {
            when (st.controlMode) {
                MODE_ROOT -> Check("Modo de controle", String(st.shell()!!.run("id")).contains("uid=0"), "root: `su -c id`")
                MODE_ADB -> Check("Modo de controle", String(st.shell()!!.run("echo macro17")).trim() == "macro17", "ADB local na porta ${st.adbPort}")
                else -> Check("Modo de controle", true, "acessibilidade")
            }
        }

        // --- agendamento ---
        safe("Agendamentos") {
            val all = st.alarms(); val on = all.filter { it.enabled }
            val bad = on.filter { Alarms.conflict(it, all) }
            if (bad.isEmpty()) Check("Agendamentos", true, "${on.size} ativos de ${all.size}") else Check("Agendamentos", false, "${bad.size} ativos em conflito de horário (não disparam)")
        }
        val total = out.count { it.ok == true }; val fail = out.count { it.ok == false }; val warn = out.count { it.ok == null }
        Diag.d("autoteste", "fim: $total ok, $warn avisos, $fail falhas")
        return out
    }
}
