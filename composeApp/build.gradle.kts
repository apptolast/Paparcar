import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// ─────────────────────────────────────────────────────────────────────────────
// PAPARCAR — composeApp/build.gradle.kts
// KMP (Android + iOS) · Clean Architecture · MVI · Koin · Room · Firebase
// ─────────────────────────────────────────────────────────────────────────────

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
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
// KOTLIN MULTIPLATFORM
// ─────────────────────────────────────────────────────────────────────────────
kotlin {

    // Suppress Beta warning for expect/actual classes (stable usage, not preview features)
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // ── Targets ──────────────────────────────────────────────────────────────
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // ── Source Sets ──────────────────────────────────────────────────────────
    sourceSets {

        // ── commonMain — KMP compartido ──────────────────────────────────────
        commonMain.dependencies {

            // Compose Multiplatform
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material)
            implementation(libs.material3)
            implementation(libs.ui)
            implementation(libs.components.resources)
            implementation(libs.ui.tooling.preview)
            implementation(libs.material.icons.extended)

            // Login Library (JitPack)
            implementation(libs.baselogin)

            // Logging — Napier (KMP structured logger)
            implementation(libs.napier)

            // Kotlin coroutines y utils
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            // DI — Koin (core + Compose Multiplatform + ViewModel KMP)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Persistencia local — Room KMP (todo en commonMain)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)

            // Navegación — Compose Navigation KMP
            implementation(libs.navigation.compose)

            // Firebase — GitLive SDK (wrapper KMP sobre Firebase oficial)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.common)

            // KMP Maps — Google Maps en Android + Apple Maps en iOS
            implementation(libs.kmp.maps.core)
        }

        // ── androidMain — exclusivo Android ──────────────────────────────────
        androidMain.dependencies {

            // Entry point de Compose en Android
            implementation(libs.androidx.activity.compose)

            // AndroidX base
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.material)
            implementation(libs.androidx.lifecycle.service)
            implementation(libs.androidx.splashscreen)

            // Coroutines Android dispatcher
            implementation(libs.kotlinx.coroutines.android)

            // DI — Koin Android
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)

            // Detección — FusedLocationProviderClient + Activity Recognition
            implementation(libs.play.services.location)

            // Firebase BOM + Crashlytics
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.crashlytics)

            // WorkManager
            implementation(libs.work.runtime.ktx)

            // DataStore Preferences
            implementation(libs.androidx.datastore.preferences)

            // GeoFirestore — proximity queries via geohash
            implementation(libs.geofire.android)
        }

        // ── commonTest ────────────────────────────────────────────────────────
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        // ── androidUnitTest ───────────────────────────────────────────────────
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.junit)
                implementation(libs.konsist)
                implementation(libs.work.testing)
                implementation(libs.robolectric)
                implementation(libs.androidx.test.core)
            }
        }

        // ── androidInstrumentedTest ───────────────────────────────────────────
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.androidx.test.junit)
                implementation(libs.androidx.espresso.core)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ROOM KMP
// ─────────────────────────────────────────────────────────────────────────────
room {
    schemaDirectory("$projectDir/schemas")
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSE RESOURCES
// ─────────────────────────────────────────────────────────────────────────────
compose.resources {
    packageOfResClass = "paparcar.composeapp.generated.resources"
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)

    debugImplementation(libs.ui.tooling)
}

// ─────────────────────────────────────────────────────────────────────────────
// ANDROID
// ─────────────────────────────────────────────────────────────────────────────
android {
    namespace = "io.apptolast.paparcar"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.apptolast.paparcar"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 3
        versionName = "1.0.0-beta02"

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
//            if (hasReleaseSigning) {
//                signingConfig = signingConfigs.getByName("release")
//            } else {
//                logger.warn("⚠️  RELEASE signing keys not found — build will be UNSIGNED.")
//            }
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

// [build] The Crashlytics ProGuard-mapping upload runs after the APK is built and reaches Firebase
// over the network — blocked by SSL inspection on this dev network (PKIX cert error), failing the
// build even though the APK is already produced. `mappingFileUploadEnabled = false` is ignored by
// AGP 9, so disable the task directly. Re-enable from a network that can reach Firebase (e.g. CI)
// with -PuploadCrashlyticsMapping=true.
tasks.matching { it.name.startsWith("uploadCrashlyticsMappingFile") }.configureEach {
    enabled = project.findProperty("uploadCrashlyticsMapping") == "true"
}
