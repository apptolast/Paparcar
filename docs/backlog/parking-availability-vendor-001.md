# PARKING-AVAILABILITY-VENDOR-001 · Plazas libres de parking, en todo el mundo, comprándolas

**Estado:** 🧊 Diferido — sin fecha. Abierto el 09-09-2026 al cerrar el alcance de
[`parkings-on-the-map-001.md`](parkings-on-the-map-001.md) (F6a).

## La pregunta

Existen APIs que dan **plazas libres ahora mismo** en parkings cerrados, con cobertura de decenas de
países: **TomTom Parking Availability** (refresco ~10 min, tabla de cobertura pública que incluye
España, y productos hermanos de *Parking Prices* y *On-Street*) y **HERE Off-Street / On-Street
Parking**. Ninguna es gratuita ni abierta.

## Por qué NO entra ahora — dos puertas, y la primera no es el precio

1. ⛔ **No hay puerta de autoservicio.** La Parking Availability de TomTom **no está en el tier
   gratuito ni en el Pay-As-You-Grow**: el acceso se pide a su equipo de ventas. No se puede sacar
   una key y MEDIR su cobertura real en España antes de decidir, que es como este proyecto decide
   todo lo demás. HERE ni siquiera publica cobertura por país: se consulta con un comercial.
2. 🔑 **Su producto ON-STREET compite con el nuestro.** «Probabilidad de encontrar sitio en esta
   calle» es exactamente la pregunta que Paparcar responde con la comunidad y con detección
   automática, y con dato real en vez de estadístico. Pagar por una estimación de lo que la propia
   app mide en directo es comprar la competencia de uno mismo.
   ✅ Lo que sí es **complementario** es el OFF-STREET: cuántas plazas quedan dentro de un parking
   cerrado. Eso los usuarios no lo pueden aportar, y es justo el hueco que deja F6a.

## Si algún día se retoma

- Pedir acceso de evaluación y **medir primero**: cuántos parkings devuelve en Madrid, Málaga y una
  ciudad pequeña, contra los que ya da OSM, y cuántos traen ocupación de verdad.
- Contrastar contra la vía gratuita equivalente: `PARKINGS-LIVE-OCCUPANCY-001` (F6b) da ocupación
  real de Madrid y Málaga por open data municipal, y `parkapi-sources` cubre decenas de ciudades
  DE/CH/AT/DK. Si el solape es alto, el contrato no compra gran cosa.
- Revisar la cláusula de **caché y almacenamiento** antes que el precio: es lo que decide si el dato
  puede vivir en nuestro backend o hay que consultarlo por usuario y por vista.
- Alcanza a `PARKING-AFFILIATE-001` (Parclick/Parkimeter/SpotHero): ahí el dinero va en la otra
  dirección — comisión por reserva — y suele traer precio y disponibilidad de sus propios parkings.
