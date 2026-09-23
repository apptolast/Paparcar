# SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001 · la plaza más temprana que podemos dar, y por qué no es una plaza

**Estado:** ✅ Done · rama `feature/SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001-early-spot` ·
worktree `../Paparcar-early-spot` · issue [#133](https://github.com/rndevelo/Paparcar/issues/133)

> **Decisión del usuario (23-09):** adelante con el **cuarto estado `RELEASING`**, con tono e icono
> propios. F0 (medir antes) se salta.

## Lo que pide el issue

> *«Analiza qué señales más tempranas podríamos tener en la detección para poder enviar el spot
> libre lo más pronto posible, siendo este provisional con su respectivo icono y su fila en la lista
> de la modal con un nuevo estado que representa la plaza posible antes de verificarse del todo.»*

## Primero: la mitad que YA está construida

El mecanismo de «plaza posible antes de verificarse» **existe y está en producción** desde
[DET-HANDOFF-NOT-MANUAL-001 §B]:

| Pieza | Dónde | Qué hace hoy |
|---|---|---|
| `SpotStatus.PROVISIONAL` | `domain/model/SpotStatus.kt` | la plaza sale **a toda velocidad** con la salida sólo DEDUCIDA |
| `SpotStatus.RETRACTED` | ídem | retirada ≠ borrado: sigue llegando `RETRACTION_GRACE_MS` para poder **explicarse** a quien ya iba |
| `PROVISIONAL_SPOT_TTL_MS` | `SpotTtlPolicy.kt` | **12 min** en vez de 2 h — el fantasma muere solo |
| Badge de duda | `SpotFreshness.kt` | *«Doubt about the departure (PROVISIONAL) is told by its badge»* |
| `FinalizeDeducedDeparture` / `RetractDeducedDeparture` | `usecase/parking/` | ascender a CONFIRMED con conducción medida, o retirar |

Así que esto **no es construir un estado nuevo**: es responder a una pregunta mucho más concreta —
**qué señal lo abre, y cuánto antes puede abrirlo**. Y ahí sí hay algo que ganar, medido.

## La medición: cuánto antes lo sabemos ya

Del ledger de triggers `diagnostics/8fhNJGctp…/sessions/triggers_20718` (22-09, un día entero de un
usuario real con BT), cada vez que el usuario **se sube al coche** y después sale:

| Embarque detectado | Trigger que hoy ARMA la sesión | Ventaja |
|---|---|---|
| 07:10:45 `AR_VEHICLE_ENTER` | 07:11:15 `GEOFENCE_EXIT` | **30 s** |
| 09:16:00 `AR_VEHICLE_ENTER` | 09:17:14 `GEOFENCE_EXIT` | **1 min 14 s** |
| 10:16:34 `AR_VEHICLE_ENTER` | 10:20:19 `ARRIVAL_HANDOFF` | **3 min 45 s** |
| 17:39:12 `AR_VEHICLE_ENTER` | 17:43:03 `GEOFENCE_EXIT` (*departure-follower, ya a 34 km/h*) | **3 min 51 s** |

Mediana ≈ **2,5 min**, y es una **cota inferior** por dos razones:

1. Ese `GEOFENCE_EXIT` es cuando **arma la sesión**, no cuando se publica la plaza: la publicación
   llega después de verificar la salida.
2. La fila de las 17:39 lo enseña crudo: cuando la geocerca reaccionó, **el coche ya iba a 34
   km/h**. La plaza llevaba minutos libre y nadie lo sabía.

Y el `bt_recorder` arranca en el CONNECT del BT: sus primeras muestras (07:10:56, 09:16:24,
10:16:53, 17:39:37) caen **11–25 s después** del `AR_VEHICLE_ENTER`. O sea: con este aparato el AR
llega antes que el BT, y el embarque es el evento más temprano que este sistema recibe hoy.

### ⛔ Y esas cuatro señales se TIRAN

Las cuatro dicen `outcome=REFUSED_STRATEGY reason=owner=BLUETOOTH`. **La señal más temprana que
tenemos de que una plaza va a liberarse se descarta**, porque el carril BT es el dueño del vehículo.
Ya estaba anotado como violación de doctrina en [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001]
(*«el trigger se descarta en vez de degradarse al evaluador»*), donde se dejó fuera de alcance
porque *no costaba pines*. Para este ticket **sí cuesta**: es justo el dato que habilita todo lo
demás.

## ⛔ La corrección de fondo: un embarque no es una salida

Aquí está el motivo del nombre del ticket, y es la única parte del planteamiento del issue que
recomiendo cambiar.

`PROVISIONAL` significa hoy **«el coche probablemente YA se fue, pero nadie lo midió»**. Si
publicamos en el embarque estamos diciendo algo **distinto y más fuerte**: *«el coche SIGUE AHÍ, y
se va ahora mismo»*. Un conductor que acepte esa plaza y llegue en 60 segundos **se encuentra el
coche puesto**. Eso no es una plaza vieja: es una plaza ocupada — y es peor que no ofrecerla, porque
gasta el único recurso que no se recupera, que es el desplazamiento de quien busca.

Además rompería la rampa: `SpotFreshness` deriva de la EDAD, y una plaza publicada antes de estar
libre nace **verde** diciendo «liberada hace 0 minutos» cuando todavía tiene un coche encima. El
propio KDoc de `SpotFreshnessPolicy` ya se preocupaba de *«no pintar un posible fantasma de verde
durante sus primeros nueve minutos»*.

**Conclusión:** no es un `PROVISIONAL` más temprano. Es un **cuarto estado**, con su propia frase,
su propio icono y su propio sitio en el orden:

```
RELEASING ──sale medido──▶ PROVISIONAL ──conducción medida──▶ CONFIRMED
     │                          │
     └────no sale, nunca────────┴──────────────────────────▶ RETRACTED
```

- **`RELEASING`** = *«Se libera ahora»* — hay alguien dentro del coche. Ocupada, con salida
  inminente y con un dueño que la está soltando.
- Icono: **no** puede parecerse al de una plaza libre. Es la misma plaza con el coche todavía
  dentro y una marca de movimiento (Nivel 3, vector propio multicolor, `composeResources/drawable/`
  + su `_dark`) — la doctrina de iconos no permite un glifo Material para un concepto de Paparcar.
- Fila en la hoja: con su badge (LECTURA/`badge`, como `SIN CONFIRMAR`), **ordenada por debajo** de
  toda plaza realmente libre. Nunca es la fila que el carril encuadra primero.
- TTL: más corto todavía que los 12 min de `PROVISIONAL` — un embarque que no se convierte en
  salida en pocos minutos no fue una salida. Propuesta a medir: **3–5 min**.

## Qué señal, con qué puerta

Ordenadas por lo pronto que llegan. Ninguna confirma nada: todas **nominan**, que es exactamente lo
que `RELEASING` admite en su nombre.

| Señal | Cuándo llega | Qué la hace creíble | Veredicto |
|---|---|---|---|
| **BT CONNECT del coche emparejado que TIENE plaza** | al arrancar / encenderse la radio | determinista, atado a la MAC; ya hay watch ([DET-A-BT-CONNECT-IS-A-DEPARTURE-001]) | ✅ la mejor para quien tiene BT — **con la puerta antirebote de [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001]**: el dongle de #131 hace ~20 ciclos/día y sin esa puerta serían 20 plazas fantasma |
| **`AR_VEHICLE_ENTER` con `dep=enter_at_car`** | 30 s–4 min antes del EXIT (medido arriba) | `EvaluateArEnterArmUseCase` ya exige que el embarque sea del PROPIO coche (bus/taxi no arman) | ✅ la única disponible para quien NO tiene BT — hoy **descartada** por `REFUSED_STRATEGY` |
| Ancla de regreso / pasos acercándose al coche | antes que las dos | hoy sólo acelera el sellado ([DET-RETURN-ANCHOR-001]) | 🟡 demasiado ambigua sola: volver al coche a por algo es lo mismo |
| `GEOFENCE_EXIT` | ya conduciendo | es lo de hoy | — |
| «Me voy» del usuario | cuando él quiera | máxima autoridad | ✅ ya publica |

**La puerta, en una frase:** *sólo nomina un embarque que esté atado al propio coche, que ese coche
tenga una plaza viva, y que no sea el rebote de una desconexión reciente.* Las tres condiciones
tienen ya su evaluador o su dato en el sistema; no hace falta inventar umbrales.

## Doctrina — dónde encaja y dónde roza

- ✅ *«El evento NOMINA, sólo el movimiento MEDIDO confirma.»* Se respeta literalmente: el embarque
  publica un estado que **dice** que no está confirmado, y sólo la conducción medida lo asciende.
- ✅ *«Fallo asimétrico: mejor falso negativo que falso positivo.»* Se respeta **si y sólo si** el
  cuarto estado existe. Reutilizar `PROVISIONAL` sería plantar una plaza fantasma con una etiqueta
  que ya significa otra cosa — el falso positivo que la doctrina prohíbe.
- ⚠️ *«Nunca se gatea VER.»* `RELEASING` es información gratis, como toda plaza. No entra en Plus.
- ⚠️ **Zona privada**: pasa por `SpotReleasePolicy` igual que cualquier publicación, y la compuerta
  se pregunta al PUBLICAR — así que aquí se preguntaría antes. Hay que comprobar que preguntarla en
  el embarque y otra vez en la salida no publique dos veces.

## Fases propuestas

- **F0 · Medir sin publicar nada.** Estampar en el ledger el embarque nominado y el instante en que
  hoy se publica la plaza de verdad, y recoger **la distribución real de la ventaja** sobre varios
  usuarios y varios días — las cuatro muestras de arriba son de UN día y UN usuario. Salida: ¿son
  2,5 min de mediana, o son 40 s? De eso depende que el resto valga la pena.
  **También mide la tasa de embarques que NO acaban en salida**, que es el coste.
- **F1 · Dejar de tirar la señal.** `REFUSED_STRATEGY` degrada al evaluador en vez de descartar.
  Es corrección de doctrina por sí sola y **no publica nada todavía**.
- **F2 · El cuarto estado**, de punta a punta: `SpotStatus.RELEASING`, TTL propio, transiciones,
  reglas de Firestore, serializadores (⛔ campo nuevo ⇒ paridad end-to-end), orden en la lista.
- **F3 · La cara**: icono Nivel 3 + badge + fila + galería de estados y `MockScenario`, en la misma
  tarea [feedback_keep_dev_catalog_in_sync].
- **F4 · Campo**: ¿cuántas `RELEASING` acaban en `CONFIRMED`? ¿cuántos conductores aceptan una y se
  encuentran el coche todavía puesto? Si la segunda cifra no es ~0, se retira la fase.

## Decisión pendiente (usuario)

1. **¿Cuarto estado (`RELEASING`) o reutilizar `PROVISIONAL`?** Mi recomendación es el cuarto, por
   lo de arriba: publicar «libre» una plaza que tiene un coche dentro rompe la promesa del mapa, y
   es el tipo de fallo que no se nota en los números y sí en la confianza.
2. **¿F0 antes de F2, o directo?** Recomiendo F0: cuesta poco, y la ventaja medida es la que decide
   si el cuarto estado paga su complejidad.
3. **Frase de la fila** (el copy manda en la percepción). Candidatas, sin jerga y con
   causa+consecuencia: *«Se libera ahora»* · *«Saliendo»* · *«Alguien se va»*. ⛔ Nunca *«posible»*
   a secas: no dice qué es lo posible.

## Fuera de alcance

- Reservar / «voy de camino» sobre una `RELEASING` (el `enRouteCount` ya existe para plazas libres;
  aplicarlo a una que aún no lo está es otra conversación).
- Publicar el embarque de un coche COMPARTIDO al grupo.

---

# Implementación (23-09)

## Las decisiones que se tomaron al escribirlo

### 1 · `SpotTone`: la rampa no crece, se le pone un hermano

`SpotFreshness` **no gana una cuarta entrada**: es una función de la EDAD y sus tres tramos se
derivan de un timestamp. Una plaza que se está liberando no tiene edad que contar — nació hace
cero segundos y aun así no está libre.

Nace `SpotTone` (4 entradas) como lo que la UI pinta, con **un solo resolver** `Spot.tone(nowMs)`:
RELEASING si el estado lo dice, y si no el tramo de edad. Así ninguna superficie puede pintar una
plaza que se libera con la rampa de frescura — que es justo lo que advertía `SpotFreshnessPolicy`
(*«no pintar un posible fantasma de verde durante sus primeros nueve minutos»*), sólo que aquí
saldría verde **con razón** y mentiría igual. [UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001]

🔑 Y hay un motivo mecánico además del semántico: `PaparcarMapView` registra los dibujos de los
marcadores iterando `entries` del tipo. Con el tono como enum, **la cuarta variante registra su
propio dibujo sola** — que es exactamente el fallo de `UI-A-MARKER-ID-MUST-BRING-ITS-DRAWING-001`
(un id sin dibujo no pinta NADA) cerrado por construcción en vez de por vigilancia.

### 2 · `provisional: Boolean` → `claim: SpotStatus`

Un booleano no puede decir tres cosas. La publicación pasa a llevar **el estado con el que sale**,
y el compilador barre los cinco consumidores (puerto, scheduler Android, worker, outbox, iOS). Es
el mismo movimiento que `SessionOutcome` hizo con los strings de salida.

### 3 · El sello de la nominación vive en `AppPreferences`, no en Room

Hace falta acotar la retirada (sin sello, `retractSpot` sobre un documento inexistente devuelve
PERMISSION_DENIED y llena el log — medido: 256 denegaciones en cinco días,
[DET-RETRACT-DENIED-FOREVER-001]). Pero **no hace falta una columna de Room**: se embarca en un
coche cada vez, el patrón «ventana pendiente» ya existe en `AppPreferences` con sus dos
implementaciones (`PendingPromptWindow`, `PendingParkNudge`), y así no hay migración, ni mapper, ni
paridad de DTO — el sello es local por definición.

### 4 · No hace falta tocar `REFUSED_STRATEGY` (F1 se cae del alcance)

La fase F1 existía porque el embarque del usuario de campo se descartaba con
`REFUSED_STRATEGY owner=BLUETOOTH`. Pero **ese usuario tiene BT**, y en el carril BT el CONNECT
del coche emparejado llega en el mismo momento (medido: 11–25 s después del AR ENTER). Las dos
poblaciones quedan cubiertas por su propio carril:

| Usuario | Señal que nomina |
|---|---|
| con BT emparejado | `BT_CONNECT` del coche que tiene plaza viva |
| sin BT | `AR_VEHICLE_ENTER` con `enter_at_car` |

`REFUSED_STRATEGY` sigue siendo una violación de doctrina y sigue sin costar un pin: **se queda
donde estaba**, fuera de alcance, y ya no bloquea esto.

### 5 · Tono: violeta, y es el único hueco de la paleta

Medido sobre `Color.kt`: la paleta cubre verdes (marca, vigilancia, Plus, plaza fresca), ámbar,
rojo y tres azules (mapa vivo, identidad BT, parking público). **No hay ni un violeta.**

⛔ Y no vale reutilizar `PapLiveMap`: ese azul ya es *«movimiento que no es tuyo»* en la forma
concreta de la plaza EN RUTA — y el puck en ruta y el puck liberándose se dibujan en **la misma
capa**, uno al lado del otro. Dos significados con la misma tinta en el mismo sitio es el defecto
que `UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001` existe para impedir.

Valores elegidos **con la medición delante**, no por gusto:

| Token | Hex | Medido |
|---|---|---|
| `PapSpotReleasing` (oscuro + tono fijo del puck) | `#A96BFF` | 5,62:1 sobre `PapInk` · blanco encima 3,37:1 (suelo de la familia del puck: 2,85) · **ΔE00 17,0** del `PapLiveMap` con el que comparte capa |
| `PapSpotReleasingMuted` (lecho oscuro) | `#241040` | el tono escribe encima a 5,10:1 |
| `PapSpotReleasingLight` | `#7C3AED` | 5,70:1 sobre blanco · 5,31:1 sobre la página clara |
| `PapSpotReleasingContainerLight` | `#EADDFF` | — |
| `PapOnSpotReleasingContainerLight` | `#4A148C` | 9,20:1 sobre su lecho |

### 6 · Icono: la plaza que todavía tiene coche

El puck de plaza es Canvas (no VectorDrawable), así que el estado se dibuja, no se tinta:

- **Glifo**: el del coche saliendo, no el glifo comunitario de plaza libre — de un vistazo se ve
  que **ahí todavía hay un coche**.
- **Anillo de TTL**: ninguno. El anillo mide *cuánta vida le queda a una plaza libre*, y ésta no lo
  está: pintarlo sería contestar una pregunta que nadie ha hecho.
- Se dibuja con `#A96BFF`, y el punto de suelo y la sombra siguen siendo los de siempre — la
  familia de marcadores no cambia de anatomía por un estado.


## Criterio de éxito

- [x] `SpotStatus.RELEASING` de punta a punta: dominio, TTL propio (4 min), publicación, outbox,
      worker de Android, scheduler de iOS.
- [x] El embarque publica desde los DOS carriles: `ACL CONNECT` del coche emparejado con plaza viva
      (con la puerta antirebote) y `ArEnterDecision.ArmAtCar`.
- [x] La salida real **promociona el mismo documento** (`spotId = session.id`) y retira el sello.
- [x] Tres redes de retirada: `NeverDrove` del watch BT · fin de sesión con el sello aún puesto ·
      TTL. La retirada está acotada para no escribir sobre un documento barrido.
- [x] La plaza que se libera **nunca se lee como libre**: tono violeta propio y medido, glifo de
      coche, insignia de salida, sin anillo de TTL, badge propio en la fila, nota en el peek, y
      **última en el orden del carril**.
- [x] Fuera de las alertas de zona — con su testigo, porque ese `when` acaba en `else`.
- [x] Copy en los **9 locales** (badge · eyebrow · nota), sin keys reutilizadas.
- [x] Galería de estados: `FreeSpot · se libera ahora — normal · seleccionado`.
- [x] `./gradlew testAll --rerun-tasks` **verde**, más `:app:compileMockDebugKotlin`,
      `:app:compileProdDebugKotlin` y `:shared:compileKotlinIosSimulatorArm64`.
- [ ] Campo: que una plaza `RELEASING` de un viaje real acabe en `CONFIRMED`, y que ninguna
      caduque con el coche todavía puesto.

### Testigos, falsificados uno a uno

| Si se rompe… | Cae |
|---|---|
| `Spot.tone` deja de mirar el estado | `SpotToneTest > should_neverReadAsFresh_when_theCarIsStillInTheSpace` |
| el orden deja de mandar `RELEASING` al final | `NearbyOrderingTest > should_keepAReleasingSpotBehindEveryFreeOne…` |
| la retirada pierde su cota de TTL | `WithdrawSpotReleaseUseCaseTest > should_notWriteIntoTheVoid…` |
| la nominación deja de preguntar por la zona privada | `EvaluateSpotReleaseNominationUseCaseTest > …InsideAPrivateZone` |

Los cuatro se rompieron a mano, fallaron, y volvieron a verde al restaurar.

## Consumidores auditados

| Señal / símbolo | Sitios | Clasificación |
|---|---|---|
| `provisional: Boolean` de la publicación | puerto, scheduler Android, worker, outbox, scheduler iOS, 3 use cases, 2 fakes | **cerrado** — sustituido por `claim: SpotStatus`; el compilador enumeró los ocho |
| `SpotTtlPolicy.ttlMs` | worker, outbox, scheduler iOS | **cerrado** — `when` exhaustivo sobre el estado; un quinto no compila sin decidir su vida |
| `SpotFreshness` en UI | marcador, vista de mapa, fila, peek, medidor, indicadores, galería, 2 previews, `MapMarkerIdsTest` | **cerrado** — pasan a `SpotTone`; la rampa se queda pura, con su input único y sus tests |
| Registro de dibujos del marcador | `PaparcarMapView` (`entries.forEach`) | **cerrado por construcción** — al iterar el TONO, la cuarta variante trae su dibujo sola; era el fallo de [UI-A-MARKER-ID-MUST-BRING-ITS-DRAWING-001] |
| `NearbyOrdering.spots` | carril de plazas de Home | **cerrado** — de dos booleanos encadenados a rangos: un estado nuevo tiene que declarar su sitio |
| `ZoneSpotAlertPolicy.isAlertableInZone` | worker de zona + mirada in-app | **cerrado** — `RELEASING -> false` con testigo; el `else` lo habría hecho alertable en silencio |
| `Spot.status` en el peek | `SpotPeek` | **cerrado** — nota propia, y la línea de edad **suprimida** (diría «hace 0 min» de la única plaza ocupada) |
| `SpotStatus.isAvailable` | 3 filtros de `HomeSlices` | **exento, y es la decisión** — `RELEASING` SÍ está disponible: esconderla equivaldría a no publicarla, y el valor entero es que alguien pueda ponerse en marcha |
| `OutboxOp.ReportSpot` (persistido) | cola de sync | **cerrado** — `claim` nullable + el booleano legacy conservado sólo para leer colas escritas por el build anterior |
| workData del `ReportSpotWorker` | jobs en vuelo durante una actualización | **cerrado** — `KEY_CLAIM` nuevo y `KEY_PROVISIONAL` leído como fallback; sin eso, un spot deducido encolado antes heredaría las 2 h |
| `RetractDeducedDepartureUseCase` | fin de sesión | **cubierto por convergencia** — sigue con lo suyo (salidas deducidas); el sello del embarque tiene su propio retirador |

### Lado iOS

Todo el diseño vive en `commonMain` salvo el cableado de los carriles, que es de
`CoordinatorDetectionService` (androidMain). En iOS **no hay carril BT**
[IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001], así que lo que viaja es el estado, el TTL, el tono, el
icono, la fila, el peek y el orden — más `IosReportSpotScheduler`, `IosAppPreferences` (el sello) y
`IosAppNotificationManagerImpl`, ya adaptados y compilando. **Queda escrito el hueco**: nominar
desde el embarque en iOS necesita su propio disparador (`IosDetectionController` no tiene hoy el
equivalente de `ArmAtCar` cableado a esto). No lo hace este ticket.

### No aplica

- **Web**: no toca límites del plan free, ni hechos del build. ⚠️ Sí nombra una feature que
  `paparcar.com` describe (plazas) — pero la web habla de *lo que la app hace*, y lo que la app hace
  no cambia: sigue publicando la plaza que dejas. Que se publique antes no es una promesa nueva.
- **`MockScenario` / routing**: ni pantalla ni estado MVI ni condición de arranque.
- **Room**: ninguna migración — el sello es local y vive en preferencias, por diseño.

## Lo que falta ver en mano

- El puck violeta sobre teselas claras y oscuras, al lado de uno verde y de uno en ruta.
- El medidor de fiabilidad del peek con una plaza `RELEASING`: hoy pinta el tramo medio bajo una
  cabecera que dice «Frescura», y la nota de debajo es la que explica el estado. Es el borde más
  áspero que queda; se decide viéndolo. [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]


---

## Segunda vuelta (user, 23-09) — una plaza se libera UNA vez

> *«Es importante en caso bt diferenciar una salida de cuando se desconecta y se conecta el bt en
> caso de pérdida. Cuando ya con parking aparcado sacamos la primera releasing spot, en ese tramo
> no debería hacerlo más veces: ya sería actualizarlo a spot real y ya está.»*

Tenía razón, y persiguiéndolo apareció un segundo agujero peor.

### 🔴 Agujero 1 — la puerta «ya nominada» llevaba un reloj dentro

Estaba acotada por el TTL de 4 min, así que **un segundo CONNECT pasados esos minutos volvía a
publicar la plaza**. Y había un test bendiciéndolo (`should_publishAgain_when_theEarlierNominationHasExpired`).

La puerta antirebote de arriba **no cubre este caso**: sólo ve una reconexión mientras la caza sigue
abierta (~30 s). Una caída de enlace que vuelve a los diez minutos —un dongle que pierde el móvil,
una radio que despierta— la cruza entera. Y no es nadie embarcando dos veces: es el mismo coche, en
el mismo hueco, que no se ha movido.

**Arreglo:** una nominación por SESIÓN de aparcamiento, sin reloj. Después de la primera, al
documento sólo pueden pasarle dos cosas: la promoción que escribe la salida real, o la retirada.

**Coste, nombrado y aceptado:** si la primera caduca y el conductor se va de verdad veinte minutos
después, esa salida no tiene ventaja — publica su plaza por el camino de siempre. Una ventaja
perdida es el fallo barato; una plaza que se reanuncia sola es el caro.

Es la misma separación que [DET-RETRACT-DENIED-FOREVER-001] ya había dibujado para la salida
deducida: *«¿hay un documento ahí fuera que retirar?»* lleva reloj, *«¿esta sesión ya gastó su
publicación?»* no. La había leído y aun así metí las dos en el mismo sello.

**Consecuencia:** `WithdrawSpotReleaseUseCase` **deja de borrar el sello**. La cota de edad es lo
que impide escribir sobre un documento ya barrido; el sello se queda porque la sesión sigue gastada.

### 🔴🔴 Agujero 2 — «Me voy» dejaba su propia plaza expuesta

`ReleaseActiveParkingSessionUseCase` publica con `spotId = currentSession.id` — **el mismo
documento** — y no retiraba el sello. Secuencia completa:

```
embarque → plaza fuera como RELEASING
«Me voy» → se publica la plaza REAL sobre ese mismo id
fin de la sesión del coordinator, con el sello todavía vivo
  → el teardown RETIRA la plaza que el usuario acaba de publicar
```

Una plaza real retirada segundos después de salir. La publicación y la retirada del sello tienen que
viajar juntas, como ya hacían en el camino automático.

### Testigos

| Si se rompe… | Cae |
|---|---|
| la puerta vuelve a llevar reloj | `EvaluateSpotReleaseNominationUseCaseTest > should_stillSayNothing_when_theLinkDropsAndComesBackLongAfterTheFirstNomination` |
| la retirada vuelve a borrar el sello | `WithdrawSpotReleaseUseCaseTest > should_withdrawTheSpot_when_theBoardingNeverBecameADeparture` |
| «Me voy» no retira el sello | `ReleaseActiveParkingSessionUseCaseTest > should_retireTheBoardingStamp_when_theUserReleasesAPlazaThatWasAlreadyAnnounced` |

Los tres falsificados a mano, fallando, y verdes al restaurar. Suite completa
(`testAll --rerun-tasks`) verde.

### Quién retira el sello ahora — la lista entera

| Camino | Qué hace |
|---|---|
| `ProcessConfirmedDepartureUseCase` (salida automática) | publica el spot real sobre el mismo id → retira el sello |
| `ReleaseActiveParkingSessionUseCase` («Me voy») | ídem — **añadido en esta vuelta** |
| `WithdrawSpotReleaseUseCase` | retira el DOCUMENTO, **no** el sello: la sesión sigue gastada |
| una sesión de aparcamiento nueva | id nuevo: ningún sello viejo puede casar con ella |
