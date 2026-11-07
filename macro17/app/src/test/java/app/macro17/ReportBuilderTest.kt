package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class ReportBuilderTest {
    private fun ev(time: String, tag: String, msg: String, th: String = "macro17-engine") = "11-06 $time [$th] $tag $msg"

    /** Macro com 3 passos: o 2 repete 4 vezes (salto para si mesmo, buscas lentas) e a execução termina concluída. */
    private val log = listOf(
        ev("23:10:00.000", "tela", "MainActivity em primeiro plano", "main"),
        ev("23:10:01.000", "toque", "MainActivity (10,20) → ImageView[Menu]", "main"),
        ev("23:10:02.000", "janela", "com.outro.app/android.widget.FrameLayout", "main"),
        ev("23:16:59.927", "run", "início \"teste\" passos=3 repetições=1"),
        ev("23:16:59.929", "passo", "passo 1/3 TAP atraso=2000ms"),
        ev("23:17:00.131", "passo", "passo 1 imagem do item: achou em 426,2214 semelhança 99%"),
        ev("23:17:02.137", "gesto", "426,2214>426,2214 @5ms"),
        ev("23:17:02.165", "passo", "passo 2/3 IMAGE atraso=1000ms"),
        ev("23:17:02.500", "visão", "casamento lento: 297 ms (OpenCV) área 1080x2340 modelo 74x92"),
        ev("23:17:02.900", "passo", "passo 2 imagem: achou em 59,492 semelhança 94%"),
        ev("23:17:03.000", "aviso", "Passo 2 → Passo 2"),
        ev("23:17:04.000", "passo", "passo 2/3 IMAGE atraso=1000ms"),
        ev("23:17:04.400", "visão", "casamento lento: 280 ms (OpenCV) área 1080x2340 modelo 74x92"),
        ev("23:17:04.900", "passo", "passo 2 imagem: não achou"),
        ev("23:17:05.000", "aviso", "Passo 2 → Passo 2"),
        ev("23:17:06.000", "passo", "passo 2/3 IMAGE atraso=1000ms"),
        ev("23:17:07.000", "passo", "passo 2 imagem: achou em 59,492 semelhança 95%"),
        ev("23:17:08.000", "passo", "passo 2/3 IMAGE atraso=1000ms"),
        ev("23:17:09.000", "passo", "passo 2 imagem: achou em 59,492 semelhança 95%"),
        ev("23:17:10.000", "passo", "passo 3/3 TAP atraso=10000ms"),
        ev("23:17:20.000", "run", "fim: Macro concluída (237135 ms)"),
        ev("23:17:21.000", "painel", "botão: Fechar o painel", "main"),
    ).joinToString("\n")

    @Test fun parsesEventsAndJoinsContinuationLines() {
        val evs = ReportBuilder.parseEvents(ev("23:00:00.000", "run", "início \"a\" passos=1 repetições=1") + "\n    segunda linha\n" + ev("23:00:01.000", "início automático", "ligado"))
        assertEquals(2, evs.size)
        assertEquals("run", evs[0].tag)
        assertTrue(evs[0].msg.endsWith("\nsegunda linha"))
        assertEquals("início automático", evs[1].tag)
        assertEquals("ligado", evs[1].msg)
    }

    @Test fun repeatedConsecutiveStepIsCollapsedWithTimeRange() {
        val runs = ReportBuilder.analyze(log).runs
        assertTrue(runs, runs.contains("passo 2/3 IMAGE repetiu 4 vezes, de 23:17:02 a 23:17:08"))
        assertEquals(1, Regex("passo 2/3").findAll(runs).count())   // uma linha só para as 4 passadas
        assertTrue(runs.contains("passo 1/3 TAP"))
        assertTrue(runs.contains("passo 3/3 TAP"))
    }

    @Test fun summaryHasResultTimeAndCounts() {
        val s = ReportBuilder.analyze(log).summary
        assertTrue(s, s.contains("1 concluída(s)"))
        assertTrue(s, s.contains("“teste” · concluída · 3 min 57 s"))
        assertTrue(s, s.contains(" 0 aviso(s)"))        // os 2 saltos não são avisos
        assertTrue(s, s.contains("2 salto(s)"))
        assertTrue(s, s.contains("1 busca(s) de imagem sem sucesso"))
        assertTrue(s, s.contains("passo mais repetido: passo 2 (4 vezes)"))
        assertTrue(s, s.contains("6 execuções de passo"))
    }

    @Test fun errorRunIsReportedAsError() {
        val t = ev("10:00:00.000", "run", "início \"x\" passos=1 repetições=1") + "\n" + ev("10:00:01.000", "run", "fim: Erro: macro vazia (1000 ms)")
        val p = ReportBuilder.analyze(t)
        assertTrue(p.summary, p.summary.contains("1 com erro"))
        assertTrue(p.summary, p.summary.contains("Erro: macro vazia"))
    }

    @Test fun runWithoutEndIsMarked() {
        val p = ReportBuilder.analyze(ev("10:00:00.000", "run", "início \"x\" passos=1 repetições=1"))
        assertTrue(p.summary, p.summary.contains("1 sem fim registrado"))
    }

    @Test fun messagesAreTranslated() {
        assertEquals("Passo 8 salta para si mesmo (repete até a condição mudar)", ReportBuilder.translate("aviso", "Passo 8 → Passo 8"))
        assertEquals("Passo 3 salta para o Passo 7", ReportBuilder.translate("aviso", "Passo 3 → Passo 7"))
        assertEquals("busca de imagem levou 336 ms em área 1080x2340 (modelo 80x80)", ReportBuilder.translate("visão", "casamento lento: 336 ms (OpenCV) área 1080x2340 modelo 80x80"))
        assertEquals("toque em 59,492 (50 ms)", ReportBuilder.translate("gesto", "59,492>59,492 @50ms"))
        val runs = ReportBuilder.analyze(log).runs
        assertTrue(runs, runs.contains("salta para si mesmo"))
        assertTrue(runs, runs.contains("NÃO encontrou"))
        assertFalse(runs, runs.contains("casamento lento"))
    }

    @Test fun slowSearchHintAppearsOnlyOnce() {
        val runs = ReportBuilder.analyze(log).runs
        assertEquals(1, Regex("Posição Fixa").findAll(runs).count())
        assertTrue(runs.contains("2 buscas de imagem lentas: de 280 a 297 ms"))
    }

    @Test fun usageIsSeparatedFromRuns() {
        val p = ReportBuilder.analyze(log)
        assertFalse(p.runs.contains("MainActivity"))
        assertTrue(p.usage, p.usage.contains("1 toque(s)"))
        assertTrue(p.usage, p.usage.contains("MainActivity"))
        assertTrue(p.usage, p.usage.contains("com.outro.app"))
        assertTrue(p.usage, p.usage.contains("Fechar o painel"))
    }

    @Test fun noiseFilterDropsSystemTagsButKeepsRealWarningsAndMacro17() {
        val raw = listOf(
            "11-06 23:19:45.873 19770 19770 D StrictMode: StrictMode policy violation",
            "11-06 23:19:45.873 19770 19770 I VRI[]@e030f76: setView = android.view.View@2916238",
            "11-06 23:19:45.877 19770 19794 E perf_hint: Session ID too large",
            "11-06 23:19:45.879 19770 19770 D InputTransport: Input channel constructed",
            "11-06 23:19:45.880 19770 19789 D HWUI    : treat SMPTE_170M as sRGB",
            "11-06 23:19:45.881 19770 19770 I InsetsController: onStateChanged",
            "11-06 23:19:45.882 19770 19770 W BitmapFactory: Unable to decode stream",
            "11-06 23:19:45.883 19770 19770 E AndroidRuntime: FATAL EXCEPTION: main",
            "11-06 23:19:45.884 19770 19770 I Toast   : show: caller = app.macro17.MacroService.toast:262",
            "--------- beginning of main",
        ).joinToString("\n")
        val out = ReportBuilder.filterNoise(raw)
        for (gone in listOf("StrictMode", "VRI[", "perf_hint", "InputTransport", "HWUI", "InsetsController")) assertFalse(gone, out.contains(gone))
        for (kept in listOf("BitmapFactory", "FATAL EXCEPTION", "app.macro17.MacroService", "beginning of main")) assertTrue(kept, out.contains(kept))
        assertEquals("", ReportBuilder.filterNoise(""))
    }

    @Test fun zipHoldsReportRawLogMacrosAndInfo() {
        val f = File.createTempFile("diag", ".zip").also { it.deleteOnExit() }
        ReportBuilder.writeZip(f, "REL", "RAW", listOf("a.json" to "{\"v\":2}"), "INFO")
        ZipFile(f).use { z ->
            assertEquals(listOf("info.txt", "macros/a.json", "registro-completo.txt", "relatorio.txt"), z.entries().toList().map { it.name }.sorted())
            assertEquals("RAW", z.getInputStream(z.getEntry("registro-completo.txt")).readBytes().toString(Charsets.UTF_8))
        }
        Files.deleteIfExists(f.toPath())
    }
}
