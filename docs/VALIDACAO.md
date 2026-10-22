# Validação da base importada

## Integridade

Os 30 arquivos do ZIP foram comparados byte a byte com `android/` após a importação e compilação: nenhuma diferença. O README original da raiz também foi preservado.

## Build e análise

- `:app:assembleDebug`: aprovado; APK gerado para pacote `br.com.pedrinho.amorcarrega`, versão 1.0 (1), Android mínimo 26 e alvo 35.
- Reexecução de `scripts/install-android-cloud.sh`: aprovada, build incremental com 33 tarefas atualizadas, sem reinstalações desnecessárias.
- `apksigner verify`: aprovado, assinatura de desenvolvimento Android Debug. Não prova compatibilidade com a assinatura do app instalado.
- `:app:testDebugUnitTest`: `NO-SOURCE`; nenhum teste executado.
- `:app:lintDebug`: reprovado com 1 erro e 31 avisos. Erro `MissingSuperCall` em `AlertActivity.kt:204`, no override de `onBackPressed`. O código recebido deixa esse método vazio intencionalmente para manter o alerta aberto. A escolha precisa ser revista em uma tarefa de código, preservando o comportamento desejado. Não foi suprimido o lint nem alterado o código durante a preparação.

Relatório completo gerado em `android/app/build/reports/lint-results-debug.html` e versão texto em `android/app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`.

O módulo de medicamentos não foi implementado nesta preparação; consulte `EVOLUCAO-2.0.md`.

## Verificação funcional no Android 15

A imagem completa Google APIs apresentou reinício pelo watchdog e consultas com timeout sem KVM. Foi substituída por AOSP ATD, com display 540×960 e 2 GB de RAM. A primeira inicialização da imagem mínima levou cerca de 7 minutos.

Verificações concluídas na imagem mínima:

- Instalação do APK via ADB: `Success`.
- `MainActivity` confirmada como `topResumedActivity`; permissão `POST_NOTIFICATIONS` concedida.
- Hierarquia de interface: título “Amor, Carrega”, controle “Primeiro alerta”, valor 30% e explicações dos níveis 15%, 5% e 1% presentes.
- Serviço `BatteryMonitorService`: `isForeground=true`, notificação contínua `id=10`.
- Bateria simulada sem carregador e em 15%: notificação do aplicativo `id=9015`, canal `battery_alerts`, importância 4 e categoria `alarm`.
- Após `cmd battery reset`, que restaurou o carregamento original: notificação `id=9015` cancelada e notificação de monitoramento `id=10` mantida. O estado de bateria simulado foi restaurado.

A imagem ATD desativa o desenho da interface por padrão; não foi obtida captura visual utilizável. Esses resultados validam instalação, estrutura da tela, serviço e fluxo representativo de notificação/cancelamento. Não validam aparência, som audível, vibração, telas de emergência, todos os níveis, tela bloqueada, reinício no Motorola nem atualização sobre o APK instalado. Essas verificações permanecem necessárias no aparelho real. Nenhuma funcionalidade de medicamentos foi testada ou implementada.
