# DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001 · el carril que publica «alguien está saliendo» no deja rastro remoto

**Estado:** ✅ Done (28-09-2026) · rama `feature/DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001-release-diag` · ⏳ sin ver en campo: el primer `SPOT_RELEASE` remoto llegará con el próximo APK

## Problema

Field-test del **27-09 por la noche**: el user conecta unos auriculares —registrados como el BT del
Ford Focus— para ver publicarse la plaza **RELEASING**. No la ve. Más tarde, en casa, sí ve una.
Pide mirar los diagnósticos remotos.

**No se puede contestar.** La telemetría remota del uid `0l7X59ptXeQ4WUMlpFOZHLPIJAI3` trae la noche
entera —tres sesiones `aborted_false_enter` (20:31 / 20:33 / 20:42, 18-36 s cada una), la salida de
las 23:39:50 con `publishedSpot=true`, el `bt_park_confirmed` de las 00:24:46, el `PROCESS_DEATH
force_stop` de las 02:55— y **ni un solo evento del carril de nominación**. Ni publicada, ni
saltada, ni retirada.

El motivo está en dos ficheros:

- `NominateSpotReleaseUseCase` — sus dos salidas (`Skip(reason)` y la publicación) sólo hacen
  `PaparcarLogger.d(TAG = "PARKDIAG/SpotRelease", …)`.
- `WithdrawSpotReleaseUseCase` — igual: `d()` en los tres caminos (nada pendiente, ventana agotada,
  retirada efectiva).

`PaparcarLogger` escribe en el `parkdiag.log` **local**. Ningún `DetectionEventLogger.log(...)` en
todo el carril, y `DetectionEvent` no tiene variante para él. Grep de comprobación:

```
$ grep -rn "SpotRelease" --include=*.kt shared/src | grep -c "DetectionEvent"
0
```

Consecuencia medida en esta misma sesión de diagnóstico: la plaza que el user sí vio a las 02:52
**no era la del Focus**. Dice «Liberada por SUV grande» y el Focus es `HATCHBACK_MEDIUM`; la publicó
la segunda cuenta del mismo coche (`b2fjhAADl1PQ9oYfxN7cW2NdY1G2`, Range Rover `SUV_LARGE`, sesión
`f1dea257`, Lugar la Cañada 205) — y ese móvil ni siquiera tiene `diagnostics_config`.

Y esa plaza **no era un embarque real**: la sesión `f1dea257` se abrió a las 00:26 con la LLEGADA a
casa, y nadie se subió a ese coche a las 02:52. Lo dijo el user al leer este diagnóstico. Así que el
carril sin BT nominó una plaza sobre un coche parado — un fantasma, justo lo que la doctrina de fallo
asimétrico prohíbe. **No se puede confirmar ni descartar**: ese móvil no tiene el gate encendido y no
hay informe suyo, que es exactamente el agujero de este ticket visto desde el otro lado. Queda como
pregunta de campo abierta, no como hallazgo.

### Lo que el `parkdiag` local sí contestó (28-09, informe `1790592808332`)

El user mandó el informe desde el Oppo (`OPPO CPH2371`, app 1.3.0, 614 KB gzip, 39 327 líneas) y el
log local **contesta la pregunta en cuatro líneas** que Firestore no tenía:

```
09-27 23:38:58.747 BTReceiver:  ▶ BT CONNECTED device=00:A4:1C:65:2B:3F
09-27 23:38:58.828 SpotRelease: ▶ plaza 31aaea54 published as RELEASING — somebody boarded … (4 min)
09-27 23:39:43.787 SpotRelease: ⊘ boarding … nominates nothing — already_nominated
09-27 23:40:36.004 Depart:      measured departure … → sesión cerrada, la plaza se promueve
```

**El carril BT funcionó**: publicó 81 ms después del CONNECT, rechazó bien el segundo embarque, y la
salida medida promovió el documento. La plaza estuvo RELEASING **98 segundos** y luego pasó a ser una
plaza normal. No hubo nada que arreglar en la lógica — lo que no existía era la forma de saberlo.

Eso no debilita el ticket, lo demuestra: **hicieron falta un informe manual, 5 MB de log y un
worktree para leer cuatro líneas que tenían que haber estado en `diagnostics/{uid}/sessions`.** Y
sigue habiendo dos cosas que el log local tampoco puede decir, porque nadie las escribe: si el
`ReportSpotWorker` llegó a posar el documento (**no imprime una sola línea en todo el log**) y qué
hizo el carril en el otro móvil.

## Doctrina violada

**[DET-VERDICT-NOT-PREDICATE-001]**, y no por interpretación: el KDoc de
`EvaluateSpotReleaseNominationUseCase` ya se reclama veredicto con estas palabras —

> *«A VERDICT, not a predicate: its answer names a `SpotStatus`, changes what strangers see on the
> map, and its refusal reasons are the vocabulary a field diagnosis will quote when a releasing spot
> did or did not go out.»*

— y esa cita es literalmente lo que este ticket no ha podido hacer. El evaluador nombra cinco
razones (`not_a_boarding`, `no_plaza`, `already_published`, `already_nominated`, `approximate_park`,
más las de `SpotReleasePolicy.Shape.Withheld`) y ninguna es citable: no salen del móvil.

Corolario del contrato de detección: *el fallo asimétrico exige saber por qué se calló*. Un carril
que decide no publicar y no dice por qué es indistinguible de un carril roto.

## Señales / datos disponibles

Ya existe toda la fontanería; no hay que inventar transporte.

- `DetectionEventLogger` (port en `domain/diagnostics/`), con `NoOpDetectionEventLogger` por defecto
  y `FirestoreDetectionEventLogger` bajo el gate `diagnostics_config/{uid}`.
- `DetectionEventDto` tiene columnas libres para esto sin tocar la superficie del serializador:
  `action` (la usa `Hold`/`Candidate`), `reason` (`HonestClose`/`Released`/`GeofenceRegistration`),
  `source` (`DepartureVerdict`/`GeofenceRegistration`/`Sentry`), `sessionAgeMs` (`SpotRetracted`/
  `Reverted`/`Hold`), `success`.
- `triggerLedgerSessionId(nowMs)` para los eventos que nacen **fuera** de una sesión — el caso
  `no_plaza`, donde no hay `session.id` bajo el que archivar.
- El id del documento publicado **es** el id de la sesión de aparcamiento, así que `sessionId` ya
  identifica la plaza sin columna nueva.

## Diseño

**Una variante nueva, `DetectionEvent.SpotRelease`, que cubre el carril entero** — nominación,
negativa y retirada bajo un solo `type=SPOT_RELEASE`. Misma razón que dio
[DET-EVERY-TRIGGER-LEAVES-A-TRACE-001] para meter los arms en el mismo tipo que los descartes: *el
histograma de disposiciones de un móvil tiene que ser UNA query*, y aquí la pregunta de campo es
exactamente esa («de N embarques, cuántos publicaron y por qué callaron los demás»).

```kotlin
data class SpotRelease(
    override val sessionId: String,   // el id de la plaza == el de la sesión; ledger diario si no hay
    override val timestampMs: Long,
    val action: SpotReleaseAction,    // NOMINATED · SKIPPED · WITHDRAWN · WITHDRAW_EXPIRED
    val reason: String? = null,       // el vocabulario del evaluador, tal cual
    val lane: String,                 // "bt" | "coordinator" — QUIÉN pidió
    val liveMs: Long? = null,         // al retirar: cuánto estuvo viva la nominación
    override val location: GpsPoint? = null,
) : DetectionEvent
```

Al DTO por columnas existentes: `action` → `action`, `reason` → `reason`, `lane` → `source`,
`liveMs` → `sessionAgeMs`. **Cero columnas nuevas**, cero cambio en el serializador.

**El `lane` es la mitad que importa.** Las dos mitades del carril las invocan jobs distintos
(`btWatchJob` en `launchBtDepartureWatch:1732`, el job de detección del Coordinator en `:1259` y
`:2430`), y hoy no hay forma de saber cuál actuó. Por eso `NominateSpotRelease` y
`WithdrawSpotRelease` pasan a llevar el carril en la firma:

```kotlin
fun interface NominateSpotRelease { suspend operator fun invoke(vehicleId: String, boardingIsCredible: Boolean, lane: String): Boolean }
fun interface WithdrawSpotRelease { suspend operator fun invoke(lane: String): Boolean }
```

⛔ **Este ticket NO cambia comportamiento.** La retirada sigue retirando lo que haya pendiente, venga
de donde venga. Que eso sea un bug —un job del Coordinator retira una nominación que hizo el carril
BT, y además quema la única nominación de la sesión— es
`DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001`, que se abre aparte. Aquí sólo se hace
**observable**: sin el `lane` en la telemetría, ese ticket tampoco se podría verificar en campo.

**Por qué en los use cases y no en el servicio.** El veredicto lo produce `EvaluateSpotRelease
NominationUseCase` y lo ejecuta el use case de I/O; el servicio no lo conoce. Ponerlo en el servicio
obligaría a re-derivar la razón allí — un segundo sitio donde equivocarse, que es justo lo que el
KDoc del evaluador se prohíbe a sí mismo con el caso `boardingIsCredible`.

## Criterio de éxito

1. Un field-test del carril RELEASING se puede cerrar **sin cable y sin el log local**: para cada
   embarque hay un evento `SPOT_RELEASE` en `diagnostics/{uid}/sessions/{id}/events` con `action`,
   `reason` y `source`.
2. Tests unitarios (fakes, no mocks) de ambos use cases: cada rama emite su evento con su razón.
   `should_logSkippedWithReason_when_sessionAlreadyNominated`, etc.
3. `DetectionEventDtoTest` cubre la variante nueva — el `when` exhaustivo de `toDto()` ya obliga a
   la rama, pero el test fija las columnas elegidas.
4. `./gradlew testAll` en verde. ✅ (`--rerun-tasks`, 1 m 3 s; 4 tests nuevos de nominación + 5 de retirada)

## Consumidores auditados

Barrido de todo lo que toca el sello o el carril, antes de cambiar las firmas:

| Sitio | Qué hace | Estado |
|---|---|---|
| `CoordinatorDetectionService:1259` | `nominateSpotRelease(...)` tras AR ENTER en fence propia | ✅ pasa `lane="coordinator"` |
| `CoordinatorDetectionService:1732` | `nominateSpotRelease(...)` en `launchBtDepartureWatch` | ✅ pasa `lane="bt"` |
| `CoordinatorDetectionService:1769` | `withdrawSpotRelease()` en `BtDepartureOutcome.NeverDrove` | ✅ pasa `lane="bt"` |
| `CoordinatorDetectionService:2430` | `withdrawSpotRelease()` al cerrar el job del Coordinator | ✅ pasa `lane="coordinator"` |
| `ProcessConfirmedDepartureUseCase:231` | `clearPendingSpotRelease()` al promover el documento | ✅ emite `PROMOTED` (`lane=departure`) |
| `ReleaseActiveParkingSessionUseCase:132` | `clearPendingSpotRelease()` al soltar la sesión | ✅ emite `PROMOTED` (`lane=user`, el «Me voy») |
| `DetectionModule:307/316` | construcción de ambos use cases | ✅ inyectado |
| `MockKoinModuleVerifyTest` (`:app`) | resuelve el grafo del flavor mock | ✅ `testAll --rerun-tasks` verde |

## Follow-ups deliberadamente fuera de alcance

- `DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001` — la retirada sólo puede retirar lo que ella
  nominó, y una retirada ajena no gasta la única nominación de la sesión.
- `ReportSpotWorker` **no imprime nada**: la nominación dice «publicada» y el worker que de verdad
  escribe el documento en Firestore es mudo, así que «nominado» y «posado» siguen sin poder
  distinguirse. Es la misma enfermedad un escalón más abajo.
- El lado **iOS**: `IosAppPreferences` ya persiste `PendingSpotRelease`, pero no hay host que llame a
  estos use cases allí. Si al cerrar esto sigue sin haberlo, queda escrito aquí como hueco.
