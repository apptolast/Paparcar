# Auditoría del sistema de estadísticas e insights

**Ticket:** STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001 · **Fecha de auditoría:** 2026-09-22
**Regla rectora:** *ABSENCE OF EVIDENCE = ABSENCE OF DATA.* Toda cifra mostrada procede de un dato
real, de una fórmula determinista sobre datos reales, o de una estimación identificada como tal.
Sin datos suficientes → sin tile / «sin datos», nunca un default que parezca dato.

> Convención de citas: se nombran ficheros y símbolos, nunca líneas — las líneas caducan con el
> primer commit. El contrato métrica a métrica (definición, inputs, fallback, UI) vive en
> [`analytics-data-contract.md`](analytics-data-contract.md).

---

## 1 · Mapa del pipeline

Una sola tubería alimenta la pantalla de Actividad/Estadísticas y la sección Actividad de la ficha
de vehículo (ambas componen los mismos hijos: `ActivityCard` y `VehicleStatsBlock`):

```
Detección (ConfirmParkingUseCase — TODO pin converge aquí)
    │  estampa: location(+timestamp), detectionPath, armEvidence, reliability,
    │  routePolyline (encodeFreshRoute), searchTimeMs (ParkingSearchTime.measure)
    ▼
UserParkingRepositoryImpl.saveNewParkingSession
    │  estampa routeDistanceMeters (PolylineCodec.lengthMeters) — choke point de toda ruta
    ▼
Room `parking_sessions` (fuente de verdad de stats; Firestore es espejo/restauración)
    │  cierre: clearActiveById (endedAtMs COALESCE — el primer cierre gana)
    ▼
StatsViewModel.observeAllSessions → HistoryTimeline.resolve
    │  gate de plan (HistoryEntitlementGate, free = 7 días) → filtro de periodo
    │  (VehicleHistoryCalculator.windowStartMs/EndMs) → ventana previa (previousWindow)
    ▼
ComputePeriodStatsUseCase (+ ResolveFuelPriceUseCase para precios)
    │  → PeriodStats (con ScopedMeasure = cifra + cobertura)
    ▼
UI: VehicleStatsBlock (tiles) · HistoryWeeklyChart/ActivityCard (barra semanal) ·
    StatsInsightsCard (DeriveStatsInsightsUseCase) · StatsMonthRecapCard ·
    StatDeltaFormat (deltas ×N / %)
```

Datos remotos verificados durante la auditoría (Firestore `users/{uid}/parkingHistory`, 22-09):
el documento de sesión NO tiene métricas agregadas; `searchTimeMs` existe por sesión desde
~13-09; las cifras de la pantalla se derivan SIEMPRE en cliente desde Room.

## 2 · Clasificación de cada métrica

| Métrica (tile/label) | Fuente primaria | Fórmula | Clase | ¿Podía ser falsa? |
|---|---|---|---|---|
| Nº aparcamientos | filas Room en ventana | `sessions.size` | REAL | Sesgo de reloj (ver §3.2) |
| Duración media | `endedAtMs − location.timestamp`, ≥3 sesiones | media entera | DERIVADO | **SÍ** — ambos relojes llevaban latencia (§3.2, §3.3) |
| Buscando plaza (tile+barra) | `searchTimeMs` por sesión | suma con cobertura (`ScopedMeasure`) | **INFERIDO→MEDIDO** | **SÍ** — el caso del martes (§3.1) |
| Hora punta | hora local de `location.timestamp`, ≥5 | moda | DERIVADO | Sesgo de reloj (§3.2) |
| Día habitual | día ISO de `location.timestamp`, ≥5 | moda | DERIVADO | Sesgo de reloj (§3.2) |
| Sitio habitual | clustering 120 m, ≥3 por sitio | `clusterParkings` | DERIVADO | No (umbral y naming honestos) |
| Distancia | `routeDistanceMeters` (polilínea medida; inferida pin-a-pin marcada) | suma con cobertura | DERIVADO / parcialmente ESTIMADO | Parcial en recap sin nota (§3.5, corregido) |
| Combustible (L/kWh) | distancia × consumo (vehículo o `FuelDefaults`) | determinista | **ESTIMADO** | Identificado («aprox.» falta en el tile de combustible — decisión §6) |
| Gasto aprox. | energía × precio (`ResolveFuelPriceUseCase`) | determinista, se niega sin precio/2 divisas | **ESTIMADO** | El precio embebido no gatea antigüedad (§4.2) |
| CO₂ aprox. | litros × factor (`EmissionFactors`: 2.31/2.68/1.66 kg/L · 0.25 kg/kWh) | determinista | **ESTIMADO** | Identificado («aprox.») |
| Detectados solos (71 %) | `detectionPath`→`ParkingDetectionSource`, ≥5 conocidas | `auto/known`, cap 99 % | DERIVADO | No — denominador excluye Unknown; ni la mano (`Manual`) ni un «sí» a un aviso (`Confirmed`) cuentan como auto [DET-A-YES-IS-NOT-A-HAND-001] |
| Plazas cedidas | `publishedSpot` (política de publicación real) | conteo; el 0 se muestra | REAL | No |
| Deltas ↑×N / % | ventana previa equivalente | `StatDeltaFormat.statDelta` | DERIVADO | Previo 0/null → **sin badge** (correcto); recap sin gate de cobertura (§3.5, corregido) |
| Insights (≤3) | umbrales por insight | `DeriveStatsInsightsUseCase` | DERIVADO | `isUp` inventaba dirección sin baseline (§3.6, corregido) |
| Ritmo/proyección | fracción transcurrida 25–90 % | `projectPeriod/projectYear` | ESTIMADO | Identificado como proyección, con guardas |
| Heatmap día×franja | sesiones/`searchTimeMs` | conteo · media (≥2 muestras) | DERIVADO | No |

## 3 · Problemas encontrados (y qué se hizo)

### 3.1 ⛔⛔ «Buscando plaza» medía LATENCIA DE CONFIRMACIÓN — el caso del martes
- **Evidencia de campo (Firestore, uid del autor):** martes 22-09 00:26, sesión `3d95ffc8`,
  `detectionPath = user` (pin confirmado a mano tras un viaje directo a casa) con
  `searchTimeMs = 387 236` (~6,5 min → la barra de «6 min» del martes). Martes 15-09, sesión
  `3b2dad8c`, `unattended_timeout`, 417 001 ms (~7 min): cerrada por la red de seguridad, no por
  una llegada observada.
- **Causa raíz:** `ParkingSearchTime.measure` v1 cerraba el reloj en `anchor.timestamp`, y en tres
  caminos (pin manual, «Sí, he aparcado», timeout desatendido) ese timestamp es un **reloj de
  pared** (el momento de confirmar). Todo lo que pasó entre la parada real y la confirmación —
  andar, esperar el nudge, el timeout de 15 min — se contaba como «búsqueda». Además, la v1
  cobraba por diseño un «suelo» de 30–60 s de aproximación final a TODA llegada directa.
- **Corrección (v2, mismo fichero):** el reloj termina en la **parada medida** — el último fix de
  la ruta grabada que está físicamente en el pin (`STOP_NEAR_METERS`), hallada por geometría y por
  tanto independiente del carril que confirme. Y la llegada directa es un **0 medido**: solo hay
  búsqueda cuando la línea conducida dentro del radio excede la entrada recta por ratio
  (`DIRECT_EXCESS_FACTOR`) y por exceso absoluto (`MIN_EXCESS_METERS`) y dura ≥`MIN_SEARCH_MS`.
  Tres veredictos: `null` = no medible · `0` = directo medido · `>0` = vueltas demostradas.
- **Datos ya almacenados:** irrecuperables (la polilínea no guarda relojes) → `MIGRATION_19_20`
  los ANULA y los marca `pendingSync` para que la anulación limpie también el espejo remoto; el
  reconcile de `searchTimeMs` pasa a autoridad local (si existe fila local, su veredicto manda,
  incluido el null) para que la nube no resucite cifras retiradas.

### 3.2 ⛔ El inicio de sesión se fechaba con el reloj del confirm
`ConfirmParkingUseCase` sustituía el timestamp del fix ancla por `Clock.System.now()`. Un confirm
por pasos+egress llega minutos tarde y un backfill horas tarde → duración media, hora punta, día
habitual y el encuadre de periodo se calculaban sobre la hora en que el código terminó de decidir,
no sobre la parada. **Corregido:** el pin se fecha por el fix medido, acotado a plausibilidad
(sin reloj o «del futuro» → reloj del confirm). El supersede del DAO cierra ahora al predecesor con
el inicio medido del sucesor, no con el reloj del procesado.

### 3.3 ⛔ El fin de sesión ignoraba el instante medido que ya tenía en la mano
`RunDepartureCheckUseCase` calcula `departedAtMs` (el fix que cruzó la valla) y lo usaba SOLO para
el gate de publicación; el cierre estampaba `now()` — una salida recuperada 5 h tarde (Redmi
06-07) inflaba la estancia 5 h. **Corregido:** `ProcessConfirmedDepartureUseCase` acepta
`departedAtMs` (sin default: toda puerta contesta), fecha `endedAtMs` y la ventana de refutación
con él; watchdog y still-parked declaran honestamente `null` (atestiguan el hecho, no la hora) y
caen al reloj de proceso como cota «no más tarde de». `FinalizeDeducedDepartureUseCase` ya lo
hacía bien y queda como estaba.

### 3.4 Los tres caminos con reloj de pared, tras la corrección
`user` / `manual` / `nudge`: la parada sale de la geometría de la ruta grabada; sin ruta fresca →
`searchTimeMs = null` (nunca minutos de espera). `unattended_timeout` / `safety_net_backfill`:
ídem; su latencia de 15 min+ ya no puede entrar en la cifra.

### 3.5 Recap mensual: comparaba sumas parciales y publicaba una distancia parcial sin nota
Los tiles gatean deltas con `coverageAllowsComparison` y acompañan sumas parciales con «N de M
viajes»; la `StatsMonthRecapCard` no hacía ninguna de las dos cosas. **Corregido:** mismos gates
que los tiles; la distancia del titular solo se imprime completa (un titular no tiene sitio para
la nota de cobertura → completa o silencio).

### 3.6 `InsightValues.isUp` inventaba dirección con baseline ausente
`(valueMs ?: 0) >= (baselineMs ?: 0)` convertía un baseline ausente en un «↑» seguro. Latente (los
productores garantizan ambos lados), pero un bug futuro de productor habría salido en pantalla
como flecha fabricada. **Corregido:** `isUp` es `Boolean?` y la card salta un insight de tendencia
sin dirección en vez de pintarlo.

### 3.7 Auditado y declarado CORRECTO (sin cambio)
- **Deltas ×N:** `statDelta` ya se niega con previo null, previo 0, o delta que redondea a 0 —
  no existe el «×∞» ni la comparación fabricada. `All` no tiene ventana previa → sin badge.
- **Política «campo null → sin tile»:** `statTileKeys` + KDoc de `PapStatTile` («un guion se lee
  como cero») ya implementan la regla rectora en UI. Excepciones deliberadas: `SPOTS_SHARED`
  muestra el 0 (es información) y `AUTO_DETECTED` exige denominador > 0.
- **Umbrales de datos mínimos:** duración ≥3 · hora punta/día habitual ≥5 · % auto ≥5 conocidas ·
  sitio ≥3 aparcamientos · media de celda del heatmap ≥2 · proyección solo con 25–90 % del periodo
  transcurrido · tendencias con ambos lados medidos y cambio mínimo.
- **Coste:** se niega ante tres casos (sin energía, energía sin precio, dos divisas). El agregado
  solo suma sesiones CON distancia medida — no fabrica km.
- **`detectionSharePercent`:** nunca imprime 100 % si `auto < known` (cap 99).
- **Retracciones:** las filas retiradas (`retractedAtMs`) salen del historial y de toda estadística
  a nivel DAO.
- **«Últimos 7 días» / semanas:** ventanas medio-abiertas `[start, end)` sobre hora local
  (`TimeZone.currentSystemDefault()`), previa = ventana de calendario equivalente. Sin bug de
  medianoche encontrado; el sesgo real era el reloj de §3.2, no la ventana.
- **Stale/caché:** stats leen Room reactivo (`observeAll`), sin caché intermedia; Firestore solo
  entra por reconcile explícito. «Dato antiguo» ≠ «dato incorrecto»: el historial viejo es válido
  y no se poda (el plan free solo estrecha la VENTANA de cálculo).

### 3.8 Deudas conocidas que se DOCUMENTAN y no se tocan en este ticket
- **Barra del chart con `?: 0f`** (`ChartMetric.valueOf`): un día sin medida dibuja pista vacía —
  visualmente idéntico a un 0 medido. La cobertura se paga a nivel de card (qualifier). Aceptado y
  documentado en el contrato; separar «0 medido» de «sin medida» por barra exigiría un canal de
  cobertura por bucket que hoy no compra nada (ambos pintan igual).
- **Precio embebido sin gate de antigüedad** (§4.2): mitigado porque la UI imprime SIEMPRE la
  fecha del precio (`fuelPriceNoteText`); el gate duro de frescura vive solo en el carril de
  avisos de gasolinera. Decisión de producto pendiente si se quiere negar el gasto con tabla >N días.
- **Ruta inferida pin-a-pin** alimenta distancia→combustible como si fuera medida a efectos de
  cobertura; está marcada (`routeInferredSpans`) y es cota inferior. Documentado en el contrato.
- **`tripMaxSpeedMps` es local-only**: se pierde al migrar de móvil (no alimenta stats, solo guards).
- **KDoc huérfano de `searchTimeMs`** en modelo/entity/DTO (el bloque sienta sobre
  `communitySpotId`): cosmético, corregido de paso en el modelo de dominio… *(ver commit)*.

## 4 · Estimaciones: inputs y transparencia

### 4.1 Cadena del gasto
`km medidos (ruta) × consumo (vehículo.consumptionPer100 || FuelDefaults por tipo) × precio`.
Precio por escalera: override del usuario → gasolinera (SIN productor hoy — peldaño muerto
esperando FUEL-STATIONS-001) → media nacional embebida (`EmbeddedAverageFuelPrices`, con `asOf`)
· kWh: constantes mensuales a mano (`StaticAverageFuelPrices`). País = país de la sesión más
reciente del periodo (geocodificación asíncrona; sin país → sin tile de gasto).
La UI ya imprime la fecha del precio y el tile se llama «gasto aprox.».

### 4.2 Defaults que se convierten en cifra (deliberados, documentados)
`FuelDefaults` (6.5 L gasolina · 5.5 diésel · 8.5 GLP · 5.0 HEV · 3.0+12 kWh PHEV · 17 kWh EV ·
3.5 moto) y la fila `EU 0.25 €/kWh` para países sin constante propia. Son estimaciones de
producto: deterministas, con fuente única, y solo alimentan tiles marcados «aprox.». El usuario
puede sustituirlas con su consumo/precio real.

### 4.3 CO₂
`pumpProduct` decide el factor (HEV/PHEV emiten a factor gasolina); EV usa mix eléctrico UE
0.25 kg/kWh. Nada se muestra si no hay distancia medida.

## 5 · Casos de test que fijan la regla (añadidos en este ticket)
1. Viaje directo a casa → `searchTime = 0` medido (no suelo, no latencia).
2. Aproximación lenta (semáforos) que supera el suelo temporal pero conduce recto → 0.
3. Vueltas a la manzana → se cobran TODAS, con el reloj terminando en la parada medida.
4. Pin manual marcado 6,5 min después de aparcar (el martes) → 0, no 387 s.
5. Timeout desatendido con búsqueda real → la búsqueda sí, la latencia no.
6. Caminata post-aparcar >300 m antes de confirmar → ni extiende ni resetea el reloj.
7. GPS muerto a 250 m del pin → `null` (llegada no atestiguada), nunca una cifra.
8. Anomalías (sin reloj, >45 min) → `null`.
9. Cierre de sesión fechado por la salida medida; hora desconocida → reloj de proceso; instante
   anterior al aparcamiento → anomalía → reloj de proceso.
10. Sesión fechada por el fix del ancla (backfill 2 h tarde) y fallback con fix sin reloj.
11. Migración v20: anula la cifra v1, marca sync, no toca filas sin cifra ni el resto de campos.
12. (Preexistentes, verificados) previo 0 → sin delta; cobertura parcial → sin delta en tiles.

## 6 · Decisiones de producto — RESUELTAS por el user el 22-09
[STATS-AN-ESTIMATE-NAMES-ITSELF-AND-ITS-AGE-001]
- **Etiquetas de combustible y energía:** llevan el calificador de estimación en los 9 locales,
  con la abreviatura que su propio tile de gasto ya usaba en cada lengua. ✅
- **Gate de antigüedad del precio embebido:** una media nacional con más de 180 días no precifica
  (`MAX_NATIONAL_AVERAGE_AGE_DAYS`); por debajo, se sigue mostrando con su fecha. El override del
  usuario no caduca. ✅
- **Limpieza remota adicional:** ejecutada por MCP sobre los espejos con `searchTimeMs` v1 (además
  del outbox de la migración, que cubre los dispositivos que actualicen). ✅
