# WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001 · paparcar.com, construida con lo que ya existe

**Estado:** 🟢 Hecho y **desplegado** · **2026-09-17**, desde master `469e22b02`
**En vivo:** <https://paparcar.com> (dominio propio ya conectado a Hosting de `pap-26`)
**Alcance:** web oficial completa en `web/` → `hosting/public`, ES + EN, más el rediseño del
cascarón de las dos páginas legales que ya vivían ahí.

## Qué había antes

`hosting/public` tenía tres ficheros: `privacy-policy.html` (35 KB, bilingüe, el documento que abre
la app desde Ajustes), `delete-account.html` y un `index.html` de **267 bytes** cuyo cuerpo entero
era un `<meta http-equiv="refresh">` a la política. Es decir: el dominio de la ficha de Play no
tenía web, tenía un redirector.

## La decisión que ordena todo lo demás

> **La web no puede prometer nada que la app no haga.** Cada afirmación sale del repositorio, no de
> un brief de marketing.

De ahí salen las cuatro reglas que se aplicaron una por una:

1. **Las capturas son las de la app, y cada idioma enseña la suya.** Los ocho *raws* de la sesión de
   [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001] entran recortados (fuera la barra de estado) y
   convertidos a WebP en dos anchos. La página española enseña el móvil en español — que es
   exactamente el fallo que aquel ticket existió para arreglar.
2. **Las ilustraciones son las de la app.** `tools/prepare-assets.py` convierte los
   `illus_*_dark.xml` de `composeResources` a SVG plano. No hay arte «parecido al de la app»: es el
   de la app.
3. **La letra es la de la app.** `plus_jakarta_sans_variable.ttf` subsetado a woff2 (172 KB → 36 KB)
   y **auto-alojado**. Traerla de un CDN de fuentes añadiría un destinatario de la IP de cada
   visitante, y §4 de la política publicada los nombra uno a uno: una web que añade uno en silencio
   deja esa tabla mintiendo.
4. **Las cifras y los límites son los del código.** El free/Plus de la sección de precios sale de
   `PaywallReason` y de los gates (`FREE_HISTORY_WINDOW_DAYS = 7`, `FREE_OWN_VEHICLE_LIMIT = 1`,
   `FREE_ZONE_LIMIT = 2`), el mínimo de Android de `minSdk = 26`, el enlace de Play del
   `applicationId`, el correo del string que la propia app enseña. Nada inventado.

## Lo que la web dice y lo que NO dice

- **No dice** que haya comunidad grande ni plazas garantizadas: dice que las plazas «dependen de que
  haya conductores usando Paparcar cerca de ti», y que el mapa vacío significa que ahora mismo no
  hay ninguna publicada, no que la app falle.
- **No dice** «sin anuncios» como ventaja de Plus. La ficha de Play v1.3.0 promete *sin anuncios* a
  secas, así que la web lo cuenta como propiedad del producto, no como algo que se vende. (La tabla
  de `plus-alerts-and-tiers-001.md` tiene una fila «anuncios: con / sin» que hoy **no** es cierta en
  producto — anotado ahí abajo.)
- **No dice** precio de Plus: se activa dentro de la app y lo fija la tienda.
- **No dice** en qué países hay precios de carburante. Hay fuentes de ES, FR, IT y DE en
  `data/fuel/`, pero eso es una lista que cambia; la web dice «la cobertura depende del país».
- **No hay foto de stock ni imagen generada.** Todo lo visual es producto real o vector propio.

## El vocabulario, que aquí es especialmente fácil de romper

[COPY-SPOT-IS-NOT-A-PARKING-001] en una web de aparcamiento se rompe en el ICONO antes que en el
texto. La primera versión pintaba «Plazas compartidas» con una **P** — la misma P que el parking
público — y en el mapa dibujado pasaba igual. Corregido: la plaza es un **pin hueco** (el hueco que
alguien acaba de dejar) con el punto de quien se fue; la **P** se queda para el parking, que es la
instalación de un tercero y lleva la señal de tráfico. El texto ya distinguía plaza / aparcamiento /
parking en los dos idiomas.

## Arquitectura

```
web/
  build.mjs            generador estático, CERO dependencias (Node 24, sin node_modules)
  content/{es,en}.json TODO el copy; añadir un idioma es un JSON + una línea en LOCALES
  lib/                 config (los hechos verificables) · icons · components · layout
  pages/               home · support · terms
  assets/              css · js · font · img (generados, versionados)
  tools/               prepare-assets · prepare-font · restyle-legal · serve · audit · shots
```

Salida a `hosting/public`: `index.html`, `en/index.html`, `{,en/}support.html`, `{,en/}terms.html`,
`404.html`, `sitemap.xml`, `robots.txt`, `assets/**`.

⛔ **`privacy-policy.html` y `delete-account.html` NO son salida del build.** Siguen siendo
documentos escritos a mano, en su sitio de siempre, porque la app enlaza el primero
(`SettingsViewModel.PRIVACY_POLICY_URL`) y `PrivacyPolicyParityGuardrailTest` compara el esqueleto
de sus dos mitades. `tools/restyle-legal.mjs` les cambia **solo el cascarón** (head, cabecera, h1,
pie) entre marcadores `pap:shell`, y **verifica que los dos `<section>` salen byte a byte iguales
antes de escribir**; si no, se niega. El primer intento envolvía la tabla de destinatarios en un
`<div>` para que hiciera scroll en el móvil — dentro de `<section id="en">` — y el propio script lo
paró. Eso se arregló en CSS, que es donde tocaba. Guards verificados con `--rerun-tasks`: pasan.

## Por qué no Next/Astro

Prioridad declarada: rendimiento, SEO, accesibilidad, mantenibilidad y facilidad de despliegue. El
sitio son seis páginas de copy casi estático sobre un *hosting* que ya existía en `firebase.json`.
Un build de un fichero sin `node_modules` no manda framework al cliente, no se pudre en un bump
transitivo y despliega con el `firebase deploy` de siempre. Los componentes son funciones en `lib/`
y `pages/`; no hay ningún fichero gigante.

## Herramientas que quedan, y por qué existen

- `tools/serve.py` — preview con el `cleanUrls: true` de Hosting. Sin eso, `/terms` no resuelve en
  local y la clase de bug que tienen los enlaces del pie no se ve.
- `tools/audit.mjs` — conduce Chrome por CDP y **pregunta al documento vivo** en 9 anchos ×
  6 páginas: desbordamiento horizontal con el culpable nombrado, dianas táctiles pequeñas, orden de
  encabezados, `h1` único, `alt` que falten, longitud de `title` y `description`.
- `tools/shots.mjs` — capturas de página completa con emulación de dispositivo REAL.
  ⛔⛔ **Medido hoy:** `chrome --headless --screenshot --window-size=390,7000` **no** renderiza a
  390 px en Windows — la ventana tiene un ancho mínimo (~440) y la captura se recorta. Eso se lee
  como «el texto sale cortado» y manda a buscar un desbordamiento que no existe. Se perdieron
  varias vueltas ahí antes de medirlo con `Emulation.setDeviceMetricsOverride`, que es lo único
  que enseña lo que ve un móvil.

## Cabeceras de Hosting — la que no se ve venir

⛔⛔ **Con `cleanUrls: true`, una regla `**/*.html` no cubre ninguna página.** Las reglas de
`headers` se casan contra la RUTA PEDIDA, y el navegador pide `/terms`, no `/terms.html`. Medido en
producción: con solo esa regla, las páginas salían con el `max-age=3600` por defecto de Hosting —
es decir, un redespliegue tardaba hasta una hora en verse. Hacen falta `**/!(*.*)` y `@(/|/en/|/en)`
para las rutas sin extensión y las raíces. Verificado leyendo las cabeceras en vivo, no el
«Deploy complete!».

## 2.ª vuelta — lo que se vio con la web ya en vivo

Tres cosas, y las tres son de la misma familia: *un sistema no está aplicado hasta que se mira el
resultado*.

1. **El logo era el de fondo claro sobre fondo oscuro.** `logoMark()` servía por defecto
   `paparcar_logo.xml` — disco Ink + glifo neón — sobre una página que ES Ink: el disco desaparecía
   en el fondo y quedaba el glifo flotando. Sobre Ink toca el otro, `paparcar_logo_dark.xml` (disco
   neón + glifo Ink, trazo **7** por la compensación óptica). Lo curioso es que la nota ya estaba
   escrita **en el repo**, en el comentario de la cabecera de la política publicada: *«the header
   band is always Ink, so the disc goes neon-green with the Ink glyph — the Ink disc would blend»*.
   El parámetro ahora se llama `surface: 'ink' | 'light'`, que es la pregunta real, y `'ink'` es el
   defecto porque la web entera lo es.
2. **El botón de idioma decía un idioma solo, y eso es ambiguo por construcción.** «English» se lee
   igual como *estás leyendo inglés* que como *cambia a inglés*, y no hay forma de saberlo sin
   pulsarlo. Ahora son **las dos etiquetas con la activa escrita** (`ES` · `EN`), que es el mismo
   principio que la doctrina de color aplica al estado: se escribe, no se adivina. La activa no es
   enlace (no va a ninguna parte) y lleva `aria-current`.
3. **Las capturas se veían borrosas y lo eran.** El ancho mayor del `srcset` era 640 px, y el móvil
   del hero mide ~300 px CSS: en una pantalla 3× el navegador estiraba un 640 hasta ~900. Ahora el
   `srcset` llega al **ancho nativo (1080)**, que no se puede escalar hacia arriba, y los dos anchos
   pequeños llevan un unsharp suave porque reducir una captura de UI densa ablanda las líneas de
   1 px. Verificado a 2× antes de darlo por bueno.

## El sistema que faltaba: que esto no se repita solo

El encargo de la 2.ª vuelta fue explícito — *que cuando hagamos cambios también se reflejen en web,
política, docs y README*. La web es un **consumidor** de la verdad del producto y nadie la compila,
así que la deriva es silenciosa por defecto. Lo montado:

- **`WebSiteParityGuardrailTest`** (`:shared`, `androidUnitTest`), cinco comprobaciones:
  hechos de `config.mjs` contra su fuente · paridad de claves ES/EN · plan free contra los gates de
  `domain/entitlement/` · que el copy use los tokens · que la salida construida venga del copy que
  hay en el repo. **Las cinco se han visto en rojo** antes de darlas por buenas.
  ⛔ No toca bytecode ⇒ sale UP-TO-DATE ⇒ `--rerun-tasks`.
- **Los números del plan salen del copy.** El copy escribe `{{historyDays}}`, `{{freeVehicles}}`,
  `{{freeZones}}`, `{{minAndroid}}` y el build sustituye desde `FREE_LIMITS`. Un número a mano
  estaría en la tabla de planes y otra vez en la FAQ, por idioma: cuatro sitios que olvidar y
  ninguno se pondría rojo. Un token desconocido **rompe el build**.
- **La tabla de «si tocas X, toca Y»** en `web/README.md`, el resumen en `CLAUDE.md`, la entrada en
  el README raíz y un punto en la skill `nuevo-ticket` §B, que es la que se invoca en cada tarea.

⚠️ **Y lo primero que hizo el guard fue encontrar algo**: buscaba `minSdk` en `app/build.gradle.kts`
y ahí no está — ese fichero lee `libs.versions.android.minSdk`, el número vive en el catálogo.

## 3.ª vuelta — el bug que se vio en el propio arreglo anterior

Al desplegar el selector de idioma nuevo, el user lo vio **sin estilo y sin poder cambiar**: «los
textos se leen pegados». En un navegador limpio funcionaba —clic verificado por CDP, navega a
`/en/`—, así que no era la web: era **su caché**, y la causa era mía.

⛔⛔ **`Cache-Control: max-age=900` sobre `site.css` con nombre fijo.** El HTML va
`must-revalidate`, así que un despliegue llega al instante; la hoja de estilos no. Resultado: HTML
NUEVO pintado con CSS VIEJO durante hasta quince minutos. Y eso no es «algo desactualizado», es una
página rota — las clases que aún no existían no tienen reglas, así que el control de idioma perdía
su forma y quedaban dos palabras sueltas. El cambio `.lang-switch` → `.langs` es justo el tipo de
cambio que lo dispara.

Acortar la ventana no arregla nada; lo que hay que quitar es la posibilidad:

- **Hash de contenido en todos los assets.** `build.mjs` publica `site.<hash>.css`, reescribe las
  referencias dentro del CSS (la fuente) y deja `assets/manifest.json`. Todo pasa por `asset()`
  (`lib/assets.mjs`), que **rompe el build** si la ruta no está en el manifiesto.
- Un fichero que cambia es **otra URL** ⇒ la copia en caché nunca puede ser la equivocada ⇒
  `immutable` un año en `firebase.json`. Verificado en vivo, y `/assets/css/site.css` (sin hash)
  ahora devuelve **404**, que es la prueba de que el emparejamiento viejo ya no existe.
- **`restyle-legal` pasa a ser un PASO del build.** Las dos páginas legales enlazan la misma hoja
  hasheada; dejarlo como comando suelto era desplegarlas apuntando a un fichero que ya no está.

## Hallazgos que NO son de la web

1. **`plus-alerts-and-tiers-001.md` promete anuncios en free** (fila «Coche compartido (F8) · rutas
   · anuncios | ❌ · limitadas · con | ✅ · ilimitadas · sin»). La ficha de Play de v1.3.0, en los
   nueve idiomas, dice **«Sin anuncios»**. Una de las dos está mal y la de Play es la que lee el
   usuario. Decidir cuál y arreglar la otra.
2. **Las dos páginas legales no tenían `h1` ni `meta description`** — no es cosmético: son las dos
   URLs que Play enlaza desde la ficha. Arreglado aquí.

## Pendiente del usuario

- Revisar el copy de las dos lenguas (está escrito, no traducido por máquina, pero es copy de marca).
- `/terms` sale con un aviso VISIBLE de «borrador pendiente de revisión legal», porque el repo no
  tenía términos y no se inventan como si fueran definitivos. Sustituir por el texto revisado.
- Los otros siete idiomas de la app (it, pt, fr, de, nl, pl, ro) **no** están en la web: la
  arquitectura los admite (un JSON + una línea en `LOCALES`), pero traducir 500 líneas de copy de
  marca a máquina es justo lo que [I18N] nos ha costado caro dentro de la app.
