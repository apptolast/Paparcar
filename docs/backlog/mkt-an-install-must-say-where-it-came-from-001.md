# MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001 · una instalación tiene que decir de dónde viene

**Estado:** ✅ Done (14-10-2026) · rama y worktree borrados al cerrar
· ⏳ **queda desplegar y verlo UNA vez en device** — ver *Criterio de éxito*: hasta entonces la
atribución está escrita, no demostrada

## Problema

`firebase.json` lleva el redirect `/descargar` → Play desde el ticket
[WEB-A-DOWNLOAD-LINK-NEEDS-A-ROUTE-001], y su propio comentario dice para qué existe:
*«Marketing shortcut (campaigns, social bios, QR)»*. **Funciona**, y la cuenta de Instagram
`@paparcar_oficial` ya lo tiene puesto en la bio.

Lo que no hace es decir **de dónde viene el clic**:

```
hoy        https://play.google.com/store/apps/details?id=com.rndeveloper.paparcar
```

Play apunta esas instalaciones como **orgánicas** y GA4 las ve como *(direct)*. Consecuencia medible:
**hoy no se puede saber si la cuenta de Instagram ha traído una instalación o trescientas.** Y no
hay forma de arreglarlo desde fuera del repo:

- ⛔ **poner UTMs en el enlace de la bio no sirve**: los parámetros de la URL **se pierden** en el
  salto a la tienda. Lo único que sobrevive es el **install referrer** de Play;
- ⛔ **el redirect no propaga la query entrante**: un 301 de Hosting a una URL fija descarta lo que
  venga detrás, así que `/descargar?utm_source=x` tampoco llega a ningún sitio.

🟢 Y la mitad difícil ya está hecha, verificada en el manifest fusionado de los tres flavors: la app
declara `BIND_GET_INSTALL_REFERRER_SERVICE` (entra transitivamente con Firebase / Play Services), así
que **ya lee el install referrer** y Firebase lo publica como *First user source/medium* en el primer
`first_open`. Falta sólo que el redirect **lo mande**.

## Doctrina violada

Ninguna regla de código. Sí una de las del proyecto, la de
[WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001]: *la web es un consumidor más de la verdad del
producto*, y este redirect es la pieza de la web que el marketing usa. Está a medio construir: tiene
la ruta y no tiene el dato.

Y una del plan de marketing (`docs/marketing/INSTAGRAM-GROWTH-STRATEGY.md` §7.1): **sin atribución,
el mes de contenido no se puede leer** — ni el orgánico ni, sobre todo, el pagado, donde además hay
dinero en juego y el CPA se calcula dividiendo gasto entre instalaciones atribuidas.

## Señales / datos disponibles

- `firebase.json` · `hosting.redirects` — un único objeto, `source: "/descargar"`, 301.
- `web/lib/config.mjs` · `PLAY_URL` — la otra copia de la misma URL.
- `WebSiteParityGuardrailTest` ya compara **las dos** contra el `applicationId`, con
  `endsWith("?id=$applicationId")`.
- `docs/marketing/INSTAGRAM-GROWTH-STRATEGY.md` §7.1 y §9 — qué slugs hacen falta y por qué
  `utm_medium` tiene que separar lo orgánico de lo pagado.

## Diseño

**Una entrada por ruta exacta, cada una con su `referrer` codificado**, y el guard pasa a exigirlo.

| Ruta | `utm_medium` | `utm_campaign` | Para qué |
|---|---|---|---|
| `/descargar` | `social` | `bio` | el enlace de la bio y los QR |
| `/descargar/parte` | `social` | `parte` | territorio EL PARTE |
| `/descargar/vuelta` | `social` | `vuelta` | territorio LA VUELTA |
| `/descargar/galibo` | `social` | `galibo` | territorio EL DATO · gálibo |
| `/descargar/compartido` | `social` | `compartido` | coche compartido |
| `/descargar/z-mostoles` | `paid` | `z-mostoles` | primera zona de compra en Meta (§9) |

Destino, con `utm_source=instagram` en todas:

```
https://play.google.com/store/apps/details?id=com.rndeveloper.paparcar&referrer=utm_source%3Dinstagram%26utm_medium%3Dsocial%26utm_campaign%3Dbio
```

Tres decisiones y su razón:

1. ⛔ **Una entrada por ruta exacta, jamás una alternancia `@(a|b|c)`.** El repo ya pagó esa lección
   en las reglas de cabeceras de este mismo fichero: *«desplegó limpio y no coincidió con NADA»*
   [WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001]. Y se verifica leyendo la URL viva, no el
   deploy.
2. ⛔ **El valor del `referrer` va PERCENT-ENCODED** (`%3D`, `%26`). Un `&` literal dentro del valor
   lo parte en parámetros de Play distintos: la URL sigue funcionando, la tienda sigue abriendo, y la
   atribución se pierde **sin un solo error**. Es el fallo mudo que justifica el guard de abajo.
3. ⛔ **`utm_medium` separa `social` de `paid`.** Sin eso el libro de cuentas mezcla instalaciones
   compradas con instalaciones ganadas y el CPA deja de poder calcularse.

**Y el guard se hace más fuerte, no más débil.** El chequeo actual (`endsWith("?id=…")`) se rompería
con el `referrer` detrás, y la salida fácil —relajarlo a `contains`— dejaría de vigilar lo único
nuevo que puede romperse en silencio. Pasa a:

- el chequeo de hechos acepta el `referrer` (`contains("?id=$applicationId")`);
- **un test nuevo** sobre **todas** las rutas `/descargar*`: que exista la base, que cada destino
  apunte al `applicationId`, que cada una lleve `referrer` con los tres `utm_*`, que esté
  **codificado** (y no con `&` literales), y que el **`utm_campaign` coincida con el slug de su
  propia ruta** — para que `/descargar/parte` no pueda publicar `utm_campaign=vuelta`.

## Criterio de éxito

✅ **El guard, falsificado de cinco maneras distintas y cada una con su propio mensaje** (no se da por
bueno un guard que no se ha visto fallar):

| Lo que se rompió a mano | Lo que dijo |
|---|---|
| `/descargar/parte` sin `&referrer=` | *no `&referrer=` at all → this install will be filed as organic* |
| `/descargar/vuelta` con `utm_campaign=parte` | *utm_campaign is 'parte' but the path says 'vuelta'. A campaign that names another territory makes the acquisition report lie* |
| `/descargar/galibo` con el valor **sin codificar** | *referrer is NOT percent-encoded (…). Write %3D for '=' and %26 for '&', or Play splits it into parameters of its own* |
| `/descargar/z-mostoles` declarado `utm_medium=social` | *utm_medium='social' and the slug 'z-mostoles' disagree … Mixing them merges bought installs into the earned ones* |
| la ruta base renombrada a `/download` | *[empty population] firebase.json has no redirect whose source is exactly '/descargar' — found []* |

✅ `./gradlew :shared:testDebugUnitTest --tests '*WebSiteParity*' --rerun-tasks` — **9 tests, verde**,
y comprobado en el XML que la prueba nueva **se ejecuta** (no UP-TO-DATE).
✅ `./gradlew testAll` verde. ⚠️ Falló primero en `BacklogStatusGuardrailTest` por el recuento de
`docs/backlog/README.md`, que cambia al añadir este doc: 791 → **792 ficheros, 707 cerrados, 76 abiertos**.
✅ JSON válido y 6 redirects, parseado.

⏳ **Pendiente, y ningún test puede hacerlo**: desplegar (`firebase deploy --only hosting`, lo hace
el user) y luego **la comprobación en device** — pinchar `paparcar.com/descargar` en un móvil,
instalar, abrir, y ver en GA4 el `first_open` con `First user source/medium = instagram / social`.
Hasta que eso se vea **una vez**, la atribución está escrita pero no demostrada.

## Consumidores auditados

`grep -rn "/descargar"` sobre el repo, clasificado:

| Sitio | Veredicto |
|---|---|
| `firebase.json` · `hosting.redirects` | ✅ **es lo que se cambia** |
| `WebSiteParityGuardrailTest` | ✅ **se actualiza y se endurece** |
| `web/lib/config.mjs` · `PLAY_URL` | ✅ **exento**: es la URL canónica de la tienda, sin campaña. El `referrer` es propiedad del atajo de marketing, no del hecho «dónde vive la app» |
| `web/content/{es,en}.json`, páginas generadas | ✅ **exento**: enlazan `PLAY_URL`, que no cambia |
| `docs/marketing/*` | ✅ ya describen este diseño (§7.1, §9) |
| Bio de `@paparcar_oficial` | ✅ **no se toca**: el enlace es el mismo; el arreglo es de servidor |

⚠️ **No es retroactivo**: lo instalado hasta el despliegue queda sin atribuir, y eso no se recupera.

## Fuera de alcance (deliberado)

- **Una ruta por anuncio** dentro de una zona: multiplicaría rutas y guardarraíles para distinguir
  creatividades que el panel de Meta ya distingue por CTR. La atribución de instalación se queda
  **por zona**, y está dicho en §9.4 del plan.
- **Píxel de Meta y SDK de Meta**: descartados con su coste escrito en §7.1.b y §9.8 del plan.
- **Desplegar**: `firebase deploy --only hosting` lo hace el user, como cualquier despliegue.
