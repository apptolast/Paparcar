# PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001 · Las capturas de Play enseñan la app en el idioma de su ficha

**Estado:** ✅ Done · rama `chore/PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001-pixel8`

## Problema

Dos cosas a la vez en el set de capturas del 01-09:

1. **Las nueve fichas enseñaban el móvil en inglés.** Sólo el titular y el subtítulo del marco
   estaban traducidos; las capturas crudas se tomaron una vez, en EN, y se reusaron en los nueve
   idiomas. El propio README lo decía y dejaba el trabajo escrito: *«si algún día se quieren raws
   por idioma, hay que repetir la sesión de emulador»*.
2. **El guion había caducado.** Desde el 01-09 entraron gasolineras con precio, parkings públicos,
   panel de cifras y coche compartido, y ninguna de las 8 capturas los enseñaba.

Y un tercero que sólo se ve al intentar repetir la sesión: el guion **no era repetible**. Pedía
aplicar a mano un parche `if (false)` en `DevRoot` para ocultar los chips DEV y revertirlo después.

## Doctrina violada

- **Sistemas, no parches.** Un procedimiento que exige editar código a mano y revertirlo no es un
  procedimiento, es una nota. Por eso el set nunca se rehízo.
- **i18n**: la app embarca 9 locales y la ficha vendía una app que sólo se veía en inglés.

## Diseño

El invariante es *una sesión de fotos se repite con un comando*, y vive en tres sitios:

- **`ShotPresets.kt`** (mock) — `DevMainActivity` lee `--es shot <preset>`, aplica el escenario sobre
  `MockScenario` y monta la app real. `DevRoot(shotSession = true)` no compone los chips DEV.
  ⛔ Ocultarlos con `alpha(0f)` no vale: el glifo emoji se salta la capa de alpha.
- **`-PmockAsProd`** (`app/build.gradle.kts`) — quita el sufijo `.mock` del applicationId y del
  versionName sólo cuando se pasa. **Es un apaño, no el arreglo**: existe porque la clave de Maps
  se restringió el 03-09 a `com.rndeveloper.paparcar` + SHA-1 y nadie añadió `.mock`, así que desde
  entonces el mapa del Dev Catalog sale negro. Y el versionName importa porque Ajustes lo imprime:
  una captura de tienda no puede decir «1.2.0-mock».
- **`tools/shoot.sh` + `tools/render.sh`** — capturan 8×9 y renderizan 8×9.

## Hallazgos medidos

- **La hoja de Home no cae siempre en el mismo detente.** El mismo swipe la dejó con el borde
  superior en `0`, `642`, `888` y sin abrir siquiera, en repeticiones idénticas. Cualquier tap
  calibrado a una `y` fija es una moneda al aire — y cuando fallaba no daba error: abría *otra*
  pestaña y la captura parecía plausible. **Solución: `uiautomator dump` y tap por TEXTO**, con las
  pestañas localizadas por su badge numérico (`6`/`4`/`5`, cifras árabes idénticas en los 9 idiomas)
  y la fila del parking por «Parking Plaza Mayor», que es nombre propio.
- **Detectar la hoja por color de píxel no funciona** (se intentó antes): los marcadores de plaza,
  los badges de precio y el banner de «zona habitual» son chips oscuros a cualquier `y`, y rompen
  la monotonía que una búsqueda binaria da por supuesta.
- **`uiautomator dump` puede devolver un árbol casi vacío** (4 nodos) sin error, justo después de un
  scroll, con la pantalla llena. Hay que reintentar comprobando que el árbol no venga escuálido.
- **El emulador se cayó dos veces** (WHPX `hr=80070005`) tras muchos relanzamientos seguidos. Las
  capturas de esa ventana salen en **0 bytes**: verificar tamaño, no sólo que el fichero exista.
- **La localización por app va por `cmd locale set-app-locales`** (Android 13+). `setprop
  persist.sys.locale` no vale: la imagen Play Store no se rootea.
- **El AVD `Pixel_8` hubo que crearlo** (el SDK sólo traía el Pro). 1080×2400 @ 420 dpi, mismo ratio
  9:20 que el Pro, así que la plantilla del marco no se tocó.

## Consumidores auditados

- `FakeFuelStationsSource` — marcas reales (`Repsol`, `Cepsa`) y direcciones de Madrid bajo una
  cabecera de Cádiz → nombres genéricos y calles de El Puerto, y de 3 surtidores a 5. Sólo lo
  consume `MockModule`; los tests de parser traen sus propios payloads y no se tocan.
- `screenshot-frame.html` — el mapa `SHOTS` reordenaba las raws (1→2→4→3…); ahora el orden de
  captura y el del carrusel coinciden 1:1. Titulares 4-7 reescritos en los 9 idiomas.
- `README.md` de la ficha — el procedimiento viejo queda plegado como superseded.

## Criterio de éxito

✅ 72 capturas crudas (8 × 9 idiomas) con la app en su idioma, todas verificadas no vacías.
✅ 72 frames renderizados en `assets/screenshots/{lang}/play-shot-{1..8}.png`.
✅ La sesión se repite con `shoot.sh <locale> <outdir>` sin editar una línea de código.

## Follow-up deliberadamente fuera de alcance

- ⛔ **Añadir `com.rndeveloper.paparcar.mock` a la clave de Maps en la consola de GCP.** Mientras no
  se haga, el mapa del Dev Catalog sale negro en cualquier build mock normal y `-PmockAsProd` es
  obligatorio para verlo. Es trabajo de consola, no de repo.
