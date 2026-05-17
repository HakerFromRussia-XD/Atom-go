plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

android {
    namespace = "com.atomgo.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atomgo.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        val switchProps = Properties().apply {
            val switchFile = rootProject.file("mobile/iosApp/AtomGoIOS/BackendSwitch.properties")
            if (switchFile.exists()) {
                switchFile.inputStream().use { load(it) }
            }
        }
        val atomgoEnv = (project.findProperty("atomgoEnv") as String?)
            ?.trim()
            .orEmpty()
            .ifBlank { switchProps.getProperty("ATOMGO_ENV", "local").trim() }
        val atomgoBackendUrl = (project.findProperty("atomgoBackendUrl") as String?)
            ?.trim()
            .orEmpty()
        buildConfigField("String", "ATOMGO_ENV", "\"$atomgoEnv\"")
        buildConfigField("String", "ATOMGO_BACKEND_URL", "\"$atomgoBackendUrl\"")
        buildConfigField(
            "String",
            "ATOMGO_BASE_URL_PROD",
            "\"${switchProps.getProperty("ATOMGO_BASE_URL_PROD", "https://atomgo.157.22.203.6.nip.io/api/v1").trim()}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":mobile:shared"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
