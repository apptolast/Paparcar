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
        versionCode = 13
        versionName = "1.2.0"

        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${prop("GOOGLE_WEB_CLIENT_ID") ?: ""}\""
        )
        // Tankerkönig (Germany's fuel prices). Personal, gitignored, and OPTIONAL on purpose:
        // blank means "Germany has no feed" and the map degrades to OSM's honest floor — never a
        // build failure, because anyone cloning the public repo builds without one.
        // [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
        buildConfigField(
            "String",
            "GERMANY_FUEL_STATION_KEY",
            "\"${prop("GermanyFuelStationKey") ?: ""}\""
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
            // [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001] ⛔ The Maps key is restricted to
            // `com.rndeveloper.paparcar` + SHA-1 (created 03-09), and `.mock` was never added to
            // that list — so since then the Dev Catalog's map paints BLACK and only the puck shows.
            // `-PmockAsProd` drops the suffix for a store-shot session, so the emulator build passes
            // the restriction. It is a WORKAROUND: the durable fix is adding the `.mock` package to
            // the key in the GCP console. Never pass it for a build that lands on a phone which also
            // carries prod — same applicationId means one replaces the other.
            if (!project.hasProperty("mockAsProd")) {
                applicationIdSuffix = ".mock"
                // Settings prints the version name, so a store capture of that screen would read
                // "1.2.0-mock". The suffix goes with the package: both exist to say "this is not
                // the app", and the shot session is the one build where that must not be said.
                versionNameSuffix = "-mock"
            }
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
            // RevenueCat Test Store (key test_...): permite probar el flujo de compra completo
            // sin que existan aún los productos reales en Play. [MONEY-001]
            buildConfigField(
                "String",
                "REVENUECAT_API_KEY",
                "\"${prop("revenuecatKeyDebug") ?: ""}\""
            )
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            // Key de producción de RevenueCat (goog_...), la del dashboard al conectar la app de
            // Play. Si está en blanco el arranque simplemente NO configura el SDK (guard en
            // PaparcarApp) en vez de crashear — así un build de desbloqueo, que solo necesita que
            // Play Billing viaje dentro del bundle, sigue saliendo. [MONEY-001]
            buildConfigField(
                "String",
                "REVENUECAT_API_KEY",
                "\"${prop("revenuecatKeyRelease") ?: ""}\""
            )
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

    // Home-screen widget del aparcamiento activo. [WIDGET-PARKED-001]
    implementation(libs.glance.appwidget)

    debugImplementation(libs.ui.tooling)

    // El grafo del flavor mock se verifica como el de prod. `:shared` no puede hacerlo: mockModule
    // vive aquí, y sin dataModule cada puerto que domainModule consume está declarado DOS veces.
    // [MOCK-A-SECOND-GRAPH-NEEDS-A-SECOND-VERIFY-001]
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.koin.test)
}

// [build] A release that ships uploads its mapping, or it is not a release.
// [RELEASE-A-CRASH-WITHOUT-A-MAPPING-IS-UNREADABLE-001]
//
// ⛔ **On by default, and the skip is what has to be typed.** This used to be the other way round —
// off unless you passed `-PuploadCrashlyticsMapping=true` — because the upload reaches Firebase over
// the network and SSL inspection on this dev machine failed it with a PKIX error, tearing down a
// build whose APK was already produced. The reason was real; leaving the OFF side as the default was
// the mistake. Nobody remembers a flag on every release, so 1.0.0 went to Play on 03-09-2026 and
// every production crash since arrived obfuscated: the 10-09 map crash blamed `od2.m`, a frame
// Crashlytics could not even attribute to us.
//
// R8 does not rename identically across runs, so a mapping only ever fits ITS OWN build — a crash
// whose mapping was never uploaded can never be read afterwards, from any leftover file. There is no
// catching up later, which is what makes the default the whole ticket.
//
// Re-measured on 10-09-2026 from this same machine: `uploadCrashlyticsMappingFileProdRelease` runs
// and succeeds. The PKIX failure went with the Norton root landing in the JBR truststore (done for
// Gradle itself), so the guard was outliving its cause.
//
// The escape hatch stays for the case it was written for — a release build you are NOT going to put
// on anybody's phone (measuring R8, reproducing a minified bug) — and it now costs a word:
//     ./gradlew :app:bundleProdRelease -PskipCrashlyticsMapping=true
//
// A failing upload is left FAILING the build on purpose. A release whose mapping did not reach
// Firebase is a release that cannot be diagnosed, and finding that out at build time is the point.
//
// (`mappingFileUploadEnabled = false` is ignored by AGP 9, which is why this toggles the task.)
//
// ⛔ `mock` is excluded here, not just above: that flavor's upload is switched off in the
// `afterEvaluate` block near the flavor declarations (its applicationId carries a suffix that
// `google-services.json` does not know). `configureEach` runs when the task is REALISED, which is
// after `afterEvaluate`, so without this guard flipping the default to ON would quietly switch the
// mock upload back on and break `assembleMockRelease` at the last step.
tasks.matching {
    it.name.startsWith("uploadCrashlyticsMappingFile") && !it.name.contains("Mock", ignoreCase = true)
}.configureEach {
    enabled = project.findProperty("skipCrashlyticsMapping") != "true"
}
