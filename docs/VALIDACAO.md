# Validação da versão 2.0

## Resultado desta entrega

Build e verificações executados em 22/10/2026, com Gradle 8.7 e Java 21:

```bash
python3 scripts/android-cloud.py gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

- Build do app e do APK de testes instrumentados: aprovados.
- **51 testes JVM executados, zero falhas e zero testes ignorados.**
- Lint: zero erros, 37 avisos. O relatório detalhado é gerado em `android/app/build/reports/lint-results-debug.html`. Há avisos de compatibilidade/estilo e APIs da interface legada; o build não foi configurado para esconder falhas.
- APK: versão `2.0.0` (2), pacote `br.com.pedrinho.amorcarrega`, Android mínimo 26 e alvo 35. Assinatura de desenvolvimento verificada com `apksigner`.
- O manifesto do app não solicita acesso à Internet.

## Cobertura executada

| Grupo | Testes | Verificações |
|---|---:|---|
| Agendamentos | 16 | Recorrência diária, dias da semana, intervalos, múltiplos horários, versões futuras, término, arquivamento, fuso e horário de verão |
| Banco Room / SQLite em Robolectric | 13 | Confirmação manual, quantidade e data reais, duplicidade, persistência ao reabrir, correções auditadas, conflitos de edição, rejeição de datas futuras, versões de prescrição e preferências de bateria preservadas |
| Áudio dos medicamentos | 19 | APIs 28/35: canal de alarme, migração, silêncio e ajustes do usuário, entrega sem repetição e sem confirmação automática |
| Bateria | 2 | Níveis originais e limites do valor configurável |
| Telas reais em Robolectric / Skia | 1 | Renderização de cinco telas e fluxo pelos controles de cadastro, confirmação e correção, com verificação do banco e auditoria |

As imagens em `docs/imagens` são renderizações das Views reais com dados fictícios. Não são capturas de um aparelho. Os testes de interface usam a implementação de Room e as Activities do app; não demonstram entrega de alarmes pelo sistema operacional.

## Testes nativos preparados e limites

Há 15 testes instrumentados: 13 contratos de banco, um fluxo de interface Espresso e um teste de lembrete nativo offline com AlarmManager e notificação. O APK desses testes compilou. **Não há resultado aprovado de execução nativa da versão 2.0 nesta entrega.** O emulador Android 15 sem aceleração KVM apresentou reinícios do framework e serviços indisponíveis, impedindo uma execução confiável. A compilação e os testes JVM não substituem essa verificação.

Os testes instrumentados devem rodar em emulador dedicado. O teste de lembrete altera temporariamente Wi-Fi/dados e permissões, restaura a conectividade no encerramento e utiliza registros de teste próprios. Não o execute sobre dados pessoais. Para executar o conjunto padrão:

```bash
cd android
./gradlew :app:connectedDebugAndroidTest
```

Para exercitar a alternativa de alarme sem permissão exata, negue a permissão **antes** de iniciar o runner e execute apenas `CareUiFlowTest` com `-e alarm_mode inexact`. Revogar essa permissão durante uma instrumentação pode encerrar o processo no Android.

Permanecem pendentes no Motorola: entrega de lembretes com app fechado/tela bloqueada, otimização de bateria do fabricante, reinício e mudança de hora/fuso, permissão negada/reconcedida, som, vibração e atualização sobre a instalação real. O Android pode atrasar avisos quando a permissão de alarme exato está negada; a interface informa essa condição.

## Compatibilidade e dados

A lógica e as preferências de bateria foram preservadas. A validação nativa da base V8 anterior não equivale a um teste nativo aprovado desta nova versão. O calendário de medicamentos reflete somente confirmações manuais; ausência de confirmação não prova que uma administração deixou de ocorrer.

O APK é de desenvolvimento. Atualizar sem perder dados exige a mesma assinatura da instalação existente; o build da nuvem não comprova compatibilidade com a chave do seu Android Studio. Não desinstale uma versão com registros importantes para contornar uma incompatibilidade de assinatura.

## Correção do som dos medicamentos

A regressão acima foi reexecutada após a correção localizada de áudio. O canal agora usa o som padrão de alarme, com reprodução única pelo Android e migração que preserva escolhas do usuário. A execução instrumentada e a audição no Motorola permanecem pendentes. Veja [investigação, alterações e roteiro de conferência](CORRECAO-AUDIO-MEDICAMENTOS.md).
