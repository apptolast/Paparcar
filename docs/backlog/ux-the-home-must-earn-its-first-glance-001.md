# UX-THE-HOME-MUST-EARN-ITS-FIRST-GLANCE-001 · La home se entiende antes de tocarla, y sirve sin comunidad

**Estado:** ✅ Done (diseño y prototipos completados) · rama `experiment/UX-THE-HOME-MUST-EARN-ITS-FIRST-GLANCE-001-home-redesign` · mergeado a master

## Problema

La home está muy iterada —cada decisión tiene su ticket y su razón medida— pero se diseñó
suponiendo una comunidad que todavía no existe. Con **<20 usuarios**, el carril por defecto
(`HomeNearbyLens.SPOTS`) es el que más veces contesta vacío, y el bloque fijo que lo precede puede
apilar hasta 9 items antes de él. El primer vistazo de la app es un mapa + **una** fila de peek
(88 dp): el estado del coche y la acción principal no se ven sin arrastrar.

Datos leídos del código, no supuestos:
- `SheetPeekHeightInitial = 88.dp` y `peekOffsetPx = containerHeight − peekHeight`: en reposo se ve
  el mapa y una sola fila.
- `homeSheetItems()` emite, en orden: `first_steps` (hasta 6 filas) · `detection_surface` ·
  `vehicles_header` · tira de coches · `mine_lane_split` · sticky (tabs + filtros) · carril ·
  `report_spot_cta`.
- `HomeNearbyLensRow` reparte los tres sujetos a `weight(1f)` cada uno, mientras su propio KDoc
  declara que **no son pares** («free spots are the product; car parks and fuel are complements»).
- `HomeFuelSlice` / `HomeParkingsSlice`: nada se pide hasta entrar en su carril. Lo más rico en
  datos y de valor recurrente cuesta un toque + una espera; lo más probable que esté vacío es lo
  que se abre por defecto.

## Doctrina violada

Ninguna regla de CLAUDE.md se rompe en el código actual — al contrario, el estado vacío ya respeta
`FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001` (unasked ≠ vacío ≠ fallo). Lo que falla es una
tensión **declarada y no aplicada**: `HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001` dice que
la jerarquía vive «en el DEFAULT y en el orden, nunca en la distancia vertical», y la fila la
renderiza como tres tercios idénticos.

## Señales / datos disponibles

Todo lo que las tres propuestas necesitan ya está en el estado: `DetectionStory`, `ParkedWatchBadge`,
`VehicleCard`, `HomeFuelSlice.hasPrices/reportedAt/didFallBack`, `publicParkingsShown`,
`PeriodStats` (coste, litros, `searchTime`, `spotsReleased`, `previous`), `StatsInsight`,
`FREE_HISTORY_WINDOW_DAYS = 7`.

## Diseño

Fase 1 = **decisión de diseño documentada y prototipada**, no implementación:
`docs/design/home-ux-audit.md` + 3 prototipos HTML comparables en `design-prototypes/paparcar-home/`
+ matriz de evaluación + sistema de diseño + spec de interacción + plan por fases.

## Criterio de éxito

Una dirección ganadora defendible, con su coste de implementación separado en «composición de datos
que ya existen» vs «lógica nueva», y sin una sola línea de producción tocada en esta fase.

## Entregado (27-10) — fase de diseño completa, producción intacta

| Fichero | Qué es |
|---|---|
| `docs/design/home-ux-audit.md` | auditoría sobre el código real: anatomía, alturas medidas, las 8 preguntas, los 6 escenarios vacíos, §9 descartados, §10 hipótesis + plan de validación, §11 limitaciones |
| `design-prototypes/paparcar-home/index.html` | índice + matriz de evaluación ponderada (A 4,01 · B 3,41 · C **4,51** · hoy 2,90) |
| `…/vehicle-first.html` · `…/smart-mobility.html` · `…/adaptive-home.html` | los tres prototipos, armazón y tokens compartidos, panel de 10 ejes de estado |
| `…/assets/{base.css,proto.js,shell.js}` | el lenguaje visual y el motor comunes |
| `…/comparison.md` | las tres en detalle, ponderación justificada, el cálculo y los puntos donde C **no** gana |
| `…/design-system.md` | tokens medidos, evaluación de la paleta de referencia, componentes nuevos, accesibilidad |
| `…/interaction-spec.md` | la spec de la dirección elegida + el plan en 4 fases. ⚠️ Nació como spec de **C** y se **reescribió para B el 29-10** (ver 6.ª vuelta) |

**Recomendación: C · Adaptive home**, con la estructura de A como base.

### Verificado
- `./gradlew testAll --rerun-tasks` → **3.921 tests, 0 fallos** (el recuento de
  `docs/backlog/README.md` se actualizó a 811/724/78/9, que es lo que pedía
  `BacklogStatusGuardrailTest`).
- Los tres prototipos: `node --check` + barrido de **2.268 comprobaciones** de render sobre los 10
  ejes de estado × 3 carriles, sin excepciones y sin `undefined`/`NaN` en la salida.
- Ningún `data-act` emitido queda sin manejador: **cero botones decorativos**.
- 35 declaraciones `font: … inherit` del CSS corregidas a `var(--font)`: el atajo `font` exige una
  familia real y habría descartado la tipografía entera.

### Hallazgos que valen aunque se descarte el rediseño
1. **La fila de sujetos se contradice con su propio KDoc**: declara que plazas, parkings y
   gasolineras «no son pares» y los pinta a `weight(1f)` cada uno.
2. **La home es más densa para el usuario NUEVO** (~700–800 dp antes del carril) que para el
   establecido (~340–390 dp) — y el nuevo es el único que Paparcar tiene hoy.
3. **Contraste medido en tema claro**: la etiqueta blanca sobre el relleno verde da **3,43:1**,
   por debajo del 4,5:1 que la propia doctrina del proyecto exige a un relleno frente a su etiqueta.
   `PapOnGreenContainerLight` daría 4,65:1 **sin tocar el verde**. Detalle en `design-system.md` §3.4.
4. **La paleta de referencia del encargo es verde-neutra; la viva es azul-navy** (ΔE00 de 4,9 a 15,1,
   con 110° de diferencia de tono). No son dos versiones del mismo gris. Se recomienda mantener la
   viva, con razón medida.
5. **Deriva documental**: CLAUDE.md dice `statNumber(25sp)`; el código tiene 20 sp.

## 2.ª vuelta (27-10) — revisión del user: **se inclina por B**

El user revisó los tres prototipos, eligió **B · Smart mobility** «por ahora» y planteó siete
puntos. Todos resueltos y documentados en
`design-prototypes/paparcar-home/b-decisions.md`; **cinco de los siete no eran de B sino del
producto, así que están aplicados a las tres alternativas.**

| # | Pregunta del user | Decisión |
|---|---|---|
| P1 | la tarjeta lleva a la modal como una fila; ¿debería llevar también a la lista? | **dos destinos, dos controles**: el cuerpo abre ese item, el pie abre su lista (con su cuenta: «Ver las 3») |
| P2 | el sheet habla del carril sin que se vea el carril; ¿y al subir?; ¿y al plegar? | la cabecera describe **lo que se lee**, es reversible, y lleva **las pestañas**. Al plegar, la hoja **vuelve arriba** (`reset`) — las 3 políticas comparables en el panel |
| P3 | «primeros pasos» debería llevar título | sí, con la regla de **cuándo una cabecera se la gana**: nombrar una provincia, no repetir el bloque. Y por eso «En esta zona» sale |
| P4 | ¿qué entra en «Aquí y ahora»? ¿y si no hay nada? | **uno por sujeto**, **orden fijo**, **umbral por sujeto**. Sin notables la sección **no existe** (ni título ni hueco) |
| P5 | ¿el HTML es la pantalla real? | **no**, y había una divergencia que maquillaba el diseño → corregida (ver abajo) |
| P6 | ¿qué hacemos con «hace 2 h · a 180 m»? | se reparten **por la pregunta que contestan**: el cuándo a la línea 1, el a-cuánto a la línea 2. Siguen siendo dos líneas |
| P7 | ¿el peek de plaza/parking/gasolinera sigue el mismo diseño? La 3.ª línea repetía dirección | **gramática única de dos líneas** + cifra al trailing. La causa de la repetición era un sujeto **sin nombre propio** (la plaza): ⛔ la dirección aparece en UNA línea, nunca en las dos |

### ⚠️ Fidelidad: una divergencia que maquillaba el diseño

El CSS del prototipo aplicaba **`tabular-nums`** y CMP 1.12 **no aplica `fontFeatureSettings`**
aunque Plus Jakarta Sans declare la feature: el prototipo alineaba columnas de cifras que en la app
bailan. **Quitado.** En el otro sentido, el **levante óptico** de las CIFRAS —que la app calcula con
las métricas reales de la fuente (cap 0,745 · asc 1,038 · desc 0,222 em, `figureOpticalLiftSp()`)—
ahora **sí** está replicado: a 21/9 px la fórmula da 2,08 px.

⚠️ Lo que sigue sin modelarse es **`fontScale`**, y es el caso a ver en device antes de implementar
un peek más alto. Tabla completa de divergencias en `b-decisions.md` §0.

### Nuevo entregable

`design-prototypes/paparcar-home/peek-cases.html` — **hoja de contactos de 28 casos del peek**
pintados uno al lado de otro, al ancho real del contenido de la hoja (358 px) y con los roles
tipográficos del código: el coche en sus 11 situaciones (incluido nombre largo y sin fix), el
detalle de los 3 sujetos en sus 7 casos, la cabecera leyendo cada carril, y «Aquí y ahora» con sus
7 casos de umbral. Cada caso lleva debajo **la regla que lo gobierna**.

### Verificado en esta vuelta

- `node --check` + **37.179 comprobaciones** de render (14 ejes de estado × 3 carriles × 4 estados
  de detalle × 3 alternativas) sin excepciones ni `undefined`/`NaN`; **28 casos** de la hoja de
  contactos; cero `data-act` sin manejador; cero enlaces internos roto s.
- `./gradlew testAll --rerun-tasks` → **3.921 tests, 0 fallos**.

### Dos decisiones más del user, el mismo día

**P8 · Conduciendo no se filtra nada — los tres sujetos, gasolineras incluidas.**
*«¿quién dice que una persona conduciendo no pueda parar a repostar?»*. Desmonta mi recomendación:
repostar se hace **sólo** conduciendo, así que apagar las gasolineras ahí era apagarlas siempre que
sirven. Consecuencias: B **no cambia** (la reducción sólo existía en mi texto; `notables()` nunca
miró el estado del coche) y C pierde su regla `EN_RUTA` — la precedencia baja de 9 reglas a 8 y de
cinco mudas a cuatro. Se mantiene que **la hoja no se despliega sola** y que el **ancla de los
notables sigue al coche, no a la cámara**. → `b-decisions.md` §10.

**P9 · El badge de vigilancia tenía un hueco, y lo cazó preguntando.**
Los cinco estados estaban intactos; el **dónde** no. `watchBadge()` sólo lo consumía
`carTwoLines()`, y la cabecera en modo carril se construía su propia fila con el **nombre del
carril** en el trailing → al leer una lista el badge **desaparecía, incluso muerto**. Hueco
introducido por mí al añadir la cabecera-por-scroll.

⛔ Arreglado como **sistema, no parche**: la regla vive en un sitio
(`badgeMayYield()` + `badgeMarkup()`) y la leen los tres modos —
***el badge cede su hueco sólo cuando es el feliz***. El sano se encoge a su glifo; el que avisa se
queda con su texto, incluido el peek de detalle. Es la misma regla que `resolveDetectionStory` ya
aplica (`carCardSaysTheWatch` aparta la fila calmada, **nunca** un aviso). Y el nombre del carril
salió del trailing por redundante: las pestañas de debajo ya lo dicen y lo marcan.
→ `b-decisions.md` §11, `design-system.md` §4.1.

### Nuevo: testigo re-ejecutable

`design-prototypes/paparcar-home/verify.js` — `node verify.js`, sin dependencias.
**22.646 comprobaciones**: barrido de render (14 ejes × 3 carriles × 4 estados de detalle × 3
alternativas), cero botones decorativos, **la regla del badge en los 3 modos × los 3 estados de
aviso**, «sin notables no hay sección», el orden fijo de los notables, conduciendo-no-filtra, la
dirección en una sola línea, enlaces internos y la fidelidad del CSS (ni `tabular-nums` ni
`font: … inherit`).

⛔ **Cada regla se añadió falsificándola primero.** La del badge, con el bug devuelto, da exactamente
3 fallos — uno por estado de aviso en modo carril. Sin esa falsificación el verde no probaría nada
(`TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001`).

### 3.ª vuelta (27-10) — dos decisiones más

**P10 · El umbral de «precio viejo» depende de la FUENTE.** Confirmado por el user. Añadido el
inventario real, porque la regla sólo es implementable sabiendo qué sella cada una, y son **cuatro
comportamientos distintos**: Minetur (ES) sella la **respuesta** y la propaga a cada precio; Francia
e Italia sellan **cada precio** (`reportedAt = null` → `oldestPriceAtMs` se lee como **cota**, nunca
como «la lista es de esta hora»); Alemania MTS-K **no sella nada** y declara `pricesAreLive` —
garantía legal de tiempo real; OSM nunca da precios; el disco conserva el `reportedAtMs` de cada
precio junto a su `rememberedAtMs`.
⛔ Por eso **no puede ser una constante global**: no tendría contra qué medir `pricesAreLive` y
enmudecería la única fuente que garantiza tiempo real. → `b-decisions.md` §12.
⚠️ Los 13 días del prototipo son el eje `fuelAge` del panel, **no** un umbral propuesto.

**P11 · Los badges de estado: retirar lo que yo había inventado.** El user preguntó si el badge de
vigilancia seguía intacto. Al ir al código encontré **dos** cosas rotas por mí, no una:

- **(a) el hueco que preguntaba**: en modo carril el trailing lo ocupaba el nombre del carril y el
  aviso **desaparecía, incluso en pausa**;
- **(b) lo que no preguntaba y era peor**: el badge **no debería existir sano**. Imprimía
  «Bluetooth» y «Vigilando tu sitio», violando `UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001` (*«ninguna
  superficie imprime el tier como texto»*), y comprimía en un chip lo que la app cuenta como **fila
  de acción** con causa + consecuencia + remedio (`HomeDetectionSurface`).

⛔ Regla nueva, **más simple** que la que tenía: **el badge de vigilancia SÓLO EXISTE cuando algo no
se cumple**, y entonces es un **botón** que abre la fila que lo arregla. El resolver de cinco
estados está **borrado**, no desactivado. → `b-decisions.md` §11, `design-system.md` §4.1.

⛔ **Y no se unifican los demás badges.** `SpotAgeIndicator` / `EnRouteIndicator` / `SizeChip` hablan
de una **plaza** o de un **dato**, no de tu coche. Juntarlos por su forma de píldora es cómo esta app
llegó una vez a tener *«tres azules con cuatro significados»*.

**Testigo en las DOS direcciones** (`verify.js` §3) y **las dos falsificadas**: con el bug del
nombre del carril devuelto → 3 fallos; con el chip verde permanente devuelto → 8; restaurado → 0.
Una sola dirección habría dejado pasar el error (b).

⚠️ **Deriva documental que esto saca a la luz:** la tabla de identidad de
`docs/design/COLOR-SYSTEM.md` todavía tiene una columna **«Badge»** con «ACTIVO» / «BLUETOOTH» /
«INACTIVO», anterior a `UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001`. **Fue esa fila la que me hizo
creer que etiquetar el tier era correcto.** No la toco (doc de producción, fuera de esta fase) pero
hay que arreglarla.

### 4.ª vuelta (28/29-10) — doc de producción arreglado + los standouts

**P12 · Arreglada la columna «Badge» de `docs/design/COLOR-SYSTEM.md`** (petición explícita del
user). Retirada entera, con la nota de por qué: la puso un producto anterior a
`UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001`, que quitó esas etiquetas. Las cadenas siguen vivas pero
**sólo como `contentDescription`**. ⚠️ La fila **sobrevivió 2 meses al cambio que la desmentía** y
costó lo que cuestan estas cosas: fue la fuente de verdad que leí para diseñar un badge con el tier
escrito. ⛔ Ningún test parsea el doc (`ColorGuardrailTest` sólo lo cita en KDoc), así que el arreglo
es seguro — `testAll --rerun-tasks` → 3.921 tests, 0 fallos.
⚠️ **Queda la misma deriva en CLAUDE.md** línea ~277: `ACTIVO` se cita como ejemplo del rol `badge`,
y es la única de los cinco ejemplos que ya no existe. No la toco (es la constitución del proyecto).

**P13 · «Mostrar cuáles son mejores»: ya existe, y mi prototipo lo había omitido.** El chip
**MÁS BARATA** / **MÁS CERCANA** está en la app desde
`FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001` (`domain/fuel/FuelStandouts.kt`) y el user lo
revisó dos veces (posición al final de la fila; tonos). Traído al prototipo **calculado, no
afirmado**: `DEMO` pasa a llevar distancia y precio numéricos.

Las **seis negativas**, todas con testigo **falsificado** (`verify.js` §8):
un solo candidato → nada · empate al mínimo → el chip **se comparte** (exigir «estrictamente la más
barata» lo borraba casi siempre: los surtidores copian el precio a la milésima) · empate
**universal** → se calla · **dos monedas** → sin «más barata», la más cercana aguanta · distancias
empatadas → sin «más cercana» · es las dos cosas → **un** chip.
Y el orden: **sólo la más barata sube**; la más cercana no reordena (muy a menudo no tiene precio).

⛔ **La negativa que propongo yo: una PLAZA nunca lleva «la mejor».** Una gasolinera no se agota, una
plaza sí; señalar «la mejor» entre cuatro manda a todos a la misma, y la app **ya cuenta** quién va
de camino (`Spot.enRouteCount`) — convertiría esa cifra en una carrera que habríamos empezado
nosotros. El chip es de los **complementos**, nunca del producto.

🟡 **Parkings: propuesta, no hecho.** Probado con las mismas negativas; su caso débil se ve solo —
sin tarifa publicada (lo normal) sólo queda «MÁS CERCANO», que es lo que el orden ya dice.

🟢 **Lazo cerrado:** la tarjeta de «Aquí y ahora» lleva el mismo chip **y** su sujeto lo elige la
misma función pura, así que el primer nivel y la lista no pueden nombrar cosas distintas (testigo
propio).

**P14 · Copy del pie: «Ver las 3»** (el user delegó la elección). La tarjeta ya dice el sujeto en su
eyebrow; lo que el usuario no sabe es **cuántas** hay. ⚠️ Con `aria-label` que sí lleva el sujeto
(«Ver las 3 gasolineras»), porque «Ver las 3» a solas no es un nombre accesible. Con una, «Ver la
única».

⚠️ **Un testigo se cazó a sí mismo**: el caso «la más cercana sin precio no se adelanta» estaba
montado con **una sola** gasolinera cotizando, donde el superlativo no existe y los rangos empatan
correctamente. El fallo era mi expectativa, no el código — y es exactamente para lo que está.

### 5.ª vuelta (28-10) — dos cerradas y dos ideas del user que ya tenían datos

**P15 · Standout en parkings: SÍ.** Ya no es propuesta. Mismas seis negativas; la que más manda ahí
es que **sin tarifa publicada —lo normal— no hay «más barato» que decir** y queda sólo «MÁS
CERCANO». ⚠️ En la app **no existe todavía**: entra en la fase 1, y el tipo tendrá que dejar de
llamarse `Fuel…` porque pasará a servir a dos sujetos.

**P16 · Al plegar: `reset`.** La hoja vuelve arriba. Ya era el defecto del prototipo.

**P17 · «Últimas actualizaciones cerca» → los datos YA EXISTEN, son GRATIS, y el radio ya está
decidido.** `RunFuelPriceWatchUseCase` corre **dos carriles en un tick**: `followedMoves` (Plus) y
**`nearbyMoves`** (free), anclándose en **dónde ESTÁ el conductor**, con
**`AROUND_FOLLOWED_RADIUS_M = 1.000 m`** y tope de las más cercanas. ⛔ El radio no se inventa: x = 1 km.

🟢 **Y contesta un ticket abierto:** `FUEL-A-RECORDED-SERIES-MUST-BE-READABLE-001` dice que se graban
30 días de precios y **no se leen en ninguna superficie** (`observeHistoryFor` reactivo, cero
observadores), y que *«pide decisión de producto (¿dónde vive?)»*. **Ésta es esa decisión.**

⛔ **Pero NO es una sección nueva**, por tres razones: sólo tiene **un sujeto** (una plaza ya es un
cambio por naturaleza — el carril de plazas *es* el feed de novedades; de los parkings no se
muestrea ocupación en el tiempo); rompería el **orden fijo**; y el mismo hueco puede decirlo mejor.
→ **La tarjeta de gasolinera prefiere el CAMBIO al NIVEL**: «Bajó hoy cerca de ti · −4 cts» en vez de
«Gasolina 95 más barata · 1,419». Mismo sitio, mismo orden. **Y las subidas también** — contar sólo
las bajadas convierte la app en un cupón.

⛔ La negativa que lo hace honesto **no es mía**, es de `EvaluateDailyPriceMoveUseCase`: *«dos filas
que difieren no es un precio que cambia»* — hace falta que **las dos muestras lleven sello de la
fuente y los sellos difieran** (Italia: 69 % de precios de 2 a 7 días, cola hasta 2013), más el
umbral de **≥0,5 c/l**. Y un movimiento **no se salta el umbral de frescura**: con precio viejo o sin
precios, no hay tarjeta.

**P18 · La hoja se abre sola al arrancar — sí, pero el disparador NO es «hay contenido».** Con «hay
algo que mostrar» se abriría casi siempre (hay parkings en todas partes) y taparía el mapa cada vez.
⛔ **El disparador es «hay NOVEDAD»**: algo que no estaba la última vez que miró. Reglas: una vez por
arranque en frío · **a media hoja, nunca desplegada** · ⛔ **nunca** conduciendo, con pregunta con
plazo, vigilancia en pausa, falta de permiso o nada que mostrar — esas cuatro **ya mandan** en la
cabecera y abrir la hoja encima las esconde · se cierra al primer arrastre.
⚠️ **Necesita una marca de «visto» persistida** (qué novedad ya se enseñó): sin ella se reabre en
cada arranque con la misma noticia, que es **peor** que no abrirse. Es la única pieza que exige campo
nuevo → **fase 2**, no 1.

### Verificado en esta vuelta
`verify.js` → **22.715 comprobaciones, 0 fallos** · `peek-cases.html` → **44 casos** ·
`testAll --rerun-tasks` → 3.921 tests, 0 fallos (con `COLOR-SYSTEM.md` ya editado).
**Falsificadas** las 6 negativas del standout, las 5 reglas nuevas (auto-abrir ×3, movimiento ×2) y
las 2 direcciones del badge. Un testigo se cazó a sí mismo (ver 4.ª vuelta).

### Lo que queda: cero decisiones y una medición

**Todas las decisiones de diseño están tomadas.** El rastro, en orden: P1–P7 (27-10) · P8–P9 ·
P10–P11 · P12–P14 · P15–P18 (28-10).

Lo único que queda **no es una decisión, es una medición**: ⚠️ el peek de B a **`fontScale` 1,3** en
device. Es el caso que el prototipo no modela y el que puede obligar a recortar la línea de meta de
las tarjetas.

👉 **Siguiente paso: el user decide si se implementa.**

⚠️ **6.ª vuelta (29-10): el plan estaba escrito para C.** `interaction-spec.md` se titulaba «C ·
Adaptive home» y sus tareas eran de C — peek de cuatro zonas, `ResolveHomeNewsUseCase`, la «zona
③» — dos días después de que el user eligiera B. **Reescrito entero para B** (§9, 4 fases, 24
tareas). La precedencia de C no se pierde: vive en `comparison.md` §2 y en `adaptive-home.html`.

(El orden fijo plaza · parking · gasolinera queda confirmado implícitamente por P8, que no lo
cuestionó; sigue vigilado por `verify.js` §4.)

### 🟢 El último riesgo ALTO de B se ha caído al medirlo (29-10)

**La premisa había caducado.** La regla documentada —*«nada se pide hasta entrar en el carril»*—
sigue siendo buena pero **ya no describe la app**: `FuelPriceWatchWorker` es **periódico y diario** y
`RunFuelPriceWatchUseCase` muestrea cada día las gasolineras más cercanas a **donde está el
conductor** (carril **free**, radio 1 km, con tope) y **las guarda en Room**, sin que nadie entre en
ningún carril. Entró con `FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001`.

Y los dos complementos tienen **cache de disco POR ZONA** (no un blob global) con `rememberedAtMs`.
Más aún: `GetNearbyPublicParkingsUseCase.remembered(lat, lon)` **ya existe** y `HomeViewModel` **ya
lo llama** — y el KDoc de `DiskBackedPublicParkingsSource` dice que esa API existe *«para los
llamantes que quieren algo en pantalla mientras preguntan»*.

⇒ **Decisión: «Aquí y ahora» LEE LO RECORDADO y no dispara ni una petición.** El disparador
documentado **no cambia**; entrar en un carril sigue siendo lo que pide datos frescos.
**El riesgo pasa de ALTO a BAJO y desaparece una tarea del plan.**
⚠️ Lo único que falta es la **puerta de lectura del lado del combustible**: no hay `remembered()`
ahí (los parkings sí). El dato está; falta el acceso → tarea 2.3.
⚠️ Coste asumido: en el **primer** arranque en una zona nueva el carrusel puede traer sólo la
tarjeta de plazas. Se autocorrige con una visita al carril o con el tick diario siguiente.

## Consumidores auditados

N/A en esta fase (no se modifica código de producción). El plan de implementación de
`interaction-spec.md` §6 lista, por tarea, los ficheros que tocaría, su riesgo y cómo se comprueba.

⚠️ La tarea **1.9** del plan (retirar `mine_lane_split` y `vehicles_header`) **revierte dos
decisiones tomadas en device por el user**. Va al final de su fase y con A/B en mano, no por
convicción de estos documentos.

⚠️ Y la **2.2** (cabecera por scroll) es la única de riesgo **medio-alto**: mover las pestañas a la
cabecera cambia el alto del peek, del que depende toda la geometría de anclas. El prototipo lo
resuelve dejando las pestañas del cuerpo invisibles pero con su hueco; en Compose hay que hacer lo
mismo o aceptar un desplazamiento (`interaction-spec.md` §9.1).
