# Amor, Cuida 2.0 — roteiro de implementação

Referência do produto: `Amor_Cuida_2_0_Roteiro_Oficial.pdf`, fornecido pelo usuário. O documento é uma especificação de evolução, não evidência de funcionalidades presentes na V8.

## Base importada

O ZIP tem pasta interna `v7`, README com título V8 e versão Android `1.0`/código `1`. A base inclui telas e monitoramento de bateria, níveis de alerta, testes manuais, som, vibração, serviço em primeiro plano e recepção de reinício. Não foi comparada com o projeto do PC nem com o APK instalado.

As preferências existentes usam SharedPreferences `config`. O pacote permanece `br.com.pedrinho.amorcarrega`. Preservar essas identidades e dados ao evoluir.

## Próximas etapas, ainda não implementadas

1. Conferir se esta base corresponde à versão funcional mais recente e registrar os testes de regressão no Motorola.
2. Aplicar nome Amor, Cuida e navegação Início, Bateria, Medicamentos, Histórico e Configurações. Preservar o módulo de bateria.
3. Adicionar cadastro/edição de medicamentos conforme prescrição: nome, apresentação, concentração, quantidade, horários, início e término opcionais. Nenhuma dose de exemplo deve virar uma prescrição automática.
4. Criar persistência local (preferencialmente Room) e migrações. Separar cadastro, ocorrência agendada e administração confirmada.
5. Agendar avisos locais com AlarmManager e receivers; respeitar fuso/data do aparelho e reagendar após reinício, mudança de hora/fuso, edição e alterações de permissões aplicáveis.
6. Usar notificações breves de medicamento, com vibração opcional; não reutilizar o som persistente de emergência de bateria.
7. Registrar administração somente mediante confirmação manual de quantidade e data/hora reais. Diferenciar pendente, confirmado e sem confirmação. Permitir correção com trilha da alteração.
8. Tratar permissões de notificação, alarmes exatos e restrições Motorola. Validar sem internet, com tela bloqueada, processo encerrado e após reinício.
9. Reexecutar os testes de bateria e validar a atualização assinada com a chave original no Motorola antes da distribuição.

O app não deve calcular doses, recomendar compensações ou inferir administração por um aviso disparado. Não incluir calendário, contas ou nuvem nesta versão. Não prometer alarmes infalíveis sob restrições do Android.

## Critério de entrega

A preparação do repositório e do ambiente não constitui entrega da versão 2.0. A conclusão do roteiro depende da implementação acima e de validação no Motorola.
