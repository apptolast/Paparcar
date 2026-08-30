# DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001 · el veto de bici decide con pasos que ningún diagnóstico registra

**Estado:** ✅ Done · en master como `c692d61c` — *"the bicycle veto's only inputs reached no lane at
all"*. La rama `chore/…-log-the-inputs` y el worktree `../Paparcar-cadence-telemetry` ya no existen.

> Corregido el 2026-08-30 por [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]: decía *"🔵 Implementado, sin
> commitear"*.

**Origen:** descubierto el 2026-08-27 construyendo el replay de
`DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001`. La primera versión de la traza **pasaba también sin
el fix**: los eventos que causaron el bug no estaban en el log.

## Problema

`CoordinatorParkingDetector` emite `✦ step #N` (y su gemelo remoto `DetectionEvent.Step`) en
exactamente tres ramas:

```kotlin
if (!updated.session.driveAuthorized)                → "pre-drive, false-ENTER candidate"
else if (updated.anchorTrust.stopStartedAt != null)  → "stopped"
else if (updated.anchorTrust.anchor != null)         → "egress walk, anchor set"
```

Un paso dado **conduciendo, con el ancla ya limpiada y la conducción autorizada** no cae en ninguna:
se procesa, incrementa `fastMotionStepEvents` y **no deja rastro en ningún sitio**.

Y esa es, exactamente, la forma de un paso de cadencia — `cadenceQualifies` exige `!anchorPinned` y un
fix fresco por encima del techo peatonal. **Los únicos eventos capaces de activar el veto de tracción
humana eran justo los que ningún diagnóstico grababa.**

## Cómo se descubrió

La sesión del 26-08 (Redmi, Valdés→Góndola) escribe:

```
20:22:11.527  ♲ pedal cadence — 12 steps concurrent with 3 above-ceiling fixes
```

…y entre el seed de conducción de las 20:21:12 y esa línea, el log **no contiene ni una sola** línea
`✦ step`. Las dos afirmaciones sólo son ciertas a la vez si los doce tomaron la rama muda. Al
reconstruir la traza desde el log, el replay salía verde con y sin el fix: sin esos doce eventos la
cadencia nunca se dispara y no hay bug que reproducir. Hubo que reconstruirlos por aritmética.

## Doctrina violada

`DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C` cerró este mismo defecto un nivel más arriba, y lo dejó
escrito en el código:

> *"A veto that can decide a session silently is the defect"*

Se hizo visible el **veredicto** (`PEDAL_CADENCE_LATCHED`) y se dejaron mudas sus **entradas**. El
latch se puede leer pero no auditar: sabemos que hubo 12 pasos, no contra qué fixes, ni a qué
velocidad, ni si la concurrencia era rítmica o casual.

## Diseño

**Es un cambio de logging. No cambia ninguna decisión** — ni un umbral, ni una rama de veredicto.

### Local (`parkdiag`) — la cuarta rama, con las dos caras

```
♬ step while driving — PEDAL STROKE (cadence 12/12 on 3/2 fixes ·
  judged against speed=4.65m/s acc=7.06m age=812ms · band=3.0-11.1mps)
```

Se registran **los pasos acreditados Y los no acreditados**. Calibrar el veto necesita el
**denominador** (pasos en movimiento que NO leyeron como pedaleo) tanto como el numerador; un lane
que sólo apunta condenas no puede contestar *"¿con qué frecuencia se equivoca?"*.

No lleva `#N` porque `shouldCount` es falso en ese estado y `stepCount` no se mueve — que es
justamente el motivo por el que esta rama no existía.

### Remoto — `DetectionEvent.Cadence`, **rollup por fix**

Un evento **por cada fix distinto acreditado**, nunca uno por paso: una bici real pedalea de forma
continua y por-paso serían cientos de escrituras a la hora sobre un lane que ya lleva todos los fixes.

Cabalga columnas existentes, **sin tocar el serializador** — la convención que `toDto` declara cuatro
veces:

| campo | columna | por qué esa |
|---|---|---|
| `sessionStepEvents` | `stepCount` | es un conteo de pasos |
| `creditedFixes` | `pathLabel` (`fixes=N`) | misma forma que el `PEDAL_CADENCE_LATCHED` ya escribe → entrada y veredicto agrupan por la misma cadena |
| `fixAgeMs` | `enterAgeMs` | la columna "cuánto de vieja era esta señal" que estableció `ACTIVITY_TRANSITION` |
| velocidad y precisión del fix juzgado | `speed` / `accuracy` de `base` | **`location` del evento ES el fix juzgado**, no dónde estaba el caminante |

Esa última fila es la que contesta la pregunta de calibración: 3,13-4,65 m/s condenaron a un coche en
el centro de Cádiz.

**Semántica exacta del rollup**, escrita en el evento y en su test: cada evento se emite en el
**primer** trazo acreditado a un fix nuevo, así que su `sessionStepEvents` es el total acumulado
*incluyendo* ese trazo de apertura (1, luego 13). La ráfaga que un fix realmente recogió es el
**delta al evento siguiente**, no el valor que él lleva. Coste asumido: la ráfaga del **último** fix
acreditado no se recupera, porque ningún fix posterior la cierra — y eso no afecta a nada que un
umbral lea.

Los pasos NO acreditados se quedan en local: la fracción que acotan se recompone en remoto desde
`LOCATION_FIX`, que ya lleva todos los fixes con su velocidad.

## Criterio de éxito

- [x] Un paso dado conduciendo sin ancla deja línea en `parkdiag`, acreditado o no.
- [x] Un fix acreditado deja **un** evento remoto; doce trazos sobre ese fix no dejan doce.
- [x] Un paso por debajo del techo peatonal **no** deja evento remoto ni activa nada.
- [ ] Replayar un viaje nuevo reconstruye la cadencia **sin inventar un solo evento** (necesita un
      viaje con el build nuevo).
- [ ] El bloque `⚠ RECONSTRUCTED` de `Trace_Gondola2608CadenceVeto.kt` deja de hacer falta para
      trazas futuras. La del 26-08 se queda como está: sus datos ya se perdieron.

## Estado de ejecución

- [x] `DetectionEvent.Cadence` + su rama en `typeName()` y `toDto()` (el `when` es exhaustivo, así
      que olvidar una es error de compilación, no una columna nula silenciosa).
- [x] Cuarta rama en el colector de pasos del coordinator, con ambos desenlaces.
- [x] **2 tests nuevos, los dos verificados en rojo** por neutralización:
      - anulando la emisión → cae el del rollup; **el negativo sigue verde**, como debe (afirma una
        ausencia);
      - emitiendo sin condición → **caen los dos**: el negativo es el que impide que un paso no
        acreditado se cuele en el numerador.
- [x] Suite completa verde. `compileMockDebugKotlinAndroid` + `compileProdDebugKotlinAndroid` sin
      warnings.
- [x] Entrada en `docs/detection/PARKING-DETECTION.md` §2.
- [x] Sin strings, sin pantallas, sin estados MVI → no toca los 9 locales ni el Dev Catalog.
- [x] `detectionPath` / `armEvidence` sin cambios: no hay camino de confirmación nuevo.

## Consumidores auditados

`grep -rn "DetectionEvent.Step\|typeName()\|toDto()" composeApp/src`

| Sitio | Clasificación |
|---|---|
| `dto/DetectionEventDto.kt` `typeName()` / `toDto()` | **cerrado** — `when` exhaustivo sobre el sealed; el compilador exige la rama |
| `FirestoreDetectionEventLogger.accumulate()` | **exento con razón** — tiene `else -> Unit` y el rollup de sesión no debe moverse: los trazos de cadencia no incrementan `stepCount`, así que `maxStepCount` seguiría siendo el mismo aunque se añadiera |
| Las tres ramas existentes de `DetectionEvent.Step` | **intactas** — este ticket añade una cuarta, no toca las tres |
| `EgressEvidence.onStepEvent` / `cadenceQualifies` | **exento** — no se toca: la decisión es idéntica, sólo se observa |

## ⚠️ Orden de merge

Este ticket y `DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001` **crean ambos este mismo fichero** y los
dos añaden al final de `docs/detection/PARKING-DETECTION.md`. El padre entra primero; al rebasar
éste, resolver quedándose con **esta** versión del doc (es la del ticket que lo implementa) y con
**las dos** entradas de `PARKING-DETECTION.md`.

## Relacionado

- Padre: `docs/backlog/det-human-powered-veto-must-be-revocable-001.md`
- **Bloquea a:** `docs/backlog/det-pedal-cadence-cannot-convict-a-car-in-traffic-001.md` — su corpus
  (fracción de fixes en movimiento con pasos concurrentes, en coche y en bici) no se puede recoger sin
  esto.
- Mismo defecto un nivel arriba, ya cerrado: `DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C`
