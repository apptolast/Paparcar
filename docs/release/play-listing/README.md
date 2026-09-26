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
