# Amor, Cuida 2.0 — implementação

Base: `Amor_Cuida_Android_Studio_Sync_Corrigido.zip`, que mantém os fontes V8 e corrige o wrapper para Gradle 8.7. Referência: roteiro oficial fornecido em PDF. O pedido posterior do usuário acrescentou o calendário **interno e offline**; não há integração com calendário Motorola/Google.

| Requisito | Implementação |
|---|---|
| Identidade Amor, Cuida e interface modernizada | Nova tela inicial, cartões, navegação inferior, ícones, paleta vinho/rosa e suporte a tema escuro |
| Preservar bateria | Tela anterior em BatteryActivity, mesmos níveis e preferências; serviço, testes e alertas mantidos |
| Múltiplos medicamentos | Cadastro/edição de nome, apresentação, concentração, quantidade, horários, recorrência, início/término e observações |
| Alteração futura conforme prescrição | Esquemas versionados, data de início explícita, preservação do período anterior |
| Alarmes pelo Android | AlarmManager, receivers privados, horário/fuso do aparelho, aviso breve e configurações independentes |
| Reprogramação | Boot, atualização, hora/fuso/data, cadastro, confirmação e retorno de permissões |
| Confirmação manual | Quantidade real e data/hora reais, sem confirmação por notificação ou passagem do tempo |
| Estados | Pendente, confirmado e sem confirmação, sem inferir esquecimento |
| Histórico com correções | Room, snapshot da prescrição, auditoria de antes/depois/motivo e controle de escrita concorrente |
| Calendário local | Marcadores na data real da administração, consulta de registros e lembretes por dia |
| Offline | Sem conta, Internet, API externa ou integração com calendário externo |
| Segurança da prescrição | Sem cálculo de dose, compensação ou alteração automática de tratamento; nenhum medicamento pré-cadastrado |

O schema inicial do módulo é exportado e não usa migração destrutiva. O pacote continua `br.com.pedrinho.amorcarrega` e a versão foi incrementada para 2.0.0/código 2. A assinatura original de uma instalação no PC/Motorola não faz parte do ZIP; a compatibilidade dessa assinatura não pode ser presumida para o APK gerado na nuvem.

Resultados executados e pendências reais estão em `VALIDACAO.md`. A conclusão no Motorola, incluindo tela bloqueada, som, vibração, restrições de energia e atualização com a assinatura original, depende de testes no aparelho físico; não foi substituída por uma afirmação de sucesso no emulador.
