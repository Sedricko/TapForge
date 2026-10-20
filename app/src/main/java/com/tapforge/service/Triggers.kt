package com.tapforge.service

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.tapforge.data.MacroStore
import com.tapforge.data.RunLog
import com.tapforge.data.Settings
import com.tapforge.model.Trigger
import com.tapforge.ui.MainActivity
import java.util.Calendar

/** Agenda os macros com gatilho de horário (AlarmManager, aproximado). */
object Scheduler {
    private const val ACTION = "com.tapforge.ALARM"

    private fun pi(c: Context, id: String) = PendingIntent.getBroadcast(
        c, id.hashCode(), Intent(c, AlarmReceiver::class.java).setAction(ACTION).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Próximo "HH:mm" a partir de agora; null se o texto for inválido. */
    fun next(time: String, now: Long = System.currentTimeMillis()): Long? {
        val parts = time.trim().split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, parts[0]); set(Calendar.MINUTE, parts[1]); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun reschedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val prefs = c.getSharedPreferences("scheduled", 0)
        val old = prefs.getStringSet("ids", emptySet())!!
        val now = MacroStore.list(c).mapNotNull { m -> (m.trigger as? Trigger.Schedule)?.let { m.id to it.time } }
        (old - now.map { it.first }.toSet()).forEach { am.cancel(pi(c, it)) }
        for ((id, time) in now) next(time)?.let { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it, pi(c, id)) }
        prefs.edit().putStringSet("ids", now.map { it.first }.toSet()).apply()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val s = ClickerAccessibilityService.instance
        if (s == null) RunLog.add(c, "Horário de \"$id\": serviço de acessibilidade desligado")
        else s.startById(id, "horário")
        Scheduler.reschedule(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED || i.action == Intent.ACTION_MY_PACKAGE_REPLACED) Scheduler.reschedule(c)
    }
}

/** Bloco nas Configurações rápidas: inicia/para o macro selecionado. */
class QuickTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = if (ClickerAccessibilityService.instance?.isRunning == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val s = ClickerAccessibilityService.instance
        if (s != null) { s.toggle(); onStartListening(); return }
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}

/**
 * Ponto de entrada de atalhos da tela inicial e de apps de automação (Tasker, Automate).
 * Ação com.tapforge.RUN (extra "macro") ou com.tapforge.STOP. Exige o token do atalho
 * ou a opção "Permitir outros apps" ligada.
 */
class RunActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val settings = Settings(this)
        val ok = intent.getStringExtra("token") == settings.token || settings.allowExternal
        val svc = ClickerAccessibilityService.instance
        when {
            !ok -> Toast.makeText(this, "TapForge: ative \"Permitir outros apps\" nos Ajustes", Toast.LENGTH_LONG).show()
            svc == null -> Toast.makeText(this, "TapForge: ative o serviço de acessibilidade", Toast.LENGTH_LONG).show()
            intent.action == ACTION_STOP -> svc.stop()
            else -> intent.getStringExtra("macro")?.let { svc.startById(it, "atalho") } ?: svc.toggle()
        }
        finish()
    }

    companion object {
        const val ACTION_RUN = "com.tapforge.RUN"
        const val ACTION_STOP = "com.tapforge.STOP"
    }
}
