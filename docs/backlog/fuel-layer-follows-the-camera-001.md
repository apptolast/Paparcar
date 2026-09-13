# FUEL-LAYER-FOLLOWS-THE-CAMERA-001 · La capa enseña lo que miras, la lista sigue siendo tuya

**Estado:** ✅ Done — en master (hash en `MEMORY.md`, por construcción del cierre)

> ⚠️ **Superado en parte por [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001](nearby-one-store-follows-the-camera-001.md)** (13-09-2026): las tiendas de carril y de capa se fusionaron en UNA por sujeto, y la unión que pintaban mapa y lista desapareció. Lo que aquí se dice de dos tiendas / dos anclas es historia, no regla vigente.

## ⛔⛔ El hallazgo que cambió el ticket: la capa NUNCA funcionó fuera de España

Diagnosticando el síntoma del user (arrastró a París y no salía nada) el log nuevo dijo
`Overpass answered 406`. Bisecando cabeceras contra el endpoint real: **misma petición, 200 con el
agente de curl, 406 con `okhttp/x.y.z`** — Overpass tiene el agente por defecto de OkHttp en su
filtro de bots. O sea que **desde F5b-2 la capa estaba rota en todo el mundo menos España**, y
nadie lo vio porque España responde por el Ministerio, que contesta primero y no filtra. Era
también el «fallo de red inexplicado» que FUEL-STATIONS-LIST-001 dejó sin causa.

→ `data/fuel/FuelHttpClient.kt`: un cliente compartido que **identifica la app** (política del
ecosistema OSM, además) y trae **timeouts explícitos** — sin deadline, una petición colgada dejaba
`isLoading` fijo y mataba la capa entera (medido: Overpass tardando más de un minuto ese día).
Con test que prueba la cabecera en cada petición: un header que nadie asserta es un header que
el siguiente refactor borra.

Pisado por el user en el emulador (09-09): arrastró el mapa a París y la capa siguió pintando las
cápsulas de Madrid. La capa de [`fuel-stations-map-and-peek-001.md`](fuel-stations-map-and-peek-001.md)
pide UNA vez alrededor del GPS del user y no vuelve a preguntar — para una capa de mapa, el
contrato que la mano espera es «lo que miro».

## Diseño (opción A, decidida por el user)

**Dos preguntas, dos almacenes, una caché:**

- **La capa responde «¿qué hay AQUÍ donde miro?»** — almacén nuevo `HomeState.fuelLayer`
  (`HomeFuelLayerSlice`: ancla del último fetch + estaciones). Se rellena al encender la capa y se
  re-pide cuando la cámara SE ASIENTA en Browse a más de medio radio del ancla (decisión pura
  `ViewportRefetch.shouldRefetch`, testeable — es el MOLDE que spots y parkings reusarán, ver ficha
  hermana). El registro ya enruta por país del punto pedido: París cae a OSM (sin precios) él solo,
  y la caché por área hace gratis volver a una zona ya pedida.
- **La sección de la sheet responde «¿dónde reposto YO?»** — el slice `fuel` de siempre, anclado al
  GPS del user, intacto: mismos guards (`missingFuelType`, `failed`), mismas distancias reales.
- **La selección sabe leer de los dos**: el peek resuelve contra la UNIÓN (encuadre primero — el
  stepper camina «lo que estás viendo»; con la capa apagada la unión ES la lista). Al reemplazar el
  encuadre, una selección que ya no está en NINGUNO de los dos se poda — el molde de
  `applyNewSpots`.
- **Fallo de la capa = log + quedarse con lo que había** (el ancla avanza igual para no martillear
  una zona que falla en cada asentamiento). La capa no tiene frase de error propia: sus estados
  honestos viven en la sección; un encuadre sin datos se ve vacío y el log dice por qué.
- **Peek a mucha distancia**: pasado un umbral (100 km) se enseña la distancia SIN tiempo de
  conducción — «12 h drive» estimado con una heurística urbana sería una cifra inventada.

## Criterio de éxito

- Arrastrar a París con la capa activa → cápsulas de París (OSM, sin cifra) al asentarse la cámara;
  volver a Madrid → las tuyas, sin re-descarga (caché por área).
- La lista de la sheet no se inmuta en todo el viaje de cámara.
- Seleccionar una gasolinera del encuadre lejano abre su peek con distancia real sin tiempo.
- Pan corto (< medio radio) NO re-pide; el asentamiento tiene debounce.
- Tests: decisión pura de re-fetch (ancla vacía / debajo / encima del umbral) · VM (asentarse
  re-pide solo con capa ON y movimiento suficiente; el fetch usa el CENTRO DE CÁMARA; poda de
  selección huérfana) · suite · assembles · klib iOS.

## Dos límites que puso el user mirándolo en device

1. **Hasta 50 km de ti** (`ViewportRefetch.MAX_FROM_USER_METERS`). El caso real es «arrastro a mi
   destino», no «paseo por el mundo pidiendo a un servicio público frágil». Más allá la capa se
   **vacía** — nunca las gasolineras de tu ciudad sobre una lejana — y suelta su ancla, para que al
   volver vuelva a preguntar en vez de restaurar un almacén que ya tiró.
   ⚠️ Decidido así frente a «ampliar el radio de búsqueda»: arrastrando eliges la DIRECCIÓN (tu
   destino); un radio mayor trae cientos de estaciones que te dan igual y Overpass corta en 60.
2. **(0,0) no es un sitio**, es el Golfo de Guinea — la coordenada que reporta el mapa antes de
   tener cámara. El log lo pilló pidiendo allí de verdad (`0 stations around 0.0,0.0`); es la misma
   trampa que cerró `SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001`.

## Verificado en device (09-09-2026, prodDebug `da33a0c7…`, hash comprobado)

- Arranque limpio: **ni una petición a (0,0)** — antes la había.
- Madrid: `fuel viewport: 60 stations around 40.4168,-3.7038`, cápsulas en pantalla.
- Con el seguimiento libre (antes del límite): Porte de Clichy pintó cápsulas de París **sin cifra**
  — OSM no publica precios, y la capa lo dice callando en vez de inventar.
- Con el límite puesto: en París **no pinta nada y no pide nada**.
- Suite verde: `ViewportRefetchTest` (5) + los 5 nuevos del VM + `FuelHttpClientTest`;
  `assembleMockDebug`, `assembleProdDebug` y klib iOS verdes.

## Consumidores auditados

- **`HomeFuelLayerSlice` (almacén nuevo)** — el mapa lee de él; la sección de la sheet sigue con
  `fuel` intacto (guards `missingFuelType`/`failed` sin tocar). El peek resuelve contra la UNIÓN,
  encuadre primero, así que una cápsula tocada lejos abre su peek aunque no esté en tu lista.
- **`ViewportRefetch`** — pieza reutilizable a propósito: es el molde que `spots-follow-the-camera-001.md`
  reusará, y F6 (parkings) tras ella. Pura y testeada aparte.
- **`fuelHttpClient`** — único constructor de cliente HTTP del área (grep: no queda ningún
  `HttpClient()` a pelo fuera de un test con `MockEngine`).
- **`onCameraPositionChanged`** — el enganche solo actúa en Browse: mientras se coloca un pin, el
  mapa es el lienzo del pin, no un navegador.
