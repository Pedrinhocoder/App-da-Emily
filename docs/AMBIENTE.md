# Desenvolvimento Android na nuvem

A base recebida em `AmorCarrega-V8.zip` foi importada para `android/`, sem alterações no código, recursos, manifesto ou dependências. O README original do repositório permanece na raiz; a documentação do ZIP está em `android/README.md`.

## Ferramentas e comandos

- Java 21 disponível na máquina; o aplicativo mantém seu alvo de bytecode Java/Kotlin 17.
- Android SDK 35, Build Tools 34.0.0 e Platform Tools em `/workspace/toolchains/android-sdk`.
- Gradle 8.7 em `/workspace/toolchains/gradle-8.7`, usado pelo helper. O wrapper recebido aponta para Gradle 9.3.0, mas o Android Gradle Plugin recebido é 8.6.1. O helper evita alterar esses arquivos e usa o Gradle 8.7 recomendado para esse plugin.
- Cache e configuração local ficam fora do checkout em `/workspace/toolchains`.
- Instalações usam HTTPS com verificação TLS e checksums oficiais. A CA e o proxy da sessão são respeitados pelas ferramentas Java.

Na raiz `/workspace/App-da-Emily`:

```bash
bash scripts/install-android-cloud.sh
python3 scripts/android-cloud.py gradle :app:assembleDebug
python3 scripts/android-cloud.py gradle :app:testDebugUnitTest :app:lintDebug
```

O APK de desenvolvimento fica em `android/app/build/outputs/apk/debug/app-debug.apk`. Não há servidor web a iniciar. Para abrir e testar o app é necessário Android Studio, emulador ou aparelho Android 8.0+.

Cada tarefa na nuvem já é isolada. Use o checkout existente; não crie Git worktrees sem solicitação explícita.

## Assinatura e instalação

O identificador recebido é `br.com.pedrinho.amorcarrega`, com `versionCode = 1`. A configuração original de release usa a chave de debug. A chave gerada nesta máquina não comprova compatibilidade com a assinatura de uma instalação anterior. Antes de distribuir uma atualização, compare certificados e versão com o APK instalado e use a chave original por um fluxo seguro. Não desinstale o app existente como parte da preparação: isso pode apagar os dados.

## Validação em aparelho

A compilação e o lint não substituem testes reais de notificações, áudio, vibração, tela bloqueada e políticas Motorola. O ZIP não inclui testes automatizados. A tarefa Gradle de testes pode concluir com `NO-SOURCE`; isso não equivale a testes aprovados.

Use os controles de teste já existentes para verificar os níveis configurável, 15%, 5% e 1%, parada pela notificação, conexão do carregador e silenciamento. Depois valide reinicialização e permissões no aparelho. Não use um APK de desenvolvimento como atualização definitiva sem conferir sua assinatura.

## Emulador local na nuvem

A instalação também prepara o emulador Android e a imagem `system-images;android-35;aosp_atd;x86_64`, com o AVD `AmorCuida35ATD`, em resolução 540×960. O app não depende de serviços Google. A imagem completa Google APIs apresentou travamentos do sistema sem KVM; a imagem AOSP ATD foi escolhida para reduzir a carga. Não há KVM nesta máquina; a inicialização usa emulação por software e levou cerca de 7 minutos no primeiro boot validado. Aguarde até 10 minutos, acompanhando o progresso dos logs, antes de diagnosticar uma falha de boot.

O ADB necessita de `/home/agent/.android` para sua chave local de desenvolvimento. A pasta foi inicializada pelo fluxo de permissão do ambiente. Se uma nova tarefa restringir a criação dessa pasta, solicite acesso somente a esse caminho; não desative autenticação nem altere `HOME`.

Na raiz do repositório, execute o emulador em processo separado e mantenha seus logs:

```bash
python3 scripts/android-cloud.py emulator -avd AmorCuida35ATD -accel off -gpu swiftshader -no-window -no-audio -no-snapshot -no-boot-anim -memory 2048 -cores 2 -port 5554 -no-metrics > /tmp/amor-emulator.log 2>&1
```

Em outro processo, aguarde `adb -s emulator-5554 shell getprop sys.boot_completed` retornar `1`. O estado `device` sozinho não garante que o gerenciador de pacotes esteja pronto.

```bash
python3 scripts/android-cloud.py adb -s emulator-5554 shell getprop sys.boot_completed
python3 scripts/android-cloud.py adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP
python3 scripts/android-cloud.py adb -s emulator-5554 shell wm dismiss-keyguard
python3 scripts/android-cloud.py adb -s emulator-5554 shell settings put system screen_off_timeout 1800000
python3 scripts/android-cloud.py adb -s emulator-5554 install -r -g android/app/build/outputs/apk/debug/app-debug.apk
python3 scripts/android-cloud.py adb -s emulator-5554 shell am start -n br.com.pedrinho.amorcarrega/.MainActivity
```

O `-g` concede permissões de runtime ao APK de teste somente no emulador. Não use esses comandos em um aparelho pessoal sem selecionar explicitamente o serial correto. Não presuma que emulador ou servidor ADB sobreviverão a uma nova tarefa.

A imagem AOSP ATD usa `debug.hwui.drawing_enabled=0`: capturas podem ficar pretas. A hierarquia de Views e os serviços permitem verificações funcionais sem renderização. A tentativa de ativar o desenho em runtime não produziu uma captura utilizável nesta máquina; não considere aparência, áudio ou comportamento de bloqueio validados. Para conferir a hierarquia, use `adb shell uiautomator dump /data/local/tmp/amor-ui.xml` pelo helper. O `am start -W` pode esperar o desenho ou uma tela de permissão; use `am start -n` e confirme a atividade, a hierarquia e o serviço.
