# DET-FENCE-REREGISTER-BY-CAUSE-001 · Re-registrar una geocerca por CAUSA, no por reloj

**Estado:** 🟢 ✅ DONE (§D+§A+§B) — master `ef03cea6` (squash 21-08, sin pushear). ⏳ Pendiente SÓLO los dos números (suelo de la cura 6 h, periódico del janitor 12 h), que esperan la telemetría `GEOFENCE_REGISTRATION`.


> ⚠️ La rama se apiló (no sale ya de master) porque su base traía Room **v18** y los móviles corren
> **v20** por §B de DET-HANDOFF-NOT-MANUAL-001: instalarla sola habría sido un downgrade de esquema
> → Room revienta al abrir o tira las tablas, y adiós a los dos aparcamientos restaurados a mano y a
> las 251 filas de historial del Oppo.

### ✅ Verificado en campo (2026-08-20 18:14, APK `f5f2c8af…`, los dos móviles)

El dedup hace exactamente lo que dice, medido en ambos:

| | antes | ahora |
|---|---|---|
| arranques del janitor por apertura de app | 2 | 2 |
| **registros efectivos** | **2** | **1** |
| descartes por el libro | 0 | 1 |

```
18:14:48.113  ✓ re-registered geofence=a1d0ee92…
18:14:59.128  ⊘ skip re-register geof=a1d0ee92… — this process registered it moments ago
```

Ventanas ciegas por apertura de app: **2 → 1**. Sin FATAL, sesiones activas intactas, Room v20 en
los dos.
**Sustituye a** `DET-CURE-FAILURE-SILENCES-CURE-001` (borrado): aquel arreglaba el acelerador; éste
elimina la razón por la que el acelerador existe. El único resto de aquel ticket que sobrevive —
loguear el motivo del fallo — está aquí abajo como §D.

## Problema

Hoy **dos workers distintos re-registran las mismas geocercas**, por motivos distintos, con
salvaguardas distintas, y ninguno de los dos sabe si hacía falta.

| | `ParkingSafetyNetWorker` (la "cura") | `GeofenceJanitorWorker` |
|---|---|---|
| Cuándo corre | cada 15 min · app-start · geofence-enter · fin de detección | cada 12 h · **cada arranque de app** · boot · post-sync |
| ¿Exige estar junto al coche? | **sí** | no |
| ¿Exige un fix fresco? | **sí** | no |
| ¿Salta sesiones recién creadas? | **sí** (`cureSkipFreshSessionMs`) | no |
| Acelerador | 6 h (`cureReregisterMinIntervalMs`) | **ninguno** |
| Si falla | descarta el motivo y **se gasta el turno de 6 h** | loguea la excepción y reintenta (3) |

### Por qué re-registrar no es gratis

Evidencia de campo propia (2026-07-11, recogida en `ParkingDetectionConfig:621-628` y
`EvaluateSafetyNetCheckUseCase:411-422`):

> **Cada re-registro CON ÉXITO resetea el estado interno INSIDE/OUTSIDE de Play Services a
> "desconocido"** hasta su siguiente evaluación — una ventana ciega en la que una salida **no
> produce EXIT**. Una cura aterrizó ~40 s antes de arrancar y la salida fue muda.

La cura está blindada contra ese peligro con cuatro puertas. **El janitor lo dispara sin ninguna**,
en cada arranque de app. Si el usuario abre la app junto a su coche y arranca al minuto, acabamos de
abrir la ventana ciega justo antes de su salida — el mismo modo de fallo del 11-07.

### Por qué el periódico de 12 h ya no tiene justificación

`PaparcarApp:81` dice: *"Geofences have a **24h TTL**…; the janitor renews them in time."*
`GeofenceManagerImpl` registra con `Geofence.NEVER_EXPIRE` desde [GEOF-001], y su propio comentario
explica que la prevención de huérfanas ya no depende de la caducidad. **El periódico renueva una
caducidad que no existe.** El comentario nunca se actualizó al cambiar el diseño.

### Por qué no basta con "preguntar si siguen registradas"

Sería lo correcto y **la API de Android no lo permite**: `GeofencingClient` sólo expone
`addGeofences` / `removeGeofences`. No hay listado ni consulta (ya anotado en
`GeofenceManagerImpl.removeAllGeofences`: *"GMS exposes no list API"*). El sistema es ciego por
construcción, así que la única alternativa honesta a un reloj es **registrar cuando ha ocurrido algo
que sabemos que rompe la valla**.

## Doctrina violada

1. **Sistemas, no parches**: dos dueños para un mismo invariante ("la valla del coche aparcado está
   viva y sana") y ninguna regla que diga cuál manda.
2. **El evento NOMINA, sólo el movimiento MEDIDO confirma** — aplicado aquí: un reloj no es
   evidencia de que la valla se haya roto. Registrar "por si acaso" cada 12 h es actuar sin causa.
3. **Fallo asimétrico**: un re-registro innecesario NO es neutro (abre la ventana ciega), así que la
   duda debe resolverse hacia no registrar, no hacia registrar.
4. **Provenance obligatoria**: hoy un fallo de registro es un booleano sin motivo, en local y en
   remoto (§D).

## Diseño

### La regla rectora

> **Una geocerca se re-registra por una CAUSA conocida, nunca por un reloj.** Y hay exactamente dos
> familias de causa, que son averías distintas y no se sustituyen entre sí.

| Avería | Síntoma | Quién la cura | Requiere |
|---|---|---|---|
| **A · la valla ya no existe** | GMS no tiene nada registrado | el janitor | nada — vale desde cualquier sitio |
| **B · la valla existe con el estado envenenado** (quedó OUTSIDE tras un EXIT andando y no se vio la re-entrada) | GMS la tiene pero **no emitirá EXIT: ya te cree fuera** | la cura | estar DENTRO + fix fresco |

El janitor no puede con B (es ciego a la posición: re-registrar estando lejos deja el estado en
"fuera", que no arregla nada). La cura no debería ocuparse de A (exige cercanía que A no necesita).

### A · Existencia — dueño único: el janitor, y sólo por causa

Las causas que destruyen vallas **ya están enganchadas** en `BootCompletedReceiver`:
- `BOOT_COMPLETED` → el reboot borra todas las vallas.
- `MY_PACKAGE_REPLACED` → una actualización de app también.
- ~~En Android 15+, `BOOT_COMPLETED` se entrega además sintéticamente al salir del estado
  *stopped*~~ → **VERIFICADO 2026-08-20, y NO nos sirve.** Ver abajo.

#### ⛔ Verificación en device (2026-08-20) — el force-stop NO está cubierto por el boot sintético

**Los dos móviles de field-test son Android 13 / API 33** (`CPH2371` y `2201117TY`). El boot
sintético al salir del estado *stopped* es de **Android 15 / API 35**, así que en este hardware no
existe. Y con `minSdk 26`, tampoco existe para la mayoría del parque real.

Experimento en el Oppo, con el Focus aparcado y su valla viva:

```
adb shell am force-stop io.apptolast.paparcar
dumpsys package …                    → stopped=true          (entró de verdad en stopped)
adb shell am start -n …/.MainActivity                        (lo saca de stopped)
logcat -b all | grep BOOT_COMPLETED  → (vacío)                NO se entregó nada
```

**Consecuencia directa para el diseño:** en API < 35 —o sea, en nuestros dos móviles— lo ÚNICO que
restaura la valla tras un force-stop / OEM deep-kill es **el pase de arranque de app**, justo el que
§A proponía quitar. **No se puede quitar**, y tampoco condicionar a la distancia (ver la corrección
del punto 1 de "Cambios"): se queda como la vía de recuperación de force-stop, y lo único que se le
recorta es la repetición demostrablemente redundante.

#### 📏 Medido, no supuesto: el arranque re-registra DOS veces

En ese mismo arranque, la misma valla se re-registró dos veces con **4,3 s** de diferencia:

```
17:00:29.149  GeofenceJanitorWorker: ✓ re-registered geofence=2a1b068c…
17:00:33.471  GeofenceJanitorWorker: ✓ re-registered geofence=2a1b068c…
```

Son los dos llamadores de `enqueueOnce`: `PaparcarApp:90` y `WorkManagerParkingSyncScheduler:63`
(post-sync). `ExistingWorkPolicy.REPLACE` no los deduplica porque el primero **ya había terminado**
cuando llega el segundo. Según la evidencia de campo propia, eso son **dos ventanas ciegas en 4
segundos** en cada apertura de la app. La deduplicación necesita una guarda temporal, no `REPLACE`.

#### Nota: la ceguera es total, no sólo de la app

`adb shell dumpsys location` sólo expone el *Geofence Manager* de la plataforma
(`service: unregistered`), no las vallas de Play Services. Ni siquiera desde adb se pueden listar:
no hay forma, dentro ni fuera del proceso, de comprobar si una valla sigue registrada.

Cambios:

1. ⛔ **CORRECCIÓN de diseño (al implementarlo): el gate por DISTANCIA que yo mismo propuse arriba
   es peligroso y queda descartado.** El razonamiento era "no re-registres estando junto al coche,
   que es cuando la ventana ciega duele". El fallo: **tras un force-stop las vallas están BORRADAS**,
   y no hay forma —ni dentro ni fuera del proceso— de saberlo. Saltarse el registro por estar cerca
   del coche dejaría a ese coche **sin valla ninguna, de forma permanente**, en vez de con una
   ventana ciega que se cierra sola en el siguiente fix. *Un agujero temporal es mejor que uno
   permanente.* La regla correcta es la de abajo: saltarse sólo lo que se puede **demostrar**
   redundante.

2. **El pase de arranque de app SE QUEDA** (verificación de arriba: en API < 35 es la única
   recuperación tras un force-stop, y nuestros dos móviles son API 33) y **se deduplica por
   evidencia propia**: `FenceRegistrationLedger` (androidMain, en memoria) anota qué vallas ha
   registrado ESTE proceso y cuándo; `FenceRegistrationPolicy.shouldRegister(...)` (commonMain,
   puro, testeado) decide. Un proceso nuevo no tiene registro y por tanto **siempre registra** —que
   es justo el caso force-stop—, mientras que un segundo pase 4,3 s después se descarta.
   La anotación vive dentro de `GeofenceManagerImpl.createGeofence`, por donde pasan **todas** las
   vías (alta al aparcar, edición de ubicación, swap de vehículo, y los dos carriles de
   restauración), así que no hay cuatro sitios que olvidar. **Sólo se anota el ÉXITO**: un intento
   fallido no dejó valla ni abrió ventana, luego no debe bloquear al siguiente.
   Efecto colateral bueno: el janitor ya no re-registra una valla creada hace segundos (el modo de
   fallo de la Glorieta, 2026-07-12, que hasta ahora sólo cubría la cura vía `DET-CURE-FRESH-001`).
2. **El periódico de 12 h deja de ser el mecanismo y pasa a ser el SUELO**, con su razón escrita y
   honesta: cubre lo que no sabemos detectar (Play Services soltando vallas por su cuenta al
   actualizarse, localización off→on). Intervalo a decidir con dato de campo; 24 h como punto de
   partida, no 12.
3. Corregir el comentario muerto del TTL de 24 h en `PaparcarApp`.

### B · Estado envenenado — dueño único: la cura, y sólo por causa

Las causas **también existen ya**, y son dos:
1. **EXIT entregado y descartado como falso** → `DepartureDetectionWorker:50` llama a
   `ParkingSafetyNetWorker.clearCureThrottle(...)`. Ese es *el* evento de envenenamiento: la entrega
   dejó el estado en OUTSIDE.
2. **La valla gemela ENTER dispara** (el usuario vuelve a entrar andando) → `GeofenceEnterReceiver`
   → chequeo de la red de seguridad con `source=geofence-enter`. [DET-RETURN-ANCHOR-001]

Cambio: **quitar el acelerador de 6 h y curar sólo ante una de esas dos señales.**

⚠️ Residual honesto: queda un caso sin señal — GMS consumió el EXIT al salir andando **y** se perdió
la re-entrada por Doze, así que ni hay EXIT descartado ni dispara la gemela. Para eso, y sólo para
eso, conservar un **suelo ciego largo** (el actual 6 h es un candidato razonable, pero ahora con su
razón real escrita, que no es "no curar demasiado" sino "no nos hemos enterado de nada").

### C · Consecuencia: el bug del acelerador se evapora

Con B por causa, el defecto que originó todo esto **deja de existir en lugar de arreglarse**:
`ParkingSafetyNetWorker:296` sella `CURE_KEY_PREFIX` *antes* de intentar el registro y no lo revierte
si falla, así que un intento fallido se gastaba el turno de 6 h — la cura se callaba justo cuando
había fracasado restaurando la valla. Sin acelerador no hay turno que gastar.

Si por lo que sea se conserva el suelo ciego, entonces sí hay que aplicar la regla derivada:
**el suelo cuenta ÉXITOS, no intentos** — un registro que falla no abre ninguna ventana ciega porque
no cambia nada, luego no debe consumir nada. (Sellar sólo en éxito: sello provisional antes del
intento, revertido al valor anterior si falla, para no perder la protección anti-bucle de un proceso
matado a mitad del `await()`.)

### D · El motivo del fallo deja de perderse

`GeofenceManagerImpl.createGeofence` envuelve todo en `runCatching`; `ParkingSafetyNetWorker:315` lee
**sólo** `result.isFailure` y el `Throwable` —con el `ApiException` de GMS y su código— se descarta.
`DetectionEvent.GeofenceRegistration` lleva `success: Boolean` y nada más, así que el diagnóstico
remoto es igual de ciego. Es incoherente dentro del propio código: las vallas auxiliares loguean
`it.message` y `GeofenceJanitorWorker:101` loguea el throwable — la única cuyo fallo no se explica es
**la principal, la del EXIT**.

Arreglo: loguear la excepción como ya hace el janitor y llevar el motivo al evento remoto. El
`DetectionEventDto` **ya tiene columna `reason`** (la usan `HonestClose` y `Released`), así que cabe
sin tocar la superficie del serializador. Mapear el `ApiException` a su código legible
(`GEOFENCE_NOT_AVAILABLE`, `TOO_MANY_GEOFENCES`, permiso) para poder agrupar en telemetría.

Origen: 2026-08-20, `✗FALLÓ el re-registro` en rojo en el Redmi con la valla viva (su
`geofence-enter` llegó 15 s después) y **sin forma de saber por qué**.

## Lo implementado (sin commitear)

| Pieza | Qué |
|---|---|
| `GeofenceRegistrationFailure` (commonMain) | vocabulario del fallo — `NOT_AVAILABLE`, `TOO_MANY_GEOFENCES`, `TOO_MANY_PENDING_INTENTS`, `PERMISSION_DENIED`, `UNKNOWN` — con el mapeo desde el código de estado de GMS puro y testeado. El permiso gana sobre cualquier código: son arreglos opuestos |
| `Throwable.toGeofenceRegistrationFailure()` / `.geofenceFailureDetail()` (androidMain) | la única parte específica de plataforma: sacar el entero del `ApiException`. Lo usan los dos carriles, así que no pueden divergir describiendo el mismo fallo |
| `DetectionEvent.GeofenceRegistration(source, failure)` | el evento dice ahora **quién** pidió el registro (`cure` / `janitor`) y **por qué** falló. Ambos viajan en columnas que el DTO ya tenía (`source`, `reason`) → sin cambio de superficie del serializador |
| `ParkingSafetyNetWorker` | loguea la excepción con su motivo y lo manda al evento; el `✗FALLÓ` del debug ya dice la causa en vez de ser un aspa pelada |
| `GeofenceJanitorWorker` | **emite evento de registro por primera vez** — este carril era invisible en remoto, y es el que dispara sin puertas: es el sospechoso principal de abrir la ventana ciega |
| `FenceRegistrationPolicy` (commonMain, puro) + `FenceRegistrationLedger` (androidMain, en memoria) | la regla y su libro: saltarse sólo lo demostrablemente redundante; una causa conocida siempre gana; un sello del futuro (reloj hacia atrás) nunca silencia el registro |
| `ParkingDetectionConfig.fenceRegisterDedupWindowMs` = 5 min | muy por encima de la ráfaga medida (4,3 s) y muy por debajo de cualquier causa legítima |
| `GeofenceManagerImpl` | anota el éxito en el libro y lo olvida al `removeGeofence` (olvido **antes** del borrado: si el borrado falla, preferimos re-registrar de más que saltarnos uno por una entrada rancia) |
| `PaparcarApp` | corregido el comentario muerto del "TTL de 24 h"; el periódico queda documentado como **suelo**, no como mecanismo |

Tests nuevos (9): `FenceRegistrationPolicyTest` (5 — sin registro previo registra, ráfaga de 4,3 s se
descarta, ventana cumplida vuelve a registrar, causa conocida manda, sello del futuro no silencia) y
`GeofenceRegistrationFailureTest` (4 — los tres códigos de GMS, permiso separado de desconocido,
permiso ganando sobre el código, y las etiquetas de cable estables).

## §B · lo implementado

| Pieza | Qué |
|---|---|
| `markFenceStatePoisoned` (sustituye a `clearCureThrottle`) | la causa se **declara**, no se deduce de un reloj borrado. El anterior decía "estado envenenado" BORRANDO la clave del acelerador, y una clave ausente significaba a la vez *envenenado*, *nunca curado* y *recién instalado* — tres situaciones con respuestas distintas. Ahora hay un sello propio (`cure_poisoned_`), sobrevive a la muerte del proceso y **lo consume la cura que lo repara**: un envenenamiento compra una reparación |
| `shouldReregisterCure(statePoisoned)` | la causa manda sobre todo lo demás, **incluida la guarda de frescura**: "una valla creada hace minutos está sana" es una suposición, y el EXIT descartado es la prueba de que es falsa para ESA valla. Un EXIT falso puede caer dentro de los 10 min de la guarda (aparcas, te alejas, la deriva dispara, lo rechazamos) |
| el sello del suelo se pone **sólo si el registro tuvo ÉXITO** | era el bug original: se sellaba ANTES de intentar y no se revertía, así que una cura fallida se compraba 6 h de silencio — el carril cuyo trabajo es restaurar vallas se callaba justo por haber fracasado restaurando una. Un registro que falla no cambió nada en Play Services, no abrió ninguna ventana ciega, y por tanto no gana ningún turno. También se libera `curedFencesThisProcess` en el fallo, para no heredar un turno que nunca se tomó |
| `cureReregisterMinIntervalMs` redocumentado | su razón real no es "no curar demasiado" sino **"no nos hemos enterado de nada"**: cubre el único envenenamiento sin señal (GMS se come el EXIT andando y luego pierde el ENTER de vuelta por Doze). **El valor no se toca** hasta tener dato |
| el ENTER de la valla gemela, explícitamente NO es causa | si la gemela dispara, GMS **vio** la vuelta y su estado es INSIDE: eso es evidencia de que la valla está SANA. Re-registrar ahí abriría la ventana ciega para nada, y encima con el usuario junto al coche y plausiblemente a punto de arrancar. Ese carril resella el ancla y nada más |

Tests nuevos (2): la causa conocida vence al suelo, y la causa conocida vence a la guarda de frescura.

## Deliberadamente NO hecho

- **El número del suelo** (`cureReregisterMinIntervalMs`, hoy 6 h) y el del periódico del janitor
  (12 h). Son los dos únicos parámetros que dependen del dato, y la telemetría de §D lleva minutos
  corriendo. Moverlos por intuición es exactamente lo que este ticket existe para dejar de hacer.
- Con §D unos días en campo, el evento `GEOFENCE_REGISTRATION` responde solo: cuántos registros por
  arranque, cuántos del janitor frente a la cura, con qué motivo fallan, y —lo que decide el
  número— **cuántas veces el suelo ciego encuentra algo que reparar de verdad**.

## Criterio de éxito

- Un arranque de app junto al coche **no** re-registra su valla.
- Un reboot / una actualización / un force-stop **sí** la re-registran, y se ve en el log con su causa.
- Un EXIT descartado como falso cura en el siguiente tick dentro, sin esperar reloj.
- Un fallo de registro deja el motivo en el log **y** en el evento remoto, y no consume ningún turno.
- Un solo sitio del código decide "hay que re-registrar", y lo hace por una causa nombrable.
- Tests: la política pura ("¿toca re-registrar?") con sus casos de causa, y el suelo contando éxitos
  y no intentos.
- Campo: una noche aparcado en casa sin un solo re-registro en el log, y la salida de la mañana con
  su EXIT.

## Riesgos de este cambio

Se está retirando red de seguridad, así que conviene decirlo claro: hoy re-registramos de más y eso
**tapa** fallos que no sabemos detectar. Al pasar a causa conocida, cualquier causa que se nos escape
se convierte en un coche sin trigger de salida. Mitigación: el suelo periódico se mantiene, §D nos da
por fin telemetría del fallo, y **el orden de trabajo es §D primero** — instrumentar antes de retirar,
para tener el dato con el que decidir el intervalo del suelo.

## Consumidores auditados

Barrido de `createGeofence(` y de quien encola el janitor:

| Sitio | Asumía | Clasificación |
|---|---|---|
| `GeofenceJanitorWorker:98` | re-registrar siempre que haya sesión activa | **cerrado** — consulta el libro; un proceso nuevo sigue registrando siempre |
| `ParkingSafetyNetWorker:300` (cura) | su acelerador de 6 h era el único juez | **cerrado** — el libro tiene la última palabra sobre redundancia, con `lastCureAt == 0L` como causa conocida que lo anula (nunca curada, o `clearCureThrottle` tras un EXIT falso) |
| `ConfirmParkingUseCase:310` (alta al aparcar) | alta deliberada | **exento y anotado** — nunca se salta (no consulta el libro), pero sí ANOTA, y eso es lo que evita que el janitor re-registre una valla creada hace segundos |
| `UpdateParkingLocationUseCase:74` (mover el pin) | idem | **exento y anotado** — misma razón |
| `SwapActiveVehicleFencesUseCase:56` | idem | **exento y anotado** — misma razón |
| `GeofenceManagerImpl.removeGeofence` | borra en GMS | **cerrado** — olvida la entrada ANTES de borrar: si el borrado falla preferimos re-registrar de más que saltarnos uno por una entrada rancia |
| `GeofenceManagerImpl.removeAllGeofences` (logout / borrado de cuenta) | tira todas las vallas | **cerrado** — vacía el libro entero |
| `registerAuxiliaryFences` (vallas ENTER y testigo) | best-effort, no deciden el `Result` | **exento** — no entran en el libro: el libro habla de la valla principal, que es la que decide si hay trigger de salida |
| `PaparcarApp:90` / `WorkManagerParkingSyncScheduler:63` / `BootCompletedReceiver:47` (los tres `enqueueOnce`) | cada uno pedía su pase | **cerrado por convergencia** — siguen encolando; el worker es quien decide, así que la regla vive en UN sitio y no en tres llamadores |
| `IosGeofenceManagerImpl` | — | **exento** — el libro es androidMain; iOS no tiene ninguno de los dos workers |

## Clases implicadas

| Clase | Papel |
|---|---|
| `androidMain/…/worker/GeofenceJanitorWorker.kt` | dueño propuesto de la EXISTENCIA; hoy el que re-registra sin ninguna puerta |
| `androidMain/…/PaparcarApp.kt` (:81-90) | el pase incondicional por arranque + el comentario muerto del TTL |
| `androidMain/…/receiver/BootCompletedReceiver.kt` | las causas reales ya enganchadas (boot / update / force-stop A15) |
| `androidMain/…/worker/ParkingSafetyNetWorker.kt` (:283-316, :797) | dueño propuesto del ESTADO; el sello previo al intento y `clearCureThrottle` |
| `commonMain/…/EvaluateSafetyNetCheckUseCase.shouldReregisterCure` | la política pura donde se expresa "por causa, no por reloj" |
| `androidMain/…/worker/DepartureDetectionWorker.kt` (:50) | la señal de envenenamiento conocida (EXIT descartado) |
| `androidMain/…/receiver/GeofenceEnterReceiver.kt` | la otra señal: el regreso a pie [DET-RETURN-ANCHOR-001] |
| `androidMain/…/GeofenceManagerImpl.kt` | el `runCatching` que pierde el motivo; y quien documenta que GMS no tiene API de listado |
| `commonMain/…/diagnostics/DetectionEvent.GeofenceRegistration` + `DetectionEventDto` | el evento sin motivo y la columna `reason` donde cabe |

## Origen

Detectado el 2026-08-20 al instalar el APK combinado de [DET-HANDOFF-NOT-MANUAL-001] +
[DET-HUMAN-POWERED-EARLY-CLOSE-001]. **No lo introduce ninguno de los dos.** El ticket nació como
"arreglar el acelerador de la cura" y creció al preguntarse el usuario lo correcto: *¿cuándo es
REALMENTE necesario volver a registrar una geocerca, y por qué hay dos workers haciéndolo?*
