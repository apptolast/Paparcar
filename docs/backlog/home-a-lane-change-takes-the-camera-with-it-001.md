# HOME-A-LANE-CHANGE-TAKES-THE-CAMERA-WITH-IT-001 · Cambiar de carril mueve el mapa y la lista

**Estado:** 🔵 En revisión · rama `feature/HOME-A-LANE-CHANGE-TAKES-THE-CAMERA-WITH-IT-001` ·
cierra la **issue #47**

## Problema

Pedido por el product owner (#47): al cambiar de pestaña entre plazas / parkings / gasolineras, la
cámara se queda donde estaba —mirando un sujeto que ya no se pinta— y la lista, donde la dejaste.
«Para no dejar el mapa sin foco al usuario; si no está cargado, pues cuando carguen los elementos.»

## Diseño

- **`HomeState.laneFirstPoint()`** — el primer elemento del carril activo, leído de **las mismas
  listas que pinta la hoja**. Una segunda fuente con otro orden convertiría el encuadre en una
  coordenada que no corresponde a ninguna fila.
- **La cámara** se mueve en `HomeCameraEffects`, por la puerta deliberada `goToPlace`, que suelta el
  follow del conductor y trata el zoom como SUELO — ir al primer elemento no puede costarle al
  usuario volver a acercar [MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001].
- ⛔ **Espera a que el carril tenga algo.** Parkings y gasolineras se piden AL ENTRAR, así que en el
  instante del cambio la lista está vacía: el encuadre queda **armado** y se dispara con la primera
  lista no vacía. Es literalmente lo que pedía la issue.
- ⛔ **La primera composición no encuadra.** El carril de arranque es PLAZAS, y ahí manda el enfoque
  inicial (`FOCUS-001`: tu coche, o tú). Esto sólo reacciona a un cambio **deliberado** de carril.
- **La lista** vuelve a su principio (`animateScrollToItem(0)`) en el mismo cambio: mapa y lista
  tienen que estar mirando lo mismo.

## Criterio de éxito

- Tocar «Parkings» con el mapa en otro sitio: la cámara viaja al primer parking de la lista en
  cuanto la respuesta llega, y la lista está arriba.
- Volver a «Plazas» hace lo propio sin re-disparar el enfoque inicial.
- Arrancar la app sigue encuadrando lo de siempre.

## Preguntas (para la revisión)

1. **La lista va al índice 0**, que es la cabecera del carril con sus chips, y la primera fila queda
   justo debajo. Si prefieres que aterrice **sobre la fila**, hay que indexar los items de la hoja
   para saber en cuál empieza el carril — se puede, pero es más maquinaria.
2. **Encuadro el primer elemento, no todos.** Alternativa: `framePlaces` con bounds sobre los N
   primeros, que enseña el grupo pero aleja la cámara. Dime si lo prefieres.

## Segunda vuelta (review del user, 20-09)

Respondidas sus dos preguntas —lista al índice 0 (sí) y encuadrar el primer elemento y no el grupo
(sí)— y añadido lo que faltaba:

> «A parte del scroll de la lista, deberíamos levantar la modal a la mitad más o menos para que se
> muestren la lista de elementos.»

Tiene razón y es lo que cerraba el gesto: desde el **peek**, cambiar de carril movía la cámara y
devolvía la lista a su principio **sin que se viera ninguna de las dos cosas**. El usuario pedía
gasolineras y lo único que cambiaba en pantalla era una pestaña.

La hoja sube ahora a `BrowseDetent.HALF`, con el mismo `snapSpec` que usa el gesto de arrastre — así
el movimiento se lee como el de siempre y no como un salto nuevo.

### Las dos guardas, que son la mitad de la decisión

- ⛔ **Sube, NUNCA baja.** Si la hoja ya estaba por encima de la mitad (expandida o entera) se queda
  donde el usuario la dejó. Pedir otro carril no es pedir menos sitio, y encogerle la lista a quien
  la tenía abierta sería quitarle algo que no ha soltado. Con el offset, «más arriba» es un valor
  **menor**, así que la condición es `sheetOffsetPx.value > halfOffsetPx`.
- ⛔ **No en la primera composición.** Home abre en peek y ahí se queda hasta que alguien toque una
  pestaña; el efecto sólo **arma** en su primer paso. Es la misma guarda que ya llevaba el encuadre
  de cámara, y por el mismo motivo.

### Verificación

`./gradlew testAll :app:compileMockDebugKotlin` → BUILD SUCCESSFUL.

⏳ **Sin captura del estado final**: no hay device conectado (`PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001`).
Ésta es de movimiento, así que lo que hay que juzgar en mano es si media hoja es la altura correcta
o se queda corta.
