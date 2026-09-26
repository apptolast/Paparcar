# PLAY-SHOTS-A-LISTING-IS-COMPOSED-NOT-CROPPED-001 · La ficha se COMPONE, no se recorta

**Estado:** ✅ Done · rama `chore/PLAY-SHOTS-A-LISTING-IS-COMPOSED-NOT-CROPPED-001-feed` ·
worktree `../Paparcar-play-feed`

## Problema

El set vigente (`14a761ece`, 17-09) es **una captura de pantalla recortada con un titular encima**:
sin marco de dispositivo, el móvil sangra por el borde inferior del lienzo, y cada imagen enseña
exactamente una pantalla. Al lado de cualquier ficha cuidada (Citymapper, Revolut, Too Good To Go)
se lee como material de desarrollador, no de producto.

Y además está **caducado**: el mundo de demostración se mudó de El Puerto de Santa María a Dublín
en `4b90ae506` (25-09), así que las 72 imágenes publicadas enseñan calles y rótulos de un sitio
que la app ya no usa. El guion de captura tampoco arranca: `shoot.sh` toca la fila del parking
buscando el texto **«Parking Plaza Mayor»**, que ya no existe (hoy es `Drury Street Car Park`).

## Doctrina violada

Ninguna regla de código, pero sí la del §*Una PR con resultado visual SE ENSEÑA*: lo que la tienda
enseña es lo que compila master, y hoy no lo es.

## Diseño

Tres decisiones del user (25-09), y el marco las implementa como un SISTEMA, no como ocho maquetas:

1. **Composición mixta** — cada imagen elige su forma según lo que tiene que contar:
   `hero` (una pantalla, grande, para abrir y cerrar) · `pair` (dos móviles solapados cuando la
   historia son dos momentos) · `detail` (una pantalla + un recorte ampliado del propio raw cuando
   el valor está en un dato concreto: el precio, el gálibo, la cifra).
2. **Fondo en tira continua** — las 8 imágenes son ocho ventanas de UN lienzo de 8640×1920: el
   degradado y la traza de ruta cruzan de una a la siguiente. Cada imagen sigue valiendo suelta
   (nada se parte a la mitad); lo único que viaja es el fondo.
3. **Marco de dispositivo de verdad** — bisel, radio y sombra: el móvil entra ENTERO en el lienzo.

⛔ **El recorte ampliado sale del MISMO raw**, nunca de una maqueta HTML que imite la UI: un
callout dibujado a mano es una promesa que el binario no firma.

⛔ **El copy no se reescribe**: las 8 promesas ya están traducidas a los 9 idiomas y auditadas
contra `COPY-SPOT-IS-NOT-A-PARKING-001`. Esta tarea cambia la FORMA, no lo que se promete. Los
callouts van **sin leyenda** por lo mismo: una leyenda nueva serían 9 traducciones nuevas.

## Criterio de éxito — ✅ cumplido el 25-09

- 8 imágenes × 9 idiomas = **72 PNG de 1080×1920**, con la app en SU idioma y el mundo en Dublín.
- Las ocho de un idioma, puestas en fila, leen como una sola tira.
- `tools/scan.py raws` (99 capturas) y `tools/scan.py frames` (72): **0 problemas**.
- Revisadas las 72 a ojo, idioma por idioma — no una muestra.

## Lo que costó, medido (para la próxima tanda)

- ⛔⛔ **El build de una tanda necesita dos cosas que se peleaban**: el mapa sólo pinta si la firma
  pasa la restricción de la key de Maps (paquete + SHA-1, y la huella de DEPURACIÓN no está en esa
  lista), y `--es shot <preset>` sólo funciona sin R8. mock+release pinta el mapa e ignora todos los
  presets (arranca en la pantalla que la app restauró); mock+debug obedece los presets y pinta el
  mapa NEGRO. `-PmockAsProd` pasa a firmar también el debug con la key de release.
- ⛔⛔ **Un tap de carril que no entra NO se nota**: deja una captura plausible de OTRO carril. La
  primera pasada trajo tres listas de plazas libres etiquetadas gasolineras, parkings y ficha.
- ⛔ **Y el primer chequeo que escribí para eso tampoco valía**: preguntaba «¿está ese número por
  encima de y=1300?», y el badge de la propia pestaña lo está. Un chequeo de selección tiene que
  mirar lo que SÓLO existe si la selección ocurrió — aquí, el contador de la cabecera de la hoja, en
  la columna izquierda. Verificar por estructura, no por presencia.
- ⛔ La hoja no cae en el mismo detente: el MISMO gesto dejó la fila de pestañas en y=1202 y en
  y=1713. Cualquier `y` fija es una moneda al aire; las coordenadas salen de un `uiautomator dump`
  tomado en el mismo instante.
- ⛔⛔ **Y eso vale también para el RECORTE ampliado, no sólo para el tap.** Con un recorte fijo
  medido en inglés, la tarjeta del coche aparcado estaba en y=1267 en ocho idiomas y en **y=375**
  en `es` — así que la ventana de `pl` amplió un trozo de carretera. Por eso el rect lo dice ahora
  el DEVICE, por idioma (`anchor` en el guion → `uiquery --rect`), anclado en un nombre propio de la
  fixture que nadie traduce («Camden Street, 21», «Northgate Fuel») o, cuando la unidad sí se
  traduce, en el valor por prefijo (`45` para «45 min» / «45 Min.»). El zoom se DERIVA del rect para
  que la ventana mida siempre lo mismo en el lienzo.
- ⛔ **`adb` y `python` son binarios de Windows**: el destino de un `adb pull` y la ruta que se le
  pasa a python tienen que ser rutas Windows aunque el guion hable POSIX. Con `MSYS_NO_PATHCONV=1`
  puesto (que hace falta para que `/sdcard/...` llegue sin mutilar) un destino POSIX se pasa tal
  cual y el pull **falla en silencio**: el dump no llega, todas las búsquedas fallan y la sesión
  tapea a ciegas. Ésa era la causa REAL de las capturas del carril equivocado.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `tools/shoot.sh` | ✅ reescrito: 11 raws, nombres propios de Dublín, `select_lane` con verificación estructural |
| `assets/screenshot-frame.html` | ✅ motor de composición (tira de 8640×1920 · hero/pair/detail · marco de dispositivo) |
| `tools/render.sh` | ✅ sin rutas de worktree: raíz por argumento y assets relativos al script |
| `README.md` de la ficha | ✅ tabla nueva (forma + raws) y el «cómo se repite» con las dos trampas medidas |
| `web/` | ✅ no promete nada de esto; sus capturas se regeneran aparte con `prepare-assets.py` |
| `tools/framescan.ps1` | ✅ sustituido por `tools/scan.py`, que además caza el carril equivocado |
| `app/build.gradle.kts` | ✅ `-PmockAsProd` firma el debug con la key de release (sólo si hay keystore) |
