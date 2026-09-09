# FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001 · La sección de repostaje solo responde lo que preguntó

**Estado:** ✅ Done — en master (hash en `MEMORY.md`, por construcción del cierre)

**Verificado el 09-09 en el Pixel 8 Pro** (prodDebug `36543f73…` + mockDebug `cde22751…`, sha256
comprobados en device): con GPS en Sol y el coche en GASOLINE, la sección enseña la lista real del
Ministerio ordenada por distancia (BLANCA 1,3 km 1.879 € … hasta 5,0 km) con «Prices reported at
09/09/2026 13:33:19» al pie — el criterio que F5b-1 dejó abierto. En la galería mock: variante
nueva «Sin combustible en el coche» con su frase (apóstrofo crudo limpio) y el vacío con la
ilustración del surtidor en oscuro. Suite `:shared:testDebugUnitTest` verde con los 6 tests nuevos
ejecutados (comprobado en el XML de resultados, no solo el BUILD SUCCESSFUL); `assembleMockDebug`,
`compileProdDebugKotlin` y klib iOS verdes.

⚠️ El fallo de red del emulador visto durante el diagnóstico (bug 3) no reapareció — con la
provincia arreglada Minetur contesta a la primera y OSM ni se consulta. Si vuelve, ahora deja su
excepción en logcat con tag y «fuel stations lookup failed».

Sale de la verificación en device de [`fuel-stations-list-001.md`](fuel-stations-list-001.md)
(sección «Verificación en device con datos reales», 09-09-2026): tres fallos distintos con la misma
raíz — la sección afirma cosas que nunca preguntó.

## Problema

Verificado en el Pixel 8 Pro con prodDebug `512ad54a…` (sha256 comprobado) y fix GPS en la Puerta
del Sol, donde el propio feed del Ministerio tiene **63 estaciones a <5 km** y Overpass 60:

1. **El «No fuel stations within 5 km» era fabricado.** El coche activo tiene `fuelType = NULL`
   (alta anterior a que fuera obligatorio; leído del Room del device con `run-as`). En
   `HomeViewModel.toggleFuelStations()` el guard `?: return` corre DESPUÉS de
   `isExpanded = true`: la sección queda desplegada sin loading ni fallo y la UI pinta el estado
   vacío. Ni una petición salió del móvil. El comentario del método dice «the section stays
   folded»; el código hace lo contrario.
2. **España multiprovincial nunca alcanza Minetur.** `AndroidGeocoderDataSourceImpl` mapea
   `region = adminArea`, y en España `adminArea` es la COMUNIDAD («Comunidad de Madrid», leído del
   `geocoder_cache` del device). `SpanishProvinces` indexa por provincia → `idFor()` = null →
   Minetur lanza → el registro cae EN SILENCIO a OSM sin precios. La función insignia de F5b-1
   queda muda en toda comunidad multiprovincial. El test «"Cádiz" → "11"» pasa un input que el
   geocoder real no emite jamás (Cádiz → «Andalucía»).
3. **El fallo real no deja rastro.** Con `fuelType` puesto, el fetch real falla en el emulador y el
   `onFailure` traga la excepción sin un log — «cero catch silenciosos» violado. La causa exacta
   sigue sin diagnóstico PORQUE no hay log; este ticket pone el log y el diagnóstico se hace con él.

Además, encargo del user (09-09): **ilustración propia de empty state** para la sección — hoy el
vacío es una línea de texto, y es el estado que más se ve.

## Doctrina violada

- *Fallo asimétrico / ante la duda se pregunta*: «no hay gasolineras a 5 km» es un veredicto, y se
  emitió sin haber preguntado (fallo 1) o habiendo preguntado a la fuente equivocada (fallo 2).
- El propio ticket padre: «un precio viejo no se distingue de uno bueno mirando la pantalla» — la
  degradación silenciosa a OSM convierte un país con precios en uno sin precios y nadie lo ve.
- CLAUDE.md, error handling: **cero catch silenciosos** (fallo 3).
- El comentario-contrato de `toggleFuelStations()` promete un comportamiento que el código no tiene.

## Señales / datos disponibles

- Room del device: `vehicles.fuelType = NULL` en el coche activo; `geocoder_cache.addressRegion =
  'Comunidad de Madrid'`, `addressCountryCode = 'es'` para Sol.
- Feed real: provincia 28 → 895 estaciones, 63 a <5 km de Sol; `/Listados/Provincias/` responde y
  «MADRID» tiene id 28. Overpass: 60 elementos con la query exacta de `OsmFuelStationParser`.
- DNS y ruta OK desde el emulador hacia ambos hosts (overpass responde a ping; minetur filtra ICMP
  pero responde 200 al curl del host).

## Diseño

**Un invariante, tres consumidores: la sección tiene un estado por cada respuesta HONESTA que puede
dar, y ninguno se alcanza sin haber hecho su pregunta.**

1. **Guard antes de desplegar, y un estado con nombre para «falta el combustible».**
   `HomeFuelSlice` gana `missingFuelType: Boolean` (estado propio, no un vacío disfrazado). En
   `toggleFuelStations()` los guards corren ANTES de mutar `isExpanded`… no: la sección SÍ se
   despliega (el user pulsó), pero lo que enseña es su estado honesto:
   - sin `fuelType` → `missingFuelType = true` → frase causa+consecuencia+remedio («di qué
     combustible usa tu coche para ver precios») sin mecánica interna, string ×9 locales.
   - sin GPS → no hay pregunta posible → `failed = true` (el estado «no pudimos preguntar» ya
     existe y es verdad; el remedio del copy pasa a ser genérico, no solo «check your connection»).
   - El orden del método queda: leer estado → decidir qué estado honesto toca → UNA sola mutación.
2. **La provincia real viaja en el geocode: `AddressInfo.province`.**
   - `AddressInfo` gana `province: String?` (default null — no rompe constructores).
   - Android: `province = subAdminArea` (en España es la provincia). iOS: `subAdministrativeArea`
     (misma semántica CLPlacemark), aunque iOS no corre aún.
   - `GetNearbyFuelStationsUseCase` construye la query con `regionName = address.province ?:
     address.region` — Cantabria y toda uniprovincial siguen funcionando por el fallback.
   - `SpanishProvinces.idFor()` no cambia: el matching estaba bien; el input estaba mal.
   - Test nuevo con el input REAL del geocoder: `province="Madrid"` + `region="Comunidad de
     Madrid"` → 28, y `province=null` + `region="Cantabria"` → id de Cantabria (fallback vivo).
   - ⚠️ Caché del geocoder (Room `geocoder_cache`): decidir al auditar si la provincia se persiste
     (columna nueva ⇒ bump + `Migration` en `ALL_MIGRATIONS`) o si el caché guarda el AddressInfo
     serializado. Si exige migración, va con su migración — nunca schema sin bump.
3. **El fallo se registra.** El `onFailure` de `toggleFuelStations()` loguea con tag y throwable
   (Logger/Napier, patrón del proyecto). Con eso el fallo del emulador se diagnostica en la
   próxima pasada en vez de adivinarse.
4. **Ilustración de empty state** (encargo del user): vector propio Nivel 3, multicolor, SIN
   tintar, `composeResources/drawable/` + variante `_dark`, solo `path` (VectorDrawable-compatible).
   Se enseña en el estado vacío real (el legítimo «no hay a 5 km»). Los estados de texto restantes
   (error, missingFuelType, no_prices) siguen siendo frase — no inventar cuatro ilustraciones.

**Galería/mock en la misma tarea:** variante nueva `missingFuelType` + la vacía con ilustración, en
paridad con `*Previews.kt`.

## Criterio de éxito

- Test VM: sin `fuelType` → la sección desplegada dice `missingFuelType`, NUNCA lista vacía, y no
  se llama al use case. Sin GPS → `failed`, no vacío.
- Test use case/provincia con inputs reales del geocoder (`province` + comunidad como `region`).
- En device (emulador, GPS Sol, coche con GASOLINE): la sección enseña estaciones **con precio del
  Ministerio** y su hora — el criterio que quedó abierto en FUEL-STATIONS-LIST-001.
- El fallo de red, si reaparece, deja su excepción en logcat.
- Empty state con ilustración propia en claro y oscuro (galería + device).
- Strings ×9 · galería · Konsist · suite · assembles · klib iOS.

## Consumidores auditados

**`AddressInfo.province` (campo nuevo, default null):**
- Constructores posicionales existentes (tests, workers, mappers) — **cerrado**: el default los deja
  intactos; ninguno necesita la provincia.
- `RoomLocalAddressAndPlaceDataSource` (caché Room, TTL 30 días) — **exento a propósito, sin
  migración**: el use case de gasolineras NO pasa por esta caché (inyecta `GeocoderDataSource`
  directo, ver `DomainModule`), y ningún lector del caché consume `province`. Un null ahí es
  honesto («no capturado»). El límite está declarado en el KDoc del propio campo.
- `ReportSpotWorker` workData / `ParkingSessionMapper` / `SpotDtoMapper` / entities — **exentos por
  la misma razón**: la dirección persistida es un snapshot de display (calle/ciudad); la provincia
  es dato de ENRUTADO y solo tiene sentido en un geocode vivo. No se amplía el contrato de campos.
- `GetNearbyFuelStationsUseCase` — **el único consumidor**: `province ?: region`.

**`HomeFuelSlice.missingFuelType`:**
- `HomeFuelSection` — rama propia, antes del fallo y del vacío.
- Galería mock — variante nueva «Sin combustible en el coche». No existían previews de la sección
  (F5b-1 la dejó solo en galería), así que no hay paridad extra que mantener.
- `HomeViewModel.toggleFuelStations()` — la escribe y la limpia; 4 tests nuevos con spy que cuentan
  las preguntas REALES al puerto (la diferencia entre un vacío honesto y uno fabricado).

**Limpieza al paso:** `HomeFuelSlice.isIdle` tenía CERO consumidores (código muerto de F5b-1) y
acababa de extenderlo — borrado en vez de mantenido, por la misma regla que sacó al banner de la
allowlist tipográfica.

**Riesgo conocido, aceptado sin ampliar el encargo:** el matching provincia↔feed sigue siendo por
nombre normalizado. Si algún `subAdminArea` real no coincide con el rótulo del Ministerio (p. ej.
bilingües tipo «Bizkaia»/«VIZCAYA»), esa provincia cae a OSM igual que hoy — se medirá cuando un
device real lo pise, no se inventa una tabla de alias sin datos.
