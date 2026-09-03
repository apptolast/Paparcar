# ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001 · El paso 2 no cuenta cómo se libera tu plaza, y el paso 3 ofrece plazas que aún no existen

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash)
> Cerrado DENTRO del commit de `ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001`, por la
> misma razón: misma superficie, mismos ficheros.

## Problema

Dos huecos en el checklist guiado, señalados por el user tras verlo en device (03-09).

**A · El paso 2 no dice lo que de verdad pasa — y con la detección apagada, lo que dice es mentira.**
Su subtítulo era *«Mientras tu coche está aparcado la app lo vigila, para que no tengas que recordar
dónde lo dejaste»* — describe una comodidad, no el mecanismo. Lo que el usuario necesita saber es que
**al irse, su aparcamiento libera una plaza para otro conductor**, y que eso ocurre de dos formas: la
detección automática, o él mismo con «Me voy». Eso estaba explicado, sí, pero **solo dentro de la
modal del explainer** — la misma modal que hay que descubrir levantando la sheet, o sea justo lo que
el usuario dijo que nadie va a hacer.

Y hay un agravante que señaló el user: contar la mitad automática **presupone que la detección está
activa**. Con la detección parada, *«la app lo vigila»* y *«al irte libera tu plaza»* son promesas que
la app no está cumpliendo — dichas, además, en la superficie donde la app habla de sí misma, que es el
peor sitio posible para decir algo que no es verdad. La única mitad siempre cierta es que **el usuario
puede cerrar su aparcamiento cuando quiera con «Me voy»**.

**B · El paso 3 ofrecía plazas que no existen, y su botón hacía otra cosa.** Dos fallos apilados:

| Pieza | Decía / hacía | Problema |
|---|---|---|
| Título | «Encuentra una plaza libre, o avisa de una» | promete encontrar; al lanzar no hay ninguna, porque no hay usuarios |
| CTA | «Ver plazas» | **el botón mentía**: disparaba `RequestReportMode`, o sea el modo de AVISAR |
| Explainer | «Y si ves una plaza libre, avísala» | ya iba por avisar — el único desalineado era la fila |

O sea: el paso invitaba a VER, abría el flujo de AVISAR, y en día uno no había nada que ver.

## Doctrina violada

- **[COPY-SPOT-IS-NOT-A-PARKING-001] en su punto más delicado.** El paso 2 es exactamente la frase que
  tiene que nombrar las dos cosas distintas: *tu APARCAMIENTO* libera *una PLAZA*. No decirlo deja al
  usuario sin el concepto central del producto.
- **Un CTA nombra lo que hace.** «Ver plazas» sobre un handler que entra en modo reporte es la misma
  clase de defecto que `home_release_dialog_delete_only` («BORRAR» en un botón que no borraba,
  destapado en `ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001`): el copy y el botón se escribieron en
  momentos distintos y nadie los volvió a leer juntos.
- **Nada de `if`s de producto en el composable** [DET-ASK-STATE-001]. Que el paso tenga dos caras NO
  puede resolverse con un `if (spots.isEmpty())` dentro de la tarjeta: lo decide la proyección pura.

## Señales / datos disponibles

- `resolveFirstSteps` ya es la proyección pura de los pasos, con test propio.
- La regla «qué plazas están realmente en oferta» ya existía… **escrita inline en la slice**
  (`nearbySpots.any { it.status.isAvailable && !isMyOwnLiveSession(it) }`, que alimentaba
  `hasAnySpots`). Una segunda copia para el paso 3 sería la vía directa a que el paso prometa una
  lista que luego sale vacía.
- La sección «PLAZAS LIBRES CERCA» ya es un item con key de la lista de la sheet, así que se puede
  scrollear hasta ella sin contar los items que tenga encima.
- Los botones reales existen y se pueden CITAR en lugar de parafrasear: `home_parking_leave_release`
  = «Me voy» / «I’m leaving» / «Me ne vado» / «Je pars» / … en los 9 locales.

## Diseño

**A · El paso 2 cuenta el mecanismo en su propia fila, y tiene DOS CARAS para no mentir.**
`first_steps_watch_sub` pasa a decir que al irte tu aparcamiento libera una plaza para otro conductor,
**o** que lo cierras tú con «Me voy» — citando el botón real de cada locale, no una paráfrasis. El
explainer sigue detallando las dos formas; la fila deja de depender de que alguien lo abra.

Y `WatchAsk`, resuelto por la proyección desde `DetectionUiState.isDetectionStopped` (el MISMO flag
con el que el diálogo de liberación decide si puede prometer lo mismo):

| Cara | Cuándo | Qué dice | CTA |
|---|---|---|---|
| `EXPLAIN_RELEASE` | la detección funciona | las dos formas de liberar | ninguno — no hay nada que hacer |
| `TURN_IT_ON` | la detección está parada | «Activa la detección y funciona sola» · *«Ahora mismo tu plaza solo se libera cuando tú lo dices con 'Me voy'. Con la detección activada, Paparcar nota que te has ido y la libera por ti.»* | «Activar» |

Nótese que la cara honesta **no pierde** la mitad verdadera: dice que hoy la plaza se libera con «Me
voy», que es exactamente lo que el usuario puede hacer siempre.

**Por qué el paso 2 ahora sí lleva CTA, cuando `ONBOARDING-FIRST-STEPS-…-001` decidió a propósito que
no.** Aquel razonamiento era *«no hay nada que el usuario pueda hacer aquí, y la superficie de abajo ya
tiene el botón; un botón aquí sería una segunda voz para una acción»*. Sigue siendo cierto — y por eso
el CTA **no se añade solo**: viene con la cesión de voz que ya existía para el paso 1.

`firstStepsOwnsColdStart: Boolean` se convierte en `firstStepsOwns: FirstStepsOwnership`
(`NOTHING` · `COLD_START` · `DETECTION_OFF`). Un paso solo puede ser un paso, así que es UN valor y no
una bolsa de flags. Con `DETECTION_OFF`, la fila `DetectionStory.Inactive` **se calla**, el paso dispara
`HomeIntent.EnableAutoDetection` (el mismo intent de esa fila) y **toma prestada su etiqueta**
(`home_det_producer_cta`, sin key nueva): una acción, una etiqueta, un botón en pantalla.

Lo que NO se cede, y es deliberado: el bloqueo CORE, una pregunta viva, un nudge pendiente y **toda
alerta de vigilancia frágil o interrumpida**. Esa última importa: la fila nombra la causa concreta
(batería, servicio muerto) y el paso solo podría decir algo más vago. Un tutorial no es razón para
callar nada de eso. [DET-WATCH-HONEST-001]

**B · El paso 3 tiene DOS CARAS, decididas por la proyección.** `FindSpotAsk` (`SEE_NEARBY` /
`REPORT_ONE`) sale de `resolveFirstSteps(hasSpotsOnOffer = …)` y viaja en `FirstStepsProgress`:

| Cara | Cuándo | Título/CTA | Acción |
|---|---|---|---|
| `SEE_NEARBY` | hay al menos una plaza en oferta | «Encuentra una plaza libre cerca» · «Ver plazas» | `RevealFreeSpots`: abre la sheet y baja a la sección de plazas |
| `REPORT_ONE` | no hay ninguna (día uno) | «¿Ves una plaza libre? Avísala» · «Avisar de una plaza» | `RequestReportMode`, que es lo que el botón ya hacía |

El glifo también sigue a la cara: avisar es poner una plaza EN el mapa (`AddLocationAlt`, el mismo
gesto del paso 1), no explorar buscándola (`Explore`).

**La fuente de «qué hay en oferta» se unifica**: `HomeState.spotsOnOffer` (disponible y no mi propio
coche aún aparcado) con TRES consumidores — la barra de filtros/estado vacío (`hasAnySpots`, que antes
lo escribía inline), la cara del paso 3, y el destino de su botón. Sin filtro de talla a propósito: el
filtro es una lente que el usuario eligió, y la pregunta aquí es si la comunidad tiene algo.

**El scroll no cuenta items.** `RevealFreeSpots` resuelve el índice por la KEY del header
(`FREE_SPOTS_SECTION_KEY`, ahora una constante compartida en vez de un literal repetido) y, si aún no
está compuesto, cae al último item — que no es otro destino, porque la sección de plazas es lo último
de la sheet. Ambos caminos terminan dentro de ella.

Casos honestos que quedan y por qué se aceptan:
- Con un filtro de talla activo, «Ver plazas» puede aterrizar en «ninguna de tu tamaño». Es lo
  correcto que ver: la barra de filtros está justo ahí para deshacerlo.
- El explainer del paso 3 es uno solo para las dos caras. No hace falta partirlo: ya cuenta las dos
  mitades (una plaza nace de tu salida, o de que tú avises de la que viste), que es lo que el paso
  necesita explicar en ambos casos.

## Criterio de éxito

- `FirstStepsTest`: sin plazas → `REPORT_ONE`; con plazas → `SEE_NEARBY`; y que existir plazas **no**
  complete el paso (se completa abriendo una, no viéndola en el mapa).
- `FirstStepsTest`: detección parada → `TURN_IT_ON` y `owns == DETECTION_OFF`; funcionando →
  `EXPLAIN_RELEASE` y `owns == NOTHING`; y con el paso 1 aún pendiente, `owns == COLD_START` aunque la
  detección esté parada (un paso solo posee una fila).
- `DetectionStoryTest`: `Inactive` se calla con `DETECTION_OFF` y **no** con `COLD_START` (y al revés
  para `AwaitingFirstPark`); una vigilancia interrumpida sigue hablando en los dos casos.
- El CTA de cada cara dispara la acción que nombra.
- 3 keys nuevas + 3 reescritas en los **9 locales** (`LocaleParityGuardrailTest` verde).
- Las dos caras visibles en la galería mock y en los previews.
- Tests verdes y `assembleMockDebug`/prod compilando.
- En device: ver la cara `REPORT_ONE` (base vacía hoy) y forzar la otra con la plaza de prueba.

## Consumidores auditados

- **`resolveFirstSteps`** — 5 llamadas: `HomeState.firstSteps` (producción), `FirstStepsTest`,
  `StateGalleryScreen`, `FirstStepsPreviews`, y el default del parámetro nuevo (`false` = la cara
  conservadora, la que no promete plazas). Las cuatro primeras pasan el valor explícitamente.
- **`hasAnySpots`** — su definición inline se sustituye por `spotsOnOffer.isNotEmpty()`; los tests
  `should_report_nothing_on_offer_when_the_only_spot_is_my_own_parked_car` y
  `should_offer_the_spot_again_once_its_session_is_released` cubren que la regla no cambió.
- **`FirstStep.copy()`** — ahora toma la cara; `StepMarker` recibe el glifo YA resuelto en vez de
  volver a llamar a `copy()` (resolvía el icono dos veces, y con dos caras eso sería un icono que se
  contradice con su propio título).
- **`first_steps_spot_cta`** — se mantiene para la cara `SEE_NEARBY`, y ahora sí describe lo que el
  botón hace.
- **`firstStepsOwnsColdStart`** — 1 productor (`homeSheetItems`) y 1 consumidor
  (`resolveDetectionStory`), más 4 tests; todos migrados al enum. No queda ningún sitio que hable de
  «cold start» como si fuera la única fila cedible.
- **`DetectionStory.Inactive`** — antes se devolvía incondicionalmente; ahora se calla solo con
  `DETECTION_OFF`. El test `should_suppressNothingElse_when_theChecklistOwnsTheColdStart` ya afirmaba
  que seguía visible con el paso 1 al mando, y sigue verde: es la mitad del contrato que no cambia.
- **El CTA del paso 2** no crea key nueva: reutiliza `home_det_producer_cta` a propósito, para que el
  paso y la fila que sustituye no puedan decir cosas distintas del mismo botón.
