# BRAND-THE-NAME-IS-WRITTEN-IN-LOWERCASE-001 · la marca se escribe en minúscula, también fuera del logo

**Estado:** ✅ Done

⚠️ **Apilada sobre `BRAND-THE-MARK-GAINS-ITS-WORDMARK-001`**, no sobre master. No es preferencia: las
dos regeneran `hosting/public`, así que en paralelo chocarían en ~10 HTML generados. Esta entra
**después**.

## Problema

El imagotipo que el user entrega el 19-10 escribe **`paparcar`**, en minúscula. El resto del
producto escribe **«Paparcar»**: 39 strings × 9 locales, 6 más × 9 en el espejo de notificaciones de
`app/src/main/res`, 124 en el copy de la web, las dos páginas legales, la ficha de Play y el
`android:label` que se lee bajo el icono.

El user decide (19-10) que la minúscula **no es sólo del logo**: es de la marca. Así que el dibujo y
el texto tienen que decir lo mismo — si no, la app enseña un logo en minúscula y a dos centímetros
una frase que lo escribe de otra forma.

## Doctrina violada

Ninguna existente: es una decisión de identidad, no un invariante roto. Lo que sí crea es un
invariante **nuevo** que antes no se podía ni formular, porque no había una forma correcta de
escribir el nombre. A partir de aquí: *el nombre de la marca se escribe `paparcar`, en todo texto
que lea un usuario.*

Y esa es justo la clase de regla que se degrada sola: no rompe nada, no falla al compilar, y la
siguiente persona que escriba un string nuevo pondrá «Paparcar» porque es lo que parece correcto.
Por eso lleva guard.

## Decisiones de redacción

- **`Paparcar Plus` → `paparcar Plus`.** El nombre propio baja, el calificador del plan NO: es un
  nombre de producto, no parte de la marca. Mismo patrón que *adidas Originals*.
- **Minúscula también a principio de frase.** «paparcar recuerda dónde aparcaste» se lee raro la
  primera vez y es lo que hacen las marcas que eligen minúscula; alternarla según la posición
  destruye la decisión, porque la mayoría de las apariciones ABREN frase.
- **`Settings → Paparcar → Location`** (las guías de permisos de iOS y Android) también baja: ahí el
  nombre que sale es el del `android:label` / `CFBundleDisplayName`, que esta tarea cambia. Si no
  bajara, el copy mandaría a buscar una entrada que el sistema ya no muestra así.
- **Lo que NO baja**: identificadores de código, `applicationId`, nombres de clase y paquete
  (`PaparcarLogger`, `com.rndeveloper.paparcar`), los comentarios internos, `docs/` y `CLAUDE.md`.
  La marca es lo que lee un usuario; el código no es texto de marca.
- **La ficha de Play: sólo la VIVA.** El histórico no se toca — es el acta de lo que se publicó en
  su día, y reescribirla para que cuadre con el presente es falsificarla.
  ⚠️ **Y cuál es cuál no se adivina por el nombre**: lo dice `published-listing.txt`, que nombra
  `LISTING-v1.4.0.md` como la ficha del build publicado. `LISTING.md` y `LISTING-i18n.md`, que
  *parecen* las actuales por llamarse así, son las **retiradas**. El primer barrido se las tragó al
  revés —tocó las históricas y dejó la viva— y lo cazó `PlayListingPasteParityGuardrailTest` en
  segundos, que es justo para lo que existe: el nombre de la ficha vive en UN sitio desde
  [PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001]. Los `paste/*.txt` se regeneran con
  `python tools/make_paste.py`, no se editan.

## Señales / datos disponibles

Conteo previo al barrido:

| Dónde | Ocurrencias |
|---|---|
| `composeResources/values*/strings.xml` | 39 × 9 locales (38 en `pt`) |
| `app/src/main/res/values*/strings.xml` (espejo de notificaciones) | 6 × 9 |
| `web/content/{es,en}.json` | 62 + 62 |
| `hosting/public/*.html` | 170 (la mayoría **salida del build**) |
| `docs/release/play-listing/paste/*.txt` + `LISTING.md` | ~140 |
| `app/src/main/AndroidManifest.xml` | 1 (`android:label`) |

## Diseño

Un barrido por **valor**, no por fichero: en los XML se sustituye sólo dentro del contenido de
`<string>`, para que los comentarios internos no entren. En JSON, HTML y TXT la sustitución es
directa. Después se regenera lo generado (`node web/build.mjs`) en vez de editarlo.

### El guard

`BrandNameLowercaseGuardrailTest`: ningún texto visible por el usuario escribe `Paparcar` con
mayúscula. Lee los 9 locales de `composeResources`, los 9 del espejo de `:app`, el `android:label` y
el copy de la web. Existe porque es una regla de las que no fallan solas — y porque ya hay
precedente exacto: la regla del apóstrofo se barrió a cero, se escribió en CLAUDE.md, y volvieron a
entrar seis por el espejo de notificaciones [COPY-AN-APOSTROPHE-GUARD-THAT-NEVER-EXISTED-001].

## Criterio de éxito

- Cero `Paparcar` en texto visible; el guard lo prueba y se falsifica.
- `./gradlew testAll` verde, `node web/build.mjs --check` limpio.
- La ficha de Play y el `android:label` listos; subir el título a Console es del user.

## Consumidores auditados

**808 ocurrencias** en 38 ficheros, en dos pasadas:

| Consumidor | Veredicto |
|---|---|
| `composeResources/values*/strings.xml` ×9 | **cerrado** — 38 por locale (37 en `pt`), sólo VALORES |
| `app/src/main/res/values*/strings.xml` ×9 | **cerrado** — 6 por locale (espejo de notificaciones) |
| `app/src/main/AndroidManifest.xml` | **cerrado** — `android:label`, el nombre bajo el icono |
| `web/content/{es,en}.json` | **cerrado** — 62 + 62 |
| `web/pages/{notfound,home,support}.mjs` · `web/build.mjs` · `web/lib/layout.mjs` | **cerrado** — 2ª pasada: el 404, el JSON-LD, el asunto de soporte, el webmanifest y el `og:site_name` **no salen del copy**, viven en su módulo |
| `web/tools/restyle-legal.mjs` | **cerrado** — 7; es quien escribe el *chrome* de las legales, así que editar el HTML sin esto lo revertía el siguiente build |
| `hosting/public/{privacy-policy,delete-account}.html` | **cerrado** — escritos a mano, 15 + 19 |
| `hosting/public/**` (7 páginas + 404) | **regenerado**, no editado |
| `docs/release/play-listing/LISTING-v1.4.0.md` + `paste/*.txt` | **cerrado** — la ficha VIVA; `paste/` regenerado con `make_paste.py` |
| `LISTING.md` · `LISTING-i18n.md` · `LISTING-v1.3.0.md` | **exentos** — fichas RETIRADAS, son el acta |
| `web/tools/prepare-assets.py` | **cerrado** — el titular de la tarjeta social |
| `BrandSurfaceGuardrailTest` | **corregido** — su cebo era la palabra `"Paparcar"`; un guard que se cae cuando cambia la ortografía de lo que vigila no vigila nada, así que pasa a la FIRMA de la línea (`font=_font(46`) |
| Identificadores, paquetes, `applicationId`, `PaparcarLogger` | **exentos** — código, no copy |
| Comentarios (`<!-- Paparcar Plus -->`, cabeceras de drawables generados, `docs/`, CLAUDE.md) | **exentos** — hablan del proyecto, no los lee un usuario |

## Verificación

- **`./gradlew testAll` + ambos flavors: 3916 tests, 0 fallos.**
- **El guard nuevo, FALSIFICADO**: los tres chequeos en rojo a la vez (devolver la mayúscula a
  `brand_name`, al `android:label`, al copy EN y a una página publicada) y restaurados.
- **`PlayListingPasteParityGuardrailTest` cazó un error real del barrido** y conviene dejarlo
  escrito: toqué las fichas históricas y dejé intacta la viva, porque me fié del nombre de los
  ficheros en vez de leer `published-listing.txt`. Ese guard existe precisamente porque el nombre de
  la ficha publicada tiene que vivir en UN sitio.
- **Leído en 4 idiomas** antes de dar por bueno un `sed` sobre prosa: *«Welcome to paparcar»*,
  *«Bienvenido a paparcar»*, *«Willkommen bei paparcar»*, *«Bienvenue sur paparcar»*; y
  **`paparcar Plus`** en los nueve.
- **Visto en device** (emulador, preset Onboarding del Dev Catalog): título y subtítulo en
  minúscula, sin cortes ni saltos de línea raros por el cambio de ancho.

## Lo que queda, y es del user

- **El título en Play Console**: la ficha de este repo ya dice `paparcar`, pero el nombre de la app
  en la tienda se cambia en Console, no aquí.
- **Desplegar la web** (`firebase deploy --only hosting`).
- ⚠️ **El `android:label` cambia el nombre bajo el icono** en el próximo release. No rompe nada
  —no es el `applicationId`— pero es visible el día que salga.
