# MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001 · Ir a un sitio deja de alejarte

**Estado:** ✅ Done · master `c11073ea8` · cierra la **issue #31**

## Problema

Reportado por el product owner (#31): al seleccionar una zona —y al añadir una— el zoom cambia y
**aleja demasiado**, obligando a otra interacción para volver a acercar. Pide evaluar cuál es el
destino de zoom óptimo para **cada** animación de cámara, no solo las de zonas.

## Qué pasaba (medido en el código)

Cada animación llevaba su nivel escrito a pelo y lo aplicaba **en absoluto**:

| acción | nivel | efecto si venías de nivel calle (17-18) |
|---|---|---|
| seleccionar zona | `NAVIGATE_ZOOM` = 15 | **te aleja** |
| añadir/editar zona | `ZONE_EDITING_ZOOM` = 14 | **te aleja más** |
| centrar en tu coche | 17 | te aleja si estabas a 18 |
| centrar en ti / buscar | 16 | te aleja si estabas a 17 |

## Diseño — el nivel escrito es un SUELO, no un destino

`presentation/map/CameraFraming.kt`, funciones puras y testeables:

- **`settleZoom(current, wanted, intent)`** con dos intenciones:
  - `AtLeast` (por defecto): `max(current, wanted)`. Acerca si estabas lejos, **nunca aleja** lo que
    el usuario había elegido. Es lo que quiere toda animación que solo cambia DÓNDE miras.
  - `Fit`: manda el encuadre, aunque toque alejar. Para cosas que tienen tamaño.
- **`zoomForZoneRadius(radiusMeters)`**: editar una zona sí es un encuadre, pero por su RADIO. El 14
  fijo de antes enseñaba ~4 km para dimensionar un garaje de 40 m: el círculo se veía entero, sí,
  del tamaño de una moneda. Escalera monótona (test incluido), misma familia que la de
  `BOUNDS_DELTA_*` del mapa.

La intención viaja en `CameraTarget.zoomIntent` y **por defecto es `AtLeast`**, así que el barrido
de «todos los casos» que pide la issue sale de la propia opción por defecto, no de tocar diez sitios.

## Consumidores auditados

| Animación | Intención | Nota |
|---|---|---|
| Seleccionar zona (Navigate, 15) | `AtLeast` | la queja original |
| Añadir / editar zona | `Fit` por radio | ya no hay número fijo |
| Coche aparcado (17) · candidato de pregunta (17) | `AtLeast` | |
| Centrar en ti / zona sin ubicación / buscador (16) | `AtLeast` | |
| `goToPlace` sin zoom (filas del peek) | preserva | ya era así, sin cambio |
| Seguir al conductor | sin cambio | manda el lock |
| Encuadre de dos puntos (tu coche y tú) | `Fit` por caja | camino de bounds, intacto |

## Criterio de éxito

- Mirando de cerca, tocar una zona **centra sin alejar**.
- Dimensionar un garaje pequeño no manda la cámara al barrio entero.
- Ninguna animación aleja por debajo de lo que el usuario había elegido, salvo un encuadre real.

## Verificación

`CameraFramingTest` — 6 tests en verde, incluida la monotonía de la escalera de radios ·
`:shared:compileDebugKotlinAndroid` ✅
