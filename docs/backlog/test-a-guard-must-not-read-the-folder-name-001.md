# TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001 · un guardarraíl no puede depender de cómo se llame la carpeta

**Estado:** ✅ **Done** · rama `bugfix/TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001` ·
rebasada sobre `1b690f45`

> Nació buscando otra cosa —el crash del Dev Catalog, que el user arregló en master mientras tanto—
> y lo que queda es lo que ese rato dejó al descubierto. El crash NO es de este ticket.

## Problema

Dos cosas, medidas el 15-09 con el Dev Catalog delante.

**(a) `:shared:testDebugUnitTest` no es la suite.** El grafo del flavor mock tiene su propio
guardarraíl —`MockKoinModuleVerifyTest`, de [MOCK-A-SECOND-GRAPH-NEEDS-A-SECOND-VERIFY-001]— que
resuelve cada constructor del grafo mock y falla en segundos cuando un puerto se declara en
`dataModule` y se olvida en `mockModule`. Existía, era correcto, y no cazó nada: vive en `:app`, y
el comando que todo el mundo escribe (y que documentan CLAUDE.md y la skill `nuevo-ticket`) solo
corre `:shared`. Dos tickets seguidos dejaron su repositorio fuera del grafo mock
(`ZoneWatchRepository` en `56d63e1f`, `FuelWatchRepository` en `1cff6f26`) y el catálogo murió al
entrar, con un `NoDefinitionFoundException` en pantalla. **Un guardarraíl que nadie ejecuta es un
comentario.**

**(b) Un guard cambiaba de opinión según el nombre de la carpeta.** `GuardrailScope` decidía qué es
código de producción con `path.contains("commonMain") || path.contains("androidMain") ||
path.contains("mock")` sobre el path **absoluto**. Este worktree se llamaba `Paparcar-mock-graph`,
así que **todos** los ficheros del repo contenían «mock» y pasaban por producción — tests incluidos.
El resultado: dos guardarraíles en rojo sin que se tocara una línea de lo que vigilan
(`DetectionDoctrineGuardrailTest` marcando literales que solo existen en tests, y
`HistoryReadsGuardrailTest` reventando con un `List has more than one element` que no dice nada). En
el árbol principal pasaban; en el worktree, no. Eso es un guard que no se puede creer.

## Doctrina violada

- [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]: una suite verde tiene que demostrar que MIRÓ. Aquí
  el verde de `:shared` no incluía el único test que mira el grafo mock, y el rojo del otro guard
  dependía de dónde estuviera clonado el repo — dos formas distintas de que el color no signifique
  nada.

## Diseño

1. **`./gradlew testAll`** en el raíz: `:shared:testDebugUnitTest` + `:app:testMockDebugUnitTest`.
   Un comando que no se puede escribir a medias, en vez de una nota pidiendo acordarse del segundo.
2. **`GuardrailScope.inSourceSet(...)`**: el source set se pregunta por el TRAMO de ruta
   (`/src/commonMain/`, `/src/androidMain/`, `/src/mock/`), nunca por una subcadena del path
   absoluto. La forma anclada ya existía tres líneas más abajo para los source sets de test
   (`/commonTest/`, `/androidUnitTest/`) — esto es aplicar a producción lo que los tests ya hacían.

⛔ **Fuera de alcance**: los dos bindings que faltaban. Los arregló el user en master mientras esto
se escribía, con las implementaciones Room reales en vez de fakes.

## Criterio de éxito

- `./gradlew testAll` en verde, y **rojo** si se quita un binding del grafo mock.
- Los guardarraíles dan el mismo resultado desde cualquier carpeta: comprobado ejecutándolos en un
  worktree llamado `Paparcar-mock-graph`, que es justo el nombre que los rompía.
- ✅ En device (Redmi, mock `4366da19…`): el Dev Catalog **entra al grafo real sin crashear** — Home
  con el Toyota aparcado —, que es lo que el crash impedía.

## Consumidores auditados

| Sitio que decide «esto es producción» por el path | Estado |
|---|---|
| `GuardrailScope.featureFiles` / `presentationFeatureFiles` / `commonMainProduction` / `productionSourceFiles` | ✅ anclados a `/src/<sourceSet>/` |
| `GuardrailScope` (source sets de TEST) | ✅ ya estaban anclados (`/commonTest/`, `/androidUnitTest/`…) |
| `GuardrailScopeTest` | ✅ su propio filtro de ejemplo, anclado igual |
| `HoldLaneGuardrailTest` / `TriggerLaneGuardrailTest` (`MAIN_SOURCE_SET`) | ✅ ya usaban `Regex("""/src/[a-zA-Z]*[Mm]ain/""")` — el precedente que faltaba copiar |
| `FieldTraceGuardrailTest` | ✅ filtra por paquete (`replay/Trace_*`), no por carpeta |

## Y lo primero que cazó: master no compilaba

Al correr `testAll` sobre `1b690f45` reventó `:app:mergeMockDebugResources`:
`notif_zone_spot_text_provisional` llevaba el apóstrofo **crudo** en `values-it` y `values-fr` de
`app/src/main/res`. La línea de justo encima (`notif_zone_spot_text`) sí lo escapaba.

⛔ **Las dos reglas del apóstrofo son OPUESTAS y conviven en este repo:**
- `app/src/main/res/**` (Android res, notificaciones) → **`'`**. Un `'` crudo no compila:
  *«Invalid unicode escape sequence»*, y se lleva por delante el build de la app entera.
- `shared/src/commonMain/composeResources/**` (Compose Resources) → **`'` crudo**. Ahí `'` no se
  desescapa y el usuario lee la barra [COPY-APOSTROPHE-IS-NOT-ESCAPED-001].

Y es exactamente el argumento del ticket: `:shared:testDebugUnitTest` estaba **verde** mientras la
app no compilaba sus recursos. La suite no miraba a `:app`.

## Bitácora

- **15-09** · Abierto persiguiendo el crash del Dev Catalog. El guardarraíl del grafo mock lo cazaba
  en 10 s; nadie lo ejecutaba. Al correrlo desde este worktree aparecieron dos rojos más que no eran
  del código sino del NOMBRE de la carpeta. El user arregló los bindings en master; queda esto.
- **15-09** · Cerrado. `testAll` cazó, en su primera ejecución de verdad, que master no compilaba
  recursos: dos apóstrofos crudos en IT y FR del ticket de zona, arreglados aquí.
