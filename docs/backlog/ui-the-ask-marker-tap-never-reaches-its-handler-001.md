# UI-THE-ASK-MARKER-TAP-NEVER-REACHES-ITS-HANDLER-001 · El marcador de «¿Has aparcado?» no responde al tap

**Estado:** 🟡 Abierto, sin implementar · sin rama · hallazgo de
`UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001` (30-08-2026)

## Problema

`PaparcarMapView.onMarkerClick` enruta por prefijo de `contentId` y las ramas se evalúan **en
orden**:

```kotlin
cid?.startsWith(MARKER_MY_CAR) == true ||                       // "my_car"
    cid?.startsWith("vehicle_badge_") == true ->
    sessionIdByCoords[marker.coordinates]?.let(onMyCarClick)
// La rama de abajo es INALCANZABLE:
cid?.startsWith(MARKER_MY_CAR_ASKING) == true -> onAskMarkerClick()   // "my_car_asking"
```

`"my_car_asking_lt".startsWith("my_car")` es `true`, así que el tap del marcador de pregunta cae
siempre en la PRIMERA rama, busca una sesión por coordenadas, **no la encuentra** (una pregunta
abierta no tiene sesión guardada — es justamente su definición) y el `?.let` no hace nada.
`onAskMarkerClick()` no se ejecuta nunca.

Efecto para el usuario: el marcador con el `?` se pinta donde toca, pero **tocarlo no hace nada**.
Introducido con `DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001` (`e47240fd`).

## Doctrina violada

Ninguna de detección: es un fallo de enrutado de UI. Sí toca la regla de que la pregunta tiene que
poder contestarse desde donde se muestra — un marcador que enseña dónde pregunta y luego ignora el
dedo deja al usuario buscando la notificación.

## Señales / datos disponibles

Ninguna telemetría lo delata: no hay error, no hay log, la rama simplemente no corre. Se ve leyendo
el `when`, o tocando el marcador en device durante una pregunta abierta.

## Diseño

El orden no es la causa raíz, es el síntoma: **`startsWith` sobre ids que son prefijo unos de
otros** es una jerarquía implícita que se rompe cada vez que alguien añade un id nuevo — y este
ticket ya añadió otro (`my_car_apx_lt`, que sí quiere caer en `onMyCarClick` y lo hace por suerte,
no por diseño). Opciones, de menos a más sistema:

1. Mover la rama de `ASKING` **arriba** — una línea, arregla hoy, se vuelve a romper mañana.
2. Que el enrutado no dependa de prefijos: una función `markerRole(contentId)` que devuelva un
   `enum` (MY_CAR / ASK / ZONE / SPOT / CLUSTER) y un `when` exhaustivo sobre él. El id sigue
   siendo la clave de caché de kmpmaps; el ROL deja de deducirse de cómo empieza la cadena.

Recomendado el 2 — es el mismo movimiento que este ticket hizo con `VehicleMarkerDoubt`: convertir
en estado lo que estaba en un comentario.

## Criterio de éxito

- Tocar el marcador de pregunta abre la respuesta (lo que haga `onAskMarkerClick`).
- Tocar un aparcamiento guardado — exacto o zona — sigue abriendo su sesión.
- ⏳ Verificable sólo en device, con una pregunta abierta viva.
