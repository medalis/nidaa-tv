import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// URL de l'écran web : -PnidaaScreenUrl=https://... (défaut dans gradle.properties).
val nidaaScreenUrl: String =
    (project.findProperty("nidaaScreenUrl") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        ?: "https://nidaa-ecran.netlify.app"

// Signature release : fichier optionnel keystore.properties (non versionné) à la racine d'apps/tv.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "dj.nidaa.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "dj.nidaa.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.0"
        buildConfigField("String", "SCREEN_URL", "\"${nidaaScreenUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            // HTTP en clair autorisé en debug (serveur de test LAN http://192.168.x.x:5173).
            manifestPlaceholders["usesCleartext"] = "true"
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartext"] = "false"
            // Sans keystore.properties, l'APK release est produit non signé.
            signingConfig = signingConfigs.findByName("release")
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

    lint {
        // Appli kiosque diffusée hors Play Store.
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
