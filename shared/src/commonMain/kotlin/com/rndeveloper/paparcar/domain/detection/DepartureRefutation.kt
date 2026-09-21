package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.physics.SessionOutcome

/**
 * ¿Queda **refutada** la salida que este viaje llegó a PRESENCIAR?
 * [DET-A-REFUSED-TRIP-GIVES-THE-CAR-BACK-001]
 *
 * Una salida presenciada no es una deducción: publicó la plaza liberada y **cerró la sesión**, o
 * sea entregó el coche. Deshacerla es devolverlo — eso lo hace
 * [com.rndeveloper.paparcar.domain.usecase.parking.RestoreRefutedDeparture], que nació con
 * [DET-A-TRAIN-IS-NOT-A-CAR-001] para el AVE que confirmó salida desde el andén de Atocha.
 *
 * Esta función contesta **cuándo** hay que llamarla, y hay dos razones — una que mide la app y otra
 * que dice la persona:
 *
 *  1. **La carretera era imposible** ([RoadPlausibility.IMPOSSIBLE]). Ningún coche hace eso; era un
 *    tren. Es la razón con la que nació el `restore`.
 *  2. **El conductor dijo que no era su coche** ([SessionOutcome.StoppedByUser]). Pulsó «No voy en
 *    mi coche» — el amigo que le recogió junto a su coche, el bus, ir de copiloto.
 *
 * ⛔ **Y la segunda vale exactamente lo mismo que la primera.** El techo de plausibilidad es una
 * inferencia nuestra sobre lo que el móvil midió; el usuario es la fuente primaria. Tratar su «no»
 * como algo menos que una refutación —que es lo que pasaba: el botón limpiaba el estado de
 * detección y dejaba el coche entregado— es preferir nuestra conjetura a su testimonio, y encima en
 * el único caso en que estamos seguros de estar equivocados.
 *
 * El coste de equivocarse aquí es asimétrico en la dirección buena: devolver un coche que sí había
 * salido deja una sesión de más, que el usuario cierra con un gesto. **No** devolverlo deja al
 * usuario sin su aparcamiento y a un desconocido conduciendo hacia una plaza que nadie liberó.
 *
 * ⛔ No cubre la salida **deducida**: ésa no entrega nada —sesión y geocerca siguen en su sitio— y
 * su deshacer es retirar la plaza provisional (`RetractDeducedDeparture`), que ya corre en el mismo
 * epílogo y **sí** se disparaba con el botón. Son dos deshaceres porque son dos entregas distintas.
 */
fun witnessedDepartureIsRefuted(
    roadPlausibility: RoadPlausibility,
    outcome: SessionOutcome,
): Boolean = roadPlausibility == RoadPlausibility.IMPOSSIBLE || outcome == SessionOutcome.StoppedByUser
