# DET-WATCH-RESUME-RACE-001 · El resumidor vuelve a preguntar lo que el stream ya decidió, y pierde la carrera

**Estado:** ✅ Done · master `96f948e9` (ff-only, 2026-08-16) · rama y worktree eliminados · sin pushear

> **Rebase (16-08):** el commit `e6953387` se replicó como `96f948e9` sobre master. Conflicto único en
> `docs/detection/PARKING-DETECTION.md`: los dos lados añadían entradas al final del log cronológico
> (ambas se conservan, sin el `---` que no es la convención entre entradas). Master avanzó **durante**
> el rebase — otra sesión mergeó DET-UNVERIFIED-ARM-DRIVE-PROOF-001 (`e9186a52`) — así que la base
> real fue esa, no `150907a4`. Verificado ya rebasado: 1190 tests, 0 fallos.

## Problema

Field 2026-08-16, Oppo, APK de master (`71148bcd`), arranque en frío con el móvil bloqueado:

```
02:42:28.224 D DepartureWatch: resume(foreground-gap) declined — parked=false strategy=COORDINATOR
```

Nadie llama a `resume(foreground-gap)` salvo el lane de `MainActivity`, y ese lane solo dispara
cuando `ObserveDepartureWatchGapUseCase` ha emitido `true` — es decir, cuando el stream YA vio una
sesión aparcada. Cien milisegundos después, la relectura del propio resumidor dijo que no había
ningún coche aparcado. **Las dos puertas se contradicen sobre el mismo dato.**

En el segundo arranque, ya desbloqueado, la misma app dijo `→ RESUME_SENTRY dispatched` y el
vigilante subió (`ServiceRecord … isForeground=true, channel=sentry_channel`). O sea: el fix de
[[det-watch-reactivate-001]] funciona, pero **tiene una carrera que lo deja mudo un arranque de cada
tantos**.

### Causa 1 — la puerta se re-deriva con un `first()` sobre Room

`DepartureWatchResumerImpl` recibía `UserParkingRepository` + `ParkingStrategyResolver` +
`AppPreferences` y volvía a montar el veredicto:

```kotlin
val hasParkedSession = runCatching {
    userParkingRepository.observeActiveSessions().first().isNotEmpty()
}.getOrDefault(false)
```

Es exactamente el patrón que DET-WATCH-REACTIVATE-001 vino a erradicar de `PaparcarApp.onCreate`, y
lo dejé colado en el resumidor. Un `first()` sobre una fuente que aún no ha entregado su primer
valor real no espera al dato: se queda con lo que haya.

### Causa 2 — al declinar, nadie reintenta

El gap es un `Flow<Boolean>` con `distinctUntilChanged`. Si el valor sigue siendo `true`, no hay
emisión nueva: el lane de `MainActivity` no vuelve a llamar a `resume` hasta el siguiente
`repeatOnLifecycle(STARTED)`. Un `declined` por carrera deja el vigilante muerto **toda la sesión de
app**, aunque la sesión aparcada esté ahí desde el primer segundo.

### Causa 3 — el log miente por omisión

`runCatching { … }.getOrDefault(false)` escribe `parked=false` tanto si de verdad no hay coche
aparcado como si la lectura falló. Diagnosticar desde campo con esa línea es imposible: dice
"no había nada que vigilar" cuando quizá quiso decir "no pude mirar".

## Doctrina violada

- **Sistemas, no parches / el invariante en UN sitio.** "El vigilante debería estar vivo" tenía dos
  implementaciones simultáneas leyendo las mismas fuentes con timings distintos. Dos relojes nunca
  dan la misma hora.
- **Todo trigger dispara SIEMPRE.** Un hueco detectado que se descarta en silencio, sin reintento y
  sin rastro, es justo el trigger perdido que el contrato prohíbe.

## Señales / datos disponibles

- `ObserveDepartureWatchGapUseCase` — el veredicto ya calculado, con `combine`, que por definición
  **espera a que las cuatro fuentes tengan valor** antes de emitir. Es la puerta buena.
- `resolvePostDetectionLifecycle` — la regla pura compartida con el epílogo del servicio.
- El epílogo del propio servicio: `ACTION_RESUME_SENTRY` no trae trabajo, así que si el arranque
  llega sin motivo, el servicio se para solo. La puerta del resumidor es una cortesía, no la última
  palabra.

## Diseño

**Una sola puerta, la del stream.** El resumidor deja de recibir repositorios y pasa a depender de
`ObserveDepartureWatchGapUseCase`:

- `ObserveDepartureWatchGapUseCase.current()` — `suspend`, devuelve la primera lectura REAL del gap.
  Al ser el `combine` de siempre, suspende hasta que sesiones, vehículos, preferencia y presencia
  tienen valor; no existe el "por defecto, false".
- Techo de espera (`GATE_READ_TIMEOUT_MS`) para que un `combine` que nunca completa no cuelgue ni al
  CTA ni al lane. Si expira:
  - **automático** → declina (fallo asimétrico: ante la duda, no encender),
  - **explícito (`force`)** → dispara igual y deja que el epílogo del servicio decida. Un tap del
    user siempre hace algo, y lo peor que puede pasar es un servicio que se para solo.
- El log distingue los tres finales: `no gap` · `could not read the watch state` · `refused by the OS`.

Con la puerta única, la carrera desaparece por construcción: el resumidor no puede discrepar del
stream porque **es** el stream.

## Criterio de éxito

- Arranque en frío con coche Coordinator aparcado → `RESUME_SENTRY dispatched`, nunca
  `declined — parked=false`, esté la pantalla bloqueada o no.
- Sin coche aparcado → sigue declinando (no se enciende un FGS sin propósito).
- Una lectura fallida se ve como tal en el log, no disfrazada de "no hay coche".
- Tests: `current()` espera al dato tardío; declina sin sesión; declina con el vigilante ya vivo.

## Consumidores auditados

`grep -rn "DepartureWatchResumer\|ObserveDepartureWatchGapUseCase\|resolvePostDetectionLifecycle"`:

| Sitio | Estado |
|---|---|
| `DepartureWatchResumerImpl` (Android) | ✅ pasa a depender del gap use case; se le quitan repos, resolver y prefs |
| `IosDepartureWatchResumerImpl` | ✅ exento: no-op, no hay servicio residente en iOS |
| `MainActivity` lane (`foreground-gap`) | ✅ sin cambios: ya consumía el stream correcto |
| `HomeViewModel` (`home-cta`, `force = true`) | ✅ sin cambios de firma; gana el camino de timeout que siempre dispara |
| `CoordinatorDetectionService` epílogo | ✅ exento: sigue siendo la última palabra vía `resolvePostDetectionLifecycle` |
| `ParkingSafetyNetWorker` | ✅ exento: no arranca FGS desde background, cubre por otra vía |
| `AndroidDetectionModule` / `IosDetectionModule` / `MockModule` | ✅ DI actualizada al nuevo constructor |
| `detectionPath` / `armEvidence` | ✅ exento: reconstruir un VIGILANTE no confirma plazas, no hay camino nuevo |
| Dev Catalog / galería | ✅ exento: sin pantalla, estado ni routing nuevos |
| Strings | ✅ exento: ningún texto nuevo al user |

## Estado de verificación

- ✅ `compileProdDebugKotlinAndroid`, `compileMockDebugKotlinAndroid`, `assembleMockDebug`
- ✅ `testProdDebugUnitTest`: **1177 tests, 0 fallos** (5 nuevos sobre `current()`) · **1190 tras el
  rebase**, contando los que trae DET-UNVERIFIED-ARM-DRIVE-PROOF-001
- ✅ `testMockDebugUnitTest --tests "…domain.coordinator.*"`
- ✅ `docs/detection/PARKING-DETECTION.md` con su entrada en el log cronológico
- ✅ **Device, 16-08 (Oppo + Redmi, APK `96f948e9`, hash verificado en ambos):**

  | Caso | Resultado |
  |---|---|
  | Oppo, arranque en frío con 2 sesiones activas | `resume(foreground-gap) → RESUME_SENTRY dispatched` a los 60 ms, `enterSentry`, `isForeground=true channel=sentry_channel` |
  | Oppo, servicio muerto con la app en primer plano | resucitado en **58 ms** (`onDestroy` 19:23:24.354 → `dispatched` .412) — el lane reactivo cerrando el hueco solo |
  | Oppo, segunda muerte dentro del minuto | `skipped — automatic retry cooling down` (el backstop, no la carrera) |
  | Redmi, `active sessions=0` | ninguna línea y ningún FGS: sin hueco el lane ni pregunta |

  Cero apariciones de `declined — parked=false`. **No reproducible por adb:** el caso con la pantalla
  BLOQUEADA — el Oppo pide credencial, y con keyguard `MainActivity` no llega a `STARTED`, así que el
  lane (`repeatOnLifecycle(STARTED)`) no corre por diseño. Lo que sí queda probado es el instante en
  que ocurría la carrera: el primer `STARTED` tras arranque en frío. La variante con Room vacía y la
  sesión llegando por sync no se puede forzar sin borrar datos → la cubre
  `should_seeTheSession_when_readAfterTheSyncLands`.
- ⚠️ Sin ver en device: `declined — no watch gap to close`. Solo se alcanza tocando el CTA sin nada
  aparcado, y sin sesión la fila no se pinta. Cubierto por test.

> Nota sobre los tests: que `current()` **espere** al primer valor real es semántica de `combine`, no
> código nuestro; por eso los tests cubren el invariante que sí es nuestro — que `current()` y el
> stream contesten siempre lo mismo, incluida la sesión que llega tarde.
