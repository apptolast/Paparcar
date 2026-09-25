# Paparcar — CLAUDE.md

## Cómo trabajamos — skills del proyecto (`.claude/skills/`)
Antes de improvisar un flujo, invocar la skill que lo cubre:
- **`nuevo-ticket`** — al empezar CUALQUIER tarea de código, al aparcar trabajo, o al mergear una
  rama. Worktree aislado + `docs/backlog/<id>.md` + rama; cierre con rebase/squash y limpieza.
- **`det-change`** — al tocar detección (estrategias, `Evaluate*UseCase`, config, workers,
  geofence, mappers `UserParking`, guards DET-/LOC-/PARKING-/MAPPER-*).
- **`field-test`** — ante un viaje real, un FP/FN o una petición de mirar diagnósticos.
- **`rebase-rama`** — al poner una rama al día sobre master ("rebasea", "¿qué ramas están
  desfasadas?", o ff-only fallido al cerrar). Inventario + elección + rebase desde SU worktree.
- **`instalar-apk`** — es lo que significa **`/run`** aquí: `assembleProdDebug` + `install -r` en
  TODOS los móviles conectados + verificación de sha256 en device + arranque. Nunca desinstalar.

Reglas transversales que ninguna skill sustituye: **nunca commitear ni mergear sin permiso
explícito de este turno**; **worktree nuevo por tarea** (las ramas NO aíslan el árbol);
**sistemas, no parches** — el invariante se arregla en UN sitio y se barren todos sus consumidores.

## Proyecto
Paparcar es una app KMP (Kotlin Multiplatform) de compartición de plazas de aparcamiento en tiempo real basada en comunidad. Android es la plataforma principal; iOS es target futuro. Cuando un usuario sale con el coche, la app detecta automáticamente el evento y publica la plaza recién liberada para que otros usuarios cercanos puedan encontrarla.

## Stack
> Fuente de verdad: `gradle/libs.versions.toml`. Todas las versiones de abajo re-verificadas contra
> el catálogo y el wrapper el **2026-09-15** ([BUILD-DEPS-AT-LATEST-STABLE-002]) — **doce** subieron:
> once que habían derivado respecto a su índice upstream, más `kmp-maps`, que no sale de ningún
> índice porque es nuestro fork. El resto ya estaba en su última estable.
> Solo estables en el catálogo: ni un alpha/beta/rc. Al comprobar si algo se ha quedado atrás,
> consultar `repo1.maven.org/.../maven-metadata.xml` y `dl.google.com/dl/android/maven2/.../group-index.xml`
> — el índice de `search.maven.org/solrsearch` está obsoleto y miente por versiones enteras.
> ⚠️ `kotlinx-datetime` publica una línea `0.8.0-0.6.x-compat` que **ordena después** de `0.8.0` sin
> ser más nueva: es la variante de compatibilidad con la API 0.6. No leerla como un bump pendiente.
- Lenguaje: Kotlin 2.4.20 (KSP 2.3.12) · Gradle 9.7.1 (**es la actual**, no hay 9.8)
- Build: AGP 9.4.0 · compileSdk 37 · targetSdk 37 (Android 17) · minSdk 26
- UI: Compose Multiplatform 1.12.0 · Material3 (JB) 1.9.0 (**no hay estable por encima**) · Navigation Compose 2.9.2
- Arquitectura: Clean Architecture + MVI (State + Intent + Effect)
- DI: Koin 4.2.2
- DB local: Room KMP 2.8.5 (SQLite bundled 2.7.1)
- Backend: Firebase (GitLive KMP SDK 2.7.0) · firebase-bom 34.19.0
- Auth: BaseLogin 3.0.0 (librería propia, **Maven Central** `io.github.apptolast:baselogin` desde
  2.0.0; JitPack quedó atrás) — ⛔ no se toca desde Paparcar
- Async: Coroutines 1.11.0 + Flow · Serialization 1.11.0 · Datetime 0.8.0
- Mapas: kmp-maps **fork propio** `io.github.rndevelo.kmpmaps:core:0.9.1-puck6`, ya en Maven Central
  (marker de id estable; upstream SW Mansion 0.9.1 + PR #170 sin mergear) — su versión la manda
  nuestro repo, no upstream
- Imágenes: Coil 3.6.2 + Ktor 3.5.2 (motor de red)
- Monetización: RevenueCat `purchases-kmp` 3.8.0 — ⛔ **no volver a declarar el artefacto Android
  `purchases` a mano**: 3.8.0 ya pina 10.20.0, la versión con las consumer rules de R8 estrechadas,
  así que el puente de [R8-REVENUECAT-ALREADY-NARROWED-ITS-RULES-001] murió en
  [BUILD-DEPS-AT-LATEST-STABLE-002]
- Logging: Napier 2.7.1 · Monitoring: Firebase Crashlytics
- ⛔ **El stack no es sólo Gradle**: hay DOS raíces npm con su propio lockfile —`functions/` (Cloud
  Functions, se despliega) y `firestore-rules-tests/` (devDeps del harness de reglas)— y
  [BUILD-DEPS-AT-LATEST-STABLE-002] nunca las miró. Entran en el mismo barrido
  [DEPS-THE-NPM-SIDE-WAS-NEVER-SWEPT-001]. Tres trampas medidas ahí: (a) en un salto de major el
  lockfile **se regenera**, no se actualiza —con el viejo puesto, `npm install` muere en `ERESOLVE`
  anclado a la versión vieja—; (b) `npm audit fix --force` propone **bajar** `firebase-tools` cinco
  majors, que no es un arreglo; (c) un `override` sobre una transitiva **se prueba, no se razona**:
  `stream-json@3.5.0` reorganizó sus ficheros y dejó `firebase deploy` muerto mientras
  `firebase --version` seguía contestando. Verificar SIEMPRE los comandos que usamos
  (`firebase deploy --help`, `emulators:exec --help`), no sólo que los tests pasen.

> El repo **no tiene `gradlew.bat`**: compilar SIEMPRE con la herramienta Bash (`./gradlew …`).
> En PowerShell `.\gradlew` sale exit 0 **sin compilar nada**.
>
> ⛔ **La suite es `./gradlew testAll`**, no `:shared:testDebugUnitTest`. Ese último deja fuera los
> tests de `:app` — y ahí vive el único guardarraíl que comprueba que el grafo del flavor mock se
> puede resolver (`MockKoinModuleVerifyTest`). Dos tickets seguidos metieron un repositorio en
> `dataModule` sin declararlo en `mockModule`, el guard los habría cazado en segundos, y nadie lo
> ejecutó. [TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001]

## Estructura
Dos módulos Gradle desde ARCH-HEALTH F7 (paquete: `com.rndeveloper.paparcar`):
```
:shared  → KMP, TODA la lógica de producto
  commonMain/  → domain/, data/, presentation/, di/, core/
  androidMain/ → detection/, location/, bluetooth/, notification/, worker/, geofence/
  iosMain/     → (futuro) CLLocation, CMMotion, CoreBluetooth, BGTask wrappers
  commonTest/  → el GRUESO de los tests (191 ficheros): use cases, evaluadores, ViewModels,
                 mappers, y el replay de 16 trazas de campo reales
  androidUnitTest/ → solo lo que necesita JVM/Android (16): guardarraíles Konsist, Robolectric,
                 workers, paridad de deserializadores Firestore
  Ambos corren con `:shared:testDebugUnitTest`
:app     → shell Android: MainActivity, PaparcarApp, AppNotificationManagerImpl,
           manifest, res, flavors prod/mock (Dev Catalog en app/src/mock/), firma.
           BuildConfig vive aquí: shared lee build facts vía AppBuildInfo/isDebugBuild.
:mockkit → TODO lo falso: los 19 fakes, MockScenario, ShotPresets, iosMockModule y
           MockMainViewController. Depende de :shared y NUNCA al revés — Gradle rechaza el ciclo,
           así que producción no puede verlo ni por descuido. Lo consumen el flavor mock
           (`mockImplementation`), los tests de :shared, y el framework de capturas de iOS.
           [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
```

## Arquitectura
- Domain layer es Kotlin puro — sin imports de Android/iOS
- Todo UseCase retorna `Flow<T>` (observables) o `Result<T>` (stdlib, operaciones one-shot); los evaluadores puros y síncronos pueden retornar un value object de dominio
- ViewModels usan MVI: sealed class State, Intent, Effect
- Repositorios exponen interfaces en domain/, implementación en data/
- Persistencia dual: Room (offline-first local) + Firestore (sync real-time)

## Detección de aparcamiento — Dual Strategy

> Detalle vivo: `docs/detection/PARKING-DETECTION.md` (log cronológico de cada guard y por qué
> existe) + tickets `docs/backlog/det-*.md`. Antes de tocar nada, skill **`det-change`**.

**Doctrina rectora** (violarla es un bug):
- *El evento NOMINA, solo el movimiento MEDIDO confirma.* Un EXIT de geocerca o un AR ENTER solo
  despiertan/arman; ninguno confirma una plaza por sí mismo — hace falta conducción medida en el
  stream (o pasos/egress inambiguos). Un evento re-entregado (Doze/OEM) nunca coloca un pin.
- *Fallo asimétrico: mejor falso negativo que falso positivo.* Ante la duda se PREGUNTA (nudge /
  prompt), nunca se planta una plaza fantasma. La fiabilidad se estampa en cada sesión.
- *Todo trigger dispara SIEMPRE*, aunque llegue tarde, con verificación tardía. Un evento viejo
  pierde autoridad directa (pasa al evaluador), nunca se descarta.

Dos estrategias independientes que **NUNCA se mezclan** — no metas señales BT en el scoring del
Coordinator:

- **BluetoothDetectionStrategy** (determinista, nivel "automático"): BT disconnect del MAC
  emparejado → fix GPS → alejarse ≥30 m → confirma. Ligada a la MAC, no al modelo. Sin scoring ni
  Activity Recognition.
- **CoordinatorDetectionStrategy** (probabilístico, el "asistido"): arma con AR IN_VEHICLE ENTER
  (AR-first, `getForegroundService`) o GEOFENCE_EXIT — la escalera `EvaluateArEnterArmUseCase` solo
  arma si el embarque está atado al PROPIO coche (bus/taxi no arman). Confirma vía
  `EvaluateParkingDecisionUseCase` (pasos+egress · egress cinemático por GPS · vehicle-exit+ventana),
  todas exigiendo conducción medida; scoring HIGH por sí solo NO auto-confirma. El ancla se BLOQUEA
  con pasos de egress o se CONGELA al final de la conducción, para que la caminata no arrastre el
  pin. Red de seguridad: `ParkingSafetyNetWorker` + `EvaluateSafetyNetCheckUseCase` (worker 15 min +
  sensor de movimiento) reconcilian salidas que el OS no entregó; nunca liberan por distancia sola.

`ParkingStrategyResolver.resolve()` / `strategyFor(vehicles)`: BT solo si hay coche emparejado
**Y conectado ahora mismo** (ACL viva, [DET-BT-CONNECTED-NOT-PAIRED-001]); si no → Coordinator
(o NONE si el primario no aparca en plaza). ⛔ En iOS el carril BT es estructuralmente imposible
(sin eventos de BT clásico para terceros): el resolver cae SIEMPRE a Coordinator y ninguna
superficie promete «Automático» allí [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001].
Ambas convergen en **ConfirmParkingUseCase** → Room + Firestore + Geofence + Notification +
WorkManager. `CoordinatorDetectionService` serializa todos los triggers en un intake único
[DET-INTAKE-001] y solo hace I/O + side-effects: **la DECISIÓN vive en use cases puros de
commonMain**. Todo pin persiste su `detectionPath` + `armEvidence` (provenance).

⛔ **Carriles separados ≠ servicios separados** [DET-ONE-SERVICE-TWO-LANES-001, 15-09]: desde
`d7880ac7` hay **UN solo FGS y una sola notificación** — `BluetoothDetectionService` está borrado y
el carril BT entero (caza del disconnect, trabajo del connect, vigilancia de salida, grabador) entra
por el MISMO intake, en slots de job propios (`btHuntJob`, `btTrackingJob`). La doctrina de no
mezclar sigue INTACTA y ahora se lee así: *carriles separados = slots + use cases separados, mismo
host*. Lo prohibido no ha cambiado: **cero señales BT dentro del scoring del Coordinator**.

---

## REGLAS DE CÓDIGO OBLIGATORIAS

### ⛔ Iconos — sistema de 3 niveles
Regla mental: *plumbing de UI → Material; concepto de Paparcar → vector propio.*
- **Nivel 1 · Sistema** → Material Symbols **Rounded** (no Outlined, para casar con Outfit): nav,
  ajustes, buscar, cerrar/atrás, editar, chevron, calendario, filtros, capas. `tint = onSurfaceVariant`.
- **Nivel 2 · Iconos de UI** → Material Symbols Rounded con `tint`. Incluye POI/categorías. NO
  creamos glifos custom. El mapeo `PlaceCategory → Icons.Rounded.*` vive en presentación (domain
  es Kotlin puro, sin `Icons`).
- **Nivel 3 · Ilustración/marcadores** → vector propio, multicolor, **NO tintar**: hero, onboarding,
  empty states, marcadores, vehículos, fiabilidad. SVG VectorDrawable-compatible (solo `path`) →
  `composeResources/drawable/` con variante `_dark`. Si usa `stroke-dasharray`, nested-svg, filtros
  o texto → **Compose Canvas** en commonMain (VectorDrawable NO soporta trazos discontinuos).
- Nivel 1/2 se tintan con el tema; Nivel 3 trae su color.
- **Compensación Óptica del Logo**: para que el logo se perciba igual en cualquier fondo,
  el grosor de trazo de las ruedas varía según el contraste (irradiación):
    - **Claro sobre Oscuro (Base)**: grosor **`6`** (fiel al SVG).
    - **Oscuro sobre Claro (Compensado)**: grosor **`7`** (para evitar que el fondo se lo coma).

### ⛔ Tipografía — tres voces y un rol que manda [UI-TYPE-TWO-VOICES-ONE-ROW-001]
Familia, tamaño **y peso** son propiedad del **ROL**, no del widget. Nunca elijas fuente, tamaño ni
peso: elige rol. Fuente de verdad: `ui/theme/PaparcarType.kt` (**22 roles**), se lee
`PaparcarType.current.<rol>`. **Solo `color` se decide en el call site** (lo exige la doctrina de
color: el estado se escribe, no se tiñe).
> Una VOZ es una pregunta, no una fuente. Con qué letra se pinta cada voz lo dice `PapFontSet`, y
> desde `UI-TYPE-RETIRE-THE-OLD-FAMILIES-001` las tres apuntan a **Plus Jakarta Sans**. Eso no las
> fusiona: siguen decidiendo tamaño, peso y qué es un nombre, una cifra o prosa. Nombrar la voz por
> su fuente (*"MARCA · Outfit"*) es lo que hacía elegir rol por la letra que uno recordaba.
- **MARCA** — nombres de cosas reales y títulos: `screenTitle`, `heroTitle`, `sectionTitle`,
  `cardTitle`, `rowName` (el NOMBRE en una fila: esta plaza, este coche, este lugar)
- **LECTURA** — todo lo que se lee o se pulsa: `sectionHeader` + `subsectionHeader`
  (**ambos SOLO vía `PapSectionHeader`**; el sub es el separador de un grupo DENTRO de una sección ya
  encabezada — los días del historial — y se pide con `dense = true`), `eyebrow` (la línea en caps
  que cualifica el título de justo debajo), `cta`, `rowTitle` (título ESTRUCTURAL de fila: cabecera
  de la superficie de detección, paso de onboarding, estado vacío, fila de Ajustes), `subtitle`(16sp),
  `body`, `label`, `caption`, `meta` (la meta-line bajo un título), `badge` (`FIABLE`,
  `SIN CONFIRMAR`, `ACTIVO`, `MEDIANO`, `3 en camino`)
- **CIFRA** — una cifra que es el SUJETO DE SU PROPIO BLOQUE, nunca dentro de una línea de texto:
  `statNumber`(25sp) + `statLabel`, `counter` + `counterUnit`, `chartLabel`, `chartValue`
- Regla mental, tres preguntas con una sola respuesta: *¿es un nombre propio o un título? → MARCA.
  ¿es una cifra que protagoniza su bloque? → CIFRA. ¿todo lo demás? → LECTURA.*
- **Una cifra alineada en COLUMNA no necesita cambiar de voz**: la columna la marcan la posición y el
  peso. Medido en device el 29-08 — por eso la distancia de la fila de plaza es LECTURA y CIFRA ya no
  aparece en ninguna FILA. [PEEK-META-INTER-001]
- ⛔ **No hay corte condensado que reservar para CIFRA**: leído del `fvar` de
  `plus_jakarta_sans_variable.ttf`, **un solo eje `wght` 200–800, sin `wdth`**. La misma comprobación
  ya lo descartó con el set anterior (Outfit estática; Inter `opsz`+`wght` sin `wdth`; Inter Tight es
  espaciado, no anchura). CIFRA se distingue por tamaño, peso y caja recortada. No volver a proponerlo.
- ⛔ **`fontFeatureSettings` (p. ej. `tnum`) NO se aplica** en Compose Multiplatform 1.12 aunque la
  fuente declare la feature. No apoyar ninguna decisión en cifras tabulares.
- **PROHIBIDO** en `presentation/` y `ui/components/`: (a) `fontSize`/`letterSpacing` inline;
  (b) **`fontWeight`/`titleWeight`** — el rol trae su peso; si hacen falta dos pesos a un tamaño, son
  dos roles. El peso tampoco marca selección: eso lo dicen el color, el borde o el check;
  (c) `MaterialTheme.typography.*` — usa un rol, y si falta un tamaño añade/ajusta el rol;
  (d) construir familias (`rememberXxxFontFamily()`/`FontFamily(...)`) fuera de `ui/theme`.
  Enforced por `TypographyGuardrailTest` (Konsist). Allowlist: canvas/`TextMeasurer` de marcadores
  de mapa + `AppBottomNavigation`. **Y nada más**: el banner salió al descubrirse que la exención era
  lo que le permitía no tener familia, y la action bar salió porque eximía a un componente MUERTO
  — una excepción sobre código que no se renderiza no es una excepción, es un agujero.
  [UI-TYPE-SYSTEM-HYGIENE-001]

### ⛔ Color — identidad por MÉTODO, estado en texto [UI-COLOR-DOCTRINE-001]
> Significado y tabla completa de tokens: **`docs/design/COLOR-SYSTEM.md`**. Valores en
> `ui/theme/Color.kt`. Todo token nuevo exige su fila (con historia única) en el doc.

*La app es VERDE (marca). El color del NOMBRE de un coche dice CÓMO se le vigila. El estado
(aparcado / en ruta / sin aparcar) se ESCRIBE en `onSurface` y se anima — nunca se tiñe.*

- 🟢 verde = marca y acción (CTA, links, nav, spinners) + identidad de vehículo con detección activa
- 🔵 `papCarBlue` = vehículo vigilado por Bluetooth · `PapLiveMap` = movimiento sobre el mapa que
  **NO es tuyo** (la plaza a la que va otro). Tu viaje —traza, origen, salida, halo del puck, FAB
  que lo sigue— lleva la identidad de vigilancia de SU coche [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001]
- ⚪ gris = vehículo sin vigilancia · ⬛ estado siempre en `onSurface` (animado, nunca teñido)
- 🟢🟡🔴+🔵 spots: rampa de frescura **exclusiva** de la caducidad de plazas. `PapRed` NUNCA en un CTA
- 🚗 multicolor = QUÉ coche es (glifo ilustrado), jamás mezclado con estado
- El **glifo** (y borde/badge/punto) lleva el color; el nombre queda en `onSurface`. Excepción: el
  eyebrow del peek, donde no hay glifo.
- **Un solo resolver**: `ui/theme/VehicleIdentity.kt` → `vehicleIdentityColor(watch)`.
- **PROHIBIDO** en `presentation/` y `ui/components/`: (a) `colorScheme.tertiary` (retirado; zona
  privada = outline + candado); (b) teñir el estado del vehículo; (c) `Color(0x…)` literal.
  Enforced por `ColorGuardrailTest` (Konsist).

### ⛔ Strings — NUNCA hardcoded
- Todo texto visible va en `composeResources/values/strings.xml` · `stringResource(Res.string.key)`
- Key en inglés, convención `feature_component_description` (`home_fab_report_spot`)
- **Todo string nuevo se añade a los 9 locales en la MISMA tarea**: `values` (EN base), `values-es`,
  `-it`, `-pt`, `-fr`, `-de`, `-nl`, `-pl`, `-ro`. Si la traducción no está clara, poner el texto
  inglés antes que omitir la key. Enforced por `LocaleParityGuardrailTest`.
- ⚠️ **Faltar en un locale NO crashea, y por eso es peor** [I18N-PERMISSIONS-BUTTONS-EXIST-IN-ONE-LOCALE-ONLY-001].
  Medido en `ResourceEnvironment.kt:182-195` de CMP 1.12: si ningún item lleva el idioma pedido,
  `filterByLocale` devuelve el item **sin qualifier**. Dos fallos distintos, uno mudo:
  (a) falta en una traducción y está en `values` → **sale en inglés, en silencio** (así vivieron 48
  días dos botones de la pantalla de permisos); (b) falta en `values` → resolución vacía →
  `error("Resource with ID='…' not found")` = **crash** en todo locale que no la declare. La base
  `values` es la que sostiene a las otras ocho: es la única que no puede faltar nunca.
- **Apóstrofos CRUDOS (`'`), nunca `\'`** [COPY-APOSTROPHE-IS-NOT-ESCAPED-001]. Esto es Compose
  Resources, no `android:strings`: **no desescapa `\'`** y el usuario lee la barra
  («Paparcar\'s»). Medido en el `.cvr` del APK: `\n` sí se desescapa y el apóstrofo crudo sale
  limpio; `\'` es el único roto. Muerde en EN (contracciones) y FR/IT (elisiones).
  ⛔ **Las dos reglas de abajo las vigila `ComposeResourcesEscapeGuardrailTest`**, y existe porque sin
  él la regla ya se rompió una vez: se barrió a cero, se escribió aquí, y volvieron a entrar seis por
  el espejo de notificaciones —copiar de `app/src/main/res`, donde el escape es OBLIGATORIO, sin
  desescapar—. `NotificationCopyParityTest` no las ve: normaliza `\'` en los dos lados, que para su
  pregunta es lo correcto. [COPY-AN-APOSTROPHE-GUARD-THAT-NEVER-EXISTED-001]
- **Un `%` literal se escribe `%`, nunca `%%`** [COPY-PERCENT-IS-NOT-ESCAPED-001]. Misma familia
  que lo anterior y misma causa: el formateo de Compose Resources **no es `String.format`**. Leído
  en la fuente de `components-resources` 1.12 (`StringResourcesUtils.kt`), es un solo
  `Regex("""%(\d+)\$[ds]""").replace(...)` — sustituye `%1$s` / `%1$d` y **no toca nada más**. O sea:
  `%%` sale impreso como `%%` (así salió «−41%%» en el badge de ahorro), y un `%` suelto es seguro,
  no puede petar porque no hay `Formatter` que valide. Solo existen `$s` y `$d`: `%1$f` no se
  sustituye — formatea el número en Kotlin y pásalo como `$s`.

### ⛔ Vocabulario: PLAZA es de la comunidad, APARCAMIENTO es tuyo [COPY-SPOT-IS-NOT-A-PARKING-001]
Los dos conceptos centrales del producto se llamaban igual, y en el mismo flujo: la acción de
registrar dónde has dejado tu coche se llamaba "Mark parking" en el chip, "Marcar mi sitio" en la
detección y "Marcar mi **plaza**" en el nudge — usando para lo TUYO la palabra de lo AJENO.

| Concepto | EN | ES | IT | PT | FR | DE | NL | PL | RO |
|---|---|---|---|---|---|---|---|---|---|
| Plaza libre de la comunidad (la que ves, avisas o se publica al irte) | spot | plaza | posto | lugar | place | Platz | plek | miejsce | loc |
| Tu sesión: dónde has dejado el coche | parking | aparcamiento | parcheggio | estacionamento | stationnement | Parkplatz | parkeerplaats | parkowanie | parcare |
| Instalación de un tercero en la que entras a aparcar | **car park** | parking | parcheggio pubblico | parque de estacionamento | parking | Parkhaus | parkeergarage | parking | parcare publică |

- Verbo de lo tuyo: *"Marcar aparcamiento" / "Mark parking"*. Verbo de lo comunitario: *"Avisar de
  una plaza" / "Report a free spot"*. Nunca "marcar mi plaza".
- El pin que pones y la plaza que se publica **son dos cosas**: al irte, tu APARCAMIENTO libera una
  PLAZA. Cuando una frase habla de las dos (`home_det_ask_sub`), tiene que nombrarlas distinto.
- "spot" como "sitio" genérico en inglés (*"drag to the correct spot"*) no vale: es `location`.
- ⛔ **El parking público NO es "parking" en inglés** — esa palabra ya es TU sesión (*"Mark parking"*),
  así que la instalación es **car park** [PARKINGS-ON-THE-MAP-001]. En ES/FR/PL/DE/NL/PT la lengua ya
  tiene dos palabras distintas; en IT y RO, donde chocarían, el público lleva su calificador
  (*parcheggio pubblico*, *parcare publică*). Y no es una **plaza**: una plaza la libera la comunidad
  en la calle, un parking público lo explota alguien y se entra con el coche.

### ⛔ La web es un consumidor más de la verdad del producto [WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001]
`paparcar.com` se genera con `node web/build.mjs` desde `web/` hacia `hosting/public`, y **se
despliega con `firebase deploy --only hosting`**. No es material de marketing aparte: es la URL que
enlaza la ficha de Play, y dice cosas que viven en este repo. Un cambio de producto que no llegue
ahí deja la web prometiendo lo de antes, en dos idiomas, para siempre.

**Al cambiar cualquiera de estas cosas, la web entra en la MISMA tarea** (detalle y checklist:
[`web/README.md`](web/README.md)):

| Si tocas… | La web… |
|---|---|
| un límite del plan free (`FREE_*` de `domain/entitlement/`) | `FREE_LIMITS` en `web/lib/config.mjs` — el copy **no** escribe el número, escribe `{{historyDays}}` · `{{freeVehicles}}` · `{{freeZones}}` |
| `applicationId` · `versionName` · `minSdk` · `PRIVACY_POLICY_URL` · el correo de soporte | los hechos de `web/lib/config.mjs` |
| una feature que la web nombra (plazas, parkings, gasolineras, Plus, coche compartido, detección) | `web/content/{es,en}.json` — **los dos**, en la misma tarea |
| la política de privacidad o el borrado de cuenta | `hosting/public/{privacy-policy,delete-account}.html` **a mano** (⛔ no son salida del build) |
| el logo, un color o un rol de tipografía | `web/lib/icons.mjs` · los tokens de `web/assets/css/site.css` |
| la UI que sale en las capturas | regenerar con `web/tools/prepare-assets.py` desde los raws de la sesión de Play |

- ⛔ **Editar el copy y no reconstruir es desplegar lo anterior.** `node web/build.mjs --check`
  antes de dar nada por hecho.
- ⛔ **Lo mecánicamente comprobable lo vigila `WebSiteParityGuardrailTest`** (5 chequeos, los cinco
  falsificados): hechos vs. su fuente, paridad de claves ES/EN, plan free vs. los gates, los tokens
  en uso, y la salida construida vs. el copy. Como no toca bytecode, sale **UP-TO-DATE** →
  `--rerun-tasks`. Lo que NO vigila —que el copy sea bueno, que una feature nueva tenga sección—
  es esta tabla.

### ⛔ Un caso de uso por VEREDICTO, nunca por PREDICADO [DET-VERDICT-NOT-PREDICATE-001]
Regla mental: *si su resultado no se puede citar en un diagnóstico, no es un caso de uso.*
- Es **veredicto** si su resultado aparece en el vocabulario de diagnóstico (`detectionPath`,
  `outcome`, `armEvidence`, `sessionOutcome`) o cambia lo que ve el usuario → **caso de uso propio**
  en `domain/usecase/<área>/`, con su test unitario.
- Es **predicado** si sólo alimenta a un veredicto → vive **dentro** de ese veredicto. Si lo comparten
  2+ veredictos → función pura de nivel superior en `domain/detection/` (patrón ya establecido:
  `SentryWakeCooldown.kt`, `SentryLifecycleDecision.kt`, `VehicleFenceOwnershipPolicy.kt`,
  `HumanPoweredRide.kt`). Sigue siendo directamente testeable, sin ceremonia de clase inyectada.
- **Un predicado NO se queda como método privado del coordinator sólo porque estuvieras editando ese
  fichero.** Ese reflejo produjo a la vez ~45 casos de uso y un `CoordinatorParkingDetector` de 2.600
  líneas con 11 predicados puros dentro — no son dos problemas opuestos, son el mismo.
  **Medido el 15-09 (`d7880ac7`): el detector bajó a 1.466 líneas y 4 privadas, `domain/detection/`
  tiene 95 ficheros de funciones puras, y los casos de uso son 57** — la cifra que importa no es
  cuántos hay, es que cada uno sea citable en un diagnóstico. La clase gorda de hoy es
  `CoordinatorDetectionService` (2.713 líneas, I/O del intake): auditarla es lo que queda de
  [DET-VERDICT-NOT-PREDICATE-001].
- **Arreglar un bug no justifica un caso de uso nuevo.** Lo normal es añadir una línea a un evaluador
  que ya existe. Crear uno se justifica cuando hay lógica **inalcanzable para los tests** (p. ej. una
  cadena que sólo se ejecuta tras un timeout de 15 min de reloj real).
- Antes de crear uno: `ls domain/usecase/*/ | wc -l` y buscar si sus predicados ya viven en otro. Dos
  evaluadores con los MISMOS parámetros donde uno es superconjunto del otro son un solo evaluador.

### ⛔ Magic numbers — NUNCA inline
Constantes en `companion object` privado de la clase que las usa, UPPER_SNAKE_CASE. Si la comparten
2+ clases → fichero de config del módulo. Nunca en God Objects compartidos.
```kotlin
private companion object { const val GEOFENCE_RADIUS_METERS = 80f }   // ✅
if (distance > 80f) { ... }                                          // ❌
```

### Error handling
- Estándar `kotlin.Result<T>` (stdlib) — NO hay wrapper `AppResult`. One-shot vía `runCatching`.
- Los `Flow` aíslan errores con `.catch { }` para no matar el stream (la UI sigue sirviendo cache).
- Errores de negocio a la UI → `PaparcarError` (sealed: `Location`, `Network`, `Database`,
  `Detection`, `Auth`, `Parking`, `Vehicle`) vía `Effect.ShowError(...)` → `when` → `SnackbarHost`.
  Cero catch silenciosos.

### Testing
Toda UseCase nueva lleva test unitario. **Fakes sobre mocks** (`FakeAuthRepository`,
`FakePermissionManager`, `FakeUserParkingRepository`…). Naming
`should_expectedBehavior_when_condition`.

### Commits y ramas — Conventional Commits
```
feat(home): implement bottom sheet with nearby spots [HOME-002]
fix(detection): geofence departure not triggering spot publish [FND-004]
refactor(core): extract magic numbers to companion objects [FND-002]
```
Ramas: `feature/` · `bugfix/` · `refactor/` · `chore/` · `experiment/` + `<TICKET-ID>-<slug>`.
El ID debe ser autoexplicativo. ⚠️ PS 5.1 rompe `git commit -m` con comillas → usar `-F <fichero>`.

### ⛔ Una PR con resultado visual SE ENSEÑA [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]
Regla mental: *si el cambio se puede ver, la PR tiene que verse — describirlo no es enseñarlo.*
- **Toda PR cuyo cambio se aprecie en pantalla lleva una captura en su DESCRIPCIÓN**, como
  evidencia del comportamiento o del diseño nuevo. No en un comentario suelto: en el cuerpo de la
  PR, que es lo que se lee al revisarla y lo que queda al cerrarla.
- **La captura tiene que ser del estado FINAL del código de esa PR.** Una captura de una revisión
  anterior enseña algo que ya no existe, y eso es peor que no enseñar nada: da por revisado lo que
  nadie ha visto. Si la PR cambia tras la revisión, la captura se **sustituye**.
- Dispara con cualquier cambio visible: UI y Compose, pantallas, componentes, navegación,
  diálogos y hojas, estados visuales (vacío/carga/error), mapa, marcadores, iconos, animaciones,
  layout, tema y color, tipografía, accesibilidad visual. Ante la duda, se enseña.
- **Si el cambio no se ve, no hace falta captura** — y decirlo en la PR en una línea (*«sin
  resultado visual»*) vale más que dejar el hueco, que se lee como un olvido.
- Con varios estados afectados, una captura **por estado** (o antes/después cuando el valor del
  cambio está justo en la diferencia). La captura sale de un device o del Dev Catalog, no de una
  maqueta: lo que se enseña es lo que compila esta rama.

### Cosas que NO hacer
- No re-implementar filas "icono+título+subtítulo+trailing" → `PapListItem` dentro de
  `PapOutlinedCard` + `PapIconTile`. Un solo esqueleto; leading/trailing son slots. [UI-LIST-ITEM-001]
- No usar `HorizontalDivider`/`VerticalDivider` crudos → `PapDivider`/`PapVerticalDivider`
  (alpha en `PapBorders.HAIRLINE_DIVIDER_ALPHA`). Enforced por `DividerGuardrailTest`.
- No usar `println` para logs → Logger con tag
- No usar wildcard imports (`import com.paparcar.*`) — nombrar los símbolos. Enforced por
  `ImportGuardrailTest` (Konsist). ⚠️ Un wildcard **no lo cubre un rename**: no hay símbolo que
  renombrar, y por eso los 5 que había sobrevivieron al `sed` de BaseLogin.
  ⛔ **Falsificar un guard de Konsist exige `--rerun-tasks`**: un import no cambia el bytecode, así
  que Gradle da el test por UP-TO-DATE y no llega a ejecutarlo. Vale para todos los de este paquete.
- No commitear archivos de build: logs, .kotlin/metadata, build/
- No escribir strings en español en el código — EN es siempre la base
- No mezclar señales Bluetooth dentro del Coordinator scoring
- No crear pantallas sin sus State/Intent/Effect sealed classes
- No añadir pantalla/estado/flujo nuevo sin actualizar el sistema de pruebas mock (⛔ abajo)
- No copy al usuario con mecánica interna ni jerga inventada — causa + consecuencia + remedio

### ⛔ Sistema de pruebas mock (Dev Catalog) — mantener SIEMPRE en sync
Flavor `mock` (`app/src/mock/.../dev/`) para entrar sin OAuth/Firebase y probar pantallas y estados en
device: **Dev Catalog** (`DevMainActivity` → `DevRoot`/`DevCatalogScreen`) con escenarios
(`MockScenario` + fakes scenario-aware) y **galería de estados** (`StateGalleryScreen`). En la MISMA
tarea, o queda fuera del set probable:
- **Pantalla nueva** → `ScreenGroup` en `StateGalleryScreen.kt` llamando a su `XxxContent(state=…)`,
  espejando su `*Previews.kt`.
- **Estado/variante nuevo** (loading/empty/error/modo) → variante en la galería, paridad con `*Previews.kt`.
- **Condición que afecte routing** (sesión, permisos, onboarding, vehículo) → `MockScenario` + el
  fake que la lee + preset/control en `DevCatalogScreen.kt`.
- Verificar `assembleMockDebug` sin romper prod. Solo se toca `app/src/mock/` + `:mockkit`.
- ⛔ **Los fakes salieron de `:shared` el 18-09 y no vuelven.** Estaban en `commonMain`, y en iOS eso
  significa publicarlos: medido con `nm` sobre el Release del build 2 de TestFlight, **161 clases
  `Fake*` exportadas** — `MockMainViewController` era público en `iosMain`, Kotlin/Native lo exporta
  a `ComposeApp.h`, y un símbolo exportado es raíz de DCE que arrastra todo lo demás. Eso es
  guideline 2.3.1 (funcionalidad oculta) y ya costó un rechazo en otra app del equipo. Ahora viven en
  `:mockkit`, que depende de `:shared` y no al revés; `MockGraphGuardrailTest` vigila que ninguna
  fuente publicable los importe. [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]

## Modelos de datos clave
- `Spot` — plaza comunitaria: location, type (AUTO_DETECTED/MANUAL_REPORT), status, confidence, sizeCategory, carbodyType, enRouteCount, TTL
- `UserParking` — sesión propia: vehicleId, location, geofenceId, isActive, detectionMethod, detectionPath, armEvidence, routePolyline, sizeCategory, carbodyType
- `Vehicle` — brand, model, licensePlate? (on-device), bluetoothDeviceId?, isDefault, sizeCategory,
  carbodyType?, fuelType? (obligatorio en alta si `burnsFuel`), consumptionPer100? (override;
  null ⇒ `FuelDefaults`), consumptionElectricPer100? (solo PHEV), energyLabel?,
  **sharedCarId?** (el espejo del grupo) + **ownerUid?** (no null ⇒ fila VINCULADA: coche de otro,
  no cuenta para el límite free) [VEH-SHARED-001]
- `UserProfile` — perfil Firebase: userId, email, displayName, photoUrl
- `SharedCar` — ESPEJO del coche que conducen varios [VEH-SHARED-001]: carId (= id del coche del
  dueño, no se re-keya nada), ownerUid, displayName, btMacHash (token de comparación, **nunca la
  MAC**), `state` (dónde está AHORA y quién lo dejó — lo lee todo el grupo, **dueño incluido**),
  `members/{uid}` (subcolección: la pertenencia tiene que ser comprobable por reglas) y
  `sessions/{id}`, el historial compartido. Un coche compartido **se mide como cualquier otro** y
  sus cifras son del grupo: km, tiempo buscando plaza y plaza liberada viajan SIEMPRE; la
  **geometría** también viaja SIEMPRE, y lo que decide su autor es si se MUESTRA
  (`vehicles.shareRoutesInGroup` → `members/{uid}.sharesRoutes`, encendido por defecto, resuelto al
  leer en `asHistoryEntry(showRoute)` para que volver a encenderlo devuelva los viajes viejos). ⛔ El espejo **no tiene campo** para `armEvidence`,
  `detectionPath`, `tripMaxSpeedMps` ni matrícula: eso es mecánica de detección, y la lista blanca
  ahí es el TIPO, no un `if`. La sesión personal sigue siendo la fuente de verdad, con su ruta.
  ⛔ **No hay pantalla de grupo**: el coche compartido es un coche más de la pestaña Vehículos, con
  su sección dentro de la página (miembros, código, interruptor de rutas) y con historial y cifras
  del GRUPO por el mismo agregador de siempre.

### Categorización bidimensional de vehículos
> Detalle: `docs/architecture/VEHICLE-CATEGORIZATION.md`
- `VehicleSize` (5): MOTORCYCLE, MICRO_SMALL, MEDIUM_SUV, LARGE_SEDAN, VAN_HIGH — longitud de plaza y radio de geofence
- `CarbodyType` (10): HATCHBACK_SMALL, SUV_SMALL, HATCHBACK_MEDIUM, SUV_MEDIUM, SEDAN, FAMILY_LONG, SUV_LARGE, VAN_LIGHT, VAN_COMMERCIAL, PICKUP — anchura, gálibo e identidad visual
- Inferencia `brand + model → CarbodyType` vía `VehicleCatalog.inferBodyType()` con fallback regex
- Compatibilidad `SpotFit` (OPTIMAL / FITS / DOES_NOT_FIT / UNKNOWN) con ambos ejes

## Plus vs free — dónde cae una feature nueva
> Detalle y lista de espera: **`docs/backlog/plus-alerts-and-tiers-001.md`**.

**Free = tú preguntas y la app responde · Plus = la app te avisa a ti, y recuerda por ti.** El eje no
es cuántas cosas ves, es **quién trabaja**: mirar es gratis, que la app vigile mientras no miras se
paga. Por eso las alertas de zona ya eran Plus con el mapa gratis.
- ⛔ **Nunca se gatea VER**: plazas, parkings públicos y gasolineras —con precio, tarifa, gálibo y
  cómo llegar— son free COMPLETO. Cobrarlo vacía el mapa justo para quien no paga, que es el problema
  que F5 y F6 existen para resolver.
- Vendible encima: **vigilancia** (alertas) y **memoria** (más allá de los 7 días de historial).
- ⛔ Gating **soft** siempre: la acción dispara el paywall, el botón nunca se esconde.

## Navegación
BottomNav con 3 destinos (`bottomNavItems` en `App.kt`):
- **Home** — el AHORA: mapa, plazas libres, sesión activa, detección en curso
- **Vehículos** — lo MÍO: garaje (pager) + Historial fusionado
- **Ajustes** — configuración + salud de detección/permisos

Regla editorial: si pasa AHORA → Home; si pasó o es mío-permanente → Vehículos; si configura → Ajustes.
Futuro (post-lanzamiento, NO ahora): posible 4º tab Comunidad/Perfil.

`Splash → Auth → VehicleRegistration → Onboarding → Permissions → Home`

## i18n
- Base EN (siempre completo) · P0 ES · P1 IT, PT, FR · P2 DE, NL, PL, RO — **los 9 se mantienen en sync**
- Excluidos por complejidad UI: RTL (AR, HE) y glifos complejos (ZH, JA, KO, TH, HI)
