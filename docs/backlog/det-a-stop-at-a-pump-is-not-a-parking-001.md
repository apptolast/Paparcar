# DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001 · una parada en el surtidor no es un aparcamiento

**Estado:** 🔵 Abierto, sin rama · nace de una idea del user el **03-11-2026** mientras revisábamos
las modales de Home ([`HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001`](home-the-first-glance-speaks-for-the-car-001.md))

> *«Cuando detectemos que el usuario ha parado en una gasolinera, la detección debería preguntar si
> ha repostado y entender ese aparcamiento como una parada a posible repostaje. Lo guardaríamos en
> historial y datos con el diferencial de que es una parada a repostaje, no un aparcamiento que se
> deba publicar.»*

## El problema

Hoy la detección sólo sabe decir **aparcamiento**. Paras tres minutos en un surtidor, el coordinador
mide la conducción, confirma, planta un pin — y al irte, ese pin **libera una plaza** que la
comunidad va a ver en el mapa. Una plaza que no existe: nadie puede aparcar en el surtidor de una
gasolinera, y quien conduzca hasta allí hace el viaje en balde.

Es un **falso positivo con consecuencia para un tercero**, que es la clase que la doctrina de
detección prohíbe explícitamente: *fallo asimétrico, mejor un falso negativo que un falso positivo*.
Y además mete una sesión de 3 minutos en el historial y en las cifras como si fuera aparcar.

⚠️ No está medido cuántas veces pasa. **Primer paso del ticket: contarlo**, no arreglarlo —
`parkdiag.log` y las sesiones de `diagnostics/{uid}/sessions` ya guardan la ubicación del pin, así
que se puede cruzar contra la capa de gasolineras sin tocar la app.

## Lo que hay ya, y no hay que volver a construir

| Pieza | Dónde | Qué aporta |
|---|---|---|
| Capa de gasolineras con coordenadas | `NearbyFuelStation` / el repositorio de fuel | **dónde hay un surtidor** — la pregunta «¿he parado en una?» es un radio contra esta lista |
| Zonas privadas, que YA deciden si se publica | `VehicleFenceOwnershipPolicy`, `SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001` | **el precedente exacto**: una sesión que se guarda pero NO publica plaza. Esto es el mismo mecanismo con otro disparador |
| El nudge que pregunta ante la duda | la superficie de detección de Home | **dónde preguntar**, sin inventar una pantalla |
| `detectionPath` / `sessionOutcome` | `UserParking` | **dónde se estampa el diferencial**, sin campo nuevo si cabe en el vocabulario |

## Decisiones que hay que tomar antes de escribir código

1. **¿Qué dispara la pregunta?** Un radio contra la gasolinera más cercana es lo barato, pero una
   gasolinera urbana tiene la acera a 15 m: el radio solo va a confundir «aparqué enfrente» con
   «repostaje». ⛔ Candidato a medir: radio **+ duración** (un repostaje son minutos, no horas).
2. **¿Se pregunta o se deduce?** La doctrina dice que ante la duda se PREGUNTA. Pero una pregunta
   que salta cada vez que pasas cerca de un surtidor es ruido, y `DET-A-STILL-PARKED-ASK-MUST-EARN-ITS-INTERRUPTION-001`
   ya estableció que **una interrupción se gana**. Probable respuesta: se deduce para **no publicar**
   (lado seguro, sin preguntar) y se pregunta sólo para **enriquecer** el historial.
3. **¿Qué pasa si no contesta?** Debe quedar en un estado honesto —parada *posible* de repostaje—,
   no caer a «aparcamiento» por silencio. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
4. **¿Es un tipo de sesión o un atributo?** Si `UserParking` gana un tipo, lo heredan Room, Firestore,
   los serializadores, el espejo del coche compartido y el agregador de cifras. Si es un atributo del
   `detectionPath`, el barrido es menor. **Decidir esto primero**, porque cambia el tamaño del ticket.
5. **¿Y las cifras?** Un repostaje medido es un dato que la app **ya querría tener** —el consumo real
   se calcula con litros y kilómetros— así que esto puede alimentar a `FUEL-*`, no sólo quitar ruido
   del historial. Ahí hay producto, no sólo un arreglo.

## Lo que NO es

- ⛔ No es un botón de «he repostado». Eso ya existiría si el usuario tuviera que decirlo él.
- ⛔ No es gatear nada: el carril free recibe avisos de precio **alrededor** y esto no lo toca.
- ⛔ No publica una plaza nunca, ni siquiera «por si acaso». Ese es el bug que cierra.

## Lado iOS

El resolver cae siempre a Coordinator en iOS, así que el disparador —parada medida + radio a un
surtidor— **vale igual**. Lo que cambia es la pregunta: en iOS no hay nudge residente equivalente,
así que o se pregunta al abrir la app o no se pregunta.
[IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001]
