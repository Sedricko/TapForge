package com.tapforge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings as SysSettings
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.tapforge.R
import com.tapforge.data.*
import com.tapforge.model.Macro
import com.tapforge.model.macroTemplates
import com.tapforge.service.ClickerAccessibilityService
import com.tapforge.service.RunActivity
import com.tapforge.service.ScreenCaptureService
import com.tapforge.service.realScreenSize
import com.tapforge.vision.Templates

class MainActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var nav: BottomNavigationView
    private val settings by lazy { Settings(this) }
    private val main = Handler(Looper.getMainLooper())
    private var tab = R.id.nav_home
    private var exporting: Macro? = null

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK && r.data != null) {
            ContextCompat.startForegroundService(this, Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_CODE, r.resultCode).putExtra(ScreenCaptureService.EXTRA_DATA, r.data))
            main.postDelayed({ render() }, 600)
        }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importFrom(it) } }
    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { write(it, MacroStore.backup(this), "Backup salvo") }
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val m = exporting
        if (uri != null && m != null) write(uri, MacroStore.encode(m), "Macro exportado")
    }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }
    private val logListener: () -> Unit = { runOnUiThread { if (tab == R.id.nav_log) render() } }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.content)
        toolbar = findViewById(R.id.toolbar)
        fab = findViewById(R.id.fab)
        nav = findViewById(R.id.nav)
        nav.setOnItemSelectedListener { tab = it.itemId; render(); true }
        fab.setOnClickListener { newMacroSheet() }
        if (!settings.tosAccepted) welcome()
    }

    override fun onResume() { super.onResume(); RunLog.listen(logListener); render() }
    override fun onPause() { RunLog.unlisten(logListener); super.onPause() }

    private fun snack(msg: String) = Snackbar.make(content, msg, Snackbar.LENGTH_LONG).setAnchorView(if (fab.isShown) fab else nav).show()

    private fun render() {
        content.removeAllViews()
        fab.visibility = if (tab == R.id.nav_macros) android.view.View.VISIBLE else android.view.View.GONE
        when (tab) {
            R.id.nav_home -> home()
            R.id.nav_macros -> macros()
            R.id.nav_log -> log()
            R.id.nav_settings -> settingsPage()
        }
    }

    private fun welcome() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Bem-vindo ao TapForge")
            .setMessage("Automatize toques, gestos e tarefas repetitivas no seu Android: toques em sequência, busca de imagens, leitura de texto, condições e muito mais.\n\n" +
                "Sem anúncios, sem conta e sem internet: tudo fica no aparelho.\n\n" +
                "Use com responsabilidade: em jogos online e em alguns apps, automação pode violar os termos de uso e levar a banimento.")
            .setCancelable(false)
            .setPositiveButton("Começar") { _, _ -> settings.tosAccepted = true }.show()
    }

    // ---------- Início ----------
    private fun home() {
        toolbar.subtitle = "Auto clicker e macros"
        val svc = ClickerAccessibilityService.instance
        val selected = MacroStore.selected(this)
        val running = svc?.isRunning == true

        content.section("Macro do painel flutuante")
        if (selected == null) content.card("✨", "Nenhum macro selecionado", "Crie um macro a partir de um modelo pronto ou do zero.",
            action = "Criar macro", onAction = { newMacroSheet() })
        else content.card(if (running) "⏹️" else "▶️", selected.name,
            "${actionCount(selected)} ações · ${limitsText(selected)}" + (triggerText(selected.trigger)?.let { " · $it" } ?: "") + "\n" + Stats.summary(this, selected),
            action = if (running) "Parar" else "Executar", onAction = { run(selected) },
            endIcon = R.drawable.ic_edit, endLabel = "Editar", onEnd = { edit(selected) })

        content.section("Configuração")
        content.card(if (svc != null) "✅" else "⚠️", "Serviço de acessibilidade",
            if (svc != null) "Ativo. O painel flutuante está na tela." else "Necessário para tocar na tela. Toque em Ativar, escolha TapForge e ligue o serviço.",
            action = if (svc != null) "Configurações" else "Ativar",
            onAction = { startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS)) })
        val capturing = ScreenCaptureService.instance != null
        content.card(if (capturing) "✅" else "📷", "Captura de tela",
            if (capturing) "Ativa: imagem, texto (OCR) e cor funcionam." else "Opcional. Necessária para procurar imagens, ler texto e cores. O Android pede confirmação a cada ativação.",
            action = if (capturing) "Desligar" else "Ativar",
            onAction = {
                if (capturing) { ScreenCaptureService.stop(this); main.postDelayed({ render() }, 400) }
                else captureLauncher.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
            })
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            content.card("🔔", "Notificações", "Mostra o macro em execução com um botão Parar.",
                action = "Permitir", onAction = { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) })
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName))
            content.card("🔋", "Economia de bateria", "Alguns aparelhos fecham o serviço para economizar bateria e o painel some. Veja como evitar.",
                action = "Ver guia", onAction = { batteryGuide() })

        content.section("Como usar")
        content.card("🧭", "Painel flutuante",
            "▶ executar ou parar · ⏸ pausar · ⏭ próxima ação (quando pausado)\n" +
                "✚ marcar na tela: toque, deslize, imagem, texto ou cor\n" +
                "⏺ gravar seus toques · – recolher em bolha · ☰ arrastar\n" +
                "Toque no nome do macro para trocar de macro.")
        content.card("🛑", "Parada de emergência",
            "Aperte volume para baixo ou use o botão Parar da notificação." + if (settings.shakeStop) " Chacoalhar também para." else "")
        content.card("⚖️", "Use com responsabilidade",
            "Em jogos online e alguns apps, automação pode violar os termos de uso e levar a banimento. Bloqueie apps sensíveis (como bancos) nos Ajustes.")
    }

    private fun run(m: Macro) {
        val svc = ClickerAccessibilityService.instance ?: return snack("Ative o serviço de acessibilidade na aba Início")
        if (svc.isRunning) { svc.stop(); main.postDelayed({ render() }, 300); return }
        snack("\"${m.name}\" começa em 3 s: vá para o app desejado")
        moveTaskToBack(true)
        main.postDelayed({ svc.start(m) }, 3000)
    }

    private fun edit(m: Macro) = startActivity(Intent(this, EditorActivity::class.java).putExtra("id", m.id))

    // ---------- Macros ----------
    private fun macros() {
        toolbar.subtitle = "Seus macros"
        val list = MacroStore.list(this)
        if (list.isEmpty()) {
            content.section("Nenhum macro ainda")
            content.card("✨", "Comece por um modelo", "Toque em Novo macro: há modelos prontos para toque repetido, rolagem, fechar anúncios, OCR e mais.",
                action = "Novo macro", onAction = { newMacroSheet() })
            return
        }
        val selectedId = MacroStore.selected(this)?.id
        var header: String? = null
        for (m in list) {
            val h = if (m.favorite) "★ Favoritos" else m.folder.ifBlank { "Sem pasta" }
            if (h != header) { content.section(h); header = h }
            macroCard(m, m.id == selectedId)
        }
    }

    private fun macroCard(m: Macro, selected: Boolean) {
        val v = LayoutInflater.from(this).inflate(R.layout.item_macro, content, false)
        v.findViewById<TextView>(R.id.title).text = m.name
        v.findViewById<TextView>(R.id.desc).text = "${actionCount(m)} ações · ${limitsText(m)}"
        v.findViewById<TextView>(R.id.stats).text = Stats.summary(this, m)
        val chips = v.findViewById<ChipGroup>(R.id.chips)
        fun chip(t: String) = chips.addView(Chip(this).apply { text = t; isClickable = false })
        if (selected) chip("No painel")
        if (m.favorite) chip("★ Favorito")
        triggerText(m.trigger)?.let { chip("⚡ $it") }
        if (m.screenW > 0) chip("Ajusta à tela")
        if (chips.childCount == 0) chips.visibility = android.view.View.GONE
        v.findViewById<MaterialButton>(R.id.run).setOnClickListener { run(m) }
        v.findViewById<MaterialButton>(R.id.edit).setOnClickListener { edit(m) }
        v.setOnClickListener { edit(m) }
        v.findViewById<MaterialButton>(R.id.more).setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menuInflater.inflate(R.menu.macro_item, menu)
                setOnMenuItemClickListener { item -> macroMenu(m, item.itemId); true }
            }.show()
        }
        content.addView(v)
    }

    private fun macroMenu(m: Macro, id: Int) {
        when (id) {
            R.id.m_run -> run(m)
            R.id.m_edit -> edit(m)
            R.id.m_select -> { MacroStore.select(this, m.id); ClickerAccessibilityService.instance?.refreshPanel(); snack("\"${m.name}\" está no painel flutuante"); render() }
            R.id.m_fav -> { MacroStore.save(this, m.copy(favorite = !m.favorite)); render() }
            R.id.m_dup -> { MacroStore.save(this, m.copy(id = MacroStore.newId(), name = "${m.name} (cópia)", trigger = null)); render() }
            R.id.m_shortcut -> shortcut(m)
            R.id.m_share -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json")
                .putExtra(Intent.EXTRA_SUBJECT, "Macro TapForge: ${m.name}").putExtra(Intent.EXTRA_TEXT, MacroStore.encode(m)), "Compartilhar macro"))
            R.id.m_export -> { exporting = m; exportLauncher.launch("${m.name}.json") }
            R.id.m_delete -> confirm("Excluir \"${m.name}\"?", "O macro e suas versões anteriores serão apagados.", "Excluir") {
                MacroStore.delete(this, m.id); render()
            }
        }
    }

    private fun shortcut(m: Macro) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) return snack("Seu inicializador não aceita atalhos")
        val intent = Intent(RunActivity.ACTION_RUN).setClass(this, RunActivity::class.java)
            .putExtra("macro", m.id).putExtra("token", settings.token)
        val info = ShortcutInfoCompat.Builder(this, "run_${m.id}").setShortLabel(m.name.take(24))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_launcher)).setIntent(intent).build()
        ShortcutManagerCompat.requestPinShortcut(this, info, null)
    }

    private fun newMacroSheet() {
        val sheet = BottomSheetDialog(this)
        val box = vertical(16)
        box.section("Começar")
        val (w, h) = realScreenSize(this)
        box.card("📄", "Em branco", "Um macro vazio para montar do zero no editor.") {
            sheet.dismiss(); create(Macro(MacroStore.newId(), "Novo macro", actions = emptyList(), screenW = w, screenH = h))
        }
        box.card("⏺️", "Gravar meus toques", "Use o botão ⏺ do painel flutuante: seus toques viram um macro.") {
            sheet.dismiss()
            if (ClickerAccessibilityService.instance == null) snack("Ative o serviço de acessibilidade primeiro")
            else { ClickerAccessibilityService.instance?.toggleRecording(); moveTaskToBack(true) }
        }
        box.card("📥", "Importar arquivo", "Abra um macro (.json) ou um backup do TapForge.") { sheet.dismiss(); importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        box.section("Modelos prontos")
        for (t in macroTemplates(w, h) { MacroStore.newId() }) {
            box.card("🧩", t.title, t.desc) { sheet.dismiss(); create(t.macro) }
        }
        sheet.setContentView(scrolling(box))
        sheet.show()
    }

    private fun create(m: Macro) { MacroStore.save(this, m); MacroStore.select(this, m.id); edit(m) }

    // ---------- Registro ----------
    private fun log() {
        toolbar.subtitle = "O que aconteceu nas execuções"
        val lines = RunLog.lines(this)
        content.section("Registro")
        if (lines.isEmpty()) { content.card("🧾", "Nada registrado ainda", "Quando um macro rodar, cada início, fim, erro e mensagem aparece aqui."); return }
        val row = LinearLayout(this).apply { gravity = android.view.Gravity.END }
        row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Limpar"; setOnClickListener { RunLog.clear(this@MainActivity) }
        })
        row.addView(MaterialButton(this).apply {
            text = "Compartilhar"
            setOnClickListener { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n")), "Compartilhar registro")) }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        content.addView(row)
        content.addView(com.google.android.material.card.MaterialCardView(this).apply {
            addView(mono(lines.takeLast(300).joinToString("\n")).apply { setPadding(dp(16), dp(16), dp(16), dp(16)) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    // ---------- Ajustes ----------
    private fun settingsPage() {
        toolbar.subtitle = "Ajustes"
        content.section("Execução")
        content.setting("Parar com volume para baixo", "Durante a execução, a tecla de volume para baixo para o macro na hora.", settings.volumeStop) { settings.volumeStop = it }
        content.setting("Parar ao chacoalhar", "Chacoalhe o aparelho para parar o macro.", settings.shakeStop) { settings.shakeStop = it }
        content.setting("Mostrar alvos na tela", "Desenha um círculo onde o macro toca e um quadro onde achou imagens. Pode atrapalhar a busca de imagens.", settings.showTargets) { settings.showTargets = it }
        content.setting("Transparência do painel", "Quanto o painel flutuante aparece. Vale ao reativar o serviço.")
        content.addView(Slider(this).apply {
            valueFrom = 30f; valueTo = 100f; stepSize = 5f; value = settings.panelAlpha.toFloat().coerceIn(30f, 100f)
            contentDescription = "Transparência do painel"
            addOnChangeListener { _, v, _ -> settings.panelAlpha = v.toInt() }
        })

        content.section("Segurança")
        val blocked = settings.blocked
        content.setting("Apps bloqueados", if (blocked.isEmpty()) "Nenhum. O macro pausa sozinho enquanto um destes apps estiver aberto (ex.: banco, senhas)."
            else blocked.joinToString(", ") { appLabel(it) }) { pickApps(blocked) { settings.blocked = it; render() } }
        content.setting("Permitir outros apps", "Deixa apps como Tasker e Automate iniciarem macros com a ação com.tapforge.RUN (extra \"macro\" = id do macro). Atalhos criados pelo TapForge funcionam sempre.",
            settings.allowExternal) { settings.allowExternal = it }

        content.section("Dados")
        content.setting("Fazer backup", "Salva todos os macros e imagens recortadas num único arquivo.") { backupLauncher.launch("tapforge-backup.json") }
        content.setting("Restaurar ou importar", "Abre um backup ou um macro (.json). Macros com o mesmo id são substituídos (a versão antiga fica no histórico).") {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        content.setting("Imagens recortadas", "${Templates.list(this).size} imagens. Toque para ver ou apagar.") { templatesDialog() }

        content.section("Aparelho")
        content.setting("Guia de bateria", "Evite que o sistema feche o TapForge (${Build.MANUFACTURER}).") { batteryGuide() }
        content.setting("Configurações de acessibilidade", "Ligar ou desligar o serviço do TapForge.") { startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS)) }

        content.section("Sobre")
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        content.setting("TapForge $version", "Sem anúncios, sem conta e sem internet. Macros em JSON aberto.")
        content.setting("Uso responsável", "Em jogos online e alguns apps, automação pode violar os termos de uso e levar a banimento.")
    }

    private fun templatesDialog() {
        val box = vertical()
        val ids = Templates.list(this)
        if (ids.isEmpty()) box.addView(TextView(this).apply { text = "Nenhuma imagem. Use ✚ › Imagem no painel flutuante para recortar da tela." })
        for (id in ids) {
            val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            row.addView(ImageView(this).apply {
                setImageBitmap(android.graphics.BitmapFactory.decodeFile(Templates.file(this@MainActivity, id).path))
                adjustViewBounds = true; maxHeight = dp(56); maxWidth = dp(96); contentDescription = id
            })
            row.addView(TextView(this).apply { text = id; setPadding(dp(12), 0, dp(12), 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "Apagar"; setOnClickListener { Templates.delete(this@MainActivity, id); row.visibility = android.view.View.GONE }
            })
            box.addView(row)
        }
        MaterialAlertDialogBuilder(this).setTitle("Imagens recortadas").setView(scrolling(box)).setPositiveButton("Fechar") { _, _ -> render() }.show()
    }

    private fun batteryGuide() {
        val m = Build.MANUFACTURER.lowercase()
        val steps = when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> "Configurações › Apps › TapForge › Economia de bateria › Sem restrições. Ative também Início automático. Nos Recentes, segure o TapForge e toque no cadeado."
            "samsung" in m -> "Configurações › Bateria › Limites de uso em segundo plano: tire o TapForge de \"Apps em suspensão\" e adicione em \"Apps que nunca são suspensos\"."
            listOf("huawei", "honor").any { it in m } -> "Configurações › Bateria › Inicialização de apps: gerencie o TapForge manualmente e ative as três opções."
            listOf("oppo", "realme", "oneplus").any { it in m } -> "Configurações › Bateria › Otimização de bateria: TapForge › Não otimizar. Permita também execução em segundo plano."
            "vivo" in m -> "Configurações › Bateria › Consumo em segundo plano: permita alto consumo para o TapForge."
            else -> "Configurações › Apps › TapForge › Bateria › Sem restrições (ou Não otimizado)."
        }
        MaterialAlertDialogBuilder(this).setTitle("Guia de bateria (${Build.MANUFACTURER})")
            .setMessage("$steps\n\nSe o painel sumir sozinho, é o sistema fechando o serviço: refaça estes passos e reative a acessibilidade.")
            .setPositiveButton("Otimização de bateria") { _, _ -> runCatching { startActivity(Intent(SysSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
            .setNeutralButton("Detalhes do app") { _, _ -> startActivity(Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            .setNegativeButton("Fechar", null).show()
    }

    // ---------- Arquivos ----------
    private fun write(uri: Uri, text: String, ok: String) {
        runCatching { contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
            .onSuccess { snack(ok) }.onFailure { snack("Não foi possível salvar: ${it.message}") }
    }

    private fun importFrom(uri: Uri) {
        runCatching { MacroStore.import(this, contentResolver.openInputStream(uri)!!.bufferedReader().readText()) }
            .onSuccess { snack("$it macro(s) importado(s)"); tab = R.id.nav_macros; nav.selectedItemId = R.id.nav_macros; render() }
            .onFailure { info("Arquivo inválido", "Não é um macro ou backup do TapForge.\n\n${it.message}") }
    }
}
