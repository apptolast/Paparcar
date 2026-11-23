# MKT-A-SITE-CTA-MUST-SAY-WHERE-ITS-INSTALL-CAME-FROM-001 · los botones de `paparcar.com` mandan a Play sin decir de dónde venían

**Estado:** ✅ Done (23-11-2026) · rama y worktree borrados al cerrar
· ⏳ **queda el dato, que es lo único que prueba esto**: 24-48 h tras el despliegue, Play Console
→ Adquisición de usuarios tiene que ensenar instalaciones con `utm_source=web`. Hasta ese número,
el enlace está bien formado y nada más — el mismo ⏳ que arrastran
`mkt-an-install-must-say-where-it-came-from-001` y `mkt-add-facebook-attribution-route-001`.

## Problema

Salió preguntando lo más básico que se puede preguntar de una campaña: **de dónde se instaló la app
cada uno de los 4 usuarios nuevos del 22–23 de noviembre** (Manuel J. Roldán 22-11 22:16, Muhammad
Bajwa 23-11 01:55, Jessica Viedma 23-11 12:58, Juan José Trujillo 23-11 13:49, los cuatro por Google
Sign-In). La respuesta medida es **que no se puede saber de ninguno**, y la mitad de la razón es este
ticket.

La otra mitad es estructural y no se arregla aquí: la app **no lee el install referrer** (ni
`com.android.installreferrer` en `gradle/libs.versions.toml`, ni una llamada a `getInstallReferrer()`
en `shared/src` ni en `app/src` — verificado con grep). Así que la atribución por usuario no existe
en nuestros datos y nunca ha existido; lo único que puede haber es el **agregado por campaña** de
Play Console y GA4. Ese agregado es exactamente lo que este ticket está tirando a la basura.

**Medido el 23-11 sobre `hosting/public`: 42 enlaces a Play en 11 páginas desplegadas, CERO con
`&referrer=`.** Es decir: toda instalación que venga de la web —de la home, del header de cualquier
página, del pie, de las dos llamadas de `paparcar.com/blackfriday`, del 404— **Play la archiva como
orgánica y GA4 como `(direct)`**. La campaña de Black Friday se desplegó el 22-11 y lleva desde
entonces trayendo tráfico que no se puede separar del que habría llegado solo.

Y ninguna página usa los atajos que SÍ están etiquetados: `grep -rn "/descargar" web/` no devuelve
nada. Los 7 redirects de `firebase.json` existen solo para la bio de Instagram, los QR y Facebook.

## Doctrina violada

[MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001], literalmente su frase: *«el `referrer` es lo ÚNICO
que sobrevive al salto a la tienda […] sin él, Play archiva cada una de estas instalaciones como
orgánica y GA4 como (direct), y nadie puede decir si la cuenta trajo una instalación o trescientas»*.

⛔ Y de paso explica **por qué el guard no lo cazó**, que es la parte que hay que arreglar bien:
`WebSiteParityGuardrailTest.downloadShortcuts()` lee `firebase.json → hosting.redirects` y filtra por
`startsWith("/descargar")`. O sea: **el guard vigila a un productor de enlaces a Play y hay dos.**
Los `/descargar*` están impecables —percent-encoding correcto, `utm_medium` separando ganado de
comprado, un slug por territorio— y el otro productor, los CTA del propio sitio, no lo mira nadie.
Es el mismo molde de fallo que el doc de aquel ticket ya describe para otras cosas: *despliega
limpio, abre la tienda y pierde la atribución sin un error en ninguna parte.*

Transversal de `CLAUDE.md`: **sistemas, no parches** — el invariante «un enlace a Play dice de dónde
viene» vive hoy en UN productor de DOS. Arreglarlo es ponerlo donde no se pueda escribir un enlace
sin él, no añadir `&referrer=` a mano en diez sitios.

## Señales / datos disponibles

Auditoría completa de productores de enlaces a Play (grep `PLAY_URL` sobre `web/`, 23-11):

| Call site | Qué es | Páginas que lo llevan |
|---|---|---|
| `web/lib/layout.mjs:171` | CTA del header | **todas** |
| `web/lib/layout.mjs:185` | CTA del nav móvil | **todas** |
| `web/lib/layout.mjs:228` | badge de Google Play del pie | **todas** |
| `web/lib/components.mjs:57` | componente `storeButton()` | donde se use |
| `web/pages/home.mjs:111` | CTA del hero | home ES/EN |
| `web/pages/home.mjs:235` | CTA de la sección Plus | home ES/EN |
| `web/pages/blackfriday.mjs:34` | CTA del hero de la landing | blackfriday ES/EN |
| `web/pages/blackfriday.mjs:113` | CTA de cierre de la landing | blackfriday ES/EN |
| `web/pages/notfound.mjs:25` | enlace del 404 | 404 |
| `web/tools/restyle-legal.mjs:116` | enlace en las páginas legales | privacidad, borrado de cuenta |
| `web/pages/home.mjs:312` | `installUrl` de los datos estructurados | home ES/EN — **no es un CTA, ver abajo** |

Y lo que ya existe y hay que reutilizar en vez de duplicar:
- `web/lib/config.mjs:16` — `PLAY_URL`, el único sitio donde vive la URL de la tienda.
- `firebase.json → hosting.redirects` — los 7 atajos etiquetados y su vocabulario ya decidido:
  `utm_source` ∈ {`instagram`, `facebook`}, `utm_medium` ∈ {`social`, `paid`}, `utm_campaign` = el
  slug del territorio, los comprados con prefijo `z-`.
- `WebSiteParityGuardrailTest` — 5 chequeos vivos, con el testigo de población vacía ya puesto en
  los dos tests que leen los atajos.

## Diseño

**El invariante: en `web/` no se puede escribir un enlace a la tienda sin decir de dónde sale.** Y
donde se pone eso es en el único sitio que ya tiene la URL.

1. **`web/lib/config.mjs` deja de exportar un string y exporta una función**: `playUrl(campaign)` (o
   `playUrl({ campaign, content })`), que devuelve `PLAY_URL` + el `referrer` **percent-encodeado**.
   `PLAY_URL` se queda, privado al módulo y para el canónico de los datos estructurados. Un CTA que
   se olvide de la campaña no construye la página: la función la exige.
2. **La tabla de campañas vive UNA vez, en `config.mjs`**, y pasa a ser la fuente de los dos
   productores. `firebase.json` no puede importarla —de ahí que hoy haya dos copias de la URL—, así
   que lo que las ata es el guard: pasa a comprobar que **cada redirect `/descargar*` coincide con
   su fila de la tabla**, que hoy es más de lo que mira (hoy valida la FORMA del referrer, no que
   diga lo que la tabla dice).
3. **Desde la web se enlaza DIRECTO a Play con su referrer, no por `/descargar`.** Un salto menos, y
   sobre todo: por el atajo haría falta un redirect nuevo por cada superficie del sitio, que es
   multiplicar los sitios donde se declara una campaña justo cuando estamos reduciéndolos a uno.
4. **Vocabulario nuevo, y es la única decisión de producto que hay aquí.** El sitio no es `social`
   ni `paid`, y `UTM_MEDIUMS` solo tiene esas dos columnas a propósito (*«el libro de cuentas solo
   tiene esas dos columnas»*: ganado vs comprado). Propuesta:
   - `utm_source = web`, `utm_medium = owned` — tercera columna, porque el tráfico propio del sitio
     no es ninguna de las dos y meterlo en `social` mezclaría el sitio con Instagram.
   - `utm_campaign` = **la página**, no el botón: `home`, `blackfriday`, `404`, `legal`. Mantiene el
     censo de campañas acotado, que es lo que pide la propia nota de `firebase.json`.
   - `utm_content` = **la posición**: `header`, `nav`, `hero`, `plus`, `footer`, `close`. Es lo que
     responde «¿convierte el pie o solo el hero?» sin inflar la lista de campañas.
   - El prefijo `z-` y la regla ganado/comprado se quedan como están: `owned` nunca es `z-`.
5. **Un exento, explícito y con razón**: `home.mjs:312` `installUrl` de los datos estructurados
   (schema.org). No es un CTA, es el **canónico** que leen los buscadores; etiquetarlo mete UTMs en
   un resultado de búsqueda y en cualquier sitio que reutilice el dato. Se queda con `PLAY_URL`
   pelado, y el guard lo exime **por call site concreto**, nunca por fichero.
6. **El guard se amplía a los dos productores**, y se falsifica antes de darlo por bueno:
   - sobre las páginas CONSTRUIDAS de `hosting/public`: ni un `play.google.com/…` sin `&referrer=`,
     salvo la ocurrencia exenta del JSON-LD. Mirar la salida construida y no solo las fuentes es lo
     que hace que *«edité el copy y no reconstruí»* no pueda desplegar en silencio, que es el
     chequeo 3 que ya tiene.
   - cada referrer de la web: percent-encodeado, con las tres claves de `REQUIRED_UTM`, `utm_medium`
     dentro del vocabulario, y la campaña perteneciendo a la tabla.
   - testigo de población vacía en los dos, como los que ya hay.

⚠️ **Recordatorio operativo de `CLAUDE.md`**: este guard no toca bytecode → sale **UP-TO-DATE**.
Falsificarlo exige `--rerun-tasks`, y además hay que `node web/build.mjs` porque el cambio reescribe
las 11 páginas.

## Lo que cambió al ejecutarlo (y no estaba en el diseño)

1. ⛔⛔ **`esc()` escribe `&amp;`, y el primer recuento mintió por eso.** Tras el primer pase salía
   «11 de 42 etiquetados» y parecía que faltaban replacements: lo que pasaba es que `button()` pasa
   el `href` por `esc()`, así que en el HTML el enlace es `…&amp;referrer=…` — que es el HTML
   CORRECTO, y la URL que sigue el navegador lleva `&`. El único que salía crudo era el badge del
   pie, porque `layout.mjs` lo escribía sin `esc()` (antes no tenía ningún `&` que escapar). Se
   unificó a `esc()` **y el guard acepta las dos grafías**, porque esa es la que se despliega.
2. **`PLAY_URL` sigue exportado**, al contrario de lo que decía el punto 1 del diseño. Dos razones:
   el chequeo 1 del guard lo parsea como hecho declarado (`export const NAME = '…'`), y el
   `installUrl` del JSON-LD lo necesita. Lo que sostiene el invariante no es esconder la constante,
   es el guard sobre el HTML construido — que es más fuerte: ve lo que se despliega, no lo que se
   importa.
3. **El exento son DOS ocurrencias, no una**: `installUrl` aparece en la home de cada locale.

## Falsificado, no solo verde

[TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]. Seis mutaciones, seis fallos con su mensaje:

| Mutación | Lo cazó |
|---|---|
| un CTA de `index.html` se queda sin `referrer` | «a store link with no referrer → filed as organic» |
| `utm_campaign%3Dbogus` en `blackfriday.html` | «'bogus' is not in SITE_CAMPAIGNS» |
| `&` literal en vez de `%26` en `support.html` | «referrer is NOT percent-encoded» |
| sin `utm_content` en `terms.html` | «referrer has no `utm_content`» |
| `/descargar/navidad` en `firebase.json` sin fila en la tabla | «no row in DOWNLOAD_SHORTCUTS» — ⛔ y el test de FORMA lo dio por bueno (su slug casa con el path): ese es exactamente el hueco que cierra la tabla |
| `PLAY_LINK` apuntando a un host que no existe | el testigo de población: «only 0 non-exempt store links found across 11 built pages» |

## Criterio de éxito

1. ✅ **40 de los 42** enlaces a Play de `hosting/public` llevan referrer; los dos pelados son los
   `installUrl` del JSON-LD, uno por locale. (El criterio decía 41 al abrir el ticket: mal contado,
   los exentos son DOS porque hay dos homes.)
2. El guard ampliado **falla** si se quita el referrer de un CTA, si se escribe con `&` literal en
   vez de `%26`, o si una campaña no está en la tabla. Comprobado quitándolo y volviéndolo a poner,
   con `--rerun-tasks`.
3. `node web/build.mjs --check` limpio y `./gradlew testAll` verde.
4. **Lo que cierra el ticket de verdad no es el verde**: 24–48 h después del despliegue, Play Console
   → Adquisición de usuarios tiene que mostrar instalaciones con `utm_source=web`. Hasta que ese
   número exista, lo único demostrado es que el enlace sale bien formado — y este ticket nace
   justamente de confundir «el guard pasa» con «el dato llega».

## Consumidores auditados

- `web/lib/layout.mjs` (3 call sites) · `web/lib/components.mjs` (1) · `web/pages/home.mjs` (2 CTA
  + 1 exento) · `web/pages/blackfriday.mjs` (2) · `web/pages/notfound.mjs` (1) ·
  `web/tools/restyle-legal.mjs` (1) → **pendientes**, son el trabajo.
- `firebase.json → hosting.redirects` (7 atajos) → **correctos hoy**; pasan a derivarse de la tabla
  y a estar comparados contra ella, no solo validados en forma.
- `hosting/public/{privacy-policy,delete-account}.html` → ⛔ **a mano, no son salida del build**
  (`CLAUDE.md`). Los reescribe `web/tools/restyle-legal.mjs`, así que entran por ahí, pero hay que
  verificar el fichero desplegado a ojo, no asumir que el build los tocó.
- `web/README.md` (checklist) y la tabla *«La web Y LA FICHA DE PLAY son consumidores…»* de
  `CLAUDE.md` → **les falta la fila**: hoy ninguna dice que un CTA nuevo necesita su campaña. Sin
  esa fila, el próximo botón vuelve a salir pelado y el guard lo cazará, que es mejor que hoy, pero
  la fila es la que lo evita antes.
- Ficha de Play → **no la toca**: su disparador es el build publicado, y esto no cambia lo que la app
  hace.
- App Android → **fuera de alcance, y es el hueco grande**: sin leer el install referrer no habrá
  atribución por usuario nunca, solo agregada en Play/GA4. Se abre aparte,
  `MKT-AN-INSTALL-REFERRER-MUST-REACH-THE-ACCOUNT-001`, porque es otra decisión (toca privacidad,
  consentimiento y el carril de Meta) y no se mete de rondón en un ticket de la web.
- iOS → **no aplica**: no hay app iOS publicada, y el sitio solo enlaza Google Play.
