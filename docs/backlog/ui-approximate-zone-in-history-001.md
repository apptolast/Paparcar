# UI-APPROXIMATE-ZONE-IN-HISTORY-001 · La card de Home y el historial siguen pintando un área como si fuera un pin

**Estado:** 🔵 Abierto, sin código · follow-up deliberado de
`UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001`

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
