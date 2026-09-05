# TEST-A-KMP-SUITE-THAT-ONLY-RUNS-ON-JVM-IS-HALF-A-SUITE-001 · `commonTest` no compila para iOS

**Estado:** ✅ Done (05-09-2026) · mergeado a master con squash · **2.126 tests, 0 fallos, en el
simulador iOS** (PR #4) · descubierto 31-08-2026 al migrar BaseLogin a Maven Central.

**Aplicado (05-09), re-barrido sobre master `f1371096`** — la medición del 31-08 había derivado:
**18** nombres ilegales (4 nuevos aparecieron, otros se fueron), **5** relojes, y solo **2**
`assert(` (los 4 de `FakeUserParkingRepository` ya no existen). Los 25 sitios arreglados:
renombres mínimos (fuera `,`/`()`, prosa intacta), `Clock.System.now().toEpochMilliseconds()`
(+ import en `RevertParkingUseCaseTest`), `assertTrue` de kotlin.test. Guardarraíl añadido:
paso `:shared:iosSimulatorArm64Test` en el job `apple` (+ upload de resultados) — los tests
**corren** en simulador, no solo compilan. JVM verificado verde en Windows.
✅ **VEREDICTO (05-09, PR #4 run `33966477425`): 2.126 tests, 0 fallos, 0 ignorados, en el
simulador iOS.** Costó 6 iteraciones de CI, cada capa escondiendo la siguiente:
1. Frontend: los renombres/relojes/asserts previstos… menos 2 nombres que el script olvidó.
2. Los «4 asserts» medidos en `FakeUserParkingRepository` **no eran asserts**: eran
   `sessions.replaceAll { }` — método de `java.util.List` que la JVM cuela y Native resuelve
   contra una API experimental. Sustituido por un `mapInPlace` propio.
3. Linker: `ld: framework 'FirebaseCore' not found` — los klibs de GitLive incrustan
   `-framework FirebaseCore/Auth/Firestore` y el binario de test de Gradle no tiene el search
   path de SPM. `dynamic_lookup` NO basta: es error de búsqueda, no de símbolos.
4. El asset `Firebase.zip` de la release 11.8.0 (la que pina GitLive 2.6.0) es un **wrapper
   firmado**; el payload real va anidado en `11_8_0/Firebase-11.8.0-latest.zip` (STORED).
   CI extrae solo los slices `ios-arm64_x86_64-simulator`, con caché.
5. dyld: `_OBJC_CLASS_$_FIRHeartbeatController` — las referencias de clase ObjC se atan AL
   CARGAR, `dynamic_lookup` no las aplaza. Gradle ahora NOMBRA cada framework del set además
   del `-F`; con archivos estáticos, sobre-enlazar es seguro.
6. Verde: 2.126/2.126.
Solo el binario de TEST se relaja; el framework de la app sigue estricto.

## Problema

Paparcar es KMP con iOS como target, y **los 2044 tests de `commonTest` no han corrido nunca en
iOS**. Ni siquiera compilan: `:shared:compileTestKotlinIosSimulatorArm64` falla.

Eso significa que todo lo que `commonTest` protege — evaluadores de detección, use cases, mappers,
ViewModels, el replay de las 16 trazas de campo — está verificado **solo en el runtime de la JVM**.
Un evaluador que se comporte distinto en Kotlin/Native (aritmética de flotantes, orden de
inicialización, congelación de objetos, `Clock`) pasaría el CI verde y fallaría en el iPhone.
`commonTest` existe precisamente para no depender de la plataforma; hoy depende de una.

`CLAUDE.md` dice que la suite corre con `:shared:testDebugUnitTest`, y eso es cierto como
descripción de lo que hay — pero no es un diseño deliberado, es la consecuencia de que nadie
compilara el target. Es el mismo agujero que
[CI-IOS-COMPILES-ON-A-MAC-NOT-ON-A-PROMISE-001] tapó para `iosMain`, sin tapar para `commonTest`.

## Medido: 29 errores en 12 ficheros

`./gradlew :shared:compileTestKotlinIosSimulatorArm64 --rerun-tasks` (31-08-2026).

⚠️ **Corrección de una estimación previa**: esto NO es "un proyecto sobre 191 ficheros de test".
Son 12 ficheros y los arreglos son mecánicos. Se dijo lo contrario antes de medirlo.

### (a) 18 × nombres de test ilegales en Native

Kotlin/Native no admite `,` ni `()` en un identificador, ni entre backticks. Los nombres con
backtick que los llevan son ilegales como símbolo.

| Carácter | Sitios |
|---|---|
| `","` | 10 |
| `"()"` | 7 |
| `",()"` | 1 |

En: `DrivingRouteTest`, `ParkingStrategyResolverTest`, `TrailMapMatcherTest`,
`ObserveDetectionReadinessUseCaseTest`, `CalculateParkingConfidenceUseCaseTest`,
`ConfirmParkingUseCaseTest`, `GeoUtilsTest`, `SplashViewModelTest`, `ParkedWatchBadgeTest`.

Arreglo: renombrar quitando los caracteres. ⚠️ Ojo con el naming del proyecto
(`should_expectedBehavior_when_condition`) — varios de estos ya se lo saltan usando prosa con
backticks, así que el rename es también la ocasión de alinearlos.

### (b) 5 × `System.currentTimeMillis()` — JVM-only

`ConfirmParkingUseCaseTest.kt:422,432,453` y `RevertParkingUseCaseTest.kt:29,101`.

🔑 **El propio `ConfirmParkingUseCaseTest` ya usa la forma correcta en otras 8 líneas**
(`Clock.System.now().toEpochMilliseconds()`, líneas 671, 690, 715, 742, 761, 777, 810). No hay que
decidir nada: es una inconsistencia dentro de un mismo fichero.

### (c) 6 × `assert(...)` — necesita `@ExperimentalNativeApi`

`ObserveAdaptiveLocationUseCaseTest.kt:94,123` y `FakeUserParkingRepository.kt:43,132,141,158`.

`kotlin.assert` en Native pide opt-in (y además **se compila fuera en release**, así que como
aserción de test es la herramienta equivocada). Arreglo: `assertTrue(...)` de `kotlin.test`, que es
lo que usa el resto de la suite. No añadir `@OptIn`.

## Alcance real

1. Los tres arreglos de arriba.
2. **Volver a compilar**: el frontend reporta lo que ve en esta pasada; al pasar la barrera pueden
   aflorar errores de fases posteriores. Iterar hasta verde, no dar por buena la lista.
3. Hacer que **corran**, no solo que compilen: `:shared:iosSimulatorArm64Test` en el runner de
   macOS. Aquí es donde pueden aparecer fallos REALES de comportamiento en Native — que es el
   objetivo del ticket, no un efecto colateral.
4. **Guardarraíl**: añadir el compile+test de iOS al job `apple` de `.github/workflows/ci.yml`. Sin
   eso vuelve a derivar en cuanto alguien escriba un nombre con coma, exactamente como pasó aquí.

## Por qué nadie lo vio

Con BaseLogin en JitPack, la metadata de iOS **no resolvía**
(`Could not find custom-login-iosarm64-1.1.0.jar`), así que el camino de análisis de iOS estaba roto
en la raíz y el IDE no podía señalar nada. Al pasar a Maven Central resuelve, y esto salió.
