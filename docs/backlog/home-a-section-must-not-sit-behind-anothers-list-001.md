# HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001 · un carril por sujeto: la posición de una sección no puede depender de cuántas filas tenga la de arriba

**Estado:** ✅ Done — en master (el hash vive en `MEMORY.md`: este doc viaja DENTRO de su propio
commit, así que por construcción no puede citarlo).

> ⚠️ **Superado en parte por [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001](nearby-one-store-follows-the-camera-001.md)** (13-09-2026): las tiendas de carril y de capa se fusionaron en UNA por sujeto, y la unión que pintaban mapa y lista desapareció. Lo que aquí se dice de dos tiendas / dos anclas es historia, no regla vigente.

## Problema

El sheet de Home concatena en un solo `LazyColumn` todo lo que Home sabe decir:

```
first steps · superficie de detección · TUS VEHÍCULOS + coches
PLAZAS LIBRES CERCA + filtros + N filas (sin cap) + CTA "avisar de una plaza"
PARKINGS CERCA          (plegado)
DÓNDE REPOSTAR          (plegado)
```

La lista de plazas es `nearbySpots.filter { it.status.isAvailable }` **sin `take(n)`**. Por tanto la
`y` de los dos complementos no es una constante: es una función del número de plazas que la
comunidad tenga cerca. Calculado sobre los paddings del propio código (fila de plaza ≈ 68 dp,
cabecera de sección 44 dp, barra de filtros 40 dp, bloque personal ≈ 260 dp en régimen estable):

| plazas cerca | y del header de PARKINGS | y del header de DÓNDE REPOSTAR |
|---|---|---|
| 3 | ≈ 640 dp | ≈ 684 dp |
| 10 | ≈ 1.116 dp | ≈ 1.160 dp |
| 25 | ≈ 2.136 dp | ≈ 2.180 dp |

Con un viewport de lista de ~500-550 dp a snap completo, son 2 a 4 swipes para llegar a repostar, y
**crece con la densidad de la zona**: en el barrio donde la app funciona mejor es donde peor se
llega a los complementos. ⏳ Pendiente de confirmar en device con el recuento p50/p90 de una zona
real (es la medición que `plus-roadmap-001.md:418` dejaba abierta antes de decidir esto).

Dos problemas de sistema debajo del síntoma:

1. **Dos controles por concepto.** El menú de capas del mapa enciende la capa
   (`ToggleFuelStationsLayer` / `TogglePublicParkingsLayer`, preferencia persistida) y el chevron
   del sheet abre la sección (`ToggleFuelStations` / `TogglePublicParkings`). Estados alcanzables
   hoy: capa encendida con sección plegada, y sección abierta con el mapa sin una sola cápsula. El
   usuario que quiere "ver gasolineras" no sabe cuál de los dos controles es el suyo.
2. **Plegar no acota nada.** Para plegar plazas hay que llegar hasta su chevron, y el estado lo
   tiene que mantener el usuario. `freeSpotsSectionExpanded` se auto-abre cuando hay plazas
   (`HomeState.kt:404`), o sea que el caso "hay muchas plazas" —el que empuja los complementos
   abajo— es exactamente el caso en el que la sección se abre sola.

## Doctrina violada

- **Sistemas, no parches.** Cada sujeto nuevo ha entrado como "una sección más abajo". Es la tercera
  vez (`FUEL-STATIONS-LIST-001`, `PARKINGS-ON-THE-MAP-001`, y F7/afiliados detrás) y la geometría
  ya no da: el invariante que falta es *el contenido de un sujeto arranca en una `y` fija*.
- **Un solo control por concepto** (misma familia que `UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001`
  y que el propio `PARKINGS-ON-THE-MAP-001`, que ya juntó dos toggles sueltos en un menú de capas).
- `UI-COLOR-DOCTRINE-001` / iconos de 3 niveles / tipografía por rol: el carril nuevo no estrena
  ningún componente — usa `PaparcarFilterChip`, `PapSectionHeader` y los roles que ya existen.

## Señales / datos disponibles

- `HomeState.showFuelStationsLayer` · `showPublicParkingsLayer` — preferencias persistidas de capa.
- `HomeFuelSlice.isExpanded` · `HomeParkingsSlice.isExpanded` — el pliegue de sección, que además
  **es el disparador de la petición** (nada se descarga hasta desplegar).
- `HomeFuelLayerSlice` / `HomeParkingsLayerSlice` — stores propios de las capas, ancladas a la
  CÁMARA. ⛔ No se fusionan con las de sección: `FUEL-LAYER-FOLLOWS-THE-CAMERA-001` ya midió que
  responden preguntas distintas ("qué hay donde miro" vs "dónde reposto") y que fusionarlas pintaba
  las cápsulas de Madrid sobre París.
- `freeSpotsExpandedOverride` + `HomeSheetAction.RevealFreeSpots` + `FREE_SPOTS_SECTION_KEY` — el
  paso del checklist que hoy hace `scrollToItem` a una clave de string.

## Diseño

**El sheet pasa a tener dos bloques con reglas distintas**, que es la partición que ya existe en la
cabeza del producto y no estaba escrita en el layout:

```
┌─ peek handle ───────────────────────────────┐  sin cambios
├─ BLOQUE FIJO · lo MÍO ahora ────────────────┤  nunca se mueve, nunca se pliega
│  first steps · superficie de detección      │
│  TUS VEHÍCULOS + fila de coches             │
├─ LENTE (stickyHeader) ──────────────────────┤  siempre alcanzable, no scrollea
│  [Plazas · 7] [⛽ Gasolineras] [P Parkings]  │
├─ carril único del sujeto elegido ───────────┤  profundidad acotada por UNA lista
└─────────────────────────────────────────────┘
```

Una sola pieza de estado, `HomeNearbyLens { SPOTS, FUEL, PARKINGS }`, y con ella:

- **Profundidad acotada.** Cualquier sujeto arranca en la misma `y`. Gasolineras pasa a ser un
  toque y cero scroll, y lo sigue siendo con 40 plazas cerca.
- **El pliegue muere.** `isExpanded` de las dos secciones y `freeSpotsExpandedOverride` se retiran:
  el sujeto visible es la lente. El disparador de red se conserva **igual** (entrar en un carril es
  lo que hoy es desplegarlo), con sus mismas reglas de no re-preguntar.
- **Jerarquía por DEFECTO y ÉNFASIS, no por dp de scroll.** `PARKINGS-ON-THE-MAP-001` decidió que
  plazas es el producto y los otros dos son complementos, y esa decisión se respeta: Plazas es la
  lente por defecto en cada arranque en frío, lleva su cifra siempre, y va primera. Los
  complementos llevan icono (nivel 1/2, Material Rounded, `onSurfaceVariant`) y no llevan cifra
  hasta que se ha preguntado — ⛔ un `0` que nadie ha verificado es la mentira que
  `FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001` prohíbe. La lente **no** es una preferencia
  persistida: Home es el AHORA.
- **Un control por concepto: el menú de capas SE RETIRA** (decisión del user, 10-09, tras plantearle
  las tres opciones). El carril manda en el mapa:
  - se pinta el complemento del carril que estás mirando, y **solo ese** — nunca dos capas de
    etiquetas apiladas sobre los pines de plaza, que son el producto;
  - plazas, tus coches aparcados, tus zonas y el viaje vivo se pintan **siempre**: nunca fueron
    capas, y nada de lo que elija el usuario aquí puede esconderle su propio coche;
  - mueren `showFuelStationsLayer` / `showPublicParkingsLayer` (estado, `AppPreferences`, DataStore,
    UserDefaults de iOS y los tres fakes) y con ellas `MapLayersFab` + `map_cd_layers`. Una capa
    dejaba de ser "dónde estás mirando" para ser una preferencia que sobrevivía a la sesión;
  - salir de un carril **suelta la selección de ESE sujeto** (su pin ya no se dibuja), nunca la de
    los otros.
- ⛔ **Una petición por entrada, no dos.** El carril pregunta anclado al USUARIO y el mapa lee esa
  misma respuesta (`toMapSlice` une los dos almacenes, igual que ya hacía el peek); la capa anclada a
  la CÁMARA solo pregunta cuando la cámara se ha movido de verdad. Los dos almacenes siguen separados
  y con su propio ancla — lo que se acabó es pagar dos consultas al mismo servicio gratuito por
  abrir un carril. Excepción medida: un coche **sin combustible** no tiene pregunta de lista, así que
  ahí sí pregunta el mapa (cápsulas sin cifra son honestas; un mapa vacío no).
- **`RevealFreeSpots` deja de ser un scroll a una clave** y pasa a ser "pon la lente en plazas":
  se va el `scrollToItem(FREE_SPOTS_SECTION_KEY)` que puede dejar de funcionar en silencio si
  alguien renombra el key.
- Las cuatro frases honestas de repostar (`missingFuelType`, `failed`, vacío, sin precios) y las
  tres de parkings **sobreviven como estados del carril**. Se mudan, no se colapsan.

**Fuera de alcance (deliberado):** capar la lista de plazas. Con un carril por sujeto la longitud de
plazas ya no empuja a nadie, así que capar sería resolver un problema que este ticket elimina.

## Lo que cambió AL VERLO en device (10-09, Pixel 8 Pro)

1. **Chips → PESTAÑAS de ancho completo.** La primera versión usaba `PaparcarFilterChip`, las mismas
   píldoras que el filtro de tamaño que va justo debajo — y en mano leía exactamente como lo que esas
   píldoras son: criterios opcionales que se suman. Estos tres no son criterios, son las tres cosas
   que Paparcar sabe enseñarte alrededor. Ahora ocupan un tercio cada una, solo una está encendida, y
   la seleccionada lleva subrayado (decisión del user).
2. **Glifos propios, no Material.** Tres marcas nuevas en `PaparcarIcons`, en el lenguaje de las que
   ya tenía la app (`SpotPin`, `SpotParkingP`, pictogramas de coche): silueta rellena, huecos por
   `EvenOdd`, 24×24, tintables. Y cada una dibuja lo que ESE sujeto es aquí: la plaza es el hueco
   entre dos marcas viales, el parking público es una instalación con su P (⛔ no la P de Paparcar,
   que es de las plazas), la gasolinera es su surtidor.
   - ⛔ **La plaza se dibuja ABIERTA (una U).** El primer intento la cerró por arriba y a 22 dp salía
     un bloque verde macizo: con paredes de 5 unidades el hueco desaparecía. Lo que la hace legible
     es justo lo que la hace ser una plaza. Visto en device, no en la galería.
3. **La card de un solo vehículo baja de ~230 dp a ~100 dp** (petición del user al ver el sheet).
   Se va el divisor y la fila-pie entera con su caja de glifo de 44 dp; el nombre, el estado y el
   chevron se quedan. No estrena anatomía: `VehicleIdentityHeader` ya tenía los slots `statusLine` y
   `trailing` para exactamente esto.
4. **Etiquetas cortas en los 9 locales.** Un tercio de fila no admite "Parques de estacionamento"
   (25 car.). Los nombres de sujeto pasan a su forma corta donde hacía falta (PT, IT, FR, DE, NL, PL,
   RO); el término completo sigue vivo en las filas y en el peek, que es donde hay sitio para él.

## Abierto a medir en device (no se decide a ciegas)

- **Dos filas de chips seguidas** (lente + filtro de tamaño de plaza) en el carril de plazas. v1 las
  deja apiladas y diferenciadas (la lente es sticky, con icono; el filtro sigue con su tratamiento
  actual). Si en mano se leen como una sola banda confusa → el filtro se pliega detrás de un chip
  de filtros al final de la fila de la lente. ⛔ No se adivina desde la galería: la banda anterior
  de este mismo sheet ya se decidió mal dos veces sin device.
- La `y` del header de repostar con recuento p50/p90 real, para dejar el número del problema medido
  y no calculado.

## Criterio de éxito

- Con N plazas cerca (N = 0, 3, 25), el primer contenido de cualquier lente aparece a la misma `y`,
  sin scroll. ✅ **medido en device** (Pixel 8 Pro, 10-09): desde el sheet abierto, *Car parks · 49* y
  *Fuel stations · 60* salen a un toque y cero scroll, con el bloque personal intacto encima.
- No existe estado alcanzable con la capa de un sujeto encendida y su carril hablando de otro: ✅ por
  construcción — la visibilidad se DERIVA de la lente (`showsFuelOnMap` / `showsParkingsOnMap`), no
  hay segundo estado que pueda discrepar.
- Entrar dos veces en el mismo carril no dispara dos peticiones. ✅ `should_notReask_when_theSameLensIsChosenTwice`.
- Un arranque en frío abre en plazas. ✅ `should_openOnSpots_when_theViewModelStarts`.
- Suite: **2.629 tests en verde** con `--rerun-tasks` (necesario para que los guardarraíles Konsist
  se ejecuten de verdad, no `UP-TO-DATE`).

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `HomeSheetContent.homeSheetItems` | tres secciones concatenadas y plegables | ✅ bloque fijo + carril |
| `HomeFuelSection` / `HomeParkingsSection` | cabecera con chevron + `AnimatedVisibility` | ✅ → `HomeFuelLane` / `HomeParkingsLane` |
| `HomeIntent.ToggleFuelStations` / `TogglePublicParkings` / `SetFreeSpotsSectionExpanded` | pliegue por sección | ✅ retirados por `SetNearbyLens` |
| `HomeIntent.ToggleFuelStationsLayer` / `TogglePublicParkingsLayer` | capa como control propio | ✅ retirados |
| `HomeViewModel.toggleFuelStations` / `togglePublicParkings` / `*Layer` | dueños del disparo de red | ✅ → `setNearbyLens` + `askFuelForLane` / `askParkingsForLane` |
| `HomeState.freeSpotsExpandedOverride` / `freeSpotsSectionExpanded` | auto-apertura de plazas | ✅ retirados |
| `HomeState.showFuelStationsLayer` / `showPublicParkingsLayer` | preferencia persistida | ✅ → `showsFuelOnMap` / `showsParkingsOnMap`, derivadas |
| `AppPreferences` + `AndroidDataStore…` + `IosAppPreferences` + 2 fakes | prefs de capa | ✅ retiradas (keys anotadas como inertes, según la convención del fichero) |
| `HomeSlices.spotsSectionExpanded` | pliegue en el slice | ✅ → `nearbyLens` |
| `HomeSheetAction.RevealFreeSpots` + `FREE_SPOTS_SECTION_KEY` | scroll a clave | ✅ → `SetNearbyLens(SPOTS)` + `NEARBY_LENS_KEY` (la clave sigue existiendo porque el paso del checklist camina hasta la fila pegada) |
| `HomeMapFabColumn` / `MapLayersFab` / `LayerMenuItem` | menú de capas | ✅ borrados; la columna es solo la tríada de cámara |
| `HomeMapSection` / `HomeScreen` | cableado `onToggle*Layer` | ✅ borrado |
| `StateGalleryScreen` (fuel/parkings) | variantes plegado/desplegado | ✅ carriles + grupo nuevo "Home · carril de cerca de ti" con las 3 lentes **y la lista de plazas llena** (una variante con 0 plazas no ejercitaría el caso del ticket) |
| `HomeSlicesTest` / `HomeViewModelTest` | pliegue, capas y prefs | ✅ traducidos, no borrados (uno por invariante) |
| Strings ×9 | `home_feed_nearby`, `home_feed_nearby_with_count`, `home_fuel_title`, `home_parkings_title`, `map_cd_layers` | ✅ borradas (las cazó `LocaleParityGuardrailTest`); `map_layer_*` → `subject_*`, ahora leídas por los chips Y por el resto |
| KDoc de `HomeParkingsSection` | decía "tercera sección, bajo repostar" cuando el código la ponía ANTES de fuel | ✅ murió con el pliegue |

## Lo que cambió respecto al plan inicial (y por qué)

1. **El menú de capas no sobrevive.** El plan lo mantenía y solo sincronizaba ambos sentidos. Al
   preguntar por la acumulación de marcadores, el user eligió que mande el carril: un control, cero
   acumulación. Es más coherente con el propio ticket — mantenerlo habría dejado en el mapa el mismo
   "dos controles por concepto" que estábamos quitando del sheet.
2. **La unión de almacenes en `toMapSlice`** no estaba en el plan. Apareció al medir la consecuencia
   de que la lente mandara en las dos superficies: sin ella, cada entrada a un carril costaba DOS
   peticiones. El peek ya unía los dos almacenes desde F5b-2, así que hay precedente.
3. **El ancla del carril.** `loadFuelStations` / `loadPublicParkings` ahora estampan dónde
   preguntaron en el almacén de la capa. Sin eso, el primer paneo nervioso tras entrar volvía a
   preguntar por el mismo suelo — lo cazó `should_notReaskOnEveryFix_when_theCameraHasNotMoved`, que
   es exactamente la regresión que ese test existe para vigilar.

## Follow-up abierto por este ticket

`ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001` — con los complementos a la vista en un carril propio, el
onboarding sigue vendiendo solo plazas de la comunidad. Ficha aparte, sin implementar aquí.

## Rebase sobre master (10-09)

Rebasado sobre `96f09b73`, que traía 4 commits con solape fuerte (16 ficheros, 2 de ellos borrados
por este ticket). Conflictos en `HomeViewModel` + los dos ficheros de sección borrados.

**El choque real no era de texto, era de doctrina.** `A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001`
(bug visto en el Oppo: el mapa pintaba seis parkings sobre Jerez mientras la lista decía "ninguno a
1,5 km") ancló las secciones a la CÁMARA; este ticket las anclaba al USUARIO. Se adopta master —y la
lente lo simplifica—: con un solo sujeto a la vista, mapa y lista preguntan por el mismo sitio con el
mismo radio, así que la contradicción ya no tiene dónde nacer, y el refetch al asentarse la cámara
pasa de comprobar dos secciones posiblemente abiertas a comprobar una.

Lo que el rebase destapó, y no habría salido de otra forma:

1. **Doble petición por asentamiento.** Con el carril y su capa siguiendo ambos a la cámara, cada
   settle costaba DOS peticiones por el mismo suelo. Corte en `schedule*ViewportRefetch`: si el carril
   de ese sujeto está a la vista, la capa no pregunta. ⛔ El corte comprueba TAMBIÉN
   `activeFuelType() != null`, porque con un coche sin combustible el carril no puede preguntar y la
   capa tiene que seguir siendo quien sigue a la cámara.
2. **Bug latente en master:** su carril anclado a la cámara **no devuelve el ancla al fallar**. Es la
   regla que `FUEL-LAYER-FOLLOWS-THE-CAMERA-001` ya pagó en campo (un 504 pasajero dejaba París en
   blanco). En master no se veía porque la capa seguía preguntando por su cuenta; aquí, con el carril
   como único seguidor de la cámara, queda al descubierto. Arreglado en los DOS carriles.
   Lo cazó `should_askAgainInTheSamePlace_when_theFirstAskFailed`.
3. La frase del caché de disco (`rememberedAtMs`) que master añadió a las secciones se **portó a los
   carriles**; sus variantes de galería también.

Verificado tras el rebase: **2.663 tests en verde** con `--rerun-tasks`, prod y mock compilan, y la
app arranca y pinta el sheet en el Pixel 8 Pro sin caídas.
