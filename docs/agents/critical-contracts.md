# Contratos críticos y validación
Verificación estática: 2026-11-07, HEAD `f84ade96a`. Guía de entrada; los detalles y umbrales pertenecen al código y a [PARKING-DETECTION](../detection/PARKING-DETECTION.md).

## Detección: seguir la evidencia completa
1. **Elegir dueño.** ParkingStrategyResolver consulta flota y ACL: un vehículo emparejado que aparca y está conectado gana BLUETOOTH; después se evalúa si el primario aparca (NONE si no); resto COORDINATOR. Este es el orden ejecutable, aunque la tabla KDoc del resolver enumera NONE primero.
2. **Armar.** AR IN_VEHICLE, salida de geofence y movimiento significativo nominan. EvaluateArEnterArm/EvaluateGeofenceExit y coordinatorMayArm deciden admisión. Solo MANUAL tiene excepción a la estrategia; ARRIVAL_HANDOFF es automático.
3. **Medir.** El stream de ubicación, evidencia sostenida de conducción, parada, pasos y desplazamiento de salida sustentan el veredicto. Un único fix rápido, STILL o un score alto no autorizan por sí solos un aparcamiento.
4. **Decidir.** EvaluateParkingDecision devuelve Confirmed, Rejected, Inconclusive, Prompt o cierres terminales por desplazamiento humano/vehículo no vial. No colapsar pregunta, rechazo de candidato y fin de trayecto en el mismo estado.
5. **Guardar.** ConfirmParking protege contra relocalización inferida de un pin afirmado por el usuario y reaparcamientos inverosímiles; registra detectionPath/armEvidence/decisionEvidence. Fecha del pin: ancla medida acotada por reloj de confirmación. La ruta se recorta al aparcamiento y se valida su frescura.
6. **Persistir antes de adornar.** UserParkingRepositoryImpl reemplaza la sesión activa del mismo vehículo mediante transacción Room, con pendingSync y updatedAt; agenda sincronización. No es una transacción distribuida Room+WorkManager: si enqueue falla, la fila local sigue guardada y se registra el fallo. ConfirmParking agenda enriquecimiento, retira geofence huérfana y solicita restauración si falla el registro.
7. **Abandonar/compartir.** La nominación RELEASING no equivale a salida confirmada. ProcessConfirmedDeparture recibe explícitamente publishSpot, evita republicaciones provisionales y consulta contradicción del pin y SpotReleasePolicy. Cierre de sesión y publicación son decisiones distintas. Zonas privadas y formas demasiado amplias no producen plaza pública. No rejuvenecer una salida vieja por llegar tarde.

**Host Android:** CoordinatorDetectionService usa Channel de comandos e intake único, con jobs BT separados (caza, seguimiento y vigilancia). Reconexión BT cancela caza pendiente; arbitraje puede ceder al coche conectado sin sumar puntos al Coordinator.
Receivers AR/BT/geofence/boot y workers forman parte del sistema, no son duplicación accidental.
Consultar ParkingDetectionConfig para constantes; no copiar valores desde esta guía.

**Muerte del proceso:** pending/heartbeat y ruta durable permiten reconciliar; no reconstruyen mágicamente el detector en memoria. Un onStartCommand con intent null tras START_STICKY detiene el FGS sin promoverlo porque no hay sesión recuperable. GeofenceJanitor, ParkingSafetyNet, backfill y nuevos triggers ofrecen recuperación condicionada; no prometer continuidad perfecta ante force-stop/OEM.
**Sin BT:** Coordinator si corresponde al vehículo. **Sin permiso de ubicación:** FGS puede ser rechazado; detener y exponer readiness, no bajar seguridad. **Sin red:** conservar sesión Room y pendingSync; las plazas remotas dependen de caché y reintento, no publicar eternamente información caducada.
**iOS:** IosDetectionController y adaptadores CL/CM/sensores/sync existen. El scanner BT clásico no aporta el carril determinista Android. Validar lifecycle y delivery en hardware Apple antes de prometer paridad.

Antes de cambiar una señal: localizar todos sus consumidores, clasificar cerrado/cubierto/exento y probar veredictos, persistencia, mappers y camino de publicación. Mantener el log canónico y fixtures de campo; no borrar un guard sin entender el fallo original.

## Datos, Firebase y privacidad
Firebase se consume vía GitLive para Auth/Firestore/Analytics; Android añade Crashlytics y Messaging. BaseLogin es dependencia propia externa, no se modifica aquí.
firebase.json declara **(default)** y **debug**, mismas reglas e índices. La skill de campo asigna release a default y debug a debug; comprobar build antes de buscar telemetría.

| Datos | Autoridad/contrato local inspeccionado |
|---|---|
| users/{uid}, vehicles, parkingHistory, zones | Reglas limitan al uid; repositorios locales y reconciliación para trabajo offline |
| spots | Lectura autenticada; creación con uid propio, estado permitido y caducidad acotada; terceros solo contadores; retirada/caducidad específicas |
| sharedCars | Membresía, estado y sesiones; autorización por miembro/dueño según operación |
| sharedCarInvites | Get autenticado, list denegado; no confundir invitación con libre lectura del grupo |
| diagnostics / diagnostics_reports | Acceso cliente del propio uid; no volcar trazas personales al contexto compartido |
| diagnostics_config | Cliente lee/borra propio gate; no crea/actualiza; configuración administrativa independiente |
| zoneAlertSubs | Suscripción del propio usuario; FCM en servidor, revisar esquema completo antes de tocarlo |
| publicParkings | Lectura pública deliberada; escritura cliente denegada; ingestión Admin aparte |
| Resto | Denegación final por defecto |

Esto describe reglas locales, no una certificación de seguridad ni reglas desplegadas. Admin SDK no está restringido por estas reglas.
functions/src/index.ts exporta onSpotPublished y onSharedCarParked con onDocumentWritten, ingestOccupancy programado y mergeCell HTTP. No convertirlos a onDocumentCreated: las promociones/actualizaciones también disparan entrega.
Hay tests npm de funciones y harness de reglas con emulador. No se ejecutaron aquí ni se consultó Firebase remoto.
Storage, App Check, Remote Config SDK y OneSignal no quedaron acreditados en la búsqueda de shared/src; eso no demuestra ausencia en toda la infraestructura. El gate diagnóstico encontrado es Firestore, no evidencia de Remote Config.

**Espejo compartido:** la decisión documentada envía geometría y cifras del viaje; sharesRoutes controla su visualización al leer. No afirmar que es un control de subida. No añadir matrícula, armEvidence, detectionPath o tripMaxSpeedMps al espejo: la lista blanca pertenece al tipo.
**Analytics/Meta:** consultar analytics-data-contract y AdAttribution antes de ampliar eventos. CLAUDE exige consentimiento legal, whitelist exhaustiva de nombres hacia publicidad y separación del identificador publicitario respecto a Firebase Analytics.

Los archivos de firma, local.properties y credenciales Play encontrados no se leyeron; se verificaron no rastreados e ignorados. Su presencia local no concede autorización para usarlos ni prueba restricciones correctas en consola.

## Integraciones externas y monetización
- **Mapas:** fork kmp-maps del catálogo; Google Maps Android / Apple Maps iOS según arquitectura documentada. No sustituirlo por upstream sin revisar el contrato de identificador estable del marcador.
- **Parkings:** ApiPublicParkingsSource, snapshot OSM y cachés de disco/memoria. Hosting reescribe /api/parkings.json a mergeCell; TTL servidor declarado de 120 s con datos vivos y 3600 s estáticos. Madrid es la fuente municipal localizada para ocupación; tarifas/capacidad estática no equivalen a ocupación. Guard predeploy protege snapshot de hosting: no saltarlo ni desplegar desde árbol sin celdas.
- **Combustible:** Minetur (España), data.economie.gouv.fr (Francia), Tankerkoenig (Alemania), carburanti.mise.gov.it (Italia), OSM/Overpass y promedios OpenVan. Cobertura y requisitos de claves son específicos de proveedor. Caché en memoria de estaciones declara 15 min y promedios 24 h; precio conserva su fecha propia. Costes, cuotas efectivas, restricciones de claves y disponibilidad de endpoints no se comprobaron en red.
- **RevenueCat:** RevenueCatEntitlementRepositoryImpl observa CustomerInfo; entitlement activo **plus** determina acceso. Ofertas: **default**, fallback current; packages monthly/annual. Precio y trial se leen de la tienda. Purchase comprueba que plus esté activo; restore devuelve si recuperó Plus. Login por uid y logout anónimo están contemplados.
- El estado de suscripción se mantiene inicialmente FREE y se alimenta desde SDK; no se copia a Room, preferencias ni Firestore. Caché SDK es autoridad; no inventar garantía offline de duración ilimitada. No añadir purchases Android manualmente junto al wrapper KMP.
- **Promo Fundadores:** la app autenticada solicita el regalo al llegar a Home. `foundersClaims/{uid}` admite una creación propia con sello del servidor mientras la campaña está abierta, y el cliente sólo puede leer su propio resultado. El cupo `promos/founders` y el registro `grants/{uid}` son privados del servidor. El resultado de la solicitud activa una lectura fresca de CustomerInfo. El acceso Plus sigue dependiendo de RevenueCat. El borrado se coordina mediante el trigger de Auth y el [runbook](../legal/ACCOUNT-DELETE-RUNBOOK.md).
- Gates: HistoryEntitlementGate, VehicleEntitlementGate, ZoneEntitlementGate y SharedCarEntitlementGate. Consultarlos para límites numéricos en cada tarea. Acciones con paywall soft; ver mapa/parkings/combustible sigue gratis. Los avisos gratuitos de combustible y plazas tienen contratos distintos.
- Precios, productos activos y disponibilidad por tienda requieren consola o compra de prueba; no se verificaron.

## Calidad: comandos con procedencia
Comandos declarados en build/CI/skills/package.json; **no ejecutados en esta incorporación documental**:
| Cambio | Comprobación |
|---|---|
| Suite agregada Kotlin | ./gradlew testAll --console=plain |
| Home dirigido | ./gradlew :shared:testDebugUnitTest --tests '*HomeViewModelTest' --console=plain |
| DI y flavors | ./gradlew :app:assembleProdDebug :app:compileMockDebugKotlin |
| Galería mock | ./gradlew :app:assembleMockDebug |
| Guards de fuente/recursos | Test dirigido correspondiente con --rerun-tasks |
| Functions | npm test en functions (compila y ejecuta node --test) |
| Reglas | npm test en firestore-rules-tests (emulador demo-paparcar + Vitest) |
| Web | node web/build.mjs --check, según CLAUDE/web |
| iOS completo | Job Apple de ci.yml; requiere Mac/frameworks nativos, no confundir con klib de testAll |

No ejecutar emuladores que descarguen componentes o instalaciones sin revisar dependencias disponibles y autorización. No hay detekt/ktlint en el catálogo inspeccionado; Konsist y guardarraíles son explícitos. No inventar tareas lint a partir del nombre del job “Lint & Unit Tests”.
commonTest contiene lógica pura y ViewModels; androidUnitTest cubre dependencias JVM/Android y guards; app aporta verificación del grafo mock. HomeViewModelTest contiene casos de liberación repetida, dismiss durante operación y cierre de otra sesión. La existencia de pruebas no acredita que pasen en HEAD.
CI validate compila prod/mock y ejecuta testAll; Apple se condiciona a workflow_dispatch o ramas feature/IOS-. El estado remoto de CI no fue accesible.

## Publicación
Versión local inspeccionada: applicationId com.rndeveloper.paparcar, versionName 1.5.0, versionCode 16; no significa publicada.
Proceso canónico en RELEASE-PROCESS y tools/release/cut.py. Firma separa upload key de firma Play; no copiar material ni ejecutar corte/upload/publicación en una tarea documental.
Verificar manualmente tracks, estado publicado, OAuth/SHA, restricciones de claves, permisos y declaraciones Play cuando la tarea lo requiera. Los informes históricos de consola no prueban el estado actual.
