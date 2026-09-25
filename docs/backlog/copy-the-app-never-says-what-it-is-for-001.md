# COPY-THE-APP-NEVER-SAYS-WHAT-IT-IS-FOR-001 · La app explica su mecánica y nunca su propósito

**Estado:** ✅ Done · mergeado a master por squash (el hash vive en `MEMORY.md`, no aquí: el doc
viaja DENTRO de ese commit y no puede citarse a sí mismo)

## Problema

Barrido de las cuatro superficies donde la app se explica (app, first steps, `paparcar.com`, ficha
de Play). La pedagogía del **flujo** es abundante y buena. Faltan dos cosas, y una de ellas se
contradice entre superficies.

### H1 · El prerequisito se promete distinto en cada superficie

La verdad del sistema está escrita en `DetectionUiState.kt:88-92`:

> *Only the Coordinator cold-start needs a manual bootstrap. Bluetooth is fully automatic.*

O sea **dos carriles con dos promesas distintas**. Ninguna superficie lo dice:

| Superficie | Dice hoy | Para quién es falso |
|---|---|---|
| FAQ web, `en.json` items[2] | *«Do I have to mark where I park manually? **No — that is the whole point of the app.**»* | Para el carril Coordinator, que necesita bootstrap |
| Ficha de Play, descripción completa | *«saves where you left the car automatically: without opening the app, dropping a pin»* | Idem |
| `first_steps_park_sub` | *«Mark where you left your car»* — paso REQUERIDO para todos | Para el carril BT, que no lo necesita |
| `home_det_awaiting_sub` | *«Mark your parking and I will watch it»* | Para nadie: es cierto, pero no dice la CONSECUENCIA de no hacerlo |

El fallo no es que no lo digamos: es que decimos el **qué** y nunca el **a cambio de qué**. El
usuario que activa detección, no marca nada y espera que funcione sola no recibe nada y la app
nunca le dijo por qué. El estado se llama literalmente `AwaitingFirstPark` y
`isDetectionWorking == true`: la app SÍ está trabajando, y está esperando.

### H2 · `spots_shared` es una cifra muda — su significado vive en un KDoc

`PeriodStats.spotsReleased = ended.count { it.publishedSpot }` (`ComputePeriodStatsUseCase.kt:158`)
es un **recuento exacto**, sin estimación de nada. El tile lo pinta verde porque
`isContribution(SPOTS_SHARED)`. Y lo que ese número SIGNIFICA está escrito en el KDoc de
`isContribution` (`VehicleStatsBlock.kt:583-588`):

> *la única de las once que el usuario DA en vez de gastar, y **la razón de ser del producto***

El código lo sabe. El usuario ve «3 · spots shared» y nada más.

### H3 · No hay una sola frase de propósito en ninguna superficie

`grep -riE 'co2|environment|medio ambiente|planet|ecolog|sostenib|contamina|climate'` sobre app (9
locales) + `web/content/*.json` + `hosting/` + ficha de Play: **CO₂ aparece tres veces y las tres es
tu factura** (`onboarding_step1_desc`, `vehicle_registration_fuel_help`, `stats_tile_co2`). Cero
apariciones de tráfico, medio ambiente o por qué liberar una plaza le importa a alguien.

## Doctrina violada

- **`docs/analytics-data-contract.md`** — cada cifra cita su evidencia. Es lo que **impide** el
  arreglo fácil de H3 (un contador de «CO₂ ahorrado»), no lo que lo pide. Ver §Fuera de alcance.
- **`WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001`** — la web promete automatismo total a los dos
  carriles. Es exactamente el fallo que esa regla existe para impedir, en la dirección contraria a
  la habitual: aquí la web va por delante de lo que la app hace.
- **COPY-SPOT-IS-NOT-A-PARKING-001** — respetado en todo el copy nuevo: lo tuyo es APARCAMIENTO, lo
  de la comunidad es PLAZA.

## Señales / datos disponibles

- Carril y estado: `DetectionUiState` (`ArmedBluetooth` vs `AwaitingFirstPark`), ya proyectado.
- `FirstStep.MARK_PARKING` KDoc: *«Creates the session, the fence and the watch»* — el encadenado
  que hay que contar, ya escrito, en el sitio equivocado (un KDoc).
- `PeriodStats.spotsReleased` — recuento exacto, cero estimación.
- `ExplainerCopy.note` — slot existente; `MARK_PARKING` lo tiene a `null` (`FirstStepExplainer.kt:143`).
- `StatTileData.note` — slot existente, ya usado por `AUTO_DETECTED` («12 of 13 trips»).

## Diseño

**Ni un string nuevo con una cifra dentro.** Los tres huecos se cierran con copy sobre hechos que ya
medimos o sobre mecánica que ya existe.

### H1 — cada superficie nombra el carril

- `home_det_awaiting_sub` pasa a decir que es **una vez** y que desde el próximo viaje va sola. La
  fila tiene 2 líneas y dos CTAs: ahí cabe la respuesta a *«¿no era automático?»*, no el discurso.
- `first_steps_explain_park_note` **nace** (hoy `note = null`): la consecuencia entera, en la hoja,
  que es donde hay sitio. Sin aparcamiento abierto no hay nada que liberar.
- FAQ web items[2] deja de ser un «No» plano y distingue los dos carriles.
- Ficha de Play: el paso 1 de HOW IT WORKS dice el arranque.

**División de trabajo respetada:** la fila ACTÚA, el explainer ENSEÑA. Es la misma partición que ya
usa el resto de la superficie de detección.

### H2 — la cifra dice qué cuenta

- `stats_tile_spots_shared_note` **nace** y va al slot `note` del tile. Dice qué son esas plazas en
  términos del hecho que sí conocemos: se publicaron para los conductores de al lado. Funciona
  igual a 0 que a 12, así que no hace falta rama de código — el tile se añade
  incondicionalmente **a propósito** (`hasMeasuredFigures`), y su cero es una respuesta.
- `stats_lifetime` deja de ser dos recuentos pegados y nombra a quién fueron esas plazas.

### H3 — propósito cualitativo, cero cifras

- `onboarding_step3_desc` y `first_steps_explain_watch_body`: el efecto de liberar una plaza.
- Web: `how.keyIdea` y la FAQ de plazas comunitarias.
- Ficha de Play: un párrafo de propósito.

## Fuera de alcance, deliberadamente

**Un contador de «CO₂ que has ahorrado a otros» NO entra, y no por prudencia sino porque es
incalculable.** Sería un contrafactual, no una estimación: exigiría saber si alguien cogió la plaza,
cuántas vueltas habría dado si no, y con qué coche. Los tres son cero datos. El `stats_tile_co2` que
ya existe es distinto y sí es legítimo: `routeDistanceMeters` **medido** × consumo (`FuelDefaults` o
el del usuario) × `EmissionFactors` — tres eslabones citables, y el «aprox.» califica el del medio.
Aproximado = banda alrededor de algo que pasó; inventado = cifra sobre algo que no sabemos si pasó.

Ya se rechazó una vez, al cerrar la issue #52, y está escrito en `VehicleStatsBlock.kt:590-593`:
*«Pintarlos de verde le diría al usuario que emitir 24 kg está bien.»*

El camino honesto a un «has ayudado a N conductores» es `Spot.enRouteCount` (`Spot.kt:29`), que sí
mide de verdad, pero hoy no vuelve a la sesión: hace falta fontanería (agregador + Firestore) y eso
es lógica, no copy. → `docs/backlog/stats-a-spot-handed-over-can-count-its-takers-001.md` (user, 25-09).

## Criterio de éxito

- Ninguna superficie promete automatismo total sin nombrar el bootstrap del carril Coordinator.
- `spots_shared` dice qué cuenta sin que haya que abrir el código.
- Existe una frase de propósito, y **ninguna cifra nueva**.
- `LocaleParityGuardrailTest` verde con los strings nuevos en los 9 locales.
- `WebSiteParityGuardrailTest` verde con `--rerun-tasks`, y `node web/build.mjs --check` limpio.
- `testAll` verde.

## Consumidores auditados

### A · ¿Qué otra superficie promete cero-toque sin nombrar el arranque?

| Superficie | Veredicto |
|---|---|
| `home_det_awaiting_sub` · `first_steps_explain_park_*` | ✅ **Cerrado** — lo dicen |
| FAQ web items[2] · ficha de Play paso 1 | ✅ **Cerrado** |
| `onboarding_step1_desc` (*«notices your trips and parkings on its own»*) | 🟡 **Exento con motivo**: se lee ANTES de registrar coche, así que el matiz no tiene referente — no hay carril todavía. `first_steps`, que corre después del alta y sí conoce el carril, lo dice |
| `home_det_pitch_body` (*«Turn it on now and forget about it»*) | 🟡 **Cubierto aguas abajo**: la secuencia real es pitch → activar → `AwaitingFirstPark` («márcalo una vez, desde el próximo viaje va sola») → automático. «Olvídate» es cierto del régimen estacionario, y el estado siguiente dice el arranque |
| Web `hero.lead` · `automation.lead` (*«nothing to tap when you step out»*) | 🟡 **Cubierto por la FAQ**, que es la pregunta a la que va un lector con esa duda — y `how.steps[0]` ya nombraba los dos carriles |
| `home_det_producer_sub` · `first_steps_watch_sub` · `first_steps_watch_on_sub` · `onboarding_page1_subtitle` | ✅ **Exentos**: describen qué le pasa a un aparcamiento que YA existe, no prometen cero-toque |

### B · ¿Quién más pinta `spotsReleased`?

`grep -rn 'spotsReleased' presentation/` → **dos sitios, y los dos pasan por `VehicleStatsBlock`**:
el tile (`:856`) y el pie de vida (`:247`), alcanzados desde `StatsScreen` y `HistoryContent`.
Un solo choke point, los dos cubiertos. ✅ **Cerrado.**

### C · Dev Catalog

Nada que añadir: no hay pantalla ni estado nuevo, solo dos slots `note` que ya existían y se
renderizan. Y las tres superficies tocadas **ya se ven en la galería** —
`FirstStepExplainerScreen(FirstStep.MARK_PARKING)` (`StateGalleryScreen.kt:3737`),
`HomeDetectionSurface` y `StatsContent` — con `publishedSpot = i % 4 != 0` en los fakes, así que el
tile sale con cuenta real y su nota. ✅ **Verificado, no inferido.**

### D · Fuera: `paste/*.txt` está desfasado, y ya lo estaba

`docs/release/play-listing/paste/play-*.txt` es el corte del **29-08** (su EN mide 2.734, el de
`LISTING.md`), mientras `LISTING-v1.3.0.md` es la fuente viva de los 9 (3.568-3.989). No se toca
aquí: es anterior a este ticket y regenerarlo es trabajo del proceso de release. ⚠️ **Al pegar en
el Console hay que ir a `LISTING-v1.3.0.md`, no a `paste/`.**

### E · Hallazgo ajeno: el CSS publicado tenía un nombre que mentía

Reconstruir la web cambió 11 ficheros de `hosting/public` que este ticket no toca, y **no es ruido
del build**: el build es determinista (dos pasadas seguidas dan el mismo hash) y el hash es
`sha256(bytes)` del CSS ya reescrito (`web/build.mjs:120`). Medido:

```
sha256(HEAD:hosting/public/assets/css/site.166264b3.css) = fa24a943…
```

El fichero commiteado **como** `166264b3` contiene bytes que hashean a `fa24a943`, con
`web/assets/css/site.css` sin tocar desde entonces. O sea: el nombre con hash de contenido no
correspondía a su contenido. Eso derrota justo la garantía por la que existe el nombre hasheado, y
que el propio `build.mjs` documenta — se cachean un año, así que un visitante que guardó
`site.166264b3.css` tiene bytes distintos de los que ese mismo nombre sirve hoy: *«HTML nuevo
pintado con CSS viejo»*.

La reconstrucción de este ticket lo **deja coherente** (y borra los dos huérfanos). No hace falta
ticket: estaba roto, ya no. ⚠️ Pero la lección es que `hosting/public` no se edita a mano nunca,
ni un byte — se regenera.

### F · El hero del explainer llevaba un glifo de 20 dp en un disco de 64 (user, 25-09)

Visto por el user en la captura del run, no razonado: el candado dentro del disco verde era *«una
pelotilla en un cuenco»*. Medido, `PapIconTile` default = 40 dp de caja / 20 dp de glifo = **50%**,
y el hero pasaba `size = HERO_TILE` (64 dp) **sin** `iconSize` → se quedaba en los 20 dp → **31%**.

⛔ Arreglado en el SISTEMA, no en el call site: `iconSize` pasa de un `20.dp` fijo a
`size * GLYPH_RATIO` (0,5). A la medida por defecto da los mismos 20 dp de siempre, así que **nada
que use el default se mueve**; el hero sube a 32 dp solo. Barridos los tres callers que dimensionan
la caja: `HomeSpotRows` y `CarbodyInfoCard` ya pasaban su propio `iconSize` y no se tocan — el
explainer era **el único** que se lo había dejado. Verificado en device y aprobado por el user.

⛔ Las filas `way` del explainer **no** se tocan: usan el default 40/20, que es la proporción de
cada `PapListItem` de la app. Engordarlas ahí las desalinearía de todas las demás.

## Resultado

- `testAll` → **3.756 tests, 368 clases, 0 fallos**; `:app:compileProdDebugKotlin` y
  `:app:compileMockDebugKotlin` verdes.
- Los 4 guards de copy ejecutados con `--rerun-tasks` (15 tests): LocaleParity, WebSiteParity,
  ComposeResourcesEscape, NotificationCopyParity.
- **`LocaleParityGuardrailTest` falsificado**: quitada `first_steps_explain_park_note` de `values-pl`
  → *«key missing from a locale — the string silently renders in English there»* nombrando la key.
  Restaurada.
- `node web/build.mjs --check` → *links, anchors and hand-written pages: ok*.
- Límite de Play respetado en los 9, con el margen medido: fr-FR 3.989/4.000 (11 libres) es el
  ajustado, de-DE 3.956 (44). **Ese margen es la razón de que H3 no entre en la ficha**: un párrafo
  de propósito en los nueve exigiría recortar copy existente, que es otra decisión editorial.

## Pendiente

- ⏳ **Captura** — [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]: el cambio se ve en tres sitios (la fila de
  `AwaitingFirstPark`, el explainer de marcar, el tile de plazas cedidas). Los tres salen del Dev
  Catalog sin conducir.
- ⏳ Despliegue de la web (`firebase deploy --only hosting`) — solo con permiso.
