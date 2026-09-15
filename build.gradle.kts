plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinCocoapods) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
}

/**
 * La suite ENTERA, en un solo comando. [TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001]
 *
 * ⛔ `:shared:testDebugUnitTest` no es la suite: deja fuera los tests de `:app`, y ahí vive el
 * único guardarraíl que comprueba que el grafo del flavor mock se puede resolver
 * (`MockKoinModuleVerifyTest`). El agujero se midió el 15-09: DOS tickets seguidos añadieron un
 * repositorio a `dataModule` sin declararlo en `mockModule`, el guardarraíl los habría cazado en
 * segundos, y nadie lo ejecutó — porque el comando que todo el mundo escribe no lo incluye.
 *
 * Arreglar solo los dos bindings habría dejado intacta la causa. Esto es la causa: un comando que
 * no se puede escribir a medias.
 */
tasks.register("testAll") {
    group = "verification"
    description = "Los tests de :shared Y los de :app (incluido el verify del grafo mock)."
    dependsOn(":shared:testDebugUnitTest", ":app:testMockDebugUnitTest")
}
