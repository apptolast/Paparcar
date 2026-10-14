# PLUS-ALERTS-AND-TIERS-001 · Qué es gratis, qué es Plus, y por qué

**Estado:** 📋 Abierto (10-09-2026 · revisado contra el código el **15-09-2026**) — doc de PRODUCTO,
no de código. Sucede en decisiones a [`plus-roadmap-001.md`](plus-roadmap-001.md) §0, que sigue
mandando en la ejecución de la ola 2; esto manda en **dónde cae una feature nueva**.

> ⛔ **Corrección del 15-09.** La versión del 10-09 decía *«ninguna alerta implementada»* y era
> falso en la dirección peligrosa: **la alerta de zona existe desde `MONEY-001`**, con gate Plus
> vivo y master switch en Ajustes. Lo que no existe es su **entrega**: `ObserveZoneSpotAlertsUseCase`
> lo colecta el `AppViewModel`, así que muere con el proceso. O sea que lo único que ya cobramos es
> lo que casi nunca llega — que es peor que no haberlo hecho. → ticket
> [`zone-an-alert-nobody-is-awake-to-receive-001.md`](zone-an-alert-nobody-is-awake-to-receive-001.md).

> ⛔ **Cómo se usa este doc** (regla del user, 10-09): nada de aquí se implementa por iniciativa
> propia. Cada cosa entra **cuando se decide implementarla**, con su ticket y su worktree; lo demás
> se queda escrito y esperando. Al terminar cualquier tarea, **recordarle qué sigue pendiente aquí**
> — el valor de esta lista es que alguien la recite, no que exista.

## El principio, para no volver a discutirlo caso por caso

> **Free = tú preguntas y la app responde. Plus = la app te avisa a ti, y recuerda por ti.**

No es «cuántas cosas ves», y por eso las alertas de zona ya eran Plus mientras ver el mapa era
gratis. El eje es **quién trabaja**: mirar es gratis, que la app vigile mientras no miras se paga.

> ⛔ **Enmienda del user, 13-10-2026 — gasolineras salen de ese eje**
> [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]. *«De manera gratuita debería avisar de gasolineras
> cercanas si suben o bajan el precio, y de Plus debería ser el seguimiento»*, y preguntado por el
> canal eligió **notificación de verdad**, no banner con el mapa delante.
>
> Así que en esta familia el free **sí** recibe trabajo de la app, y el eje pasa a ser otro:
> **free = el suelo que pisas, ahora · Plus = el SUJETO que tú nombraste, esté donde esté, y su
> MEMORIA.** Lo que el free no puede tener son las tres cosas que no se deducen de estar allí:
> 1. una gasolinera **que no te rodea** —la de la autopista de los viernes, la del pueblo— vigilada
>    sin pasar por delante;
> 2. la **referencia propia**: «8 c/l menos que *donde sueles repostar*», frente a la mediana de la
>    zona que ve el free;
> 3. la **serie de 30 días** y lo que se construya encima.
>
> ⚠️ Y la consecuencia incómoda, dicha en voz alta para que nadie la "arregle": **los dos carriles
> free de avisos del producto dejan de tener la misma forma.** El de plazas es in-app y sólo con el
> mapa delante ([`zone-an-alert-near-you-is-not-an-alert-in-your-zone-001`](zone-an-alert-near-you-is-not-an-alert-in-your-zone-001.md));
> el de gasolineras es notificación con la app cerrada. No unificarlos «por coherencia» sin el user:
> la asimetría es la decisión, no un descuido.

> ⛔ **Y el 14-10 se cambió el marco de FUERA**, porque el de dentro se le había quedado grande. El
> titular de la sección de precios de la web decía *«Mirar es gratis. Que la app vigile por ti, es
> Plus»* — justo encima de un item free que es la app avisando sin que nadie le pregunte. Ahora dice
> **«Lo de alrededor es gratis. Lo que tú eliges, Plus»**, y las dos columnas se titulan por ahí
> (*«lo de aquí y ahora, completo»* · *«vigila lo que eliges, y recuerda por ti»*).
>
> Eso **no es una concesión a gasolineras**: ese marco ya describía el reparto de PLAZAS —cerca de
> ti free, en tus zonas Plus— que existía desde antes de esta enmienda. Lo que se vio al tocar
> gasolineras es que **«quién trabaja» explica bien por qué la vigilancia y la memoria se venden, y
> describe mal lo que el usuario compra**. Las dos frases pueden convivir, y de hecho deben: el eje
> de dentro decide dónde cae una feature nueva (esta sección), y el de fuera es lo que se lee en la
> página de precios. Lo que no puede volver a pasar es que la de fuera contradiga a sus propios
> items.
De ahí salen dos ejes de Plus y uno solo de free:

- **Vigilancia** — la app trabaja en segundo plano y te interrumpe cuando merece la pena.
- **Memoria** — lo que pasó más allá de la ventana free (hoy 7 días).
- **Free** — la respuesta puntual a la pregunta que acabas de hacer, completa y sin recortes.

⛔ **Lo que NO se gatea nunca, y esto es una decisión firme:** ver plazas, parkings públicos y
gasolineras cercanas, sus precios, y cómo llegar. Cobrar por eso vacía el mapa justo para quien no
paga — que es *exactamente* el problema que F5 y F6 existen para resolver. Un mapa vacío no vende
Plus: hace desinstalar.

## Vigilancia — candidatas, con su coste real

| Alerta | Por qué encaja | Qué hace falta | Estado |
|---|---|---|---|
| **Plaza libre en tu zona** | La que estrenó el eje | — | 🟡 **hecha, sin entregar** → `ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001` |
| **Gasolinera barata cerca** — «a 1,2 km, 8 c/l menos de lo normal por aquí» | Dinero real y recurrente; el precio ya lo tenemos en toda España | Nada nuevo: el veredicto existe y sabe comparar contra la mediana de la zona | 🔵 **pasa a FREE** (13-10) → `FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001`. Con referencia propia («donde sueles repostar») sigue siendo Plus |
| **Movimiento diario de precio** | Es el aviso que ahorra dinero, y por eso se parte en dos | El precio de ayer. Para una SEGUIDA ya existe (serie de 30 días, `c765301c5`); para una de tu alrededor hay que abrir una serie corta y acotada, porque la caché lo **borra** en cada escritura | 🔵 **free el de tu ALREDEDOR · Plus el de una SEGUIDA** → `FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001` |
| **Seguir una gasolinera** | Es el SUJETO que les falta a las dos de arriba, no una tercera alerta — y desde el 13-10 es **lo que vende Plus** en esta familia | Lista de seguidas + consulta dirigida a SU sitio | ✅ `c765301c5` — la marca se ve en fila y peek, y la muestra se toma donde está la gasolinera, no donde estás tú |
| **Aviso de gálibo** — «este parking tiene 1,9 m; tu furgoneta mide 2,05» | El problema real de furgonetas y SUV altos, y el que más caro sale equivocarse | ⛔ **no es «nada nuevo»**: `maxHeightMeters` existe, pero `CarbodyType` **no lleva altura** | 📋 aparcada — ticket de modelo + migración |
| **Parking habitual lleno / con hueco** | Es la pregunta que la gente hace de verdad antes de salir | Ocupación en tiempo real | 📋 **F6b** (`PARKINGS-LIVE-OCCUPANCY-001`) |
| **Aviso de depósito** — «~450 km desde tu último repostaje» | Usa consumo + km que ya se calculan para las estadísticas | Marcar repostajes (o inferirlos de una parada en gasolinera) | 📋 F3 + UI de repostaje |
| **Serie larga de precios / multi-dispositivo** | El eje memoria en su forma vendible | Almacenamiento en servidor | 📋 **backend** |

⛔ **Lo aparcado no se planifica sin nombrar lo que le falta debajo**:
[`infra-an-alert-that-travels-needs-a-backend-001.md`](infra-an-alert-that-travels-needs-a-backend-001.md)
lleva la cuenta de lo que hay que montar — **FCM, Cloud Functions, suscripciones en servidor** — y
de qué alerta desbloquea cada pieza. Hoy no hay ninguna de las tres.

## Memoria — lo que free solo enseña 7 días

- **Gasto real de combustible** por coche y periodo — casi hecho en las estadísticas de F3.
- **«Cuánto te habrías ahorrado»** repostando siempre en la más barata de tu zona. Sale de datos que
  ya existen y es una cifra que se vende sola.
- **Tus parkings y gasolineras frecuentes**, con lo que gastas en cada uno.

## La tabla, al día con F5 y F6

| | Free | Plus |
|---|---|---|
| Detección automática y mapa comunitario | ✅ completo | ✅ |
| **Parkings públicos** (ver, tipo, tarifa, gálibo, cómo llegar) | ✅ **completo** | ✅ |
| **Gasolineras** (ver, precio real, cómo llegar) | ✅ **completo** | ✅ |
| Historial | 7 días | ilimitado |
| Coches propios · zonas | 1 · 2 | ilimitados |
| Estadísticas | todas, últimos 7 días [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] | todas, cualquier periodo |
| Alertas de zona | ❌ | ✅ *(entrega en curso)* |
| **Alertas de gasolinera · tu ALREDEDOR** (barata cerca · sube/baja una de al lado) | ✅ *(en curso)* | ✅ |
| **Alertas de gasolinera · una que SIGUES** (esté donde esté, con tu referencia) | ❌ | ✅ |
| Estrellar una gasolinera y ver su precio | ✅ | ✅ |
| Serie de precios de una seguida | ❌ *(3 días de las de tu alrededor)* | ✅ *(30 días)* |
| **Alertas de parking** (lleno / con hueco) | ❌ | ✅ *(bloqueada: F6b + FCM)* |
| Coche compartido (F8) · rutas · anuncios | ❌ · limitadas · con | ✅ · ilimitadas · sin |

## Avisos para quien lo ejecute

- ⚠️ **Casi toda la vigilancia necesita backend**: FCM para notificar y almacenamiento para el
  histórico compartido. Llega naturalmente **después de F6b**, que es quien estrena las Cloud
  Functions del proyecto. Planificar una alerta antes de eso es planificar el backend sin decirlo.
- ✅ **Pero «necesita backend» no es lo mismo que «necesita push»**, y el 15-09 se midió la
  diferencia: un aviso **decidible con lo que el móvil ya sabe** (el precio de una seguida, una
  plaza dentro de tu zona) se entrega con un worker y cuesta batería; uno que depende de enterarse
  de algo ocurrido en otro sitio no tiene sustituto local. Las dos primeras alertas salen por ahí.
- ⛔ **El gálibo ya no es «la excepción que va primero»**: se dio por hecho que los dos datos estaban
  en el modelo y **el del coche no está** — `CarbodyType` solo lleva `sizeCategory`. Sigue siendo
  barata y sigue disparándose en el mejor momento, pero empieza por una migración.
- 📣 El copy dice **car park** para la instalación y **plaza** solo para lo comunitario
  [COPY-SPOT-IS-NOT-A-PARKING-001]: una alerta de gasolinera no avisa de una «plaza».
- ⛔ **Gating soft, sin excepciones** (doctrina de `MONEY-001`): la acción dispara el paywall, el
  botón nunca se esconde. Una alerta que no se ve no se puede desear.
- ⛔ Al perder Plus **nada se borra**: las alertas se pausan, no se olvidan.
- 📣 Todo lo que entre aquí toca **la ficha de Play en 9 idiomas** y el copy in-app: lo que la app
  hace nuevo tiene que poder leerse dentro de la app.
