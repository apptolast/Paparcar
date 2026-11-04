# Auditoría de UX de la Home — Paparcar

> Fecha: **2026-10-27** · ticket `UX-THE-HOME-MUST-EARN-ITS-FIRST-GLANCE-001`
> Fase: **diagnóstico y dirección**. Nada de producción se ha tocado.
> Prototipos: [`design-prototypes/paparcar-home/`](../../design-prototypes/paparcar-home/index.html)

## Cómo leer este documento

Cada hallazgo está marcado con su grado de evidencia:

- **[CÓDIGO]** — comprobado leyendo el fichero que se cita. Es un hecho del repo.
- **[MEDIDO]** — cifra derivada de constantes del propio código (paddings, alturas declaradas). Es
  aritmética sobre el código, **no** una medición en device.
- **[HIPÓTESIS]** — juicio de UX. **Necesita validación con usuarios**; se lista en §10.

⛔ No se atribuye a ningún componente una capacidad que no se haya abierto. Donde no se ha podido
verificar algo, dice «no verificado».

---

## 0 · Resumen del diagnóstico

La home de Paparcar **no está mal organizada por inercia**. Es lo contrario: cada bloque tiene su
ticket, su razón medida y, en muchos casos, la historia de su propio error anterior escrita al lado.
El sistema de estados vacíos es mejor que el de la mayoría de apps de este tamaño — distingue «no se
ha preguntado», «no hay nada» y «no se pudo preguntar» en **tres sentencias distintas**
(`HomeFuelSlice.failed` / `missingFuelType` / lista vacía, y `HomeState.spotsUnasked`).

El problema es otro, y es de **producto, no de maquetación**:

> La home está diseñada para una app con comunidad. Paparcar tiene menos de 20 usuarios. El carril
> que se abre por defecto es el único cuyo contenido depende de que esa comunidad exista, y está
> detrás del bloque más alto de la pantalla.

Tres consecuencias concretas, las tres verificadas en código:

1. **En reposo no se ve el estado del coche.** El peek son 88 dp: una fila, y desde el primer
   despliegue habla del carril, no del coche.
2. **El carril por defecto es el que más probablemente está vacío**, y lo que sí tiene datos
   (gasolineras, parkings) no se pide hasta que lo pidas.
3. **La fila de sujetos dice que los tres valen lo mismo** —tres tercios idénticos— cuando su propio
   KDoc declara que no son pares.

La dirección recomendada (§8) es **C · Adaptive home**, con la estructura de A (vehicle-first) como
su estado base. Puntuación completa en
[`comparison.md`](../../design-prototypes/paparcar-home/comparison.md).

---

## 1 · Qué hace hoy la home — reconstrucción desde el código

### 1.1 Anatomía

```
HomeScreen.kt (1.657 l.)
├── HomeMapSection      mapa kmp-maps + marcadores + FABs + pill de ubicación
├── HomeHeaderSection   buscador flotante + chips de zona + toggle de tipo de mapa
└── HomeBottomSheet     hoja arrastrable, 4 anclas
    ├── HomePeekHandle  la cabecera SIEMPRE visible (BrowsePeek / peeks de detalle)
    └── homeSheetItems()  el interior, LazyColumn (890 l.)
```

Estado único `HomeState` (746 l., ~60 campos) con proyecciones `by lazy`; `HomeViewModel` tiene
2.206 l. y delega en 7 controladores (`HomeSpotsController`, `HomeTripController`,
`HomeSearchController`, `HomeGeocodingController`, `HomeUiController`, `HomeNearbyAlertController`,
`HomeSelection`). **[CÓDIGO]**

### 1.2 Las cuatro anclas de la hoja

`sections/sheet/HomeSheetPositioning.kt` **[CÓDIGO]**:

| Ancla | Dónde queda | Nota |
|---|---|---|
| `PEEK` | `containerHeight − peekHeight` | `SheetPeekHeightInitial = 88.dp`, luego la altura real medida del handle |
| `HALF` | `(expanded + peek) / 2` | ancla intermedia real |
| `EXPANDED` | `containerHeight × 0,42` | la hoja ocupa ~58 %; es el auto-snap «desplegado» |
| `FULL` | `0`, o la altura del contenido si todo cabe | sólo por arrastre manual |

**Consecuencia [MEDIDO]:** al abrir la app en un 390×844, la hoja descansa en `PEEK`. Visible sin
tocar nada: el mapa, el buscador, los FABs y **una** fila de ~88 dp.

### 1.3 El interior, en su orden real

`homeSheetItems()` emite en este orden **[CÓDIGO]**:

| # | Item (key) | Condición | Alto aprox. **[MEDIDO]** |
|---|---|---|---|
| 1 | `first_steps` | usuario nuevo con coche | ~310–420 dp (header + hasta 6 filas: `progress.applicable.forEach`) |
| 2 | `detection_surface` | historia ≠ `Hidden` y no la hospeda la card | ~72–140 dp |
| 3 | `vehicles_header` | ≥1 coche | ~40 dp |
| 4 | tira de coches | ≥1 coche | ~150 dp (`HomeVehicleChip`) o ~190 dp (`HomeVehicleCard`, un solo coche) |
| 5 | `mine_lane_split` | **siempre** | ~56 dp («En esta zona») |
| 6 | sticky: tabs + filtros | siempre | ~53 dp tabs + ~48 dp filtros |
| 7 | carril | según `nearbyLens` | variable |
| 8 | `report_spot_cta` | siempre | ~90 dp |

**Suma antes del primer dato del carril, usuario nuevo con un coche: ~700–800 dp.**
En un 390×844 con la hoja en `EXPANDED` (~490 dp útiles), el carril **no entra**: hay que arrastrar a
`FULL` y además hacer scroll. **[MEDIDO]**

Para un usuario establecido (checklist cerrada; la vigilancia sana se calla porque la card ya la
cuenta — `carCardSaysTheWatch`) la suma baja a ~340–390 dp y el carril **sí** entra. **[MEDIDO]**

> Es decir: la home es más densa **exactamente para el usuario nuevo**, que es el único que
> Paparcar tiene hoy.

### 1.4 El carril: un sujeto a la vez

`HomeNearbyLens` = `SPOTS` | `PARKINGS` | `FUEL`. Una pestaña activa; el mapa pinta la capa del
carril activo (`showsFuelOnMap`, `showsParkingsOnMap` derivan del **mismo** valor). No se persiste:
**todo arranque en frío abre en `SPOTS`**. **[CÓDIGO]**

Esto fue un arreglo deliberado y acertado: antes los tres sujetos se apilaban plegables y la `y` de
«dónde repostar» dependía de cuántas plazas hubiera (su KDoc cita ~1.100 dp con 10 plazas y
~2.100 dp con 25). **[CÓDIGO]**

Lo que queda abierto es la **asimetría declarada y no aplicada**:

```kotlin
// HomeNearbyLensRow.kt — KDoc
// ⛔ The three are not peers, and the asymmetry is deliberate.
//    Free spots are the product; car parks and fuel are complements to it.
...
LensTab(SPOTS,    modifier = Modifier.weight(1f))
LensTab(PARKINGS, modifier = Modifier.weight(1f))
LensTab(FUEL,     modifier = Modifier.weight(1f))
```

Tres tercios idénticos. La jerarquía la lleva **sólo** el default y el orden. **[CÓDIGO]**

### 1.5 Estado de cada coche

`vehicleIdentityColor(watch)` → **verde** = detección activa · **azul** `papCarBlue` = vigilado por
Bluetooth · **gris** = sin vigilancia. El **estado** (aparcado / en ruta / sin marcar) se escribe en
`onSurface` y se anima, nunca se tiñe. Un solo resolver (`ui/theme/VehicleIdentity.kt`). **[CÓDIGO]**

`HomeVehicleChip` muestra glifo del coche + glifo de vigilancia + nombre en el color de identidad,
sobre una fila accionable: aparcado → icono + dirección (máx. 2 líneas) · sin marcar → glifo «no
marcado» + píldora «Marcar» · en ruta → glifo de ruta auto-dibujado + palabras de estado pulsando +
dónde está el coche + píldora de parar. **[CÓDIGO]**

La vigilancia es **honesta**: `ParkedWatchBadge` se resuelve contra el `ServicePresence` real, así que
un FGS muerto lee `WATCH_INTERRUPTED` y no «Vigilando». **[CÓDIGO]**

Estados reales del sistema de detección (`DetectionUiState`): `NoVehicle`, `Inactive`,
`BlockedCore`, `Parked`, `Monitoring`, `AwaitingFirstPark`, `ArmedBluetooth`, `Silent`. La historia
que se cuenta en pantalla (`DetectionStory`) tiene precedencia fija: `BlockedCore` →
`AwaitingAnswer` → `PendingAsk` → `NoVehicle` → `Inactive` → `AwaitingFirstPark` → `Watching` →
`Hidden`. **[CÓDIGO]**

### 1.6 Qué datos tiene cada sujeto, y qué sabe decir

| Sujeto | Cuándo se pide | Sentencias que distingue |
|---|---|---|
| Plazas | suscripción viva, siempre | lista · vacío (`home_empty_title` + ilustración) · **no preguntado** (`spotsUnasked`: sin fix y sin cámara movida) · vacío por filtro de talla |
| Parkings | **al entrar en su carril** | lista · vacío · `failed` · `hasAnyBeforeFilter` (ninguno cumple el filtro ≠ no hay ninguno) · `rememberedAtMs` (de disco) |
| Gasolineras | **al entrar en su carril** | lista con precios · lista **sin** precios · `missingFuelType` · `failed` · `didFallBack` · `reportedAt` · `pricesAreLive` · `rememberedAtMs` · `oldestPriceAtMs` |

**[CÓDIGO]** — y éste es el activo más valioso de la app para el rediseño: **el vocabulario para no
mentir ya existe**. Ninguna propuesta de este documento necesita inventarlo.

### 1.7 Lo que la home NO conecta

No hay en la home **ninguna** superficie que lea `PeriodStats`. Las cifras viven en Vehículos
(`VehicleStatsBlock` → `PapStatTile`, 12 claves de tile) y en el panel de Stats
(`StatsScreen` + `StatsInsightsCard` + `StatsMonthRecapCard` + `StatsPaceCard` + `StatsPlacesCard` +
`StatsWhenCard`). La home no enseña ni una conclusión. **[CÓDIGO]**

Lo que `PeriodStats` ya sabe calcular: `parkings`, `endedParkings`, `distance` (con su cobertura),
`fuelLiters`, `energyKwh`, `costEstimate`, `co2Kg`, `avgParkedDurationMs`, `peakHourBucket`,
`mostActiveDayOfWeek`, `places` (clusters), `spotsReleased`, `autoDetectedCount` /
`detectionKnownCount`, `searchTime` (con cobertura) y `previous` para los ▲/▼. Cada nullable es
`null` **por debajo de su umbral de significancia** — nunca un 0 disfrazado. **[CÓDIGO]**

---

## 2 · Las ocho preguntas

### 2.1 ¿Qué función principal espera el usuario al abrir Paparcar?

Hay que separar dos momentos, porque el código ya los separa:

- **Al volver al coche** → *«¿dónde lo dejé y sigue vigilado?»*. La app responde: el peek colapsado
  titula el coche aparcado… **pero sólo hasta que el usuario abre Browse una vez**. Desde entonces
  `laneHeaderLatched` hace que la cabecera hable **del carril**
  (`HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001`, decidido al plegar por `nextLaneHeaderLatch`).
  **[CÓDIGO]**
- **Al salir con el coche** → *«¿hay plaza, dónde aparco, cuánto cuesta repostar?»*.

**Hallazgo [CÓDIGO + HIPÓTESIS]:** el latch es correcto como mecánica —quien está mirando parkings no
quiere que la cabecera le hable de su coche— pero su efecto neto es que **la pregunta nº 1 del
producto deja de estar en el primer vistazo en cuanto el usuario explora una vez**. Y lo que la
sustituye es el contador de un sujeto que hoy casi siempre vale 0.

### 2.2 ¿Qué necesita ver sin ninguna interacción?

Hoy, en 88 dp, ve **una** de tres cosas: el coche aparcado, el viaje vivo, o el contador del carril.

Debería ver a la vez **[HIPÓTESIS]**:

1. el estado del coche — una línea: nombre + estado + dónde;
2. si la vigilancia está viva o rota (el badge honesto ya existe);
3. **una** acción que tenga sentido ahora;
4. y, si hay algo que merezca la pena cerca, **un** dato — no tres listas.

Eso cabe en ~170 dp. El coste es que el mapa pierde ~85 dp de alto visible. **[MEDIDO]**

### 2.3 ¿Qué compite por la atención sin aportar lo mismo?

1. **El checklist de primeros pasos [CÓDIGO].** Hasta 6 filas (`MARK_PARKING`, `PRIVATE_ZONE`,
   `UNDERSTAND_WATCH`, `FORTIFY_WATCH`, `FIND_SPOT`, `EXPLORE_NEARBY`), el item más alto de la hoja,
   encima de todo, y sólo para el usuario nuevo. Su diseño interno es cuidadoso (nunca bloquea, cada
   paso tiene dos caras, se puede diferir, señala en vez de hacer). Lo que compite es su
   **ubicación**: convierte la home en un tutorial. La prueba estructural es que existe un mecanismo
   entero —`FirstStepsOwnership`— cuya única misión es **callar la fila de detección** cuando el
   checklist dice lo mismo. Dos superficies que necesitan un árbitro para no repetirse se solapan
   por diseño.
2. **`mine_lane_split`** («En esta zona»): ~56 dp cuya función medida es separar visualmente dos
   bloques —su comentario lo dice: *«SEPARA dos bloques que se estaban leyendo como uno»*— justo
   encima de una fila sticky de pestañas que ya anuncia el cambio de tema. **[CÓDIGO + HIPÓTESIS]**
3. **`vehicles_header`**: misma familia. Se quitó una vez y se volvió a poner por una razón real
   (separar, no nombrar), pero son dos cabeceras decorativas en ~700 dp de recorrido. **[CÓDIGO]**
4. **`report_spot_cta` permanente** al final del carril de plazas: correcto editorialmente
   (deshabilitado sin fix pero visible, diciendo por qué), y a la vez compite con el estado vacío
   que tiene justo encima. **[CÓDIGO]**

### 2.4 ¿Qué obliga a tocar, explorar o interpretar demasiado?

- **Ver gasolineras cuesta 4 pasos**: arrastrar la hoja → scroll → tocar la pestaña → esperar el
  fetch. Es el contenido que más veces tiene datos. **[CÓDIGO]**
- **Dos de las tres pestañas se abren a ciegas**: el contador sólo existe *después* de preguntar.
  La decisión es correcta (`FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001`: un `0` sobre algo que
  nadie preguntó es un veredicto que ninguna fuente dio) y tiene ese efecto secundario. **[CÓDIGO]**
- **22 roles tipográficos** y 5 vocabularios de espaciado (`PapGap`, `PapSectionRhythm`,
  `PaparcarSpacing`, `PapControlHeight`, `PapPageRhythm`). No es un problema de usuario: es riesgo
  de mantenimiento y de que dos superficies elijan distinto. **[CÓDIGO]**

### 2.5 ¿Qué es poco útil mientras no haya usuarios suficientes?

- El carril de **plazas como default**. No la feature: el default. **[CÓDIGO]**
- El **contador de plazas en la cabecera latcheada**, que con 0 plazas pone un cero en el sitio más
  visible de la app. **[CÓDIGO]**
- `Spot.enRouteCount` («3 en camino»): con <20 usuarios casi siempre 0 — ya hay ticket abierto
  (`stats-a-spot-handed-over-can-count-its-takers-001`).
- El paso `FIND_SPOT` **ya está bien resuelto**: tiene dos caras (`SEE_NEARBY` cuando hay plazas,
  `REPORT_ONE` cuando no), así que nunca pide algo que no existe. No tocar. **[CÓDIGO]**

### 2.6 ¿Qué debe ocupar la home, qué ser contextual, qué irse a otra pantalla?

| Primer nivel de la home | Contextual (sólo cuando aporta) | Otra pantalla |
|---|---|---|
| Estado del coche + vigilancia + 1 acción | Pregunta de detección con plazo (`AwaitingAnswer`) | Historial completo → Vehículos |
| Mapa con tus coches, tus zonas y el viaje | Precio notable de gasolinera cercana | Gráficas y serie de 30 días → Stats |
| Acceso a los tres sujetos de «cerca» | Plaza libre a menos de X m | Gestión de zonas → su hoja |
| Marcar aparcamiento / avisar de plaza | Parking público cuando no hay plazas | Fortalecer vigilancia → Ajustes |
| | Recap del mes cerrado (una línea, una vez) | Insights completos → Stats |

**[HIPÓTESIS]**, salvo la columna derecha, que es donde ya viven hoy.

### 2.7 ¿Qué es jerarquía y qué es limitación funcional?

**Jerarquía — se arregla con presentación:**
- El peek de 88 dp que sólo dice una cosa.
- Las tres pestañas a `weight(1f)` contra su propia doctrina.
- El checklist encima de todo.
- Dos cabeceras de sección decorativas.
- La home sin ninguna conexión con cifras que ya se calculan.

**Limitación funcional — hace falta lógica nueva:**
- Pedir gasolineras/parkings **sin** entrar en su carril (hoy el disparador *es* entrar; está
  documentado como decisión de ahorro de datos del usuario).
- Comparar precios («X está 0,07 €/l por debajo de Y, a 600 m»): `NearbyFuelStation` lleva precio y
  distancia, pero **no existe** el veredicto comparativo ni su umbral de significancia.
- Un «¿qué me ha dado Paparcar?» en la home: `PeriodStats` existe, pero no llega al grafo de Home.

**Limitación de DATOS — no la arregla ninguna de las dos:**
- No hay plazas porque no hay usuarios. Ninguna decisión de diseño lo cambia; sólo decide si la
  ausencia se lee como «la app está vacía» o como «aquí no hay, y esto sí».

### 2.8 ¿Qué es sólo visual y qué toca el modelo de estado?

**Sólo visual / recomposición** (ni un campo nuevo):
- Peek de 2–3 líneas: todo está ya en `HomePeekSlice`.
- Reordenar o retirar items del sheet.
- Pestañas con ancho asimétrico.
- Checklist como fila plegada con su progreso.

**Requiere campos o casos de uso nuevos:**
- Prefetch de un sujeto que no es el carril activo → cambia el disparador documentado de las slices.
- Veredicto de ahorro → **caso de uso propio** con test (por `DET-VERDICT-NOT-PREDICATE-001`: su
  resultado se cita en pantalla ⇒ es veredicto, no predicado).
- Resumen de valor en la home → proyección nueva + un `Flow` de stats en `HomeViewModel`.
- Carril «oportunidades» mezclado → proyección nueva **y rompería** `showsFuelOnMap` /
  `showsParkingsOnMap`, que hoy derivan la capa del mapa de un único valor. ⚠️ Riesgo real y
  documentado: dos controles para un sujeto es justo lo que ese ticket vino a eliminar.

---

## 3 · La jerarquía correcta (§4 del encargo)

### 3.1 Alrededor de qué se organiza la experiencia

La respuesta no es «el vehículo» ni «el mapa»: es **el vehículo como sujeto, el mapa como lienzo y
lo de alrededor como respuesta a una pregunta que el usuario hace**.

Razonamiento:

- **El mapa no puede ser el organizador** porque con <20 usuarios el mapa no tiene contenido propio
  más allá de tus coches y tus zonas. Un mapa que organiza una app sin datos organiza el vacío.
- **El vehículo sí puede serlo** porque su estado existe siempre, es 100 % local, no depende de
  comunidad, de red ni de cobertura de un feed, y es lo que la app hace mejor que nadie (detección
  automática). Es el único contenido **garantizado**.
- **Pero el vehículo solo no basta**: el 2º momento del producto (salir a buscar sitio) no es sobre
  tu coche, es sobre lo de alrededor. Una ficha de vehículo no sirve para eso.

De ahí la forma: **un sujeto fijo arriba (tu coche) y una respuesta variable abajo (lo de aquí)** —
que es, estructuralmente, lo que la home ya hace. Lo que cambia la propuesta es **cuánto de cada
cosa se ve sin tocar nada** y **qué sujeto se elige cuando el por defecto no tiene nada que decir**.

### 3.2 Las tres preguntas, contestadas por capa

| Pregunta | Dónde se contesta | Sin interacción |
|---|---|---|
| ¿Qué pasa con mi vehículo? | peek, línea 1–2 | **sí** |
| ¿Qué puedo hacer ahora? | peek, fila de acciones | **sí**, 1–2 acciones |
| ¿Qué utilidad adicional hay aquí? | peek, línea 3 (una sola) + carril al desplegar | **una** línea |

### 3.3 Lo que se rechaza explícitamente

- ❌ **Dashboard de tarjetas.** Hay datos para llenarlo (`PeriodStats` tiene 15 campos) y es
  precisamente por eso que no se hace. La home es el AHORA.
- ❌ **Cuarto tab.** La navegación de 3 destinos tiene regla editorial clara y funciona.
- ❌ **Quitar las pestañas.** Son la solución a un problema medido (§1.4). Se **reponderan**, no se
  eliminan.

---

## 4 · Estados vacíos — la estrategia (§5 del encargo)

Principio rector, derivado de la doctrina de detección que ya rige el proyecto:

> *El evento NOMINA, sólo lo MEDIDO confirma* → aplicado a la home:
> **la ausencia se nombra, nunca se rellena.** Un hueco se llena con lo que SÍ sabemos, y lo que
> sabemos se dice con su fuente. Jamás con una estimación de actividad inventada.

### A · No hay plazas compartidas cerca

Lo que hay hoy: ilustración + `home_empty_title` («No spots reported yet» / «Aún no hay plazas
reportadas») + la tarjeta de avisar debajo. Es correcto y honesto. **[CÓDIGO]**

Lo que falta: **una salida útil**. Propuesta:

1. La frase se queda (una línea, sin caja — la doctrina `UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001`
   ya lo exige).
2. Debajo, **una sola** alternativa, nombrada por lo que es y **sólo si ya la hemos preguntado o la
   podemos preguntar**: *«3 parkings públicos en esta zona»* → entra en el carril de parkings.
3. La tarjeta de avisar se queda, porque es la única forma de que la comunidad exista.
4. ⛔ Lo que **no** se hace: prometer un número de parkings antes de pedirlos. Si el carril de
   parkings no se ha preguntado, la línea dice *«Mira los parkings públicos de aquí»* sin cifra —
   que es una invitación, no un veredicto.

### B · El usuario no ha configurado bien el vehículo

Esto **ya está resuelto mejor de lo que el encargo supone**, y la auditoría lo confirma: no todos los
usuarios necesitan la misma configuración. `resolveWatchReinforcement` decide entre `BLUETOOTH`
(hay dispositivos emparejados y el coche no tiene MAC), `BATTERY` (fiabilidad reducida) y `NONE` (no
aplica), y un coche con Bluetooth **nunca** llega a `REDUCED`. **[CÓDIGO]**

Lo que esta auditoría propone: **que esa información deje de necesitar el checklist para
aparecer**. Hoy una vigilancia frágil se cuenta dentro de la card del coche
(`HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001`) y eso es correcto — pero sólo se ve si la hoja está
abierta. Propuesta: **la salud de la vigilancia es un badge en el peek**, visible en reposo, porque
es la promesa central del producto. Una vigilancia muerta que no se ve es una promesa incumplida en
silencio.

### C · El coche está aparcado

Primer nivel: nombre del coche, «Aparcado», la dirección, la hora/el relativo, y el badge de
vigilancia. A un toque: el peek de la sesión con liberar / corregir pin / ver detalle.
Todo existe ya (`HomeVehicleCard` footer, `ParkedWatchBadge`, `home_vehicle_card_parked_at`).
**[CÓDIGO]**

### D · El coche está en ruta

El riesgo que el encargo señala —confundir el último aparcamiento con la posición actual— **ya está
cerrado** y de forma explícita: `DrivingMeta` no lleva posición (vive en `HomeViewModel.tripRender`
a tasa de fix), el chip en ruta enseña *dónde está el coche ahora* por su **propio** geocode
(`UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001`), y la sesión aparcada **no existe** mientras hay viaje
(`VehicleCard.session == null` es parte de `isDriving`). **[CÓDIGO]**

Lo que esta auditoría añade: en el peek, **el viaje vivo gana siempre**, por encima del latch del
carril. Eso ya es así (`BrowsePeek`: la rama driving outranks el latch). **No tocar.** **[CÓDIGO]**

### E · Gasolineras sin precios

Lo que hay hoy, y es ejemplar **[CÓDIGO]**:

| Situación | Qué dice |
|---|---|
| sin `fuelType` en el coche | `home_fuel_missing_fuel_type` — la lista no tiene pregunta que hacer |
| fallo de red | `home_fuel_error` — «no se pudo preguntar», nunca «no hay» |
| hay estaciones, 0 precios | las filas se pintan **sin** figura + nota (y si `didFallBack`, lo dice) |
| algunas con precio | se pintan todas; el orden puede ser por precio (`FuelLaneSort.CHEAPEST`) |
| de disco | `rememberedAtMs` → «esto es lo que había la última vez» |
| fuente sin sello | `pricesAreLive` (sólo MTS-K alemán) |
| fuente con sello | `home_fuel_reported_at` con la hora **verbatim** de la fuente |

Propuesta: **nada que arreglar aquí**. Lo único que falta es que el peek pueda citar **un** precio
cuando lo hay, con su hora, y que cuando no lo haya **no diga nada de gasolineras** — el silencio es
una respuesta válida y es la única honesta.

### F · El mapa sin resultados útiles

El mapa nunca está vacío de lo propio: tus coches, tus zonas, tu viaje y tu traza se pintan
**siempre** (nunca fueron capas). **[CÓDIGO]** Propuesta: cuando el carril activo no tiene nada,
**no se añade ni un marcador**; lo que cambia es la línea 3 del peek.

### Estados de sistema

| Estado | Hoy | Propuesta |
|---|---|---|
| Cargando | `NearbyRowsSkeleton` sólo si la lista está **vacía**; si hay datos viejos se mantienen | mantener |
| Error de red | `failed` por sujeto + banner global `WifiOff` en `App.kt` | mantener |
| Falta permiso CORE | `DetectionStory.BlockedCore`, fila de error | subirlo al peek como badge |
| Sin ubicación | pill de precisión del mapa (`UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001`) + `spotsUnasked` | mantener; el peek **no** lo repite |
| Pregunta pendiente | `AwaitingAnswer` / `PendingAsk`, máxima precedencia | subirla al peek **con su plazo** |

---

## 5 · El papel de las gasolineras (§6 del encargo)

### 5.1 Los cinco modelos, evaluados

| Modelo | Ventaja | Inconveniente | Veredicto |
|---|---|---|---|
| **1 · Sección permanente de primer nivel** | el contenido con más datos siempre a la vista | Paparcar deja de ser una app de aparcar; y sin precios válidos la sección queda muda a diario | ❌ |
| **2 · Dentro de un bloque «oportunidades» mezclado** | una sola lista, siempre con algo | rompe `showsFuelOnMap`/`showsParkingsOnMap` (un sujeto, un control) y mezcla sujetos con unidades distintas | ❌ riesgo técnico real |
| **3 · Prioridad sólo con precio válido y ahorro real** | honesto por construcción; el silencio es gratis | exige el veredicto de ahorro, que **no existe** | ✅ a medio plazo (fase 4) |
| **4 · Acceso directo + presentación centrada en el coche** | coste de implementación casi nulo; respeta la identidad | no aprovecha el dato cuando es bueno | ✅ **ahora** |
| **5 · Precios + historial como experiencia de ahorro** | es la visión de producto más fuerte | depende de 3 y de una serie de 30 días que hoy es Plus | ✅ a largo plazo |

### 5.2 Recomendación

**4 ahora, 3 después, 5 como horizonte.** Es decir: la pestaña se queda donde está (y **gana peso**
en la fila, §6.2), y el peek puede citar **un** precio cuando se cumplan las tres condiciones:

1. hay precio para el producto del coche (`pricedFuelType`, que ya existe y ya resuelve híbridos),
2. la estación está dentro del radio que ya se pide,
3. el precio es **suficientemente reciente** según su propia fuente (`reportedAt` /
   `oldestPriceAtMs` / `pricesAreLive`).

Si falla cualquiera de las tres, **el peek no habla de gasolineras**. No hay estado degradado.

### 5.3 Ahorro y comparación — qué se puede afirmar y qué no

Vocabulario obligatorio, y no es cosmético: son **tres cosas distintas** y la app no puede
presentarlas como equivalentes.

| Concepto | Qué es | Se puede afirmar hoy? |
|---|---|---|
| **Diferencia por litro** | `precio(A) − precio(B)`, dos precios reales | ✅ sí, es aritmética sobre datos de la fuente |
| **Ahorro potencial de un repostaje** | diferencia × litros **que el usuario diga** | ⚠️ sólo con un volumen **declarado** o el depósito conocido; hoy no se pide ninguno |
| **Ahorro conseguido** | lo que realmente se ahorró al repostar allí | ❌ **imposible**: la app no sabe dónde ni cuánto repostaste |

Reglas para cuando esto se implemente:
- Nunca comparar precios con **fechas** distintas sin decir ambas.
- Nunca comparar productos distintos (el 95 de una con el gasóleo de otra).
- Un «ahorro potencial» lleva **siempre** el volumen que lo genera escrito al lado.
- Un umbral de significancia: por debajo de él **no hay noticia**. La cifra concreta la decide el
  user; esta auditoría no la inventa.

---

## 6 · Conectar la home con gastos y estadísticas (§7 del encargo)

### 6.1 La pregunta central

> ¿Debe la home mostrar un resumen del beneficio que da Paparcar, o eso desvía del coche?

**Respuesta: sí, pero una línea y sólo cuando hay un hecho que contar — y no en la home «de
siempre», sino en la del momento en que ese hecho existe.**

Razonamiento: el eje de monetización del proyecto es *«free = tú preguntas y la app responde; Plus =
la app te avisa y recuerda por ti»*. Un resumen permanente en la home es **memoria**, es decir, lo
que se vende. Un **hecho puntual recién cerrado** (el mes terminó; esta sesión liberó una plaza) es
noticia, y la noticia es del AHORA, que es lo que la home es.

### 6.2 Reparto por distancia

| Información | Dónde | Condición |
|---|---|---|
| «Tu aparcamiento liberó una plaza» | **home**, al cerrar la sesión | `spotsReleased` incrementó en esta sesión |
| Recap del mes cerrado, 1 línea | **home**, primeros días del mes | `closedMonth != null`; Plus (`MONTH_RECAP`) — el free ve la línea bloqueada, nunca oculta (gating soft) |
| Gasto del mes + ▲▼ vs anterior | **a un toque** (Vehículos) | `costEstimate != null` **y** `previous != null` |
| Tiempo buscando plaza | **a un toque** | `searchTime != null`, con su cobertura |
| % de detección automática | **sólo Stats** | `detectionKnownCount` por encima de su umbral |
| Lugares habituales | **sólo Stats** | `places` no vacío |
| Serie de 30 días de un precio | **sólo Stats** | Plus |
| Historial de ubicaciones | **Vehículos** | siempre (7 días en free) |

### 6.3 La regla que no se puede romper

**Falta de datos ≠ cero.** Ya es así en el dominio (cada nullable de `PeriodStats` es `null` por
debajo de su umbral) y la home tiene que heredarlo: si no hay dato, **la línea no existe**. No hay
«0 €» ni «—».

---

## 7 · Tres direcciones de diseño

Desarrolladas en detalle, con su estructura, ventajas, riesgos y coste, en
[`comparison.md`](../../design-prototypes/paparcar-home/comparison.md). Prototipos navegables:

- **A · Vehicle-first** → `vehicle-first.html`
- **B · Smart mobility** → `smart-mobility.html`
- **C · Adaptive home** → `adaptive-home.html`

La dirección ganadora y su justificación están en §8 de este documento y en `comparison.md`.

---

## 8 · Dirección recomendada

**C · Adaptive home**, construida sobre la estructura de A.

### Por qué

1. **Es la única que responde al condicionante real del producto.** Con <20 usuarios el contenido
   disponible varía radicalmente según el momento y el sitio. A es sólida pero desperdicia el dato
   cuando lo hay; B promete un entorno rico que a menudo no existe. C es la única que trata «hay
   datos» y «no hay datos» como dos estados **de primera clase** en vez de uno y su degradación.
2. **El código ya está construido así.** `DetectionStory` es precedencia fija y pura.
   `DetectionUiState` tiene 8 estados. `FuelLaneSort`, `ParkedWatchBadge`, `HomeFuelSlice` ya
   distinguen entre no preguntado / vacío / fallo. C no añade un paradigma: **le da pantalla al que
   ya existe**. Es la propuesta de menor riesgo arquitectónico, no la de más.
3. **La adaptación es acotada y por eso predecible.** Lo que cambia es **el contenido de dos
   líneas**: la línea de estado (que ya adapta hoy) y una línea de oportunidad (que puede estar o no
   estar). La estructura —peek, mapa, pestañas, carril, nav— **no se mueve nunca**. Ésa es la
   diferencia entre una home adaptativa y una home impredecible.
4. **Escala con la comunidad sin rediseño.** Cuando haya plazas, la línea de oportunidad las dirá y
   el default podrá volver a `SPOTS` cambiando una regla, no una pantalla.

### Qué se lleva de cada una

- de **A**: el peek de 3 líneas y el coche como sujeto permanente del primer vistazo;
- de **B**: que la oportunidad del entorno tenga derecho a una línea del primer nivel;
- de **C**: que **qué** se dice en esa línea lo decida una precedencia pura y testeable, como ya
  hace `resolveDetectionStory`.

### Qué NO se lleva

- de **B**: el carril «oportunidades» mezclado. Rompe la regla de un sujeto = un control del mapa.
- de **C** en su versión pura: cualquier reordenación de bloques. La adaptación es **de contenido**,
  jamás de layout.

---

## 9 · Decisiones descartadas (§15.9 del encargo)

| Descartado | Por qué |
|---|---|
| Quitar las pestañas de sujeto | resuelven un problema medido: la `y` de un sujeto dependía de la lista del de arriba (hasta ~2.100 dp) |
| Persistir el último carril | la home es el AHORA; un arranque en frío en «gasolineras» de hace 3 días es desorientación |
| Carril único «cerca de ti» mezclando los 3 sujetos | rompe `showsFuelOnMap`/`showsParkingsOnMap` (un sujeto, un control) y mezcla unidades |
| Dashboard de estadísticas en la home | hay datos para llenarlo, y eso es exactamente la razón de no hacerlo |
| Cuarto tab | la regla editorial de 3 destinos funciona; un 4º es post-lanzamiento |
| Esconder plazas mientras no haya comunidad | es la propuesta diferencial; se **reordena**, no se esconde |
| Mostrar «N usuarios cerca» o actividad estimada | sería un dato inventado |
| Repintar el logo o la marca | cerrado el 19-10 (`BRAND-THE-MARK-GAINS-ITS-WORDMARK-001`); fuera de alcance |
| Cambiar la paleta a los verdes-neutros de referencia | medido: la paleta viva es **azul-navy** con razón documentada. Ver `design-system.md` §2 |
| Tocar la detección | el encargo lo prohíbe y la doctrina también; **ninguna** propuesta la roza |

---

## 10 · Hipótesis que necesitan validación con usuarios

| # | Hipótesis | Cómo se falsifica |
|---|---|---|
| H1 | El usuario no identifica el estado de su coche en el primer vistazo actual | Test de 5 s sobre captura en `PEEK`: «¿dónde está tu coche?» |
| H2 | El checklist de 6 filas encima de todo se lee como un tutorial, no como la app | A/B con checklist plegado vs desplegado: tiempo hasta el primer tap en el mapa |
| H3 | «En esta zona» + `vehicles_header` no aportan separación que las pestañas no den ya | quitarlas en una build y medir errores de orientación |
| H4 | Una línea de oportunidad en el peek se lee como información, no como publicidad | preguntar qué creen que es esa línea |
| H5 | Reponderar las pestañas (plazas más ancha) comunica la jerarquía sin ocultar nada | ¿siguen encontrando gasolineras? |
| H6 | El contador `0` en la pestaña de plazas se lee como «la app no funciona» | entrevista sobre captura con 0 |
| H7 | Un precio citado en el peek se percibe útil y no intrusivo | tasa de tap + percepción declarada |
| H8 | «Tu aparcamiento liberó una plaza» se entiende como contribución, no como pérdida | comprensión literal de la frase |
| H9 | La adaptación de la línea 3 **no** se percibe como inestabilidad | sesión repetida en 2 días: ¿«se mueve»? |

### Plan mínimo de validación

**6–8 participantes** (mezcla: 3 que ya usan la app, 4 nuevos). 25 min. Sobre prototipo HTML, no
sobre build.

Tareas:
1. «Acabas de abrir la app. Sin tocar nada, dime qué sabes de tu coche.» → H1, H6
2. «Quieres saber dónde dejaste el coche.» → nº de toques
3. «Buscas gasolina cerca.» → nº de toques, H5
4. «No hay plazas libres aquí. ¿Qué harías?» → §4A
5. «¿Qué crees que hace esta línea?» (señalando la de oportunidad) → H4, H7
6. SUS corto + «¿qué te sobra de esta pantalla?» → H2, H3

Qué observar: toques hasta completar, dónde mira primero (declarado), qué interpreta mal, qué
nombra sin que se lo pregunten.

Indicadores de que la home nueva se entiende antes:
- tarea 1 respondida **sin** abrir la hoja,
- tarea 3 en ≤2 toques (hoy son 4),
- nadie describe la tarea 4 como «la app está vacía».

⛔ **No** se mide conversión ni retención: no hay base, no hay cohortes, y 20 usuarios no dan
significancia. Esto es comprensión, no negocio.

---

## 11 · Limitaciones de esta auditoría

1. **Ninguna medición en device.** Las alturas son aritmética sobre constantes del código. Un
   `TextStyle` con `lineHeight` y un `fontScale` distinto de 1 cambian los totales. Las cifras de §1
   son **órdenes de magnitud**, no medidas.
2. **Cero datos de uso.** No hay analítica de producto en el repo (Crashlytics es estabilidad).
   Todo lo que diga «el usuario hace» es hipótesis.
3. **No se ha ejecutado la app.** La reconstrucción es por lectura de código.
4. **iOS no auditado.** El carril BT es estructuralmente imposible allí
   (`IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001`) y eso afecta a lo que una home puede prometer. Toda
   propuesta de este documento es **commonMain**, así que no introduce divergencia — pero tampoco la
   resuelve.
5. **Deriva documental detectada, no corregida:** CLAUDE.md dice `statNumber(25sp)` y `counter`; el
   código tiene `statNumber` a **20sp** y `counter` a **21sp**. Es un detalle de documentación y no
   se toca en esta fase (sería tocar producción); se anota aquí para que no se pierda.
6. **Los prototipos usan datos de demostración**, marcados visualmente como tales. Ninguna cifra
   proviene de la app.
7. **La fidelidad del prototipo se auditó en la 2.ª vuelta (27-10)** y se encontró una divergencia
   que maquillaba el diseño: el CSS aplicaba `tabular-nums`, que CMP 1.12 **no** puede aplicar.
   Corregida. La tabla completa de qué se puede y qué no se puede juzgar en el HTML está en
   [`b-decisions.md`](../../design-prototypes/paparcar-home/b-decisions.md) §0.
   ⚠️ Lo que sigue sin modelarse es **`fontScale`**, y es el caso que hay que ver en device antes de
   implementar un peek más alto.
