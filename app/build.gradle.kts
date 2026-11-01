import java.security.MessageDigest

// AGP 9 já traz o suporte a Kotlin embutido: não é preciso o plugin org.jetbrains.kotlin.android.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// sherpa-onnx: o motor da voz da IANA, embutido no app. Ele não está no Maven Central,
// então o AAR oficial é baixado das releases do GitHub na primeira vez que o projeto sincroniza.
// (A 1.13.8 é a versão testada com a voz "dii"; a 2.x mudou o tratamento desse modelo.)
val sherpaVersion = "1.13.8"
val sherpaSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")
if (!sherpaAar.exists()) {
    sherpaAar.parentFile.mkdirs()
    val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
    logger.lifecycle("Baixando $url")
    val tmp = File(sherpaAar.path + ".part")
    uri(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
    val digest = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes())
    val actual = digest.joinToString("") { byte -> "%02x".format(byte) }
    check(actual == sherpaSha256) { "Checksum do sherpa-onnx não confere: $actual" }
    tmp.renameTo(sherpaAar)
}

android {
    namespace = "com.davipeter.iana"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.davipeter.iana"
        minSdk = 29 // Android 10
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.0"

        // Celulares (arm64) e emulador (x86_64): deixa o APK menor que com todas as arquiteturas.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
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
    // Voz da IANA (sherpa-onnx) e o descompactador do modelo (.tar.bz2).
    implementation(files(sherpaAar))
    implementation(libs.commons.compress)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
