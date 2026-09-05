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
    alias(libs.plugins.aboutLibraries)
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
        // GitLive Firebase needs the native Firebase frameworks at LINK time. The app gets them
        // from SPM inside the Xcode build; a bare Gradle test binary has no search path to them
        // and dies with `ld: framework 'FirebaseCore' not found`. The suite never touches real
        // Firebase (fakes only), so the TEST binary — and only it; the app framework above stays
        // strict — resolves those symbols lazily instead of at link time.
        // [TEST-A-KMP-SUITE-THAT-ONLY-RUNS-ON-JVM-IS-HALF-A-SUITE-001]
        iosTarget.binaries.withType(org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable::class.java)
            .configureEach {
                linkerOpts("-undefined", "dynamic_lookup")
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

            // Licencias OSS — SOLO el parser de los datos que genera su plugin Gradle.
            // `implementation` a propósito, contra la norma `api` de este bloque: sus tipos se
            // mapean a modelo de dominio en el repositorio y no cruzan a :app.
            // [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001]
            implementation(libs.aboutlibraries.core)

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
// LICENCIAS OSS — la lista es una PROYECCIÓN del grafo de dependencias
// [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001]
// ─────────────────────────────────────────────────────────────────────────────
// El fichero se GENERA en `build/` y se enchufa como directorio de composeResources: nunca se
// commitea. Es la única forma de que subir una dependencia actualice la pantalla sin que nadie
// se acuerde de regenerar nada — un JSON versionado a mano se queda obsoleto en silencio, que es
// justo el fallo que este ticket vino a cerrar.
val licensesResourceDir: Provider<Directory> =
    layout.buildDirectory.dir("generated/aboutLibraries/composeResources")

/** Fuentes reales + el JSON generado, en un solo árbol. Ver [mergeComposeResourcesWithLicenses]. */
val mergedComposeResourcesDir: Provider<Directory> =
    layout.buildDirectory.dir("generated/composeResourcesWithLicenses/commonMain/composeResources")

aboutLibraries {
    // ⛔ `collect { fetchRemoteLicense = true }` NO se usa, y no es un olvido. Medido el 03-09 sobre
    // este mismo grafo: baja el LICENSE de cada repo, así que las 7 licencias distintas se
    // convierten en 18 entradas con ONCE copias byte-a-byte del mismo Apache-2.0 (+100 KB en el
    // APK) y la pantalla pasa a enseñar "18 licencias" donde hay 7. Sin él, el mapeo a SPDX ya
    // trae el texto íntegro de las tres licencias OSS reales (Apache-2.0, MIT, BSD-3-Clause);
    // las otras cuatro son términos propietarios (Android SDK, Play ×2, Go) que no son
    // redistribuibles ni fetchables, y se enseñan con su enlace — como hace el propio Google.
    library {
        // Guardarraíl en el build, no en un test: una dependencia sin licencia declarada rompe la
        // compilación en vez de aparecer como "Unknown" en la pantalla. Una lista incompleta sigue
        // siendo una mentira.
        requireLicense = true
    }
    export {
        outputFile = licensesResourceDir.map { it.file("files/aboutlibraries.json") }
        prettyPrint = false
        // La pantalla enseña nombre, versión, licencia y web. La prosa de marketing de cada POM
        // y sus enlaces de financiación solo engordan el APK.
        excludeFields = setOf("description", "funding")
    }
}

// ⚠️ `compose.resources { customDirectory(...) }` SUSTITUYE el directorio del source set, no lo
// añade — medido: registrar el dir generado en `commonMain` dejó sin resolver las ~700 keys de
// strings y todos los drawables de golpe (`ResourcesDSL.kt:55` → `customResourceDirectories[name]
// ?: default`). Por eso el árbol se fusiona aquí: fuentes reales + JSON generado en un directorio
// único que es el que ve Compose Resources. Fusionar (en vez de registrar el dir generado en
// `androidMain`/`iosMain`, que están vacíos hoy) evita la trampa de que crear
// `src/androidMain/composeResources` mañana desactive esto en silencio.
// El plugin, además del export, engancha su propia generación a las variantes Android y mete una
// SEGUNDA copia del JSON en `res/raw/aboutlibraries.json` (medido: 128 KB en el APK, duplicando los
// 131 KB que ya viajan como composeResource). Esa vía es para su UI de Android, que no usamos —
// nuestra pantalla lee el recurso KMP. Se apaga: dos copias de la misma lista es peso muerto, y la
// que no se lee es además la que nadie comprobaría si se desincronizara.
tasks.matching { it.name.startsWith("prepareLibraryDefinitions") }.configureEach { enabled = false }

val mergeComposeResourcesWithLicenses = tasks.register<Sync>("mergeComposeResourcesWithLicenses") {
    dependsOn(tasks.named("exportLibraryDefinitions"))
    from(layout.projectDirectory.dir("src/commonMain/composeResources"))
    from(licensesResourceDir)
    into(mergedComposeResourcesDir)
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSE RESOURCES
// ─────────────────────────────────────────────────────────────────────────────
compose.resources {
    packageOfResClass = "paparcar.composeapp.generated.resources"
    // :app (galería del Dev Catalog) referencia Res.* directamente.
    publicResClass = true
    // Árbol fusionado: `Res.readBytes("files/aboutlibraries.json")` ve el JSON generado como
    // cualquier otro recurso, sin que viva en el árbol de fuentes ni se commitee.
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = mergeComposeResourcesWithLicenses.map { mergedComposeResourcesDir.get() },
    )
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

    // Mismo motivo para la atribución OSS: `OpenSourceAttributionGuardrailTest` lee del disco el
    // JSON YA FUSIONADO — los bytes que se empaquetan — así que sin esto ni existiría al correr los
    // tests en limpio, ni volvería a correr el guardarraíl al cambiar una dependencia.
    // [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001]
    dependsOn(mergeComposeResourcesWithLicenses)
    inputs.file(mergedComposeResourcesDir.map { it.file("files/aboutlibraries.json") })
        .withPropertyName("openSourceAttribution")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
