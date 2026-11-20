import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ─────────────────────────────────────────────────────────────────────────────
// PAPARCAR — mockkit/build.gradle.kts
// Every fake, the mock Koin graphs and the mock entry points.
//
// ⛔ It is a module of its own for ONE reason: whatever lives in :shared reaches production.
// Measured 18-09 on the Release framework of TestFlight build 2 — 161 exported `Fake*`
// Objective-C classes plus MockScenario. The cause was not the tests: `MockMainViewController`
// and `iosMockModule` sat in `shared/src/iosMain`, and Kotlin/Native exports the module's public
// API to ComposeApp.h. An exported symbol is a DCE ROOT, so neither dead-code elimination nor
// LTO could drop them, and they dragged all 19 fake files of commonMain behind them. Nothing in
// Swift ever called it: dead code that shipped anyway. That is guideline 2.3.1 (hidden
// functionality) and it is what got a sibling app rejected.
//
// The direction of the arrow IS the design: :mockkit → :shared, never back. Production code
// cannot import a fake because it cannot see this module — no flag to remember, no rule to obey.
//   · Android: only the `mock` flavor consumes it (`mockImplementation`), so prod never compiles it.
//   · iOS:     :shared links it into the framework ONLY with -Ppaparcar.screenshots=true.
//   · Tests:   :shared's test source sets consume it; test binaries never ship.
// [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
// ─────────────────────────────────────────────────────────────────────────────

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }

    // ⛔ Its own framework, named ComposeApp like :shared's, and that is the whole trick.
    //
    // The obvious wiring — :shared links :mockkit when a property is set — is IMPOSSIBLE, and
    // Gradle says so out loud: `Circular dependency between :mockkit:compileKotlinIosSimulatorArm64
    // and :shared:compileKotlinIosSimulatorArm64`. :mockkit already depends on :shared, so :shared
    // cannot depend back, not even behind a flag. (Measured 18-09 with --dry-run, before building
    // anything.) That refusal is a feature: it means no property, no typo and no hurried merge can
    // wire the mock graph into the shipped framework. The compiler is the guardrail.
    //
    // So the screenshot build links a DIFFERENT framework instead: this one. Same module name, so
    // `import ComposeApp` in Swift is untouched; it carries :shared whole (a static framework
    // embeds its dependencies) plus the mock graph. Which one Xcode builds is decided by
    // PAPARCAR_FRAMEWORK_PROJECT in the Run Script phase, and only the `screenshots` lane sets it.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export(projects.shared)
            export(libs.baselogin)
        }
    }

    sourceSets {
        // api on purpose, same reasoning as :shared's own block: the mock graphs hand :app and the
        // screenshot build types that come straight from :shared.
        commonMain.dependencies {
            api(project(":shared"))
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "com.rndeveloper.paparcar.mockkit"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
