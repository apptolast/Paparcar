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
| **Gasolinera barata cerca** — «a 1,2 km, 8 c/l menos que donde sueles repostar» | Dinero real y recurrente; el precio ya lo tenemos en toda España | Referencia («tu gasolinera») + umbral | 🔵 **en curso** → `FUEL-A-PRICE-REMEMBERED-…-001` |
| **Movimiento diario de precio** — «tu gasolinera ha bajado 3 c/l» | Convierte un dato puntual en vigilancia, que es la definición de Plus | El precio de ayer: hoy la caché lo **borra** en cada escritura | 🔵 **en curso** (mismo ticket, local, 30 días) |
| **Seguir una gasolinera** | Es el SUJETO que les falta a las dos de arriba, no una tercera alerta | Lista de seguidas + consulta dirigida a su provincia | 🔵 **en curso** (mínima, mismo ticket) |
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
| **Alertas de gasolinera** (barata cerca · movimiento diario) | ❌ | ✅ *(en curso)* |
| Estrellar una gasolinera y ver su precio | ✅ | ✅ |
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
