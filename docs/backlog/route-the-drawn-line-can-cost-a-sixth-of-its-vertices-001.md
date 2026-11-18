# ROUTE-THE-DRAWN-LINE-CAN-COST-A-SIXTH-OF-ITS-VERTICES-001 · simplificar solo al dibujar, y el guard de velocidad que `admit` no tiene

**Estado:** 🔵 Abierto · sin rama · follow-up medido de
[ROUTE-A-SNAPPED-LINE-MUST-NOT-INVENT-GROUND-001](route-cleaning-pipeline-001.md)

Los dos quedaron **deliberadamente fuera** de aquel ticket, que arregló lo único demostrablemente
roto. Se escriben aquí con su medida para que nadie los re-descubra desde cero.

## 1 · Simplificación solo para RENDER (Douglas-Peucker)

La traza almacenada es geometría de calle de OSM, y sus vértices son tan densos como los nodos de
la vía: medido sobre 47 trayectos reales, **11.598 vértices que se dibujan igual con 1.918**
(ε ≈ 6 m, **−83 %**). No es ruido ni un defecto de dato — es densidad legítima de la cartografía.

⛔ **Solo para dibujar, jamás para medir.** La distancia se calcula sobre la línea completa; un
Douglas-Peucker que alimentara el kilometraje recortaría curvas y restaría km. Va en presentación,
nunca en lo que se persiste.

Valor: rendimiento de mapa y de la lista de historial. Riesgo: bajo, si se respeta la frase de
arriba. No se hizo porque no cambia lo que el user ve mal, solo lo que cuesta pintarlo.

## 2 · Guard de velocidad/aceleración implícita en `DrivingRoute.admit`

`DrivingRoute.admit` (`domain/detection/DrivingRoute.kt:58-68`) decide con **dos** pruebas:
accuracy > 100 m → REJECT, y desplazamiento < 12 m → DECIMATE. **No mira la velocidad implícita
entre fixes consecutivos**, así que un fix disparado por multipath con accuracy aceptable entra sin
objeción y dibuja el salto que cruza manzanas.

⚠️ **Es un hueco del código, no un defecto observado**: en 2.334 fixes crudos de 6 sesiones reales
hubo **cero** velocidades implícitas por encima de 250 km/h (máx real 110,6 km/h) y **cero** pares
con dt = 0. Por eso es seguro barato y no urgencia.

⛔ Si se implementa, el umbral se compara contra la velocidad **implícita** (distancia/tiempo entre
fixes), nunca contra el campo `speed` declarado — que ya se sabe que miente en reposo
([DET-THE-SPEED-FIELD-LIES-LOW]). Y la decisión es del evaluador puro, con su test falsificado.

## Criterio de éxito
Cada uno con su testigo: el de render, que la distancia NO cambie al simplificar; el del guard, un
fix teletransportado que hoy entra y después no.
