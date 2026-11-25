# UI-PLAY-IN-APP-REVIEW-001 · Pedir valoración después de aparcar

**Estado:** ✅ Done · implementación Android validada localmente.

## Problema

Paparcar carece de un momento integrado para solicitar una valoración a quien ya ha usado con éxito la función de aparcar.

## Diseño

- Tres aparcamientos nuevos guardados por acción manual normal en Home, con al menos un éxito en un proceso anterior. El umbral evita interrumpir en la primera sesión y exige que se haya usado la función con éxito varias veces. Se excluyen la primera experiencia guiada, correcciones, reaparcamientos desde edición, avisos de detección y guardados fallidos.
- La Activity Android inicia Play In-App Review si sigue en primer plano. Play decide cuándo muestra su diálogo y conserva su propia cuota. No hay cooldown local, interfaz propia, encuesta previa ni inferencias sobre la respuesta del usuario.
- iOS pendiente: aplicar el mismo criterio a la API nativa de StoreKit y probar con un host Apple.

## Consumidores auditados

- `HomeViewModel.confirmAddParking` emite `ManualParkingSaved` después de guardar y cerrar el editor. Home entrega el efecto si su pestaña está activa y la entrada de Plus y la petición de consentimiento no compiten por la UI.
- El host de producción Android integra `review-ktx:2.0.2`. Conserva un contador local acotado al umbral, sin IDs de cuenta. Comprueba que la Activity esté reanudada y enfocada tanto al solicitar como al presentar, y deja pasar una única operación en curso.
- El host mock y el host iOS no invocan Play. La única superficie nueva la dibuja el SDK, así que las pruebas de Home cubren el evento y no hay estado visual propio que reproducir en el catálogo.

## Criterio de éxito

El flujo se solicita tras un guardado elegible a partir del umbral y después de la primera sesión. No se solicita tras una corrección, falsa detección ni error. Validar los dos flavors y usar una distribución de prueba de Play para comprobar el diálogo real.

## Validación local

- `./gradlew testAll :app:testProdDebugUnitTest :app:assembleProdDebug :app:assembleMockDebug --console=plain --rerun-tasks` terminó con código 0. Se ejecutaron 4.244 pruebas de shared, 4 del grafo mock, 3 pruebas del umbral en prod y la compilación klib de iOS. El intento offline no pudo resolver los AAR de Play Review. El build final usó la descarga autorizada.
- Tras el rebase sobre Invitaciones, `./gradlew :shared:testDebugUnitTest :shared:compileKotlinIosSimulatorArm64 --console=plain --offline --rerun-tasks` terminó con código 0. La suite conjunta `testAll` con ambos APK requirió los AAR de Play Review que no estaban disponibles offline.
- `git diff --check` terminó con código 0.
- Queda pendiente observar el diálogo real con una instalación de una distribución de prueba de Google Play. La API deja a Play decidir si muestra la valoración.
