# HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001 · La cámara encuadra lo que la lista enseña

**Estado:** 🔵 En revisión · rama `bugfix/HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001` ·
cierra **3 de las 4 partes** de la issue **#87**

## Problema

Reportado en device. Tres síntomas, y resultaron ser **dos causas**, no tres.

1. Al pulsar el tab de parkings **el mapa vuela donde no hay ningún parking**; quitando los filtros,
   ese sitio sí aparece.
2. Buscando **Madrid** desde Italia, la lista sigue siendo la italiana y cambiar de pestaña
   **devuelve la cámara a Italia**.
3. Cambiar de carril deja el scroll tan arriba que *«cada vez que cambie tiene que volver a
   scrollear para bajar al contenido»*.

## Causa 1 — el encuadre leía una lista que la hoja no pinta

`laneFirstPoint()` prometía en su propio KDoc leer *«las mismas listas que pinta la hoja»*. Era
verdad cuando se escribió — y **dejó de serlo en cuanto entraron los filtros** (#43, #42):

| Carril | Leía | La hoja pinta |
|---|---|---|
| Plazas | `filteredNearbySpots()` ✅ | igual |
| Parkings | `parkings.parkings` ❌ **cruda** | `publicParkingsShown` (orden + filtro) |
| Gasolineras | `fuel.stations` ❌ **cruda** | `fuelStationsShown` (orden + producto) |

Las tres ramas leen ahora la lista **pintada**. Eso cierra el síntoma 1.

## Causa 2 — se encuadraba una respuesta que iba de otro sitio

El síntoma 2 no es el filtro: es que la lista **contesta sobre Italia** mientras la cámara está en
Madrid. Encuadrar su primer elemento **devuelve la cámara de donde el usuario acaba de sacarla**.

⛔ **Y aquí había un invariante a punto de duplicarse.** `refreshNearby` ya decide si una respuesta
sigue cubriendo la cámara (`shouldRefetch` para gasolineras, `sameZone` para parkings) para saber si
hay que repreguntar. El encuadre necesita **la misma pregunta leída al revés**. Escribirla otra vez
habría sido garantizar que un día discrepen — exactamente lo que acaba de pasar con las listas.

Nace `ViewportRefetch.answerCoversCamera(...)`, y el encuadre no dispara hasta que llega la respuesta
de aquí, que el carril ya está pidiendo.

## Causa 3 — `animateScrollToItem(0)` sobrepasa

El índice 0 es el principio de **toda la hoja**: por encima de la tira de coches y de la superficie
de detección. «Volver al principio del carril» dejaba al usuario por encima de todo eso.

Ahora va a la **cabecera del carril** (`nearbyLensIndexOrNull()`), que ya tenía clave pública porque
el checklist navega hasta ella. ⛔ Sin cabecera compuesta **no se mueve nada**: quien está mirando
sus coches no ha pedido que le desplacen la hoja.

El helper devuelve `null` en vez de inventarse un destino porque **cada uso quiere otro fallback**:
el checklist baja al último item (todo lo que hay bajo las pestañas es el carril); el cambio de
carril prefiere quedarse quieto.

## Lo que esta rama NO cierra

⏳ **Parte 3 de la issue**: al volver de otra pantalla se recuerda la pestaña pero no la cámara. Es
un problema de restauración de estado, no de encuadre, y merece su propia mirada.

## Tests

`HomeSlicesTest`, 2 nuevos, **los dos falsificados** (con la regla vieja fallan):
- el filtro esconde el más cercano → se encuadra **el que la lista enseña**;
- una respuesta sobre Roma **no** encuadra una cámara puesta en Madrid, y **sí** cuando la cámara
  sigue en Roma.

⚠️ El primero **no discriminaba** en su primera versión: el helper de test daba las mismas
coordenadas a todos los parkings, así que el assert pasaba con la regla vieja. Se le dieron
coordenadas distintas — un testigo que no puede fallar no es un testigo.

## Pendiente

⏳ **Device.** Sin captura del estado final: no hay móvil conectado.
