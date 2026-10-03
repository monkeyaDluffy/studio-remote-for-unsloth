import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.monkeyadluffy.studioremote"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.monkeyadluffy.studioremote"
        minSdk = 29
        targetSdk = 37
        versionCode = 13
        versionName = "1.12"
    }

    // The battery-optimisation exemption is a restricted permission on Play, so only the GitHub build asks for it.
    flavorDimensions += "dist"
    productFlavors {
        create("github") {
            dimension = "dist"
            buildConfigField("boolean", "ASK_BATTERY_EXEMPTION", "true")
        }
        create("play") {
            dimension = "dist"
            buildConfigField("boolean", "ASK_BATTERY_EXEMPTION", "false")
        }
    }

    // Release key lives outside git: keystore.properties next to settings.gradle.kts (see keystore.properties.example).
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    signingConfigs {
        if (keystoreProps != null) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // R8: strips unused code (most of the Material icon set) and shrinks resources.
            optimization {
                enable = true
            }
            // Without keystore.properties the release APK is left unsigned; sign it with apksigner.
            signingConfig = signingConfigs.findByName("release")
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
    packaging {
        // Sideloaded over KDE Connect: keep dex compressed (minSdk 28+ would store it raw, ~3x larger).
        dex { useLegacyPackaging = true }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.45.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-code:0.45.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.0")
}
