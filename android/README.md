# Amor, Cuida 2.0 ♥ — módulo Android

Abra **esta pasta** (`android`) no Android Studio e sincronize o projeto. O wrapper corrigido usa Gradle 8.7; use JDK 17 ou 21 e SDK Android 35.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

O pacote `br.com.pedrinho.amorcarrega` e as preferências de bateria foram mantidos. A versão é 2.0.0, código 2. A configuração de release herdada ainda usa chave de debug; configure a assinatura original localmente antes de distribuir uma atualização da instalação anterior. Não envie chaves para o Git.

Medicamentos e histórico são locais, em Room. Não há prescrição pré-preenchida, cálculo de dose, confirmação automática, conta nem sincronização externa. O calendário marca a data real informada pelo usuário.

As instruções completas, limitações e resultados de testes estão no [README principal](../README.md) e em [docs/VALIDACAO.md](../docs/VALIDACAO.md).
