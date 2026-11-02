import java.util.Properties

plugins {
    id("com.android.application")
}

// La firma vive fuera del repo: keystore.properties apunta a la clave.
val firma = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "org.acedo.jmtiempos"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.acedo.jmtiempos"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.8.1"
    }

    signingConfigs {
        if (firma.isNotEmpty()) {
            create("release") {
                storeFile = file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (firma.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
