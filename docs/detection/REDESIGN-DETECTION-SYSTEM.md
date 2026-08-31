# Rediseño del motor de detección — documento de trabajo

> **Estado:** 🔵 en construcción, noche del 29→30-08-2026.
> Este documento existe para que nadie tenga que acordarse de nada. Todo lo que se mide, se escribe
> aquí. Las secciones marcadas ⏳ están pendientes de las tres investigaciones en curso.

---

## 0 · Por qué existe este documento

Ocho meses corrigiendo falsos positivos de uno en uno. Cada FP de campo ha producido un guard nuevo,
y cada guard ha cerrado **la puerta por la que entró ese FP concreto**, dejando abiertas las de al
lado. La noche del 29-08 produjo tres fallos distintos en cuatro horas, y los tres tienen la misma
forma. Eso ya no es mala suerte: es un defecto de diseño que hay que nombrar y arreglar de raíz.

**La tesis a validar o refutar con datos** (§2 y §3):

> Casi todos nuestros fallos son *permisos concedidos por omisión*. El código enumera los casos malos
> conocidos; cualquier caso nuevo o desconocido pasa por defecto hasta el día que muerde en campo y
> se gana su línea en la lista. Un sistema así no puede converger: su tasa de fallo depende de
> cuántos casos raros ha visto ya, no de ninguna propiedad que podamos demostrar.

---

## 1 · Evidencia de campo del 29→30-08-2026 (medida, no inferida)

Dispositivo: **Redmi 2201117TY**, uid `itmGbBxaz8ZJkLUlwvOnWDnMDto1`, estrategia **Coordinator**.
Fuente: `parkdiag.log` del propio móvil (5.317 → 5.714 líneas) + Firestore.
Contexto: la app se reinstaló como paquete nuevo (`com.rndeveloper.paparcar`) el 29-08 a las 21:36,
lo que la dejó con base de datos vacía y en una cuenta distinta — de ahí la sensación de "hemos
perdido los aparcamientos". **No se perdió nada**: el historial anterior sigue en Firestore bajo
`WZB7oftWLDY1toGJrDwoRHnnYHx2` (Redmi) y `fiypNbElGlfFexLMpU9sNaMjRMD3` (Oppo).

### 1.1 Los cuatro desenlaces de la noche

| hora | sesión | armEvidence | detectionPath | desenlace | veredicto |
|---|---|---|---|---|---|
| 21:47→22:19 | `092c74d7` | `verified_late` | `steps+egress` | pin Calle del Vivero 10A, viaje de 9,3 km | ✅ **correcto** |
| 23:47→23:56 | `c6a57fad` | `enter_at_car` | `steps+egress` | pin "La Parafarmacia", fiabilidad 0.9 | 🔴 **FALSO POSITIVO** |
| 00:13→01:22 | varias | `self_observed` | — | abortos honestos en silencio | ✅ correcto |
| 01:20→01:49 | `825dcb60` | `self_observed` | `unattended_zone_gap_anchor` | pin a **142 m** de casa, fiabilidad 0.5, zona 250 m | 🔴 **PIN EN SITIO EQUIVOCADO** |

El control positivo importa: **la detección de viajes reales funciona**. El viaje de las 21:47 trae
`drive PROVEN by track`, `sustained drive 30001ms` y `MOTOR witnessed 40074ms`. El de la 01:22 trae
`drive PROVEN`, `sustained drive 45021ms` y `MOTOR witnessed by displacement 29.1 m/s`. Lo que falla
no es *ver el coche*: es **decidir** y **colocar el pin**.

### 1.2 FP de la parafarmacia — reconstrucción fix a fix

```
23:47:41  IN_VEHICLE ENTER  → bus stamped (lag=89019ms)          ← el AR llega 89 s tarde
23:47:44  AR ENTER at own fence — arming, waiting for ride proof (dep=enter_at_car)
23:47:44  loc#1  36.5993395,-6.2516270  speed=0.00  acc=16.4m
23:47:52  loc#2  36.5990717,-6.2508977  speed=7.71  acc=16.1m
          ✓ hasEverReachedDrivingSpeed → true (7.71≥5.0) dist=71.59m [BUG-SHORT-TRIP]
23:47:55  loc#3  36.5993522,-6.2515334  speed=1.52  acc=11.6m    ← vuelve 64,8 m ATRÁS en 3,5 s
23:47:59  loc#4  36.5992437,-6.2513251  speed=0.25  acc=11.3m    ← coordenada exacta del pin
23:48:13  ⚓ anchor FROZEN                                        acc=63m
          …acc sigue degradándose a 123 m, 220 m, 251 m, 180 m
23:54:23  ▶ steps+egress (steps=8 kinematicFixes=0) → fast confirm
23:54:23  ⏸ tentative confirm — holding 120000ms [DET-C-02]
23:56:28  ✓ hold settled (held=125003ms, userYes=false) — finalizing
23:56:28  → confirmParking(reliability=0.9, path=steps+egress)
```

Hechos medidos:

- Desplazamiento **neto** armado → pin: **29 m**, con fixes que declaran 16 m de precisión.
- El "viaje" fueron 71,6 m de ida **deshechos 64,8 m** 3,5 s después. Los 244,9 m de
  `routeDistanceMeters` son la suma del zigzag, no terreno cubierto.
- `hasEverMoved = false` en **todas** las líneas de estado de la sesión.
- **`DriveProof.proven == null` durante toda la sesión**: no existe ni una línea `✓ drive PROVEN`
  entre el armado (línea 2602 del log) y el confirm (línea 2937).
- `kinematicFixes = 0`, `vehicleExit = false`.

### 1.3 Pin equivocado en casa — reconstrucción

```
01:22:29  ✓ drive PROVEN by track (pendingMax=8.51m/s)
01:29:42  ✓ sustained drive — 45021ms en la banda de conducción
01:31:03  ✓ MOTOR witnessed by displacement — 29.1 m/s sostenidos
01:34:29  ▶ weak-evidence prompt notification POSTED (score=0.6)
01:34→01:49  ？ confirm degraded to user prompt (reason=anchor_gap_entered)   ×~200 veces
01:49:32  → confirmParking(reliability=0.5, path=unattended_zone_gap_anchor, zoneRadius=250.0)
```

El viaje fue real y se midió bien. Al parar hubo un **hueco de GPS**, así que el ancla quedó
congelada en un punto de paso de Av. de Fuentebravía. El sistema lo detectó (`anchor_gap_entered`),
se negó a confirmar y preguntó — **correcto**. Nadie contestó en 15 minutos. Y entonces el guardado
desatendido plantó el pin **en esa misma ancla que ya había declarado inválida**.

Distancia del pin (`36.6098405, -6.2784644`) a la casa (`≈36.6086, -6.2781`): **142 m**.

### 1.4 El hallazgo que reencuadra el problema: armamos 28 veces para acertar una

Ventana del log: **29-08 21:45:37 → 30-08 01:59:19** = 4 h 14 min. Contado sobre `parkdiag.log`:

| medida | valor |
|---|---|
| `coordinator.invoke() entry` (armados) | **28** |
| de ellos, `⊘ false-ENTER abort` (el usuario ANDANDO, no conduciendo) | **23** |
| de ellos, terminaron en pin | **3** |
| pines correctos | **1** (Calle del Vivero) |
| pines malos | **2** (parafarmacia FP · casa a 142 m) |

Distribución por hora: 1 · 7 · 8 · 7 · 5. **Un armado cada 9 minutos**, sostenido toda la noche, con
el coche parado. Los `honest close` de esos 23 abortos reportan `pinDist` entre 22 y 69 m: el
usuario moviéndose por su casa, a metros del coche aparcado.

**Esto reencuadra el diagnóstico.** No tenemos un problema de confirmación, tenemos un problema de
**armado**. Cada arranque es un billete de lotería para un falso positivo, y estamos comprando 28
por noche estando quietos. Los ocho meses de trabajo han ido a poner guardas *aguas abajo* del
armado — `too_close`, `false-ENTER abort`, `weak-evidence`, `anchor freeze` — que es defender el
último metro en vez de no abrir la puerta. Basta con que uno de los 28 se cuele (el 23:47 se coló)
para tener el FP de la noche.

Es también, y por el mismo motivo, la factura de batería: 28 sesiones con GPS a cadencia alta y
podómetro activo para producir **un** pin útil.

**Consecuencia de diseño (P7, ver §3):** la métrica que hay que optimizar no es "cuántos FP se
filtran" sino **cuántas veces armamos sin que haya un viaje**. Con 25 armados espurios menos, la
mayoría de los guards aguas abajo dejan de ser necesarios.

#### De qué disparador sale cada armado

```
24  trigger=SIGNIFICANT_MOTION      ← 86 % de todos los armados
 3  trigger=AR_VEHICLE_ENTER
 1  trigger=GEOFENCE_EXIT
```

Intents recibidos por el servicio en la misma ventana:

```
61  ACTION_SENTRY_WAKE       ← el cooldown frenó 37, dejó pasar 24
 6  ACTION_AR_TRANSITION
 3  ACTION_GEOFENCE_EXIT
 1  ACTION_RESUME_SENTRY
```

Transiciones AR crudas de toda la noche: **6** `IN_VEHICLE ENTER`, 5 `IN_VEHICLE EXIT`, 1
`ON_BICYCLE ENTER`, 1 `ON_BICYCLE EXIT`.

**Dos problemas distintos, y ninguno se arregla aguas abajo:**

1. **El sentry-wake por movimiento significativo produce el 86 % de los armados y cero pines
   buenos.** El sensor de significant motion se dispara con el TELÉFONO, no con el coche: no
   distingue "el motor arranca" de "he cogido el móvil de la mesa". 24 sesiones con GPS a cadencia
   alta y podómetro activo, ninguna útil. Es simultáneamente la factura de batería y la fábrica de
   oportunidades de FP.
2. **La señal en la que sí confiamos acierta 2 de 3.** El AR emitió 6 `IN_VEHICLE ENTER` en cuatro
   horas; 3 armaron; **uno de esos 3 fue el falso positivo de la parafarmacia**, con el ENTER
   llegando con `lag=89019ms`. Cerrar el confirm (§7) convierte ese caso en una pregunta, que es
   correcto, pero no evita el armado ni el gasto.

Corolario para el rediseño: mientras el armado dependa de un sensor de movimiento del teléfono, la
tasa de FP dependerá de cuánto se mueva el usuario por su casa. Eso no es un sistema, es una apuesta.

### 1.5 La duda se calcula y se tira

`confirmParking` recibió `zoneRadius=250.0`. En Firestore el documento **no tiene**
`zoneRadiusMeters` ni `isApproximate` — es local-only por diseño (`ParkingSessionMapper.kt:111`), así
que vive sólo en Room. Pero en la pantalla:

- `ParkingPeek.kt:114` → sí pinta `ApproximateZoneRow`.
- `PaparcarMapView.kt:1275` → sí pinta el círculo.
- **`ParkingHistoryDetailScreen.kt` → no lee `zoneRadiusMeters` ni `isApproximate` en ningún sitio.**

Resultado: un pin que el propio sistema marcó como *zona de 250 m con 0,5 de fiabilidad* se presenta
en Historial como un pin exacto, con botón "Navegar a esta ubicación". La app sabía que dudaba y no
lo dijo.

---

## 2 · El defecto de diseño, nombrado

Los tres fallos de la noche son **la misma forma**:

| # | dónde | qué hace la salvaguarda cuando no se cumple su condición |
|---|---|---|
| 1 | política de evidencia débil | la etiqueta no está en la lista negra → **deja pasar** |
| 2 | `unattended_zone_gap_anchor` | la pregunta no se contesta → **planta igual**, y en el ancla que declaró inválida |
| 3 | Historial | hay duda registrada → **no la pinta** |

Ninguna de las tres BLOQUEA. Una deja pasar por omisión, otra espera y luego deja pasar, la tercera
calcula la duda y la descarta. **Fallan abiertas.**

Y la doctrina del proyecto dice exactamente lo contrario: *fallo asimétrico, mejor un falso negativo
que un falso positivo; ante la duda se PREGUNTA*.

### El caso testigo: `enter_at_car`

La política leía una lista de etiquetas "débiles":
`verified_enter`, `verified_late`, `self_observed`, `arrival_handoff`. Cada una entró en esa lista el
día que produjo su propio FP de campo. `enter_at_car` no estaba porque todavía no había mordido —
así que era **fuerte por omisión**, pese a que su propio KDoc dice:

> *"Arms the coordinator 'waiting for ride proof': deliberately NOT a verified departure (no seed),
> so the session must measure the drive itself"*

La intención estaba escrita. La política no la aplicaba. Y la clasificación correcta **ya existía**
en el mismo fichero, como `when` exhaustivo que el compilador obliga a rellenar
(`ArmEvidence.driveAuthorization`), donde `BoardingAtCar` está bien marcado como `None`.

Había dos fuentes de verdad para lo mismo, y el sitio que decide usaba la copia manual.

---

## 3 · Principios del sistema nuevo

Provisionales — se confirmarán o corregirán con las tres investigaciones (§4, §5, §6).

**P1 · Fallar cerrado, siempre.** Toda decisión de detección enumera lo que SÍ vale, nunca lo que no.
Un caso desconocido pregunta; jamás confirma. Corolario: prohibidas las listas de strings a mano
donde exista un `when` exhaustivo sobre el tipo sellado.

**P2 · La nominación nunca es la prueba.** Un evento (AR ENTER, EXIT de geocerca, motion) sólo arma.
Sólo `DriveProof` medido confirma. Ninguna ruta de confirm puede leer una bandera de nominación.

**P3 · Una pregunta es una puerta, no un retraso.** Un prompt sin contestar no puede convertirse en
un pin pasado un timeout. Si al expirar el reloj no hay más evidencia que al empezar, el desenlace
es cerrar sin pin, no plantar con fiabilidad rebajada.

**P4 · Evidencia declarada inválida no se reutiliza.** Si un ancla se descarta por hueco de GPS, esa
ancla no vale tampoco para el guardado desatendido, ni para el backfill, ni para nada.

**P5 · La duda viaja hasta el ojo del usuario.** Si el sistema calcula un radio de incertidumbre, ese
radio se persiste y se pinta en **todas** las superficies que muestran el pin. Un pin de 250 m no
puede parecerse a uno de 2 m.

**P6 · Un invariante, un sitio.** Cada regla vive en un único punto y se barren todos sus
consumidores. Dos evaluadores donde uno es superconjunto del otro son un solo evaluador.

**P7 · Despertar barato, armar caro — dos niveles.** Medido el 29-08: 28 armados para 1 viaje, 24 de
ellos por `SIGNIFICANT_MOTION`.

⚠️ **La lectura ingenua de ese dato es errónea y hay que decirlo**: "despertar menos" produciría
falsos NEGATIVOS, porque el significant motion es precisamente lo que impide perderse un viaje. El
defecto no es despertar 61 veces — es que **cada despertar se convierte de inmediato en una sesión
completa**, con stream de GPS a cadencia alta, podómetro y una máquina de estados capaz de plantar
un pin. Estamos pagando el precio de una sesión por cada vez que el usuario coge el móvil de la mesa.

La arquitectura correcta son **dos niveles con derechos distintos**:

- **Nivel 1 · vigilancia barata.** Despierta con todo (significant motion, AR, geocerca). Toma una
  muestra mínima —un fix, un par de lecturas— y responde una sola pregunta: *¿esto se parece a un
  vehículo saliendo?* Puede dispararse 61 veces por noche sin coste apreciable. **No puede plantar
  nada, ni abrir sesión, ni encender el stream.**
- **Nivel 2 · sesión de detección.** Sólo se promociona desde el nivel 1 con evidencia clara de
  viaje (desplazamiento real, no un sample de velocidad). Es el único que enciende GPS a cadencia
  alta y el único con derecho a confirmar.

Así se conservan íntegras las defensas contra el FN —seguimos despertando ante todo— y desaparecen a
la vez la superficie de FP y la factura de batería. Es además lo que hace la competencia
(`reference_driversnote_detection_stack`: *FGS sólo en `moving`*), pendiente de confirmar en §5.

Métrica de calidad del motor: **promociones a nivel 2 por viaje real**, con objetivo ≈1. Los
despertares de nivel 1 no se cuentan porque no deben costar.

---

## 4 · ⏳ Catálogo histórico de FP y FN

_(pendiente — investigación en curso sobre `docs/backlog/`, `PARKING-DETECTION.md`, git log, KDoc de
`domain/detection/` y los `Trace_*` de commonTest)_

Lo que tiene que responder: cuántos incidentes hay, en qué familias caen sus causas, **cuáles han
reincidido después de darse por cerrados**, y qué guards se solapan o se contradicen.

## 5 · Cómo lo hacen las apps que funcionan

> Barrido de ~25 apps y SDK del sector con fuentes citadas. _(Queda pendiente el informe en
> profundidad de Google Maps / Driversnote / MileIQ, en curso.)_

### 5.1 Los patrones universales

**U1 · Dos etapas: trigger barato → confirmación cara.** Nadie enciende el GPS por una sospecha. Un
clasificador de bajo consumo (AR, energía del acelerómetro, SLC) **arma**, y sólo entonces se
enciende el GPS para confirmar velocidad sostenida. Damoov, DriveQuant, Sentiance y Radar,
idénticos. → **valida la Pieza 5.**

**U2 · Debounce en las transiciones. Ninguna decisión con una sola lectura.** Damoov, literal:
*"las transiciones tienen debounce para que una lectura ruidosa no pueda cambiar el estado"*; su
máquina `IDLE → CANDIDATE → DRIVING → TRIP END` exige **velocidad GPS sostenida varios segundos**
para pasar de CANDIDATE a DRIVING. iOS Core Motion expone `confidence` que **baja durante la
transición**.

> Éste es el hallazgo que más directamente nos señala. El FP de la parafarmacia fue **un** fix a
> 7,71 m/s volteando `hasEverReachedDrivingSpeed`. No nos falta un guard: nos falta el patrón básico
> que usa todo el sector.

**U3 · Suelo mínimo de viaje, y por DOS condiciones cuando la señal es floja.**

| sistema | suelo |
|---|---|
| Life360 | ≥0,5 millas **Y** >15 mph (si no, degrada de "Drive" a "Trip") |
| Traccar | 500 m **o** 300 s |
| Sentiance | no rompe la geocerca hasta 200–300 m **o** 2–3 min |
| Samsara | ≥5 mph para abrir el viaje |
| **Radar.io** | parada = **140 s Y <70 m**, las **dos** — "porque ninguna sola es fiable" |

→ **valida empíricamente el umbral de §6.1**, y añade que con GPS+AR (nuestro caso) hay que exigir
**dos** condiciones, no una. Y el matiz de Life360 es nuestra propia doctrina: *no se descarta, pierde
autoridad*.

**U4 · Estado `provisional` reprocesado al final del viaje.** Sentiance marca los eventos en tiempo
real como **`provisional`** y los reprocesa post-trip, específicamente para eliminar *"colas idle y
eventos espurios de fin de conducción"*. Damoov rellena hacia atrás los primeros segundos desde un
buffer circular mientras el GPS calienta.

> Esto es **más fuerte que nuestra "verificación tardía"**: es un estado de dos fases en el modelo de
> datos que permite **mover o retirar un pin ya colocado**. Es exactamente lo que le faltaba al pin a
> 142 m de casa: cuando el usuario llegó a su portal, el sistema tenía datos para corregir el ancla,
> y no tenía forma de hacerlo.

**U5 · El timeout de parada escala con la calidad de la señal.**

| señal disponible | timeout del sector |
|---|---|
| señal dura (desconexión BT, ignición, puerta) | segundos / instantáneo |
| **GPS + AR (nuestro caso)** | **3–5 min** (Sentiance 3 · DriveQuant 4 · Traccar y Samsara 5) |
| sólo GPS | 140 s **+ segunda condición** (Radar) |

Nadie baja de 140 s sin señal dura; nadie pasa de 5 min. Sentiance añade un timeout duro de
seguridad de 2 h. Damoov explica el compromiso: *"muy corto y un viaje se parte en fragmentos; muy
largo y un recado y la vuelta a casa se funden en uno"*.

**U6 · Zonas conocidas degradan la CONSECUENCIA, no el detector.** ParKing permite zonas donde el
aparcamiento **se guarda en silencio, sin notificar** (casa, oficina). CMT tipifica el viaje usando
el histórico de viajes previos del usuario. La respuesta del mercado a "aparco en mi casa 300 veces
al año" no es afinar el detector: es cambiar qué se hace con el resultado.

**U7 · Preguntar está normalizado, y es producto.** Parkify pide confirmación en coches ajenos;
Android Auto pregunta al llegar; EasyPark manda push "te has ido sin parar la sesión". Nadie lo
presenta como carencia. → nuestro *ante la duda se pregunta* va con el mercado.

**U8 · Confianza explícita con estado "indeterminado".** La patente de Apple (US9080878B2) es la
formulación más limpia de nuestro fallo asimétrico: score ponderado de ~10 señales y, si no llega al
umbral, el estado **no es "no aparcado", es `undetermined`, y se conserva el último estado
conocido**. No se decide.

**U9 · El pin del aparcamiento caduca.** Google Maps: 48 h, o hasta detectar que vuelves a conducir.
Higiene contra el estado zombi — nosotros tenemos ahora mismo pines `isActive: true` de hace tres
días.

**U10 · Apagar la localización cuando el usuario está parado.** Foursquare: el SDK está inactivo el
**95 % del día**. Radar: se apaga parado y exige >100 m para despertar. Menos tiempo en background =
menos superficie de OEM-kill, que es nuestra otra guerra.

### 5.2 Cifras públicas (y el silencio revelador)

| métrica | cifra | fuente |
|---|---|---|
| conductor vs pasajero, exactitud | 96,5 % (n=57, revisado por pares) | PMC12416331 |
| idem, especificidad | 91,2 % — **DE de 14,8**, enorme | ídem |
| modo de transporte | 91 % global, >95 % en "vehicle" | Sentiance |
| coste del lag de 3 min de Sentiance | sólo 1–2 % de precisión | Sentiance |

**Cero cifras públicas de precisión/recall de detección de FIN de viaje.** Nadie —ni Zendrive, ni
Arity, ni CMT— publica su tasa de viajes perdidos ni de viajes falsos. El estudio revisado por pares
tuvo que **pedir a los participantes que reportaran los viajes que la app no detectó**, lo que
confirma que el FN sigue sin resolverse incluso en el líder del sector.

> Conclusión sobria: **este problema es difícil de verdad y nadie lo tiene resuelto del todo.** Lo
> que sí tienen los que aciertan es *estructura* — debounce, dos etapas, suelos mínimos, estado
> provisional — no umbrales mágicos.

### 5.3 El competidor directo

**SpotAngels** hace exactamente nuestro producto: BT o movimiento → guarda tu plaza **y publica
automáticamente la que dejas libre** a la comunidad, con *"la mayoría de las plazas del mapa añadidas
automáticamente"*. Merece un teardown propio: es el único que ya resolvió el problema de producto
entero, no sólo el de detección.

### 5.4 Un dato incómodo que registro sin recomendarlo

La industria usa la señal Bluetooth **dentro** del mismo pipeline probabilístico, como *identidad de
vehículo y como veto* — Arity dice explícitamente que su beacon BLE existe *"para reducir la captura
de viajes falsos (p. ej. ir en taxi)"*; DriveQuant usa iBeacon para detectar **antes** de que el
coche arranque. Nuestra doctrina separa BT y Coordinator en dos estrategias que "NUNCA se mezclan".

Se anota como hecho medido, no como propuesta: **somos una excepción del sector en esto.** La
decisión de mantener la separación es del proyecto y tiene su razón (no contaminar el scoring).

## 6 · Mapa end-to-end e inventario de fallos abiertos ✅

### 6.0 El hallazgo de fondo

> **La ruta `steps+egress` — la que más pines planta — no consulta la prueba de conducción en
> absoluto.** Se apoya en que `driveAuthorized` (la nominación) ya dejó pasar el fix. El evaluador
> sólo mira `sustainedDrivingMs` para `hasKinematicProof` y para `weakEvidenceOnly`; el camino
> principal no pasa por ninguno de los dos.

`PreDriveSkipStage.kt:17-20` avisa en su propio KDoc: *"passing this gate proves nothing about the
trip"*. Tres etapas más abajo se trata como si lo probara.

### 6.1 Estadística de nuestra propia telemetría — el separador limpio

148 sesiones COORDINATOR de la cuenta del Redmi (agosto 2026), cruzando desenlace contra
`drivingFixes` (fixes a ≥ 5,0 m/s con precisión creíble):

| desenlace | n | `drivingFixes` mín/mediana/máx |
|---|---|---|
| `aborted_false_enter` (ruido: el usuario andando) | 125 | **0 / 0 / 2** |
| `confirmed_steps+egress` | 8 | **7 / 53 / 68** |
| `confirmed_user` | 2 | 31 / 50 / 50 |
| `confirmed_unattended_zone_egress_mismatch` | 1 | 54 |
| `aborted_no_movement` | 5 | 0 / 0 / 1 |

**Confirmados con `drivingFixes == 0`: CERO.** El ruido nunca supera **2**; el viaje real más flojo
tiene **7**. Hay un hueco limpio con margen por ambos lados.

Contado sobre el `parkdiag` de la noche del 29-08, las tres sesiones que produjeron pin:

| sesión | fixes | `drivingFixes` (≥5 m/s) | vmax | veredicto |
|---|---|---|---|---|
| FP parafarmacia 23:47 | 102 | **1** | 27,8 km/h | 🔴 falso positivo |
| viaje real 21:47 | 324 | **86** | 58,0 km/h | ✅ pin correcto |
| casa 01:20 | 376 | **44** | 43,1 km/h | 🔴 pin a 142 m (viaje real, ancla mala) |

**El FP tiene `drivingFixes = 1`, dentro de la banda de ruido (≤2).** Un umbral conservador en
**≥ 5** habría matado el falso positivo y no habría tocado ninguno de los 11 confirmados legítimos
del histórico. Este es el invariante empírico central del rediseño y sale de nuestros propios datos,
no de una intuición.

> 🔴 **CORREGIDO al implementar la Pieza 1 (30-08), con dos mediciones. Las dos cifras de arriba
> están mal y el umbral de 5 también.**
>
> 1. **La sesión de casa tiene 7 `drivingFixes`, no 44.** El 44 sale de contar `speed >= 5` **sin la
>    puerta de precisión**, y `DriveProof.credibleFixCount` sí la aplica (`credibleSpeedFix &&
>    speed >= minimumTripSpeedMps`). 37 de esos 44 fixes traían accuracy > 50 m — era la noche del
>    agujero de GPS. Con la definición real del contador, el margen del umbral no es 39, es **2**.
> 2. **Un umbral de 5 SÍ toca un confirmado legítimo, y lo dijo un test.** El replay
>    `calle_gavia_001_correct_detection_still_anchors_at_calle_gavia` (traza del 04-07, el stream
>    esquelético de MIUI que este repo cita como el peor trayecto real del histórico) se puso rojo:
>    *«the correct park must save expected:1 but was:0»*. Esa traza tiene **2 fixes creíbles de 11**.
>
> **El bar implementado es 2**, y no es un número nuevo: es la regla LONE-SAMPLE que el proyecto ya
> aplica en `DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001` («run 1 of 2»). Un contador de fixes mide
> la densidad con que muestreó el OS, no si un coche se movió: es la más DÉBIL de las tres
> condiciones de `Measured`. El peso lo llevan las dos físicas —excursión ≥150 m y banda ≥30 s—, que
> el FP falla ambas (72 m, 0 ms) y Calle Gavia pasa ambas con holgura (543 m, 36 s).
>
> Lección, que es la misma que este documento predica: *una cifra que no se ha contado con la
> definición del código es una intuición con aspecto de dato*.

### 6.2 Inventario de fallos abiertos (16 sitios, con fichero:línea)

Rutas relativas a `shared/src/commonMain/kotlin/com/rndeveloper/paparcar` (`$C`) y
`shared/src/androidMain/kotlin/com/rndeveloper/paparcar` (`$A`).

| # | sitio | qué decide | qué pasa con un caso nuevo/desconocido |
|---|---|---|---|
| 1 | `$C/domain/usecase/parking/EvaluateParkingDecisionUseCase.kt:281` `weakLabels` | silencio vs pregunta | pasa → **pin silencioso**. *(cerrado en §7)* |
| 2 | mismo fichero `:297` `humanPowered` | si puede auto-confirmar | un `VehicleType` nuevo (e-bike, patinete) **no** es human-powered → auto-confirm. *(cerrado el 31-08, `VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001`)* |
| 3 | `$C/domain/detection/ParkingStrategyResolver.kt:131` `NON_PARKING_TYPES` + `:104` | si se detecta | tipo desconocido o sin vehículo → **COORDINATOR activo**. *(el set murió el 31-08, mismo ticket; “sin vehículo → COORDINATOR” sigue siendo deliberado)* |
| 4 | `$C/domain/detection/ParkingDetectionSource.kt:58-68` | cómo se presenta el pin | `else -> Assisted` (existiendo `Unknown`); y `startsWith("bt")` por **prefijo** |
| 5 | `$C/domain/usecase/detection/EvaluateGeofenceExitUseCase.kt:88` | confianza del EXIT | `deliveredAtMeters == null` → **boundary**, la máxima confianza, sin probe ni sello |
| 6 | `$C/domain/usecase/parking/EvaluateBackfillDeferralUseCase.kt:53` | diferir o plantar | sello nulo/ilegible/reloj atrás → **planta el pin** |
| 7 | `$A/detection/service/CoordinatorDetectionService.kt:1202` | escribir el sello | sólo sella `GAP_ANCHOR` de **8** razones → las otras 7 las re-decide el backfill |
| 8 | `$C/domain/detection/physics/EvidenceAdmissibility.kt:33` | admitir evidencia | `sessionStartMs == null` → **admite** (un AR re-entregado vuelve a valer) |
| 9 | `$C/domain/model/ParkingDetectionConfig.kt:1332` `isCredibleDrivingSpeed` | certificar conducción | `accuracyMeters == null` → **certifica** |
| 10 | `$C/domain/detection/state/AnchorPredicates.kt:212` `isEgressBornAtAnchor` | si hay duda del ancla | sin ancla o sin birth → `true` = **no hay duda** (sus 7 hermanas devuelven `false`) |
| 11 | `$C/domain/detection/AssertedPinAuthority.kt:83` | proteger el pin del usuario | `pinReliability` nulo → **no protege** |
| 12 | `$C/domain/usecase/parking/EvaluateParkingDecisionUseCase.kt:346` | la fiabilidad del pin | se elige comparando el `pathLabel` **como string**: camino nuevo → **0.90**, el máximo |
| 13 | mismo `:301` `pathLabel` | qué se traza | `else` incondicional: un prompt sin pruebas se traza como `vehicleExit+window+egress` |
| 14 | `$C/domain/detection/CoordinatorParkingDetector.kt:372` y `$A/.../CoordinatorDetectionService.kt:1500` | con qué evidencia arma | **`armEvidence: ArmEvidence = ArmEvidence.Manual` por defecto** — la evidencia más fuerte del sistema es el valor omitido |
| 15 | `ParkingDecisionInput` `:109-182` | toda la decisión | `egressBornAtAnchor = true`, `anchorGapEntered = false`, `humanPoweredRide = false`, `assertedPinBlocksRelocation = false`… **todos** hacia permitir. *(cerrado el 31-08, `DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001`: se borraron los 12 defaults, y los 2 de su hermano `UnattendedSaveInput` con ellos)* |
| 16 | `$C/domain/model/ParkingDetectionConfig.kt:318` | veto de step tras arm | `enterArmStepVetoMs = 0L` → el veto está **desactivado por defecto** ✅ **cerrado 30-08 BORRÁNDOLO** (`DET-A-VETO-NOBODY-EVER-TURNS-ON-IS-NOT-A-VETO-001`): su desenlace ya lo da el `when` sellado y su un-seed ya tiene camino general |

Contraste que demuestra que el proyecto sabe hacerlo bien: `DetectionEffectDispatcher.kt:92,150,191,229,316`
**revienta** (`error(...)`) ante un valor desconocido. Política opuesta, mismo módulo.

### 6.3 Los relojes — cuatro plantan un pin al vencer

| reloj | valor | al vencer |
|---|---|---|
| `confirmHoldMs` | 2 min | **CONFIRMA** |
| watchdog del hold (`HoldAction.STARVED`) | 2 min 30 s | **CONFIRMA sin fix que lo revalide** — y no tiene **ningún** test |
| fin de stream con hold vivo (`SESSION_ENDED`) | — | **CONFIRMA**, mismo caveat |
| `vehicleExitObservationWindowMs` | 2 min | **CONFIRMA** si hubo AR EXIT — sin pasos ni cinemática, sólo ≥18 m del ancla |
| `confirmationResponseTimeoutMs` | 15 min | decide `SaveExact`/`SaveZone`/`Ask` — el fallo 1.3 |
| `maxNoMovementMs` · `jamExtendedNoMovementMs` · `staleExitNoMovementMs` | 4 / 10 min / 75 s | ABORTAN (los únicos) |

`HoldLifecycle.kt:16` lo admite por escrito: *"Two of these exits **plant a pin with no fix to
justify it**"*.

### 6.4 La cura ya existe en el repo y nadie la replicó

`$C/domain/detection/physics/SessionOutcome.kt:5-11` documenta **exactamente** este defecto y cómo
se curó:

> *"membership was decided by how the string was spelled… Adding an outcome granted or denied it
> three behaviours at once, silently"*

La solución fue declarar la pertenencia en cada caso, con `when` exhaustivo que el compilador
obliga a rellenar. Ese patrón, ya probado aquí dentro, **no se aplicó** a las cinco decisiones que
plantan pines: `weakLabels`, `isVerifiedLabel`, `USER_PLACED_PATHS`, `NON_PARKING_TYPES` y la
elección de `reliability`.

**El sistema sólido no hay que inventarlo: hay que extender a las decisiones que plantan pines el
patrón que ya funciona en `SessionOutcome`.**

### 6.5 Por qué esto empeora al escalar (y qué invariante sí viaja)

Todas las cifras de este documento salen de **dos** dispositivos. El 84 % de armados espurios no es
una constante de la app: depende del OEM (política de Doze de Xiaomi vs Samsung vs Huawei), de la
calidad del acelerómetro, de la vivienda del usuario y de cuánto se mueva por ella. Con mil
dispositivos no habrá un 84 %, habrá **mil tasas distintas**, y la cola mala llegará como reseñas de
una estrella sin log que las acompañe.

Consecuencia de diseño: **un default permisivo con dos móviles es un bug visible; con mil es un bug
invisible.** Los 16 sitios de §6.2 no son deuda técnica, son la superficie de fallo que se
multiplica por la diversidad del parque de dispositivos.

Y el corolario útil: el invariante de §6.1 es bueno precisamente porque **`drivingFixes` es una
medición, no una peculiaridad de sensor**. Un Xiaomi mediocre y un Pixel coinciden en que un coche en
movimiento produce decenas de fixes a velocidad de conducción y una persona andando por su casa
produce cero. Ese umbral viaja entre dispositivos. "Cuántas veces dispara el significant motion" no
viaja, y por eso no puede estar en el camino de decisión.

---

## 8 · EL SISTEMA

> Los umbrales numéricos son los que salen de §6.1 (nuestros datos). §5 puede ajustarlos, no
> cambiar la estructura.

El diagnóstico de §6 dice que no tenemos un motor con bugs: tenemos **decisiones repartidas por
comparaciones de string y defaults permisivos**. Un sistema sólido no es "los 16 sitios arreglados";
es una estructura donde **añadir un caso sin clasificarlo no compile**, y donde plantar un pin sin
conducción medida sea imposible de escribir.

Siete piezas. Las tres primeras son el sistema; las cuatro siguientes lo hacen irreversible.

---

### Pieza 1 · `DrivingEvidence` — un veredicto medido, tipado, y uno solo

Hoy la pregunta *"¿condujo?"* se responde en cuatro sitios distintos con cuatro cosas distintas:
`driveAuthorized` (nominación), `hasEverMoved` (desplazamiento), `DriveProof.proven` (prueba) y
`sustainedDrivingMs` (banda). El camino principal consulta la más débil.

Se sustituyen por **un value object en `domain/detection/physics/`**, construido en un único sitio
desde el estado de sesión:

```kotlin
sealed interface DrivingEvidence {
    /** Cero fixes a velocidad de conducción. No puede plantar NADA, ni preguntar por un park. */
    data object None : DrivingEvidence

    /** Entre 1 y el umbral. Es la banda del ruido de GPS. Puede PREGUNTAR, nunca confirmar. */
    data class Weak(val credibleFixes: Int, val why: String) : DrivingEvidence

    /** Conducción medida. Lo ÚNICO que autoriza un pin silencioso. */
    data class Measured(
        val credibleFixes: Int,
        val netDisplacementMeters: Double,
        val sustainedBandMs: Long,
        val source: DriveProofSource,
    ) : DrivingEvidence
}
```

`Measured` exige las tres a la vez:

1. `credibleFixes >= minDrivingFixesForConfirm` — **5**, de §6.1: el ruido nunca pasó de 2 en 125
   sesiones y el viaje real más flojo tuvo 7. El FP de la parafarmacia tenía **1**.
2. **desplazamiento NETO** fuera del sobre de precisión — mata el "71 m de ida y 65 de vuelta".
3. banda de conducción sostenida (`sustainedDriveProofMs`, ya existe).

**Regla única, y sustituye a todas las de hoy:**

| evidencia | derecho |
|---|---|
| `Measured` | confirmar en silencio |
| `Weak` | **preguntar**, jamás plantar |
| `None` | cerrar. Ni pin, ni pregunta de park |

Esto cierra §6.0 (el camino `steps+egress` deja de poder ignorar la prueba), el FP de la
parafarmacia por una segunda vía independiente de la de §7, y hace innecesarios varios guards
aguas abajo que hoy existen sólo para tapar esta ausencia.

---

### Pieza 2 · Membresía declarada — extender el patrón que ya funciona

`SessionOutcome.kt` ya curó este defecto aquí dentro (§6.4). Se aplica a las **cinco** decisiones que
hoy se deciden deletreando strings:

| hoy | pasa a ser |
|---|---|
| `weakLabels: Set<String>` | `ArmEvidence.confirmsSilentlyWithoutMeasuredDrive` — `when` exhaustivo ✅ *(hecho, §7)* |
| `isVerifiedLabel(String)` | propiedad declarada en el `sealed interface` ✅ *(hecho el 30-08, `DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001`, con un matiz que el plan no vio: la palabra necesita **su propio tipo**, `ArmLabel`, porque el arm lleva payload que un parse no puede reconstruir y porque `verified_late` es una palabra SIN arm)* |
| `humanPowered == SCOOTER \|\| == BIKE` | `VehicleType.isHumanPowered` — `when` exhaustivo ✅ *(hecho el 31-08, `VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001`)* |
| `NON_PARKING_TYPES: Set` | `VehicleType.parksInASpot` — `when` exhaustivo ✅ *(misma tarea; el plan lo llamaba `parkingStrategy`, pero el resolver no pregunta qué estrategia usa un tipo: pregunta si ocupa una plaza, y la estrategia la decide él)* |
| **`detectionPath: String`** | **`DetectionPath` sellado**, que lleva DENTRO su `reliability`, su `ParkingDetectionSource` y si es colocado por el usuario |

La última es la más rentable: convierte `detectionPath` de string a tipo y **mata de una vez los
fallos #4, #12 y #13** — el `startsWith("bt")`, el `else -> Assisted`, la fiabilidad elegida por
igualdad de string (que regala 0.90 al camino nuevo) y el `pathLabel` que miente en los prompts.

Criterio de aceptación de la pieza: **un caso nuevo no compila hasta que su autor responde.**

---

### Pieza 3 · Fallar cerrado por construcción

**3a. Se acaban los defaults permisivos.**
- `armEvidence: ArmEvidence = ArmEvidence.Manual` (#14) → **parámetro obligatorio**. Que la evidencia
  más fuerte del sistema sea el valor omitido es indefendible.
- `ParkingDecisionInput` (#15) → los campos de duda pasan a ser obligatorios, o se **invierten** para
  que el default sea el valor dudoso: `egressBornAtAnchor: Boolean = true` → `egressDoubt: Boolean = true`.
  ✅ *(hecho el 31-08, `DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001`, por la primera vía:*
  ***obligatorios***. *La inversión se descartó y por qué importa: una vez que nada se puede omitir, la
  polaridad del campo no compra seguridad — sólo renombraría el predicado, el input hermano y las
  trazas. Y el barrido encontró que el hermano `UnattendedSaveInput` también tenía 2 defaults, uno de
  ellos `humanPoweredRide = false`, su PRIMER guard, que su helper de tests jamás nombraba.)*
- `enterArmStepVetoMs = 0L` (#16) → un veto desactivado por defecto no es un veto. ✅ **Resuelto el
  30-08 por BORRADO**, no por calibración: nada lo encendió nunca, su desenlace lo da hoy
  `VerifiedByVehicleEnter.confirmsSilentlyWithoutMeasuredDrive = false` y su un-seed lo da la
  retractación del seed `OnTrust`. Ver `DET-A-VETO-NOBODY-EVER-TURNS-ON-IS-NOT-A-VETO-001`.

**3b. Una sola política de nulos, escrita una vez.** Hoy hay tres ficheros contiguos con tres
políticas distintas (§6.2 #8, #9, #10). Regla: **en una pregunta de evidencia, `null` significa
"no hay evidencia", y no hay evidencia significa que no se confirma.** Se corrigen
`EvidenceAdmissibility:33`, `isCredibleDrivingSpeed:1334`, `isEgressBornAtAnchor:212`,
`EvaluateGeofenceExitUseCase:88` y `EvaluateBackfillDeferralUseCase:53`.

**3c. Evidencia declarada inválida no se reutiliza (P4).** Un ancla descartada por hueco de GPS no
vale para el guardado desatendido, ni para el backfill, ni para el honest close. Y el sello de
resolución se escribe para **las 8** razones, no para 1 (#7).

---

### Pieza 4 · Ningún reloj planta un pin

Que un reloj venza significa *"no llegó más evidencia"*, y eso no es evidencia. Los cuatro relojes de
§6.3 cambian de desenlace:

| reloj | hoy | sistema |
|---|---|---|
| `confirmHoldMs` | confirma | confirma **sólo si sigue habiendo `Measured`**; si no, pregunta |
| watchdog `STARVED` | confirma sin fix | **no planta**. Cierra, y pregunta si procede. **+ test, que hoy no tiene ninguno** |
| `SESSION_ENDED` con hold vivo | confirma sin fix | igual |
| ventana `vehicleExit` | confirma con AR EXIT + 18 m | exige `Measured` |
| prompt sin responder (15 min) | **planta** con 0.5 y zona 250 m | **cierra sin pin** (P3) |

Esto es el fallo 1.3 de la noche —el pin a 142 m de casa— cerrado en su origen.

---

### Pieza 5 · Dos niveles de vigilancia (P7)

Sin tocar el significant motion, que es lo que nos salva de los FN:

- **Nivel 1 — triage barato.** Despierta con todo. Un fix, dos lecturas, una pregunta: *¿esto se
  parece a un vehículo saliendo?* Sin stream, sin máquina de estados, **sin derecho a plantar**.
  Puede dispararse 61 veces por noche. Ya existe parcialmente (`SentryWakeTriage`); aquí pasa a ser
  la única puerta.
- **Nivel 2 — sesión.** Se promociona sólo con desplazamiento real. Es el único que enciende GPS a
  cadencia alta y el único con derecho a confirmar.

Métrica: **promociones a nivel 2 por viaje real**. Medido hoy: 28/1. Objetivo: ≈1.

---

### Pieza 6 · La duda llega al ojo del usuario (P5)

`zoneRadiusMeters` se calcula, se guarda en Room y **no se pinta en Historial** (§1.5). Toda
superficie que muestre un pin muestra su duda, y el botón "Navegar" de un pin aproximado dice a qué
está navegando.

---

### Pieza 7 · Guardarraíles — lo que hace que esto no se deshaga

Sin esta pieza, las seis anteriores duran hasta el próximo fix con prisa. Tests de arquitectura, al
estilo de los `ColorGuardrailTest` / `TypographyGuardrailTest` que ya existen:

1. **`DrivingEvidenceGuardrailTest`** — enumera **todas** las rutas que terminan en
   `ConfirmParkingUseCase` y afirma que ninguna es alcanzable con `DrivingEvidence` distinta de
   `Measured`. Es la joya: convierte la doctrina en una propiedad demostrada, no en un comentario.
2. **`FailClosedGuardrailTest`** — prohíbe en el paquete de detección: comparar `detectionPath` o una
   etiqueta de armado como `String`; `setOf(LABEL_…)` usado con `in` para decidir; defaults de
   parámetro en dirección permisiva.
3. **Test de exhaustividad de membresía** — para cada `ArmEvidence`, `VehicleType` y `DetectionPath`,
   que exista clasificación declarada en cada política. Compile-time donde el `when` llega; test
   donde hay frontera de string.
4. **Replays de campo como regresión** — el método de `DET-2208-TRIPS-BECOME-REPLAYS-001`, con la
   regla que ya se aplicó hoy: **cada aserción se verifica neutralizando su guard y viéndola roja**.
   Empezando por `Trace_Parafarmacia2908` y `Trace_CasaGapAnchor3008`.

---

### Orden de ejecución

| # | ticket | cierra | riesgo |
|---|---|---|---|
| 0 | `DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001` ✅ | FP parafarmacia | hecho |
| 1 | `DET-DRIVING-EVIDENCE-VALUE-OBJECT-001` (Pieza 1) ✅ | §6.0, la raíz | hecho |
| 2 | `DET-NO-CLOCK-PLANTS-A-PIN-001` (Pieza 4) ✅ | pin a 142 m, batería | hecho |
| 3 | `DET-DETECTION-PATH-IS-A-TYPE-001` (Pieza 2) 🟡 | #4 #12 (#13 era falsa alarma) | parcial |
| 4 | `DET-FAIL-CLOSED-BY-CONSTRUCTION-001` (Pieza 3) 🟡 | #5 #9 #14 #15 (#8 y #16 refutados/borrados) | **3a completo**; faltan 3b (#10) y 3c (#7) |
| 5 | `DET-TWO-TIER-SENTRY-001` (Pieza 5) ✅ | 28→1 armados, batería | hecho ⏳ medir en campo |
| 6 | `DET-DOUBT-MUST-REACH-THE-SCREEN-001` (Pieza 6) ✅ | §1.5 | hecho ⏳ sin ver en device |
| 7 | `DET-GUARDRAILS-KEEP-THE-DOCTRINE-001` (Pieza 7) 🟡 | que no se deshaga | 3 reglas hechas; replays pendientes |

1, 2 y 4 son los que cambian la tasa de FP. 5 es el que cambia la batería y la escala (§6.5).

🟢 **Las siete piezas están ejecutadas** (30-08), tres de ellas parciales y con lo que falta escrito
en su propio ticket. Dos enunciados de este documento se REFUTARON al implementarlos y quedan
corregidos donde viven: el umbral de 5 `drivingFixes` de §6.1 (rompía Calle Gavia; va a 2) y el
`DrivingEvidenceGuardrailTest` de la Pieza 7 (su propiedad es falsa: `manual`, `inherited_drive` y
`verified_speed` confirman en silencio sin conducción medida **por diseño**).

🟡 **La Pieza 3 cerró #5, #9 y #14, y REFUTÓ #8**: `isAdmissibleEvidence(sessionStartMs = null)` no es
un default permisivo — vigila una SEÑAL que nomina, y el contrato de triggers dice que un evento
viejo pasa al evaluador, nunca se descarta. Fallar cerrado gobierna lo que PLANTA, no lo que nomina.
Es una distinción que le falta a la Pieza 3b y que conviene escribir en ella.

🟢 **Y su apartado 3a está COMPLETO** (31-08): #14 y #16 ya lo estaban, y #15 se cerró con
`DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001`. Quedan **3b** (una sola política de nulos:
#8 refutado, #9 hecho, #10 abierto) y **3c**. ⚠️ Y una advertencia que 3b hereda del 3a: los defaults
de #15 se justificaban en su propio KDoc *"for legacy callers"* y **no existía ni un legacy caller** —
un campo con default en un input de evaluador no es compatibilidad, es una respuesta permanente que
nadie tiene que dar. El barrido encontró además 2 defaults en el input HERMANO (`UnattendedSaveInput`),
uno de ellos su primer guard.

🟢 **La Pieza 2 está CERRADA** (31-08). Fue por su decisión más rentable (`detectionPath` → tipo) y el
barrido de textos del safety-net; las tres membresías que le quedaban se cerraron en dos tickets:
`isVerifiedLabel` en `DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001` (30-08) y las dos
de `VehicleType` en `VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001` (31-08). **#13 se cerró
como falsa alarma**: el `else` incondicional de `pathLabel` sólo se lee cuando `confirmNow` es true, y
ahí la única rama restante es la que la etiqueta nombra.

⚠️ Lo que el plan no vio de `VehicleType`: la fila decía **dos** membresías y hay **cuatro** preguntas.
`hasCarbody` (cinco `== CAR` en el registro) y `slowTripContradictsProfile` (el guard de mismatch de
`BUG-SCOOTER-001`) estaban deletreadas igual, y no son sinónimos de las otras dos — coinciden sobre los
cuatro tipos de hoy por accidente, no por significado.

⚠️ Antes de abrir el #1, leer **§9.4**: el cruce con el backlog abierto añade obligaciones a las
Piezas 2, 3 y 4, y deja sobre la mesa si hace falta una **Pieza 8** correctiva.

---

## 7 · Trabajo ya hecho

**`DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001`** — cierra el FP de la parafarmacia aplicando **P1** y
**P2** en un sitio: la política de evidencia débil deja de leer la lista de strings y pregunta al
`when` exhaustivo de `ArmEvidence`. Delta de comportamiento: **exactamente una etiqueta**
(`enter_at_car`). `manual`, `inherited_drive` y `verified_speed` conservan el suyo, cada uno con su
test de regresión.

1.798 tests en verde con ejecución forzada. Verificado por el método de
`DET-2208-TRIPS-BECOME-REPLAYS-001`: al neutralizar el guard falla **exactamente un test**, el de la
parafarmacia, y ninguno más. Detalle en `docs/backlog/det-driving-evidence-is-the-only-gate-001.md`.

**Abiertos, sin implementar** (nombrados aquí para que no se pierdan):

- `DET-UNANSWERED-PROMPT-IS-NOT-A-CONFIRM-001` — **P3** + **P4**. El fallo 1.3.
- `DET-DOUBT-MUST-REACH-THE-SCREEN-001` — **P5**. El fallo 1.5.
- `DET-NOTIFIED-MUST-EXPIRE-001` — la sesión clavada en `Notified` reevaluando cada 4-6 s
  indefinidamente: 368 s y subiendo cuando se tiró el log. Es la quema de batería.
- `DET-FRESH-INSTALL-IS-NOT-BLIND-001` — un uid nuevo nace sin `diagnostics_config`, así que ninguna
  sesión llega a Firestore. Toda beta nueva nace ciega.

Y **seis tickets abiertos anteriores a esta noche**, que este documento no había mirado: el cruce
está en §9.

---

## 9 · Cruce con el backlog abierto anterior al rediseño

Este documento nació de la noche del 29→30-08 y su inventario (§6.2) sale de leer el código, no el
backlog. En `docs/backlog/` había ya **seis tickets de detección abiertos** de los field tests del
26, 27 y 28-08, ninguno con commit de fix. Sin este cruce, el riesgo era doble: reimplementar lo que
ya está pensado, o dar por muerto lo que las siete piezas no tocan.

| ticket abierto | origen | ¿lo absorbe una pieza? |
|---|---|---|
| `DET-STARVED-HOLD-HAS-NO-WITNESS-001` | 🔴 27-08, `//FIXME` del user | **Sí, entero** → Pieza 4 |
| `DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001` | field 27-08 + 28-08 | **A medias** → Piezas 1 y 3 |
| `PARK-RETRACTED-BACKFILL-MUST-LEAVE-NO-PIN-001` | field 27-08 | **A medias** → Piezas 1+3 crean menos; nadie retira |
| `DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001` | field 26-08 | **No** — y el rediseño lo encarece |
| `DET-EXPLAINED-RIDE-ASKS-NO-OTHER-CAR-001` | field 27-08 | **No** — otro plano (bucle del worker) |
| `DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001` | field 27-08 | **No** — y sigue bloqueado por un viaje |

### 9.1 Absorbido por completo — se cierra como duplicado

**`DET-STARVED-HOLD-HAS-NO-WITNESS-001` → Pieza 4 / ticket #2.** La Pieza 4 ya decide el desenlace
(*el watchdog `STARVED` no planta: cierra*) y ya pide el test que falta. Coinciden hasta en el
diagnóstico: §6.3 cita el mismo `HoldLifecycle.kt:16`.

⚠️ Pero el ticket viejo lleva **tres cosas que la Pieza 4 no dice** y que se pierden si se borra sin
más. Pasan a criterio de aceptación del #2:

1. `confirmHoldMs > 0` es una **costura de test, no una opción de runtime** — la ponen a 0
   `CoordinatorParkingDetectorTest:73`, `DetectionTraceReplayTest:1003` y
   `StagePrecedenceCharacterizationTest:215`. Quien "limpie" esa guarda rompe tres ficheros.
2. El test **no está bloqueado por esperar 2 minutos**: `runTest` usa tiempo virtual y el fichero ya
   inyecta su reloj. Estaba sin escribir, no impedido.
3. ⛔ `pendingConfirm === pending` se compara **por identidad**, deliberadamente. Un test que
   reconstruya el objeto pasa por casualidad o falla por el motivo equivocado.

### 9.2 Absorbidos a medias — el ticket encoge, no muere

**`DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001`.** Tiene tres mitades y las piezas cubren
dos:

- ✅ *El espejismo no puede CONFIRMAR* — `Measured` exige desplazamiento **neto** fuera del sobre de
  precisión, y eso mata por construcción el «230 m de ida, de vuelta al ancla en 7 s». Pieza 1.
- ✅ *El fix inadmisible por un carril no puede ser admisible por otro* — el hallazgo del 28-08 (el
  MISMO fix rechazado por accuracy como conducción y aceptado como desplazamiento en el mismo beat)
  es exactamente la política de nulos/inadmisibilidad de la **Pieza 3b**, que hoy sólo enumera
  `EvidenceAdmissibility`, `isCredibleDrivingSpeed`, `isEgressBornAtAnchor`, `EvaluateGeofenceExit`
  y `EvaluateBackfillDeferral`. **Falta añadir `sustainedDepartureFromAnchor` a esa lista.**
- ❌ *Un latch monótono no se puede deshacer* — `hasEverReachedDrivingSpeed` es la autorización de
  ciclo de vida de la sesión, y ninguna pieza la toca. Su daño medido no fue un pin: fue **una
  sesión de 19 min y un prompt a las 2 de la mañana** con el móvil en el sofá, y en el 28-08 un
  ancla PINNED limpiada por un solo sample. La Pieza 1 gobierna el derecho a **confirmar**; esto
  gobierna el derecho a **seguir viva**. Son planos distintos.

⚠️ **Y no doy por hecho que el umbral de §6.1 lo cubra.** La banda de ruido «≤2 `drivingFixes`» se
midió sobre 125 sesiones de usuario andando. Una tormenta de multipath en interior es otra
distribución: la sesión del 27-08 tuvo **216 fixes y vmax 78 km/h** con accuracy de 8-15 m
plenamente creíbles. **No sé cuántos de esos 216 pasan el listón de 5**, y es medible desde el
`parkdiag` del Oppo. Hasta contarlo, afirmar que la Pieza 1 lo cubre sería justo el error que este
documento existe para no repetir.

> Sobrevive como ticket propio, con alcance recortado a **el latch y el ancla**. Y una obligación
> nueva: el KDoc de `DriveProof` (*«once the car provably drove, no later fix un-drives it»*) tiene
> que cambiar con el código o quedará mintiendo.

**`PARK-RETRACTED-BACKFILL-MUST-LEAVE-NO-PIN-001`, y el agujero que destapa.** Las Piezas 1 y 3
reducen los pines de backfill que llegan a nacer (#6, sello nulo → planta). Pero este ticket no va
de **crear**: va del registro que **ya existe** en `parkingHistory` y que la propia app desmintió 63
segundos después. Ninguna de las siete piezas puede mover, marcar ni retirar un pin ya colocado.

🔴 **Eso es una omisión del rediseño, no de este ticket.** §5.1 **U4** lo tenía escrito y no se
convirtió en pieza:

> *"Sentiance marca los eventos como `provisional` y los reprocesa post-trip… Es más fuerte que
> nuestra verificación tardía: permite mover o retirar un pin ya colocado. Es exactamente lo que le
> faltaba al pin a 142 m de casa."*

Las siete piezas son todas **preventivas**: deciden mejor antes de plantar. Ninguna es correctiva.
Y el fallo 1.3 de esta misma noche —el pin a 142 m— es un viaje **real** con ancla mala: la Pieza 4
evita plantarlo, pero cuando el usuario llegó a su portal el sistema tenía datos para **corregirlo**
y sigue sin tener forma de hacerlo.

> Candidata a **Pieza 8 · el pin es provisional hasta el final del viaje**, con este ticket como
> primer caso de uso. No la meto en el orden de ejecución sin decidirlo: cambia el modelo de datos
> (`UserParking` en dos fases) y toca Historial, mapa y Firestore. La pregunta abierta del ticket
> —¿borrar o marcar?— es la misma pregunta, y no debe contestarse sin mirar antes cómo lee el
> Historial.

### 9.3 Intactos — las piezas no los rozan, y dos se vuelven MÁS urgentes

**`DET-EXPLAINED-RIDE-ASKS-NO-OTHER-CAR-001`.** Vive en el bucle de `ParkingSafetyNetWorker`, que
itera todas las sesiones en el mismo tick; las siete piezas viven en la decisión de UNA sesión. Un
evaluador puro no puede saber lo que hicieron las otras sesiones del tick — por construcción, esto
no lo absorbe ninguna pieza.

**`DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001`.** §6.2 #2 y la Pieza 2 tocan
`humanPowered`, pero otra cosa: que un `VehicleType` **nuevo** no sea human-powered por omisión. Este
ticket es el defecto contrario — el detector de cadencia condena a un coche **real** por su
velocidad urbana (2 de 2 trayectos el 26-08, con 11-17 km/h por calle estrecha). Es un problema de
umbral y de ruido de podómetro dependiente del hardware (8 disparos en el Redmi, 1 en el Oppo), no
de membresía declarada. Sigue **bloqueado por medición**, aunque su bloqueo previo —la telemetría
invisible— ya se levantó en `c692d61c`.

⚠️ **Interacción a vigilar, y va en la dirección mala.** Hoy un veto de cadencia mal puesto degrada a
pregunta, y un prompt sin contestar planta con fiabilidad 0,5 y zona de 250 m: se pierde precisión,
no la plaza. Con **P3** (*una pregunta es una puerta, no un retraso*) ese mismo prompt sin contestar
pasará a **cerrar sin pin**. El coste de un falso veto sube de «pin impreciso» a «plaza perdida».
P3 es correcta y no se toca; pero **encarece este ticket**, así que su prioridad sube con la Pieza 4,
no baja.

**`DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001`.** Sigue igual de bloqueado: su premisa central —si la
vía BT cierra la sesión cuando el Kamiq se conduzca— **no está demostrada** y cuesta un viaje con
ese coche, que compite con el tiempo de cazar FP en el Focus. Dos matices del cruce:

- Su **mitad de coste** (44 `PERMISSION_DENIED` contra un spot inexistente, 568 evaluaciones en 3
  días, 2 armes espurios) sí toca la Pieza 5 en un punto: los dos armes espurios desde su valla
  morirían en el triaje de nivel 1. El resto —heartbeat del safety-net y reintentos de retractación—
  es otro worker y no lo cubre nadie.
- Su **log que miente** (imprime *«SIN pruebas de viaje»* cuando la causa real era la identidad BT)
  es la misma familia que #13 —el `pathLabel` con `else` incondicional que se traza en los prompts—
  pero en otro fichero. Vale la pena barrerlos juntos en la Pieza 2 en vez de arreglar uno solo.

### 9.4 Consecuencias para el orden de ejecución

Nada de esto reordena las siete piezas, pero añade cuatro obligaciones:

1. La **Pieza 3b** amplía su lista con `sustainedDepartureFromAnchor` (§9.2).
2. La **Pieza 4** hereda los tres caveats del hold famélico (§9.1) como criterio de aceptación.
3. La **Pieza 2** barre también el texto del safety-net que nombra la causa equivocada (§9.3).
4. Hay que **decidir si existe una Pieza 8** correctiva (§9.2). Es la única laguna estructural que
   ha destapado este cruce.

Y una medición pendiente, barata y que puede tumbar una afirmación de §9.2: **contar los
`drivingFixes` de la sesión `1787789799012`** (Oppo, 27-08, 216 fixes) en su `parkdiag`. Si pasan de
5, la Pieza 1 no cubre el espejismo de multipath y el ticket del latch sube de prioridad.
