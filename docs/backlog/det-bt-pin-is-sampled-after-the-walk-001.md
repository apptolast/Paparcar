# DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001 · una espera para no ACTUAR no puede retrasar lo que se MIRA

**Estado:** ✅ Done · mergeado a master 2026-08-30 (squash) · implementado en
`bugfix/DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001-look-during-the-debounce`, abierto sobre `c6b150a7`
y rebasado sobre `9946ae94` antes de cerrar. ⏳ **Sin validar en campo** — sólo el Kamiq (el coche con
MAC) puede ejercitar este carril.

## Problema

Reporte del user (30-08): *«si desconectamos puede ser ya cuando me haya alejado del coche, lo cual
el pin quedaría algo movido. ¿Es el comportamiento que estamos obteniendo?»*

Sí, y no por el instante de la desconexión: por lo que hacemos **después** de ella.

`BluetoothParkingDetector.detectParking()` coloca el pin en `parkingFix`, y `parkingFix` es el
**primer fix que se muestrea a partir del segundo 30**:

| t | qué pasa |
|---|---|
| 0 s | `ACL_DISCONNECTED`. `evaluateEngagement` decide si fue viaje — sin GPS |
| 0 → 30 s | `delay(BT_DISCONNECT_DEBOUNCE_MS)`. **El GPS no se mira en absoluto: se duerme** |
| 30 → 90 s | se muestrea hasta el primer fix con `accuracy ≤ 50 m` y `speed < 1 m/s` |
| ese fix | **es el pin** (`confirmParking(parkingFix, …)`), y nada lo corrige hacia atrás |

La fase de walk-away no repara nada: `sealPoint = walkSettled` es la posición del **cuerpo** para el
presupuesto de pasos; el pin se queda donde se muestreó.

Magnitud, con los valores de hoy:

- **30 s andando a 1,2-1,4 m/s ≈ 35-45 m** de arrastre, regalados por el debounce.
- `stoppedSpeedThresholdMps = 1f` está **por debajo del paso humano**: andando, el fix da
  `KeepWaiting` (hay test que lo fija: `should_keepSampling_when_movingAtWalkingPace`, 1,4 m/s), así
  que se sigue muestreando mientras el user se aleja **más**, y el pin cae donde primero baje de
  1 m/s — un semáforo de peatones, el portal — hasta 60 s más tarde. Si no para: `bt_gps_timeout`,
  **ningún pin**.
- Que el caso medio no sea catastrófico depende de que muchos móviles reportan `speed=0.0` en fixes
  derivados de baja calidad. O sea, de un accidente del hardware, no de un guard.

### Por qué el carril BT no tiene de dónde tirar

`BluetoothDetectionService.handleConnected()` no abre stream de localización: sella
`BtConnectionStore.recordConnected`, cancela el job pendiente y encola un check del safety-net. **No
hay ancla.** Cuando llega el DISCONNECT no existe un "último fix conduciendo" en memoria, así que la
posición del coche sólo puede salir de un fix posterior a la desconexión — y de esos, el bueno es el
más TEMPRANO, que es justo el que hoy tiramos.

El Coordinator congela el ancla al final de la conducción precisamente *para que la caminata no
arrastre el pin*. El BT hace lo contrario sin querer.

### ⚠️ Esto está medido en el código y **sin medir en el móvil**

**Cero sesiones BT en todo el log del 27-08**: el coche activo es el Focus (sin MAC) y el MAC del
Kamiq no aparece ni una vez. Es intencionado — el BT es determinista y el tiempo de conducción rinde
en el Coordinator. Consecuencia honesta: **este ticket no nace de un pin torcido observado**, nace de
leer el carril. La validación de campo queda pendiente y necesita el Kamiq.

Y queda una incógnita que sólo un viaje resuelve: **cuándo dispara el `ACL_DISCONNECTED` en el
Kamiq** — al cortar contacto (móvil aún dentro: caso bueno, el fix temprano cae sobre el coche) o por
pérdida de rango (ya andando: los 30 s se suman a lo ya caminado). El fix de este ticket mejora los
dos casos, pero cuánto sólo lo dice el `parkdiag`.

## Doctrina violada

Ninguna de las tres grandes, y eso es lo interesante: el carril **no plantaba plazas fantasma**, ni
confirmaba sin movimiento medido, ni descartaba triggers. Falla contra un invariante más callado, que
esta rama pone por escrito:

> **Una espera que existe para no ACTUAR no puede además retrasar lo que se OBSERVA. La posición del
> coche sólo se puede perder con el tiempo, nunca recuperar.**

El debounce (BT-005) responde a *«¿cuándo puedo actuar?»* — 30 s para no reaccionar a una oscilación
de radio o a un semáforo. Se implementó como *«¿cuándo puedo mirar?»*, y las dos preguntas tienen
respuestas opuestas: actuar, lo más tarde posible; mirar, lo más pronto posible.

Corolario de segundo orden, que es lo que hace de esto un sistema y no un parche: **la distancia y el
tiempo del ritmo peatonal se miden desde el MISMO instante — el del fix candidato.** Hoy coinciden
por accidente (el candidato se acepta justo antes de arrancar el reloj del walk-away). En cuanto el
candidato puede ser 30 s más viejo que el inicio de la vigilancia, separar los dos relojes convierte
una caminata normal en un teletransporte y **aborta un aparcamiento real**. El fix del muestreo sin
este acoplamiento sería una regresión.

## Señales / datos disponibles

- `EvaluateBtParkUseCase` ya clasifica fix a fix: `DrivingAbort` / `CandidateAccepted` /
  `KeepWaiting`. No hace falta un criterio nuevo, sólo aplicarlo antes y quedarse con el primero.
- El FGS con permiso de localización **ya está promocionado** antes del `delay`
  (`fgs.promote(withLocationPermission = true)`), así que mirar durante el debounce no exige
  permisos, servicio ni batería nuevos: son 30 s más de GPS en un evento que ya los tenía reservados.
- `Flow.first` es punto de cancelación igual que `delay`: el abort-on-reconnect (BT-005) sigue
  funcionando sin bandera nueva.
- `DepartureVerdict.enterAgeMs` es, por convención ya establecida en el DTO, la columna *"cómo de
  viejo era esto"* (la reutilizan `ActivityTransition`, `Cadence` y el testigo de
  `DET-UNWITNESSED-DISPLACEMENT-001`). Cabe ahí la edad del candidato sin tocar el serializador.

## Diseño

### 1 · Una sola observación, desde el segundo 0

El debounce deja de ser un `delay` y pasa a ser un **suelo sobre cuándo se puede actuar**. El
detector observa desde t=0 y acumula:

- **gana el candidato más TEMPRANO** — una vez aceptado, un fix posterior no lo reemplaza;
- **cualquier fix de conducción creíble aborta**, esté el candidato puesto o no.

La colección para cuando `aborted`, o cuando hay candidato **y** el debounce ya pasó. Techo total
`BT_DISCONNECT_DEBOUNCE_MS + GPS_SAMPLE_TIMEOUT_MS` = 90 s, el mismo de hoy. Si el techo salta con un
candidato ya en mano, **se usa** (hoy no puede pasar; mañana sí, y perderlo sería un FN nuevo).

⚠️ El punto que hace esto **más estricto** que hoy, no menos: la vigilancia de conducción cubre
ahora también los primeros 30 s, que hoy son un **punto ciego**. Un corte de radio a mitad de
marcha se caza antes, no después.

### 1-bis · El peligro que abre el propio cambio, cerrado en el mismo fold

Suscribirse en t=0 en vez de en t=30 s significa que el fused puede servir un fix **cacheado** como
primera emisión: su `maxUpdateAge` por defecto es **2× el intervalo** ≈ 10 s en alta precisión.
Diez segundos antes de cortar contacto el coche seguía rodando, y `minimumDepartureSpeedKmh` son
**10 km/h** — así que esa única muestra podría, según cómo caiga:

- **plantar el pin una manzana atrás** (si estaba parado en el último semáforo), o
- **abortar TODOS los aparcamientos BT normales** (si no lo estaba) — un FN silencioso y sistemático.

Regla: **un fix sellado antes de la desconexión no decide nada** — ni planta ni aborta. Es la misma
doctrina de siempre (*un evento re-entregado nunca coloca un pin*) aplicada al fix en vez de al
evento. `GpsPoint.timestamp` es `Location.time`, reloj de pared, comparable directamente.

Un fix **sin sello** (`timestamp <= 0`, que Android nunca produce) se juzga normal: negarse a
aparcar por falta de sello sería un fallo peor que el que se evita, y es la misma rama permisiva que
`isCredibleDrivingSpeed` ya documenta para la accuracy nula.

### 2 · El candidato-hunt vive DENTRO de su veredicto

Por [DET-VERDICT-NOT-PREDICATE-001] esto **no** es un caso de uso nuevo: "quién gana el hunt" no
aparece en ningún vocabulario de diagnóstico, sólo alimenta al veredicto que ya existe. Va como
estado plegable dentro de `EvaluateBtParkUseCase` (`BtCandidateHunt` + `foldCandidateFix`), que es su
veredicto — y así la regla queda testeable con una lista de fixes en `commonTest`, en vez de vivir
como bucle no testeado en `androidMain`.

### 3 · Los dos relojes atados por la firma

`evaluateWalkAway(candidate, current, elapsedMs)` → `evaluateWalkAway(candidate, candidateAtMs,
current, nowMs)`. El elapsed se calcula dentro. Así el call site no puede dar una base temporal que
no sea la del propio candidato: el "desde dónde" y el "desde cuándo" viajan juntos en la llamada.

### 4 · Lo que NO se hace

- **No se toca `stoppedSpeedThresholdMps = 1f`.** Con el muestreo temprano deja de ser el mecanismo
  de arrastre (el primer fix llega con el user aún en el coche o bajándose, por debajo de 1 m/s). Es
  una calibración aparte y sin dato de campo que la sostenga.
- **No se toca la precisión del pin.** El gate usa `minGpsAccuracyForDriving = 50 m`, que es el
  umbral de *credibilidad de conducción* reutilizado como "pin-grade" (el KDoc del detector lo llama
  así; la constante no lo es). Un pin con 50 m de error es otro problema, con otra decisión detrás
  (¿cuánta precisión exige COLOCAR?) y sin constante propia hoy en `ParkingDetectionConfig` →
  **follow-up**, no scope creep aquí.
- **No se mantiene GPS durante el viaje** para tener ancla como el Coordinator. Sería la solución
  "completa" y cuesta batería en el carril que hoy no gasta nada; el muestreo temprano recupera la
  mayor parte del error a coste ~0. Si el campo demuestra que no basta, se reabre.

## Criterio de éxito

- Test: viaje real — DISCONNECT, fix parado a los 3 s sobre el coche, fixes de caminata después →
  **el pin es el de los 3 s**, no el último.
- Test: corte a mitad de marcha — fix parado en el semáforo a los 2 s, fix de conducción a los 15 s
  → **aborta** (hoy el candidato de los 2 s ni se ve).
- Test: el candidato más temprano no lo reemplaza otro posterior.
- Test: techo agotado con candidato en mano → se usa, no es `bt_gps_timeout`.
- Test (el acoplamiento del §3): 35 m caminados en 28 s **medidos desde el candidato** →
  `WalkAwayConfirmed`; los mismos 35 m medidos desde el arranque del walk-away → `DrivingAbort`.
  Es la aserción que demuestra que el fix del muestreo sin el §3 sería una regresión.
- **Verificación por falsación**: neutralizar el "gana el más temprano" y ver rojo. Un test que no se
  ha visto fallar no demuestra nada.
- ⏳ Campo (necesita el **Kamiq**): en el `parkdiag`, `pin lag` de la línea de confirmación por
  debajo de ~10 s, y el pin sobre el coche. Hoy ese número sería ≥ 30 000 ms por construcción.

## Provenance / telemetría

**Ningún `detectionPath` nuevo** — no hay camino nuevo de confirmación, el mismo veredicto se toma
con un fix mejor. Sí hace falta poder medirlo:

- Línea PARKDIAG de la confirmación con la **edad del candidato** (`pin lag`): es el número que
  valida o tumba este ticket, y sin él "el pin cayó bien" es indistinguible de "tuvimos suerte".
- Remoto: esa misma edad viaja en `DepartureVerdict.enterAgeMs`, la columna *"cómo de viejo era
  esto"* que el DTO ya reutiliza para tres eventos distintos. **Sin cambio de esquema**, sin barrido
  DTO ⇄ Entity ⇄ dominio.

## Consumidores auditados

`grep -rn "evaluateWalkAway\|evaluateCandidateFix\|evaluateEngagement\|BT_DISCONNECT_DEBOUNCE\|GPS_SAMPLE_TIMEOUT" shared/src app/src --include=*.kt`

| Sitio | Qué asume | Clasificación |
|---|---|---|
| `BluetoothParkingDetector` | el pin es el primer fix tras el debounce | ⛔ **donde mordió** — se cierra |
| `EvaluateBtParkUseCaseTest` (7 tests de walk-away/candidate) | firma `elapsedMs` suelta | ⛔ se migran a la firma de dos instantes |
| `BluetoothDetectionService` | sólo lanza/cancela el job; no sabe de fixes | ✅ exento — no toca posición |
| `BluetoothConnectionReceiver` | resuelve vehículo y arbitra sobre el Coordinator | ✅ exento — no toca posición |
| `CoordinatorDetectionStrategy` y su ancla | carril separado, ancla propia congelada al final de la conducción | ✅ exento con razón — ⛔ los carriles no se mezclan |
| `EvaluateSafetyNetCheckUseCase` / `ParkingBackfillWorker` | colocan por presupuesto de llegada | ✅ ya cerrado por [DET-DEPARTURE-IS-NOT-ARRIVAL-001] |
| `SaveManualParkingUseCase` | posición elegida por el user | ✅ exento con razón |

`EvaluateBtParkUseCase` no tiene más consumidores que el detector y su test: el barrido completo son
dos ficheros de producción.

## Estado de la implementación

| Fichero | Cambio |
|---|---|
| `domain/usecase/detection/EvaluateBtParkUseCase.kt` | `BtCandidate` (fix + instante, un sello) · `BtCandidateHunt(sinceMs)` + `foldCandidateFix` (gana el más temprano · abortar es terminal · un fix anterior a la desconexión no decide) · `evaluateWalkAway` pasa a `(candidate, current, nowMs)` |
| `bluetooth/BluetoothParkingDetector.kt` | muere el `delay`; una sola observación desde la desconexión con techo `debounce + sampleTimeout`; el debounce pasa a ser condición de parada, no de arranque; `pin lag` en el log y en `enterAgeMs` |
| `commonTest/.../EvaluateBtParkUseCaseTest.kt` | 4 tests del hunt + 2 del fix cacheado + el del acoplamiento de los dos relojes; los 7 de walk-away migrados a la firma nueva |
| `docs/detection/PARKING-DETECTION.md` | §1.2 pasos 1-3 reescritos (estaban ya desfasados: citaban `GPS_ACCURACY_THRESHOLD_M` y no mencionaban el gate de reposo) + entrada de §2 |

**Suite: 1.801 tests, 0 fallos.** `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` OK.

### Criterio de éxito — estado

- ✅ gana el candidato más temprano · ✅ conducción posterior a un candidato parado aborta ·
  ✅ el abort es terminal · ✅ ningún fix usable → sin candidato y sin abort (el detector reporta
  `bt_gps_timeout`)
- ✅ el acoplamiento de los dos relojes, con **las dos mitades asertadas**: 35 m en 28 s desde el
  candidato confirman; los mismos 35 m clocados desde el arranque de la vigilancia abortan
- ✅ el fix cacheado anterior a la desconexión ni planta ni aborta, y el siguiente fix bueno sí
  planta (las dos mitades del peligro, asertadas por separado)
- ✅ **falsación ×2**: neutralizado el "gana el más temprano" (el fold reemplaza siempre) → cae
  `should_pinTheEarliestStationaryFix_when_laterOnesAlsoQualify` y **sólo** ese. Quitado el guard de
  sello → caen los **dos** tests del fix cacheado y sólo esos. Ambos restaurados.
- ⏳ Campo: pendiente del Kamiq. Leer el `pin lag` de la línea de confirmación (o `enterAgeMs` en
  remoto) y de paso zanjar cuándo dispara el `ACL_DISCONNECTED` de ese coche.

### Sin impacto en

Dev Catalog / galería de estados (ni pantalla, ni estado MVI, ni routing), strings (ninguno nuevo),
`detectionPath` (ningún camino nuevo), esquema del DTO (`enterAgeMs` ya existía).

## Follow-ups abiertos

- `docs/backlog/det-bt-pin-grade-is-not-a-driving-threshold-001.md` — la barra de precisión con la
  que se COLOCA no puede seguir siendo el umbral de credibilidad de conducción.
