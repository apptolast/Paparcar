# ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001 · Una plaza nace de dos maneras distintas y la app nunca lo explica

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash), detrás de
`ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001`, del que dependía.
> ⏳ Queda verlo en mano. 📌 Al cerrarlo: como esta rama salía de la rama de A y A entró aplastada,
> el rebase hubo que hacerlo con `git rebase --onto master <viejo-tip-de-A>` — un `git rebase master`
> a secas intenta reaplicar el commit de A y choca contra su propia versión ya mergeada.

## Problema

En Paparcar una plaza libre puede nacer de **dos formas que no tienen nada que ver entre sí**, y el
usuario no tiene dónde aprender la diferencia:

**Forma 1 — la plaza la dejas TÚ.** Marcas tu aparcamiento; la plaza aparece cuando te vas. Y eso
ocurre por dos mecanismos distintos:
- *automático*: la detección ve que te has ido y publica la plaza sola;
- *a mano*: el **"me voy"** de la modal (`HomeReleaseDialog`), que además ofrece dos respuestas —
  "avisar de la plaza" o "solo liberar, sin publicar".

**Forma 2 — la plaza la has VISTO.** Vas por la calle, ves un hueco libre que no es tuyo, y avisas
(`HomeSheetAction.RequestReportMode` → `SpotType.MANUAL_REPORT`).

Hoy los dos caminos existen en la UI y **ninguno se explica**. El `HomeReleaseDialog` es el único
sitio donde se insinúa que irse publica una plaza, y solo aparece cuando ya estás usándolo. Un
usuario nuevo no tiene forma de saber que marcar su coche es lo que alimenta la comunidad.

## Doctrina violada

`COPY-SPOT-IS-NOT-A-PARKING-001` — la regla ya está escrita en CLAUDE.md y este es justo el hueco
que no cubrió. La regla arregló el **vocabulario** ("marcar aparcamiento" vs "avisar de una plaza")
pero no creó ninguna superficie donde se enseñe **la mecánica** que hay detrás de esas dos palabras.
Su propia frase — *"al irte, tu APARCAMIENTO libera una PLAZA"* — no está dicha en ninguna parte de
la app fuera del cuerpo de un diálogo.

## Señales / datos disponibles

Todo el comportamiento ya existe; falta únicamente explicarlo:

| Camino | Entrada en la UI | Efecto |
|---|---|---|
| Marcar tu aparcamiento | `HomeIntent.EnterAddParkingMode` | Sesión `UserParking` + geocerca + vigilancia |
| Salida automática | Detección (BT o Coordinator) → `ConfirmParkingUseCase` | Publica la plaza sin preguntar |
| Salida a mano ("me voy") | `HomeReleaseDialog` | "Avisar de la plaza" / "solo liberar" |
| Avisar de una plaza vista | `HomeSheetAction.RequestReportMode` | `Spot(type = MANUAL_REPORT)` |

## Diseño

Depende de la espina del tutorial (`ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001`) y aterriza en
sus **pasos 2 y 3**, que este ticket llena de contenido:

- **Paso 2 · "Cuando te vayas, tu sitio queda libre para otro"** — explica los dos mecanismos de la
  Forma 1. El automático como comportamiento por defecto, y el "me voy" como el control manual que
  siempre tienes. Debe dejar claro que **liberar no obliga a publicar**: el diálogo tiene esa
  segunda respuesta y ocultarla sería vender la app como más intrusiva de lo que es.
- **Paso 3 · "Y si ves una plaza libre, avisa"** — la Forma 2, explícitamente marcada como *otra
  cosa*: no es tu coche, no crea sesión, no arma detección.

⛔ **La regla de copy que gobierna este ticket:** las dos formas no pueden describirse con el mismo
verbo ni con la misma palabra. *Tuyo* → **aparcamiento**, verbo *marcar*. *De la comunidad* →
**plaza**, verbo *avisar*. Cualquier frase que hable de las dos a la vez las nombra distinto.

## Criterio de éxito

- Los dos pasos existen en el tutorial con copy que distingue las dos formas, en los 9 locales.
- Ninguna cadena nueva usa "plaza" para lo del usuario ni "aparcamiento" para lo de la comunidad
  (revisión manual sobre la tabla de vocabulario de CLAUDE.md, en los 9 idiomas).
- El paso 2 menciona la salida manual, no solo la automática.
- Galería de estados y escenarios mock cubren ambos pasos.

## Lo construido

`FirstStepExplainerSheet` — se abre **tocando la fila** de cualquier paso del checklist, hechos
incluidos: eso es lo que hace que "repetir el tutorial" desde Ajustes valga algo cuando el estado
real ya cumple los tres pasos. Va por `HomeSheetAction.OpenFirstStepExplainer`, que es literalmente
para lo que existe ese canal (*local UI orchestration — dialogs*), traducido en un solo sitio.

Cada explicador lista las **formas** de que ocurra su cosa, y el paso 2 es el que carga con las dos
mecánicas de la Forma 1:

| Paso | Formas que lista | Nota (la salvedad sin la que la explicación prometería de más) |
|---|---|---|
| 1 · Marcar | *A partir de ahí, el coche es cosa nuestra* | — |
| 2 · La salida | **Solo** (la detección avisa por ti) · **O lo dices tú** ("Me voy") | *Cerrar tu aparcamiento no te obliga a avisar de nada: "Solo liberar" lo termina sin decírselo a nadie* |
| 3 · La comunidad | **Avisar de una plaza** | *Esto no va de tu coche: no abre ningún aparcamiento tuyo ni activa ninguna vigilancia* |

El copy **cita los botones reales de la app** ("Me voy", "Solo liberar") en vez de parafrasearlos,
para que quien vaya a buscar lo que acaba de leer encuentre esa palabra exacta en pantalla.

## 🔴 Defecto encontrado al verificar esas citas

Al comprobar que la cita coincidía con el botón real en los 9 idiomas apareció esto:

> **`home_release_dialog_delete_only` decía "Solo BORRAR" en 7 de los 9 idiomas.**

Ese botón dispara `ParkingReleaseReason.DEPARTURE_UNPUBLISHED` — es una **salida sin publicar**, no
un borrado. EN y ES se habían corregido en su día ("Just release" / "Solo liberar") y los otros siete
se quedaron en la redacción vieja. Y hay un borrado de verdad en otro sitio (`RECORD_DELETED` →
`home_parking_menu_delete`), así que en esos 7 idiomas **dos botones distintos decían "borrar" y uno
de ellos mentía** — justo sobre la mecánica que este ticket existe para explicar. Un usuario que lee
"Nur löschen" cree que está destruyendo su registro cuando solo se está yendo en silencio.

De paso, en el MISMO diálogo, DE y NL usaban el lado equivocado de la tabla de vocabulario para la
plaza comunitaria (`Stellplatz` — una tercera palabra que no está en la tabla — y `Parkeerplaats`,
que es el aparcamiento del usuario).

| Key | Locales tocados | Antes → después |
|---|---|---|
| `home_release_dialog_delete_only` | it · pt · fr · de · nl · pl · ro | *borrar* → *liberar* (`Libera soltanto`, `Só libertar`, `Libérer seulement`, `Nur freigeben`, `Alleen vrijgeven`, `Tylko zwolnij`, `Doar eliberează`) |
| `home_release_dialog_title` | de · nl | `Stellplatz freigeben?` → `Platz freigeben?` · `Parkeerplaats vrijgeven?` → `Plek vrijgeven?` |
| `home_release_dialog_publish` | de | `Stellplatz teilen` → `Platz teilen` |

## Criterio de éxito — estado

- ✅ Los dos pasos explican las dos formas, en los 9 locales (17 keys nuevas).
- ✅ Ninguna cadena nueva usa "plaza" para lo del usuario ni "aparcamiento" para lo comunitario.
- ✅ El paso 2 menciona la salida manual, y su segunda respuesta.
- ✅ Galería (3 variantes) + `FirstStepExplainerPreviews.kt` en paridad.
- ✅ `2174 tests · 0 failures` (incluye `LocaleParityGuardrailTest`) · mock y prod compilan.
- ⏳ Verlo en mano (`/run`).

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `HomeReleaseDialog` (`home_release_dialog_*`) | **Cerrado, y estaba roto**: ver el defecto de arriba. El tutorial cita ahora sus palabras, ya corregidas, en los 9 idiomas |
| `home_det_ask_sub` (*"Guardo tu aparcamiento y publico la plaza cuando te vayas"*) | **Cerrado sin tocar**: nombra las dos cosas distinto y ya dice la Forma 1 correctamente; es la frase que el paso 2 amplía |
| Capa de notificaciones (`app/src/main/res`, cerrada en `COPY-NOTIFICATION-LAYER-STILL-SAYS-PLAZA-001`) | **Verificado**: no diverge; este ticket no la toca |
| `home_parking_menu_delete` / `RECORD_DELETED` | **Cerrado**: es el borrado REAL, y ahora es el único botón que dice "borrar" en los 9 idiomas |
