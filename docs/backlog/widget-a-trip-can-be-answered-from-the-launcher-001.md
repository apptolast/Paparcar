# WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001 · El widget en ruta también tiene un verbo

**Estado:** 🔵 En revisión · rama `feature/WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001` ·
cierra la **issue #53**

## Problema

Pedido por el product owner (#53): *«Incluir botones en widget para cuando está el vehículo en ruta,
por si el usuario quiere revertir la detección o realizar alguna otra interacción.»*

El estado `Driving` del widget no tenía ninguno, y con un argumento escrito: *«este estado no tiene
sesión, así que los dos verbos de `Parked` —navegar, ver ficha— no apuntan a nada»*.

## Diseño

El argumento sigue siendo cierto **para esos dos**: sin pin no hay a dónde navegar ni ficha que
abrir. Lo que no se seguía de ahí es que no hubiera NINGÚN verbo. **El que falta no mira al pasado,
mira al presente: «ya he aparcado»** — justo lo que el conductor sabe y la app todavía no.

- Mismo botón callado que los de `Parked`: icono sin relleno, a la derecha, `CircleIconButton`.
- **Mismo destino que la notificación** de «¿sigues aparcado?» (`EXTRA_START_ADD_PARKING`), no un
  camino nuevo: el widget ofrece lo que la app ya sabe hacer.
- `fromDetection = false`, como la notificación: este pin lo pone una persona, no lo nomina una
  detección, y su procedencia tiene que decirlo. [DET-NUDGE-PIN-PROVENANCE-001]
- String nuevo en los 9 locales de `app/src/main/res` (el widget usa recursos Android, no Compose
  Resources — y ahí el apóstrofo SÍ se escapa).

## Lo que NO he hecho, y por qué quiero que lo decidas

**«Revertir la detección» durante el viaje.** Es la otra mitad de la issue y no la he tocado porque
no es un botón, es una decisión:

1. Durante un viaje **no hay nada que revertir todavía** — el revert existe sobre un aparcamiento ya
   guardado. Lo que tendría sentido aquí es **«no estoy conduciendo»**, que MATA la detección de este
   viaje.
2. Y eso es un **comando en segundo plano**, no abrir la app. Los tres botones del widget hoy son
   `actionStartActivity`: abren la app en un sitio. Un comando exige un `ActionCallback` que hable
   con el servicio, y equivocarse ahí es perder una detección de verdad.
3. Encima ya existe un verbo parecido en la notificación (`notif_action_stop_detection`), así que
   hay que decidir si es el mismo o uno distinto — «paro la detección» no es «este viaje no era un
   viaje».

## Criterio de éxito

- Conduciendo, el widget ofrece «Marcar aparcamiento» y abre el marcado manual.
- El pin que sale de ahí se registra como manual, no como detectado.
