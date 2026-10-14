# FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001 · lo que pasa a tu alrededor se avisa gratis; lo que SIGUES es Plus

**Estado:** ✅ Done (14-10-2026) · rama y worktree borrados al cerrar · ⏳ sin ver en mano (exige
forzar el tick diario y **dos días** de serie) · ⛔ queda **una decisión del user**: si la serie corta
del carril free —3 días, local, pero traza de dónde has estado por aproximación— entra en la política
de privacidad y en Data Safety
**Origen:** decisión del user, 13-10-2026: *«de manera gratuita debería avisar de gasolineras cercanas
si suben o bajan el precio, y de Plus debería ser el seguimiento de gasolinera»*. Las dos preguntas de
alcance las contestó él: el free avisa **las dos cosas** (más barata cerca **y** movimientos de tu
alrededor) y entra por **notificación de verdad**, no por banner in-app.

## Problema

Hoy **las dos** alertas de gasolinera son Plus, y con el mismo gate: `RunFuelPriceWatchUseCase` abre
con `if (!observeIsPlus().first()) return Skipped("not-plus")`, así que un free no recibe ni el aviso
de que la gasolinera de su calle ha bajado 4 c/l. `plus-alerts-and-tiers-001.md` lo cifra en su tabla
—*Alertas de gasolinera (barata cerca · movimiento diario) → ❌ free / ✅ Plus*— y lo único free es
*«estrellar una gasolinera y ver su precio»*.

Eso deja al plan free con el mapa lleno de precios y **sin una sola razón para volver**: el dato que
ahorra dinero de verdad —que el precio de al lado se ha movido— no se le cuenta a quien no paga, que
es exactamente el problema que F5 existe para resolver. Y deja a Plus vendiendo *«vigilancia»* en
abstracto en vez de lo que de verdad no puede hacer el free.

## Doctrina violada… y la que hay que REESCRIBIR

⛔ Este ticket **no corrige un despiste: cambia el eje de Plus para gasolineras**, y por eso lo
primero que entrega es doctrina escrita.

- `plus-alerts-and-tiers-001.md` dice: *«Free = tú preguntas y la app responde. Plus = la app te avisa
  a ti, y recuerda por ti»*, y de ahí deriva que **toda** alerta es Plus. Con un aviso free por
  notificación ese eje **deja de describir** gasolineras: la app también trabaja para el free.
- El eje nuevo, para esta familia: **free = el suelo que pisas, ahora · Plus = el SUJETO que tú
  nombraste, esté donde esté, y su MEMORIA.** Lo que el free no puede tener:
  1. una gasolinera **que no te rodea** (la de la autopista de los viernes, la del pueblo) vigilada
     sin pasar por delante — el carril que [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001] acaba de arreglar;
  2. la **referencia propia**: «8 c/l menos que *donde sueles repostar*» en vez de «menos que la
     mediana de la zona»;
  3. la **serie de 30 días** y lo que se construya sobre ella
     ([`fuel-a-recorded-series-must-be-readable-001`](fuel-a-recorded-series-must-be-readable-001.md)).
- Precedente que sí se respeta: el aviso de plaza ya está partido así en
  [`zone-an-alert-near-you-is-not-an-alert-in-your-zone-001`](zone-an-alert-near-you-is-not-an-alert-in-your-zone-001.md)
  — *cerca de ti* free, *en tu zona* Plus. ⚠️ Con una diferencia que hay que decir en voz alta: ahí el
  carril free es **in-app y solo con el mapa delante**, y aquí el user ha pedido notificación con la
  app cerrada. O sea que **este ticket separa los dos carriles free del producto**, y el de plazas
  no se toca.

## Señales / datos disponibles

- `EvaluateCheaperStationNearbyUseCase` **ya existe y ya sabe** comparar contra la mediana de la zona
  cuando no hay seguidas (`CheaperReference.FOLLOWED` vs. el resto): el aviso free nº 1 es desgatear,
  no construir.
- `NearbyComplement.FUEL ∈ observeIntroducedComplements()` **ya está persistido** en `AppPreferences`
  — es el sello de «este usuario ha abierto el carril de gasolineras alguna vez».
- `FuelWatchRepository.RETENTION_DAYS = 30` y `prune()` ya existen; el DAO ya tiene el patrón de
  subconsulta `NOT IN (SELECT stationKey FROM followed_fuel_stations)` en `deleteOrphanedHistory`.
- `AppNotificationManager` ya tiene dos ids (`CHEAPER_FUEL_NOTIFICATION_ID = 2010`,
  `FUEL_PRICE_MOVE_NOTIFICATION_ID = 2011`) y la regla de **una tarjeta por carril**.

## Diseño

### A · Dos carriles en el MISMO tick, con gate por carril

`RunFuelPriceWatchUseCase` deja de abrir con un `return` por Plus. El gate se parte:

| | Carril ALREDEDOR (free) | Carril SEGUIDAS (Plus) |
|---|---|---|
| Sujeto | las gasolineras de la respuesta centrada en tu fix | las que has estrellado, donde estén |
| Requiere | interruptor ON + haber abierto el carril alguna vez | lo anterior **+ Plus** |
| Avisa | «la más barata cerca» · «una de tu alrededor ha subido/bajado» | «la que sigues ha bajado X» |
| Referencia | mediana de la zona | tu gasolinera |
| Serie | corta (`NEARBY_RETENTION_DAYS`) | 30 días |

⛔ **Una gasolinera seguida pertenece al carril Plus y se EXCLUYE del free**, o el mismo surtidor
saca dos tarjetas el mismo día.

### B · La memoria que falta (y su techo)

«Subió/bajó cerca de ti» necesita el precio de **ayer** de una gasolinera que **no sigues**, y hoy eso
no existe en ninguna parte: la caché `fuel_station_fuels` **reemplaza** los precios en cada escritura
por contrato, y `fuel_price_history` solo guarda seguidas a propósito —*«escribir una fila por cada
gasolinera que pase la cámara haría crecer una tabla sin techo»*.

Se abre esa puerta, acotada y con la razón escrita:
- solo las **N más cercanas con precio** de la respuesta del tick (`NEARBY_SAMPLED_MAX`), no la
  respuesta entera;
- retención **corta** (3 días): para decir «ayer costaba menos» no hace falta un mes, y la ventana
  larga es lo que se vende;
- ⛔ **`deleteOrphanedHistory` deja de tener sentido y se retira**: las filas de alrededor son
  huérfanas por definición, así que ese barrido borraría justo lo que el carril free acaba de medir.
  Lo que hacía —*un unfollow retira el sujeto*— lo pasa a hacer la retención corta: al dejar de seguir
  una gasolinera su serie no desaparece, **encoge** de 30 días a 3. Hay que decirlo en el doc de
  privacidad del repo si allí se describe la retención.
- ⛔ **Privacidad**: una serie diaria de las gasolineras que te rodean es una traza de donde estás,
  por aproximación. Es **local** (Room), no sale del móvil, y eso tiene que quedar escrito donde ya
  se dice de las demás tablas locales. Revisar si toca Data Safety de Play.

### C · El tick existe para todos, y eso se paga en datos

`FuelWatchScheduler` arma el periódico **solo mientras haya algo seguido**. Pasa a armarse con
`notifyFuelPrices && FUEL ∈ introducedComplements`.

⛔ **El sello de «ha abierto el carril» es el freno, y es deliberado**: sin él la app notificaría
precios a quien nunca los ha mirado y bajaría un feed de provincia al día para todo el mundo. Es la
misma idea que el carril free de plazas conseguía exigiendo el mapa delante.

### D · Las superficies que lo dicen

- **Ajustes**: el interruptor de avisos de carburante deja de abrir el paywall (`ToggleFuelAlertsNotif`)
  y deja de leerse como `isPlus && notifyFuelPrices`. Un interruptor, dos carriles: el free recibe lo
  suyo, el Plus además lo de las seguidas. Su descripción tiene que decir **qué** avisa sin nombrar
  planes — quien explica Plus es el paywall, en el momento del toque (decisión del user, 16-09).
- **`PaywallReason.FUEL_ALERTS`** hoy se describe como *«the fuel-price watch — a cheaper pump nearby,
  and what a followed station did overnight»*: la primera mitad pasa a ser free, así que el paywall
  tiene que vender el **sujeto y la memoria**, no «avisos de precio».
- **Tarjeta nueva** para el movimiento de alrededor (id propio, no reutilizar el 2011, que sobreescribe)
  + copy en los 9 locales. ⛔ Nombra **una gasolinera cerca de ti**, nunca «tu gasolinera»: eso es la
  seguida, y confundirlas es regalar el eje de Plus en una frase.
- **La web**: la columna **Gratis** gana la línea del aviso de alrededor; Plus conserva *«Aviso cuando
  baja el precio de una gasolinera que sigues»*, que ya está escrita. `web/content/{es,en}.json`, los
  dos, + `node web/build.mjs` + `WebSiteParityGuardrailTest --rerun-tasks`.
- **CLAUDE.md § Plus vs free** y `plus-alerts-and-tiers-001.md`: el eje nuevo, con la razón.

## Criterio de éxito

- Un usuario **free**, con el interruptor puesto y el carril ya visitado, recibe notificación cuando
  una gasolinera de su alrededor sube o baja, y cuando hay una claramente más barata cerca.
- El **mismo** surtidor no produce dos tarjetas el mismo día.
- Un **Plus** sigue recibiendo lo de sus seguidas, con su referencia y su serie de 30 días; un free
  que sigue una gasolinera **no** recibe el aviso de esa gasolinera salvo que además esté a su
  alrededor.
- Perder Plus **pausa, no olvida**: ni una seguida ni una muestra se borran.
- La serie de las no seguidas no crece sin techo: test de que el tick no escribe más de
  `NEARBY_SAMPLED_MAX` filas y de que la retención corta las barre.
- `./gradlew testAll` + `assembleMockDebug` + galería + `node web/build.mjs --check`.

## Avance

### ✅ Fase 1 · Doctrina (13-10)

- `plus-alerts-and-tiers-001.md`: el principio gana su **enmienda** citada, las dos filas de
  vigilancia cambian de lado, la de «seguir una gasolinera» pasa a ✅ con su hash, y la tabla de
  planes parte la fila de alertas en **alrededor** (free) y **una que sigues** (Plus), con una fila
  nueva para la serie (3 días vs. 30).
- `CLAUDE.md` § Plus vs free: gasolineras declaradas **excepción al eje**, con la razón y con la
  asimetría de los dos carriles free dicha en voz alta para que nadie la «arregle».

### ✅ Fase 2 · El gate (13-10)

- `RunFuelPriceWatchUseCase`: **el plan ya no abre la puerta, decide qué carril corre.** Fuera el
  `if (!isPlus) return Skipped("not-plus")`; dentro `watchesFollowed = isPlus && followed.isNotEmpty()`,
  que es lo único que gobierna la serie, los movimientos y la referencia propia. La referencia del
  aviso de alrededor es la **mediana de la zona** para el free y **tu gasolinera** con Plus — el
  veredicto ya sabía hacer las dos, no se ha tocado.
- El freno del carril free: `NearbyComplement.FUEL ∈ introducedComplements`, con
  `Skipped("never-asked-about-fuel")` cuando nadie ha preguntado nunca y no hay nada seguido.
- `FuelWatchScheduler`: «algo que vigilar» deja de ser «algo seguido» — `combine` del conjunto de
  seguidas con el sello del carril. Sigue **sin** leer el interruptor ni el plan: eso se lee vivo en
  un solo sitio, como ya decía su KDoc.
- Ajustes: el interruptor **deja de ser de pago** (ni paywall en el intent, ni `isPlus && …` en el
  `checked`, que mostraba OFF un carril que estaba corriendo).
- ⛔ Y el paywall de carburante se quedaba **sin puerta**: su único disparador era ese interruptor.
  `PaywallReason.FUEL_ALERTS` cambia de significado —vende el **sujeto**, no «avisos de precio»— en
  su KDoc y en los 9 locales, y su puerta se muda a donde el usuario encuentra el límite: la línea
  bajo la estrella, que sin Plus se viste de enlace y abre el paywall vía
  `HomeIntent.OpenPaywall`. [PAYWALL-A-GATE-THAT-CHARGES-MUST-BE-SOLD-001]
- Tests: `RunFuelPriceWatchUseCaseTest` pasa de 6 a **9 casos** — el free recibe su aviso con
  referencia `AREA_MEDIAN`, el carril de seguidas está apagado sin Plus *y no gasta ni una consulta*,
  el freno del sello salta, y un Plus con seguidas corre aunque nunca abriera el carril. El viejo
  `should_recordNothing_when_thisDriverHasNoPlus` **se retira: describía el producto anterior.**
  `testAll --rerun-tasks` verde (3.899).

### ✅ Fase 3 · La memoria de tu alrededor, y su tarjeta (13-10)

- **El veredicto deja de afirmar un follow.** `EvaluateDailyPriceMoveUseCase` tomaba
  `List<FollowedFuelStation>`, así que meter ahí una de tu alrededor —aunque los campos encajaran—
  le habría dicho al resto del código que el usuario la estrelló. Nace `WatchedForecourt` (clave,
  nombre, sitio): el sujeto de una serie **sin decir por qué se guarda**. La lista blanca es el
  TIPO, no un `if`. El mismo veredicto se llama ahora **dos veces**, con sujetos distintos: «¿se
  movió este precio?» no es una pregunta distinta según quién mire.
- **Dos ventanas en la misma tabla.** `deleteOrphanedHistory` **muere** —borraba toda fila sin follow
  detrás, que desde hoy son la mayoría— y la sustituye `pruneUnfollowedHistoryBefore`: misma
  subconsulta, un `observedDay` de más. `prune()` barre las dos (30 días / 3).
- **Y el unfollow ENCOGE la serie en vez de borrarla.** Lo que hacía antes seguía siendo deseable
  —*un unfollow es el usuario retirando un sujeto*— y **ya no se puede cumplir borrando**: esa
  gasolinera puede seguir estando al lado de tu casa, y se medirá igual que sus vecinas. Lo que el
  unfollow retira de verdad es la ventana larga, que es justo lo que la distinguía de ellas.
- **El muestreo del carril free**, acotado: las `NEARBY_SAMPLED_MAX` = 10 más cercanas **con
  precio** de la respuesta de hoy, excluyendo las seguidas. Con 3 días de retención son ~30 filas
  vivas por conductor, frente a las ~90 que deja una sola seguida. Una sola fábrica de muestras para
  los dos carriles, para que las dos series se escriban con las mismas reglas.
- **La tarjeta**, con su propio id (`NEARBY_FUEL_MOVE_NOTIFICATION_ID`): compartir el 2011
  significaría que el segundo carril del día **sobreescribe** al primero, y el que se perdería es el
  de pago. El carril viaja como TIPO (`FuelMoveLane`) y no como booleano porque decide dos cosas —el
  sujeto del título y la tarjeta—, y un `isFollowed: Boolean` en una llamada de siete parámetros es
  exactamente la forma de llamar «la que sigues» a una gasolinera que nadie estrelló.
- **Un solo string nuevo**, y medido: el título nombra al sujeto y el cuerpo dice dirección y cifra,
  así que las 8 variantes de cuerpo se reutilizan tal cual y solo hace falta
  `notif_fuel_move_title_nearby` (9 locales). El espejo de `composeResources` **no** entra: solo
  cubre las 30 claves que usa iOS, y `NotificationCopyParityTest` compara únicamente lo espejado.
- Tests: `RunFuelPriceWatchUseCaseTest` **12 casos**, `EvaluateDailyPriceMoveUseCaseTest` 11 (juzga
  sujetos), `FuelWatchStoreTest` 5 — con los dos nuevos de las dos ventanas contra Room, que es
  donde vive la consulta. `testAll --rerun-tasks` verde: **3.907**.
- **Falsificados los dos invariantes del carril**, uno a uno: quitando el filtro de seguidas caen 3
  casos (entre ellos el de las dos tarjetas del mismo surtidor); apagando el muestreo de alrededor
  caen 5. El fake del repositorio **dejó de borrar la serie al dejar de seguir** y lo dice en su
  KDoc: no encoge nada porque no modela ventanas, y fingirlo con un reloj inventado sería sustituir
  una mentira por otra.

### ✅ Fase 4 · Las dos superficies que lo cuentan (13-10)

- **La web** (`web/content/{es,en}.json`, reconstruida con `node web/build.mjs`): la columna
  **Gratis** gana *«Aviso cuando una gasolinera cerca de ti cambia de precio, o hay otra más
  barata»* —una línea para los dos avisos free, porque son la misma promesa vista desde fuera— y la
  de **Plus** deja de decir solo «baja»: ahora *«cuando CAMBIA el precio de una gasolinera que
  sigues, esté donde esté»*. Las dos correcciones son la misma: el aviso de seguidas siempre reportó
  subidas **y** bajadas (sus ocho variantes de cuerpo incluyen «más caro»), y «esté donde esté» es
  lo que de verdad se paga.
- **Ajustes** (`settings_notif_fuel_alerts_desc`, 9 locales): lidera con lo que recibe **todo el
  mundo** y deja lo de las seguidas al final. Antes abría por la mitad de pago, así que un free leía
  ahí lo único que no va a recibir. Sigue sin nombrar planes — quien explica Plus es el paywall, en
  el momento del toque (decisión del user, 16-09).
- Guards: `WebSiteParityGuardrailTest`, `LocaleParityGuardrailTest`,
  `ComposeResourcesEscapeGuardrailTest` y `NotificationCopyParityTest` con `--rerun-tasks`, más
  `node web/build.mjs --check`. `testAll` verde.

### ✅ Fase 5 · El marco de la página de precios (14-10)

El user, leyendo el punto 1: *«vale, pues tendremos que cambiar esa dirección»*. Las cuatro frases
que envolvían la tabla eran de antes de la enmienda y contradecían a sus propios items:

| | Antes | Ahora |
|---|---|---|
| Titular | «Mirar es gratis. Que la app vigile por ti, es Plus» | **«Lo de alrededor es gratis. Lo que tú eliges, Plus»** |
| Entradilla | «Plus es para cuando quieres que la app trabaje mientras tú no estás mirando» | añade que lo de alrededor se cuenta gratis, y Plus pasa a ser vigilar **lo que TÚ has elegido, esté donde esté** |
| Columna free | «Tú preguntas, la app responde» | «Lo de aquí y ahora, completo» |
| Columna Plus | «La app te avisa a ti, y recuerda por ti» | «Vigila lo que eliges, y recuerda por ti» |

⛔ **Y no es una concesión a gasolineras.** Ese marco ya describía el reparto de PLAZAS —cerca de ti
free, en tus zonas Plus— que existía desde antes. Lo que se vio al tocar gasolineras es que *«quién
trabaja»* explica bien **por qué** la vigilancia y la memoria se venden, y describe mal **qué**
compra el usuario. Los dos ejes conviven a propósito: el de dentro decide dónde cae una feature
nueva (`plus-alerts-and-tiers-001.md`), el de fuera es lo que se lee en la página de precios. Queda
escrito en los dos sitios y en CLAUDE.md.

### ⏳ Lo que queda, en orden
1. **En mano**: ver una de las dos tarjetas de verdad. El worker es diario, así que para probarlo hay
   que forzar el tick — y la serie necesita **dos días** para que el movimiento exista.
2. ⛔ **La privacidad del carril free sigue sin decidirse**: la serie corta es local y dura 3 días,
   pero es una traza de dónde has estado por aproximación. Falta decidir si entra en la política y
   repasar Data Safety.

## Consumidores auditados

Pendiente de barrer al implementar. Lo ya localizado:
`RunFuelPriceWatchUseCase` (gate), `FuelWatchScheduler` (arme), `FuelPriceWatchWorker` (qué notifica),
`FuelWatchRepository` + `FuelWatchDao` (retención y el `deleteOrphanedHistory` que se retira),
`AppNotificationManagerImpl` (ids y copy), `SettingsViewModel`/`SettingsScreen` (gate del interruptor),
`PaywallReason`, `plus-alerts-and-tiers-001.md`, `CLAUDE.md`, `web/content/{es,en}.json`.
