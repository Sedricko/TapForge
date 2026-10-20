# Roadmap
- **Fase 1 (feito):** modelo de macro, motor, gestos, overlay de acessibilidade, captura de tela, template matching, OCR, persistência JSON.
- **Fase 2 (feito):** interface Material 3, editor com catálogo descrito, marcar na tela, parar por volume/chacoalhar, notificação de status, multitoque.
- **Fase 3 (feito):** gravação de toques, agendamento, gatilhos (app, carregador), contadores e estatísticas, backup/restauração, import/export, histórico de versões.
- **Fase 4 (parcial):** guia de bateria por fabricante (feito), acessibilidade do próprio app (descrições nos botões; feito).

## Pendente
- Testes instrumentados (precisam de emulador no CI).
- Gatilho por notificação recebida (exige NotificationListenerService e outra permissão).
- Exportar como QR code (exige biblioteca extra).
- Tradução para outros idiomas (textos estão em português no código).
- APK de release assinado no CI (exige guardar a chave como segredo do repositório).
- Captura de tela girada: a captura usa a orientação de quando foi ativada.

## Não verificado ainda
- Comportamento em aparelho real: o CI compila e roda os testes do motor; painel, captura, OCR e gravação não têm teste automático.
- MediaProjection varia por versão/fabricante (Android 14+ exige consentimento a cada sessão).
