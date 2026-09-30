# paparcar.com

La web oficial. Se genera con `node web/build.mjs` y sale a `hosting/public`, el mismo target de
Firebase Hosting que ya estaba declarado en `firebase.json`.

**Ficha del trabajo:** [`docs/backlog/web-the-site-says-only-what-the-app-does-001.md`](../docs/backlog/web-the-site-says-only-what-the-app-does-001.md)

## La regla

> La web no dice nada que la app no haga. Cada afirmación sale del repositorio.

Los hechos verificables viven en un solo sitio, [`lib/config.mjs`](lib/config.mjs), con la fuente de
cada uno anotada al lado: el enlace de Play sale del `applicationId`, el correo del string que la
propia app enseña, el mínimo de Android del `minSdk`, los límites del plan free de los gates de
`domain/entitlement/`. Si algo no se puede comprobar en el repo, el copy dice menos.

## Cuando cambia el producto — qué hay que tocar aquí

La web es un **consumidor** de la verdad del producto, y los consumidores derivan en silencio: nadie
compila esta carpeta, así que un límite que cambia en Kotlin deja la página prometiendo el anterior
hasta que alguien se da cuenta. Esta tabla es lo que hay que recorrer en la MISMA tarea que el
cambio, no después.

| Cambias… | Tocas… | Lo vigila |
|---|---|---|
| un límite del plan free (`FREE_HISTORY_WINDOW_DAYS`, `FREE_OWN_VEHICLE_LIMIT`, `FREE_ZONE_LIMIT`) | `FREE_LIMITS` en `lib/config.mjs`, **solo ahí** | ✅ test |
| `applicationId`, `versionName`, `minSdk` | los hechos de `lib/config.mjs` | ✅ test |
| `SettingsViewModel.PRIVACY_POLICY_URL` o el correo de soporte | `ORIGIN` + `PRIVACY_PATH` + `SUPPORT_EMAIL` | ✅ test |
| una feature que la web nombra | `content/es.json` **y** `content/en.json` | ⚠️ solo la paridad de claves |
| una sección nueva de la home | `pages/home.mjs` + su copy en los dos idiomas + `SECTIONS` en `lib/config.mjs` | ⚠️ solo enlaces y anclas |
| la política de privacidad o el borrado de cuenta | `hosting/public/*.html` **a mano** (⛔ no son salida del build) | ✅ otro test |
| el logo | ⛔ **no se toca aquí**: `docs/branding/master/export_surfaces.py` → `lib/mark.generated.mjs`, y luego `node web/build.mjs` | ✅ `WebSiteParityGuardrailTest` |
| un color, un rol de tipografía | tokens de `assets/css/site.css` | ❌ a ojo |
| la UI que sale en las capturas | `tools/prepare-assets.py` con los raws de la sesión de Play | ❌ a ojo |
| un idioma nuevo | `content/<code>.json` + una línea en `LOCALES` | ⚠️ paridad de claves |

**Y siempre, al final:** `node web/build.mjs --check` y desplegar. Editar el copy y no reconstruir
es desplegar lo anterior — hay un test que lo caza, pero solo si alguien lo ejecuta.

### ⛔ Los números del plan no se escriben en el copy

El copy lleva `{{historyDays}}`, `{{freeVehicles}}`, `{{freeZones}}` y `{{minAndroid}}`; el build los
sustituye desde `lib/config.mjs`. Un número escrito a mano estaría en la tabla de planes y otra vez
en la FAQ, por idioma — cuatro sitios que olvidar, y ninguno se pondría rojo. Un token desconocido
(`{{historydays}}`) **rompe el build** en vez de llegar a producción con las llaves puestas.

### El testigo

`WebSiteParityGuardrailTest` (en `:shared`, `androidUnitTest`) hace cinco comprobaciones y **las
cinco se han visto fallar** antes de darlas por buenas:

1. cada hecho de `config.mjs` contra su fuente en el repo,
2. las dos locales con el mismo esqueleto de claves,
3. el plan free de la web contra los gates de `domain/entitlement/`,
4. que el copy use los tokens y no numerales,
5. que las páginas construidas salgan del copy que hay en el repo.

```bash
# ⛔ No toca bytecode → Gradle lo da por UP-TO-DATE y no lo ejecuta.
./gradlew :shared:testDebugUnitTest --tests '*WebSiteParity*' --rerun-tasks
```

Lo que **no** comprueba: que el copy sea bueno, que una feature nueva tenga su sección, o que una
traducción sea fiel. Para eso está la tabla de arriba y una persona.

## Comandos

```bash
node web/build.mjs                 # genera hosting/public
node web/build.mjs --check         # + enlaces rotos, anclas sin destino, páginas a mano presentes
python web/tools/serve.py          # preview en :5173 con el cleanUrls de Hosting
node web/tools/audit.mjs           # 9 anchos × 6 páginas: overflow, dianas, encabezados, SEO
node web/tools/shots.mjs <dir>     # capturas de página completa con emulación de dispositivo real
firebase deploy --only hosting     # publicar
```

Assets (se regeneran solo cuando cambian las capturas o el arte de la app):

```bash
python web/tools/prepare-assets.py <raws-es> <raws-en>   # capturas → WebP, illus_* → SVG, OG card
python web/tools/prepare-assets.py                       # sin capturas: illus_*, OG card y touch icon
python web/tools/prepare-font.py                          # Plus Jakarta Sans → woff2 subsetado
node web/tools/restyle-legal.mjs                          # (ya lo hace el build; suelto solo para depurar)
```

## Estructura

```
build.mjs              generador (cero dependencias, Node 24)
content/{es,en}.json   TODO el copy
lib/config.mjs         los hechos verificables + rutas + locales
lib/icons.mjs          iconos nivel 1/2 en la rejilla de 24; el EMBLEMA (marca sobre disco) lo
                       compone desde lib/mark.generated.mjs — aquí no se dibuja nada de marca
lib/mark.generated.mjs GENERADO por docs/branding/master/export_surfaces.py. No editar.
lib/components.mjs     eyebrow, botón, tarjeta, móvil, tier, FAQ…
lib/layout.mjs         head con SEO/hreflang/JSON-LD, cabecera, pie
pages/                 home · support · terms
assets/                css · js · font · img (generados, versionados)
tools/                 preparación de assets y verificación
```

## Añadir un idioma

1. `cp content/es.json content/<code>.json` y traducirlo.
2. Añadir `{ code: '<code>', base: '/<code>' }` a `LOCALES` en `lib/config.mjs`.

El `hreflang`, el sitemap, el selector de idioma y las rutas salen de ahí.

## ⛔ Lo que este build NO toca

`hosting/public/privacy-policy.html` y `hosting/public/delete-account.html` son **documentos
escritos a mano**, no salida del generador:

- la app abre el primero desde Ajustes (`SettingsViewModel.PRIVACY_POLICY_URL`),
- el formulario de Data Safety de Play tiene que coincidir con él,
- y `PrivacyPolicyParityGuardrailTest` compara el esqueleto de sus mitades inglesa y española.

`build.mjs` solo comprueba que siguen ahí. `tools/restyle-legal.mjs` les cambia el cascarón entre
marcadores `pap:shell` y **verifica que los dos `<section>` salen byte a byte iguales antes de
escribir**; si cambian, aborta.

### ⛔ Los assets van con hash de contenido, y por eso pueden ser `immutable`

`build.mjs` publica `assets/**` como `site.<hash>.css`, reescribe las referencias dentro del propio
CSS (la fuente) y deja un `assets/manifest.json`. Todo el código pide URLs por `asset()`
([`lib/assets.mjs`](lib/assets.mjs)), que **revienta el build** si pides algo que no está en el
manifiesto — un `href` mal escrito es un 404 que ningún test vería.

Esto no es optimización, es corrección, y salió de un bug real: con nombres fijos la hoja de estilos
tenía que llevar un `max-age` corto, y **cualquier ventana** significa que un visitante que vuelve se
lleva HTML NUEVO pintado con CSS VIEJO. No es «algo desactualizado», es una página rota: las clases
que aún no existían no tienen reglas. Pasó al reconstruir el selector de idioma de `.lang-switch` a
`.langs`: durante quince minutos, quien hubiera abierto la web ese día veía dos palabras pegadas
donde iba el control.

Con hash, un fichero que cambia es otra URL: la copia en caché nunca puede ser la equivocada, y por
eso `firebase.json` les pone un año. Por lo mismo, **`restyle-legal` es un PASO del build**, no un
comando aparte: las dos páginas legales enlazan esa misma hoja hasheada.

## Dos cosas medidas que cuestan tiempo si no se saben

- **`chrome --headless --screenshot --window-size=390,…` no renderiza a 390 px en Windows.** La
  ventana tiene un ancho mínimo (~440) y la captura se recorta: se lee como «el texto sale cortado»
  y manda a buscar un desbordamiento que no existe. Solo `Emulation.setDeviceMetricsOverride`
  (lo que hace `shots.mjs`) enseña lo que ve un móvil.
- **`serve.py` usa `ThreadingHTTPServer` a propósito.** Con el `HTTPServer` normal, una conexión
  keep-alive de Chrome headless bloquea el servidor entero y todo lo demás se queda colgado, con
  pinta de que la herramienta de capturas está rota.
