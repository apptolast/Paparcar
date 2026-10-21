# DEPS-REVENUECAT-3-10-AND-A-FULL-SWEEP-001 · subir purchases-kmp + barrido completo de dependencias

**Estado:** ✅ Done · rama `chore/DEPS-REVENUECAT-3-10-AND-A-FULL-SWEEP-001` ·
worktree `../Paparcar-deps-sweep`

## Problema
Play Console reportó un nuevo "SDK issue" sobre `com.revenuecat.purchases:purchases-kmp-core:3.8.0`
(versión publicada en el build 16 / 1.5.0): 3.8.0 y 3.9.0 tienen un bug que puede desloguear a
usuarios que actualizan desde una versión anterior a 3.0.0 — tendrían que `restore` sus compras.
Arreglado en 3.10.0+. El user pidió además aprovechar para barrer el resto del catálogo.

## Doctrina violada
Ninguna de detección. Aplica la cabecera de "Stack" de CLAUDE.md: "Solo estables en el catálogo" y
la fuente de verdad de versiones (`repo1.maven.org` / `dl.google.com`, nunca `search.maven.org`).

## Señales / datos disponibles
`gradle/libs.versions.toml` no se re-verificaba desde `BUILD-DEPS-AT-LATEST-STABLE-002` (15-09).
Barrido completo contra `maven-metadata.xml` de Maven Central y de `dl.google.com` (no
search.maven.org) el 2026-10-21.

## Diseño
Bump directo de versión en el catálogo — no hay invariante de sistema que mover, es actualización
de dependencias. Verificado antes de tocar nada:
- **`purchases-kmp` 3.8.0 → 3.14.0** (última estable, por encima del mínimo 3.10.0 que pide Play).
  CHANGELOG de `RevenueCat/purchases-kmp` sin breaking changes en la superficie que usa Paparcar
  (`Purchases`, `PurchasesDelegate`, `awaitCustomerInfo/Offerings/Purchase/Restore/LogIn/LogOut`,
  `CustomerInfo`, `Offerings`, `Package`, `PurchasesError`, `StoreProduct`, `StoreTransaction` —
  únicos tipos usados, en `RevenueCatDataSourceImpl.kt`). El fix del bug de logout es específico de
  iOS (recuperación del `appUserID` 2.x tras el salto a 3.x); Paparcar no publica iOS aún, pero el
  bump es el que pide el aviso de Play para el lado Android igualmente.
  POM de `purchases-kmp-core-android:3.14.0` confirma que sigue pinando el artefacto Android nativo
  por encima de 10.20.0 (ahora 10.27.0) → el puente de
  [R8-REVENUECAT-ALREADY-NARROWED-ITS-RULES-001] sigue sin resucitar.
- **`firebase-bom` 34.19.0 → 35.0.2** (único bump de MAJOR del barrido; no está en Maven Central,
  solo en `dl.google.com`). Release notes revisadas: sube `minSdkVersion` de todas las libs
  Firebase a 24 (sin efecto — Paparcar ya pide 26) y retira `firebase-ml-modeldownloader` del BoM
  (no lo usamos). Nada que toque Crashlytics, Messaging, ni lo que GitLive envuelve.
- **Kotlin 2.4.20 → 2.4.21**, **AGP 9.4.0 → 9.4.1** — patches.
- **Compose Multiplatform 1.12.0 → 1.12.2** — las 7 claves del catálogo que comparten esta línea
  (`compose-plugin`, `componentsResources`, `foundation`, `runtime`, `ui`, `uiToolingPreview`,
  `material` JB) se mueven juntas, como siempre.
- **`kotlinx-serialization` 1.11.0 → 1.12.0**, **`aboutlibraries` 15.2.0 → 15.3.0**,
  **`coil` 3.6.2 → 3.6.3**, **`ktor` 3.5.2 → 3.6.0**, **`work-runtime` 2.11.2 → 2.12.0**,
  **`androidx-core-ktx` 1.19.0 → 1.19.1**, **`konsist` 0.17.3 → 0.18.1** — todos patch/minor
  estables, sin release notes con breaking changes relevantes.
- **Ya en su última estable, sin cambio**: `kotlinx-coroutines` 1.11.0, `kotlinx-datetime` 0.8.0
  (⚠️ la línea `0.8.0-0.6.x-compat` NO es un bump pendiente, ver nota del catálogo), `koin` 4.2.2,
  `room` 2.8.5, `sqlite` 2.7.1, `google-services` 4.5.0, `napier` 2.7.1, `baselogin` 3.0.2 (⛔ no se
  toca desde Paparcar), `play-services-location` 21.4.0, `glance` 1.2.0, `androidx-lifecycle`
  2.11.0, `navigation-compose` 2.9.2, `material3` (JB) 1.9.0, `materialIconsExtended` 1.7.3,
  `androidx-activityCompose` 1.13.0, `androidx-appcompat` 1.8.0, `androidx-material` 1.14.0,
  `androidx-datastore` 1.2.1, `androidx-splashscreen` 1.2.0, `junit` 4.13.2, `androidx-test-junit`
  1.3.0, `androidx-espresso-core` 3.7.0, `turbine` 1.2.1, `robolectric` 4.17, `androidx-test-core`
  1.7.0, `ksp` 2.3.12, `firebase-crashlytics-plugin` 3.0.8, `firebase-gitlive` 2.7.0. `kmp-maps`
  fuera de alcance (fork propio, no sale de ningún índice).
- CLAUDE.md § Stack actualizado con las versiones nuevas y la nota de re-verificación (como hizo
  BUILD-DEPS-AT-LATEST-STABLE-002 la vez anterior).

## Criterio de éxito
- `./gradlew testAll --console=plain --rerun-tasks` en verde.
- `./gradlew :app:compileMockDebugKotlin :app:compileProdDebugKotlin --console=plain` sin errores.
- Ningún uso de API de RevenueCat ni de Firebase roto por los bumps (revisado por lectura; sin
  `when` exhaustivo sobre sealed de ninguna de las dos libs).

## Consumidores auditados
- `grep -rln "com.revenuecat" shared/src` → 3 ficheros, todos ya revisados contra el CHANGELOG.
- `grep -rln "purchases" --include="*.gradle.kts" .` → solo `purchases-core` (no hay `purchases-ui`
  declarado, el paywall de Paparcar no usa plantilla remota de RevenueCat).
- Firebase: `firebase-bom` solo acota versión de `firebase-crashlytics` / `firebase-messaging`
  nativos; GitLive (`firebase-gitlive`) lleva sus propias versiones internas de los SDKs nativos,
  sin conflicto declarado por Gradle en la resolución (confirmado en el build, ver ejecución de
  `testAll` más abajo).
