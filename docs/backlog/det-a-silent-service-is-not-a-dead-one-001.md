# DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001 · «no ha hablado» no es «lo mataron»

**Estado:** 🔵 Código escrito y falsificado, **sin commitear** · rama `feature/HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001-phase1` ·
worktree `../Paparcar-glance`

Sale de revisar los avisos de Home con el user, dentro de
[`HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001`](home-the-first-glance-speaks-for-the-car-001.md).
Su pregunta era de verificación —*«el badge que aparece abajo del sheet cuando faltan permisos o hay
vigilancia muerta sigue estando no?»*— y la fila estaba intacta. Lo que se destapó al comprobarla es
que **se mostraba cuando no debía**.

## Problema

`ServicePresence` valía **`Dead` por defecto** en los tres sitios que lo inicializan: el default de
la interfaz (`ALWAYS_DEAD`), el holder de producción (`_presence`) y el doble de tests.

Pero ese flujo lo escribe **únicamente el propio servicio**, en sus tres bordes de ciclo de vida
(`Active` al lanzar un job de tracking, `Sentry` al hacerse residente, `Dead` en su `onDestroy`), y
vive **en memoria del proceso**. O sea: en un arranque en frío vale el default *hasta que el
servicio arranca y dice `Sentry`*. Y mientras ese default fuera `Dead`, toda superficie que
preguntara «¿está muerto?» recibía un **sí que nadie había medido**.

Medido en el código, con coche aparcado en carril Coordinator:

| Paso | `servicePresence` | Badge derivado | Qué dice Home |
|---|---|---|---|
| Home se compone | `Dead` (default) | — (aún no hay sesión cargada) | nada |
| cargan sesiones y coches | `Dead` (default) | **`WATCH_INTERRUPTED`** | «Vigilancia inmediata en pausa de tu Ford Focus · Tu móvil la pausó» |
| el servicio arma | `Sentry` | `WATCHING` | «Vigilando tu Ford Focus» |

La app acusaba al sistema de un asesinato que no había ocurrido, **en cada arranque en frío**.

⚠️ El defecto es **anterior** a esta tanda, pero `HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001` lo
amplificó: su `warningOpenCue` **despliega la hoja** sobre la fila del aviso, así que el falso
positivo pasó de una fila que se corrige sola a **abrirse la hoja encima en cada arranque**.

## Doctrina violada

Dos, y las dos están escritas:

- *El evento NOMINA, sólo el movimiento MEDIDO confirma.* Un valor por defecto no es una medición.
- `DET-WATCH-HONEST-001`: *«Vigilando tu sitio» debe ser VERDAD, no aspiracional.* El mismo
  razonamiento vale en el otro sentido — **«en pausa» también tiene que ser verdad.** Esa doctrina
  nació para que la app no prometiera de más; se le había escapado que también acusaba de más.

Y la forma del arreglo ya existía en el proyecto, en otro dominio:
`FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001` — **no preguntado ≠ vacío ≠ fallido**.

## Fix

`ServicePresence` gana un miembro **`Unknown`**, que pasa a ser el valor inicial en los tres sitios.
No es un estado del servicio: es la **ausencia de información** sobre él.

⛔ **Y la misma falta de noticias significa cosas distintas según quién pregunte**, así que cada
consumidor **declara su pregunta** vía `ServicePresence.isWatchingLive` en vez de comparar contra un
valor y heredar lo que signifique mañana:

| Consumidor | Qué pregunta | `Unknown` responde | Por qué |
|---|---|---|---|
| `resolveParkedWatchBadge` (Home) | ¿puedo **afirmar** que vigilo? | **no** → `WATCH_UNKNOWN` | una frase sin medir es una acusación; y caer en `WATCHING` sería la mentira que `DET-WATCH-HONEST-001` impide |
| `ObserveDepartureWatchGapUseCase` | ¿hay un hueco que **cerrar**? | **sí**, igual que `Dead` | produce una ACCIÓN idempotente, no una frase. **Fallo asimétrico**: arrancar un vigilante que ya corre no cuesta nada, no arrancar uno muerto cuesta una plaza. Preserva el comportamiento anterior |
| `resolveSentryKillVerdict` | ¿mataron al residente? | **sí** → `Killed` (el `else`) | el único sitio donde el silencio SÍ prueba la muerte: la pregunta sólo se hace cuando la residencia **se esperaba** y el proceso es nuevo |
| `CoordinatorDetectionService` · `SignificantMotionMonitor` | ¿soy residente **ahora**? | no (comparan `== Sentry`) | sin cambio |

`WATCH_UNKNOWN` **no se dibuja**: `resolveDetectionStory` lo resuelve a `Hidden` antes de que
ninguna superficie lo vea. La card del coche ya dice la verdad —aparcado, y dónde— sin afirmar nada
sobre el vigilante, y un beat después hay veredicto. Un aviso que se desdice solo cuesta más que el
silencio que lo precede.

## Consumidores auditados

`grep -rn "ServicePresence" shared/src app/src mockkit/src --include=*.kt`

| Hit | Clasificación |
|---|---|
| `resolveParkedWatchBadge` | **cerrado** — rama nueva para `Unknown` |
| `ObserveDepartureWatchGapUseCase` | **cerrado** — pasa a `!isWatchingLive`, comportamiento preservado |
| `resolveSentryKillVerdict` | **cubierto por el `else`**, documentado como deliberado |
| `CoordinatorDetectionService` ×3 · `SignificantMotionMonitor` | **cubiertos** — comparan `== Sentry` |
| `DetectionNotice.notice()` · `HomeDetectionSurface` | **cerrados, y los encontró el COMPILADOR** al añadir el miembro al enum — el barrido gratis de un enum sellado |
| `FirstSteps` (`isWatching = badge == WATCHING`) | **cubierto** — `Unknown` no es `WATCHING`, así que el paso queda abierto, que es la verdad |
| escritores: service ×4, `IosDetectionController` ×2, fakes, `ShotPresets` | **exentos** — escriben un valor medido |
| presets de `DevCatalogScreen` que escriben `Dead` | **exentos con razón** — ahí el user AFIRMA la muerte con su interruptor |

## Tests

- `ParkedWatchBadgeTest` +4: sin veredicto nunca acusa · tampoco se vuelve frágil · un coche BT se
  vigila hable o no el servicio · sin sesión sigue siendo invitación (el ORDEN de las ramas importa).
- `ObserveDepartureWatchGapUseCaseTest` +1: la **mitad opuesta** — sin noticias sí hay hueco.
- `WatchInterruptionTest` reescrito: el arranque en frío sano **no** abre la hoja; la duda que se
  resuelve en muerte **sí**.

## ⚠️ La lección, que vale más que el arreglo

El cue de Home tenía un test —`should_open_when_home_arrives_with_the_watch_already_dead`— que
**defendía el bug**, con su falsificación en verde. Su premisa («llegar con la vigilancia ya muerta
es la misma noticia que verla morir delante») es una frase razonable sobre un hecho falso: aquello
no era una vigilancia muerta, era una **sin medir**.

> **Falsificar prueba que el test MIRA. No prueba que mire lo correcto.**

Un testigo verde protegiendo la regla equivocada es más caro que no tener testigo, porque compra
confianza. Lo que lo destapó no fue un test: fue que el user preguntara si el aviso seguía
apareciendo «cuando y donde debe», y que al ir a comprobarlo se leyera el valor inicial.
