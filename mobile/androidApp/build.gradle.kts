plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
        compose = true
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
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
