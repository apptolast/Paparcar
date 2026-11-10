# Mapa operativo de Paparcar
Verificación estática: 2026-11-07, HEAD `f84ade96a`. No implica pruebas en dispositivo ni estado remoto de producción.

## Fuentes y orden de lectura
| Tarea | Fuente que se reutiliza | Entrada en código |
|---|---|---|
| Arquitectura/KMP | [ARCHITECTURE](../ARCHITECTURE.md), revisada parcialmente el 2026-11-07 | settings y build.gradle.kts; shared source sets; di |
| Detección | [PARKING-DETECTION](../detection/PARKING-DETECTION.md), [readiness](../detection/DETECTION-READINESS.md), tickets det-* | domain/detection, domain/usecase/detection y parking; androidMain/detection |
| UI | [COLOR-SYSTEM](../design/COLOR-SYSTEM.md), [branding](../branding/README.md), [reglas detalladas](../../CLAUDE.md) | ui/theme, ui/components, presentation |
| Datos del vehículo | [categorización](../architecture/VEHICLE-CATEGORIZATION.md) | Vehicle, VehicleSize, CarbodyType; VehicleRepositoryImpl |
| Backend/privacidad | firestore.rules, firestore.indexes.json, firebase.json; [contrato analytics](../analytics-data-contract.md) | data/datasource/remote; functions/src |
| Plus | [decisión de tiers](../backlog/plus-alerts-and-tiers-001.md) | domain/entitlement; RevenueCatEntitlementRepositoryImpl |
| Parkings | [investigación](../parking-data-research.md), [pipeline](../../tools/parkings/README.md) | data/parkings, functions/src/parkingsCell.ts |
| Calidad | build.gradle.kts y [.github/workflows/ci.yml](../../.github/workflows/ci.yml) | commonTest, androidUnitTest, app/src/test |
| Release | [proceso](../release/RELEASE-PROCESS.md), [seguridad](../release/RELEASE-SECURITY.md), [ficha](../release/play-listing/README.md) | tools/release/cut.py y play_api.py |
| Web | [web/README](../../web/README.md) | web/build.mjs → hosting/public |
| Pendientes | [backlog](../backlog/README.md), [roadmap](../ROADMAP.md), [deuda](../BUGS_AND_DEBT.md) | contrastar ticket con código y Git |
| iOS | [plan](../IOS-IMPLEMENTATION-PLAN.md), [verificación Mac](../VERIFICACION-MAC-F0.md) | iosApp y shared/src/iosMain; no interpretar planes como ausencia de implementación |

Las rutas de código sin prefijo de source set son relativas a
`shared/src/commonMain/kotlin/com/rndeveloper/paparcar/`.
Los documentos de investigación, auditoría y archive conservan contexto histórico; no sustituyen implementaciones ni decisiones posteriores.

## Entradas y dependencias
`PaparcarApp/MainActivity` alojan Android; `MainViewController/PaparcarIosBootstrap` alojan iOS.
`AppViewModel/SplashViewModel` gobiernan bootstrap y acceso; `App.kt` compone navegación.
La secuencia documentada es Splash → Auth → VehicleRegistration → Onboarding → Permissions → Home.
La navegación principal es Home/Vehículos/Ajustes; confirmar condiciones de routing en AppState/AppViewModel antes de alterarla.

Gradle declara tres módulos: app, shared y mockkit. Android usa JVM 17; shared declara Android, iosArm64 e iosSimulatorArm64 con framework ComposeApp. El plugin Android clásico de shared es deliberado por el problema documentado de empaquetado de composeResources. No migrarlo por modernización cosmética.
Gradle y dos raíces npm (functions, firestore-rules-tests) tienen dependencias y validaciones distintas.

## Inventario funcional
**Clasificación:** “implementado y verificado (estático)” significa que se inspeccionó implementación concreta; no acredita ejecución. “Parcialmente verificado” indica que se localizaron piezas y contratos, pero no se recorrió todo el flujo. Ninguna fila acredita disponibilidad comercial o funcionamiento end-to-end.

| Área / objetivo | Estado y recorrido local | Persistencia, pruebas y límite |
|---|---|---|
| Vehículos y estado | Parcialmente verificado: VehicleRegistrationViewModel → VehicleRepositoryImpl; VehiclesViewModel; DeclareActiveVehicle/SwapActiveVehicleFences | Room + reconciliación remota; límites en VehicleEntitlementGate. No confundir primario con coche conectado |
| Conducción/aparcamiento | Implementado y verificado estático en resolver, evaluador, confirmación e intake | UserParking + Room, provenance y workers. Tests de evaluadores; requiere campo para fiabilidad |
| Ubicación del coche | Implementado estático: ConfirmParking escribe sesión y conserva ancla; corrección/manual tienen casos de uso propios | Geofence por sesión, registro fallido no borra pin; no usar posición actual del teléfono como coche |
| Historial/viajes | Parcialmente verificado: UserParkingRepositoryImpl, VehicleHistoryCalculator, ParkingHistoryViewModel, HomeTripController | routePolyline/distancia, sesiones cerradas; HistoryEntitlementGate. Geometría y estadísticas no son equivalentes |
| Salida y plaza liberada | Implementado estático: ProcessConfirmedDeparture, Nominate/WithdrawSpotRelease, ReportSpotReleased | RELEASING/PROVISIONAL/CONFIRMED/RETRACTED; estado/política/TTL. Tests de nominación localizados; no publicar garajes privados |
| Consulta comunitaria | Parcialmente verificado: ObserveNearbySpots, SpotRepositoryImpl, HomeSpotsController | Firestore + caché Room; autenticación; antigüedad y TTL importan |
| Parkings públicos | Parcialmente verificado: GetNearbyPublicParkings, ApiPublicParkingsSource, cachés y snapshot | OSM estático + ocupación municipal donde existe; no disponibilidad global en tiempo real |
| Gasolineras y precios | Parcialmente verificado: GetNearbyFuelStations, ResolveFuelPrice, RunFuelPriceWatch | Fuentes ES/FR/DE/IT y OSM; cachés Room/memoria; precio fechado, no prometer precio actual universal |
| Estadísticas y gastos | Parcialmente verificado: ComputePeriodStats, DeriveStatsInsights, StatsViewModel y VehicleStatsBlock | Medidas de sesión y precios/consumo; no se verificó una contabilidad de gastos independiente |
| Notificaciones | Parcialmente verificado: AppNotificationManager, receivers, FCM; functions onSpotPublished/onSharedCarParked | Permisos y deduplicación a revisar con sus tests; entrega remota no ensayada |
| Auth/perfiles | Parcialmente verificado: BaseLogin, AppViewModel, BootstrapUserData, UserProfileRepository, SignOut/DeleteAccount | Firebase Auth y perfil; OAuth/certificados reales no consultados |
| Coche compartido | Parcialmente verificado: SharedCarRepositoryImpl, sección en Vehicles, reglas sharedCars | Espejo de estado/membresía/historial; rutas compartidas según contrato crítico; no pantalla de grupo separada |
| Premium/compras/restauración | Implementado y verificado estático en RevenueCatEntitlementRepositoryImpl; PaywallViewModel localizado | Entitlement plus, offering default/current; SDK como autoridad, no Room/Firestore. Compra real no probada |
| Configuración/permisos | Parcialmente verificado: Settings/Permissions ViewModels, readiness y adaptadores plataforma | Preferencias + permisos OS. UI de salud no sustituye permiso real |
| Onboarding | Parcialmente verificado: OnboardingScreen, FirstSteps y VehicleRegistration | Condiciones del bootstrap y escenarios mock; no recorrido visual ejecutado |

Para estado de desarrollo, errores abiertos y próximos pasos usar el ticket concreto del backlog. Sus encabezados también pueden quedar obsoletos: verificar hash, implementación y test antes de afirmar que falta algo. No se han clasificado como descartadas funcionalidades por mera ausencia en esta muestra.

## Diseño constatado
`ui/theme/Color.kt` conserva PapGreen **#25F48C**, pero las superficies oscuras actuales son:
PapInkDeep **#08090E**, PapInk **#0D1117**, PapInkContainer **#131A24**, PapInkHigh **#1A2232**, PapInkHighest **#222C3E**.
PapOnDark es **#EBF2EF**, PapOnDarkMuted **#8EA0B4**, PapGreenMuted **#133D28** y PapGreenOutline **#226D49**.
La referencia inicial del usuario no es la paleta actual completa.

Marca, vigilancia y frescura tienen significados distintos; PapWatchGreen **#0FBF9A** no es PapGreen. El estado del vehículo se escribe y anima, no se codifica solo por color. Leer VehicleIdentity y SpotStateColors.
Tipografía por roles en PaparcarType; CLAUDE documenta Plus Jakarta Sans para las tres voces. No restaurar Outfit/Inter por comentarios históricos.
Iconos de sistema Rounded; ilustraciones/vehículos/marcadores propios multicolor sin tint. Logo generado desde maestro; no discos ni composición manual de símbolo+texto.
Reutilizar PapListItem/PapOutlinedCard/PapIconTile, divisores propios y componentes existentes.
HomeScreen/HomeState/HomeSlices y controladores distribuyen mapa, selección, trayecto y hojas. La accesibilidad, adaptación y estados visuales no se validaron en dispositivo en esta incorporación.
