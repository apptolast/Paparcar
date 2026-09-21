# HOME-A-LANE-THAT-CANNOT-ANSWER-HERE-SHOWS-NO-ROWS-001 · el carril no enseña filas de otra ciudad

**Estado:** ✅ Done · rama `bugfix/HOME-A-LANE-THAT-CANNOT-ANSWER-HERE-SHOWS-NO-ROWS-001` · issues #98, #96, #97

## Problema
Reporte de campo, tras verificar el arreglo de #87:
> «el bug de Italia-Madrid sigue estando: si clico un elemento de la lista al llegar a Madrid me
> lleva a Italia, quizás debería dejar que cargara, pero ¿es el comportamiento que queremos?»
> «el shimmer aparece en el peek donde aparece el número de elementos, pero no en la lista»

## Doctrina violada
[HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001] enseñó a la **cámara** a no encuadrar una respuesta
que no cubre donde estás mirando, y dejó a la **lista** sin aprenderlo. Resultado: la cámara dejó de
mentir y la lista no. Tocar una fila italiana es una orden legítima, así que te lleva a Italia.

Los dos síntomas son **el mismo defecto**: la cuenta del peek sabía que estaba cargando (shimmer) y
las filas se pintaban asentadas. Dos superficies de la misma tienda en estados distintos.

## Diseño
Una sola transformación sobre el ESTADO, donde vive la cámara, antes de derivar ninguna porción:

```kotlin
val shown = remember(state, cameraLat, cameraLon) { state.laneAnsweringHere(cameraLat, cameraLon) }
val mapSlice = remember(shown) { shown.toMapSlice() }   // …y peek y browse
```

`laneAnsweringHere` vacía la tienda de cada complemento cuya respuesta no cubra la cámara, con el
mismo predicado que ya decide el encuadre y la recarga (`ViewportRefetch.answerCoversCamera`). Como
todas las porciones salen de ahí, mapa, peek, lista, selección y encuadre **no pueden discrepar**.

⛔ **No hizo falta ni un composable nuevo.** `NearbyRowsSkeleton` ya se dibuja con
`isLoading && lista vacía`; no salía porque la lista no estaba vacía: tenía Italia. Misma forma que
el `takeUnless { isLoading }` de PARKINGS-A-REMEMBERED-MAP: la frase ya era correcta, le faltaba el
dato honesto debajo.

⛔ **«Vacía» se comprueba antes que «de aquí».** Una tienda nunca preguntada no tiene ancla, y sin
ancla el predicado dice —con razón— que no va de aquí. Sin esa guarda, una lista vacía «se vaciaba»
otra vez y el estado se copiaba en cada movimiento de cámara, deshaciendo
[HOME-A-PROJECTION-IS-COMPUTED-ONCE-001]. Lo destapó un `assertSame` del propio testigo.

## Dos arreglos que viajan con éste (misma pantalla, misma sesión de revisión)

**#96 · el anillo de selección lleva el tema CONTRARIO** [MAP-A-SELECTION-RING-WEARS-THE-OTHER-THEME-001].
El disco de parking marcaba su selección con `paper` (= `surface`): negro sobre tesela oscura. Pasa a
`ink`. No es una excepción nueva — el puck de plaza ya marca la suya con `ink` («outer onSurface
ring, max contrast») y el disco era el que divergía. Regla: *el anillo de reposo es un separador y
lleva el color del tema; el de selección es un realce y lleva el contrario.*
⚠️ La cápsula (parking con tarifa y gasolinera) usa `accent` a alfa plena, que sí contrasta en los
dos temas: queda como está, y dicho.

**#97 · el chip enseña el combustible por el que el carril ESTÁ cotizado**
[FUEL-THE-CHIP-SHOWS-THE-FUEL-THE-LANE-IS-QUOTED-IN-001]. La barra leía `fuelProduct` en crudo
(null mientras el usuario no elige) y decía «ningún filtro» con la lista ya filtrada por gasolina.
Pasa a leer `pricedFuelType`.
⛔ Y **desaparece el apagado**, que era falso: el carril siempre está cotizado por algún combustible,
así que un chip apagado no describía ningún estado del mundo. Lo que hacía `takeUnless { isOn }` era
devolver el carril al coche — que la mitad de las veces es el MISMO combustible: el chip se apagaba y
la lista no cambiaba. Ahora es elección única, como «Cercanas / Más baratas».
⚠️ Garaje sólo eléctrico: `fuelProductsAvailable` sólo ofrece lo que el feed cotiza, así que no se
enciende nada. Correcto: mejor ninguno que el primero de la lista.

## Criterio de éxito
- `should_showNoRowsAtAll_when_theAnswerIsAboutSomewhereElse` — desde Madrid, ni una fila ni un pin
  italianos, y la lista declara que está preguntando.
- `should_emptyOnlyTheLaneThatCannotAnswer_when_theOtherStoreIsStillHere` — dos anclas, dos
  veredictos: vaciar las dos tirararía una respuesta buena.
- `should_lightTheCarsFuelChip_when_theUserHasNotChosenOne` — el chip encendido ES por el que se
  cotiza, y elegir a mano sigue mandando.

## Consumidores auditados
`grep` de `toMapSlice()` / `toPeekSlice()` / `toBrowseListSlice()`:

| Sitio | Veredicto |
|---|---|
| `HomeScreen` (las tres) | **cerrado** — derivan de `shown` |
| `HomeSheetPreviews` · `StateGalleryScreen` | exentos — construyen su estado a mano, sin cámara |
| `laneFirstPoint` | **cerrado** — pasa a derivar de `laneAnsweringHere`, así que la regla vive en UN sitio en vez de repetirse por carril |
