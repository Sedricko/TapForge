# Roadmap
- **Fase 1 (este esqueleto):** modelo de macro, motor, gestos, overlay de acessibilidade, captura de tela, template matching, OCR, persistência JSON.
- **Fase 2:** editor visual (alvos arrastáveis, regiões, captura de template), parar por volume, notificação de status, multi-alvo simultâneo (gestos paralelos).
- **Fase 3:** gravação de toques, agendamento (AlarmManager), contadores, backup/restauração, import/export.
- **Fase 4:** troubleshooting guiado por fabricante (economia de bateria), testes instrumentados, acessibilidade do próprio app.

## Não verificado ainda
- O projeto **não foi compilado** aqui (sem Android SDK/Gradle no ambiente). Abra no Android Studio e rode `./gradlew test` para a lógica pura.
- Comportamento de MediaProjection varia por versão/fabricante (Android 14+ exige consentimento a cada sessão).
