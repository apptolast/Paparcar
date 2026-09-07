# MONEY-001 · Suscripción "Paparcar Plus" con RevenueCat

**Estado:** ✅ Done — mergeado a master (2026-09-06). Las 7 fases + la revisión visual en device.
> ⚠️ **Superseded en los LÍMITES free por [`plus-roadmap-001.md`](plus-roadmap-001.md)** (07-09):
> historial 30 d → 7 d · vehículos ilimitados → 1 propio · zonas ilimitadas → 2. El resto de
> decisiones (gating soft, RC única fuente de verdad, nada se borra) siguen vigentes.

## Problema
La app está publicada (versionCode 10) sin monetización. Hay que implementar la suscripción
"Paparcar Plus" para versionCode 11, con deadline duro el 30-09-2026 (hackathon Shipaton).
Google Play no permite crear los productos de suscripción hasta detectar la Play Billing Library
en un artefacto subido → la FASE 0 (build de desbloqueo) es la prioridad máxima y bloquea todo
lo demás.

## Doctrina violada
Ninguna — es feature nueva. Restricciones que SÍ aplican:
- ⛔ Detección CONGELADA hasta octubre: no tocar detección, geofences, Bluetooth ni
  CoordinatorParkingDetector.
- RevenueCat es la ÚNICA fuente de verdad del estado de suscripción — prohibido replicar
  expiraciones en Firestore o backend propio.
- Domain layer Kotlin puro: cero imports de `com.revenuecat` en domain/.

## Decisiones cerradas (producto)
- Entitlement RC: `plus` · Offering: `default` · Packages: `$rc_monthly`, `$rc_annual` ·
  Productos RC: `monthly`, `yearly` (hoy en Test Store de RC, intencionado).
- SKU Play: `paparcar_plus` · base plans `monthly`/`yearly` · 1,99 €/mes · 13,99 €/año ·
  trial 7 días en ambos · NO hay Lifetime.
- Gating soft (el usuario intenta la acción → paywall), nunca ocultar botones:
  - Historial free → 30 días + pie "Tienes N aparcamientos anteriores · Ver todo con Plus".
    Los datos NUNCA se borran, solo se filtran.
  - Alertas de zona free → botón dispara paywall. Al perder Plus las alertas se PAUSAN, no
    se eliminan.
  - Ajustes: entrada Plus arriba, visible sin ser insistente.
  - Vehículos y zonas ILIMITADOS — sin límites nuevos.
- Paywall: composable de `purchases-kmp-ui` con plantilla remota, NO diseño propio.
- API key por BuildConfig desde `local.properties` (debug → key `test_...`, release → key
  `goog_...` aún inexistente, dejar preparada y documentada). Nunca hardcodeada ni commiteada.
- Fuera de alcance: Cloud Function promo Fundadores, AdMob, paywall con identidad visual propia.

## Fases y estado
- ✅ **FASE 0** — build de desbloqueo (2026-09-05). `purchases-kmp` 3.7.0 en el catalog
  (core cableado en commonMain vía `api`; ui declarado, se cablea en FASE 5).
  `Purchases.configure(PurchasesConfiguration(key))` en `PaparcarApp.onCreate()` ANTES de
  `initLoginKoin`, con guard: key en blanco ⇒ no se configura el SDK (log de aviso), nunca
  crashea. Key por `BuildConfig.REVENUECAT_API_KEY` con valor por buildType:
  debug ← `REVENUECAT_TEST_API_KEY`, release ← `REVENUECAT_GOOGLE_API_KEY` (aún no existe),
  ambas por la cascada keystore.properties → local.properties → env. Documentado en README.
  Verificado: bundle prodRelease firmado con `com/android/billingclient` y `com/revenuecat`
  en el dex, `InitializationProvider` + `PurchasesApplicationProvider` en el manifest
  fusionado, suite `:shared:testDebugUnitTest` completa en verde, mock compila.
  ⚠️ La API 3.x NO acepta `configure(apiKey = …)` nombrado: el miembro
  `configure(configuration)` gana la resolución — usar `PurchasesConfiguration(…)`.
- ✅ **FASE 1** — dominio puro (2026-09-06). `Entitlement` (FREE|PLUS) + `PlusPlan`
  (MONTHLY|YEARLY) en `domain/model/`; `EntitlementRepository` (`observeEntitlement():
  Flow<Entitlement>` + `purchasePlus(plan): Result<Unit>`) en `domain/repository/`;
  `ObserveIsPlusUseCase` y `PurchasePlusUseCase` en `domain/usecase/entitlement/`.
  Cero imports de `com.revenuecat` (además lo vigila `ArchitectureTest`: domain es
  platform-pure). `FakeEntitlementRepository` en commonTest + 7 tests. Sin registro en
  Koin todavía — a propósito: la impl no existe hasta FASE 2 y `KoinModuleVerifyTest`
  fallaría con un binding huérfano.
- ✅ **FASE 2** — data + DI (2026-09-06). `RevenueCatEntitlementRepositoryImpl` en
  commonMain: delegate del SDK (push de compras/renovaciones/expiraciones) + fetch inicial
  → `MutableStateFlow<Entitlement>`; mapping = `entitlements.active.containsKey("plus")`.
  Guard `Purchases.isConfigured` en init y en compra (build sin key ⇒ FREE fijo, sin crash).
  `purchasePlus(plan)`: offering `default` → `monthly`/`annual` → `awaitPurchase`.
  Fake de PRODUCCIÓN scenario-aware en `fakes/data/repository/` + `MockScenario.isPlus`
  (con reset) + switch "Plus activo" en el Dev Catalog + binding en `MockModule`.
  Koin: `single<EntitlementRepository>` en dataModule, 2 factories en domainModule
  (`KoinModuleVerifyTest` verde). 4 tests del fake (palanca en ambos sentidos + reset).
- ✅ **FASE 3** — identidad (2026-09-06). `logIn(uid)`/`logOut()` en `EntitlementRepository`
  (impl con guards: no configurado ⇒ no-op; mismo `appUserID` ⇒ no-op sin red; `logOut`
  con perfil ya anónimo ⇒ no-op, el SDK lo trataría como error). Enganchados al ÚNICO
  observador global de sesión: `SplashViewModel.bootstrap()` llama `logIn` tras
  `CrashReporter.setUserId` (NO-fatal: un fallo no aborta bootstrap ni desloguea) y
  `wipeLocalUserData()` llama `logOut` junto al wipe de Room [SESSION-ISOLATION-001].
  **Decisión usuario anónimo**: `configure()` SIN `appUserID` (en el arranque, antes de
  Koin, el uid de BaseLogin no está disponible síncronamente) → RC genera un
  $RCAnonymousID; `logIn(uid)` en cuanto Splash ve Authenticated. La ventana anónima es
  INERTE por construcción: el paywall es inalcanzable antes del login (AuthNavigation
  envuelve toda la app), así que ninguna compra puede caer en un perfil anónimo; y si
  algún día cayera, `logIn` transfiere el perfil anónimo al uid (semántica estándar RC).
  4 tests nuevos en `SplashViewModelTest` (bind con uid, logIn fallido no rompe bootstrap,
  unbind en sign-out, logOut fallido no bloquea el wipe) + binding en `IosMockModule`
  (klib iOS compila).
- ✅ **FASE 4** — gating (2026-09-06). Diseño de campana acordado con el user (06-09).
  - **Historial 30 días**: el invariante vive en UN sitio — `domain/entitlement/
    HistoryEntitlementGate.kt` (`gateHistoryToEntitlement`): free ve 30 días rolling; la
    sesión ACTIVA nunca se oculta (es presente, no historial); los datos jamás se tocan.
    `HistoryTimeline.resolve(sessions, filter, isPlus, nowMs)` — `isPlus` SIN default a
    propósito — deriva `visibleSessions`/`hiddenOlderCount` y de ahí lista, filtro, stats y
    chart salen coherentes. Pie "Tienes N aparcamientos anteriores · Ver todo con Plus"
    (plurals ×9) → paywall; si la ventana no cubre nada, el pie sustituye al empty state
    (un historial OCULTO no puede decir "nunca has aparcado"). El stepper del detalle
    (`ParkingHistoryViewModel`) se gatea con la MISMA función — un stepper que camina donde
    la lista no ve sería un paywall con agujero.
  - **Alertas de zona** (ZONE-SUBSCRIBE-001 opción (a) + pausa por entitlement):
    `Zone.notifyOnSpot` end-to-end (Room v2 + `MIGRATION_1_2` con testigo en
    `AppDatabaseV1BaselineTest`; DTO + deserializador manual + mappers; LWW intacto).
    Campana `MapCircleFab` 56dp a la IZQ del buscador (simetría con MapTypeToggle): free →
    paywall; Plus → `ZoneAlertsDialog` (switch por zona, misma vía de persistencia que el
    formulario). Toggle "Avisarme de plazas libres aquí" en `AddingZonePeek` (visible para
    todos; ENCENDER en free → paywall). PAUSA = el flag no se toca; la entrega gatea en el
    entitlement vivo.
  - **Entrega en cliente (1.x)**: `ObserveZoneSpotAlertsUseCase` (zonas suscritas × spots
    cercanos por zona, haversine contra el radio, baseline sin replay, dedupe, filtra
    RETRACTED y spots propios, master switch leído al disparar) → `AppViewModel` (vivo toda
    la sesión UI) → `showZoneSpotAlert` (notificación local, canal UPLOAD, strings en
    app/src/main/res ×9 con `\'` escapado). Background delivery = ZONE-SUBSCRIBE-001.
  - **Ajustes**: fila Plus PRIMERA (free → paywall con subtítulo de valor; Plus → badge
    ACTIVO en verde marca, sin chevron) + switch "Alertas de zona" en Notificaciones
    (`AppPreferences.notifyZoneSpots`, leído por el use case — no es una pref muerta).
  - **Paywall stub**: ruta `Routes.PAYWALL` + `presentation/paywall/` MVI completo
    (State/Intent/Effect/VM); la pantalla se auto-cierra si Plus llega por cualquier vía.
    FASE 5 monta el composable remoto encima.
  - **Vehículos y zonas sin límite** — cero cambios ahí.
  - Tests: 5 gate + 9 stream (pausa/reanuda sin tocar opt-in, baseline, dedupe, radio,
    retracted/propios, master gate) + opt-in persiste en `SaveOrUpdateZone` + fixtures de
    5 VMs actualizados. Suite completa + Konsist + paridad ×9 en verde; `assembleProdDebug`
    y klib iOS compilan.
  - **Consumidores auditados**: `observeAllSessions()` → SOLO VehiclesViewModel y
    ParkingHistoryViewModel (ambos gateados). `Resolved.sessions` → re-resolve del filtro
    (necesita la lista COMPLETA, documentado) y previews/galería (isPlus explícito).
    Lecturas de diagnóstico (`getByUser`) deliberadamente SIN gate. `VehicleWithStats.
    sessionCount` sigue siendo el total de por vida: es la cifra que el propio pie cita.
- ✅ **FASE 5** — paywall remoto (2026-09-06). `purchases-kmp-ui` cableado en commonMain
  (`implementation` a propósito: sus tipos no cruzan a :app). `PaywallContent` monta
  `Paywall(PaywallOptions(dismissRequest) { shouldDisplayDismissButton = true })` SIN fijar
  offering (el SDK resuelve el *current*, que el dashboard apunta a `default` — una sola
  fuente de verdad). La compra, el restore y la comparativa viven en la PLANTILLA REMOTA;
  aquí no se diseña nada. `PaywallState.storeAvailable` (leído una vez de
  `Purchases.isConfigured` en el VM, estado-driven para la galería): sin store (mock /
  release sin key) → marco estático con `paywall_store_unavailable` ×9, honesto y sin
  jerga. Auto-dismiss al llegar Plus por CUALQUIER vía (compra, restore, otro device) —
  fijado por `PaywallViewModelTest`. Suite + klib iOS + assembleProdDebug en verde.
  ⚠️ **Pendiente del user en el dashboard RC**: crear el Paywall (plantilla) del offering
  `default` con la comparativa completa Plus vs Free — sin plantilla configurada el
  composable muestra el fallback por defecto del SDK.
- ✅ **FASE 6** — calidad (2026-09-06).
  - **Dev Catalog**: grupo "Paparcar Plus" en la galería con 7 variantes — paywall
    (tienda no disponible; la variante CON tienda es imposible en mock a propósito),
    historial free con pie, historial free con TODO oculto, Ajustes con badge ACTIVO,
    diálogo de alertas (con zonas y vacío), y el form de zona con la alerta ON.
    `ZoneAlertsDialog` pasó a público para que la galería lo renderice. La palanca
    `isPlus` del catálogo ya cubría el resto en el grafo real (F2).
  - **Tests de downgrade a nivel VM** (10 nuevos): Vehicles (perder Plus re-resuelve EN
    VIVO — visible se estrecha, `hiddenOlderCount` cuenta, la lista completa sigue
    debajo; pie → paywall), Settings (free que ENCIENDE alertas → paywall sin tocar la
    pref; Plus persiste; silenciar siempre permitido; fila Plus → paywall), Home (campana
    free → paywall sin abrir el diálogo; Plus abre/cierra; toggle del form gateado con el
    switch quieto; el diálogo escribe el flag por la MISMA vía que el form).
  - **Konsist + paridad ×9 con `--rerun-tasks`** (la única ejecución que no puede quedar
    UP-TO-DATE) en verde; suite completa, `assembleProdDebug` y `assembleMockDebug` en verde.

- ✅ **Revisión en device** (2026-09-06, tras ver el mock en mano). Cinco arreglos: el buscador de
  Home volvió a UNA línea (la campana de 56dp de F4 lo estrechaba → se muda a la fila de chips de
  zona, 32dp, donde además pertenece: sin zonas no hay nada que avisar); Plus baja bajo el perfil
  en Ajustes; y un bug real en Notificaciones — dos `PapSwitchRow` se dibujaban UNO ENCIMA DEL
  OTRO porque `PapOutlinedCard` entrega su contenido a un `Surface` (un `Box`), y faltaba el
  `Column` que el resto de tarjetas multi-fila sí tienen. Además: `RevenueCatDataSource` (el repo
  ya no llama al SDK directo, misma costura que `FirebaseDataSource`), identidad Plus metálica y
  el paywall montado con la comparativa.
  ⛔⛔ **El verde premium costó tres intentos y dos revocaciones del user**: v1 a 158,8°/161,5° se
  leía como el teal de vigilancia; v2 corrigió la hue pero mantuvo el lecho oscuro (L*≈10-18%) y
  seguía leyéndose verde bosque. **A esa luminosidad la hue no se percibe** — si Plus lleva la
  marca, la marca tiene que VERSE, y lo que se atenúa es el TEXTO. v3 = superficie rellena de
  verde marca con contenido invertido a `onPrimary`. Lo fija `the premium sweep keeps the brand's
  hue in both themes`: la restricción del premium es la INVERSA del trío — no separarse de la
  marca, quedarse encima.

**Las 7 fases cerradas + la revisión.** Mergeado a master por squash.

Un commit por fase; parar y resumir al cerrar cada una.

## Señales / datos disponibles
- Inspección inicial del repo (2026-09-05): ver resumen en la conversación de apertura.
- RevenueCat dashboard ya configurado (entitlement/offering/packages/productos Test Store).

## Diseño
El invariante "¿este usuario es Plus?" vive en UN sitio: `EntitlementRepository`
(interfaz domain, impl RevenueCat en data). Todo consumidor (historial, alertas de zona,
ajustes, paywall) pregunta vía `ObserveIsPlusUseCase`; nadie consulta RevenueCat directo
desde presentación ni replica el estado.

## Criterio de éxito
- F0: `:app:bundleProdRelease` genera bundle con Play Billing dentro; subido a Pruebas
  internas desbloquea la creación de productos en Play Console.
- Final: usuario free ve historial 30 días + pie con paywall; compra en Test Store activa
  `plus` y desbloquea; perder Plus pausa alertas sin borrarlas; Konsist + paridad de locales
  + tests de downgrade en verde.

## Consumidores auditados
(se rellenará en FASE 4: todos los call sites de historial, alertas de zona y ajustes)
