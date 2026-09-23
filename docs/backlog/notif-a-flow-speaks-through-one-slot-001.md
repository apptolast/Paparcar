# NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001 · un aparcamiento habla por UNA ranura, y quien lo confirma apaga lo que lo contradice

**Estado:** ✅ Done · rama `bugfix/NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001-notif-hygiene` ·
worktree `../Paparcar-notif` · issue [#132](https://github.com/rndevelo/Paparcar/issues/132) ·
`testAll --rerun-tasks` verde · iOS y los dos flavors compilan · testigos falsificados

## Problema

La captura del issue (22-09, 22:37) tiene **tres tarjetas de Paparcar a la vez** y dos de ellas
cuentan lo mismo sobre el mismo coche:

| # | Tarjeta | Edad | Ranura | Quién la puso |
|---|---|---|---|---|
| 1 | «**Kamiq aparcado** · Toca para abrir el mapa, o cancela si no era tu vehículo» + `Sí, confirmar` / `No, cancelar` | 12 min | `PARKING_CONFIRMATION` (2002) | el pin `b631f1e2` de las 22:25 (`kinematic+egress`) |
| 2 | «**Aparcamiento guardado** · Tu coche ha sido aparcado. Toca para verlo en el mapa.» | 46 min | `UPLOAD` (**1002**) | el pin `587ce9e1` de las 21:50 (`nudge_bt_no_ride`) |
| 3 | «Aparcado · atento a tu salida» (silenciada) | — | FGS/sentry | correcta: es el servicio en curso |

La #2 **no habla del aparcamiento actual**: habla de uno que ya fue sustituido media hora antes. Y
sigue ahí porque **nadie puede apagarla**.

### Los tres defectos, medidos

**1 · Una ranura que nadie apaga.** `grep dismiss(` da 16 llamadas a `PARKING_CONFIRMATION`, 6 a
`STILL_PARKED`, 2 a `MARK_PARKING_NUDGE`, 1 a `DETECTION` y **cero a `UPLOAD_NOTIFICATION_ID`**. La
tarjeta legacy sólo desaparece si el usuario la toca: sobrevive a un revert, a un aparcamiento
nuevo, a una salida y a la sesión entera.

**2 · Una afirmación en un comentario no se ejecuta.** El KDoc de `RevertParkingUseCase` dice, en el
paso 3 de su propia receta:

> *«Dismiss de la notificación 2002 — la única notificación visible para este evento (**ya no se
> muestra la antigua "showParkingSaved" duplicada**)»*

Se muestra. `showParkingSaved` sigue viva y con **cuatro productores**:
`SaveManualParkingUseCase:93`, `BluetoothParkingDetector:351` y `:386` (ésta con el comentario
`// Legacy tap-to-open-map notification. [BT-NOTIF-LEGACY-CLEANUP]`), más el alias `@Deprecated`
`showParkingSpotSaved`. Mismo defecto de clase que [#121]: *lo escrito en un comentario no corre*.

**3 · El nudge sobrevive al aparcamiento que lo responde.** `ClearParkNudgeUseCase` declara en su
KDoc que resuelve la pregunta cuando *«el usuario marcó un aparcamiento, **una sesión del vehículo
nudgeado fue confirmada**, o el usuario descartó el banner»*. Sus **únicos dos call sites están en
`HomeViewModel`**: un confirm en background —el carril BT, el coordinator, el backfill— no lo apaga.
La pregunta «¿dónde dejaste el coche?» se queda en la bandeja al lado de la respuesta.

**4 · El interruptor de Ajustes miente.** «**Aparcamiento detectado** · Avisa cuando se guarda tu
aparcamiento» (`settings_notif_parking`) gobierna **sólo** la tarjeta legacy. La tarjeta que de
verdad sale —la unificada— no lo lee: apagar el aviso no apagaba nada de lo que el usuario ve.

## Doctrina violada

1. **«Sistemas, no parches.»** REFACTOR-300 unificó el par prompt→guardado en una sola ranura
   precisamente porque el usuario ya se quejó de la doble notificación. El arreglo se escribió en el
   KDoc y se dejó a medias: el carril del coordinator migró, el carril BT y el guardado manual no.
   Cerrar la vía donde mordió no basta.
2. **«No copy al usuario con mecánica interna»** no se rompe aquí, pero sí su hermana: **dos frases
   distintas para el mismo hecho** («Aparcamiento guardado» / «Kamiq aparcado») obligan al usuario a
   deducir que hablan de lo mismo.
3. **Un ajuste es una promesa.** Un interruptor que no gobierna lo que su texto dice es peor que no
   tenerlo: el usuario lo apaga, sigue recibiendo la tarjeta y concluye que la app le ignora.

## Señales / datos disponibles

- Las 17 ranuras (`AppNotificationManager.Companion`) ya están agrupadas por flujo en el código; lo
  que falta es que el flujo del aparcamiento tenga **una**, no cuatro.
- `ConfirmParkingUseCase` es, por diseño declarado en CLAUDE.md, **el punto donde converge todo
  aparcamiento de todos los carriles**. Es el único sitio desde el que se puede apagar el flujo una
  sola vez y que valga para todos.
- `launchBtHunt` **ya resuelve el nombre del vehículo** (lo pone en la notificación del FGS) tres
  líneas antes de llamar a `detectParking`: el carril BT puede decir «Kamiq aparcado» sin inyectar
  un repositorio nuevo.

## Diseño

**El invariante, en una frase:** *un flujo habla por UNA ranura, y quien confirma un aparcamiento
apaga lo que lo contradice.*

### A · La ranura legacy desaparece del mapa

`showParkingSaved`, su alias `@Deprecated showParkingSpotSaved` y la constante
`UPLOAD_NOTIFICATION_ID` **se borran del puerto**. No se "deja de llamar": se quita, para que el
compilador impida volver a poner una tarjeta de aparcamiento en una ranura que nadie apaga.
(`UPLOAD_CHANNEL_ID` se queda — es un canal, y lo usan avisos de otros flujos.)

### B · El carril BT gana el «No, cancelar» que nunca tuvo

Sus dos confirmaciones pasan a la tarjeta unificada. El comentario de DI que lo justificaba —
*«MAC-address binding makes BT detection reliable enough that the revert affordance was overkill»*—
**lo desmiente el campo de la issue #131**: ese usuario lleva un BT *aftermarket* comprado aparte,
atado a una MAC que no es la del coche, y ese carril produce pines a destiempo y `nudge_bt_no_ride`.
Es justo el carril que más necesita el deshacer.

El nombre del coche viaja como parámetro desde `launchBtHunt`, que ya lo tiene resuelto.

### C · El guardado a mano no se anuncia

`SaveManualParkingUseCase` deja de publicar tarjeta. El usuario **acaba de colocar el pin mirando el
mapa**: anunciárselo es contarle lo que está viendo, y ofrecerle «Sí, confirmar» es pedirle que
confirme lo que acaba de afirmar — exactamente lo que `AssertedPinAuthority` ya tiene escrito:
*«Asking is not free… A second prompt cannot resolve uncertainty, it manufactures it.»*

### D · Quien confirma apaga el flujo

`ConfirmParkingUseCase` —la puerta común— apaga al guardar las dos tarjetas que su propio éxito
deja obsoletas: el nudge «¿dónde dejaste el coche?» y el «¿sigues aparcado aquí?». Hoy eso sólo
pasa si Home está abierto.

⚠️ La separación que el KDoc defiende («el caller elige qué MOSTRAR») se mantiene intacta: aquí no
se muestra nada, se **apaga** lo que ha dejado de ser verdad.

### E · El interruptor deja de mentir

`notifyParkingDetected` pasa a gobernar la tarjeta que de verdad sale, y gobierna **el aviso, no la
tarjeta**: apagado, la tarjeta se publica en silencio (sin sonido ni heads-up). Así el usuario que
pidió «no me avises» deja de ser interrumpido **sin perder el «No, cancelar»**, que es la red del
falso positivo y por doctrina no se gatea.

## Criterio de éxito

- [x] Un aparcamiento confirmado por CUALQUIER carril deja **una** tarjeta, y la siguiente
      confirmación la **sustituye** (misma ranura). El compilador lo sostiene: la otra ranura no
      existe.
- [x] Un aparcamiento confirmado en background apaga el nudge pendiente y el «¿sigues aparcado?».
- [x] Marcar a mano desde la app no publica ninguna tarjeta.
- [x] Un pin del carril BT se puede deshacer desde la bandeja, y la tarjeta nombra el coche.
- [x] Con «Aparcamiento detectado» apagado: la tarjeta aparece sin sonar (Android e iOS).
- [x] `grep showParkingSaved` / `grep UPLOAD_NOTIFICATION_ID` sólo devuelven **KDoc histórico**:
      ningún símbolo, ninguna llamada.
- [x] Suite `./gradlew testAll --rerun-tasks` verde, más `:app:compileMockDebugKotlin`,
      `:app:compileProdDebugKotlin` y `:shared:compileKotlinIosSimulatorArm64`.

### Testigos, falsificados

Quitando **una** de las dos líneas de dismiss caen **tres** tests —
`ConfirmParkingUseCaseTest > should retire the cards this park contradicts`,
`SaveManualParkingUseCaseTest > …placing_a_new_pin` y `…pin_answers_a_detection_nudge`— y vuelven
verdes al restaurarla.

⚠️ Y hacía falta comprobarlo: `buildUseCase` del test de confirm **recibía un
`FakeAppNotificationManager` y lo tiraba** —nunca llegaba al use case—, así que el test
«should not post any notification» no podía fallar. Cableado ahora; es lo que hace que el de arriba
signifique algo.

### Lo que NO tiene testigo automático

El silencio de la tarjeta con el interruptor apagado vive en las dos impls de plataforma
(`NotificationCompat.Builder.setSilent` / `UNNotificationSound`), donde este proyecto no tiene
banco de pruebas de notificaciones. Se verifica en device: Ajustes → «Aparcamiento detectado» off →
aparcar → la tarjeta sale sin sonido y con «No, cancelar».

## Consumidores auditados

| Señal / símbolo | Sitios | Clasificación |
|---|---|---|
| `showParkingSaved` | `SaveManualParkingUseCase:93`, `BluetoothParkingDetector:351,386`, impl Android, impl iOS, fake | **cerrado** — símbolo borrado; el compilador caza cualquier resto |
| `showParkingSpotSaved` (@Deprecated) | sin call sites | **cerrado** — borrado con su delegado |
| `UPLOAD_NOTIFICATION_ID` | impl Android, impl iOS, KDoc de `dismiss` | **cerrado** — borrado |
| `UPLOAD_CHANNEL_ID` | zona, combustible ×2, recap, primer-aparcamiento, fiabilidad | **exento** — es un canal, no una ranura; ningún aviso de esos pertenece al flujo del aparcamiento |
| `ClearParkNudgeUseCase` | `HomeViewModel` ×2 | **cubierto por convergencia** — sigue igual; ahora además lo llama la puerta común, así que da igual quién confirme |
| `MARK_PARKING_NUDGE` / `STILL_PARKED` | dismiss desde servicio, worker, MainActivity | **cerrado** — se les suma el confirm, que era el que faltaba |
| `notifyParkingDetected` | impl Android (legacy) | **cerrado** — se muda a la tarjeta unificada |
| `showZoneSpotAlert` (mismo patrón de gate) | `PushMessageHandler`, `AppViewModel` | **exento** — otro flujo, ranura propia, gate propio ya honesto |
| `showSharedCarParked` / `Left` | ranuras propias y **documentadas** como tales | **exento** — son dos coches distintos, no dos voces del mismo |

### Lado iOS

`IosAppNotificationManagerImpl.showParkingSaved` se borra igual (era el mismo duplicado, misma
ranura 1002). El resto del diseño vive en `commonMain` (`ConfirmParkingUseCase`,
`SaveManualParkingUseCase`), así que viaja entero. El carril BT no existe en iOS
[IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001], así que B no tiene lado iOS que escribir.

### No aplica en esta tarea

- **Strings**: cero keys nuevas. Se **retiran** de uso `notif_parking_saved_title` /
  `notif_parking_saved_text` (se dejan declaradas en los 9 locales; borrarlas es otra tarea, y
  quitarlas de `values` sin barrer los ocho es la vía al crash de [I18N-…-ONLY-001]).
- **Dev Catalog**: ni pantalla, ni estado MVI, ni condición de routing. Las notificaciones no tienen
  galería.
- **Web**: no toca límites free, ni feature que `paparcar.com` nombre, ni hechos del build.

## Fuera de alcance (ticket aparte)

- **El inventario completo de 17 ranuras** y si los flujos C (comunidad/Plus) y D (salud) merecen
  agrupación con `setGroup`. Aquí sólo se cierra el flujo del aparcamiento, que es el del issue.
- **Retirar las dos keys de copy huérfanas** en los 9 locales.
