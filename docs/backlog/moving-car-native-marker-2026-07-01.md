# DRIVE-PUCK-NATIVE-001 — coche en movimiento como marker nativo (fork kmp-maps)

**Fecha:** 2026-07-01
**Estado:** BACKLOG (prueba / spike)
**Prioridad:** baja (mejora de pulido; el overlay actual es aceptable)

## Problema

El coche que se pinta mientras **conduces** no es un marker nativo del mapa: es un overlay
Compose (centrado cuando la cámara sigue, proyectado cuando el usuario panea — ver
`PaparcarMapView.puckOffsetFromCenterPx` / rama de proyección, [FOLLOW-001] [PUCK-FLICKER-001]).

Como el overlay se dibuja **fuera del render del mapa**, al **arrastrar el mapa durante un viaje**
el coche va ~1 frame por detrás de las teselas ("se recalcula al mover"). Las apps de transporte
(Uber/Bolt) no tienen esto porque su marca es un **marker nativo dentro del mapa**, movido y girado
por el motor del mapa en el mismo pase de render → lag-cero.

## Por qué HOY no se puede (confirmado en fuentes de kmp-maps 0.8.1 y 0.9.1)

La vía de marker nativo de kmp-maps está doblemente bloqueada para un marker **en movimiento**:

1. **Key inestable.** En `androidMain/.../Map.kt`, cada marker se envuelve en
   `key(marker.getId(), marker.contentId)`. `Marker.getId()` = `"marker_${hashCode()}"` y
   `coordinates` entra en el `hashCode` (es un `data class`). Al cambiar de posición cada frame,
   cambia la key → Compose destruye y recrea el `MarkerComposable` → **flicker**. La librería
   incluso tiene el código para mover suave (`markerState.position = newLatLng`), pero lo inutiliza
   ese `key()`.
2. **Sin rotación nativa.** `AndroidMarkerOptions` NO expone `rotation`/`flat`. Girar el coche exige
   cambiar de bitmap (heading en el `contentId`) → otra recreación al girar.

Por eso los markers **estáticos** (plazas, coche aparcado, zonas, y el coche CONGELADO en fase
Candidate) sí son nativos y van perfectos: no se mueven, no disparan el bug. Solo el coche en
movimiento no puede serlo.

Nota: kmp-maps es open-source (Software Mansion): https://github.com/software-mansion/kmp-maps

## Propuesta (spike)

Fork de kmp-maps con dos cambios y wire del puck como marker nativo:

1. Keyear el marker por un **id estable** proporcionado por el caller (p. ej. un nuevo campo
   `Marker.stableId`) en vez de `getId()`. Con key estable, `markerState.position = newLatLng` mueve
   el marker nativo suave (glued como la polilínea), lag-cero al panear.
2. Exponer `rotation` (+ `flat`) en `AndroidMarkerOptions` y pasarlo al `Marker`/`MarkerComposable`
   nativo, para girar el coche sin recrear bitmap.
3. En `PaparcarMapView`: dar al puck un `stableId` constante ("driving_puck") + `contentId` sin
   heading, y pasar el rumbo por `rotation`. Sustituir el overlay proyectado por este marker nativo.

Publicar el fork (JitPack) hasta que el PR upstream sea aceptado.

### Alternativa mayor (si el fork no compensa)
Migrar la capa de mapa Android a `com.google.maps.android:maps-compose` directamente (marker nativo
persistente + `rotation`, estándar de la industria). Coste: reescribe el lado Android de
`PaparcarMapView` y pierde la abstracción KMP (iOS necesitaría su propio MapKit aparte).

## Plan de ejecución (fork → corregir → PR → contactar al dev)

Estrategia acordada 2026-07-01: no solo parchear para nosotros, sino **arreglar el bug upstream** y
devolverlo a la comunidad.

1. **Acceder al repo público y forkear.** https://github.com/software-mansion/kmp-maps (Software
   Mansion, open-source). Fork a nuestra org/usuario.
2. **Localizar el bug.** `.../androidMain/.../Map.kt`, bloque `markers.forEach { marker -> key(marker.getId(), marker.contentId) { ... } }`.
   - `Marker.getId() = "marker_${hashCode()}"` y `coordinates` (un `var`) entra en el `hashCode` del
     `data class` → la key cambia en cada movimiento → recreación → flicker. El propio código de
     movimiento suave (`LaunchedEffect(marker.coordinates) { markerState.position = newLatLng }`) queda
     inutilizado por esa key.
3. **Corregir (2 cambios):**
   a. Añadir un identificador **estable** al `Marker` (p. ej. `val stableId: String? = null`) y keyear
      por él cuando exista: `key(marker.stableId ?: marker.getId())`. Retrocompatible (sin `stableId`
      = comportamiento actual). Con `stableId` fijo, la posición se actualiza vía `markerState.position`
      sin recrear → movimiento nativo suave, lag-cero.
   b. Exponer `rotation` (+ `flat`) en `AndroidMarkerOptions` y pasarlo al `Marker`/`MarkerComposable`
      nativo → girar sin cambiar bitmap.
   c. **Paridad iOS** (mismo PR): en `iosMain`, reutilizar la `MKAnnotationView`/anotación por
      identificador estable y aplicar rotación equivalente. Sin esto el PR es solo-Android y no lo
      aceptarán.
4. **Publicar el fork mientras tanto:** `publishToMavenLocal` o JitPack; apuntar el catálogo de
   versiones de Paparcar al fork. Wire del puck como marker nativo (`stableId="driving_puck"`,
   `contentId` sin heading, rumbo por `rotation`); quitar el overlay proyectado.
5. **Validar** contra el criterio de aceptación (abajo) en device.
6. **Upstream:** abrir **PR** en software-mansion/kmp-maps con los 2 cambios + tests + demo (gif del
   coche moviéndose sin flicker). Abrir antes un **issue** describiendo el flicker de markers en
   movimiento (con repro mínimo) y enlazarlo al PR.
7. **Contactar al dev:** comentar en el issue/PR; canal comunidad kotlinlang Slack (hilo #feed del
   anuncio de KMP Maps) y/o X (@swmansion). Objetivo: que lo mergeen y publiquen para eliminar el fork.

### Notas / riesgos del PR
- Mantener API **retrocompatible** (campos nuevos opcionales con default) o no lo mergean.
- Necesitan paridad Android+iOS y probablemente tests + actualización de docs.
- Si tardan/rechazan, nos quedamos con el fork publicado (coste = mantenerlo al día con upstream).

## Criterio de aceptación
- Coche en movimiento pintado como marker **dentro** del mapa (no overlay).
- Al arrastrar el mapa durante un viaje, el coche queda **glued sin lag** (como la polilínea del rastro).
- Mueve y **gira** suave, decoupleado del follow.
- iOS sigue soportado (si vía fork) o plan explícito para MapKit (si vía maps-compose).

## Estado actual (lo que YA está resuelto sin esto) — [FOLLOW-001] [DET-PHASE-001]
- Coche en movimiento visible **independiente del follow** vía overlay proyectado (Web Mercator).
- Coche **congelado** al aparcar = marker nativo estático + punto azul = peatón.
- Pendiente en Opción 2 (en curso): interpolación de GPS (glide entre fixes) para suavizar el
  movimiento entre lecturas.
