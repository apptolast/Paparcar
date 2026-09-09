# FUEL-STATIONS-MAP-AND-PEEK-001 · Las gasolineras suben al mapa, y cada una cuenta lo suyo

**Estado:** ✅ Done — en master (hash en `MEMORY.md`, por construcción del cierre) · **cierra F5**

**Verificado el 09-09 en el Pixel 8 Pro con datos reales del Ministerio** (prodDebug `cf3ca923…` +
mockDebug, sha256 comprobados): FAB del surtidor en la columna del mapa (verde al activarse, y
sigue verde tras `force-stop` — la pref persiste); con fix en Embajadores, cápsulas con el precio
escrito (1.879 · 1.955 · 1.949 · 1.869) sobre el mapa; tap en la de Glorieta → peek de BLANCA con
672 ft · 3 min a pie, horario textual del feed, los TRES combustibles con su verdad (Gasoline
1.879 · Diesel 1.799 · LPG/CNG «Not sold here», el negativo declarado), «Prices reported at
09/09/2026 14:20:41» y Directions; la cápsula seleccionada viste el borde pleno. Galería: peek
España, peek OSM sin precios y las tres cápsulas. Suite verde (108 tests de VM con los 4 nuevos de
la capa, corpus de ids con la familia nueva, use case con el contrato `fuel = null`);
`assembleMockDebug`, `compileProdDebugKotlin` y klib iOS verdes.

Segunda mitad de la **F5b de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md) §F5) y cierre
de F5. La lista con precio real ya vive en el sheet
([`fuel-stations-list-001.md`](fuel-stations-list-001.md), saneada en
[`fuel-a-section-answers-only-what-it-asked-001.md`](fuel-a-section-answers-only-what-it-asked-001.md)).

## Problema

La lista dice a cuánto está el litro, pero repostar es una decisión ESPACIAL: «¿me pilla de camino?»
se responde mirando el mapa, no una columna de distancias. Y una fila de lista solo enseña el precio
del combustible del coche activo — la gasolinera entera (todos sus productos, su horario, cómo
llegar) no tiene dónde contarse.

## Lo que el roadmap ya fija (no se rediscute aquí)

- **Capa activable** de marcadores, toggle junto a `MapTypeToggle`, **apagada por defecto**, pref
  en `AppPreferences`.
- **Marcador Nivel 3 propio** (surtidor, multicolor, variante oscura), **NUNCA tintado**, con el
  precio pintado vía `TextMeasurer` (allowlist de canvas de marcadores ya existente).
- **Tap → peek** con los precios de todos los combustibles y CTA **«cómo llegar»** (deep link a la
  app de mapas).
- Los datos son los de F5b-1: `GetNearbyFuelStationsUseCase` → registro por país → caché 15 min.
  Una fuente por país; nada nuevo de red aquí.

## Doctrina que aplica

- Color: la rampa 🟢🟡🔴 es EXCLUSIVA de la caducidad de plazas — el marcador de gasolinera no
  puede usarla para «barato/caro». El precio se ESCRIBE, no se tiñe.
- Peek: sin scroll interno (crash Infinity) · componentes del peek existentes, no un peek nuevo
  desde cero · nunca modal sobre modal.
- Vocabulario: la gasolinera no es «plaza» ni «aparcamiento» — es un POI con nombre propio (MARCA
  para el rótulo).
- Un caso de uso por veredicto: la capa reusa `GetNearbyFuelStationsUseCase`; si el peek necesita
  «todos los precios de ESTA estación», eso ya viaja en `FuelStation.prices` — no se crea un use
  case nuevo para releer lo que ya se tiene.

## Diseño

**No hay abstracción de "capas" en el mapa y no se inventa aquí**: cada familia de pin viaja como
lista propia a `PaparcarMapView` y ese es el molde que se copia (zonas/spots). Piezas, en el orden
de dependencia:

1. **Datos**: la capa reusa `GetNearbyFuelStationsUseCase` + caché 15 min. El parámetro `fuel` pasa
   a **nullable**: el mapa quiere marcadores aunque el coche no tenga combustible (sin cifra), y con
   `null` no hay filtro `NOT_SOLD` ni precio. La sección de lista sigue exigiéndolo (su guard
   `missingFuelType` no cambia). **Una sola carga en el VM** (`ensureFuelStationsLoaded()`): la
   disparan tanto el despliegue de la sección como el encendido de la capa (y el primer fix GPS si
   la pref ya estaba encendida); ambos leen el mismo `HomeFuelSlice.stations`.
2. **Pref** `showFuelStationsLayer` (Boolean, default false) en `AppPreferences` — 4 sitios:
   interfaz, DataStore Android, NSUserDefaults iOS, fakes. NO va en `USER_SCOPED_KEYS` (es
   preferencia de dispositivo, como el map type).
3. **Toggle**: `MapCircleFab` con `Icons.Rounded.LocalGasStation` en la columna de FABs del mapa
   (`HomeMapFabsLayer`) — ⛔ NO en la cabecera: un tercer círculo ahí estrujó la búsqueda a dos
   líneas, medido [UI-HOME-SEARCH-BAR-MUST-STAY-ONE-LINE-001]. Estado activo con el tint de
   acción (verde = acción, doctrina).
4. **Ids**: familia nueva en `MapMarkerIds.kt` (`fuelstation/`) + `MarkerTapTarget.FuelStation` +
   casos en `MapMarkerIdsTest` (regla de prefijos disjuntos).
5. **Marcador** `FuelStationMarker` en `PaparcarMapMarkers.kt` (⛔ MISMO fichero: es la allowlist
   del guardarraíl tipográfico; ampliarla ya tapó bugs). Cápsula tipo `ZoneMarker`: glifo de
   surtidor propio + precio del combustible del coche activo con `TextMeasurer` (familia del tema,
   patrón `:747`). Sin precio → cápsula solo con glifo, nunca «0». ⛔ La rampa 🟢🟡🔴 es de la
   caducidad de plazas: aquí el precio se escribe, no se tiñe. Tema y precio van HORNEADOS en el
   `contentId` (la caché de kmpmaps no refresca de otro modo); variante `sel`.
6. **Selección**: arm nuevo `HomeSelection.FuelStation(id)` + derivada `selectedFuelStation`
   (resuelve en el slice de fuel) + barrido de los tres sitios que enumeran peeks:
   `capExpandAtPeek`, `isPureBrowsePeek` (`HomeScreen.kt:463-478`) y `showList`
   (`HomeBottomSheet.kt:176-179`). Tap en marcador (routing por `resolveMarkerTapTarget`) y tap en
   fila de la sección → el MISMO `SelectItem`.
7. **Peek** `FuelStationPeek.kt` en `components/peek/`, sobre `PapSheet` — ⛔ geometría intocable
   (sin scroll interno, título 1 línea, `PeekState` guarda solo el id y re-lee el objeto del slice
   [BUG-PEEK-JITTER-001]). Lead `GenericIcon(LocalGasStation)`; título = rótulo (voz MARCA);
   subtitle = dirección; meta = `DistanceRow` + horario + hora del dato; contenido = **todos** los
   combustibles con su disponibilidad honesta (precio si `SOLD`, «no lo vende» si `NOT_SOLD`
   declarado, raya si `UNKNOWN`); acción = `common_directions` → `HomeSheetAction.NavigateExternal`
   (walking=false), el deep link ya existente. **Sin stepper** en esta fase: el orden «más cercana
   primero» ya lo da la lista; se apunta como mejora si el uso lo pide.

**Fuera de alcance, dicho a propósito:** clustering de marcadores (60 máx del fetch, se mide en
device si molesta), stepper del peek, y cualquier país nuevo.

## Segunda tanda — feedback del user mirándolo en device (09-09)

1. **El precio dice su combustible.** La cápsula pasa a `glifo · 1.879 · Gasoline` (tipo en
   apagado, del `fetchedFor` real de la respuesta — no puede mentir). El texto viaja HORNEADO en el
   `contentId` como el precio. Sin precio, la cápsula sigue siendo solo glifo, sin etiqueta.
2. **Tocar una fila de la lista vuela la cámara**, igual que tocar su cápsula: acción nueva
   `HomeSheetAction.SelectFuelStation` enrutada a LA MISMA lambda del marcador (seleccionar +
   `goToPlace` + abrir peek) — una selección que el mapa no acusa se lee como un tap muerto.
3. **A repostar se va EN COCHE**: el peek deja el modo andando (heredado del peek de plazas, que
   responde otra pregunta — «¿la alcanzo a pie antes que otro?») y muestra siempre tiempo de
   conducción; el deep link sale con `walking = false`.
4. **La sheet deja de enterrar las gasolineras** (decisión del user: colapso inteligente, no
   reorden): la sección de plazas se renombra a **«Free spots nearby» / «Plazas libres cerca»**
   (×9; el plural con contador ya era específico) y se hace PLEGABLE con el molde de la de
   gasolineras. Con 0 plazas arranca plegada (una línea + chevron) y «Where to refuel» queda una
   fila más abajo; con plazas arranca abierta. ⛔ El ORDEN nunca cambia — lo que se pliega es el
   cuerpo; una lista que se reordena bajo el dedo era el fallo que se descartó.
   `freeSpotsExpandedOverride` (elección explícita del user, sesión) gana al dato;
   `RevealFreeSpots` del checklist DESPLIEGA antes de hacer scroll. 2 tests en `HomeSlicesTest`.

Verificado en device (prodDebug `5807170e…`): 0 plazas → «FREE SPOTS NEARBY ▾» en una línea con
«WHERE TO REFUEL» debajo; tap en la fila del REPSOL de Paseo Acacias → cámara volando, cápsula
seleccionada centrada y peek con «870 ft · 1 min drive». Galería: variante «Sheet con 0 plazas».

## Criterio de éxito

- Toggle de capa junto al de tipo de mapa; apagado por defecto; la elección sobrevive al reinicio.
- Con la capa encendida: marcadores de surtidor con el precio del combustible del coche activo
  escrito; sin precio (OSM/EV) el marcador sale sin cifra, nunca con «0».
- Tap en un marcador → peek de la estación: rótulo, distancia, TODOS sus precios con su
  disponibilidad honesta (`SOLD`/`NOT_SOLD`/`UNKNOWN`), hora del dato, y «cómo llegar».
- La capa no dispara red si está apagada; encenderla reusa la caché de la sección.
- Estados nuevos → galería mock en paridad; strings ×9; suite + Konsist + assembles + klib iOS.
- Verificado en device con datos reales del Ministerio (emulador, GPS español).

## Consumidores auditados

- **`HomeSelection` (arm nuevo `FuelStation`)** — los tres sitios que enumeran «un pin posee la
  superficie», barridos: `capExpandAtPeek` + `isPureBrowsePeek` (`HomeScreen.kt`) y `showList`
  (`HomeBottomSheet.kt`). `HomePeekHandle` con su `PeekState.SelectedFuelStation` (identidad, no
  objeto — [BUG-PEEK-JITTER-001]). `stepDirection`: sin carril de stepper para gasolineras a
  propósito (devuelve 0 vía el else existente).
- **`GetNearbyFuelStationsUseCase(fuel: FuelType?)`** — llamadores: la sección (siempre non-null,
  su guard `missingFuelType` intacto) y la capa (null cuando el coche no declara combustible).
  Test nuevo del contrato null: nada oculto, nada preciado, `fuel = null` en la respuesta.
- **`NearbyFuelStations.fuel` → `FuelType?`** — único lector nuevo: `fetchedFor` del slice, que
  evita que la sección sirva la respuesta sin precios de la capa como si fuera España sin precios
  (test `should_reprice_when_theSectionOpens_afterAFuelLessLayerLoad`).
- **`HomeMapSlice` / `HomeFabsSlice` / `HomePeekSlice`** — solo las proyecciones los construyen
  (grep); campos nuevos añadidos en ambas puntas.
- **`MapMarkerIds`** — familia `fuel_station_` disjunta de las cinco existentes; corpus del test
  ampliado (16 ids nuevos, floor 8) y prefijo en la matriz de exclusión.
- **`formatPumpPrice`** — promovido de privado de las filas a `presentation/home/model/FuelPriceFormat.kt`:
  TRES consumidores (fila, cápsula del marcador, peek) y un solo redondeo. La fila quedó reescrita
  sobre el compartido.
- **Guardarraíl tipográfico** — `FuelStationMarker` vive en `PaparcarMapMarkers.kt` (allowlist
  existente); la familia sale del tema (`PaparcarType.current.cardTitle.fontFamily`), sin ampliar
  la allowlist.
- **Detección** — sin tocar: ni estrategias, ni evaluadores, ni geofences (skill `det-change` no
  aplica).
