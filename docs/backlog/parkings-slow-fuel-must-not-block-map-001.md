# PARKINGS-SLOW-FUEL-MUST-NOT-BLOCK-MAP-001 · El mapa no espera al otro proveedor

**Estado:** ✅ Done · rama `bugfix/PARKINGS-SLOW-FUEL-MUST-NOT-BLOCK-MAP-001-independent-loads` · worktree `../Paparcar-parkings-pan`

## Problema
Durante la comprobación de la ampliación francesa en Pixel 8, el usuario comunica que los parkings no se actualizan al panear. La instalación observada es prod release 1.7.0. En París se comprueba carga y relevo al cambiar de zona, pero una captura a los 4,5 s de un desplazamiento aún conserva los marcadores anteriores; en otra posterior ya aparecen los nuevos. No se atribuye toda esa espera a una sola causa sin traza de red del cliente.

## Evidencia y causa comprobable en código
La API conjunta responde con la versión publicada tanto en París como en Gentilly; Dublín y su entorno quedan fuera del snapshot y ordenan respaldo a Overpass. La app llega a actualizar lista y marcadores franceses.
HomeViewModel tiene dos serializaciones adicionales a OverpassQuota: queueComplementRefresh espera cada job y nearbyRequestMutex envuelve ambos use cases completos. Una fuente de combustible retenida impide resolver la pregunta de parkings de la nueva cámara incluso si su API podría responder. Se preparan pruebas con una fuente retenida para aislar ese bloqueo de la red y del dibujo de marcadores.

## Invariante y diseño
Cada sección responde y se cancela por su propio dueño. Una descarga nacional, un geocoder o el CDN de parkings no consume un turno de Overpass. Su único presupuesto de concurrencia permanece en OverpassClient.SHARED_QUOTA, compartido por los consumidores reales de Overpass, sin alterar sus límites ni paciencia. Se conserva queueComplementRefresh con sus join: el calentamiento no lanza los dos respaldos de Overpass a la vez. Se retira únicamente el mutex transversal que bloquea entre refrescos distintos. Conservar debounce, rejilla, cachés, filtros, anclas, recuperación y la única tienda que leen mapa y lista.

## Criterio de éxito
- Parkings de la nueva cámara responden mientras la otra fuente permanece retenida.
- Se conserva el calentamiento secuencial: una segunda consulta de Overpass no compite con la primera por el presupuesto acotado.
- Se mantienen cancelación de respuestas anteriores, consulta por zona, no reconsulta por cada fix y paridad de mapa/lista.
- Pruebas de HomeViewModel y cuota Overpass, testAll y compilación prod/mock.
- Prueba de dispositivo con un build real actual, conservando datos mediante actualización si es posible; copia de configuración y firma autorizada expresamente por el usuario.

## Consumidores y límites
HomeViewModel y sus pruebas: retirar solo nearbyRequestMutex; conservar queueComplementRefresh y sus join. OverpassClient/OverpassQuota: autoridad conservada, verificar tests. ApiPublicParkingsSource, feeds nacionales, cachés, rejilla, UI, DI y detección: sin cambios de contrato. No ampliar países ni desplegar backend en este ticket. No registrar cuenta, coordenadas de la sesión ni credenciales en esta documentación.

## Validación
- Regresión dirigida ejecutada contra HomeViewModel sin modificar: FAIL real, esperaba 2 consultas de parkings y seguía en 1 mientras la fuente de combustible permanecía retenida.
- Tras retirar únicamente el mutex: HomeViewModelTest, 202 pruebas, y OverpassQuotaTest, 4 pruebas, cero fallos. Se conserva el test de calentamiento secuencial y los de cancelación, cambio de cámara, paridad de mapa/lista y no reconsulta por fix.
- `testAll :app:assembleProdRelease :app:compileMockDebugKotlin --console=plain`: BUILD SUCCESSFUL, salida 0. Incluye testAll y compilación prod/mock; no equivale a enlazar ni probar iOS en Mac.
- APK prodRelease 1.8.3 (22) instalado con `install -r` en Pixel 8 conservando sesión y datos. SHA-256 local y APK instalado coinciden: `5fe35108fe606e7a260e8c0e6699ae7f60f9d82284a1c154284e2c58401c1e5d`. Build basado en d2dcd026a más la corrección local sin commit.
- Verificación UI: París carga 112 parkings y 35 gasolineras. Al navegar a Gentilly aparecen sus cápsulas y al panear al sur se observan nuevos marcadores en la zona visible. No se ha medido una latencia de red ni comprobado exhaustivamente cada límite de celda.
- El emulador se apagó durante la sesión y se reabrió; la app conserva la sesión. El usuario eligió rechazar la medición. Tras el arranque posterior no apareció de nuevo el diálogo.
- Implementación y validación terminadas. Usuario autoriza commit e integración. `git diff --check` sin errores.
- Rebase de un commit sobre master `d52059125`, sin conflictos; incorpora la sincronización de identidad de app. Validación posterior: `./gradlew testAll :app:compileMockDebugKotlin :app:compileProdDebugKotlin --console=plain`, BUILD SUCCESSFUL en 2m 13s, salida 0; shared:testDebugUnitTest y testAll ejecutados.

## Autorización
Solicitud del usuario: diagnosticar y corregir el refresco durante la comprobación. El usuario autoriza expresamente commit y merge de esta rama al finalizar; incluye su actualización sobre master como parte de la integración. Push y publicación no solicitados. Tras el rechazo inicial de revisión automática, el usuario autorizó expresamente copiar local.properties, app/google-services.json, keystore.properties y keystore al nuevo worktree, compilar prodRelease e instalar con -r en Pixel 8. Copia realizada sin abrir contenido; los cuatro destinos están ignorados por Git.
