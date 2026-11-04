# OVERPASS-A-QUEUE-OF-ONE-MUST-LET-THE-USER-GO-FIRST-001 · la cola de Overpass no sabe quién está esperando

**Estado:** 🔵 Abierto · sin empezar · sin rama · **sale de una observación del user** durante
[UX-THE-HOME-MUST-EARN-ITS-FIRST-GLANCE-001](ux-the-home-must-earn-its-first-glance-001.md):
*«si preguntamos de 1 en 1, ¿por qué no pedir primero la de parkings?»*

## Problema

`OverpassQuota` serializa todas las consultas de Overpass de la app a **una a la vez**
(`CONCURRENT_ASKS = 1`), y lo hace con un `Semaphore(1)` y una espera acotada. Un semáforo **no es
una cola de prioridad**: reparte por orden de llegada, y **no tiene forma de saber si al otro lado
hay un usuario mirando una pantalla en blanco o un refresco de fondo que nadie ha pedido**.

La colisión no es hipotética: **la nombra el propio KDoc de la clase**, como justificación de por
qué la cola es de uno —

> *«One ask at a time also means a lane never competes with itself: the map matcher fetches roads
> **during a trip** (`HomeTripController`), which is exactly when the driver opens the car-parks lane
> on arrival.»*

Es decir: el autor ya detectó que el emparejador de calles y el carril de parkings chocan **justo al
llegar**, y lo resolvió **serializando**, que evita el 504 pero no decide **quién pasa primero**. Hoy
pasa primero el que llegó antes — y el que llega antes, en ese escenario, es el de fondo.

## Doctrina violada

Ninguna regla escrita. Lo que falla es que la clase resuelve **«¿puedo preguntar ya?»** y
deliberadamente no resuelve **«¿quién de los dos?»**, y ese segundo hueco se nota en pantalla.
⛔ No es un bug de `OverpassQuota`: es una pregunta que nadie le ha hecho todavía.

## Señales / datos disponibles

Todo medido el 14-09-2026 contra `overpass-api.de` y escrito en el repo:

| Hecho | Dónde está |
|---|---|
| `Rate limit: 2` por IP; tomamos **1** porque la IP se comparte (operadoras ponen miles de abonados detrás de una) | KDoc de `OverpassQuota` |
| **seis** consultas seguidas → dos respuestas y **cuatro 429**, de 7 a 8,5 s cada uno | idem |
| **cuatro a la vez** → un 429 y **tres 504 a 14,1 s**, para una consulta que sola contesta en **1,4 s** | idem |
| Overpass **buffea**: nada llega hasta que termina de calcular | KDoc de `OverpassWalkPatience` |
| Madrid **1,4 s en caliente / 4,68 s en frío** · París 3,90 s · Málaga **7,72 s por fibra** · Barcelona 504 a 8,06 s · Berlín 429 a 9,48 s | idem |
| El paseo de espejos está acotado a **30 s** (`WALK_CEILING_MS`) | idem |

**Los consumidores de Overpass, medidos** (`grep -rln "OverpassClient(" shared/src/commonMain`):

| Consumidor | ¿Lo espera el usuario? |
|---|---|
| `OsmPublicParkingsSource` (carril de parkings) | **sí**, mirando la lista |
| `OverpassPlacesDataSource` (POI del sitio aparcado) | **sí**, al confirmar un pin |
| `OverpassRoadNetworkDataSource` (red de calles) | **no** — corre durante el viaje |
| `OsmFuelStationsSource` (fallback de gasolineras) | sí, pero sólo cuando el feed nacional falló |
| `FranceFuelStationsSource` (nombres por id oficial) | enriquecimiento, no bloquea |

## ⛔ Y un hallazgo que corrige la premisa de la pregunta

**En España, parkings y gasolineras NO comparten cola.** Verificado:

| Fuente de gasolineras | Transporte |
|---|---|
| `MineturStationsSource` (ES) | **feed propio** del ministerio |
| `GermanyFuelStationsSource` (MTS-K) | **feed propio** |
| `ItalyFuelStationsSource` | **feed propio** |
| `FranceFuelStationsSource` | **Overpass** (sólo los nombres) |
| `OsmFuelStationsSource` (fallback) | **Overpass** |

Así que *«pedir primero los parkings»* no hace falta en España: **van por tuberías distintas y pueden
ir a la vez**. La competencia real por el hueco de Overpass es entre **parkings · POI del sitio
aparcado · red de calles**, y ahí sí hay que decidir el orden.

> La pregunta del user era la correcta; el sujeto no. No es *gasolineras vs parkings*, es
> **el usuario vs el fondo**.

## Diseño

La pieza es `OverpassQuota`, y el invariante que le falta es uno:

> **Quien tiene a alguien esperando pasa primero.**

Forma propuesta (a validar al abrir el ticket, no decidida aquí):

- Un `enum` de **dos** valores y no más — `Foreground` / `Background`. Tres niveles es una escala que
  nadie sabrá asignar; dos es una pregunta que cualquiera puede contestar: *¿hay una pantalla
  esperando esto?*
- `withSlot(priority)` sirve a los `Foreground` antes que a los `Background` en espera.
- ⛔ **Sin inanición**: un `Background` que lleva esperando más de X pasa. Si no, la red de calles
  durante un viaje largo nunca llega a correr.
- ⛔ **La espera acotada se queda** (`WAIT_CEILING_MS`): *«apilar un segundo paseo detrás de uno que
  ya está expirando es cómo un fallo de 30 s se convierte en uno de 60»*.
- ⛔ **`CONCURRENT_ASKS` sigue siendo 1.** Este ticket **no** sube el paralelismo: está medido que
  empeora (cuatro a la vez → tres 504 a 14,1 s).

### ⚠️ El techo de lo que la prioridad puede arreglar, y la pregunta que deja abierta

**Una prioridad decide quién pasa DESPUÉS, no interrumpe a quien ya está dentro.** Y eso acota el
beneficio, así que conviene decirlo antes de prometer nada:

| Situación | ¿La prioridad ayuda? |
|---|---|
| el hueco está libre y llegan dos a la vez | ✅ sí, del todo |
| hay varios esperando y entra uno de usuario | ✅ sí: se pone primero de la fila |
| un `Background` **ya está preguntando** y tiene 30 s de paseo por delante | ❌ **no**. El de usuario espera a que acabe, hasta el tope de `WAIT_CEILING_MS`, y si no le llega el turno recibe `null` |

Ese tercer caso es justo el que el KDoc describe —la red de calles pidiendo **durante** el viaje y
el carril de parkings abriéndose **al llegar**— así que no es el caso raro: es el caso.

Para cubrirlo habría que **cancelar** la consulta de fondo en vuelo cuando entra una de usuario. Es
posible (`withSlot` ya es cancelable y `Semaphore.acquire` es atómico bajo cancelación), pero es otra
cosa y trae sus propias preguntas:
- una consulta cancelada **ya se pagó** en cuota del servidor, así que cancelar no devuelve el hueco
  a nadie más que a nosotros;
- la red de calles cancelada a mitad deja un viaje con un tramo sin emparejar, y hay que decidir si
  se reintenta o se pierde.

⛔ **Este ticket se queda en la prioridad de la COLA.** La cancelación se nombra aquí para que no
parezca un olvido, y si el user la quiere, es su propio ticket — con la decisión de qué pasa con el
tramo del viaje por delante.

## Criterio de éxito

Un test que, con el hueco ocupado por un `Background`, demuestre que un `Foreground` que llega
después **entra antes** que otro `Background` que llevaba más tiempo esperando. Y otro que demuestre
que el `Background` no se queda fuera para siempre.

⛔ Y la falsificación: con la prioridad quitada, los dos tests tienen que ponerse rojos.

## Consumidores auditados

Los cinco de la tabla de arriba tendrán que declarar su prioridad **en la misma tarea** — un
consumidor sin declarar es un consumidor que se cuela. Candidatos según «¿lo espera el usuario?»:
`OsmPublicParkings` y `OverpassPlaces` → `Foreground`; `OverpassRoadNetwork` → `Background`;
los dos de combustible → a decidir con el user (el fallback sí bloquea una lista).

⚠️ **Y es prerrequisito de una tarea del rediseño de la home**: la 2.3 (`interaction-spec.md` §8.3)
añade un **sexto** demandante —el refresco del carrusel al arrancar— que **debe** ir en
`Background`. Sin este ticket, ese refresco puede colarse delante del carril que el usuario está
mirando.
