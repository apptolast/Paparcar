# DOCS-THE-BACKLOG-STATUS-LOOP-MUST-BE-A-GUARD-001 · el detector de estados vive en un README, así que nadie lo corre

**Estado:** 📋 Abierto (2026-10-07) · sin rama · sale de
[DOCS-A-CLOSED-TICKET-MUST-NOT-SAY-IT-IS-IN-REVIEW-001](docs-a-closed-ticket-must-not-say-it-is-in-review-001.md)

## Problema

El barrido [DOCS-BACKLOG-TRUTH-002] (03-09, re-medido el 15-09) dejó escrito en
`docs/backlog/README.md` un bucle de 8 líneas que clasifica cada doc por el primer emoji de su línea
de estado, y hasta el chivato bueno —`git show-ref` sobre la rama que cita el doc—. **Y en 22 días
nadie lo ejecutó.** El barrido del 07-10 encontró:

- 23 docs en `🔵 En revisión · rama X` con X borrada y su commit en master.
- 4 marcando cerrado con 🟢, dos de ellos fuera de toda etiqueta legible (uno en el **título**).
- 4 con el estado en palabras, sin emoji.
- 1 doc **binario para `grep`** por un byte NUL crudo, invisible al bucle entero.

Esto ya tiene nombre en el repo: *un comando suelto en un README no es una herramienta*. Es
exactamente lo que costó ocho días de arte de Play con el logo viejo
[PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001].

## Doctrina violada

**Prohibición sin testigo no es chequeo.** La regla «todo doc lleva su línea de estado, y esa línea
es verdad» existe desde el 03-09 y no la vigila nada. Todo lo demás comparable en este repo tiene su
guard: `LocaleParityGuardrailTest`, `TypographyGuardrailTest`, `ColorGuardrailTest`,
`WebSiteParityGuardrailTest`, `BrandSurfaceGuardrailTest`, `ComposeResourcesEscapeGuardrailTest`.
El backlog, que es la fuente de verdad entre sesiones, no.

## Señales / datos disponibles

Todo es mecánico y ya está medido — no hace falta inventar nada:

1. **Cada `.md` de `docs/backlog/` tiene línea de estado** con un primer emoji del vocabulario
   conocido, salvo `README.md` y los `*-PROMPT.md`, que son la allowlist.
2. **Ningún `.md` contiene bytes de control** (el NUL de hoy lo habría cazado al escribirse).
3. **Una rama citada por un doc abierto existe.** Si no existe y el ID tiene commit en master, el
   doc miente. Esto es el chequeo valioso y el único que necesita `git`.

## Diseño

Un test Konsist/JVM en `shared/src/androidUnitTest/.../architecture/`, al lado de los demás, que
lea el directorio y falsifique los tres chequeos de arriba. El 3 necesita `git` desde el test: si
sale caro, se parte — 1 y 2 en el guard (puro, rápido) y 3 como script de `tools/` que el guard
nombre, que es peor pero sigue siendo mejor que un README.

⛔ **No lo vigila el bytecode**: como todos los guards de este paquete, saldrá **UP-TO-DATE** y hay
que falsificarlo con `--rerun-tasks`.
⛔ **El guard no debe leer el nombre de la carpeta** para decidir qué es un ticket
[TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001]: la allowlist se declara, no se deduce.

## Criterio de éxito

Falsificado en los tres sentidos: quitar la línea de estado a un doc → rojo; dejar un 🔵 citando una
rama borrada con commit en master → rojo; meter un byte de control en un `.md` → rojo. Y el test
corre dentro de `./gradlew testAll`.

## Consumidores auditados

- `docs/backlog/README.md` — el bucle se queda como documentación del criterio; la ejecución pasa
  al guard.
- La skill `nuevo-ticket` — su §C.3 marca el doc ✅ antes del squash; el guard lo respalda, no lo
  sustituye.
