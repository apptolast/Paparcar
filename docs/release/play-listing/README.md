# Ficha de Play Store — qué hay aquí y qué falta

> **Nueva tanda guardada en Google Play — 09-11-2026.** La [galería local](assets/review.html)
> reúne ocho composiciones en cada uno de los nueve idiomas; los PNG están en
> `assets/screenshots/<idioma>/`. Son derivados ignorados por Git, y sus raws y anclas están en
> `build/store-raw/`, que puede borrarse al limpiar Gradle. El bloqueo de permisos 403 se resolvió.
> Google aceptó la edición: 72 capturas y nueve descripciones completas. Una lectura posterior
> confirmó huellas, orden y textos; los canales no cambiaron. La visibilidad pública depende de
> la revisión y propagación de Google.
> Las descripciones completas revisadas están en `LISTING-v1.5.0.md` y `paste/`.
> El flujo actual usa el Pixel 8, el build
> `:app:assembleMockDebug -PmockAsProd` y `tools/shoot.sh <idioma> <rawroot>/<idioma>`.
> Home abre las listas desde acciones traducidas de «ver todas», no desde los badges sueltos
> descritos en el registro histórico de abajo. `uiquery.py` lee esas etiquetas de los recursos de
> la app y verifica la pantalla abierta. El inglés se captura con `en-IE` para usar km y euros.
> Los detalles ampliados requieren los archivos `.anchor.txt` de esa misma captura; si faltan,
> `render.sh` falla. El render usa los raws de cada idioma directamente y no sobrescribe `raw/`.
> Ejecutar `scan.py raws <rawroot>` antes del render y `scan.py frames <assets>` después; revisar
> visualmente todos los resultados. La tanda y sus validaciones se registran en
> [PLAY-SHOTS-MOBILITY-CAROUSEL-001](../../backlog/play-shots-mobility-carousel-001.md).

## Subir sólo el TEXTO de la ficha (lo normal al publicar una versión)

Cuando Play publica una versión nueva: mover `published-listing.txt`, regenerar `paste/` y, con
autorización del user, subirlo. Sólo título y descripciones de los 9 idiomas; las capturas no se
tocan.

```bash
python docs/release/play-listing/tools/make_paste.py
python tools/release/cut.py --phases listing   # sube, valida y RELEE lo publicado
python tools/release/cut.py --phases verify    # compara titulo, corta y COMPLETA
```

⛔ Nunca cancela una revisión en curso: el commit va con `ERROR_IF_IN_REVIEW`, así que si hay algo
en revisión Play lo rechaza y no cambia nada. Sin ese parámetro la API cancela la revisión y la
reenvía. ⛔ Hasta el 10-11 `verify` no comparaba la descripción completa y dio «cuadran» con la
ficha 1.6.0 sin subir. [PLAY-THE-LISTING-MOVES-TO-1-6-0-001]

## Actualizar capturas y descripción mediante la API

Con autorización del usuario para modificar la ficha, desde la raíz del worktree:

```bash
python docs/release/play-listing/tools/make_paste.py --check
node tools/release/store_listing.mjs --inspect <ruta-a-la-credencial-local>
node tools/release/store_listing.mjs --check-permissions <ruta-a-la-credencial-local>
node tools/release/store_listing.mjs --apply <ruta-a-la-credencial-local>
```

La inspección guarda la ficha, los canales y las huellas de las capturas anteriores en
`build/store-publish/before.json`, junto al plan local, y descarta su edición temporal.
`--check-permissions` prepara una descripción en una edición temporal, valida y descarta;
no guarda cambios. `--apply` repite esa comprobación antes de subir imágenes.
La aplicación exige que el plan y la ficha remota sigan iguales; sustituye sólo las ocho capturas
de teléfono y la descripción completa de cada idioma. Comprueba las huellas y el orden, valida
la edición, hace un único commit de Play y vuelve a leer el resultado desde otra edición.
Conserva los recibos locales; la siguiente inspección se detiene si ya existe un baseline para no
sobrescribirlo. Estos archivos están bajo `build/` y se pueden perder al limpiar Gradle.
El guardado en la API no acredita que Google haya terminado su revisión ni que los cambios sean
ya visibles en la ficha pública. La credencial se consume mediante el cliente existente sin
imprimirla ni guardar su token. El comando no cambia binarios ni canales.

## Registro histórico

Generado el 2026-08-29 para la **Ficha de Play Store predeterminada** (en-US).

| Fichero | Qué es |
|---|---|
| `LISTING.md` | Los textos: nombre, descripción breve y completa, en **en-US** y **es-ES**, más los campos de contacto/URLs |
| `assets/play-icon-512.png` | **Icono de la aplicación** — 512×512, PNG RGB (sin alpha), 15 KB |
| `assets/play-feature-graphic-1024x500.png` | **Gráfico de funciones** de la ficha predeterminada (en-US) — 1024×500, PNG RGB, 199 KB |
| `assets/screenshots/<loc>/<loc>-feature-graphic-1024x500.png` | El mismo gráfico en los otros ocho idiomas. **Derivados y gitignored**: se regeneran, no se versionan |
| `assets/alt-icon-512-*.png` | Dos alternativas del icono, por si prefieres otro tamaño de glifo o el fondo con halo |
| `assets/icon.html`, `assets/feature.html` | Las fuentes de los dos gráficos. Se re-renderizan con `tools/render_graphics.sh` (abajo) |

Los gráficos están construidos con los assets reales del proyecto: el glifo sale del maestro
`docs/branding/master/` (y ahí se regenera — ⛔ no se reescribe a mano en el HTML), los colores de `ui/theme/Color.kt`
(`PapGreen #25F48C`, `PapInk #0D1117`, `PapAmber #F4A825`, `PapBlue #5B9EFF`) y la
tipografía es la **Plus Jakarta Sans** que embarca la app — la única familia del repo desde
`UI-TYPE-RETIRE-THE-OLD-FAMILIES-001`. No hay ni un color ni una fuente inventada.

## Qué subir al Console, y de dónde sale

Re-renderizado el **29-09-2026** con la marca de 2026
[PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001].

| Campo del Console | Fichero |
|---|---|
| Icono de la aplicación | `assets/play-icon-512.png` — uno solo, no se localiza |
| Gráfico de funciones · en-US | `assets/play-feature-graphic-1024x500.png` |
| Gráfico de funciones · los otros 8 | `assets/screenshots/<loc>/<loc>-feature-graphic-1024x500.png` |
| Capturas de teléfono | `assets/screenshots/<loc>/<loc>-play-shot-{1..8}.png` — ⚠️ derivadas: si no están, `tools/render.sh` |
| Nombre · breve · completa, 9 idiomas | `paste/play-<locale>.txt` (o `paste/play-listing-import.md` del tirón) |

⛔ **Los gráficos por idioma y las capturas son derivados gitignored**: en un clon limpio no están,
y eso es a propósito (59 MB en un repo de 88). Antes de abrir el Console, correr el render.

**Resuelto el 09-11-2026:** las nuevas capturas y descripciones completas de los nueve idiomas
se guardaron mediante la API, tras corregir el permiso de presencia en tienda. La lectura desde
una edición nueva confirmó las 72 imágenes y su orden, los textos y los canales sin cambios.
Recibos locales en `build/store-publish/commit.json` y `after.json`; su existencia no acredita
que Google haya finalizado su revisión o propagación pública.

## Re-renderizar los gráficos

```bash
python docs/branding/master/export_surfaces.py      # PRIMERO, si cambió el dibujo de la marca
bash docs/release/play-listing/tools/render_graphics.sh             # icono + los 9 idiomas
bash docs/release/play-listing/tools/render_graphics.sh feature es  # sólo uno
```

⛔ **Aquí había un comando de Chrome suelto, y ése es el defecto que este script cierra**
[PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001]: los dos HTML llevan la **marca**, la marca se
GENERA (`export_surfaces.py` reescribe sus grupos del glifo), y un comando que hay que copiar,
completar a mano con una ruta absoluta y repetir diez veces no se vuelve a correr. Resultado
medido: `play-icon-512.png` pasó **ocho días** enseñando el logo de 2022 mientras su propio
`icon.html` ya tenía el de 2026 dentro.

El script hace las tres cosas que el comando dejaba al lector: recorta las **tres celdas** del
icono (el row es 1536×512), aplana a **RGB** (Chrome escribe RGBA y Play quiere los dos gráficos
opacos) y da tiempo a que cargue la fuente — sin `--virtual-time-budget` la captura puede caer en
la familia de respaldo, que **parece un render que salió bien**. `feature.html` busca las fuentes
en `assets/fonts/` (ya están; salen de `shared/src/commonMain/composeResources/font/`).

---

## Estado de los campos del Console

### ⛔ Qué está vivo en Play y qué falta pegar — leído del Console el 27-09
[PLAY-A-LISTING-MUST-MATCH-THE-BUILD-IN-PRODUCTION-001]

**En producción: 1.4.0 (vc 15), publicada el 13-10-2026.** La ficha que hay que pegar es la de
`LISTING-v1.4.0.md` (la nombra `published-listing.txt`), y **nada de ella está pegado todavía**: lo
vivo en el Console sigue siendo el copy de 1.3.0 — sin las tres funciones nuevas y **con el paso 1
viejo de «CÓMO FUNCIONA»**:

| | Texto |
|---|---|
| Vivo en Play (es-ES) | «1. Aparcas. Paparcar lo detecta y guarda el sitio. **También puedes marcarlo a mano con un toque.**» |
| Lo que hay que pegar | «1. Aparcas. Paparcar lo detecta y guarda el sitio. **Sin Bluetooth, el primer aparcamiento lo marcas tú.**» |

⏳ **Paso del user, en dos partes:**

1. **Pegar los 9 idiomas** desde `paste/` — nombre, breve y completa. Trae el paso 1 corregido
   (`72e78f9bd`, 25-09: el «también» lo lee como un extra opcional y es lo contrario — en el carril
   Coordinator, sin Bluetooth, **el primer aparcamiento hay que marcarlo o no hay nada que vigilar
   ni que liberar**) **y** las tres funciones que 1.4.0 publicó.
2. **Marcar la casilla de widget** en el Console. Es campo propio de Play, no copy: el texto puede
   nombrar el widget y la ficha seguir sin declararlo.

⚠️ Comprobar también la **descripción breve**: la vigente es la que nombra gasolina y parkings
(intacta en 1.4.0 — no cabe una cuarta promesa en 80 caracteres), y hasta este ticket eso no estaba escrito en ninguna parte — el fichero
ofrecía dos tablas y `paste/` llevaba la otra (la del 29-08, «y tu plaza pasa al siguiente»).

**Largos con el paso 1 ya corregido dentro** (Play cuenta caracteres, no bytes):

| Locale | Nombre | Breve | Completa |
|---|---|---|---|
| en-US | 28/30 | 72/80 | 3.798/4.000 |
| es-ES | 25/30 | 75/80 | 3.904/4.000 |
| it-IT | **30/30** | 70/80 | 3.927/4.000 |
| pt-PT | 27/30 | 62/80 | 3.905/4.000 |
| fr-FR | 28/30 | 73/80 | **3.986/4.000** |
| de-DE | 28/30 | 78/80 | **3.991/4.000** |
| nl-NL | 28/30 | 68/80 | **3.972/4.000** |
| pl-PL | 28/30 | 66/80 | 3.833/4.000 |
| ro | 24/30 | 67/80 | 3.879/4.000 |

⛔ **fr-FR va a 14 caracteres del tope, de-DE a 9, y it-IT no tiene ni uno de margen en el
nombre.** Cualquier añadido al copy hay que medirlo ahí primero, no en en-US — con 1.3.0 el francés
tenía 11 libres y el alemán 44, así que las tres funciones de 1.4.0 **no cabían**: entraron
condensando frases que decían lo mismo dos veces, idioma por idioma. El detalle está en
`LISTING-v1.4.0.md` § *Lo que costó sitio*.

### ⛔ `paste/` se GENERA — no se edita a mano

```bash
python docs/release/play-listing/tools/make_paste.py            # escribe los 10 ficheros
python docs/release/play-listing/tools/make_paste.py --check     # solo comprueba
```

La fuente única es **la ficha que nombra `published-listing.txt`** (hoy `LISTING-v1.4.0.md`):
nombres de app, descripción breve vigente y los 9 bloques de descripción completa. ⛔ Ese puntero es
el ÚNICO sitio donde vive el nombre; antes estaba cableado en el generador **y** en el guard, así que
escribir la ficha de un build nuevo y olvidar una copia dejaba el guard verificando una ficha
retirada. [PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001] **Por qué existe el generador:** el copy vivía en dos sitios y nada los
comparaba, así que `paste/` se quedó **dos generaciones atrás** — 29 días prometiendo *«Todos tus
coches en un mismo garaje»*, que `MONEY-001` ya había hecho falso (`FREE_OWN_VEHICLE_LIMIT = 1`). No
llegó a producción por suerte: quien pegó, pegó del `.md`.

El testigo es **`PlayListingPasteParityGuardrailTest`** (4 chequeos, los cuatro falsificados:
editar un pegable a mano, borrarle el banner, romper el parser renombrando la cabecera, mover el
puntero sin regenerar `paste/`, y apuntarlo a una ficha que no existe). El banner exige el **nombre**
de la ficha, no sólo la palabra GENERADO: así, mover el puntero y no regenerar sale rojo. Como no toca
bytecode sale **UP-TO-DATE** → `--rerun-tasks`.

### ⛔ La ficha describe el build PUBLICADO, no master

✅ **1.4.0 está en Play desde el 13-10-2026**, y con eso esta regla cambió de lado: lo que hasta
ayer prohibía nombrar, hoy lo obliga. Hecho en `LISTING-v1.4.0.md` y en la web, con cada promesa
verificada contra el código **y** contra el corte del AAB publicado (`8c45ce88a`): las tres son
ancestras de ese commit, así que están en lo que la gente tiene instalado.

| Qué entra | En master | Qué se promete |
|---|---|---|
| **Widget** del launcher | `d5696b774` | dónde está el coche, cómo ir, y contestar un viaje **sin abrir la app** |
| **Gálibo** del coche | `a0f57bf1a` | el alto declarado, o la banda de su carrocería, contra la barrera — y un alto desconocido nunca es un «cabes» |
| **Acierto** de la detección | `4ef6cd541` | el porcentaje de viajes que detectó sola, con el recuento detrás |

⏳ Queda **del user**: pegar los 9 idiomas y **marcar la casilla de widget** del Console (campo de
Play, no copy).

⛔ **La cuarta NO entra, y no es por la versión.** El aviso de plaza cercana sólo salta hoy dentro de
una zona guardada, que es el aviso de Plus → `docs/backlog/zone-an-alert-near-you-is-not-an-alert-in-your-zone-001.md`.

✅ La web entró en la misma tarea (`web/content/{es,en}.json`, los dos idiomas, dentro de las
tarjetas que ya hablaban de eso).


### ✅ Listo para pegar
- [x] Nombre, descripción breve y descripción completa — los 9 idiomas en `paste/`, generados desde
      la ficha que nombra `published-listing.txt`. ⛔ **Los largos NO se repiten aquí**: están en la tabla de arriba, y ésta
      es la única que se mantiene. Tres cifras del 29-08 vivieron en esta lista hasta el 27-09
      contradiciendo al copy vigente, que es el mismo defecto que este ticket vino a cerrar.
      ⚠️ El nombre es **con tilde** en es-ES (`dónde`), y en it-IT va a 30/30 sin margen.
- [x] Icono de la aplicación — vigente el de glifo al 72% (`icon.html` celda B), decisión
      confirmada el 03-09. ⛔ **El launcher NO lleva ese número y no debe llevarlo**: el
      02-09 se le subió a 0.72 «para que ambos midan igual» y eso era falso — el icono
      adaptativo solo enseña los **72 centrales de 108**, así que a igual número el glifo
      se ve 1,5× más grande en el móvil. Medido sobre los PNG: a 0.72 el glifo ocupa el
      **48,4 %** del tile de Play pero el **72,7 %** del tile visible del launcher. El
      launcher volvió a **0.60** el 03-09 (= 60,4 % de su tile visible). Igualarlos a ojo
      exigiría subir el de Play a ~0.90, que deja el coche pegado al borde: descartado.
- [x] Gráfico de funciones — lleva dibujada la frase de marca, no un campo de texto.
      Texto actualizado el 29-08 a «Park and forget it. Paparcar remembers.»
      **Localizado el 02-09 a los 9 idiomas**: `screenshots/{lang}/feature-graphic-1024x500.png`
      (la frase sale de `feature.html?lang=…`; tipografía migrada a Plus Jakarta Sans porque
      las Outfit/Inter que pedía la plantilla ya no existen en el repo — el
      `play-feature-graphic-1024x500.png` de la raíz **ya no está superseded**: desde el 29-09
      es exactamente el render `en` —misma Jakarta, misma marca— y es el único de los diez que
      sobrevive a un clon limpio, porque los otros ocho son derivados gitignored).

> Copy vigente y su auditoría: `LISTING.md`. El razonamiento completo (qué se promete,
> qué se descarta y por qué) está en `PLAN-OPTIMIZACION.md`.

### ✅ Capturas de teléfono — rehechas el 25-09 en **Pixel 8**, compuestas y en los 9 idiomas
[PLAY-SHOTS-A-LISTING-IS-COMPOSED-NOT-CROPPED-001]

`assets/screenshots/{en,es,it,pt,fr,de,nl,pl,ro}/<locale>-play-shot-{1..8}.png` (1080×1920),
p. ej. `de/de-play-shot-4.png`. El gráfico de funciones va igual:
`de/de-feature-graphic-1024x500.png`. ⛔ **El locale va en el NOMBRE, no sólo en la carpeta**: las
72 acaban juntas en la bandeja de descargas y en el selector del Console, y nueve ficheros llamados
`play-shot-1.png` se pisan entre ellos sin forma de saber cuál es cuál sin abrirlos.

**Qué cambia respecto al set del 17-09.** Aquél era *una captura recortada con un titular encima*:
sin marco, el móvil sangrando por el borde inferior y una pantalla por imagen. Y había caducado, con
el mundo de demostración todavía en El Puerto de Santa María (`4b90ae506` lo mudó a **Dublín**).
Ahora las ocho imágenes se **componen**:

1. **Una tira continua.** Las 8 son ocho ventanas de UN lienzo de 8640×1920: el degradado, los focos
   verdes y la traza de ruta cruzan de una imagen a la siguiente, así que el carrusel se lee como un
   solo panel — y cada imagen sigue valiendo suelta, porque nada con significado se parte por la mitad.
2. **Marco de dispositivo de verdad.** Bisel, radio proporcional al ancho dibujado y sombra propia:
   el móvil entra ENTERO.
3. **La forma sigue a la promesa.** `hero` cuando una pantalla lo dice todo (1 y 8), `pair` cuando la
   historia son dos momentos (3, 5 y 7), `detail` cuando el valor está en un dato que merece
   ampliarse (2, 4 y 6). ⛔ **El recorte ampliado sale del MISMO raw**, nunca de una maqueta HTML que
   imite la UI, y va **sin leyenda**: una leyenda serían nueve traducciones nuevas.

| # | Forma | Qué enseña | Raws |
|---|---|---|---|
| 1 | hero | Home, coche propio aparcado: calle y minuto sobre el mapa | `home` |
| 2 | detalle | Tus coches en la hoja + la fila del aparcado, ampliada | `sheet` |
| 3 | par | Las plazas libres: marcadores en el mapa y filas en la lista | `spotmap` + `spots` |
| 4 | detalle | Gasolineras + una fila con su precio, ampliada | `fuel` |
| 5 | par | El carril de parkings y uno abierto: tarifa, plazas y gálibo | `carparks` + `carpeek` |
| 6 | detalle | Panel de cifras + «tiempo buscando plaza», ampliado | `numbers` |
| 7 | par | El garaje y el grupo del coche compartido | `garage` + `members` |
| 8 | hero | Ajustes: qué avisa y qué no sale del móvil | `settings` |

El copy de los 8 titulares **no cambió**: esta tanda cambia la FORMA, no lo que se promete.

**Cómo se repite la tanda**

```bash
./gradlew :app:assembleMockDebug -PmockAsProd          # + install -r en el emulador
bash tools/shoot.sh en  <rawroot>/en                   # 11 raws de un idioma (~8 min)
python tools/scan.py raws <rawroot> en                 # ⛔ ANTES de renderizar
bash tools/render.sh <rawroot> en es it pt fr de nl pl ro
python tools/scan.py frames <ruta a assets>            # las 72 compuestas
```

- **AVD `Pixel_8`** (1080×2400 @ 420 dpi — se creó a mano, el SDK sólo traía el Pro).
- ⛔ **El build de una tanda necesita las DOS mitades y se peleaban entre ellas**: el mapa sólo pinta
  si el build pasa la restricción de la key de Maps (paquete + SHA-1, y **la huella de depuración no
  está en esa lista**), y `--es shot <preset>` sólo funciona **sin R8** (R8 renombra las constantes
  de `ShotPreset`, así que `from("parked")` devuelve null y la tanda dispara la pantalla que la app
  hubiera restaurado). Medido el 25-09: mock+release pinta el mapa e ignora todos los presets;
  mock+debug obedece los presets y pinta el mapa NEGRO. Por eso `-PmockAsProd` ahora **firma también
  el debug con la key de release**: mismo paquete, mismo certificado, sin R8.
- **`DevMainActivity` acepta `--es shot <preset>`** (`ShotPresets.kt` en `:mockkit`): monta la app
  real con el escenario puesto y los chips DEV apagados.
- Idioma por captura vía `adb shell cmd locale set-app-locales <pkg> --locales <L>` (per-app locale,
  Android 13+). ⛔ `setprop persist.sys.locale` **no** vale: la imagen de sistema Play Store no deja
  rootear. Los rótulos del mapa los localiza Google con ese mismo locale; los nombres de calle de
  Dublín se quedan en inglés, que es el idioma del sitio.
- Tema oscuro (`cmd uimode night yes`) + barra de estado en modo demo de SystemUI (reloj 9:30,
  batería llena, sin notificaciones).

⚠️ **Los taps dentro de la hoja se resuelven por `uiautomator dump`, no por coordenada fija.** La
hoja no cae siempre en el mismo detente: medido, el MISMO gesto dejó la fila de pestañas en y=1202 y
en y=1713. Se busca el nodo por TEXTO: las pestañas por su **badge numérico** (`6`/`4`/`5`, cifras
árabes iguales en los 9 idiomas) y la fila del parking por **«Drury Street Car Park»**, nombre propio
de la fixture que ningún idioma traduce.

⛔ **Y un tap que no entra NO se nota**: deja una captura perfectamente plausible de OTRO carril. En
la primera pasada de esta tanda salieron tres listas de plazas libres etiquetadas como gasolineras,
parkings y ficha de parking. Por eso `select_lane` **verifica** que el carril está vivo, y lo
verifica por ESTRUCTURA: el contador de la cabecera de la hoja, que vive en la columna izquierda
(x<200), no «que ese número esté en pantalla» — el badge de la propia pestaña también lo está, y esa
primera versión del chequeo daba por buena una selección que no había ocurrido.

⛔ **Revisar la tanda con `tools/scan.py` ANTES de subir; mirar una muestra no vale.** Caza lo que no
se nota: fichero de 0 bytes (el emulador se cae a media tanda y escribe vacío, no basura), diálogo
del sistema por encima (gris **neutro**, `B−R ≤ 10`, mientras el mapa (28,42,64) y la hoja (19,26,36)
son azulados), mapa sin tiles (casi negro) y **carril equivocado** (comparando la firma de `fuel`,
`carparks` y `carpeek` contra la de `spots`).

⚠️ **El emulador se cayó dos veces a media tanda** en la sesión del 17-09 (WHPX `hr=80070005` tras
muchos relanzamientos seguidos). Las capturas de esa ventana salen en **0 bytes**: es lo primero que
mira el scan.

### Sobre el ANR que se coló (leído de su traza, no supuesto)

`dumpsys dropbox --print data_app_anr` → la entrada cuadra al segundo con la captura (⚠️ el reloj
del emulador va en **UTC**: device 01:02 = host 03:02). El hilo principal estaba así:

```
"main" prio=5 tid=1 Native
  DrawFrameTask::drawFrame  ← pthread_cond_wait
  HardwareRenderer.syncAndDrawFrame / ViewRootImpl.performDraw / Choreographer.doFrame
```

**Ni un solo frame de Paparcar ni de Compose en la pila**: el main estaba parado esperando al
RenderThread dentro del pipeline de la plataforma. Y alrededor, `39%` del compositor `ranchu` (el
virtual del emulador) + `13%` de surfaceflinger, **29% iowait** y presión de IO `full avg300=51%`,
con sólo `3.2%` de CPU de usuario. Es decir: **el host se quedó sin disco y el emulador no pintó el
frame a tiempo**, no que la app hiciera trabajo en el hilo principal. Confirmado además porque el
mismo preset y el mismo gesto salieron limpios en los otros 8 idiomas. ⛔ No es un bug de producto
y no hay nada que arreglar en el código — pero **sí hay que mirar las 72**, porque el sistema
estampa su diálogo encima de la app y la captura sale publicable a la vista.

Los fakes de gasolineras se saneron para poder publicar: las marcas reales (`Repsol`, `Cepsa`) y
las direcciones de Madrid pasaron a nombres genéricos y calles de El Puerto — una marca de terceros
dentro de una captura de tienda, encima con una dirección a 600 km del pin de la cabecera.

<details>
<summary>Set anterior — 01-09 (en-US), localizado el 02-09 (superseded)</summary>
**Una carpeta por idioma con TODO lo que se sube a su ficha**:
`assets/screenshots/{en,es,it,pt,fr,de,nl,pl,ro}/` → `play-shot-{1..8}.png`
(1080×1920, orden del carrusel ya en el nombre) + `feature-graphic-1024x500.png`.
Solo PNG: los JPG duplicados se retiraron el 02-09 — Play acepta el PNG directamente y
un JPG se regenera de la plantilla en segundos si hiciera falta.
Las 8: 1 Home aparcado · 2 sheet «Watching» · 3 plaza recién liberada ·
4 conducción · 5 «Too tight» (SpotFit) · 6 garaje de Vehículos · 7 Historial · 8 Ajustes.
Raws del emulador en `assets/screenshots/raw/` y plantilla en `assets/screenshot-frame.html`
(misma estética que `feature.html`; tipografía Plus Jakarta Sans, la única que embarca la app
desde UI-TYPE-RETIRE-THE-OLD-FAMILIES-001).

⚠️ **Lo localizado es el titular y el subtítulo del frame** (objeto `T` de la plantilla,
`?n=1..8&lang=en|es|it|pt|fr|de|nl|pl|ro`, vocabulario plaza≠aparcamiento verificado contra
`LISTING-i18n.md`, FR en *vous*). **La UI del móvil dentro del frame sigue en inglés**: las
raws se capturaron una vez, en EN. Si algún día se quieren raws por idioma, hay que repetir
la sesión de emulador (parche `if (false)`, escenarios, etc.) con
`adb shell "setprop persist.sys.locale <lang> ; am force-stop com.rndeveloper.paparcar"` —
la app embarca los 9 locales, así que es solo tiempo de sesión, ×8.

Cómo se hicieron — repetible:
- **Pixel 8 Pro (emulador) + flavor `mock`**, tema oscuro del sistema, mapa en tipo NORMAL
  (el estilo de marca oscuro NO rinde en TERRAIN, el tipo por defecto — toggle superior
  derecho de Home). Barra de estado en modo demo de SystemUI.
- Parche temporal en `DevRoot` para ocultar los chips `DEV`/`☀🌙`: envolver el cluster en
  `if (false)` — **`alpha(0f)` no vale: el glifo emoji se salta la capa de alpha**. El parche
  se REVIRTIÓ; si se repite la sesión, reaplicar y navegar relanzando la app (`am force-stop`
  + launcher) entre escenarios.
- Escenarios usados: presets «Aparcado (vigilando)» (1), ídem + sheet desplegado (2),
  «Vigilando por Bluetooth» (4º frame, conducción), «Home (todo OK)» + tap en marcador de
  plaza (3º y 5º frames: tarjeta JUST-FREED y tarjeta «Too tight for your Medium»),
  «Aparcado (vigilando)» → tab Vehículos (6º) y su scroll al Historial (7º), y Settings (8º).
- Para la sesión de Vehículos/Historial las calles placeholder del historial fake
  («Calle Histórica N», «Av. Corolla N», «Paseo Moto N», «Calle Furgoneta N») pasaron a
  calles reales de El Puerto indexadas por módulo — la repetición se conserva para que el
  «usual street» siga alcanzando su umbral de ≥3.
- Para re-renderizar frames: mismo comando Chrome headless de abajo con
  `--window-size=1080,1920 --screenshot=... "file:///…/screenshot-frame.html?n=1..6"`.

Los fakes se retocaron EN EL ÁRBOL para que la sesión de fotos fuera publicable (pins que
caían en el río Guadalete movidos a calle, direcciones coherentes con la coordenada,
«Mercadona»/«Repsol» → nombres genéricos por la política de marcas de terceros en capturas,
y perfil «Rene Dev / rene@paparcar.mock» → «Alex García / alex@paparcar.app»). Son mejoras
legítimas del Dev Catalog, pendientes de commit.

⚠️ Guiones anteriores: el vigente es `PLAN-OPTIMIZACION.md` § 15-17. Dos titulares del
guion viejo cayeron en la revisión del 29-08: «No necesitas hacer nada» (demasiado absoluto
— la app **sí** pregunta cuando duda) y «Encuentra plazas que acaban de quedar libres»
(promesa de resultado que la estrategia evita). Los frames finales usan los titulares
corregidos, en EN (la ficha predeterminada es en-US; localizarlos es opcional y barato:
están en el objeto `FRAMES` de `screenshot-frame.html`).

</details>

### ⏳ Pendiente — fuera de esta ficha, pero bloquea la publicación
- [ ] **Cuenta de trader** en Play Console (lo tienes marcado como pendiente tuyo)
- [ ] Verificaciones de identidad + nombre legal
- [ ] **Data Safety form** → las respuestas ya están escritas en `docs/legal/DATA-SAFETY-FORM.md`
- [ ] **Declaración de permisos** de `ACCESS_BACKGROUND_LOCATION` y `FOREGROUND_SERVICE_LOCATION`:
      exige un **vídeo** enseñando el aviso destacado del onboarding ANTES del diálogo del sistema.
      Es un campo distinto del "Vídeo" de esta ficha (ese es un tráiler de YouTube y es opcional).
- [ ] Clasificación de contenido (cuestionario IARC)
- [ ] Público objetivo (no dirigida a menores)

### Traducciones — ✅ los 9 idiomas listos
- **en-US** y **es-ES** → `LISTING.md`
- **it, pt, fr, de, nl, pl, ro** → `LISTING-i18n.md`

Todos verificados: 21 campos dentro de límite y la distinción **plaza ≠ aparcamiento**
comprobada idioma por idioma.

⛔ **No usar «Importar traducciones con IA» del Console para traducir.** Sirve para *subir*
estas traducciones de una vez (botón «Importar un archivo»), pero no debe generarlas: no
conoce la tabla de vocabulario de `CLAUDE.md` ni los topes de 30 / 80 caracteres, y el
bloque de ubicación en segundo plano tiene que decir literalmente lo mismo que la política.

⏳ Decisión pendiente: **pt-PT o pt-BR** (ver `LISTING-i18n.md` § Locales).
