package app.macro17

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        Diag.d("alarme", "disparou macro id=$id (serviço conectado=${MacroService.instance != null})")
        Store(c).setScheduleTime(id, null) // alarme de uma vez só: já disparou
        MacroService.instance?.runMacro(id)
            ?: android.widget.Toast.makeText(c, "Macro agendada não rodou: serviço de acessibilidade desligado", android.widget.Toast.LENGTH_LONG).show()
    }
}

object Alarms {
    private fun pi(c: Context, id: String) = PendingIntent.getBroadcast(
        c, id.hashCode(), Intent(c, AlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** O PendingIntent some junto com o alarme após reinício; sem ele o horário guardado está velho. */
    fun isScheduled(c: Context, id: String) = PendingIntent.getBroadcast(
        c, id.hashCode(), Intent(c, AlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
    ) != null

    fun cancel(c: Context, id: String) { c.getSystemService(AlarmManager::class.java).cancel(pi(c, id)) }

    /**
     * Agenda a próxima ocorrência de hh:mm (uma vez). Retorna true se o alarme é exato.
     * shortcut: alarme não sobrevive a reinício nem a "forçar parada"; sem BOOT_COMPLETED, subir se virar requisito.
     */
    fun schedule(c: Context, id: String, hour: Int, minute: Int): Boolean {
        val t = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val am = c.getSystemService(AlarmManager::class.java)
        val p = pi(c, id)
        if (am.canScheduleExactAlarms()) {
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p); return true } catch (_: SecurityException) { /* permissão revogada na corrida */ }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
        return false
    }
}
