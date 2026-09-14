# DET-A-MANUAL-RELEASE-LEAVES-THE-ARRIVAL-ENTER-IN-THE-BUS-001 · la liberación manual no resetea el bus de salidas

**Estado:** 🔵 Abierto, sin empezar · sin rama

## Problema

`ProcessConfirmedDepartureUseCase` resetea `DepartureEventBus` al cerrar una sesión
(`departureEventBus.reset()`, justo antes de quitar la geocerca). `ReleaseActiveParkingSessionUseCase`
—la liberación manual, «Me voy»— **no lo hace, y ni siquiera inyecta el bus**. Tras una liberación
manual el bus conserva el `IN_VEHICLE_ENTER` del embarque.

Ese es exactamente el estado que `ConfirmParkingUseCase` limpia a propósito al guardar un
aparcamiento, con su motivo escrito: *sin ese reset, alejarse andando del coche dentro de los 30 min
de `vehicleEnterWindowMs` confirma una salida falsa* [BUG-WALK-DEPART-001].

| Quién cierra | ¿Resetea el bus? |
|---|---|
| `ConfirmParkingUseCase` (al aparcar) | ✅ |
| `ProcessConfirmedDepartureUseCase` (salida detectada) | ✅ |
| `ReleaseActiveParkingSessionUseCase` (**«Me voy»**) | ❌ — ni lo inyecta |

## Procedencia

Segundo hallazgo del §5.1 de `docs/detection/07-duplicacion.md`, catalogado como «para adjudicar, NO
arreglar». El primero (la zona privada) se cerró en
**SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001** (`eff2bdfa`); este se dejó fuera de
alcance a propósito: es un bug de detección, no de publicación.

## ⚠️ La asimetría puede ser CORRECTA — y el reset, dañino

Leído de cerca el 14-09-2026, y esto es lo que hay que tener delante antes de tocar nada.

Fíjate en **cuándo** resetean los dos que sí lo hacen: `ProcessConfirmedDeparture` limpia después de
que el ENTER haya hecho su trabajo — ese ENTER es justo lo que probó la salida. Está **consumido**.
Tirarlo es correcto.

La liberación manual ocurre con el usuario **junto al coche, a punto de arrancar**:

```
t0  sube al coche            → AR dispara ENTER, bus = t0
t1  abre el peek, «Me voy»   → sesión cerrada, geocerca fuera
t2  arranca y conduce
```

El ENTER de `t0` **no lo ha consumido nadie**. Es precisamente el que debería armar el viaje que
empieza en `t2` (lo lee `EvaluateArEnterArmUseCase`). Resetear «por simetría» lo borraría: no sería
limpiar, sería tirar la única prueba de que el usuario ha embarcado, justo antes de necesitarla.

⛔ Por eso «¿bug o intención?» sigue abierto, y por eso el reset por simetría sería el parche que la
doctrina prohíbe.

## Dónde SÍ podría morder: multi-vehículo

El único filtro que protege contra un ENTER ajeno es
`isAdmissibleEvidence(evidenceAtMs, sessionStartMs)` (`domain/detection/physics/EvidenceAdmissibility.kt`),
y su regla es literalmente `evidenceAtMs >= sessionStartMs`: un ENTER es admisible para una sesión si
es **más nuevo que el instante en que esa sesión aparcó**. No comprueba de qué coche es.

```
lunes    aparcas el coche B en otro barrio   → sesión B activa
martes   subes al coche A                    → ENTER, bus = t_martes
martes   «Me voy» con el coche A             → el bus NO se limpia
```

`t_martes` es más nuevo que el aparcamiento de B → **pasa el filtro**. Un embarque en el coche A
entra como evidencia sobre la sesión del coche B, que lo leen su red de seguridad
(`EvaluateSafetyNetCheckUseCase.lastVehicleEnteredAtMs`) y, si B emite un EXIT,
`DetectParkingDepartureUseCase` / `VerifyDepartureEvidenceUseCase`.

**Hipótesis, no hallazgo.** No está medido si ese camino llega a liberar B o si lo paran los guards
de conducción medida que vienen después (el ENTER nomina; la corroboración es desplazamiento fuera
del alcance peatonal). Eso es exactamente lo que falta.

## Lo que hay que medir

> ⚠️ **Los dos caminos de cierre no dejan el mismo rastro**, y esa es la razón de que esta pregunta
> no se pueda contestar con los datos que ya hay: el manual emite `DetectionEvent.Released` y el
> automático `DepartureProcessed`, y ninguno de los dos dice en qué estado quedó el bus. Cerrar la
> brecha es la precondición de todo lo de abajo.

1. ¿Qué edad tenía el ENTER en el instante de cada cierre? Con eso se sabe si el bus queda
   realmente poblado tras un «Me voy» y con qué frecuencia.
2. ¿Hay alguna sesión de OTRO vehículo activa en ese momento? Es la precondición del único escenario
   dañino identificado.
3. Si la hay, ¿llega el ENTER huérfano a influir en su veredicto, o lo para la corroboración por
   desplazamiento?

### Instrumentación propuesta (barata, y es la mitad buena del fix)

`DetectionEvent.Released` no lleva la edad del ENTER; `DetectionEventDto` **ya tiene la columna
`enterAgeMs`**, establecida por ACTIVITY_TRANSITION y reutilizada por `DepartureVerdict`,
`ProcessDeath` y el fix-age. Así que medir cuesta:

- un campo nullable en `DetectionEvent.Released`,
- una línea en el mapper (`base.copy(..., enterAgeMs = …)`),
- **inyectar `DepartureEventBus` en `ReleaseActiveParkingSessionUseCase` para LEERLO, sin resetear.**

Sin migración de Room y sin columna nueva (los diagnósticos son documentos de solo escritura). Y esa
inyección es justamente la que el fix necesitaría después: se paga una vez y deja la pregunta
contestándose sola en el siguiente uso real.

## Criterio de éxito

Una respuesta MEDIDA a (1)-(3) y, entonces sí, una de dos:

- es bug → el reset en la puerta manual, con su test y con el cuidado de no matar el ENTER que arma
  el viaje siguiente (quizá reset condicionado a que el ENTER ya esté consumido, no incondicional);
- no lo es → una línea en `PARKING-DETECTION.md` explicando por qué la asimetría es correcta, para
  que la próxima auditoría no vuelva a levantarla.
