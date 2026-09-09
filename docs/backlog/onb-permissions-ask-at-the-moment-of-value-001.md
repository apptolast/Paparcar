# ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001 · El primer arranque pide lo mínimo; la detección se activa en su momento de valor

**Estado:** ✅ Done (09-09-2026) — v1 (focos excluyentes) + v2 (Home-first, ask sobre el mapa) +
v3 (cards a ancho completo, pitch de la estrella, banner sin cortes). Verificado en Oppo con
prodRelease (R8) y en emulador; el hash del squash vive en MEMORY.md.

## Problema

El primer arranque (`Onboarding → PERMISSIONS` sin argumento → `PermissionsFocus.All`) pinta la
lista COMPLETA de permisos: tarjeta de nivel de detección + 3 tiers con ~8-10 filas (localización,
GPS, background, actividad, notificaciones, Bluetooth, batería, autostart OEM…). Aunque el footer ya
guía por pasos (CORE primero), el usuario ve el muro entero antes de haber visto una sola pantalla
de valor de la app. El propio user lo describe: «meterte una lista de permisos de primera puede ser
algo agresivo».

Además cada petición prematura quema munición real: desde Android 11, dos denegaciones = «no volver
a preguntar» silencioso. Pedir PRODUCER a alguien que aún no ha marcado un aparcamiento maximiza
denegaciones justo donde menos nos lo podemos permitir.

## Doctrina violada

Ninguna regla escrita se rompe — es deuda de UX contra el espíritu de dos doctrinas existentes:
- *Fallo asimétrico / ante la duda se pregunta*: la app ya sabe preguntar en contexto (banner,
  checklist); el primer arranque es el único sitio que aún pide por adelantado.
- El propio diseño del tier PRODUCER (`PermissionTier.kt`): «Requested contextually at the moment
  of value». El código lo declara; el flujo de primer arranque no lo cumple.

## Señales / datos disponibles — lo que YA existe (auditado 09-09-2026)

El andamiaje contextual está construido; este ticket NO crea infraestructura nueva:

- **Gate de entrada ya es CORE-only**: `SplashViewModel.resolveStartRoute` [DET-READY-001d] y
  `AppViewModel` gatean solo `hasCorePermissions`. Un usuario sin PRODUCER entra a Home.
- **El banner de Home ya pide la detección**: `DetectionStory.Inactive` («Detection off … one-tap
  activate») → `onActivateDetection` → `PERMISSIONS?focus=Producer` (`App.kt:582`).
- **El checklist guiado ya la pide con más contexto**: `FirstStep.UNDERSTAND_WATCH` con cara
  `WatchAsk.TURN_IT_ON`, y `FirstStepsOwnership.DETECTION_OFF` hace callar al banner para que haya
  una sola voz.
- **BT ya se pide en su momento de valor**: `BluetoothConfigScreen` (elegir la MAC) llama
  `rememberRequestBluetoothPermissionAction()`; el checklist además lo ofrece como
  `WatchReinforcement.BLUETOOTH`.
- **La pantalla ya sabe enfocarse**: `PermissionsFocus.Core` / `.Producer` ocultan secciones
  (`showEssential` / `showProducer` en `PermissionsContent`).
- **La ruta de «entrar solo con CORE» ya existe y funciona**: `ContinueWithCore` → Home con el
  banner pidiendo el resto. Hoy está escondida tras un TextButton + diálogo de culpa
  («¿seguro que quieres saltarte…?») [DET-TOGGLE-002].

## Diseño v2 — «el mapa pide, la app convence» (feedback del user, 09-09 tarde)

> El diseño v1 (abajo) redujo el muro a una pantalla Core de 2 filas. El user la probó en el Oppo
> y la rechazó con razón: el flujo acababa en **dos pantallas de seguridad seguidas** (candado del
> onboarding + escudo de permisos) antes de enseñar nada del producto. Decisiones nuevas
> (confirmadas por el user): Home-first, diálogo del sistema directo sobre el mapa, y vender la
> detección en vez de bloquear.

1. **Muere la parada de permisos del primer arranque.** Splash deja de gatear por CORE/GPS:
   `Auth → Onboarding (valor, acaba en «Empezar») → registro de vehículo → Home`. Home ya sabe
   vivir sin ubicación (`DetectionStory.BlockedCore`): pasa de estado de error a primeros segundos
   normales.
2. **La ubicación se pide SOBRE el mapa.** Primera entrada a Home → diálogo del sistema directo
   (patrón Maps/Waze; el mapa ES el contexto). La decisión (core ausente + nunca pedido, vía
   `hasRequestedLocationPermission`) vive en un sitio puro y testeable. Si deniega: Home en modo
   lectura; la fila BlockedCore y Ajustes poseen la re-pregunta (pantalla `Focus.Core` = reparación,
   nunca estreno).
3. **Onboarding pierde la página del candado.** La página 3 («Why we need your permissions» +
   hero PRIVACY) se borra; el CTA final es «Empezar». La **prominent disclosure** de ubicación en
   segundo plano se muda a la visita Producer, ANTES de pedir background — sigue precediendo a la
   petición, que es lo que exige Play. ⚠️ user: si Play re-revisa la declaración, regrabar el vídeo
   con el flujo nuevo.
4. **La detección se VENDE en sus superficies.** Paso `TURN_IT_ON` del checklist, banner
   `Inactive` y header de la visita Producer reciben el pitch memoria + comunidad: *Paparcar se
   acuerda de dónde aparcas por ti; al irte, tu plaza queda libre para otro — como la que tú
   buscas*. Sin diálogos de culpa, sin pantalla bloqueante.
5. **El GPS disclaimer (precisión) se muda a su momento honesto**: se muestra una sola vez al
   completar la visita Producer (habla de la detección; quien no la activa no lo necesita).
6. El v1 no se tira: focos excluyentes, footer por focus, strings y galería son la base sobre la
   que se monta esto.

## Diseño v1 — el sistema (implementado, base del v2)

**Invariante (en UN sitio):** *el primer arranque muestra y pide SOLO el tier CORE; todo permiso
PRODUCER se pide desde una superficie contextual que ya es dueña de esa pregunta.* El sitio que lo
encarna es `PermissionsFocus`: deja de existir la vista «todo» (`All`) y el default de navegación
pasa a `Core`.

1. **`PermissionsFocus.All` se retira.** Quedan `Core` y `Producer`, mutuamente excluyentes en
   contenido. `fromArg` y el `defaultValue` de la ruta pasan a `Core`: tanto el primer arranque
   (post-onboarding) como el gate de cold-start por CORE revocado aterrizan en la vista mínima.
2. **Vista Core = esencial + teaser.** Tier esencial (localización + GPS) y una caption debajo
   que cuenta que la detección automática se activa después desde el mapa — causa + consecuencia +
   remedio, sin mecánica interna. String nuevo ×9 locales.
3. **Footer consciente del focus.** Hoy `requiredComplete` exige PRODUCER siempre; en una visita
   `Core` con todo lo esencial concedido el footer mostraba «Activar detección» de una sección que
   ni se renderiza (bug latente). Pasa a: Core → conceder/GPS/ajustes → **Continuar**; Producer →
   flujo actual (sensores → guía background → batería opcional → Continuar).
4. **El diálogo de culpa muere.** `showSkipDetectionDialog`, `RequestSkipDetection`,
   `DismissSkipDetectionDialog`, `ContinueWithCore` y `canContinueWithCore` se retiran (borrado
   limpio, no sobrecarga muda). En focus Producer el escape es «Ahora no» (un tap → vuelve a Home,
   sin diálogo); en focus Core no hay nada que saltarse. Strings del diálogo se borran de los 9
   locales; entra `permissions_btn_not_now` ×9.
5. **Header por focus** (hallazgo durante la implementación): el título/subtítulo compartidos
   («Automate your parking» / «Only location is required — the rest…») estaban escritos para el
   muro y mentían a ambas vistas. La Core estrena `permissions_core_title/subtitle` (ver el mapa y
   las plazas); la Producer conserva su título y reescribe `permissions_subtitle` nombrando los dos
   conceptos según [COPY-SPOT-IS-NOT-A-PARKING-001].
6. **Sin cambios en detección**: ni estrategias, ni evaluadores, ni routing de Splash (ya era
   CORE-only), ni la cadena cold-start `PERMISSIONS → GPS_DISCLAIMER → explainer/HOME` [NAV-DEDUP-001].

## Criterio de éxito

- Primer arranque: la pantalla de permisos muestra SOLO localización + GPS + teaser; conceder ambos
  habilita «Continuar» y sigue la cadena actual. Ninguna fila PRODUCER/opcional visible.
- Un usuario que entra solo con CORE ve en Home el banner/checklist pidiendo activar la detección
  (comportamiento ya existente, ahora el camino por defecto y no el excepcional).
- Visita `Producer` desde Home/Ajustes: idéntica a hoy salvo que «Ahora no» vuelve sin diálogo.
- `:shared:testDebugUnitTest` verde (incluye `LocaleParityGuardrailTest` tras el barrido de strings)
  y `assembleMockDebug` compila con la galería actualizada.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `App.kt` ruta `PERMISSIONS?focus=` (defaultValue `All`) | ✅ pasa a `Core` |
| `App.kt:518` onboarding → PERMISSIONS sin arg | ✅ hereda `Core` por default |
| `SplashViewModel` → `Routes.PERMISSIONS` sin arg (gate CORE) | ✅ hereda `Core` — correcto |
| `SettingsViewModel:177-199` | ✅ ya usa `Core`/`Producer` explícitos — sin cambio |
| Home `onActivateDetection` (`"core"`/`"producer"` vía `fromArg`) | ✅ case-insensitive, nunca usó `All` |
| `PermissionsScreen` expect + actuals Android/iOS (default `All`) | ✅ default `Core`; iOS gana `onNotNow` |
| `PermissionsContent` footer no-consciente-de-focus | ✅ `requiredComplete` y batería por focus |
| Header compartido que mentía a ambas vistas | ✅ por focus (diseño §5) |
| Galería mock `StateGalleryScreen` (9 variantes) | ✅ 2 Core + 7 Producer; variante del diálogo borrada |
| `PermissionsContentPreviews.kt` (paridad galería) | ✅ 9 refs `Producer`; first-run queda Core |
| Strings `permissions_skip_dialog_*` (4), `permissions_continue_with_core` | ✅ borrados ×9 |
| Strings nuevos `permissions_btn_not_now`, `permissions_core_detection_teaser`, `permissions_core_title/subtitle` | ✅ ×9 locales |
| Tests commonTest `PermissionsViewModelTest` | ✅ 18/18 verdes; test nuevo del `NotNow` sin diálogo |
| Onboarding pág. 3 (prominent disclosure de Play) | ✅ EXENTO a propósito: la disclosure de background-location debe preceder a todo y está atada a la declaración en vídeo de Play — no se toca |
| `GpsDisclaimerScreen`, `AppViewModel`, `BluetoothConfigScreen` | ✅ sin cambio |

**Verificación v1 (09-09-2026):** `:shared:testDebugUnitTest` verde (incluye guardarraíles Konsist y
`LocaleParityGuardrailTest` tras el barrido) · `:app:compileMockDebugKotlin` y
`:app:compileProdDebugKotlin` compilan.

## Consumidores auditados — v2 (Home-first)

| Consumidor | Estado |
|---|---|
| `SplashViewModel.resolveStartRoute` (gate CORE/GPS) | ✅ fuera; `PermissionManager` retirado del constructor + DI + test builder |
| `SplashState.postPermissionsRoute` | ✅ renombrado `postSetupRoute` (ya no hay parada de permisos) |
| `App.kt` onboarding → PERMISSIONS | ✅ → `postSetupRoute` directo |
| `App.kt` guard reactivo del GPS disclaimer (`isFullyOperational`) | ✅ → `isDetectionReady` (CORE+GPS+PRODUCER): quien no activa la detección no lo ve |
| `App.kt` PERMISSIONS `onPermissionsGranted` (cadena cold-start → disclaimer) | ✅ pop a Home; disclaimer solo reactivo |
| `AppState` | ✅ + `detectionPermissionsGranted` / `isDetectionReady`; `showGpsAccuracyDisclaimer` cuelga de detección |
| `OnboardingScreen` página 3 (candado + «Set up permissions») | ✅ borrada; 2 páginas, CTA «Empezar» (`onboarding_cta_start` ×9) |
| Prominent disclosure de background (`onboarding_page3_subtitle`) | ✅ movida TAL CUAL (misma traducción ×9) a `permissions_bg_disclosure`, pintada en la vista Producer ANTES de pedir background |
| `HomeEffect.RequestLocationPermission` (handler no-op + emisor a ciegas en `LoadNearbySpots`) | ✅ cableado de verdad: `rememberRequestFineLocationAction` expect/actual (Android launcher / iOS `requestStep1`); el branch ciego, borrado |
| Disparo del ask | ✅ en `SetMapForeground(true)` — ⛔ NO en `init`: un SharedFlow sin replay DESCARTA efectos sin colector (medido: test «No value produced in 3s»); por lo mismo el colector de efectos va declarado ANTES de `MapForegroundEffect` |
| Latch una-vez | ✅ `hasRequestedLocationPermission` estampado antes de lanzar; re-asks = fila BlockedCore / Ajustes (`Focus.Core` = reparación) |
| Copy de venta (memoria + comunidad) | ✅ `first_steps_watch_on_title/sub`, `home_det_producer_sub`, `permissions_subtitle` ×9 |
| Dev Catalog presets «Permisos · rationale» / «Pantalla de permisos (GPS off)» | ✅ renombrados a su destino real (Home pide en el mapa / BlockedCore) |
| Tests | ✅ Splash: 3 tests de gate → HOME/explainer · AppVM: 2 tests nuevos `isDetectionReady` · HomeVM: 2 tests del ask (fixture default = ya-preguntado para no romper 20 tests de efectos) |
| `OnboardingHero.PRIVACY` (candado) | ✅ EXENTO: queda como pieza del inventario de ilustraciones (showcase `IdentityIconsPreviews`), sin flujo que lo use |
| ⚠️ Declaración de Play (vídeo) | ⏳ user: la disclosure sigue precediendo a la petición de background (cumple), pero si Play re-revisa hay que regrabar el vídeo con el flujo nuevo |

**Verificación v2 (09-09-2026):** suite completa + `compileMockDebugKotlin` + `compileProdDebugKotlin`
verdes en la misma invocación.

## v3 — pulido con la pantalla en la mano (feedback del user, 09-09 noche)

- **Cards a ancho completo**: la timeline de `PermissionTier` (nodo + conector) murió — con focos
  excluyentes (1-2 secciones por vista) no contaba ninguna progresión y robaba ~48dp por card. El
  estado «tier concedido» lo cuenta ahora un check verde en la cabecera (`PapSectionHeaderRow` con
  slots). El respiro inter-sección que ponía el conector es un `Spacer(xxl)` explícito.
- **Banner Inactive**: el sub se cortaba con elipsis (compartía ancho con el botón «Activar») → 
  acortado a ~40 caracteres ×9 (`home_det_producer_sub`). **Color: se queda verde** — en la
  doctrina el verde es marca+acción; el ámbar significa salud degradada y pintarlo así devaluaría
  los avisos reales.
- **Diálogo-pitch de la funcionalidad estrella** (`home_det_pitch_title/body` ×9): al conceder la
  ubicación del primer arranque sobre el mapa, UNA vez, se ofrece activar la detección
  («Lo mejor de Paparcar…»). No es el diálogo de culpa retirado: «Ahora no» es un tap y no vuelve
  (el banner y el checklist conservan la pregunta). Disparo: flag en vuelo armado por el ask del
  primer arranque + consumido en `subscribePermissions` al llegar el grant sin PRODUCER
  (`HomeState.showDetectionPitch`, intents `AcceptDetectionPitch`/`DismissDetectionPitch`; aceptar
  reutiliza `EnableAutoDetection` [DET-TOGGLE-001]). ⛔ El diálogo NO está en la galería: como el de
  liberar plaza, es de interacción pura y la galería no monta el host de diálogos de Home.
- Verificado en emulador (prodDebug `5cb86d8e`): checklist con el copy de venta nuevo (y el banner
  callado mientras el checklist pregunta — una voz), y la vista Producer con header de venta,
  disclosure, cards a ancho completo y «Not now». Tests: 2 nuevos del pitch en `HomeViewModelTest`.
- ⚠️ El prodRelease NO entra sobre un prodDebug previo en el mismo device (firmas debug vs release
  → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, medido en el emulador). El Oppo ya está en release, así
  que release-sobre-release entra bien.

**Rebase (09-09-2026):** WIP committeado y rebasado sobre master `6d56115e` (6 commits nuevos:
F5b gasolineras, paywall crest, y el fix del FGS), **sin conflictos**; suite y compiles verdes tras
el rebase. El hallazgo lateral del crash-loop del centinela que esta tarea apartó resultó ser
[DET-A-LOCATION-FGS-MUST-NOT-KILL-THE-APP-001], ya arreglado en master `73b32238` con datos de
producción — la ficha duplicada se retiró.
