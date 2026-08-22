# DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001 · Un despertar caro no se calla: se abarata

**Estado:** ✅ Done · en master · **era el agujero residual que
`DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001` dejó a propósito** (✅ master `4d1d6716`) ·
⏳ falta el campo: que un paseo real junto al coche no inunde de sesiones

## El dato que lo desbloqueó — field 2026-08-22, Redmi

El doc de apertura decía «⛔ no empezar antes de validar en campo el amortiguador; si sus dos puertas
bastan, esto se queda en el cajón». **No bastaron, y hay medida.**

| Hora | Arm | Resultado |
|---|---|---|
| 18:38:26 | `SIGNIFICANT_MOTION` (sentry-wake) | `aborted_false_enter` · vmax 14 km/h · 13 pasos |
| 18:39:39 | `SIGNIFICANT_MOTION` (sentry-wake) | `aborted_false_enter` · vmax 0 km/h · 12 pasos |
| 18:40:20 | `SIGNIFICANT_MOTION` (sentry-wake) | `aborted_false_enter` · vmax 0 km/h · 16 pasos |
| 18:43:59 | **`GEOFENCE_EXIT`** d=255 m `dep=verified_enter` | ✅ `confirmed_steps+egress` · 75 km/h |

Tres abortos en **114 segundos**, caminando **dentro** de la valla, y conduciendo tres minutos
después. Las dos puertas del amortiguador **no aplicaron, y con razón**: los abortos estaban mucho
más juntos que el decaimiento de 10 min, y el móvil estuvo dentro de su propia valla todo el rato.
El sensor se apagó exactamente como estaba diseñado.

El viaje se cazó — **pero por la geovalla, no por el centinela**. Las puertas hacen seguro al
amortiguador cuando puede PROBAR que otro carril vigila; aquí no lo probó nada, simplemente la valla
entregó. La noche del 21-08 es lo que pasa cuando no entrega.

## El error de encuadre

Lo caro **no es el sensor**. `TYPE_SIGNIFICANT_MOTION` corre en el co-procesador y armado cuesta
~cero. Lo caro es lo que el disparo DESENCADENA: sesión FGS con stream de GPS, documento de sesión +
documento de armado en Firestore, y un boleto más para que un espejismo Doppler de arranque en frío
finja conducción. Estábamos apagando lo barato para no pagar lo caro.

## Lo que se midió (la pregunta que podía hundir el ticket)

El doc decía: *«si despertar el GPS cuesta casi lo mismo que la sesión, el ticket entero se cae. Es
la primera pregunta a responder, no la última.»* Respondida con los datos reales de hoy:

- Las sesiones abortadas de hoy duraron **0,3–1,2 min** con **2–13 fixes** cada una — no los 4 min
  que el doc asumía. **El ahorro es menor de lo prometido**: ~7 fixes → 1 fix, no 240:1.
- Pero cada una también paga arranque de FGS y **dos documentos en Firestore**, que el triaje no
  paga en absoluto.

**Reencuadre honesto: el ahorro no es el argumento que sostiene este ticket — es no quedarse ciego.**
El ahorro es real pero modesto; lo que no era aceptable es que el único nominador que no pasa por
GmsCore estuviera apagado durante una conducción, colgando de que la valla entregue.

## Diseño

Durante el periodo de silencio el sensor **sigue armado**; lo que cambia es lo que un disparo COMPRA:

1. **Suelo de cadencia** — `mayTriageSentryWake`. Por debajo de `cheapWakeMinTriageIntervalMs`
   (60 s) el disparo se descarta **sin leer fix**. La moción significativa se re-dispara cada ~18 s
   al caminar; sin suelo cambiaríamos una factura por otra. 60 s son ~500 m a 30 km/h: un viaje no
   cabe ahí, y sigue agitando el sensor.
2. **Un fix, y el veredicto** — `cheapWakeVerdict`. Escala si el fix no puede ser un peatón junto a
   su coche, con las **dos preguntas que el proyecto ya sabe hacer** — cero fórmulas nuevas:
   - `isCredibleDrivingSpeed` (velocidad **y** precisión, así que leer un fix no compra boleto de
     espejismo);
   - `isInsideAnyOwnedFence` invertido — si el cuerpo salió de la valla, el silencio ya perdió su
     propia justificación.
3. **Fix nulo → ESCALA.** La misma asimetría que la puerta de valla: fallar hacia el ruido cuesta una
   sesión, fallar hacia el silencio cuesta una plaza. Un triaje que no ve no puede concluir «no pasa
   nada».

Coste en la tormenta del 13-08: **~60 fixes/hora en vez de ~130 sesiones FGS**, y nunca ciego.

### El triaje es un FILTRO, no un juez

El replay del 22-08 lo enseña: los dos abortos de `vmax 0km/h` se quedan callados, y el que leyó
14 km/h **escala** — arranca la sesión real y el coordinator la refuta como `aborted_false_enter`,
igual que en campo. Ahorra los casos obvios y reenvía los dudosos al evaluador que sí tiene autoridad
para decidir. La asimetría del proyecto, intacta.

### Qué NO se toca

- **La puerta de valla de `sentryWakeRearmCooldownMs` se queda.** Fuera de toda valla, ni siquiera un
  triaje basta: ese despertar va directo a sesión completa.
- **Los números del amortiguador se quedan.** Acortar el silencio o subir el umbral de racha cambia
  una ventana ciega por otra más pequeña — y las pérdidas del 21-08 ocurrieron dentro de 180 s. El
  eje nunca fue la duración, era la supresión.

## Observabilidad

`DetectionEvent.Sentry.WAKE_TRIAGE` estampa cada triaje con su veredicto y lo que lo movió. Una
captura de campo ya puede distinguir «el sensor estuvo callado porque no pasaba nada» de «el sensor
estaba apagado» — justo lo que el amortiguador volvía inobservable.

## Criterio de éxito

- ✅ Replay de la tormenta del 13-08: cero sesiones FGS y coste por debajo del actual.
- ✅ Replay del Redmi 22-08: el triaje escala con el fix de conducción **sin necesitar la valla**
  (el test omite el `GEOFENCE_EXIT` a propósito — era la hebra de la que colgaba todo).
- ⏳ Campo: un paseo real junto al coche no inunda de sesiones ni de documentos.

## Consumidores auditados

`grep -rn "applyRearmCooldown\|significantMotionMonitor.sync\|DET-SENTRY-COOLDOWN-001"`

| Consumidor | Clasificación |
|---|---|
| `SignificantMotionMonitor.sync` | **cerrado** — ya no hace `return` durante el silencio; arma en modo triaje |
| `SignificantMotionMonitor.onTrigger` | **cerrado** — lee el periodo **en el disparo**, no al armar, así refleja el estado de ahora |
| `CoordinatorDetectionService:1252` (`enterSentry`) | **cubierto por convergencia** — llama a `sync(true)`, que ahora arma siempre |
| `ParkingSafetyNetWorker:146` (tick de 15 min) | **cubierto por convergencia** — idem, mismo `sync` |
| `sentryWakeRearmCooldownMs` / `nextSentryWakeAbortStreak` | **exentos con razón** — el número no cambia de valor, cambia de SIGNIFICADO; KDoc reescrito para que no mienta |
| Vía worker (proceso muerto, `SOURCE_SIG_MOTION`) | **exento** — el periodo vive en memoria del monitor; sin proceso no hay periodo que aplicar |
| `detectionPath` / `armEvidence` | **exentos** — el triaje no confirma nada ni crea camino nuevo; al escalar arma como `Unverified`, igual que antes |
| Strings / copy de usuario | **exento** — sólo `debugNotify` (BuildConfig.DEBUG); ningún texto nuevo para el usuario |
| Dev Catalog / galería | **exento** — sin pantalla, estado MVI ni condición de routing nueva; `compileMockDebug` verde |

## Verificación

- `:composeApp:testProdDebugUnitTest` → **1414 tests, 0 fallos** (13 nuevos).
- `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid` → verdes.
- ⏳ **Sin validar en device.** Esta es la parte que un test no puede dar: que en un paseo real el
  triaje de verdad se coma los disparos y que el coste baje.
