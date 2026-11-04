# Sistema de diseño de la home — v1 propuesta

> Primera versión documentada, pensada para trasladarse a Compose Multiplatform **sin librerías
> nuevas**. No es un sistema desde cero: la mayor parte ya existe en
> `shared/src/commonMain/kotlin/com/rndeveloper/paparcar/ui/theme/`, y lo que este documento hace es
> (a) nombrar lo que ya hay, (b) **medirlo**, y (c) añadir sólo lo que la propuesta C necesita.
>
> ⚠️ Toda cifra de contraste de este documento está **calculada** sobre los hex del repo
> (WCAG 2.1 relative luminance) y toda ΔE00 con CIEDE2000, D65. No son estimaciones.

---

## 1 · Qué ya existe y no se toca

| Pieza | Fichero | Estado |
|---|---|---|
| Paleta y tokens semánticos | `ui/theme/Color.kt` + `PapColor.kt` | ✅ completa, con doc propia (`docs/design/COLOR-SYSTEM.md`) |
| 22 roles tipográficos | `ui/theme/PaparcarType.kt` | ✅ completa, con guard Konsist |
| Escalas de espaciado (5 vocabularios) | `Spacing.kt` | ✅ completa |
| Radios por nivel | `Shapes.kt` → `PapShapes` | ✅ completa |
| Identidad de vehículo | `VehicleIdentity.kt` | ✅ un solo resolver |
| Rampa de frescura de plazas | `SpotStateColors.kt` | ✅ exclusiva de plazas |
| Movimiento | `PapMotion.kt` | ✅ |
| Fila canónica | `PapListItem` + `PapIconTile` + `PapOutlinedCard` | ✅ |
| Divisores | `PapDivider` / `PapVerticalDivider` | ✅ con guard |

**La conclusión de la auditoría sobre el sistema de diseño es que no hace falta uno nuevo.** Lo que
hace falta son **tres componentes** (§6) y **una corrección medida** (§3.4).

---

## 2 · La paleta de referencia del encargo, evaluada

El encargo propone evaluar seis valores. Se han medido contra los que la app usa hoy.

| Rol | Referencia del encargo | Valor vivo en el repo | ΔE00 | Veredicto |
|---|---|---|---|---|
| Verde de acento | `#25F48C` | `PapGreen` **#25F48C** | 0,00 | ✅ **idéntico** |
| Fondo claro | `#F4F7F4` | `PapSurfaceLight` **#F4F7FC** | 4,19 | ⚠️ distinto matiz |
| Oscuro principal | `#0B0F0D` | `PapInk` **#0D1117** | 4,90 | ⚠️ distinto matiz |
| Superficie oscura 1 | `#121915` | `PapInkContainer` **#131A24** | 9,99 | ⚠️ distinto matiz |
| Superficie oscura 2 | `#1B251F` | `PapInkHigh` **#1A2232** | 14,68 | ❌ otro color |
| Texto secundario | `#9FB0A6` | `PapOnDarkMuted` **#8EA0B4** | 15,09 | ❌ otro color |
| Carreteras | `#243B32` | — (el mapa lo pinta kmp-maps) | n/a | ⚠️ ver abajo |

### 2.1 El hallazgo: dos neutros distintos, no dos versiones del mismo

Lo que separa las dos listas no es claridad, es **tono**. Medido en CIELAB:

| | Referencia (encargo) | Repo (vivo) |
|---|---|---|
| Oscuro principal | H **162°** (verde) · C\* 1,7 | H **270°** (azul) · C\* 4,2 |
| Superficie 1 | H **158°** · C\* 4,5 | H **270°** · C\* 7,9 |
| Superficie 2 | H **157°** · C\* 6,5 | H **278°** · C\* 11,8 |
| Texto secundario | H **159°** · C\* 8,4 | H **261°** · C\* 12,6 |

> **La referencia del encargo es una familia de neutros teñidos de VERDE; la que la app usa es una
> familia teñida de AZUL-NAVY.** No son dos aproximaciones al mismo gris: son dos decisiones de
> identidad opuestas, y la diferencia crece con la claridad (ΔE00 4,9 en el más oscuro, 15,1 en el
> texto secundario).

### 2.2 Qué recomienda esta auditoría, y por qué

**Quedarse con la familia azul-navy que ya está en producción.** Tres razones, en orden de peso:

1. **El verde de marca no necesita que el fondo lo apoye — necesita que no compita.** Medido:
   `PapGreen` sobre `PapInk` da **12,97:1**, y sobre el `#0B0F0D` de referencia **13,23:1**. La
   diferencia es irrelevante para la legibilidad. Lo que sí cambia es la **saturación percibida del
   acento**: un fondo teñido del mismo tono que el acento (ambos verdes) le resta presencia. Un
   fondo de tono complementario (azul tras verde) se la da. Es la razón por la que la app lee verde
   hoy.
2. **La app tiene tres azules con significados asignados** —`PapCarBlueDark` (identidad Bluetooth),
   `PapCarParkBlueDark` (parking público) y `PapLiveMap` (movimiento ajeno)— y su doctrina de color
   prohíbe que dos cosas compartan tinta. Un fondo verde-neutro **no** rompe eso, pero tampoco lo
   ayuda; un fondo navy ya es la cama contra la que esos tres azules se midieron y aprobaron.
3. **Cambiarla es un barrido de 60+ call sites de tokens y una revisión completa de contrastes en
   dos temas**, sin un problema medido que lo justifique. La deriva no está en el fondo.

**Lo que sí se adopta del encargo:** `#243B32` como color de carretera **del decorado del
prototipo**. En la app el mapa lo pinta `kmp-maps` y su estilo no se ha auditado en esta fase —
anotarlo como abierto es más honesto que recomendar un valor para una superficie que no se ha leído.

---

## 3 · Tokens

### 3.1 Color — tema oscuro (base)

| Token | Hex | Trabajo | Medido |
|---|---|---|---|
| `surfaceContainerLowest` | `#08090E` | detrás del mapa | — |
| `surface` | `#0D1117` | base de la app | — |
| `surfaceContainer` | `#131A24` | **hoja y nav** | — |
| `surfaceContainerHigh` | `#1A2232` | tarjetas | — |
| `surfaceContainerHighest` | `#222C3E` | modales | — |
| `onSurface` | `#EBF2EF` | texto primario | **16,65:1** sobre `surface` |
| `onSurfaceVariant` | `#8EA0B4` | texto secundario | **7,06:1** sobre `surface` · 6,53:1 sobre la hoja |
| `outlineVariant` | `#3B4A5E` | divisores | con alfa de `PapBorders.HAIRLINE_DIVIDER_ALPHA` |
| `primary` | `#25F48C` | **acción y marca** | **12,97:1** sobre `surface` |
| `onPrimary` | `#0D1117` | etiqueta sobre relleno verde | **12,97:1** |
| `primaryContainer` | `#133D28` | contenedor de acento | — |
| atención | `#F4A825` | aviso | **9,44:1** |
| peligro | `#FF5252` | error · ⛔ nunca en un CTA | **5,93:1** |
| identidad BT | `#5B9EFF` | vigilancia por Bluetooth | **7,01:1** |
| vigilancia | `#0FBF9A` | el badge honesto | 5,97:1 sobre `surfaceContainerHighest` |
| parking público | `#3E8FD4` | la P blanca | — |
| movimiento ajeno | `#2F6BFF` | fijo en ambos temas | **4,21:1** |
| plaza fresca | `#8FE83C` | rampa exclusiva de plazas | **12,41:1** |

### 3.2 Color — tema claro

| Token | Hex | Medido |
|---|---|---|
| `surface` | `#F4F7FC` | — |
| `surfaceContainerHigh` (tarjeta) | `#FFFFFF` | — |
| `onSurface` | `#0E1A2E` | **16,22:1** sobre `surface` |
| `onSurfaceVariant` | `#374460` | **9,06:1** |
| `primary` | `#009F5E` | **3,20:1** sobre `surface` · **3,43:1** sobre blanco |
| identidad BT | `#0057CA` | **6,53:1** sobre blanco |
| vigilancia | `#05876D` | **4,47:1** sobre blanco |
| parking público | `#00468F` | **9,24:1** sobre blanco |
| atención | `#B56000` | **4,53:1** sobre blanco |

### 3.3 Los tres niveles de color, sin excepciones

1. 🟢 **verde** = marca y acción (CTA, enlaces, nav, spinners) **+** identidad de vehículo con
   detección activa.
2. 🔵 **azul**: `papCarBlue` = vigilado por Bluetooth · `PapLiveMap` = movimiento que **no es tuyo** ·
   `carPark` = instalación de un tercero. Tres azules, tres significados, ninguno compartido.
3. ⬛ **el estado se ESCRIBE** en `onSurface` y se anima. Nunca se tiñe. El **glifo** lleva el color;
   el nombre, no. Excepción única: el eyebrow del peek, donde no hay glifo.

### 3.4 ⚠️ Una corrección medida que esta auditoría propone

**En tema claro, la etiqueta blanca sobre el relleno verde mide 3,43:1.**

La doctrina del propio proyecto dice que un relleno *«se le exige 4,5:1 contra su etiqueta y 3:1
contra su cama»* (`COLOR-SYSTEM.md` §v3). La cama está bien: **3,43:1** contra blanco y **3,20:1**
contra el scaffold, los dos por encima de 3:1. La etiqueta no llega: 3,43 < 4,5. Y el rol `cta` es
**15 sp SemiBold**, que no cuenta como texto grande en WCAG (hacen falta 18,66 px en negrita), así
que no hay exención por tamaño.

Opciones medidas, **sin tocar el verde** (que es lo que el user vetó expresamente el 29-08):

| Etiqueta sobre `#009F5E` | Contraste | Nota |
|---|---|---|
| `Color.White` (hoy) | 3,43:1 | ❌ por debajo del suelo propio |
| **`PapOnGreenContainerLight` #002819** | **4,65:1** | ✅ ya existe como token |
| `PapOnSurfaceLight` #0E1A2E | 5,08:1 | ✅ pero mezcla el rol de texto con el de tinta |
| `#003524` | 3,99:1 | ❌ sigue corto |

**Recomendación: `onPrimary` en tema claro pasa de `Color.White` a `PapOnGreenContainerLight`.** Es
un token que ya existe, el verde de marca queda intacto, y el botón gana 1,2 puntos de contraste.

⛔ **No se aplica en esta fase** (sería tocar producción) y **debe verse en device antes de
decidirse**: tinta oscura sobre verde neón es un cambio de carácter del botón, y el carácter del
verde ya se revocó una vez en mano. Entra como tarea de la **fase 3** del plan.

### 3.5 Espaciado — cinco vocabularios, y por qué no es un error

El código tiene cinco escalas y eso está bien: son **cinco preguntas distintas**, no cinco gustos.

| Vocabulario | Pregunta que contesta | Valores |
|---|---|---|
| `PaparcarSpacing` | ¿cuánto aire? | 4 · 8 · 12 · 16 · 20 · 24 · 32 · 48 |
| `PapGap` | ¿aire **entre qué**? | `chipRow` 6 · `block` 14 · `iconLabel` 8 · `betweenControls(visible)` |
| `PapSectionRhythm` | ¿y entre secciones? | `block` 8 · `beforeSection` 20 · `afterSection` 10 · `sideInset` 16 · `edgeInset` 8 |
| `PapControlHeight` | ¿qué alto tiene un control? | + `overhang()`, el aire invisible del mínimo táctil |
| `PapPageRhythm` | ¿qué margen tiene una página? | uno, declarado una vez |

⚠️ `PapGap.betweenControls(visible, controlHeight)` existe porque un control pulsable de menos de
48 dp arrastra aire invisible: dos cápsulas de 40 dp con `spacedBy(12)` dejan **20,2 dp** a la vista.
Hay que **pedir** el aire visible, no el nominal. Es el tipo de regla que un sistema de diseño nuevo
habría perdido.

### 3.6 Radios y superficies

| Nivel | Radio | Qué |
|---|---|---|
| `PapShapes.cell` | 6 dp | celda de datos, muestra, contador |
| `PapShapes.row` | 12 dp | lo que va **dentro** de una tarjeta: tiles, filas internas, botones |
| `PapShapes.card` | 16 dp | toda tarjeta, lo que sea que lleve |
| `PapShapes.sheet` | 20 dp arriba | las superficies que ocupan el fondo de la pantalla |
| `PapShapes.chip` | 999 dp | píldoras: chips, badges, cápsulas de estado |

**Elevación:** no se usa sombra para separar capas, se usa **superficie + hairline**. La hoja y la
nav comparten `surfaceContainer`; las tarjetas suben a `surfaceContainerHigh`. Una sombra negra
sobre un fondo casi negro no existe — medido en otro ticket del proyecto el 27-10.

### 3.7 Tipografía

Tres **voces**, 22 roles, una familia (Plus Jakarta Sans, eje `wght` 200–800 — sin eje `wdth`, leído
del `fvar`).

- **MARCA** — nombres de cosas reales y títulos: `screenTitle` 24/32 · `heroTitle` 28/36 ·
  `sectionTitle` 20/26 · `cardTitle` 18/24 · `rowName` 14/20.
- **LECTURA** — todo lo que se lee o se pulsa: `sectionHeader` 12 caps · `subsectionHeader` 11 caps ·
  `eyebrow` 11 caps · `cta` 15 · `rowTitle` 14 · `subtitle` 16 · `body` 14 · `label` 12 ·
  `caption` 12 · `meta` 12 · `badge` 12.
- **CIFRA** — una cifra que protagoniza su bloque: `statNumber` 20 · `statLabel` 12 caps ·
  `counter` 21 · `counterUnit` 8,5 · `chartLabel` 11 · `chartValue` 10.

⛔ Familia, tamaño **y peso** son propiedad del **rol**. Sólo `color` se decide en el call site.
⛔ `fontFeatureSettings` (`tnum`) **no se aplica** en CMP 1.12: ninguna decisión puede apoyarse en
cifras tabulares. El prototipo HTML lo aplicaba —y por tanto alineaba columnas de cifras que en la
app bailan—; **se quitó el 27-10** al revisar la fidelidad. Un prototipo que puede más que el
destino no sirve para decidir.

⛔ **Y las CIFRAS llevan un levante óptico que NO es decoración.** Una caja de texto mide
ascenso + descenso (1,038 + 0,222 em de Plus Jakarta Sans, leídos de sus tablas `OS/2` y `hhea`),
pero un dígito sólo pinta hasta la altura de mayúscula (0,745 em) y no baja de la línea base. Ese
hueco muerto es lo que hunde el número contra el borde de su celda.
`lift = ((ascenso − cap)·figura − descenso·unidad) / 2` → a 21/9 px son **2,08 px**. Lo calcula
`figureOpticalLiftSp()` en la app, y el prototipo aplica el equivalente medido.

⚠️ **Deriva documental detectada:** CLAUDE.md dice `statNumber(25sp)`; el código tiene 20 sp.
Anotado, no corregido (sería tocar producción).

---

## 4 · Estados semánticos del vehículo

Dos ejes **ortogonales** que nunca se mezclan. Es la regla que más fácil se rompe y la que más caro
cuesta.

| Eje | Qué dice | Cómo se pinta |
|---|---|---|
| **Vigilancia** (método) | cómo se vigila este coche | el **color de identidad**: verde = detección activa · azul = Bluetooth · gris = sin vigilar |
| **Estado** (situación) | dónde está ahora | **palabras** en `onSurface`, animadas: «Aparcado» · «En ruta» · «Aparcando…» · «Sin aparcamiento marcado» · «¿Has aparcado?» |

Y un tercer eje que **no es ninguno de los dos**: la **salud** de la vigilancia, que es un badge
aparte porque es lo único que puede desmentir a los otros dos.

| Badge | Cuándo | Tono |
|---|---|---|
| «Vigilando tu sitio» | el servicio está vivo de verdad | `watch` (verde azulado) |
| «Bluetooth» | carril determinista armado | `bt` (azul) |
| «Vigilancia frágil» | sin excepción de batería en un OEM agresivo | `warn` (ámbar) |
| «Vigilancia interrumpida» | el servicio fue matado | `error` (rojo) |
| «Falta permiso» | sin permiso CORE | `error` |

⛔ **El badge nunca es aspiracional.** Se resuelve contra el `ServicePresence` real. Un badge verde
sobre un servicio muerto es la mentira más cara que esta app puede contar.

### 4.1 ⛔ El badge de vigilancia SÓLO EXISTE cuando algo no se cumple

Dos doctrinas que ya existen en el código deciden esto, y conviene leerlas juntas porque por
separado se malinterpretan:

1. **`UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001`** — el **método** de vigilancia (asistida ·
   Bluetooth · sin vigilar) es un **glifo y un color**. *«Ninguna superficie lo imprime como texto»*:
   las tres cadenas sobreviven sólo como `contentDescription` de ese glifo, para quien no distinga
   el verde del gris.
2. **La salud no es un chip, es una fila.** `HomeDetectionSurface` la cuenta con causa, consecuencia
   y remedio: *«Tu móvil la puso en pausa. Seguimos comprobando en segundo plano, pero tu salida
   puede detectarse tarde»* + **Reactivar**. En un chip no cabe el remedio.

De ahí la regla:

| Situación | Qué lleva la cabecera |
|---|---|
| vigilancia **sana** (asistida o Bluetooth) | **nada**. El glifo de vigilancia y el color de identidad ya dicen el método; el estado está escrito en palabras |
| **frágil** · **en pausa** · **falta permiso** | un chip con su texto, **en los tres modos de cabecera**, y es un **botón**: abre la fila que lo explica y lo arregla |

Es `carCardSaysTheWatch` llevado al peek: la fila **calmada** se aparta porque la cabecera ya dice
lo mismo, pero *«un aviso frágil, una vigilancia muerta o un coche sin marcar dicen algo que la card
NO dice, y esos se quedan enteros»*. **Apartar por «parecerse» en vez de por «decir lo mismo» es
como se pierde un aviso.**

⚠️ **Mi primera versión violaba las dos**, y se descubrió porque el user preguntó si el badge
seguía intacto:
- imprimía «Bluetooth» y «Vigilando tu sitio» → etiquetaba el tier, contra la doctrina 1;
- y en modo carril el trailing lo ocupaba el **nombre del carril**, así que el aviso **desaparecía**
  al leer una lista, incluso en pausa.

El arreglo no fue añadir el badge ahí, que era el parche: fue **borrar** el resolver de cinco estados
y dejar `watchAlert()`, que devuelve `null` cuando no hay nada que avisar. Y el nombre del carril
salió del trailing por redundante: la fila de pestañas de debajo ya lo dice **y** lo marca.

Lo vigila `verify.js` §3 **en las dos direcciones**, y las dos están falsificadas: con el bug del
nombre del carril devuelto da 3 fallos; con el chip verde permanente devuelto, 8.

⚠️ **Deriva documental detectada:** la tabla de identidad de `docs/design/COLOR-SYSTEM.md` todavía
tiene una columna **«Badge»** con «ACTIVO» / «BLUETOOTH» / «INACTIVO» — un estado anterior a
`UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001`, que retiró esas etiquetas. No se toca aquí (es doc de
producción y esta fase no la modifica), pero conviene arreglarla: fue exactamente la fila que me
hizo creer que etiquetar el tier era correcto.

---

## 5 · Estados de disponibilidad de la información

El vocabulario **ya existe en el dominio** y el sistema de diseño sólo le pone forma. Son **cinco**
estados, no tres, y ninguno es la degradación de otro.

| Estado | Lo que significa | Forma |
|---|---|---|
| **No preguntado** | no hay centro de consulta, o el carril no se ha abierto | una frase que dice **qué falta y cómo arreglarlo**. Sin ilustración: no es un veredicto sobre el mundo |
| **Cargando** | la pregunta está en vuelo | skeleton **sólo si no hay nada que mostrar**; con datos viejos, se mantienen |
| **Vacío** | se preguntó y no hay nada | ilustración mínima + una línea. **Es** un veredicto, y por eso se ilustra |
| **Vacío por filtro** | hay cosas, ninguna cumple lo que pediste | una línea **+ la acción de quitar el filtro** |
| **No se pudo preguntar** | fallo de red o de fuente | una línea que lo dice. ⛔ **Jamás** «no hay nada» |
| **Recordado** | esto es lo que había la última vez que se pudo preguntar | los datos, **con su hora** |

⛔ **Falta de dato ≠ cero.** Si un valor no llega a su umbral de significancia, **la línea no
existe**. No hay «0 €» ni «—».

---

## 6 · Componentes

### 6.1 Los que ya existen y se reutilizan

`PapListItem` + `PapIconTile` + `PapOutlinedCard` (fila canónica) · `PapCtaPill` · `PapDivider` ·
`PapSectionHeader` · `PapShimmerBox` · `PapStatTile` · `PapSheet` (+ `PapSheetLead`,
`PapSheetStepper`) · `VehicleGlyphTile` · `HomeVehicleChip` / `HomeVehicleCard` ·
`HomeNearbyLensRow` · `HomeDetectionSurface` · `FirstStepsCard`.

### 6.2 Los tres que la propuesta C necesita

#### `PapPeekSubject` — la zona ① del peek

```
[ tile 44 ]  EYEBROW DE ESTADO
             Nombre del coche            (color de identidad)
             línea de sitio              (onSurface)
             meta: hace X · a Y de ti    (onSurfaceVariant)
                                                     [ chevron ]
```
**Reglas.** Altura fija de 3 líneas reservadas (la 4ª es opcional y no cambia el alto del bloque).
El nombre **siempre** en el color de identidad; el estado **siempre** en `onSurface`. Una sola
posición para el chevron. Sin ilustración, sin badge: el badge es la zona ②.

#### `PapPeekNews` — la zona ③

```
[ glifo 18 ]  Titular en una línea
              subtítulo con su fuente y su hora        [ chevron ]
```
**Reglas.**
- **Altura reservada siempre**, llena o muda. Es la mitad de lo que hace predecible a C.
- **Un** elemento. Nunca dos, nunca un carrusel. Si hiciera falta más, la propuesta es B.
- Lo decide una **precedencia pura** (ver `interaction-spec.md` §2), nunca un `if` en el composable.
- El glifo lleva el color del sujeto del que habla; el titular queda en `onSurface`.
- Un dato de una fuente externa **tiene que** poder decir **cuándo** se midió. Si no puede, no sale.

#### `PapProgressRow` — el checklist plegado a una fila

```
[ tile ]  Primeros pasos · 1 de 4
          Siguiente: marca dónde has dejado el coche
          ▓▓▓▓░░░░░░░░░░░░░░              [ Ver ]  [ × ]
```
**Reglas.** Una fila, un progreso, el siguiente paso nombrado, una salida (`Ver`) y un cierre (`×`).
⛔ No sustituye a `FirstStepsCard`: la abre. El tutorial completo sigue existiendo, pero **deja de
ser lo primero que se ve**. La regla «nunca bloquea» se mantiene intacta.

### 6.3 Pestañas de sujeto — reponderadas

```
grid-template-columns: 1,3fr  1fr  1fr
       Plazas       Parkings   Gasolineras
```
Es la jerarquía que el KDoc de `HomeNearbyLensRow` ya declara («free spots are the product; car
parks and fuel are complements») y que tres tercios idénticos no comunican.

⛔ Lo que **no** cambia: la selección la lleva el **indicador** y el color sube a `onSurface`; nunca
el peso (propiedad del rol) ni el verde (que tiene que llamar en una sola cosa por pantalla). El
contador existe **sólo donde se sabe**.

### 6.4 Botones y acciones

| Variante | Forma | Cuándo |
|---|---|---|
| **principal** | relleno verde, tinta `onPrimary` | la acción del momento. **Una** por superficie |
| **secundaria** | borde `green-outline`, texto verde | la alternativa razonable |
| **quieta** | borde `outlineVariant`, texto `onSurface` | salidas, «más tarde», «no» |
| **peligro** | borde rojo, texto rojo | sólo destructivas. ⛔ **Nunca** rellena de rojo |
| deshabilitada | 45 % de alfa **+ el motivo escrito al lado** | ⛔ un botón apagado sin motivo es un callejón |

Mínimo táctil 48 dp **siempre**, aunque el alto pintado sea 34 o 40 (`PapControlHeight.overhang`).

---

## 7 · Accesibilidad

### 7.1 Suelos que se cumplen (medidos)

- Texto primario: **16,65:1** (oscuro) · **16,22:1** (claro).
- Texto secundario: **7,06:1** (oscuro) · **9,06:1** (claro).
- Acento verde como texto/glifo sobre superficie oscura: **12,97:1**.
- Todos los tonos semánticos sobre superficie oscura ≥ **4,21:1** (el peor, `PapLiveMap`).

### 7.2 Lo que **no** se cumple y hay que decidir

- La etiqueta blanca sobre el relleno verde en tema claro: **3,43:1** (§3.4).
- `PapGreen` como relleno en tema claro mediría **1,36:1** sobre el scaffold — por eso **no se usa
  así**, y ésa es la razón de que exista `PapGreenLight`. No es un fallo, es el motivo del token.

### 7.3 Reglas

- Área táctil **≥ 48×48 dp**, sin excepciones.
- Foco visible: anillo de 2 px en `primary`, offset 2 px, en **todo** control (en el prototipo:
  `:focus-visible`).
- La hoja tiene que ser operable **sin gesto**: pulsar la cabecera alterna peek↔desplegada; con
  teclado, ↑ / ↓ recorren las anclas. Implementado en los tres prototipos.
- Las pestañas llevan `role="tab"` + `aria-selected`; los chips, `aria-pressed`.
- El color **nunca** es el único portador de información: la identidad del coche va en color **y**
  en glifo de vigilancia; el estado va en **palabras**; la frescura de una plaza va en color **y** en
  su edad escrita.
- `prefers-reduced-motion`: se desactivan los shimmer y los pulsos. ⚠️ Lo respeta el prototipo;
  **no se ha verificado** si el código lo respeta — queda como tarea de la fase 3.
- Escala de fuente: los roles están en `sp` y los bloques del peek reservan altura **en líneas**, no
  en dp fijos, para que `fontScale 1,3` no corte nada. ⚠️ El prototipo HTML **no** modela esto (usa
  px): es una divergencia conocida que hay que comprobar en device.

---

## 8 · Comportamiento adaptativo

| Ancho | Qué cambia |
|---|---|
| ≤ 360 px | el peek baja la línea de meta; las píldoras pasan a dos filas (ya lo hace `flex-wrap`) |
| 390 px | referencia del diseño |
| ≥ 430 px | nada se estira: el contenido mantiene su ancho máximo y crece el margen |
| landscape / tablet | **no auditado.** No se propone nada sin medirlo |

**Temas.** Los dos están completos en el código. El prototipo los implementa con `data-theme` y el
mismo reparto de roles, para poder comparar. ⛔ Ningún color se define **sólo** dentro de un bloque
de tema: la paleta clara es la base y la oscura redefine tokens.

---

## 9 · Compatibilidad con Compose Multiplatform

Todo lo propuesto es `commonMain`:

| Pieza del prototipo | Equivalente en Compose | Nuevo? |
|---|---|---|
| zonas del peek | `Column` dentro de `HomePeekHandle` | composición |
| altura reservada de la zona ③ | `Modifier.heightIn(min = …)` derivado de líneas | composición |
| pestañas reponderadas | `Modifier.weight(1.3f / 1f / 1f)` | 1 línea |
| fila de progreso | composable nuevo en `ui/components/` | **sí**, pequeño |
| precedencia de la noticia | **caso de uso puro** en `domain/usecase/home/` + test | **sí** |
| tokens | ya existen | no |

⛔ **Ninguna librería externa.** Nada de lo propuesto la necesita, y por tanto nada la justifica.

---

## 10 · Lo que este sistema NO resuelve

1. **El estilo del mapa.** Lo pinta `kmp-maps`; su paleta no se ha auditado. El `#243B32` del
   encargo es, de momento, el color del decorado del prototipo.
2. **iOS.** Todo es `commonMain`, así que no divergirá — pero no se ha verificado cómo se ve.
3. **Escala de fuente y landscape.** Reglas escritas, no medidas.
4. **Los 22 roles.** Son muchos. No se propone recortarlos sin medir: cada uno tiene su historia, y
   el guard de Konsist los sostiene. Si algún día se reducen, será con un barrido medido, no por
   gusto.
