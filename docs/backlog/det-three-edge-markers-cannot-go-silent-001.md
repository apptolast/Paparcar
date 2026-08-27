# DET-THREE-EDGE-MARKERS-CANNOT-GO-SILENT-001 · tres marcadores de diagnóstico que podían dejar de emitir sin que nada se pusiera rojo

**Estado:** ✅ Done — 3 tests nuevos, los 3 verificados en rojo contra mutaciones deliberadas del
coordinator. Sin cambios en producción.

## Problema

El collector de fixes del coordinator lleva **cuatro** marcadores de diagnóstico, y son cuatro
formas distintas de dedup:

| Marcador | Forma | Qué emite | ¿Tenía testigo? |
|---|---|---|---|
| `loggedVehicleExit` | flanco **re-armable** | `ActivityTransition(IN_VEHICLE, EXIT)` | ⚠️ sólo `any { … }` |
| `loggedBicycleRideAtMs` | dedup **por valor** | `ActivityTransition(ON_BICYCLE, ENTER)` | ✅ sí |
| `loggedVehicleRideAtMs` | dedup **por valor** | `ActivityTransition(IN_VEHICLE, ENTER)` | ❌ ninguno |
| `loggedMotorWitnessed` | **pestillo** único | `Decision(MOTOR_WITNESSED)` | ❌ ninguno |

El del EXIT parecía cubierto y no lo estaba: `should_log_vehicle_exit_transition_in_trace` afirma
`any { … }`, o sea que llegara **al menos uno**. Eso es un testigo del silencio total y de nada más
— un flanco degradado a heartbeat (una línea por fix, justo el ruido que el marcador existe para
evitar) lo pasa, y un flanco que no se re-arma también.

`jamExtensionLogged` **no** entra aquí: pese al nombre es una ENTRADA DE VEREDICTO (el presupuesto
de no-movimiento elige `aborted_no_movement_jam` a partir de él), y así está dicho en el KDoc de
`DetectionDiagnosticsTap`. `PEDAL_CADENCE` tampoco: ya vive en el tap y está testeado allí.

## Doctrina violada

Ninguna decisión cambia. Lo que se rompe es la premisa de
[DET-HOLD-BRANCHES-MUST-SPEAK-001]: *una rama que puede decidir una sesión tiene que dejar algo en
la traza*. Un marcador que deja de emitir en silencio devuelve la traza al estado que hizo
imposible leer la sesión del 2026-08-20 — 1.476 eventos y ninguno nombraba el veto que la decidió.

## Diseño

Tres tests en `CoordinatorParkingDetectorTest`, junto a los de la vía de evidencia AR. No se toca
producción.

1. **EXIT — las DOS mitades.** Una línea por salida: no una por fix (se emiten dos fixes con el
   hint puesto), y otra sí cuando el coche se vuelve a ir (un fix de conducción limpia el hint vía
   `EgressEvidence.onFix`, que es lo que re-arma el flanco). Se afirma `== 2`, no `any`.
2. **Boarding — dedup por valor.** El mismo sello no repite línea; un sello NUEVO sí la produce —
   la propiedad que un pestillo se habría tragado. Se afirma además el `trueTimeAgeMs` de cada uno
   (60 s y 5 s), que es el dato por el que el veredicto humano-vs-motor se arbitra.
3. **Motor — pestillo.** Una sola línea al cruzar `sustainedDriveProofMs`, y ninguna después.

## Criterio de éxito

- Suite verde → ✅ **1.665 tests, 0 fallos** (1.662 + 3).
- **Verificados en rojo**, revirtiendo el coordinator entre cada mutación:

| # | Mutación | Tests que la cazan |
|---|---|---|
| M1 | El flanco del EXIT se vuelve heartbeat (`&& !loggedVehicleExit` fuera) | **sólo el nuevo** — el `any { … }` viejo pasa |
| M1b | El flanco nunca se re-arma | el nuevo · y también el viejo |
| M2 | El dedup por valor del boarding se vuelve pestillo | el nuevo |
| M3 | El pestillo del motor se vuelve heartbeat | el nuevo |

M1 es la que justifica el ticket entero: es un bug real que el test que ya existía no ve.

### ⚠️ El tercer test NO valía cuando lo escribí, y la mutación es lo único que lo dijo

La primera versión del test del motor emitía cuatro fixes y pasaba **igual de verde con el
coordinator mutado**. Causa: la banda acredita el intervalo ENTRE dos fixes en banda, así que el
primero no aporta nada y el cruce de los 30 s no cae en el tercer fix sino en el **cuarto** — el
último. Sin ningún fix posterior al cruce, un heartbeat no tiene dónde manifestarse y es
indistinguible de un pestillo. Se arregla con un quinto fix, no tocando la aserción.

Queda escrito porque el error no fue de aritmética: fue **asumir dónde caía el cruce en vez de
medirlo**, y un test verde no lo habría dicho nunca.

## Consumidores auditados

`grep` de los cuatro marcadores en el coordinator: sólo se leen y escriben dentro del collector de
fixes (`:589`, `:596-598` declarados; `:941-992` usados), ninguno escapa. Los tres eventos que
emiten viajan a Firestore por `DetectionEventDto` (ya con test de mapeo propio) y no los consume
ninguna decisión — son traza. Nada que cerrar fuera.

## Follow-up que este ticket habilita

**`DET-EDGE-MARKERS-TO-THE-TAP-001`** — mover los cuatro marcadores a `DetectionDiagnosticsTap`,
donde la arquitectura objetivo [09 §7] los asigna. **Este ticket era su prerequisito**: sin aserción
que discrimine, la mudanza se haría a ciegas.

Tiene doc propio (`docs/backlog/det-edge-markers-to-the-tap-001.md`) y no un párrafo aquí, porque un
follow-up que sólo vive dentro del doc de otro ticket es un follow-up que el backlog no ve — que es
justo lo que `DOCS-BACKLOG-TRUTH-001` vino a terminar. El aviso importante está allí: **no es una
mudanza mecánica**, el tap sólo implementa hoy la forma de pestillo (`latchOnce`) y hacen falta
también el flanco re-armable y el dedup por valor.
