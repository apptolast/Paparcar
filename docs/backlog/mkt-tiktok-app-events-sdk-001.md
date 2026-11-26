# MKT-TIKTOK-APP-EVENTS-SDK-001 · TikTok se atribuye sus propias instalaciones (SDK, no MMP)

**Estado:** 🔵 En progreso. SDK, divulgaciones, guards y build Android con configuración local
verificados. TikTok Ads Manager confirma la recepción del SDK. Quedan el corte y la
validación de atribución de campaña.
· base `feature/MKT-TIKTOK-APP-EVENTS-SDK-001-app-events-sdk` (`6b442aa2e`)
· continuación local `codex/mkt-tiktok-app-events-sdk-001`

## Problema

Hoy TikTok promociona Paparcar con el workaround de [[MKT-TIKTOK-INSTALL-ATTRIBUTION-001]]: el CTA
del anuncio abre `/descargar/z-tiktok` (un redirect propio con UTM), no el flujo nativo "App
Promotion" de TikTok Ads Manager. Eso significa que **TikTok Ads Manager nunca sabe cuántas
instalaciones trajo de verdad** — no puede optimizar la puja por instalación (MAI/AEO/VBO), y el
anunciante (el user) solo ve lo que Firestore/GA4 le dicen desde dentro, no lo que TikTok reporta
desde fuera. Es el mismo punto ciego que ya se resolvió para Meta con `facebook-core`
([[MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]]).

El user pidió explícitamente igualar TikTok a Meta: "para poder promocionar por TikTok como app,
no como web".

**Por qué la decisión del 24-11 merece revisarse**: en esa fecha se decidió el workaround de enlace
propio porque se asumió que el objetivo nativo "App Install" de TikTok exige un MMP (AppsFlyer/
Adjust) que Paparcar no tiene. Investigado el 25-11-2026: eso es **parcialmente falso**. TikTok
ofrece su propio "TikTok Business SDK" / "TikTok App Events SDK" (Android, Kotlin/Java,
[github.com/tiktok/tiktok-business-android-sdk](https://github.com/tiktok/tiktok-business-android-sdk))
que reporta instalaciones y eventos DIRECTAMENTE a TikTok **sin MMP** — simétrico a lo que ya se
hizo con Meta (sólo `facebook-core`, nunca el SDK completo `facebook-android-sdk`).

## Doctrina violada

Ninguna todavía — esto es una feature nueva, no un bug. Pero al implementarla hay que cumplir LA
MISMA doctrina que ya rige a Meta en `CLAUDE.md` § *Atribución publicitaria*
([[MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]]), sin excepciones por ser un anunciante
distinto:

- Hacia una red publicitaria sale un **nombre de evento y nada más** — lista blanca cerrada, `when`
  exhaustivo, nunca un `if` suelto.
- **Nada sale sin el sí a la medición de anuncios** (`adMeasurementConsent == GRANTED`).
- El identificador de publicidad lo recoge **ese carril y sólo él** — Firebase Analytics lo sigue
  teniendo prohibido.
- La ficha de Play, la web y el Data Safety son consumidores de esto — y el binario (AD_ID en el
  AAB) lo vigila `tools/release/cut.py`.

## Señales / datos disponibles (investigación del 25-11-2026, sin cuenta de implementación)

- **Coste**: el SDK y el uso de la API a nivel de endpoint son gratis — el único coste real es el
  gasto publicitario en sí, igual que con Meta Ads.
  ([ads.tiktok.com/help/article/how-to-integrate-tiktok-app-events-sdk](https://ads.tiktok.com/help/article/how-to-integrate-tiktok-app-events-sdk))
- **Permiso**: el SDK declara `com.google.android.gms.permission.AD_ID` en su propio manifest para
  poder leer el Advertising ID en apps con `targetSdk` ≥ 33 (el nuestro es 37) — mismo patrón que
  ya exige Meta.
- **Qué pide fuera de código**: un "TikTok App ID" generado en TikTok Ads Manager (registrar la app
  como Asset, con el store listing de Play ya existente) y migrar o duplicar la campaña actual de
  "Traffic"/redirect a un objetivo "App Promotion" — dos acciones del user, ninguna de código.
- **Qué manda**: eventos estándar (Install, Registration, Purchase, …) y eventos custom, usados
  para optimización MAI (Mobile App Install) / AEO (App Event Optimization) / VBO en TikTok Ads
  Manager.
- ⚠️ **NO verificado** (la documentación técnica completa vive detrás de un portal con login,
  `business-api.tiktok.com/portal/docs`, inalcanzable sin cuenta):
  - Versión exacta del artefacto Maven/Gradle y su coordenada (`groupId:artifactId`).
  - `minSdkVersion` real del SDK, y si exige algo incompatible con el stack actual (Kotlin 2.4.21,
    KSP 2.3.14, AGP 9.5.1, compileSdk 37, minSdk 26, Compose Multiplatform 1.12.2).
  - Formato exacto de los eventos estándar que acepta (nombres, parámetros) — para diseñar la lista
    blanca con el vocabulario correcto desde el principio, no adivinado.
  - Si publica en Maven Central / Google's Maven o sólo via JitPack/Gradle plugin propio (afecta a
    `gradle/libs.versions.toml` y a la regla de "sólo estables, verificado contra el índice real").

## Diseño — decisiones tomadas (25-11-2026, con el user)

1. **Mismo consentimiento que Meta**: `AppPreferences.adMeasurementConsent`. Una red nueva no pide
   un segundo sí — es el mismo gesto, preguntado una vez, cubriendo cualquier red publicitaria.
2. **Misma lista blanca**: `domain/analytics/AdAttribution.kt` NO se duplica. `AdAttributionEvent`
   (`parking_confirmed`, `spot_released`, `paywall_shown`, cero parámetros) es el vocabulario que
   sale hacia CUALQUIER red — Meta y TikTok reciben el mismo nombre de evento, cada uno por su
   propio SDK. Añadir una tercera red no toca este fichero.
3. **Qué manda**: por ahora sólo los tres de siempre + lo que el propio SDK de TikTok dispara
   automáticamente al inicializarse (`InstallApp`/`LaunchAPP`, ver abajo) — nada de eventos de
   producto nuevos todavía.
4. **JitPack**: aceptado como excepción documentada (único canal de distribución real, confirmado
   contra el `build.gradle` del propio repo de TikTok) — ver `settings.gradle.kts` y
   `gradle/libs.versions.toml`.

## Investigación de la API real (fuente primaria: el código del SDK en GitHub, NO el portal)

El portal de ayuda de TikTok (`business-api.tiktok.com/portal/docs`, el que el user pegó) da una
versión **desactualizada** (`1.5.0`; la real es `1.7.1`) y un snippet de integración **de 2018**
que no compila en este repo. Verificado línea por línea contra el código fuente real
(`gh api repos/tiktok/tiktok-business-android-sdk/...`):

- `TikTokBusinessSdk.TTConfig(application, accessToken).setAppId(appId).setTTAppId(ttAppId)` —
  `appId` es el paquete de Android (`com.rndeveloper.paparcar`) y `ttAppId` es el «TikTok App ID»
  numérico de Events Manager. El SDK acepta el App Secret de la fuente como `accessToken`.
  [Validación de los IDs en el SDK 1.7.1](https://github.com/tiktok/tiktok-business-android-sdk/blob/1.7.1/business-core/src/main/java/com/tiktok/util/RegexUtil.java).
- ⛔ **No hay "configurar mudo y desmutear después"** como con Meta: `disableAdvertiserIDCollection()`
  sólo se decide en el momento de `initializeSdk`, y no existe un `enableAdvertiserIDCollection()`
  posterior. Por eso `TikTokAdAttribution.configure()` NO llama al SDK — sólo guarda credenciales;
  la ÚNICA llamada a `initializeSdk` vive en `activate()`, y por diseño nunca ocurre sin
  consentimiento ya concedido. Es la documentación del propio `startTrack()` la que describe este
  caso exacto: *"invoked after the user agrees to some terms or conditions, so events are not
  pushed to the network before user's consent"*.
- `destroy()` SÍ permite apagar del todo (a diferencia de Meta) — `deactivate()` lo usa.
- Todos los overloads estáticos `trackEvent(String, …)` están **deprecados** en el SDK (confirmado
  compilando: `-Werror` los rechaza). La vía viva es `trackTTEvent(TTBaseEvent)`, con
  `TTBaseEvent.newBuilder(nombre).build()` para un evento sin propiedades — exactamente "cero
  parámetros".
- El manifest que fusiona el SDK sólo declara `INTERNET` + `AD_ID` (nada parecido a las dos APIs
  de Privacy Sandbox que Meta fusiona de más) — **AD_ID ya está declarado explícitamente por
  Meta**, así que no hace falta tocar `AndroidManifest.xml` para nada.
- El propio AAR trae sus reglas de R8 (`consumer-proguard-rules.pro`: `-keep class com.tiktok.**`
  + las de `com.android.billingclient.api`) — el paso 3 del copia-pega del portal (añadirlas a
  mano) es redundante, igual que ya era innecesario para `facebook-core`.
- Por defecto el SDK dispara automáticamente `InstallApp`/`LaunchAPP` al inicializarse
  (`autoEvent = true`) — es lo que de verdad atribuye la instalación a TikTok Ads, sin que
  `TikTokAnalyticsTracker.track()` tenga que hacer nada por su cuenta.

## Qué se implementó (esta tanda)

- `domain/attribution` — nada nuevo aquí; el vocabulario de ads ya vivía en `AdAttribution.kt`.
- `data/analytics/CompositeAdAttributionTracker.kt` (**commonMain**, nuevo) — fan-out a N redes
  (`List<AdAttributionTracker>`), también `AdMeasurementLane` compuesto. Mismo patrón que
  `CompositeAnalyticsTracker`, un nivel más abajo.
- `data/analytics/TikTokAdAttribution.kt` (androidMain, nuevo) — el interruptor, `configure`/
  `activate`/`deactivate`, documentado arriba por qué su forma es distinta a `MetaAdAttribution`.
- `data/analytics/TikTokAnalyticsTracker.kt` (androidMain, nuevo) — el `AdAttributionTracker` +
  `AdMeasurementLane`, hermano de `MetaAnalyticsTracker`, misma lista blanca.
- `di/AndroidPlatformModule.kt` — `single<AdAttributionTracker>` ahora construye
  `CompositeAdAttributionTracker(listOf(Meta..., TikTok...))`.
- `PaparcarApp.kt` — `TikTokAdAttribution.configure(...)` en `onCreate` (antes de Koin, igual que
  Meta) y `TikTokAdAttribution.activate(this)` junto al de Meta, bajo el mismo `if (consentimiento
  == GRANTED)`.
- `app/build.gradle.kts` — `TIKTOK_APP_ID` / `TIKTOK_TT_APP_ID` / `TIKTOK_ACCESS_TOKEN` como
  `buildConfigField`, leídos de `local.properties` con el mismo helper `prop(...)` que Meta. Sin
  `manifestPlaceholders`: no hay `<meta-data>` que rellenar, todo entra por el builder de código.
- `gradle/libs.versions.toml` + `settings.gradle.kts` — dependencia + repo JitPack, documentados.
- `./gradlew testAll --rerun-tasks` verde (871 tests… 4243 totales), `compileProdDebugKotlin` y
  `compileMockDebugKotlin` verdes.

## Segunda tanda (código puro, sin tocar web/política/Data Safety) — HECHA

1. **`AdvertisingDisclosureGuardrailTest`** — nuevo test
   `TikTokBusinessSdk initializeSdk has exactly one call site, and it is the gate`: grep sobre
   `shared/src/{androidMain,commonMain}` (excluye `androidUnitTest` a propósito — el propio test
   nombra el literal que busca) que exige que `TikTokBusinessSdk.initializeSdk(` viva en
   EXACTAMENTE un fichero, `TikTokAdAttribution.kt`. Es el invariante real de TikTok (no hay un
   flag de manifest como `AutoInitEnabled` que leer — la única garantía posible es "la llamada
   peligrosa vive en un solo sitio, guardado"). **Falsificado**: añadir una segunda llamada en
   otro fichero pone el test en rojo; retirada, vuelve a verde.
   La lista `AD_SDKS` quedó para la siguiente tanda, una vez actualizadas las divulgaciones.
2. **`tools/release/cut.py`** — `AD_SDKS` ahora tiene entrada `tiktok-business-sdk` (nombres,
   `props` = dos ids, `lands_in = "dex"`, sin `requires`/`forbids`). `check_aab_carries_the_ad_key`
   generalizado: antes sólo sabía buscar en el manifiesto (`aab_manifest_bytes`); ahora también
   sabe buscar en el DEX del módulo base (`aab_dex_bytes`, nuevo) para una red cuyo id NO pasa por
   el manifest — los ids de TikTok se configuran con el builder de código (`TTConfig`), nunca con
   `<meta-data>`. Verificado a mano contra el repo real (mismo nivel que exigió el ticket de
   Meta — "comprobado sobre un AAB real"): `declared_ad_networks()` ya detecta las DOS redes;
   `check_aab_carries_the_ad_key` pasa limpio con las tres claves puestas y aterrizadas, y falla
   con el mensaje correcto cuando `TIKTOK_APP_ID` está vacía (el estado REAL de hoy).
   ⛔ Efecto colateral real y correcto: `check_listing_names_the_ad_network` ahora también exige
   que la ficha nombre a TikTok en cualquier corte futuro — rompía `test_cut.py::BumpPhase`
   (lee el `ROOT` real, sin parchear) porque esas pruebas no aíslan `declared_ad_networks()`.
   Arreglado parcheando esa función a `[]` en `BumpPhase.setUp` (prueba otra cosa: duplicados,
   versión, escritura en los dos sitios) — `python tools/release/test_cut.py` 45/45 verde.
3. `./gradlew testAll --rerun-tasks` sigue verde tras todo lo anterior.

## Tercera tanda — divulgaciones y guard

1. **Data Safety** (`docs/legal/DATA-SAFETY-FORM.md`). TikTok figura junto a Meta en *App
   interactions* y *Device or other IDs* (Advertising ID). Mismo consentimiento y finalidad
   publicitaria para ambas redes.
2. **Política EN/ES** (`hosting/public/privacy-policy.html`). §2.10 y §2.11 describen el SDK y
   el consentimiento compartido. TikTok figura en la tabla de destinatarios y en la retención.
3. **Web EN/ES**. La sección `trust` nombra a Meta y TikTok. Regeneradas las dos portadas de
   `hosting/public` con `node web/build.mjs`. `node web/build.mjs --check` pasó.
4. **`AdvertisingDisclosureGuardrailTest`**. `AD_SDKS` incluye `tiktok-business-sdk`. El guard
   exige que cada superficie nombre a **cada** red detectada. Antes bastaba con nombrar a una.

La lista blanca limita lo que **Paparcar** añade a cada evento de producto. El código del SDK 1.7.1
muestra que el SDK añade su propio contexto técnico, incluido el identificador seudónimo y la
referencia de Google Play, y puede registrar un retorno al día siguiente además de instalación y
apertura. Las divulgaciones reflejan esa distinción. [Contexto de la petición](https://github.com/tiktok/tiktok-business-android-sdk/blob/1.7.1/business-core/src/main/java/com/tiktok/appevents/TTRequestBuilder.java) · [Eventos automáticos](https://github.com/tiktok/tiktok-business-android-sdk/blob/1.7.1/business-core/src/main/java/com/tiktok/appevents/TTAutoEventsManager.java).

**Validación local.** `./gradlew testAll --rerun-tasks --console=plain` terminó con
`BUILD SUCCESSFUL` y 95 tareas ejecutadas, incluida la compilación del klib iOS.
`PrivacyPolicyParityGuardrailTest` pasó 1/1 y `AdvertisingDisclosureGuardrailTest` pasó 3/3.
`python tools/release/test_cut.py` terminó con `OK (skipped=1)` sobre 45 casos. El caso omitido
requiere el AAB histórico de la 1.5.0.

## Preparación de la prueba en dispositivo

- Configurado `local.properties` del worktree con los IDs y el App Secret real de la fuente.
- El builder desactiva el evento de vuelta al día siguiente, las compras automáticas, Enhanced
  Data Postback y las métricas propias del SDK. Quedan la instalación, la apertura y los eventos
  de la lista blanca de Paparcar. Data Safety y la política EN/ES reflejan este alcance.
- Pasaron `./gradlew testAll --rerun-tasks --console=plain` (95 tareas ejecutadas),
  `./gradlew :app:assembleProdDebug --console=plain`, `node web/build.mjs --check` y
  `python tools/release/test_cut.py` (45 casos, 1 omitido por falta del AAB histórico).
- APK instalado y abierto en un emulador limpio. El arranque llegó a inicio de sesión y
  `logcat` no mostró activación de TikTok en ese tramo.
- La primera prueba tras iniciar sesión confirmó que Ajustes sólo nombraba a Meta. Se
  corrigieron el aviso y Ajustes en los nueve idiomas para explicar el envío del identificador
  publicitario y eventos a Meta y TikTok. Android guarda una aceptación específica de ambos
  destinatarios. Una aceptación antigua vuelve a pendiente y un rechazo antiguo se conserva.
  Los dos SDK consultan el mismo estado desde el arranque y al enviar eventos.
- Verde `./gradlew testAll :app:assembleProdDebug --rerun-tasks --console=plain`
  (139 tareas ejecutadas, 3/3 casos de almacenamiento de consentimiento). Volvieron a pasar
  `node web/build.mjs --check` y `python tools/release/test_cut.py` (44 pasan, 1 omitido).
- El APK actualizado conservó la sesión de prueba. Ajustes nombró a ambas redes y empezó
  apagado. `logcat` registró el encendido tras conceder el permiso (18:10:25 UTC) y el apagado
  al revocarlo (18:11:19 UTC). Tras reiniciar la app no hubo una nueva activación.

## Lo que sigue sin entrar — y el ticket sigue abierto

- **Events Manager**. La captura del 25-11-2026 confirma que la fuente de Paparcar pasó a
  **Verified** y que Overview → Reporting muestra **1 Install** y **2 Launch App**, con
  registro del día. Antes de aparecer en el panel, la prueba del emulador registró un
  evento aceptado y cero fallos en un lote. Se retiró la instrumentación temporal y se
  revocó el permiso de prueba.
- **Atribución de campaña**. Falta validar una instalación desde un anuncio hasta Google
  Play con la versión que incluya la integración. Reporting de Events Manager muestra
  eventos totales de la fuente.
- **Corte de Play**. Preflight de Play confirmó 1.8.0 (vc 19) en producción e internal.
  Preparadas la ficha candidata y las notas de 1.8.1 (vc 20) en nueve idiomas. Pasaron las
  comprobaciones de nombres de redes, locales y longitud. La ficha que apunta
  `published-listing.txt` sigue siendo la que está publicada.
- **Declaraciones y Hosting**. Revisar el CSV vigente de Data Safety antes de cualquier
  actualización por API, que escribe el formulario completo. Mantener las respuestas de
  Advertising ID y Ads si ya reflejan el uso publicitario del identificador y la ausencia
  de anuncios dentro de la app. Publicar las páginas de Hosting junto con el corte que
  incluya TikTok, desde el árbol integrado.

## Criterio de éxito

- [x] Compilan prod y mock según la primera tanda.
- [x] Guards de inicialización, paridad y divulgación en `testAll`.
- [x] Data Safety, política bilingüe y web reconstruida.
- [x] Suite de `cut.py` verde, con la omisión del AAB histórico registrada.
- [x] Configuración local con credenciales reales y build prodDebug.
- [x] Aviso y Ajustes en nueve idiomas y renovación de las aceptaciones antiguas en Android.
- [x] Consentimiento, activación y revocación verificados en el emulador.
- [x] Recepción de instalación y apertura confirmada en Events Manager.
- [ ] Atribución a una campaña real confirmada en Ads Manager.
- [ ] Ficha y declaraciones de Play Console en el corte.

## Consumidores auditados

- `AndroidPlatformModule.kt` — único punto que construye `AdAttributionTracker`; ahora compone
  Meta+TikTok, ningún otro sitio del grafo lo hace.
- `MockGraphGuardrailTest`/Dev Catalog — `androidPlatformModule` sigue sin cargarse en el flavor
  mock, así que `TikTokAnalyticsTracker` es tan inalcanzable desde ahí como `MetaAnalyticsTracker`
  ya lo era. Verificado: `testMockDebugUnitTest` sigue verde dentro de `testAll`.
- iOS — sin cambios; `IosPlatformModule.kt` sigue sin atar `AdAttributionTracker`
  (`DataModule` lo pide con `getOrNull`), igual que con Meta.
