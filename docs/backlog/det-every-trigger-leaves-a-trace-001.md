# DET-EVERY-TRIGGER-LEAVES-A-TRACE-001 · todo trigger deja rastro, incluso el que muere

**Estado:** ✅ Done (2026-08-24) · rama `feature/DET-EVERY-TRIGGER-LEAVES-A-TRACE-001-trigger-lane` ·
worktree `../Paparcar-trigger-trace`

Primera entrega de la **propuesta 3** (`09 §14.3`, adjudicada entera) / **P4.2** del plan de
refactor, adelantada por decisión del user tras la Fase 0.

## Problema

La doctrina dice *«todo trigger dispara SIEMPRE»*. Hasta ahora **solo era observable cuando salía
bien**: un arm dejaba `SessionStarted`, y **todas las formas de morir eran mudas en remoto**
(`04 §2`). En una sesión de campo donde «la detección no arrancó», estas cinco cosas se veían
exactamente igual — es decir, no se veían:

- el OEM se comió el broadcast,
- la puerta de estrategia paró al coordinator porque el coche va por Bluetooth,
- el usuario había revocado el permiso de ubicación en segundo plano,
- un guard de re-arm hizo exactamente su trabajo,
- el lookup de la geocerca falló de forma indeterminada.

Es el mismo agujero que `DET-PIN-PROVENANCE-001` cerró para los pines, un paso antes: en el trigger.

## Por qué se adelanta

El plan la tenía dentro del refactor (Fase 4). La Fase 0 la movió a *precondición*: dos tickets
independientes chocaron con que **tres ramas del hold son indistinguibles desde fuera porque no
emiten evento propio**, y sobre ellas no se puede escribir ningún test de precedencia
(`DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001`). Esta entrega cubre el carril de triggers; las
ramas del coordinator son la siguiente.

## Diseño

**Vocabulario puro, emisión en el servicio.** `TriggerDisposition` (enum en `domain/detection/`, NO
un caso de uso — el precedente es `ParkingDetectionSource`) más `DetectionEvent.Trigger`. Emitir es
un side-effect, así que vive en `CoordinatorDetectionService`; el veredicto no es un string literal
en el call site.

**Una sola puerta.** `logTrigger(...)` es el único sitio que construye el evento. El valor del
carril es que `type=TRIGGER` agrupado por `outcome` sea el histograma **completo** de lo que le pasa
a cada trigger del device, y eso solo se sostiene si ninguna rama emite por su cuenta.

| Disposición | Rama que deja de ser muda | Dónde |
|---|---|---|
| `ARMED` | (nueva) el arm existe como evento propio, no solo inferido del `SessionStarted` | embudo único |
| `REFUSED_STRATEGY` | 04 §2.2 — field 2026-08-01, los viajes del Kamiq pinneados al Focus | embudo único |
| `REFUSED_PERMISSIONS` | 04 §2.5 — permiso revocado, solo visible en logcat | `guardPermissions` (1 sitio, 5 llamadores) |
| `SUPPRESSED_USER_STOP` | ya trazaba, como `Decision` improvisado | migrado |
| `SUPPRESSED_REARM` | 04 §2.3 — el supersede trazaba, la supresión no | 2 sitios |
| `NOT_ARMABLE` | 04 §2.4 — un ENTER re-entregado y bien descartado | rama del `when` |
| `LOOKUP_FAILED` | 04 §2.6 — el caso que jamás debe leerse como "sin sesión" | lookup |
| `ORPHAN` | media rama: `OrphanCleaned` cuenta el borrado de la valla, no que llegó un trigger | limpieza |

**Columnas reutilizadas, sin cambio de superficie de serialización** (patrón de la casa): el carril
en `event`, el veredicto en `outcome`, el porqué en `reason`.

## El defecto que este ticket habría creado si no se mira

⚠️ **El hallazgo del ticket.** La retención (`cleanupExpiredSessions`) encuentra sesiones
consultando `startedAt`, y **solo `SessionStarted` escribe el documento padre**. Un evento escrito
bajo un id de sesión que nunca se creó es **inalcanzable para la query y no se borra jamás** — el
KDoc de retención ya lo admite para el carril de departures.

El `ARM_SUPPRESSED_USER_STOP` que ya existía escribe bajo `arm_<timestamp>`: **fuga exactamente un
huérfano incobrable por cada supresión.** Copiar ese patrón para siete disposiciones habría
multiplicado la fuga por cada trigger rechazado del device, para siempre. Un ticket de telemetría
creando en silencio un defecto de almacenamiento.

**Solución**: un **libro diario** — `triggers_<día>`, con cabecera real escrita una vez por bucket y
por proceso. El carril entero pasa a ser recogible por la barrida que ya existe, la supresión del
user-stop **deja de fugar**, y la forma resultante es la que los datos querían: un documento por
device-día con todos los triggers y qué fue de ellos.

## Doctrina

- *El evento NOMINA, solo el movimiento MEDIDO confirma* — intacta. **Cero decisiones cambiadas**;
  esto es puramente telemetría, que es lo que hace seguro entregarlo con la validación de campo
  pendiente. Ni un `if` de detección se ha tocado.
- *Todo trigger dispara SIEMPRE* — pasa de afirmación a **comprobable**.
- Carriles separados — el Bluetooth no entra aquí (sigue siendo `04 §2.12`, pendiente).

## Barrido de consumidores

| Consumidor | Estado |
|---|---|
| `when` exhaustivo de `toDto()`/`typeName()` | **cerrado** — variante nueva mapeada (el `when` la exige) |
| `accumulate()` (rollups del logger) | **exento con razón** — `Trigger` cae en su `else`; no crea rollup, así que no acumula entradas que nunca se vacían |
| `DetectionSessionDto` | **cerrado** — el libro diario reutiliza el DTO existente, sin campos nuevos |
| `arm_$now` de `logArmTrigger` | **exento con razón** — ese sí emite `SessionStarted`, luego crea su padre y es recogible. Fuera de alcance |
| `CoordinatorParkingDetectorTest` (5 `is DetectionEvent.`) | **cubierto por convergencia** — no son exhaustivos sobre el sealed |
| Estrategia BT (`04 §2.12`) | **abierto, declarado** — sigue sin sesión propia; ticket aparte |

## Tests

- `TriggerLedgerTest` (4) — el bucket diario y, sobre todo, que el `startedAt` de la cabecera es el
  **inicio del bucket** y no la hora del primer evento: es la clave que compara la retención.
- `DetectionEventDtoTest` (+3) — paridad de wire; ninguna disposición puede serializar `outcome`
  nulo, que sería una fila que nadie puede agrupar.
- `TriggerLaneGuardrailTest` (2, Konsist) — **sin vocabulario muerto** (una disposición que nadie
  emite se lee como «esto no pasa nunca» cuando significa «nadie la cableó») y **una sola puerta**.
  - ✅ Verificado discriminante: quitando la emisión de `ORPHAN`, el primero se pone rojo nombrándola;
    el segundo ya se había puesto rojo solo al pillar el fichero de test antes de acotarlo a
    source sets de producción.
- ⚠️ `ArchitectureTest` pilló un test mío en paquete `domain` importando `data`. Las aserciones de
  wire se movieron a `data/mapper/`, que es su sitio.

**1.449 tests**, 0 fallos (eran 1.440). `assembleMockDebug` ✅ — sin pantalla, estado ni condición de
routing nuevos, así que el Dev Catalog no cambia. Sin strings nuevos.

## Lo que NO entra (y sigue vivo en la propuesta 3)

1. Las **ramas mudas del coordinator** — las tres del hold. Es la mitad que hace testeable esa zona.
2. `PROMPT_ANSWERED` y el resultado del **backfill** (`04 §2.11`, `§2.15`).
3. Telemetría **en el momento de extender** la ventana de atasco, no solo al plegar (`09 §14.4-bis`
   lo anotó como requisito de esta propuesta).
4. El recorte de `LOCATION_FIX` bajo flag de replay — es lo que paga el coste de todo lo anterior.
   ⚠️ Con su aviso del plan: el flag debe quedar **ACTIVO** en los móviles de field-test o perdemos
   la materia prima de las trazas.
5. La estrategia **BT sin sesión** (`04 §2.12`) y los receivers/sensores (`§2.13`).
