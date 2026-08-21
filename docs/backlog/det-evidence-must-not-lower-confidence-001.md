# DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001 · Añadir evidencia no puede bajar la puntuación

**Estado:** ✅ **DONE** — pasos 1 y 2 en master (squash 21-08, sin pushear; rama + worktree
borrados) · 1317 tests verdes · ⏳ **campo**: el paso 2 reactiva la vía `vehicleExit + ventana +
egreso`, dormida hasta ahora, o sea más superficie de auto-pin — eso solo lo valida la carretera
· detectado desde [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001](det-motorway-trip-judged-bicycle-001.md)

## Problema

`CalculateParkingConfidenceUseCase` tiene dos carriles y el rápido **retorna antes** de mirar el
lento. Consecuencia: la MISMA parada, madurada 5 minutos, puntúa

| | sin `AR IN_VEHICLE EXIT` | con `AR IN_VEHICLE EXIT` |
|---|---|---|
| puntuación | **0,90 → High** | **0,65 → Medium** |

La señal más fuerte que tenemos de "me he bajado del coche" **baja** la confianza. Y como High es la
única puerta a la fase CANDIDATO, la consecuencia real es que **la fase candidato es inalcanzable en
cuanto AR entrega un EXIT**, y con ella la vía de confirmación `windowElapsed && hadVehicleExit`
(`vehicleExitObservationWindowMs`): `hadVehicleExit` se fotografía al ENTRAR en candidato, y para
entrar hace falta un High que el propio EXIT bloquea. Esa vía está muerta en campo.

Contrastado con telemetría (20-08 y 19-08):

| Sesión | `ACTIVITY_TRANSITION` | Candidatos |
|---|---|---|
| Redmi autovía 20-08 | 2 | **0** |
| Oppo viajes 1 y 2 (confirmadas) | 1 / 0 | **0** — confirmaron por el carril rápido |
| Bicis reales 19-08 (×2) | 0 | **5 cada una** |

## Doctrina violada

*Fallo asimétrico* dice qué hacer ante la duda, no permite que una evidencia RESTE. Un sistema donde
añadir una señal empeora el veredicto no se puede razonar: cada guard nuevo se calibra contra un
escalón que a lo mejor ni existe en el caso que le importa.

## Lo que NO funciona (medido, no supuesto)

El arreglo obvio —que el carril rápido sea un **suelo** (`max(rápido, lento)`) en vez de un techo—
**cuesta una plaza real**. Spike aplicado sobre la rama de DET-MOTORWAY-…-001 y pasado por las 12
fixtures de campo:

```
redmi_late_exit_home_001 (DET-NODRIVE-ZONE-001, campo 27-07):
  antes: candidatos=[]                                    → 1 zona guardada
                                                            confirmed_unattended_zone_no_drive_egress
  spike: candidatos=[OPENED, DISCARDED, OPENED, DISCARDED, OPENED]
                                                          → 0 guardados, 1 nudge
                                                            aborted_unattended_no_drive
```

**Mecanismo:** al levantar el techo, la sesión entra en el bucle de candidatos, y **cada `Rejected`
pone `stepCount = 0`** — borra los pasos de egreso que YA habían ocurrido. El veredicto desatendido
que venía después leía esos pasos para justificar la zona; sin ellos degrada a `Ask`. Es la misma
patología que ya documentó DET-HUMAN-POWERED-EARLY-CLOSE-001 en otra traza ("un bucle incapaz de
concluir otra cosa"), y aquí se paga con un coche perdido en vez de con 19 minutos de FGS.

Las otras 11 fixtures no cambiaron de veredicto.

## Diseño (esbozo)

El techo del carril rápido es **load-bearing por accidente**: está tapando que el descarte de
candidato es destructivo. Así que el orden correcto es al revés de como parece:

1. **Primero, que descartar un candidato deje de borrar evidencia ya medida.** `stepCount` son pasos
   que ocurrieron; un candidato que expira dice "esto todavía no confirma", no "esos pasos no
   pasaron". Probablemente el contador de egreso debe sobrevivir al descarte y solo limpiarse con
   conducción medida, como ya hacen `walkFixesSinceDriving` y compañía.
2. **Después**, y solo después, el carril rápido pasa a ser suelo.
3. Re-medir las 12 fixtures entre paso y paso.

⚠️ El paso 2 reactiva una vía de auto-confirmación dormida (`vehicleExit + ventana + egreso`), o sea
más superficie de auto-pin. Necesita su propio viaje de validación, nunca ir de rebote en otro
ticket.

## Criterio de éxito

- Ninguna traza de campo cambia de veredicto salvo las que el ticket quiere cambiar.
- La misma parada con más evidencia nunca puntúa menos.
- Test que fije la propiedad: para cualquier `signals`, añadir `activityExit = true` no puede bajar
  el `ParkingConfidence` resultante.

---

## Paso 1 · HECHO (sin commitear)

Un veredicto ya no destruye una medición. `stepCount` deja de ponerse a 0 al descartar un candidato;
en su lugar se mueve una **línea de frescura** (`stepsAtLastDiscard`):

- `stepCount` = la verdad completa. La leen el candado del ancla, los techos de alcance a pie y el
  veredicto desatendido. Solo la **conducción medida** lo pone a 0, igual que `walkFixesSinceDriving`.
- `freshStepCount` = lo que aún puede CONFIRMAR. Lo lee solo el evaluador de confirmación. Un
  candidato nuevo debe ganarse `minStepsToConfirm` pasos **por encima** de la marca.

Mismo contador, dos preguntas: *"¿esto puede confirmar ahora?"* y *"¿el usuario se bajó del coche?"*.
Confundirlas es lo que dejaba que un descarte borrara un egreso real.

**Regresión verificada ROJA sin el fix**:
`should_keep_the_egress_steps_on_the_record_when_a_candidate_is_discarded` — forma del campo 27-07
(sin conducción corroborada), descarte primero y caminata después: 12 pasos + 2 posteriores. Con el
borrado el veredicto ve 2 → nudge sin guardar; sin él ve 14 → zona guardada. 1313 tests verdes.

> ⚠️ Montar esa prueba enseñó algo: **el bug del paso 2 impedía escribirla**. Con un `AR EXIT` el
> scorer topa en Medium y no hay candidato que descartar, así que la prueba tiene que provocar la
> señal vehicular por la vía cruda (2 fixes creíbles) en vez de por AR. La mitad "preservar" solo se
> vuelve observable en campo cuando entre el paso 2 — y ahí ya está medida (red→green).

## Análisis de `CalculateParkingConfidenceUseCase` (para el paso 2)

Con los números reales de config:

| | máximo alcanzable | nivel |
|---|---|---|
| carril rápido (`activityExit` + ≥30 s) | 0,50 + 0,15 = **0,65** | Medium |
| carril lento (parada ≥5 min) | 0,70 + 0,05 + 0,05 = **0,80** | High |

Cuatro problemas, no uno:

1. **La inversión.** La misma parada madura: 0,80 sin `AR EXIT`, 0,65 con él. La evidencia resta 0,15.
2. **High es frágil.** Umbral 0,75 y el tramo de 5 min da 0,70: High **exige los dos bonus de 0,05**.
   Sin el de precisión (acc ≥ 15 m: lo normal aparcando entre edificios) o sin el de velocidad (deriva
   GPS ≥ 0,3 m/s en interiores) se queda en 0,75 justo, y sin ninguno en 0,70 → **nunca candidato**.
   Que abrir la fase candidato dependa de un bonus de 0,05 explica por qué apenas aparece en las trazas.
3. **El tiempo se tira.** En el carril rápido una parada de 30 s y una de 3 horas puntúan igual (0,65):
   toda la información de reposo se descarta en cuanto AR habla.
4. **El techo Medium es aritmético, no estructural.** 0,50 + 0,15 < 0,75 por casualidad de config, sin
   ningún `require` que lo garantice. Un ajuste de pesos convertiría el carril rápido en
   auto-confirmación silenciosa sin que nada avise.

**La raíz de los cuatro es la misma:** `activityExit` es un **selector de rama** en vez de una
**evidencia**. Por eso el paso 2 no debería ser un `max(rápido, lento)` —eso es un parche sobre las
dos ramas— sino borrar la bifurcación:

```
score = tramoDeReposo(stoppedDurationMs)   // 30-90 s: 0,50 · 3 min: 0,45+ · 5 min: 0,70
      + bonusVelocidad + bonusPrecisión
      + bonusSalidaAR                       // 0,15, un sumando más
```

- **Monotonía por construcción**: todos los términos ≥ 0, así que añadir evidencia nunca puede bajar
  el nivel. La clase entera de bug desaparece, no solo este caso.
- Se conserva lo que el carril rápido hacía bien: parada breve + salida de AR = 0,65 → Medium →
  prompt temprano en un drop-off. Mismo número, sin rama.
- `require` explícitos sobre los invariantes aritméticos (que el tramo breve + bonus AR no alcance High).
- Test de propiedad: para cualquier `signals`, poner `activityExit = true` no puede bajar el nivel.
- **Aparte y a medir**: si el tramo de 5 minutos debería llegar a High por sí solo (0,75) en vez de
  depender de dos bonus de 0,05. Cambia comportamiento → las 12 fixtures antes y después.


## Paso 2 · HECHO (sin commitear)

**Se borra la bifurcación.** El scorer pasa de dos caminos mutuamente excluyentes a **una suma**:

```
gate  = si hay AR EXIT → fastPathMinStoppedMs, si no → slowPathGateMs   (la evidencia ACORTA la espera)
score = tramoDeReposo(stoppedDurationMs)
      + speedBonus + accuracyBonus + activityExitBonus                  (cada término ≥ 0)
```

La propiedad violada queda garantizada **por la forma de la función**, no por un `max` defensivo:
todos los sumandos son ≥ 0, así que añadir una señal nunca puede bajar el resultado. Y acortar una
puerta también es monótono: más evidencia hace que la puntuación llegue antes, nunca después.

**Lo que NO cambia.** Low y Medium los trata igual el coordinator, así que solo dos fronteras
llevan comportamiento: NotYet (silencio) y High (fase candidato).
- **Silencio idéntico**: la regla de la puerta *es* la vieja bifutcación rápido/lento escrita como
  umbral.
- **High gana exactamente un caso**, el del ticket: una parada madura de 5 min que además tiene
  `AR EXIT` llega a High (0,70 + 0,15 = 0,85) en vez de quedarse capada en 0,65 para siempre. Y lo
  hace **sin depender de los dos bonus de 0,05**, que era el problema nº 2 del análisis.
- Una parada breve o el tramo de 3 min **siguen sin poder llegar a High** pase lo que pase.

**Config:** `fastPathBaseScore`/`fastPathSpeedBonus` (nombres de una rama que ya no existe) pasan a
ser `briefRestScore` (0,35) y `activityExitBonus` (0,15). Y cuatro `require` nuevos convierten los
techos en invariantes explícitos — antes se sostenían por casualidad aritmética y un ajuste de pesos
habría convertido el carril breve en auto-confirmación silenciosa sin que nada avisara.

## Re-medición (el criterio de éxito del ticket)

**1317 tests verdes, las 12 fixtures de campo intactas** — incluida
`redmi_late_exit_home_001`, que es la que el spike ingenuo rompía. Esa es exactamente la deuda que
pagaba el paso 1: con el borrado de pasos todavía dentro, este mismo cambio perdía un coche; con el
paso 1 delante, no cambia ni un veredicto.

**Test de propiedad** (`adding an AR vehicle exit can never lower the confidence`): barre 6
duraciones × 2 velocidades × 2 precisiones y exige que el nivel con `activityExit` sea ≥ el nivel
sin él. Fija la CLASE de bug, no el caso.

## Pendiente

- ⏳ **Viaje de campo.** El paso 2 reactiva la vía `vehicleExit + ventana + egreso`, dormida hasta
  hoy: más superficie de auto-pin. Los tests no validan eso, solo la carretera.
- 🔍 Queda abierta la pregunta del análisis: si el tramo de 5 min debería llegar a High por sí solo
  (0,75) sin necesitar bonus. Ya no es urgente — con `AR EXIT` el caso frecuente llega a 0,85.
