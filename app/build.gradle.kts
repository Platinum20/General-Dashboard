import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

// La firma della release arriva dalle variabili d'ambiente (secret di GitHub Actions).
// Senza di esse la build release usa la chiave di debug, utile solo per prove in locale.
val keystorePath: String? = System.getenv("KEYSTORE_FILE")
val hasReleaseKey = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
    namespace = "com.platinum20.dashboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.platinum20.dashboard"
        minSdk = 26
        targetSdk = 35
        versionCode = versionProps.getProperty("VERSION_CODE").toInt()
        versionName = versionProps.getProperty("VERSION_NAME")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_REPO", "\"Platinum20/General-Dashboard\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        // NewPipe Extractor usa API di Java 11 che i telefoni più vecchi non hanno.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Giro fotografico dell'app: gradle testDebugUnitTest -Ptour=true
                it.systemProperty("tour", project.findProperty("tour") ?: "false")
                it.systemProperty("tour.dir", layout.buildDirectory.dir("tour").get().asFile.absolutePath)
                // Controlli con la rete vera (notizie, meteo, aggiornamenti, radio): gradle testDebugUnitTest -Plive=true
                it.systemProperty("live", project.findProperty("live") ?: "false")
                it.systemProperty("live.dir", layout.buildDirectory.dir("live").get().asFile.absolutePath)
                it.maxHeapSize = "3g"
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    // Aggiornamenti in background per widget e notifica del mattino
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Immagini delle notizie e lettore interno degli articoli
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    // Vetro sfocato sugli elementi che galleggiano (barra in basso, mini player).
    implementation("dev.chrisbanes.haze:haze:1.3.1")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("androidx.webkit:webkit:1.12.1")
    // Modalità lettura: estrae testo, immagini e video dalla pagina dell'articolo (algoritmo Readability)
    implementation("net.dankito.readability4j:readability4j:1.0.8")

    // Player video (file del telefono, mp4/webm e streaming HLS) e anteprime dei video
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("io.coil-kt.coil3:coil-video:3.0.4")
    // Player nativo di YouTube: ricava i flussi dei video senza pagina web (lo stesso motore di NewPipe)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")
    // Accesso Google per le API di YouTube (solo lettura)
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.work:work-testing:2.10.0")
}
