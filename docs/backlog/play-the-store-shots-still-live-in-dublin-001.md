# PLAY-THE-STORE-SHOTS-STILL-LIVE-IN-DUBLIN-001 · las 72 capturas de la ficha enseñan otra ciudad y la marca en mayúscula

**Estado:** 🔵 Abierto · sin rama — necesita una **sesión de emulador**, no una edición

## Problema

Las 72 capturas que se suben a Play (9 idiomas × 8 frames, `1080×1920`) tienen **dos defectos
independientes**, y los dos se ven abriendo `assets/screenshots/en/en-play-shot-1.png`:

1. **La marca en mayúscula.** El titular dice *«Where did I park? **Paparcar** remembers»*. El molde
   (`screenshot-frame.html`) ya está corregido por
   [BRAND-THE-LOWERCASE-GUARD-MISSED-THE-STORE-001], pero los PNG son anteriores.
2. **⛔ El móvil de dentro enseña DUBLÍN.** En el frame 1 se leen *O'Connell Street Upper*,
   *Parnell Square W*, *Moore Ln*, *Henry Pl*, *O'Rahilly Parade*, y el peek dice
   *«Camden Street, 21»*. El mundo mock se mudó a **Madrid** en
   [MOCK-THE-DEMO-WORLD-MOVES-TO-SPAIN-001] (`ad1a898bc`), y el mercado al que apunta el plan de
   marketing es **Móstoles**. La ficha de una app española enseña calles irlandesas.

El segundo es **peor que el primero**, y eso decide la forma de la tarea.

## Por qué no vale el arreglo barato

La tentación es re-renderizar los frames desde los raws que ya hay —son minutos de Chrome
headless— y quedarse con el titular arreglado. **No vale, y conviene que quede escrito por qué:**

- `tools/render.sh` espera `<rawroot>/<locale>/<name>.png`, o sea **raws por idioma**. Lo que hay en
  el repo es `assets/screenshots/raw/`, **plano y con 11 ficheros capturados una sola vez en
  inglés** — medido: no existe ninguna subcarpeta de idioma, ni ahí ni fuera del repo. El propio
  script lo dice en su cabecera: *«That single path is also why the 01-09 set could only ever show
  the phone in English»*.
- Así que un re-render daría 72 frames con el titular en minúscula **y Dublín dentro, en inglés, en
  los nueve idiomas**. Cambia el defecto menor y deja el mayor.

**Los dos se arreglan con la misma sesión.** Hacer sólo la mitad barata gasta el trabajo de subir 72
ficheros a Console para seguir enseñando otra ciudad.

## Lo que sí está corregido y NO entra aquí

- **Los 9 gráficos de funciones** (`<loc>-feature-graphic-1024x500.png`): re-renderizados el 20-10,
  con el imagotipo, sin disco y en minúscula. Ésos están listos para subir.
- **El molde** `screenshot-frame.html`: ya dice `paparcar` en sus 18 cadenas.

## Diseño — lo que hay que hacer

Una **sesión de capturas nueva**, con raws **por idioma** esta vez. El procedimiento completo está
en `docs/release/play-listing/README.md`; lo que no puede perderse:

- **Pixel 8 Pro (emulador) + flavor `mock`**, tema oscuro, mapa en tipo **NORMAL** (el estilo de
  marca oscuro no rinde en TERRAIN, que es el tipo por defecto). Barra de estado en modo demo.
- ⛔ **Parche temporal en `DevRoot`** para ocultar los chips `DEV` / `☀🌙`: envolver el cluster en
  `if (false)`. **`alpha(0f)` NO vale — el glifo emoji se salta la capa de alpha.** El parche se
  revierte al terminar.
- **Raws por idioma**: `adb shell "setprop persist.sys.locale <lang> ; am force-stop …"` y repetir
  la pasada. La app embarca los 9 locales, así que es tiempo de sesión, ×9.
- Luego `tools/render.sh <rawroot>` compone los 72.

⚠️ **Y antes de capturar, mirar el mundo mock**: se mudó a Madrid, así que los escenarios, las
calles del historial y las direcciones del peek son otros. Las comprobaciones que ya costaron una
revisión —pines que caían en el agua, marcas de terceros en pantalla, direcciones coherentes con su
coordenada— hay que volver a pasarlas sobre el mundo nuevo.

## Criterio de éxito

- 72 PNG `1080×1920`, titular en minúscula, y el móvil enseñando **Madrid** en los 9 idiomas.
- Ni una marca de terceros ni un pin fuera de calzada.
- Subidas a Console junto a los 9 gráficos de funciones que ya están listos.

## Bloquea

Nada en el código. Bloquea la **próxima actualización de la ficha**: hoy la tienda enseña la marca
con mayúscula, que es justo lo que [BRAND-THE-NAME-IS-WRITTEN-IN-LOWERCASE-001] cerró en todo lo
demás.
