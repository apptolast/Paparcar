# DET-AR-FIRST-001 — Armado AR-first + ancla de fin de conducción

> Rama: `feature/DET-AR-FIRST-001-ar-first-arming` (sobre `feature/DET-BREADCRUMBS-001-trip-trail`)
> Estado: ESPECIFICADO — pendiente de implementación
> Origen: field-tests 2026-07-09/10. El EXIT de geocerca se entrega sistemáticamente tarde en OEMs
> (6/6 entregas a 951–2.192 m el 10-07 en Oppo; el fix disparador ya venía lejos → GMS muestrea
> ubicación en background cada varios minutos, el radio es irrelevante). Los receivers de Activity
> Recognition dispararon todo el día en ambos móviles con lag pequeño → AR es la señal rápida y
> la estamos desaprovechando como trigger.

## Principio

Extensión del invariante DET-RIDE-PROOF-001 al tercer trigger:

**Dos nominadores independientes (AR IN_VEHICLE ENTER y geofence EXIT), un solo confirmador
(movimiento medido), una sola regla de ancla (el pin nace donde termina la conducción medida).**

Ninguna señal de OS gana autoridad por sí sola; ninguna se mezcla con la estrategia Bluetooth.

## F1 — Entrega AR de doble carril (decisión + testigo)

Replicar la arquitectura probada del geofence EXIT:

- **Carril de decisión (NUEVO):** registro adicional de `ActivityTransitionRequest` entregado a
  `PendingIntent.getForegroundService(CoordinatorDetectionService, ACTION_AR_TRANSITION)`.
  Es el mecanismo que Play Services arranca CON privilegios — el mismo del EXIT de valla, probado
  en campo (6/6 arranques el 10-07 incluso con entrega tardía). NO es el arranque app-side desde
  receiver que crasheó en mayo (BUG-FGS-001: `startForegroundService()` nuestro = 24 crashes;
  la entrega `getForegroundService` de GMS = 0 fallos documentados).
- **Carril de evidencia (EXISTENTE, se conserva):** `ActivityTransitionReceiver` por `getBroadcast`
  sigue estampando `trueTime` en el bus (elapsedRealTimeNanos) y encolando ticks del evaluador.
  Mismo patrón que valla principal + valla testigo: un carril decide, otro atestigua. Si el carril
  de decisión resulta denegado en algún OEM (telemetría lo dirá), el de evidencia mantiene el
  comportamiento actual — degradación, no pérdida.
- El service, al recibir `ACTION_AR_TRANSITION`: `startForeground` inmediato → un fix
  (`GetOneLocationUseCase` con maxAge) → escalera de decisión (F2) → `stopIfIdle` rápido si no
  procede (coste por evento espurio: segundos de notificación, igual que hoy los EXIT).

## F2 — Escalera de decisión del ENTER (en el service, con sesión activa + fix)

| # | Qué dice el fix | Interpretación | Acción |
|---|---|---|---|
| 0 | Sin sesión activa | No hay coche que vigilar | `stopIfIdle` |
| 1 | ENTER rancio (lag trueTime > umbral de frescura, reutilizar `exitEnterPairWindowMs`) | Ventana de exención perdida, dato viejo | Tick del evaluador y parar. JAMÁS armar con dato rancio |
| 2 | Cerca del coche (≤ radio valla + accuracy), sin velocidad de conducción | **Embarque en el coche** — el instante ideal que hoy nunca cazamos | Armar coordinator en modo "esperando prueba de conducción" (`ArmEvidence` nuevo: `vehicle_enter_at_car`). Sus abortos ya acotan el coste: false-ENTER a los 8 pasos, aborto por no-movimiento |
| 3 | Velocidad de conducción creíble (≥ umbral con acc ≤ `minGpsAccuracyForDriving`) O `isBeyondPedestrianReach` respecto al coche | Viaje ya en marcha (ENTER con lag) | Armar + despachar salida preconfirmada por movimiento medido — el mismo camino que hoy solo abre el EXIT tardío |
| 4 | Rango andable, velocidad de andar | ENTER espurio andando (veneno del 09-07) | Tick del evaluador, parar. No armar |

El geofence EXIT conserva TODA su maquinaria actual como segundo nominador (AR tampoco es
garantizado). El AR EXIT no cambia: solo tick `[ar-exit]` (ya existe).

## F3 — Ancla de fin de conducción (fix Camelias / arrastre a casa)

Defecto actual demostrado (Redmi 10-07 15:35–15:54): el candado del ancla (ANCHOR-LOCK-001)
exige ≥8 pasos con el estado "parado" vivo; al aparcar y salir andando inmediatamente solo
llegaron 3 → el andar real borró el ancla desbloqueada de la calle → el GPS de interior rompió
el "parado" 3 veces → el candado acabó cerrándose DENTRO de la casa. A escala grande (sesión
nacida post-viaje) el mismo defecto pone el pin en el salón de casa.

La captura funciona (LOC-001 capturó en la calle a las 15:35:40); lo que falla es la RETENCIÓN
pre-candado: la regla "movimiento → borra ancla y re-captura en la siguiente parada" existe para
distinguir la parada definitiva de una intermedia (semáforo, maniobra de aparcamiento), pero no
sabe distinguir "el coche avanza despacio" de "el usuario anda" — por eso ANCHOR-LOCK le puso el
prerrequisito de 8 pasos, que pierde la carrera al salir del coche inmediatamente.

Nueva regla — **los pasos son el discriminador persona/coche** (siguen siendo fundamentales;
ganan protagonismo, no lo pierden):

- **Movimiento CON pasos = persona** → el ancla NO se borra ni se re-captura jamás. El primer
  paso ya blinda (se acaba la espera de 8): "esto que se mueve soy yo, no el coche".
- **Movimiento SIN pasos = el coche** (semáforo, maniobra, avance lento) → borrar y re-anclar
  en la siguiente parada es correcto y se conserva tal cual.
- El ancla se captura en la transición conducir→parado (LOC-001, ventana de 30 s, sin cambios)
  y **solo la libera conducción creíble** (regla `isRealDrive` existente) o movimiento sin
  pasos. El vagabundeo GPS de interior (Doppler fantasma SIN pasos... ojo: sin pasos parece
  "coche"; el gate es que además exija desplazamiento real acumulado > precisión — el
  vagabundeo no desplaza) no mueve el ancla.
- **Los pasos conservan TODOS sus papeles de verificación sin cambios**: steps+egress para
  confirmar, false-ENTER abort, prueba de egreso. Este punto solo cambia QUIÉN cierra el
  candado del ancla, no cómo se verifica el aparcamiento.
- **Sesión sin conducción medida en-sesión = sin pin, sin excepciones.** Aplica a TODOS los
  caminos de confirmación, incluido el save desatendido 0.5: si la sesión no vio conducir
  (armada post-viaje, evidencia solo sembrada), puede LIBERAR el spot viejo (eso lo autoriza el
  reconcile) pero nunca COLOCAR pin nuevo → notificación accionable "¿dónde has dejado el
  coche?" con deep-link a marcar (reusa `StartAddParkingEventBus`). La siembra de
  `hasEverReachedDrivingSpeed` por `ArmEvidence` pasa a dar autoridad de liberación, no de
  colocación.

## F4 — Contrato de notificación (cierres pendientes del 10-07)

- El caso "lejos de la valla sin medios de verificar" (hoy: 9 ticks `anillo ambiguo, solo fix`
  en 4 h con el móvil a 1,9 km) debe mostrar el prompt "¿sigues aparcado?" con causa +
  consecuencia + remedio — no callar. [feedback_detection_contract]
- Bug: la sesión Redmi 19:19 llegó a scoring Medium y el save desatendido guardó a los 15 min
  **sin que `NotifyParkingConfirmation` se invocara jamás** (a las 15:39 sí). Localizar por qué
  el prompt no se emitió (¿fase nunca salió de Idle?) y garantizar: nunca un save desatendido
  sin prompt previo visible.

## Validación

- Replay harness: fijar como trazas los tres casos de campo del 10-07 —
  (a) Redmi 15:30–15:54 (armado en viaje, ancla debe quedar en la calle, no en la casa),
  (b) Redmi 19:19–19:34 (sesión post-viaje: liberar sí, pin NO, prompt sí),
  (c) Oppo 15:31 (EXIT post-park: false-ENTER abort + salida real debe acabar en prompt, no en
  spot zombi 4 h).
- Field-test ambos móviles: primera salida del día detectada vía ENTER (ventana AR), pin en el
  bordillo con entrada a edificio inmediata, cero pins en casa, prompts visibles.
- Telemetría: contador de arranques `ACTION_AR_TRANSITION` concedidos vs denegados por OEM
  (verificar la hipótesis del carril privilegiado también para AR).

## Riesgos

- Coste de espurios: cada ENTER falso que pase el filtro = 2–4 min de FGS con notificación.
  Filtro de la escalera + abortos existentes lo acotan; medir en campo.
- Doble registro AR: verificar que GMS acepta dos `requestActivityTransitionUpdates` con
  PendingIntents distintos (mismo patrón multi-registro que las 3 vallas). Si no, multiplexar
  en el carril de decisión y que el service re-emita al bus.
- F3 endurece la colocación: más casos acabarán en prompt en vez de pin automático. Es el
  trade-off elegido (pregunta honesta > pin falso), coherente con el contrato.
