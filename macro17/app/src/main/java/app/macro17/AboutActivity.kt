package app.macro17

import android.app.Activity
import android.os.Bundle

/** Sobre o Aplicativo: versão e licenças do que veio de terceiros (também em THIRD_PARTY.md no repositório). */
class AboutActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = Prefs(this)
        val pi = packageManager.getPackageInfo(packageName, 0)
        p.category("Macro17", Ic.INFO)
        p.valueRow("Versão", "${pi.versionName} (${pi.longVersionCode})")
        p.valueRow("O que é", "Automação de toques por acessibilidade: gravar, editar e repetir macros, com busca por imagem, texto e número na tela.")
        p.category("Licenças de terceiros", Ic.BOX)
        p.valueRow("OpenCV 4.9.0", "Apache-2.0. Casamento de imagem (matchTemplate).")
        p.valueRow("ML Kit Text Recognition 16.0.1", "Termos do Google ML Kit (proprietário). Reconhecimento de texto no aparelho.")
        p.valueRow("Kotlin stdlib 2.1.0", "Apache-2.0. Runtime da linguagem.")
        p.note("Sem internet: o app não tem a permissão INTERNET. Nada sai do aparelho a menos que você compartilhe um relatório.")
        setContentView(p.page("Sobre o Aplicativo", Ic.BACK, { finish() }))
    }
}
