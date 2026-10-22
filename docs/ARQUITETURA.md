# Arquitetura da versão 2.0

## Separação dos módulos

`MainActivity` reúne Início, Remédios, Calendário, Histórico e Ajustes. `MedicineEditorActivity` edita cadastro e vigência de esquemas. `BatteryActivity` preserva a tela de bateria anterior; `BatteryMonitorService`, `AlertEngine`, `AlertTester`, `HeartView` e `BatteryRingView` continuam responsáveis por seus comportamentos. A lógica dos níveis não mudou. `AlertActivity` usa OnBackPressedDispatcher para manter a mesma exigência de desligamento explícito, sem o erro de lint da implementação antiga.

## Persistência e correções

`CareDatabase` é um banco Room independente, `amor_cuida.db`, versão de schema 1. Não havia banco de medicamentos na base V8. As preferências `config` da bateria não são migradas nem substituídas. O schema exportado está em `android/app/schemas/`. Não existe `fallbackToDestructiveMigration`; mudanças futuras de schema devem ter Migration explícita e testes correspondentes.

- `medicines`: identidade, apresentação, concentração, observações e instante de arquivamento.
- `plans`: quantidade prescrita, horários, recorrência e vigência. Uma alteração fecha o esquema anterior na véspera da nova vigência; esquemas futuros substituídos ficam preservados como superseded. Não há cálculo automático de mudança de dose.
- `administrations`: uma confirmação por chave medicamento/esquema/data/hora planejada, com snapshot da prescrição e identidade do medicamento. Guarda quantidade real, data/hora locais e fuso informados, instante e timestamps de criação/alteração.
- `corrections`: motivo, valores anteriores/novos e instante da alteração. A correção e a atualização do registro ocorrem na mesma transação; uma versão antiga não pode sobrescrever uma correção mais recente.

A confirmação exige ação explícita e dados reais. Datas futuras são rejeitadas. Não é criada administração ao notificar, abrir a tela, silenciar um aviso ou passar do horário. Quantidades são texto informado pelo usuário, sem cálculo médico.

## Calendário e estados

Os marcadores de administração agrupam `actualDate`, a data real que a pessoa confirmou. Não usam a data do lembrete. Corrigir essa data move o marcador e preserva a data anterior no histórico de correções. O fuso original fica registrado; viajar não muda retroativamente as datas locais que a pessoa informou.

Ocorrências planejadas são geradas a partir dos esquemas, sem preencher um banco com anos de eventos. O estado é confirmado quando há um registro; antes do horário é pendente; depois, sem confirmação. Sem confirmação não é evidência de uma dose esquecida. O histórico mostra os últimos 100 registros e até 20 pendências recentes; o calendário permite consultar outras datas sem apagar os registros anteriores.

## Alarmes nativos

`Schedules` é lógica Kotlin testável com java.time. Seleciona os horários a partir de data/fuso do aparelho, sem polling. O intervalo em dias é ancorado no início do esquema; dias da semana usam segunda a domingo; a data final é inclusiva. Em um horário inexistente por mudança para horário de verão, o agendamento segue a conversão de java.time para o próximo horário válido. Em uma hora repetida, usa a primeira ocorrência, uma vez por horário local. A confirmação manual rejeita um horário local inexistente.

`CareAlarms` agenda o próximo aviso de cada medicamento com AlarmManager. Usa `setExactAndAllowWhileIdle` quando permitido; caso contrário, usa `setAndAllowWhileIdle` e informa na interface que os avisos podem atrasar. Não existe serviço em loop para medicamentos. O evento de minuto do Android atualiza apenas a tela enquanto aberta; não dispara lembretes.

`CareAlarmReceiver` recebe um PendingIntent explícito, valida a versão atual do esquema e publica a notificação. Depois agenda o próximo aviso, inclusive quando uma falha de permissão impede a notificação. Na versão 2.0.2, o serviço privado CareMedicationAlarmService repete o player direto (até cinco segundos de áudio, três de pausa) até a gravação da administração. A notificação usa fullScreenIntent para MedicationAlarmActivity, conforme os acessos do Android. Abrir o formulário não confirma uma dose. As pendências persistidas são revalidadas no Room e o último registro encerra serviço/player/wakelock. Som/vibração de bateria são independentes.

`CareRescheduleReceiver` restaura os avisos após boot, atualização do pacote, mudança de data/hora/fuso e concessão de alarmes exatos. Voltar à tela também reavalia permissões e agendamentos. As notificações antigas de esquemas alterados/arquivados ou confirmados são canceladas. Horários passados não são disparados em lote depois de um reinício; ficam consultáveis como sem confirmação.

Fechar a interface é diferente de **Forçar parada** nas configurações do Android: a parada forçada bloqueia alarmes até a pessoa abrir o aplicativo novamente. Permissões, Doze, canais silenciados, Não Perturbe e políticas Motorola podem limitar a entrega. Não há promessa de disparo infalível.

## Operação offline

O APK não declara permissão de Internet e não integra provedores de calendário, contas, analytics ou serviços remotos. Room, AlarmManager, histórico e calendário funcionam localmente. O backup automático dos dados em nuvem foi desabilitado. A proteção pública da notificação evita expor o nome do medicamento na tela bloqueada por padrão; o Android e as escolhas da pessoa controlam a visibilidade final.
