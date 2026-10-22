# Correção localizada do áudio de medicamentos

## Investigação

Em `BatteryMonitorService.kt`, `AlertEngine.fire()` chama `playLoopingAlarm()`. O alerta de bateria comum (incluindo 20%) usa `MediaPlayer`, `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)` e `AudioAttributes` com `USAGE_ALARM` / `CONTENT_TYPE_SONIFICATION`. O player tem `isLooping = true`, preparo assíncrono e parada explícita por `stopImmediately()`. Os níveis críticos têm recursos de áudio próprios. Esse código não foi alterado.

Em `care/CareAlarms.kt`, o medicamento já era entregue por AlarmManager e NotificationManager, mas o canal `care_short_s*_v*` usava `TYPE_NOTIFICATION` / `USAGE_NOTIFICATION_EVENT`. O som padrão de notificações pode estar silencioso ou ter volume/configuração diferentes do alarme. Além disso, modificar `setSound()` no código não corrige o áudio de um canal já criado no aparelho: essa propriedade é persistida pelo Android.

## Alteração

Somente a implementação de notificações dos medicamentos foi alterada:

- Novo canal versionado `care_alarm_v2_s*_v*`, usando `TYPE_ALARM`, `USAGE_ALARM` e `CONTENT_TYPE_SONIFICATION`, como o alerta comum de bateria.
- O NotificationManager é a única fonte de reprodução. Não foi adicionado MediaPlayer, serviço de áudio ou uma segunda chamada de reprodução. Reutilizar o motor persistente da bateria interromperia/compartilharia seu estado e produziria um comportamento inadequado para medicamentos.
- O aviso não usa `FLAG_INSISTENT`; toca uma vez e termina automaticamente, sem repetição contínua. A duração de uma reprodução depende do toque de alarme escolhido no sistema; não há temporizador de duração fixa. `ONLY_ALERT_ONCE` evita novo áudio quando a mesma notificação é atualizada.
- O áudio é solicitado pelo sistema junto com a notificação, sem depender de Activity visível ou tela desbloqueada. O fluxo de AlarmManager e os receivers permanecem os mesmos.
- O novo canal conserva a importância (inclusive bloqueio/baixa prioridade), grupo, indicação de badge e configuração de vibração do perfil anterior. No Android 11+, `hasUserSetSound()` preserva o som ou silêncio escolhido explicitamente pelo usuário. No Android 8–10, sem esse indicador público, sons personalizados e silêncio são conservados de forma preventiva; o URI padrão de notificações é trocado pelo URI padrão de alarme.
- A preferência interna de som desligado continua criando um perfil silencioso. Um novo canal que já exista nunca tem seus ajustes redefinidos. Os canais antigos não são apagados.

Não foram alterados layout, formulários, banco, histórico, horários, manifesto, permissões ou alertas de bateria.

## Validação

`CareReminderAudioTest` exercita criação/migração dos canais e entrega real pela implementação do módulo em Robolectric nas APIs 28 e 35: URI e atributos de alarme, preferências de silêncio/som personalizado/importância, canal já configurado, ausência de loop, atualização do mesmo aviso e ausência de administração automática. O teste verifica a configuração enviada ao Android, não captura áudio do alto-falante.

O teste instrumentado `CareNativeReminderTest` também verifica canal e atributos do aviso efetivamente entregue, agora com a tela apagada. Continua restrito a emulador dedicado e não foi executado nesta correção. Sua compilação não comprova som audível em aparelho físico.

Comando de build/regressão na raiz:

```bash
python3 scripts/android-cloud.py gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Resultado executado: **51 testes JVM aprovados** (19 novos casos de áudio e 32 de regressão), nenhum ignorado; build do app e do APK instrumentado aprovado; lint com zero erros e 37 avisos preexistentes. A assinatura do APK debug foi verificada.

## Conferência no Motorola

1. Atualize com a mesma assinatura, mantendo os dados. Ative o som de medicamentos no app e conceda notificações; confira o canal ativo em Ajustes.
2. Selecione um som padrão de alarme audível no Android, confira o volume de alarmes e cadastre um horário próximo. Feche a interface e bloqueie a tela.
3. Confirme que o aviso aparece, reproduz o som uma única vez e para sozinho. O registro deve continuar sem confirmação até o preenchimento manual.
4. Repita com som desligado no app e com o canal silenciado/bloqueado pelo Android: não deve haver áudio. Confira também o modo Não Perturbe conforme a política do aparelho.
5. Confira o alerta de bateria de 20%: deve continuar persistente, com sua parada original, sem interferência dos medicamentos.

Não são forçados volume, saída de áudio ou acesso ao Não Perturbe. Permissão negada, bloqueio do canal, volume de alarme zerado, toque padrão silencioso e políticas do Android/fabricante podem impedir áudio. Alarmes aproximados podem atrasar conforme as condições já documentadas na versão 2.0.
