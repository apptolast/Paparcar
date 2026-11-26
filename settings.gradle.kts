rootProject.name = "Paparcar"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ⛔ Excepción documentada, no precedente: el SDK de TikTok Business SOLO se publica aquí
        // (confirmado contra el build.gradle del propio repo de TikTok, 25-11-2026) — a diferencia
        // de BaseLogin, que SÍ migró fuera de JitPack. Un solo consumidor:
        // libs.tiktok.business.sdk. [MKT-TIKTOK-APP-EVENTS-SDK-001]
        maven { url = uri("https://jitpack.io") }
    }
}

include(":app")
include(":shared")
// Fakes and mock graphs. Deliberately NOT part of :shared — see mockkit/build.gradle.kts.
// [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
include(":mockkit")
