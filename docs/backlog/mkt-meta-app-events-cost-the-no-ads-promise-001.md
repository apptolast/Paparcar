# MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001 · el SDK de Meta cuesta la promesa de «sin anuncios»

**Estado:** ✅ Done (03-11-2026) · rama y worktree borrados al cerrar
· ⏳ **tres confirmaciones, ninguna de código**: verlo en un device con las claves puestas,
desplegar la política, y los pasos del Console. La **ficha de Play** se mueve en el corte de la
versión que lleve el SDK — la para `cut.py`, no la memoria. Ver *Criterio de éxito*.

## Problema

Para anunciar Paparcar en Meta y que Meta optimice por instalación hace falta su SDK de **App
Events** dentro del binario. Eso no es una dependencia más: es la **primera vez** que algo de esta
app sale hacia un tercero **con fin publicitario**, y tres superficies publicadas prometían
exactamente lo contrario:

| Superficie | Lo que prometía |
|---|---|
| `hosting/public/privacy-policy.html` §3 | *«No usamos tus datos para publicidad ni los cedemos a terceros con fines comerciales»* |
| `web/content/{es,en}.json` → `trust` | *«Tu ubicación no se vende ni se usa para publicidad, y no hay anuncios»* |
| Ficha de Play, bloque PRIVACIDAD | *«Sin anuncios»* / *«Tu ubicación nunca se vende ni se usa para publicidad»* |

Y el manifest las sostenía: borraba los **tres** permisos del identificador de publicidad con
`tools:node="remove"` [ANALYTICS-EVENTS-001].

El trabajo de partida lo hizo el user con Gemini y lo aparqué el 27-10 sin revisar (`68d87dfc8`,
*wip*). Compilaba. Lo que traía, y por qué no se podía mergear tal cual, está en *Qué cambió
respecto al WIP*.

## Doctrina violada

- [WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001] y
  [DOCS-THE-STORE-LISTING-IS-A-CONSUMER-TOO-001] — la web y la ficha son consumidores de la verdad
  del producto, con **disparadores distintos**: la web describe el PRODUCTO y se mueve ya; la ficha
  describe el BUILD PUBLICADO y se mueve en su corte.
- [VEH-SHARED-001] — *la lista blanca de lo que sale del móvil es el TIPO, no un `if`*. El espejo
  del coche compartido no tiene campo para `armEvidence` ni `detectionPath`; el carril publicitario
  tampoco puede tenerlo.
- [ANALYTICS-EVENTS-001] — *la ubicación sale como CELDA, nunca como coordenada*. Hacia una red
  publicitaria no sale **ni la celda**.
- `feedback_systems_not_patches` — el invariante («lo publicado dice lo que hace el binario») se
  arregla en un sitio y se barren todos sus consumidores.

## Las tres decisiones del user (02-11-2026)

| | Decisión | Consecuencia |
|---|---|---|
| **Identificador de publicidad** | **Activarlo** | Sin GAID, Meta no puede casar una instalación con el anuncio que la pagó: el SDK mandaría eventos que nadie puede atribuir, que es justo para lo que entra. Vuelven los tres permisos al manifest y la fila de *Advertising ID* al Data Safety |
| **Consentimiento** | Detrás del **consentimiento legal que ya existe** (`hasAcceptedLegalConsent`, el que bloquea el sign-in [AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001]) | Sin UI nueva. El SDK no se inicializa, no recoge el GAID y no manda el evento de sesión hasta que el usuario acepta términos + política |
| **Qué eventos recibe Meta** | **Lista blanca mínima, sin parámetros** | Tres nombres y nada más. La mecánica de detección no sale del móvil |

## Qué se hizo

### 1 · El vocabulario cerrado, en el dominio y testeable

`domain/analytics/AdAttribution.kt`:

- `AdAttributionTracker : AnalyticsTracker` — el carril que existe para que un anuncio se pueda
  atribuir. Nombre propio a propósito: `grep AdAttributionTracker` contesta de una vez *qué sale de
  este móvil hacia un anunciante*, que es la pregunta del formulario de Data Safety.
- `AdAttributionEvent` — **tres** nombres: `parking_confirmed`, `spot_released`, `paywall_shown`.
- `AnalyticsEvent.asAdAttributionEvent()` — función pura con `when` **exhaustivo**: añadir un
  `AnalyticsEvent` no compila hasta decidir si sale del móvil. `TriggerFired`,
  `ParkingSessionEnded` y `SpotReported` devuelven `null`, y el porqué de cada uno está en su KDoc.

⛔ **Cero parámetros.** Ni celda, ni `detectionPath`, ni `armEvidence`, ni `spot_type`. Una red
publicitaria no puede optimizar por ninguno de ellos, así que mandarlos sólo ensancharía la
declaración de Data Safety a cambio de nada.

### 2 · El interruptor, y el consentimiento

`data/analytics/MetaAdAttribution.kt` (androidMain) — el **único** sitio que enciende el SDK, en dos
pasos porque son dos momentos:

- `configure(appId, clientToken)` corre en `PaparcarApp.onCreate` y deja el SDK **mudo**: repite en
  código los tres `false` del manifest (`AutoInitEnabled`, `AutoLogAppEventsEnabled`,
  `AdvertiserIDCollectionEnabled`), porque un `meta-data` perdido en una fusión de manifiestos no
  avisa y el fallo sería silencioso y en la dirección mala.
- `activate(context)` lo enciende, idempotente, y **sólo** se llama con el consentimiento en la
  mano: en el arranque (`PaparcarApp`, si ya está aceptado) y en `MetaAnalyticsTracker` si llega a
  mitad de sesión.

`MetaAnalyticsTracker` lee `hasAcceptedLegalConsent` **en cada llamada**, no al construirse: la
aceptación ocurre en la pantalla de auth, y un flag cacheado dejaría el carril apagado hasta el
arranque siguiente. Su `setUserProperty` no manda nada (Meta no tiene propiedades) pero **sí
activa**, y eso no es un efecto colateral: es la señal más temprana tras el consentimiento
(`PublishDetectionStateUseCase` la emite durante toda la sesión), y es lo que lleva la atribución de
la instalación a la sesión 1 en vez de a la 2.

### 3 · El manifest

- Vuelven los tres permisos (`AD_ID` + el par de AdServices), **declarados explícitamente** para que
  `aapt2 dump permissions` diga lo que declara el formulario, en vez de depender de lo que fusione
  una dependencia.
- ⛔ `google_analytics_adid_collection_enabled=false`: **Firebase Analytics sigue sin tocar el
  GAID**. Un permiso declarado no es un permiso usado por todos — los eventos de producto siguen
  anclados al app-instance id, como estaban.
- Los tres automatismos del SDK, a `false`.
- ⛔ **Y entran tres permisos, no cinco.** `facebook-core` fusiona **sola** las dos APIs de Privacy
  Sandbox que sirven para segmentar —`ACCESS_ADSERVICES_TOPICS` (intereses inferidos) y
  `ACCESS_ADSERVICES_CUSTOM_AUDIENCE` (remarketing)—, medido en el manifiesto fusionado de
  prodDebug el 02-11. Se retiran: la política dice *«tres nombres de evento y el identificador de
  publicidad»*, y un binario que **puede** más de lo que el documento dice es el mismo defecto de
  siempre con otro disfraz.

### 4 · Lo publicado, que es la mitad cara

- **Política** (`hosting/public/privacy-policy.html`, a mano, las DOS mitades): §2.10 nueva
  *«Medición de publicidad (Meta)»* con 4 puntos, fila nueva en la tabla del §4, el §3 reescrito
  (la promesa absoluta se retira; *«la app no muestra publicidad dentro»* se queda porque **sigue
  siendo cierta**), §2.7 corregido y §5 con la retención. Esqueleto EN/ES en paridad —
  `PrivacyPolicyParityGuardrailTest` lo comprueba.
- **Web**: `trust.points[3].body` en `es.json` y `en.json` nombra a Meta y dice las tres cosas y el
  consentimiento. Reconstruida con `node web/build.mjs`.
- **Data Safety** (`docs/legal/DATA-SAFETY-FORM.md`): se acabó el *«Compartido = No en todo»* —
  Meta no es un *service provider* nuestro ni es funcionalidad que inicie el usuario. **Compartido
  = Sí** en *App interactions* y en *Device or other IDs*; fila del GAID; y la declaración de
  *App content → Advertising ID*, que es un sitio **distinto** del Data Safety y que Play cruza con
  el permiso del manifest.

⛔ **Y la pregunta «¿tu app contiene anuncios?» del Console sigue siendo NO.** Paparcar no muestra
ni un anuncio dentro; lo que hay es medición de los anuncios que ponemos fuera. Confundir las dos
cosas es lo que convierte una respuesta honesta en una infracción.

### 5 · Los dos guards, cada uno en su momento

| Guard | Dónde | Qué impide |
|---|---|---|
| `AdvertisingDisclosureGuardrailTest` | `testAll` | Que el binario declare una red publicitaria y la **política, la web (copy **y** salida construida) o el Data Safety** no la nombren. Y dos invariantes del binario de los que la política depende palabra por palabra: que el GAID no vuelva a borrarse del manifest, y que los tres automatismos sigan en `false` — la promesa *«nada de esto ocurre hasta que aceptas»* **es** ese `AutoInitEnabled=false` |
| `check_listing_names_the_ad_network` | `tools/release/cut.py`, fase `bump` | Que se corte una versión que lleva la red dentro con una ficha que no la nombra en los nueve idiomas. Falsificado en las dos direcciones el 02-11 |
| `check_aab_carries_the_ad_key` | `tools/release/cut.py`, fase `bundle` | Que se suba un AAB que lleva la red dentro **sin su clave**. Es el fallo más caro del ticket porque es el único invisible: la app va igual, no hay crash ni log, y la atribución está muerta — pagas anuncios sin medir ni uno y lo descubres semanas después en el panel de Meta. Basta con construir el corte en una máquina sin la clave, que es lo que hacen CI y un clon limpio **a propósito**. Falsificado en tres direcciones el 02-11 |

⛔ **Son dos y no uno a propósito.** La web describe el producto → se mueve ya. La ficha describe el
build publicado → se mueve en el corte, y ni un día antes: nombrar en la tienda algo que la app
todavía no hace es el defecto contrario [DOCS-THE-STORE-LISTING-IS-A-CONSUMER-TOO-001].

Y lo que el guard de la ficha exige es que la red se **NOMBRE**, no que desaparezca una frase: cada
frase del bloque de PRIVACIDAD puede seguir siendo literalmente cierta con el SDK dentro —no se
muestran anuncios, la ubicación no sale— y aun así el bloque entero se lee como *«nada tuyo se usa
para publicidad»*. Una prohibición de frases se esquiva reescribiéndolas.

## Qué cambió respecto al WIP `68d87dfc8`

| | WIP | Ahora | Por qué |
|---|---|---|---|
| Eventos a Meta | los **6** `AnalyticsEvent` con todos sus params (`detection_path`, `arm_evidence`, `trigger`, `disposition`) | 3 nombres, **cero** params | mecánica de detección hacia un anunciante; `det_trigger_fired` es además el evento más frecuente de la app |
| Interfaz | `PlatformAnalyticsTracker` (marcador «específico de plataforma») | `AdAttributionTracker` | lo que lo distingue no es dónde corre, es a **dónde escribe** |
| Consentimiento | ninguno: `activateApp` en `onCreate` | puerta legal existente, leída en cada llamada | mandaba datos a un tercero con fin publicitario antes de que el usuario aceptara nada |
| GAID | los tres `tools:node="remove"` intactos | permisos de vuelta, y sólo para el carril de Meta | el SDK no podía atribuir **nada**: el objetivo del ticket no se cumplía |
| Dependencia | `api` en `:shared` **y** `implementation` en `:app` | `implementation` sólo en `:shared` | el único sitio del repo que nombra a Facebook es `data/analytics/Meta*.kt` |
| 4 subidas ajenas | `datastore 1.3.0`, `firebase-bom 35.1.0`, `purchases-kmp 3.15.0`, `navigation-compose 2.10.0` | **revertidas** | un commit que dice «SDK de Meta» y mueve el BOM de Firebase no se revierte limpio. Y como los merges son `--squash`, un commit aparte en esta rama **no sobreviviría**: la única forma de separarlas es sacarlas. Entran en el próximo barrido de dependencias, con su protocolo |
| Lo publicado | nada | política, web, Data Safety, dos guards | el WIP habría dejado el binario mandando datos a Meta con tres superficies prometiendo lo contrario |

## El lado iOS — el hueco, escrito

Este carril es **Android y nada más**, y no por descuido: `facebook-core` es un artefacto de
Android. Lo que hay que saber es que **no deja nada roto detrás**, y está medido, no supuesto:

- `IosPlatformModule.kt` **no ata** `AdAttributionTracker`, y no tiene que atarlo: `DataModule` lo
  pide con `getOrNull`, así que en iOS la lista del compuesto se queda en Firebase sola y nadie
  tiene que acordarse de nada. `:shared:compileKotlinIosSimulatorArm64` —hoja de `testAll`— verde.
- Todo lo caro de este ticket **ya es común**: el vocabulario cerrado y `asAdAttributionEvent()`
  viven en `commonMain` con su test en `commonTest`, que
  [corre también en iOS](test-a-kmp-suite-that-only-runs-on-jvm-is-half-a-suite-001.md). Cuando iOS tenga el carril, lo único
  que hay que escribir es el sink.

⛔ **Y cuando se escriba, el consentimiento NO es el de aquí.** En iOS el identificador de
publicidad (IDFA) lo gobierna **App Tracking Transparency**: hace falta un diálogo del sistema
(`NSUserTrackingUsageDescription` + `ATTrackingManager`) que es una **segunda** puerta, más
estricta que `hasAcceptedLegalConsent`, con su propio texto en los nueve idiomas y que el usuario
puede denegar aunque haya aceptado la política. Reutilizar la puerta de Android ahí sería un
rechazo de App Store, no un matiz. Es el mismo patrón que
[IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001]: una plataforma no hereda la mecánica de la otra porque el
nombre del carril coincida.

Así que el hueco de iOS **no es «copiar el tracker»**: es ATT + su copy + decidir qué hace el
carril cuando el usuario deniega el seguimiento (la respuesta por defecto: nada, silencio total, y
la app igual de útil). Ticket propio cuando iOS llegue a anunciarse; hoy iOS no está publicada.

## Lo que NO entra aquí

1. **La ficha de Play.** No se mueve hasta el corte de la versión que lleve el SDK: hoy la viva
   (`LISTING-v1.5.0.md`, que nombra `published-listing.txt`) describe la 1.5.0, que **no** lo lleva,
   y todo lo que dice sigue siendo cierto. Lo fuerza `cut.py`.
   ⚠️ **Y hay que condensar ANTES de escribir**: margen medido el 02-11 sobre la ficha viva —
   **fr-FR 9 libres de 4000**, de-DE 18, nl-NL 18, it-IT 48. Nombrar a Meta en el bloque de
   PRIVACIDAD cuesta del orden de 70 caracteres, así que en cuatro idiomas **no cabe** sin
   condensar algo que ya se dice dos veces. Nunca recortando una promesa ni el bloque de ubicación
   en segundo plano, que Play exige.
2. **Pegar las claves.** Los huecos ya están escritos en `local.properties` (de los dos
   worktrees), con su procedencia al lado. De dónde sale cada una, en
   `developers.facebook.com` → *My Apps* → la app:

   | Clave | Dónde | Forma |
   |---|---|---|
   | `FACEBOOK_APP_ID` | *Settings → Basic*, campo **App ID** (también en la cabecera del panel) | sólo dígitos, 15-16 |
   | `FACEBOOK_CLIENT_TOKEN` | *Settings → Advanced → Security*, campo **Client token** | 32 hex |

   ⚠️ **Y antes de que lleguen eventos, la app de Meta necesita la plataforma Android declarada**:
   *Settings → Basic → Add Platform → Android*, con **Package Name**
   `com.rndeveloper.paparcar` y *Google Play Package Name* el mismo. El **key hash** es de Login,
   no de App Events: se puede dejar vacío, y si algún día se pide, el SHA-1 que vale es el de
   **firma de app** del Play Console (no el de subida que pina `cut.py` — Play App Signing
   refirma).

   ⛔ **No son secretos** —viajan en el APK, igual que las de Google— pero el *client token*
   autoriza llamadas a la Graph API en nombre de la app, así que vive fuera del repo como el
   resto.

   🟢 **El cableado está verificado de punta a punta** (02-11), no supuesto: con valores de prueba
   en `local.properties` llegan a `BuildConfig.FACEBOOK_*` **y** al manifiesto **fusionado** de
   prodDebug (`com.facebook.sdk.ApplicationId` / `ClientToken`); al vaciarlas, los dos vuelven a
   `""`. Sin ellas el carril no arranca y la app funciona igual — que es lo que hace que un clon
   limpio y el CI no manden eventos de prueba a la cuenta de anuncios.
3. **El Console.** La declaración de *Advertising ID*, el Data Safety rehecho y la ficha nueva son
   pasos del user.

## Criterio de éxito

- [x] `./gradlew testAll` verde, con `--rerun-tasks` (los dos guards nuevos no tocan bytecode).
      **02-11.**
- [x] Los dos guards **falsificados**: sabotear la política (quitarle «Meta») y poner
      `AutoInitEnabled` a `true` ponen el test en rojo; una ficha sin la red en los nueve idiomas
      para `cut.py`, y nombrándola pasa. **02-11.**
- [x] Las claves llegan de `local.properties` a `BuildConfig` y al manifiesto **fusionado**, y en
      blanco dejan el carril muerto. **02-11, con las claves reales del user.**
- [x] El gate del AAB mide el ARTEFACTO, no el input: el valor de `FACEBOOK_APP_ID` aparece como
      bytes UTF-8 dentro de `base/manifest/AndroidManifest.xml` del AAB (comprobado sobre un
      `bundleProdDebug` real). Falla sin clave configurada **y** con una clave que no esté en el
      AAB — el segundo caso es el que distingue «medido» de «le creo al input». **02-11.**
- [ ] En un device con las claves puestas: **antes** de aceptar el consentimiento, `logcat` no
      enseña ni un evento de Meta y el *Events Manager* no recibe nada. Después de aceptar, llega
      el evento de sesión y, al aparcar, `parking_confirmed`.
- [x] El manifiesto **fusionado** de prodDebug (`--rerun-tasks`, no incremental
      [BUILD-DEPS-AT-LATEST-STABLE-002]) enseña `AD_ID` y `ACCESS_ADSERVICES_{AD_ID,ATTRIBUTION}`,
      y **no** enseña `ACCESS_ADSERVICES_{TOPICS,CUSTOM_AUDIENCE}`. **02-11.**
- [ ] Lo mismo con `aapt2 dump permissions` sobre el **AAB de release** del corte, que es el que
      Play lee. (La clave sí queda cubierta: la mide `check_aab_carries_the_ad_key` en cada corte.)
- [ ] `firebase deploy --only hosting` y **leer `paparcar.com/privacy-policy` del dominio**, no el
      «Deploy complete»: §2.10 presente en las dos mitades.
      ⛔ **Después del merge, nunca desde este worktree**: Hosting publica el `hosting/public` del
      árbol desde el que se lanza, así que desplegar la rama dejaría el dominio **por delante de
      master** y el siguiente deploy desde master revertiría la política sin que nada chille.
- [ ] Play Console: Data Safety rehecho, *Advertising ID* declarado, *«contiene anuncios»* = No.

## Relacionado

- [`mkt-an-install-must-say-where-it-came-from-001.md`](mkt-an-install-must-say-where-it-came-from-001.md)
  — la atribución por install referrer, que sigue viva y es complementaria: contesta *de qué
  campaña* viene una instalación, pero no deja que Meta **optimice** por ella.
- [`analytics-events-001.md`](analytics-events-001.md) — el sistema del que cuelga este carril, y de
  donde sale el «la ubicación es una celda, nunca una coordenada» que aquí se endurece a «ni celda».
