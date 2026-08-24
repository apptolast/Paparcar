# 07 — Duplicación y ruido (Fase 3)

> Refactor de solo-lectura · 2026-08-19. Este documento NO cambia código: consolida los 4 parciales
> de Fase 3 (S1 similitud de use cases · S2 helpers del coordinator · S3 conceptos de primera clase
> · S4 ruido) y los verifica contra el contrato de Fase 2 (`docs/detection/06-invariantes.md`: 133
> tags vigentes y las 5 políticas demostradas en su §3). Abreviaturas como en el 06 (CPD, CDS,
> EvalPD, EvalUS, EvalHC, EvalSNC, SHP, Config). Lo que un parcial marcó **NO VERIFICADO** se
> mantiene NO VERIFICADO aquí. Regla sagrada aplicada: ningún guard [DET-*]/[BUG-*] se propone
> eliminar sin la demostración escrita que ya traiga el parcial; sin demostración, el veredicto es
> MANTENER.
>
> ⛔ Recordatorio de doctrina [DET-VERDICT-NOT-PREDICATE-001]: nada de lo aquí veredictado se
> codifica hasta validar los 4 fixes de campo pendientes.

---

## 1. Matriz de similitud de use cases

Consolidada de S1 (15 pares + 3 análisis de split). Veredicto por fila con la justificación en una
línea; el detalle línea a línea vive en el parcial S1.

| # | Par | Veredicto | Justificación (tags) |
|---|---|---|---|
| P1 | `ProcessConfirmedDepartureUseCase` ↔ `ReleaseActiveParkingSessionUseCase` | **PARAMETRIZAR** | Misma secuencia publicar(prefetched [SPOT-PREFETCH-001])→clear→removeGeofence→log con la doctrina "encolar el report ANTES del clear" copiada palabra por palabra; un núcleo `(sesión, razón)` preserva [DET-RECONCILE-001], [PARK-DELETE-NO-DECLARE-001], [DET-AUDIT-002 T5/M4]; los dos eventos de diagnóstico (`DepartureProcessed` vs `Released`) sobreviven como salidas del núcleo. ⚠ Bloqueado por las 2 divergencias del §5 (bug-o-intención) hasta adjudicar. |
| P2 | `DetectParkingDepartureUseCase` ↔ `VerifyDepartureEvidenceUseCase` | **MANTENER SEPARADOS** | Dos VEREDICTOS distintos (`DepartureDecision` suelta plaza irreversible; `ArmEvidence` decide guards de nacimiento, upgradeable [DET-G-05]); la tríada triplicada se extrae al verificador DriveProof por perfil, conservando `departureProofMinGapMs` como parámetro exclusivo del perfil worker-live [DET-DEPART-PROOF-001] (06 §3-a). |
| P3 | (P2) ↔ tríada de `EvaluateSafetyNetCheckUseCase` | **MANTENER SEPARADOS** | `SafetyNetAction` no solapa con ningún otro veredicto (ancla posicional, veto BT-identity, frozen-counter físico, cada uno con incidente propio); su tríada entra como perfil `reconcile` del verificador [DET-SAFETY-NET-001, DET-CONJUNCTION-001, BUG-WALK-DEPART-001]. |
| P4 | `DetectParkingDeparture` ↔ `RunDepartureCheckUseCase` | **MANTENER SEPARADOS** | No es duplicación sino composición decisión-pura/orquestador-I/O deliberada [DET-SOLID-001]; fundirlos re-mezclaría I/O con el veredicto puro. |
| P5 | `EvaluateHonestClose` ↔ `EvaluateSafetyNetCheck` (step-budget espejo) | **MANTENER SEPARADOS** | Dos veredictos con entradas y momentos distintos (06 §3-c); se extrae el predicado `walkedVsRode` con [DET-STEP-BUDGET-ORIGIN-001, DET-TRIP-WITNESS-001, DET-FROZEN-COUNTER-001, DET-WALK-FLOOR-001] como cláusulas. Matiz al 06: los dos guards frozen NO son copias (testigo interno vs física) — ver §3. |
| P6 | `EvaluateHonestClose` ↔ `EvaluateUnattendedParkingSave` (`zoneOrAsk`) | **MANTENER SEPARADOS** | Misma doctrina, álgebra distinta: EvalUS degrada zona→ask por ACOTABILIDAD de la duda; EvalHC degrada pin→zona por ACCURACY del testigo y su rung 3 es silencio [BUG-WALK-DEPART-001]. La demostración de fusión que 06 §3-f.1 dejaba pendiente NO sale — ver §3, candidato 4. |
| P7 | `EvaluateDetectionReliability` ↔ `ObserveDetectionReliability` | **MANTENER SEPARADOS** | Par Evaluate(pura)/Observe(reactiva) sin lógica duplicada; mejora opcional: degradar Evaluate a función top-level [DET-RELIABILITY-001, DET-TIERS-001 preservados]. |
| P8 | `GetLastKnownLocationUseCase` | **ELIMINAR** | Muerto: única referencia fuera del fichero = `DomainModule.kt:92`; sus consumidores del KDoc fueron purgados en DET-SOLID-001 C1b. [DET-AR-REARM-001] no se pierde: vive en el guard de frontera espacial (06 §3-f.4, `SessionSupersede.kt`), no en este fichero. |
| P9 | `SendSpotSignalUseCase` | **ELIMINAR** (inline) | Delegación 1:1 al repo, call site único (HomeViewModel), cero tags en 06; sin resultado citable no hay caso de uso [DET-VERDICT-NOT-PREDICATE-001]. |
| P10 | `ClearParkNudgeUseCase` | **ELIMINAR** (inline) | 2 llamadas adyacentes, caller único; [DET-NUDGE-PERSIST-001] queda intacto (las 2 llamadas siguen juntas y el otro dueño, ConfirmParking:346-350 + janitor, no se toca). |
| P11 | `NotifyParkingConfirmationUseCase` | **ELIMINAR** (plegar en el port) | Cero tags en 06; ⚠ NO inlinear en el CPD — metería I/O (`observeActiveVehicle`) en el coordinator, contra [DET-INTAKE-001]: se pliega en la impl de `AppNotificationManager` o en el borde de servicio. |
| P12 | `RevertParking` ↔ Release/Process (3ª secuencia de cierre) | **MANTENER SEPARADOS** | Es un UNDO, no un cierre: nunca publica, fallo best-effort opuesto, no resetea el bus a propósito, y su evento `Reverted` es el FP etiquetado por el usuario — la telemetría más valiosa [DET-SOLID-001]. Barrido conjunto si P1 cambia la secuencia. |
| P13 | `CalculateParkingConfidence` ↔ `EvaluateParkingDecision` | **MANTENER SEPARADOS** | Dos FASES de la máquina (abrir CANDIDATE vs veredicto de la CANDIDATE), cero solape de entradas ni fórmulas [DET-SOLID-001 C1, BUG-DETECT-310503, BUG-COORD-106]. Si el scoring legacy merece seguir siendo la puerta es decisión de producto, no de duplicación. |
| P14 | `EvaluateShortHopDriveProof` ↔ `corroboratesDrive` (CPD:1961-1988) | **PARAMETRIZAR** | Dos pruebas independientes del MISMO latch `driveProven` → perfiles short-hop-track y track-window del dueño DriveProof (06 §3-a); la fusión documental DET-SHORT-HOP-PROOF-001 + DET-UNVERIFIED-ARM-DRIVE-PROOF-001 está probada (CPDTest:1620). |
| P15 | `evidencia ≥ sessionStart` ×4 | **PARAMETRIZAR** (función única) | Misma comparación de una línea en Detect:129-135, Verify:72-73, EvalSNC:158-160, EvaluateArEnterArm:81-83 (la 4ª citada de 06 §3-d, no re-leída por S1) → función en `domain/detection/`; [DET-SESSION-BIRTH-001] queda en UN sitio. |

**Totales: FUNDIR 0 · PARAMETRIZAR 3 · MANTENER SEPARADOS 8 · ELIMINAR 4.**
Hallazgo transversal de S1: ninguna duplicación real del área es de VEREDICTOS — toda es de
PREDICADOS (tríada, step-budget, session-birth, drive-proof), exactamente lo que la doctrina
DET-VERDICT-NOT-PREDICATE-001 predice y los límites que 06 §3 ya trazó.

### 1.1 Eliminables — resumen

- **`GetLastKnownLocationUseCase`** — código muerto (clase + registro Koin `DomainModule.kt:92`).
- **`SendSpotSignalUseCase`** — inline en HomeViewModel (pasa a depender de `SpotRepository`).
- **`ClearParkNudgeUseCase`** — inline en HomeViewModel (las 2 llamadas juntas).
- **`NotifyParkingConfirmationUseCase`** — plegar en la impl del port `AppNotificationManager` /
  borde de servicio (nunca en el CPD).

### 1.2 Splits verificados (de 01 §7)

1. **`ConfirmParkingUseCase` (434 LOC) — SPLIT confirmado**: `encodeFreshRoute` (:366-412) + sus 4
   constantes son ~80 LOC de lógica pura de rutas cuyo dueño natural es `DrivingRoute`; viajan con
   ella DET-ROUTE-TRACK-001, ROUTE-QUALITY-001, ROUTE-START-AT-CAR-001, ROUTE-END-AT-CAR-001,
   ROUTE-GAP-HONEST-001. El confirm real (save→fence→seal→nudges) queda como único veredicto.
2. **`EvaluateSafetyNetCheckUseCase` (439 LOC) — split ligero**: `shouldReregisterCure` (:426-434)
   → función top-level en `domain/detection/` (DET-ANCHOR-FREEZE-001 F4, DET-CURE-FRESH-001). El
   `invoke` NO se trocea: sus pruebas son los predicados que 06 §3 reparte entre DriveProof y
   AnchorTrust.
3. **`RunDepartureCheckUseCase` (164 LOC) — NO split**: orquestador delgado; trocearlo añadiría
   ceremonia sin veredicto nuevo.

---

## 2. Helpers del coordinator y condiciones repetidas sin nombre

Consolidada de S2 (los 16 helpers privados del CPD, leídos enteros, + 6 condiciones inline sin
nombre). Leyenda: **EXTRAER** = función pura en `domain/detection/` (patrón `SentryWakeCooldown.kt`
/ `HumanPoweredRide.kt`) · **AnchorTrust/DriveProof** = pasa al dueño demostrado en 06 §3-e/3-a ·
**DEJAR DENTRO** = predicado de un solo veredicto que ya vive donde debe.

### 2.1 Los 16 helpers

| Helper | Veredicto | Nota clave |
|---|---|---|
| `humanPoweredRide` | **DEJAR DENTRO** (adaptador) | La física ya vive en `domain/detection/HumanPoweredRide.kt`; 11 líneas de adaptación state→args [DET-BIKE-NOT-A-CAR-001]. |
| `hasEgressDisplacement` | **AnchorTrust** | Floor del egress medido contra el ancla; sin envelopes de accuracy a propósito (suelo vs versión probada, §2.3). |
| `isAnchorLocked` | **AnchorTrust** | 06 §3-e textual; `stepCount` NO es del dueño — se le presenta como argumento [ANCHOR-LOCK-001]. |
| `isAnchorPinned` | **AnchorTrust** | El predicado CENTRAL del dueño (`pinned = locked ∨ frozen`), 5 call sites / 3 veredictos [ANCHOR-LOCK-001, DET-ANCHOR-FREEZE-001]. |
| `isAnchorWalkEntered` | **AnchorTrust** (taint `walkEntered` + exención de maniobra) | Lee EXCLUSIVAMENTE los 3 snapshots del rebind → huérfano total (§2.4) [DET-CREDIBLE-DRIVE-001]. |
| `hasKinematicEgressSignal` | **AnchorTrust** | Sus 3 campos están en la lista del dueño; `kinematicEgressFixes` es ciclo de vida del ancla [DET-KINEMATIC-EGRESS-001]. |
| `movementOutrunsSteps` | **EXTRAER → `outrunsPedestrianReach`** | 1 de las 4 copias de la MISMA fórmula; expuesto como predicado de escape de AnchorTrust [DET-AR-FIRST-001 F3, DET-STEP-SPEED-GATE-001]. |
| `heldConfirmOutrunByVehicle` | **EXTRAER → `outrunsPedestrianReach`** (base = pin holdeado) | La única copia cuya base NO es el ancla — la invocación queda en el veredicto hold, fuera de AnchorTrust [DET-CONFIRM-FRESHNESS-001]. |
| `escapesAnchorEnvelope` | **EXTRAER → `outrunsPedestrianReach`** (steps = 0) | Misma fórmula con el término de pasos a cero; predicado de AnchorTrust. |
| `egressExceedsWalkReach` | **EXTRAER → `outrunsPedestrianReach`** (floor generoso) | El tag conserva su escenario (Calle Abeto) como juego de parámetros NOMBRADO [DET-EGRESS-PEDESTRIAN-CEILING-001]. |
| `isSustainedDepartureFromAnchor` | **FRONTERA: física en DriveProof, estado y destino en AnchorTrust** | Exactamente el límite que 06 §3-a demostró. ⚠ Log dentro (:1916-1921) — sube al caller al extraer. |
| `isCorroboratedVehicleHop` | **DriveProof** (perfil hop) | Ya stateless; versión de UNA arista de la corroboración por desplazamiento [DET-CREDIBLE-DRIVE-001]. |
| `corroboratesDrive` | **DriveProof** (perfil track-window) | Tercer productor del latch `driveProven` (06 §3-a) [DET-DRIVE-PROOF-001]. |
| `pruneRecentFixes` | **DriveProof** (mantenimiento del ring) | Viaja con `corroboratesDrive` o la ventana y su poda divergen en silencio; sus 2 constantes se mudan con ella. |
| `isEgressBornAtAnchor` | **AnchorTrust** | 06 §3-e textual; 4 call sites / 3 veredictos; huérfano total (§2.4) [DET-ANCHOR-EGRESS-001]. |
| `refinedParkLocation` | **AnchorTrust** (método) | Único helper que devuelve value object (Rule A). ⚠ 2 cabos para F4: log dentro (:2040-2044) → al caller; su fallback `bestFix` lee `stoppedFixes` (máquina 1) → o el rebind entrega el `bestFix` sellado al dueño, o el caller resuelve el fallback. |

Recuento S2: **EXTRAER 4** (la familia envelope ×4→1) · **AnchorTrust 7** (`isAnchorLocked`,
`isAnchorPinned`, `isAnchorWalkEntered`, `hasKinematicEgressSignal`, `isEgressBornAtAnchor`,
`refinedParkLocation`, `hasEgressDisplacement`) · **DriveProof 3** (`corroboratesDrive`,
`isCorroboratedVehicleHop`, `pruneRecentFixes`) · **frontera 1** (`isSustainedDepartureFromAnchor`)
· **DEJAR DENTRO 1** (`humanPoweredRide`). Ningún helper justifica un use case nuevo.

### 2.2 Las 6 condiciones repetidas sin nombre

| Condición | Copias | Veredicto |
|---|---|---|
| Rebind del ancla: `anchorStopOfRecord != s.anchorCapturedAtStop` gobernando el sellado de CADA snapshot | **×5** (CPD:2165, :2170, :2172, :2177, :2193) | **AnchorTrust** — es SU momento de sellado; los 5 sellados deben ser UNA decisión (hoy un campo nuevo debe recordar copiar la condición una 6ª vez). 06 §3-e lo pide literalmente. |
| Nacimiento del egress en 2 sabores (parado vs móvil; el `refine` es literalmente idéntico, el `record` difiere en `!shouldClearBestStop` y en la pata kinemática) | **×2** (CPD:2144-2156/2196-2197 vs :2336-2345/2380-2389) | **AnchorTrust** — `egressBirthTransition(...)`; condición del 06 §3-e: re-validar contra las trazas Enamorados/CameliasOppo. |
| Gate de accuracy de conducción: `accuracy <= config.minGpsAccuracyForDriving` | **≥5** (CPD:750, :862, :2206, :2213, :2333) | **EXTRAER** (cláusula 3 del verificador DriveProof) — el gate LOC-002 repetido a mano; 5 sitios que olvidar si la barra se matiza. |
| Fix móvil creíble con barra variable (`speed ≥ BAR && acc ≤ …`, BAR elegida por `pinned`) | **×3** (CPD:861-862, :2205-2206, :2212-2213; selección :856-860) | **EXTRAER** — `isCredibleMovingFix(fix, speedBar)`: una función, dos barras nombradas. |
| Ancla-pertenece-a-ESTE-stop: `anchorCapturedAtStop ==/!= startedAt` | **×3** (CPD:2101, :2109, :2132) | **AnchorTrust** — su regla de re-captura [ANCHOR-LOCK-001]. |
| Envelope peatonal (misma fórmula, 4 helpers) | **×4** (ver §2.1) | **EXTRAER → `outrunsPedestrianReach(base, fix, steps, floor)`** con 4 juegos de parámetros nombrados (06 §3-b íntegro). |

(+1 anotada sin veredicto: rings duplicados `creepWindow` vs `recentFixes` — ver §5.)

### 2.3 Coherencia interna — misma física, umbral distinto

- Familia envelope ×4 verificada carácter por carácter: solo cambian (base, steps, floor).
- `isCorroboratedVehicleHop` vs `corroboratesDrive`: perfiles hermanos del mismo verificador; NO
  fusionables en una sola condición (sus checks de rate apuntan en direcciones opuestas — suelo vs
  techo, a propósito).
- `isSustainedDepartureFromAnchor` vs `corroboratesDrive`: misma ventana de rate, base y destino
  distintos — la frontera de 06 §3-a.
- `hasEgressDisplacement` vs `escapesAnchorEnvelope`: suelo y versión probada del mismo envelope —
  la misma `outrunsPedestrianReach` con steps=0 y término de accuracy opcional; la semántica se
  conserva en el NOMBRE del juego de parámetros.

### 2.4 Huérfanos de estado si AnchorTrust absorbe los snapshots `*AtCapture` (input para F4)

Si el dueño absorbe los 8 snapshots de 02 §2 más sus campos nucleares (`bestStopLocation`,
`anchorFrozen`, `kinematicEgressFixes`):

- **7 huérfanos totales** (no leerían ningún campo de `ParkingDetectionState`):
  `isAnchorWalkEntered`, `hasKinematicEgressSignal`, `isEgressBornAtAnchor`,
  `isSustainedDepartureFromAnchor` (+`now`/fix como args), `hasEgressDisplacement`,
  `escapesAnchorEnvelope`, y el derivado `anchorGapEnteredAtCapture` (CPD:365-369).
- **Huérfanos parciales** (necesitan `stepCount`, que es de la máquina de pasos de SESIÓN y NO debe
  absorberse): `isAnchorLocked`, `isAnchorPinned`, `movementOutrunsSteps`, `egressExceedsWalkReach`.
  Corte de frontera para F4: **AnchorTrust posee el ancla y sus taints; los pasos se le PRESENTAN,
  nunca se le copian** (copiarlos recrearía los snapshots desincronizados).
- **Caso con cabo**: `refinedParkLocation` (fallback `bestFix` → decisión de diseño explícita, §2.1).
- **Fuera del dueño por diseño**: `heldConfirmOutrunByVehicle` (base ≠ ancla), `humanPoweredRide`
  (ya extraído) y todo DriveProof.

### 2.5 Los 2 helpers "puros" con logging dentro

`isSustainedDepartureFromAnchor` (CPD:1916-1921) y `refinedParkLocation` (CPD:2040-2044) llevan un
`PaparcarLogger` DENTRO: al extraerlos/agruparlos, el log sube al caller — la función queda pura de
verdad. Conecta con el bloque de side-effects en lambdas `update` del §4.

---

## 3. Conceptos de primera clase — veredictos

Consolidada de S3 y cruzada con las 5 políticas demostradas del 06 §3.

### 3.1 `AnchorTrust` — **CONFIRMADO**

Data class dueña de estado + predicados (no enum: LOCKED y FROZEN coexisten — dos booleanos
independientes con el derivado `pinned = lockedBySteps ∨ frozenByRest`). Diseño resumido (detalle en
S3): campos `anchor`, `capturedAtStop`, `lockedBySteps`, `frozenByRest`, `walkIn` (los 4 snapshots),
`gapMsAtCapture`, `egressBirth`; predicados `pinned`, `walkEntered` (con la exención de maniobra
íntegra), `gapEntered`, `egressBornAtAnchor`, `refinedParkLocation` (Rule A), `restMs(now)`
[DET-CAR-REST-CLOCK-001], `walkInDoubtMeters`, `gapDoubtMeters`; transiciones `capture`/`rebind`
(UN sellado atómico, hoy ×5) y `clear()` (hoy cascada `shouldClearBestStop` de 9 campos).

- **Cruce con 06 §3-e**: coincide punto por punto (verificación tag a tag en S3: ANCHOR-LOCK-001,
  DET-ANCHOR-FREEZE-001, DET-SHORT-TRIP-FREEZE-001, LOC-001/LOC-002/PARKING-001,
  DET-GAP-ANCHOR-001/-ZONE-001, DET-WALK-ENTERED-ANCHOR-ZONE-001, DET-CREDIBLE-DRIVE-001 taint,
  DET-ANCHOR-EGRESS-001 — con la condición del 06 de re-validar los 2 sabores del birth contra
  Enamorados/CameliasOppo —, DET-CONFIRM-ANCHOR-001 como consumidor). Absorbe los ~12 campos/tags
  del 06 **+ el nuevo DET-CAR-REST-CLOCK-001** como prueba viviente de la tesis: el fix del 18-08
  tuvo que inventar OTRO inquilino suelto (`anchorRestMs` inline en el collect) porque no había
  objeto al que preguntarle — 3 FN en 3 días por la ausencia del dueño.
- **Matiz documentado**: S3 afirma que «el código va POR DELANTE del 06» y que el 06 no cataloga
  DET-CAR-REST-CLOCK-001; la cabecera vigente del 06 ya lo añadió post-síntesis (133 tags, con el
  desplazamiento ~+14 de las línea-refs de EvalUS). El fondo del matiz de S3 se mantiene: el taint
  nació SIN dueño y ese es el argumento.
- **Nota de diseño**: `lockedBySteps` depende de `stepCount`, que el dueño recibe como entrada de
  transición, no posee (§2.4). Los veredictos consumidores (EvalPD, EvalUS, user-confirm) siguen
  siendo casos de uso aparte.

### 3.2 Clasificador único persona/coche — **DESCARTADO como concepto único; CONFIRMADOS sus 2 núcleos**

Los 7 predicados comparados en S3 difieren genuinamente en los tres ejes (base / ventana / testigo),
y dos son señales latcheadas con estado propio que un clasificador puro por fix no puede subsumir.
El punto de fusión YA existe y es un solo sitio: el `when` de precedencia de `effectiveDriving`
(CPD:2279-2288), cuya ORDENACIÓN es el contenido — cada línea tiene su incidente (Enamorados,
Bodegas Osborne, Camelias-Oppo, Galeote). **El `when` queda intacto**: aplanarlo en configuración
sería menos legible que el `when` comentado, la parte mejor documentada del fichero.

Lo que SÍ se confirma (coherente con 06 §3-b y con la frontera de 3-a):
1. **`outrunsPedestrianReach` ×4** — la familia envelope, verificada carácter por carácter.
2. **Corroboración-por-desplazamiento ×2** — `isCorroboratedVehicleHop` +
   `isSustainedDepartureFromAnchor`: núcleo extraíble, veredictos separados.

Colocación opcional: si AnchorTrust se ejecuta, `effectiveDriving` es la transición `clear` del
dueño y puede vivir como función pura junto a él — moverlo es opcional; fragmentarlo, no.

### 3.3 `DriveProof` — **CONFIRMADO**

Tipo acumulador puro por fix con grados **HINT (pico) → RUN crudo → PROVEN(provenance)**:
`peakMps`, `credibleFixCount`, `shortHopRun`, `proven: null | TrackWindow | ShortHop`,
`recentFixes` (ring; absorbe también `creepWindow`, ver §5), con `provenMaxSpeedMps` y la
**promoción retroactiva del pico banked al llegar la prueba (CPD:815)**. El grado RUN nunca
promociona a PROVEN por sí solo (compra zona, jamás pin). El «cómo» (`proven=short_hop` vs
`track_window`) pasa a ser citable en diagnóstico — hoy solo existe como línea de logcat.

- **`hasEverReachedDrivingSpeed` queda FUERA del tipo, a propósito**: es la AUTORIZACIÓN de ciclo
  de vida («el evento nomina»), no un grado de prueba — meterlo fundiría nominación con
  confirmación, exactamente el bug que DET-G-05 cerró. Preserva DET-G-04/G-05 (el seed toca ese
  flag, que no entra en el tipo). Matiz explícito sobre el 06: su §3-a no enuncia esta exclusión
  literalmente; S3 la deriva de los mismos tags y es coherente con el límite «los veredictos no se
  fusionan» — se documenta como refinamiento de diseño, no como contradicción.
- **Cruce con 06 §3-a**: coincide — verificador único parametrizado por perfil (pre-arm /
  worker-live / reconcile / short-hop-track / track-window / hop), tags verificados en S3:
  DET-DRIVE-PROOF-001, DET-CREDIBLE-DRIVE-001 (frontera), DET-SHORT-HOP-PROOF-001,
  DET-UNVERIFIED-ARM-DRIVE-PROOF-001 (dos etiquetas → una ficha del tipo, como pide el 06),
  DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001, DET-G-04/G-05.

### 3.4 Doubt radius — **CONFIRMADO a media escala**

Los 3 deciders (EvalPD en vivo / EvalUS timeout / EvalHC abort) **siguen separados** — sus peldaños
responden preguntas distintas (¿duda acotable? vs ¿calidad del único testigo?) y son veredictos con
vocabulario de diagnóstico protegido por contrato de trazas; coherente con 06 §3-c y con el
resultado de P6 (§1). Lo que nace como concepto son dos piezas pequeñas:

1. **El sealed de FORMA del guardado** — hoy la misma semántica tiene 3 sealed con nombres
   distintos (`SaveZone`/`ApproximateZone`; `Ask`/`Prompt`; `SaveExact`/`ApproximatePin`). Un
   sealed común en `domain/detection/` (p. ej. `SavedParkingShape`: `ExactPin` / `BoundedZone` /
   `AskUser` / `KeepSilent`) que cada veredicto EMITE junto a su razón propia (las razones NO se
   unifican).
2. **La función única del radio honesto** — hoy el radio final se calcula en 2 sitios con reglas
   distintas: `saveUnattendedZone` aplica floor Y TECHO (CPD:1501-1504) mientras EvalHC aplica solo
   el floor (:215, :319) — ver bug latente en §5. `honestZoneRadius(centerAcc, doubt, config)`
   elimina la divergencia.

- **Matiz sobre 06 §3-f.1**: el 06 dejaba la fusión de las dos escaleras «pendiente de
  demostración». Fase 3 la ADJUDICA en dos mitades: la fusión de deciders NO se demuestra (S1 P6:
  solo comparten la frase, no el álgebra) y lo que SÍ se demuestra es el tipo de resultado
  compartido + la función de radio (S3). Con ello, DET-GAP-ANCHOR-ZONE-001 / DET-NODRIVE-ZONE-001 /
  DET-WALK-ENTERED-ANCHOR-ZONE-001 quedan como taints que aportan su `doubtMeters` (AnchorTrust) a
  un `zoneOrAsk` único, y DET-NEVER-SILENT-001 como el suelo de la escalera.

### 3.5 Cruce global con las 5 políticas del 06

| Política 06 §3 | Resultado Fase 3 | ¿Coincide? |
|---|---|---|
| 3-a DriveProof (verificador por perfil) | S1 P2/P3/P14 + S2 (3 helpers + frontera) + S3 candidato 3 | ✅ (matiz: exclusión explícita de `hasEverReachedDrivingSpeed`, §3.3) |
| 3-b envelope peatonal ×4 → 1 | S2 (4 helpers EXTRAER) + S3 candidato 2 núcleo 1 | ✅ íntegro |
| 3-c walked-vs-rode predicado compartido | S1 P5 | ✅ (matiz: los dos guards frozen no son copias — el predicado admite AMBAS cláusulas de salud, testigo Y física) |
| 3-d `evidencia ≥ sessionStart` ×4 → 1 | S1 P15 | ✅ trivial |
| 3-e AnchorTrust dueño de estado | S2 (7 helpers + 5 condiciones) + S3 candidato 1 | ✅ (+1 inquilino nuevo: `anchorRestMs`) |

### 3.6 Conceptos menores descubiertos (S3)

- **`SessionOutcome` sellado** con `isConfirmed` como propiedad, manteniendo las serializaciones
  exactas (contrato de trazas) — misma enfermedad que el sealed de forma (§3.4.1); hoy el outcome
  se discrimina por prefijo de string (ver §5).
- **`creepWindow` absorbible por el ring de DriveProof** — nota de diseño, ver §5.

---

## 4. Catálogo de ruido (separable sin tocar decisiones)

Consolidado de S4. Dos columnas conceptuales: **RUIDO PURO** (mecánico, cero riesgo, sin decisión de
diseño) vs **ROZA DECISIÓN** (misma semántica pero exige elegir dueño o cambiar firmas — test antes).

### 4.1 RUIDO PURO (mecánico)

- **FQN inline — 6 grupos, ~36 ocurrencias**: 16× `io.apptolast.paparcar.domain.util.haversineMeters`
  en CPD (el plan decía ~15; verificadas 16); 3× `ArmEvidence.LABEL_*` en EvalPD:194-196; 4×
  `PendingDetectionStore` + 7× `domain.detection/location` en CDS; 3× en `ParkingSafetyNetWorker`;
  ~10 en bindings Koin (bajo valor, opcional). Todo: un import + replaces.
- **Dobles cálculos menores (4)**: `hasKinematicEgressSignal` ×2 en el mismo `if` (CPD:1256+1269);
  `isAnchorPinned`/`isAnchorWalkEntered` recomputados solo para el log (CPD:1153-1175);
  `now - sessionStartMs` ×2 (CPD:789+796); relectura `_detectionState.value.stoppedSince` tras el
  `update` (CPD:2215 — usar el valor de `updateAndGet`).
- **Constantes (2)**: outcomes `"aborted_false_enter"`/`"aborted_no_movement"` hardcodeados en
  CPD:934/987 mientras `DetectionSessionOutcomes` (SentryWakeCooldown.kt:25-26) ya es el dueño que
  el service lee — un typo rompería el cooldown del sentry en silencio; añadir además
  `ABORTED_NO_MOVEMENT_JAM`/`ABORTED_NO_VEHICLE`/`ABORTED_RESPONSE_TIMEOUT` al object. Y
  `"parking_safety_net"` duplicado entre `ParkingSafetyNetWorker.PREFS_NAME` y
  `SentryResidenceStore` (mismo fichero de prefs; si divergen, el residence deja de verse).
- **Logs (3)**: fusionar los DOS Napier por fix del hot loop (CPD:733-736 + 826-832 — el dual-channel
  con `DetectionEvent.LocationFix` es de diseño y se queda); el log que recalcula la cota
  `hueco_s × peatón` del dominio (CPD:2098-2105 — loguear solo `newStopGapMs`); el log que
  re-invoca predicados ya computados 10 líneas antes (CPD:1164-1175).

### 4.2 ROZA DECISIÓN (requiere test antes)

- **Dobles cálculos de haversine (3 patrones + 1 O(n²))**. El peor: en la rama "moving" de
  `updateStopTracking`, `movementOutrunsSteps` + `isSustainedDepartureFromAnchor` +
  `escapesAnchorEnvelope` calculan CADA UNO `haversine(ancla → fix)` — **hasta 3× por fix DENTRO de
  la lambda de `_detectionState.update`** (CPD:2260/2261/2275), re-ejecutable bajo contención CAS
  (→ 6×, 9×…). Mismo patrón en cada decision-input (`hasEgressDisplacement`+`egressExceedsWalkReach`
  ×2 por pasada, 3 call sites) y en `isEgressBornAtAnchor`+`refinedParkLocation` (anchor↔birth ×2).
  Arreglo: una `dAnchor` calculada antes del `update` y pasada a los predicados (cambia firmas
  privadas — que es exactamente lo que la extracción de §2 hará de todos modos). Y
  **`corroboratesDrive` es O(n²)/fix** (techo teórico ~2.300 haversines por fix, con realocación de
  lista por anchor del ring): cortar temprano + sub-rango por índice, misma semántica.
- **Constantes duplicadas (7 con decisión de dueño)**: `KMH_PER_MPS = 3.6f` en **9 companions + 1
  inline** (CPD:360, que además viola la regla de magic numbers) → const/extension en `domain/util`
  y barrer los 10 consumidores; `JAM_CREEP_MAX_ACCURACY_M = 50f` (CPD:2539) duplica
  `config.minGpsAccuracyForDriving` (el propio comentario de CPD:748 los llama «same 50 m gate») →
  usar config o documentar por qué diverge; `IMPLAUSIBLE_REPARK_PROMPT_SCORE`/`WEAK_EVIDENCE_PROMPT_SCORE`
  = 0.6f con el mismo rol; `"system"` como sessionId sintético ×2; `INITIAL_BACKOFF_SECONDS` ×3 con
  `MAX_RETRY_ATTEMPTS` divergiendo sin comentario (3 vs 5 vs 5); `INTERVAL_HOURS`+`MAX_RETRIES` ×2
  → fichero de config de workers.
- **⚠ Los 2 falsos amigos — NO tocar**: `2.5f` (cota peatonal de zonas vs barra inferior de la
  banda ambigua) y `8` (cuatro umbrales de config independientes que hoy coinciden). Mismo número,
  conceptos distintos.
- **Logging (3 con decisión)**: `PaparcarLogger` es **100 % eager** (sin overload lambda) y el CPD
  tiene **69 llamadas**, 2 incondicionales por fix en el hot loop → añadir `d(tag, msg: () -> String)`
  y barrer el hot path. **Side-effects dentro de lambdas `update`/`updateAndGet`** (CPD:759, 762,
  804 dentro de :739; 2098-2105, 2229-2234, 2290-2316, 2328-2333 dentro de :2073/:2236): bajo
  contención CAS → líneas de log y haversines duplicados; sacar el cómputo o loguear tras el
  `update`. Y las **5 ramas solo-log de `updateStopTracking`** (CPD:2228-2235, 2289-2296,
  2297-2303, 2304-2317, 2327-2334): el veredicto que narran no tiene dueño citable — es señal para
  DriveProof/AnchorTrust, parte del refactor grande, NO tocar ahora.

Recuento S4: FQN 6 grupos (6 puros) · dobles cálculos 8 (4 puros / 4 decisión) · constantes 11
filas (9 duplicadas reales: 2 puras / 7 decisión; 2 falsos amigos) · logs 6 patrones (3 puros / 3
decisión).

---

## 5. Bugs latentes y contratos frágiles detectados en Fase 3 (para adjudicar, NO arreglar)

1. **`ReleaseActiveParkingSessionUseCase` divergió en silencio de su gemelo** (S1 P1): (a) NO
   comprueba `privateZoneId` antes de publicar el spot — un `DEPARTURE_PUBLISHED` desde la UI sobre
   una sesión en zona privada publica el spot comunitario (`ParkingReleaseReason` no codifica zona
   y HomeViewModel no la mira; **NO VERIFICADO** si la UI esconde el botón para sesiones
   HOME_GEOFENCE — si no lo hace, es un bug real); (b) NO resetea `DepartureEventBus` (ni lo
   inyecta) — tras una liberación manual el bus conserva el ENTER viejo del viaje de llegada
   (Process:80 sí resetea). **¿Bug o intención? Bloquea la parametrización de P1 hasta adjudicar.**
2. **Posible zona SIN techo en el path honest-close**: EvalHC:319 calcula su radio de zona con solo
   el floor (`max(acc, honestCloseMinZoneRadiusMeters)`) mientras el path desatendido aplica
   también el techo `unattendedZoneMaxRadiusMeters` (CPD:1501-1504). **NO VERIFICADO** si el
   consumidor de `ApproximateZone` (CDS:860-882, androidMain — no leído en Fase 3) aplica el techo
   después. Comprobar en F4; la `honestZoneRadius` única del §3.4 lo cierra por construcción.
3. **`sessionOutcome` discriminado por PREFIJO de string** (`startsWith("confirmed_")`, CPD:1520;
   y `UnattendedSaveReason` transporta 3 strings por convención porque dos etiquetas históricas no
   siguen el patrón): contrato frágil — un outcome nuevo mal nombrado cambia la semántica en
   silencio. Remedio catalogado en §3.6 (`SessionOutcome` sellado con serializaciones exactas).
4. **`creepWindow` vs `recentFixes`** (02 §7.5): dos rings de fixes recientes con podas distintas.
   Nota de diseño para F4: el ring pertenece a DriveProof y el jam-creep es candidato a consumir el
   MISMO ring con su propia ventana — pero eso exige demostración propia (el jam lee una ventana
   temporal distinta); no se afirma aquí.

### Discrepancias entre parciales (documentadas, no resueltas)

- **Línea-refs desfasadas entre parciales**: S1/S2 citan el CPD/EvalUS del snapshot base
  (`corroboratesDrive` CPD:1961-1988, rebind :2165-2193, `zoneOrAsk` EvalUS:304-314) mientras S3/S4
  leyeron el árbol post-`fbc83847` (DET-CAR-REST-CLOCK-001), con desplazamientos de ~+12/+14
  (`corroboratesDrive` :1973-1994, rebind :2177-2206, `zoneOrAsk` :320-330). No es contradicción de
  contenido — el propio 06 anota el desplazamiento en su cabecera — pero en F4 las citas deben
  re-anclarse sobre UN commit.
- **S3 vs cabecera del 06**: S3 afirma que el 06 «no cataloga» DET-CAR-REST-CLOCK-001; la cabecera
  vigente del 06 ya lo incorpora post-síntesis (133 tags). El fondo del argumento de S3 (el taint
  nació sin dueño) se mantiene intacto (§3.1).
- **Conteo de haversines FQN**: S4 verifica **16** ocurrencias en CPD donde el plan de fase decía
  ~15 — corrección menor, adoptada aquí.

---

## 6. Resumen ejecutivo para el user

- **Nada se funde**: 0 pares de use cases justifican fusión — toda la duplicación real del
  subsistema es de PREDICADOS, nunca de veredictos, exactamente lo que tu doctrina predice.
- **3 se parametrizan**: el cierre de sesión (Process↔Release, un núcleo con `reason` — bloqueado
  por 2 divergencias bug-o-intención), las dos pruebas del latch `driveProven` (SHP↔`corroboratesDrive`
  como perfiles de DriveProof) y el `evidencia ≥ sessionStart` ×4 (función única trivial).
- **8 se mantienen separados** porque son veredictos distintos con vocabulario de diagnóstico
  propio (Detect/Verify/SafetyNet, HonestClose↔SafetyNet, HonestClose↔UnattendedSave, Revert,
  Confidence↔Decision, Evaluate↔Observe reliability).
- **4 se eliminan**: `GetLastKnownLocation` (muerto), `SendSpotSignal`, `ClearParkNudge` (inline en
  su único caller) y `NotifyParkingConfirmation` (plegado en el port de notificaciones).
- **Nacen 2 dueños + 2 piezas**: `AnchorTrust` (7 helpers + sellado ×5→1 + cascada de 9→`clear()`;
  DET-CAR-REST-CLOCK-001 es la prueba de que faltaba) y `DriveProof` (HINT→RUN→PROVEN con
  provenance citable; `hasEverReachedDrivingSpeed` fuera). El clasificador persona/coche único se
  DESCARTA (el `when` comentado queda); de él salen `outrunsPedestrianReach` ×4→1 y la corroboración
  por desplazamiento ×2. Del doubt radius nace el sealed de forma del guardado + la función única
  de radio honesto; los 3 deciders siguen separados.
- **Ruido separable en mecánico puro**: ~36 FQN, 4 dobles cálculos, 2 constantes y 3 patrones de log
  se pueden limpiar hoy sin tocar ninguna decisión; el resto (KMH_PER_MPS ×10, haversine ×3 dentro
  de lambdas CAS, `corroboratesDrive` O(n²), logger eager con 69 llamadas) exige elegir dueño o
  cambiar firmas — con test delante.
- **4 hallazgos a adjudicar antes de F4**: Release sin gate de zona privada ni reset del bus;
  posible zona sin techo en honest-close (NO VERIFICADO el consumidor); outcome por prefijo de
  string; los dos rings de fixes.
