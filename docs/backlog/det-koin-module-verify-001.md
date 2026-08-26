# DET-KOIN-MODULE-VERIFY-001 · Un binding de detección ausente no lo caza nada

**Estado:** 🔵 Abierto · sin rama · follow-up de `DET-DI-DETECTION-MODULE-001`

## Problema

El proyecto tiene 6 módulos Koin (`presentationModule`, `domainModule`, `detectionModule`,
`dataModule`, `androidDetectionModule`/`iosDetectionModule`, `androidPlatformModule`, `mockModule`)
y **cero verificación de que el grafo resuelva**. No hay `koin-test` en el build.

Un `get()` sin definición al otro lado no rompe la compilación ni ningún test: revienta con
`NoDefinitionFoundException` la primera vez que alguien pide ese objeto. Para detección eso
significa **en mitad de un viaje real**, que es justo donde no se puede depurar.

`DET-DI-DETECTION-MODULE-001` movió 36 registros entre ficheros y lo único que lo respaldó fue un
diff de conjuntos hecho a mano y arrancar la app. Eso no escala.

## Diseño

`koin-test` en `androidUnitTest` + un test que arranque `koinApplication { modules(...) }` y
resuelva las raíces reales — `CoordinatorParkingDetector`, `ConfirmParkingUseCase`,
`RunDepartureCheckUseCase`, `ObserveDetectionReadinessUseCase`… — en lugar de `verify()` a secas,
que exigiría un `extraTypes` largo por el `androidContext()` y los puertos de plataforma.

Alternativa más barata a evaluar primero: `module.verify(extraTypes = …)` solo sobre
`detectionModule`, que es el que concentra el riesgo.

## Criterio de éxito

Borrar a mano una definición de `detectionModule` hace fallar un test unitario, no un viaje.
