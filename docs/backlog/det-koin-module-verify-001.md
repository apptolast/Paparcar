# DET-KOIN-MODULE-VERIFY-001 · Un binding de detección ausente no lo caza nada

**Estado:** ✅ Done · rama `chore/DET-KOIN-MODULE-VERIFY-001-koin-verify` (el hash del merge vive
en `MEMORY.md`, este doc viaja dentro de ese commit)

## Problema

El proyecto tiene 6 módulos Koin (`presentationModule`, `domainModule`, `detectionModule`,
`dataModule`, `androidDetectionModule`/`iosDetectionModule`, `androidPlatformModule`, `mockModule`)
y **cero verificación de que el grafo resuelva**. No había `koin-test` en el build.

Un `get()` sin definición al otro lado no rompe la compilación ni ningún test: revienta con
`NoDefinitionFoundException` la primera vez que alguien pide ese objeto. Para detección eso
significa **en mitad de un viaje real**, que es justo donde no se puede depurar.

`DET-DI-DETECTION-MODULE-001` movió 36 registros entre ficheros y lo único que lo respaldó fue un
diff de conjuntos hecho a mano y arrancar la app. Eso no escala.

## Resolución

`KoinModuleVerifyTest` (androidUnitTest, `di/`), dos tests sobre el **grafo Android de producción
tal y como lo lista `PaparcarApp.startKoin`** — presentation + domain (`includes(detectionModule)`)
+ data + androidDetection + androidPlatform — más un `boundaryModule` que liga con sus fakes de
producción los DOS contratos que viven fuera de `:shared` (`AppNotificationManager` de `appModule`,
`AuthRepository` de BaseLogin):

1. **`verify()` estático de koin-test** (la alternativa barata que este doc pedía evaluar primero
   — y basta). Refleja el constructor de cada definición declarada como clase CONCRETA y exige que
   cada parámetro esté ligado en el set. `extraTypes` mínimo y justificado: `Context`
   (lo aporta `androidContext()`), `Function0/Function1` (factorías inline en el lambda) y
   `UserScopedRepository` (verify desenvuelve `List<T>` a su tipo ELEMENTO; la lista se monta
   inline con `listOf(get(), …)`).
2. **Testigo de población**: el set aplanado debe contener las raíces de detección
   (`CoordinatorParkingDetector`, `ConfirmParkingUseCase`, `RunDepartureCheckUseCase`,
   `ObserveDetectionReadinessUseCase`, `GeofenceManager`). Un `detectionModule` vaciado o un
   wrapper que dejase de incluirlo pondría el verify en verde con cero sujetos — un verde sin
   dónde mirar y un verde sin hallazgos no pueden ser el mismo verde.

### Lo que el verify destapó ya en la primera pasada

`phaseSink: DetectionPhaseSink` del coordinator no existía como binding: producción lo resolvía
con un `get<MutableDetectionRuntimeState>()` tipado dentro del lambda, así que la identidad
"el runtime state ES el phase sink" era invisible para el grafo. Arreglo de producción, no de
whitelist: `MutableDetectionRuntimeState` pasa a `binds arrayOf(DetectionRuntimeState::class,
DetectionPhaseSink::class)` y el lambda vuelve a un `get()` plano.

### Límites (por diseño de verify)

- Las definiciones declaradas tras interfaz (`single<Foo> { FooImpl(get()) }`) no tienen
  constructor reflejable y se saltan: el test prueba el lado CONSUMIDOR del grafo, que es donde
  muerde un binding ausente. Los `get()` internos de esos lambdas los cubre el arranque real.
- Los nullables NO cuentan como opcionales (solo los params con default): las tres deps nullable
  del coordinator quedan verificadas de verdad.

## Criterio de éxito — CUMPLIDO y demostrado

Borrar a mano `factory { EvaluateParkingDecisionUseCase(get()) }` de `detectionModule` hace fallar
el test nombrando exactamente el tipo ausente y su consumidor:

```
MissingKoinDefinitionException: Missing definition for
'[field:'evaluateParkingDecision' - type:'…EvaluateParkingDecisionUseCase']'
in definition '[Singleton: '…CoordinatorParkingDetector']'.
```

Verificado además: suite completa `:shared:testDebugUnitTest` en verde y
`:app:compileMockDebugKotlin`/`:app:compileProdDebugKotlin` en verde con el `binds` nuevo.

## Consumidores auditados

- `phaseSink = get<MutableDetectionRuntimeState>()` era el único call site que resolvía el sink
  por su clase concreta (grep `get<MutableDetectionRuntimeState>`); ahora `get()` plano.
- `bind`/`binds` en `DetectionModule`: el único otro uso era el propio
  `bind DetectionRuntimeState::class` que este cambio absorbe en `binds arrayOf(…)`.
- El grafo iOS (`iosDetectionModule`) queda fuera: `verify()` de koin-test es JVM y el módulo es
  de iosMain. El día que `commonTest` compile en iOS ([project_ios_commontest_never_compiled_001])
  puede plantearse su gemelo.
