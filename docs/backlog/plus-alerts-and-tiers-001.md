# PLUS-ALERTS-AND-TIERS-001 · Qué es gratis, qué es Plus, y por qué

**Estado:** 📋 Abierto (10-09-2026) — doc de PRODUCTO, no de código. **Ninguna alerta
implementada: esto es una LISTA DE ESPERA, no un plan en marcha.**
Sucede en decisiones a [`plus-roadmap-001.md`](plus-roadmap-001.md) §0, que sigue mandando en la
ejecución de la ola 2; esto manda en **dónde cae una feature nueva**.

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

| Alerta | Por qué encaja | Qué hace falta | Depende de |
|---|---|---|---|
| **Aviso de gálibo** — «este parking tiene 1,9 m; tu furgoneta mide 2,05» | El problema real de furgonetas y SUV altos, y el que más caro sale equivocarse | **NADA nuevo**: `PublicParking.maxHeightMeters` (OSM) × `CarbodyType`/`VehicleSize` del coche | — ✅ hoy |
| **Gasolinera barata cerca** — «a 1,2 km, 8 c/l menos que donde sueles repostar» | Dinero real y recurrente; el precio ya lo tenemos en toda España | Umbral + noción de «tu gasolinera habitual» | backend (FCM) |
| **Seguir una gasolinera** — avisar si sube o baja de X | Convierte un dato puntual en vigilancia, que es la definición de Plus | **Histórico de precios** — nadie lo guarda hoy | backend + almacenamiento |
| **Parking habitual lleno / con hueco** | Es la pregunta que la gente hace de verdad antes de salir | Ocupación en tiempo real | **F6b** (`PARKINGS-LIVE-OCCUPANCY-001`) |
| **Aviso de depósito** — «~450 km desde tu último repostaje» | Usa consumo + km que ya se calculan para las estadísticas | Marcar repostajes (o inferirlos de una parada en gasolinera) | F3 + UI de repostaje |

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
| Estadísticas | cifra-teaser | avanzadas (gasto, consumo, CO₂) |
| Alertas de zona | ❌ | ✅ |
| **Alertas de parking y gasolinera** (las de arriba) | ❌ | ✅ *(futuro)* |
| Coche compartido (F8) · rutas · anuncios | ❌ · limitadas · con | ✅ · ilimitadas · sin |

## Avisos para quien lo ejecute

- ⚠️ **Casi toda la vigilancia necesita backend**: FCM para notificar y almacenamiento para el
  histórico de precios. Llega naturalmente **después de F6b**, que es quien estrena las Cloud
  Functions del proyecto. Planificar una alerta antes de eso es planificar el backend sin decirlo.
- ✅ **El aviso de gálibo es la excepción y por eso va primero**: los dos datos ya están en el
  modelo, no necesita red ni backend, y se puede hacer en un ticket corto. Es la mejor primera
  alerta también por otra razón: se dispara justo cuando el usuario está a punto de equivocarse.
- ⛔ **Gating soft, sin excepciones** (doctrina de `MONEY-001`): la acción dispara el paywall, el
  botón nunca se esconde. Una alerta que no se ve no se puede desear.
- ⛔ Al perder Plus **nada se borra**: las alertas se pausan, no se olvidan.
- 📣 Todo lo que entre aquí toca **la ficha de Play en 9 idiomas** y el copy in-app: lo que la app
  hace nuevo tiene que poder leerse dentro de la app.
