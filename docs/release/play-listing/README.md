# Ficha de Play Store — qué hay aquí y qué falta

Generado el 2026-08-29 para la **Ficha de Play Store predeterminada** (en-US).

| Fichero | Qué es |
|---|---|
| `LISTING.md` | Los textos: nombre, descripción breve y completa, en **en-US** y **es-ES**, más los campos de contacto/URLs |
| `assets/play-icon-512.png` | **Icono de la aplicación** — 512×512, PNG RGB (sin alpha), 15 KB |
| `assets/play-feature-graphic-1024x500.png` | **Gráfico de funciones** — 1024×500, PNG RGB, 247 KB |
| `assets/alt-icon-512-*.png` | Dos alternativas del icono, por si prefieres otro tamaño de glifo o el fondo con halo |
| `assets/icon.html`, `assets/feature.html` | Las fuentes de los dos gráficos. Se re-renderizan con Chrome headless (comando abajo) |

Los gráficos están construidos con los assets reales del proyecto: el glifo sale de
`composeResources/drawable/paparcar_logo.xml`, los colores de `ui/theme/Color.kt`
(`PapGreen #25F48C`, `PapInk #0D1117`, `PapAmber #F4A825`, `PapBlue #5B9EFF`) y la
tipografía es la Outfit/Inter que embarca la app. No hay ni un color ni una fuente inventada.

## Re-renderizar los gráficos

```bash
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
  --force-device-scale-factor=1 --window-size=1024,500 \
  --screenshot=out.png "file:///<ruta absoluta>/feature.html"
```
`feature.html` necesita las fuentes en `./fonts/` (cópialas de
`shared/src/commonMain/composeResources/font/`). El icono se renderiza con
`--window-size=1536,512` y se recorta en tres celdas de 512.

---

## Estado de los campos del Console

### ✅ Listo para pegar
- [x] Nombre de la aplicación — `Paparcar: where did you park` (28/30) en la ficha en-US;
      `Paparcar: dónde aparcaste` (25/30) en es-ES — **con tilde** en `dónde`
- [x] Descripción breve — 65/80 (EN) · 76/80 (ES)
- [x] Descripción completa — 2.734/4.000 (EN) · 2.853/4.000 (ES)
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
      `play-feature-graphic-1024x500.png` de la raíz es el render Outfit del 29-08, superseded).

> Copy vigente y su auditoría: `LISTING.md`. El razonamiento completo (qué se promete,
> qué se descarta y por qué) está en `PLAN-OPTIMIZACION.md`.

### ✅ Capturas de teléfono — rehechas el 17-09 en **Pixel 8**, con la UI en los 9 idiomas
[PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001]

`assets/screenshots/{en,es,it,pt,fr,de,nl,pl,ro}/<locale>-play-shot-{1..8}.png` (1080×1920),
p. ej. `de/de-play-shot-4.png`. El gráfico de funciones va igual: `de/de-feature-graphic-1024x500.png`.
⛔ **El locale va en el NOMBRE, no sólo en la carpeta**: las 72 acaban juntas en la bandeja de
descargas y en el selector del Console, y nueve ficheros llamados `play-shot-1.png` se pisan entre
ellos sin forma de saber cuál es cuál sin abrirlos.
**Lo que cambia respecto al set del 01-09**: el móvil de dentro del marco ya **no está en inglés
en las nueve fichas** — cada idioma tiene sus 8 capturas tomadas con la app en ese idioma. Y el
guion se rebalanceó a lo que entró después: gasolineras con precio, parkings públicos, panel de
cifras y coche compartido (caen SpotFit, garaje y conducción).

| # | Pantalla | Preset |
|---|---|---|
| 1 | Home, coche propio aparcado | `parked` |
| 2 | Íd. con la hoja abierta: tus coches con calle y minuto | `parked` |
| 3 | Carril de plazas libres | `map` |
| 4 | Carril de gasolineras, precio por surtidor | `map` |
| 5 | Peek de un parking público: tarifa, plazas, gálibo | `map` |
| 6 | Panel de cifras: gasto, combustible, CO₂, sitio habitual | `numbers` |
| 7 | Coche compartido: miembros e interruptor de rutas | `sharedcar` |
| 8 | Ajustes: alertas y dónde se guardan tus viajes | `settings` |

**Cómo se repite** (el flujo entero es reproducible, ya no hay parche manual que reaplicar):
- **AVD `Pixel_8`** (1080×2400 @ 420 dpi — se creó a mano, el SDK solo traía el Pro). Mismo ratio
  9:20 que el Pixel 8 Pro del set viejo, así que la plantilla del marco no cambió.
- **`DevMainActivity` acepta `--es shot <preset>`** ([`ShotPresets.kt`]): aplica el escenario y monta
  la app real con los chips DEV ocultos. ⛔ Ya **no** hace falta el parche `if (false)` en `DevRoot`
  que el guion del 01-09 mandaba aplicar y revertir a mano.
- **`-PmockAsProd`** quita el sufijo `.mock` del applicationId **y del versionName**. Hace falta
  porque la clave de Maps está restringida a `com.rndeveloper.paparcar` + SHA-1 y `.mock` nunca se
  añadió a esa lista: **desde el 03-09 el mapa del Dev Catalog sale NEGRO**. Es un apaño; el
  arreglo de verdad es añadir el paquete `.mock` a la clave en la consola de GCP.
- Idioma por captura vía `adb shell cmd locale set-app-locales <pkg> --locales <L>` (per-app locale,
  Android 13+). ⛔ `setprop persist.sys.locale` **no** vale: la imagen de sistema Play Store no
  deja rootear.
- Tema oscuro (`cmd uimode night yes`) + barra de estado en modo demo de SystemUI.
- Script de captura y de render: ver el ticket `docs/backlog/play-shots-every-language-shows-its-own-ui-001.md`.

⚠️ **Los taps dentro de la hoja se resuelven por `uiautomator dump`, no por coordenada fija.** La
hoja de Home no cae siempre en el mismo detente (medido: la misma gesto la dejó en 0, 642, 888 y
sin abrir), así que cualquier `y` calibrado a ojo es una moneda al aire. Se busca el nodo por
TEXTO: las pestañas por su **badge numérico** (`6`/`4`/`5`, cifras árabes iguales en los 9 idiomas)
y la fila del parking por **«Parking Plaza Mayor»**, que es nombre propio y no se traduce.

⛔ **Revisar las 72 con `tools/framescan.ps1` ANTES de subir. Verificar una muestra no vale.**
Se hizo así la primera vez —las 8 de `en` y sólo shot4/shot5 del resto— y se colaron dos:
`de/de-play-shot-2` con el diálogo **«Paparcar isn't responding»** del sistema encima, y
`fr/fr-play-shot-1` con el **mapa sin cargar** (negro). Ninguno de los dos lo avisa nada: el fichero
pesa lo normal y la captura *parece* una captura. El script los caza por color — el diálogo del
sistema es gris **neutro** (`B−R ≤ 10`) mientras el mapa (28,42,64) y la hoja (19,26,36) son
azulados, y el mapa sin tiles es casi negro.

⚠️ **El emulador se cayó dos veces a media tanda** (WHPX `hr=80070005` tras muchos relanzamientos
seguidos). Las capturas de esa ventana salen en **0 bytes**, no con contenido malo: verificar
siempre tamaño antes de dar una tanda por buena.

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
