import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    // google-services procesa google-services.json y configura Firebase para Android.
    // Si no tienes el archivo en el repo (gitignored), usa un placeholder o elimina esta línea
    // hasta que estés listo para conectar Firebase en Android.
    alias(libs.plugins.googleServices)
}

// ─────────────────────────────────────────────────────────────────────────────
// KOTLIN MULTIPLATFORM
// ─────────────────────────────────────────────────────────────────────────────
kotlin {

    // ── Targets ──────────────────────────────────────────────────────────────
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
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
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)

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

            // Navegación — Compose Navigation KMP
            implementation(libs.navigation.compose)

            // Firebase — GitLive SDK (wrapper KMP sobre Firebase oficial)
            // Siempre en commonMain. En Android necesita google-services.json en runtime.
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.common)
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

            // Coroutines Android dispatcher
            implementation(libs.kotlinx.coroutines.android)

            // DI — Koin Android (KoinApplication, androidContext(), etc.)
            implementation(libs.koin.android)
            // Koin AndroidX Compose (koinViewModel con scoping Android ViewModel)
            implementation(libs.koin.androidx.compose)

            // ── Detección (exclusivo Android) ─────────────────────────────────
            // FusedLocationProviderClient + Activity Recognition Transitions API
            implementation(libs.play.services.location)
            // Firebase BOM — gestiona versiones nativas que GitLive SDK necesita en Android
            implementation(project.dependencies.platform(libs.firebase.bom))

            // WorkManager (tareas en background opcionales)
            implementation(libs.work.runtime.ktx)

            // GeoFirestore — queries de proximidad por geohash
            implementation(libs.geofire.android)

            // Google Maps Compose
            implementation(libs.maps.compose)
            implementation(libs.play.services.maps)
        }

        // ── commonTest ────────────────────────────────────────────────────────
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        // ── androidUnitTest ───────────────────────────────────────────────────
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.junit)
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
// schemaDirectory exporta el JSON del esquema para migraciones
// KSP debe configurarse para CADA plataforma objetivo de Room
// ─────────────────────────────────────────────────────────────────────────────
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // KSP processors — uno por cada plataforma KMP objetivo
    add("kspAndroid", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)

    // Compose tooling (solo debug)
    debugImplementation(compose.uiTooling)
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
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
//            applicationIdSuffix = ".debug"
//            versionNameSuffix = "-debug"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}
