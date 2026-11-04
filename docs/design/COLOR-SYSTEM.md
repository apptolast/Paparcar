# Sistema de color de Paparcar — doctrina

> **Doc vivo**, al día — re-verificado contra master `46621e7f` el 2026-08-30
> [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]: los tokens citados existen en `Color.kt` con los mismos
> hexes. Es el único doc de diseño que el barrido no tuvo que corregir, porque cada ticket de color
> lo actualiza en su propio commit.
>
> Fuente de verdad del significado del color. Los valores viven en `ui/theme/Color.kt` y
> `ui/theme/Theme.kt`; **este documento manda sobre por qué existe cada token**.
> Creado 2026-08-11. v1 (color-por-urgencia) descartada en device; v2 (mi coche = azul) descartada
> en device 2026-08-13 — el usuario perdió el verde de identidad que la app siempre tuvo. v3
> vigente: **identidad del vehículo = MÉTODO de vigilancia; el estado es texto**.
> [UI-COLOR-DOCTRINE-001]

---

## 0. La doctrina en una frase

> **La app es VERDE (marca). El color del NOMBRE de un coche dice CÓMO se le vigila: verde de
> vigilancia = detección activa, azul = Bluetooth, gris = sin vigilar. El estado (aparcado / en ruta
> / sin aparcar) se ESCRIBE en `onSurface` y se anima — nunca se tiñe.**

Desde [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001] hay **tres verdes, no uno**, en un espectro donde la
marca queda en medio y cada vecino a ≥14° de tono:

> `FRESH` lima (h≈128) — **`BRAND` menta (h≈151)** — `WATCH` teal-esmeralda (h≈166)

*La marca* es lo que la app te ofrece; *la vigilancia* es lo que la app hace por tu coche; *la
frescura* es lo que otro vecino acaba de dejar libre. Eran el mismo píxel.

Violar esto es un bug de diseño, igual que un `fontSize` inline.

---

## 1. El problema que resuelve (por qué existe este documento)

El desorden original: **demasiados significados sin parentesco compartiendo color**.

- El verde contaba tres historias sin relación: marca/CTA, plaza libre fresca, y estado del
  vehículo propio (monitorizado, aparcado, candidato…). Ocho usos en total.
- Había **tres azules con cuatro significados**: `PapBlue` #5B9EFF (BT + reporte manual + zona
  privada + "info"), `PapDriveBlue` #2F6BFF (conducción) y `SpotPalette.ManualBlue` #0057CA —
  este último **idéntico** a `PapBlueLight` #0057CA: en tema claro "coche con Bluetooth" y "plaza
  reportada a mano" eran literalmente el mismo color.
- Y **cuatro resolvers rivales** pintaban el mismo vehículo con ontologías distintas
  (`vehicleStatusAccent`, `vehicleBadgeTone` — que daba prioridad al MÉTODO sobre el estado —, un
  `when` inline en el marcador, y el punto del selector). El mismo coche salía verde en el chip y
  azul en el peek sin cambiar de estado.

### La lección de la v1 (por qué se reformuló)

La primera doctrina decía *"el color solo se gasta en urgencia; el reposo es neutro"* y codificó
la vigilancia en un cian con escalera de alfas en el borde. **Falló en device** (2026-08-11, mock
en Oppo+Redmi): un garaje de tarjetas grises cuya única diferencia era la transparencia de un
borde de 1dp. Dos errores medibles:

1. **La distinción más importante (BT vs asistido) iba en la señal más débil que existe** — el
   alfa de un borde fino. Ilegible.
2. **"Aparcado" no es un estado sin importancia.** Es *la app ha hecho su trabajo: coche anclado y
   vigilado*. En gris parecía apagado, muerto.

Y el error de fondo: el color estaba asignado al eje equivocado (urgencia). Las apps que se leen
bien lo asignan a la **entidad**: en Google Maps el azul no significa "urgente" — significa TÚ
(tu punto, tu ruta). La distancia de color hace falta **entre historias**, no dentro de una.

### La lección de la v2 (por qué se re-reformuló)

La v2 pintó TODO lo del coche propio de azul (dos energías) y desterró el verde del vehículo.
**Falló en device** (2026-08-13): el usuario vio la pantalla de Vehículos "con cosas verdes y
azules" sin criterio aparente y sintió rota la identidad de la app — el verde de siempre había
desaparecido de donde siempre estuvo (historial, stats, coche activo). La lección: **el usuario ya
leía verde = coche activo y azul = BT**; ese mapeo previo ERA el sistema. La corrección no era
cambiar el mapa de colores sino quitarle al ESTADO el derecho a teñir: el nombre lleva el color de
su método (estable), y "aparcado / en ruta" es texto neutro que se anima.

---

## 2. Las historias

| Canal | Significa — y NADA más | Dónde vive |
|---|---|---|
| 🟢 **Verde marca** `PapGreen`/`PapGreenLight` | La MARCA y la acción — y NADA más | CTAs, links, nav, spinners, punto de usuario, mobiliario de gráficas |
| 🟩 **Verde vigilancia** `PapWatchGreen*` | Vehículo con **detección activa** (tier asistido) | Glifo/badge/borde/marcador del coche asistido |
| 🟢 **Verde frescura** `PapSpotFresh*` | Cabeza de la rampa de plazas — ver fila de la rampa | Puck de plaza libre fresca, chip de fiabilidad ALTA, anillo TTL |
| 🔵 **Azul `papCarBlue`** | Vehículo vigilado por **Bluetooth** | Nombre, glifo BT, badge, borde, marcador del coche BT |
| 🔵 **Azul `PapLiveMap`** (solo mapa) | Movimiento sobre tiles **que NO es tuyo** | Pin de plaza a la que va OTRO usuario. También el repliegue de un viaje sin vigilancia declarada (el gris de "sin vigilar" desaparece sobre los tiles) |
| 🟢🔵 **Identidad del coche seguido** (solo mapa) | Un viaje TUYO lleva el color de **cómo se le vigila** | Traza, punto de origen, punto de salida, halo del puck y FAB `MyLocation` mientras sigue al coche — `papWatchGreen` asistido, `papCarBlue` BT |
| ⚪ **Gris** | Vehículo **sin vigilancia** / ausencias | Nombre gris, anillo hueco, borde neutro |
| ⬛ **`onSurface`** | El ESTADO: aparcado · en ruta · sin aparcar | Texto neutro; en ruta se ANIMA (pulso + halo radar en el color de identidad) |
| 🟢🟡🔴 **Rampa frescura** | Caducidad de plazas comunitarias — EXCLUSIVA | Pucks/badges de spots; manual = badge persona sobre su tier |
| 🟣 **Violeta `PapSpotReleasing*`** | La plaza que **todavía NO está libre**: alguien acaba de subirse al coche que la ocupa | Puck, badge de fila y eyebrow del peek de una plaza `RELEASING` [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] |
| 🟠🔴 **Ámbar/Rojo** | Se acaba el tiempo / algo te necesita | Permisos, errores, destructivo, TTL crítico |
| 🚗 **Multicolor** | QUÉ coche es — el retrato, jamás mezclado con estado | Glifo ilustrado del vehículo (Nivel 3 de iconos) |

### 🟣 El violeta, y por qué no podía ser ninguno de los colores que ya había

[SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] `SpotStatus.RELEASING` dice algo que ningún otro canal
dice: *hay un coche todavía en la plaza, y se está yendo*. No es un tramo de la rampa —la rampa
mide hace cuánto se liberó un hueco, y éste no se ha liberado— así que no puede tomar prestada
ninguna de sus tres piernas sin afirmar que está libre.

⛔ **Y tampoco podía ser `PapLiveMap`**, que es el candidato obvio porque ese azul ya significa
«movimiento que no es tuyo». Lo significa en una forma CONCRETA: la plaza a la que va otro. Ese
puck y éste se dibujan en **la misma capa**, uno al lado del otro. Dos significados compartiendo
tinta en el mismo sitio es exactamente lo que esta doctrina existe para impedir.

Violeta porque es el único tono que esta paleta no había gastado nunca —verdes (marca, vigilancia,
Plus, plaza fresca), ámbar, rojo y tres azules (mapa vivo, identidad BT, parking público)— y la
separación con el más cercano está **medida**:

| Token | Hex | Medida |
|---|---|---|
| `PapSpotReleasing` (oscuro + tono fijo del puck) | `#A96BFF` | 5,62:1 sobre `PapInk`; blanco encima 3,37:1 (el suelo de la familia del puck es 2,85:1, el ámbar); **ΔE00 17,0 de `PapLiveMap`**, con el que comparte capa |
| `PapSpotReleasingMuted` | `#241040` | el tono escribe encima a 5,10:1 |
| `PapSpotReleasingLight` | `#7C3AED` | 5,70:1 sobre tarjeta blanca · 5,31:1 sobre la página clara |
| `PapSpotReleasingContainerLight` | `#EADDFF` | lecho pálido del badge |
| `PapOnSpotReleasingContainerLight` | `#4A148C` | 9,20:1 sobre su lecho |

Y el color **no va solo**: el puck cambia de glifo (coche en vez del de plaza regalada), cambia de
insignia (salida en vez de megáfono) y pierde el anillo de TTL. Un estado que un conductor puede
leer como «cuarto tono de disponible» le cuesta un viaje, así que no se confía a la tinta.

El verde tiene tres papeles —marca, coche vigilado, plaza fresca— y hasta el 29-08-2026 los tres
compartían **un solo valor**. La v3 lo defendía diciendo que las anatomías no se tocan (pastilla
rellena / nombre+glifo radar / puck con "P") y que *"la app trabajando para ti"* y *"tu coche
vigilado"* son la misma promesa.

Medido, ese argumento se cae por dos sitios:

1. **Sí se tocan.** En el mapa, el marcador del coche activo y el puck de plaza libre eran el mismo
   literal `#009F5E`, en la misma capa, a la vez, siempre. ΔE00 = 0.00.
2. **Aunque marca y coche sean la misma promesa, la plaza de otro vecino no lo es** — y la propia
   doctrina llama a la rampa de frescura *exclusiva*.

Ahora cada papel tiene su tono (§0). Marca y vigilancia siguen siendo parientes cercanos, porque esa
parte del argumento era buena: son dos caras de la app trabajando. La plaza fresca se va al lima,
que además alarga el primer escalón de su propia rampa hacia el ámbar.

---

## 3. La identidad del vehículo: su MÉTODO de vigilancia

Resolver único `ui/theme/VehicleIdentity.kt` → `vehicleIdentityColor(watch)`:

| Vigilancia | Color | Glifo junto al nombre | Borde de tarjeta | Marcador mapa |
|---|---|---|---|---|
| **Detección activa** (Coordinator/asistida) | `papWatchGreen` (**ya no** `primary`) | radar/geocerca (`Icons.Rounded.Radar`) | verde vigilancia @ 0.55 | marco `PapWatchGreenLight` |
| **Bluetooth** (automático) | `papCarBlue` | marca BT | azul @ 0.55 | marco `PapBlueLight` |
| **Sin vigilar** | gris `onSurfaceVariant` | anillo hueco | outline neutro | marco `PapOutlineVariantLight` |

⛔ **Aquí había una columna «Badge» con "ACTIVO" / "BLUETOOTH" / "INACTIVO". Retirada el 27-10-2026,
porque describía un producto que ya no existe.** La retiró
[UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001]: el tier de vigilancia **es un glifo y un color, y
ninguna superficie lo imprime como texto**. Las tres cadenas siguen vivas —`vehicleWatchPinLabel()`
en `VehicleStatusIndicators.kt`— pero **sólo como `contentDescription`** de ese glifo, que es el
único canal que le queda a un lector de pantalla y a quien no distinga el verde del gris. Borrar las
cadenas era la limpieza tentadora y la equivocada; borrar la columna es la correcta.

> ⚠️ **Y la fila sobrevivió 2 meses al cambio que la desmentía.** Costó lo que cuestan estas cosas:
> en el rediseño de la home de octubre se diseñó un badge de vigilancia **con el tier escrito**
> —«Bluetooth», «Vigilando tu sitio»— leyendo esta tabla como fuente de verdad. *Un comentario que
> sobrevive al cambio que lo desmiente cuesta más que ninguno*, y una tabla de doctrina es un
> comentario grande. Lo que la app dice del tier se lee en `VehicleStatusIndicators.kt`, no aquí;
> esta tabla dice **de qué color** se pinta, y nada más.

El color viaja con el coche a todas partes — pero lo lleva su GLIFO de método (icono/badge/punto/
borde/marco del marcador), no el texto del nombre: el nombre queda en `onSurface` (glifo + nombre
teñidos = sobreinformación). Excepción: donde no hay glifo de método (el eyebrow del peek,
"TOYOTA COROLLA · APARCADO"), el NOMBRE es quien viste el color y el estado queda neutro. El mismo
coche jamás cambia de color al cambiar de estado.

Un caso más sin glifo de método: el **FAB de coche** de la columna del mapa Home, que cicla entre
las sesiones aparcadas — su icono lleva la identidad del coche SELECCIONADO (azul BT / verde activa
/ gris sin vigilar) y queda `onSurface` mientras no hay ninguno seleccionado
[UI-FAB-CAR-IDENTITY-001]. Ojo con su vecino: el FAB de `MyLocation` se tiñe `PapLiveMap` cuando
sigue al coche en movimiento — eso es **movimiento sobre el mapa**, no identidad, y por eso es el
azul de mapa y no `papCarBlue`.

El **historial también es superficie de coche**: el detalle de un aparcamiento (acento de sus filas
meta mientras la sesión vive) y la timeline de Vehículos (puntos del raíl y relleno de la tarjeta
viva) resuelven por el mismo `vehicleIdentityColor`. **Todo lo demás de la página se queda en verde
de marca en TODAS las fichas**: la card de Actividad entera (barras, contadores, cifra del título,
tile pocos-datos), la fila de stats de la hero card, el punto de la cabecera de día, "ver en mapa",
chips de filtro y CTAs. La identidad del coche vive SOLO en su anatomía de método (borde + badge +
punto + pill) — decisión reafirmada en device el 28-08 tras probar la alternativa, ver §8. El
rótulo "APARCADO ACTUALMENTE" sigue NEUTRO — es estado, ver §3.1; sólo su punto pulsante lleva la
identidad. [UI-HISTORY-IDENTITY-AND-SOURCE-001][VEH-STATS-SAY-SOMETHING-USEFUL-001]

> 🔑 **El metal es EL MISMO en los dos temas, y eso es doctrina, no comodidad.**
> [UI-PLUS-THE-METAL-IS-AN-OBJECT-NOT-A-BACKGROUND-001]
>
> Tuvo piernas claras y fueron un error, revocado en device («en tema claro chirría bastante»).
> Dos motivos:
> - **Una pierna de tema existe para que un color siga siendo legible contra la SUPERFICIE de
>   detrás. Esta superficie es opaca y trae su propia tinta**, así que no tiene contra qué seguir
>   siendo legible. Es un OBJETO sobre la página, no un fondo. Una chapa cepillada no cambia de
>   aleación cuando enciendes la luz.
> - Las piernas claras vivían a L\*25-49 sobre una página a L\*98: **59 L\* por debajo**. En oscuro
>   el metal queda POR ENCIMA de la página y se lee como objeto iluminado; en claro quedaba muy por
>   debajo y se leía como un agujero en sombra. El metal es brillante porque REFLEJA.
>
> ⚠️ Y la causa de que se fueran oscuras conviene recordarla: la superficie tomaba su tinta de
> `colorScheme.onPrimary`, que en tema claro es **blanca**, y con tinta blanca el lecho TIENE que
> ser oscuro. Se dobló el lecho para encajar una tinta PRESTADA. Por eso existe ahora el rol
> `premiumInk`: la tinta es propiedad de esta superficie, no del primary del tema.

| Token / rol | Valor (ambos temas) | Medida |
|---|---|---|
| `premiumSweep.shadow` · `PapPremiumDeep` | `#36AA67` | L\*62,1 · C\*54,0 · 0,0° de la marca |
| `premiumSweep.bed` · `PapPremiumBed` | `#44C479` | L\*70,9 · C\*58,8 · 0,0° |
| `premiumSweep.sheen` · `PapPremiumSheen` | `#63F49D` | L\*86,9 · C\*65,5 · **0,1° del verde del LOGO** |
| `premiumInk` | `PapInk` | 6,40:1 en la parada peor |
| `premiumEdge` | blanco (oscuro) / `PapPremiumDeep` (claro) | ver abajo |

El **brillo se calibró contra el verde del LOGO** (`#25F48C`), no contra la pierna clara de la
marca: el logo es el verde que el usuario reconoce y tampoco se invierte con el tema. Comparar la
croma de un lecho de luminosidad media contra `PapGreenLight` (una pierna profunda, con la croma
limitada por el gamut a esa luminosidad) era comparar peras con manzanas — justo lo que las
matemáticas en Lab existen para evitar.

⚠️ El **filo** sí cambia con el tema, y por una razón física, no decorativa: en oscuro es blanco
(la esquina iluminada del metal); sobre página blanca un filo blanco es invisible y deja el card
sin contorno, así que allí el filo es el tono de sombra del propio barrido — la misma idea (el
borde que se aparta de la luz) apuntando al otro lado.


⛔ **Y la restricción es la INVERSA de la del trío**: el premium no debe separarse de la marca, debe
quedarse ENCIMA (≤7° de hue en Lab, y siempre más cerca de la marca que del teal de vigilancia).
La primera versión de estos tokens salió a 158,8° y 161,5° — más cerca del teal (167°) que de la
marca (150°) — y nadie lo detectó: el trío no los mira, precisamente porque premium no es una
cuarta identidad. Lo vio el user en device y preguntó si habíamos cambiado el verde de la app.
Ahora lo fija el test `the premium sweep keeps the brand's hue in both themes`, que desde que el
metal es único comprueba las MISMAS tres paradas contra las DOS piernas de marca: sin variantes de
tema, el acabado tiene que vestir la hue de la marca esté en el tema que esté.

### 3.1 El estado NUNCA tiñe — se escribe y se anima

"APARCADO / EN RUTA / SIN APARCAR" va en `onSurface`. Un viaje en curso se nota por MOVIMIENTO:

- **Halo radar** (`DrivingRadarHalo(color = identidad)`) pulsando tras el glifo del coche.
- **Pulso de texto** (`rememberDrivingStatePulse()`): las palabras de estado respiran en alfa.
- **Borde a plena intensidad** y más grueso mientras conduce — misma hue, más energía.

Prohibido: un azul/verde "de conducción" distinto del color de identidad, y la escalera de alfas
en bordes como señal (ilegible en device, probado en v1).

### 3.2 Mapa vs UI

El mapa usa tonos fijos (`PapGreenLight`/`PapBlueLight`, `SpotPalette`, `PapLiveMap`) porque los
marcadores van sobre imagen de calles, no sobre nuestra superficie; la UI usa `primary`/theme-aware.
Cambian juntas. `PapLiveMap` queda SOLO para **movimiento que no es tuyo** sobre el mapa: la plaza
a la que va otro usuario. Tu propio viaje —traza, origen, salida, halo del puck y el FAB que lo
sigue— lleva la **identidad de vigilancia** de su coche.

> **Por qué cambió** [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001]: mientras `PapLiveMap` cubría las dos
> cosas, la traza de un coche BT quedaba a ΔE00 **10,0** de `PapCarBlueLight` — dos azules casi
> iguales, dos significados, la misma pantalla; medido y declarado como defecto abierto en
> `UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001` §5. Al vestir el viaje con la identidad de su coche,
> el par deja de existir: en un viaje BT los dos azules pasan a decir lo mismo. Y el verde asistido
> no invade la rampa de plazas — `papWatchGreen` está a ΔE00 19,7–27,7 de toda la rampa, porque es
> un teal, no el verde de marca.

## 5. La procedencia de un DATO es un glifo

El origen de un dato comunitario se dibuja con pictograma, nunca con color propio:

| Procedencia | Glifo |
|---|---|
| Reporte manual | persona (`drawPersonBadge`, ya existe) |
| Antigüedad | reloj (`drawClockBadge`, ya existe) |

### `MANUAL` sale de la escala de fiabilidad (F5)

`SpotReliabilityUiState` mezcla HIGH/MEDIUM/LOW (frescura, escala continua) con MANUAL
(procedencia, categoría). Un reporte manual *también* tiene frescura y hoy la pierde
(`ringFraction = null`, azul plano). Pasa a: **color de frescura normal + badge de persona +
anillo de TTL recuperado**.

---

## 6. Tabla de referencia — qué se ve

| Caso | Se ve |
|---|---|
| Chip/ficha del Kamiq (BT) | glifo BT + borde AZULES, nombre `onSurface`, estado neutro |
| Chip del Corolla (activa) en ruta | glifo radar + borde VERDES, nombre `onSurface`, estado pulsando + halo radar verde |
| Eyebrow del peek "TOYOTA COROLLA · APARCADO" | nombre en su color de método, "· APARCADO" neutro (sin glifo, el nombre lleva el color) |
| Coche sin vigilar | anillo hueco + nombre y estado neutros |
| Fila de detección "Vigilando tu X" | barra/tile del color del método del coche |
| Historial / stats / gráfica | verde `primary` (mobiliario de la app, como siempre) |
| Plaza libre fresca / enfriándose / caducando | verde / ámbar / rojo |
| Reporte manual | color de frescura + badge persona + anillo TTL (F5) |
| Plaza a la que va otro usuario | `PapLiveMap` (movimiento ajeno, solo mapa) |
| Plaza que se está liberando (coche dentro, arrancando) | `PapSpotReleasing` — puck VIOLETA con glifo de coche, insignia de salida y **sin anillo de TTL** |
| Parking público (marcador y fila) | `PapColor.carPark` — puck AZUL relleno con la P blanca [MAP-A-CAR-PARK-MARKER-WEARS-ITS-OWN-BLUE-001] |
| Traza · origen · salida · halo del puck · FAB que sigue al coche | identidad de vigilancia del coche seguido [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001] |
| Marcador de mi coche en mapa | tag cuadrado con marco verde/azul/gris según método |
| CTA | verde `primary`, pastilla rellena |
| Opción de Tema en Ajustes | círculo con anillo: blanco (`PapCardLight`) / tinta (`PapInk`) / mitad y mitad — es una MUESTRA de la superficie que pintaría, no identidad ni estado |

---

## 7. Guardarraíles

Test Konsist (`ColorGuardrailTest`, F6), igual que tipografía y dividers:

1. **Prohibido `colorScheme.tertiary`** en `presentation/` y `ui/components/` — retirado (F6).
2. **El color de un vehículo sale SOLO del resolver único** (`vehicleIdentityColor`); prohibido
   teñir el ESTADO del vehículo con un color — el estado es texto `onSurface` (+ animación).
3. **Prohibido declarar `Color(0x…)` literal** en `presentation/` — los valores viven en `ui/theme/`.
4. Todo token nuevo en `Color.kt` exige una fila en §2/§3 con su historia única.
5. **Un hex, una historia** — `no two colour stories share a hex`
   [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]. La regla 4 era prosa, así que nadie la ejecutaba y
   **cinco pares de tokens** volvieron a compartir valor bajo nombres distintos: exactamente el
   desorden que §1 describe como el problema original, sobreviviendo renombrado. Dos tokens pueden
   compartir valor **sólo declarando la misma historia** en la tabla `PALETTE` del test; una
   coincidencia sin declarar falla. El quinto par (`PapAmberMuted` = `PapOnAmberContainerLight`
   = `#3D2A10`) **lo encontró el test, no el ojo, en su primera ejecución**.
6. **Los tres verdes no vuelven a juntarse** — `the three greens stay perceptually apart in both
   themes`. La igualdad exacta de hex **no** es el invariante: un futuro ΔE00 de 2 sería igual de
   indistinguible y pasaría la regla 5. El suelo es perceptual (≥ 14° de tono, ≥ 18 CIE76 en Lab, en
   los dos temas) y vive en el TONO, por la razón de §8 (2026-08-29, fase 2).
7. **Una pata no es un rol** — `feature code reads roles, never theme legs`
   [UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001]. `PapSpotFreshMuted` no es "el color de plaza fresca":
   es ese color **en el tema oscuro**, media historia. Nombrar una pata desde `presentation/` o
   `ui/components/` es decidir el tema a mano, y quien lo decide a mano puede olvidarse: la píldora
   «Hace N min» nombró las tres patas oscuras y ninguna clara, y pintó una pastilla negruzca sobre
   sheet blanco durante meses **sin que ningún barrido lo viera**, porque todos los tokens que leía
   eran los correctos. Un barrido pregunta QUÉ token lee un sitio; el fallo estaba en CÓMO lo
   elegía. Prohibir la pata sí lo ve, porque un `when` ciego al tema necesita nombrarla para
   existir. El feature layer pide ROL: `PapColor`, `stateColors()`, `vehicleIdentityColor()`,
   `colorScheme`. Exentos (y por qué no pintan sobre nuestra superficie): los marcadores y el chrome
   del mapa —paleta fija sobre teselas, la misma foto en los dos temas— y las muestras del selector
   de tema, que enseñan el tema CONTRARIO a propósito.

---

## 7.1 Roles de color — `ui/theme/PapColor.kt` [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]

> **El color lo decide el TRABAJO, no el widget. Dos trabajos pueden compartir un hex sólo si este
> sistema declara que son la misma promesa — y nunca si se ven a la vez.**

Es el gemelo de la regla de tipografía (*el rol posee su peso*). Nace de una medición:
`colorScheme.primary` hacía **nueve trabajos distintos** en 50 call sites de 26 ficheros — acción,
selección, foco, logro, mobiliario de marca, identidad de coche asistido, mapa, spinner y, en
`VehicleColorLabels`, **un dato AUSENTE**. Un token al que se le pide por nueve nombres que no tiene
no se puede razonar, ni cambiar para un trabajo sin cambiarlo para los otros ocho.

| Rol | Historia |
|---|---|
| `action` / `onAction` | algo que se PULSA: CTA, link, botón de footer, confirmar de diálogo |
| `selected` | "esta opción es la ELEGIDA" — selectores, segmentos, puntos de página |
| `focus` | el campo que tiene el CURSOR |
| `progress` | un escalón CONSEGUIDO: permiso concedido, tier alcanzado |
| `brandData` | mobiliario de marca sobre datos: gráfica, tiles de icono, overlines, cifras |
| `attention` / `onAttention` | algo PENDIENTE que puedes arreglar: permiso sin dar, GPS pobre |
| `danger` / `onDanger` | bloqueado, destructivo o fallido — **nunca** un CTA |
| `live` | MOVIMIENTO sobre teselas: traza, origen, pin en-route, FAB sigue-coche |

Varios roles resuelven hoy al mismo valor. **No es redundancia**: un rol con nombre puede divergir
luego sin arqueología, y nombrarlo obliga a que compartir sea una decisión escrita en vez de un
accidente sin fecha.

Lo que **no** vive ahí: la identidad de vehículo (`vehicleIdentityColor`), la rampa de frescura
(`stateColors()`) y los neutros (`onSurface`/`onSurfaceVariant` + `PapAlpha`), que ya son
inequívocos.

La rampa se resuelve en `ui/theme/SpotStateColors.kt` y **sólo ahí** — cuatro campos, uno por
trabajo: `bg` (relleno, suelo 3:1) · `text` (la palabra sobre la superficie, 4.5:1) ·
`container`/`onContainer` (la píldora tonal: cama suave + su contenido). Ningún consumidor elige
entre patas; si un trabajo nuevo necesita otro par, se añade aquí, no en la pantalla.

---

## 8. Decisiones registradas

### Un parking público es un SITIO, no un coche ni una plaza [MAP-A-CAR-PARK-MARKER-WEARS-ITS-OWN-BLUE-001]

Los dos azules del mapa hablaban de MOVIMIENTO: `papCarBlue` es el método de vigilancia de un coche
y `PapLiveMap` es movimiento que no es tuyo. Un parking público no es ninguna de las dos cosas: es
una instalación de un tercero en la que entras, y la P blanca sobre azul es la señal que cualquier
conductor ya lee en la calle.

Por eso lleva **token propio** (`PapCarParkBlueDark` / `PapCarParkBlueLight`) y no toma prestado el
azul del BT. Un color aquí significa UNA cosa: un parking vistiendo el azul del Bluetooth haría que
el mismo tono dijera «este coche está vigilado por BT» y «esto es un parking» en la misma pantalla.

**Por qué el relleno y no el borde.** Antes era cuerpo papel con borde verde, y la razón escrita era
buena: una plaza libre es un puck RELLENO con su nivel de frescura (verde → ámbar → rojo), y
compartir ese relleno habría convertido cada parking en lo que parece una plaza recién liberada.
El azul resuelve eso mejor que el borde, porque **el azul no está en la rampa de frescura**: no hay
forma de confundirlo con un nivel. El relleno pasa a decir de QUIÉN es la cosa, nunca cómo de fresca
está.

⚠️ **El azul está apretado.** En superficie oscura este tono queda a 87/765 de `PapCarBlueDark` y a
94/765 de `PapLiveMap`. Lo que los separa en uso no es el tono sino QUÉ los lleva: un puck relleno
(parking), un glifo de vehículo (BT) y una traza (movimiento ajeno). Si en device se leen como un
solo azul, el plan B es un navy fijo en ambos temas, como ya hace `PapLiveMap`.

### Un mapa de calor es INTENSIDAD, no un tono nuevo [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001]

La rejilla «¿Cuándo aparcas?» necesita una escala secuencial, y lo obvio —una rampa de tonos— está
prohibido dos veces: la rampa verde→amarillo→rojo es **exclusiva de la caducidad de plazas**, y un
tono nuevo tendría que ganarse un significado que no tiene [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001].

La escala es `brandData` a **alpha variable**, que es lo que el gráfico de barras ya hace para decir
«menos» (`INACTIVE_BAR_ALPHA`): un mapa de calor es esa misma idea en dos dimensiones. Y una celda
**sin dato** no se pinta pálida — se queda en el listón vacío de una barra sin relleno, porque un
verde tenue diría «poco» donde la respuesta honesta es «nada».


- **2026-08-11 (v1, revocada)** — "aparcado pierde el color; vigilancia = cian en el chasis".
  Rechazada por el usuario en device: garaje ilegible, cian incomprensible. Se conserva de la v1
  la parte de ingeniería (resolver único, azul-vivo unificado, rampa exclusiva de plazas, retiro
  de `tertiary`, guardarraíles) y se corrige la asignación: **el color va por entidad/historia,
  no por urgencia**.
- **2026-08-11 (v2, revocada)** — mi coche = familia azul con dos energías; verde jamás para el
  coche propio. Rechazada por el usuario en device (2026-08-13): rompía la identidad verde de la
  app y el mapeo que él ya leía (verde = activo, azul = BT).
- **2026-08-13 (v3, vigente)** — identidad del vehículo = MÉTODO (verde activa / azul BT / gris
  off), estable en todas las superficies incluido el marcador; el ESTADO se escribe en `onSurface`
  y se anima en ruta (pulso + halo radar del color de identidad); glifo del tier asistido pasa de
  diana a radar/geocerca; historial/stats/selector vuelven al verde de marca; `papLive` UI muere —
  `PapLiveMap` queda solo-mapa. Se conservan de v1/v2: resolver único, retiro de `tertiary`,
  MANUAL como procedencia (F5), rampa exclusiva de plazas, tokens AA, guardarraíles.
- **2026-08-29 (UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001, fase 1)** — la tabla se audita con
  medición (contraste WCAG 2.1 + CIEDE2000), no a ojo. Lo que salió:
  - ⚠️ **El `primary` del tema claro NO cumple AA como texto**: `#009F5E` mide **3.01:1** sobre el
    scaffold `PapAzure`, con 4.5:1 de mínimo — mientras el azul BT de al lado mide 5.72:1. La frase
    «tokens AA» heredada de la v1 es falsa para este token. Se cambió a `#237A46` y **el user lo
    revocó en device el mismo día** (ver la entrada de fase 2): el verde corporativo no se toca.
    Queda como **deuda conocida y aceptada**, no como despiste: como relleno y como glifo/borde
    (objeto gráfico, suelo 3.0) cumple; donde es texto verde pequeño sobre claro, no. Si se arregla,
    se arregla dándole al TEXTO su propia variante oscura — nunca apagando la marca.
  - **Cinco pares de tokens compartían valor**: `PapGreenLight`/`PapGreenOutlineLight`/
    `SpotPalette.Green` (`#009F5E`), `PapBlue`/`PapCarBlueDark` (`#5B9EFF`), `PapBlueLight`/
    `PapCarBlueLight` (`#0057CA`), `PapGreen`/`SpotPalette.LegacyGreen` (`#25F48C`) y
    `PapAmberMuted`/`PapOnAmberContainerLight` (`#3D2A10`). `PapBlue`, `PapBlueLight` y `PapForest`
    (muerto) se borran; el esquema respalda sus slots azules con los tokens de coche-BT;
    `PapGreenOutlineLight` queda como **alias declarado** de `PapGreenLight`.
  - **Tres bugs de criterio**: el punto pulsante de la sesión viva tenía el verde de marca
    *hardcodeado* por defecto (`PulsingDot`) cuando es justo el elemento que lleva la identidad
    (§3); el banner de GPS pintaba `Color.White` crudo sobre un relleno semántico; y un color de
    coche **desconocido** se pintaba con el verde de marca — un dato ausente vestido de identidad.
  - Nace §7.1 (roles) y el guardarraíl §7.5.
- **2026-08-29 (UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001, fase 2)** — **los tres verdes se separan**.
  Espectro con la marca en medio: `FRESH` lima h≈128 · `BRAND` menta h≈151 · `WATCH` teal-esmeralda
  h≈166. Tokens nuevos: `PapWatchGreen` `#0FBF9A` / `PapWatchGreenLight` `#05876D` ·
  `PapSpotFresh` `#8FE83C` / `PapSpotFreshMuted` `#0F3B08` / `PapSpotFreshLight` `#398701`.
  Valores claros finales tras el juicio en device: `PapGreenLight` `#009F5E` (intacto) y
  `PapWatchGreenLight` `#05876D`.
  `vehicleIdentityColor(Assisted)` deja de ser `colorScheme.primary`, y la rampa de frescura deja de
  usar `PapGreen`/`PapGreenMuted`.
  - **La separación va en el TONO, nunca en la luminosidad ni en el alfa.** No es preferencia: la v1
    se revocó en device porque su distinción más importante viajaba en el alfa de un borde de 1dp,
    un eje de ÉNFASIS. Dos verdes separados sólo por claridad se leen como *"el mismo color, más
    flojo"* — el mismo error con otro sombrero. Todos los pares: ΔE00 ≥ 12.5 y ≥ 14° de tono en
    ambos temas.
  - **Cada valor cumple el suelo del trabajo que hace**, no un suelo genérico: la plaza fresca no es
    texto sino un RELLENO que lo lleva, así que se le exige 4.5:1 contra su etiqueta y 3:1 contra su
    cama — pedirle 4.5:1 como texto es lo que estrangulaba el tema claro y producía un verde-gris
    apagado (C\*=21) igual al que hundió la v1.
  - Guardarraíl §7.6: los tres verdes no pueden volver a juntarse, con suelo **perceptual** (ΔE76 y
    ángulo de tono), porque la igualdad exacta de hex no es el invariante — ser indistinguibles lo es.
  - ✅ **Visto en device (Oppo, mock, 29-08)** — y el tema claro se corrigió ahí mismo:
    - El **oscuro se aprobó tal cual**: `#25F48C` · `#0FBF9A` · `#8FE83C`.
    - El **claro se revocó**: *«te has cargado el verde corporativo del logotipo, has metido un verde
      oscuro camuflaje»*. Causa medida: exigir 4.5:1 **como texto** sobre un scaffold casi blanco
      arrastra los tres verdes a L\*=31-45, entre 15 y 25 puntos por debajo del corporativo. El peor
      era el de vigilancia, `#00543D` (L\*=31, C\*=30): oscuro **y** apagado a la vez.
    - Claro definitivo: marca **`#009F5E` restaurada intacta** · vigilancia **`#05876D`** (L\*=50, la
      mitad más de croma que el revocado) · plaza **`#398701`**.
    - **La rampa de spots deja de tomar prestados el ámbar y el rojo DEL TEMA** (`PapAmberLight`,
      `PapRedLight`), que son los de warning y error y por eso son oscuros por necesidad: eso hacía
      que los spots se leyeran apagados **como conjunto**, no sólo el verde. La rampa estrena sus
      tres tokens claros — `#5FBF1F` · `#E08200` · `#E0322F` — que son **los del mapa**: una sola
      rampa, los mismos tres tonos en el sheet y sobre las teselas.
    - ⚠️ **Deuda aceptada, decidida en device viendo las dos versiones**: como TEXTO sobre tarjeta
      blanca esos tres miden 2.34 / 2.85 / 4.50 : 1. La versión que cumplía AA se juzgó demasiado
      oscura para lo que esto es — una señal de frescura, no prosa. **La forma de tener las dos
      cosas** es pintar la etiqueta de fiabilidad como badge RELLENO (relleno vivo + texto tinta,
      8.07:1) en vez de como texto de color; sus campos todavía se llaman `badgeBg`/`badgeFg` de
      cuando lo era. Queda como follow-up explícito, no hecho a escondidas.
    - El puck del mapa conserva su lima profundo (`PapSpotFreshMap` `#398701`): lleva una "P" blanca
      encima y el lima vivo la dejaría en 1.9:1.
  - 🐞 Encontrado **en device, no por el barrido**: `HomeDetectionSurface` escribía la pierna verde
    de su tono de identidad como `cs.primary`. Correcto mientras marca y vigilancia eran el mismo
    valor, y silenciosamente falso en cuanto dejaron de serlo — la fila que dice «vigilando ESTE
    coche» vestía el color de la app. Es el modo de fallo típico de este refactor: separar dos
    tokens convierte cada `primary` mal puesto en un bug visible.
- **2026-08-11** — `MANUAL` fuera de `SpotReliabilityUiState` (F5): frescura ≠ procedencia.
- **2026-08-29 (UI-THEME-OPTION-SHOWS-ITS-THEME-001)** — el selector de Tema es el **único** sitio
  donde se pinta a propósito el color del tema CONTRARIO: cada opción lleva un círculo con la
  superficie que aplicaría (`PapCardLight`, `PapInk`, o las dos mitades para *Sistema*), y ocupa el
  hueco del check de M3 — el relleno `primaryContainer` del segmento ya dice cuál está elegida. No
  es una excepción a "el color va por historia": ahí el color **es** el dato, un muestrario, igual
  que en una carta de pinturas. No hay tokens nuevos y no se extiende a ningún otro ajuste.
- **2026-09-03 (UI-COLOR-THE-DEFAULT-SWATCH-MUST-SHOW-ITS-PAINT-001)** — la fila de colores de
  vehículo **es** el otro muestrario, y se le había aplicado la regla de los roles semánticos. La
  muestra de "Predeterminado" se pintaba con el rol `unknown` («un dato que no tenemos»), pero con
  `color = null` el coche se dibuja en verde marca (`defaultCarPalette`): la carta prometía gris y
  entregaba verde, y el glifo de coche metido dentro de la burbuja existía sólo para disculparlo —
  además prestado del eje de TIPO (`Icons.Rounded.DirectionsCar`, que dice coche-vs-moto en
  `VehicleTypeSelector`). Aquí no había hueco al que ponerle ropa: el verde no es una etiqueta para
  "no sé el color", es el color con el que se pinta. **Invariante: una muestra enseña el relleno que
  aplicará, sin excepciones**, y lo cumple leyendo la MISMA función que pinta el coche, así que
  muestra y pictograma no pueden divergir. El anillo de seleccionado (`selected` = verde marca) se
  separa del relleno con un hueco de superficie, que es lo que le permite leerse sobre el verde — y
  de paso despega el anillo del negro. El rol `unknown` se retira: no tenía **ni un** consumidor, y
  su historia escrita era justo la decisión revertida.
- **2026-08-27/28 (episodio cerrado: la v3 se reafirma)** — el user pregunta en device si en la
  ficha BT "chocan los azules con el verde" y si conviene uniformizar cada ficha con su color.
  Se probaron DOS alternativas en el Redmi y se revocaron AMBAS con él delante:
  1. Fila de stats de la hero card en neutro (27-08) → revocada ("las stats estaban bien").
  2. Chart de Actividad vistiendo la identidad del coche — azul en BT, y neutro fuerte para el
     coche sin vigilar porque sus barras en `onSurfaceVariant` se leían como zona deshabilitada
     (28-08) → revocada entera; existió brevemente un resolver `vehicleDataAccent` y se retiró.
  **Decisión vigente: la v3 tal cual estaba.** El tema es VERDE en toda la página y en todas las
  fichas (chart, stats, filtros, CTAs); la identidad del coche vive solo en su anatomía de método
  (borde + badge + punto + pill). Antes de reabrir esto: las dos variantes ya se vieron en
  pantalla y perdieron contra la v3.

---

## 9. Alphas de énfasis de texto — `PapAlpha` (`ui/theme/Alpha.kt`)

La escala única para des-enfatizar texto sobre `onSurface`. Nació en SETTINGS-AUDIT-REMEDIATION-001
(2026-08-28): los mismos cuatro números vivían como constantes privadas en ~16 ficheros de
presentación, y el mismo NOMBRE valía 0.55 en Settings y 0.65 en `CarbodyInfoCard`. Los alphas de
BORDES siguen en `PapBorders` — esta escala es solo de contenido.

| Token | Valor | Historia única |
|---|---|---|
| `PapAlpha.body` | 0.65 | Prosa que se LEE: cuerpo de diálogos, empty states |
| `PapAlpha.subtitle` | 0.55 | El subtítulo estándar junto a un título a plena fuerza |
| `PapAlpha.muted` | 0.5 | Meta de apoyo: hints, subtítulos de filas de Settings, valores trailing |
| `PapAlpha.disabled` | 0.38 | Contenido deshabilitado (el 0.38 canónico de Material) |
| `PapAlpha.dim` | 0.3 | Lo más tenue: chevrons, separadores, bordes deshabilitados |
