# DET-EDGE-MARKERS-TO-THE-TAP-001 · los marcadores de flanco, a su dueño

**Estado:** 🔵 Abierto · sin rama · **desbloqueado** — su prerequisito
(`DET-THREE-EDGE-MARKERS-CANNOT-GO-SILENT-001`, master `4272377b`) está cerrado.

## Problema

La arquitectura objetivo [09 §7] asigna los marcadores de diagnóstico al
`DetectionDiagnosticsTap`: *un pestillo que existe SÓLO para dejar una línea en la traza en vez de
una por fix es contabilidad de diagnóstico, y pertenece ahí*. Su propio KDoc lo dice. Pero cuatro de
ellos siguen siendo `var` sueltas dentro del collector de `CoordinatorParkingDetector.invoke()`:

| Marcador | Emite |
|---|---|
| `loggedVehicleExit` | `ActivityTransition(IN_VEHICLE, EXIT)` |
| `loggedBicycleRideAtMs` | `ActivityTransition(ON_BICYCLE, ENTER)` |
| `loggedVehicleRideAtMs` | `ActivityTransition(IN_VEHICLE, ENTER)` |
| `loggedMotorWitnessed` | `Decision(MOTOR_WITNESSED)` |

## ⛔ No es una mudanza — y ésta es la razón de que exista este doc

El reflejo natural es «mover cuatro `var` a `latchOnce`». **No encajan.** Son *cuatro formas
distintas* de dedup, y el tap sólo implementa una:

- **Flanco re-armable** (`loggedVehicleExit`) — se emite al ponerse el hint y **se rearma** cuando el
  hint se cae con un fix de conducción (`EgressEvidence.onFix`). Una salida por marcha, no una por
  sesión. `latchOnce` lo convertiría en una sola línea para todo el viaje.
- **Dedup por VALOR** (los dos sellos AR) — reemite cuando el sello CAMBIA, incluyendo desde el 0
  sembrado a propósito para que un sello HEREDADO de antes de la sesión se loguee con su edad real.
  `latchOnce` se tragaría el segundo embarque.
- **Pestillo** (`loggedMotorWitnessed`) — la única forma que el tap ya tiene.

Así que el trabajo es **diseñar dos formas nuevas en el tap** y después mover. Diseño pequeño, pero
diseño.

## Doctrina

[DET-HOLD-BRANCHES-MUST-SPEAK-001] y el propio KDoc del tap: **una sola puerta** para lo que la traza
emite. Hoy la respuesta a «¿habló esta rama?» está en dos sitios.

⛔ `jamExtensionLogged` **NO entra en el alcance** por mucho que su nombre lo parezca: es una ENTRADA
DE VEREDICTO (de él sale `aborted_no_movement_jam` en vez de `aborted_no_movement`). Está escrito en
el KDoc de `DetectionDiagnosticsTap` y ahí se queda.

## Señales / datos disponibles

Los cuatro **ya tienen aserción que discrimina** desde `4272377b`, verificadas en rojo contra
mutaciones deliberadas. Ése era el prerequisito y por eso este ticket no se hace a ciegas: si la
mudanza rompe una forma de dedup, un test se pone rojo.

## Criterio de éxito

- Los cuatro marcadores viven en `DetectionDiagnosticsTap`, con las tres formas nombradas.
- **Cero tests editados.** Los cinco testigos existentes (los 3 de `4272377b` + el del `ON_BICYCLE` +
  el `any { … }` viejo del EXIT) deben pasar sin tocarse: son la definición de "sin cambio de
  conducta" aquí.
- El `latches` del tap se sigue limpiando en `open()`, para que ninguna forma nueva filtre de una
  sesión a la siguiente — que es el bug que `latchOnce` reemplazó a una `var` suelta para evitar.

## ⚠️ Medir antes de prometer

Una nota previa estimó «~55 líneas» para esta mudanza. **Esa clase de cifra ya falló una vez**: para
`DET-FIX-REDUCTION-TO-ITS-REDUCER-001` se había estimado «~62 líneas» y «el `collect` pasa de 185 a
~70», y lo medido fue un bloque de **159** que bajó **44**. Contar el bloque, no estimarlo, antes de
escribir un número en ningún sitio.

## Consumidores auditados

Pendiente de hacer al abrir el ticket. Punto de partida: los cuatro sólo se leen y escriben dentro
del collector (declarados junto a `loggedVehicleExit`, usados en el bloque de traza posterior al
`LocationFix`), y ninguno escapa. Los tres eventos que emiten viajan a Firestore vía
`DetectionEventDto`, que tiene su propio test de mapeo, y **no los consume ninguna decisión** — son
traza.
