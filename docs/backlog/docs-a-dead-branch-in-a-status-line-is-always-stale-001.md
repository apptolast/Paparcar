# DOCS-A-DEAD-BRANCH-IN-A-STATUS-LINE-IS-ALWAYS-STALE-001 · una rama muerta en la línea de estado ya es el defecto

**Estado:** ✅ Done (13-10-2026)

## Problema

Encontrado el 13-10-2026 barriendo «qué nos queda» después de cerrar
[PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001](play-the-listing-moves-to-the-published-build-001.md).
**Tres docs abiertos presentan una rama viva en su línea de estado, y esa rama no existe. En los
tres el trabajo está en master.** `tools/backlog/branch_tell.py` dice `OK` sobre los tres.

| Doc | Dice | Dónde está el trabajo de verdad |
|---|---|---|
| `ui-the-light-theme-is-lighter-without-losing-its-edges-001` | 🔵 En revisión · rama `feature/UI-THE-LIGHT-THEME-…` | **`fe576b3e5`**, con las CUATRO declaraciones de color (`PapVariantLight` `#D8E0EE`→`#E4EAF5`, `PapSurfaceLight`, `PapAzure`, `PapAzureLow`) |
| `ios-store-shots-without-shipping-the-demo-code-001` | 🔵 En progreso · rama `feature/IOS-STORE-SHOTS-…` | **`6970b4065`**, bajo el ticket de **Apple Sign-In**: ahí nace `mockkit/` |
| `ios-f2-a-wake-must-query-the-past-001` | 🔵 En progreso · rama `feature/IOS-F2-…-wake-and-query` | **`8c2daf096`**, bajo **IOS-F0**: `IosWakeMonitors.kt`, `CmPedometerQueries.kt` |

## Doctrina violada

⛔ **Prohibición sin TESTIGO no es chequeo** — y aquí el testigo existe, mira, y contesta `OK`. Es
peor que no tenerlo: convierte «nadie lo ha comprobado» en «está todo bien». Misma familia que
[MEM-A-DESCRIPTION-IS-WRITTEN-ONCE-AND-NEVER-REVISITED-001] y
[DOCS-A-CLOSED-TICKET-MUST-NOT-SAY-IT-IS-IN-REVIEW-001]: metadatos que afirman estado de git y que
nadie vuelve a contrastar contra git.

## Señales / datos disponibles

Dos agujeros, y **ninguno de los dos es un descuido del chivato**: los dos salen de reglas suyas
que son razonables y resultan incompletas.

### 1 · Un commit `docs(` puede llevar código

El chivato descarta los commits cuyo asunto empieza por `docs(`:

```python
impl = [s for s in subjects if ("[%s]" % ticket) in s and not s.startswith("docs(")]
```

La intención es correcta —un commit de documentación no es que el trabajo haya aterrizado— pero el
asunto **no es la prueba**. Medido: `fe576b3e5` es un `docs(ui)` cuyo diff cambia `Color.kt` con
cuatro `val` nuevos. No es KDoc: son los colores del tema claro.

→ *Un commit `docs(` que toca algo que no es un `.md` no es un commit de docs.* Lo que decide es
**qué ficheros toca**, no cómo se tituló.

### 2 · El trabajo puede aterrizar bajo OTRO ticket-ID

Los otros dos se le escapan por otra vía: su código entró en master dentro del squash de otro
ticket, así que no hay ningún commit con `[IOS-F2-…]` en el asunto. El chivato exige **probar dónde
aterrizó** antes de acusar, y cuando no puede probarlo se calla.

→ Pero la mentira no necesita esa prueba: **una línea de estado que presenta como viva una rama que
no existe ya es falsa**, sepamos o no dónde acabó el trabajo. Que el chivato no pueda decir *dónde*
está no lo autoriza a decir *OK*.

## Diseño

El invariante es uno: **si la línea de estado cita una rama, la rama tiene que existir; si no
existe, la línea está desfasada y hay que decirlo.** El chivato pasa de una pregunta a dos
severidades, y las dos salen con exit 1:

1. 🔴 **MIENTE** — rama citada inexistente **y** commit de implementación localizado por su ID. Se
   mantiene, arreglando la detección: un commit cuenta como implementación si toca **algún fichero
   que no sea `.md`**, sin mirar su prefijo.
2. 🟡 **DESFASADO** — rama citada inexistente y **sin** commit localizable por el ID. No se puede
   decir dónde está el trabajo, y precisamente por eso la línea no se puede creer. El arreglo lo
   escribe una persona; el chivato sólo deja de callarse.

Y una ayuda medida, no adivinada: si existe el tag `prerebase/<rama>`, el commit de la rama sigue
retenido y se puede ir a buscar allí. Los tags `prerebase/` son **locales**.

⛔ **No se toca su negativa a correr a ciegas** (clon shallow, sin master, historial por debajo del
suelo): es la razón por la que vive fuera de la suite, y `BacklogStatusGuardrailTest` lo vigila.

## ⚠️ Al ejecutarlo: eran SIETE, no tres, y la primera versión acusaba a los honestos

El barrido a mano que abrió este ticket se dejó cuatro docs: usaba un regex más estrecho y sólo
miraba la línea `**Estado:**`, no el bloque entero ni el prefijo `fix/`. La severidad nueva
encontró **7**.

Y de esos 7, **tres eran falsos positivos**: `DET-AUDIT-REMEDIATION-001`,
`det-verdict-not-predicate-001` y `snap-to-park-001` **declaran en su propia línea de estado que la
rama ya no existe**. Son justo los que hicieron los deberes, y los estaba señalando. De ahí la
exención `BRANCH_DECLARED_DEAD`: *citar una rama muerta para decir que está muerta no es el defecto,
es el arreglo.*

⛔ Y uno de los tres seguía acusado después de añadir la exención, porque dice «ya no\nexisten»
**partido por el salto de línea**. El bloque de estado es prosa ENVUELTA, así que la comparación
normaliza espacios: un chequeo que depende de dónde envuelve el texto no comprueba lo que cree.

**Los tres hallazgos de arriba salieron de EJECUTARLO, no de razonarlo.** Es el mismo patrón que
[MEM-A-DESCRIPTION-IS-WRITTEN-ONCE-AND-NEVER-REVISITED-001], donde dos bugs del detector los cazó
la falsificación.

## Criterio de éxito, y qué midió la falsificación

- ✅ El chivato sale **rojo** hoy, con severidad por doc, y **verde** después, diciendo cuántos miró
  y cuántos exentó (7 docs, 6 exentos).
- ✅ Los cuatro docs dicen dónde está su trabajo, verificado **en el código**, no por el asunto del
  commit.
- ✅ Las cifras del README del backlog cuadran (`BacklogStatusGuardrailTest`).

Falsificado plantando la línea vieja del tema claro y quitando cada regla por separado:

| Qué se quita | Qué contesta el chivato |
|---|---|
| nada (defecto plantado) | 🔴 `1 MIENTEN`, nombrando `fe576b3e5` |
| el mirar-los-ficheros (vuelve el filtro por prefijo `docs(`) | 🟡 `1 DESFASADOS` |
| la exención `BRANCH_DECLARED_DEAD` | 🔴 `1 MIENTEN` + 🟡 **6 DESFASADOS** (los tres honestos, acusados) |
| nada, defecto retirado | ✅ `OK`, exit 0 |

⛔ **Esa segunda fila me desmiente a medias, y queda escrito.** El criterio que escribí al abrir
decía que sin la regla nueva el doc «vuelve a salir verde». **Falso**: vuelve a salir, pero como
DESFASADO. Lo que convertía el verde en rojo era la **severidad nueva**; lo que aporta el mirar los
ficheros tocados es poder **señalar el commit** y pedir el arreglo fuerte (cita tu hash) en vez del
débil (averigua dónde está). Las dos cargan peso, pero no el mismo.

## Consumidores auditados

- `tools/backlog/branch_tell.py` — el chequeo.
- `BacklogStatusGuardrailTest.OUT_OF_SUITE_TOOLS` — su registro en la suite: hay que actualizar lo
  que dice que el chivato `carries`, porque deja de ser una sola pregunta.
- Los **cuatro** docs señalados — ✔ auditados uno a uno contra el código, y **ninguno se cerró por
  tener su código en master**, porque no es lo mismo:
  - `ui-the-light-theme…` → ✅ **Done**, `fe576b3e5`: los cuatro tokens del tema claro y el KDoc de
    la medida. Era el único realmente terminado.
  - `det-a-car-connection…` → sigue 🟡: su único commit (`1339ea263`) toca **sólo el doc**. Diseño en
    master, implementación no. Su commit pre-squash lo retiene un tag `prerebase/` **local**.
  - `ios-f2-a-wake…` → sigue 🟡: las **cuatro** etapas están en master (`8c2daf096`, squash de
    IOS-F0), medidas símbolo a símbolo; lo que falta son deberes del propio doc —
    `PARKING-DETECTION.md` no documenta el carril por etapa, y su barrido dice «(por etapa,
    pendiente)». Su sección «etapas 1 y 2, **pendiente de plegar**» llevaba un mes describiendo
    trabajo ya plegado.
  - `ios-store-shots…` → sigue 🟡: 92 ficheros de metadata, `Snapfile` y los lanes `screenshots` y
    `upload_metadata` están en master (`6970b4065`), y la mitad del título la sostiene `:mockkit`
    con su guard. ⏳ Falta un **Mac** para correr las capturas.
- `BacklogStatusGuardrailTest.OUT_OF_SUITE_TOOLS` — ✔ su `carries` ya no describe una sola pregunta.
- ⛔ **El worktree que cita un doc NO entra en el chequeo, y es deliberado**: `../Paparcar-aa-signal`
  no existe tampoco, pero una ruta de worktree es **local a una máquina**. Un guard que la juzgue
  acusa a cualquiera que clone el repo en otro sitio. Se arregla a mano, como aquí, cuando se
  reescribe la línea.
- `MEMORY.md` — su línea 🔵 sobre este hallazgo pasa a ✅ con el hash.
