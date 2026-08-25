# DET-STAGE-USER-CONFIRM-001 · P3.6 — la forma se vuelve obligatoria, y P0.1 se gana el sueldo

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-USER-CONFIRM-001-p3-6` ·
worktree `../Paparcar-stage-6`

Paso **P3.6**, el que el plan señala como especial. Sigue a `5b9d32ee` (P3.5).

## Qué mueve

[BUG-COORD-115] Un toque supera a toda inferencia por debajo. La etapa gana al response-timeout, al
candidato, a la vía rápida y al scorer — y pierde solo contra las tres cosas que un toque **no puede
hacer verdad**: un hold ya resolviéndose, una sesión que nunca condujo, un presupuesto ya plegado.

**La respuesta resuelve el SI, nunca el DÓNDE.** Y casi toda la etapa es averiguar dónde está el
coche de verdad: el ancla cuando el egress nació en ella, la parada del propio usuario cuando la
respuesta llega lejos del ancla Y del nacimiento [DET-CONFIRM-ANCHOR-001], nunca un ancla nacida en
un hueco.

## El único sitio del plan donde el refactor cierra un bug de omisión POR CONSTRUCCIÓN

[DET-USER-YES-IS-NOT-A-COORDINATE-001] Una rama de aquí clavaba un punto exacto **inmediatamente
después de concluir que no sabía dónde estaba el coche**: se descarta el ancla nacida en hueco como
«posible punto de paso», y el fix de repuesto se guardaba como coordenada exacta **con la duda
registrada en ningún sitio**.

La etapa devuelve ahora un `SavedParkingShape`, así que **un camino añadido después no puede guardar
nada sin decir de qué forma es**. Olvidarse dejó de ser expresable.

`SavedParkingShape` aterrizó en P1.10 sin que nadie lo adoptara — deliberadamente. Este es su primer
adoptante, y llega como **requisito**, no como opción.

## La retenibilidad deja de ser una elección del call site

La diferencia entre un confirm INFERIDO (puede esperar la ventana de gracia para descartar un recado)
y uno CONTESTADO (no puede: nada que la ventana pudiera aprender supera a que el usuario ya lo haya
dicho) era **qué función llamaba la rama** — `beginConfirm` contra `runConfirm`.

Es una propiedad de la DECISIÓN, así que ahora es `Confirm.mayHold`, declarada por la etapa.

⚠️ La primera versión de este paso tenía al ejecutor olfateando `pathLabel == "user"`. Es el mismo
defecto con otro sombrero, y conviene dejarlo escrito: el ejecutor no debe deducir la decisión.

## Y P0.1 se ganó el sueldo

`should_resolve_the_vehicle_before_confirming_within_the_same_fix` se puso **rojo**.

Las etapas leen el snapshot de la iteración, pero las ramas leían la atribución de vehículo **EN
VIVO** — porque la atribución ocurre **a mitad de iteración**, en una etapa que las supera. Una etapa
a la que le das el snapshot crudo recibe un `vehicleId` nulo **en el mismísimo fix que lo resolvió**,
y la plaza se guarda a nadie.

Ese test se escribió exactamente para esto y lo cazó en la primera ejecución. El arreglo superpone la
atribución viva **para todas las etapas**, no solo para ésta: las cinco por debajo de la atribución
estaban expuestas igual.

Es la tercera vez en la Fase 3 que el snapshot muerde (P3.1: `pendingConfirm`; P3.3: la línea de
frescura; P3.6: la atribución). Las tres tienen la misma raíz y la misma cura definitiva: **el bucle
de un solo escritor de P3.13**.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Ni un assert editado. **6** de precedencia (P0.1) verdes, **18** replays verdes.
**1.629 tests**, 0 fallos. Coordinator **−54 líneas**. `assembleMockDebug` ✅.

**Seis de diez etapas movidas.** Siguiente: **P3.7**, `VehicleAttributionStage` — y el plan la marca
con aviso: **es la única etapa que necesita I/O**. Decide en puro con
`VehicleFenceOwnershipPolicy.resolveSessionVehicleId` y **pide** el lookup como efecto
`ResolveVehicle`; el ejecutor re-entra por un entrypoint atómico. Si eso se complica, la señal es que
el efecto está mal definido — no que haya que meter el repositorio dentro de la etapa.
