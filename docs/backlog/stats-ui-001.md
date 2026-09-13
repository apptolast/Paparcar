# STATS-UI-001 · Una superficie, un alcance — la ficha del vehículo y el panel de cifras

**Estado:** ✅ Done · rama `feature/STATS-UI-001-one-scope-per-surface`

> ⚠️ **Superado en la regla free/Plus de las cifras** por
> [`stats-free-sees-every-figure-for-7-days-001.md`](stats-free-sees-every-figure-for-7-days-001.md)
> (13-09-2026): free ya no ve «una cifra y el resto tapado», ve TODAS sobre sus últimos 7 días; Plus
> vende el periodo. El resto de este doc sigue vigente.


Fase **F4 de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md)). Layout **aprobado por el
user el 08-09-2026** sobre la propuesta visual previa (el primer paso obligado de la fase).

## Problema

F4 mete ocho cifras nuevas en una pantalla que ya tiene **dos relojes** y ninguno se declara:

1. La card de identidad cuenta **toda la vida del coche** (`137 aparcamientos`).
2. La card de Actividad cuenta **el periodo del filtro** (`12 aparcamientos`).
3. Y un tercero escondido: dentro de la card de Actividad, el título y los km salen de
   `filteredSessions`, pero las tres líneas de abajo (día habitual · calle habitual · %
   automático) salen de `resolved.statsData`, que se calcula sobre **todas** las sesiones
   visibles. Cambiar el filtro a «Esta semana» no las mueve, dentro de una card que dice
   «12 aparcamientos». Está así en master hoy.

Añadir tiles encima sin arreglar esto multiplica el problema en vez de resolverlo.

## Doctrina violada

- **Ninguna cifra sin su alcance.** Misma familia que
  [UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001] (el chart pintaba días que el filtro
  había excluido) y que [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] (una suma parcial no es un
  total): una cifra que no dice sobre qué tiempo habla es una cifra que miente barato.
- **Gating soft, nada se borra** [MONEY-001]: Plus no añade zonas, LEVANTA el velo de la zona 03.
  Perder Plus da la misma pantalla con menos cifras, nunca una pantalla distinta.
- **El estado se escribe, no se tiñe** [UI-COLOR-DOCTRINE-001]: el ▲/▼ de la comparativa va en
  `onSurface`, jamás en verde/rojo.
- **Roles, no tamaños** [UI-TYPE-TWO-VOICES-ONE-ROW-001]: la cifra protagonista de un tile es CIFRA
  (`statNumber`/`statLabel`); su delta y todo lo demás, LECTURA.

## Diseño — cuatro zonas, de arriba abajo

La ficha se lee como una frase: **quién es este coche → qué hizo en el periodo → cuánto le costó
eso → cuándo lo hizo.** Regla rectora: *una superficie, un alcance, y aquí solo existen dos —
el coche (desde siempre) y el periodo (lo que dice el filtro).*

### Zona 01 · Identidad — `VehiclePageContent.VehicleHeroCard`
- Se queda: glifo, nombre, chip de tamaño, borde según vigilancia, editar, columna de datos
  (carrocería · tamaño / combustible · etiqueta), y la fila «Marcar como activo» al pie.
- **SALE la fila de 3 cifras** (`VehicleStatsRow`): son cifras de uso, no identidad, y son el
  origen del choque de relojes.
- «Última vez» **no era una estadística sino ESTADO** → sube a la línea de estado, junto al punto
  de aparcado.
- «Aparcamientos» y «plazas cedidas» → pie de la zona 03, etiquetadas «desde siempre».

### Zona 02 · Actividad — `HistoryContent` + `ActivityCard`
- Se queda: filtro, gráfico, título con N aparcamientos del periodo + km con su cobertura.
- **SALEN los tres `ActivityFact`**: eran del historial entero dentro de una card del periodo.
  Reaparecen en la zona 03 **ya recalculados por periodo** (el agregador de F3 los da así).

### ⛔ La tarjeta de cifras es la SEGUNDA CARD DE ACTIVIDAD, no una sección (user, 08-09)
Corrección del user sobre la propuesta: «Este mes» encaja con el mismo tipo de título que
«12 aparcamientos», porque las dos son cards de **Actividad**; el historial va aparte. Así la
dependencia se lee sola — un solo encabezado, un solo filtro mandando sobre las dos cards — y no
hace falta explicar en ninguna parte que el filtro gobierna las cifras. El Historial sigue siendo
sección propia porque el filtro genuinamente NO lo gobierna.

### Zona 03 · Cifras — NUEVO, el corazón de la fase
- Rejilla de tiles del **mismo periodo** que la zona 02, con ▲/▼ contra el periodo anterior
  (`VehicleHistoryCalculator.previousWindow` + `PeriodStats.previous`, ambos de F3).
- Tiles: gasto aprox · CO₂ · duración media · hora punta · día habitual · calle habitual · %
  automático · plazas cedidas. Cada uno **existe solo por encima de su umbral** — bajo el umbral no
  se pinta, nunca un «0» que el dato no respalda.
- **Free**: la primera cifra con dato se ve entera; el resto con velo (se leen los títulos, no los
  valores) + fila Plus sobre `PapPremiumSurface`.
- **Plus**: todo visible + «Ver desglose completo» → panel.
- Pie, en una línea de LECTURA y no como tiles: «Desde siempre: 137 aparcamientos · 41 plazas
  cedidas».

### ⛔ El alcance del bloque en free se llama por su ventana REAL (decidido 08-09)
Desde F3 las cifras se calculan sobre lo que el usuario puede VER (`gateHistoryToEntitlement`), así
que **un free no puede tener un «Este mes» de verdad**: detrás solo hay 7 días, y los filtros Mes /
3 meses / Todo le darían la misma cifra que Semana. Por tanto:
- En free el bloque se titula **«Últimos 7 días»**, no por el filtro.
- Elegir un periodo más largo **es** el gesto que abre el paywall (`PaywallReason.STATS`).
- En Plus el bloque se titula por el periodo elegido.

### Zona 04 · Historial
Intacta: sesión activa arriba, días agrupados, footer de lo oculto por la ventana gratuita.

## Criterio de éxito

- Ningún fact de la zona 02 sobrevive con alcance de historial: **test** de que cambiar el filtro
  cambia día/calle/% automático.
- **Test**: en free el título del bloque no depende del filtro; en Plus sí.
- **Test**: bajo umbral, el tile no existe (no aparece un 0/—).
- **Test**: free ve exactamente una cifra sin velo; Plus ninguna velada.
- Sin `fontSize`/`fontWeight`/`Color(0x…)` inline (Konsist: typography, color, divider, import).
- Strings nuevas en los 9 locales. Galería de estados + escenarios mock en la MISMA tarea.
- Suite + `assembleProdDebug` + `assembleMockDebug` + klib iOS en verde.

## Desviaciones sobre lo aprobado, y por qué

1. **El velo NO es un desenfoque.** `Modifier.blur` es **no-op por debajo de API 31** y el minSdk
   es 26: un valor «difuminado» se habría leído perfectamente nítido en cualquier móvil anterior a
   Android 12 — un paywall con agujero, y mudo. El velo es una **barra redactada**: el valor no se
   compone siquiera, así que no puede filtrarse. Se conserva la intención aprobada (se leen los
   títulos, no los valores) sin la vía de fuga.
2. **`HistoryStatsData` se ELIMINA.** Al mover los facts a la tarjeta de cifras y quitar la fila de
   la card de identidad, el tipo de presentación se quedaba sin un solo consumidor: la UI lee
   `PeriodStats` directamente. Los 8 tests que STATS-CORE-001 había migrado a través de su mapper
   pasan a asertar sobre el agregado.
3. **El pie «desde siempre» cuenta sobre la lista COMPLETA**, no sobre la gateada. Una línea que
   dice «desde siempre» tiene que decir verdad, y para un free la lista visible son 7 días. No
   revela contenido gateado — sólo dos conteos, y el pie del historial ya dice cuántas sesiones
   quedan fuera de la ventana.
4. **Con el filtro «Todo» el pie desaparece**: el periodo YA es toda la vida del coche, así que la
   línea repetiría la tarjeta en letra pequeña.
5. **`statTileKeys` es una función pura aparte del copy.** Qué cifras existen y en qué orden son
   las reglas de producto (la primera es la que un free ve entera); dentro de un `@Composable` no
   habría test que las alcanzara.

## Segunda pasada, con la pantalla en la mano (08-09-2026)

Cinco correcciones del user tras verla en device. Ninguna cambia el reparto de zonas: son la
diferencia entre el layout aprobado y el layout terminado.

0. **El glifo de la moto se hundía en su recuadro.** No era el recuadro: `ContentScale.Fit` centra el
   VIEWPORT, y la tinta de la moto vive en la mitad baja de un viewport de 62 unidades diseñado para
   coches (su centro de tinta cae en y≈44; el de cualquier coche, en y≈38). `CarSpec` gana
   `inkYOffset` —un desplazamiento declarado, aplicado por el builder como transformación de grupo—
   y la moto sube 5,7 unidades. Se corrige el DATO, no se redibujan 13 paths.
1. **El glifo se alinea con la primera línea** en cuanto la columna pasa de dos líneas: centrado
   flotaba a media altura de un bloque de cuatro. Se deriva del CONTENIDO (`metaSecondLine` o
   `statusLine` presentes), así que Home no se entera y sigue centrado.
   Y **la línea de estado dice DÓNDE**: glifo de ubicación + el mismo nombre de sitio que resuelve
   Home + «hace 2 h». Es un slot, no un String, porque la cabecera compartida no tiene por qué saber
   qué aspecto tiene un aparcamiento.
2. **La cabecera del gráfico se parte en dos extremos** (icono + N aparcamientos a la izquierda, km
   en `caption` a la derecha): corridos en una frase se leían como iguales.
   Y ⛔ **la constante del gráfico pasa a ser el HUECO, no la barra.** Antes cada bucket ocupaba
   `ancho/n` y la barra tomaba una fracción, así que la separación crecía y menguaba con el número
   de barras — siete días apretados, cuatro semanas flotando. Ahora el aire es fijo y las barras se
   reparten lo que queda; el radio es el del tema (8 dp), tope a media anchura para las estrechas.
3. **Cada cifra lleva su glifo** — y son los que la app ya gastaba en esas mismas ideas (calendario
   para el día habitual, pin para la calle, destello para lo automático, manos para las plazas
   cedidas): la rejilla hereda un vocabulario en vez de inventarlo. La tarjeta respira con 16 dp
   sobre la del gráfico (más que los 8 de un filtro, menos que los 24 de una sección).
4. **La fila del historial ENTERA navega.** Era una tarjeta inerte con un botón de 48 dp en la
   esquina: había que apuntar a un icono al lado de algo que parecía pulsable y no lo era. Con la
   tarjeta como diana el botón ya no dice nada que la tarjeta no diga, así que se convierte en
   chevron — el mismo movimiento que hizo el pie de Plus. La etiqueta accesible viaja con la
   TARJETA, que es el botón; el chevron es una marca.

### Tercera pasada (08-09-2026, misma sesión)

- **El track vacío se probó SIN pintar y se revirtió** a petición del user, en el mismo rato. Queda
  escrito porque es una decisión, no un descuido: un bucket que no dibuja nada deja el gráfico
  flotando sobre sus etiquetas y quita la regla contra la que se compara la barra llena. El track es
  el listón; el relleno es la lectura.
- **Todo lo que cambia con el filtro se mueve.** No sólo el CO₂: el titular del gráfico cuenta hasta
  su nuevo valor con el MISMO tiempo que los tiles (dos velocidades en una pantalla se leen como dos
  sucesos), los km y las cifras de palabra —día habitual, calle, hora punta— hacen fundido, porque
  «Martes» no puede contar hasta «Jueves» pero sí cambió. Números suben, palabras se cruzan.
- ⛔ **Los tiles se componen con `key(label)`, no por posición.** El contador recuerda de dónde
  viene un número, y la rejilla cambia de contenido con el periodo: un tile que pasa de «gasto» a
  «CO₂» porque la primera cifra desapareció heredaba la animación de la otra y trepaba de 9,40 a
  14,2 como si un mismo número se hubiera movido. La identidad es la cifra; la posición es el
  accidente de cuántas sobrevivieron.

## Consumidores auditados

- `VehicleIdentityHeader` — COMPARTIDO con la card de Home: gana un slot `statusLine` OPCIONAL que
  sólo pasa la ficha; Home no lo usa (ya tiene su propio pie de estado) y queda intacto ✔
- `VehicleStatsRow` / `StatCell` / `StatDivider` — eliminados; sin más consumidores (grep) ✔
- `ActivityCard(facts = …)` — parámetro, tipo `ActivityFact`, su columna y sus 4 constantes
  retirados; único call site era `HistoryContent` ✔
- `HistoryStatsData` / `AutoDetectedShare` / `toHistoryStatsData` — ELIMINADOS, sin consumidores ✔
- `PaywallReason.STATS` — ya existía desde FREE-LIMITS-001; sólo faltaba el intent que lo envía ✔
- `HistoryTimeline.resolve` — gana `vehicle` (para consumo) y ahora devuelve `periodStats` +
  lifetime; los 3 call sites (VM ×2, galería) pasan el coche ✔
- Strings: 6 keys obsoletas borradas y 21 nuevas añadidas en los 9 locales
  (`LocaleParityGuardrailTest` verde con 5 tests) ✔

## Verificación (08-09-2026)

- Suite completa **BUILD SUCCESSFUL**. Testigos: `HistoryTimelineScopeTest` tests="6" failures="0"
  (rescoping del filtro, comparativa, lifetime sobre la lista completa aun en free, free≠Plus) ·
  `VehicleStatsBlockTest` tests="6" failures="0" (presencia por umbral y orden de lectura) ·
  `VehicleHistoryCalculatorTest` tests="16" failures="0".
- Guardarraíles ejecutados de verdad, no cacheados: Typography 5 · Color 8 · Divider 1 · Import 1 ·
  LocaleParity 5, todos failures="0".
- `assembleProdDebug`, `assembleMockDebug` y `compileKotlinIosSimulatorArm64` en verde.
- Galería: 3 variantes nuevas en el grupo Historial (Plus completo · free con una cifra y el resto
  tapado · por debajo de umbrales) y `resolvedHistory` pasa coche para que el bloque enseñe CO₂.
- **En device (emulador Pixel 8 Pro, mock, sha256 verificado)**: la ficha se ve con la card de
  identidad sin cifras y con «Last parking · 11 days ago», la tarjeta de cifras como segunda card de
  Actividad titulada «Last 7 days» en free, y el Historial aparte. **Defecto encontrado y
  corregido allí**: con un solo tile en la fila, el divisor vertical dibujaba media celda vacía y se
  leía como un tile que no cargó — el divisor sólo se pinta ENTRE dos tiles presentes.
- ⏳ La cara Plus queda verificada por tests y por su variante de galería, **sin ver en mano**: el
  toggle «Plus activo» del Dev Catalog se reinicia al elegir un escenario, así que no se pudo
  levantar en la misma sesión.
- ⚠️ Un test propio falló al primer intento por un supuesto MÍO de calendario (`nowMs` cae en lunes,
  así que «esta semana» sólo contenía el día 0): se ancló a `windowStartMs` en vez de a «días
  atrás». El fallo era del test, no del código.
