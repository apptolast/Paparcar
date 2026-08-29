# Sistema de color de Paparcar — doctrina

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
| 🔵 **Azul `PapLiveMap`** (solo mapa) | Movimiento sobre tiles | Traza del viaje, punto de origen, pin en-route, FAB sigue-coche |
| ⚪ **Gris** | Vehículo **sin vigilancia** / ausencias | Nombre gris, anillo hueco, borde neutro |
| ⬛ **`onSurface`** | El ESTADO: aparcado · en ruta · sin aparcar | Texto neutro; en ruta se ANIMA (pulso + halo radar en el color de identidad) |
| 🟢🟡🔴 **Rampa frescura** | Caducidad de plazas comunitarias — EXCLUSIVA | Pucks/badges de spots; manual = badge persona sobre su tier |
| 🟠🔴 **Ámbar/Rojo** | Se acaba el tiempo / algo te necesita | Permisos, errores, destructivo, TTL crítico |
| 🚗 **Multicolor** | QUÉ coche es — el retrato, jamás mezclado con estado | Glifo ilustrado del vehículo (Nivel 3 de iconos) |

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

| Vigilancia | Color | Glifo junto al nombre | Badge | Borde de tarjeta | Marcador mapa |
|---|---|---|---|---|---|
| **Detección activa** (Coordinator/asistida) | `papWatchGreen` (**ya no** `primary`) | radar/geocerca (`Icons.Rounded.Radar`) | "ACTIVO" | verde vigilancia @ 0.55 | marco `PapWatchGreenLight` |
| **Bluetooth** (automático) | `papCarBlue` | marca BT | "BLUETOOTH" | azul @ 0.55 | marco `PapBlueLight` |
| **Sin vigilar** | gris `onSurfaceVariant` | anillo hueco | "INACTIVO" | outline neutro | marco `PapOutlineVariantLight` |

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

| Token | Dark | Light |
|---|---|---|
| `papCarBlue` (BT, theme-aware) | `#5B9EFF` (7.0:1 sobre `PapInk`) | `#0057CA` (6.5:1 sobre blanco) |
| `PapLiveMap` (movimiento, fija solo-mapa) | `#2F6BFF` | `#2F6BFF` |
| `vehicleIdentityContainer(watch)` / `onVehicleIdentityContainer(watch)` — relleno SÓLIDO de una tarjeta que ES un coche (la sesión viva del historial), y su color de contenido. Nace porque `primaryContainer` solo existe en verde: un coche BT anunciaba su sesión viva en el color del tier asistido. Se probó un wash traslúcido de la identidad y se descartó en device: la tarjeta viva tiene que leerse RELLENA. **Desde la fase 2 la pierna verde tiene su PROPIA cama** (`PapWatchGreenMuted` / `PapWatchGreenContainerLight` + `PapOnWatchGreenContainerLight`): era el `primaryContainer` del esquema, y al separarse los verdes eso habría rellenado la tarjeta de un coche vigilado con el verde de MARCA mientras su punto y su borde ya eran teal. | `PapBlueMuted` + `papCarBlue` | `PapBlueContainerLight` + `PapOnBlue` |

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
Cambian juntas. `PapLiveMap` queda SOLO para movimiento sobre el mapa (traza, origen, en-route,
FAB sigue-coche); el punto de usuario es verde marca, como siempre.

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
| Plaza en ruta · traza · origen | `PapLiveMap` (movimiento, solo mapa) |
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
| `unknown` | un dato que NO tenemos — neutro a propósito |

Varios roles resuelven hoy al mismo valor. **No es redundancia**: un rol con nombre puede divergir
luego sin arqueología, y nombrarlo obliga a que compartir sea una decisión escrita en vez de un
accidente sin fecha.

Lo que **no** vive ahí: la identidad de vehículo (`vehicleIdentityColor`), la rampa de frescura
(`stateColors()`) y los neutros (`onSurface`/`onSurfaceVariant` + `PapAlpha`), que ya son
inequívocos.

---

## 8. Decisiones registradas

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
