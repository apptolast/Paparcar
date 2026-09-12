# FUEL-GERMANY-001 · Alemania, tercer país de la saga de combustible

**Estado:** 📋 Backlog — **sin medir**. No tiene rama ni worktree. Decisión del user (10-09-2026):
queda aparcado mientras se cierra `OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001`.

Orden fijado en [`fuel-france-a-price-carries-its-own-hour-001.md`](fuel-france-a-price-carries-its-own-hour-001.md):
**Francia → Alemania → Italia**. España ✅ (Minetur) · Francia ✅ (`37c4ef48`).

## Problema

Fuera de los países con adaptador, la app sabe **dónde** repostar (OSM) pero no **a cuánto**. En
Alemania eso son decenas de miles de gasolineras sirviéndose como puntos mudos, y el gasto estimado
de un viaje cayendo a la media nacional de OpenVan en vez de a un precio real.

## ⛔ Lo que hay que MEDIR antes de escribir una línea

Francia costó una pasada entera de medición contra el endpoint real antes de tocar código, y ahí
salieron las tres trampas que ningún diseño sobre el papel habría visto (el nombre hay que cruzarlo
por **ID oficial** y no por cercanía · la hora es de cada **precio**, no de la respuesta · `latitude`
peta al parsear y solo vale `geom` · el radio sin unidad devuelve 200 con 0 resultados). Alemania
merece la misma pasada, y **este doc no la tiene todavía**.

Preguntas abiertas, ninguna respondida aún:

1. **Qué feed.** Los precios alemanes salen del MTS-K (Markttransparenzstelle für Kraftstoffe), que
   no se consume directo. Hay que verificar QUÉ intermediario se usa, si sigue vivo y qué publica.
2. **⛔⛔ ¿Exige clave registrada?** Si la respuesta es sí, Alemania **no es como España ni Francia**,
   que son abiertas y sin clave. Eso abre dos preguntas que **son del user, no técnicas**:
   - una clave dentro del APK es una clave pública — decidir si se asume o si hace falta proxy;
   - los términos de uso del feed (¿permiten una app de pago? ¿exigen atribución?).
   **Nada se implementa hasta que esto esté decidido.**
3. **Cruce nombre ↔ estación.** ¿Trae el feed un id que exista en OSM (como
   `ref:FR:prix-carburants` en Francia, que cubría el 97 %)? Si no lo hay, ⛔ **no vale la
   cercanía**: en Francia nombraba mal 1 de cada 10.
4. **Tipos de combustible.** El mapeo alemán (E5/E10/Diesel) contra nuestro `FuelType`, y qué pasa
   con los que el feed no publica.
5. **Frescura.** Si los precios traen hora propia, entra por `StationPrice.reportedAtMs` como Francia
   — la pieza ya existe y no hay que inventarla.

## Dónde encaja sin tocar nada más

La arquitectura ya está preparada: `FuelStationsRegistry` (una entrada por país, cada fuente declara
a quién cubre), `OsmFuelStationsSource` como suelo mundial y `DiskBackedFuelStationsSource` encima.
Un país nuevo es **un fichero** que implementa `FuelStationsSource` más su línea en el registro de
`DataModule`. La tabla de caché es **una y mundial** con clave `fuente|id`
[CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001], así que tampoco toca esquema.

## Criterio de éxito

- Precio real en gasolineras alemanas, con su edad, en mapa, lista y peek.
- El cruce de nombres declarado **con su cobertura medida** en este doc, no supuesta.
- Cero regresiones en España y Francia (el registro enruta por país; una fuente nueva no puede
  contestar por otra).
