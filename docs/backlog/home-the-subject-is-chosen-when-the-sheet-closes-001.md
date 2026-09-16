# HOME-THE-SUBJECT-IS-CHOSEN-WHEN-THE-SHEET-CLOSES-001 · El sujeto del peek se elige al cerrar la hoja

**Estado:** ✅ Done — en master

Corrige [HOME-AN-EMPTY-LANE-HANDS-THE-HEADER-BACK-TO-THE-CAR-001] (`c0b9c11a`), que dejó la regla
correcta pero evaluada en el momento equivocado.

## Problema
`c0b9c11a` decidía **cada frame**, contra la cifra viva del carril. Con la hoja cerrada, esa cifra se
mueve por dos motivos muy distintos y el código no los distinguía:

1. **El mundo cambia solo** — una plaza se publica o caduca cerca de tu coche aparcado.
2. **Tú paneas el mapa** — y entonces el carril re-pregunta: las plazas re-centran la consulta al
   superar **300 m** de paneo (`HomeSpotsController.maybeRecenterOnPan`, umbral
   `SPOT_CAMERA_PAN_THRESHOLD_METERS`), y parkings/gasolineras re-preguntan por zona
   ([NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]).

El segundo caso es el que rompía: vas siguiendo el carril por el mapa, paneas a una manzana sin nada
y **te aparece el coche debajo del dedo**. El sujeto cambia sin que tú lo hayas pedido, justo mientras
estás mirando otra cosa. Y el primero, aunque lento (los sucesos son de un solo sentido y van
separados por minutos, no hay estrobo), tampoco tiene por qué mover el sujeto del estado de reposo.

## Doctrina
*Plegado, el sujeto del peek solo cambia por un GESTO.* La cifra sigue a la cámara; quién habla, no.

Un `0` al que has paneado **es una respuesta** sobre el sitio que estás mirando —la misma lógica de
[A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001]—. Un `0` con el que te quedas en reposo, sin haber
preguntado nada, es aire muerto: eso es lo que arreglaba `c0b9c11a` y sigue arreglado.

## Diseño
Dos cantos, los dos del usuario, y nada entre medias:

| Gesto | Sujeto |
|---|---|
| abre Browse | el carril (has pedido su lista) |
| cierra con el carril con resultados | el carril se la queda |
| cierra con el carril vacío + coche aparcado | el coche |
| *(nada: paneo, plaza que aparece o caduca)* | **no cambia** |

- `laneKeepsHeaderOnCollapse(laneCount, laneLoading, hasParkedCar)` — el mismo predicado de
  `c0b9c11a` (preguntando ≠ vacío; sin coche no se cede), pero preguntado **una vez por cierre**.
- `nextLaneHeaderLatch(latched, browseOpen, laneKeepsHeader)` — el canto. Abrir siempre da la
  cabecera al carril; cerrar la re-decide; **un latch ya en `false` no se re-pregunta al cerrar**, o
  un carril que se rellenó solo se la llevaría sin que el usuario abriera nada.
- `browseHeaderFollowsLane` desaparece: `followsLane` vuelve a ser `showZoneHeader || latched`. La
  cifra ya no entra en la decisión de sujeto, solo en la de contenido.
- En `HomeScreen`, el `snapshotFlow` mira **solo** `browseOpen`. Como `snapshotFlow` emite al cambiar,
  las idas y venidas del carril no llegan al `collect` ni una vez.

## Y la excepción que sobraba — [HOME-AN-UNPARKED-CAR-STILL-HAS-SOMETHING-TO-SAY-001]
`c0b9c11a` no cedía la cabecera «si no hay coche APARCADO». Pero un coche sin aparcar no está mudo:
dice de quién es, que no está marcado, y qué hacer. Callarlo dejaba el estado de reposo de Home en un
contador que el propio cierre acababa de juzgar vacío, y sin nombrar la única acción que siempre está
disponible.

Así que el predicado deja de preguntar por la SESIÓN y pregunta por el COCHE
(`hasCarToSpeakFor`), y `BrowsePeek` gana una cuarta rama entre el coche aparcado y el carril:

- lead: el glifo del vehículo, como la rama aparcada.
- eyebrow: `home_peek_vehicle_status(nombre, home_vehicle_chip_unmarked)` → «MI SEAT · SIN MARCAR».
  Solo el NOMBRE lleva color de identidad; el estado, neutro. [UI-COLOR-DOCTRINE-001]
- title: dónde estás —el mismo resolver de cámara que la cabecera del carril—, que es el sitio que
  marcarías. Sin geocodificación aún, cae a «Marcar aparcamiento»
  ([UI-LOCATION-FALLBACK-SPEAKS-HUMAN-001]: se nombra la cosa, nunca la coordenada).
- subtitle: **hint de activación**, `home_browse_hint_swipe_mark_parking` → «Desliza ↑ para marcar tu
  aparcamiento».

⛔ **Sin botón, y la copy lo DICE.** Es la misma forma que el carril de plazas ya usa en cero
(«Desliza ↑ para avisar de una plaza»): un hint, no un imperativo con pinta de pulsable. El tap del
peek handle es de la hoja, así que un título «Marcar aparcamiento» prometería una pulsación que hace
otra cosa. El hint nombra el gesto que sí funciona, y el control vive un arrastre más arriba.

**Un string nuevo, en los 9 locales**, calcado del hint de plazas y con el vocabulario de cada lengua
(⛔ el de plazas NO vale: eso es de la comunidad, esto es tuyo [COPY-SPOT-IS-NOT-A-PARKING-001]).
Las otras dos keys ya existían; `home_vehicle_chip_mark_parking` llevaba *importada sin un solo
lector* en `HomeParkingRow.kt` y vuelve a tener quien la lea (como fallback del título).

**Qué coche**: el PRIMERO de `vehiclesRowOrder` — viaje vivo, luego aparcados por recencia, luego sin
aparcar por rango de vigilancia. ⛔ No un `firstOrNull { isActive }` nuevo: cuál es «el coche del
usuario» ya era doctrina ([UI-PREFERRED-SESSION-RECENCY-001], [CHIP-DRIVING-001]), y un segundo
criterio aquí dejaría que la cabecera, la tira de chips y el stepper ‹ / › nombraran tres coches
distintos. Se lee de `steppableVehicleIds`, que el slice ya proyecta desde ese mismo orden.

⛔ Vocabulario: lo que se marca es **tu aparcamiento**, no una plaza. [COPY-SPOT-IS-NOT-A-PARKING-001]

La única exención que queda es **no tener ningún vehículo**: ahí no hay a quién cederla y el carril se
queda la cabecera, que es exactamente lo que ya enseña el peek sin latch.

## Lo que se acepta a sabiendas
Tras panear a una zona sin nada, el peek en reposo puede quedarse en «PARKINGS · 0» hasta que abras y
cierres. Es correcto: ese cero habla del sitio que TÚ elegiste mirar. Y al revés — panear a una zona
llena tampoco le quita la cabecera al coche: las plazas ya se ven en el mapa, que es su canal.

## Criterio de éxito
- Cerrar sobre carril vacío + coche aparcado → coche.
- Cerrar sobre carril con resultados → carril; panear hasta vaciarlo → **sigue el carril, en 0**.
- Abrir y cerrar otra vez ahí → coche.
- Cerrar sobre carril vacío **sin sesión activa** pero con coche → «MI SEAT · SIN MARCAR» sobre el
  sitio donde estás, con «Desliza ↑ para marcar tu aparcamiento» debajo, y **pulsar sigue abriendo la
  hoja**, no el modo de marcar. Sin ningún vehículo → el carril se queda la cabecera.
- Viaje vivo, o hoja abierta → sin cambios.
- Testigo: `BrowseHeaderSubjectTest` (11 tests) sobre las dos funciones puras. ⛔ Sin test para «el
  paneo no mueve el sujeto»: eso no es una rama de estas funciones, es que la cifra no llega al
  `snapshotFlow`. Un test que lo fingiera llamando dos veces con el mismo valor sería una tautología
  con nombre de testigo. [project_test_a_green_suite_must_prove_it_looked_001]

## Consumidores auditados
- `HomeScreen.kt` — único escritor del latch; antes, un efecto one-shot + captura de frame.
- `BrowsePeek.kt` — `followsLane` vuelve a ser trivial; las dos reglas, puras, debajo.
- `HomePeekHandle.kt` — transporta el flag y ahora resuelve además el coche sin aparcar, con el mismo
  `firstOrNull { it.isActive }` que ya usan los CTA de arranque en frío (`coldStartVehicleId`,
  `HomeSheetContent.kt:129`) y el `activeVehicle` del peek de plaza: un solo criterio de «el coche del
  que habla Home». `HomeBottomSheet.kt` solo transporta. Cerrados.
- `HomeParkingRow.kt` — tenía `home_vehicle_chip_mark_parking` importada y sin lector; no se toca
  (su chip dice «Sin marcar» a propósito), pero la key deja de estar huérfana.
- `StateGalleryScreen.kt` — la variante «carril vacío → coche» pasaba `laneHeaderLatched = true`, que
  bajo la regla nueva pinta el CARRIL: corregida a `false` y añadida la del paneo que vacía.

## Estado final
Dos funciones puras en `BrowsePeek.kt` — `laneKeepsHeaderOnCollapse` (¿merece el carril quedársela?)
y `nextLaneHeaderLatch` (¿cuándo se pregunta?) — y un solo escritor del latch en `HomeScreen`, sobre
un `snapshotFlow` que **solo mira el canto abrir/cerrar**.

Verificado: `./gradlew testAll` verde · `BrowseHeaderSubjectTest` **11 tests, 0 fallos** ·
`LocaleParityGuardrailTest` **5 tests, 0 fallos** (el string nuevo, en los 9 locales) ·
`:app:compileProdDebugKotlin` y `:app:assembleMockDebug` verdes.

Galería de estados: **4 variantes** — cierre sobre carril vacío → coche · el paneo que lo vacía
(sigue el carril, en 0) · coche SIN aparcar · sin ningún vehículo.

⏳ **Sin ver en mano.** Lo que hay que mirar: (a) que el sujeto NO se mueva paneando con la hoja
cerrada, en ninguno de los tres carriles; (b) que la cara de «sin marcar» se lea como un aviso y no
como un botón — si invita a pulsarla, el siguiente paso no es añadirle un CTA (se comería el gesto de
la hoja) sino bajarle peso al hint.

### Descartado por el camino
Una versión **pegajosa** en la que un carril vacío soltaba el latch y solo abrir Browse se lo
devolvía. Mataba el parpadeo, sí, pero dejaba **mudo el paneo**: llegabas a una calle con plazas y la
cabecera seguía en el coche. El diagnóstico que la motivaba también estaba mal calibrado — con la
cámara quieta la cifra solo cambia cuando una plaza se publica, se ocupa o caduca: sucesos de un solo
sentido separados por minutos, no un estrobo. Decidir en el canto resuelve las dos cosas sin pagar
ninguna.
