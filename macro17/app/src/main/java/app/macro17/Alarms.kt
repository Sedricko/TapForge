package app.macro17

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.UUID

/** Agendamento como no base: macro + hora de início (minuto do dia) + tempo de atividade (min); repete todo dia. */
data class Alarm(val id: String = UUID.randomUUID().toString(), val macroId: String, val start: Int, val uptime: Int, val enabled: Boolean = true) {
    val end get() = (start + uptime) % 1440
    fun label() = "%d:%02d, %d Minutos".format(start / 60, start % 60, uptime)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val stop = i.getBooleanExtra("stop", false)
        Diag.d("alarme", "disparou id=$id stop=$stop (serviço=${MacroService.instance != null})")
        val a = Store(c).alarms().firstOrNull { it.id == id && it.enabled } ?: return // alarme apagado ou desligado: nada a iniciar nem a parar
        if (stop) { MacroService.instance?.stopMacro(); return }
        Alarms.rearm(c) // próxima ocorrência (amanhã)
        Alarms.arm(c, a.id, true, System.currentTimeMillis() + a.uptime * 60_000L)
        runCatching { c.startActivity(Intent(c, FullScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } // acende a tela
        val svc = MacroService.instance
        if (svc == null) android.widget.Toast.makeText(c, "Macro agendada não rodou: serviço de acessibilidade desligado", android.widget.Toast.LENGTH_LONG).show()
        else svc.fireAlarm(a.macroId)
    }
}

/** Reagenda tudo quando o aparelho liga, o app atualiza ou a hora muda. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Alarms.rearm(c) }
}

object Alarms {
    private fun pi(c: Context, id: String, stop: Boolean, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT) = PendingIntent.getBroadcast(
        c, (id + stop).hashCode(), Intent(c, AlarmReceiver::class.java).putExtra("id", id).putExtra("stop", stop),
        PendingIntent.FLAG_IMMUTABLE or flags,
    )

    fun canExact(c: Context) = c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Intervalos [início, fim) no relógio de 24h, circulares: dois alarmes ativos que se cruzam entram em conflito. */
    fun conflict(a: Alarm, all: List<Alarm>) = all.any { o ->
        o.id != a.id && o.enabled && (covers(a, o.start) || covers(o, a.start))
    }

    private fun covers(a: Alarm, minute: Int) = (minute - a.start + 1440) % 1440 < a.uptime

    fun nextStart(start: Int, now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, start / 60); set(Calendar.MINUTE, start % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    fun arm(c: Context, id: String, stop: Boolean, at: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val p = pi(c, id, stop)
        if (canExact(c)) {
            try { am.setAlarmClock(AlarmManager.AlarmClockInfo(at, p), p); return } catch (_: SecurityException) { /* permissão revogada na corrida */ }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    /** Idempotente: cancela os desativados e arma o próximo início dos ativos. Devolve false se falta a permissão de alarme exato. */
    fun rearm(c: Context): Boolean {
        val am = c.getSystemService(AlarmManager::class.java)
        val all = Store(c).alarms()
        all.filter { !it.enabled }.forEach { am.cancel(pi(c, it.id, false)); am.cancel(pi(c, it.id, true)) }
        val on = all.filter { it.enabled && !conflict(it, all) }
        on.forEach { arm(c, it.id, false, nextStart(it.start)) }
        return on.isEmpty() || canExact(c)
    }
}
