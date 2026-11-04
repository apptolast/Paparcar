# Spec de interacción — **B · Smart mobility** (la dirección elegida)

> Especificación de la dirección que el user eligió el 27-10, con las 18 decisiones de las cinco
> vueltas de revisión aplicadas. Incluye el plan de implementación por fases (§9), también en [**plan.html**](plan.html).
> Prototipo de referencia: [`smart-mobility.html`](smart-mobility.html) ·
> los 44 casos: [`peek-cases.html`](peek-cases.html) · el porqué de cada decisión:
> [`b-decisions.md`](b-decisions.md)
>
> **Nada de esto se ha implementado.** Es la fase de diseño.
>
> ⚠️ **Esta spec era de C hasta el 29-10.** El user eligió B el 27-10 y el plan siguió siendo el de
> C —peek de cuatro zonas, `ResolveHomeNewsUseCase`, la «zona ③»— durante dos días. La precedencia de
> C no se ha perdido: vive en [`comparison.md`](comparison.md) §2 y en `adaptive-home.html`, que
> sigue navegable. Lo que no puede haber son dos planes.

---

## 1 · El invariante

> **La cabecera describe lo que se está LEYENDO, y nunca habla de algo que no esté debajo.**

De ahí salen los **tres modos** de la cabecera, y no hay un cuarto:

| Modo | Cuándo | Qué lleva |
|---|---|---|
| **`mine`** | el scroll está arriba, o la hoja está plegada | tu coche en 2 líneas + «AQUÍ Y AHORA» |
| **`lane`** | el scroll ha entrado en el carril | tu coche **encogido** a 1 línea + **las pestañas** |
| **`detail`** | hay un sujeto abierto | la ficha de ese sujeto, en 2 líneas |

Es **reversible y continuo**: al volver arriba vuelve tu coche. Eso es lo que lo hace predecible —
no es un modo en el que se entra sin saber salir, es una consecuencia del scroll.

⛔ **El coche nunca desaparece de la cabecera**, ni en `lane`: se encoge. Si dejara de nombrarlo, el
usuario perdería de qué pantalla es esto.

---

## 2 · El primer vistazo: «AQUÍ Y AHORA»

### 2.1 Qué entra

**Un notable por sujeto como máximo, en orden FIJO** (plaza · parking · gasolinera), **con un umbral
por sujeto**. Máximo tres tarjetas.

| Sujeto | Umbral | Por qué |
|---|---|---|
| **Plaza** | que exista | es el producto, y **caduca**. Una plaza libre es noticia por estar ahí |
| **Parking** | que haya uno cerca | se ofrece también con «Gálibo sin dato»: ⛔ no saber si cabe **no es** saber que no cabe |
| **Gasolinera** | precio para **tu** producto **y** hora admisible | ⛔ una cifra que no puede decir cuándo se midió no se afirma |

⛔ **Orden fijo, no por «cuán notable es».** Reordenar el primer vistazo según los datos del momento
es lo que hace impredecible una home: el usuario aprende *dónde* está cada cosa, no *cuándo*. Y es el
mismo orden de las pestañas, así que las dos superficies no pueden contradecirse.

⛔ **Si no entra nada, la sección NO EXISTE**: ni título, ni hueco, ni caja diciendo que no hay nada.
Lo que queda en su sitio es la pista de que la hoja sigue (*«Desliza para ver qué hay en esta
zona»*), que es lo que de verdad le falta ahí a un usuario nuevo.

### 2.2 El umbral de frescura es **por FUENTE**

No hay una constante global, y no puede haberla: **Minetur** (ES) sella la respuesta · **Francia** e
**Italia** sellan cada precio (`oldestPriceAtMs` se lee como **cota**, nunca como «la lista es de
esta hora») · **Alemania** (MTS-K) no sella nada y declara `pricesAreLive` — garantía legal de
tiempo real, así que un umbral global **enmudecería la fuente más fiable** · **OSM** nunca da
precios · el **disco** conserva el `reportedAtMs` de cada precio junto a su `rememberedAtMs`.

### 2.3 La tarjeta de gasolinera prefiere el **CAMBIO** al **NIVEL**

Con movimiento: «**Bajó hoy cerca de ti** · −4 cts · Repsol Embajadores · 1,489 €/L · a 300 m».
Sin movimiento: «**Gasolina 95 más barata** · 1,419 €/L».

Un precio que lleva meses igual es un hecho; que haya bajado 4 céntimos **hoy** es noticia. Mismo
hueco, mismo orden, mejor contenido — **no** una cuarta tarjeta ni una sección de «últimas
actualizaciones». **Y las subidas también**: contar sólo las bajadas convierte la app en un cupón.

⛔ **«Dos filas que difieren no es un precio que cambia»** (`EvaluateDailyPriceMoveUseCase`): hace
falta que **las dos muestras lleven sello de la fuente y los sellos difieran**, más el umbral de
**≥0,5 c/l**. Si no, el veredicto es «sin noticias», que **no es** «sin cambios».
⛔ Y **un movimiento no se salta el umbral de frescura**: con precio viejo, no hay tarjeta.

### 2.4 Cada tarjeta tiene **DOS destinos**

```
┌──────────────────────────────┐
│ [⛽] GASOLINA 95 MÁS BARATA   │
│ 1,419 €/L                    │  ← el CUERPO abre ESE item: cámara al punto
│ Ballenoil Méndez             │    + ficha de detalle (idéntico a su fila)
│ Calle de Méndez Álvaro·1,4 km│
│ dato de las 09:00            │
│ [MÁS BARATA]                 │  ← el mismo chip que lleva su fila
├──────────────────────────────┤
│ Ver las 3                  › │  ← el PIE abre SU LISTA
└──────────────────────────────┘
```

Son **dos controles hermanos**, no un botón dentro de otro: en Compose sería un `clickable` dentro
de otro, con el foco imposible de recorrer.

**Copy del pie:** «Ver las 3» — la tarjeta ya dice el sujeto arriba; lo que el usuario no sabe es
cuántas hay. Con `aria-label` que **sí** lleva el sujeto («Ver las 3 gasolineras»), porque «Ver las
3» a solas no es un nombre accesible. Con una: «Ver la única», nunca «Ver las 1».

⛔ **El sujeto de la tarjeta lo elige la misma función pura que pone el chip en la lista**, no un
índice escrito a mano. Es lo que impide que el primer nivel y la lista nombren cosas distintas.

---

## 3 · Las acciones del coche

Una principal, una quieta, nunca tres. Resueltas por el mismo estado:

| Estado | Principal | Quieta |
|---|---|---|
| falta permiso CORE | **Dar permiso** | Por qué |
| pregunta abierta | **Sí, he aparcado** | Sigo conduciendo |
| vigilancia en pausa | **Reactivar vigilancia** | Ver detalle |
| en ruta | Ver el viaje *(secundaria)* | Dejar de seguir |
| sin aparcamiento marcado | **Marcar aparcamiento** | Voy conduciendo |
| aparcado (normal) | Llévame al coche *(secundaria)* | Me voy |
| sin vehículo registrado | **Añadir mi coche** | — |

⛔ **Verde sólo donde hay algo que resolver.** En el estado normal la principal es **secundaria**
(borde verde), no rellena: no hay urgencia que justifique la tinta más cara de la app.

---

## 4 · La gramática de DOS líneas, igual para todo sujeto

> línea 1 = **NOMBRE** · su **calificador** · línea 2 = **DÓNDE** · a **CUÁNTO** de ti
> y la **cifra propia del sujeto al TRAILING**, nunca en una tercera línea.

| Sujeto | Línea 1 | Línea 2 | Trailing |
|---|---|---|---|
| Tu coche | `Ford Focus · Aparcado hace 2 h` | `Calle de Embajadores, 118 · a 180 m` | aviso de vigilancia, **si lo hay** |
| **Plaza** (sin nombre) | `Plaza libre · Mediano` | `Calle del Olivar, 34 · a 120 m` | `4 min` *(color de frescura)* |
| **Parking** | `Parking Embajadores · Cabe tu coche` | `Parking público · a 240 m` | `2,10 €/h` + chip |
| **Gasolinera** | `Ballenoil Méndez · Gasolina 95` | `Calle de Méndez Álvaro · a 1,4 km` | `1,419 €/L` + chip |

Tres reglas que no se negocian:

1. ⛔ **La dirección aparece en UNA línea, nunca en las dos.** Si el sujeto no tiene nombre propio
   —una plaza no lo tiene— la línea 1 es su **TIPO + calificador**. Ahí estaba la repetición.
2. ⛔ **La distancia no trunca.** Lleva ancho propio al final de su línea; lo que cede es la calle.
3. ⛔ **Sin fix la distancia no existe.** No se escribe un «—» ni un 0.

Debajo, **una** línea de procedencia, y sólo cuando hay fuente que citar.

---

## 5 · El chip de «la que interesa»

**MÁS BARATA** (verde de marca: el precio es LA respuesta) o **MÁS CERCANA** (neutro: es
orientación). Par tonal, caja y esquina por defecto — las mismas que `SpotAgeIndicator`.

Las **seis negativas**, que son lo que lo hace honesto:

| Caso | Qué hace |
|---|---|
| un solo candidato | nada — con una gasolinera no hay «la más barata» |
| empate al mínimo | **se comparte** — los surtidores copian el precio a la milésima |
| empate **universal** | **se calla** — no distingue nada |
| dos monedas | sin «más barata»; la más cercana aguanta |
| distancias empatadas | sin «más cercana» |
| es las dos cosas | **un** chip: una fila es un nombre, no un marcador |

Y el orden: **sólo la más barata sube.** La más cercana no reordena — muy a menudo no tiene precio, y
subirla dejaría la lista encabezada por una fila sin cifra.

**Lo llevan gasolineras y parkings.** ⛔ **Una PLAZA nunca:** una gasolinera no se agota, una plaza
sí; señalar «la mejor» entre cuatro manda a todos a la misma, y la app **ya cuenta** quién va de
camino (`Spot.enRouteCount`) — sería una carrera que habríamos empezado nosotros.

---

## 6 · El aviso de vigilancia

> ⛔ **Sólo existe cuando algo no se cumple.**

| Situación | Qué lleva la cabecera |
|---|---|
| sana (asistida o Bluetooth) | **nada**. El glifo y el color de identidad ya dicen el método; el estado está escrito en palabras |
| frágil · en pausa · falta permiso | un chip con su texto, **en los tres modos**, y es un **botón**: abre la fila que lo explica y lo arregla |

Dos doctrinas lo deciden: `UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001` (el **método** nunca se imprime
como texto) y que la **salud** no es un chip sino una **fila de acción** con causa + consecuencia +
remedio. El chip es su **puerta**, no su sustituto.

---

## 7 · Qué se ve en cada ancla

| Ancla | Qué | Nota |
|---|---|---|
| **PEEK** (~300 dp) | coche en 2 líneas + aviso si lo hay + «AQUÍ Y AHORA» con sus tarjetas | ⚠️ **~210 dp más que hoy**; el mapa queda en ~48 % |
| **HALF** | + la fila de progreso, la salud del sistema y el principio de «TU COCHE» | es el ancla del **auto-abrir** |
| **EXPANDED** (~58 %) | tira de coches completa, pestañas fijadas y el principio del carril | el cromo flotante se desvanece y la nav se oculta |
| **FULL** | el carril entero | sólo por arrastre; si todo cabe, el tope es la altura del contenido |

⚠️ **El peek de 300 dp es la decisión que más conviene ver en device.** Si el mapa resulta
insuficiente, el ajuste es, en este orden: quitar la línea de meta de las tarjetas (−16 dp),
comprimir las acciones a 34 dp (−6 dp), y **sólo en último lugar** reducir el carrusel a dos
tarjetas visibles. ⛔ **No** quitar «AQUÍ Y AHORA»: es B.

### 7.1 Al plegar: **la hoja vuelve arriba** (`reset`)

Plegar es un gesto deliberado de «ya está». Devolverla a su inicio hace que el peek **nunca** enseñe
un sujeto que al desplegar no esté en pantalla, y recupera «AQUÍ Y AHORA», que es el valor del primer
vistazo.

⚠️ No contradice `HOME-BROWSE-SURVIVES-THE-DETAIL-SHEET-001`: ésa es sobre sobrevivir a una **hoja de
detalle encima**, que el usuario no pidió. Plegar sí lo pidió.

### 7.2 El auto-abrir al arrancar

⛔ **El disparador es «hay NOVEDAD», no «hay contenido».** Con «hay algo que mostrar» se abriría casi
siempre —hay parkings en todas partes— y taparía el mapa cada vez.

| | |
|---|---|
| **Cuándo** | una vez por arranque en frío, sólo con novedad **sin ver** |
| **Hasta dónde** | ⛔ **HALF**, nunca `EXPANDED`. El mapa conserva su mitad |
| **Nunca si…** | conduces · pregunta con plazo · vigilancia en pausa · falta permiso · nada que mostrar |
| **Salida** | el primer arrastre hacia abajo la cierra, y no vuelve en esa sesión |

Las cuatro negativas tienen un motivo común: **esas cosas ya mandan en la cabecera**, y abrir la hoja
encima las esconde tras su propio contenido.

---

## 8 · ⛔ El disparador de las fetchs: **el carrusel no pide nada**

Era el último riesgo ALTO de B, y **al medirlo se cayó**.

### 8.1 La premisa había caducado

La regla documentada era *«nada se pide hasta entrar en el carril, porque una provincia de
gasolineras descargada para una lista que nadie abrió es datos del usuario gastados en nuestra
comodidad»*. Sigue siendo buena — **pero ya no describe la app**:

> **`FuelPriceWatchWorker` es PERIÓDICO Y DIARIO**, y `RunFuelPriceWatchUseCase` muestrea cada día
> las gasolineras más cercanas a **donde está el conductor** (carril **free**, radio
> `AROUND_FOLLOWED_RADIUS_M = 1.000 m`, tope `NEARBY_SAMPLED_MAX`) y **las guarda en Room** — sin que
> nadie entre en ningún carril. Eso entró con `FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001`.

Y los dos complementos tienen **cache de disco por ZONA**, no un blob global:
`DiskBackedPublicParkingsSource` / `RoomPublicParkingsCache` y
`DiskBackedFuelStationsSource` / `RoomFuelStationsCache`, con `rememberedAtMs`
(`CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001`).

### 8.2 ¿Y por qué no pedirlo todo al arrancar? Porque está medido

Antes de decidir conviene saber **qué cuesta** una consulta de cada complemento. Las dos cifras
están en el propio repo:

| Complemento | Lo que cuesta una consulta | Dónde está medido |
|---|---|---|
| **Gasolineras** (ES) | se pide **por PROVINCIA** — *«el país entero son 12 MB»* | KDoc de `MineturStationsSource` |
| **Parkings** | Overpass: **1,4 s** en caliente, **4,7–7,7 s** en frío, y **hasta 40 s** contando la espera de cuota y el recorrido de espejos | `HomeViewModel` (la nota de `PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001`) |

Así que «traerlo todo de primera» significa, en cada arranque en frío: **descargar una provincia
entera de gasolineras** y **esperar entre 1 y 40 segundos** por los parkings — para rellenar dos
tarjetas que el usuario puede no mirar. Eso es exactamente lo que la regla original evitaba, y sigue
teniendo razón.

### 8.3 La decisión: **lo recordado AL INSTANTE, y además se pide en segundo plano**

No hay que elegir entre las dos cosas, y la app **ya tiene el patrón**:

> ⛔ **La tarjeta se pinta con lo RECORDADO, sin esperar a nadie. Y si hace falta, se pide a la vez y
> la tarjeta se actualiza cuando llega la respuesta.**

Es literalmente lo que `HomeViewModel` hace hoy con los parkings
(`PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001`): *«pintando N parkings recordados
mientras pregunta»*, con la nota de «sin conexión» **suprimida mientras carga** — porque en ese
momento sí se puede preguntar y se está preguntando. Si la petición falla, la nota aparece y la frase
pasa a ser verdad.

Lo único que cambia es **cuándo** se dispara ese patrón: al arrancar, no al entrar en el carril.

⚠️ **Con dos condiciones, y la segunda no la había visto:**

1. Se pide **sólo si no hay nada recordado para esta zona**, o si lo recordado pasó su ventana.
   ⛔ No en cada arranque: una provincia de gasolineras son megas. El disco guarda **30 días**, y un
   parking de OSM apenas cambia en 30 días.
2. ⛔ **La petición del carrusel va DETRÁS de cualquiera que el usuario esté esperando.** Y esto es
   un requisito, no una optimización: `OverpassQuota` permite **UNA** consulta a la vez en toda la
   app (`CONCURRENT_ASKS = 1`) y hay **seis** consumidores de Overpass. Añadir el carrusel es
   añadir un **séptimo** a una cola de uno — si se cuela delante, retrasa el carril que el usuario
   sí está mirando.

   Por qué la cola es de uno, medido el 14-09 contra el endpoint: cuatro consultas **a la vez** dan
   un 429 y **tres 504 a 14,1 s**, para una consulta que sola contesta en **1,4 s**. Y el límite es
   **por IP**, que en móvil se comparte con miles de desconocidos. Pedir más en paralelo no acelera:
   destruye.

El disparador documentado **sigue intacto**: entrar en un carril es lo que pide datos frescos
*siempre*. Esto sólo añade una petición condicionada al arranque, y nunca bloquea la pantalla.

| Sujeto | De dónde sale la tarjeta | Qué falta |
|---|---|---|
| **Plazas** | suscripción viva, siempre | nada |
| **Parkings** | `GetNearbyPublicParkingsUseCase.remembered(lat, lon)` — **ya existe**, y `HomeViewModel` **ya lo llama** | sólo **cuándo**: al arrancar, no al entrar en el carril |
| **Gasolineras** | el muestreo diario ya tiene el dato en Room | ⚠️ **falta la puerta de lectura**: no hay `remembered()` en el lado del combustible |

Y el KDoc de `DiskBackedPublicParkingsSource` dice que `remembered` existe **justo para esto**:
*«expone el disco guardado a los llamantes que quieren algo en pantalla mientras preguntan»*.

### 8.4 Lo que esto cambia

- **El riesgo pasa de ALTO a BAJO.** No hay que tocar el disparador, ni gastar un byte más.
- **La tarea 4.1 del plan viejo desaparece** (era «cambiar el disparador de la fetch»).
- **Coste honesto y asumido, ya pequeño:** en el **primer** arranque en una zona nueva la tarjeta
  del complemento tarda lo que tarde su petición (1,4 s los parkings en caliente). Hasta entonces el
  carrusel enseña lo que tenga — y nunca un hueco: si no hay nada, la sección no existe.
- ⛔ Y **lo recordado nunca se disfraza de fresco**: `rememberedAtMs` existe exactamente para eso, y
  la tarjeta lleva su línea de procedencia.

---

## 9 · Plan de implementación

Cada tarea: problema que resuelve · ficheros · riesgo · cómo se comprueba.
⛔ **Ni una toca la detección.**

### Fase 1 · Claridad y jerarquía — presentación pura, ni un campo nuevo

| # | Tarea | Resuelve | Ficheros | Riesgo | Comprobación |
|---|---|---|---|---|---|
| 1.1 | **Gramática de 2 líneas** del coche y de los tres sujetos | la 3.ª línea repetía la dirección (§4) | `BrowsePeek.kt`, `FuelStationPeek.kt`, `PublicParkingPeek.kt`, `ParkingPeek.kt`, `HomeSpotRows.kt`, `HomeFuelStationRows.kt`, `HomeParkingRow.kt` | **medio** — barrido de consumidores: hoy son filas independientes y pasan a compartir esqueleto | galería de estados + los 44 casos; que la distancia no trunque nunca |
| 1.2 | **Peek de B**: coche + aviso + «AQUÍ Y AHORA» | el estado del coche no se ve sin arrastrar | `BrowsePeek.kt`, `PapSheet.kt` | **medio** — cambia la altura del peek, y de ella deriva toda la geometría de anclas | `computeSheetPositioning` ya es testeable; **device** para §7 |
| 1.3 | **Tarjeta de dos destinos** + «Ver las N» | la tarjeta sólo llevaba a un sitio | nuevo `PapOpportunityCard` en `ui/components/` | **bajo** | foco recorrible entre los dos botones; `aria`/`contentDescription` con el sujeto |
| 1.4 | **Pestañas reponderadas** 1,25 : 1 : 1 | la jerarquía declarada no se comunica | `HomeNearbyLensRow.kt` | **bajo** | que los 9 locales no truncen |
| 1.5 | **Checklist → fila de progreso** + cabeceras de provincia | 6 filas encima de todo; «En esta zona» sobra | nuevo `PapProgressRow`, `HomeSheetContent.kt` | **bajo** — `FirstStepsCard` se conserva íntegra, sólo cambia la puerta | que ningún paso quede inalcanzable |
| 1.6 | **Alternativa útil** en el vacío de plazas | §5A de la auditoría | `HomeSheetContent.kt` (`spotsLane`) | **bajo** — `publicParkingsShown` ya está | que no aparezca si no hay parkings |
| 1.7 | **El aviso de vigilancia sólo cuando avisa** (§6) | un chip verde permanente que además etiquetaba el tier | `BrowsePeek.kt` + el resolver | **bajo** | los 3 modos × los 3 avisos, y que sano **no** imprima nada |
| 1.8 | **Standout en parkings** | decidido el 28-10 | ⚠️ renombrar `FuelStandouts` → sirve a dos sujetos; `HomeParkingRow.kt` | **bajo** | las 6 negativas, con la tarifa ausente como caso principal |
| 1.9 | Retirar `mine_lane_split` y `vehicles_header` | ~96 dp de cabeceras decorativas | `HomeSheetContent.kt` | **bajo**, pero **revierte dos decisiones tomadas en device** ⚠️ | **A/B en mano**. Si vuelven a leerse como un bloque, se revierte |

⚠️ **1.9 va al final de la fase y con A/B en device**, no por convicción de este documento.

### Fase 2 · Estados y contenido contextual

| # | Tarea | Resuelve | Ficheros | Riesgo | Comprobación |
|---|---|---|---|---|---|
| 2.1 | **`ResolveNearbyNotablesUseCase`** — un notable por sujeto, orden fijo, umbral por sujeto | §2.1; hoy nada decide esto | nuevo `domain/usecase/home/` + test | **bajo** — puro | test por umbral **y** por «sin notables no hay sección»; fakes |
| 2.2 | **Cabecera por scroll** (3 modos) | el sheet hablaba del carril sin que se viera | `HomeScreen.kt`, `HomeBottomSheet.kt`, `BrowsePeek.kt` | **MEDIO-ALTO** ⚠️ — hay que mover las pestañas a la cabecera **sin que el alto salte** | ver §9.1 |
| 2.3 | **Lo recordado al instante + petición condicionada** (§8.3) | el carrusel vacío en frío | `HomeViewModel`; ⚠️ **nuevo `remembered()` en el lado del combustible** | **bajo** — reusa el patrón que los parkings ya tienen | que la tarjeta aparezca **sin esperar**; que sólo pida si no hay nada recordado de esta zona; que `rememberedAtMs` llegue a la tarjeta y no se disfrace de fresco |
| 2.4 | **El CAMBIO de precio en la tarjeta** (§2.3) | 30 días de serie que no se leen en ninguna pantalla | `HomeViewModel` lee `nearbyMoves`; `HomeFuelSlice` | **medio** | 🟢 **cierra `FUEL-A-RECORDED-SERIES-MUST-BE-READABLE-001`** |
| 2.5 | **Auto-abrir con novedad** (§7.2) | idea del user | `HomeScreen.kt` + ⚠️ **marca de «visto» persistida** (`AppPreferences`) | **medio** — es el único campo nuevo | las 5 negativas; que no reabra dos veces la misma noticia |
| 2.6 | **`latch = reset`** al plegar | §7.1 | `nextLaneHeaderLatch` y su call site | **bajo** | volver a abrir cae arriba |
| 2.7 | Strings nuevos en los **9 locales** | — | `composeResources/values*/strings.xml` | **bajo**, obligatorio en la misma tarea | `LocaleParityGuardrailTest` |

### Fase 3 · Sistema de diseño

| # | Tarea | Resuelve | Riesgo | Comprobación |
|---|---|---|---|---|
| 3.1 | `PapPeekSubject` · `PapOpportunityCard` · `PapProgressRow` como componentes | tres maquetaciones a mano | **bajo** | galería + previews |
| 3.2 | `onPrimary` claro → `PapOnGreenContainerLight` | **3,43:1 medido**, bajo el suelo propio del proyecto | **medio** ⚠️ | **device primero**: el verde ya se revocó una vez en mano |
| 3.3 | Verificar `prefers-reduced-motion` en los pulsos | escrito, no verificado | **bajo** | con la opción del sistema activada |
| 3.4 | **Verificar el peek a `fontScale` 1,3** | la altura reservada tiene que ser en LÍNEAS, no en dp | **medio** | device con fuente grande — ver §7 |

### Fase 4 · Valor de la información

| # | Tarea | Resuelve | Riesgo | Comprobación |
|---|---|---|---|---|
| 4.1 | **Veredicto de comparación** de precios | «X está 0,07 €/l por debajo, a 600 m» | **medio** | test con fechas y productos distintos → **no debe comparar** |
| 4.2 | **«Ahorro potencial»** con volumen declarado | el vocabulario de la auditoría §5.3 | **medio** | que la cifra **nunca** salga sin el volumen escrito al lado |
| 4.3 | **Recap del mes cerrado**, 1 línea | conectar la home con lo que ya se calcula | **medio** — es memoria, y la memoria es Plus | gating **soft**: bloqueada, nunca oculta |
| 4.4 | **Umbral de frescura por fuente** (§2.2) | hoy no existe | **medio** | uno por fuente, con su nombre y su motivo |

### 9.1 ⚠️ El único punto que puede no salir tan limpio como el prototipo

**Mover las pestañas a la cabecera cambia el alto del peek**, y de ese alto depende toda la geometría
de anclas (`computeSheetPositioning`). En el prototipo lo resuelvo dejando las pestañas del cuerpo en
su sitio pero **invisibles**, para que el alto del cuerpo no cambie y nada salte. En Compose hay que
hacer lo mismo —reservar su hueco— o aceptar un desplazamiento al cruzar el umbral.

### Lo que NO entra en ninguna fase

- Tocar la detección.
- El carril «oportunidades» **mezclado** (un sujeto = un control del mapa).
- Persistir el carril activo.
- Un cuarto tab · un dashboard de estadísticas en la home.
- Repintar la marca · cambiar la familia de neutros (`design-system.md` §2).
- Una sección de «últimas actualizaciones» (`b-decisions.md` §14.3).
- «La mejor plaza» (§5).

---

## 10 · Cómo se sabe si ha funcionado

Antes de nada: el plan de validación con usuarios de la auditoría §10. **Tres indicadores**, y los
tres son de comprensión, no de negocio:

1. «¿Dónde está tu coche?» se responde **sin abrir la hoja**.
2. Llegar a gasolineras baja de **4 toques a 2**.
3. Con 0 plazas cerca, nadie describe la pantalla como «la app está vacía».

⛔ No se mide conversión, retención ni satisfacción. Con 20 usuarios no hay significancia, y afirmar
lo contrario sería inventar un dato — que es justo lo que este rediseño existe para no hacer.
