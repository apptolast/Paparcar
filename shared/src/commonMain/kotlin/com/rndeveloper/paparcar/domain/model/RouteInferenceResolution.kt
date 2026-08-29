package com.rndeveloper.paparcar.domain.model

/**
 * The user's answer to "did you drive this way?" for a route that carries road-INFERRED stretches
 * (data holes reconstructed along the streets, [ROUTE-GAP-HONEST-001]). Null on the session means
 * the question is still PENDING — the inferred stretches render dimmed and the detail screen asks.
 *
 * - [CONFIRMED]: the user vouched for the reconstruction → drawn like the measured line.
 * - [REJECTED]: the user says the car did not go that way → the inferred stretches are never drawn
 *   again (the line cuts at the holes); the measured stretches stay.
 */
enum class RouteInferenceResolution { CONFIRMED, REJECTED }
