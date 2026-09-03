# UI-A-MOTORCYCLE-IS-DRAWN-LIKE-EVERY-OTHER-VEHICLE-001 · la moto entra en el sistema de pictogramas de vehículo

**Estado:** ✅ Done · rama `feature/UI-A-MOTORCYCLE-IS-DRAWN-LIKE-EVERY-OTHER-VEHICLE-001-moto-pictogram` ·
worktree `../Paparcar-moto-glyph`

## Problema

Reportado por el user: *"el icono de moto se ve negro opaco"*.

`PaparcarIcons.VehicleMotorcycle` es `Icons.Rounded.TwoWheeler`, un glifo Material cuyo fill nativo
es `Color.Black`. Donde se pinta con `Icon(tint = …)` sale teñido y correcto. Donde sale **negro
opaco** es en la superficie de identidad del vehículo: `vehicleIconPainter` no encuentra carbody para
`VehicleSize.MOTORCYCLE`, cae a la rama `rememberVectorPainter(size.icon)`, y `VehicleIcon` lo dibuja
con `Image` y `tint = Color.Unspecified` **a propósito** — porque los pictogramas de coche traen su
propio color. El coche se pinta solo; el glifo Material, sin tinte, se pinta de negro.

Al abrir el caso aparecieron dos consecuencias más del MISMO fallo, ninguna reportada:

1. **El puck cenital de una moto es un sedán.** `VehicleTopdownIcon` resuelve
   `carbody ?: size?.fallbackCarbody() ?: CarbodyType.SEDAN`, y como una moto no tiene carbody, cae
   al `SEDAN`. Conduciendo una moto, el mapa dibuja un coche.
2. **El hero de registro dibuja un coche mientras registras una moto.**
   `VehicleRegistrationScreen.kt:667` pasa `defaultCarbody = CarbodyType.HATCHBACK_MEDIUM`, y el
   orden `carbody ?: size?.fallbackCarbody() ?: defaultCarbody` deja que ese default de coche gane
   sobre una talla que ya decía MOTORCYCLE.

## Doctrina violada

**Iconos, Nivel 3** (`CLAUDE.md`): *ilustración/marcadores → vector propio, multicolor, NO tintar;
los vehículos son Nivel 3.* La identidad de un vehículo se dibujaba con un glifo Material de Nivel 1.

**Sistemas, no parches**: no son tres bugs, es un invariante que falta —
*una moto no es un coche al que le falta la carrocería.* Al no existir ese hecho en ningún sitio,
cada renderizador improvisó su propio remate (glifo negro, sedán, hatchback), y los tres improvisaron
distinto.

## Señales / datos disponibles

- `VehicleSize.MOTORCYCLE` y `CarbodyType.fallbackCarbody()` ya devuelven `null` para moto a
  propósito (`CarbodyType.kt:41`) — el dominio ya dice que no hay carrocería. Lo que faltaba era la
  otra mitad: qué SÍ se dibuja entonces.
- `buildCarImageVector` + `CarPalette` ya soportan recolorear por `VehicleColor`, borde blanco en
  ambos temas y variante oscura. La moto no necesitaba maquinaria nueva, necesitaba su geometría.

## Diseño

**Un solo resolver decide con qué arte se dibuja un vehículo, y los dos renderizadores lo comparten.**

`VehicleCarGeometry.kt`:

- `MOTO_ISO` / `MOTO_TOPDOWN`: dos `CarSpec` nuevos, construidos con los MISMOS colores fuente que
  los coches (`BODY`/`BODY2`, `HI`, `LO`, `GLASS`, `TIRE`, `HUB`, `HEAD`, `SHADOW`, `WHITE`), de modo
  que `recolor()` los recolorea por `VehicleColor` y `buildCarImageVector` les pone el borde blanco
  de cuerpo y el anillo blanco de rueda sin tocar una línea del constructor. Color fuente nuevo:
  `SADDLE` (asiento, horquilla, basculante), con su fila en `recolor`.
- El perfil comparte **el mundo** de los coches: rueda r=8 con cubo r=3.2 centrada en y=50, suelo y
  sombra en y=57. Por eso la moto sale más baja que un coche en la misma caja: lo es. Las alturas
  salen de la escala del propio arte (rueda de 16 px = 0,6 m ⇒ 26,7 px/m ⇒ asiento a 0,80 m = y≈36).
- `VehicleArt` (`TwoWheeler` | `Car(carbody)`) + `vehicleArtOf(carbody, size, defaultCarbody)`:
  **`VehicleSize.MOTORCYCLE` gana antes que ningún default**, que es justo lo que rompía el hero de
  registro. Un default pensado para coches no puede vestir de hatchback a una moto.

`VehicleIconPainter.kt` y `VehicleTopdownIcon.kt` pasan a pedir el arte al resolver. La rama
`rememberVectorPainter(size.icon)` deja de ser alcanzable para MOTORCYCLE.

**Fuera de alcance, a propósito:** el selector de TIPO (`VehicleTypeSelector`) sigue con glifos
Material teñidos para los cuatro tipos — ahí no se dibuja la identidad de un vehículo concreto, se
eligen categorías, y las cuatro filas son coherentes entre sí. Igual `NonCarSizeBadge`, que es un
`PapIconTile` teñido de talla, no el pictograma del vehículo.

## Criterio de éxito

- Ninguna superficie de identidad de vehículo pinta un `ImageVector` Material sin tintar.
- Registrando una moto, el hero enseña una moto; conduciéndola, el puck enseña una moto.
- Test que lo demuestre con TESTIGO de población: recorre `CarbodyType.entries` (nadie añade una
  carrocería sin arte) y fija que MOTORCYCLE resuelve `TwoWheeler` **contra todos** los
  `defaultCarbody` posibles, no solo contra `null`.

## Consumidores auditados

`grep` de todo lo que dibuja un vehículo o asume que sin carbody hay coche:

| Sitio | Veredicto |
|---|---|
| `VehicleIconPainter.vehicleIconPainter` | **cerrado** — usa el resolver |
| `VehicleTopdownIcon` | **cerrado** — usaba `?: SEDAN`; ahora resolver |
| `VehicleRegistrationScreen.kt:667` (hero, `defaultCarbody`) | **cerrado** por precedencia de MOTORCYCLE |
| `VehicleSizeSelector.kt:107` (rama `if (size == MOTORCYCLE)` con glifo Material) | **cerrado** — la rama especial se borra, `VehicleIcon` ya sabe |
| `VehiclesScreen.kt:402,462` · `VehicleSizeExplainerScreen.kt:78` · `PaparcarMapMarkers.kt:370` · `VehicleBadge.kt:48` | **cubiertos** — pasan por `VehicleIcon` |
| `CarbodyInfoCard.kt:93` · `CarbodyManualPicker.kt:160` | **cubiertos** — pasan `size = null` + carbody real (solo coches) |
| `VehicleTypeSelector` · `NonCarSizeBadge` | **exentos** — icono teñido de categoría/talla, no identidad (ver Diseño) |
| `PaparcarIcons.VehicleSize.icon` | **superviviente acotado** — su rama MOTORCYCLE deja de ser alcanzable desde el pictograma; sigue viva como fallback del `VehicleType` selector |

**SCOOTER y BIKE** se guardan con `sizeCategory = MOTORCYCLE` (`VehicleRegistrationViewModel.kt:200`),
así que heredan el pictograma de moto. Es lo que ya hacían con el glifo Material `TwoWheeler`: el
cambio no los mueve de sitio, solo deja de pintarlos de negro.

## Cómo se dibujó (por si hay que retocarlo)

El arte NO se escribió a ciegas. Se montó un harness que replica `buildCarImageVector` (mismos
colores fuente, mismo anillo blanco automático en las ruedas, mismo `recolor` para oscuro) y emite
un SVG que Chrome headless captura, con un `CarSpec` de coche real al lado como referencia y una
escalera 96/48/32/24 px en claro y oscuro. Cinco iteraciones: las cuatro primeras salieron cubo,
bolso y termómetro, y el fallo de fondo se vio midiendo — el cuerpo iba ~10 px demasiado alto.

Dos divergencias del harness se cerraron antes de transcribir, porque habrían hecho que el device no
se pareciera a lo aprobado: (a) el preview forzaba `stroke-linecap="round"` y el constructor lo deja
en `Butt`, lo que convertía el manillar en un guion flotante → pasó a forma rellena; (b) el cenital
heredaba el viewport de 56 de los coches, donde una moto queda en el centro y el `ContentScale.Fit`
la encoge a un palo → viewport propio de 40.

Al final se comparó ruta a ruta el Kotlin contra el arte revisado: **23/23 idénticas**.

## Verificación

- `:shared:testDebugUnitTest --rerun-tasks` → **2.186 tests, 0 fallos** (196 clases), incluidos los 6
  de `VehicleArtTest`.
- **Testigo del guard**: se invirtió a propósito la precedencia del resolver a la de antes y
  `should_keepTheTwoWheelerArt_when_aCallSiteOffersAnyCarDefault` **falló** (1 de 6). El test
  discrimina; no es verde por construcción.
- `:app:assembleMockDebug` + `:app:compileProdDebugKotlin` verdes.
- ✅ **Visto en device** (Oppo `LNRCMZ8H6HBITWNJ`, tema oscuro, sha256 device↔local verificado en
  prod `d25da554…` y mock `dedd2db8…`): Dev Catalog → Galería → MAPA · MARCADORES. Las dos filas
  nuevas dibujan lo aprobado en el harness — la moto de perfil entre las diez carrocerías, y el puck
  cenital moto · hatchback · furgo. Prod arranca sin crash (pid vivo, 0 `FATAL`).
- ⏳ **Sin ver en prod**: el Oppo lleva el Kamiq y **no hay ninguna moto registrada**, así que garaje,
  hero de registro y puck real siguen sin comprobarse con datos de verdad. Registrar una moto tocaría
  el setup de field-test, que es intencional — se deja para cuando el user lo autorice o para el
  primer alta real de una moto.
