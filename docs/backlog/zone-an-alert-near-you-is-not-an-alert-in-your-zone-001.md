# ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001 · el aviso que se pidió no es el que se implementó

**Estado:** ✅ Done (27-09-2026) · rama `feature/ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001-nearby-lane` · worktree `../Paparcar-nearby-alert` · ⏳ sin ver en campo

## Problema

El user pidió un aviso de **plaza cerca de donde estás AHORA**. Lo que se implementó es el aviso de
**plaza dentro de una zona guardada**, con un canal nuevo. Son dos features distintas y se
confundieron al implementarlas; lo dijo el user el 27-09: *«me refería a que te saliera un SPOT CERCA
DE DONDE ESTÁS, NO CERCA DE TU ZONA, son cosas distintas y parece que te liaste al implementarlo»*.

La petición está citada textualmente **dentro** del commit que la implementó, `9af3331097`
[ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001], en el KDoc de `AlertChannel`:

> *«cuando el usuario esté viendo la pantalla, si aparece un spot NUEVO se le notificará también al momento»*

Ahí no hay ninguna zona. Y sin embargo el banner (`PapSpotAlertBanner`, colgado en `App.kt` desde
`AppViewModel`) se alimenta de `ObserveZoneSpotAlertsUseCase`, que emite sólo lo que cae dentro de
una zona con `Zone.notifyOnSpot` encendido. Consecuencia medida en el código:

- `Zone.notifyOnSpot` nace en **`false`** (`Zone.kt:32`).
- Encenderlo abre el **paywall** siendo free, por las dos puertas: `HomeViewModel` `SetZoneNotify`
  y `SettingsViewModel` `ToggleZoneAlertsNotif`.
- → Para un usuario sin zonas, o free, **el banner no salta nunca**. La feature que se pidió no
  existe en ninguna versión, ni en 1.3.0 ni en master.

Un efecto colateral del lío: al implementarlo se quitó el gate de Plus **del stream de zonas**
(`ObserveZoneSpotAlertsUseCase` ya no lee `ObserveIsPlusUseCase`) y se invirtieron los dos tests que
lo cifraban, con el KDoc diciendo *«no será de pago, al menos al principio»*. Pero el aviso de zona
**sí sigue siendo de pago** — lo confirma el user el 27-09, y lo hacen cumplir los dos interruptores.
O sea que el gate quitado era del carril equivocado: no cambia el comportamiento (nadie puede
encenderlo gratis), pero deja un KDoc y dos tests afirmando una regla de producto que es falsa.

## Doctrina violada

- **Una afirmación en un comentario no se ejecuta.** El KDoc de `ObserveZoneSpotAlertsUseCase` dice
  que la alerta ya no es de pago; los dos interruptores dicen que sí. Gana el interruptor.
- **El eje de Plus** (`plus-alerts-and-tiers-001.md`): *free = tú preguntas y la app responde · Plus
  = la app te avisa a ti*. Las dos features caen en lados distintos de ese eje, y por eso mezclarlas
  no es un detalle:
  - *plaza cerca de donde estás, con el mapa delante* → **free**. No hay vigilancia que pagar: el
    mapa ya está abierto, ya tiene tu ubicación y ya está consultando ese terreno. Es la
    demostración de lo que hace la app — y nadie paga por una vigilancia que no ha visto funcionar.
  - *plaza en una zona tuya, con la app cerrada* → **Plus**. Ahí sí trabaja la app mientras no miras:
    índice en servidor, fanout y push.

## Señales / datos disponibles

- `ObserveNearbySpotsUseCase` ya observa el terreno alrededor de un punto
  (`DEFAULT_SEARCH_RADIUS_METERS` = 2 km) y es lo que pinta el mapa: el dato ya está en memoria.
- `MapVisibility` (de `9af3331097`) ya responde «¿está el mapa delante?», que es la condición exacta
  del carril free.
- `alertChannelFor(mapOnScreen)` ya elige canal; para el carril cerca-de-ti sólo existe `IN_APP`.

## Diseño, tal como quedó

**Dos carriles, dos preguntas, y un invariante compartido.**

| | Zona (ya existía) | Cerca de ti (nace aquí) |
|---|---|---|
| Ancla | un sitio que guardaste | **tu cuerpo** (`state.userGpsPoint`) |
| Radio | `max(zona, 500 m)` | `NEARBY_SPOT_ALERT_RADIUS_METERS` = 500 m |
| Vive | también con la app cerrada (worker + push) | sólo con el mapa delante |
| Plan | **Plus** | **free** |
| Canal | in-app o notificación, según `alertChannelFor` | **siempre in-app**, por construcción |
| Memoria | durable (`ZoneWatchRepository`) | de sesión, en el controller |

- **`domain/spot/SpotAlertEligibility.kt` · `isSpotWorthAnAlert(spot, ownUserId, nowMs)`** — los vetos
  que no son de ningún carril: retirada, provisional, liberándose, caducada, y **la propia**. Estaban
  dentro de `isAlertableInZone`, atados al círculo de la zona, y nunca fueron reglas de zona: estaban
  en el único fichero que las necesitaba. Regla del user, 27-09: *una plaza que deja un usuario no se
  le anuncia a ese usuario* — en este carril es el caso más probable de todos, porque la plaza que
  acabas de liberar está a metros de ti.
- **`domain/spot/NearbySpotAlertPolicy.kt` · `nearbySpotsWorthTelling(...)`** — puro y síncrono, en la
  forma que el carril de zona ya había establecido (función de nivel superior, sin ceremonia de clase
  inyectada). Decide: qué círculo, qué es novedad, y **una sola plaza por aviso, la más cercana**.
- **`presentation/home/HomeNearbyAlertController`** — la memoria de sesión y el disparo. En Home, como
  los otros cuatro controllers, porque es el único sitio donde ya están las dos entradas que hay que
  cruzar: el fix y las plazas vivas. Pedirlas a nivel de app serían dos suscripciones y dos verdades
  sobre dónde estás.
- **`domain/notification/NearbySpotAlerts`** — hermano de `MapVisibility` y por la misma razón: una
  señal, un dueño. Home decide, `AppViewModel` entrega, y entrega por **la misma ranura** que el aviso
  de zona (`showSpotAlertInApp`, nueva y única) — dos emisores de un solo hueco sin un punto común es
  cómo el temporizador se habría quedado clavado sin que ninguno pareciera culpable.
  [NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001]
- **`SpotAlertCue.origin: SpotAlertOrigin`** — `InZone(zoneName)` | `NearYou`. El campo era
  `zoneName: String` y el carril nuevo no tiene zona: meter `""` para decir «cerca de ti» es la clase
  de codificación implícita que se podre. Con el tipo, un tercer carril no compila sin elegir su frase.

### Las dos decisiones del user (27-09)

- **Radio 500 m.** El mismo valor que el suelo del carril de zona, y **dos constantes a propósito**:
  aquél es «la zona más ancha que se puede dibujar», éste es «hasta dónde andaría desde donde estoy».
  Misma respuesta hoy, preguntas distintas — la lección que ya pagamos con `ctaFitsInline`.
- **Salta también conduciendo**, porque es justo cuando una plaza cerca importa: dando vueltas
  buscando sitio. ⛔ Y de ahí sale una regla que no es cosmética: el ancla **tiene** que ser tu fix y
  no la cámara, porque la suscripción del mapa sigue a la cámara (5 km) y mediría «cerca» desde un
  sitio que sólo estás mirando.

### Lo que decidí yo, y queda dicho

**La pauta entre avisos**, `NEARBY_SPOT_ALERT_MIN_GAP_MS` = 45 s, **derivada y no elegida**: a 30 km/h
(8,3 m/s) el círculo de 500 m se renueva entero en ~60 s, así que 45 s permite como mucho un aviso por
círculo nuevo. Sin pauta, una calle densa convierte el banner en un parpadeo.
⛔ Una plaza callada por la pauta **no se apunta como contada**: espera y se anuncia al levantarse.
Apuntarla sería la diferencia entre una cola y una fuga.

Y la que va con ella: **la baseline apunta sólo lo que está DENTRO del círculo.** Eso es lo que hace
el carril de proximidad y no de novedad — una plaza que aparece a 3 km no se apunta, así que sigue
siendo noticia cuando llegues a 500 m de ella. Apuntar los 5 km enteros habría sido más limpio y
habría matado en silencio el caso para el que existe: dando vueltas, casi toda plaza ya estaba ahí
antes de que te acercaras.

## Criterio de éxito

Con el mapa delante, sin Plus y **sin ninguna zona creada**, aparecer una plaza nueva a menos de 500 m
enseña el banner una sola vez. Antes de este ticket ese test no podía pasar.

## Consumidores auditados — ¿quién decide si una plaza se anuncia?

| Decisor | Estado |
|---|---|
| `isAlertableInZone` (carril de zona, cliente) | ✅ ahora llama a `isSpotWorthAnAlert`; su test previo `ObserveZoneSpotAlertsUseCaseTest` sigue verde, y **se pone rojo** al quitar el veto |
| `nearbySpotsWorthTelling` (carril nuevo, cliente) | ✅ mismo `isSpotWorthAnAlert` |
| `functions/src/fanout.ts:158` (push, servidor) | ✅ **ya excluía al autor** (`sub.uid === spot.reportedBy`) — auditado, no tocado: es la puerta del push y su regla vive en el servidor por necesidad |
| `ReportSpotReleasedUseCase` / `ReportSpotScheduler` | ✅ exentos: comparan autoría para los permisos de Firestore (editar/borrar), no para avisar |
| `alertChannelFor` | ✅ exento: el carril nuevo no lo usa y está dicho por qué — es in-app por construcción |

⛔ **Lo que este ticket NO arregla**, y queda abierto: el KDoc de `ObserveZoneSpotAlertsUseCase` y los
dos tests invertidos siguen afirmando que el aviso de zona *«no será de pago»*. El user confirmó el
27-09 que **sí es de pago**, y lo hacen cumplir los dos interruptores (`HomeViewModel.SetZoneNotify`,
`SettingsViewModel.ToggleZoneAlertsNotif`). No se toca aquí porque cambiarlo es decidir sobre el
carril de zona, no sobre éste — pero es una afirmación falsa por escrito y merece su ticket.

## iOS

**Paridad por construcción, y aquí eso es literal:** las cuatro piezas nuevas —el veredicto, la
política, el controller y la señal— viven en `commonMain`, y la entrega es el mismo banner de Compose
Multiplatform. No hay nada que escribir en `iosMain` porque este carril **nunca toca la bandeja del
sistema**: lo único de plataforma del carril de zona era `AppNotificationManager`, y de eso es
precisamente de lo que este se libra por diseño.

⏳ **Sin ver en mano.** El cambio es visible (banner nuevo, frase nueva), así que falta una captura de
la variante «Aviso · plaza nueva cerca de ti» de la galería, o del banner saltando en device. No es
comprobable desde aquí.

## Verificación

- `./gradlew testAll --rerun-tasks` → ver el resumen del commit.
- Tests nuevos: `NearbySpotAlertPolicyTest` (12) y `HomeNearbyAlertControllerTest` (5), más dos de
  entrega en `AppViewModelTest`.
- **Falsificado el veto de plaza propia** quitándolo de `isSpotWorthAnAlert`: caen los TRES niveles a
  la vez — la política pura, el controller (que es lo que prueba que el uid llega de verdad al
  veredicto) y el test ya existente del carril de zona (que prueba que la extracción no perdió nada).
- `assembleMockDebug` + `compileProdDebugKotlin` OK. Variante nueva en la galería, al lado de la de
  zona, para que se vea si algún día vuelven a decir lo mismo.
- 9 locales para `spot_alert_nearby_in_app`, sin `'` ni `%%`.

⛔ **`docs/detection/PARKING-DETECTION.md` no se toca, y es deliberado**: ese log es de la detección de
aparcamiento (tickets `DET-*`), y esto es el reparto de avisos de plaza — ningún `ZONE-*` está ahí. Un
aviso no coloca ningún pin, así que no hay `detectionPath` ni `armEvidence` que ampliar.
