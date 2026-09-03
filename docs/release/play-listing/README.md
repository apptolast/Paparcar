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

### ✅ Capturas de teléfono — hechas el 01-09 (en-US), localizadas el 02-09 (9 idiomas)
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
