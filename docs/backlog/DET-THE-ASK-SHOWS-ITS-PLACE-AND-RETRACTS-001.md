# DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001 · La pregunta "¿Has aparcado?" enseña DÓNDE pregunta, y se retira cuando deja de aplicar

**Estado:** ✅ Done — mergeado a master. Base `b1995e09`.

**Verificado en device** (Pixel 8 Pro, emulador, escenario mock «pregunta abierta»): marcador en
claro y oscuro, calle y hora en la fila, encuadre al pulsar la card. **Sin ver**: el título de la
notificación con calle — el mock no postea a la bandeja, hace falta prod y una sesión real.

## Progreso

| # | Trabajo | Estado |
|---|---|---|
| D1 | Punto durable (`PendingPromptWindow.candidate` + `witnessedCarStop`) | ✅ hecho |
| D2 | Retracción por conducción (`StopTracking.promptRetracted` + `PROMPT_RETRACTED`) | ✅ hecho, falsado en ambos sentidos |
| D7 | `AwaitingAnswer` → `papWatchGreen` | ✅ hecho |
| D3 | Pin fantasma con `?` (marcador + cableado + galería) | ✅ hecho |
| D4 | Alejarse a pie no revierte (ya era así; sólo hay que no romperlo) | ✅ cubierto por test |
| D5 | Card pulsable que encuadra la cámara | ✅ hecho |
| D6a | Hora en la fila (absoluta, 9 locales) | ✅ hecho |
| D6b | Calle en fila y notificación (9 locales × 2 sistemas de recursos) | ✅ hecho · fila vista en device; **título de bandeja sin ver** (el mock no postea notificaciones) |
| D9 | El tema entra en el `contentId` del marcador | ✅ hecho — fallo cazado en device, ver abajo |
| D8 | Modal se abre sola al entrar + tap en el marcador la abre | ✅ (lo primero YA existía) |

### Verificado en device (Pixel 8 Pro, emulador, escenario mock "pregunta abierta")

La apertura automática de la sheet **ya estaba implementada** por `DET-ASK-STATE-001`
(`HomeSheetPositioning`, `LaunchedEffect(promptShownAtMs)`, una vez por pregunta). Lo que faltaba era
el tap en el marcador, que ahora abre la sheet y encuadra.

**Dos fallos que sólo salieron corriéndolo:**

1. **El fantasma dibujaba una silueta genérica, no el coche del usuario.** Lo había cableado a los
   `parkingVehicle*`, que son el fallback de UN aparcamiento del detalle de historial y valen `null`
   en Home. Ahora tiene sus propios parámetros (`askVehicle*`) alimentados por el vehículo ACTIVO —
   que no puede discrepar del título, porque las dos vías que postean la pregunta nombran también al
   activo (`activeVehicleName()` / `observeActiveVehicle()`).
2. **El encuadre duraba menos que un parpadeo durante un viaje.** `followDriver` pasa por
   `moveCamera`, así que el siguiente frame del puck (~700 ms) devolvía la cámara al coche. No se
   puede arreglar dentro de `moveCamera` sin matar el propio follow en su primer frame → método
   propio `HomeUiController.focusPlace(lat, lon)`, que **pausa el follow** y va. Pedir ver un sitio
   concreto es la misma intención que un paneo, así que recibe la misma respuesta: gana el usuario, y
   el FAB de reanudar sigue ahí.

**1815 tests, 0 fallos** (master `b1995e09`: 1802). `:app:compileProdDebugKotlin` y
`:app:assembleMockDebug` verdes.

⚠️ El primer intento de instalar **falló en silencio** (`INSTALL_FAILED_INSUFFICIENT_STORAGE`, el
pipe se tragó el error) y estuve mirando capturas del APK viejo. Lo cazó comparar el sha256 device
vs local, que es exactamente para lo que está esa comprobación. **Verificar el hash no es ceremonia.**

### ⚠️ D9 · El tema no estaba en el `contentId`, y eso es un fallo PREEXISTENTE

En tema oscuro el fantasma salía con el tag **blanco** sobre el mapa oscuro. La causa no es el
marcador: kmpmaps **cachea el bitmap rasterizado por `contentId`**, y el `contentId` no llevaba el
tema — así que un cambio de tema seguía sirviendo el ráster del tema anterior. Es exactamente la
misma clase de obsolescencia que el sufijo `_dim` documenta y resuelve (*"that is the only reliable
way to refresh opacity"*), sólo que a nadie se le había ocurrido que el TEMA cambia píxeles igual
que el dim.

Y no afectaba sólo al fantasma: **`vehicle_badge_*` y los tres `my_car*` tenían el mismo agujero**
—su tag es `Color.White` en claro y `PapInk` en oscuro—, así que un coche aparcado en pantalla
también se quedaba con el relleno del tema anterior. Barrido entero: el tema entra en las cuatro
familias de id, y `isThemeDark` sube por encima de la lista de marcadores (estaba declarado
trescientas líneas más abajo, junto al estilo del mapa).

El tap de los marcadores pasa a comparar por prefijo, ya que el id lleva sufijo.

### Notas de ejecución — la calle (D6b)

- **`ResolveAskedStreetUseCase` es UNA regla en UN sitio**, inyectada tanto en
  `NotifyParkingConfirmationUseCase` como en el executor, porque **tres** sitios postean la pregunta
  y una calle distinta entre ellos serían tres preguntas distintas sobre una parada.
- **Se resuelve ANTES de postear**, con presupuesto de 2 s. La alternativa obvia —postear ya y
  re-postear con la calle— pasa por `showParkingConfirmation`, que reescribe `shownAtMs` sin
  condiciones: una mejora cosmética habría movido un plazo de seguridad.
- **⛔ Dirección `approximate` rechazada de plano.** La calle prestada del vecino es útil como
  «cerca de X» en una lista; en una pregunta que planta un pin es una vía en la que no estás,
  impresa con número de portal. Sin calle es honesto; prestada, no.
- **⛔ El POI tampoco se usa**, aunque «¿has aparcado en Mercadona?» se lea mejor: es la Fase 2 del
  repositorio (red, un orden de magnitud más lenta que el presupuesto), así que sólo aparecería
  cuando una visita anterior lo hubiera cacheado — la MISMA parada se redactaría distinto según el
  día.
- **Cuatro redacciones de título, no una concatenación**: la preposición ante una calle («en», «at»,
  «à», «przy») es parte de la frase. Y ojo, son **dos sistemas de recursos distintos**: la
  notificación vive en `app/src/main/res` (android:strings, apóstrofo escapado) y la fila en
  `composeResources` (apóstrofo CRUDO).
- **⚠️ Sobrescribí `FakeAddressAndPlaceRepository` sin mirar que ya existía.** Lo restauré con
  `git checkout` y resultó que ya tenía todo lo necesario (`addressResult`, `delayMs`, `approximate`,
  `placeInfo`, `calls`). Escribir un fichero "nuevo" sin comprobar que no lo era es la forma barata
  de borrar el trabajo de otro.

### Notas de ejecución

- **`witnessedCarStop` se mantiene APARTE de `UserConfirmStage.whereTheCarIs`** y el porqué está
  escrito en el fichero: la cascada es un VEREDICTO en tiempo de respuesta (puede preferir el fix
  donde está el usuario al contestar junto al coche); el testigo es estable y es del COCHE.
  Recalcularlo por fix haría que el marcador caminara con el peatón — el fallo que `ANCHOR-LOCK-001`
  y `DET-ANCHOR-FREEZE-001` existen para impedir.
- **La precisión viaja con la coordenada** en DataStore (4 claves, se limpian juntas): un marcador
  que no puede decir cuánto duda se dibujaría como una afirmación exacta, que es lo único que un pin
  sin confirmar no puede hacer. `speed = 0f` no es un dato perdido en el round-trip — un stop
  presenciado está parado por definición.
- **Falsación de D2**: forzar la retracción a `false` pone en rojo el caso de conducción; quitarle la
  mitad `effectiveDriving` pone en rojo el caso de caminata. Un test de prohibición que no se ha
  visto fallar no prueba nada.
- **⚠️ D3 salió distinto de lo acordado, y por una razón del código.** Hablamos del `?` DENTRO de la
  cabeza del pin, sobre el supuesto de una gota con glifo interior (`MyVehicleMarker`). Pero ese
  marcador **no tiene call site de producción** — el aparcamiento real lo dibuja
  `VehicleBadgeMarker`, un "tag" cuadrado cuyo interior **es el propio coche**. Poner ahí la `?`
  borraría la identidad del vehículo. Así que la duda va donde la doctrina del marcador ya la pone —
  **en el marco**: borde discontinuo + disco `?` en la esquina superior derecha + el relleno del tag
  al 72 %.
- **El disco de la `?` va RELLENO**, con el glifo recortado en el color del tag (blanco en claro,
  ink en oscuro) y un aro del mismo color que lo despega del mapa. La primera pasada era al revés
  —disco pálido, glifo de color— y en device leía como una mancha pálida más entre las etiquetas del
  mapa. Relleno habla el mismo idioma que los pucks de plaza (disco de color, aro y glifo de papel),
  así que se lee como insignia de marcador y no como pegatina. Decidido mirándolo, no en pantalla
  grande.
- **⛔ La carrocería NO se atenúa.** `MAP-ICONS-V2` ya decidió que el coche se queda opaco y a todo
  color en todos los estados, *"so an inactive car never reads as fading away"*. La duda es sobre el
  SITIO; expresarla dudando de QUÉ COCHE es sería el mismo error una capa más abajo. Por eso la
  opacidad baja afecta al tag, nunca al vehículo.
- **`stroke-dasharray` sí se puede aquí**: la prohibición es de VectorDrawable, y esta familia de
  marcadores es Canvas — que es exactamente por lo que nunca fue un SVG.
- **La `?` se mide con `TextMeasurer`**, no se dibuja como path: a ~9 dp un glifo vive o muere por su
  hinting, y los marcadores de mapa son la excepción declarada del guardarraíl de tipografía.
- **`AwaitingAnswer` pasa a llevar la `PendingPromptWindow` entera** en vez de una copia de sus
  campos: tres parámetros sueltos serían tres oportunidades de que la fila describa una pregunta
  distinta de la que describe la bandeja.
- **La hora es ABSOLUTA** (`formatClockTime`, `HH:mm`). Una relativa necesita ticker y sin él se
  congela en lo que leyó al componerse. De paso, `HistoryTimeline` tenía su propia copia del mismo
  formateo inline: ahora hay **un** sitio que decide cómo escribe este app un reloj.
- **⛔ Sin cuenta atrás.** De los tres veredictos del timeout sólo uno planta un pin aquí, así que
  cualquier "se guardará en N min" sería una promesa que la app no puede cumplir.
- **La card pulsable no toca la ventana**: es un `MoveCamera`, la MISMA acción que ya usa el tap de
  una fila. Consultar no puede confundirse con responder.
- **⚠️ Al rebasar, master ya había resuelto el tirón de cámara mejor que yo.** Esta rama traía un
  `HomeUiController.focusPlace(...)`; `UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001` llegó a
  master con **puertas con rango declarado** (`goToPlace` / `framePlaces`) y `moveCamera` ya privado.
  Se retira `focusPlace` y el encuadre de la pregunta pasa por `goToPlace`: una tercera puerta con
  otro nombre es exactamente lo que ese ticket existe para impedir.
- **Regalo del "Aún no"**: `onUserDeniedParking()` ya pasa por `dismiss(...)`, que limpia la ventana
  durable — así que el fantasma desaparece por esa vía sin una línea de código propia.

## Problema

Dos fallos con la misma raíz: **la pregunta no dice de qué sitio habla, y no sabe callarse.**

### P1 · La app sigue preguntando mientras el usuario conduce (bug)

Cuando vuelve la conducción, `StopTracking.kt:411` hace `confirmation.stopEnded()`: el motor **ya da
la pregunta por muerta** — `ResponseTimeoutStage` deja de tener `promptShownAt` y el guardado
desatendido de los 15 min no llega a ocurrir. Pero nadie retira la pregunta de las dos superficies
que la muestran:

- la notificación sigue posteada — los únicos llamantes de `dismissPrompt()` son el safety-net
  (`ParkingSafetyNetWorker.kt:175,547`), el `SaveUnattended` del dispatcher
  (`DetectionEffectDispatcher.kt:169,183`) y el "No" del usuario
  (`CoordinatorParkingDetector.kt:1164`);
- el slot durable sigue escrito → `HomeViewModel.kt:172-181` sólo lo cierra **por tiempo** o por un
  `null` que llegue del flow.

Y `DetectionStory` da a `AwaitingAnswer` precedencia **por encima de `Driving`**
(`DetectionStory.kt:146`). Resultado observable: el coche circula, el puck se mueve, y Home sigue
mostrando *"¿Has aparcado tu Ford Focus?"* con dos botones durante hasta 15 minutos
(`confirmationResponseTimeoutMs`, `ParkingDetectionConfig.kt:821`). La app pregunta algo cuya
respuesta ya sabe y que ya nadie escucha.

### P2 · La pregunta se responde a ciegas

`showParkingConfirmation(score, vehicleName)` (`AppNotificationManager.kt:21`) no tiene ni recibe
coordenadas: el título sólo nombra el coche. La fila in-app tampoco dice ni dónde ni cuándo — tiene
`promptShownAtMs` en el estado y lo usa **sólo** para posicionar la sheet
(`HomeSheetPositioning.kt:354`).

Un "Sí" confirma **que** aparcaste, no **dónde**. El usuario no puede desmentir un ancla mala porque
no la ve, y ésa es justo la corrección que la doctrina asimétrica quiere que sea barata.

### P3 · `AwaitingAnswer` se quedó fuera del barrido de color (bug)

`UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001` separó el verde de marca del verde de coche vigilado, y
`HomeDetectionSurface.kt:140-148` documenta el fallo que eso destapó, cazado **en device, no por el
barrido**: una fila que habla de un coche concreto llevaba el color de la app. Ese arreglo llegó a
`methodTone` (filas `Driving` / `Watching`) y **`AwaitingAnswer` sigue con `tone = brand`**
(`HomeDetectionSurface.kt:157`) — la fila que más claramente habla de un coche concreto, porque lo
nombra en el título y lleva su glifo. Mismo fallo, una rama del `when` más abajo.

## Doctrina violada

- **[DET-ASK-STATE-001]** — "la pregunta es estado de app, no un aviso desechable". Lo es en
  *cuándo* (`shownAtMs`) y en *de quién* (`vehicleName`), pero **no en dónde**, que es lo único que
  permite responderla con criterio.
- **[UI-COLOR-DOCTRINE-001] / [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]** — el color de una fila
  que habla de un coche es el del COCHE (`papWatchGreen`), no el de la app (`primary`). Ver P3.
- **Sistemas, no parches** — la ventana de la pregunta se cierra en UN sitio (el punto de posteo del
  canal). La retracción tiene que pasar por ahí, no por un segundo camino de limpieza en el
  ViewModel.
- **Fallo asimétrico** — enseñar el sitio abarata el "no", que es la dirección segura. No lo
  encarece: el veredicto por silencio no cambia.

## Señales / datos disponibles

- **La ubicación candidata ya está en el sitio que lanza el prompt**: `degradeToPrompt(...)` recibe
  `location: GpsPoint` y hoy la usa **sólo** para el diagnóstico
  (`DetectionEffectExecutor.kt:384-415`). No hay que ir a buscarla.
- **Geocoder inverso completo con caché Room** (`AddressAndPlaceRepositoryImpl`), y
  `AddressInfo.street` ya es `thoroughfare + subThoroughfare` → *"Calle Padornelo 3"*, con número.
- **`AddressAndPlace.approximate`** marca las respuestas prestadas de una celda vecina cuando el
  geocoder falla (`GEO-CACHE-ANSWERS-NEARBY-001`).
- **El puck ya se congela** en el ancla durante `Candidate` (`HomeTripController.kt:204-213`).
- **`effectiveDriving`** ya distingue conducción de caminata, con corroboración
  (`StopTracking.kt:348-359`).

### ⚠️ Lo que NO tenemos, y es la razón de que esto sea un ticket y no un retoque

**El sitio del fantasma no es durable.** El congelado del puck sale de `lastDrivingLocation`, una
`var` en memoria de `HomeTripController` (`HomeTripController.kt:115,210`), y si es `null` el ancla
cae a `loc` — la ubicación **actual**. O sea: si el usuario abre la app en frío dentro de la ventana
—el escenario exacto para el que existe DET-ASK-STATE-001, y el más probable: coche aparcado,
andando a casa, saco el móvil— el "pin fantasma" apuntaría **al peatón, no al coche**, y el encuadre
te llevaría a ti mismo.

## Diseño

### D1 · El punto entra en `PendingPromptWindow` (habilita todo lo demás)

`PendingPromptWindow` lleva `shownAtMs` + `vehicleName` precisamente para que bandeja y fila no
puedan discrepar. **El `GpsPoint` candidato es el tercer campo de esa misma familia.** Con él, el
fantasma, la calle y el encuadre leen la misma coordenada y no pueden desmentirse entre sí; sin él,
el resto del ticket no se sostiene.

Se escribe en el mismo choke point que ya escribe el resto (`AppNotificationManagerImpl.kt:41-50`),
lo que obliga a que `showParkingConfirmation` reciba la ubicación. Los llamantes ya la tienen.

### D2 · Retracción por conducción — UNA llamada, no una por superficie

`dismiss(PARKING_CONFIRMATION_NOTIFICATION_ID)` **ya limpia el slot durable**
(`AppNotificationManagerImpl.kt:125`). Así que bandeja y fila caen juntas con un solo
`dismissPrompt()`, la historia cae sola a `Driving` y el eyebrow del peek vuelve a "EN RUTA" sin
estados nuevos. Es el mismo verbo que ya ejecuta `onUserDeniedParking()`, disparado por evidencia en
vez de por un dedo.

El disparo es la **transición** `promptShownAt: no-null → null` que ya produce `stopEnded()`, no un
predicado nuevo: efecto `DismissPrompt` emitido donde se observa la transición. No se crea caso de
uso — no hay veredicto nuevo, hay un efecto que faltaba.

⚠️ **`effectiveDriving` no es sólo conducción medida**: incluye `steplessDeparture`,
`corroboratedMuteHop` y `displacementOutrunsSteps`. Esto **no añade riesgo**, porque el motor ya
cancela ahí dentro hoy — sólo hace visible una decisión que ya se toma. Si el criterio de
cancelación es demasiado laxo, el bug está en `stopEnded()` y es ticket de detección aparte.

⚠️ **Debe dejar rastro**: `PROMPT_RETRACTED` / `reason=drive_resumed` en el diagnóstico. Sin eso un
field test futuro leerá "prompt nunca mostrado", como la forense del 10-07.

### D3 · El pin fantasma

Durante la pregunta abierta, **el puck congelado se convierte en el fantasma** — un objeto, no dos
marcadores solapados en el mismo punto. Secuencia completa: puck → fantasma con `?` → pin sólido.

- **Misma silueta que el pin confirmado**, con menos opacidad. Si es otro marcador, el `SaveExact`
  se ve como "desaparece uno, aparece otro" en vez de como algo que se resuelve.
- **La `?` es la mitad que sostiene el significado**; la opacidad sola en un mapa se lee como
  "lejos" o como artefacto de render.
- **Decisión de glifo pendiente de device** (dos candidatos, se mide, no se decide en pantalla
  grande): (a) badge en esquina — el circulito toma el **color de superficie del tema**, nunca gris
  (gris ya significa *coche sin vigilancia*, y sobre asfalto desaparece); arriba-derecha antes que
  arriba-izquierda para no pisar la punta del pin ni su glifo. (b) **recomendada para probar
  primero**: la `?` **sustituye al glifo interior** del pin — una sola silueta, legible al tamaño
  real, y la confirmación es cambio de glifo + subida de opacidad.
- Nivel 3: vector propio en `composeResources/drawable/` con variante `_dark`, **no se tinta**.
  ⚠️ Si acaba pidiendo borde discontinuo → VectorDrawable **no soporta `stroke-dasharray`** →
  Compose Canvas. Decidirlo antes de dibujar el SVG.

**Las tres salidas se pintan**, o el usuario que fue a mirar el fantasma lo ve endurecerse o
evaporarse sin explicación:

| Veredicto de `EvaluateUnattendedParkingSaveUseCase` | Qué hace el fantasma |
|---|---|
| `SaveExact` | se solidifica en el mismo punto |
| `SaveZone` | se convierte en la zona con su radio: la duda se dibuja |
| `Ask` | se retira; la historia pasa a `PendingAsk` |

**El silencio no es una pregunta colgada**: `ResponseTimeoutStage` (DET-RECONCILE-001) ya lo cierra
con veredicto a los 15 min. El prompt es la oportunidad de responder ANTES, no una puerta.

⛔ **Nada de cuenta atrás.** "Se guardará en 12 min" sólo es cierto en uno de los tres veredictos.
La hora de lanzamiento (absoluta) sí; el countdown miente con un reloj delante.

### D4 · Alejarse a pie ≠ volver a "en ruta"

- **A pie** (egress): el fantasma **se queda en el coche** mientras el punto del usuario se aleja.
  Es evidencia de aparcamiento, no de conducción — por eso el ancla se bloquea con pasos o se
  congela. Y es justo el caso donde el encuadre vale para algo.
- **Conduciendo** (`effectiveDriving`): fantasma → puck, seguimiento vivo, y retracción (D2).

### D5 · Pulsar la card encuadra la cámara

Card entera pulsable; **ni botón helper ni icono**. El icono del badge es ~24 dp (bajo el mínimo
táctil) e invisible como afordancia; un tercer botón diluye una decisión binaria ("Sí" / "Aún no" /
"Ver" no son del mismo rango). El pin fantasma en el mapa es el segundo target, gratis y natural.

- Es un **encuadre de cámara, no una navegación**: la respuesta tiene que seguir a un pulgar de
  distancia mientras la cámara vuela.
- ⛔ **Consultar no responde.** No puede tocar la ventana — misma trampa que el `setDeleteIntent`
  deliberadamente ausente que documenta `PendingPromptWindow`. Merece línea de guardarraíl.
- Discoverabilidad por contenido, no por controles: la línea de calle hace que la card se lea como
  "esto va de un sitio" + chevron (Nivel 1) como afordancia de entrada.
- `ActionRow` la comparten todas las historias → `onClick` **opcional con default null**, no un
  `if (story is AwaitingAnswer)` dentro del composable.
- A11y: card pulsable con dos botones dentro necesita sus propias semantics.

### D6 · Calle y hora

- **Calle**: resolver **ANTES de postear**, con presupuesto corto (~2 s). ⚠️ Re-postear "ya con
  calle" pasa por `showParkingConfirmation`, que reescribe `shownAtMs = now` sin condiciones → **le
  reinicia al usuario su ventana de 15 minutos**. Trampa de una línea que no se ve.
- ⛔ **Nunca la dirección `approximate`** ni la prestada del vecino: en una lista de POIs es
  aceptable, en una pregunta que planta un pin es mentir con nombre y número. Sin `street` o con
  `approximate == true` → se pregunta como hoy, sin calle. Sin fallback.
- 🎁 Ese geocode **calienta la caché Room** para el `UpdateParkingSessionAddressAndPlaceWorker` que
  corre justo después del guardado: no es trabajo extra, es el mismo trabajo movido antes.
- **Hora absoluta** ("a las 12:41"), no relativa: un "hace N min" necesita ticker y sin él se
  congela. Rol `meta` (voz LECTURA) — va dentro de una línea de texto, no es CIFRA.
- Strings: **una key entera con dos placeholders por idioma**, nunca concatenar (la preposición
  cambia en los 9 locales). Vocabulario: esto es tu **APARCAMIENTO**, no una plaza.

### D7 · `AwaitingAnswer` → `papWatchGreen`

`tone = brand` → el leg verde de identidad (`watched`, `HomeDetectionSurface.kt:147`). Como el
prompt es **exclusivo del Coordinator** (`NotifyParkingConfirmationUseCase` y `degradeToPrompt` sólo
están cableados en `CoordinatorParkingDetector` / `DetectionEffectExecutor`), es siempre `watched` y
nunca `bluetooth`: token fijo, no `methodTone`.

⚠️ **El nombre sigue en `onSurface`.** El color viaja en el glifo, el borde y el contenedor tonal.

## Criterio de éxito

1. Conducción medida durante una ventana abierta → notificación fuera + fila fuera + `Driving` en la
   historia + `PROMPT_RETRACTED` en el diagnóstico. Test unitario sobre la transición.
2. `PendingPromptWindow` con punto → cerrar y reabrir la app dentro de la ventana muestra el
   fantasma **en el coche**, no en el usuario. Verificado en device andando desde el coche.
3. Los tres veredictos del timeout se ven en la galería mock (solidifica / zona / se retira).
4. Pulsar la card encuadra y **NO** cierra la ventana — guardarraíl.
5. Sin `street` o con `approximate` → la pregunta se posta igual, sin calle, y sin reiniciar
   `shownAtMs`.
6. `AwaitingAnswer` en `papWatchGreen`; el nombre en `onSurface`. `ColorGuardrailTest` verde.
7. 9 locales para toda key nueva.

## Consumidores auditados

| Sitio | Asunción contraria | Estado |
|---|---|---|
| `ParkingSafetyNetWorker.kt:175,547` | llama `dismissPrompt()` por su cuenta | ✅ cubierto — mismo choke point |
| `DetectionEffectDispatcher.kt:169,183` | `SaveUnattended` retira el prompt | ✅ cubierto |
| `CoordinatorParkingDetector.kt:1162-1166` | "No" del usuario retira y resetea | ✅ cubierto |
| `DetectionEffectExecutor.kt:236,401` | los 2 sitios que postean el prompt | ✅ pasan `witnessedCarStop` |
| `NotifyParkingConfirmationUseCase.kt:20-22` | 3ª vía de posteo (Low/Medium/High) | ✅ recibe `candidate` vía `NotifyPrompt.at` |
| `IosAppNotificationManagerImpl.kt:40` · fakes `commonMain`/`commonTest` · `NotifyParkingConfirmationUseCaseTest` · `StageOrderTest` | implementan la firma | ✅ barridos |
| `HomeTripController.kt:115,210` | el congelado vive en memoria de la UI | ✅ **exento con razón**: el puck sigue siendo el del VIAJE (posición viva, no una pregunta). El fantasma es un marcador aparte alimentado por el punto durable, así que el `var` ya no es la única fuente de "dónde está el coche" |
| `StateGalleryScreen.kt` variantes del prompt + marcadores | galería mock | ✅ 3 variantes de fila (con sitio / sin sitio / sin nombre) + fila de marcadores confirmado vs sin confirmar |
| `HistoryTimeline.kt:156` | formateaba `HH:mm` inline por su cuenta | ✅ barrido a `formatClockTime` |
| `HomeDetectionSurface.kt:157` | `tone = brand` | ✅ → `watched` |
| `HomeDetectionSurface.kt:200,220` (+`NoVehicle`) | otras filas en `tone = brand` | ✅ **exentas con razón**: son filas de SETUP — «activa la detección», «marca tu aparcamiento», «añade un vehículo». Habla la app, no un coche. La regla del propio fichero las deja en `brand` |
| `StateGalleryScreen.kt` · `MockScenario` | variantes del prompt | ⏳ paridad en la misma tarea |

## Follow-ups deliberadamente fuera de alcance

- **"Sí, pero ahí no"** — enseñar el sitio crea una respuesta que hoy no tiene destino: "Aún no"
  significa *sigo conduciendo*, y `onUserDeniedParking()` borra la sesión entera sin dejar pin ni
  nudge, así que el que ve el pin a 80 m **pierde el aparcamiento**. Implica flujo de corrección
  (y ya tumbamos el «arrastra el pin» de la ficha de Play por no existir). → ticket propio.
- ~~El tirón de cámara en los taps de plaza y coche aparcado~~ → **ya en master**
  (`UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001`), y con más alcance del que yo le había
  dado: cubre también búsqueda, chip de zona y los FABs.
- **`monitoringStatus()` resuelve por configuración, no por la estrategia viva**
  (`bluetoothDeviceId != null` → azul) — un coche emparejado con el BT apagado lo vigila el
  Coordinator y se pintaría azul. No muerde en esta fila (queda en token fijo), pero es una mentira
  latente en `DetectionStory.Driving.viaBluetooth`. → ticket propio.
