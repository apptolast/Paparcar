# UI-PEEK-STEPS-BETWEEN-PINS-001 · El peek pasa de un pin al siguiente con flechas, en vez de desplegar una lista

**Estado:** ✅ Done · rama
`feature/UI-PEEK-STEPS-BETWEEN-PINS-001-peek-stepper` · worktree `../Paparcar-peek-stepper`

Verificado en device (Redmi `5f8991cb`, 27-08): clúster `‹ › ×` y swipe en el peek de plaza y en el
de coche, page-turn direccional, convivencia con el arrastre vertical de la hoja, app bar del
histórico y el copy corregido.

**Rebase 27-08, en dos pasadas, hasta `3f608e5e`** (11 commits de master: refactors de detección,
`targetSdk` 37, Room downgrade, docs y el merge de AUTH-PROVIDERS-EXPLICIT-001, que entró en master
mientras se rebaseaba — de ahí la segunda pasada, limpia). Un solo conflicto, en
`ParkingLocationScreen.kt`: master retocó
`AddressHeroRow` mientras esta rama la borraba al pasar la tarjeta a `PapSheet`. Resuelto
borrándola — ya no la llama nadie — y adoptando de master el acceso con smart cast
(`session.address.region`). Además master ha activado **`-Werror`**, y eso convirtió en error un
`if (hasStepper && stepper != null)` redundante en `PapSheet`; ahora es un `takeIf` con smart cast.
Tras el rebase: **1.685 tests en verde** y los dos flavors compilando.

## Problema

El peek de una plaza seleccionada terminaba con una fila-toggle **"Plazas cercanas"**
(`home_spot_peek_show_list`, `HomeSheetAction.ToggleSpotList`): al pulsarla el sheet se abría y
volvía a aparecer DEBAJO la lista completa de plazas, con la plaza abierta arriba.

No cuadra:

- La lista y el peek hablan del mismo conjunto en dos lenguajes distintos, apilados uno sobre otro
  en la misma superficie. El usuario ya eligió un pin; volver a enseñarle la lista entera es
  deshacer su elección sin cerrarla.
- Es un tercer estado del sheet (peek / peek+lista / browse) que sólo existe en un caso y que
  arrastra geometría propia: `capExpandAtPeek`, un `LaunchedEffect(spotListExpanded)` que anima el
  sheet, y un auto-scroll a la fila del spot con un cálculo de índice
  (`homeSheetSpotItemIndex`) que descuenta cabeceras ocultas.
- El coche aparcado no tenía NADA equivalente: con dos coches aparcados, para ir del uno al otro
  había que cerrar el peek y volver a tocar el marcador (o ciclar con el FAB del coche).

Ya existía en el proyecto el gesto correcto, y lleva meses en producción: el **stepper ‹ ›** del
detalle histórico de aparcamientos (`ParkingLocationScreen`, `[HISTORY-DETAIL-002]`).

## Doctrina violada

- **`[UI-LIST-ITEM-001]` / una sola forma por concepto**: "pasar del item abierto al siguiente" ya
  tenía forma en la app (el stepper del histórico). El peek se inventó otra (desplegar la lista).
- **Sistemas, no parches**: el botón de flecha vivía como `private fun StepperButton` dentro de
  `ParkingLocationScreen.kt`. En cuanto lo necesita una segunda pantalla, o sube a `ui/components/`
  o se copia — y copiarlo es exactamente cómo se separan dos visuales que deberían ser uno.

## Señales / datos disponibles

Todo el orden necesario ya existe y tiene un único dueño:

- **Plazas**: `HomeState.filteredNearbySpots()` — la MISMA lista (filtrada por talla, sin
  retiradas, sin la sesión propia, provisionales al final) que pinta el sheet. El stepper recorre
  ese orden, no `nearbySpots` crudo.
- **Coches aparcados**: `HomeState.activeSessions`, el mismo orden que ya cicla el FAB del coche
  `[MULTI-PARKING-001]`.

## Diseño

### 1. El botón de flecha es un componente, no un privado de una pantalla

`ui/components/PapStepperButton.kt` — extraído de `ParkingLocationScreen`. El histórico pasa a
importarlo. Un solo visual para los tres sitios.

### 2. Dónde va: el clúster `‹ › ×` del trailing de la cabecera

> **Tercera** versión, y la que queda. Las dos anteriores se montaron, se instalaron en el móvil y
> las rechazó el user; se dejan escritas porque cada una enseñó algo:
> 1. **Fila al pie**, bajo un divisor → *una fila extra al final no pertenece a nada y compite con
>    las acciones*. Lección: el stepper **no es una acción de la tarjeta, es chrome de la tarjeta**.
> 2. **Flanqueando la cabecera por los cantos** → el chrome ya estaba en el carril correcto, pero
>    partía la cabecera en tres y en los extremos el hueco reservado la dejaba descolocada.
>
> Lo que las dos tenían en común: inventaban un carril nuevo. El carril ya existía — el de la ×.

Las dos flechas entran en el **trailing** del header, delante de la ×: `‹ › ×`. Consecuencias:

- El peek **no crece**: un chevron de 32dp es más bajo que el lead tile de 46dp, así que la altura
  reservada de la cabecera —y con ella la altura medida del peek, el corte de plegado y el divisor
  peek/nav [UI-SHEET-006]— no se mueve ni un dp.
- La × **se queda exactamente donde estaba**. Ningún estado se queda sin salida.
- El resto del contenido de la tarjeta no se toca en ningún estado.

**El relleno es la jerarquía.** Con los tres dentro de píldoras salían tres círculos tonales
gemelos y la × dejaba de distinguirse de la flecha que tiene al lado. Así que **círculo relleno =
acción sobre esta tarjeta (cerrar); glifo desnudo = chrome que cambia de tarjeta**. Las dos flechas
van pegadas entre sí (son UN control) y el aire —10dp— va antes de la ×.

### 3. El paso se ve y se arrastra

- **Page-turn direccional**: `HomePeekHandle` ya envolvía el peek en un `AnimatedContent`; ahora
  distingue un PASO (mismo tipo de pin, otro id) de un cambio de estado. Pulsar › mete la tarjeta
  nueva desde la derecha y echa la vieja por la izquierda; ‹ al revés. Todo lo demás —entrar a un
  pin, volver a browse— conserva la transición vertical, que es lo que ESO es. La dirección sale de
  `HomePeekSlice.stepDirection`, que consulta las mismas listas de las que se construyen las
  flechas: la flecha pulsada y el sentido del deslizamiento no pueden discrepar.
- **Swipe**: arrastrar la tarjeta a la izquierda = ›, a la derecha = ‹, con el mismo umbral de 64dp
  del histórico. Es `detectHorizontalDragGestures`, que espera slop **horizontal**, así que el
  arrastre vertical de la hoja lo sigue recibiendo el `draggable` de debajo — verificado en device:
  swipe lateral pasa de plaza, arrastre vertical mueve la hoja sin cambiar de pin.

### 4. Los extremos: chevron GASTADO, no hueco vacío

El hueco de 32dp **se reserva siempre**, para que el clúster no baile al llegar a un extremo, y por
eso el extremo no puede quedar vacío: un hueco sin causa visible se lee como un fallo de maquetación.
El chevron gastado (alpha 0,25, sin click, sin `contentDescription`) explica su hueco y además dice
el límite: *no hay nada más antiguo que esto*. Es el argumento que ya hacían los botones
deshabilitados del histórico, así que las tres superficies vuelven a comportarse igual.

### 5. El histórico deja de ser una copia a mano de la tarjeta de Home

La tarjeta del detalle era una **reimplementación** del mismo diseño: su propio `Surface` + `Column`,
su propio hero (tile 46dp + título + ciudad), sus propias meta-rows y sus propios paddings (20dp
horizontales frente a los 16 del molde). Misma intención, código distinto — y la copia había
derivado: su cabecera quedaba **más baja** que la de Home porque no tenía la altura reservada a 3
líneas del molde [UI-SHEET-006].

Ahora es literalmente `PapSheet`, el mismo molde: lead = glifo del vehículo, eyebrow = el coche,
title = la dirección, subtitle = la ciudad, meta = fecha + detección con `PeekMetaRow`, actions =
"Navegar a esta ubicación", stepper = las dos flechas. Lo único que NO toma del molde es la × —
esto es una pantalla y su salida es el back de la app bar, así que pasa `trailing = null`.

Mueren con el cambio `AddressHeroRow`, `MetaRow`, `HistoryStepBody`, `heroStepperTopInset()` y 8
tokens locales; y el **swipe deja de estar duplicado**: lo monta el molde en cuanto lleva stepper.
El `AnimatedContent` del page-turn envuelve la tarjeta entera, igual que en Home lo hace
`HomePeekHandle` sobre el peek.

### 6. El histórico deja de etiquetarse a sí mismo

`APARCAMIENTO HISTÓRICO` / `APARCAMIENTO ACTUAL` era un eyebrow DENTRO de la tarjeta que repetía el
nombre de la pantalla, y el único modo de salir era un círculo flotante sobre el mapa. Ahora la
pantalla dice dónde estás en la voz del resto de la app: `PapCollapsingTopBarScaffold` con
**"Historial"** y su flecha atrás — la misma barra de Ajustes, Vehículos, Bluetooth y Registro. Las
dos strings del label quedan retiradas de los 9 locales.

El eyebrow de la tarjeta no se queda vacío: pasa a decir **de qué coche** es este aparcamiento,
exactamente como el peek de Home ("MI SEAT", y "MI SEAT · APARCADO" si la sesión sigue viva). El
nombre lleva el color de vigilancia por el resolver único y el estado se queda en tinta neutra
[UI-COLOR-DOCTRINE-001] — así el dato que sobra (el título de la pantalla) se va y entra uno que
faltaba (el coche), sin inventar strings.

Si no hay pin a NINGÚN lado, no se emiten flechas: la cabecera conserva todo su ancho. Con una sola
plaza cerca, un solo coche aparcado o un solo aparcamiento en el historial, la tarjeta queda
exactamente como estaba antes del ticket.

**Sin contador ni etiqueta** (decisión del user): son exactamente dos flechas.

### 7. La antigüedad de la plaza sube a la tercera línea

El peek de browse ya pone el tiempo en la tercera línea de su cabecera ("Aparcado hace 1 h · a 355 m
de ti"). El peek de una plaza no tenía subtítulo y ponía su antigüedad como una meta-row más, en
cola detrás del ajuste y la distancia — cuando es justo el dato que decide si merece la pena
conducir hasta allí. Pasa a `subtitle` ("Publicada hace 12 min") y **se retira de las meta-rows**,
que no la duplican. La altura no cambia: el molde ya reservaba las tres líneas [UI-SHEET-006].

### 8. Vocabulario: PLAZA es de la comunidad, APARCAMIENTO es tuyo

> Encargo del user en el mismo turno (27-08), hecho aquí a petición suya pese a no ser el tema del
> ticket. La regla queda escrita en `CLAUDE.md` para que no haya que redescubrirla.

Los dos conceptos centrales del producto se llamaban igual **dentro del mismo flujo**: registrar
dónde has dejado el coche era "Mark parking" en el chip del vehículo, "Marcar mi **sitio**" en la
historia de detección y "Marcar mi **plaza**" en el nudge. Y "plaza" es exactamente la palabra con
la que la app nombra la plaza de OTRO. En EN el choque era el mismo: `Mark my spot` contra
`Report a free spot`.

Reparadas 14 claves × 9 locales (111 líneas) donde lo TUYO usaba la palabra de lo AJENO:
`home_det_awaiting_cta_primary`, `home_det_awaiting_sub`, `home_nudge_cta`, `home_nudge_sub`,
`home_det_candidate_sub`, `home_det_watching_parked_sub`, `home_det_ask_sub`,
`home_det_trip_stopped_msg`, `onboarding_step2_desc`, `error_parking_save_failed`,
`settings_auto_detect_desc`, `settings_notif_parking_desc`, `permissions_tier_automatic_promise` y
`home_add_parking_helper_primary_edit` (ahí "spot" era el inglés genérico de "sitio" → `location`).

Dos casos merecen mención porque **nombran las dos cosas a la vez**, y ahora las distinguen:
- `home_det_ask_sub`: *"Guardo tu aparcamiento y publico la plaza cuando te vayas"*.
- `permissions_tier_automatic_promise`: *"Tu aparcamiento se marca y la plaza se libera sola"*.

Lo que NO se toca, porque ahí "plaza/spot" es correcto: todo el vocabulario comunitario
(`home_report_*`, `home_spot_*`, `home_peek_spot_*`, `spot_indicator_*`, `home_release_dialog_*`,
`home_parking_release`, `onboarding_step3_*`), donde se habla de la plaza que se ofrece.

### 9. La pantalla pasa a llamarse por lo que es: `ParkingHistoryDetailScreen`

`ParkingLocationScreen` era el nombre de cuando esa pantalla servía para *elegir* una ubicación.
Hoy es el detalle de UN aparcamiento del historial sobre el mapa — y la app bar que estrena lo dice
en voz alta. Renombrados el fichero y toda su familia MVI, que arrastraba el mismo nombre viejo:

| Antes | Ahora |
|---|---|
| `ParkingLocationScreen.kt` · composable `HistoryParkingDetailScreen` | `ParkingHistoryDetailScreen.kt` · `ParkingHistoryDetailScreen` |
| `ParkingLocationViewModel` / `State` / `Intent` / `Effect` | `ParkingHistoryViewModel` / `State` / `Intent` / `Effect` |
| `ParkingLocationViewModelTest` | `ParkingHistoryViewModelTest` |

⚠️ `UpdateParkingLocationUseCase` **no** se toca pese a contener la misma subcadena: ese sí mueve la
ubicación de un aparcamiento. El renombrado fue por símbolo exacto (`\b…\b`), no por substring.

Los `docs/backlog/*` viejos siguen citando el nombre anterior a propósito: son el registro de lo que
pasó entonces. La doc viva (`ARCHITECTURE.md`) sí queda actualizada.

### 10. Un vecino es una proyección del slice, no lógica en el composable

`HomePeekSlice.spotStep` / `.sessionStep` → `PeekStep(prevId, nextId)`, resuelto por
`PeekStep.of(order, currentId)`: si el id seleccionado no está en la lista (una plaza RETIRADA
sigue seleccionada a propósito pero ya no se ofrece), devuelve `PeekStep.None` — nunca "el
primero de la lista". Es puro y va con test.

### 11. Dar un paso == tocar ese pin en el mapa

`HomeSheetAction.SelectSpot(id)` / `SelectParking(id)` se cablean en `HomeSheetSection` a los
MISMOS lambdas que ya usa el mapa (`onSpotMarkerClick` / `onMyCarMarkerClick`): seleccionar +
mover cámara + asentar el sheet. Así un paso del stepper y un toque en el marcador no pueden
divergir. No se toca el camino de la FILA de la lista, que es otra superficie con su propio
comportamiento.

### 12. Barrido: lo que muere con el toggle

Con la lista ya inalcanzable mientras hay una plaza seleccionada (`showList` en
`HomeBottomSheet`), toda la rama "lista visible CON spot seleccionado" queda muerta y se retira,
no se deja apagada.

## Criterio de éxito

- Peek de plaza, peek de coche aparcado y detalle histórico llevan `‹ ›` en el **trailing de su
  cabecera** (delante de la × donde la hay); en los extremos la de ese lado sale gastada; con un
  solo item no hay flechas y la tarjeta va a ancho completo.
- Pulsar una flecha hace page-turn hacia ese lado, y arrastrar la tarjeta hace lo mismo sin robarle
  el arrastre vertical a la hoja. ✅ verificado en device (Redmi, 27-08).
- El histórico abre con app bar "Historial" + atrás, y su tarjeta ya no se etiqueta. ✅ device.
- La altura del peek (y el divisor peek/nav) no cambia respecto a master.
- Pulsar › abre el siguiente pin **igual** que tocar su marcador (cámara incluida).
- Una plaza retirada (RETRACTED) no ofrece flechas.
- `PeekStep` con test unitario para medio / primero / último / uno solo / id ausente.
- Sin rastro de `spotListExpanded` en el árbol.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `SpotPeek.SpotListToggleRow` | la fila-toggle | ❌ eliminada |
| `HomeSheetAction.ToggleSpotList` | canal del toggle | ❌ eliminado |
| `HomeScreen` `spotListExpanded` + `onToggleSpotList` | estado local | ❌ eliminados |
| `HomeScreen.capExpandAtPeek` | `spot != null && !expanded` | ✅ `spot != null` |
| `HomeSheetPositioning.SheetTransitionEffects` | `LaunchedEffect(spotListExpanded)` | ❌ eliminado |
| `HomeBottomSheet.showList` | `!isSpotSelected \|\| spotListExpanded` | ✅ `!isSpotSelected` |
| `HomeSheetContent.homeSheetItems` | ramas `isSpotSelected` (oculta vehículos, cabecera, story) | ❌ muertas → retiradas |
| `homeSheetSpotItemIndex` + `LaunchedEffect` auto-scroll | índice de la fila del spot en la lista | ❌ muertos → retirados |
| `HomeBrowseListSlice.selectedSpotId` | resaltar la fila del spot en la lista | ❌ inalcanzable → retirado |
| `HomeSpotRow(isSelected)` | estilo de fila seleccionada | ✅ se queda (param del componente + su preview); ya no se le pasa nada |
| `HomeMapSection/PaparcarMapView selectedSpotId` | marcador resaltado | ✅ intacto (nada que ver con el sheet) |
| `ParkingLocationScreen.StepperButton` | privado de la pantalla | ✅ movido a `ui/components`; pierde la píldora y el flag `enabled` (muerto al unificar extremos) |
| `ParkingLocationScreen` cabecera-pager | flechas dentro de `PapSectionHeaderRow` | ✅ pasan al trailing de la ficha; el eyebrow desaparece |
| `AnimatedContent` del histórico | el bloque que pagina | ✅ extraído a `HistoryStepBody`, para poder emitirlo con y sin flechas |
| `FloatingBackButton` + sus 3 tokens | único modo de salir del histórico | ❌ retirado → lo sustituye el `navigationIcon` de la app bar |
| `HistoryDetailSheet(isActive)` | alimentaba el eyebrow | ❌ param retirado (pantalla + galería mock barridas) |
| `parking_detail_section_label` / `_active_section_label` | el eyebrow | ❌ retiradas de los 9 locales |
| `statusBarsPadding()` del chip "recalculando" y de la tarjeta de ruta inferida | el mapa llegaba hasta la status bar | ✅ ahora cuelgan del `contentPadding` de la app bar |
| `AnimatedContent` del peek (`HomePeekHandle`) | toda transición era vertical | ✅ un PASO se distingue y desliza en horizontal; el resto, igual |
| `HomeBottomSheet` `.draggable` vertical | era el único gesto de la hoja | ✅ convive: el swipe del peek espera slop horizontal (verificado en device) |
| Dev Catalog (`StateGalleryScreen`) | variantes de peek | ✅ variantes de extremos añadidas |
