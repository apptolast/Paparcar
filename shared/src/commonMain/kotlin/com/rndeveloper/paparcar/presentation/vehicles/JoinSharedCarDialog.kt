package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.sharedcar.SharedCarInviteCode

/**
 * Teclear los seis caracteres que alguien te ha dictado. [VEH-SHARED-001]
 *
 * Un diálogo y no una pantalla: escribir un código no es un destino, y así el garaje vacío ofrece
 * sus dos salidas —registrar un coche o unirse a uno— sin salir de él.
 *
 * ⛔ El rechazo se enseña DEBAJO del campo, no en un snackbar: el usuario sigue mirando lo que
 * acaba de escribir, y «ese código ha caducado» es una instrucción para reescribir, no un aviso
 * que se va solo.
 */
@Immutable
data class JoinSharedCarDialog(
    val code: String = "",
    val working: Boolean = false,
    val error: PaparcarError.SharedCar? = null,
) {
    /** Seis caracteres del alfabeto: la única forma que merece un viaje a la red. */
    val canSubmit: Boolean get() = !working && SharedCarInviteCode.normalize(code) != null
}
