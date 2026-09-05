# TEST-A-KMP-SUITE-THAT-ONLY-RUNS-ON-JVM-IS-HALF-A-SUITE-001 · `commonTest` no compila para iOS

**Estado:** 🔵 En progreso (05-09-2026) · rama
`bugfix/TEST-A-KMP-SUITE-THAT-ONLY-RUNS-ON-JVM-IS-HALF-A-SUITE-001-native-test-compile` ·
worktree `../Paparcar-ios-tests` · descubierto 31-08-2026 al migrar BaseLogin a Maven Central.

**Aplicado (05-09), re-barrido sobre master `f1371096`** — la medición del 31-08 había derivado:
**18** nombres ilegales (4 nuevos aparecieron, otros se fueron), **5** relojes, y solo **2**
`assert(` (los 4 de `FakeUserParkingRepository` ya no existen). Los 25 sitios arreglados:
renombres mínimos (fuera `,`/`()`, prosa intacta), `Clock.System.now().toEpochMilliseconds()`
(+ import en `RevertParkingUseCaseTest`), `assertTrue` de kotlin.test. Guardarraíl añadido:
paso `:shared:iosSimulatorArm64Test` en el job `apple` (+ upload de resultados) — los tests
**corren** en simulador, no solo compilan. JVM verificado verde en Windows.
⚠️ **Pendiente el veredicto real**: K/N no compila en Windows — lo decide el job `apple` del PR
de esta rama, iterando si el frontend destapa errores de fases posteriores (alcance §2).

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
