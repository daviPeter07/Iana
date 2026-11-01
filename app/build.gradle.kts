// AGP 9 já traz o suporte a Kotlin embutido: não é preciso o plugin org.jetbrains.kotlin.android.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.davipeter.iana"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.davipeter.iana"
        minSdk = 29 // Android 10
        targetSdk = 36
        versionCode = 1
        versionName = "0.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // App pessoal: assina o release com a chave de debug para poder instalar direto.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.glance.appwidget)
    implementation(libs.androidx.work.runtime)
    // Vosk: reconhecimento de voz offline e open source, usado para ouvir a palavra "IANA".
    // O JNA precisa vir como AAR (com as bibliotecas nativas do Android), não como JAR.
    implementation(libs.vosk.android) { exclude(group = "net.java.dev.jna") }
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
