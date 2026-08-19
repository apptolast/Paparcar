# BUILD-KMPMAPS-CENTRAL-DEPENDENCY-001 · el fork de kmp-maps se consume desde Maven Central, no desde un binario dentro del repo

**Estado:** ✅ Done · master `6db9bc7a` (ff-only 2026-08-19; rama y worktree borrados) ·
⏳ pendiente de validar conduciendo

## Problema

El fork de kmp-maps con el marcador de id estable no existía en ningún repositorio público, así que
para que el proyecto compilara en cualquier máquina se metieron **37 ficheros y 11 MB de binarios
dentro del repo de la app** (`third_party/maven/`), con un repositorio Maven local declarado en
`settings.gradle.kts` [BUILD-KMPMAPS-VENDORED-MAVEN-001]. Era la solución correcta entonces —al
compañero no le compilaba— pero deja el repo de una app cargando artefactos binarios, algo que ni se
revisa en un diff ni se actualiza sin repetir el ritual de publicar a mano.

El 2026-08-19 el fork se publicó en Maven Central como
`io.github.rndevelo.kmpmaps:core:0.9.1-puck4` (firmado, con fuentes y javadoc, seis variantes
incluidos los tres klibs de iOS). Desde ese momento el vendorizado es deuda pura.

## Doctrina violada

Ninguna doctrina de detección. Es higiene de build: **un repo de aplicación no aloja los binarios de
sus dependencias**. Mientras existió una única forma de que el equipo compilara, el vendorizado era
la opción honesta; publicada la librería, mantenerlo sería duplicar una dependencia que ya sabe
resolver Gradle.

## Señales / datos disponibles

- `io.github.rndevelo.kmpmaps:core:0.9.1-puck4` publicado, despliegue
  `0b4ae304-d4fc-4c89-9128-0c1bde07c951`, `VALIDATED` sin errores en los 6 módulos.
- Fuente exacta: `rndevelo/kmp-maps`, commit `d12abc5`, tag `v0.9.1-puck4`.
- **La versión no cambia.** `0.9.1-puck4` es el mismo binario ya probado en el Oppo y el Redmi, así
  que esta tarea no puede introducir un cambio de comportamiento: si el pin del coche se mueve
  igual, está bien migrada.

## Diseño

Cambia únicamente **de dónde** sale el artefacto, no cuál es:

1. `gradle/libs.versions.toml` → `module = "io.github.rndevelo.kmpmaps:core"` (versión intacta).
2. `settings.gradle.kts` → fuera el bloque `maven { url = third_party/maven }` con su content
   filter; `mavenCentral()` ya estaba declarado y basta.
3. Borrar `third_party/` entero.
4. `.gitignore` → fuera la excepción `!third_party/maven/**`; `.gitattributes` → fuera
   `third_party/maven/** -text`. Ambas existían solo para que los binarios sobrevivieran a los
   patrones globales y no sufrieran conversión de saltos de línea.

**El código Kotlin no se toca.** El paquete dentro de la librería sigue siendo
`com.swmansion.kmpmaps.core`: lo que cambia son las coordenadas Maven, no el namespace del código.
Los 22 imports de `PaparcarMapView.kt` y compañía quedan igual.

## Criterio de éxito

- `:composeApp:dependencies` resuelve `io.github.rndevelo.kmpmaps:core:0.9.1-puck4` desde
  `repo1.maven.org`, con `third_party/` ya borrado y sin `mavenLocal()` en juego.
- Compilan `mockDebug` y `prodDebug`; la suite de tests sigue verde.
- El repo pierde 11 MB de binarios.
- ⏳ Campo: el puck del coche se desliza igual que antes (misma versión, no debería haber diferencia
  observable — si la hay, es que se resolvió otro artefacto).

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `gradle/libs.versions.toml:69-72,182-184` | ✅ coordenadas a `io.github.rndevelo.kmpmaps:core` + comentario |
| `settings.gradle.kts:17-26` | ✅ bloque del repo vendorizado eliminado |
| `.gitignore:761-762` | ✅ excepción `!third_party/maven/**` eliminada |
| `.gitattributes` | ✅ borrado entero (su única línea era la del vendorizado) |
| `third_party/**` | ✅ borrado — 38 ficheros, 11 MB |
| `composeApp/src/**/*.kt` (22 imports `com.swmansion.kmpmaps.core.*`) | ✅ exento — el paquete del código no cambia |
| `docs/backlog/build-kmpmaps-vendored-maven-001.md` | ✅ marcado como superado por este ticket |

## Ejecución (2026-08-19)

Central tardó **11 minutos** en propagar tras el *Publish* (21:08:54 el POM devolvió 200 en
`repo1.maven.org`; antes, 404).

- `:composeApp:dependencies --configuration prodDebugRuntimeClasspath` resuelve
  `io.github.rndevelo.kmpmaps:core:0.9.1-puck4 → core-android:0.9.1-puck4`, descargado a la caché de
  Gradle. Con `third_party/` borrado y sin `mavenLocal()`, el único repositorio declarado capaz de
  servirlo es `mavenCentral()`.
- `compileMockDebugKotlinAndroid` + `compileProdDebugKotlinAndroid`: **BUILD SUCCESSFUL** (solo
  warnings preexistentes de deprecación, ajenos a este cambio).
- `testProdDebugUnitTest`: **1236 tests, 0 failures, 0 errors**.

⏳ **Pendiente de campo**: es la misma versión que ya estaba en los dos móviles, así que no debería
haber diferencia observable. Si el puck del coche se comportara distinto, significaría que se está
resolviendo otro artefacto — y eso sí sería un bug de esta tarea.
