# DOCS-THE-BACKLOG-STATUS-LOOP-MUST-BE-A-GUARD-001 · el detector de estados vive en un README, así que nadie lo corre

**Estado:** ✅ Done · `BacklogStatusGuardrailTest` (3 chequeos + testigo de población) dentro de
`./gradlew testAll`, y el chivato de la rama en `tools/backlog/branch_tell.py` · sale de
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

## Diseño — y por qué el corte quedó donde quedó

`BacklogStatusGuardrailTest` en `shared/src/androidUnitTest/.../architecture/`, al lado de los
demás, leyendo el directorio. Tres chequeos **puros de ficheros**, más el testigo de población que
exige `GuardrailScope`:

1. Todo doc fuera de la allowlist tiene línea de estado y su **primer marcador** está en el
   vocabulario. ⛔ **🟢 NO está en el vocabulario a propósito**: cuatro docs lo usaron para decir
   «cerrado» y la regla desde el 03-09 es ✅. Dejarlo fuera es lo que convierte ese error en rojo.
2. Ningún `.md` lleva bytes de control.
3. Las cifras de la cabecera del README **son** las medidas. El mensaje de fallo escribe la línea
   que hay que pegar, así que el coste es una edición, no una investigación.
4. Testigo: si el directorio baja de **380** docs (la mitad de los 775 medidos), rojo — un guard
   que selecciona cero se satisface con una carpeta vacía.

⛔ **El chequeo 3 original —el chivato de la rama— NO podía vivir aquí, y esto se midió.** Necesita
la lista completa de refs y el historial completo de `master`, y el CI hace `actions/checkout@v4`
con `fetch-depth` por defecto = **1**. Lo comprobé sobre un `git clone --depth 1` real,
neutralizando la negativa para ver qué habría pasado:

```
Revisados 7 docs abiertos que citan rama, sobre 775 ficheros y 0 commits de master.
OK: ningun doc abierto cita una rama borrada cuyo trabajo ya esta en master.
```

**Verde, con cero commits mirados, e indistinguible del verde real.** Eso es exactamente
[CI-A-GUARD-THAT-NEVER-STARTS-IS-NOT-A-GUARD-001]. Así que vive en
`tools/backlog/branch_tell.py`, que **se niega** (exit 2) sobre un clon shallow o sobre un `master`
con menos de 200 commits, y `verifyBranchTellToolIsPresent` vigila que el fichero exista **y que
siga llevando esa negativa**.

⛔ **La rama que cuenta es la del BLOQUE DE ESTADO, no cualquiera que el doc mencione.** La primera
versión leía las 20 primeras líneas y produjo un falso positivo en el acto:
`ios-apple-sign-in-on-baselogin-3-001` está 🟡 con razón —el código entró en master y lo que queda
es compilarlo en un Mac— y nombra su rama en el párrafo donde explica que **se borró**. Hablar de
una rama en prosa es legítimo; presentarla como si el trabajo siguiera ahí, no.

⛔ **No lo vigila el bytecode**: como todos los guards de este paquete, saldrá **UP-TO-DATE** y hay
que falsificarlo con `--rerun-tasks`.
⛔ **El guard no debe leer el nombre de la carpeta** para decidir qué es un ticket
[TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001]: la allowlist se declara, no se deduce.

## Criterio de éxito — falsificado, los seis

Cada testigo se rompió a propósito y se comprobó que sale ROJO, con `--rerun-tasks` (sin él Gradle
da el test por UP-TO-DATE: no toca bytecode). Y verde otra vez al restaurar.

| Se rompe | Resultado |
|---|---|
| quitar el marcador de una línea de estado (como vivió el 🟢 en el título) | 🔴 chequeo 1, nombrando el doc y la línea |
| plantar un byte NUL en un `.md` (`file` pasa a decir `data`) | 🔴 chequeo 2, con el offset del byte |
| desviar las cifras del README en 2 tickets | 🔴 chequeo 3, y el mensaje da la línea a pegar |
| borrar `tools/backlog/branch_tell.py` | 🔴 `verifyBranchTellToolIsPresent` |
| quitarle al chivato su negativa al clon shallow | 🔴 el mismo test, segunda aserción |
| subir el suelo de población por encima de los docs reales | 🔴 los tres chequeos a la vez |

Y el chivato, aparte: con un doc plantado diciendo «🔵 En revisión · rama X» sobre una rama borrada
con commit en master → **exit 1** nombrándolo; restaurado → exit 0 tras revisar 7 docs abiertos
sobre 775 ficheros y 1.576 commits.

## Consumidores auditados

- `docs/backlog/README.md` — ✔ cerrado: el bucle se queda como documentación del criterio, y el
  README dice ya que la ejecución la hace el guard y dónde vive el chivato.
- La skill `nuevo-ticket` — ✔ cerrado: su §C.3 gana la línea de que cerrar un doc obliga a recontar
  las cifras del índice, porque el guard lo exige y conviene saberlo ANTES del rojo.
- `CLAUDE.md` — ⛔ exento a propósito: no tiene sección de convención del backlog, y añadirle una
  por un guard de docs sería ampliar el encargo.

## Lo que este guard NO puede ver

- **Que el trabajo haya aterrizado bajo el ID de OTRO ticket.** El chivato empareja por
  `[TICKET-ID]` en el asunto del commit, así que no ve lo que entró dentro del squash de otro. Caso
  vivo medido hoy: `ios-f2-a-wake-must-query-the-past-001` sigue 🔵 y sus tres ficheros
  (`IosWakeMonitors.kt`, `IosSafetyNetRecords.kt`, `IosDetectionController.kt`) **están en master**
  — entraron en `8c2daf096`, que cita el ID de la Fase 0. Ningún emparejado por ID lo caza.
- Que la prosa de un doc abierto sea buena, o que un ticket nuevo tenga su fila en el índice.
