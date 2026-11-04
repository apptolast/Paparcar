# HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001 · fase 1 del rediseño: el primer vistazo habla de tu coche y de lo que hay aquí

**Estado:** ✅ Done · rama `feature/HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001-phase1` · mergeado a master

Implementa la **fase 1** del plan de
[`UX-THE-HOME-MUST-EARN-ITS-FIRST-GLANCE-001`](ux-the-home-must-earn-its-first-glance-001.md), que
cerró la fase de diseño con la dirección **B · Smart mobility** elegida por el user y 18 decisiones
documentadas. El plan vive en `design-prototypes/paparcar-home/interaction-spec.md` §9 y se puede
ver en `plan.html`.

## Problema

Medido en el código en la fase de diseño:

- El peek son **88 dp**: en reposo se ve el mapa y **una** fila, así que **el estado del coche y la
  acción principal no se ven sin arrastrar**. Y desde el primer despliegue la cabecera habla del
  carril, no del coche (`laneHeaderLatched`).
- Para el usuario **nuevo** hay **~700–800 dp** de bloque fijo antes del primer dato del carril
  (hasta 6 filas de checklist + fila de detección + 2 cabeceras decorativas + tira de coches); para
  el establecido, ~340–390. La home es más densa justo para quien menos sabe — y con <20 usuarios,
  el nuevo es el único que hay.
- `HomeNearbyLensRow` pinta los tres sujetos a `weight(1f)` **mientras su propio KDoc declara que no
  son pares** («free spots are the product; car parks and fuel are complements»).
- La tercera línea de los peeks de detalle **repetía la dirección**, porque hay sujetos sin nombre
  propio (una plaza no se llama de ninguna manera).

## Doctrina violada

Ninguna regla de CLAUDE.md se rompe en el código actual. Lo que falla es una tensión **declarada y
no aplicada**: `HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001` dice que la jerarquía vive «en
el DEFAULT y en el orden, nunca en la distancia vertical», y la fila la renderiza como tres tercios
idénticos.

## ⚠️ Corrección del plan al abrir el ticket

El plan ponía `ResolveNearbyNotablesUseCase` en la **fase 2** y el peek con «Aquí y ahora» en la
**fase 1** — y el segundo **necesita** al primero. Es una inversión de dependencia mía, y se corrige
aquí:

| Tarea | Estaba en | Va a | Por qué |
|---|---|---|---|
| `ResolveNearbyNotablesUseCase` (2.1) | fase 2 | **fase 1** | el carrusel no puede decidir qué enseña dentro del composable: sería lógica que la 2.1 tendría que reescribir, y el proyecto la quiere en un caso de uso con su test |
| Lo recordado al arrancar (2.3) | fase 2 | **fase 1** | sin ella el carrusel arranca sólo con la tarjeta de plazas, y entonces **no hay nada que ver en el emulador** — que es el objetivo de esta fase |

⛔ Y **la 1.9 queda FUERA** (retirar `mine_lane_split` y `vehicles_header`): revierte dos decisiones
que el user tomó en device, así que va en su propio A/B y no escondida dentro de esta tanda.

## Alcance — qué entra

| # | Tarea | Qué |
|---|---|---|
| 1.4 | Pestañas reponderadas | `1.25f / 1f / 1f` en vez de tres tercios |
| ~~1.8~~ | ~~Standout también en parkings~~ | ❌ **RETIRADA al leer el código** — ver abajo |
| 1.6 | Alternativa útil en el vacío de plazas | una, nombrada, y sólo si se puede ofrecer |
| 1.7 | El aviso de vigilancia sólo cuando avisa | fuera el chip permanente que además etiquetaba el tier |
| 2.1 | `ResolveNearbyNotablesUseCase` | uno por sujeto, orden fijo, umbral por sujeto |
| 1.3 | `PapOpportunityCard` | tarjeta con dos destinos |
| 1.1 | Gramática de dos líneas | coche + plaza + parking + gasolinera |
| 1.2 | El peek de B | coche en 2 líneas + aviso + «Aquí y ahora» |
| 1.5 | Checklist a fila de progreso + cabeceras de provincia | — |
| 2.3 | Lo recordado al arrancar | ⛔ en `Background` de la cola de Overpass |

## ❌ 1.8 retirada: el parking ya dice lo único comparable que sabe

Al abrir el código para implementarla, las tres formas posibles del chip se caen, cada una por su
motivo:

| Qué diría el chip | Por qué no |
|---|---|
| **«MÁS BARATO»** | **imposible**: `FeeInfo { PAID, FREE, UNKNOWN }` — no hay importe. El `fee` de OSM es sí/no, no un precio |
| **«MÁS CERCANO»** | **redundante**: `NearbyOrdering.publicParkings()` ordena **sólo por distancia**, así que el más cercano **es la primera fila**. El chip rotularía lo que la posición ya dice |
| **«GRATIS»** | **ya está**: `HomePublicParkingRow` pinta `feeLabel()` en su trailing — «Gratuito» / «De pago» — **en el mismo sitio donde iría el chip**. Y con su doctrina ya escrita: `UNKNOWN`, que es el **71 %** de los parkings, se pinta como **nada**, nunca como «gratis» |

> ⛔ **Y la decisión de diseño se tomó sobre un dato que yo inventé.** El prototipo mostraba
> «2,10 €/h» en la tarjeta de parking, un importe que **la app no tiene de ninguna fuente**. Eso es
> lo que hizo que «standout en parkings» pareciera razonable, y es exactamente lo que el proyecto
> prohíbe: un dato inventado presentado como resultado. Corregido en el prototipo en la misma tanda.

⚠️ Lo único que queda de la idea, y **no entra aquí porque es decisión de device**: la etiqueta
«Gratuito» se pinta hoy en `onSurfaceVariant`, neutra como «De pago». Entre parkings de pago, un
gratuito podría merecer énfasis. Es cambiar el tono de una etiqueta existente, y eso se mira en
mano, no se decide leyendo.

## ⚠️ 1.7 corregida: el chip que pedía el plan ya vivió ahí y se retiró

De las tres partes de la tarea, al abrir el código **dos se caen y la tercera cambia de sitio**:

| Lo que pedía el plan | Qué dice el código |
|---|---|
| chip **«Falta permiso»** | **ya existe, y mejor**: `HomeLocationPill` dice «Sin ubicación» flotando sobre el mapa, es pulsable, y **sobrevive a los modos de selección y de pin** — justo cuando un fix de ±80 m decide si el pin vale algo |
| el chip vive **en el trailing de la cabecera** | **ese badge VIVIÓ exactamente ahí y se retiró**, por la issue #27 del user: competía en ancho con el nombre del coche y la dirección, y la cabecera se oculta en selección/pin, así que el aviso desaparecía cuando más hacía falta. Volver a ponerlo es revertir una decisión medida [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001] [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001] |
| que el aviso **se vea en reposo** | **esto sí falta** — y sólo para la vigilancia **MUERTA**: hoy, enterarse de que la app dejó de vigilar tu coche exige abrir la hoja |

> ⛔ Y el chip tampoco cabía donde el plan lo ponía: un cuerpo en el peek de browse activa el
> `hasBody` de `PapSheet`, que añade 16 dp de aire y descoloca el divisor peek/nav que
> `BUG-PEEK-DIVIDER-ALIGN` dejó sentado. El peek no tiene hueco libre; tiene una altura derivada.

### ⛔⛔ Y al verificarlo con el user, la 1.7 nacía con un bug

Su pregunta era de comprobación —*«el badge que aparece abajo del sheet cuando faltan permisos o hay
vigilancia muerta sigue estando no?»*— y la fila estaba intacta. Lo que se destapó al ir a
comprobarla es que **se mostraba cuando no debía**: `ServicePresence` valía `Dead` por defecto, así
que Home acusaba al sistema de haber matado al vigilante en **cada arranque en frío** — y el
`warningOpenCue` de esta misma tarea **abría la hoja** encima.

Arreglado por su propio ticket, porque toca detección:
[`DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001`](det-a-silent-service-is-not-a-dead-one-001.md).
`ServicePresence` gana `Unknown`, y cada consumidor declara qué pregunta hace — Home calla sin
medir, el resumidor actúa igual que antes.

> ⚠️ **Y había un testigo defendiendo el bug**, falsificado y en verde:
> `should_open_when_home_arrives_with_the_watch_already_dead`. Falsificar prueba que el test MIRA;
> **no prueba que mire lo correcto.**

### Lo que SÍ se implementa

El remedio es **el mecanismo que la app ya tiene** para la ubicación rechazada —rebobinar la lista y
desplegar la hoja sobre la fila—, no una superficie nueva. La fila ya lleva causa, consecuencia y
remedio; un chip no cabe el remedio. [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]

⛔ **Y no se añade un segundo contador al lado del primero.** `locationWarningCue` pasa a ser
`warningOpenCue`, **uno**, con dos productores. Dos contadores haciendo «rebobina y despliega» son el
invariante duplicado, y el día que divergen el aviso abre la hoja dos veces o ninguna.

**⛔ Sólo la muerta, nunca la frágil** — la asimetría de siempre, aplicada a quién puede interrumpir:

| Badge | ¿Abre la hoja? | Por qué |
|---|---|---|
| `WATCH_INTERRUPTED` | **sí** | es un **suceso**: el sistema mató al vigilante y la promesa está rota *ahora* |
| `WATCHING_FRAGILE` | **no** | es una **condición de configuración** y la vigilancia **sigue viva**. Es permanente para quien no da la exención, así que abrir la hoja por ella sería abrirla en cada arranque: eso no es avisar, es insistir. Ya tiene su fila y su paso del checklist |

Y la transición se lee sobre el **badge derivado**, no sobre `ServicePresence.Dead`: ese `if` sería
una quinta regla capaz de discrepar de la fila que abre — abriría la hoja por un coche cubierto por
Bluetooth, al que la presencia del FGS no afecta.

## ⚠️ 1.1 acotada por medición: el defecto está en UNA superficie, no en siete

El plan la llamaba un **barrido de siete consumidores**. Al abrirlos uno a uno, seis ya cumplen la
regla —con contenido distinto en la línea 2, que es lo que la regla pide— y el defecto que el user
midió está en uno:

| Superficie | Línea 1 | Línea 2 | ¿Repite? |
|---|---|---|---|
| `FuelStationPeek` | nombre — **o la calle** si no hay nombre | la calle — **o la población** si la calle ya está arriba … **y la distancia en una fila aparte** | ❌ **sí** |
| `HomeFuelStationRows` | nombre | distancia · sitio, **deduplicado** | ✅ ya cumplía — en una copia **privada** de la regla |
| `HomeSpotRows` | el sitio (identidad de la fila) | fiabilidad · tiempo · en camino | ✅ |
| `HomeParkingRow` / `PublicParkingPeek` | nombre — o su **tipo** si no hay nombre | el tipo, deduplicado | ✅ |
| `ParkingPeek` (tu coche) | el sitio | — (no tiene subtítulo) | ✅ |
| `SpotPeek` | el sitio | la **antigüedad**, que no es un sitio | ✅ |

> ⛔ **Y eso no hace la tarea más pequeña, la hace de SISTEMA.** La regla existía —en privado,
> dentro de la única fila que la cumplía— y no existía en los tres peeks. Ahora vive en
> `subjectWhereLine()`, con sus testigos, y la fila de gasolinera la **consume** en vez de tener su
> copia. Un invariante en un sitio, sus consumidores barridos.

### Las dos cosas que cambian de verdad

1. **El ORDEN de la línea 2: el sitio primero, la distancia después.** La fila de gasolinera decía
   «a 420 m · Calle Mayor 12»; la gramática de B responde *dónde · a cuánto*, y leer la cifra antes
   de la cosa que mide la deja sin sujeto. ⚠️ **Esto invierte una fila ya publicada** — es de las
   que hay que ver en mano.
2. **La distancia deja de decirse dos veces en el peek de gasolinera.** Sube a la línea 2 y su fila
   se queda **sólo con el tiempo**, que es lo que la línea 2 no dice. Con eso **muere el caso
   especial de los 100 km** (`FAR_FOR_DRIVE_TIME_M`): existía para quedarse con la distancia sola
   cuando el tiempo era una cifra inventada, y ya no hay nada que rescatar.

⛔ `DistanceRow` **no se retira**: los peeks que no ponen la distancia arriba —tu coche, la plaza,
el parking— la siguen necesitando ahí. Dos superficies, dos filas; lo que no puede haber es la
misma cifra dos veces en la misma.

## ⚠️ 2.3 corregida: el carrusel no arrancaba vacío, arrancaba TARDE

La corrección que escribí al abrir el ticket decía que sin la 2.3 *«el carrusel arranca sólo con la
tarjeta de plazas, y entonces no hay nada que ver en el emulador»*. **No es cierto**:
`warmUpHiddenLanes()` ya existía — [HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001] — y rellena los
dos carriles ocultos al entrar.

Lo que faltaba no era el **contenido**, era **cuándo**. Y el porqué está escrito en el propio
calentamiento: es **secuencial por obligación**, porque la cola de Overpass es de **una** pregunta a
la vez (el servidor concede 2 por IP y una IP es una operadora entera). Así que el segundo
complemento llega entre 3 y 16 s después de abrir la app — y hasta 40 s en el peor caso medido,
contando la espera de cuota y la caminata entre mirrors. Durante todo ese rato «Aquí y ahora» sólo
podía enseñar plazas, que vienen de Firestore y no pasan por ahí.

**El primer vistazo no puede ser lo último que se rellena.** Por eso el recuerdo se pinta *delante*
del calentamiento, en el mismo sitio, y no en un camino de arranque nuevo.

### Lo que hizo falta construir

El lado de los parkings ya tenía `remembered()`; **el de gasolineras no existía**. Se añade con la
MISMA forma, que es lo que lo hace baratito y lo que impide que las dos mitades deriven:

| Capa | Qué recuerda |
|---|---|
| `FuelStationsSource` | **null por defecto** — una fuente que sólo sabe preguntar no recuerda nada, así que las 5 fuentes nacionales y la de OSM no tocan una línea |
| `CachingFuelStationsSource` | lo de memoria si vive, y si no **lo que recuerde el disco** — ⛔ no se salta al delegado: al arrancar la memoria está SIEMPRE vacía, que es justo el momento para el que esto existe |
| `DiskBackedFuelStationsSource` | lo del disco, con `reportedAt` a **null**: ese sello es de una respuesta que no ha ocurrido |
| `FuelStationsRegistry` | el de la fuente a la que le TOCA (por `claims`), y ⛔ **sin marcar `didFallBack`** — esa marca dice que el feed nacional falló *al preguntar*, y aquí no se ha preguntado nada |
| `GetNearbyFuelStationsUseCase.remembered()` | la misma zona, el mismo filtrado y las mismas distancias que `invoke()`, compartiendo `queryFor()` y `answeredFor()` — extraídos de dentro de `invoke` para eso |

⛔ **Y no toca `isLoading`, ni el ancla, ni la selección.** Dejar el ancla puesta haría creer a
`refreshNearby` que este suelo ya se preguntó, y el recuerdo se quedaría en pantalla para siempre sin
que nadie lo refrescara.

⚠️ **Lo que queda fuera, y por una dependencia real**: el plan pedía que el refresco fuera en
`Background` de la cola de Overpass. **Esa prioridad no existe** — `OverpassQuota` es un
`Semaphore(1)` sin prioridades, y crearla es justo el ticket
`overpass-a-queue-of-one-must-let-the-user-go-first-001`, que está abierto. Pintar lo recordado **no
pasa por Overpass** (lee disco), así que esta mitad no depende de aquello; el orden del refresco sí.

## Señales / datos disponibles

Todo lo que hace falta ya está en el estado: `DetectionStory`, `ParkedWatchBadge`, `VehicleCard`,
`HomeFuelSlice`, `publicParkingsShown`, `FuelStandouts`, `GetNearbyPublicParkingsUseCase.remembered()`.

## Diseño

El invariante: **la cabecera describe lo que se está leyendo, y nunca habla de algo que no esté
debajo.** Tres modos (`mine` / `lane` / `detail`), y la gramática de dos líneas es **una sola** para
todo sujeto — por eso 1.1 es un barrido y no siete parches.

## Criterio de éxito

- Con la app recién abierta y sin tocar nada: se lee **de qué coche** es esta pantalla, **en qué
  estado** está, **dónde** y **a cuánto**, y hay una acción que tiene sentido.
- Llegar a gasolineras baja de **4 toques a 2**.
- Con 0 plazas cerca, la pantalla **no se lee como «la app está vacía»**.
- `./gradlew testAll` verde, con test propio para el caso de uso nuevo y su falsificación.
- Visto en **device**, no sólo en galería: el peek a `fontScale` 1,3 y el tema claro.

## Progreso

| # | Tarea | Estado |
|---|---|---|
| 1.4 | Pestañas reponderadas | ✅ `PRODUCT_TAB_WEIGHT = 1.25f`, con el porqué del número medido sobre el ancho real (a 1,5 «Gasolineras» empieza a elidir) |
| 2.1 | Qué entra en el primer vistazo | ✅ `domain/nearby/NearbyNotables.kt` — **función pura, no caso de uso** (ver abajo), + 9 tests, **4 falsificados** |
| 1.6 | Alternativa útil en el vacío de plazas | ✅ `HomeTryCarParksCard` + strings en los 9 locales |
| 1.7 | El aviso de vigilancia sólo cuando avisa | ✅ **corregida al abrir el código** (ver arriba): `watchInterruptionEarnsAnOpen` + un solo `warningOpenCue` con dos productores, + 9 tests, **4 falsificados** |
| 1.3 | `PapOpportunityCard` | ✅ dos destinos, pie con plural **por sujeto** en los 9 locales, + 4 variantes de galería |
| ~~1.8~~ | ~~Standout en parkings~~ | ❌ retirada — ver arriba |
| 1.1 | Gramática de dos líneas | ✅ **acotada por medición** (ver arriba): `subjectWhereLine()` + 7 tests, el peek de gasolinera arreglado, `FAR_FOR_DRIVE_TIME_M` retirada |
| 1.2 | El peek de B | ✅ **completa**: «AQUÍ Y AHORA» (`HomeNowSection`) · el **aviso** lo resolvió la 1.7 · el **coche en 2 líneas**, medido en emulador — ver abajo |
| 2.3 | Lo recordado al arrancar | ✅ **corregida** (ver arriba): `remembered()` en las 4 capas de gasolineras + pintado delante del calentamiento, + 5 tests, **4 falsificados** |
| 1.5 | Checklist a fila de progreso | 🔵 pendiente |

### 1.2b · El peek de dos líneas, y lo que el emulador cambió

La forma es la que decidimos: **dos líneas, dos preguntas.**

| | Qué responde | Ejemplo |
|---|---|---|
| Línea 1 | quién · qué le pasa · **cuándo** | «SEAT LEÓN · APARCADO HACE 31M» |
| Línea 2 | **dónde** | «Repsol Av. Castellana» |
| Borde derecho | **a cuánto** | «180 m» |

El «hace» sube a la línea 1 porque responde CUÁNDO, la misma pregunta que el estado. La tercera
línea que los llevaba juntos —«Aparcado hace 2 h · a 180 m de ti»— desaparece, y con ella sus dos
strings en los 9 locales.

⛔ **La ALTURA no se mueve**, y por eso esto se pudo hacer sin tocar la geometría: la reserva de la
cabecera es un `defaultMinSize` de tres líneas (14 + 24 + 16 sp), así que con dos el bloque respira
dentro de la misma caja y los cuatro detents y el divisor peek/nav siguen sentados. [BUG-PEEK-DIVIDER-ALIGN]

#### ⚠️ Dos correcciones que sólo salió viendo, y la segunda la cantó el user

**1 · La distancia no cabe al final de la línea.** Primera versión: línea 2 = «dónde · a cuánto» en
un solo `Text`. En pantalla:

> `Repsol Av. Castellana · 1450.6 …`

El texto elide **por el final**, así que la cifra era siempre lo primero en caerse — al revés de lo
que hace falta: un nombre se abrevia sin perder la respuesta, una distancia no.

**2 · Y la jerarquía estaba invertida respecto al prototipo** (user, viéndolo en el emulador):
*«sigo viendo un tamaño de texto muy grande para la dirección y los km están puestos a parte»*. En
producción la dirección se llevaba `cardTitle` —la voz grande— y el coche quedaba en un eyebrow en
caps. En el prototipo que aprobó es al revés. Mi primer arreglo del punto 1 —la cifra a un trailing
propio— además la sacaba de la línea, que tampoco es el prototipo.

#### La forma final, que es la del HTML con nuestros roles

| Pieza | Rol | Color |
|---|---|---|
| nombre del coche | `rowName` (MARCA) | identidad de vigilancia |
| calificador — «aparcado hace 31m», «en ruta» | `rowTitle` (LECTURA) | `onSurfaceVariant`, y **late** si el estado está en movimiento |
| dónde | `body` | `onSurfaceVariant` |
| a cuánto | `caption` | `onSurfaceVariant` |

Los cuatro roles **ya existían**: no hay `fontSize` inline ni rol nuevo. Y la cifra vuelve **dentro
de la línea**, tras el `·`, pero en su **propia columna**: el sitio lleva el `weight` y el número no,
así que elide el sitio y el número sobrevive — que es justo lo que hace el CSS del prototipo.

Lo soporta reenviar `titleSlot`/`subtitleSlot` de `PapListItem` a través de `PapSheet`: el contrato
ya existía —es el mismo con el que la fila de plaza mete su glifo POI— y sólo faltaba el paso.
`titleTrailing`, que fue mi primer intento, **murió con él**: una API que nació y se retiró en la
misma tanda no deja deuda.

#### ⛔ A qué estados se aplica (decisión del user, 30-10: **a todos**)

> *«el peek tendrá siempre la misma altura y estructura para todos sus estados»*. Planteé que plaza,
> parking y gasolinera no tenían el problema que esta estructura resuelve; el user lo repitió, y eso
> cierra la discusión. Aplicada a los seis.

| Estado | Línea 1 | Línea 2 |
|---|---|---|
| coche aparcado (colapsado) | **nombre** · aparcado hace 31m | calle · a 180 m |
| coche en ruta | **nombre** · en ruta *(late)* | calle · ciudad |
| tu coche (abierto) | **nombre** · aparcado | calle · a 180 m |
| plaza | **Plaza** *(color de frescura)* · hace 3 min | sitio · a 120 m |
| parking público | **nombre** *(azul)* · subterráneo | a 260 m |
| gasolinera | **nombre** | calle · a 450 m |

⛔ **Dos ausencias que NO son huecos olvidados.** La gasolinera no lleva calificador porque la hoja
itemiza TODOS sus carburantes: no hay uno que la cualifique, y elegirlo sería hacerlo por ella. El
parking público no lleva sitio porque `PublicParking` **no trae dirección en ninguna fuente**, así
que su línea 2 es sólo la cifra. El hueco es el mismo; el dato que cabe, no.

⛔ **El coche sin marcar y las cabeceras de carril/zona quedan fuera**, y no por pereza: el sin
marcar **no tiene un dónde** —es su asunto— y darle una línea 2 de sitio reintroduce el bug que
[HOME-AN-UNMARKED-CAR-HAS-NO-ADDRESS-001] cerró, que le ponía la calle de la CÁMARA y lo «movía» al
panear. Las cabeceras de carril son un recuento y su sujeto, no un nombre y su sitio.

⛔ Con el barrido, `DistanceRow` se quedó **sin un solo consumidor** y se retiró: los seis ponen la
distancia en su línea 2. Lo que añadía de verdad —el TIEMPO— vive en `TravelTimeRow`.

#### ⚠️ Y un tercer defecto, que sólo salió en el emulador

La línea 1 eran **dos `Text` en un `Row`**: el calificador no llevaba peso, así que se medía primero
y se quedaba el ancho entero. El **NOMBRE colapsaba a cero** y el peek del parking salió
literalmente así:

> `· Underground car …`

Empezando por el separador. En el coche no se veía porque «parked 30m ago» es corto; con un
calificador largo, el sujeto desaparece. Arreglado con **un solo `Text`** y una `AnnotatedString`:
la elipsis cae al final por construcción, así que el nombre sobrevive siempre y lo que se recorta es
el calificador. Es la misma técnica con la que `PapListItem` tiñe el nombre dentro de su eyebrow.

### 🔵 Lo que queda, y por qué no lo he hecho a ciegas

| # | Qué | Por qué espera |
|---|---|---|
| 1.5 | checklist → fila de progreso + cabeceras de provincia | es puro resultado visual sobre una superficie que el user ya tiene decidida; entra en la misma sesión de device |
| — | **abrir la modal al arrancar** si hay algo en «Aquí y ahora» (decisión tuya) | ya hay un mecanismo (`warningOpenCue`) y **tres** aperturas automáticas más compitiendo (checklist, pregunta abierta, aviso). La precedencia entre cuatro no se decide sin verla |

### ⚠️ Segunda corrección del plan: función pura, no caso de uso

El plan la llamaba `ResolveNearbyNotablesUseCase`. Al escribirla, la regla del proyecto dice lo
contrario: un caso de uso es para un **veredicto citable en un diagnóstico**, y crear uno se
justifica *«cuando hay lógica inalcanzable para los tests»*. Esto es aritmética sobre tres listas que
ya existen. El precedente más cercano —`FuelStandouts`, que también decide qué dice una fila del
carril— es función pura **y lo documenta**. [DET-VERDICT-NOT-PREDICATE-001]

### Verificado hasta aquí
- `./gradlew testAll` → **verde** (3.932 tests).
- `NearbyNotablesTest`: 9 tests, y **4 falsificados uno a uno** (orden fijo · gasolinera sin precio ·
  asumir que la primera es la más barata · inventar 0 como distancia sin fix) → 1 test rojo cada uno,
  restaurado 0.
- ⚠️ **Y una falsificación que no valía**: el primer script lanzaba gradle por `subprocess` con
  `shell=True` y **fallaba la invocación**, así que los cuatro «rojos» eran del arranque, no de los
  tests. Lo delató que «restaurado» también saliera rojo. Repetida con bash.
- `SubjectLinesTest`: 7 tests, y **5 falsificados** (repetir la línea 1 · no bajar de nivel · una
  cadena en blanco cuenta como sitio · la distancia delante · cadena vacía en vez de null) → 3, 2,
  1, 3 y 1 rojos, restaurado **0**.
- `GetNearbyFuelStationsUseCaseTest`: 5 tests nuevos, y **4 falsificados** (recordar pregunta por la
  red · el recuerdo pregunta por otra zona · el recuerdo no se cotiza · se cotiza por la mecánica y
  no por el surtidor) → 5, 1, 1 y 1 rojos, restaurado **0**.
- ⚠️ **Y una segunda falsificación inválida, por otra razón**: el «restaurado» de `SubjectLines`
  salió ROTO porque mientras el script corría yo estaba escribiendo otro fichero que aún no
  compilaba. El veredicto de un script que restaura UN fichero no vale si el árbol se mueve a la
  vez; re-verificado después con la suite entera en verde.
- `WatchInterruptionTest`: 9 tests, y **4 falsificados** (la frágil también abre · se olvida de la
  transición · llegar ya muerta no cuenta · cualquier cambio abre) → 2, 1, 1 y 5 rojos
  respectivamente, restaurado **0**.
- **Galería al día en la misma tarea**, que es la regla del proyecto:
  - *Sheet · vacío CON parkings cerca* — la variante que ya existía **no** enseña la tarjeta de 1.6,
    porque sin parkings no hay nada que ofrecer; las dos juntas son los dos lados de la regla.
  - *Home · oportunidad (dos destinos)* — cuatro variantes: con cifra y chip, sin cifra, pie en
    **singular**, y nombre largo a dos líneas.

## 📌 Visto en emulador (30-10) — y lo que queda apuntado

Instalado con hash verificado **en device** en los dos builds (`c79a10b6…`). Comprobado:

| Qué | Resultado |
|---|---|
| «Aquí y ahora» con los 3 sujetos | ✅ orden fijo plaza → parking → gasolinera, el mismo que las pestañas |
| Dos destinos por tarjeta | ✅ cuerpo + pie, con su cifra y su chevron |
| La más barata se **busca**, no se asume | ✅ eligió Petroprix a 1,759 y no Repsol a 420 m, que cuesta 1,879 |
| Pie en **singular** | ✅ «See the only car park» — el plural por sujeto resuelve |
| Arranque en frío ⇒ **sin fila de vigilancia** | ✅ sólo la card del coche |
| `Dead` ⇒ fila roja + «Reactivar» | ✅ mismo estado salvo una palabra |

⚠️ **Y el emulador destapó un fallo de la propia galería**: la variante se llamaba «los 3 sujetos» y
enseñaba **dos**. El coche del fixture (Seat León) gasta **gasolina** y las gasolineras sólo cotizan
**diésel**, así que no había precio para su producto y `nearbyNotables` no emitía tarjeta — *sin
precio no hay noticia*, funcionando como está escrito. **La regla estaba bien; mentía el nombre de
la variante.** Corregida a la furgoneta (diésel) + añadida su pareja, que enseña el umbral en vez de
dejarlo pareciendo un bug.

### 📝 Apuntado — es de MOCK, no toca producción

Decisión del user (30-10): si sólo afecta al flavor mock, se anota y no entra aquí.

| Qué | Dónde | Por qué molesta |
|---|---|---|
| Las plazas del fixture siguen en **Dublín** y `sampleGps` es Madrid ⇒ «**1450,4 km**» | `FakeData.nearbySpots` | hace que «plaza libre más cerca» se lea como un disparate. Misma raíz que las anclas de capturas que murieron en la mudanza (`PLAY-SHOTS-THE-CAPTURE-ANCHORS-DIED-IN-THE-MOVE-001`) y que las 72 capturas de tienda aún por rehacer |
| Las 4 variantes sueltas de `PapOpportunityCard` mezclan eyebrows en español escritos a mano con el pie en inglés del recurso | `StateGalleryScreen` | se lee raro; son demo de maquetación, pero podrían leer los recursos reales |

⛔ **Y lo que NO es un defecto**, comprobado antes de tocarlo: `1.759 EUR/l` con la moneda en código
ISO es **deliberado** y está en el KDoc de `pumpPriceLabel` — *«moneda tal y como la da el feed»*—, y
la unidad en voz alta existe porque un precio de combustible sin unidad es ambiguo por un factor de
3,785: cuando la cifra se desvió a «7.552 EUR» fue justo lo que impidió verlo.
[FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001]

## 1.2c · La gramática del MODAL — cinco bandas (30-10)

Sale de cuatro mensajes seguidos del user sobre el modal, mirándolo en el Oppo. Los pongo en orden
porque cada uno destapó el siguiente:

1. *«tenemos más grandes el contenido del body que el del head del título»*
2. *«el nombre… se puede poner en negrita; en la modal de coche pone apar… y es redundante»*
3. *«en cada modal de los otros estados aplicaremos lo mismo… será en color normal, no le metas
   color… si queda muy largo el título que pueda ser 2 líneas»*
4. *«el resto de info que mostrábamos en peek pasará al body de la modal»*

### La causa, que era mía

La jerarquía invertida **no era del cuerpo: era del título, que yo había bajado.** `PapSheet`
declara su título en `cardTitle` (18sp **Bold**, y su KDoc nombra este caso con todas las letras:
*«vehicle name, **peek title**, dialog title»*). Al meter el `titleSlot` del rediseño lo sustituí
por `rowName` (14sp SemiBold) — **degradé el título de una hoja a rol de nombre-de-fila**, en los
seis peeks a la vez. Y `rowTitle`, el rol de las filas del cuerpo, es **el mismo 14sp SemiBold**:
tres filas con su glifo en color le ganaban al título por acumulación.

Medido: `papSheetHeaderReservedHeight()` reserva su pila con `lineHeight` **24sp**, que es justo la
de `cardTitle` — así que lo que no encajaba era el 20 de `rowName`, y el alto de la cabecera **no
se mueve** al arreglarlo.

### Lo que se ha declarado

La gramática vive en el KDoc de `PapSheet`, que es donde la leerá quien añada la próxima modal:

| Banda | Pregunta | Slot |
|---|---|---|
| 1 · Identidad | ¿qué es esto? | header: tile + NOMBRE, y nada más |
| 2 · Aviso | ¿algo que saber antes de leer? | `banner` |
| 3 · Hechos | ¿dónde está y cómo es? | `meta` |
| 4 · Desglose | la prueba, el detalle | `chips` / `content` |
| 5 · Acción | ¿qué hago? | `actions` |

Tres reglas, cada una con su medición:

1. **La cabecera NOMBRA; el cuerpo DESARROLLA.** El sitio y la distancia salen del `subtitleSlot` y
   abren la banda 3 (`PeekPlaceRow` + `DistanceAndTimeRow`). En la cabecera compartían ancho con el
   racimo «‹ › ×»: **432 px de 1080** medidos en el Oppo —los tres botones de 48 dp, el ‹
   deshabilitado incluido, que **no sale en el volcado de uiautomator pero sí ocupa**— así que al
   título le quedan 345 px, unos 12 caracteres a 18sp Bold. De ahí «Parking de …» y «apar…».
   ⛔ El peek **colapsado** sí conserva su línea 2: no tiene cuerpo donde desarrollar nada.
2. **El título gana por ROL, no por color.** `cardTitle` arriba, `body` (14sp Normal) en las filas;
   nombre en `onSurface`, identidad en el TILE — que es literalmente lo que manda
   `UI-COLOR-DOCTRINE-001`. Los CARRILES («Plazas», «Parkings», «Gasolineras») sí siguen en su
   color: ahí el nombre es la categoría, no un sujeto con tile propio.
3. **Una sola columna de hechos.** `PeekMetaRow` acepta `icon = null` y deja el canal **vacío, no
   colapsado**. Medido en el parking público: «1 min en coche» en x=126 y justo debajo
   «Altura máx. 1,9 m» en **x=48** — 28 dp de desalineo entre dos hechos consecutivos, porque el
   segundo venía de `ParkingFactRow`, un componente propio que no conocía a sus vecinos.
   ⛔ No se inventa un glifo para rellenar: el set extendido de Material **no está en el classpath**
   (el catálogo lo declara, ningún módulo depende de él).

### El hallazgo estructural

`content` estaba documentado como *«escape hatch (forms — AddingZone)»* y **cuatro modales lo
usaban de cuerpo**. La prueba de que era un hueco mal declarado: los cuatro terminaban con el
**mismo** `Spacer(Modifier.height(PapGap.block))` escrito a mano. Ahora la banda existe, el aire lo
pone la hoja una vez, y los cuatro `Spacer` están borrados. Un formulario sigue siendo legítimo ahí
— es el cuerpo de su hoja, no el desglose de unos hechos.

### Barrido

| Superficie | Qué se hizo |
|---|---|
| `ParkingPeek` (coche) | ya era la referencia; pierde el calificador «aparcado» (redundante con `ParkingDurationRow`) y pasa a `PeekPlaceRow` |
| `SpotPeek` | nombre a `onSurface`, sitio y distancia a la banda 3, `Spacer` fuera |
| `PublicParkingPeek` | ídem; sus tres hechos (tarifa, capacidad, gálibo) suben de `content` a `meta` |
| `FuelStationPeek` | ídem; el desglose de precios se queda en la banda 4, que es lo que es |
| `BrowsePeek` ×3 | **sin tocar la línea 2**, a propósito: es el peek colapsado |
| `ReportPeek`, `AddingParkingPeek`, `AddingZonePeek` | ya usaban `title = title` ⇒ ya estaban en `cardTitle`. Sólo pierden el `Spacer` a mano |
| `TravelTimeRow` | **borrada**: con la distancia en la banda 3 se quedó sin consumidores |
| `ParkingFactRow` | reescrita sobre `PeekMetaRow(icon = null)` |

⚠️ **Fuera del barrido, y dicho a propósito**: `ParkingHistoryDetailScreen` compone su banda 1 con
eyebrow + título + subtítulo (la ciudad). No es Home, su cabecera no pelea con un `×` y su eyebrow
tiene una composición propia, así que no se ha tocado. Queda escrito en el KDoc de `PapSheet` como
la excepción conocida, no como un olvido.

### Estado

`testAll --rerun-tasks` → **3984 tests, 0 fallos** (tras rebasar sobre `d6e8a740f`).

✅ **Verificado en pantalla** en el Pixel 8, en los dos temas: las cuatro modales, los tres
carriles, el peek colapsado y la tira de «Aquí y ahora». Las capturas viven en el artifact de la
revisión de diseño.

⏳ **Sin ver en los móviles de campo.** El Oppo lleva un `prodRelease` del commit anterior
(`dd841c9da`, sha256 `7d184832ee26…`, firmado con la upload key vigente), así que **no lleva nada
de esta tanda**: para verlo ahí hay que compilar otro release encima.

⚠️ Dos cosas vistas de paso y NO arregladas, porque son anteriores a esta rama: en la expansión
completa la cabecera se mete bajo la barra de estado (le falta el inset), y el nombre del segundo
coche elide en su tarjeta («Segundo coc…»).

## 1.2d · Las correcciones del user sobre la gramática (02/03-11)

Nueve peticiones seguidas viéndolo en el Pixel 8. Cada una con lo que la cerró:

| Lo que pidió | Qué se hizo | Nota |
|---|---|---|
| «‹ › × una línea arriba del título» | Salen del `trailing` y van a **fila propia**, repartidos a los extremos | El título pasa de **345 px a 777 px** |
| «¿y un back button a la izquierda?» | **No.** Tres razones en el KDoc de `papSheetHeaderChrome` | Dos glifos a la izquierda con sentidos distintos; «atrás» promete una pila que no existe; el atrás del sistema ya cierra |
| Renombrar «Plaza» | **«Plaza comunitaria»** en los 9 locales | ⛔ Sin forma corta: el único call site cabe entero, y **un string muerto rompe la suite** |
| «‹en camino› seguido del resto» | `SpotFitRow` sube a la banda **`banner`** | Es un pill con fondo propio: partía en dos la columna de hechos |
| «¿a pie o en coche?» | Plaza → **siempre DRIVING**; murió el umbral de 400 m | A una plaza se va con el coche; a pie se va a TU coche |
| «al parking le falta un icono» | 💳 `De pago` · 🅿 `Capacidad` · ↕ `Altura` | ⚠️ **Mi excusa era falsa**: dije que el set extendido no estaba en el classpath y sí está |
| «carriles sin color» | `onSurface`; revierte su propia petición del 30-10 | El verde de «Plazas» competía con el verde de ACCIÓN |
| «el título seguido de · → abajo, sin ·» | El calificador pasa al **`subtitle`** de la hoja | El slot ya existía; sobraba el separador |
| «el nombre del coche en onSurface» | Nombre neutro + `VehicleWatchLeadingIcon` **delante** | ⚠️ Lo puse detrás; el componente se llama *leading* |
| «quita LIBRES» | Sólo la cifra | Con él murieron su `Column` y `figureOpticalLiftSp` |
| «icono de plaza cuadrado» | Cuadrado en lista y hoja, **redondo en el mapa** | Matiza `HOME-PUCK-001`: se comparte el color/glifo/anillo, no el soporte |
| «reducir un pelin la altura» | Padding superior de la cabecera **12 → 2 dp** | ⛔ `HEADER_TEXT_STACK_SP` **no** se puede bajar: `UnmarkedCarHeader` sí pinta 3 líneas |
| «Aquí y ahora: plaza, gasolinera, parking» | Reordenado en `nearbyNotables` | El testigo del orden **saltó**, que es lo que debía hacer |

### Lo que se llevó por delante

Código muerto borrado, no deprecado: `TravelTimeRow` (la distancia subió a la banda 3),
`STEPPER_TO_DISMISS_GAP_DP` (el reparto a los extremos ES el separador), `WALK_DISTANCE_THRESHOLD_M`,
`figureOpticalLiftSp` y la clave `home_counter_unit_free` en los 9 locales.

### Dos tickets que salieron de aquí y NO entran aquí

- [`det-a-stop-at-a-pump-is-not-a-parking-001`](det-a-stop-at-a-pump-is-not-a-parking-001.md) · issue **#142**
- [`home-one-tab-answers-one-intention-001`](home-one-tab-answers-one-intention-001.md) · issue **#143**, con preview HTML

## Consumidores auditados

Se completa durante el trabajo. El barrido de 1.1 (la gramática) es el que más consumidores tiene:
`BrowsePeek`, `FuelStationPeek`, `PublicParkingPeek`, `ParkingPeek`, `HomeSpotRows`,
`HomeFuelStationRows`, `HomeParkingRow`.
