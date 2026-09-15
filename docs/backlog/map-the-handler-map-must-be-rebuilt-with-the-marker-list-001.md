# MAP-THE-HANDLER-MAP-MUST-BE-REBUILT-WITH-THE-MARKER-LIST-001 · Al cambiar la vigilancia del viaje, salida y llegada pierden su dibujo

**Estado:** 🔵 Hallazgo lateral, SIN arreglar (encontrado auditando el crash del 10-09-2026)
· **re-verificado en código el 15-09 contra `d7880ac7`: sigue exactamente igual, con las líneas
corridas.** Hoy: la lista keya por `watchKey` (`PaparcarMapView.kt:983`, y los contentId de salida y
llegada lo llevan dentro — `:1164`, `:1176`), mientras el `remember` del mapa de handlers
(**`:1202-1214`**) enumera 16 claves y **`watchKey` no está entre ellas**; los `put` de
`departureContentId(watchKey)` / `arrivalContentId(watchKey)` (`:1269`, `:1274`) quedan dentro de un
bloque que no se recompone cuando cambia la vigilancia. La tabla de abajo conserva las líneas del
10-09 (966-983 / 1173-1184): son las mismas dos piezas.

## Problema

`PaparcarMapView.kt` construye dos cosas que **tienen que estar de acuerdo**, y las llava por
claves distintas:

| qué | dónde | ¿lleva `watchKey` como clave? |
|---|---|---|
| la LISTA de marcadores (`markers`) | línea 966-983 | ✅ sí (línea 973, con su comentario explicando por qué) |
| el MAPA de handlers (`customMarkerContent`) | línea 1173-1184 | ❌ **no** |

Y el mapa de handlers registra tres entradas cuyo `contentId` **contiene** el `watchKey`:

```kotlin
put(departureContentId(watchKey)) { DepartureDotMarker(color = tripAccent) }   // 1239
put(arrivalContentId(watchKey))   { DepartureDotMarker(color = tripAccent) }   // 1244
locationActiveContentId(meta.carbodyType, meta.color, watchKey) to { … }        // 1393, el puck
```

Cuando cambia la vigilancia del viaje (empieza un viaje, se resuelve su coche, cambia de método), la
lista se reconstruye con los `contentId` nuevos y **el mapa de handlers no**, porque ninguna de sus
claves cambió. Resultado: esos `contentId` no tienen dibujo.

Consecuencia por tipo de marcador, leída del fork (`Map.kt`):
- salida y llegada (marcadores normales): caen a `Marker` nativo → **el pin rojo de Google con el
  texto "Marker title"** en el origen y el final de tu traza;
- el puck (marcador vivo): `LiveMarkerNode` hace `?: return` → **el coche desaparece del mapa**.

Es intermitente: cualquier otra clave que sí esté en la lista (`parkedVehicles`, `zones`,
`fuelStations`…) reconstruye el mapa de rebote y lo arregla sola. Por eso se ve "a veces".

`tripAccent` (línea 960) está en la misma situación: se usa dentro de los handlers y tampoco es clave,
así que el color de la vigilancia puede quedarse en el del viaje anterior.

## Doctrina violada

> Dos listas que se indexan entre sí se recalculan juntas o no se recalculan.

Es la misma familia que `MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001`: dos proyecciones del mismo
estado, con reglas de invalidación distintas, y el usuario viendo la más vieja.

## Diseño

Que las claves de las dos memoizaciones **no puedan divergir**. Lo mínimo es añadir `watchKey` y
`tripAccent` a la lista de claves de `customMarkerContent`; lo que cierra la clase de bug es que ambas
salgan de una sola declaración de "de qué depende un marcador", para que añadir un dato al `contentId`
obligue a añadirlo a las dos.

## Criterio de éxito

Test de que todo `contentId` presente en la lista de marcadores tiene handler registrado, para un
estado en el que la vigilancia acaba de cambiar. En device: empezar un viaje y ver que salida y
llegada siguen siendo los puntos de la app, no el pin rojo de Google.
