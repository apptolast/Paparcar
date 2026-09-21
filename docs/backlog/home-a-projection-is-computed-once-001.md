# HOME-A-PROJECTION-IS-COMPUTED-ONCE-001 · el chip de filtro tarda en marcarse

**Estado:** ✅ Done · rama `perf/HOME-A-PROJECTION-IS-COMPUTED-ONCE-001` · issue #88

## Problema
Reporte de campo: «al pulsar un chip de filtro, el chip tarda en marcarse: parece que espera a que
llegue lo que sea que tenga que cargar». Aplica a los tres carriles (plazas, parkings, gasolineras).
Un control que no responde al dedo se lee como que no ha registrado el toque, y se vuelve a pulsar.

## Lo que la issue sospechaba, y no era
La issue apuntaba a que el `selected` del chip se derivara de la RESPUESTA en vez del intent. **Eso
está descartado, leyendo el camino entero del toque:**

| Chip | Intent | Qué hace el ViewModel |
|---|---|---|
| Tamaño de plaza | `SetSizeFilter` | `updateState { copy(sizeFilter = …) }` |
| Filtro de parking | `SetParkingFilter` | `updateState { copy(parkingFilter = …) }` |
| Orden / producto de gasolinera | `SetFuelSort` · `SetFuelProduct` | `updateState { copy(…) }` |

`handleIntent` no es `suspend`, `updateState` escribe en un `MutableStateFlow` en
`Dispatchers.Main.immediate`, y los tres `selected` se derivan de ese campo del estado. **Ninguno
pide nada a la red.** El único I/O del camino (`appPreferences.setIntroducedComplements`, y sólo al
entrar por primera vez en un carril) es un `scope.launch` sobre DataStore, no una escritura
bloqueante.

Conclusión: el `selected` cambia en el instante del toque. Lo que tarda es **el frame** en el que
ese cambio se pinta — y el frame lo pinta todo Home, no sólo el chip.

## Doctrina violada
*Sistemas, no parches* y *un consumidor no depende de lo que no puede mostrar.* El mapa leía
`fuelStationsShown` / `publicParkingsShown`, que son la tienda **ordenada para el carril**. Un
marcador no tiene fila ni scroll: el orden no se ve en un pin. Pero esa lista es clave del
`remember` que construye la lista de marcadores en `PaparcarMapView`, así que pulsar «más baratas»
—que no mueve un solo pin de sitio— entregaba una lista distinta y reconstruía todos los marcadores.

Y `NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001` dice **una tienda**, no **un orden**. Los tests lo
tenían escrito como lista ordenada porque era la forma más corta de compararlas, no porque el orden
fuera el invariante.

## Diseño
Dos invariantes, cada uno en un sitio:

1. **Una proyección se calcula una vez por ESTADO, no una vez por lectura.**
   `fuelStationsShown` y `publicParkingsShown` eran `get()`: recalculaban haversine + orden sobre la
   tienda entera en **cada** lectura, y las leen el mapa, el peek, la lista y la selección. Pasan a
   `by lazy` — mismo valor, un solo cálculo por instancia de `HomeState`.

2. **El mapa lee el CONJUNTO, no el ORDEN** [MAP-THE-MAP-READS-THE-SET-NOT-THE-ORDER-001].
   `NearbyOrdering` se parte en dos: `fuelStationsPriced` / `publicParkingsMeasured` (precio +
   distancia + filtro, en el orden de la fuente) y `fuelStations` / `publicParkings` (lo anterior,
   ordenado para leerlo). `toMapSlice()` lee las primeras vía `fuelStationsOnMap` /
   `publicParkingsOnMap`.
   ⛔ El **filtro** sí sigue llegando al mapa: decide qué parkings existen para ti, y eso un pin sí
   lo enseña. Lo que no llega es el orden de lectura.

Efecto medible en el código: los dos chips de orden de gasolinera producen ahora un `HomeMapSlice`
**igual**, así que Compose se salta la sección del mapa entera.

## Criterio de éxito
- `should_leaveTheMapUntouched_when_onlyTheLanesReadingOrderChanges` — la porción del mapa es igual
  con «cercanas» y con «baratas», y el carril sí cambia de orden en las dos.
- `should_projectOncePerState_when_theSameListIsReadTwice` — identidad (`assertSame`), no igualdad:
  dos listas iguales serían dos cálculos.

**Los dos falsificados** contra el código anterior (fallan; el resto de la suite pasa). El primero
hubo que corregirlo: con todas las gasolineras sin precio los dos órdenes coinciden y el testigo no
podía fallar — necesita que la más cercana y la más barata sean gasolineras distintas.

## ⛔ Lo que este ticket NO mide
El coste real del frame en el móvil del usuario. Aquí sólo hay un emulador, y el flavor `mock` ni
trae las ~215 gasolineras / ~128 parkings de sus capturas ni pinta el mapa (la key de Maps no cubre
el paquete `.mock`). Lo que este ticket demuestra es que **había trabajo redundante en el camino
del toque y ya no está**; si el chip sigue sintiéndose lento en mano, el siguiente paso es medir con
`dumpsys gfxinfo framestats` en el device, no seguir deduciendo.

## Consumidores auditados
`grep` de `fuelStationsShown` / `publicParkingsShown`:

| Sitio | Veredicto |
|---|---|
| `HomeSlices.toMapSlice()` ×2 | **cerrado** — pasa a `…OnMap` |
| `HomeSlices.toPeekSlice()` ×2 | exento — el stepper del peek camina el orden DEL CARRIL |
| `HomeSlices.toBrowseListSlice()` ×2 | exento — es el carril |
| `HomeState.selectedFuelStation` · `selectedPublicParking` | exento — resuelve por id, el orden da igual |
| `HomeSlicesTest` ×3 · `HomeViewModelTest` ×1 | **cerrado** — comparaban conjunto como lista ordenada; pasan a `.sorted()` |
| `laneFirstPoint()` (PR #92, sin mergear) | exento **a propósito** — encuadra la PRIMERA fila, así que ahí el orden del carril es justo lo que hay que leer |

Nada fuera de `presentation/home` lee ninguna de las dos.
