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
        // Vendored publish of the kmp-maps fork with the stable-id animated marker
        // (com.swmansion.kmpmaps:core:0.9.1-puck4), so the project builds on any machine
        // without a local publish. Source: rndevelo/kmp-maps branch paparcar/puck, upstream
        // PR #170. [DRIVE-PUCK-NATIVE-001][BUILD-KMPMAPS-VENDORED-MAVEN-001]
        maven {
            url = uri(rootDir.resolve("third_party/maven"))
            content { includeGroup("com.swmansion.kmpmaps") }
        }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

include(":composeApp")
