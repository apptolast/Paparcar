# UI-COLOR-THE-DEFAULT-SWATCH-MUST-SHOW-ITS-PAINT-001 · la muestra de color por defecto pinta gris y el coche sale verde

**Estado:** ✅ Done · verificado en device (Oppo `CPH2371`, 2026-09-03)

## Problema

En el formulario de vehículo (`VehicleRegistrationScreen` → sección COLOR) la primera burbuja de la
fila es la opción **"Predeterminado"** (`color = null`). Hoy se pinta con `onSurfaceVariant` — un
gris azulado — y lleva dentro un glifo de coche.

Pero cuando `color == null` el coche **se dibuja en verde marca**: `vehicleIconPainter` resuelve
`defaultCarPalette(topdown = false)` (`VehicleIconPainter.kt:60`), cuyo `body` es `SRC_BODY_A`
= `#00794A` (`VehicleCarPaint.kt:57-61`).

O sea: la fila de burbujas es una **carta de pinturas** — cada burbuja enseña el `body` que se va a
aplicar — y la única que no cumple es la primera. **Promete gris y entrega verde.** El usuario lo
detectó en device preguntando por qué había un coche metido dentro de un color.

El glifo de coche era el parche que tapaba esa mentira: estaba ahí para decir *"ya, se ve gris, pero
en realidad es el verde de siempre"*. Y encima estaba **prestado de otro eje**:
`PaparcarIcons.VehicleCar` es `Icons.Rounded.DirectionsCar`, cuyo trabajo declarado es la taxonomía
de TIPO — CAR vs MOTORCYCLE vs SCOOTER vs BIKE en `VehicleTypeSelector` (`PaparcarIcons.kt:94-100`).
En el mismo formulario el mismo glifo decía dos cosas distintas.

Agravantes medidos:

- La fila tiene **cinco grises reales** (Blanco, Plata, Gris, Grafito, Negro). El cochecito era lo
  único que separaba la burbuja de defecto de ser un gris más.
- Ese glifo **desaparece al seleccionarlo**: el `when` de `VehicleColorSelector.kt:119` pinta el
  check en su lugar. Justo en el estado en que el usuario más necesita saber qué eligió, el único
  distintivo se va.
- El anillo de seleccionado es `PapColor.selected` = `colorScheme.primary` = el mismo verde marca
  (`PapColor.kt:76-77`). Al devolver el verde a la burbuja, verde sobre verde: el anillo se pierde.
  El negro seleccionado ya tenía hoy ese problema en menor grado (anillo verde pegado a un relleno
  casi negro se lee, pero el borde y el relleno se tocan sin separación en todas las muestras).

## Doctrina violada

Ninguna de las reglas de `UI-COLOR-DOCTRINE-001` prohíbe lo que hacemos aquí — el problema es que se
aplicó una regla **correcta al caso equivocado**.

`UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001` introdujo el rol `unknown` («un dato que NO tenemos —
neutro a propósito», `COLOR-SYSTEM.md:261`) y repintó esta muestra con él, razonando que pintar de
verde un valor ausente *"viste un agujero de identidad"*. La regla es buena. El caso no es ese:
aquí no hay dato ausente al que ponerle ropa, porque **el verde no es una etiqueta que le pongamos a
"no sé el color" — es literalmente el color con el que se dibuja el coche**.

El propio `COLOR-SYSTEM.md` ya tiene el precedente escrito, en la entrada del 2026-08-29 del selector
de Tema: *"no es una excepción a 'el color va por historia': ahí el color **es** el dato, un
muestrario, igual que en una carta de pinturas"* (`COLOR-SYSTEM.md:364`). La fila de colores de
vehículo es exactamente ese mismo caso, y se le aplicó la regla de los roles semánticos.

## Señales / datos disponibles

- `defaultCarPalette(topdown = false).body` = el verde real del pictograma lateral, **el mismo valor**
  que consume `vehicleIconPainter`. No hay que inventar ni un token ni un literal: la muestra puede
  leer la misma fuente que la pintura.
- Es independiente del tema: `defaultCarPalette` no toma `isDark` y el `body` verde es el mismo en
  claro y oscuro (lo que cambia en el `*_dark` son cristales, ruedas y sombra, vía `recolor`).
  Así que la muestra es veraz en los dos temas sin ramas nuevas.
- `PapColor.unknown` tiene **cero consumidores** en todo el repo (`grep -rn "PapColor.unknown"` → 0).
  Su KDoc nombra a `swatchColor()` como su razón de ser, pero `swatchColor()` nunca lo llegó a leer:
  fue directo a `MaterialTheme.colorScheme.onSurfaceVariant` (`VehicleColorLabels.kt:61`). El token
  nació muerto y su historia es precisamente la decisión que este ticket revierte.

## Diseño

**El invariante: una muestra de la carta enseña el relleno que se va a aplicar. Sin excepciones.**
Vive en UN sitio, `swatchColor()`, y se cumple leyendo la misma fuente que pinta el coche.

1. **`VehicleColorLabels.swatchColor()`** — para `null` devuelve `defaultCarPalette(topdown = false).body`
   en vez de `onSurfaceVariant`. No es un literal (lo prohíbe `ColorGuardrailTest`) ni un token nuevo:
   es la misma función que ya resuelve la pintura, así que muestra y coche **no pueden divergir**.
   Se elige la orientación lateral porque es la que renderiza `vehicleIconPainter`, el pictograma que
   el usuario tiene delante en esta pantalla.

2. **`VehicleColorSelector`** — fuera la rama `isDefault` y el `PaparcarIcons.VehicleCar`. El glifo
   vuelve a ser solo taxonomía de tipo. La burbuja de defecto se dice con su color y con la etiqueta
   "Predeterminado" de debajo, igual que las otras doce.

3. **Hueco interior en la muestra seleccionada** — el anillo se dibuja en el Box exterior y el relleno
   se mete hacia dentro, de modo que entre anillo y relleno queda el color de la superficie:
   - sin seleccionar → el relleno se mete `REST_BORDER_WIDTH`, así el hairline sigue pegado y las
     muestras claras (Blanco, Plata) siguen recortándose contra la superficie, como hasta ahora;
   - seleccionada → se mete `SELECTED_RING_WIDTH + SELECTED_RING_GAP`, y el anillo verde se lee
     contra cualquier relleno, **incluido el verde**.

   Un solo camino de código, sin caso especial para el verde: el hueco arregla a la vez el verde
   sobre verde y el negro pegado al anillo. `isDefault` deja de existir como parámetro.

   **`.clip(CircleShape)` se queda en el Box EXTERIOR, antes de `.clickable`.** Al mover el relleno
   al Box interior el clip se fue con él, y el `clickable` quedó en un nodo sin forma: el ripple
   dejó de ser redondo y pintaba la caja de layout, un cuadrado sobre una muestra circular (visto en
   device por el user). El ripple no elige forma, dibuja los límites del nodo — y el clip solo los
   recorta si va ANTES en la cadena. Barrido de las demás superficies redondas pulsables
   (`PapClearIconButton:40-43`, `PapStepperButton:38-40`, el dismiss de `HomeHeaderSection:222-224`,
   los chips de `AddingZonePeek:296-302`): todas clipan antes de `clickable`, la convención estaba
   bien en el repo y esta la había perdido en esta misma edición.

4. **`PapColor.unknown` se retira** junto con su fila en `COLOR-SYSTEM.md`. Es un rol sin un solo
   consumidor cuya justificación escrita es la decisión que aquí se revierte: dejarlo sería dejar un
   token que miente sobre por qué existe, esperando a que alguien lo use por el nombre.

## Criterio de éxito

- En device, sección COLOR del formulario: la primera burbuja es **verde marca** y el coche de la
  tarjeta de arriba es de ese mismo verde. Sin glifo de coche dentro.
- Al seleccionarla, el anillo verde se distingue del relleno verde por el hueco, y el check blanco
  se lee encima.
- Elegir cualquier otro color repinta el coche a ese color, y su burbuja queda con anillo + hueco.
- Al pulsar una muestra el ripple sale **redondo**, recortado al círculo. ✅ verificado en device.
- `ColorGuardrailTest` y el resto de `:shared:testDebugUnitTest` en verde.
- `assembleMockDebug` + `compileProdDebugKotlin` sin romper.

## Consumidores auditados

| Sitio | Qué asume | Estado |
|---|---|---|
| `VehicleColorSelector.kt:71` | `swatchColor()` es el relleno de la muestra | ✅ cubierto — es el arreglo |
| `VehicleColorSelector.kt:119-133` | la muestra de defecto necesita un glifo | ✅ cerrado — rama eliminada |
| `VehicleColorLabels.kt:60-64` | `null` = dato ausente → neutro | ✅ cerrado — `null` = verde de fábrica |
| `VehicleIconPainter.kt:60` | `null` → `defaultCarPalette(topdown = false)` | ✅ exento — es la fuente de verdad que ahora lee la muestra |
| `VehicleTopdownIcon.kt:36` | `null` → `defaultCarPalette(topdown = true)` | ✅ exento — marcador de mapa, no muestra |
| `PapColor.unknown` (`PapColor.kt:121-125`) | rol "dato ausente" | ✅ cerrado — 0 consumidores, retirado |
| `COLOR-SYSTEM.md:261` | fila del rol `unknown` | ✅ cerrado — fila retirada + entrada de historia |
| `PaparcarIcons.VehicleCar` | glifo de taxonomía de TIPO | ✅ cerrado — recupera su único significado |
| `VehicleTypeSelector` / `AddingParkingPeek` | usan `VehicleCar` como tipo | ✅ exentos — uso correcto, no se tocan |
| `StateGalleryScreen.kt:1652`, `VehicleRegistrationPreviews.kt:61/80` | pintan la pantalla con `VehicleColor.RED`/`BLUE` | ✅ exentos — no hay pantalla ni estado nuevo; el defecto ya es el estado base |
| Strings | ninguna key nueva ("Predeterminado" ya existe en los 9 locales) | ✅ sin trabajo de i18n |
