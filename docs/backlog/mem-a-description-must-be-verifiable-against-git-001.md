# MEM-A-DESCRIPTION-MUST-BE-VERIFIABLE-AGAINST-GIT-001 · el `description:` de una memoria afirma cosas de git y nadie las comprobaba

**Estado:** ✅ Done · `tools/memory/description_tell.py` (4 chequeos) + el registro de chequeos
fuera de la suite en `BacklogStatusGuardrailTest` · sale de
[DOCS-THE-BACKLOG-STATUS-LOOP-MUST-BE-A-GUARD-001](docs-the-backlog-status-loop-must-be-a-guard-001.md)

## Problema

El barrido manual del 07/08-10-2026 sobre las 565 fichas de memoria encontró **44 con el
`description:` mintiendo**, todas con el mismo origen mecánico: el frontmatter se redacta **al abrir
el ticket** —«commiteado en rama, SIN mergear», «staged sin commit en `../Paparcar-x`»— y al mergear
se actualiza el CUERPO pero no el `description:`. `UI-SHEET-001` llevaba así desde el **12-07**:
tres meses diciendo «IMPLEMENTADO ✅ SIN MERGE» mientras `PapSheet` era el esqueleto de toda la app.

⛔⛔ Y es el campo que más duele: el `description:` es lo que decide la **relevancia al recordar**.
Una ficha que se describe como trabajo sin mergear se recupera —o se descarta— sobre una premisa
falsa, y tener el cuerpo correcto no salva nada si nadie llega a abrirlo.

Arreglarlo a mano no cierra el problema: el barrido fue una tarde de trabajo y en dos semanas vuelve
a derivar, exactamente como volvió a derivar el backlog cuando su bucle vivía en un README.

## Doctrina violada

**Prohibición sin testigo no es chequeo** [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]. Y la
particular de este caso: *una afirmación sobre el estado de git tiene que ser verificable contra
git*. Si una ficha dice «en master `X`», `X` tiene que ser ancestro de master; si cita una rama, esa
rama tiene que existir. Las dos cosas se comprueban en un comando y ninguna se comprobaba.

## Señales / datos disponibles

Cuatro chequeos, todos mecánicos, cero juicio:

| | qué |
|---|---|
| **C1** | falta el `description:` o está vacío |
| **C2** | cita una RAMA o un WORKTREE que ya no existe (`git for-each-ref` · `git worktree list`) |
| **C3** | afirma «sin mergear / sin commitear» y el cuerpo de la ficha dice master |
| **C5** | el **hash** que presenta como estando en master no es ancestro de master, o no existe |

**C5 es el que nadie habría hecho a mano.** Caza los hashes **pre-squash / pre-rebase**, que existen
como objeto en ESTE clon y en ninguna otra parte: `PROFILE-PHOTO` decía `a456617a` cuando master
lleva `f7f2a8b34`; `SPOT-A-PRIVATE-ZONE`, `eff2bdfa` por `8ff7a6493`. Y uno, `3f8ccc39`, ya no existe
ni como objeto: esa arqueología se perdió.

## Diseño — y por qué el testigo NO es un test

`tools/memory/description_tell.py`, fuera de `./gradlew testAll`, por **dos** razones medidas:

1. ⛔⛔ **El corpus de memoria vive FUERA del repo** (`~/.claude/projects/<proyecto>/memory`). En CI
   y en cualquier checkout limpio esa carpeta **no existe**, así que un test la encontraría vacía y
   pasaría sin mirar nada.
2. ⛔ **C5 necesita el historial completo de `master`** y el CI hace `actions/checkout@v4` con
   `fetch-depth` por defecto = 1 — el mismo muro que puso fuera al chivato de la rama.

Un guard que no puede ver lo que juzga convierte «nadie ha mirado» en «todo correcto»
[CI-A-GUARD-THAT-NEVER-STARTS-IS-NOT-A-GUARD-001]. Así que el script **se niega con exit 2** si no
ve la memoria o no ve el historial, y el guard vigila que siga negándose.

**Lo que entra en el guard es el REGISTRO**: `OUT_OF_SUITE_TOOLS` en `BacklogStatusGuardrailTest`
declara cada chequeo que la suite no puede alojar, qué lleva, por qué está fuera y las frases de su
negativa. Es lo que impide que «no puede ser un test» se convierta en «nadie lo comprueba».

## Criterio de éxito — falsificado

| Se rompe | Resultado |
|---|---|
| `description:` vacío | 🔴 C1 |
| rama inventada en el `description:` | 🔴 C2 |
| «SIN mergear» con el cuerpo en master | 🔴 C3 |
| `master deadbee1234` | 🔴 C5, «no existe ni como objeto» |
| la carpeta de memoria no está | ⛔ exit 2, no verde |
| clon shallow | ⛔ exit 2, no verde |
| la allowlist nombra un fichero inexistente | ⛔ exit 2 |
| borrar `description_tell.py` | 🔴 el guard, en `testAll` |
| quitarle cualquiera de sus **dos** negativas | 🔴 el guard |

Falsificado sobre una **copia** del corpus en el scratchpad, no sobre las memorias reales. Verde
sobre el corpus real: 566 fichas · 195 hashes afirmados en master comprobados contra 1.578 commits ·
120 solo mencionados, que no se juzgan.

## Consumidores auditados

- `BacklogStatusGuardrailTest` — ✔ su testigo pasa de nombrar un tool a llevar el registro de los
  dos; el KDoc explica el registro y por qué existe.
- `tools/backlog/branch_tell.py` — ✔ sin cambios; entra en el registro tal como está.
- La skill `nuevo-ticket` — ⛔ exenta: su cierre ya nombra `branch_tell.py`, y este chequeo no es de
  cierre de ticket sino de higiene del corpus de memoria, que no es del repo.

## Tres cosas que aprendió al escribirse (están en el KDoc del script)

- ⛔⛔ **El slug de la memoria se deriva del árbol PRINCIPAL, no del worktree.** La primera versión lo
  sacaba de la raíz del repo y, corriendo desde un worktree —que es como se trabaja aquí **siempre**—
  buscaba `…-Paparcar-desc-tell/memory` y se negaba a correr siempre. Lo destapó su propia negativa.
- ⛔⛔ **Un hash solo se juzga si la ficha lo presenta como estando en master.** Sin eso marcaba cuatro
  fichas que no mienten: `plegado en la rama iOS 424bf25a (pre-rebase f205773f)` es verdad —ese hash
  es de una rama— y `commit-hash 35a5e15fc4` es de **otro repo** (BaseLogin). Exigir que las 40 chars
  anteriores hablen de master bajó de 7 falsos positivos a 0 y dejó 120 menciones fuera.
- ⛔⛔ **El chequeo era sensible a mayúsculas y el razonamiento no lo vio.** Planté «SIN mergear» con
  «sin mergear» en la lista y **no saltó**. Lo cazó la falsificación. Todo compara en minúscula.
- ⚠️ `acababa` encaja en `[0-9a-f]{7,}` — y `decidida`, `cafetera`, `ed25519`. De ahí `NOT_A_HASH`.

## Lo que este chequeo NO puede ver

Si la prosa del `description:` es buena, o si describe la ficha que realmente es. Eso es leer. Y las
17 descriptions de 320–490 chars se quedan como están: el frontmatter no es el índice, y para decidir
relevancia específico gana a corto.
