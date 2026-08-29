# SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001 · Decir "ya no está" no le hace nada a la plaza

**Estado:** 🟡 Abierto, sin rama · descubierto durante `SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001`

## Problema

El peek de una plaza ofrece dos botones, "Sigue ahí" y "Ya no está" (`SendSpotSignalUseCase`), que
incrementan `acceptCount` / `rejectCount` en Firestore. Esos contadores alimentan
`communityConfidence()` → `Spot.confidence`.

**Y `Spot.confidence` ya no lo consume nadie.** Hasta este ticket, su único lector era la rampa de
color, que ahora se deriva de la EDAD. O sea: un usuario puede decir "ya no está", el voto se
guarda correctamente, y **la plaza sigue exactamente igual de verde para el siguiente**.

Antes tampoco funcionaba bien — el voto se mezclaba con el reloj dentro del mismo Float, así que
"tres personas dicen que no está" y "la plaza tiene 40 minutos" eran indistinguibles en el color.
Separarlos dejó el problema a la vista en lugar de crearlo.

## Doctrina violada

- **Al quitar un botón, borrar limpio; no plegar su conducta en otro control.** El inverso también
  vale: un botón que no hace nada es peor que no tenerlo. Hoy "Ya no está" es un placebo.
- **Fallo asimétrico: mejor falso negativo que falso positivo.** Un testigo presencial diciendo que
  la plaza está ocupada es la señal más fuerte que existe sobre esa plaza, y la estamos ignorando.

## Señales / datos disponibles

- `acceptCount` / `rejectCount` en `SpotDto`/`SpotEntity`, ya persistidos y sincronizados.
- `communityConfidence()` en `SpotDtoMapper`, ya calculado (ratio suavizado con Laplace,
  `MIN_VOTES_FOR_SIGNAL = 3`).
- `SpotStatus.RETRACTED` + `RETRACTION_GRACE_MS`: **ya existe el mecanismo** para retirar una plaza
  explicando por qué, sin que el marcador se esfume en la cara de quien iba de camino.
- `enRouteCount`: cuánta gente va hacia ella (a más gente en camino, más cara sale la mentira).

## Diseño (sin decidir)

La respuesta probablemente NO es volver a teñir. Un rechazo de la comunidad no es "menos fresca",
es **otra cosa**: la plaza está ocupada. Candidato natural: reutilizar la vía de retractación —
suficientes rechazos → `RETRACTED` con su nota, que es el camino que ya sabe explicarse.

Preguntas abiertas:
- ¿Cuántos rechazos bastan? Uno es manipulable y frecuente por error; tres es lento en una plaza que
  dura minutos. Puede depender de `enRouteCount`.
- ¿Puede el propio autor de la plaza retirarla? Hoy no.
- ¿Y "Sigue ahí"? Confirmar debería al menos **rejuvenecer** la plaza (reiniciar su edad), que es
  literalmente lo que un testigo acaba de observar. Eso conecta con la rampa nueva de forma limpia.
- ¿Cuenta como voto quien nunca estuvo cerca? Hace falta una comprobación de proximidad o se abre
  la puerta al troleo remoto.

## Criterio de éxito

- Decir "Ya no está" cambia lo que ven los demás, de forma observable, o el botón desaparece.
- Decir "Sigue ahí" tiene un efecto distinto de no decir nada.
- Un voto no puede colocar ni retirar una plaza sin proximidad medida.

## Relación con otros tickets

- Lo destapa `SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001`, que sacó el tiempo de `confidence` y dejó
  a los votos sin consumidor.
- Es también el "marcar plaza como ocupada" que ese ticket dejó anotado: **son el mismo problema**,
  visto desde el botón y desde el dato.
