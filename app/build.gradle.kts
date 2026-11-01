// AGP 9 já traz o suporte a Kotlin embutido: não é preciso o plugin org.jetbrains.kotlin.android.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Segredos locais (fora do git): coloque em local.properties a linha
// picovoice.accessKey=SUA_CHAVE
val localProperties = java.util.Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.davipeter.iana"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.davipeter.iana"
        minSdk = 29 // Android 10
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"

        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"${localProperties.getProperty("picovoice.accessKey", "")}\"")
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
        buildConfig = true
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
    implementation(libs.porcupine)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
