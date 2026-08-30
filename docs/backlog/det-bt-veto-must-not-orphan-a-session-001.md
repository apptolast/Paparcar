# DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001 · ¿puede cerrarse una sesión cuya vía dueña no corre?

> ⚠️ **Cruzado con el rediseño (30-08): INTACTO y sigue BLOQUEADO por un viaje con el Kamiq.**
> Dos matices: sus **dos armes espurios** mueren en el triaje de nivel 1 de la Pieza 5 (el resto de su
> mitad de coste es otro worker y no lo cubre nadie), y su **log que miente** es la misma familia que
> §6.2 #13 — conviene barrerlos juntos en la Pieza 2.
> Ver `docs/detection/REDESIGN-DETECTION-SYSTEM.md` §9.3.

**Estado:** 🟡 Abierto · sin rama · sin worktree · **premisa a medias SIN DEMOSTRAR, bloqueado por
un viaje** (ver «Lo que NO está demostrado»)
**Origen:** field 27-08 (fisio), Oppo. Detectado al preguntarse el user por qué la sesión
`a786c135` seguía viva.

## Problema

La sesión `a786c135` (Skoda Kamiq, `manual`, Calle Góndola 7) lleva **seis días** con
`isActive: true`. Se creó el **21-08 21:52** y su documento **no se ha vuelto a escribir desde el
21-08**. Es la única sesión activa de la cuenta.

⛔ **Y eso, por sí solo, es CORRECTO.** El user (27-08): *«el Kamiq no lo he estado usando estos
días, tiene sentido que no aparezca»*. Un coche que no se ha movido sigue aparcado: la sesión
abierta es el dato bueno, no un zombi. Lo que este ticket persigue **no** es cerrarla.

## Lo que NO está demostrado — corrección a la primera versión de este doc

La primera redacción afirmaba que **ninguna vía puede cerrarla** y que el veto `bt-owned` fabrica un
huérfano. **Eso afirma más de lo que se midió.** Lo único observado es que la vía Bluetooth **no se
ejercitó**: el MAC `50:26:EF:16:1D:C0` no aparece en el `parkdiag` porque el coche no se condujo, no
porque la vía falle. Nunca vi al carril BT intentar cerrar nada y fracasar.

Lo que sí está observado, y sigue en pie:

| Vía | Observado | Evidencia |
|---|---|---|
| **Coordinator** | tiene prohibido adueñarse de un coche con MAC, **y su valla sí arma sesiones** | `✓ vehicleId locked: addbe660 … (nominator=abf6c516 **vetoed: bt-owned**)` (26-08 08:30:44) |
| **Bluetooth** | **sin ejercitar** — el coche no se ha usado | `50:26:EF:16:1D:C0` 0 veces; 0 sesiones `BLUETOOTH` |
| **SafetyNet** | «lejos pero sin pruebas de viaje» → pregunta, no libera | `geof=a786c135: LEJOS (d=2001m) pero SIN pruebas de viaje` |

**La pregunta abierta**, que es lo que queda del ticket: cuando el Kamiq SÍ se conduzca, ¿la vía BT
cierra su sesión? Si sí, aquí no hay bug de cierre y sólo queda el coste (abajo). Si no —si el veto
`bt-owned` deja la sesión sin salida real— entonces sí es la misma forma que
`DET-RETRACT-DENIED-FOREVER-001` un nivel más arriba, y el ticket recupera su título.

**Cómo se decide, y es barato:** un solo viaje con el Kamiq emparejado. Señales a mirar en el
`parkdiag`: aparición de `BT DISCONNECTED device=50:26:EF:16:1D:C0` con vehículo emparejado
reconocido, y si `a786c135` pasa a `isActive: false`.

⚠️ Dato de contexto, no acusación: el vehículo está `isActive: false` en el garaje (el activo es el
Ford Focus `addbe660`, sin BT), lo cual es coherente con no estar usándolo.

## De dónde sale el prompt, exactamente — `DET-BT-IDENTITY-GATE-001`

⚠️ **Segunda corrección.** El prompt no sale de «no había pruebas de viaje». Sale del **propio veto
BT**, y el user lo señaló antes de que yo lo encontrara: *«el Kamiq está vinculado por BT; los
vehículos por BT tienen como trigger principal el BT, no debería lanzar prompts así porque sí»*.

`ParkingSafetyNetWorker:238` → `vehicleBtGated = btEnabled && el vehículo tiene MAC`. Para el Kamiq
es **cierto** (tiene MAC, y el BT del móvil está encendido: los auriculares conectan y desconectan
durante todo el log). Y `lastBtConnectedAtMs` es **null**, porque su MAC no ha conectado nunca. Con
eso:

```kotlin
val btIdentityMissing = vehicleBtGated &&
    (lastBtConnectedAtMs == null || lastBtConnectedAtMs < sessionStartMs)   // ← permanentemente true
fun releaseOrAsk(dispatch) = if (btIdentityMissing) PromptStillParked(geofenceId) else dispatch
```

O sea: **toda** liberación que el safety-net reconstruya para esa sesión se degrada a pregunta, y lo
seguirá haciendo mientras el MAC no conecte. El guard hace lo que se diseñó para hacer —evita
liberar la plaza de un coche BT cuando te recogieron en otro (field 2026-07-18)— pero su KDoc
justifica el coste diciendo *«the honest exit is the human prompt, NEVER silence»*, y eso presupone
que preguntar es barato. Con un coche aparcado a largo plazo cuyo BT no conecta, esa «salida
honesta» se dispara **cada vez que sales con el OTRO coche**.

**El punto de doctrina del user es el correcto y es más fuerte que mi primer encuadre:** para un
coche con MAC, el trigger es el BT. Juzgar su sesión por desplazamiento es razonamiento del carril
probabilístico aplicado a un coche que no le pertenece — la mezcla que CLAUDE.md prohíbe, en el
sentido en que no solemos mirarla.

### ⛔ Y el log MIENTE sobre esta rama

`ParkingSafetyNetWorker` imprime, para cualquier `PromptStillParked`:

```
geof=a786c135: LEJOS del coche (d=2001m) pero SIN pruebas de viaje → te pregunto '¿sigues aparcado?'
```

En esta rama **sí había pruebas de viaje** — las mismas 4 pasos que liberaron la sesión del Focus 13
ms después. Lo que faltaba era la identidad BT. Las dos causas salen con el mismo texto, y ese texto
afirma la que no es. Es la misma familia que `DET-PROMPT-STATES-ITS-REASON-001` resolvió en el lado
del confirm. **Sin arreglar esto no se puede diagnosticar el resto**: yo mismo no pude distinguir las
dos rutas leyendo el log, sólo deducirlas del código.

## Lo que cuesta, medido — ⛔ ningún pin, sólo ruido

El fallo asimétrico **aguantó**: en tres días el zombi no plantó ni un pin. Los dos armes que
provocó abortaron. El coste es ruido y trabajo, no datos falsos:

- **568 evaluaciones en 3 días** — 22 (25-08) · 354 (26-08) · 192 (27-08). Cada heartbeat evalúa su
  valla.
- **44 `PERMISSION_DENIED` con stack trace**: `spots/a786c135` **no existe** en Firestore, y el
  marcador de deduced-departure no se limpia nunca (decisión deliberada de
  `DET-RETRACT-DENIED-FOREVER-001`), así que cada false-ENTER cerca de casa reintenta la retirada.
- **2 armes espurios del Coordinator** desde su valla (26-08 08:30 y 16:01), ambos abortados.
- **3 re-registros de la valla** + 1 notificación al user (ésa la cubre
  `DET-EXPLAINED-RIDE-ASKS-NO-OTHER-CAR-001`).

## Diseño — ⛔ SIN DECIDIR, y el fix tentador es el equivocado

Sea cual sea la respuesta a la pregunta abierta, **la parte del coste se puede atacar ya**: 44
reintentos contra un spot inexistente y 568 evaluaciones en 3 días son desperdicio aunque la sesión
sea perfectamente legítima.

**Lo que NO hay que hacer:** *«vehículo `isActive: false` → matar su sesión y su valla»*. El KDoc de
`Vehicle.isActive` lo dice: *«The vehicle currently used for detection and spot reporting. Only one
is active at a time»* — es un **SELECTOR**, no una jubilación. Tener dos coches aparcados a la vez es
legítimo y cotidiano; olvidar dónde está el segundo porque hoy conduces el primero sería un fallo
mucho peor que el zombi.

**Tampoco:** dejar que el SafetyNet libere la sesión cuando detecte que la vía BT no corre. Liberar
es destructivo y esto es justo el lado en el que la doctrina prohíbe adivinar: un coche aparcado de
verdad perdería su pin. *Mejor un falso negativo que un falso positivo.*

**La dirección que sí encaja con la doctrina** es no perseguir el cierre, sino **acotar el coste de
no poder cerrar**: una sesión que ninguna vía puede terminar debería poder quedarse **callada** —
dormir la valla, dejar de reintentar la retirada de un spot inexistente, dejar de gastar heartbeat—
sin dejar de existir. Y, por separado, que el user tenga una forma clara de decir «ese coche ya no
está ahí» sin que sea un prompt colgado de cada salida.

### Cómo se resuelve la tensión del veto BT sin desarmarlo

El veto tiene razón en **no liberar**: si conduces el Kamiq con el BT apagado, el desplazamiento es
real y liberar a ciegas perdería la plaza. Su KDoc elige preguntar por eso. Pero hay un caso en el
que preguntar tampoco hace falta, y es justo el del 27-08: **cuando el desplazamiento YA tiene
dueño.** Si en el mismo tick otra sesión despacha su salida con pruebas de viaje, el movimiento está
explicado — te fuiste en el Focus — y no hay ninguna sospecha que resolver sobre el Kamiq.

Es exactamente lo que propone `DET-EXPLAINED-RIDE-ASKS-NO-OTHER-CAR-001`, así que **ese ticket ya
cubre el caso que motivó éste**, sin tocar el veto ni relajar la seguridad: el silencio es seguro
porque el viaje tiene propietario, no porque confiemos en el BT.

Lo que queda aquí, entonces, es más pequeño y más honesto: (a) el texto del log que miente, (b) el
coste continuo, y (c) la pregunta abierta de si el BT cierra la sesión cuando el coche se conduzca.

Preguntas que hay que contestar antes de escribir código:

1. ¿Qué señal define «la vía dueña no puede correr»? ¿BT desactivado? ¿El MAC no visto en N días?
   ¿El vehículo no activo? Cada una tiene un falso positivo distinto.
2. ¿Dormir es reversible? Si el user vuelve a coger ese coche, la valla tiene que despertar — y
   `reference_market_research_parking_detection` avisa de que **las geocercas se borran y hay que
   re-registrarlas**.
3. ¿Debe la UI mostrar «llevas 6 días aparcado aquí, ¿sigue ahí?» una vez, en vez de preguntarlo
   pegado a cada salida?

## Criterio de éxito

- **Primero, medir**: un viaje con el Kamiq contesta si la vía BT cierra su sesión. Sin ese dato el
  ticket no debe escribir código.
- Una sesión aparcada a largo plazo deja de consumir heartbeat al mismo ritmo y deja de reintentar la
  retirada de un spot inexistente, **sin desaparecer del histórico ni del mapa**.
- Un segundo coche aparcado de verdad, con su vía viva, sigue comportándose igual que hoy.
- El user puede cerrar esa sesión a mano en un paso.
- Campo: tres días sin `retract failed for spot=…` y sin evaluaciones de una valla que nadie puede
  cerrar.

## Consumidores auditados

Pendiente — se hará al abrir la rama, y el barrido es la mitad del trabajo. Punto de partida:
`grep -rn "bt-owned\|VehicleFenceOwnershipPolicy\|resolveStrategy" composeApp/src --include=*.kt`

## Relación con otros tickets

- `DET-EXPLAINED-RIDE-ASKS-NO-OTHER-CAR-001` — hermano; ataca **por qué molesta**, no por qué vive.
  Independientes: la pregunta está mal hecha aunque la sesión sea legítima.
- `DET-BT-CAR-CANNOT-NOMINATE-A-COORDINATOR-SESSION-001` — el veto que crea el huérfano. **No hay
  que revertirlo**; hay que darle la mitad que le falta.
- `DET-RETRACT-DENIED-FOREVER-001` — misma forma un nivel abajo, y dueño del marcador que no se
  limpia.

## Por qué el carril BT está sin medir — y por qué eso está BIEN

Con el Focus (sin MAC) como vehículo activo, `resolveStrategy` devuelve **Coordinator siempre**:
cero sesiones Bluetooth en todo el log.

⛔ **Eso NO es un fallo de setup, y proponer «pon el Kamiq activo para probar BT» sería empeorar el
banco de pruebas.** El user (27-08): *«seguimos teniendo falsos positivos y negativos con Focus;
claro que no estoy con el Kamiq, que es por BT y tiene menos riesgo»*. Correcto: el Coordinator es
el carril **probabilístico**, es donde viven los FP y los FN, y es donde el tiempo de conducción
—el recurso escaso— rinde. El BT es determinista (MAC + fix + 30 m) y tiene mucho menos que fallar.

La consecuencia para ESTE ticket es la única que importa: la vía dueña de `a786c135` está sin medir
**por una buena razón**, así que la pregunta abierta de arriba no se contesta «de paso». Cuesta un
viaje con el Kamiq, y ese viaje compite con el tiempo de cazar FP en el Focus. **Mientras no salga
gratis, este ticket se queda parado en su mitad de coste** (los 44 reintentos y las 568
evaluaciones), que sí se puede atacar sin conducir nada.
