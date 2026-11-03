plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.nk.kum"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.nk.kum"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("release") {
            val path = System.getenv("KUM_KEYSTORE_PATH")
            if (path != null && path.isNotEmpty()) {
                storeFile = file(path)
                storePassword = System.getenv("KUM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KUM_KEY_ALIAS")
                keyPassword = System.getenv("KUM_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.1.0")
}
