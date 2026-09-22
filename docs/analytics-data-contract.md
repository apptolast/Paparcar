# Contrato de métricas — qué significa exactamente cada cifra

**Ticket fundacional:** STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001 · **Vigente desde:** 2026-09-22
**Regla rectora:** *ABSENCE OF EVIDENCE = ABSENCE OF DATA.* Cada métrica de este contrato responde
a «¿de dónde sale exactamente este dato?». Cambiar una fórmula sin actualizar su ficha aquí es
romper el contrato.

> Sin `fichero:línea` a propósito: se citan símbolos. La auditoría que originó esto:
> [`analytics-data-audit.md`](analytics-data-audit.md).

Vocabulario de clases de dato:
- **REAL** — observado directamente (un timestamp de fix, un flag de publicación).
- **DERIVADO** — aritmética determinista sobre datos reales (una resta, una moda, una suma).
- **ESTIMADO** — fórmula declarada sobre datos reales + constantes de producto documentadas; la
  UI lo identifica («aprox.», nota de precio).
- **INFERIDO** — deducido de señales indirectas. Regla: un inferido NO se muestra como cifra
  salvo que se convierta en medida con evidencia (así se re-fundó «buscando plaza»).

Representación de la ausencia: campo null → **el tile no se compone** (nunca «—», que se lee como
cero); suma parcial → nota de cobertura «N de M viajes»; comparación sin base honesta → **sin
badge**.

---

## Buscando plaza (search time)

- **Definición:** tiempo de CONDUCCIÓN dentro de un radio de 300 m del pin final, desde la última
  re-entrada al radio hasta la **parada medida**, y solo cuando la geometría demuestra vueltas.
  No es «tiempo hasta que se registró el aparcamiento».
- **Evidencia:** fixes crudos de `DrivingRouteStore` (relojes intactos) + posición del pin.
  Productor único: `ParkingSearchTime.measure`, llamado una sola vez en `ConfirmParkingUseCase`
  (la polilínea persistida no guarda relojes → irrepetible).
- **Cálculo:** parada = último fix a ≤35 m del pin (`STOP_NEAR_METERS`; la caminata posterior se
  descarta por geometría, no por reloj). Entrada = primer fix tras la última vez a >300 m
  (`SEARCH_RADIUS_METERS`) antes de la parada. Búsqueda demostrada ⇔ trazado dentro del radio
  > línea recta × 1.8 (`DIRECT_EXCESS_FACTOR`) ∧ exceso > 250 m (`MIN_EXCESS_METERS`) ∧
  duración ≥ 90 s (`MIN_SEARCH_MS`).
- **Veredicto de tres valores:** `null` = no medible (sin ruta fresca adherida, llegada no
  atestiguada a ≤75 m, nunca salió del radio, sin relojes, fuera de [0, 45 min]) · `0` = llegada
  DIRECTA medida · `>0` = búsqueda medida.
- **Datos mínimos:** ruta adherida (≥2 puntos, frescura 30 min, extensión ≥150 m) con llegada
  atestiguada.
- **Fallback:** null → la sesión no aporta; agregados con cobertura (`sumMeasuredSearchTime` →
  `ScopedMeasure`). NUNCA se rellena con el reloj de confirmación: la latencia de un nudge, de un
  pin manual o del timeout de la red de seguridad no es búsqueda (el bug que fundó este contrato).
- **UI:** tile con suma + nota de cobertura si parcial; barra semanal (suma por día); heatmap
  (media por celda, ≥2 muestras); delta solo con cobertura completa en ambos periodos.
- **Sesgo aceptado (FN sobre FP):** una vuelta que SALE del radio y re-entra reinicia la entrada
  («last time far») → infracuenta; un crawl recto entre tráfico → 0. Preferimos perder minutos
  reales a facturar minutos falsos.

## Nº de aparcamientos / histórico
- **Definición:** sesiones (`UserParking`) cuyo inicio cae en la ventana; `parkings` incluye la
  activa, `endedParkings` no. Retractadas (`retractedAtMs`) excluidas a nivel DAO.
- **Evidencia:** fila Room por sesión; inicio = `location.timestamp` = **parada medida** del
  ancla (acotada a plausibilidad; fallback documentado: reloj del confirm).
- **Ventanas:** `VehicleHistoryCalculator` — medio-abiertas `[start, end)`, hora local del
  dispositivo; plan free → siempre «últimos 7 días» (`HistoryEntitlementGate`, la etiqueta lo dice).

## Duración media de aparcamiento
- **Cálculo:** media entera de `endedAtMs − location.timestamp` sobre terminadas con delta > 0.
- **Relojes:** inicio = parada medida; fin = `departedAtMs` medido del cruce de valla cuando
  existe; hora desconocida (watchdog/still-parked) → reloj de proceso como cota «no más tarde de».
- **Datos mínimos:** ≥3 duraciones (`MIN_SESSIONS_FOR_DURATION`); si no → sin tile.

## Hora punta / Día habitual
- **Cálculo:** moda de la hora local / día ISO del inicio de sesión, sobre terminadas.
- **Datos mínimos:** ≥5 sesiones (`MIN_SESSIONS_FOR_PEAK`); empates: arbitrario (documentado).

## Sitio habitual / sitios frecuentes
- **Cálculo:** clustering aglomerativo a 120 m (`ParkingClusters`, constante medida en campo);
  un cluster es «sitio» con ≥3 aparcamientos; nombre = zona del usuario > calle más repetida >
  POI. Sin nombre → no encabeza el tile.

## Distancia
- **Definición:** metros de la ruta adherida (`routeDistanceMeters`, estampada por el repo desde
  la polilínea — no pueden divergir). Rutas **inferidas** pin-a-pin (worker post-park para pines
  de confianza sin ruta) están marcadas (`routeInferredSpans`) y son cota inferior: alimentan la
  suma como estimación identificada por el propio trazo discontinuo en el mapa.
- **Fallback:** sin ruta → la sesión no aporta km (jamás se fabrica distancia sin GPS); sumas con
  cobertura; el recap solo imprime distancia COMPLETA.

## Combustible / Energía (ESTIMADO)
- **Fórmula:** `km medidos × consumo/100`, consumo = `vehicle.consumptionPer100` (dato del user)
  o `FuelDefaults` por tipo (constantes de producto: 6.5 gasolina · 5.5 diésel · 8.5 GLP ·
  5.0 HEV · 3.0 L + 12 kWh PHEV · 17 kWh EV · 3.5 moto). PHEV suma ambos vectores.
- **Mínimos:** sesión con distancia + vehículo con `fuelType`; si no → sin tile.
- **UI:** los tiles se identifican como estimación en su etiqueta («combustible aprox.» /
  «energía aprox.», con la abreviatura propia de cada locale — la misma que ya usa su tile de
  gasto). [STATS-AN-ESTIMATE-NAMES-ITSELF-AND-ITS-AGE-001]

## Gasto aprox. (ESTIMADO)
- **Fórmula:** `litros × €/L + kWh × €/kWh`. Escalera de precio (`ResolveFuelPriceUseCase`):
  override del usuario → precio de gasolinera (peldaño aún sin productor) → media nacional
  embebida con fecha (`asOf`); kWh: constantes mensuales. País = el de la sesión más reciente
  geocodificada del periodo.
- **Negativas obligatorias:** sin energía · energía sin precio · dos divisas → sin tile.
- **Caducidad:** una media nacional (litro o kWh) con `measuredOn` a más de **180 días**
  (`ResolveFuelPriceUseCase.MAX_NATIONAL_AVERAGE_AGE_DAYS`) ya no precifica — el tile de gasto se
  niega, igual que sin precio. El override del usuario y el peldaño de gasolinera no caducan por
  esta regla. Una fecha imparseable tampoco precifica.
  [STATS-AN-ESTIMATE-NAMES-ITSELF-AND-ITS-AGE-001]
- **UI:** «gasto aprox.» + `fuelPriceNoteText` SIEMPRE que hay gasto (qué precio y de cuándo);
  por debajo del umbral el precio se muestra con su edad.

## CO₂ aprox. (ESTIMADO)
- **Fórmula:** `litros × factor` (`EmissionFactors`: gasolina 2.31 · diésel 2.68 · GLP 1.66
  kg/L; HEV/PHEV emiten a factor de su `pumpProduct`) + `kWh × 0.25` (mix UE). Sin distancia
  medida → sin cifra.

## Detectados solos (%)
- **Numerador/denominador:** sesiones terminadas con procedencia CONOCIDA
  (`detectionPath → ParkingDetectionSource`; Unknown fuera de ambos lados). Auto = lo que la app
  detectó SOLA: quedan fuera la mano del usuario (`Manual`) y también la respuesta a un aviso que
  la app tuvo que lanzar (`Confirmed`) — si hubo que preguntar, no se detectó solo
  [DET-A-YES-IS-NOT-A-HAND-001].
- **Mínimos:** ≥5 conocidas; formato: redondeo entero con tope 99 % si `auto < known`.
- **FP/FN:** un pin retractado por su propia salida (`retractedAtMs`) sale de numerador Y
  denominador; un FN (parking no detectado que el user marca) cuenta como manual — el porcentaje
  se degrada, que es la dirección honesta.

## Plazas cedidas
- **Definición:** sesiones terminadas con `publishedSpot = true` — solo lo decide la política
  real de publicación (frescura del exit, zona privada, contradicción BT, retractación la anula).
  El **0 se muestra**: es información, no ausencia.

## Comparativas (↑ ×N / %)
- **Base:** ventana de calendario equivalente inmediatamente anterior; `All` no compara.
- **Negativas obligatorias** (`StatDeltaFormat.statDelta`): previo null · previo 0 (jamás ×∞ ni
  baseline fabricada) · delta que redondea a 0 %. Formato: % hasta ×3, «×N.N» hasta ×10, «×N»
  después.
- **Gate de cobertura:** toda magnitud con `ScopedMeasure` (distancia y derivadas, búsqueda)
  exige `coverageAllowsComparison` en AMBOS periodos — en tiles Y en recap.

## Insights (≤3)
- Cada insight lleva su **n** («N aparcamientos lo respaldan») y umbrales propios: peor/mejor
  franja (ratio 1.5 / 0.6 sobre media ponderada), sitio más caro (≥2 sitios, ratio 1.5),
  tendencias (ambos lados medidos, cambio mínimo, cobertura comparable). Una tendencia sin
  dirección medible (`isUp == null`) **no se pinta**.

## Ritmo / proyección (ESTIMADO)
- Solo con 25–90 % del periodo transcurrido (año: ≥21 días); se presenta como proyección.

## Recap del mes cerrado
- Solo primera semana del mes siguiente, solo con el mes ENTERO visible para el plan
  (`closedMonthIsFullyVisible`), y con las mismas negativas de delta y cobertura que los tiles.

---

## Reglas transversales que ningún cambio futuro puede saltarse

1. **Un dato nace con su evidencia o no nace**: toda magnitud medida nueva entra por
   `ScopedMeasure` (imposible sumarla sin cobertura) y todo pin persiste `detectionPath` +
   `armEvidence`.
2. **Los relojes son medidos**: inicio = parada del ancla; fin = salida medida; los relojes de
   procesado solo aparecen como cota explícita cuando la hora es genuinamente desconocida.
3. **Cero ≠ ausencia**: `0` es una medida (búsqueda directa, cero plazas cedidas); `null` es «no
   sabemos» y no llega a la pantalla como número.
4. **Las estimaciones se identifican** («aprox.», nota de precio con fecha) y sus constantes
   viven con nombre en un único sitio (`FuelDefaults`, `EmissionFactors`,
   `StaticAverageFuelPrices`).
5. **Una comparación necesita dos medidas del mismo tipo y cobertura completa**; ante un previo
   vacío no hay badge, jamás un múltiplo inventado.
6. **Retirar una fórmula invalida sus cifras**: si una medida se re-define y las viejas no pueden
   recomputarse, se ANULAN (como `MIGRATION_19_20`), no se dejan conviviendo con las nuevas.
