# DOCS-A-CLOSED-TICKET-MUST-NOT-SAY-IT-IS-IN-REVIEW-001 · veintitrés tickets en master seguían diciendo «En revisión»

**Estado:** ✅ Done · barrido verificado contra master `1339ea263` el **2026-10-07**

## Problema

El barrido [DOCS-BACKLOG-TRUTH-002] (03-09, re-medido el 15-09) dejó `docs/backlog` legible con un
`grep`: 544 ficheros, 481 cerrados, 54 abiertos, y una regla —*todo doc lleva su línea de estado*—
con su bucle de comprobación escrito en el `README.md`. **Nadie volvió a correr ese bucle en 22
días.** Medido hoy sobre 773 ficheros:

- **23 docs** dicen `🔵 En revisión · rama X` con **X borrada** y su commit **en master**. Entre
  ellos tickets de hace semanas: `det-a-walk-is-not-an-egress-001` (`b4bf68862`),
  `map-a-car-park-marker-wears-its-own-blue-001` (`19243d2a0` + `f25ecafa7`),
  `stats-the-detection-shows-its-hit-rate-001` (`4ef6cd541`).
- **4 docs** llevaban un 🟢 donde toca un ✅ (*cerrado se marca con ✅, nunca con 🟢*, regla del
  03-09), y **dos de ellos lo ponían fuera de cualquier etiqueta que el bucle pueda leer**: uno en
  prosa (`shared-a-parking-tells-the-group-001`, `d6e890f28`) y otro **en el título**
  (`home-the-card-answers-the-badge-warns-001`, `c3d873b64`), ambos en master desde el 23-09. Los
  otros dos sí tenían línea legible: `web-the-site-says-only-what-the-app-does-001` (desplegado
  el 17-09) y `demo-a-review-account-must-look-lived-in-001`.
- **1 análisis de campo** (`detection-improvements-2026-05-27`) se quedó sin la línea que sus dos
  hermanos sí recibieron en el barrido del 03-09.
- **4 docs más** llevaban un estado en PALABRAS, sin emoji (`BACKLOG (prueba / spike)`,
  `POR DEFINIR`, `ANÁLISIS` ×2), así que el bucle los devolvía vacíos.
- **1 doc es BINARIO para `grep`**: `stats-a-period-is-a-series-not-a-number-001.md` contiene **un
  byte NUL crudo**, en la frase donde el propio doc cuenta que cazó ese mismo error en el código y
  lo corrigió «al escape». Escribió el NUL de verdad al describirlo. Un solo byte y `grep` se niega
  a leer el fichero entero: el bucle del índice lo devolvía vacío, igual que si no tuviera estado.
  Aquí dio igual porque está cerrado — en un ticket abierto lo habría escondido del todo.
- El `README.md` del índice seguía anunciando **544 ficheros**, 230 menos de los que hay; su bucle
  de comprobación miraba solo `Estado`/`Status` y dejaba fuera los cinco docs que abren con
  `**Fecha:**`; y su sección de iOS daba por **«sin mergear, en la rama `feature/IOS-F0-001-fase0`»**
  una Fase 0 que está en master desde `8c2daf096`, con esa rama ya borrada — además de encabezarse
  con un *«🧊 APARCADO por el user (07-09)»* que el user retiró el 22-09.

⛔⛔ **Corrección de este mismo barrido**: al principio escribí que los hashes de aquella rama
«quedaban colgando sin ref». **Falso, y comprobable en un comando**: `git tag --contains <hash>` los
encuentra bajo los tags `prerebase/`, la red de seguridad que las sesiones anteriores dejaron puesta.
Decir que un hash es irrecuperable cuando está a un `checkout` de distancia es el mismo error que
este ticket arregla, en la dirección contraria. Los tags, eso sí, **son locales**: no están en
`origin`.

## Doctrina violada

Ninguna del código. La que se rompe es la del propio barrido que creó este índice:
**el estado de un ticket se lee de su línea de estado, y esa línea tiene que ser verdad.** Un doc
que dice «En revisión · rama X» cuando X está borrada y el código en master no es un doc
desactualizado: es un doc que **miente en la dirección más cara** — invita a reabrir trabajo hecho.

Y el chivato que lo caza ya estaba escrito en el README desde el 15-09:
`git show-ref --verify --quiet refs/heads/<rama> || echo "rama borrada → el doc miente"`.
El defecto no fue no saberlo. Fue no ejecutarlo.

## Señales / datos disponibles

Tres fuentes, ninguna de juicio:

1. `git log --oneline master` + `grep "[<TICKET-ID>]"`, descartando los `docs(` → el commit de
   implementación. Los 24 tenían uno.
2. `git show-ref --verify` sobre la rama que cita la línea de estado → las 24 borradas.
3. El bucle del `README.md`, con el primer emoji de la línea de estado como único detector honesto.

⚠️ El detector del README **prefiere `Estado`/`Status`**; cinco docs abren con `**Fecha:**` y su
`Estado` va en la línea siguiente. Mirar `Fecha` *primero* los clasifica mal y a la vez salva
`ios-f0-001`, que lleva las dos cosas en la MISMA línea. El orden bueno es: `Estado`/`Status`, y
`Fecha` solo como respaldo cuando no hay línea de estado.

## Diseño

El invariante vive en UN sitio —la línea de estado de cada doc— y se arregla ahí, no en el índice:

- Los 23 pasan a `**Estado:** ✅ Done · master \`<hash>\``, **citando el hash verificado** y
  conservando el `issue #NN` que llevaban. Estos no cierran dentro de su propio commit, así que
  pueden citar su hash: la regla de «el doc no puede citar su propio hash» aplica al cierre
  simultáneo, no a un cierre retroactivo.
- Los 2 del 🟢 mal colocado reciben una línea de estado donde el bucle la busca, con su hash; los
  otros 2 solo cambian el emoji.
- Los 4 sin emoji reciben el suyo (📋 los dos tickets, 📚 los dos análisis) y
  `detection-improvements-2026-05-27` la declaración de análisis de sus hermanos.
- El byte NUL de `stats-a-period-is-a-series-not-a-number-001` se sustituye por el escape que el
  propio doc dice haber usado en el código, y el fichero vuelve a ser texto UTF-8.
- El bucle del `README.md` gana el respaldo de `**Fecha:**`, **después** de `Estado`/`Status` y
  nunca antes: `ios-f0-001` lleva las dos cosas en la misma línea y mirar la fecha primero
  clasifica mal a los otros cuatro.
- El `README.md` recalcula sus cifras contra el tip de hoy, deja de citar ramas muertas y su
  sección de iOS pasa a decir lo que master dice.

## Criterio de éxito

El bucle del `README.md` no devuelve ningún fichero sin clasificar salvo el propio índice y los
tres `*-PROMPT.md`, que no son tickets. Medido tras el barrido:

| | antes | después |
|---|---|---|
| ficheros | 773 | 775 (+ este doc y su follow-up) |
| cerrados (✅ · 🚫 · ❌) | 660 | **688** |
| abiertos | 104 | **78** (77 + el follow-up que abre este barrido) |
| no son tickets | 9 | 9 |
| **tickets sin estado legible** | **8** | **0** |
| **ficheros que `grep` no puede leer** | **1** | **0** |

Los 4 ficheros que siguen sin línea de estado son el propio índice y los tres `*-PROMPT.md`, que
no son tickets y no deben tenerla.

## Consumidores auditados

- `docs/backlog/README.md` — ✔ cerrado. No citaba ninguno de los 23 (los daba ya por cerrados,
  que es justo la deriva: el índice sabía más que los docs). Sí citaba una rama muerta de iOS.
- `MEMORY.md` del harness — ✔ exento: no listaba ninguno de los 24 como pendiente.
- `docs/detection/PARKING-DETECTION.md` y la skill `det-change` leen los docs **cerrados** por su
  contenido, no por su estado — ✔ no les afecta.
- `docs/ROADMAP.md` y `docs/IOS_PLAN.md` — ⛔ **fuera de alcance, y mienten**: ya estaban
  declarados como abiertos en `docs-the-readme-must-match-the-tree-001` (`0c75c3878`) y siguen
  igual. No se tocan aquí para no mezclar dos barridos.

## Lo que este barrido NO arregla

- Que el copy de un doc abierto sea bueno, o que un ticket nuevo tenga sección en el índice. Eso
  no es mecánico y no lo vigila nada.
- **Nada ejecuta el bucle.** Sigue siendo un comando en un README, y un comando en un README no es
  una herramienta: por eso se pudrió 22 días. Lo mismo que ya pasó con el arte de Play
  [PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001]. Queda abierto como
  [`docs-the-backlog-status-loop-must-be-a-guard-001`](docs-the-backlog-status-loop-must-be-a-guard-001.md).
- `ui-the-light-theme-is-lighter-without-losing-its-edges-001` se deja **🔵 abierto a propósito**:
  su rama tampoco existe, pero el único commit que lo cita es un `docs(`, así que no hay evidencia
  de que el cambio de tema aterrizara. Cerrarlo sería adivinar.
