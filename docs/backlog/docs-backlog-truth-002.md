# DOCS-BACKLOG-TRUTH-002 · un doc sin línea de estado es un doc que el índice no puede ver

**Estado:** ✅ Done (2026-09-03) · segundo barrido, hermano de
[`docs-backlog-truth-001`](docs-backlog-truth-001.md) y de
[`docs-living-docs-must-match-master-001`](docs-living-docs-must-match-master-001.md)

## Problema

El barrido de agosto arregló los docs que **mentían** (decían "sin commitear" llevando semanas en
master). Quedaba la otra mitad, más callada: los que **no dicen nada**. Un doc sin línea de estado no
sale en ningún `grep`, así que para saber si su trabajo está hecho hay que abrirlo y leerlo entero —
y por eso nadie lo hace, y por eso se recitan como pendientes cosas cerradas hace meses.

El propio índice lo sufría: `README.md` decía **«285 ficheros, 195 cerrados»** cuando ya había
**347**, y listaba como abiertos ocho tickets cerrados en los últimos días.

## Doctrina implicada

*Doc vivo = fecha + commit verificado.* Y su corolario, que es lo que añade este barrido: **un doc
que no declara su estado no es "neutral", es ilegible** — obliga a cada lector a re-derivar la
verdad, que es exactamente el trabajo que el doc existía para ahorrar.

## Lo medido (03-09-2026, contra master `6a04e119`)

| | |
|---|---|
| Ficheros en `docs/backlog/` | **347** (346 + el índice) |
| Sin ninguna línea de estado | **19** → ahora **0** |
| Cerrados | **316** |
| No cerrados (abiertos, planes, análisis) | **30** |

⚠️ **La primera pasada dio un número falso** (56 sin estado): el detector no contemplaba que muchos
docs declaran el estado **dentro de un blockquote** (`> **Estado**: …`). Con las dos formas
contempladas, los que faltaban eran 19. El `grep` que ahora vive en el README lleva las dos.

## Cómo se decidió el estado de cada uno (evidencia, no criterio)

Para los 19, se buscó **el ID del ticket en el log** y **en el código**, separando los commits de
implementación de los de doc (`docs(…)`):

- **11** tenían commit de implementación → `✅ En master`, con el hash del commit más antiguo que lo
  implementa y el número de ficheros de código que lo citan.
- **4** eran análisis/planes sin criterio propio → `📚 ANÁLISIS` / `📋 PLAN`, diciendo que su estado
  real vive por-bug o por-tarea dentro.
- **2** eran ideas de producto sin código (`zone-subscribe`, y `snap-to-park` que ya lo declaraba).
- **1** estaba cerrado y lo decía su propia cabecera (`veh-add-pill`).
- **1** parcial (`vehicles-multimarker`: marcadores en código, resto diferido).

⛔ **Lo que este barrido NO hace, y se dice en cada línea que escribe**: no re-lee el criterio de
éxito de cada ticket. Verifica *que el trabajo aterrizó*, no *que aterrizó entero*. Por eso los que
arrastran un ⏳ dentro lo declaran («el propio doc anota pendientes: leerlo antes de darlo por
terminado»).

## Hallazgos que salieron por el camino

- **Dos planes citan ramas que ya no existen** (`DET-AUDIT-REMEDIATION-001` →
  `fix/DET-AUDIT-002-detection-hardening`, `audit-a12-001` → `fix/AUDIT-A12-001-…`). Su contenido
  sigue vigente; sus punteros están muertos. Es el mismo chivato del barrido anterior.
- **Cuatro docs terminados marcados con 🟢 en vez de ✅** salían en la lista de abiertos. Normalizados
  — y la regla («cerrado se marca con ✅») ahora está escrita en el README.
- **`audit-a12-001` pedía extraer un `invoke()` de ~400 líneas**: lo desmontó después el refactor F6
  (`stages/`, `FixReduction`, `StopTracking`), o sea que su petición está cubierta por otra vía.
- **Tres docs de la sesión paralela siguen sin commitear**, así que el índice los nombra pero no
  puede enlazarlos.

## Criterio de éxito

- `grep` del estado sobre `docs/backlog/*.md` → **346 de 346** (solo el índice queda fuera) ✅
- El README dice números verdaderos y su lista de abiertos coincide con lo que declaran los docs ✅
- La regla queda escrita en el README para que un doc nuevo no vuelva a nacer mudo ✅
