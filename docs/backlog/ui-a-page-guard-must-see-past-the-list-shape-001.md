# UI-A-PAGE-GUARD-MUST-SEE-PAST-THE-LIST-SHAPE-001 · Un guard que reconoce la página por su lista no ve las otras cuatro

**Estado:** ✅ Done · en master · sin resultado visual (no se mueve un píxel)

## Problema

[UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001] dio dueño al aire vertical de una página
(`PapPageRhythm`) y lo vigiló con dos chequeos. El segundo —el que de verdad cierra el agujero,
porque caza la pantalla que entre mañana— detecta la población **por la forma**:

```kotlin
it.text.contains("LazyColumn(") && it.text.contains("contentPadding")
```

Medido en master el 20-10 sobre los 181 ficheros de `presentation/`: esa forma selecciona **7**
ficheros. Y **cuatro superficies que SÍ colocan bloques contra el borde de la pantalla quedan
fuera**, no por doctrina sino porque su scroll lo pone otro:

| superficie | qué es | por qué la forma no la ve |
|---|---|---|
| `VehiclePageContent` | la página de un vehículo dentro del pager | `Column`, el scroll lo pone el pager |
| `HomeHeaderSection` | buscador + fila de zonas sobre el mapa | `Column` flotante, sin lista |
| `SharedCarSection` | la sección del grupo dentro de esa página | `Column`, hereda el scroll |
| `VehicleRegistrationScreen` | alta de vehículo | `Column` + `verticalScroll`, no `LazyColumn` |

Las cuatro citan `PapPageRhythm` hoy **porque las editó ese ticket, no porque nada se lo exija**.
Si mañana alguien escribe ahí `padding(12.dp)`, el chequeo 2 no se entera: no están en su
población. Es la misma forma del defecto original — un rol sin dueño se re-decide— un nivel más
arriba: **un rol CON dueño pero sin guardia se des-adhiere en silencio**.

## Doctrina violada

- `TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001` — una prohibición sin testigo no es un chequeo, y
  una población que no contiene al sujeto es verde por vacío. Las cuatro pasan el guard sin ser
  miradas.
- **Sistemas, no parches** — el invariante («el aire de una superficie lo dice un rol») vive en un
  sitio, pero su guardia sólo alcanza a una de las dos formas en que este árbol escribe una página.

## Señales / datos disponibles

Poblaciones candidatas medidas en master (`af99ee1a4`), sobre `presentation/`:

| señal | ficheros | ¿ve a las cuatro? |
|---|---|---|
| `LazyColumn(` + `contentPadding` (la actual) | 7 | ninguna |
| `verticalScroll(` | 5 | sólo `VehicleRegistrationScreen` |
| `padding(horizontal = …)` | 35 | sí, y arrastra 22 componentes internos |
| `verticalArrangement = Arrangement.spacedBy` | 28 | tres de cuatro, más ruido interno |

⛔ Las dos últimas se descartan por la misma razón: meten en la población el **aire DENTRO de un
bloque**, que por doctrina NO debe citar `PapPageRhythm` — es `PapGap` o una constante privada con
su razón. Una población que exige la cita a quien no debe citarla se paga en exenciones, y una
lista de exenciones larga es un guard que ya no dice nada.

## Diseño

Dos poblaciones, porque son dos preguntas distintas, y ninguna sustituye a la otra.

### 1 · La forma, ampliada: una página también se scrollea a mano

`pageFiles()` pasa a seleccionar `LazyColumn(` + `contentPadding` **o** `verticalScroll(`. Sube de
7 a ~12 ficheros y mete en el radar las pantallas de scroll manual. Las que no son páginas salen
por la lista blanca **nombrada, con su razón en el propio guard** — que es donde se consulta,
no en el doc de un ticket cerrado:

- `HomeBottomSheet` — hoja que se arrastra; su aire es el de un peek.
- `OnboardingScreen`, `FirstStepExplainerScreen`, `VehicleSizeExplainerScreen` — composiciones
  centradas (ilustración, título, prosa, CTA). Sus márgenes 24/32 son deliberados.
- `PaywallScreen` — superficie de venta con su `SCREEN_H_PADDING_DP` 20 propio.

### 2 · Las adheridas: lo que la forma no ve, no se des-adhiere en silencio

Chequeo nuevo: las superficies que **ya** piden su aire al rol tienen que seguir pidiéndolo. Son
las tres que ninguna forma selecciona (`VehicleRegistrationScreen` entra ya por `verticalScroll`):
`VehiclePageContent`, `HomeHeaderSection`, `SharedCarSection`.

⛔ **Sí, es una lista — y es deliberado.** La enumeración es mala como única población, porque deja
fuera lo que nazca mañana; eso lo cubre la población por forma (1). Aquí la pregunta es la
contraria y sólo se puede contestar nombrando: *¿sigue adherido lo que migramos?* El guard falla
también si el fichero **ya no existe**, para que un rename no lo silencie.

## Criterio de éxito

1. Las cuatro quedan vigiladas: quitar la cita en cualquiera pone el build rojo.
2. Una página nueva con scroll manual entra sola en la población.
3. Los chequeos nuevos **falsificados** con `--rerun-tasks`, y el mensaje de fallo nombra fichero.
4. `./gradlew testAll` verde.
5. Sin resultado visual: no se toca un píxel [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001].

## 🔑 Hallazgo al falsificar · un import huérfano satisfacía al guard

El chequeo se escribió como `text.contains("PapPageRhythm")`, igual que el de
[UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001]. **Al intentar falsificarlo salió verde**: cambiados los
dos usos de `VehiclePageContent` por la escala a pelo, el `import` se quedó en el fichero — Kotlin
sólo avisa de un import sin usar— y el guard lo leyó como adhesión. El KDoc que explica el rol
habría bastado igual.

No es un detalle de regex: es **la forma exacta del defecto que este ticket persigue**, un nivel más
abajo. Un fichero puede nombrar el rol y no preguntarle nada, que es justo lo que deja un refactor
descuidado. Los dos chequeos pasan a contar **uso** — `PapPageRhythm.` fuera de `import` y fuera de
comentario— vía `usesPageRhythm()`.

⛔ Y la lección de método: el chequeo nuevo **se dio por bueno en verde durante dos intentos** y
sólo cantó al falsificarlo de verdad. Las dos primeras falsificaciones no probaron nada — una
disparó el otro chequeo (sustituí por un literal) y la otra ni compiló (quité un import que hacía
falta), y el `grep` del comando se comía el desenlace. Una falsificación que no imprime QUÉ chequeo
rompió no es una falsificación. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]

## Falsificaciones (las tres, con `--rerun-tasks`)

| qué se rompe | qué chequeo canta |
|---|---|
| `VehiclePageContent` cambia sus usos por la escala y **deja el import** | `every adhered surface keeps asking PapPageRhythm` |
| una clave de `ADHERED_SURFACES` deja de existir (rename) | el mismo, con el mensaje de *¿renombrado?* |
| se quita `PaywallScreen` de la lista blanca | `every page list asks PapPageRhythm for its rhythm` — prueba que la forma ampliada **sí** ve el scroll manual |

## Consumidores auditados

| superficie | cómo queda |
|---|---|
| `VehiclePageContent` · `HomeHeaderSection` · `SharedCarSection` | vigiladas por `ADHERED_SURFACES`: ninguna forma las ve, y ahora no pueden soltarse en silencio |
| `VehicleRegistrationScreen` | entra **sola** por `verticalScroll` en la población por forma; no se duplica en la lista |
| `OnboardingScreen` · `FirstStepExplainerScreen` · `VehicleSizeExplainerScreen` · `PaywallScreen` | entran por la forma nueva y salen por la lista blanca, **con su razón escrita en el guard** en vez de en el doc de un ticket cerrado |
| `HomeBottomSheet` | exenta como ya estaba (hoja que se arrastra) |
| `HomeSheetContent` | sigue fuera: tiene `contentPadding` pero su `LazyColumn(` vive en `HomeBottomSheet`. Hoy acierta — es hoja, no página— pero por la forma del texto, no por doctrina. Queda **escrito** aquí: si algún día deja de ser una hoja, nada lo detectará |
| los 22 componentes con `padding(horizontal = …)` literal | **fuera de alcance a propósito**: son aire DENTRO de un bloque (rama 3), y exigirles el rol de página sería el error contrario. Su literal infringe la regla de magic numbers, que es otro barrido y otro ticket |

## Estado

- ✅ `pageFiles()` ampliado: la forma ve también `verticalScroll` — población **7 → 12**, floor 4 → 6.
- ✅ `every adhered surface keeps asking PapPageRhythm`: 3 superficies que ninguna forma ve, y falla
  también si el fichero desaparece.
- ✅ Los dos chequeos cuentan **uso**, no mención (`usesPageRhythm()`), tras descubrir que un import
  huérfano los satisfacía.
- ✅ Tres falsificaciones con `--rerun-tasks`, cada una nombrando su chequeo.
- ✅ `./gradlew testAll --rerun-tasks` verde (93 tasks ejecutadas).
