# B · Smart mobility — las decisiones de la revisión (27–28-10)

> Responde punto por punto a la revisión del user sobre la alternativa B, aplica las decisiones a
> **las tres** alternativas (lo que es compartido es compartido) y deja escrito **lo que todavía no
> puedo decidir yo**.
>
> Dónde verlo: [`smart-mobility.html`](smart-mobility.html) · hoja de contactos de todos los casos:
> [`peek-cases.html`](peek-cases.html)

---

## 0 · La pregunta que iba primero: ¿el HTML es la pantalla real?

**No, y una de las divergencias estaba maquillando el diseño a mi favor.** Medido y corregido.

| Aspecto | El prototipo | La app | Estado |
|---|---|---|---|
| Familia | Plus Jakarta Sans por Google Fonts, pesos 400/600/700/800 | el mismo `.ttf` variable, eje `wght` 200–800 | ✅ equivalente para juzgar. Sin red cae a la del sistema |
| Tamaños y `lineHeight` | px, 1:1 con los `sp` de `PaparcarType.kt` | `sp` | ✅ idéntico a densidad 1 y `fontScale` 1 |
| **Cifras tabulares** | **aplicaba `tabular-nums`** | **CMP 1.12 NO aplica `fontFeatureSettings`** | ✅ **CORREGIDO**: quitado. Alineaba cifras que en la app bailan |
| Levante óptico de la CIFRA | ahora `translateY(-2px)` | lo calcula con las métricas reales (cap 0,745 · asc 1,038 · desc 0,222 em) | ✅ replicado: a 21/9 px la fórmula da 2,08 px |
| `letterSpacing` | px fijos | `sp`, **escala con `fontScale`** | ⚠️ divergen con fuente grande |
| Iconos | Material Symbols Rounded **redibujados a mano** | los reales + vectores propios | ⚠️ parecidos, no idénticos |
| Mapa | decorado SVG propio | `kmp-maps` con su estilo | ⚠️ deliberado — no hay clave de API en juego |
| Anclas de la hoja | `0,42` del contenedor, 4 anclas | `EXPANDED_MAP_VISIBLE_FRACTION = 0.42f` | ✅ misma geometría |
| Física | `cubic-bezier(.4,0,.2,1)` 300 ms, snap al ancla más cercana | `tween(300, FastOutSlowInEasing)` + umbral de fling 1.200 px/s | ⚠️ parecida, sin umbral de velocidad |
| Elevación | superficie + hairline | superficie + hairline | ✅ mismo criterio, sin sombras para separar capas |
| Densidad | 390 CSS px = 390 dp | device real ≈ 393 × 873 dp | ✅ a efectos de juicio |
| **`fontScale`** | **no se modela** | 0,85 – 1,3+ | ⚠️ **riesgo abierto** — ver §8 |

**Lo que esto significa para decidir:** la tipografía, los tamaños, los colores, el espaciado y la
geometría de la hoja **sí** se pueden juzgar aquí. La textura del mapa, los iconos al píxel y el
tacto exacto del arrastre, **no**. Y el caso que de verdad hay que ver en device antes de
implementar es el peek de B con fuente grande.

---

## 1 · [P1] La tarjeta de «Aquí y ahora» tiene DOS destinos

> *«si pulsas la card de gasolinera te lleva a la modal y al punto en el mapa con el mismo efecto
> que si pulsases un item de la lista, esa misma card quizás debería tener algún botón accesible
> para que te lleve a mostrar la lista de items de ese tipo de elemento»*

**Decisión: sí, y son dos controles, no uno.**

```
┌──────────────────────────────┐
│ [⛽] GASOLINA 95 MÁS BARATA   │
│ 1,419 €/L                    │  ← el CUERPO abre ESE item:
│ Ballenoil Méndez             │    cámara al punto + hoja de detalle
│ Calle de Méndez Álvaro·1,4 km│    (idéntico a pulsar su fila)
│ dato de las 09:00            │
├──────────────────────────────┤
│ Ver las 3                  › │  ← el PIE abre SU LISTA
└──────────────────────────────┘
```

**Por qué dos controles y no un tap con un gesto alternativo:** son dos destinos distintos y
ninguno es secundario del otro — «llévame a ése» y «enséñame todos» son dos intenciones. Un solo
tap no puede significar las dos, y un long-press es una función que nadie descubre.

**Por qué el pie y no un icono en la esquina:** el pie es una fila completa, así que tiene área
táctil de sobra sin robarle sitio al contenido, y su etiqueta **dice cuántos hay** — que es
justamente la información que hace que valga la pena pulsarlo.

⛔ **No es un `<button>` dentro de otro.** Eso es HTML inválido y en Compose sería un `clickable`
dentro de otro `clickable`, con el foco imposible de recorrer. Es un contenedor con **dos botones
hermanos**, cada uno con su `aria-label` y su foco.

**Detalle de copy:** con un solo item el pie dice *«Ver la única»*, no *«Ver las 1»*. Un plural mal
puesto delata una app traducida a medias.

---

## 2 · [P2] La cabecera habla del carril **sólo cuando el carril es lo que se lee**

> *«cuando desplegamos la modal, el sheet muestra el contenido de la pestaña pulsada, un usuario con
> poca exp le puede confundir si no llega hasta abajo y no saber que quiere decir el sheet, quizás
> cuando tengamos el scroll con el foco en la lista es cuando debería aparecer la info del sheet»*

**Tenías razón y el diagnóstico es exacto.** La regla nueva:

> **La cabecera describe lo que se está LEYENDO.** Arriba, tu coche. Dentro del carril, el carril.

Y es **reversible y continua**, que es lo que la hace predecible: la cabecera nunca habla de algo
que no esté debajo.

| Dónde está el scroll | Qué dice la cabecera |
|---|---|
| arriba (lo tuyo) | tu coche en dos líneas + «Aquí y ahora» |
| dentro del carril | tu coche **encogido** a una línea + **las pestañas** |
| con un sujeto abierto | la hoja de detalle de ese sujeto |

**⛔ Y lleva las PESTAÑAS, no una frase sobre ellas.** Esto es importante: lo que se fija en la
cabecera es *el control con el que elegiste el sujeto*, así que volver a «Plazas» desde el fondo de
una lista de parkings **no cuesta ni un scroll** — que era la única razón por la que esa fila era
`stickyHeader` en la app.

**El coche no desaparece, se encoge.** Si la cabecera dejara de nombrarlo, el usuario perdería de
qué pantalla es esto. Encogido a una línea sigue contestando «¿de quién hablamos?».

### 2.1 ¿Y si volvemos a scrollear arriba?

> *«seguir mostrándolo en el peek u ocultarlo de nuevo?»*

**Vuelve el coche.** Es la misma regla, aplicada al revés: si lo que se lee es lo tuyo, la cabecera
habla de lo tuyo. Que sea reversible es lo que la convierte en una consecuencia del scroll y no en
un modo en el que uno entra sin saber cómo salir.

### 2.2 ¿Y si plegamos con el scroll dentro de las pestañas?

> *«y si cerramos la modal con el scroll hecho en las pestañas? seguir mostrando contenido de
> pestañas?»*

Tres políticas posibles, y el prototipo deja **comparar las tres en el panel** (*«Al plegar con el
carril leído»*), porque ésta es de las que hay que sentir, no razonar:

| Política | Qué hace | Veredicto |
|---|---|---|
| **`reset`** — *plegar devuelve la hoja arriba* | el peek recupera tu coche y «Aquí y ahora»; al desplegar, empiezas arriba | ✅ **recomendada** |
| `keep` — *mantiene el sujeto leído* | lo que hace la app hoy (`laneHeaderLatched`); al desplegar sigues donde estabas | coherente, pero en B el peek **pierde «Aquí y ahora»** hasta que vuelvas a subir |
| `car` — *el peek vuelve al coche, el scroll se queda* | — | ⚠️ **incoherente**, y está en el prototipo para que se vea: al desplegar, la cabecera hablaba de algo que no está arriba |

**Por qué `reset`:** plegar la hoja es un gesto deliberado de *«ya está»*, no un accidente. Devolver
la hoja a su inicio hace que el peek **nunca** enseñe un sujeto que al desplegar no esté en
pantalla, y en B recupera lo que da valor al primer vistazo.

⚠️ **Y no contradice `HOME-BROWSE-SURVIVES-THE-DETAIL-SHEET-001`**, que es la regla de la app que
conserva el sitio: ésa es sobre sobrevivir a una **hoja de detalle encima** (que el usuario no pidió
que le moviera nada). Plegar sí lo pidió. Son dos gestos distintos y merecen dos respuestas.

---

## 3 · [P3] «Primeros pasos» lleva su cabecera

> *«también lo de los primeros pasos quizás debería llevar su título también al igual que "Aquí y
> ahora"»*

**Hecho**, y con la regla que decide cuándo una cabecera se gana su sitio — porque mi propia
auditoría criticaba dos cabeceras de la app por decorativas y no quiero contradecirme:

> **Una cabecera de sección se la gana cuando NOMBRA una provincia que el usuario tiene que
> distinguir. No se la gana cuando repite lo que el bloque de debajo ya dice.**

| Cabecera | ¿Se la gana? | Por qué |
|---|---|---|
| **Primeros pasos** · *1 de 4* | ✅ sí | nombra una provincia **temporal** que desaparecerá, y el contador dice algo que ninguna fila dice |
| **Aquí y ahora** · *lo que merece la pena cerca* | ✅ sí | nombra un conjunto **curado**, no la lista de un tipo: sin título, tres tarjetas de cosas distintas no se leen como un conjunto |
| **Tu coche** / **Tus vehículos** · *2 registrados* | ✅ sí | separa la gestión del coche del índice de lo que hay cerca, y con 2+ dice cuántos hay |
| ~~En esta zona~~ | ❌ **fuera** | la fila de pestañas **es** la cabecera de su provincia. Una cabecera encima de un control que ya nombra el sujeto no separa nada: gasta 56 dp |

---

## 4 · [P4] Qué entra en «Aquí y ahora»

> *«En Aquí y ahora realmente qué debemos mostrar? los items más llamativos de cada una de las
> pestañas? Si no hay ninguno, no mostrar esta sección supongo.»*

**Tres decisiones, cada una con su motivo:**

### 4.1 Uno por sujeto, como máximo

No «los más llamativos»: **el notable de cada sujeto, y nada más**. Dos plazas en el primer nivel
son una lista, y la lista ya existe un nivel más abajo. Máximo tres tarjetas.

### 4.2 Orden **fijo**: plaza · parking · gasolinera

No por «cuán notable es». Reordenar el primer vistazo según los datos del momento es exactamente lo
que hace impredecible una home: el usuario aprende *dónde* está cada cosa, no *cuándo*. Y el orden
es **el mismo que el de las pestañas**, así que las dos superficies no pueden contradecirse.

### 4.3 Un umbral **por sujeto**, porque «notable» no significa lo mismo para los tres

| Sujeto | Umbral | Por qué |
|---|---|---|
| **Plaza** | que exista | es el producto, y **caduca**. Una plaza libre es noticia por estar ahí |
| **Parking** | que haya uno cerca | se ofrece también con *«Gálibo sin dato»*: ⛔ no saber si cabe **no es** saber que no cabe |
| **Gasolinera** | precio para **tu** producto **y** hora admisible | ⛔ una cifra que no puede decir cuándo se midió no se afirma. Sin precio no hay noticia — aunque haya gasolineras |

### 4.4 Si no entra nada, la sección **no existe**

Confirmado tu «supongo», y con la consecuencia escrita: **ni título, ni hueco, ni caja diciendo que
no hay nada.** Cada carril ya dice lo suyo en su sitio; repetirlo arriba es llenar el primer nivel
con la noticia de que no hay noticias.

**Lo que sí queda en su lugar es una pista de que la hoja sigue abajo** —*«Desliza para ver qué hay
en esta zona»*— y eso contesta a la mitad de tu P2: lo que le falta al usuario novato ahí no es una
caja, es saber que **hay más**.

Los siete casos, uno al lado del otro, en [`peek-cases.html`](peek-cases.html) §4.

---

## 5 · [P6] Qué hacemos con «hace 2 h · a 180 m»

> *«lo veo bien con esa tipografía y tamaño me gustan, pero que haríamos con la info de Aparcado
> hace ...horas · a x metros de ti? la incluimos en Aparcado a o hace? o lo reservamos?»*

**Decisión: se reparten por la PREGUNTA que contestan, y siguen siendo dos líneas.**

```
Ford Focus · Aparcado hace 2 h 10 min        ← ¿qué · cuándo?
Calle de Embajadores, 118 · a 180 m          ← ¿dónde · cuán lejos?
```

**Por qué así:** «hace 2 h» es una propiedad **de la sesión** y responde *cuándo*; «a 180 m» es una
propiedad **de tu posición ahora** —cambia mientras andas— y responde *dónde*. Cada una se va con la
línea que ya contesta su pregunta. No se añade nada: se coloca.

**Lo que se descartó, y por qué:**

| Opción | Por qué no |
|---|---|
| todo en la línea 1: *«Aparcado hace 2 h · a 180 m»* | la línea 1 pasa a contestar dos preguntas y se queda sin sitio para nombres largos (*«Volkswagen Transporter T6.1»*) |
| una tercera línea | es justo lo que querías quitar |
| reservarlo para la sección del coche | la distancia es el dato **más accionable** cuando vas andando al coche — y ése es el momento en que el peek está cerrado y es lo único que se ve |

**Dos reglas que salen de esto y hay que respetar al implementarlo:**

1. ⛔ **La distancia no trunca.** Es el dato más corto y el que más cambia, así que lleva **ancho
   propio** al final de su línea; lo que cede cuando no cabe es la calle, con elipsis.
2. ⛔ **Sin fix la distancia no existe.** No se escribe un «—» ni un 0: la línea 2 se queda sólo con
   la calle. Y el «cuándo» sólo existe si hay sesión que fechar — en ruta no hay.

Los diez casos (nombre largo, sin ubicación, en ruta, sin marcar, pregunta abierta, los tres badges
de vigilancia, moto por Bluetooth, y la variante encogida) en
[`peek-cases.html`](peek-cases.html) §1.

---

## 6 · [P7] El peek de una plaza, un parking o una gasolinera

> *«¿qué mostramos ahora en caso de poner en el peek, plazas, parkings o gasolineras? deberíamos
> seguir el mismo diseño no? Ya que actualmente mostramos 3 líneas y las 2 últimas muestran
> dirección y a veces es repetitivo»*

**Sí, el mismo diseño, y la repetición tiene una causa concreta que se puede cerrar.**

> **Gramática única, para cualquier sujeto:**
> línea 1 = **NOMBRE** del sujeto · su **calificador**
> línea 2 = **DÓNDE** · a **CUÁNTO** de ti
> y la **cifra propia del sujeto va al TRAILING**, nunca en una tercera línea.

**La causa de la repetición:** había sujetos **sin nombre propio**. Una plaza no se llama de ninguna
manera, así que la línea 1 se rellenaba con su dirección… y la línea 2 también. De ahí la regla:

> ⛔ **La dirección aparece en UNA línea, nunca en las dos.** Si el sujeto no tiene nombre propio,
> la línea 1 es su **TIPO + calificador** y la 2 la dirección.

| Sujeto | Línea 1 | Línea 2 | Trailing |
|---|---|---|---|
| Tu coche | `Ford Focus · Aparcado hace 2 h` | `Calle de Embajadores, 118 · a 180 m` | badge de vigilancia |
| **Plaza** (sin nombre) | `Plaza libre · Mediano` | `Calle del Olivar, 34 · a 120 m` | `4 min` *(color de frescura)* |
| **Parking** | `Parking Embajadores · Cabe tu coche` | `Parking público · a 240 m` | `2,10 €/h` |
| **Gasolinera** | `Ballenoil Méndez · Gasolina 95` | `Calle de Méndez Álvaro · a 1,4 km` | `1,419 €/L` |

Fíjate en el parking: la línea 2 dice **qué es** y a cuánto, **no la calle**. Repetir el nombre de
la vía debajo del nombre del parking no añade nada — y era otra cara de la misma repetición.

**Debajo, una sola línea de procedencia**, y sólo cuando hay fuente que citar: *«Precio publicado
por la fuente a las 09:00»*, *«El municipio no publica el gálibo de este parking»*, *«Avisada por un
conductor al irse · caduca en 11 min»*. Una plaza es nuestra, así que no cita fuente: cuenta su
origen.

Los siete casos de detalle en [`peek-cases.html`](peek-cases.html) §2.

---

## 7 · Lo que esto cambia en A y en C

Las decisiones 1, 2, 3, 6 y 7 **no son de B**: son del producto. Así que están aplicadas a las tres
alternativas, y lo que queda comparándose es lo que de verdad las distingue:

| | A · Vehicle-first | B · Smart mobility | C · Adaptive home |
|---|---|---|---|
| Coche en 2 líneas | ✅ | ✅ | ✅ |
| Peek de detalle con la gramática única | ✅ | ✅ | ✅ |
| Cabecera por scroll + pestañas fijadas | ✅ | ✅ | ✅ |
| Cabeceras de provincia | ✅ | ✅ | ✅ |
| **Primer vistazo del entorno** | **nada** | **hasta 3 tarjetas** | **1 línea por precedencia** |
| Peek | ~200 dp | ~300 dp | ~276 dp |

Y una cosa que la revisión deja clara: **B y C ya no discrepan sobre qué es afirmable.** C usa
**los mismos umbrales** de `notables()` para elegir su línea, así que la diferencia entre las dos es
puramente *«cuántas cosas caben en el primer vistazo»* — una pregunta de producto, no de datos. Eso
se ve en el panel de C, que dice cuántas tarjetas enseñaría B en el mismo estado.

---

## 8 · Lo que no puedo decidir yo

Siete cosas. Las tres primeras son cifras que son tuyas; las otras cuatro hay que verlas en mano.

1. ~~¿Cuándo un precio es «demasiado viejo» para ser noticia?~~ → **DECIDIDO por el user (27-10):
   depende de la FUENTE.** Ver §12.
2. **¿Hay distancia máxima para que una plaza o un parking sean notables?** Hoy basta con que
   existan. Un parking a 3 km no es noticia para quien busca sitio **aquí**; una plaza a 1 km,
   tampoco. Pero poner un número sin medirlo es inventarlo.
3. **¿«Ver las 3» o «Ver gasolineras»?** El primero dice cuántas hay (más informativo); el segundo
   dice a dónde va (más predecible). **Me inclino por el primero**, porque la tarjeta ya dice de qué
   sujeto habla.
4. ~~¿«Aquí y ahora» sigue en pantalla mientras CONDUCES?~~ → **DECIDIDO por el user (27-10): sí, y
   con los tres sujetos, gasolineras incluidas.** Ver §10.
5. ~~Al plegar: `reset` o `keep`.~~ → **DECIDIDO por el user (28-10): `reset`.** Plegar devuelve la
   hoja arriba. Ya es el valor por defecto del prototipo.
6. ⚠️ **El peek de B a `fontScale` 1,3.** Son dos líneas de coche + cabecera de sección + una fila
   de tarjetas: con fuente grande puede comerse el mapa. El prototipo **no modela `fontScale`**, así
   que esto hay que verlo en device antes de implementar. Si no cabe, lo que cede es la línea de
   meta de la tarjeta, no la sección.
7. **¿Confirmas el orden fijo plaza · parking · gasolinera?** Es el mismo que las pestañas. Si
   prefieres que la gasolinera suba cuando el ahorro es grande, se puede — pero entonces el primer
   vistazo se reordena, y eso es justo lo que §4.2 descarta.

---

## 9 · Impacto en el plan de implementación

Lo de la revisión **no cambia las fases**, añade piezas a la 1 y a la 2
([`interaction-spec.md`](interaction-spec.md) §9 — **reescrito para B el 29-10**):

| Pieza | Fase | Coste | Riesgo |
|---|---|---|---|
| Gramática de 2 líneas (coche y sujetos) | 1 | bajo — todo está en `HomePeekSlice` | ninguno |
| Cabeceras de provincia + quitar «En esta zona» | 1 | bajo | ⚠️ revierte una decisión tomada en device |
| Tarjeta de dos destinos | 1 | bajo | ninguno |
| Peek de detalle unificado | 1 | **medio** — hoy son tres composables (`FuelStationPeek`, `PublicParkingPeek`, `ParkingPeek`) y pasarían a compartir una fila | bajo, pero es un barrido de consumidores |
| Cabecera dirigida por scroll | 2 | **medio-alto** — hay que mover la fila de pestañas a la cabecera y que el alto no salte | ⚠️ ver abajo |
| `notables()` como caso de uso | 2 | medio — es un **veredicto**: caso de uso propio + test | bajo |
| Umbral de frescura por fuente | 4 | medio | decisión de producto |

⚠️ **El punto técnico que hay que mirar antes de prometerlo:** mover las pestañas a la cabecera
cambia el **alto del peek**, y de ese alto depende toda la geometría de anclas
(`computeSheetPositioning`). En el prototipo lo resuelvo dejando las pestañas del cuerpo en su sitio
pero invisibles, para que el alto del cuerpo no cambie y nada salte. En Compose habrá que hacer lo
mismo —reservar su hueco— o aceptar un desplazamiento. **Es el único punto de las siete decisiones
donde la implementación puede no salir tan limpia como el prototipo.**

---

## 10 · Conduciendo: los tres sujetos, gasolineras incluidas — **decidido**

> *«Aquí y ahora conduciendo: solo plaza y parking y también gasolineras, ¿quién dice que una
> persona conduciendo no pueda parar a repostar?»*

**Decisión del user, y su argumento desmonta mi recomendación en una línea: repostar se hace SÓLO
conduciendo.** Apagar las gasolineras justo ahí era apagarlas siempre que sirven. Mi propuesta de
«reducir a plaza y parking» confundía *«no es sobre aparcar»* con *«no es útil»*.

### Qué ha cambiado, exactamente

| | Antes | Ahora |
|---|---|---|
| **B** | ya mostraba los tres | sin cambios — la reducción **sólo existía en mi texto**; `notables()` nunca miró el estado del coche |
| **C** | regla `EN_RUTA` → **muda** | regla **retirada**. La precedencia baja de 9 reglas a 8, y de cinco mudas a cuatro |

El argumento editorial que le quedaba a `EN_RUTA` —*«el viaje es lo que se cuenta»*— tampoco se
sostenía: **el viaje ya lo cuenta la zona ①** («En ruta · Ronda de Valencia»), así que la zona de
noticia estaba libre. Y conduciendo es, de hecho, el momento de máximo valor de lo que hay alrededor.

### Dos consecuencias que hay que respetar al implementarlo

1. ⛔ **El ancla de los notables sigue al COCHE, no a la cámara.** Conduciendo la cámara persigue al
   puck, así que en la práctica coinciden — pero si el usuario aparta el mapa para mirar otra zona,
   lo que se ofrece tiene que seguir siendo lo que hay **donde está el coche**. Ofrecerle una
   gasolinera de un barrio que está mirando por curiosidad es inútil, y encima mientras conduce.
2. ⛔ **La hoja no se despliega sola** mientras hay viaje. Esa parte de mi propuesta no la
   contradijo nadie y se mantiene: la noticia se ofrece, la pantalla no se toma por su cuenta.

Lo que **no** cambia: las tres reglas de urgencia (`PERMISO_CORE`, `PREGUNTA_ABIERTA`,
`VIGILANCIA_MUERTA`) siguen callando el ambiente — un permiso que falta o una pregunta con plazo sí
ganan a cualquier dato del entorno, conduciendo o no.

Caso visible: [`peek-cases.html`](peek-cases.html) §4, tarjeta **«CONDUCIENDO»**.

---

## 11 · Los badges de estado: qué pretendo hacer

> *«hasta lo que yo entiendo el badge de vigilancia sigue estando intacto apareciendo ese badge
> cuando y donde debe no?»* · *«Sobre los badges de estado, ¿qué pretendes hacer?»*

Fui a comprobarlo al código en vez de contestar de memoria, y **encontré dos cosas que yo había
roto**, no una. La respuesta a «qué pretendo hacer» es, literalmente, **retirar lo que había
inventado y volver a la doctrina que la app ya tenía escrita.**

### 11.1 Lo que encontré

**(a) El hueco que preguntabas.** `watchBadge()` sólo lo consumía `carTwoLines()`, y la cabecera en
**modo carril** se construía su propia fila con el **nombre del carril** en el trailing. Al bajar a
leer una lista el badge **desaparecía — incluso en pausa**. Lo introduje yo al añadir la
cabecera-por-scroll.

**(b) Lo que no preguntabas y era peor: el badge no debería existir sano.** Dos doctrinas del
código, que por separado se malinterpretan y juntas lo deciden:

1. **`UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001`** (`VehicleStatusIndicators.kt`): el **método** de
   vigilancia es un **glifo y un color**, y *«ninguna superficie lo imprime como texto»* — las tres
   cadenas sobreviven sólo como `contentDescription`, para quien no distinga el verde del gris.
   **Mi badge imprimía «Bluetooth».** Eso es etiquetar el tier.
2. **La salud no es un chip, es una FILA.** `HomeDetectionSurface` la cuenta con causa, consecuencia
   y remedio: *«Vigilancia inmediata en pausa de tu Ford Focus»* + *«Tu móvil la pausó. Seguimos
   comprobando en segundo plano, pero tu salida puede detectarse tarde»* + **Reactivar**. En un chip
   no cabe el remedio, y yo había comprimido la fila en un chip.

### 11.2 Lo que pretendo hacer, entonces

> ⛔ **El badge de vigilancia SÓLO EXISTE cuando algo no se cumple.**

| Situación | Qué lleva la cabecera |
|---|---|
| vigilancia **sana** (asistida o Bluetooth) | **nada.** El glifo de vigilancia y el color de identidad ya dicen el método; el estado está escrito en palabras |
| **frágil** · **en pausa** · **falta permiso** | un chip con su texto, **en los tres modos de cabecera**, y es un **botón**: abre la fila que lo explica y lo arregla |

Tres consecuencias, y las tres son mejoras sobre lo que tenía:

- **Se va el chip verde permanente.** Era la app reclamando mérito en cada pantalla, compitiendo con
  el acento (`UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001` pide **un** énfasis por pantalla). Y
  ahora **ver el chip ya es la noticia**: si está, algo falla.
- **El chip es la PUERTA de la fila, no su sustituto.** Resuelve mi propia preocupación («una
  vigilancia muerta que no se ve es una promesa incumplida en silencio») sin inventar una superficie
  paralela: **se ve en reposo, se lee y se arregla en la fila**, que es donde ya vive hoy.
- **El resolver de cinco estados está BORRADO**, no desactivado. Devolvía el tier como etiqueta, y
  eso era la puerta por la que la doctrina se habría vuelto a perder. Lo que queda es
  `watchAlert()`, que devuelve `null` cuando no hay nada que avisar.

Y **el nombre del carril salió del trailing** por redundante: la fila de pestañas que va justo
debajo ya nombra el sujeto **y** marca en cuál estás.

### 11.3 El resto de la familia de badges — qué NO pretendo hacer

Audité los demás por si había que unificarlos. **No hay que tocarlos**, y es mejor decir por qué:

| Familia | Qué es | Veredicto |
|---|---|---|
| `PapBadge` + variantes semánticas | el componente genérico, con su caja «densa» compartida y el tamaño de letra como propiedad del rol | ✅ ya es un sistema. Ningún call site inventa números |
| `VehicleWatchLeadingIcon` | el método, en **tres formas** distintas (marca BT · radar · anillo hueco) para que el color no lo cargue solo | ✅ y es la pieza que hace innecesario el chip sano |
| `SpotAgeIndicator` · `SpotReleasingIndicator` · `EnRouteIndicator` | la rampa de frescura, la plaza liberándose y «N en camino» | ✅ **no se unifican con el de vigilancia**: hablan de una PLAZA, no de tu coche, y su color es la rampa exclusiva de plazas |
| `SizeChip` | la talla (`MEDIANO`) | ✅ es un dato, no un estado |

⛔ **Lo que no voy a hacer es un «sistema de badges» que los meta a todos en el mismo molde.** Son
tres cosas distintas que coinciden en ser pequeñas y con forma de píldora: el **método** de
vigilancia (glifo), la **salud** (aviso con remedio) y los **datos** de una plaza (rampa). Juntarlos
por su forma es exactamente cómo esta app llegó una vez a tener *«tres azules con cuatro
significados»*.

### 11.4 Testigo, en las dos direcciones

`verify.js` §3 comprueba **las dos**, porque una sola habría dejado pasar el error (b):

- **(a)** el aviso aparece con su texto en los 3 modos, y lleva su `showwatch`;
- **(b)** sano, la cabecera **no imprime** ninguna de las palabras prohibidas («Vigilando»,
  «Bluetooth», los dos avisos) en ninguno de los 3 modos.

**Falsificadas las dos:** con el bug del nombre del carril devuelto da **3 fallos**; con el chip
verde permanente devuelto, **8**. Restaurado, 0.

Casos visibles: [`peek-cases.html`](peek-cases.html) §1 (el caso sano enseña justo lo que **no**
hay, y los tres avisos), §2 («Con la vigilancia muerta») y §3 (tres casos de carril: frágil, en
pausa y sin permiso).

### 11.5 ⚠️ Una deriva documental que esto saca a la luz

La tabla de identidad de `docs/design/COLOR-SYSTEM.md` todavía tiene una columna **«Badge»** con
«ACTIVO» / «BLUETOOTH» / «INACTIVO». Es un estado **anterior** a
`UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001`, que retiró esas etiquetas del producto. No la toco (es
doc de producción y esta fase no la modifica) pero conviene arreglarla: **fue esa fila la que me
hizo creer que etiquetar el tier era correcto.** Es el mismo coste que el proyecto ya tiene escrito
— *«un comentario que sobrevive al cambio que lo desmiente cuesta más que ninguno»*.

---

## 12 · El precio viejo depende de la FUENTE — **decidido**

> *«el precio viejo depende de la fuente, déjalo así»*

Confirmado, y lo dejo así. Lo que añado es el **inventario real**, porque la regla sólo es
implementable si se sabe qué sella cada fuente — y son cuatro comportamientos distintos, no uno:

| Fuente | Qué sella | Cómo se juzga si es afirmable |
|---|---|---|
| **Minetur** (ES) | la **RESPUESTA** (`reportedAt`), y propaga esa hora a cada precio | la hora de la respuesta |
| **France** | **CADA precio** (`reportedAtMs` por producto); `reportedAt = null` | `oldestPriceAtMs` como **COTA**: «nada es más viejo que esto». ⛔ Nunca «la lista es de esta hora» |
| **Italia** | **CADA precio**; `reportedAt = null` | igual que Francia |
| **Alemania** (MTS-K) | **nada**, y en cambio declara `pricesAreLive = true` | **afirmable por construcción** — es una garantía legal de tiempo real. ⛔ Nunca inventarle una hora |
| **OSM** | estaciones y **nunca** precios | sin precio no hay noticia (la lista sigue, sin figura) |
| **disco** (cache) | `rememberedAtMs` + conserva el `reportedAtMs` de cada precio | se juzga **el precio**, no el momento en que se guardó |

**Por qué esto obliga a que el umbral viva por fuente y no como constante global:**

1. **A Alemania no hay nada que comparar.** Un `MAX_AGE` global no tendría contra qué medir
   `pricesAreLive`, así que enmudecería la única fuente que **garantiza** tiempo real. Exactamente
   al revés de lo que debería.
2. **Francia e Italia sólo pueden dar una COTA.** `oldestPriceAtMs` acota el más viejo de la lista,
   y la propia app ya advierte de leerlo como «la lista es de esta hora»: sus precios se midieron
   *«spanning 0,7 h to thirteen months»*. Un umbral global aplicado a esa cota tiraría listas
   enteras por el precio más rancio de una gasolinera que no estabas mirando.
3. **España sella la respuesta**, así que su umbral es el de un feed que se publica entero: una
   escala de horas, no de minutos ni de días.

⚠️ **Consecuencia asumida:** la frase que acompaña al precio **no puede ser la misma en los cuatro
casos**, y ya no lo es en el prototipo — «Precio publicado por la fuente a las 09:00» /
«El más reciente que publica esta fuente aquí es de hace 13 días» / «Esta fuente no publica precios
aquí». Es la doctrina de `FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001` llevada al umbral.

⛔ **Y los 13 días del prototipo siguen siendo de demostración.** Son el eje `fuelAge: 'stale'` del
panel, para ver el efecto; **no** son un umbral propuesto. Los umbrales reales se escriben junto a
cada fuente cuando se implemente, con su nombre y su motivo.

---

## 13 · Marcar cuáles son las mejores — **ya existe, y mi prototipo lo había omitido**

> *«dejemos "ver gasolineras" y "las 3", el que veas mejor. Se le añade algún icono o alguna forma de
> mostrar que son mejores, razona sobre esto.»*

### 13.1 Lo primero: esto no hay que inventarlo

Fui a razonarlo y me encontré con que **la app ya lo tiene**, desde
`FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001` (`domain/fuel/FuelStandouts.kt`), **y tú ya lo
revisaste dos veces**: una para moverlo al final de la fila, otra porque *«el badge apenas destaca y
no se nota debido a sus tonos»*. Lo que faltaba era que mi prototipo lo reprodujera. Ya lo hace.

Un chip al final de la fila: **MÁS BARATA** (verde de marca: el precio es LA respuesta) o
**MÁS CERCANA** (neutro: es orientación, no respuesta). Par tonal, no relleno macizo — al final de
la fila la prominencia ya se la da la posición, y dos chips con la misma forma y distinto peso se
leerían como dos *niveles* de afirmación al cambiar de pestaña, cuando dicen lo mismo.

### 13.2 El razonamiento que pedías: lo interesante es lo que se NIEGA a afirmar

Marcar «la mejor» es fácil; lo difícil es no mentir. Seis negativas, cada una con su motivo medido,
y **las seis con testigo falsificado** en `verify.js` §8:

| Caso | Qué hace | Por qué |
|---|---|---|
| **Un solo candidato** | ningún chip | con una gasolinera no hay «la más barata»: hay una gasolinera |
| **Empate al mínimo** | el chip **se comparte** | la primera versión exigía ser *estrictamente* la más barata y en un mapa real eso borraba el chip casi siempre — los surtidores se copian el precio a la milésima. La verdad comprobable en el surtidor es que **las dos son las más baratas** |
| **Empate universal** | **se calla** | si todas cobran lo mismo, «la más barata» no distingue nada: es ruido en todas las filas |
| **Dos monedas** | sin «más barata»; la más cercana aguanta | 1,42 £/l no es más barato que 1,65 €/l porque el número sea menor. Los metros sí son metros |
| **Distancias empatadas** | sin «más cercana» | si empatan, ninguna lo es |
| **Es las dos cosas** | **un** chip, «MÁS BARATA» | una fila es un nombre, no un marcador. Y la que es las dos está por construcción arriba, con sus metros escritos dos palabras más allá |

Y una regla de orden que importa: **sólo la más barata sube.** La más cercana lleva su chip pero
**no reordena**, porque la lista pone primero las que tienen precio y la más cercana es muy a menudo
una sin precio — subirla dejaría la lista encabezada por una fila sin cifra, que es justo lo que ese
orden existe para evitar.

### 13.3 Lo que SÍ he añadido: el chip cierra el lazo con «Aquí y ahora»

La tarjeta de «Aquí y ahora» ahora lleva **el mismo chip** que su fila en la lista, y —más
importante— **el sujeto de la tarjeta lo elige la misma función pura**: no está escrito a mano. Si
la regla cambia, la tarjeta cambia con ella. Eso es lo que impide que el primer nivel y la lista
nombren cosas distintas, y tiene su propio testigo (`verify.js` §8.8: *«la tarjeta nombra la misma
gasolinera que lleva el badge»*).

### 13.4 ⛔ Y la negativa más importante: **una PLAZA no lleva «la mejor»**

No es una omisión, es una decisión, y es la única de las seis que propongo yo:

> Una gasolinera **no se agota**. Una plaza **sí**.

Señalar «la mejor plaza» entre cuatro manda a todo el mundo a la misma — y la app **ya cuenta**
cuánta gente va de camino a una (`Spot.enRouteCount`). Marcarla convertiría esa cifra en una carrera
**que habríamos empezado nosotros**. Lo que una plaza lleva es su **frescura** (rampa de color) y su
**edad**: hechos sobre ella, no un veredicto comparativo.

Por eso el chip es de los **complementos**, nunca del producto.

### 13.5 Parkings: propuesta, no hecho

Los parkings **no** llevan standout en la app. En el prototipo lo he probado con las mismas
negativas, y su caso débil se ve solo: **con la tarifa sin publicar —que es lo normal— no hay «más
barato» que decir** y queda sólo «MÁS CERCANO», que es lo que el orden ya dice. Lo dejo marcado como
propuesta en `peek-cases.html` §5 para que decidas mirándolo, no leyéndolo.

### 13.6 El copy del pie: **«Ver las 3»**

Dijiste el que vea mejor, así que decido y digo por qué: **«Ver las 3»**. La tarjeta ya dice de qué
sujeto habla en su eyebrow («GASOLINA 95 MÁS BARATA»), así que lo que el usuario **no** sabe —y lo
que decide si merece la pena pulsar— es **cuántas** hay. «Ver gasolineras» gasta el espacio en
repetir lo que está dos líneas arriba.

⚠️ **Con una salvedad de accesibilidad:** «Ver las 3» a solas no es un nombre accesible — fuera de
contexto no dice de qué. El texto visible lleva la cifra y el `aria-label` lleva el sujeto («Ver las
3 gasolineras»). Y con una sola, «Ver la única», no «Ver las 1».

### 13.6b Parkings: **sí** (decisión del user, 28-10)

Ya no es propuesta. Llevan el chip con las mismas seis negativas, y la que más manda aquí es la
cuarta: **con la tarifa sin publicar —que es lo normal— no hay «más barato» que decir** y queda sólo
«MÁS CERCANO». Esa negativa es lo que lo hace honesto en la mayoría de zonas, en vez de un chip que
afirma una comparación que no se ha podido hacer.

⚠️ En la app **todavía no existe**: es trabajo de implementación, ya decidido. Entra en la fase 1
del plan como *«extender `FuelStandouts` a los parkings»* — y el nombre del tipo tendrá que dejar de
ser `Fuel…`, porque pasará a servir a dos sujetos.

### 13.7 Una salvedad honesta sobre el chip

«MÁS BARATA» significa **de las que te estamos mostrando** — dentro del radio consultado y del
filtro activo. No es «la más barata de la ciudad». Hoy el carril ya lleva su línea de procedencia
abajo («Precios publicados por la fuente a las 09:00 · cotizado para Gasolina 95»), que acota de
qué respuesta hablamos, pero **no lo dice explícitamente**. No he inventado copy nuevo para eso: lo
dejo señalado como lo único de este apartado que podría necesitar una palabra más.

---

## 14 · «Últimas actualizaciones cerca» — ya tiene datos, y contesta un ticket abierto

> *«en 2, poner algo como últimas actualizaciones cerca o algo así? a un radio de x km? algo
> interesante que hacer»*

Fui a ver qué sabe la app de **cambios** (no de estado) antes de proponer nada, y resulta que esto
no hay que inventarlo tampoco.

### 14.1 Los datos ya existen, son gratis, y el radio ya está decidido

`RunFuelPriceWatchUseCase` corre **dos carriles en un tick**:

- `followedMoves` → lo que hicieron las que **sigues** (Plus);
- **`nearbyMoves`** → lo que hicieron las de **tu alrededor** (**free**).

Y lo importante: *«el ancla es donde el conductor ESTÁ, no donde está una gasolinera seguida»*, con
**`AROUND_FOLLOWED_RADIUS_M = 1.000 m`** y un tope de las más cercanas muestreadas al día.

> ⛔ **El radio no lo invento: es 1 km y ya está en el código.** Preguntabas «¿a un radio de x km?»
> — la respuesta es que x ya vale 1, decidido cuando se hizo el aviso gratuito de precios.

### 14.2 Y contesta un ticket que ya estaba abierto

`FUEL-A-RECORDED-SERIES-MUST-BE-READABLE-001` dice, literalmente, que se graban **30 días** de
precios por gasolinera en Room y **no se leen en ninguna superficie**: *«el repositorio incluso
publica un `observeHistoryFor` reactivo, pensado para una pantalla, y nadie lo observa»*. Su estado
es *«pide decisión de producto (¿dónde vive?) antes que código»*.

**Tu idea es esa decisión.** La serie pasa a tener un consumidor en la UI, y no una pantalla nueva:
una línea en la tarjeta que ya existe.

### 14.3 Dónde vive: **no es una sección nueva**

La tentación era una sección «Últimas actualizaciones». No la hago, y por tres razones:

1. **Sólo tiene un sujeto.** Mirado sujeto por sujeto, «lo que ha cambiado cerca» sólo existe para
   gasolineras: una **plaza** ya es un cambio por naturaleza (caduca en minutos — el carril de
   plazas *es* el feed de novedades, una sección aparte lo duplicaría), y de los **parkings** no se
   muestrea la ocupación en el tiempo. Una sección con un solo tipo de fila no es una sección.
2. **Rompería el orden fijo** que §4.2 defiende: una cuarta tarjeta o un bloque más reordena el
   primer vistazo.
3. **El mismo hueco puede decirlo mejor.**

> ⛔ **La tarjeta de gasolinera prefiere el CAMBIO al NIVEL.**
> Con movimiento: «**Bajó hoy cerca de ti** · −4 cts · Repsol Embajadores · 1,489 €/L · a 300 m».
> Sin movimiento: «**Gasolina 95 más barata** · 1,419 €/L» (lo de ahora).

Un precio que lleva meses igual es un **hecho**; que haya bajado 4 céntimos **hoy** es **noticia**, y
es lo que mueve a alguien a desviarse. Mismo sitio, mismo orden, mejor contenido.

**Y las subidas también se cuentan.** Si sólo se contaran las bajadas la app sería un cupón y no una
medición — una subida también cambia una decisión (repostar hoy o mañana).

### 14.4 La negativa que lo hace honesto — y no es mía

De `EvaluateDailyPriceMoveUseCase`:

> ⛔ **«Dos filas que difieren no es un precio que cambia.»** Un feed puede responder durante días
> con la misma lectura rancia (Italia: **69 %** de los precios de dos a siete días, con cola hasta
> 2013) y también puede republicar un precio sin cambiar con sello nuevo. Así que un movimiento sólo
> se reclama cuando **las dos muestras llevan sello de la fuente Y esos sellos difieren**. Si no, el
> veredicto es **«sin noticias»**, que **no es «sin cambios»** — la misma razón por la que un precio
> ausente nunca es un cero.

Más el umbral que ya existe (**≥0,5 c/l**): no toda diferencia es noticia.

⛔ Y una consecuencia que el prototipo vigila: **un movimiento no se salta el umbral de frescura.**
Con el precio demasiado viejo, o sin precios en la zona, **no hay tarjeta de gasolinera** por mucho
que las muestras difieran.

---

## 15 · Abrir la hoja sola al arrancar — **sí, pero el disparador no es «hay contenido»**

> *«si tenemos cosas interesantes que mostrar en aquí y ahora, al abrir la app, abrir modal para
> mostrar ese contenido»*

De acuerdo con la idea, y con **una corrección en el disparador** que es lo que decide si esto es
útil o es una app que grita.

### 15.1 «Hay contenido» no puede ser el disparador

Con «hay algo que mostrar» se abriría **casi siempre**: hay parkings en todas partes y gasolineras en
casi todas. La hoja taparía el mapa en cada arranque, y lo que empieza siendo un servicio acaba
siendo un folleto que hay que cerrar.

> ⛔ **El disparador es «hay NOVEDAD»**: algo que no estaba la última vez que miraste. Entonces
> abrirse **se gana el sitio** — estás viendo algo que no sabías.

Y eso es exactamente lo que §14 pone sobre la mesa: un precio que se movió **hoy** es novedad; un
parking que lleva ahí diez años, no.

### 15.2 Las reglas

| | |
|---|---|
| **Cuándo** | una vez por arranque en frío, y sólo con novedad sin ver |
| **Hasta dónde** | ⛔ **media hoja**, nunca desplegada. El mapa conserva su mitad; abrirse al 58 % al arrancar es lo que hace que una app se sienta como un folleto |
| **Nunca** si… | conduces · hay pregunta con plazo · la vigilancia está en pausa · falta permiso · no hay nada que mostrar |
| **Salida** | el primer arrastre hacia abajo la cierra, y no vuelve en esa sesión |

La tercera fila es la importante: **esas cuatro cosas ya mandan en la cabecera**, y abrir la hoja
encima las esconde detrás de su propio contenido. Incluye el viaje vivo — **conduciendo la hoja no se
abre sola**, que es lo único de mi propuesta anterior que no contradijiste.

La regla vive en `autoOpenAnchor()`, pura y testeable, con sus **seis** casos en `verify.js` §9 y
**los cinco bugs falsificados**.

### 15.3 ⚠️ Lo que esto necesita y hoy no existe

**Una marca de «visto» persistida**: qué novedad ya se le enseñó a este usuario. Sin ella la hoja se
reabre en cada arranque con la misma noticia, que es **peor que no abrirse** — enseña dos veces lo
mismo y entrena a cerrarla sin leer.

Es la única pieza de esta idea que exige un campo nuevo, y por eso va en la **fase 2** del plan, no
en la 1. El resto —la regla, el ancla, las negativas— es presentación.
