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
        // ⚠️ TEMPORARY — kmp-maps 0.9.1-puck6 (rebuilt on CMP 1.12) exists only in mavenLocal until
        // it is released to Maven Central. Scoped to the fork's group so nothing else resolves from
        // here. Remove before merging. [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001]
        mavenLocal {
            content { includeGroup("io.github.rndevelo.kmpmaps") }
        }
    }
}

include(":app")
include(":shared")
