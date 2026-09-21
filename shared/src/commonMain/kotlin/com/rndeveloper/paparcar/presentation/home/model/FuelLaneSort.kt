package com.rndeveloper.paparcar.presentation.home.model

/**
 * Con qué criterio se ordena el carril de gasolineras.
 * [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001]
 *
 * ⛔ **Un orden, no un filtro.** Ninguno de los dos esconde gasolineras: las mismas filas, en otro
 * orden. Un filtro que quitara las caras dejaría al usuario sin saber si es que no hay ninguna cerca
 * o es que se la hemos escondido, y el carril entero está construido sobre no dar veredictos sobre
 * el mundo que no hemos medido.
 */
enum class FuelLaneSort {
    /** La de al lado primero. Es el orden de siempre y el que contesta «voy justo de tiempo». */
    NEAREST,

    /** La más barata primero. Contesta la otra pregunta: «voy justo de dinero». */
    CHEAPEST,
}
