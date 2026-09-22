# HOME-A-PAN-MUST-NOT-EMPTY-THE-MAP-001 · Una respuesta nueva SUSTITUYE a la anterior; no la borra primero

**Estado:** ✅ Done · mergeado en master el 22-09-2026

## Problema

Issue **#119**: *«Cada vez que paneo con parkings puesto, siempre que muevo un poco para un lado, la
lista se vuelve a cargar, los markers desaparecen y vuelven a aparecer. Lo ideal es que siguieran
los markers y la lista intacta hasta que vuelvan a cargar datos… se ve bastante feo y poco
profesional. Las gasolineras deberían actualizarse si cambiamos de filtros, porque tengo que volver
a panear.»*

## Causa raíz — y es UNA, no dos

`HomeScreen:422` pasa el estado por `laneAnsweringHere` antes de derivar **las tres** porciones
(mapa, peek, lista). Esa función **vacía el carril** en cuanto la respuesta que tiene en la mano
deja de estar «asentada» para la cámara. Y el umbral de «asentada» es el mismo que el de
«hay que volver a preguntar»:

```kotlin
fun answerCoversCamera(...) = if (byZone) ParkingZoneGrid.sameZone(...)
                              else !shouldRefetch(...)
```

Su KDoc lo dice con orgullo: *«es la misma pregunta que decide si hay que volver a preguntar, leída
al revés»*. **Ahí está el error.** «¿Vuelvo a preguntar?» y «¿puedo seguir enseñando lo que tengo?»
no son la misma pregunta leída al revés: la primera es barata y conviene contestarla PRONTO, la
segunda cuesta la pantalla entera y hay que contestarla TARDE.

Con los parkings se ve en su forma más cruda: `sameZone` compara **celdas de una rejilla de 3 km**,
y es una igualdad exacta. Si la cámara está sobre una línea de la rejilla, **un paneo de un metro**
cambia de celda, el carril se vacía, y la respuesta nueva —que cubre 5,2 km alrededor del centro de
celda, o sea casi lo mismo que la anterior— repinta lo que acababa de borrarse. Arrastrar de un lado
a otro sobre una linde parpadea indefinidamente.

Y encima, durante la ventana del *debounce* de asentamiento, el carril está vacío **sin estar
cargando**: `isLoading` todavía es `false`, así que no sale ni el esqueleto — sale el estado vacío,
que es una frase sobre el mundo («no hay parkings aquí») dicha mientras nadie ha preguntado nada.

### Lo de los filtros es el MISMO bug

*«Tengo que volver a panear si cambio de filtro para que cargue.»* Cambiar un chip no mueve la
cámara, así que no dispara el asentamiento; si el carril está vaciado por deriva, **lo único que lo
resucita es un paneo**, porque es lo que trae un ancla nueva. No hace falta arreglar los filtros
aparte: en cuanto el carril deja de vaciarse, el chip filtra sobre las filas que siguen ahí.

## Doctrina — la que esto respeta y la que corrige

⛔ **No se revoca [HOME-A-LANE-THAT-CANNOT-ANSWER-HERE-SHOWS-NO-ROWS-001].** Su caso es real y sigue
cubierto: buscando Madrid desde Italia, las filas italianas no pueden quedarse tocables, porque
**tocar una te teletransporta**. Eso es un SALTO deliberado a otro sitio.

Lo que aquel ticket no distinguió es que un **paneo** no es un salto. Aplicar la misma regla a los
dos convierte la protección contra un teletransporte en un parpadeo cada tres metros.

## Diseño

**Dos preguntas, dos umbrales, y el de enseñar es el generoso:**

| Pregunta | Quién la hace | Umbral |
|---|---|---|
| ¿vuelvo a preguntar? | el refresco | media radio (5 km → 2,5) · cambio de celda | **sin tocar** |
| ¿puedo seguir enseñando? | las tres porciones | **hasta dónde ALCANZA la respuesta** | nuevo |
| ¿puedo volar la cámara ahí? | `laneFirstPoint` | asentada, el de hoy | **sin tocar** |

`ViewportRefetch.answerStillReaches(...)` responde la de en medio con **la geometría que cada
carril ya tiene escrita**, sin inventar constantes:

- **gasolineras** → la respuesta es un disco de `DEFAULT_RADIUS_METERS` (5 km) alrededor del ancla;
  mientras la cámara esté dentro, esas filas son de aquí.
- **parkings** → la respuesta es un disco de `ParkingZoneGrid.ZONE_RADIUS_METERS` (5,2 km) alrededor
  del **centro de la celda** del ancla, que es literalmente lo que se pidió.

Como el umbral de repreguntar (media radio / cambio de celda) se cruza **mucho antes** que el de
alcance, en la práctica la petición ya está en vuelo —y casi siempre servida de disco o de caché—
cuando el usuario llega al borde. El relevo ocurre con datos en pantalla todo el rato.

⛔ **La cámara NO se relaja.** `laneFirstPoint` sigue con el umbral estricto: enseñar una fila que
está a 4 km es correcto; volar hasta ella porque es la primera de la lista es la cosa que
[HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001] existe para impedir.

⛔ **Y no hace falta ninguna frase nueva.** Las filas con `isLoading` ya se saltan el esqueleto
[PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001], así que el refresco se ve como lo que
es: las mismas filas, y luego otras.

## Criterio de éxito

- Paneando sobre una linde de celda con parkings puestos, ni los markers ni las filas desaparecen.
- Saltar a otra ciudad (buscador) SÍ vacía: ninguna fila de allí queda tocable.
- Cambiar un chip de filtro surte efecto sin tener que mover el mapa.
- La cámara sigue sin volar a una respuesta de otro sitio.

## Consumidores auditados

`grep answerCoversCamera|laneAnsweringHere`:

| Call site | Qué pregunta | Veredicto |
|---|---|---|
| `HomeScreen:422` → mapa · peek · lista | ¿puedo enseñarlo? | **el bug** → alcance |
| `HomeSlices.laneFirstPoint` | ¿puedo volar ahí? | **sin tocar** — sigue estricto |
| `HomeViewModel.refreshNearby` | ¿vuelvo a preguntar? | **sin tocar** — usa `shouldRefetch` / `sameZone` directamente |
