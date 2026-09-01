import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ─────────────────────────────────────────────────────────────────────────────
// PAPARCAR — shared/build.gradle.kts
// KMP library (Android + iOS) · Clean Architecture · MVI · Koin · Room · Firebase
// The Android app shell (activities, Application, flavors, signing, Firebase
// plugins) lives in :app. This module owns every line of product logic.
// ─────────────────────────────────────────────────────────────────────────────

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
}

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
    // [ARCH-HEALTH-001 F7] `com.android.library` + androidTarget(), NOT the newer
    // `com.android.kotlin.multiplatform.library`. Compose Multiplatform 1.12.0 only knows how
    // to wire `copyAndroidMainComposeResourcesToAndroidAssets` into the classic Android plugins;
    // under the KMP-library plugin that task is registered with no `outputDirectory` and every
    // composeResource (drawables, fonts, ALL strings) silently vanishes from the APK. Measured
    // on device: the app installs, compiles and passes its tests, then dies on first frame with
    // MissingResourceException. Revisit when Compose MP supports the new plugin.
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // [BUILD-ZERO-WARNINGS-IS-ENFORCED-001] The counter is at zero; keep it there.
            // Scoped to the Android target on purpose: iOS belongs to a Mac, where iosMain
            // can actually be verified first.
            allWarningsAsErrors.set(true)
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
        // api(...) a propósito: :app compone pantallas del catálogo mock y arranca Koin
        // con tipos de este módulo en las firmas (Compose, Koin, BaseLogin, navegación…).
        // Con dos módulos y un solo consumidor, exponer la superficie completa es más
        // honesto que perseguir fugas de implementation una a una.
        commonMain.dependencies {

            // Compose Multiplatform
            api(libs.runtime)
            api(libs.foundation)
            api(libs.material)
            api(libs.material3)
            api(libs.ui)
            api(libs.components.resources)
            api(libs.ui.tooling.preview)
            api(libs.material.icons.extended)

            // Login Library (JitPack)
            api(libs.baselogin)

            // Logging — Napier (KMP structured logger)
            api(libs.napier)

            // Kotlin coroutines y utils
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)

            // DI — Koin (core + Compose Multiplatform + ViewModel KMP)
            api(libs.koin.core)
            api(libs.koin.compose)
            api(libs.koin.compose.viewmodel)

            // Persistencia local — Room KMP (todo en commonMain)
            api(libs.room.runtime)
            api(libs.sqlite.bundled)

            // Navegación — Compose Navigation KMP
            api(libs.navigation.compose)

            // Firebase — GitLive SDK (wrapper KMP sobre Firebase oficial)
            api(libs.firebase.firestore)
            api(libs.firebase.auth)
            api(libs.firebase.common)

            // KMP Maps — Google Maps en Android + Apple Maps en iOS
            api(libs.kmp.maps.core)

            // Image loading — Coil 3 (AsyncImage en commonMain). Fetcher de red
            // multiplataforma vía Ktor; el engine concreto va por plataforma
            // (okhttp en androidMain, darwin en iosMain).
            api(libs.coil.compose)
            api(libs.coil.network.ktor3)
        }

        // ── androidMain — exclusivo Android ──────────────────────────────────
        androidMain.dependencies {

            // AndroidX base
            api(libs.androidx.core.ktx)
            api(libs.androidx.appcompat)
            api(libs.androidx.material)
            api(libs.androidx.lifecycle.service)

            // Coroutines Android dispatcher
            api(libs.kotlinx.coroutines.android)

            // DI — Koin Android
            api(libs.koin.android)
            api(libs.koin.androidx.compose)

            // Detección — FusedLocationProviderClient + Activity Recognition
            api(libs.play.services.location)

            // Firebase BOM + Crashlytics
            api(project.dependencies.platform(libs.firebase.bom))
            api(libs.firebase.crashlytics)

            // WorkManager
            api(libs.work.runtime.ktx)

            // DataStore Preferences
            api(libs.androidx.datastore.preferences)

            // GeoFirestore — proximity queries via geohash
            implementation(libs.geofire.android)

            // Engine Ktor (OkHttp) para el fetcher de red de Coil en Android.
            implementation(libs.ktor.client.okhttp)
        }

        // ── iosMain — exclusivo iOS ──────────────────────────────────────────
        iosMain.dependencies {
            // Engine Ktor (Darwin/NSURLSession) para el fetcher de red de Coil en iOS.
            implementation(libs.ktor.client.darwin)
        }

        // ── commonTest ────────────────────────────────────────────────────────
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        // ── androidUnitTest ───────────────────────────────────────────────────
        getByName("androidUnitTest") {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.junit)
                implementation(libs.konsist)
                implementation(libs.work.testing)
                implementation(libs.robolectric)
                implementation(libs.androidx.test.core)
                // Verificación estática del grafo Koin [DET-KOIN-MODULE-VERIFY-001]
                implementation(libs.koin.test)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ANDROID (library) — no flavors: :app owns prod/mock and matches this single variant
// ─────────────────────────────────────────────────────────────────────────────
// Typed extension for the same reason as in :app — the `android { }` accessor resolves to the
// old DSL while `android.newDsl=false` is set. [BUILD-ZERO-WARNINGS-IS-ENFORCED-001]
extensions.configure<LibraryExtension> {
    namespace = "com.rndeveloper.paparcar.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    // :app (galería del Dev Catalog) referencia Res.* directamente.
    publicResClass = true
}

// ─────────────────────────────────────────────────────────────────────────────
// I18N — los strings.xml son ENTRADA de los tests [I18N-PERMISSIONS-BUTTONS-EXIST-IN-ONE-LOCALE-ONLY-001]
// ─────────────────────────────────────────────────────────────────────────────
// `LocaleParityGuardrailTest` lee los ficheros de strings de las dos superficies directamente del
// disco, así que Gradle no puede deducir que le afectan: sin esto, tocar una traducción deja el test
// UP-TO-DATE y el guardarraíl pasa en verde SIN mirar el cambio. Medido: mutar
// `app/src/main/res/values-it` no lo despertaba. Que el de `:shared` sí se despertase era casualidad
// — sus recursos alimentan la compilación. Un guardarraíl que no vuelve a correr no guarda nada.
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/commonMain/composeResources"))
        .withPropertyName("composeResourcesStrings")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.settingsDirectory.dir("app/src/main/res"))
        .withPropertyName("appAndroidResStrings")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
