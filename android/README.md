# Amor, Carrega ❤️ — V8

V7 baseada diretamente no projeto V6 enviado pelo usuário.

## Novidade V8 — desligar o alerta com a tela apagada
- A notificação de alerta agora tem o botão **🔕 Desligar alerta**, que para som e vibração na hora, mesmo com o celular bloqueado (sem precisar desbloquear).
- Tocar na notificação abre a tela do alerta, que também tem o botão de desligar.
- Se o alerta de 1% chegar com o de 5% aberto, a mesma tela é reaproveitada (não empilha duas).

## Testar alertas (novo)
Na tela inicial, o cartão **🧪 Testar alertas** dispara o alerta de verdade (som em loop, vibração, notificação e tela cheia) nos quatro níveis: lembrete (o que estiver configurado), 15%, 5% e 1%. Funciona mesmo com os alertas desligados no interruptor.

Para testar com a **tela apagada**: ligue **Esperar 10 segundos**, toque no nível (5% ou 1%) e bloqueie o celular. Em 10 s o alerta chega; confira se a tela acende e se o botão **🔕 Desligar alerta** aparece na tela e na notificação. **⏹ Parar teste** encerra tudo (inclusive um teste ainda agendado).

Durante o teste com o celular no carregador, a tela do alerta não fecha sozinha (nos alertas reais, ela fecha ao conectar o carregador). A tela mostra o selo "🧪 TESTE".

## Correções
- Corrigida a detecção do alerta de emergência em 1%. Agora 5% → 1% dispara corretamente.
- Evitada repetição do alerta de 1% enquanto a bateria continua em 1%.
- Ao conectar o carregador, o som, vibração e notificações de alerta são encerrados.
- Silenciar os alertas no app também cancela imediatamente os alertas ativos, sem desligar o monitoramento.

## Áudio e modo silencioso
O app usa `AudioAttributes.USAGE_ALARM` para reproduzir os alertas como áudio de alarme, em vez de áudio de mídia. Isso permite que o comportamento seja independente do volume de mídia em aparelhos compatíveis.

O Android, porém, pode bloquear ou alterar o comportamento de áudio em modo silencioso/Não Perturbe conforme as configurações do aparelho e do fabricante. Nenhum app comum deve tentar contornar essas políticas por exploração de brechas.

## Tela crítica
Nos níveis críticos o app usa `USE_FULL_SCREEN_INTENT`, `showWhenLocked` e `turnScreenOn`. A disponibilidade do full-screen intent depende das políticas e configurações da versão do Android.

## Instalação
Abra esta pasta no Android Studio e execute o módulo `app`. O `applicationId` permanece `br.com.pedrinho.amorcarrega`, portanto a instalação substitui a V6 pelo mesmo aplicativo em vez de criar outro app.

## Não instalava em outros aparelhos (G56, A12 etc.) — corrigido
O `minSdk 26` (Android 8.0) já cobre esses aparelhos, então não era esse o
problema. A causa era a build "release": o projeto não tinha um
`signingConfig`, então quando o Android Studio gera um `.apk` fora do botão
▶ (Run), esse arquivo sai **sem assinatura** — e um APK sem assinatura não
instala em nenhum aparelho, nem no seu. Agora `buildTypes { release { ... } }`
usa a mesma chave de debug, então qualquer build sai assinado.

Para gerar o arquivo certo para compartilhar:
1. `Build` → `Build App Bundle(s) / APK(s)` → `Build APK(s)`.
2. Pegue o arquivo em `app/build/outputs/apk/release/app-release.apk` (não
   o `.aab` — esse formato é só para a Play Store e não instala direto no
   aparelho).
3. Mande esse `.apk` (por WhatsApp, Drive etc.).

No aparelho de quem for instalar, se aparecer "app não instalado" mesmo
assim, normalmente é bloqueio do próprio celular, não do app:
- **"Instalar apps desconhecidos"**: no Android, isso é liberado por app
  instalador (WhatsApp, Arquivos, Chrome), não de uma vez só. É preciso
  permitir para o app usado para abrir o `.apk`.
- **Play Protect**: em alguns aparelhos (comum em Motorola e Samsung), o
  Play Protect verifica o APK antes de instalar e pode barrar apps
  desconhecidos que pedem permissões "sensíveis" — aqui, notificação,
  serviço em primeiro plano e iniciar com o aparelho. Se aparecer um aviso
  do Play Protect, é só escolher "instalar mesmo assim".
- **Espaço em disco**: raro, mas vale checar em aparelhos de entrada.
