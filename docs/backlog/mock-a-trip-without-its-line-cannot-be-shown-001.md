# MOCK-A-TRIP-WITHOUT-ITS-LINE-CANNOT-BE-SHOWN-001 · Un viaje sin su línea no se puede enseñar

**Estado:** ✅ Done · mergeado a master en squash

## Problema

El **detalle de un aparcamiento** no puede dibujar nunca la ruta del viaje en el flavor `mock`.
Abierto en el Pixel 8 el 26-09-2026 desde Vehículos → historial → una sesión cerrada, la pantalla
dice:

> *«This trip's route was only saved on the phone that recorded it, and it's no longer there.»*

Medido: **`routePolyline` aparece UNA vez en `:mockkit`**, y es el nombre de un parámetro de
`FakeUserParkingRepository` (línea 416). Ninguna de las ~130 sesiones del catálogo lo rellena, así
que `PolylineCodec.decode(null)` devuelve lista vacía y `ParkingHistoryDetailScreen` pinta su estado
de «no hay ruta» — que es correcto para ese dato, y por eso no se ve como un bug.

La consecuencia: **la cara visual del pivote a trayectos no existe en el mock**. La traza del viaje
sobre el mapa, con su origen y su final, es lo más vistoso que tiene la app y lo que da sentido a
«cuánto has conducido» y «cuánto tardas en aparcar» — y no se puede grabar, ni revisar en device,
ni enseñar a nadie.

## Doctrina violada

La misma que [MOCK-THE-FLAGSHIP-FIGURE-HAS-NO-MOCK-DATA-001] escribió hace dos días, y esta es su
**segunda instancia**:

> **Un campo que decide si una superficie existe tiene que tener datos en el catálogo del mock, no
> sólo en la galería.** La galería prueba el DIBUJO de un estado; el catálogo prueba que ese estado
> sea ALCANZABLE.

Que el mismo agujero aparezca dos veces en dos campos distintos —`searchTimeMs` y ahora
`routePolyline`— dice que la regla está bien escrita pero no se aplica al añadir un campo nuevo.

## Señales / datos disponibles

- `PolylineCodec.encode(points: List<GpsPoint>)` ya existe en `:shared`, y `:mockkit` depende de
  `:shared`, así que el catálogo puede codificar rutas sin herramienta nueva.
- `ParkingHistoryDetailScreen` decodifica `routePolyline` y de ahí saca **tres cosas**: la traza,
  el marcador de origen y el de final. Con el campo relleno, las tres aparecen solas.
- Los cuatro coches son perfiles deliberados, y el **Second car existe justamente para no tener
  ruta** (su ficha demuestra que km, CO₂ y coste desaparecen con honestidad).

## Diseño

**La ruta se genera, no se escribe a mano**: una función que traza un camino plausible desde ~1,5 km
hasta el pin de la sesión, con unos pocos vértices y una pequeña desviación, y lo codifica. Escribir
130 polilíneas a mano sería ilegible y quedaría desincronizado del mundo en cuanto las coordenadas
se muevan otra vez — como ya pasó al mudar la demo a Dublín.

**Quién lleva ruta, y quién no**, con el mismo criterio de tres valores que se usó para el tiempo de
búsqueda:

| coche | ruta | por qué |
|---|---|---|
| 001 · **My car** | **sí**, salvo los pines manuales | es el perfil completo: aquí se ve la traza |
| 002 · **Second car** | **no**, en las 45 | su perfil ES la ausencia de ruta; tocarlo lo destruiría |
| 004 · **Work van** | **sí**, salvo los pines manuales | el caso intermedio |
| pin manual | **no** | nadie grabó una conducción que dibujar |

Que sobrevivan sesiones sin ruta es parte del diseño: el estado «este viaje no guardó su línea» es
real —lo produce un free que no sincroniza, o un pin a mano— y tiene que seguir siendo alcanzable.

## Criterio de éxito

- En el mock, Vehículos → historial → una sesión cerrada de **My car**: el detalle dibuja la
  **traza**, su **origen** y su **final**.
- Una sesión de **Second car** sigue diciendo que no hay ruta.
- La traza cae sobre calles de Dublín, no en el mar.
- `./gradlew testAll` en verde.

## Verificado el 26-09-2026

`./gradlew testAll` → **BUILD SUCCESSFUL**. En el Pixel 8, Vehículos → historial → una sesión
cerrada de **My car**: el detalle dibuja la traza, su origen y su final sobre las calles de Dublín.

⛔⛔ **La polilínea sola NO basta, y esto es el hallazgo del ticket.** `routeAvailability()` exige
además **`routeSnapped`**:

```kotlin
routeSnapped && !routePolyline.isNullOrEmpty() -> Drawn
!routePolyline.isNullOrEmpty()                 -> Recalculating
```

Con el campo relleno pero `routeSnapped` en false, la pantalla se queda en *«Recalculating
route…»* **para siempre** — que es exactamente lo que parecía «la ruta no se pinta». No se ve
leyendo el código: se vio en la pantalla, y sólo porque se miró.

⚠️ Dos errores propios durante el trabajo, los dos cazados mirando el device y no el diff:
- una regex de refactor pisó el bloque del **Corolla** y le dejó variables que no existen en su
  ámbito (no compilaba);
- la primera traza salió en **zigzag de sierra**, porque la desviación alternaba de signo en cada
  vértice. Ahora va **perpendicular al rumbo** y es una sola curva suave.

📌 La coordenada de la sesión se sacó a una variable local: estaba escrita dos veces —en el pin y
en la ruta— y así es como un mundo se desincroniza al mudarlo, que es lo que acababa de pasar con
Dublín.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `mockkit/…/FakeUserParkingRepository.kt` | **cerrado** — el catálogo, donde estaba el agujero |
| `presentation/map/ParkingHistoryDetailScreen.kt` | **exento** — decodifica bien; le faltaban datos, no lógica |
| `domain/util/PolylineCodec.kt` | **exento** — es la herramienta, se usa tal cual |
| `mockkit/…/FakeSpotRepository.kt` | **exento** — una plaza no tiene ruta, es un punto |
