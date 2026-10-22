# Alarme de medicamentos até confirmação — 2.0.2

Pedido adicional do usuário: manter o som que funcionou na 2.0.1, repetindo de forma intermitente até salvar a administração e apresentar uma tela de alarme com o aparelho bloqueado. Este pedido substitui a regra anterior de um único aviso breve.

## Funcionamento

- O mesmo MediaPlayer direto, TYPE_ALARM e atributos USAGE_ALARM da 2.0.1 reproduzem um trecho de até cinco segundos. Há três segundos de silêncio antes da próxima reprodução. O ciclo continua enquanto houver lembretes disparados ainda válidos sem registro de administração.
- Um novo serviço privado em primeiro plano, do tipo `mediaPlayback`, mantém o ciclo e uma notificação de alarme ativo. Ele usa a permissão `FOREGROUND_SERVICE_MEDIA_PLAYBACK` e um wakelock parcial renovado com limite de 60 segundos para manter a pausa/retomada com a tela apagada. Player, ciclo e wakelock são liberados ao finalizar o alarme. Perfis sem som não mantêm esse wakelock ativo.
- O som fica no serviço, não na tela: abrir, fechar ou cancelar o formulário não confirma nem encerra o alarme. A confirmação só vale depois da transação de gravação no Room. A interface envia a atualização ao serviço, que confere o banco antes de remover a chave pendente.
- Há um único player compartilhado. Com dois medicamentos pendentes, confirmar um mantém o alarme do outro. Não há confirmação automática ou cálculo de dose. Arquivamento/invalidação de um esquema também remove seu alarme, sem fabricar administração no histórico.
- As chaves dos alarmes ativos são persistidas em preferências separadas do banco e revalidadas em reinício do serviço. Um estado antigo nunca transforma uma dose já confirmada em novo alarme. O código e os controles da bateria não foram modificados.

## Tela com o celular bloqueado

A notificação individual tem categoria ALARM, prioridade máxima, `fullScreenIntent` e Activity privada com `showWhenLocked` e `turnScreenOn`. A tela mostra nome do medicamento, quantidade prescrita, data e horário do lembrete. O botão **Informar administração** abre o formulário existente; não grava ou para o áudio por si só. O Android pode exigir desbloqueio para acessar esse formulário.

Em **Ajustes** do app:

1. Permita notificações.
2. Permita **alarmes exatos**, necessários para o Android autorizar o início do serviço persistente a partir de um alarme com o app fechado.
3. No Android 14+, use **Permitir tela de alarme bloqueada** e habilite o acesso apresentado pelo sistema.
4. Mantenha o som de medicamentos ligado e confira o volume de alarmes.

A tela cheia é decidida pelo Android: com o aparelho desbloqueado pode aparecer apenas o banner. Sem acesso a tela cheia, há notificação comum. Sem alarmes exatos, os avisos podem atrasar e o Android pode negar o início do serviço em segundo plano; nesse caso permanece o toque breve da 2.0.1, e abrir a tela do alarme inicia o ciclo persistente a partir da interface visível. Não são contornadas permissões, canal bloqueado, volume zerado ou Não Perturbe.

Uma parada forçada, desligamento ou restrição do sistema pode interromper qualquer serviço. Após reiniciar o aparelho, os próximos horários são reprogramados e os alarmes ativos salvos são conferidos quando o app é reaberto ou outro lembrete inicia o serviço; não se inicia mediaPlayback diretamente no boot, que tem restrições no Android 15.

## Preservação dos dados

Versão 2.0.2, código 4, mesmo pacote. Sem alteração de schema Room, migração destrutiva, horários, formulários ou histórico. A atualização exige assinatura compatível. O ZIP completo inclui o APK debug e todos os arquivos do projeto para abrir a pasta `android` no Android Studio.

## Verificação

Resultado executado: **76 testes JVM aprovados**, sem falhas ou ignorados; build do app e do APK instrumentado aprovado; lint com zero erros e 39 avisos. Assinatura debug verificada.

Os testes JVM nas APIs 28 e 35 verificam o ciclo sonoro existente e o novo serviço: repetição após a pausa, parada após gravação real, múltiplas pendências, descarte de estado já confirmado em reinício, arquivamento sem confirmação, tela que abre formulário sem salvar e notificação com intenção de tela cheia sem cancelamento automático.

O teste nativo preparado consulta AudioManager com a tela apagada: exige um primeiro trecho, um intervalo, uma segunda reprodução antes de confirmar e ausência de retomada depois de salvar. A execução instrumentada e a aparência/audição no Motorola continuam pendentes nesta entrega; a suíte JVM não comprova permissões e comportamento do fabricante no aparelho real.

Para conferir no Motorola, programe uma dose próxima, feche o app e apague a tela. Confira a tela de alarme e ao menos dois ciclos. Abra e cancele o formulário: o alarme deve continuar. Salve quantidade/data/hora reais: o alarme deve parar e aparecer no histórico/calendário. Repita com dois medicamentos no mesmo horário e confirme um por vez. Confira também som desligado, acesso a tela cheia negado e alerta de bateria.
