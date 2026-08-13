# UI-COLOR-DOCTRINE-001 — Ordenar la identidad cromática (un color = un significado)

> **Estado**: 🟡 F1-F6 COMPLETAS en rama `feature/UI-COLOR-DOCTRINE-001-color-identity`
> (worktree `../Paparcar-color`), SIN mergear. ⏳ revisión visual final en device → merge.
> Doctrina reformulada a v2 tras rechazar la v1 en device (ver Reformulación abajo).
> Doctrina: [`docs/design/COLOR-SYSTEM.md`](../design/COLOR-SYSTEM.md).
> Origen: el usuario reporta que verde y azul "chirrían" — un coche con BT aparcado muestra el
> nombre en verde. Analizado y decidido 2026-08-11.
> Prioridad: **P1** (deuda de diseño, no bloquea campo).

## Problema

Tres ejes ortogonales comparten un único canal (el color): **estado** (aparcado/conduciendo/
libre/caducando), **método/procedencia** (BT/Coordinator/manual/auto) y **acción** (CTA de marca).

- **Verde = 8 significados**: marca/CTA · plaza HIGH (`SpotStateColors.kt:16`) · TTL sano
  (`SpotIndicators.kt:77`) · vehículo monitorizado (`vehicleStatusAccent` → `Active`) · vehículo
  aparcado (`vehicleBadgeAccent` → `Parked`) · fase candidato (`HomeParkingRow.kt:146,262`) ·
  eyebrow de acción (`PapSheet.kt:227`) · tono "quiet" (`HomeDetectionSurface.kt:118`).
- **Tres azules, cuatro significados**: `PapBlue` #5B9EFF→`tertiary` (manual + BT + zona privada +
  info) · `PapDriveBlue` #2F6BFF (conducción, traza, halo, FAB) · `SpotPalette.EnRouteBlue`
  #2F6BFF · `SpotPalette.ManualBlue` #0057CA.
- **Colisión exacta en tema claro**: `PapBlueLight` #0057CA **==** `SpotPalette.ManualBlue`
  #0057CA. "Coche con Bluetooth" y "plaza reportada a mano" son el mismo color.
- **#5B9EFF vs #2F6BFF** son indistinguibles a 16 dp: "usa BT" y "está conduciendo" se pintan
  igual sobre el mismo objeto.
- **Dos resolvers rivales** pintan el vehículo con ontologías distintas:
  `vehicleStatusAccent(status)` (sabe de método, ignora si está aparcado) y
  `vehicleBadgeTone(isParked, isBluetoothPaired)` (sabe de ambos y **da prioridad al método**).
  El mismo coche es verde en el chip y azul en el peek sin cambiar de estado.
- El comentario de `ParkingPeek.kt:82` dice *"drive-blue when BT-paired"* pero el código devuelve
  `tertiary` (#5B9EFF), no `PapDriveBlue`: la confusión ya está sedimentada en la documentación
  del propio código.
- Mismo error un nivel abajo: `SpotReliabilityUiState` mezcla HIGH/MEDIUM/LOW (frescura, escala
  continua) con MANUAL (procedencia, categoría). El manual pierde su anillo de TTL
  (`tierVisual()` → `ringFraction = null`).

## Doctrina (v2 vigente)

Ver [`COLOR-SYSTEM.md`](../design/COLOR-SYSTEM.md). Resumen: *el color dice DE QUIÉN es la
historia; dentro de una historia, el estado va por intensidad, movimiento y forma.* Verde = la
oferta + marca (nunca el coche propio) · Azul = mi coche (calmado `papCarBlue` en reposo, vivo
`papLive` en movimiento) · ámbar/rojo = te necesita · gris = sin vigilar. La procedencia es un
glifo.

## 🔄 Reformulación v2 (2026-08-11) — validada en device, la v1 se descarta

La v1 ("el reposo no gasta color; vigilancia = cian con alfas 0.70/0.35 en el borde") se instaló
como mock en Oppo+Redmi y **el usuario la rechazó**: garaje de tarjetas grises ilegible, el cian
incomprensible, "aparcado" en gris parecía apagado. Dos fallos medibles: la distinción BT/asistido
iba en la señal más débil posible (alfa de un borde de 1dp), y "aparcado" se trató como estado
menor cuando emocionalmente es el éxito del producto.

Error de fondo: el color estaba asignado al eje URGENCIA. La v2 lo asigna a la ENTIDAD/historia
(como Google Maps: azul = tú): **verde = la oferta · azul = mi coche (calmado en reposo, vivo en
movimiento) · ámbar/rojo = te necesita · gris = sin vigilar**. BT vs asistido pasa a FORMA (glifo
BT vs radar), mismo azul calmado. La clave: la distancia de color hace falta ENTRE historias, no
dentro de una — confundir azul calmado con azul vivo es inofensivo (ambos = "mi coche").

De la v1 sobrevive toda la ingeniería: resolver único, `papLive` unificado con AA arreglado,
rampa exclusiva de plazas, marco neutro del tag en mapa (la forma dice "mío"), retiro de
`tertiary`, guardarraíles. Solo cambió QUÉ color asigna el resolver.

## ~~⚠️ Riesgo aceptado — `PapWatch`~~ (v1, revocado — el cian murió con la v2)

El usuario pidió explícitamente identidad cromática para la vigilancia: *"si vehículo activo y por
BT, para diferenciarlos, si no le damos identidad cromática no me cuadra; a BT le representa mucho
el azul"*.

**Es una excepción consciente a la doctrina**: reintroduce el *método* en el canal del color, que
es justo lo que causó el desorden. Se acepta con tres contenciones, y si alguna se rompe el
problema vuelve:

1. **Canal físico separado.** `PapWatch` vive sólo en el **chasis** (borde de tarjeta + glifo
   antes del nombre). Nunca es el acento de estado. Si algún día alguien pinta un texto de estado
   con `PapWatch`, la contención cae.
2. **Gama que no es la de nadie.** Cian `#22D3EE` (H≈188°) es el punto equidistante entre el verde
   H≈150° y `PapLive` H≈222° — 38°/34°, la máxima separación posible. Lo que lo hace seguro no es
   sólo el tono: es que **el verde desaparece de la tarjeta de vehículo**, así que cian y verde
   nunca se comparan en el mismo objeto.
3. **Alcance acotado al garaje.** `PapWatch` aparece en el chip de Home, la tarjeta única y la
   ficha de Vehículos — donde el usuario compara coches entre sí. **No aparece en el mapa ni en el
   peek de una sesión**, que es territorio de `PapLive`. Ahí es donde se concentraba la colisión.

Si en device el cian y el verde neón se pelean en oscuro, la salida es bajar el cian a
`#0E7490`-ish también en dark (menos neón), no mover el verde.

## Fases

Cada fase es un commit independiente y verificable. Orden pensado para que ninguna deje la app en
estado incoherente a medias.

| # | Ticket | Alcance | Riesgo |
|---|---|---|---|
| **F1** ✅ | UI-COLOR-DOCTRINE-001 | `COLOR-SYSTEM.md` + regla ⛔ en `CLAUDE.md` (reescritas en v2) | nulo |
| **F2** ✅ | UI-COLOR-LIVE-BLUE-001 | Fusionar `PapDriveBlue` + `SpotPalette.EnRouteBlue` + `LOC_HALO_BLUE` en `PapLiveMap` (fijo) + `papLive` (theme-aware). Arregla el fallo AA (#2F6BFF sobre `PapInk` = 4.17, bajo el 4.5) | bajo, mecánico |
| **F3** ✅ | UI-VEHICLE-ACCENT-UNIFY-001 | Resolver único + escalera de precedencia; borrar los resolvers rivales. **Aquí "aparcado" pierde el verde**. Incluye F4 (ver abajo) | **medio** — chip, card, peek, marcador, ficha, selector |
| ~~F4~~ | UI-WATCH-CHASSIS-001 | **Absorbida por F3** — no se puede borrar `vehicleStatusBorderColor` sin decidir el chasis en el mismo commit | — |
| **F5** ✅ | UI-SPOT-MANUAL-TIER-001 | `MANUAL` fuera de `SpotReliabilityUiState` → frescura por confianza (testigo=1.0→HIGH verde) + badge persona + anillo TTL recuperado; `ManualBlue` borrado; caché de marcadores con eje manual; strings "manual" retirados de 9 locales; spot manual en FakeData | **medio** |
| **F6** ✅ | UI-TERTIARY-RETIRE-001 | `tertiary` retirado de todo feature: tono info detección → `papCarBlue`, eyebrow Manual → Action, zona privada → neutro+candado (marker+anillo), previews. Slots del scheme conservan valores como backing de framework (documentado). `ColorGuardrailTest` con 3 reglas | bajo |

### Corrección de alcance en F2

El plan inicial metía también los usos "info" de `PapBlue` (`HomeDetectionSurface.kt:115`,
`Tone(cs.tertiary…)`) dentro de `PapLive`. **Es incorrecto**: un nudge informativo no es
movimiento, y colarlo en `PapLive` habría vuelto a meter dos significados en un token justo al
crearlo. Ese uso se queda donde está y se resuelve en **F6** junto con el retiro de `tertiary`.

Contrastes medidos de las variantes nuevas: `PapLiveDark` #6FA0FF sobre `PapInk` = **7.35:1**;
`PapLiveLight` #0B4FE0 sobre blanco = **6.54:1**. Ambas AA con holgura (el token viejo daba 4.17).

### Correcciones de alcance en F3

1. **F4 se absorbe en F3.** Las funciones que F3 tenía que borrar (`vehicleStatusAccent`,
   `vehicleStatusBorderColor`) *son* la fuente de color del chasis. No se pueden eliminar sin
   decidir qué hace el borde en el mismo commit, así que separarlas habría dejado un commit
   intermedio donde el coche BT pierde su identidad y la recupera después — mala APK para probar.
2. **Había un CUARTO resolver rival**, no detectado en el censo inicial: `VehicleStatusDot` en
   `VehiclesScreen.kt:407` resolvía `primary`/`tertiary` por su cuenta para el punto del selector
   de pestañas. Ahora es `VehicleWatchDot` y lee la misma fuente.
3. **El marcador de mapa pierde el color de estado.** Su borde pasa a una rampa neutra de un solo
   token (`onSurface`: sólido seleccionado · 0.35 en reposo · 0.18 sin vigilar). Tuvo que ser
   `onSurface` y no una tinta fija: el relleno del tag ya es theme-aware (`PapInk` en oscuro,
   blanco en claro), así que un borde oscuro fijo desaparecía sobre el tag oscuro. Con esto **un
   coche propio aparcado deja de competir en verde con las plazas libres del mismo mapa**.
4. **`isBluetoothPaired` se elimina de la capa de marcadores** (`VehicleBadgeMarker`,
   `ParkingCenterPin`, `PaparcarMapView`, `HomeMapSection`) en vez de dejarlo como no-op: un
   parámetro que ya no afecta al render hace creer al siguiente lector que el BT sigue pintando el
   mapa. La **clave** del marcador en `PaparcarMapView.kt:251` se deja intacta a propósito
   (`kmp-maps` es sensible al keying — ver el flicker por `hashCode(coords)`).

### Regresión completa post-F6 (2026-08-11, pedida por el user)

El user detectó en el mock un "Corolla aparcado en verde": F3 barrió chip/card/peek/marcador pero
NO las demás superficies que pintan la historia del coche. Barrido completo verificado con
capturas en el Oppo (galería mock). Superficies corregidas:

- `HomeDetectionSurface`: el tono `quiet` VERDE (filas "Conduciendo tu X" / "Vigilando tu X ·
  aparcado") era exactamente la queja. Conduciendo → `papLive`; Vigilando/aparcado → `papCarBlue`.
  El tono verde `quiet` se borra.
- Ficha (`StatCell`): iconos de stats → `papCarBlue` (el registro del coche, no la marca).
- Historial completo: `ActiveSectionHeader` + `PulsingDot` + puntos del timeline + relleno de la
  card activa (tonal azul; su texto pasa de `onPrimaryContainer` verde a `onSurface`) + gráfica
  semanal entera (`accentColor`, antes `primaryColor`).
- Detalle de aparcamiento (`ParkingLocationScreen`): tints de meta-rows activos → `papCarBlue`
  (los 3 tipos; la procedencia la dice el glifo).
- `VehicleTabPill` seleccionado → tonal `papCarBlue` (seleccionar un COCHE no lo pinta de marca).
- `BrowsePeek` eyebrow candidato → `papLive` (era verde; candidato ES el viaje en curso).
- **`UserLocationDot` → `PapLiveMap`**: el punto "tú" del mapa era VERDE — un significado más del
  verde que el censo original no cazó. Tú en el mapa = la misma historia que el puck y la traza.
- `PulsingDot` pierde su default `PapGreen` (color obligatorio en la firma).

Verdes que QUEDAN a propósito (acciones/chrome): CTAs y links, spinner de carga, check de
SetActiveRow, "Marcar aparcamiento", icono-acción "ver en mapa", modo informar del center-pin,
empty-states con CTA, y la rampa de salud de permisos (`DetectionTierStatusCard` — semáforo de
sistema, no identidad del coche). Zonas (anillo + label público) quedan en `primary` — pregunta
abierta si las zonas son "oferta" o "mío" (no bloquea).

## Ficheros tocados (censo previo)

Tema: `ui/theme/Color.kt`, `Theme.kt`, `SpotStateColors.kt`.
Componentes: `ui/components/VehicleBadge.kt`, `VehicleStatusIndicators.kt`, `PaparcarMapMarkers.kt`,
`PaparcarMapView.kt`, `SpotIndicators.kt`.
Presentación: `home/sections/sheet/components/PapSheet.kt`, `HomeParkingRow.kt`,
`HomeDetectionSurface.kt`, `peek/ParkingPeek.kt`, `peek/BrowsePeek.kt`,
`home/sections/map/components/HomeMapFab.kt`, `vehicles/VehiclePageContent.kt`,
`vehicles/components/HistoryActiveCard.kt`.

## Definition of done

- [x] `COLOR-SYSTEM.md` + regla ⛔ en `CLAUDE.md` (F1; reescritas en v2).
- [x] Un solo resolver de vehículo (`ui/theme/VehicleIdentity.kt`); los CUATRO antiguos borrados (F3).
- [x] `PapDriveBlue`, `EnRouteBlue`, `ManualBlue` y el cian borrados; `tertiary` sin ningún uso en feature — los slots del scheme quedan como backing de framework (F2/F5/F6).
- [x] `ColorGuardrailTest` verde: tertiary prohibido, sin `Color(0x…)` literal en presentation, sin verde improvisado junto al resolver (F6).
- [x] **Dev Catalog en sync**: variantes de vigilancia y acento ya cubiertas en la galería; spot manual añadido a `FakeData.nearbySpots` (badge persona visible en galería y previews).
- [x] `assembleMockDebug` + prod + `testProdDebugUnitTest` verdes.
- [ ] Revisión visual final en device (claro y oscuro) de la rama completa antes de mergear.

## v3 — Reformulación final (2026-08-13): identidad por MÉTODO, estado en texto

La v2 (mi coche = azul, dos energías) se rechazó en device: "en la pantalla de vehículos te has
cargado la identidad de la app… el tema de la app es el verde que teníamos siempre". El usuario
dictó el sistema definitivo, que además coincide con su feedback del primer día ("a BT le
representa mucho el azul"):

- **Verde primario** = marca/acción (como siempre) **y** vehículo con detección activa.
- **Azul `papCarBlue`** = vehículo Bluetooth. **Gris** = sin vigilancia.
- **El nombre del coche** viste el color de su método en TODAS las superficies (chip, card, ficha,
  selector, peek, fila de detección, marcador). "TOYOTA COROLLA (verde) · APARCADO (`onSurface`)".
- **El estado nunca tiñe**: "aparcado / en ruta / sin aparcar" es texto `onSurface`; en ruta se
  anima (pulso `rememberDrivingStatePulse` + halo radar en el color de identidad + borde a plena
  intensidad). `papLive` UI eliminado; `PapLiveMap` queda solo-mapa (traza, origen, en-route, FAB).
- **Glifo del tier asistido**: de diana (`TripOrigin`) a **radar/geocerca** (`Icons.Rounded.Radar`)
  — petición explícita del usuario.
- Reversiones a verde de marca: historial completo, stats de ficha, detalle de aparcamiento,
  punto de usuario del mapa, filas de setup de detección (Inactive/AwaitingFirstPark).
- Marcador de coche: marco por método restaurado (`PapGreenLight`/`PapBlueLight`/gris), param
  `isBluetoothPaired` de vuelta en `VehicleBadgeMarker`/`ParkingCenterPin`/`PaparcarMapView`.
- `DetectionStory.Driving` gana `viaBluetooth` (resuelto del vehículo del viaje); `Watching` lo
  deriva del vehículo activo en vez de hardcodearlo por estado. Fila de detección = tono del método.
- Guardarraíl: muere la regla "sin verde junto al resolver" (el verde ES identidad legítima);
  quedan tertiary + literales. Se conservan de v1/v2: resolver único (ahora
  `vehicleIdentityColor(watch)`), F5 manual-badge, retiro de `tertiary`, tokens AA.
- Galería/previews: variante "Conduciendo (BT → azul)" añadida.

## Fuera de alcance

- Rediseño de la rampa de fiabilidad de plazas (umbrales, no colores).
- Paleta de color de vehículo del usuario (`VehicleColor`) — es identidad del objeto, otro eje.
- Tema claro/oscuro automático por sistema (hoy `darkTheme = true` fijo en `PaparcarTheme`).
