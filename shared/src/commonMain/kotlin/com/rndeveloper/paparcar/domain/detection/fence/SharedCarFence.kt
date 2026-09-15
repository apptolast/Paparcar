package com.rndeveloper.paparcar.domain.detection.fence

/**
 * La valla de un coche que conducen varios. [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
 *
 * Hasta ahora una valla SIEMPRE era de una sesión —`geofenceId == sessionId`— y tres sitios del
 * sistema deciden qué hacer con una valla preguntando «¿qué sesión la respalda?». Una valla
 * compartida no tiene ninguna **por construcción**: vigila el aparcamiento que hizo OTRA persona,
 * del que este móvil no tiene sesión y no debe tenerla. Sin un espacio de nombres propio, los dos
 * barridos de huérfanas la borrarían en su primer disparo y el intake la leería como una salida.
 *
 * ⛔ **Es una valla de UBICACIÓN, no de liberación.** Que el coche esté vallado en mi móvil no
 * prueba que lo conduzca yo: si heredara la autoridad de una valla normal, pasar andando por la
 * calle donde Marta lo dejó armaría detección y podría publicar su plaza — un falso positivo con
 * el nombre de otra persona, que es justo lo que la doctrina de fallo asimétrico prohíbe. Quien
 * libera la plaza sigue siendo quien MIDIÓ la conducción.
 *
 * El prefijo es el contrato, y vive aquí solo: quien necesite saber si una valla es del grupo
 * pregunta a [isSharedFenceId], nunca compara cadenas por su cuenta.
 */
object SharedCarFence {

    private const val PREFIX = "shared:"

    /** La valla del grupo de este coche. Una por coche: el grupo tiene un coche y el coche está en
     *  un sitio, así que no hay nada que numerar. */
    fun idFor(carId: String): String = PREFIX + carId

    fun isSharedFenceId(geofenceId: String): Boolean = geofenceId.startsWith(PREFIX)

    /** El coche al que pertenece la valla, o null si no es una valla de grupo. */
    fun carIdOf(geofenceId: String): String? =
        geofenceId.removePrefix(PREFIX).takeIf { it != geofenceId && it.isNotEmpty() }
}
