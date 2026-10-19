# BRAND-THE-MARK-GAINS-ITS-WORDMARK-001 · la marca gana su palabra, y el disco se queda sin trabajo

**Estado:** ✅ Done · issue **#141**

## Problema

Paparcar tenía **isotipo y nada más**: el Compás B+, símbolo solo. Donde hacía falta la marca CON
su nombre —seis superficies— no había imagotipo que poner, así que cada una se lo inventaba
componiendo el símbolo con la palabra puesta como **texto en una fuente**:

| Superficie | Cómo se inventaba el lockup |
|---|---|
| Cabecera de auth (login y registro) | `PaparcarLogo(72 dp)` + `Text(auth_header_app_name)` |
| Cabecera **y pie** de la web, 6 páginas | `logoMark()` sobre disco + `<span class="brand__name">` |
| Gráfico de funciones de Play | emblema sobre disco + `.word` a 82 px en Jakarta 800 |
| Tarjeta social OG | `paparcar-emblem-512.png` + `d.text((182,92), "Paparcar")` en Pillow |
| README del repo | `paparcar-logo.svg` + `<h1 align="center">Paparcar</h1>` |

Seis composiciones, seis criterios, y la palabra con tres tratamientos tipográficos distintos
(Jakarta 800 a 82 px, Jakarta 800 a 46 px, el rol `screenTitle` de la app). El user entrega el
imagotipo oficial y pide usarlo **funcionalmente donde hoy hay isotipo + texto**.

## Doctrina violada

Ninguna, todavía — no había imagotipo que usar mal. Lo que sí hay es una doctrina que el imagotipo
**pone a prueba**, y la respuesta cambia:

> ⛔ **DOS excepciones, y las dos son del user**: *donde la marca llena su caja, no hay disco; donde
> es un **emblema** dentro de una composición ajena —con un wordmark al lado—, el soporte es
> legítimo.* — CLAUDE.md, § Iconos

Las dos excepciones (banner de Play [PLAY-THE-BANNER-MARK-KEEPS-ITS-DISC-001] y la web
[WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001]) existen por **una sola razón medida**: la marca a
`F_EMBLEM` se quedaba en ~46 px al lado de un wordmark de 82 y **flotaba**. El disco era la muleta
de una composición que nadie había diseñado.

El imagotipo trae esa composición ya resuelta y medida: **1:5,8 entre símbolo y palabra, con 14 u de
canal**. Desaparecida la razón, desaparece el disco — y con él sus tres consumidores, que son
exactamente las tres superficies de arriba. **Decidido por el user el 19-10-2026: disco fuera en las
dos.** Esta es, por tanto, la tarea que **retira las dos excepciones** y deja una sola regla:
*la marca no lleva disco, nunca; cuando necesita decir su nombre, se usa el imagotipo.*

## Señales / datos disponibles

Medido sobre el fichero entregado (`docs/branding/reference/paparcar-imagotipo-2026.svg`):

- **Los tres `path` del isotipo son idénticos byte a byte** a `mark/paparcar-mark-compas-b.svg`. La
  mitad del dibujo ya era nuestra: lo único nuevo es la **palabra**.
- Lienzo `339,676 × 62`. Símbolo a `scale(0.18620398)` sobre el lienzo `244 × 236` del maestro →
  caja `45,434 × 43,944`, en `(9, 9)`. Palabra en `(68,471, 10)`, bbox `262,205 × 42`.
- De ahí salen las **tres constantes del lockup**, y el ancho se DERIVA:
  `9 + 45,434 + 14,037 + 262,205 + 9 = 339,676` ✓ (el entregado dice `339.676`).
- La palabra está **centrada exactamente** en el eje del lienzo (`(62−42)/2 = 10,0`); el símbolo cae
  a `9`, a `0,028` del centro. Se centran los dos: es la regla limpia y reproduce el original.
- Ocho glifos en **minúscula**, contorneados (sin dependencia de fuente): bowl `r 8,4`, astil `8,4`,
  altura x `33,6`, descendente a `42`. La `r` es de medio ancho (avance 21 frente a 37,8).
- Color de la palabra en la variante oscura: `#F4F7F4`, que **no es un token del proyecto** — el más
  cercano, `PapSurfaceLight #F4F7FC`, es una superficie azulada con otro papel. Como Nivel 3 trae su
  propio color, se respeta tal cual.

## Diseño

### 1 · La palabra es geometría nueva, y entra al maestro como fuente única

El símbolo se **dibuja** desde parámetros (`build_marks.py`); la palabra **no se puede derivar**, es
un entregable contorneado. Pero sí puede tener **un solo sitio donde vive**, que es lo que la regla
del repo exige de verdad:

> ⛔ **El logo se GENERA, no se copia.** Antes de esto, `grep -rl "M92.28,57.77"` devolvía **25
> ficheros** con el path a mano dentro.

Así que la palabra entra en `docs/branding/master/wordmark.py` —los 8 `d`, su bbox medido y sus
nombres de glifo— y **ninguna superficie la escribe**: todas la reciben de `export_surfaces.py`. Si
el imagotipo se pegara como SVG suelto en seis sitios, en tres ediciones estaríamos exactamente en
el agujero de los 25 ficheros, con el añadido de que el símbolo tendría **dos** definiciones.

### 2 · El lockup se compone, no se copia

`lockup()` en el exportador compone símbolo + palabra desde **tres constantes medidas**
(`LOCKUP_PAD`, `LOCKUP_GAP`, `LOCKUP_MARK_H`) y el encuadre del maestro. Consecuencia buscada: si
`build_marks.py` mueve un lóbulo, el lockup se re-deriva —escala y ancho de lienzo incluidos— en las
seis superficies a la vez. Es el mismo criterio que ya rige `frame(fraction)` y `emblem_transform()`.

### 3 · Dos variantes, y el verde ya estaba decidido

| | Símbolo | Palabra | Contraste medido |
|---|---|---|---|
| **sobre oscuro** | `PapGreen #25F48C` | `#F4F7F4` (del entregable) | 17,60:1 sobre `PapInk` |
| **sobre claro** | `PapGreenLight #009F5E` | `PapInk #0D1117` | 18,99:1 sobre blanco |

El verde no se elige aquí: lo manda `[BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001]` —`PapGreen` sobre
blanco da **1,42:1**—. La tinta de la palabra sale de un token que **ya existe**, y el par resultante
es simétrico (17,60 / 18,99), no elegido a ojo.

### 4 · Dónde va el imagotipo y dónde sigue el isotipo

*Regla:* **la marca sola → isotipo; la marca diciendo su nombre → imagotipo.** Nunca más símbolo +
texto compuestos a mano.

- **Imagotipo** → cabecera de auth · cabecera y pie de la web (6 páginas) · banner de Play ·
  tarjeta social OG · README.
- **Isotipo, sin tocar** → splash · icono adaptativo · small icon de notificación · las cuatro
  baldosas de Apple · favicon · Dev Catalog · onboarding.
- **Paywall: isotipo, y a propósito.** Dos razones independientes, cada una suficiente: su marca
  está **fundida en metal** (`PapPremiumMark` sobre `PapPremiumSurface`, el material de la pantalla)
  y un imagotipo plano la apagaría; y su título no es el de la marca, es **«Paparcar Plus»** — una
  palabra más. [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001] sigue en pie.

### 5 · El disco se retira, no se mueve

Sus tres consumidores pasan al imagotipo, así que `EMBLEM_SITE`, `EMBLEM_BANNER`, `disc_circles()` y
`emblem_svg()` se quedan **sin un solo call site**. Se borran, y CLAUDE.md pierde sus dos
excepciones. Dejar el código muerto sería peor que no haberlo escrito: una excepción sobre algo que
no se renderiza no es una excepción, es un agujero — el mismo argumento que sacó la action bar de la
allowlist de tipografía en [UI-TYPE-SYSTEM-HYGIENE-001].

## Criterio de éxito

- `python export_surfaces.py` escribe las seis superficies y **ningún** fichero contiene los paths de
  la palabra ni los del símbolo escritos a mano.
- `BrandSurfaceGuardrailTest` mide el imagotipo generado (no lo lee: lo **mide**) y falla si una
  superficie se descuelga del maestro.
- `WebSiteParityGuardrailTest` verde con el disco retirado y `node web/build.mjs --check` limpio.
- `./gradlew testAll` verde. `assembleProdDebug` + `assembleMockDebug` compilan.
- Captura de la cabecera de auth en claro y en oscuro [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001].

## Consumidores auditados

| Consumidor | Veredicto |
|---|---|
| `PaparcarAuthSlots.kt` (cabecera) | **cerrado** — pasa a `PaparcarImagotipo`, el `Text` del nombre desaparece |
| `web/lib/layout.mjs` ×2 (cabecera, pie) | **cerrado** — `logoLockup()`, sin disco |
| `web/tools/restyle-legal.mjs` | **cerrado** — mismo lockup |
| `docs/release/play-listing/assets/feature.html` | **cerrado** — lockup, disco fuera |
| `web/tools/prepare-assets.py` (tarjeta OG) | **cerrado** — lockup rasterizado, fuera el `d.text("Paparcar")` |
| `README.md` | **cerrado** — imagotipo temado, fuera el `<h1>` decorativo |
| `PaywallScreen.kt` | **exento, razonado** — metal + «Plus» (ver §4) |
| `splash` · icono · notificación · Apple ×4 · favicon · Dev Catalog | **exentos** — marca sola, no hay texto al lado |
| `PaparcarLogo` / `PaparcarIconmark` / `PaparcarMark` | **intactos** — siguen siendo el isotipo |
| `emblem_svg()` · `disc_circles()` · `EMBLEM_*` | **borrados** — sin call sites (ver §5) |

## Verificación

- `python wordmark.py` → caja medida de los contornos **262,20469 × 42,00000**, idéntica a la
  declarada. El parser mide extremos reales de cada cuadrática: sacar min/max de los números del
  `d` ensancharía la caja un 8 % (los puntos de control caen a 1,082 r) y además mezclaría ejes,
  porque `H` y `V` llevan una sola coordenada.
- `python export_surfaces.py` → lienzo **339,67569 × 61,94414 (5,484:1)**, símbolo a **×0,186204**.
  El entregable dice `339.676 × 62` y `scale(0.18620398)`: el modelo lo reproduce sin copiarlo.
- `bash render_graphics.sh lockup` → PNG 1024×187 con alfa; símbolo **4,5 %** centrado al **10 %**
  del ancho, palabra **23,6 %** al **57 %**.
- `node web/build.mjs --check` limpio; las **7 páginas** llevan el imagotipo (incluidas las dos
  legales, vía `restyle-legal.mjs`, que verifica que las mitades de contenido salen byte a byte).
- **`./gradlew testAll` + ambos flavors: 3917 tests, 0 fallos.** Dos guards hubo que ponerlos al
  día y los dos lo pedían a gritos: `WebSiteParityGuardrailTest` comprobaba el `EMBLEM_TRANSFORM`
  que ya no existe —ahora comprueba **las dos** mitades, y la palabra es la que más lo necesita
  porque no tiene fuente paramétrica a la que volver—, y `BacklogStatusGuardrailTest` el recuento.
- **Los cuatro guards nuevos, FALSIFICADOS** (`--rerun-tasks`, los cuatro en rojo a la vez y luego
  restaurados): quitar un glifo de una superficie, devolver un `<span class="brand__name">` a la
  cabecera, devolver `EMBLEM_SITE` al exportador, y borrar la palabra del PNG. Un guard que no se
  ha visto fallar no es un guard [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001].
- **Medido en device** (emulador, cabecera de auth real vía preset «Login» del Dev Catalog), que es
  lo que prueba que la variante por tema está bien cableada:

  | | fondo | palabra | símbolo |
  |---|---|---|---|
  | oscuro | `#0D1117` | `#F4F7F4` — 7538 px | `#25F48C` — 1403 px |
  | claro | `#FFFFFF` | `#0D1117` — 7550 px | `#009F5E` — 1403 px |

  **Los mismos 1403 px de símbolo en las dos**: un solo dibujo, dos pinturas. Si el tema claro
  estuviera cogiendo el drawable oscuro, el verde saldría `#25F48C` sobre blanco — los 1,42:1 que
  `PapGreenLight` existe para evitar.

## Lo que este ticket NO deja cerrado

- **El banner de Play y la ficha están sin re-renderizar.** `feature.html` ya lleva el imagotipo,
  pero los PNG por idioma salen de Chrome headless (`render_graphics.sh feature`) y subirlos es una
  acción de tienda, del user. Hasta entonces Play sigue enseñando el banner con disco.
- **La tarjeta social regenerada no está desplegada**: `firebase deploy --only hosting` es del user.

## Fuera de alcance, con ticket propio

`BRAND-THE-NAME-IS-WRITTEN-IN-LOWERCASE-001` — el user decide el 19-10 que la marca pasa a
**minúscula también en texto corrido** (~600 ocurrencias: 39 × 9 locales de strings, 124 en el copy
de la web, 170 en `hosting/public`, la ficha de Play y el `android:label`). Es copy, no dibujo.

⚠️ **Pero no son independientes, y conviene decirlo bien**: las dos tareas **regeneran
`hosting/public`**, así que si salen de master en paralelo chocan en ~10 HTML generados. No es un
conflicto de verdad —se resuelve corriendo `node web/build.mjs` después— pero sí obliga a un orden:
la minúscula va **encima** de ésta, no al lado. Lo que de verdad no se solapa es la FUENTE: el
dibujo por un lado, el copy por otro.

🔑 Y esta tarea le deja la costura hecha: el nombre de la marca estaba en `auth_header_app_name`
—una key con ámbito de pantalla de login— y pasa a **`brand_name`**, que es un único sitio con el
ámbito correcto. La minúscula cambia ahí un valor, no treinta y nueve.
