# DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001 · cerrar una vía sin comprobar que la otra puede correr deja la sesión sin salida

**Estado:** 🟡 Abierto · sin rama · sin worktree · **diseño SIN decidir** (ver abajo)
**Origen:** field 27-08 (fisio), Oppo. Detectado al preguntarse el user por qué el zombi `a786c135`
seguía vivo.

## Problema

La sesión `a786c135` (Skoda Kamiq, `manual`, Calle Góndola 7) lleva **seis días** con
`isActive: true`. Se creó el **21-08 21:52** y su documento **no se ha vuelto a escribir desde el
21-08**. Es la única sesión activa de la cuenta.

No es que nadie la haya cerrado: es que **ninguna vía PUEDE cerrarla**.

| Vía | Por qué no cierra | Evidencia |
|---|---|---|
| **Coordinator** | tiene prohibido adueñarse de un coche con MAC | `✓ vehicleId locked: addbe660 … (nominator=abf6c516 **vetoed: bt-owned**)` (26-08 08:30:44) |
| **Bluetooth** | el MAC del Kamiq nunca conecta | `50:26:EF:16:1D:C0` aparece **0 veces** en todo el `parkdiag`; 0 sesiones `BLUETOOTH`. El único BT visto son unos auriculares (`00:A4:1C:65:2B:3F`) |
| **SafetyNet** | «lejos pero sin pruebas de viaje» → pregunta, no libera | `geof=a786c135: LEJOS (d=2001m) pero SIN pruebas de viaje` |

El veto `bt-owned` (de `DET-BT-CAR-CANNOT-NOMINATE-A-COORDINATOR-SESSION-001`, field 25-08) es
**correcto en sí mismo**: un coche emparejado pertenece a la vía determinista y el Coordinator no
debe reclamarlo. Lo que falta es la otra mitad: **nadie comprueba que la vía dueña pueda correr.**
El veto cierra una puerta sin mirar si la otra existe, y el resultado es un huérfano.

⚠️ **El vehículo está `isActive: false`** (desactivado en el garaje; el activo es el Ford Focus
`addbe660`, sin BT). O sea: la vía BT no sólo no corre por casualidad — es que ese coche ni siquiera
es el que la app está vigilando.

## Doctrina violada

Es la misma forma que `DET-RETRACT-DENIED-FOREVER-001` («a withdrawal with no terminal state»), un
nivel más arriba: **una sesión sin estado terminal alcanzable**. Allí era una retirada que nunca
podía completarse; aquí es un aparcamiento que nunca puede terminar.

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

Preguntas que hay que contestar antes de escribir código:

1. ¿Qué señal define «la vía dueña no puede correr»? ¿BT desactivado? ¿El MAC no visto en N días?
   ¿El vehículo no activo? Cada una tiene un falso positivo distinto.
2. ¿Dormir es reversible? Si el user vuelve a coger ese coche, la valla tiene que despertar — y
   `reference_market_research_parking_detection` avisa de que **las geocercas se borran y hay que
   re-registrarlas**.
3. ¿Debe la UI mostrar «llevas 6 días aparcado aquí, ¿sigue ahí?» una vez, en vez de preguntarlo
   pegado a cada salida?

## Criterio de éxito

- Una sesión cuya vía dueña no puede correr deja de consumir heartbeat y deja de reintentar la
  retirada, **sin desaparecer del histórico ni del mapa**.
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

## Nota de setup, aparte del bug

⚠️ Esto destapa que **el Oppo es «el móvil de la vía BT» sólo sobre el papel**: con el Focus (sin
MAC) como vehículo activo, `resolveStrategy` devuelve **Coordinator siempre**. Cero sesiones
Bluetooth en todo el log. La vía determinista lleva días sin probarse en ese aparato — justo lo que
avisa `project_field_test_device_setup`: *la estrategia del día depende del coche activo,
verificarlo y no darlo por hecho*. **Esto no es parte del ticket**, es una corrección al setup de
campo.
