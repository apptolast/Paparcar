# DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001 · Cada CONNECT dice dónde está el coche, y lo estamos tirando

**Estado:** 🟡 **MITAD 1 EN MASTER, mitad 2 abierta** — el guard (una sesión que el CONNECT desmiente
no anuncia plaza) está en producción; publicar la **plaza dudosa desde B** sigue bloqueado por un
**viaje con el Kamiq** (decisión del user, 08-09-2026): las decisiones abiertas de abajo se contestan
con campo, no de cabeza. Lo que NO necesitaba campo se midió en test antes de tocar nada
(ver §DEMOSTRADO). Mismo bloqueo que
[DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001](det-bt-veto-must-not-orphan-a-session-001.md), y el mismo
viaje los desbloquea a los dos.
**Origen:** duda del user (08-09-2026), justo después de mergear
[DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001](det-bt-a-ride-is-measured-not-timed-001.md).

## Problema

`DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001` cerró bien la mitad que le tocaba: un enganche BT sin
desplazamiento medido ya no puede fabricar una sesión de 0,95 con geocerca que después libera una
plaza a la comunidad por una salida que nadie hizo. Pero al negarle la AUTORIDAD le negó también la
MEDICIÓN, y ahí se tira información buena:

> **Un enganche BT prueba que el móvil estuvo a distancia de radio del coche.** Eso no es un viaje.
> Pero sí es, casi siempre, lo más fresco que la app sabe sobre DÓNDE ESTÁ EL COCHE.

El caso del user, textual: *«a lo mejor el coche lo ha cogido mi padre y yo he pasado cerca de él
cuando lo estaba aparcando»*. Ahí:

- Tú no has conducido → correcto no abrir sesión tuya con autoridad de viaje medido.
- Pero el coche **ha cambiado de sitio**, y tu app sigue enseñando el pin viejo, que ya es falso.
- El enganche BT es **la única evidencia que existe** de que se movió y de dónde está ahora.

Hoy eso acaba en un nudge que pregunta *«¿dónde dejaste el coche?»* — la pregunta equivocada (tú no
lo dejaste) y encima a ciegas, teniendo nosotros una posición medida.

## Doctrina violada

Ninguna de las tres rectoras, y conviene decirlo: no confirmar sin movimiento medido es
**correcto**. Lo que se rompe es un principio de segundo orden que el proyecto sí sostiene en otros
sitios: *un dato medido no se descarta por no alcanzar para la conclusión más fuerte; se degrada a
la conclusión que sí sostiene* — que es exactamente lo que hacen `closed_approximate_zone` y
`SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001`. Aquí la conclusión que el enganche sí sostiene es *«el
coche está en esta zona»*, no *«tú aparcaste aquí»*.

Y hay una segunda: **preguntar sin poner lo que ya sabemos** es hacerle al usuario el trabajo que la
app puede hacer.

## Señales / datos disponibles

- ✅ **Ya sellamos el fix del CONNECT** (`BtConnectionStore.recordConnected(..., fix)`, mergeado en
  `2a1a42c6`). Hoy se usa **sólo** como origen para medir el desplazamiento del viaje. Es el mismo
  dato que este ticket quiere leer como posición del coche.
- ⛔ **El candidato del disconnect NO sirve para esto.** La caza arranca en la desconexión y exige
  estar parado; si pasas de largo, el primer fix parado llega en tu portal o en un semáforo. Es el
  defecto que ya documenta `DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001`: ese punto habla de tu cuerpo,
  no del coche. **El punto honesto es el del CONNECT.**
- `zoneRadiusMeters`, `isApproximate`, `ClosedApproximatePin/Zone` — vocabulario de zona ya en el
  modelo, en Room y en Firestore.
- `PendingParkNudge` + `DetectionPath.Nudge(askedBy)` (mergeado en `d1833db5`): el ask ya sabe decir
  quién preguntó; le falta decir **dónde**.

## Lo que aportan los dos diseños sin decidir (leídos antes de escribir esto)

**`DET-A-BT-DRIVE-LEAVES-NO-TRACE-001`** — nombra exactamente la misma señal desaprovechada:

> *«Señal de arranque sin explotar: el ACL connect del MAC emparejado. Ya se escucha en el carril BT;
> hoy no arranca ningún muestreo.»*

Ese ticket la quiere como **inicio de trayecto** (para que un viaje BT tenga ruta y km). Éste la
quiere como **última posición conocida del coche**. Son dos usos de un mismo evento y no compiten:
el sello del CONNECT es el punto de partida de los dos. ⛔ Pero su alcance sigue descartado por el
user (31-08: *no* medir el viaje entero), así que este ticket **no** abre muestreo durante el
trayecto — se queda en el fix cacheado, que ya tenemos y cuesta cero.

**`DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001`** — aporta un aviso concreto sobre el consumidor:
`ParkingSafetyNetWorker` degrada a pregunta toda liberación mientras `lastBtConnectedAtMs == null ||
< sessionStartMs` (`btIdentityMissing`). O sea: **el sello del CONNECT ya alimenta a otro guard**, y
tocar cuándo/qué se escribe ahí mueve el suelo de ese ticket. Si este ticket llegara a crear o
refrescar una sesión desde un CONNECT, `sessionStartMs` cambiaría bajo sus pies. Hay que decidirlo
explícitamente, no descubrirlo en campo. Su premisa sigue **sin demostrar** y **bloqueada por un
viaje con el Kamiq**, que es la misma precondición de campo que este ticket.

## El caso que lo vuelve urgente: la plaza de A se publica cuando el coche ya no está en A

Pregunta del user (08-09): *«tengo el coche aparcado en A; mi hermano lo coge y lo aparca en B;
cuando yo lo cojo en B y conduzco, ¿se libera el spot de A?»* Trazado en código, sin ejecutarlo:

- La publicación va atada a **la sesión que se cierra**, no a dónde está el coche:
  `ProcessConfirmedDepartureUseCase` publica en la ubicación de la sesión (A). Nada comprueba que el
  coche siguiera allí.
- El filtro de frescura es `freedSpotIsStillThere(exitAtMs = exitTimestampMs…)` en
  `RunDepartureCheckUseCase`, y ese instante es **la salida que medimos nosotros**, no la real (la de
  tu hermano, horas antes y sin testigo). Una plaza liberada hace horas se anunciaría como fresca.
- ✅ Si se llega a aparcar en C **sin que nadie mida una salida de A**, el confirm nuevo **limpia la
  sesión vieja SIN publicar** y le quita su geocerca huérfana (`ConfirmParkingUseCase`, rama de
  re-park). Por ahí no hay plaza fantasma.
- ⚠️ El riesgo está en el ORDEN: si mientras A sigue activa alguien mide una salida —el EXIT de la
  geocerca de A al salir de casa a por el coche, y el trayecto hasta B medido como conducción—, se
  procesa esa salida y **se publica plaza en A**. El propio safety net lo dice: encadena la salida
  antes del confirm *«para que la sesión vieja se resuelva (publish+clear)»*.
- ⚠️⚠️ Y el **CONNECT en B satisface el gate de identidad** (`lastBtConnectedAtMs >= sessionStartMs`),
  que era justo lo que hasta ese momento degradaba toda liberación de A a pregunta
  [DET-BT-IDENTITY-GATE-001]. Conectar en B **quita el veto** que protegía a la sesión de A.

**Y la mitad que más importa, que la redacción anterior dejaba en segundo plano** (el user, 09-09:
*«si me monto en el vehículo en B y conduzco, debería mandarse spot de B»* — y tiene razón):

> **La plaza de B no se publica NUNCA.**

El spot se construye con la ubicación de la SESIÓN (`ProcessConfirmedDepartureUseCase` lo arma desde
`session.location`, su radio y su fiabilidad), y la sesión dice A. Así que:

- la plaza REAL que el usuario deja libre al salir de B **no llega a la comunidad**, con salida
  medida o sin ella;
- y si además se mide una salida con A activa, encima se anuncia una plaza en A donde el coche ya no
  estaba.

O sea: el fallo no es «un pin mal puesto», es **una plaza que no se publica y otra que se publica
mal**. Corregir la sesión desde el CONNECT no es un adorno para que Home enseñe bien el coche: es la
**precondición para publicar la plaza correcta**. Si el CONNECT en B reubica antes de arrancar, la
salida publica B, que es lo que el producto promete.

### ✅ DEMOSTRADO en test, no supuesto (09-09-2026)

El user señaló, con razón, que esto no hacía falta esperarlo a un viaje: el código lo contesta. Dos
tests en `ProcessConfirmedDepartureUseCaseTest` montan el escenario y MIDEN lo que la app hace hoy —
no proponen nada, no cambian producción:

- `should_publishTheFreedSpotAtA_when_theCarWasAlreadyMovedToB` — con la sesión en A y el coche ya en
  B, una salida procesada **publica la plaza en A**, coordenada a coordenada. Confirma que el spot
  sale de `session.location` y que la firma del caso de uso **no admite ninguna posición actual**.
- `should_neverPublishTheSpotAtB_when_thatIsThePlaceActuallyFreed` — lo publicado cae a **más de 1 km**
  de donde el coche estaba de verdad. La plaza real no se publica nunca.

⛔ **Lo que sigue SIN demostrar, y esto sí es físico**: si en ese escenario el EXIT de la geocerca de
A llega a armar y confirmar (depende de que el OS lo entregue y de que el trayecto hasta B se mida
como conducción). O sea: está demostrado **qué pasa cuando la salida se procesa**; falta saber **con
qué frecuencia se procesa**. Eso se mide en el viaje con el Kamiq, con un paso extra: mover el coche
con otra persona antes de cogerlo.

**La raíz es la misma que el resto de la ficha**: la app no tiene el concepto de *«el coche se movió
sin mí»*, y el enganche BT es la única señal que lo delata. Refuerza el punto 5 del diseño y lo
convierte en el caso principal, no en un borde.

## La regla que se deduce de todo lo anterior (user, 09-09)

> *«Si el coche sale de B y se publica A, entonces por BT deberíamos publicar siempre la ubicación
> de donde nos conectamos, no la de la sesión de aparcamiento actual.»*

**Correcto en el fondo, y es el principio que le faltaba a la ficha:** en el carril BT, la autoridad
sobre DÓNDE ESTABA EL COCHE es el CONNECT, no lo que la sesión recuerde. La sesión es memoria; el
connect es medición, y la medición gana sobre la memoria cuando se contradicen.

Dos matices que la regla necesita para no empeorar el caso normal:

1. **Cuando connect y sesión coinciden, el pin de la sesión es MEJOR dato.** El fix del connect es un
   *last known* cacheado, de accuracy y edad desconocidas; el pin de la sesión se eligió pin-grade a
   propósito. Así que la regla operativa no es *«usa siempre el connect»* sino **«el connect decide
   SI la sesión sigue siendo válida»**: si coinciden dentro de tolerancia, publica el pin (más
   preciso); si discrepan, la sesión está caducada y manda el connect.
2. **Si manda el connect, se publica lo que ese fix sostiene: una ZONA, no un punto.** Publicar un
   punto exacto desde un fix cacheado sería afirmar precisión que no tenemos — la misma línea que
   `ROUTE-GAP-HONEST-001` y que la prohibición de inventar radios.

**Y dónde aplicarla, que es la decisión de verdad.** Tres formas de conseguir que la salida de B
publique B:

- **(a) Que el publicador lea el fix del connect** en vez de la sesión, sólo en el carril BT.
- **(b) Reubicar la SESIÓN de A** — mismo id, otra ubicación.
- **(c) SESIÓN NUEVA en B**: cerrar la de A sin publicar y abrir una nueva donde el connect dice que
  está el coche.

**(c) es la buena, y es la que pide el user (09-09).** Descartadas las otras dos con su razón:

- **(a) es un parche con forma de arreglo**: la plaza saldría bien, pero **Home te seguiría enseñando
  el coche en A**. La comunidad recibiría la verdad y el dueño la mentira.
- **(b) deja la sesión mintiendo en todo lo demás**: su hora de inicio, su duración y su ruta
  seguirían describiendo el aparcamiento de A, que no es éste. Reubicar arregla un campo y corrompe
  los otros cinco.
- **(c) es lo que de verdad pasó**: *el coche fue aparcado en B, por otra persona, y nos enteramos
  ahora*. Una sesión nueva tiene legítimamente su propio inicio, y de paso **no mueve
  `sessionStartMs` de la vieja** — que era el choque con
  `DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001`. Reutiliza además maquinaria ya probada: la rama de
  re-park de `ConfirmParkingUseCase` ya cierra la sesión anterior **sin publicar** y le quita su
  geocerca huérfana.

**Y aquí está lo que mata el falso positivo, que es más importante que la sesión nueva:** la sesión
de A tiene que cerrarse **sin publicar nada**. No porque la plaza de A no exista —existe, la liberó
quien se llevó el coche— sino porque **no sabemos cuándo se liberó**: nadie lo vio. Anunciarla como
recién liberada es exactamente la mentira. El filtro de frescura no puede protegernos aquí, porque
mide desde NUESTRA salida, no desde la real.

Tres condiciones para que la sesión nueva no abra un agujero nuevo:

1. **No puede afirmar un aparcamiento MEDIDO.** Nadie midió ese park; sale de un fix cacheado. Zona,
   fiabilidad baja, y **no publica nada por sí misma**: publicará cuando el usuario salga de B, que
   eso sí se mide al conducir. Si no, se reabre justo lo que cerró
   `DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001`.
2. **Se dispara con la DISCREPANCIA, no siempre.** Si el connect cae donde ya dice la sesión, no pasa
   nada: la sesión sigue y su pin es mejor dato.
3. **Sin preguntar en el peor momento.** En el CONNECT el usuario está arrancando; ahí no se
   interrumpe. Se mide en el connect y se decide después, como manda su propia regla.

## Plan: dos mitades con riesgos muy distintos

**Mitad 1 — el guard. ✅ HECHA (09-09-2026).** `parkedSessionIsContradictedByBtConnect` en
`domain/detection/`, gemelo espacial de `freedSpotIsStillThere`, leído por las DOS puertas a la
comunidad. Falla cerrado sin fix, sin instante y con un connect anterior al aparcamiento.
El barrido encontró que `ProcessConfirmedDepartureUseCase` no era la única puerta:
`FinalizeDeducedDepartureUseCase` promueve el spot provisional a vida completa más tarde y necesitaba
el mismo gate, y la marca `markProvisionalDeparture` corría incondicionalmente —bloquear sólo la
publicación habría dejado la sesión prometida a la otra puerta—. `ReleaseActiveParkingSession` y
`ReportManualSpot` quedan exentos: son afirmaciones del usuario.
2 461 tests verdes (+9). Detalle en `docs/detection/PARKING-DETECTION.md`.

**Lo que era, para el registro:** Si el connect dice que el coche
no está donde dice la sesión, esa sesión **no puede publicar plaza al cerrarse**. Mata el falso
positivo, no inventa ninguna posición, y **no depende de ninguna de las tres decisiones abiertas**.
Asimétricamente seguro: como mucho deja de publicar una plaza que sí era buena — un falso negativo,
que es el lado correcto.
⛔ Sigue sin saberse **con qué frecuencia muerde hoy**: eso es campo. Pero el guard no necesita
saberlo para ser correcto.

**Mitad 2 — la sesión nueva en B (grande). El estado final, en palabras del user (09-09):**

> *«Si el parking que dejé en A, conduzco desde B y no coinciden las posiciones, se lanza plaza
> DUDOSA desde B.»*

Exacto, y conviene que quede escrito así porque la mitad 1 sola **no llega ahí**: hoy, cuando las
posiciones no coinciden, no se publica nada — la plaza de B se pierde. Eso es un falso negativo, que
es el lado correcto para vivir mientras tanto, pero **no es el destino**: la plaza de B existe, la
está liberando el usuario en ese mismo momento y es la única que la comunidad debería recibir.

«Dudosa» ya tiene vocabulario en la app y no hay que inventarlo: `SpotReleasePolicy.Shape.Zone` —
radio, confianza capada y vida provisional. La posición sale del fix del CONNECT; la salida de B sí
se mide (el usuario conduce). Lo que falta para poder escribirlo:

- **el radio**, que es la decisión A y no se puede inventar (alcance del enlace sin medir);
- **la fiabilidad** con la que nace esa sesión;
- y decidir si la plaza la publica la sesión nueva al cerrarse o el propio disconnect.

**Mitad 2 — resto del alcance.** Toca el radio de zona, el gate de identidad del ticket
del veto y la fiabilidad con la que nace. Necesita las tres decisiones abiertas y el viaje.

⛔ Alcance: sólo aplica al carril BT, que es donde existe un connect. Un vehículo sin MAC no tiene
esta señal y su sesión sigue siendo la única respuesta.

## Diseño

**La regla que pide el user:** *cada CONNECT del MAC emparejado restablece dónde está el coche —
sin guardar nada todavía; se decide en el DISCONNECT.*

1. **El CONNECT sólo MIDE.** Refresca el sello (ya lo hace) y con él la mejor estimación de la
   posición del coche. Cero escrituras en Room, cero Firestore, cero notificaciones: cualquier cosa
   que se persista ahí es una afirmación sobre un coche que a lo mejor está a punto de arrancar.
2. **El DISCONNECT decide**, y ya tiene la escalera montada por el ticket anterior:
   - `Moved` → como hoy: viaje medido, sesión con su autoridad.
   - `DidNotMove` / `Unmeasurable` → hoy nudge a ciegas; aquí **nudge ANCLADO** en el fix del
     CONNECT, como ZONA y no como punto.
3. **Zona, no punto, y el radio se calcula, no se inventa**: alcance de radio del enlace + accuracy
   del fix + lo que el fix pudo envejecer. Nunca un radio por defecto — la prohibición ya existe.
4. **Ni una plaza a la comunidad por este camino.** La posición es evidencia; el viaje, no. Lo que
   se publica al salir depende de la salida, y aquí no hay salida medida de nadie.
5. **Si ya hay sesión activa para ese coche y el CONNECT cae lejos de ella**, el enganche es la única
   prueba de que la sesión vieja miente. Es el caso del padre, y ya **no es una duda abierta**:
   se resuelve arriba (§*Plan: dos mitades*) — primero el guard que le prohíbe publicar, después la
   sesión nueva en B. Se decide con la DISCREPANCIA, y nunca interrumpiendo en el connect.

**Decisiones abiertas:**

- **A. Alcance de radio como número.** No lo tenemos medido. Clase 2 nominal ~10 m, en la práctica
  más y muy variable según coche y móvil. ⛔ No inventarlo: o se mide en campo con los dos móviles,
  o el radio se sostiene sólo sobre accuracy+edad y se declara así.
- **B. ~~¿Reemplaza la ubicación de una sesión activa, o sólo pregunta?~~ DECIDIDA (09-09): ni una
  cosa ni la otra — sesión NUEVA en B**, cerrando la de A sin publicar (§*Plan: dos mitades*). Lo que
  queda de esta decisión es sólo su mitad 2: con qué fiabilidad y qué radio nace esa sesión. Al no
  tocar el `sessionStartMs` de la sesión vieja, deja de chocar de frente con
  `DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001` — pero ese ticket sigue habiendo que leerlo entero
  antes, porque el gate de identidad lee el sello del CONNECT que aquí se usa.
- **C. ¿Se ancla también el nudge del carril COORDINATOR**, o sólo el del BT? El coordinator suele
  tener mejor posición que este fix cacheado; anclarlo a lo peor sería un retroceso.

## Criterio de éxito

- Un enganche sin viaje deja al usuario a **un toque** de tener el coche bien situado, en vez de a
  un pin en blanco.
- Ni una sesión con autoridad de viaje medido nace de un CONNECT. Verificado por test.
- El radio declarado se sostiene con lo medido: hay test de que no existe radio por defecto.
- Campo (misma precondición que todo lo BT): MAC emparejada, `BLUETOOTH_CONNECT` concedido,
  `prodDebug` en el Oppo, gate de diagnósticos ON.

## Consumidores auditados

> A completar durante el trabajo.

- `BtConnectionStore` (sello del CONNECT) — escritor único; lo lee también el safety net. → pendiente
- `ParkingSafetyNetWorker` (`btIdentityMissing`) — ⚠️ el aviso de
  `DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001`. → pendiente
- `BluetoothParkingDetector` (ramas `DidNotMove` / `Unmeasurable`) → pendiente
- `showMarkParkingNudge` / `PendingParkNudge` / `DetectionPath.Nudge` — el ask que hay que anclar. →
  pendiente
- Vocabulario de zona (`zoneRadiusMeters`, `isApproximate`, paths aproximados) → pendiente
- `docs/detection/PARKING-DETECTION.md` — entrada obligatoria en la misma tarea. → pendiente
