# DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001 · El carril BT llama «viaje» a 90 s de radio: nadie mide que el coche se moviera

**Estado:** ✅ Done — en master. Queda **pendiente de campo**, no de código: la verificación exige
reponer antes el setup BT (ver *Criterio de éxito*), y la decisión A sigue elegida por doctrina
hasta que haya población que medir.

## Problema

El carril Bluetooth es el único de la app que **confirma un aparcamiento sin haber medido ni un
metro de movimiento del coche**. Todo lo que separa «acabo de llegar de un viaje» de «me he sentado
en el coche parado» es una DURACIÓN: `EvaluateBtParkUseCase.evaluateEngagement`
(`EvaluateBtParkUseCase.kt:117-129`) devuelve `BtEngagement.Ride` en cuanto
`disconnect - connect ≥ btMinRideDurationMs` (90 s), y a partir de ahí el detector tiene permiso
para colocar un pin.

Dos formas de entrar sin viaje, con el código de hoy:

1. **Sesión fantasma a 0,95.** Te sientas en el coche parado ≥90 s (radio, manos libres, cargar el
   móvil, esperar a alguien), desconectas y entras en casa andando. Engagement = `Ride` por
   duración → el candidato es pin-grade y ESTACIONARIO (claro: el coche lleva ahí toda la tarde,
   `EvaluateBtParkUseCase.kt:132-139`) → caminas ≥30 m a ritmo peatonal → `WalkAwayConfirmed` →
   `confirmParking(reliabilityBluetooth = 0.95)` (`BluetoothParkingDetector.kt:202-213`).
2. **Peor, y sin ninguna evidencia de nada.** Lo mismo pero sin llegar a los 30 m (te quedas cerca
   del coche, o no hay GPS útil). A los 15 minutos entra la rama `bt_timeout_save`
   (`BluetoothParkingDetector.kt:162-195`) y **guarda la sesión igualmente**, a fiabilidad reducida.
   En ese camino no hay ni conducción medida ni caminata medida: sólo un MAC que se cayó y un
   teléfono quieto.

El pin no está en el sitio equivocado — el coche está donde el pin dice — pero la SESIÓN es falsa, y
una sesión falsa no es inocua:

- reinicia sesión, historial y estadísticas del vehículo como si acabaras de llegar;
- arma una geocerca nueva cuyo EXIT arma después al Coordinator;
- y cuando salgas de verdad, esa sesión **libera una plaza a la comunidad** (doctrina: tu
  APARCAMIENTO libera una PLAZA al irte). Es un falso positivo con coste comunitario diferido, que
  es exactamente el lado del error que la doctrina asimétrica prohíbe.

Origen: duda del user (08-09-2026), verificada leyendo el carril entero. No hay traza de campo que
lo demuestre todavía — el corpus de `parkdiag` de septiembre está sin cosechar y el verdict
`bt_timeout_save` es la firma que habría que buscar en él (ver *Criterio de éxito*).

## Doctrina violada

**«El evento NOMINA, sólo el movimiento MEDIDO confirma.»** El Coordinator no confirma nada sin
conducción medida en el stream (pasos+egress, egress cinemático, vehicle-exit+ventana). El carril BT
exige un `ACL_DISCONNECTED` de una MAC emparejada y 90 s de reloj. La justificación histórica del
tier — «determinista, ligado a la MAC, la identidad no es ambigua» — es cierta sobre QUÉ COCHE es, y
no dice nada sobre SI HUBO VIAJE. `DET-BT-DISCONNECT-WITHOUT-RIDE-001` ya vio media grieta (el coche
traído por un familiar, 11,5 s) y la tapó con el único dato que tenía en disco: la duración. Este
ticket cierra la otra mitad, que la duración no puede alcanzar.

Corolario que también se rompe hoy: **fallo asimétrico, mejor FN que FP**. `bt_timeout_save`
convierte «no pude medir nada» en «guardo la sesión», que es el lado inseguro.

## Señales / datos disponibles

- `BtConnectionStore` (`BtConnectionStore.kt:29-35`) ya persiste en SharedPrefs el instante del
  `ACL_CONNECTED` por vehículo, estampado por el receiver de manifest
  (`BluetoothConnectionReceiver.kt:150`) — sobrevive a los process kills de OEM. **Sólo guarda un
  timestamp.**
- El disconnect ya tiene un stream de GPS abierto durante la caza del candidato
  (`BluetoothParkingDetector.kt:113-121`): posición del final del supuesto viaje, con accuracy.
- `UserParking` activo / último pin del vehículo en Room: dónde creemos YA que está ese coche.
- ⛔ Lo que **no** hay, y no se va a inventar: velocidad durante el trayecto. El carril BT es barato
  precisamente porque no mantiene un FGS ni un stream mientras conduces. Cualquier diseño que exija
  medir velocidad durante la conducción convierte BT en el Coordinator y se descarta de entrada.

## Cosecha del corpus (08-09-2026) — vacío POR CONSTRUCCIÓN, y eso es el dato

Se buscó el caso en las dos fuentes. **Cero sesiones del carril BT en todo lo que queda vivo**, y la
razón no es que el fallo no ocurra: es que el carril lleva días sin poder ejecutarse.

**Firestore (`pap-26`).** Las únicas colecciones top-level son `spots` y `users`. **No existen**
`diagnostics`, `diagnostics_config` ni `diagnostics_reports`: el wipe del 03-09 se llevó agosto
entero y desde entonces el gate remoto sigue OFF, así que septiembre no ha escrito ni una sesión ni
un informe. Lo único superviviente son los pines (`parkingHistory`): **61 pines del 03-09 al 08-09
sobre 5 cuentas con actividad, y `detectionPath` = `bt` o `bt_timeout` en NINGUNO.** El reparto es
todo carril Coordinator (`steps+egress` mayoritario, `kinematic+egress`,
`unattended_timeout`, `unattended_zone_walk_entered_anchor`, `closed_approximate_*`) más
`manual` / `user` / `nudge`.

**Dispositivos.** Sólo el Oppo (el móvil BT del setup) estaba conectado. Lleva el build de Play
(`versionCode=11`, `1.1.0`, **sin flag DEBUGGABLE**) → `run-as: package not debuggable`: el
`parkdiag.log` local es inalcanzable. Y aunque lo fuera, `firstInstallTime == lastUpdateTime ==
2026-09-08 04:16` — instalación limpia de hoy, así que los datos de agosto se fueron con la
desinstalación previa. El paquete `.mock` sí es accesible pero su `files/` sólo tiene
`profileInstalled`.

**Por qué el cero, en el móvil de desarrollo.** Dos causas independientes, cada una suficiente por
sí sola:

1. El único vehículo de la cuenta desde el wipe tiene **`bluetoothDeviceId = null`** →
   `resolveStrategy` devuelve Coordinator siempre, el carril BT nunca se elige.
2. En el Oppo, **`BLUETOOTH_CONNECT` está DENEGADO** → `BluetoothConnectionReceiver` no puede leer
   `device.address` (`BluetoothConnectionReceiver.kt:80-82`) y sale antes de nada.

**Y una cuenta que SÍ tiene BT emparejado — y tampoco produce pines BT.** Una de las cuentas del
corpus lleva emparejada la MAC de su coche desde el 06-09 y ha generado 12 sesiones en dos días:
3 `steps+egress`, 1 `unattended_timeout`, 1 `closed_approximate_pin`, 1 `manual` y **6 `nudge`**.
Pines del carril BT: **cero**. Con lo que sobrevive no se puede decidir cuál de las dos
explicaciones es: que el carril nunca llegue a ejecutarse (permiso, o el edge de CONNECT perdido y
por tanto `markConnected` vacío), o que se ejecute y acabe siempre preguntando por el gate de
duración — que son 6 nudges y llama la atención.

**Y no se puede decidir por una razón que este ticket toca de cerca: el nudge PIERDE quién
preguntó.** `showMarkParkingNudge(source = …)` persiste el origen en `PendingParkNudge.source`, pero
cuando el usuario responde, el pin se guarda por `SaveManualParkingUseCase` con `detectionPath =
"nudge"` y `armEvidence = null`: un nudge del carril BT y uno del coordinator son indistinguibles en
lo único que sobrevive a un wipe. Fuera de alcance aquí y abierto aparte —
`docs/backlog/det-a-nudge-must-say-who-asked-001.md`.

**Consecuencia para este ticket:** la decisión A (qué hace `Unknown`) **no se puede calibrar con
datos hoy** — no hay población que medir, y no la habrá hasta que se reponga el setup BT (emparejar
la MAC en el vehículo + conceder `BLUETOOTH_CONNECT` + `prodDebug` en el Oppo para que `parkdiag`
vuelva a ser legible + gate de diagnósticos ON). Se elige por doctrina, no por medición, y el
diseño se instrumenta para que la medición llegue después (ver *Diseño* §6).

## Diseño

En BT no se puede medir velocidad; **sí se puede medir DESPLAZAMIENTO**, y un viaje es, antes que
nada, un cambio de sitio.

1. **Sellar posición en el CONNECT.** Extender `BtConnectionStore.recordConnected` para que además
   del timestamp guarde el último fix conocido (lat/lon/accuracy) **y su edad**. Es una lectura de
   `lastLocation` en un receiver que ya se ejecuta: ni stream, ni FGS, ni batería adicional.
2. **`evaluateEngagement` pasa de predicado de duración a veredicto de viaje.** `Ride` exige
   duración ≥ `btMinRideDurationMs` **Y** desplazamiento connect→candidato por encima de un umbral
   nuevo (`btMinRideDisplacementMeters`, orden de 150–200 m + accuracies sumadas: holgadamente por
   encima del ruido de GPS y de recolocar el coche en el mismo bloque, holgadamente por debajo del
   viaje más corto que merezca una sesión). Un viaje real de menos de ese desplazamiento existe
   (mover el coche de acera) y cae al lado seguro: se pregunta.
3. **El fix del connect que no se puede fechar no es permiso, es duda.** Un `lastLocation` viejo
   miente sobre dónde estabas (misma familia que «el `speed` declarado no prueba reposo»): con edad
   por encima de un techo, o sin fix, el desplazamiento es inmedible → `BtEngagement.Unknown`, que
   hoy ya cae al nudge «marcar aparcamiento» (`BluetoothParkingDetector.kt:84-97`). Decisión abierta
   abajo: cuánto se pregunta con esa regla.
4. **Si el coche no se ha movido, no es una sesión nueva: es la misma.** Cuando el candidato cae a
   pocos metros de donde ya creemos que está ese vehículo (sesión activa o último pin), refrescar en
   lugar de re-aparcar. Mata el duplicado de historial/estadísticas sin preguntarle nada al usuario,
   y es el caso 1 en su forma más común (el coche que nunca salió de la puerta de casa).
5. **La decisión vive en `EvaluateBtParkUseCase`**, que ya es el veredicto de este carril
   [DET-VERDICT-NOT-PREDICATE-001]: no se crea un caso de uso nuevo, se le añade la señal que le
   faltaba. El detector Android sigue siendo fontanería.
6. **El gate llega instrumentado, porque hoy no hay corpus que lo calibre.** El desplazamiento
   medido y la edad del fix del CONNECT se estampan en el veredicto remoto **siempre**, también
   cuando el resultado es `Ride` — igual que `pinLagMs` viaja hoy en `enterAgeMs`. Sin eso, la
   primera pregunta que se hará dentro de un mes («¿cuánto estamos preguntando de más?») vuelve a
   no tener datos. Un umbral que nadie puede medir después no es un umbral, es una apuesta.

**Decisiones abiertas** (no resolubles con datos hoy — ver *Cosecha del corpus*):

- **A. Qué hace `Unknown`.** Hoy pregunta (nudge). Si el `lastLocation` del CONNECT resulta ausente
  o viejo a menudo, la regla nueva convierte parques buenos en nudges. La alternativa es degradarlo
  a la fiabilidad de `bt_timeout_save` en vez de preguntar. **Se pretendía medir en el corpus y el
  corpus está vacío por construcción**, así que se elige por doctrina — asimétrico: preguntar — y se
  revisa cuando el §6 haya recogido población real.
- **B. Qué hace `bt_timeout_save` con esta prueba.** Con desplazamiento probado, el timeout-save
  sigue siendo legítimo (park en casa, no hay 30 m de caminata). Sin él, hoy guarda igual. La
  pregunta es si el guardado sin caminata queda condicionado a la prueba de desplazamiento — mi
  lectura es que sí, y que es el punto donde este ticket más aprieta.

## Criterio de éxito

- Tests en `EvaluateBtParkUseCaseTest` (commonTest, fakes): un engagement largo SIN desplazamiento
  no es `Ride`; uno largo CON desplazamiento sí; fix del connect ausente/viejo → `Unknown`; el
  umbral se cruza contando accuracies, no en crudo.
- El caso 2 (sentarse en el coche + no caminar) deja de producir sesión guardada — verificado por
  test, no por campo.
- Campo: **precondición, reponer el setup BT** (emparejar la MAC en el vehículo, conceder
  `BLUETOOTH_CONNECT`, `prodDebug` en el Oppo para que `parkdiag` sea legible, gate de diagnósticos
  ON). Sin eso no hay verificación de campo posible: hoy el carril no se ejecuta (ver *Cosecha*).
  Con el setup repuesto, buscar `bt_park_confirmed` / `bt_timeout_save` cuyo desplazamiento estampado
  (§6) delate un coche que no se movió, y contar cuántos parques buenos acaban en nudge por
  `Unknown` — que es lo que decide si la decisión A se mantiene.

## Consumidores auditados

`grep -rn "recordConnected\|lastConnectedAt\|lastConnectFix\|detectParking\|DepartureVerdict"
shared/src app/src --include=*.kt` — cada hit clasificado:

- `BluetoothConnectionReceiver.kt` — único escritor del sello. **Cerrado**: escribe también el
  origen, en el MISMO `edit` que el timestamp, y un connect sin origen BORRA el anterior (si no, el
  siguiente disconnect mediría el viaje desde donde se conectó hace dos viajes).
- `BluetoothDetectionService.kt` — lee el par y se lo pasa al detector. **Cerrado**.
- `BluetoothParkingDetector.kt` — consumidor del veredicto. **Cerrado**: el gate va DESPUÉS del
  candidato, así que las dos ramas que guardan (walk-away y timeout-save) lo heredan.
- `ParkingSafetyNetWorker.kt:297` — lee `lastConnectedAt` como gate de identidad
  [DET-BT-IDENTITY-GATE-001]. **Exento**: la firma y el significado de esa clave no cambian; el
  origen es una clave nueva, ortogonal.
- `ObserveDetectionReadinessUseCase.kt:205` — sólo menciona el par en un comentario. **Exento**.
- `DetectionEvent.DepartureVerdict` — campo `distanceMeters` nuevo con default, y `RunDepartureCheckUseCase`
  (el otro emisor) construye con argumentos nombrados. **Cubierto por convergencia**: cero call
  sites rotos, y la columna ya existía en el DTO (la usan `Decision` y `SessionSuperseded`), así que
  no hay cambio de superficie de serialización.
- `EvaluateBtParkUseCaseTest.kt` — `evaluateEngagement` conserva su firma a propósito (el gate de
  duración sigue siendo el primero y sigue siendo barato), así que **ninguna aserción previa se
  tocó**: 24 tests intactos + 7 nuevos.
- `ParkingDetectionConfig` — dos constantes nuevas con su `require`. **Cerrado**.
- Dev Catalog / `MockScenario` / strings — **no aplica**: ni pantalla, ni estado MVI, ni copy nuevo.
  El nudge reutiliza el existente; lo único nuevo es su `source`, que es diagnóstico, no texto.
- `docs/detection/PARKING-DETECTION.md` — **cerrado**, entrada añadida en la misma tarea.

## Estado de ejecución (08-09-2026)

- Núcleo puro: `BtRideProof` + `BtRideOriginGap` + `evaluateRideDisplacement` en
  `EvaluateBtParkUseCase` (commonMain, testeable sin Android). El detector sigue siendo fontanería.
- Sello del origen: `BtConnectionStore.recordConnected(..., fix)` / `lastConnectFix(...)`, JSON del
  `GpsPoint` junto a su timestamp; lectura pasiva `getLastKnownLocation()` acotada a 2 s dentro del
  `goAsync` del receiver.
- Instrumentación §6: desplazamiento en `distanceMeters` y edad del origen en `enterAgeMs`, en TODOS
  los veredictos —incluidos los que pasan—; cada `Unmeasurable` nombra su hueco en el string del
  veredicto (`bt_unmeasurable_ask_no_origin_fix` · `…_origin_fix_stale` · `…_origin_clock_incoherent`).
- **2 409 tests verdes** (2 402 + 7), `:app:compileProdDebugKotlin` y `:app:compileMockDebugKotlin` OK.
