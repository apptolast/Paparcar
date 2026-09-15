# FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001 · Alemania por Tankerkönig: el feed contesta entero, pero solo con clave

**Estado:** ✅ Done (15-09-2026) · mergeado a master con squash — el hash vive en `MEMORY.md`
· ⏳ en mano: prod con GPS en Alemania (móvil con sesión)

## Problema
Alemania es el tercer país del orden medido (España → Francia → **Alemania** → Italia,
[FUEL-STATIONS-LIST-001]). Hoy un conductor en Berlín ve las gasolineras de OSM sin un solo precio,
mientras el MTS-K (Bundeskartellamt) publica el precio de cada surtidor del país **en tiempo casi
real por obligación legal**, servido gratis por Tankerkönig bajo CC BY 4.0.

## Doctrina violada
Ninguna — es la siguiente entrada planificada del registro. Las que APLICAN:
- *Una fuente por país y responde ENTERA* — y Tankerkönig cumple mejor que nadie: trae nombre,
  marca, dirección y precios en la misma respuesta. **Cero cruce con OSM** (lo que Francia tuvo
  que resolver por id oficial aquí sobra).
- *Cada superficie dice lo que su fuente SABE decir* — este feed **no estampa ninguna hora**, ni en
  la respuesta ni por precio. No se inventa: `reportedAt = null` y `StationPrice.reportedAtMs = null`
  (el modelo ya lo contempla: «null when the source publishes none»).

## Señales / datos disponibles (medido contra el endpoint real, 14/15-09-2026)
- Sandbox (clave demo `…0002`): estructura real, precios dummy (1.009 en todo).
- Clave real (14-09, aprobada en <1 día pese al aviso de «días»): Berlín 5 km → precios reales
  (diesel 2,399 / e5 2,329 / e10 2,269 en Aral Holzmarktstraße).
- ⛔ **Una clave mala responde HTTP 200 con `{"ok":false,"message":"Key existiert nicht…"}`** —
  misma familia que el `total_count: 0` francés y el espejo suizo: el gate es `ok`, nunca el status.
- Una estación **cerrada** (`isOpen: false`) sigue trayendo precios (189/347 de noche, todas con
  precio). Se muestran: ocultar medio mapa a las 3 a.m. responde una pregunta que nadie hizo.
- `rad` en KM (documentado tope 25; pedimos 5), `sort=dist` ordena en servidor, `dist` viene puesto.
- Campos por estación: `id` (uuid), `name`, `brand`, `street`+`houseNumber`, `place`, `postCode`,
  `lat`/`lng` decimales normales, `diesel`/`e5`/`e10` numéricos, `isOpen`.
- **Medido con clave real (15-09, Berlín 25 km = 347 estaciones · Múnich 10 km = 131):**
  - El «no lo vende» es **`null`**, y es AMBIGUO (no vende vs. no reporta) → `UNKNOWN`, nunca se
    oculta. El feed no sabe declarar un negativo, a diferencia de España y Francia.
  - **E5 gana la cobertura**: 344/347 y 130/131, contra E10 344 y 122. Y todo coche de gasolina
    quema E5 → preferencia E5 con respaldo E10, la INVERSA de Francia, por medición.
  - **Sin tope de resultados observable**: 347 estaciones llegan enteras y `dist` máx 24,7 cubre el
    radio pedido → `isTruncated = false`.
  - `name` a veces es una dirección («Berlin, Wittestr. 16») y `brand` lo que se lee como nombre
    («HEM»); brand vacío solo 2/347 → **la fila viste la marca**, con `name` de respaldo.
  - Umlauts como `Holzmarktstraße` — JSON estándar, el parser no hace nada especial.

## Diseño
El SISTEMA es el que ya existe — el registro por país — y Alemania es UNA entrada:
- `GermanyStationParser` (commonMain, object como Francia): JSON → `List<FuelStation>`.
  - `e5 → GASOLINE` con respaldo `e10` (todo coche de gasolina puede quemar E5; la preferencia
    inversa a Francia es deliberada y se contrasta con la medición de cobertura).
  - `diesel → DIESEL`. El feed no publica GLP/GNC → silencio = `UNKNOWN`, nunca `NOT_SOLD`.
  - `ok:false` ⇒ excepción con el `message` del feed (→ el registro cae a OSM con `didFallBack`).
- `GermanyFuelStationsSource` (calca `FranceFuelStationsSource`, sin `OsmStationNames`):
  `covers("DE")`, `SOURCE = "de-tankerkoenig"` (parte de la clave del caché de disco).
- **La clave**: `local.properties → GermanyFuelStationKey` (gitignored — el repo es PÚBLICO) →
  `buildConfigField` en `:app` → se publica al arrancar como hace `AppBuildInfo` (BuildConfig es
  del módulo app; shared no lo ve). La fuente la recibe como **proveedor** `() -> String`, no como
  valor capturado — mismo porqué que el proveedor de la tabla de medias de F5a.
  - ⛔ **Clave vacía ⇒ la fuente NO reclama** (`claims = false`): un build sin clave (cualquiera que
    clone el repo público) trata Alemania como país sin feed → OSM sin `didFallBack`, sin banner
    falso de «los precios oficiales no cargaron».
- DI: una línea en la lista `national` de `DataModule`. El caché de 15 min en memoria ya cumple la
  cortesía de la ToS de Tankerkönig (no repetir la misma consulta en <5 min).
- Mock/galería: `FuelStationsSource` ya es un puerto declarado con fake en `MockModule` — no hay
  puerto nuevo. Variante de galería solo si la UI gana algo nuevo (¿pie «tiempo real»?, decidir).

## Criterio de éxito
- ✅ Suite verde **2.968/0** con los 9 tests del adaptador ejecutados (fixture real del endpoint:
  marca-como-nombre, E5→E10, `null`→UNKNOWN sin ocultar, sin sellos + `pricesAreLive`, `ok:false`
  tras un 200, clave vacía no reclama, feed caído = fallo y no lista vacía) + passthrough del flag
  en el use case.
- ✅ `assembleProdDebug` + `assembleMockDebug` verdes, verify del grafo mock 2/0, klib iOS
  (`compileKotlinIosArm64`) compila con el `actual` nuevo.
- ✅ Galería en device (emulador Pixel_8_Pro, mock, sha256 verificado): la variante «Precios en
  TIEMPO REAL (Alemania)» pinta marca-como-nombre (ARAL/HEM/Sprint), precio por fila y el pie
  «Prices update in real time».
- ✅ Camino de red vivo probado con la CLAVE REAL por curl (Berlín y Múnich, 15-09): precios
  reales, `ok:true`, 347 estaciones a 25 km.
- ⏳ **En mano** (prod + GPS en Alemania): la app entera pide login OAuth y eso no se automatiza
  desde el emulador limpio — queda para un móvil con sesión. España y Francia sin regresión se
  apoyan mientras tanto en la suite (2.968/0, sus tests intactos).

## El pie de frescura — la tercera frase [decisión de diseño]
El carril existe porque «un precio sin hora al lado no se puede sopesar», y Alemania era la primera
fuente CON precios y SIN nada que citar: ni sello de respuesta (España) ni horas por precio
(Francia). Lo que su fuente SÍ sabe decir es la garantía del MTS-K (tiempo real por ley) →
`FuelStationsResult.pricesAreLive`, que viaja como `didFallBack` (solo la fuente que contesta lo
sabe) y pinta «Los precios se actualizan en tiempo real» en el pie del carril y del peek.
- ⛔ Solo en respuesta VIVA: el disco reconstruye sin el flag y su frase («no se pudo preguntar
  hoy») manda; la proyección además lo apaga si `rememberedAtMs != null`.
- ⛔ No se inventa un sello con nuestro reloj: sería coser la hora de UNA fuente (la nuestra) al
  precio de otra — la costura prohibida.

## Consumidores auditados
- `FuelStationsRegistry` — genérico, sin tocar (añadir a la lista no cambia contratos).
- `DiskBackedFuelStationsSource` / `RoomFuelStationsCache` — la clave incluye `source`; el id uuid
  de Tankerkönig es único dentro de su fuente. Sin cambios.
- UI del carril (pie de frescura): Alemania será la primera fuente CON precios y SIN hora — revisar
  que el pie calla sin mentir (ni hora de España ni cota de Francia). → auditar al implementar.
- `OpenSourceLicenseRepository` / pantalla de licencias: **CC BY 4.0 exige atribución** — añadir
  Tankerkönig/MTS-K como se añadieron OpenVan/OSM/Ministerio en F5a.
