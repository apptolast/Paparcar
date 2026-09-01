# DOCS-A-BACKLOG-DOC-THAT-SAYS-PENDING-MUST-BE-PENDING-001 · quince docs decían lo que no era

**Estado:** ✅ Done — mergeado a master el 01-09-2026 (squash)
**Abierto:** 2026-09-01 sobre master `d8308ec5`

## Problema

Barrido de los 339 docs de `docs/backlog/` cruzando cada uno contra `git log` de master. Quince
mentían, en dos clases distintas:

**Clase A — cerrados de facto, sin marcar (10).** Cabecera *"implementado <fecha> · rama `X`"* con la
rama borrada y el código en master. Se leen como trabajo en vuelo cuando no lo hay.

**Clase B — dicen «pendiente» y el código lleva semanas o meses dentro (5 candidatos).** Aquí la
comprobación contra el código cambió el veredicto en **tres de los cinco**, así que no era un
barrido mecánico.

## Doctrina violada

`docs/backlog` es la fuente de verdad entre sesiones, y el propio flujo de `nuevo-ticket` marca el
doc como ✅ **antes** del squash precisamente para que esto no pase. La clase A es ese estado
intermedio congelado. No es un caso aislado: `9071e9b8` y `d8308ec5` son dos commits de otras
sesiones arreglando esta misma clase, un doc cada vez.

## Lo hecho

### Clase A — 10 cabeceras a ✅ Done, con el commit que las cerró

| doc | master |
|---|---|
| `det-a-disowned-anchor-takes-its-walk-with-it-001` | `d74e6e8c` |
| `det-a-doubt-field-must-not-default-to-certainty-001` | `c604a058` |
| `det-a-resolved-arrival-is-resolved-for-all-eight-reasons-001` | `c5bfd274` |
| `det-nothing-to-judge-is-not-no-doubt-001` | `c0144b5e` |
| `park-a-refuted-pin-leaves-the-history-001` | `d010b8c0` |
| `veh-a-new-vehicle-type-must-not-be-a-car-by-omission-001` | `22abbcc4` |
| `det-the-two-fps-that-caused-the-redesign-become-replays-001` | `3adb08ae` |
| `test-an-orphaned-field-trace-still-looks-like-coverage-001` | `f58e9d64` |
| `det-a-user-yes-does-not-shrink-a-walk-entered-doubt-001` | `51561ea4` |
| `det-a-hole-the-speed-field-denies-is-still-a-hole-001` | `b4f1256c` |

⚠️ **El hash hay que sacarlo del ASUNTO del commit, no de `git log --grep`**: el `--grep` casa
también el cuerpo, y devolvió `9dca6e74` para el doubt-field y `2855aafe` para el refuted-pin — dos
commits *posteriores* que sólo los mencionan. Dos de ocho mal, con un método que parecía sólido.

⚠️ **La clase A se REGENERA mientras la barres.** Los dos últimos de la tabla no existían al empezar
este ticket: nacieron a media tarea, cuando otras sesiones mergearon `51561ea4` y `b4f1256c` dejando
la cabecera en el mismo estado intermedio.

🔬 **Y el caso que lo diagnostica**: `b4f1256c` entró con la cabecera rancia **en el mismo minuto** en
que `80eda93c` (`ui-seven-strays-from-the-canon-001`) entró con su ✅ puesto. Dos merges simultáneos,
uno bien y otro mal. La diferencia no es el tamaño del ticket ni la prisa: es si esa línea del flujo
se ejecutó. Diez de diez casos vienen del mismo paso omitido — **esto pide un guardarraíl, no
barridos periódicos**.

### Clase B — cinco candidatos, tres veredictos distintos

| doc | veredicto | evidencia |
|---|---|---|
| `det-ar-first-001` | ✅ **cerrado**: estaba implementado | `DET-AR-FIRST-001` en 5 ficheros de producción (los dos carriles AR, el `getForegroundService` de F1) y en el `parkdiag` de campo del 01-09 |
| `det-bt-connected-not-paired-001` | ✅ **cerrado**: `cbc17ac4` (10-08) | `BluetoothScanner.isConnectedToPairedCar` existe citando el ticket; `ParkingStrategyResolver` documenta el enlace ACL como criterio |
| `bug-home-fab-padding-2026-06-05` | 🟡 **sigue abierto** — mi clasificación inicial era errónea | 1 evento, 1 usuario, sin reproducir y sin causa confirmada. Lo caducado era **la ruta**: `composeApp/…` ya no existe; hoy es `shared/…/HomeMapSection.kt:149` |
| `det-resume-reconcile-001-2026-07-02` | 🟡 **abierto con la premisa caducada** | `WATCHDOG_ENABLED` y `DetectionHeartbeatWorker` ya no existen; la reconciliación al revivir la hace hoy `ParkingSafetyNetWorker` + `EvaluateSafetyNetCheckUseCase` |
| `detection-improvements-2026-05-27` | 🗄 **histórico, no accionable** | esperaba a `BUG-GARAGE-COLA-001` + `BUG-SCOOTER-001`, y **ninguno existe** en el backlog |

📌 **Lo que enseña el barrido**: de los cinco «obsoletos» sólo dos lo eran. Uno estaba sano y con la
ruta podrida, otro sigue abierto pero razonando sobre componentes retirados, y el quinto no era un
ticket. Marcar los cinco como cerrados —que es lo que sugería la clasificación por cabecera— habría
enterrado un crash sin resolver.

## Criterio de éxito

- Ningún doc no-cerrado cuyo ticket aparezca en el ASUNTO de un commit de código de master.
- Ningún doc que diga «pendiente/sin empezar» sobre código que ya existe.

## Consumidores auditados

- **Los 10 de la clase A** — cerrado.
- **Los 5 de la clase B** — cerrado (2) / corregido sin cerrar (2) / reclasificado (1).
- **37 docs sin línea `Estado:`** (de 339), casi todos de mayo–julio y con su código en master →
  **exento en este ticket**: no mienten, son invisibles para un barrido por cabecera. Se cazan sólo
  cruzando contra `git log`. Candidatos a `MEMORY-ARCHIVE`, no a corrección.
- **Tickets en curso en otros worktrees** (`ios-f0`) → **exento, no se toca**: su doc vive en su
  propia rama.

## Follow-up que deja abierto

`DOCS-A-CLOSED-TICKET-MARKS-ITSELF-001` (sin abrir): un guardarraíl que falle cuando un doc de
`docs/backlog/` cite una rama inexistente **y** su ticket aparezca en el asunto de un commit de
master. Es la comprobación que he hecho a mano aquí, y la única forma de que la clase A deje de
regenerarse.
