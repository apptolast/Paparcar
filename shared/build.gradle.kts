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
            // Swift installs BaseLogin's sign-in handlers (GoogleSignInProviderIOS,
            // AppleSignInProviderIOS). Kotlin/Native only exports a dependency's types that our own
            // public API mentions, and nothing here mentions the providers — without this they do
            // not exist in ComposeApp.h. Needs `api(libs.baselogin)`. [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001]
            export(libs.baselogin)
        }
        // GitLive Firebase's klibs embed `-framework FirebaseCore` (et al.) linker opts, so the
        // TEST binary needs the native Firebase frameworks ON THE SEARCH PATH — the app gets them
        // from SPM inside the Xcode build, a bare Gradle test binary gets nothing and dies with
        // `ld: framework 'FirebaseCore' not found`. CI downloads the simulator slices of the
        // firebase-ios-sdk release GitLive 2.6.0 pins (11.8.0) and points FIREBASE_IOS_FRAMEWORKS
        // here. `dynamic_lookup` stays as the net for symbols of libs the opts do not name — the
        // suite never touches real Firebase (fakes only). Only the test binary is relaxed; the
        // app framework above stays strict. [TEST-A-KMP-SUITE-THAT-ONLY-RUNS-ON-JVM-IS-HALF-A-SUITE-001]
        iosTarget.binaries.withType(org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable::class.java)
            .configureEach {
                linkerOpts("-undefined", "dynamic_lookup")
                // Every framework in the set gets NAMED, not just put on the search path: the
                // klib opts only name FirebaseCore/Auth/Firestore, whose ObjC class refs into
                // sibling frameworks (FIRHeartbeatController lives in FirebaseCoreInternal) bind
                // EAGERLY at image load — dynamic_lookup cannot defer those. Static archives make
                // over-linking safe: ld only pulls objects that resolve something.
                System.getenv("FIREBASE_IOS_FRAMEWORKS")?.let { root ->
                    val named = mutableSetOf<String>()
                    File(root).walkTopDown()
                        .filter { it.isDirectory && it.name == "ios-arm64_x86_64-simulator" }
                        .forEach { slice ->
                            linkerOpts("-F${slice.absolutePath}")
                            slice.listFiles().orEmpty()
                                .filter { it.isDirectory && it.extension == "framework" }
                                .forEach { fw ->
                                    if (named.add(fw.nameWithoutExtension)) {
                                        linkerOpts("-framework", fw.nameWithoutExtension)
                                    }
                                }
                        }
                }
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

            // Monetización — RevenueCat KMP. `api` porque :app llama a Purchases.configure()
            // en el arranque, antes de Koin. En Android este artefacto arrastra Play Billing
            // Library, que es lo que Play Console necesita detectar para desbloquear la
            // creación de productos de suscripción. [MONEY-001]
            api(libs.purchases.core)
            // Paywall con plantilla remota. `implementation` a propósito, contra la norma
            // `api` de este bloque: sólo lo consume PaywallScreen y sus tipos no cruzan
            // a :app. [MONEY-001]

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
            // Product analytics — los hechos que ya ocurren, como celda, nunca coordenada.
            // [ANALYTICS-EVENTS-001]
            api(libs.firebase.analytics)

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

            // FCM — el ÚNICO canal por el que un servidor puede despertar a la app cerrada, que es
            // exactamente cuando una alerta de zona sirve para algo.
            // [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
            api(libs.firebase.messaging)

            // WorkManager
            api(libs.work.runtime.ktx)

            // DataStore Preferences
            api(libs.androidx.datastore.preferences)

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

            // The fakes moved to :mockkit so they stop reaching production; the suite is
            // their other consumer. A test binary never ships, so this costs nothing.
            // [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
            implementation(project(":mockkit"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)

            // Motor Ktor de mentira: deja probar los adaptadores de país enteros —petición
            // incluida— en commonMain, sin red y sin duplicar transporte por plataforma.
            // [FUEL-STATIONS-LIST-001]
            implementation(libs.ktor.client.mock)
        }

        // ── androidInstrumentedTest ───────────────────────────────────────────
        // ⛔ EXISTE SOLO PARA MEDIR, y su única inquilina es una sonda, no un test:
        // `PlatformGeocoderPostcodeProbe`. La pregunta que responde —¿el `Geocoder` de plataforma
        // rellena `postalCode` en España?— no la puede contestar Robolectric, porque ahí no hay
        // backend de red detrás del geocoder. Va en :shared y no en :app a propósito: un módulo
        // librería instala SOLO su APK de test, así que no reemplaza la Paparcar que haya en el
        // móvil ni interrumpe un field-test. [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
        getByName("androidInstrumentedTest") {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.junit)
                implementation(libs.androidx.test.junit)
                implementation(libs.androidx.espresso.core)
            }
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
        // Solo para la sonda de arriba. [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

// ── Generador del snapshot de parkings ───────────────────────────────────────
// [OSM-PRECOOKED-SNAPSHOT-001] El snapshot pre-cocinado **ejecuta el filtro de la app**
// (`OsmPublicParkingParser`) en vez de reimplementarlo, y para eso hace falta correr codigo de
// `commonMain` en una JVM de escritorio. `:shared` no tiene target `jvm` —solo `androidTarget()` y
// dos de iOS—, asi que el unico classpath disponible es el **runtime de los tests de Android**, que
// ya es esa JVM y no se empaqueta en nada. El `main()` vive en `androidUnitTest` por esa razon; el
// porque esta escrito en `SnapshotToolMain`.
val parkingSnapshotTool by tasks.registering(JavaExec::class) {
    group = "paparcar"
    description = "Genera el snapshot pre-cocinado de parkings ejecutando el filtro de la app."
    // El classpath se toma del task de test YA RESUELTO, no de `runtimeDependencyFiles` a mano:
    // pedirlo en crudo deja a Gradle eligiendo entre las variantes de `debugRuntimeElements`
    // (`jar`, `android-symbol-with-package-name`, `supported-locale-list`...) sin un `artifactType`
    // que desempate, y falla por ambiguedad. `Test.classpath` es lo que AGP ya decidio para correr
    // los tests, que es exactamente la JVM que esta herramienta necesita. [MEDIDO al abrirlo]
    // ⛔ El classpath NO se toma del task de test, y las dos formas obvias fallan distinto:
    //   - `unitTest.runtimeDependencyFiles` a pelo deja a Gradle eligiendo entre las variantes de
    //     `debugRuntimeElements` (`jar`, `android-symbol-with-package-name`, `supported-locale-list`)
    //     sin un `artifactType` que desempate ⇒ **falla por ambiguedad**;
    //   - `tasks.named<Test>("testDebugUnitTest").map { … }` resuelve, pero un provider derivado de
    //     un task **arrastra su dependencia** ⇒ generar el snapshot **corria la suite entera**, y en
    //     Windows la suite tiene un fallo intermitente de DLL que no tiene nada que ver con esto.
    // Asi que se pide la vista de artefactos con el `artifactType` explicito, que es lo que AGP hace
    // por dentro para el runtime de los tests. [MEDIDO al abrir el ticket]
    val jarType = Attribute.of("artifactType", String::class.java)
    classpath(
        kotlin.androidTarget().compilations.getByName("debugUnitTest").output.allOutputs,
        configurations.named("debugUnitTestRuntimeClasspath").map { runtime ->
            runtime.incoming.artifactView { attributes { attribute(jarType, "jar") } }.files
        },
    )
    dependsOn("compileDebugUnitTestKotlinAndroid")
    mainClass.set("com.rndeveloper.paparcar.tools.parkings.SnapshotToolMain")
    // Las rutas que se le pasan son del REPO (`tools/parkings/...`, `hosting/public`), no de
    // `:shared`: por defecto un JavaExec corre en el directorio de su subproyecto y
    // `tools/parkings/grid-fixtures.json` moria con NoSuchFileException. [MEDIDO al abrirlo]
    workingDir = layout.settingsDirectory.asFile
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
