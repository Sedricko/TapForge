package com.tapforge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.tapforge.R
import com.tapforge.data.MacroStore
import com.tapforge.data.RunLog
import com.tapforge.data.Settings
import com.tapforge.data.Stats
import com.tapforge.engine.Device
import com.tapforge.engine.EngineEvent
import com.tapforge.engine.GestureDispatcher
import com.tapforge.engine.MacroEngine
import com.tapforge.model.*
import com.tapforge.ui.MainActivity
import com.tapforge.vision.AndroidVision
import com.tapforge.vision.Templates
import com.tapforge.vision.cropTo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Executa gestos, hospeda o painel flutuante e controla a execução dos macros.
 * As janelas usam TYPE_ACCESSIBILITY_OVERLAY: não precisa de "Exibir sobre outros apps".
 */
class ClickerAccessibilityService : AccessibilityService(), GestureDispatcher, Device {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var settings: Settings
    private var job: Job? = null
    private var runSeq = 0
    private val gate = Channel<Unit>(Channel.CONFLATED)
    private var panel: Panel? = null
    private var targets: TargetsOverlay? = null
    private var recorder: RecorderOverlay? = null
    @Volatile private var fg: String? = null

    @Volatile var running: Macro? = null; private set
    @Volatile var paused = false; private set
    @Volatile var iteration = 0; private set
    private var startedAt = 0L
    private var found = 0

    val isRunning get() = job?.isActive == true
    val isRecording get() = recorder != null

    override fun onServiceConnected() {
        instance = this
        settings = Settings(this)
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Execução de macros", NotificationManager.IMPORTANCE_LOW))
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(ACTION_STOP); addAction(Intent.ACTION_POWER_CONNECTED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        panel = Panel(this).also { it.show() }
        RunLog.add(this, "Serviço de acessibilidade ativo")
    }

    override fun onInterrupt() { stop() }

    override fun onDestroy() {
        stop(); scope.cancel()
        runCatching { unregisterReceiver(receiver) }
        recorder?.remove(); targets?.remove(); panel?.remove()
        instance = null
        super.onDestroy()
    }

    // ---------- Gatilhos e app em primeiro plano ----------
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        // Janelas flutuantes do próprio TapForge não contam como "app aberto".
        if (pkg == packageName && e.className?.startsWith("com.tapforge") != true) return
        if (pkg == fg) return
        fg = pkg
        if (!isRunning) MacroStore.list(this).firstOrNull { (it.trigger as? Trigger.AppOpened)?.packageName == pkg }
            ?.let { start(it, "app aberto") }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                ACTION_STOP -> stop()
                Intent.ACTION_POWER_CONNECTED -> if (!isRunning)
                    MacroStore.list(c).firstOrNull { it.trigger == Trigger.Charging }?.let { start(it, "carregador") }
            }
        }
    }

    // ---------- Parada de emergência ----------
    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (isRunning && settings.volumeStop && e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (e.action == KeyEvent.ACTION_DOWN) main.post { stop(); toast("Macro parado pela tecla de volume") }
            return true
        }
        return false
    }

    private val shake = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val (x, y, z) = e.values
            if (sqrt(x * x + y * y + z * z) > 25f) main.post { if (isRunning) { stop(); toast("Macro parado ao chacoalhar") } }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    // ---------- Execução ----------
    fun toggle() { if (isRunning) stop() else (MacroStore.selected(this) ?: MacroStore.list(this).firstOrNull())?.let { start(it) } ?: toast("Crie um macro no app primeiro") }

    fun startById(id: String, reason: String) = main.post {
        MacroStore.load(this, id)?.let { start(it, reason) } ?: RunLog.add(this, "Macro \"$id\" não encontrado")
    }

    /** Executa só estas ações uma vez, nas coordenadas de [base] (botão "Testar" do editor). */
    fun test(actions: List<Action>, base: Macro) =
        start(base.copy(id = TEST_ID, name = "Teste", actions = actions, maxIterations = 1, maxRuntimeMs = 0, trigger = null), "teste", select = false)

    fun start(m: Macro, reason: String = "manual", select: Boolean = true) {
        stop()
        if (select) MacroStore.select(this, m.id)
        val seq = ++runSeq
        running = m; paused = false; iteration = 0; found = 0; startedAt = System.currentTimeMillis()
        gate.tryReceive()
        RunLog.add(this, "▶ ${m.name} ($reason)")
        val engine = MacroEngine(this, AndroidVision(applicationContext), this, { MacroStore.load(this, it) }, beforeAction = ::beforeAction)
        job = scope.launch { engine.run(m) { onEvent(seq, m, it) } }
        if (settings.shakeStop) getSystemService(SensorManager::class.java)?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(shake, it, SensorManager.SENSOR_DELAY_UI) }
        }
        if (settings.showTargets && targets == null) targets = TargetsOverlay(this).also { it.show() }
        notifyRunning(m)
        panel?.update()
    }

    fun stop() {
        job?.cancel(); job = null
        paused = false; gate.trySend(Unit)
    }

    fun pause() { if (isRunning) { gate.tryReceive(); paused = true; panel?.update() } }
    fun resume() { paused = false; gate.trySend(Unit); panel?.update() }
    fun step() { if (paused) gate.trySend(Unit) }

    private suspend fun beforeAction(a: Action) {
        var warned = false
        while (fg != null && fg in settings.blocked) {
            if (!warned) { warned = true; RunLog.add(this, "Pausado: app bloqueado aberto ($fg)"); panel?.status("bloqueado: $fg") }
            delay(500)
        }
        if (paused) { panel?.status("⏸ ${describe(a)}"); gate.receive() }
    }

    private fun onEvent(seq: Int, m: Macro, e: EngineEvent) {
        val current = seq == runSeq
        when (e) {
            is EngineEvent.Iteration -> if (current) { iteration = e.n; panel?.update() }
            is EngineEvent.Finished -> finished(seq, m, when (e.reason) {
                "iterações" -> "repetições concluídas"; "tempo" -> "tempo máximo atingido"
                "stop" -> "ação Parar"; else -> "parado"
            })
            is EngineEvent.Failed -> finished(seq, m, "erro: ${e.message}")
            is EngineEvent.Log -> RunLog.add(this, "${m.name}: ${e.message}")
            is EngineEvent.Touched -> if (current) targets?.touch(e.x, e.y)
            is EngineEvent.Found -> if (current) { found++; targets?.found(e.match) }
            is EngineEvent.Step, is EngineEvent.Started -> {}
        }
    }

    private fun finished(seq: Int, m: Macro, why: String) {
        val ms = System.currentTimeMillis() - startedAt
        RunLog.add(this, "■ ${m.name}: $why · $iteration repetições · ${ms / 1000}s")
        if (m.id != TEST_ID) Stats.update(this, m.id) { it.copy(runs = it.runs + 1, totalMs = it.totalMs + ms, lastRun = System.currentTimeMillis(), found = it.found + found) }
        if (seq != runSeq) return
        main.post {
            if (seq != runSeq || isRunning) return@post
            running = null; paused = false
            getSystemService(SensorManager::class.java)?.unregisterListener(shake)
            targets?.remove(); targets = null
            getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
            panel?.update()
        }
    }

    private fun notifyRunning(m: Macro) {
        val stopPi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_STOP).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val openPi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = b.setSmallIcon(R.drawable.ic_notify).setContentTitle("Executando: ${m.name}")
            .setContentText("Toque em Parar ou aperte volume para baixo").setOngoing(true).setContentIntent(openPi)
            .addAction(Notification.Action.Builder(null, "Parar", stopPi).build()).build()
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n) }
    }

    // ---------- Marcar na tela (✚ do painel) ----------
    enum class PickMode(val label: String) { TAP("Toque"), SWIPE("Deslizar"), IMAGE("Imagem"), TEXT("Texto"), COLOR("Cor") }

    fun pick(mode: PickMode) {
        if (mode in setOf(PickMode.IMAGE, PickMode.TEXT, PickMode.COLOR) && ScreenCaptureService.instance == null) {
            toast("Ative a captura de tela no app para usar ${mode.label}"); return
        }
        panel?.setVisible(false)
        val kind = when (mode) { PickMode.TAP, PickMode.COLOR -> PickerOverlay.Kind.POINT; PickMode.SWIPE -> PickerOverlay.Kind.LINE; else -> PickerOverlay.Kind.RECT }
        PickerOverlay(this, kind) { a, b ->
            panel?.setVisible(true)
            if (a != null) scope.launch { delay(350); addPicked(mode, a, b ?: a) }
        }.show()
    }

    private fun addPicked(mode: PickMode, a: Point, b: Point) {
        val (w, h) = realScreenSize(this)
        val base = MacroStore.selected(this) ?: Macro(MacroStore.newId(), "Novo macro", actions = emptyList(), screenW = w, screenH = h)
        fun mx(v: Int) = if (base.screenW > 0) v * base.screenW / w else v
        fun my(v: Int) = if (base.screenH > 0) v * base.screenH / h else v
        val rect = Region(minOf(a.x, b.x), minOf(a.y, b.y), kotlin.math.abs(a.x - b.x).coerceAtLeast(8), kotlin.math.abs(a.y - b.y).coerceAtLeast(8))
        val frame = ScreenCaptureService.instance?.latestFrame()
        val action: Action = when (mode) {
            PickMode.TAP -> Action.Tap(mx(a.x), my(a.y))
            PickMode.SWIPE -> Action.Swipe(mx(a.x), my(a.y), mx(b.x), my(b.y))
            PickMode.TEXT -> Action.ReadText(Region(mx(rect.x), my(rect.y), mx(rect.w), my(rect.h)), "texto")
            PickMode.IMAGE -> {
                val crop = frame?.cropTo(rect) ?: return toast("Não foi possível recortar a imagem")
                val existing = Templates.list(this).toSet()
                val id = generateSequence(1) { it + 1 }.map { "imagem_$it" }.first { it !in existing }
                Templates.save(this, id, crop)
                Action.FindImage(id, timeoutMs = 2000, onFound = listOf(Action.TapFound()))
            }
            PickMode.COLOR -> {
                val px = frame?.takeIf { a.x < it.width && a.y < it.height }?.getPixel(a.x, a.y) ?: return toast("Sem imagem da tela")
                val hex = String.format("#%06X", px and 0xFFFFFF)
                Action.If(Condition.Pixel(mx(a.x), my(a.y), hex), then = listOf(Action.Tap(mx(a.x), my(a.y))))
            }
        }
        MacroStore.save(this, base.copy(actions = base.actions + action))
        MacroStore.select(this, base.id)
        toast("Adicionado a \"${base.name}\": ${describe(action)}")
        main.post { panel?.update() }
    }

    // ---------- Gravação (⏺ do painel) ----------
    fun toggleRecording() {
        val r = recorder
        if (r == null) {
            stop()
            recorder = RecorderOverlay(this) { strokes -> scope.launch { gesture(strokes) } }.also { it.show() }
            panel?.bringToFront()
            toast("Gravando: use a tela normalmente e toque em ⏺ para terminar")
        } else {
            val actions = r.finish(); r.remove(); recorder = null
            if (actions.isEmpty()) toast("Nada gravado")
            else {
                val (w, h) = realScreenSize(this)
                val time = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                val m = Macro(MacroStore.newId(), "Gravação $time", actions = actions, screenW = w, screenH = h, maxIterations = 1)
                MacroStore.save(this, m); MacroStore.select(this, m.id)
                toast("Gravação salva com ${actions.size} ações")
            }
        }
        panel?.update()
    }

    // ---------- GestureDispatcher ----------
    override suspend fun gesture(strokes: List<Stroke>): Boolean {
        val b = GestureDescription.Builder()
        var n = 0
        for (s in strokes.take(GestureDescription.getMaxStrokeCount())) {
            if (s.points.isEmpty()) continue
            val p = Path()
            s.points.forEachIndexed { i, pt ->
                val x = pt.x.coerceAtLeast(0).toFloat(); val y = pt.y.coerceAtLeast(0).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            val start = s.startMs.coerceIn(0, GestureDescription.getMaxGestureDuration() - 1)
            b.addStroke(GestureDescription.StrokeDescription(p, start, s.durationMs.coerceIn(1, GestureDescription.getMaxGestureDuration() - start)))
            n++
        }
        if (n == 0) return false
        val g = runCatching { b.build() }.getOrNull() ?: return false
        return suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }
    }

    override fun global(kind: GlobalKind): Boolean = performGlobalAction(
        when (kind) {
            GlobalKind.BACK -> GLOBAL_ACTION_BACK
            GlobalKind.HOME -> GLOBAL_ACTION_HOME
            GlobalKind.RECENTS -> GLOBAL_ACTION_RECENTS
            GlobalKind.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            GlobalKind.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            GlobalKind.LOCK_SCREEN -> GLOBAL_ACTION_LOCK_SCREEN // Android 9+
        }
    )

    // ---------- Device ----------
    override fun screenSize() = realScreenSize(this)
    override fun foregroundPackage() = fg

    override fun openApp(packageName: String): Boolean {
        val i = this.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    override fun typeText(text: String, append: Boolean): Boolean {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val value = if (append) (node.text ?: "").toString() + text else text
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        })
    }

    override fun setClipboard(text: String) {
        main.post { getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("TapForge", text)) }
    }

    override fun alert(message: String, vibrateMs: Long, sound: Boolean) {
        main.post {
            if (message.isNotBlank()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            if (vibrateMs > 0) vibrate(vibrateMs)
            if (sound) runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 250) }
        }
    }

    private fun vibrate(ms: Long) {
        val v = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v?.vibrate(ms)
    }

    fun refreshPanel() { panel?.update() }

    fun toast(msg: String) { main.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }

    companion object {
        @Volatile var instance: ClickerAccessibilityService? = null
        private const val CHANNEL = "run"
        private const val NOTIF_ID = 2
        private const val ACTION_STOP = "com.tapforge.INTERNAL_STOP"
        const val TEST_ID = "__teste"
    }
}
