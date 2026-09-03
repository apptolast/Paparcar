import com.android.build.api.dsl.ApplicationExtension
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// ─────────────────────────────────────────────────────────────────────────────
// PAPARCAR — app/build.gradle.kts
// Android application shell: entry points (MainActivity, PaparcarApp), manifest,
// launcher/notification resources, flavors (prod/mock + Dev Catalog), signing,
// Firebase plugins. All product logic lives in :shared.
// ─────────────────────────────────────────────────────────────────────────────

plugins {
    alias(libs.plugins.androidApplication)
    // Required again: `android.builtInKotlin=false` (see gradle.properties) turns AGP's own
    // Kotlin compilation off, so this module needs KGP to compile its Kotlin sources.
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.firebaseAppDistribution)
}

// ─────────────────────────────────────────────────────────────────────────────
// CREDENTIALS — read from keystore.properties → local.properties → env vars.
// None of these files are committed (all gitignored). CI injects via env vars.
// ─────────────────────────────────────────────────────────────────────────────
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

fun prop(key: String): String? =
    keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }

val releaseKeystoreFile = prop("RELEASE_KEYSTORE_FILE")
val releaseKeystorePassword = prop("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = prop("RELEASE_KEY_ALIAS")
val releaseKeyPassword = prop("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystoreFile, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

val appDistributionCredentialsFile = prop("APP_DISTRIBUTION_CREDENTIALS_FILE")

// ─────────────────────────────────────────────────────────────────────────────
// ANDROID
// ─────────────────────────────────────────────────────────────────────────────
// [BUILD-ZERO-WARNINGS-IS-ENFORCED-001] Same contract as :shared's Android target.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

// The generated `android { }` accessor resolves to the old BaseAppModuleExtension while
// `android.newDsl=false` is set for the KMP workaround. Configuring the typed
// ApplicationExtension avoids that deprecation. [BUILD-ZERO-WARNINGS-IS-ENFORCED-001]
extensions.configure<ApplicationExtension> {
    namespace = "com.rndeveloper.paparcar"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.rndeveloper.paparcar"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 10
        versionName = "1.0.0"

        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${prop("GOOGLE_WEB_CLIENT_ID") ?: ""}\""
        )
        manifestPlaceholders["MAPS_API_KEY"] = prop("MAPS_API_KEY") ?: ""
    }

    // Fail-fast: a release build without MAPS_API_KEY produces an APK whose map
    // tiles silently fail to load. Catch it at configuration time instead of in
    // the field. Debug builds keep working without the key — the map just shows
    // the unauthenticated "for development purposes only" overlay. [SEC-001]
    gradle.taskGraph.whenReady {
        if (allTasks.any { it.name.contains("Release", ignoreCase = true) } &&
            prop("MAPS_API_KEY").isNullOrBlank()
        ) {
            throw GradleException(
                "MAPS_API_KEY is required for release builds — set it in local.properties or the " +
                        "MAPS_API_KEY env var. The key must also be restricted in GCP Console by package " +
                        "name + SHA-1; see docs/release/RELEASE-SECURITY.md."
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
        }
        create("mock") {
            dimension = "environment"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
        }
    }

    // Disable Google Services for mock flavor to avoid package name mismatch
    // in google-services.json which doesn't contain .mock suffix.
    project.afterEvaluate {
        tasks.matching {
            it.name.contains("Mock", ignoreCase = true) && (
                    it.name.contains("GoogleServices") ||
                            it.name.contains("uploadCrashlyticsMappingFile") ||
                            it.name.contains("injectCrashlyticsMappingFileId")
                    )
        }.configureEach {
            enabled = false
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("⚠️  RELEASE signing keys not found — build will be UNSIGNED.")
            }
            firebaseAppDistribution {
                artifactType = "APK"
                releaseNotesFile = "$rootDir/distribution/release-notes.txt"
                groups = "beta-paparcar"
                if (!appDistributionCredentialsFile.isNullOrBlank()) {
                    serviceCredentialsFile = appDistributionCredentialsFile
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Baseline profiles from Compose/AndroidX deps fail to install on x86_64 emulators.
    // Disabling embedding has no effect on prod devices (no custom profile module exists).
    experimentalProperties["android.experimental.art.profile.enable"] = false
}

dependencies {
    implementation(project(":shared"))

    // App-shell AndroidX (entry points + splash + notifications PendingIntents)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.splashscreen)
    implementation(libs.work.runtime.ktx)

    // Koin Android (startKoin from the Application classes)
    implementation(libs.koin.android)

    debugImplementation(libs.ui.tooling)
}

// [build] The Crashlytics ProGuard-mapping upload runs after the APK is built and reaches Firebase
// over the network — blocked by SSL inspection on this dev network (PKIX cert error), failing the
// build even though the APK is already produced. `mappingFileUploadEnabled = false` is ignored by
// AGP 9, so disable the task directly. Re-enable from a network that can reach Firebase (e.g. CI)
// with -PuploadCrashlyticsMapping=true.
tasks.matching { it.name.startsWith("uploadCrashlyticsMappingFile") }.configureEach {
    enabled = project.findProperty("uploadCrashlyticsMapping") == "true"
}
