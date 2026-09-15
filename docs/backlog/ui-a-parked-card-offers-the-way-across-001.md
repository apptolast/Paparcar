# UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001 · Del mapa a la ficha y de la ficha al mapa: el puente que faltaba entre las dos caras del mismo coche

**Estado:** ✅ Done · el modal lleva a la ficha y el bloque de estado de la ficha lleva al mapa
· viaja en el mismo commit que [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001](ui-a-parked-card-says-where-and-when-in-two-lines-001.md)

## Problema
El mismo coche se mira desde dos sitios y **no hay forma de saltar de uno al otro**:

- Desde el **modal de aparcamiento** (`ParkingPeek`, Home) lo único que se puede hacer con el coche
  es irse (`Me voy`), navegar a pie o corregir el pin. Para ver el coche —su ficha, su historial,
  sus figuras— hay que cerrar el modal, ir a la pestaña Vehículos y buscar el coche a mano.
- Desde la **ficha** (`VehicleHeroCard`, VehiclesScreen) pasa lo simétrico: la línea de estado dice
  "aparcado en Calle X" y **no se puede tocar**. El único "Ver en mapa" que existe cuelga de las
  filas del historial, o sea de sesiones PASADAS; la sesión viva, que es la que uno quiere ver en el
  mapa, no lo ofrece.

Y el icono de `Me voy` es `TimeToLeave` — un morro de coche. En cuanto en el mismo footer aparezca
un botón de "Ver vehículo", el coche pasa a ser el icono de ESE botón: dos coches, uno encima del
otro, diciendo cosas opuestas.

## Doctrina violada
- **Navegación editorial** (CLAUDE.md): *si pasa AHORA → Home; si pasó o es mío-permanente →
  Vehículos*. El reparto es correcto, pero un coche aparcado es exactamente el objeto que vive a
  caballo entre los dos — y hoy la app no tiene puente, obliga a rehacer el camino a mano.
- **Un icono dice una cosa** (sistema de 3 niveles, nivel 1/2): el morro de coche no puede ser a la
  vez "me voy" y "ver el coche".

## Diseño

### a) Modal de aparcamiento — `Ver vehículo` bajo `Me voy`
Dos botones apilados en el footer del peek, con la jerarquía que `PapFooterButton` ya define:

| Botón | Estilo | Icono | Qué hace |
|---|---|---|---|
| **Me voy** | `Filled` (la ÚNICA acción que avanza el bucle comunitario) | `Outbound` — la flecha que sale, no un coche | abre el diálogo de liberar plaza (sin cambios) |
| **Ver vehículo** | `Outlined` (la hermana relevante que NO avanza el bucle) | `DirectionsCar` — el coche, que es el sujeto | lleva a la pestaña Vehículos, abierta EN ESE coche |

`PapFooterButtonStyle` ya documenta esta pareja exacta ("the sibling action that stays relevant but
doesn't advance the loop"), así que no nace nada nuevo: se usa el componente como está diseñado.

### b) Ficha del vehículo — el bloque de estado ES el enlace, y a DÓNDE lleva
En la `VehicleHeroCard`, el bloque de estado (lugar + tiempo) se toca entero y lleva un **chevron**,
el mismo modo en que el modal dice "esta fila abre algo" (`PeekMetaRow` con `onClick`).

⛔ **No es un botón, ni un link de texto.** Lo intentamos primero como link verde `Ver en mapa →`
bajo las dos líneas y se retiró al verlo en device (user, 15-09): era un cuarto elemento suelto en
una card cuyo bloque de estado ya nombra el sitio del que habla, y el CTA de esta card ya existe
(`Poner como activo`) — un segundo verde habría sido un segundo énfasis
[UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]. La dirección ES el enlace; de paso el área táctil pasa
de un texto de 20 dp a las dos líneas enteras. La key `vehicle_view_on_map` sigue viva como
`contentDescription` del chevron, que es quien tiene que decirle a un lector de pantalla a dónde va.

Solo se toca **cuando hay algo que ver en el mapa** (una sesión con coordenadas). Un coche que nunca
aparcó no ofrece el salto ni pinta chevron.

**Dos destinos, porque son dos cosas** (decisión del user, 15-09): si el aparcamiento sigue **VIVO**
el link lleva a **Home**, con la cámara sobre el coche y su modal abierto — la regla editorial del
proyecto es *si pasa AHORA → Home*, y Home es donde se puede uno ir, navegar o corregir el pin. El
detalle del historial es de solo lectura y existe para las sesiones **cerradas**, que en Home ya no
tienen pin; esas siguen yendo allí, por la misma lambda que sus propias filas.

⚠️ Y a Home no se NAVEGA desde una pestaña hermana: Home está DEBAJO en la pila (`navigateToTab`
hace `popUpTo(HOME)`), así que el salto deja el id en el `savedStateHandle` de la entrada de Home y
hace `popBackStack`. Un argumento de ruta habría obligado a recrear Home —mapa incluido— solo para
entregar un id. Home lo atiende con la **misma lambda** que un toque en el marcador del coche
(`onMyCarMarkerClick`: seleccionar + volar + abrir), y espera a que la sesión esté en el estado: se
llega antes de que Room emita, y descartar la petición dejaría el mapa en cualquier parte.

### c) El puente de navegación — cómo llega el id del coche a la pestaña
La pestaña Vehículos no aceptaba argumento. Se le añade uno **opcional**: `vehicles?vehicleId={id}`.

⚠️ La trampa que esto evita: `navigateToTab` usa `saveState` + `restoreState`, y Navigation Compose
**restaura la entrada guardada con sus argumentos VIEJOS** en lugar de crear una nueva con el
`vehicleId` recién pasado. Por eso el salto con coche concreto navega SIN `restoreState` (entrada
nueva, argumento nuevo), mientras que el toque normal en la pestaña lo conserva. Y como el
`destination.route` pasa a ser `vehicles?vehicleId={vehicleId}`, la identidad de pestaña se compara
por su **ruta base** (lo de antes del `?`) en los tres sitios que la usan: visibilidad de la bottom
nav, orden para la dirección del deslizamiento, y selección del item.

`VehiclesViewModel` recibe el id como una **petición de foco**: si los coches ya están cargados
selecciona su índice; si aún no, queda pendiente en el estado y se resuelve en la primera emisión
(nunca se pierde por carrera).

## Copy (9 locales)
- `home_parking_view_vehicle` — *View vehicle* / *Ver vehículo*. Nombra el sujeto, no la pantalla.
- `vehicle_view_on_map` — reutiliza el sentido de `history_view_map` (*View on map* / *Ver en mapa*)
  pero con key propia: el que ya existe cuelga del historial y podría divergir. ⛔ Vocabulario:
  aquí se habla del **aparcamiento** (lo tuyo), nunca de una plaza [COPY-SPOT-IS-NOT-A-PARKING-001].

## Criterio de éxito
- Desde el modal de un coche aparcado, `Ver vehículo` abre la ficha **de ese coche** (con 2+ coches,
  el pager arranca en el correcto), y la bottom nav marca Vehículos.
- Desde la ficha de un coche con aparcamiento, tocar su bloque de estado abre ese aparcamiento en
  el mapa: Home si sigue vivo, detalle del historial si ya se cerró.
- `Me voy` conserva su sitio, su estilo y su diálogo; solo cambia el icono.
- Volver de la ficha a Home deja Home como estaba.
- Compila `assembleMockDebug` + `assembleProdDebug`; suite verde; paridad de los 9 locales
  (`LocaleParityGuardrailTest`).

## Implementado
- `ParkingPeek`: `Me voy` cambia a `Icons.AutoMirrored.Rounded.Outbound` (⚠️ `Icons.Rounded.Outbound`
  está **deprecado** y con `-Werror` rompe el build) y debajo nace `Ver vehículo` (`Outlined`,
  `DirectionsCar`), separado por los mismos 8 dp que ya usa el modal de aparcar. El botón **no
  aparece** si la sesión no tiene `vehicleId`: un botón que no lleva a ninguna parte es peor que su
  ausencia.
- Canal: `HomeSheetAction.OpenVehicle(vehicleId)` → traducción única en `HomeSheetSection` →
  `onOpenVehicle` de `HomeScreen` → `App.kt`.
- `App.kt`: la pestaña acepta `vehicles?vehicleId={vehicleId}`; nace `navigateToVehicle` (sin
  `restoreState`, ver la trampa arriba) y `NavDestination.tabRoute()`; `currentTabRoute` sustituye a
  `currentRoute` en la visibilidad de la bottom nav y en su selección, y la dirección del
  deslizamiento compara por ruta base.
- `VehiclesScreen(focusVehicleId)` → `VehiclesIntent.FocusVehicle(id)` →
  `VehiclesState.pendingFocusVehicleId`, resuelto en la primera emisión que contenga el coche.
- `VehiclePageContent`: el **bloque de estado entero es el enlace** — tocable, con chevron, y solo
  si hay sesión. Nació como link verde `Ver en mapa →` bajo las líneas y se retiró al verlo en
  device (user, 15-09): metía un cuarto elemento suelto en una card cuyo bloque de estado ya nombra
  el sitio del que habla. La dirección ES el enlace, el área táctil pasa de un texto de 20 dp a las
  dos líneas, y el chevron es como la app dice "esto abre algo" en el modal. La key
  `vehicle_view_on_map` sobrevive como `contentDescription` del chevron.
- Destino según el estado de la sesión: viva → `ViewLiveParkingOnMap` (Home), cerrada → el
  `ViewOnMap` de siempre (detalle del historial).

## Verificación
- 4 tests nuevos en `VehiclesViewModelTest` (19 en total, verdes): foco con la lista ya cargada,
  **foco pedido ANTES de que llegue la lista** (la carrera real de este botón) y coche inexistente.
- Suite completa `--rerun-tasks`: **2957 tests, 0 fallos** — con la paridad de los 9 locales.
- ✅ **En device (Oppo, release firmado con la llave del repo)**: «Ver vehículo» abre la ficha del
  coche del modal; el bloque de estado de la ficha devuelve a Home con la cámara sobre el coche y su
  modal abierto, y la bottom nav marcando Inicio.
- `:app:assembleMockDebug` + `:app:assembleProdDebug` → OK.
- ⏳ Falta en device: que el salto abra el coche correcto con 2+ coches y que volver a Home lo deje
  como estaba.

## Consumidores auditados
| Sitio | Qué asume | Cierre |
|---|---|---|
| `App.kt` · `BOTTOM_NAV_ROUTES` | la ruta de pestaña es exacta | ✅ comparación por ruta base |
| `App.kt` · `BOTTOM_NAV_TAB_ORDER` (dirección del slide) | idem | ✅ comparación por ruta base |
| `AppBottomNavigation` (`currentRoute == item.route`) | idem | ✅ recibe ya la ruta base |
| `App.kt` · `navigateToTab` | toda navegación de pestaña restaura estado | ✅ el salto con coche no restaura |
| `VehiclesScreen` / `VehiclesViewModel` | el índice seleccionado solo lo mueve el usuario | ✅ petición de foco resuelta sin carrera |
| `ParkingPeek` (previews + galería mock) | el footer tenía un solo botón | ✅ ambos previews y `StateGalleryScreen` repasados |
| `HomeReleaseDialog` | `Me voy` = `Logout` autoMirrored | exento — es el diálogo, no el footer, y ahí no compite con ningún coche |
