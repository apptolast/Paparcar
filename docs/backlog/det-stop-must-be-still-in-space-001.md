# DET-STOP-MUST-BE-STILL-IN-SPACE-001 · una parada que se desplaza 122 m no es una parada

**Estado:** ✅ Done · rama `bugfix/DET-STOP-MUST-BE-STILL-IN-SPACE-001-spatial-stop` ·
worktree `../Paparcar-stop-still`

## Problema

Field 2026-08-22, viaje 2 (Camelias → Góndola), Oppo `CPH2371`, sesión `1787416899048`
(`confirmed_steps+egress`, vmax 85 km/h, 47/147 fixes de conducción). **El pin se plantó 70 m antes
de la plaza real**, en la bocacalle por la que el coche entró desde la carretera.

Los fixes de la llegada, del `parkdiag` del propio móvil:

```
18:47:14  loc#92  36.60935  ,-6.2764917  speed=0.0  acc=10.8   ← abre la "parada"
18:47:14  loc#93  36.60935  ,-6.2764917  speed=0.0  acc=10.8
18:47:18  loc#94  36.6092167,-6.2770817  speed=0.0  acc=10.3   ← 54,8 m del #92
18:47:24  loc#95  36.6086383,-6.2775383  speed=0.0  acc=6.0    ← 122,5 m del #92
          ⚓ anchor FROZEN — drive-entered stop matured (stableFixes=3, walkFixes=0)
```

Los tres fixes que "probaron la parada" reportaban **velocidad 0,0 m/s** y estaban separados
**122,5 m en 9,56 s** — 12,8 m/s, 46 km/h de desplazamiento medido. Con precisiones de 6–11 m, el
desplazamiento es ~10× la incertidumbre: movimiento real e inequívoco. El coche estaba saliendo de
la carretera y decelerando; el campo `speed` del fix mentía.

El pin quedó en `loc#95` (36.6086383,-6.2775383, acc 6.0) — exactamente el fix que congeló el ancla.
La plaza real está en ~36.60872,-6.27832, a **70 m**. El Redmi, en el mismo viaje y minuto, acertó.

**El agravante:** una vez congelada, solo ≥ `minimumTripSpeedMps` (5,0 m/s) libera el ancla. La
maniobra final fueron 4,93 → 3,12 → 2,68 m/s. El primer fix se quedó a **0,07 m/s** del umbral. El
coche llegó a su plaza con fixes de 1,7–2,5 m de precisión y la app los ignoró todos.

## Doctrina violada

> *El evento NOMINA, solo el movimiento MEDIDO confirma.*

Aquí una velocidad **reportada** de cero se aceptó como parada **medida**, mientras la posición
medida — la única magnitud que el receptor observa directamente — decía lo contrario. Es el mismo
patrón que `DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001` (*la medición refuta a la etiqueta*), aplicado
esta vez a la etiqueta `speed` del propio fix.

## Señales / datos disponibles

Todo estaba ya en el estado. `updateStopTracking`
(`CoordinatorParkingDetector.kt:2465`) abre y sostiene la parada con un único predicado:

```kotlin
if (location.speed < config.stoppedSpeedThresholdMps)   // 1 m/s
```

y `stoppedFixes` acumula **cualquier** fix que pase ese filtro. De ahí beben dos consumidores:

- `restProvenByFixes = s.stoppedFixes.size >= config.anchorFreezeStableFixes` (3) → congela el ancla
  [DET-SHORT-TRIP-FREEZE-001]. Su KDoc afirma *"N stable fixes … prove the car came to rest here"* —
  pero **nadie comprueba que sean `stable` en el espacio**.
- `bestFix()` = `stoppedFixes.minByOrNull { it.accuracy }` → elige el ancla, o sea el pin.

Un solo defecto envenena los dos.

## Diseño — reusar el invariante que ya existe, no escribir el segundo

El proyecto **ya tiene** este predicado, calibrado en campo por ambos lados:

```kotlin
// CoordinatorParkingDetector.kt:2339  [DET-CREDIBLE-DRIVE-001]
private fun isCorroboratedVehicleHop(prev: GpsPoint?, curr: GpsPoint): Boolean {
    ...
    if (d <= prev.accuracy + curr.accuracy + config.credibleDriveHopMarginMeters) return false
    return d / dtSeconds >= config.clearBestStopSpeedMps
}
```

*"La velocidad Doppler declarada es lo que la banda muda no puede creerse; un salto medido es
evidencia independiente."* Es literalmente el razonamiento que falta en el tracking de paradas.
Está calibrado para que la deceleración de Galeote pase (23,7 m contra 9,9 m de precisión conjunta)
y para que el vaivén de recuperación de Camelias falle siempre (11,9 m contra 14,1 m de ruido).

**El cambio:** la parada mide su propia coherencia espacial contra su fix de origen.

```kotlin
val stopOrigin = s.stoppedFixes.firstOrNull()
val stillnessRefuted = stopOrigin != null && isCorroboratedVehicleHop(stopOrigin, location)
```

Cuando se refuta, el fix **no es evidencia de reposo**: no puede ser el ancla (`mayCapture` gana
`!stillnessRefuted` — esta es la mitad que sostiene el arreglo, porque el ancla se lee del fix crudo
y no de `stoppedFixes`), no entra en el quórum de congelado, y no deja madurar la parada en ese
latido (lo que mantiene honesta también la vía por reloj). Sí pasa a ser el nuevo **origen espacial**
contra el que se miden los siguientes, para que la referencia no se quede obsoleta.

**El reloj de la parada (`stoppedSince`) NO se reinicia** — ver más abajo por qué; ese fue el segundo
diseño que mataron los replays.

Tres propiedades que hacen el diseño seguro:

1. **El fix que ABRE la parada nunca se descarta.** Se compara contra el origen de su propia parada,
   no contra el último fix de conducción. Un stream escaso (el caso MIUI hambriento) sigue
   consiguiendo su ancla con un solo fix. Descartar el fix de apertura habría sido un FN nuevo.
2. **Exige además ritmo de coche** (`d/dt ≥ clearBestStopSpeedMps`), así que una deriva de GPS de
   55 m a lo largo de 60 s en un cañón urbano (0,9 m/s) **no** refuta la quietud: sigue siendo una
   parada. Solo se refuta lo que ningún teléfono quieto produce.
3. **Cero fórmulas nuevas.** Se llama al predicado existente. El refactor profundo tiene anotado
   como bug #9 *"tres cálculos de «dentro de mi valla», dos fórmulas, divergencia sin declarar"*;
   esto no añade la cuarta.

### Lo que deliberadamente NO se toca

`frozenAnchorSteplessDepartureFixes = 4` [DET-CONFIRM-FRESHNESS-001] es la vía de escape que ya
existe para un ancla congelada de más, y hoy **se quedó a un fix** de salvar el viaje: hubo 3 fixes
consecutivos ≥ 2,5 m/s (4,93 · 3,12 · 2,68) y pedía 4. Bajarlo a 3 arreglaría este viaje y sería
exactamente el parche que la doctrina prohíbe: ese número protege contra el lavado del *walk-back*
de Camelias (arrastrar el pin hasta casa andando). **Si la parada no madura donde no debe, no hace
falta ninguna vía de escape.** Se arregla el origen; el número se queda donde está.

## Criterio de éxito

- Test: tres fixes con `speed = 0` separados 55 m y 75 m, precisiones 6–11 m → **no** congelan el
  ancla; los mismos tres fixes en la misma posición → sí la congelan.
- Test de regresión con las trazas reales de hoy: el semáforo del Oppo (viaje 1, `loc#237-241`,
  posiciones idénticas a 2,5 m) sigue congelando; la llegada del Redmi (viaje 1, `loc#40-43`,
  desplazamientos de 4–20 m contra precisiones de 15–59 m) sigue congelando.
- Campo: repetir la entrada a Góndola desde la carretera y ver el pin en la plaza, no en la bocacalle.

## Consumidores auditados

`grep -rn "stoppedFixes\|bestFix(\|stoppedSince" composeApp/src --include=*.kt`

| Consumidor | Clasificación |
|---|---|
| `bestFix()` (`CPD:425`) — `stoppedFixes.minByOrNull { accuracy }` | **cerrado** — un fix refutado ya no entra en la lista |
| `restProvenByFixes` (evidencia de congelado) | **cerrado** — misma lista |
| `newBestStop` / `mayCapture` (**el ancla de verdad**) | **cerrado** — lee el fix crudo, no la lista; se le añadió `!stillnessRefuted`. Era el hueco que casi se me escapa |
| `matured` por TIEMPO (`anchorFreezeStopMs`) | **cerrado** — `!stillnessRefuted` impide madurar en el mismo latido en que la traza refuta |
| `stoppedSince` — conteo de pasos (`CPD:727`, `CPD:801`) | **exento con razón** — sigue no-nulo; el reloj de la parada no se toca a propósito (ver abajo) |
| `stoppedFixes = emptyList()` al reanudar conducción (`CPD:2788`) | **cubierto por convergencia** — el clear de conducción ya existía y es el que salvó el caso Góndola |
| `EvaluateSafetyNetCheckUseCase`, workers | **exento** — no leen `stoppedFixes`; deciden sobre el pin ya persistido |

## Qué me corrigieron los replays (y por qué el diseño final es otro)

Dos iteraciones murieron contra trazas reales, y ambas merecen quedar escritas:

1. **Contar el fix actual en la evidencia** (`stoppedFixesNow.size` en vez de `s.stoppedFixes.size`)
   adelantaba el congelado un latido y clavaba el pin en el semáforo de `Trace_CalleGavia001`. El
   umbral se lee sobre la cuenta PREVIA: el congelado se dispara en el fix cuyos predecesores ya
   alcanzaron el quórum.
2. **Reiniciar `stoppedSince`** al refutar reabría `initialStopWindowMs` a mitad de parada y permitía
   una re-captura donde master nunca la habría permitido → los dos replays de `Enamorados001` (el
   stream MIUI hambriento cuyo ancla debe seguir desheredada para que el techo PREGUNTE) fallaron.
   Diseño final: **avanza solo el ORIGEN espacial**, nunca el reloj. Una parada que repta conserva su
   reloj; lo que pierde es el derecho a llamarse probada.

## Residuo conocido, deliberadamente fuera de alcance

Una parada abierta hace 60 s cuyo coche haya reptado TODO ese tiempo por debajo de 2,5 m/s de ritmo
puede seguir madurando por reloj en el primer latido no refutado, con el ancla en su fix de apertura.
No se ha visto en campo y cerrarlo exigiría un campo de estado nuevo (el refactor profundo ya cuenta
54). Queda anotado, no parcheado.

## Estado final

- ✅ **1397 tests verdes** (`testProdDebugUnitTest`), incluidos los 2 nuevos y los 14 de replay.
- ✅ `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid`.
- Sin strings nuevos, sin pantallas ni estados nuevos → no toca i18n ni Dev Catalog.
- `detectionPath` / `armEvidence` sin caminos nuevos: esto corrige DÓNDE ancla un camino existente.
- ⏳ Campo: repetir la entrada a Góndola desde la carretera.
