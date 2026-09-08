# PLUS-ROADMAP-001 · Paparcar Plus, segunda ola: límites free, combustible, estadísticas, gasolineras, parkings

**Estado:** 📋 Abierto (2026-09-07) · F0 (fusión documental) ✅ ejecutada el 07-09 · ninguna fase de
código empezada. Sucede a `MONEY-001` (✅ Done, mergeado 06-09).

> **Plan de EJECUCIÓN de la ola 2.** Cada fase es UN ticket con su worktree (skill `nuevo-ticket`),
> su rama y su `docs/backlog/<id>.md`. No se empieza una fase hasta que la anterior está mergeada y
> este doc actualizado. **Nunca commitear ni mergear sin permiso explícito del turno.** Detección
> CONGELADA hasta octubre: F7 y F8 esperan.
>
> El horizonte completo (qué viene DESPUÉS de esta ola: embudo de búsqueda, agregación por celda,
> SER, mapa de probabilidad…) vive en [`../ROADMAP-POST-PLUS.md`](../ROADMAP-POST-PLUS.md). Este doc
> manda en el *cómo* de la ola; aquel manda en el *qué después*.

---

## 0. Por qué existe este doc y qué manda

MONEY-001 dejó Plus funcionando (RevenueCat como única fuente de verdad, `ObserveIsPlusUseCase`,
`gateHistoryToEntitlement`, alertas de zona gateadas, paywall remoto, `PapPremiumSurface`). El
07-09 el user revisó el producto con estas **decisiones nuevas, que SUPERSEDEN a las de MONEY-001**
donde choquen:

| Tema | MONEY-001 decía | Ahora manda |
|---|---|---|
| Historial free | 30 días rolling | **7 días** rolling (la sesión activa nunca se oculta, igual) |
| Vehículos free | ilimitados | **1 coche propio**. Los que le COMPARTAN (F8) no cuentan. Añadir el 2.º propio → paywall |
| Zonas free | ilimitadas | **2 zonas** como máximo. Crear la 3.ª → paywall. Las ya creadas se conservan |
| Plus incluye | historial ilimitado · estadísticas · alertas de zona · sin anuncios | lo mismo **+** estadísticas avanzadas con gasto/consumo/CO₂ · rutas ilimitadas · coche compartido (estrella) · coches y zonas ilimitados |
| Free incluye | detección, mapa, historial 30 d | detección, mapa comunitario, historial 7 d, 1 coche, 2 zonas, combustible del coche, **cifra-teaser** de estadísticas, gasolineras cercanas, parkings públicos |

Reglas que NO cambian (siguen siendo doctrina): gating **soft** (la acción dispara el paywall, nunca
se oculta el botón); al perder Plus **nada se borra**, solo se estrecha la ventana o se pausa; el
invariante "¿es Plus?" vive en `EntitlementRepository` y todos preguntan vía `ObserveIsPlusUseCase`;
cada límite free vive en **UN** sitio de `domain/entitlement/` (patrón `HistoryEntitlementGate`).

### Decisiones del 07-09 al fusionar con el roadmap padre

Cuatro, tomadas por el user al fusionar este plan (venido de una sesión en Claude web) con
`ROADMAP-POST-PLUS.md`:

1. **El orden F1→F8 manda**, pero los dos items baratos del roadmap padre **se cuelan al arranque
   de la ola** como fases A1/A2: la instrumentación de analytics 1a (media jornada; cada semana sin
   ella son datos perdidos — es lo único que se acumula solo con el tiempo) y el Widget Android
   (1 día, cero backend, refuerza la promesa "encuentra tu coche").
2. **SER Madrid queda aparcada TRAS la ola 2** — decisión consciente, no un olvido. Su análisis
   completo (4a-4g) sigue intacto en el roadmap padre para cuando le toque.
3. **La dirección del modelo de datos del coche compartido se decide antes de F2** — ✅ decidida el
   08-09 en su sesión: **proyección** (espejo `sharedCars/`), ver F8 abajo. F8 se implementa en octubre.
4. **Dos docs, una tabla**: este fichero es la ejecución de la ola; el roadmap padre es el
   horizonte. La tabla de fases vive SOLO aquí; el padre la referencia como bloque.

### F0 · Fusionar, no duplicar — ✅ ejecutada (07-09)

1. ✅ Leído `docs/backlog/money-001.md` entero + el roadmap padre; comparado contra master
   (`466a10c9`). Resumen absorbido abajo (§"Hecho en MONEY-001").
2. ✅ Este fichero vive como `docs/backlog/plus-roadmap-001.md`. ⚠️ **Desviación respecto al plan
   original**: `money-001.md` **NO se mueve a `docs/archive/`** — la convención del backlog
   [DOCS-BACKLOG-TRUTH-002] es que los tickets cerrados se quedan en `docs/backlog/` (son la
   arqueología de los guards y se buscan con `grep -rl MONEY-001 docs/backlog/`). En su lugar,
   `money-001.md` lleva un puntero "Superseded en los límites free por PLUS-ROADMAP-001".
3. ✅ Auditado qué de este plan **ya existe** en master `466a10c9` (verificado el 07-09):
   `routeDistanceMeters`, `endedAtMs`, `publishedSpot` ya persisten;
   `VehicleHistoryCalculator.computeStats` + `HistoryStatsData` + `HistoryWeeklyChart` ya pintan
   día más activo, calle habitual, plazas cedidas y autodetección; `PlacesDataSource`/Overpass ya
   trae `PlaceCategory.FUEL` y `PARKING`; `data/geocoder` ya hace reverse geocoding; Room está en
   v2 con `MIGRATION_1_2` y baseline test; `HistoryEntitlementGate` con
   `FREE_HISTORY_WINDOW_DAYS = 30`. Lo que existe se REUSA, no se reescribe.
4. ✅ `docs/ROADMAP.md` y `ROADMAP-POST-PLUS.md` actualizados en la misma tarea (esta).
   `CLAUDE.md` §Modelos de datos se tocará cuando `Vehicle` cambie (F2).
5. ⏳ Pendiente del user (no de código): plantilla del paywall en el dashboard RC con la comparativa
   nueva (7 días, 1 coche, 2 zonas, estadísticas avanzadas, coche compartido "próximamente") +
   subir AAB a Pruebas internas + crear `paparcar_plus` en Play (arrastrado de MONEY-001).

### Hecho en MONEY-001 (resumen absorbido; detalle en `money-001.md`)

| Fase | Qué quedó en código |
|---|---|
| F0 build de desbloqueo | `purchases-kmp` 3.7.0 · `Purchases.configure` en `PaparcarApp.onCreate()` con guard de key en blanco · key por `BuildConfig.REVENUECAT_API_KEY` (cascada keystore→local.properties→env) |
| F1 dominio puro | `Entitlement`, `PlusPlan`, `EntitlementRepository`, `ObserveIsPlusUseCase`, `PurchasePlusUseCase` — cero imports RC en domain |
| F2 data + DI | `RevenueCatEntitlementRepositoryImpl` (delegate + fetch → `StateFlow`) · fake scenario-aware + `MockScenario.isPlus` |
| F3 identidad | `logIn(uid)`/`logOut()` enganchados a `SplashViewModel.bootstrap()` y `wipeLocalUserData()` · ventana anónima inerte por construcción |
| F4 gating | `HistoryEntitlementGate.gateHistoryToEntitlement` (30 d, hoy) + `HistoryTimeline.resolve` · `Zone.notifyOnSpot` end-to-end (Room v2) · campana de alertas · `ObserveZoneSpotAlertsUseCase` en cliente · `Routes.PAYWALL` MVI |
| F5 paywall remoto | `PaywallContent` con plantilla remota del offering `default` · `storeAvailable` con marco honesto sin tienda |
| F6 calidad | Grupo Plus en la galería (7 variantes) · 10 tests de downgrade a nivel VM · Konsist + paridad ×9 |
| Revisión device | Campana a la fila de chips · Plus bajo el perfil · fix `PapOutlinedCard` sin `Column` apilaba filas · `RevenueCatDataSource` · verde premium v3 (a L*≈10-18% la hue no se percibe: superficie verde marca + contenido `onPrimary`) |

---

## 1. Fases · cada una = ticket + worktree

Orden fijado por el user (07-09) tras valorar éxito/riesgo/trabajo. **No se reordena sin preguntar.**

| # | Ticket | Free/Plus | Toca detección | Estimación |
|---|---|---|---|---|
| A1 | `ANALYTICS-EVENTS-001` | transversal | no (emite desde el punto que ya estampa `detectionPath`) | ½ día |
| A2 | `WIDGET-PARKED-001` | free | no | 1 día |
| F1 | `FREE-LIMITS-001` | gating | no | 1 día |
| F2 | `VEH-FUEL-001` | free (dato) | no | 2 días |
| F3 | `STATS-CORE-001` | dominio | no | 2-3 días |
| F4 | `STATS-UI-001` | free cifra / Plus panel | no | 4-5 días |
| F5 | `FUEL-PRICES-001` | free | no | 3-4 días |
| F6 | `PARKINGS-LIVE-001` | free | no | 3-4 días (+ Cloud Function) |
| F7 | `DET-SEARCH-TIME-001` | Plus (agitación) | **sí** — octubre, skill `det-change` | 2-3 días |
| F8 | `VEH-SHARED-001` | Plus (estrella) | **sí** — octubre, solo BT | 8-12 días |
| — | SER Madrid (aparcada TRAS la ola, decisión 07-09) · `ROUTES-PLUS-001` · `PARKING-AFFILIATE-001` | después | — | ver roadmap padre |

Cada fase, sin excepción: strings en los **9 locales** en la misma tarea (`LocaleParityGuardrailTest`);
Dev Catalog al día (pantalla → `ScreenGroup`; estado → variante; condición de routing →
`MockScenario`); tests de use case con fakes; Konsist en verde con `--rerun-tasks`;
`assembleProdDebug` + `assembleMockDebug` + klib iOS compilan; un commit por fase; **parar y resumir
al cerrar cada una**.

---

### A1 · ANALYTICS-EVENTS-001 — los eventos que ya son hecho (del roadmap padre, feature 1a)

**Estado: ✅ EN MASTER** (08-09) — ficha completa en
[`analytics-events-001.md`](analytics-events-001.md).

**Objetivo.** Emitir `parking_session_started`, `parking_session_ended` (con `end_reason`),
`spot_released`, `spot_reported` con celda geográfica (nunca coordenada). Es lo único de toda la
ola que se acumula solo con el tiempo.

**Lo que quedó (deltas sobre lo previsto):** GitLive publica `firebase-analytics` en 2.6.0 → API
común, **sin expect/actual** (esa suposición era pre-verificación). Puerto
`domain/analytics/AnalyticsTracker` + `FirebaseAnalyticsTracker` en data — el ÚNICO traductor
GpsPoint→celda (**geohash p7**, lo que decide de facto geohash para la agregación futura, anotado
en el padre §5.3). Emisión desde los choke points reales: `ConfirmParkingUseCase` (alta), los 4
cerradores (cada uno su `end_reason`, incluida la retractación), y `ReportSpotReleasedUseCase`
(released/reported por `SpotType`; la promoción de una salida deducida pasa `promotion = true` y no
cuenta doble). ⛔ Los 3 permisos de advertising-id que arrastra `play-services-measurement` se
eliminan del manifest (`tools:node="remove"`, verificado en el fusionado): sin anuncios no se
recoge el ad id. Data Safety + política de privacidad (EN/ES) actualizadas en la misma tarea.
⏳ User: activar el export a BigQuery en la consola de Firebase (sin código) y **redesplegar el
hosting** (`firebase deploy --only hosting` por CLI) para publicar la política actualizada.

### A2 · WIDGET-PARKED-001 — el widget del aparcamiento activo (del roadmap padre, P2)

**Objetivo.** Widget Android con dirección + tiempo transcurrido + tap que abre el mapa. Cero
backend. Refuerza la promesa que se vende ("encuentra tu coche"). Glance o RemoteViews a decidir en
su ticket; lee la sesión activa de Room (misma consulta que Home), nunca dispara detección.

---

### F1 · FREE-LIMITS-001 — tres límites, tres funciones puras, un paywall

**Objetivo.** Free = historial 7 días · 1 coche propio · 2 zonas. Todo lo demás igual.

**Diseño.**
- `HistoryEntitlementGate.kt`: `FREE_HISTORY_WINDOW_DAYS = 30 → 7`. Es el único cambio ahí: el pie
  "Tienes N aparcamientos anteriores · Ver todo con Plus" y el stepper del detalle ya lo consumen.
  Revisar los sitios que citan la constante (grep) y los tests que la asumen.
- Nuevo `domain/entitlement/VehicleEntitlementGate.kt`: `canAddOwnVehicle(ownedCount, isPlus)`.
  `FREE_OWN_VEHICLE_LIMIT = 1`. "Propio" = `userId == uid` (F8 añade `ownerId`/miembros; hoy todos
  son propios). Se gatea el **add** (botón "añadir coche" en Vehículos y el pill de
  `veh-add-pill-single-vehicle`): free con 1 → paywall. Los coches ya existentes se conservan y
  siguen detectando (regla de downgrade: gatear lo NUEVO, no lo creado). NO se toca `isActive`.
- Nuevo `domain/entitlement/ZoneEntitlementGate.kt`: `canCreateZone(zoneCount, isPlus)`.
  `FREE_ZONE_LIMIT = 2`. Se gatea el guardado de una zona NUEVA (`SaveOrUpdateZone`: si es
  creación y `count >= 2 && !isPlus` → `Effect.OpenPaywall`, la zona no se guarda). Editar/borrar
  siempre libre. Zonas existentes por encima del límite se conservan.
- Paywall: `Routes.PAYWALL` recibe un `reason` (`HISTORY`, `VEHICLES`, `ZONES`, `STATS`,
  `ZONE_ALERTS`) solo para analytics y para el subtítulo del marco sin tienda; la comparativa
  sigue viviendo en la plantilla remota.
- Copy (9 locales): `vehicles_limit_free_hint` ("Con Plus puedes añadir más coches"),
  `zones_limit_free_hint`. Sin jerga, causa + consecuencia + remedio.
- Tests: 3 gates × (free bajo, free en el límite, plus) + VM: Vehicles add → paywall, Zone create
  3.ª → paywall sin persistir, downgrade en vivo no borra nada.
- Docs: `ROADMAP.md`, ficha de Play (`docs/release/play-listing`) si menciona "historial 30 días".

---

### F2 · VEH-FUEL-001 — el coche sabe qué quema

**Objetivo.** `Vehicle` gana `fuelType` (obligatorio en alta), `consumptionPer100`
(editable, con default por tipo) y `energyLabel` (opcional). Sin validar contra marca/modelo:
no hay catálogo fiable y el Clio E-Tech demuestra que la validación miente.

**Diseño.**
- `domain/model/FuelType.kt` (enum, Kotlin puro): `GASOLINE`, `DIESEL`, `LPG_CNG`, `HYBRID`,
  `PLUG_IN_HYBRID`, `ELECTRIC`. Solo aplica a `VehicleType.CAR` y `MOTORCYCLE`; scooter/bici →
  `null` y sin selector (`veh-a-new-vehicle-type-must-not-be-a-car-by-omission-001`).
- `domain/vehicle/FuelDefaults.kt`: consumo por defecto **por tipo de combustible × VehicleSize**
  (gasolina 6,5 l/100 · diésel 5,5 · GLP 8,5 · híbrido 5,0 · PHEV 3,0 l + 12 kWh · EV 17 kWh;
  moto 3,5). Constantes con nombre, nunca inline. `consumptionPer100: Float?` = override del
  user; null ⇒ default. Unidad implícita por tipo (l o kWh); PHEV lleva las dos
  (`consumptionElectricPer100`).
- `domain/model/EnergyLabel.kt` opcional: `ZERO`, `ECO`, `C`, `B`, `NONE` (nombres DGT en ES; en
  otros países se muestra como "etiqueta ambiental" genérica). Se usa cuando llegue SER; hoy solo
  se guarda.
- Persistencia: Room **v3** (`MIGRATION_2_3` + testigo en `AppDatabaseV1BaselineTest`),
  entity + DTO Firestore + mappers + deserializador manual (paridad `androidUnitTest`). Sync LWW
  intacto. `fuelType` se sincroniza (no es PII); `licensePlate` sigue on-device.
  ⚠️ **Antes de esta migración tiene que estar decidida la dirección del modelo de F8** (ver F8):
  no migrar dos veces el mismo esquema en un mes.
- UI: `VehicleRegistration` — paso nuevo o fila en el mismo paso, selector con `PapListItem`
  dentro de `PapOutlinedCard` (nivel 2 iconos: `Icons.Rounded.LocalGasStation`, `Bolt`, `Eco`…
  mapeo en presentación). Edición en Vehículos. Consumo: campo numérico con el default como
  placeholder, `PapTextField`, unidad según tipo. Ayuda inline de una línea: "Estimamos gasto y
  emisiones con este dato".
- Dev Catalog: `MockScenario` con vehículos de cada `FuelType`; variantes del formulario.
- Docs: `CLAUDE.md` §Modelos de datos (`Vehicle`), `docs/architecture/VEHICLE-CATEGORIZATION.md`
  (nueva sección "Combustible no es categoría": no afecta a detección ni a `SpotFit`).
- ⛔ No leer `fuelType` desde ningún evaluador de detección.

---

### F3 · STATS-CORE-001 — las cifras, en dominio, sin pantalla

**Objetivo.** Un solo agregador puro de estadísticas por periodo y por vehículo (o todos), en
`commonMain`, testeable, que F4 pinta. Reusa `VehicleHistoryCalculator`; no crea un segundo.

**Diseño.**
- `domain/stats/PeriodStats.kt` (value object): `parkings`, `distanceMeters`, `fuelLiters?`,
  `energyKwh?`, `costEstimate: Money?`, `co2Kg?`, `avgParkedDurationMs?`, `peakHourBucket?`,
  `mostActiveDayOfWeek?`, `favoriteStreet?`, `spotsReleased`, `autoDetected`, `searchTimeMs?`
  (null hasta F7), `previous: PeriodStats?` (comparativa). Cada campo `null` bajo su umbral
  de significancia — patrón `MIN_SESSIONS_FOR_PEAK`; nunca "0"/"—" como cifra.
- `domain/usecase/stats/ComputePeriodStatsUseCase` (puro, síncrono): entrada = sesiones ya
  gateadas (**siempre pasan por `gateHistoryToEntitlement`** — las stats de un free se calculan
  sobre lo que puede ver; lo contrario sería un paywall con agujero), vehículos (para consumo),
  precio (`FuelPrice?` — F5; hasta entonces `null` ⇒ `costEstimate = null`), periodo
  (`HistoryFilter` existente: semana/mes/3 meses/todo + "año" si hace falta).
- `domain/stats/EmissionFactors.kt`: kg CO₂ por litro/kWh por `FuelType` (gasolina 2,31 ·
  diésel 2,68 · GLP 1,66 · electricidad: factor de mix por país con default UE, constante nombrada).
- `Money(amount: Double, currency: String)` en domain; formateo en presentación con el locale.
- Ventana de periodo: **reusar** `VehicleHistoryCalculator.windowStartMs` (es "el ÚNICO sitio
  donde se decide una ventana", `UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001`).
- `HistoryStatsData` (presentación) pasa a derivarse de `PeriodStats` — no dos cálculos.
- Tests: umbrales, PHEV (l + kWh), EV (kWh, sin litros), sesión activa excluida de duración,
  comparativa con periodo anterior vacío, free vs Plus con el mismo dataset da cifras distintas.

---

### F4 · STATS-UI-001 — la cifra que el user no sabía, y el panel que la explica

**Objetivo.** (1) **Tarjeta teaser** en free con UNA cifra real y CTA a Plus; (2) **panel
Estadísticas** completo para Plus; (3) las cifras que ya existen en Vehículos se integran, no se
duplican.

**Dónde va cada cosa — Claude Code razona y propone antes de codificar** (el user lo pidió así):
- Regla editorial de navegación (`CLAUDE.md` §Navegación): *si pasa AHORA → Home; si pasó o es
  mío-permanente → Vehículos*. Las estadísticas son "mío-permanente" → **el panel vive en
  Vehículos**, como destino desde la página del vehículo (y desde el bloque de stats existente).
- **Teaser en Home**: bajo la(s) tarjeta(s) de vehículo del sheet. Es una **cifra global de todos
  los vehículos** (Home es del usuario, no de un coche; y en free hay un solo coche, así que la
  distinción no se nota). Rotación semanal del mensaje; solo con ≥ 3 aparcamientos en el periodo;
  nunca en la primera sesión; marcada como "aprox.".
- **Teaser en Vehículos**: en cada página del pager, junto al bloque de stats de ese vehículo,
  una fila "bloqueada" con la MISMA cifra pero de ese coche ("Este mes: ~9 € en gasolina · Ver
  desglose con Plus"). Misma función de gating, mismo paywall (`reason = STATS`).
- Propuesta a validar con el user en el resumen de la fase: si las dos superficies se sienten
  redundantes en device, la de Home gana (es la que ve todo el mundo) y en Vehículos queda solo
  el enlace al panel.

**Diseño.**
- `presentation/stats/` MVI completo (`StatsScreen`, `StatsState`, `StatsIntent`, `StatsEffect`,
  `StatsViewModel`) + `Routes.STATS`. Selector de periodo (chips existentes de `HistoryFilterBar`)
  y de vehículo (pager o dropdown; si 1 coche, sin selector).
- Componentes (`ui/components/`): `PapStatTile` (cifra `statNumber` + `statLabel`, icono nivel 2
  centrado en dígitos — `ui-stat-icon-centers-on-digits-001`), `PapStatBarChart` (reusa la base
  de `HistoryWeeklyChart`; roles `chartLabel`/`chartValue`), `PapComparisonDelta` (▲/▼ vs periodo
  anterior en `onSurface`, nunca teñido), `PapStatsTeaserCard` (**vive sobre `PapPremiumSurface`**:
  es un touchpoint Plus y esa es la única superficie que dice Plus).
- Voces: cifra protagonista → CIFRA; todo lo demás LECTURA; título del panel MARCA. Ningún
  `fontSize`/`fontWeight` inline (`TypographyGuardrailTest`).
- Gating: free entra al panel y ve **la cifra del teaser en grande + el resto de tiles con
  velo** (soft: se ven los títulos, no los valores) + CTA. Plus ve todo. `hiddenOlderCount` sigue
  contando debajo del panel como en el historial.
- Empty/loading/error con dirección ("Aparca tres veces esta semana y aquí verás…").
- Dev Catalog: `ScreenGroup` Estadísticas (free teaser, Plus completo, vacío, EV, PHEV) + Home con
  teaser + Vehículos con fila bloqueada.
- Tests VM: teaser no aparece < 3 sesiones; rota por semana; free tap → paywall; Plus tap → panel.
- Docs: `ROADMAP.md`; `CLAUDE.md` §Navegación (`Routes.STATS`); onboarding/ayuda in-app si
  describen Plus; ficha de Play (capturas nuevas).

---

### F5 · FUEL-PRICES-001 — precio real donde exista, media donde no, gasolineras cercanas

**Preguntas del user, respondidas en el diseño:**
- *¿La API del Ministerio vale para más países?* No: es solo España. Por eso el precio es un
  **puerto con adaptadores por país**, y el gasto estimado funciona **en todos los países** por
  tres escalones: (1) adaptador nacional con precio por gasolinera; (2) **media nacional** de una
  tabla estática versionada en la app (o de un endpoint gratuito de medias, p. ej. EIA semanal en
  EE. UU.); (3) **precio puesto por el usuario** en Ajustes del vehículo, que siempre gana.
  Ninguno de los tres exige red en el momento de calcular.
- *¿Consumo en el resto de países?* Sí: el consumo (l o kWh/100) es del vehículo, no del país.
  Solo cambia de dónde sale el €/l. La UI muestra la fuente ("precio medio en Francia", "tu
  precio", "gasolinera más cercana").

**Diseño.**
- `domain/fuel/FuelPriceSource` (puerto): `suspend fun pricesNear(lat, lon, radiusM, fuel:
  FuelType): Result<List<FuelStationPrice>>` + `suspend fun averagePrice(countryCode, fuel):
  Result<FuelPrice?>`. `FuelStationPrice(id, name, brand?, lat, lon, prices: Map<FuelType,
  Money>, updatedAt)`.
- `domain/fuel/FuelPriceResolver` (puro): dado `countryCode` (del reverse geocoder existente en
  `data/geocoder`) elige adaptador → media estática → override user. Un solo sitio decide.
- Adaptadores en `data/fuel/`: `MineturFuelPriceSource` (ES; REST JSON, sin auth, ~10 k
  estaciones; **se descarga por provincia, no el país entero**, y se cachea en Room `fuel_station`
  con TTL de 6 h). Estructura preparada para `TankerkoenigFuelPriceSource` (DE, key gratuita),
  `PrixCarburantsFuelPriceSource` (FR, open data), `DgegFuelPriceSource` (PT), `MimitFuelPriceSource`
  (IT), `EControlFuelPriceSource` (AT). Solo ES se implementa en esta fase; el resto NO son
  ficheros vacíos, son **entradas en un registro** (`FuelSourceRegistry: Map<countryCode, factory>`)
  que se rellenan en fases posteriores. `StaticAverageFuelPrices` (tabla `countryCode × FuelType`
  con fecha de vigencia; se actualiza con cada release, documentado).
- Mapeo `FuelType → producto de cada API` vive en el adaptador (MINETUR: "Gasolina 95 E5",
  "Gasóleo A", "GLP"…; EV → sin precio de estación, solo media/override).
- **Sección Gasolineras cercanas en Home**: en el sheet, debajo de las plazas (`HomeSpotRows`),
  una sección propia con `PapSectionHeader` + filas `PapListItem` (nombre, distancia, **solo el
  precio del combustible del coche activo**, hora de actualización). Colapsable; recuerda estado.
  Marcadores en el mapa como capa activable (toggle junto a `MapTypeToggle`; apagada por
  defecto; pref en `AppPreferences`). Marcador nivel 3 propio (surtidor, multicolor, variante
  `_dark`), NUNCA tintado; con el precio en `TextMeasurer` (allowlist ya existente de canvas).
  Tap → peek con precios de todos los combustibles y "cómo llegar" (deep link a la app de mapas).
- **Base mundial sin precio**: donde no hay adaptador, la sección muestra gasolineras de
  Overpass (`PlaceCategory.FUEL` ya existe) sin precio y con la media nacional en cabecera. El
  usuario no ve "sin datos": ve gasolineras.
- El precio resuelto alimenta `ComputePeriodStatsUseCase` (F3) → gasto en el panel y en el teaser.
- Privacidad/legal: `docs/legal` + data safety si se guarda país del usuario (no se guarda: se
  resuelve al vuelo). Atribución de la fuente en Ajustes › Licencias (MINETUR, OSM).
- Tests: resolver (adaptador > media > override en los 3 órdenes), parser MINETUR con fixture
  real, cache TTL, EV sin estación, país sin adaptador → media.
- Dev Catalog: escenario con ES/DE/US para ver los tres escalones.

---

### F6 · PARKINGS-LIVE-001 — parkings públicos: base mundial + ocupación por adaptadores

**Cómo lo hacen los que muestran parkings de todo el mundo**: tres capas, y ninguna API única
gratuita. Paparcar replica el modelo:
1. **Base estática mundial** (gratis): Overpass `amenity=parking` (ya hay parser) para ubicación,
   nombre, altura, horario cuando OSM lo sabe.
2. **Ocupación en tiempo real donde hay dato abierto**: un **adaptador por fuente** en backend
   (Cloud Function programada), nunca en la app. Verificado el 07-09: Madrid (servicio municipal:
   listado, detalle con ocupación, búsqueda por vía/disponibilidad/proximidad), Málaga (CSV
   cada minuto + catálogo de ids), Zaragoza (ocupación de la empresa municipal). Valencia,
   Bilbao, Barcelona, Sevilla: ubicación sí, ocupación en abierto NO verificada (B:SM/Aussa).
   Europa: **ParkAPI / `parkapi-sources`** (open source, conversores de decenas de ciudades DE/CH/
   AT/DK a un modelo DATEX II Light; se puede desplegar o contribuir los conversores de Madrid y
   Málaga). NL: datos nacionales RDW. EE. UU.: pocas ciudades abiertas; el dato bueno está en
   agregadores de pago (TomTom Parking Availability, refresco 10 min, acceso por ventas; HERE
   Off-Street; Parkopedia; INRIX ~3-15 k$/año) — **fuera de alcance** hasta tener usuarios allí.
3. **Marketplaces con API y comisión**: Parclick (directo), Parkimeter (Awin), Onepark en Europa;
   **SpotHero y ParkWhiz/Arrive API v4** en EE. UU. (búsqueda + reserva, alta como partner).
   → `PARKING-AFFILIATE-001`, después.

**Avisos heredados del roadmap padre (§3.1, §3.4, §3.5 — siguen aplicando):**
- Es el **primer backend propio** del proyecto: `firebase.json` declara hoy `firestore` + `hosting`
  y CERO functions. Esta fase estrena runtime, deploy, CI y secretos — coste real que se amortiza
  (lo reutilizan otras ciudades, el webhook de F8 y `ZONE-SUBSCRIBE-001`).
  ⚠️ El MCP `firebase_deploy` es un **no-op silencioso**: desplegar SIEMPRE por CLI.
- El campo `source` es **procedencia, no confianza**: no crear ningún enum con `CONFIRMED` —
  colisiona con `Spot.confidence` y `SpotStatus.CONFIRMED` (dos sentidos ya ocupados). Si hace
  falta el tipo, se llama `AvailabilitySource` y el valor oficial es `OFFICIAL`.
- El mapa arranca en `MapType.TERRAIN` y el estilo de marca solo rinde sobre `NORMAL`
  (`MAP-TYPES-001` sigue bloqueado): antes de diseñar el render de la capa, decidir si esto la
  condiciona o se asume el estilo actual.

**Diseño (esta fase = capas 1 y 2 con Madrid + Málaga).**
- Backend `functions/parkings/`: `scheduler` cada 3 min (solo 07-23 h local de cada fuente) →
  `adapters/madrid.ts`, `adapters/malaga.ts` → esquema único `PublicParking { id, source,
  name, lat, lon, geohash, capacity?, free?, updatedAt, tariffSummary?, maxHeightM?, evCharging?,
  openingHours?, bookingUrl? }` → Firestore `publicParkings/{source}_{id}` (merge). Reglas:
  lectura pública, escritura solo función. Añadir ciudad = añadir adaptador; la app no cambia.
- App: `domain/parkings/PublicParkingRepository` (Room cache + Firestore por bounding box
  geohash, patrón de `data/geohash`), `ObserveNearbyPublicParkingsUseCase`. Estático de Overpass
  se fusiona por proximidad (< 40 m) para no duplicar el mismo parking.
- UI: marcador nivel 3 "P" con **rampa de ocupación PROPIA** — ⛔ NO reutilizar la rampa de
  frescura de plazas (`PapSpotFresh…` es EXCLUSIVA de la caducidad de plazas,
  `UI-COLOR-DOCTRINE-001`): usar `onSurface` + cifra de libres en el glifo, gris si solo estático,
  y "hace X min" en el peek. Sección "Parkings cercanos" en el sheet bajo Gasolineras (o pestañas
  Plazas · Gasolineras · Parkings si el sheet se alarga — Claude propone tras medir en device).
  Capa activable como la de gasolineras. Nunca en la superficie de detección ni en aparcar/liberar.
- Vocabulario: F6 añade su fila a la tabla de `COPY-SPOT-IS-NOT-A-PARKING-001` ("parking público"
  vs "aparcamiento" vs "plaza") en `CLAUDE.md`, ×9 locales.
- Free. Es lo que evita el mapa vacío el primer día.
- Tests: adaptadores con fixtures reales (JSON Madrid, CSV Málaga), fusión con Overpass, staleness.
- Docs: `docs/architecture/PUBLIC-PARKINGS.md` (fuentes, esquema, cómo añadir una ciudad),
  `firestore.rules`, `firestore.indexes.json`, licencias/atribución (Ayto. Madrid, Ayto. Málaga, OSM).

---

### F7 · DET-SEARCH-TIME-001 — tiempo buscando plaza (octubre, skill `det-change`)

**Objetivo.** Persistir `searchDurationMs: Long?` en `UserParking` = tiempo entre la primera entrada
del viaje en el radio de destino (~300 m del pin final) y la confirmación del aparcamiento. Es la
estadística única de Paparcar ("este mes has perdido 2 h 40 min buscando plaza").

**Diseño (a detallar en su ticket, con la doctrina de detección delante):**
- Cálculo **a posteriori y puro** en `domain/detection/SearchTimeEstimator.kt` sobre la traza GPS
  con timestamps que el Coordinator ya tiene en la sesión (la `routePolyline` NO sirve: no lleva
  tiempos). Se estampa en `ConfirmParkingUseCase` (choke point) — ningún evaluador lo LEE: no
  cambia ninguna decisión de detección. Redondeado al minuto, umbral mínimo 2 min, cap 60 min.
- Con BT-strategy (sin stream de conducción) puede ser `null` → el panel no lo muestra.
- Room v4, DTO, LWW. Tests con las 16 trazas de campo del replay.
- ⚠️ **No sustituye al embudo de búsqueda** (feature 1b del roadmap padre): F7 mide *cuánto
  tardaste tú*; el embudo (`search_started`/`spot_found`, modo "voy de camino") mide *si la
  comunidad funciona* — y sigue siendo la métrica del piloto, pendiente tras la ola.

---

### F8 · VEH-SHARED-001 — coche compartido, solo Bluetooth (octubre, feature estrella de Plus)

**Reglas acordadas (06/07-09):** solo con BT emparejado; sin geocerca compartida; el propietario
con Plus cubre a hasta **4 miembros** que no necesitan Plus; los compartidos no cuentan para el
límite free del miembro (F1); rutas personales NO se comparten; historial y stats del coche sí.

**✅ Modelo DECIDIDO — sesión de diseño del 08-09 (3 decisiones del user):**

**(B) PROYECCIÓN: la sesión personal sigue siendo la fuente de verdad; el coche compartido es un
espejo.** Se descartó (a) "las sesiones cuelgan del coche" tras medir el coste real en el código:
lo caro no era migrar datos (base vacía) sino re-cablear el eje usuario→sesión entero — Room/DAOs
keyados por `(id, userId)`, los 6 caminos del outbox, el reconcile LWW multi-autor, `deleteUserData`,
reglas, historial/stats e iOS — y aun así (a) tenía que partir el doc de sesión igualmente, porque
la sesión bajo el coche expone `routePolyline` a los miembros y *las rutas personales NO se
comparten*. (B) deja intacto el 100% del sistema para coches no compartidos y la privacidad de la
ruta sale gratis por construcción.

Esquema decidido (detalle fino en su ticket):
```
users/{uid}/vehicles/{id}        ← igual que hoy + sharedCarId? + ownerUid? (fila vinculada
                                   del miembro: misma MAC ⇒ la detección BT no se toca)
users/{uid}/parkingHistory/{id}  ← igual que hoy: fuente de verdad, CON ruta, outbox intacto
sharedCars/{carId}               ← nace SOLO al activar compartir: ownerId, memberIds[]/accessUids[],
                                   datos del coche, btMacHash, sharingEnabled (webhook RC),
                                   state{location, parkedAtMs, authorUid, authorName, address}
  └─ sessions/{mismoId}          ← ESPEJO: núcleo compartido (autor, ubicación, timestamps,
                                   isActive, address) — SIN ruta, SIN armEvidence/detectionPath
```
- Quien aparca escribe su sesión personal como hoy y, si el vehículo tiene `sharedCarId`, el mismo
  choke point encola un **segundo job de outbox** (patrón `ParkingSyncScheduler`) que empuja el
  espejo + `state`. Deriva del espejo → job de reparación contra la fuente.
- **Todo el grupo — dueño INCLUIDO — lee la posición del coche de `sharedCars.state`**: si aparcó
  otro miembro, la sesión personal del dueño no sabe nada (su móvil no aparcó). `sharedCarId` en la
  fila de `vehicles` marca el coche como compartido; el espejo es el punto de encuentro. El
  historial personal (con rutas) sigue siendo de cada uno. El historial DEL COCHE = la subcolección
  espejo, gateada en cliente por el entitlement de quien mira.
- **Ruta compartida = opt-in del autor, default OFF** (08-09): el espejo va sin ruta; si el usuario
  activa "compartir mis rutas en este coche" (pref por coche, con posible acción por viaje), el
  MISMO job del espejo incluye `routePolyline`. La política de qué viaja vive en UN sitio: el write
  del espejo.
- Dedupe 60 s + FCM + webhook RC = Cloud Functions sobre `sharedCars` (el runtime lo estrena F6).
- **Historial compartido DESDE LA ACTIVACIÓN**: el espejo nace vacío; nadie ve dónde estuvo el
  coche antes de existir el grupo. Sin backfill (decisión 08-09, privacidad por defecto).
- **Migraciones**: F2 migra solo combustible (v3) y F7 lo suyo (v4); **F8 hace su propio bump en
  octubre** con `sharedCarId`/`ownerUid` — sin columnas muertas antes (decisión 08-09). Con (B),
  nada de lo que construyan F2/F3/F7 se re-keya después.
- ⚠️ Hallazgo de la sesión, a corregir en F2 (que ya toca `Vehicle.kt`): el KDoc de
  `Vehicle.bluetoothDeviceId` dice *"On-device only, never synced"* y **miente** — el DTO, el
  mapper y `RemoteUserProfileDataSourceImpl` lo suben a Firestore desde siempre. Para F8 además
  importa: en `sharedCars` viaja `btMacHash`, no la MAC cruda.

**Diseño (a detallar en su ticket):**
- Invitación por enlace dinámico o código de 6 caracteres (caduca, un solo uso); aceptar = el móvil
  del miembro debe emparejar la MISMA MAC. Siempre con entrada manual del código (los deep links
  fallan). Lenguaje: *"unirme"*, nunca *"compartir"*.
- Quién conduce = el móvil cuyo BT estaba conectado; su desconexión dispara la detección y
  escribe SU `UserParking` personal + el espejo del coche; dedupe de dos móviles en ventana de
  60 s por confianza.
  Geocerca y Coordinator degradados a **sugerencia** en coche compartido (guard `[SHARE-DEDUP-*]`).
- Push a los demás: "Marta ha aparcado el coche en C/ Ponzano, 14" (canal nuevo; strings ×9).
- Si nadie del grupo detecta el movimiento → la última posición se mantiene con "hace 3 h" +
  botón manual (doctrina: mejor falso negativo que falso positivo).
- Downgrade del propietario: el coche sigue visible para los miembros pero las notificaciones se
  pausan (patrón alertas de zona); solo se bloquea añadir miembros nuevos ("conservar lo creado,
  gatear lo nuevo"). Reglas Firestore para lectura por miembros.
- Webhook RC → `plusActive`/`sharingEnabled` en Firestore = **proyección de solo lectura para las
  reglas de seguridad, no réplica** de la fuente de verdad de MONEY-001 — exige su ADR (roadmap
  padre §3.7).
- Privacidad: compartir el coche es compartir dónde estuvo quien lo condujo → pantalla de
  aceptación explícita, salir en un toque, el dueño revoca, fila en Seguridad de los datos de Play.

---

## 2. Documentación y superficies que se actualizan con cada fase

No es un paso final: es parte de la definición de hecho de cada fase.
- `docs/ROADMAP.md` (tabla + cabecera fecha/commit) · este doc (estado de la fase, ficheros clave) ·
  `ROADMAP-POST-PLUS.md` si el horizonte cambia.
- `CLAUDE.md`: §Modelos de datos (F2, F7, F8), §Navegación si aparece `Routes.STATS` (F4),
  vocabulario si nace un término (F6 añade su fila a `COPY-SPOT-IS-NOT-A-PARKING-001`).
- Onboarding y textos de ayuda in-app que describan qué hace la app o qué incluye Plus (F1, F4, F5,
  F6): lo que la app hace nuevo tiene que poder leerse dentro de la app.
- `docs/release/play-listing` (9 idiomas): descripción, "novedades", capturas (F4, F5, F6) y
  **Seguridad de los datos** si cambia lo recogido (A1 añade analytics con celda; F5 no guarda
  país; F8 comparte ubicación del coche con miembros: sí cambia).
- `docs/legal` + web (`hosting/`): política de privacidad si F8 comparte ubicación entre usuarios;
  atribuciones de fuentes (MINETUR, Ayto. Madrid, Ayto. Málaga, OSM) en Licencias.
- `README.md` si aparecen claves nuevas (Tankerkönig en el futuro) por la cascada
  `keystore.properties → local.properties → env`.
- `firestore.rules` / `firestore.indexes.json` (F6, F8).

## 3. Lo que NO se hace en esta ola
- No tocar detección antes de octubre (F7/F8 esperan). No leer `fuelType` ni parkings desde ningún
  evaluador.
- No SER Madrid (aparcada TRAS la ola, decisión 07-09 — análisis en el roadmap padre §5.4); no
  integrar apps de pago; no vender ni reservar plazas públicas (lo comunitario es información
  gratuita).
- No comprar datos de parking (TomTom/HERE/INRIX/Parkopedia) hasta tener usuarios fuera de España.
- No crear un segundo cálculo de estadísticas: `ComputePeriodStatsUseCase` es el único.
- No usar la rampa de frescura de plazas para ocupación de parkings ni para nada que no sea la
  caducidad de una plaza.
- No el embudo de búsqueda / modo "voy de camino" (tras la ola; es la métrica del piloto y el
  roadmap padre lo tiene en su orden).
