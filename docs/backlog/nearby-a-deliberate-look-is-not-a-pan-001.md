# NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001 · Pedir un sitio no es arrastrar hasta él

**Estado:** 🔵 En revisión · rama `feature/NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001` ·
cierra la **issue #44**

## La pregunta de la issue

> «¿Por qué tenemos esta condición? ¿En qué nos ayuda? Si salgo de mi casa y hago un viaje y quiero
> ver los parkings o gasolineras de otra ciudad para cuando llegue…»

**Para qué existe** (`NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001`): el carril SIGUE a la cámara, y cada
asentamiento pasada media radio dispara una petición a un servicio público. Arrastrar el mapa media
España son cientos. El tope, además, evitaba una mentira concreta: enseñar las gasolineras de tu
ciudad sobre el mapa de otra.

**Qué estaba mal**: el tope estaba en **20 km** —la provincia, no el viaje— y, sobre todo, **no
distinguía arrastrar de pedir**. Quien busca «Cádiz» y abre el carril de parkings ha hecho UNA
pregunta sobre Cádiz; contestarle «demasiado lejos» es negarle lo único que pidió.

## Diseño

- **`isWorthAsking(..., deliberate)`**: abrir el carril (`NearbyTrigger.ENTER`) no tiene tope. Es
  una petición, no un reguero. El precalentamiento (`WARM_UP`) y el seguimiento (`CAMERA`) lo
  mantienen, que son los que se multiplican.
- **`MAX_FROM_USER_METERS`: 20 km → 150 km** — el destino de un viaje de hoy. Asumible porque la
  respuesta se **cachea por celda** (cobertura OSM en Room): una región preguntada una vez no se
  vuelve a preguntar.
- **Plazas: 2 km → 5 km** (`ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS`), el mismo
  alcance que las gasolineras. Era el carril que menos lejos veía, y es el producto de la app.
- La frase de «demasiado lejos» **deriva la cifra de la constante** (`radiusString`), así que pasa a
  decir 150 km sola, en los nueve idiomas y en millas donde toca.

## Criterio de éxito

- Buscar otra ciudad y abrir parkings/gasolineras **contesta**, esté donde esté.
- Arrastrar el mapa 300 km sigue sin disparar peticiones.
- Las plazas se ven en un radio mayor alrededor de donde mires.

## Preguntas (para la revisión)

1. **150 km** es una decisión de producto, no una medida: es «hora y media de coche». Si te sabe
   corto o largo, es una constante.
2. **Plazas a 5 km**: más documentos por consulta en Firestore. No es gratis, pero es el mismo
   alcance que ya tienen las gasolineras. Si ves el mapa cargado de pines, se baja.
