# HOME-LIVE-ADDRESS-READABLE-001 · La calle en ruta tiene espacio para leerse

**Estado:** ✅ Done — integrado en master mediante squash (2026-11-08).

## Problema
El usuario ve la dirección del trayecto cortada como «Calle…», seguida de ciudad y comunidad. El contexto administrativo ocupa espacio que necesita la calle para reconocerse.

## Doctrina violada
La información principal de ubicación debe poder leerse; ciudad y región son contexto secundario. La dirección del viaje procede del coche, conforme a UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001.

## Señales / datos disponibles
PlaceLine, dirección estructurada del geocodificador y drivingSecondaryLine. BrowsePeek coloca la línea secundaria en el hueco previsto para un dato corto de distancia; la card/chip tienen sus propios límites de texto.

## Diseño
BrowsePeek compone el lugar principal y su contexto en una sola línea con PEEK_LINE_JOINER. El único recorte está al final: el contexto administrativo cede antes que la calle. No se pasa una frase larga al argumento far de peekWhereLine, reservado al sufijo corto que debe sobrevivir (distancias).

Se conserva la altura fija de los peeks, el resolver de POI/calle, la indicación de proximidad y la dirección estructurada completa. No se añade geocodificación ni copy visible nuevo. La referencia es UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001; no cambia el algoritmo de detección.

## Criterio de éxito
- La calle o lugar principal tiene ancho para leerse durante el trayecto.
- Ciudad/comunidad no desplazan la calle; el contexto queda subordinado al lugar principal.
- Peek, card y chip conservan el mismo sujeto y los recursos/locales existentes.
- Compilación prod/mock y validación proporcional desde este worktree.

## Consumidores auditados
- **Cerrado — BrowsePeek en ruta:** el contexto viaja al final de la frase recortable, sin reservar una columna completa para ciudad/comunidad.
- **Exento — peekWhereLine de aparcados/zonas:** las distancias conservan su columna sin recorte. No se cambia la gramática de otras superficies.
- **Exento — HomeVehicleCard/HomeVehicleChip:** ya reciben solo PlaceLine, sin el sufijo administrativo que causaba este defecto.
- **Exento — PlaceLine y datos:** se conserva el resolver y la fuente del coche; los geocodificadores Android/iOS mantienen calle/ciudad/región separados.
- **Cerrado — previews/galería:** calle y contexto largos, POI con calle larga, ancho de 320 dp y preview con fuente 1.3. La preview de conducción recibe por fin userAddressAndPlace, no únicamente cameraAddressAndPlace.

## Validación
- `studio_gradle_build -p <worktree> testAll :app:compileProdDebugKotlin :app:assembleMockDebug --continue --console=plain`: **4021 correctas / 5 fallos previos en shared; 4 correctas / 0 fallos en app/mock**. Compilación prod debug, APK mock y tareas nativas del agregado sin fallos de compilación.
- Los mismos cinco fallos de la base `f84ade96a` ya contrastada en esta sesión: BacklogStatusGuardrailTest (2, auth-login-redesign-140 sin Estado), LocaleParityGuardrailTest (notif_bt_saved_text Android sin consumidor) y HomeSlicesTest (2 expectativas sobre la capa de parkings).
- `git diff --check`: correcto. Sin pruebas nuevas para un cambio de distribución de texto; se reutiliza la suite y se verifica visualmente.
- El render de preview devuelve `Failed to get module` para el worktree en el IDE abierto sobre el árbol principal. Se compilaron los previews y el catálogo, con muestras de 320 dp y fuente 1.3; esos dos tamaños no se declaran renderizados.
- **Verificación visual en Oppo, paquete mock separado:** instalación Success y arranque Status ok. En «Peek · en ruta: calle y contexto largo» la captura muestra completa la calle de ejemplo y el recorte en el contexto («El Pu…»); en «POI y calle larga» conserva el POI y recorta después la calle secundaria. Las capturas y la jerarquía de UI constan en la conversación; no se han usado datos reales como fixture.
- La revisión en dispositivo se hizo con el paquete mock. El usuario autorizó commit y squash de ambos trabajos en master; la APK prod registrada en el ticket de preguntas es anterior a este ajuste de dirección.
- La revisión inicial no hizo commits ni merges. El usuario autorizó posteriormente el cierre y la integración en master; se mantiene la configuración de Claude.

## Validación conjunta para integrar
- Integrado con HOME-PARKING-QUESTIONS-KEEP-THE-MAP-001 en un worktree temporal, sin conflictos de código.
- `testAll :app:compileProdDebugKotlin :app:assembleMockDebug --continue --rerun-tasks --console=plain`: **4051 correctas / los mismos 5 fallos previos en shared; 4 correctas / 0 fallos en app/mock**. Prod, mock y klib iOS sin fallos de compilación.
- Los dos tickets quedan cerrados; el índice conjunto contabiliza 830 ficheros, 739 cerrados, 82 abiertos y 9 que no son tickets.
- Integración mediante un squash por ticket. No incluye la documentación local de otros trabajos ni cambios en credenciales o configuración de Claude.
