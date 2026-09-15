# DET-A-CANCELLED-TICK-IS-NOT-A-FAILED-READ-001 · Una cancelación rutinaria del safety net se reportaba a Crashlytics como fallo

**Estado:** ✅ Done — `:shared:testDebugUnitTest` en verde (3 tests nuevos). Crashlytics
`d677c91f7b5a867cdcbbcd5c7d26f844` queda por cerrar hasta que este commit llegue a un release
publicado (mismo criterio aplicado a `8c03bc66`/mapa: se cierra cuando el arreglo ya está en
producción, no antes).

## Problema

`kotlinx.coroutines.JobCancellationException` es la issue MÁS FRECUENTE de Crashlytics en los
últimos 90 días: 104 eventos, 19 usuarios, presente desde `1.0.0-beta02` hasta `1.1.0`
(`d677c91f7b5a867cdcbbcd5c7d26f844`). El evento muestreado trae el log
`PARKDIAG/SafetyNet: ✗ failed to read active sessions` — una lectura de sesión activa que en
realidad no falló, se CANCELÓ.

## Causa raíz

`ParkingSafetyNetWorker.doWork()` (línea 152) envuelve la lectura de sesiones activas en
`runCatching`:

```kotlin
val sessions = runCatching { userParkingRepository.observeActiveSessions().firstOrNull().orEmpty() }
    .getOrElse {
        PaparcarLogger.e(DIAG, "✗ failed to read active sessions", it)
        return Result.success()
    }
```

`runCatching` atrapa **cualquier** `Throwable`, incluida `CancellationException` — el tipo con el
que Kotlin implementa la cancelación cooperativa de corrutinas. Y hay una vía rutinaria para que
llegue exactamente aquí: `enqueueCheckNow` (línea ~1313) encola con
`ExistingWorkPolicy.REPLACE`, así que cuando el sensor de movimiento significativo dispara un
check-now mientras el tick periódico todavía está en vuelo, WorkManager cancela la corrutina del
tick viejo. Eso no es un fallo — es exactamente lo que `REPLACE` promete.

`PaparcarLogger.e()` reenvía CUALQUIER `throwable` que reciba a
`CrashReporter.recordNonFatal()` → `FirebaseCrashlytics.recordException()`
(`PaparcarLogger.kt:51-54`). Con 71 call sites de `PaparcarLogger.e` en 35 ficheros, este no es un
problema exclusivo de `ParkingSafetyNetWorker`: es el logger el que no sabe distinguir "esto
falló" de "esto se canceló porque algo más reciente lo reemplazó".

## Doctrina violada

Ninguna de detección específicamente, pero sí una de corrutinas: capturar
`CancellationException` sin relanzarla rompe la concurrencia estructurada (Kotlin coroutines
best practice — ver `CancellationException` docs de kotlinx.coroutines). Y por el principio de
"sistemas, no parches" del proyecto: el invariante "una cancelación no es un fallo reportable" no
vive en un único sitio hoy, y con 71 call sites de `PaparcarLogger.e` no es practicable auditarlos
uno a uno cada vez que se añada uno nuevo.

## Diseño

Dos arreglos, cada uno en su sitio:

1. **El invariante general, en UN sitio.** `PaparcarLogger.e()` deja de reenviar una
   `CancellationException` (o cualquier subtipo, incluida `JobCancellationException`) a
   Crashlytics — sigue logueándola vía Napier (para que el parkdiag de campo la conserve si hace
   falta rastrear una cancelación), pero no la cuenta como no-fatal. Se extrae como predicado puro
   `isReportableToCrashlytics(Throwable): Boolean` en `commonMain` — testeable sin tocar
   `CrashReporter` (que es `expect object`, no falseable en `commonTest`).
2. **El sitio concreto que disparó el diagnóstico.** `ParkingSafetyNetWorker.doWork()` deja de
   tratar una cancelación como "fallo de lectura": la relanza en vez de convertirla en
   `Result.success()` con un log de error. El resto del comportamiento fail-open (una lectura que
   SÍ falla de verdad sigue devolviendo `Result.success()` para no clasificar una valla viva como
   huérfana) no cambia.

⛔ No se ha barrido el resto de los 71 call sites de `PaparcarLogger.e()` uno a uno: el arreglo del
punto 1 cierra el invariante para todos ellos (presentes y futuros) sin tocarlos.

## Criterio de éxito

- `isReportableToCrashlytics` devuelve `false` para `CancellationException` y sus subtipos
  (incluida `JobCancellationException`), `true` para cualquier otro `Throwable`.
- `ParkingSafetyNetWorker.doWork()` relanza la cancelación en vez de devolver `Result.success()`
  con un log de error.
- `:shared:testDebugUnitTest` sigue en verde.

## Consumidores auditados

- `PaparcarLogger.e()` — único punto de reenvío a `CrashReporter.recordNonFatal`. Arreglado aquí
  cubre los 71 call sites existentes sin tocarlos.
- `ParkingSafetyNetWorker.kt:152` — el sitio diagnosticado desde el evento real de Crashlytics.
  Único `runCatching` de este fichero que alimentaba `PaparcarLogger.e`; el resto de
  `runCatching` del fichero son fail-open silenciosos (sin log `.e`) y quedan fuera de esta
  cancelación específica — no reportan a Crashlytics, así que no son parte de este invariante.
