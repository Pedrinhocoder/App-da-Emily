# Desenvolvimento Android — Amor, Cuida 2.0

O projeto está em `android/`, com wrapper **Gradle 8.7** corrigido a partir do ZIP enviado. Kotlin 2.0.21, KSP 2.0.21-1.0.28, Android Gradle Plugin 8.6.1 e Room 2.7.2. SDK de compilação/alvo 35, mínimo 26, bytecode Java/Kotlin 17. Use JDK 17 ou 21 no Android Studio.

## Máquina na nuvem

Na raiz `/workspace/App-da-Emily`:

```bash
bash scripts/install-android-cloud.sh
python3 scripts/android-cloud.py gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 scripts/android-cloud.py gradle :app:assembleDebugAndroidTest
```

O helper configura proxy, confiança na CA da sessão, SDK e cache Java. As instalações mantêm TLS e checksums oficiais. Os arquivos de ferramentas ficam em `/workspace/toolchains`, fora do Git. O helper não altera `HOME`. A pasta `/home/agent/.android` é necessária para a chave local do ADB; se o sandbox impedir sua inicialização, solicite acesso somente a esse caminho pelo fluxo de permissões.

Cada tarefa na nuvem já é isolada: use o checkout existente; não crie worktrees sem solicitação explícita. O aplicativo não tem servidor web. Processos de emulador/ADB não devem ser considerados persistentes entre tarefas.

## Testes no host

Os testes Kotlin de recorrência e bateria executam sem emulador. O contrato de persistência Room executa também no Robolectric (Android API 28), com os DAOs reais, transações e SQLite. `CareScreensTest` renderiza as Views reais em Skia e gera imagens em `android/app/build/screenshots`; essas imagens são renderizações de teste, não capturas de um Motorola.

```bash
python3 scripts/android-cloud.py gradle :app:testDebugUnitTest
```

## Emulador e testes instrumentados

A instalação prepara `AmorCuida35ATD`, imagem mínima Android 15/API 35, AOSP ATD, display 540×960. A imagem ATD desativa o desenho por padrão e pode produzir capturas pretas. Ela permite testes de estrutura de interface, banco e serviços. O Android sem KVM é lento nesta máquina e apresentou reinícios do sistema pelo watchdog. Consulte os resultados reais em `VALIDACAO.md`; não trate um processo, uma porta ou `sys.boot_completed=1` isoladamente como comprovação de funcionamento.

```bash
python3 scripts/android-cloud.py emulator -avd AmorCuida35ATD -accel off -gpu swiftshader -no-window -no-audio -no-snapshot -no-boot-anim -memory 2048 -cores 2 -port 5554 -no-metrics > /tmp/amor-emulator.log 2>&1
```

Mantenha o comando numa sessão de execução longa, acompanhando o progresso com saídas antecipadas. Aguarde o boot e verifique que os serviços de atividade e pacote respondem. Em outra sessão, na raiz:

```bash
python3 scripts/android-cloud.py adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP
python3 scripts/android-cloud.py adb -s emulator-5554 shell wm dismiss-keyguard
python3 scripts/android-cloud.py adb -s emulator-5554 install -r -g android/app/build/outputs/apk/debug/app-debug.apk
python3 scripts/android-cloud.py adb -s emulator-5554 shell am start -n br.com.pedrinho.amorcarrega/.MainActivity
```

Use apenas um emulador **dedicado aos testes**, sem dados reais. A suíte instrumentada inclui um teste que desliga temporariamente Wi-Fi/dados e concede alarmes exatos; isso não deve ser executado inadvertidamente em um aparelho pessoal. Com o emulador saudável:

```bash
# Na pasta android, usando SDK/JDK configurados:
./gradlew :app:connectedDebugAndroidTest
```

Na nuvem, use `python3 scripts/android-cloud.py gradle :app:connectedDebugAndroidTest`. O teste nativo prepara a concessão de alarmes exatos. Para verificar especificamente o caminho aproximado, configure a negação antes de iniciar um processo de teste da interface e passe `-e alarm_mode inexact`; não revogue esse acesso durante um processo de instrumentação, pois o Android pode encerrar o app ao revogá-lo.

Teste manualmente no Motorola: notificações e alarmes exatos concedidos/negados, canal silenciado, Não Perturbe, restrições de bateria, app em segundo plano, tela bloqueada, reboot e mudança de fuso/hora. **Forçar parada** pelo Android bloqueia alarmes até reabrir o app.

## Dados e assinatura

O pacote é `br.com.pedrinho.amorcarrega`, versão 2.0.2/código 4. O arquivo de preferências `config` da bateria foi mantido; Room usa outro banco. O schema inicial do módulo está versionado em `android/app/schemas/`, sem migração destrutiva.

O APK debug é `android/app/build/outputs/apk/debug/app-debug.apk`. A configuração release herdada também usa chave debug. A chave da nuvem não comprova compatibilidade com o aplicativo instalado no celular. Para atualizar mantendo os dados, gere/assine com a chave original e versão compatível. Não coloque keystores, senhas ou bancos reais no repositório.

A versão 2.0.2 também tem serviço mediaPlayback para medicamentos e acesso a tela cheia configurável no Android 14+. Consulte [alarme persistente](ALARME-MEDICAMENTOS-2.0.2.md).
