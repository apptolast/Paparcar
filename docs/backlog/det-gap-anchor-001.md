# DET-GAP-ANCHOR-001 — un ancla nacida ciega (tras hueco GPS) o contradicha por el clúster de egress no merece un pin exacto

**Estado:** 📋 ticket (2026-07-27) · sin rama · diferido — tocarlo requiere el ancla del coordinator (delicado); priorizar tras validar DET-WALK-FLOOR-001 en campo
**Origen:** field-test 2026-07-26, Redmi, sesión `1785092508564` (casa de la hermana).

## Forense
- Viaje medido perfecto (vmax 109 km/h, 35 fixes de conducción). Entre **21:04:47 y 21:06:17 no
  entró ningún fix (hueco de 90 s)** — exactamente la ventana en la que se aparcó.
- El ancla se congeló en el PRIMER fix post-hueco (36.619899,-6.213826, acc 18.4) y el confirm
  `steps+egress` (21:08:29) plantó el pin ahí.
- Ese fix era rancio: el siguiente fix, 3 s después, estaba a **69 m** (salto imposible con 2
  pasos dados), y el clúster estable de los 2 min de egress (36.62046,-6.21355, acc 7–21) coincide
  con el coche real (verificado contra el pin manual del Oppo en el mismo sitio: 36.620468,-6.213442).
- Error final del pin: **~72 m**. El usuario lo percibió como "no muy preciso".

## Invariante propuesto
*Un ancla solo merece pin EXACTO si su fix es coherente con lo que se midió justo después.*
Dos señales de incoherencia, ambas baratas y ya presentes en el stream:
1. **Nacida ciega**: el fix del ancla es el primero tras un hueco > N s desde el último fix en
   movimiento — el momento de aparcar cayó dentro de la ventana ciega; la posición real del coche
   nunca se muestreó.
2. **Contradicha por el clúster**: los fixes durante los primeros pasos de egress forman un clúster
   a una distancia del ancla que esos pasos no pueden explicar a pie (mismo lenguaje del
   presupuesto: pasos × zancada).

Respuesta (en línea con la doctrina de duda ACOTADA de DET-FROZEN-COUNTER-001):
- re-anclar al inicio del clúster de egress cuando el clúster es consistente (caso Redmi: habría
  clavado el pin), o
- degradar a ZONA con radio = discrepancia ancla↔clúster (o cota del hueco: distancia último-fix-
  en-movimiento → ancla), nunca un punto exacto mentiroso.

## Alcance / riesgos
- Toca `CoordinatorParkingDetector` (captura/lock del ancla, ANCHOR-LOCK / DET-ANCHOR-FREEZE) —
  zona de alto riesgo de regresión; exige fixtures de replay (la sesión `1785092508564` es el
  fixture natural) y revisar interacción con DET-CONFIRM-FRESHNESS-001.
- NO tocar la mecánica del presupuesto ni el honest-close (eso es DET-WALK-FLOOR-001).
