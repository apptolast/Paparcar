# DET-A-CAR-CONNECTION-ARMS-WITHOUT-NAMING-THE-CAR-001 · el cable de Android Auto arma el carril, pero no dice de quién es el coche

**Estado:** 🟡 DISEÑO, sin implementar · rama
`feature/DET-A-CAR-CONNECTION-ARMS-WITHOUT-NAMING-THE-CAR-001-aa-signal` · worktree
`../Paparcar-aa-signal`

Nace de **issue #126** («¿sería muy complicado poner la app en Android Auto? ¿se podría hacer si el
usuario conecta el cable al coche?»). La issue tenía dos preguntas y esta es la segunda — la app de
Android Auto en sí (categoría POI, templates, revisión aparte en Play) **no es este ticket** y va
después del lanzamiento.

## Problema

El Coordinator arma con AR `IN_VEHICLE_ENTER` o con `GEOFENCE_EXIT`, y las dos son evidencia floja
de embarque: el AR dispara con **cualquier** vehículo (bus, taxi) y por eso la escalera de
`EvaluateArEnterArmUseCase` existe. Mientras tanto hay una señal que el móvil ya conoce y que nadie
está leyendo: **estar enchufado a un Android Auto**. Nadie conecta el móvil al coche andando.

## Doctrina — dónde encaja y dónde NO

- *El evento NOMINA, sólo el movimiento MEDIDO confirma.* Conectar Android Auto **arma**; no
  confirma nada por sí solo y no coloca ningún pin.
- *Fallo asimétrico.* Desconectar (apagar el coche) es un **candidato a parada**, no una parada:
  entra por `EvaluateParkingDecisionUseCase` como el resto, que sigue exigiendo conducción medida
  más pasos/egress.
- ⛔ **Cero señales nuevas dentro del scoring del Coordinator.** Esto es un TRIGGER que entra por el
  intake único ([DET-INTAKE-001]) y produce `ArmEvidence`; no es un término que sume puntos.
- ⛔ **No es el carril BT.** El carril BT es determinista porque la **MAC** dice de qué coche se
  trata. Android Auto **no expone identidad del head unit** a una app que no es del coche: sé que
  vas en *un* coche con Android Auto, no que sea *el tuyo* (el de un amigo también lo tiene). Por eso
  no puede heredar `BtRide` ni nada que autorice confirmación silenciosa.

## Señales / lo que la plataforma da

Verificado contra la fuente de AndroidX y la documentación (22-09-2026):

- `androidx.car.app:app` → `CarConnection(context)`, **`Context` normal**
  (`public CarConnection(@NonNull Context context)`): **no hace falta ser app de Android Auto.**
- Observa un `LiveData<Int>`: `CONNECTION_TYPE_PROJECTION` (Android Auto, cable **o** inalámbrico),
  `CONNECTION_TYPE_NATIVE` (Automotive OS), `CONNECTION_TYPE_NOT_CONNECTED`.
- Lee `content://androidx.car.app.connection` y escucha `ACTION_CAR_CONNECTION_UPDATED`. El
  `<queries>` del provider lo aporta el manifest de la propia librería al mergearse. Si no hay
  provider (móvil sin Android Auto), responde `NOT_CONNECTED` — no lanza.
- ⚠️ El constructor es `@MainThread` y entrega por `LiveData`: en `androidMain` hay que envolverlo en
  un `callbackFlow` con observación en el main looper, no leerlo desde el hilo del servicio.

### ⛔ El límite que decide el diseño: **el cable no nos DESPIERTA**

El receptor de ese broadcast lo registra la librería **en tiempo de ejecución**, y desde Android 8
un receptor declarado en el manifest no recibe broadcasts implícitos. O sea: **la señal sólo existe
mientras un proceso nuestro está vivo**. Es un refuerzo del carril cuando el sentry ya está en pie,
no una forma nueva de arrancar la detección desde cero. Escribirlo aquí antes de que alguien lo
implemente esperando lo contrario.

## Diseño

1. **`CarProjectionMonitor`** en `shared/androidMain/detection/` — envuelve `CarConnection` en
   `Flow<Boolean>` (proyección sí/no), con `distinctUntilChanged`. Cero lógica.
2. **Entra por el intake único** de `CoordinatorDetectionService`, en su propio slot de job, como ya
   hace el carril BT desde [DET-ONE-SERVICE-TWO-LANES-001]. Dos triggers nuevos:
   `CAR_PROJECTION_CONNECTED` y `CAR_PROJECTION_DISCONNECTED`.
3. **Un veredicto, no un predicado** ([DET-VERDICT-NOT-PREDICATE-001]):
   `EvaluateCarProjectionArmUseCase` decide si la conexión arma, y con qué evidencia. Antes de
   escribirlo: comprobar si `EvaluateArEnterArmUseCase` ya es superconjunto — dos evaluadores con
   los mismos parámetros donde uno contiene al otro son UN evaluador.
4. **`ArmEvidence.CarProjection`** (nuevo, `data object`): sin `DriveAuthorization`, sin
   `isVerifiedDeparture`, sin confirmación silenciosa — exactamente el trato de `BoardedAwayFromCar`.
   Los guardas anti-caminata siguen armados y un aparcamiento al final se PREGUNTA.
   Si el fix de la conexión cae DENTRO de la geocerca del coche aparcado, la pertenencia la prueba la
   geometría y no la proyección: ahí ya existe `BoardingAtCar` y se usa ese.
5. **La desconexión** no cierra nada: marca candidato y deja que
   `EvaluateParkingDecisionUseCase` haga su trabajo.
6. **Provenance**: `detectionPath` nuevo para que un pin puesto tras una conexión de proyección se
   pueda citar en un diagnóstico — si no se puede citar, no es un veredicto.

### 🟡 Pregunta abierta, deliberadamente fuera de alcance

¿Se puede **ganar** la pertenencia con el tiempo? Si la proyección coincide repetidamente con la MAC
de un coche conocido, el par (proyección, MAC) podría atarse a ese vehículo. Es la misma forma que
`OwnCarAudio` en iOS ([IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001]). No se diseña aquí: primero medir
que la señal sirve.

## Criterio de éxito

- Un viaje con el móvil enchufado arma el Coordinator **antes** que el AR, y el `armEvidence` del
  pin lo dice.
- Ningún pin nuevo se coloca **sólo** por proyección: en el corpus de campo, cero confirmaciones con
  `CarProjection` sin conducción medida.
- Un viaje en el coche de otro con Android Auto cuesta **una pregunta**, nunca un pin fantasma.

## iOS

**No hay hueco que tapar**: el equivalente como app (CarPlay) necesita entitlement de Apple, y como
SEÑAL la pertenencia ya la prueba la ruta de audio
([IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001]) — que además responde la pregunta que Android Auto no
responde, *de quién es el coche*. Este ticket es `androidMain` por construcción; el evaluador y el
`ArmEvidence` viven en `commonMain` y son testeables en las dos plataformas.
