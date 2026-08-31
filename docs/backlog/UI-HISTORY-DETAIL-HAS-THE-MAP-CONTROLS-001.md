# UI-HISTORY-DETAIL-HAS-THE-MAP-CONTROLS-001 · El mapa del histórico tiene los mandos que ya estaban escritos

**Estado:** ✅ Done — mergeado a master el 31-08-2026
**Pendiente de device:** ⏳ los cuatro mandos sin probar en mano.
**Abierto:** 31-08-2026 · sobre master `748648fc`

## Problema

Al abrir un aparcamiento del historial se entra a un MAPA a pantalla completa **sin un solo mando**:
no hay "mi ubicación", no hay forma de volver al pin si te alejas navegando, no hay punto medio y no
se puede cambiar el tipo de mapa. Home tiene las tres primeras (`HomeMapFabColumn`) y la cuarta en su
cabecera (`MapTypeToggle`); aquí no hay ninguna, y el mapa es igual de manipulable.

## Doctrina violada

Ninguna regla de producto, pero sí una de higiene: **`MapControlButtons`
(`presentation/map/components/MapControlButtons.kt`) ya existe, hace exactamente esto — mi ubicación ·
coche · punto medio, sobre el mismo `MapCircleFab` que Home — y NADIE lo llama.** Es código muerto en
el paquete `map`, escrito para esta pantalla y nunca conectado.

Peor: el KDoc de `MapFab.kt:27` afirma *"Shared circular map FAB used by both HomeMapFabColumn and
MapControlButtons"*. Hoy esa frase es falsa — la única superficie viva es Home. Es la misma clase de
mentira que `UI-TYPE-SYSTEM-HYGIENE-001` (`b7548519`) encontró en un allowlist: una afirmación sobre
código que no se renderiza.

## Señales / datos disponibles

Está todo, no hace falta fontanería nueva:
- `CameraTarget` ya soporta punto único **y bounds** (`boundsLat2`/`boundsLon2`/`paddingDp`) — el punto
  medio no necesita API nueva.
- La pantalla ya mueve la cámara con `cameraTarget` + `cameraToken` para el stepper: los FABs empujan
  por el mismo carril.
- `state.userLocation` ya se observa (`ObserveAdaptiveLocationUseCase`).
- `PaparcarMapConfig.mapType` ya existe y `PaparcarMapView` ya hace el cross-fade entre tipos.

## Diseño

1. **Resucitar `MapControlButtons` en esta pantalla**, abajo a la derecha, por encima de la ficha
   (la altura del sheet ya se mide en `sheetHeightPx`).
2. Sus parámetros pasan a hablar de ESTE aparcamiento (`parkingLocation: GpsPoint?`) en vez de
   `userParking: UserParking?`: aquí el sujeto es el pin histórico, no la sesión activa.
3. **Copy propio.** `map_cd_go_to_car` dice *"Ir a mi vehículo aparcado"* y en un histórico el coche
   **ya no está ahí**. Claves nuevas, en los 9 locales, con el vocabulario del proyecto
   (`APARCAMIENTO` es tuyo, `PLAZA` es de la comunidad) [COPY-SPOT-IS-NOT-A-PARKING-001]:
   `map_cd_go_to_parking` y `map_cd_midpoint_parking`.
4. **Tipo de mapa**: se reutiliza `MapTypeToggle` tal cual, arriba a la derecha del mapa. El tipo NO
   se persiste en ningún sitio (en Home vive en `HomeState.mapType`, en memoria), así que aquí es
   estado local de la pantalla — misma vida que en Home, sin inventar persistencia.
5. Corregir el KDoc de `MapFab.kt` para que nombre a sus consumidores reales.

## Criterio de éxito

- Los cuatro mandos responden en device: centrar en mí, centrar en este aparcamiento, encuadrar
  ambos, y cambiar mapa (⚠️ ojo al defecto conocido de MAP-TYPES-001: el JSON de marca sólo rinde
  en `NORMAL`).
- El FAB del aparcamiento sólo aparece cuando hay pin; el de punto medio, sólo con pin **y** GPS.
- Ningún `MapCircleFab` nuevo: se usa el que ya existe.
- La galería mock no aplica (los FABs viven sobre el mapa, que la galería no monta).

## Consumidores auditados

| sitio | veredicto |
|---|---|
| `MapControlButtons.kt` | ⛔ vivo pero sin llamar → se conecta y se le ajusta el sujeto |
| `MapFab.kt:27` | ⛔ KDoc que nombra un consumidor inexistente → corregido |
| `HomeMapFabColumn` | ✅ intacto: Home tiene sus propios estados (identidad del coche, follow) |
| `MapTypeToggle` | ✅ reutilizado sin tocar |
| `ParkingHistoryDetailScreen` | 🔧 monta la columna + el toggle y empuja `CameraTarget` |
