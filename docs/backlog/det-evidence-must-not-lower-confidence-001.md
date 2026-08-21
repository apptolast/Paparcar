# DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001 · Añadir evidencia no puede bajar la puntuación

**Estado:** 🔴 Abierto, **sin código** · medido el 2026-08-20 con un spike descartado ·
detectado desde [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001](det-motorway-trip-judged-bicycle-001.md)

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
