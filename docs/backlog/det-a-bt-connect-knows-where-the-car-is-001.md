# DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001 · Cada CONNECT dice dónde está el coche, y lo estamos tirando

**Estado:** 🟡 Abierto · **sin rama · sin worktree · diseño escrito, SIN empezar** — bloqueado a
propósito por un **viaje con el Kamiq** (decisión del user, 08-09-2026): las tres decisiones abiertas
de abajo se contestan con campo, no de cabeza. Mismo bloqueo que
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

⛔ **Lo que NO está demostrado**: que en ese escenario el EXIT de A llegue efectivamente a armar y
confirmar (depende de que el trayecto a B se mida como conducción). Lo verificado es que **el camino
existe y nada lo impide**, no que ocurra siempre. Se mide en el mismo viaje con el Kamiq que bloquea
este ticket, con un paso extra: mover el coche con otra persona antes.

**La raíz es la misma que el resto de la ficha**: la app no tiene el concepto de *«el coche se movió
sin mí»*, y el enganche BT es la única señal que lo delata. Refuerza el punto 5 del diseño y lo
convierte en el caso principal, no en un borde.

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
   prueba de que la sesión vieja miente. Es el caso del padre. Decidir si eso es (a) preguntar con
   la posición nueva, (b) marcar la sesión como dudosa, o (c) no hacer nada hasta el disconnect.
   Mi lectura: (a), y sólo en el disconnect — el CONNECT no habla.

**Decisiones abiertas:**

- **A. Alcance de radio como número.** No lo tenemos medido. Clase 2 nominal ~10 m, en la práctica
  más y muy variable según coche y móvil. ⛔ No inventarlo: o se mide en campo con los dos móviles,
  o el radio se sostiene sólo sobre accuracy+edad y se declara así.
- **B. ¿Reemplaza la ubicación de una sesión activa, o sólo pregunta?** Toca `sessionStartMs` y con
  él el guard de `DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001`. No decidir sin leer ese ticket entero.
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
