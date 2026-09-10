# PARKINGS-LIVE-OCCUPANCY-001 · Cuántas plazas quedan, ahora, dentro del parking

**Estado:** 🔵 Pendiente — es la **F6b** de la ola 2, la segunda mitad de
[`plus-roadmap-001.md`](plus-roadmap-001.md) §F6. La base mundial (dónde están) la pone
[`parkings-on-the-map-001.md`](parkings-on-the-map-001.md).

## Qué añade, y qué NO puede venir de donde viene la base

⛔ **OpenStreetMap no tiene ocupación, ni la tendrá.** Es un mapa que dibuja gente, sin sensores
detrás. Lo único que ofrece es `capacity`, que es el **aforo TOTAL** y además escasea: medido el
09-09-2026 sobre los parkings que pasan el filtro de F6a, **2 de 22 en Madrid**, 6 de 32 en Málaga,
6 de 22 en París y **0 de 76 en Cuenca**.

Por eso el marcador de F6a no lleva cifra: la ranura donde iría el aforo total es la misma donde esta
fase escribirá **plazas libres**, y un hueco que dice dos cosas distintas en dos versiones enseña al
usuario a desconfiar de él.

## Fuentes españolas — estado real al 09-09-2026

| Ciudad | Qué publica | Verificado |
|---|---|---|
| **Madrid** | Ocupación en tiempo real de los rotacionales (los que se apuntan voluntariamente), con plazas totales, accesos, horarios, **tarifas** y servicios | ✅ |
| **Zaragoza** | Ocupación de los parkings gestionados por VINCI, con API propia del Ayuntamiento | ✅ |
| **Málaga** | CSV de ocupación **cada minuto** + catálogo de ids | ✅ (07-09) |
| Valencia · Bilbao · Barcelona · Sevilla | Ubicación sí; la ocupación la llevan operadores privados (B:SM, Aussa) y no está en abierto | ❌ |

### 🔎 La pista que puede ahorrar N adaptadores — SIN verificar

La DGT publica en su **Punto de Acceso Nacional** un conjunto llamado *«Estado de ocupación de
parkings en Ayuntamientos»*: **DATEX II v3 por API REST**, licencia **CC BY 4.0**, registro opcional
(solo para avisos de cambios). Si de verdad agrega a varios ayuntamientos, sería **un adaptador para
todos** en lugar de uno por ciudad, y con un modelo de datos ya normalizado.

⚠️ **No está verificado y no debe darse por bueno:** ni el dataset ni su wiki técnica listan un solo
ayuntamiento participante, y la única URL expuesta es un entorno de **preproducción**
(`pre.cmobility30.es/use-case-11`). Antes de diseñar nada sobre esto: pedir el endpoint de producción
a `soporte@cmobility30.es` y **medir qué ciudades contestan de verdad**, exactamente como se midió
Overpass antes de escribir el parser de F6a.
- `nap.dgt.es/dataset/estado-de-ocupacion-de-parkings-en-ayuntamientos`
- `github.com/dgt30-esp/Caso-de-uso-11/wiki`

Fuera de España, el equivalente open source es **ParkAPI v3 / `parkapi-sources`** (decenas de
ciudades DE/CH/AT/DK ya normalizadas a DATEX II Light). Comprar el dato a TomTom o HERE es otra
historia y tiene su propia ficha: [`parking-availability-vendor-001.md`](parking-availability-vendor-001.md).

## Lo que el roadmap ya fija (no se rediscute aquí)

- Es el **primer backend propio** del proyecto: `firebase.json` declara hoy `firestore` + `hosting` y
  **no existe `functions/`**. Esta fase estrena runtime, deploy, CI, secretos y plan Blaze.
  ⚠️ El MCP `firebase_deploy` es un **no-op silencioso**: desplegar SIEMPRE por CLI.
- `scheduler` cada 3 min (solo 07-23 h local de cada fuente) → adaptadores → esquema único
  `PublicParking{…, free?, updatedAt}` → Firestore `publicParkings/{source}_{id}` (merge). Lectura
  pública, escritura solo la función.
- El campo `source` es **procedencia, no confianza**: ⛔ ningún enum con `CONFIRMED` (colisiona con
  `Spot.confidence` y `SpotStatus.CONFIRMED`). Si hace falta el tipo se llama `AvailabilitySource` y
  el valor oficial es `OFFICIAL`.
- ⛔ **La rampa 🟢🟡🔴 es EXCLUSIVA de la caducidad de plazas** [UI-COLOR-DOCTRINE-001]: la ocupación
  necesita su propia expresión — cifra de libres escrita en el glifo, gris cuando solo hay estático,
  y «hace X min» en el peek.

## Cómo engancha con F6a — la junta, que es lo delicado

⛔ F6a **no montó registro por país a propósito**, porque esta fase no *sustituye* la base: la
**enriquece**. El feed de una ciudad trae la ocupación de los cuatro parkings que gestiona y nada
más, así que hay que fusionar por proximidad (<40 m) contra los de OSM sin duplicar el mismo parking
— y sin que un fallo de emparejamiento acabe pareciendo un dato en vez de un bug, que es la trampa
que ya mordió al cruzar catálogos de gasolineras.
