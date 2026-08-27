# Sistema de color de Paparcar — doctrina

> Fuente de verdad del significado del color. Los valores viven en `ui/theme/Color.kt` y
> `ui/theme/Theme.kt`; **este documento manda sobre por qué existe cada token**.
> Creado 2026-08-11. v1 (color-por-urgencia) descartada en device; v2 (mi coche = azul) descartada
> en device 2026-08-13 — el usuario perdió el verde de identidad que la app siempre tuvo. v3
> vigente: **identidad del vehículo = MÉTODO de vigilancia; el estado es texto**.
> [UI-COLOR-DOCTRINE-001]

---

## 0. La doctrina en una frase

> **La app es VERDE (marca). El color del NOMBRE de un coche dice CÓMO se le vigila: verde =
> detección activa, azul = Bluetooth, gris = sin vigilar. El estado (aparcado / en ruta / sin
> aparcar) se ESCRIBE en `onSurface` y se anima — nunca se tiñe.**

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
| 🟢 **Verde primario** | La MARCA y la acción (tema de siempre) + vehículo con **detección activa** | CTAs, links, nav, spinners, punto de usuario · nombre/glifo/borde/marcador del coche asistido |
| 🔵 **Azul `papCarBlue`** | Vehículo vigilado por **Bluetooth** | Nombre, glifo BT, badge, borde, marcador del coche BT |
| 🔵 **Azul `PapLiveMap`** (solo mapa) | Movimiento sobre tiles | Traza del viaje, punto de origen, pin en-route, FAB sigue-coche |
| ⚪ **Gris** | Vehículo **sin vigilancia** / ausencias | Nombre gris, anillo hueco, borde neutro |
| ⬛ **`onSurface`** | El ESTADO: aparcado · en ruta · sin aparcar | Texto neutro; en ruta se ANIMA (pulso + halo radar en el color de identidad) |
| 🟢🟡🔴 **Rampa frescura** | Caducidad de plazas comunitarias — EXCLUSIVA | Pucks/badges de spots; manual = badge persona sobre su tier |
| 🟠🔴 **Ámbar/Rojo** | Se acaba el tiempo / algo te necesita | Permisos, errores, destructivo, TTL crítico |
| 🚗 **Multicolor** | QUÉ coche es — el retrato, jamás mezclado con estado | Glifo ilustrado del vehículo (Nivel 3 de iconos) |

El verde tiene tres papeles (marca, coche activo, plaza fresca) en anatomías que no se tocan:
pastilla rellena grande / nombre+glifo radar del coche / puck pequeño con "P". Ese verde compartido
es deliberado: "la app trabajando para ti" y "tu coche vigilado por la app" son la misma promesa.

---

## 3. La identidad del vehículo: su MÉTODO de vigilancia

Resolver único `ui/theme/VehicleIdentity.kt` → `vehicleIdentityColor(watch)`:

| Vigilancia | Color | Glifo junto al nombre | Badge | Borde de tarjeta | Marcador mapa |
|---|---|---|---|---|---|
| **Detección activa** (Coordinator/asistida) | verde `primary` | radar/geocerca (`Icons.Rounded.Radar`) | "ACTIVO" | verde @ 0.55 | marco `PapGreenLight` |
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
viva) resuelven por el mismo `vehicleIdentityColor`. Se quedan en verde de marca lo que NO es el
coche: el punto de la cabecera de día, el botón "ver en mapa" (es una acción) y la fila de stats de
la hero card. Y el rótulo "APARCADO ACTUALMENTE" se queda NEUTRO — es estado, ver §3.1; sólo su
punto pulsante lleva la identidad del coche. [UI-HISTORY-IDENTITY-AND-SOURCE-001]

| Token | Dark | Light |
|---|---|---|
| `papCarBlue` (BT, theme-aware) | `#5B9EFF` (7.0:1 sobre `PapInk`) | `#0057CA` (6.5:1 sobre blanco) |
| `PapLiveMap` (movimiento, fija solo-mapa) | `#2F6BFF` | `#2F6BFF` |
| `vehicleIdentityContainer(watch)` / `onVehicleIdentityContainer(watch)` — relleno SÓLIDO de una tarjeta que ES un coche (la sesión viva del historial), y su color de contenido. Nace porque `primaryContainer` solo existe en verde: un coche BT anunciaba su sesión viva en el color del tier asistido. La pierna verde ES el `primaryContainer` del esquema; la azul es su espejo con los tokens que el esquema ya usa en sus slots azules. Se probó un wash traslúcido de la identidad y se descartó en device: la tarjeta viva tiene que leerse RELLENA. | `PapBlueMuted` + `papCarBlue` | `PapBlueContainerLight` + `PapOnBlue` |

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

---

## 7. Guardarraíles

Test Konsist (`ColorGuardrailTest`, F6), igual que tipografía y dividers:

1. **Prohibido `colorScheme.tertiary`** en `presentation/` y `ui/components/` — retirado (F6).
2. **El color de un vehículo sale SOLO del resolver único** (`vehicleIdentityColor`); prohibido
   teñir el ESTADO del vehículo con un color — el estado es texto `onSurface` (+ animación).
3. **Prohibido declarar `Color(0x…)` literal** en `presentation/` — los valores viven en `ui/theme/`.
4. Todo token nuevo en `Color.kt` exige una fila en §2/§3 con su historia única.

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
- **2026-08-11** — `MANUAL` fuera de `SpotReliabilityUiState` (F5): frescura ≠ procedencia.

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
