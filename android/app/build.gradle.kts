plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.pedrinho.amorcarrega"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.pedrinho.amorcarrega"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Sem isto, o build "release" (o que o Android Studio gera em
    // Build > Build APK(s) fora do botão ▶) sai sem assinatura e não
    // instala em NENHUM aparelho — inclusive o seu. Usar a mesma chave
    // de debug aqui é só para você compartilhar o APK com facilidade;
    // não é isso que travava a instalação em aparelhos específicos.
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // mantenha aqui qualquer outra dependência que você já tinha
}