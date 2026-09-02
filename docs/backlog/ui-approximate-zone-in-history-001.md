# UI-APPROXIMATE-ZONE-IN-HISTORY-001 · La card de Home y el historial siguen pintando un área como si fuera un pin

**Estado:** ✅ Done (2026-09-03) · ⏳ visto en device pendiente del próximo `/run`

## Problema

`UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001` hizo que una sesión aproximada dibuje su duda **en
Home**: anillo en el mapa + fila que lo explica en el peek. Quedaron fuera dos superficies que
siguen tratando un área de 154 m igual que un pin de 3 m:

- **La card de vehículo de Home** (`HomeParkingRow`) — dice la dirección sin decir que es
  aproximada.
- **El historial** (pestaña Vehículos) — cada sesión pasada con `zoneRadiusMeters != null` se lee
  como una ubicación exacta.

## Por qué se dejó fuera

No por tamaño: `UI-HISTORY-IDENTITY-AND-SOURCE-001` (master `64e1def0`) acaba de reescribir cómo el
historial pinta identidad y procedencia, y **todavía no se ha visto en device**. Meter una segunda
capa de significado en las mismas filas antes de mirar la primera es cómo se acumulan dos cambios
que hay que desenredar. Se hace después de ese vistazo.

## Señales / datos disponibles

Todo. El campo llega intacto a la UI (lo demostró el ticket padre). Para el historial hay que
comprobar si el read-model que alimenta la lista arrastra `zoneRadiusMeters` o si, como pasó con el
mapa, se pierde en una proyección intermedia.

## ⚠️ Restricción heredada

`zoneRadiusMeters` es **local-only** — `ParkingSessionMapper` no lo sincroniza a Firestore a
propósito. En un móvil nuevo (o tras reinstalar) el historial descargado **no** tendrá radios: las
sesiones viejas se leerán como exactas. Hay que decidir explícitamente si eso es aceptable
(probable: una zona sin refinar es del device que la detectó) o si el historial merece sincronizar
el campo. **No inventar un radio por defecto**: eso sería afirmar una duda que no consta.

## Criterio de éxito

- Una sesión aproximada se distingue de una exacta también en la card de Home y en el historial.
- Sin strings nuevos si se puede reusar `home_peek_parking_approximate`; si hace falta uno, va a los
  9 locales.
- La decisión sobre el local-only queda escrita, no implícita.

## Cierre (2026-09-03)

**Cero strings nuevos** — pero el reusado no fue el del criterio: `home_peek_parking_approximate` es
una frase completa (causa+remedio) que no cabe en una card; el que encaja es
**`location_approximate_near`** («Near %1$s»), la MISMA palabra que la app ya usa para ubicaciones
prestadas del geocache — así «aproximado» suena igual en toda la app. Ya está en los 9 locales.
Sin riesgo de «Near Near X»: ese prefijo hoy solo se aplica a la línea de ubicación EN VIVO, nunca
a la dirección persistida de una sesión.

**El barrido encontró una 4ª superficie** (el doc citaba 2): el header del peek plegado con el coche
aparcado (`BrowsePeek`) también afirmaba el punto exacto, y no tiene `ApproximateZoneRow` debajo.

| Superficie | Tratamiento |
|---|---|
| `HomeVehicleCard` (1 coche) | título «Near X» en vez de «Parked at X» + glifo de zona (`Adjust`) en vez del pin |
| `HomeVehicleChip` (2+ coches) | línea «Near X · hace 2 h» + glifo de zona |
| Historial (`SessionCardContent`) | título «Near X»; el fallback sin geocodificar queda llano («Parking» no afirma nada exacto que suavizar) |
| `BrowsePeek` plegado | título «Near X» (sin fila de zona debajo, el título carga la duda) |
| `ParkingPeek` / detalle de historial | **exentos** — ya llevan `ApproximateZoneRow` del ticket padre |

- El read-model NO pierde el campo: card, chip, historial y peek reciben `UserParking` entero
  (`isApproximate` ya existía en dominio).
- **Decisión local-only, escrita**: `zoneRadiusMeters` sigue sin sincronizarse a Firestore. En un
  móvil nuevo el historial descargado se lee como exacto y **es aceptable**: la zona sin refinar es
  una duda MEDIDA por el device que la detectó; sincronizarla no añade remedio (el «edítalo» ya no
  aplica a una sesión cerrada) y **no se inventa un radio por defecto** — sería afirmar una duda
  que no consta.
- Mock/galería en paridad: `FakeData.endedApproximateZone` (previews + galería la muestran en todas
  las listas) y una sesión `closed_approximate_zone` en el catálogo de device (154 m, la cifra real
  del campo 21-08; path y fiabilidad los que `RunHonestCloseUseCase` stampa de verdad).
- Suite 2.143/0 · `assembleMockDebug` y `compileProdDebugKotlin` en verde.
