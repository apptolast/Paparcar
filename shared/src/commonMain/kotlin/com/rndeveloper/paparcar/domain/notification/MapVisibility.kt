package com.rndeveloper.paparcar.domain.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ¿Está el mapa de Home delante de los ojos del usuario?
 * [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
 *
 * Una sola señal, con un solo dueño. La escribe el corchete de ciclo de vida del propio mapa
 * (`HomeIntent.SetMapForeground`, que ya existía) y la leen los dos que la necesitan: Home, para
 * decidir si puede pedir la ubicación, y el reparto de avisos, para elegir canal.
 *
 * ⛔ **No es un segundo `mapForeground`.** Vivía como `MutableStateFlow` privado dentro de
 * `HomeViewModel`, y ahí no podía verla quien reparte los avisos, que es de ámbito de app. Copiarla
 * habría dejado dos verdades sobre una sola pregunta —y la segunda se equivoca el día que alguien
 * añade una tercera pantalla—, así que la que había se MUEVE aquí en vez de duplicarse.
 */
class MapVisibility {
    private val _onScreen = MutableStateFlow(false)
    val onScreen: StateFlow<Boolean> = _onScreen.asStateFlow()

    fun set(visible: Boolean) {
        _onScreen.value = visible
    }
}
