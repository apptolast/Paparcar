# ICON-A-CONCEPT-KEEPS-ONE-GLYPH-ON-EVERY-SURFACE-001 · un concepto se dibuja igual en todas partes, también fuera de la app

**Estado:** ✅ Done · un concepto se dibuja igual en la app y en `paparcar.com`, y lo sostiene un guard.

## Problema

Salió al exportar los iconos de los seis conceptos que la app nombra —plaza de la comunidad,
parking público, gasolinera, cifras, coche compartido, historial— para usarlos fuera del código.
Al buscar cuál era el de cada uno aparecieron tres agujeros distintos:

1. **`paparcar.com` dibuja dos conceptos con glifos que la app RETIRÓ.** `web/lib/icons.mjs`
   declara a mano su propio `spot` (un pin hueco) y su propio `parking` (una P dentro de un
   cuadrado). La app no usa ninguno de los dos desde
   `SPOT-A-FREE-SPOT-IS-A-GAP-NOT-A-GIFT-001` (la plaza es un trapecio a rayas) y
   `UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001` (el parking es la P desnuda). Su `fuel` es además un
   surtidor dibujado a mano que tampoco es el `LocalGasStation` que renderiza la app.
   **Está vivo**: el path del pin hueco sale 2 veces en `hosting/public/index.html` y otras 2 en
   `/en/index.html` — tarjeta de feature y punto del recorrido.
2. **«Cifras» no tenía glifo, y a la vez tenía dos.** No hay token para el concepto, pero el
   paywall pinta `Icons.Rounded.Insights` en la tira de motivo de `PaywallReason.STATS` mientras
   la fila de beneficio que VENDE ese mismo gate lleva `Icons.Rounded.History`. Dos dibujos para
   una cosa, en la misma pantalla.
3. **`Icons.Rounded.Group` decía tres cosas.** Coche compartido, «N en camino» (conductores que
   van a por esa plaza) y la zona `FAMILY`.

## Doctrina violada

- **La web es un consumidor más de la verdad del producto**
  [WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001]. Un cambio de producto que no llega ahí deja la
  web prometiendo lo de antes, en dos idiomas, para siempre — que es exactamente lo que pasó.
- **El logo se GENERA, no se copia.** Las dos marcas propias (`CommunitySpot`, `CarParkMark`) son
  dibujo nuestro igual que la marca: tener sus paths escritos a mano en un segundo sitio es el
  mismo agujero que tenían los 25 ficheros con `M92.28,57.77` dentro.
- **Sistemas, no parches.** El invariante —*un concepto, un glifo*— no vive hoy en ningún sitio:
  cada superficie elige. Arreglar sólo los tres de la web deja el agujero abierto para el
  siguiente.

## Señales / datos disponibles

- `PaparcarIcons.kt` ya es el sitio donde se decide el glifo de un concepto; le faltan cuatro de
  los seis (`SharedCar`, `History`, `Figures`, y el `EnRoute` que sale de separar `Group`).
- `docs/branding/master/export_surfaces.py` ya **genera** el logo hacia
  `web/lib/mark.generated.mjs`, que `icons.mjs` importa. El carril existe; los glifos de concepto
  no lo usan.
- Los Material se pueden derivar del *sources jar* de `material-icons-extended` que resuelve el
  build, así que la web puede llevar el MISMO path que compila la app sin copiarlo a mano.

## Diseño

**El invariante vive en dos sitios encadenados, y el segundo se genera del primero.**

1. `PaparcarIcons` es la fuente: los seis conceptos (más `EnRoute`) tienen token ahí, y ningún
   call site vuelve a nombrar el Material crudo para uno de ellos.
2. `docs/branding/icons/export_icons.py` —el exportador que abrió este ticket— pasa a emitir
   también `web/lib/glyphs.generated.mjs`, exactamente como `export_surfaces.py` emite
   `mark.generated.mjs`. `icons.mjs` importa de ahí y **borra** sus tres paths a mano.
3. Un guard que lo sostenga, porque una regla escrita y no comprobada ya se rompió una vez aquí.

**Cifras → `Icons.Rounded.BarChart`.** Es el único candidato que no mete una idea ya gastada en la
rejilla donde vive: `Insights` lleva la chispa de `AutoAwesome`, que en ESE mismo grid significa
«detectado automáticamente», y `Leaderboard` es un podio, que dice *ranking* donde no se compite.

**`Group` se reserva para coche compartido.** «N en camino» pasa a `NearMe`, que además dice mejor
lo que es —gente YENDO hacia allí, no un grupo—. La zona `FAMILY` no se toca: ahí el usuario elige
un pictograma de familia de un selector, y es otro dominio.

## Criterio de éxito

- El path que la web sirve para plaza, parking y gasolinera es **byte a byte** el de la app, y lo
  comprueba un guard que falla si alguien vuelve a declararlo a mano.
- Los seis conceptos se piden por token; `grep` del Material crudo para uno de ellos no devuelve
  call sites.
- `./gradlew testAll` verde · `node web/build.mjs --check` limpio.

## Consumidores auditados

| Superficie | Antes | Resultado |
|---|---|---|
| `web/lib/icons.mjs` · `spot`, `parking`, `fuel` | 3 paths a mano, 2 retirados | → generados |
| `hosting/public/{,en/}index.html` | pin hueco ×2 por página | → reconstruido |
| `PaywallScreen` · motivo STATS | `Insights` | → `Figures` |
| `PaywallScreen` · beneficios | Material crudo | → tokens |
| Coche compartido (6 call sites) | `Group` crudo | → `SharedCar` |
| Historial (paywall, barra de periodo) | `History` crudo | → `History` token |
| Gasolineras (4 call sites) | `LocalGasStation` crudo | → `Fuel` token |
| «N en camino» (3 call sites) | `Group` | → `EnRoute` (`NearMe`) |
| `ZoneIconMap` · `FAMILY` | `Group` | exento — selector del usuario, otro dominio |
| Banner de Play / tarjeta social | sólo marca y wordmark | exento — no dibujan conceptos |
| `hosting/public/*.html` legales | sin glifos de concepto | exento |
| `StatsPeriodBar` · chip «este año» | `History` | exento — es un PERIODO del calendario, en una fila de `Today`/`DateRange`/`CalendarMonth`; no es el concepto |
| `BottomNavTabOptionsPreviews` | `NearMe` | exento — exploración de diseño en `@Preview`, nunca se renderiza en la app (ver follow-up) |

Tras el barrido, el Material crudo de los seis conceptos aparece en **3 sitios y los tres son los
exentos de arriba**. Todo lo demás pasa por token.

## Follow-up fuera de alcance

`BottomNavTabOptionsPreviews.kt` es una comparativa A/B/C de pestañas de nav que no se renderiza en
ningún sitio y que ya eligió su opción hace tiempo. No es de este ticket, pero es exactamente el
tipo de código muerto que obliga a escribir una exención: conviene borrarlo o quedará como
«excepción» permanente de cualquier regla de iconos futura.

## Resultado medido

- **La web.** El path del pin hueco salía 2 veces por página y ahora sale **0**; el trapecio a
  rayas de la app sale 2, y la P llega con el `transform` que la encuadra desde su rejilla de 48.
  Comprobado mirando el SVG que el sitio sirve de verdad, rasterizado desde `hosting/public`, no
  sólo por `grep`.
- **La app.** 16 call sites pasan a token, en 14 ficheros. De paso salieron dos `import` muertos
  (`FilterAltOff`, `KeyboardArrowRight`) que ya lo estaban en master — los quita el mismo barrido
  mecánico, no son cambio de alcance.
- **Suite.** `./gradlew testAll --rerun-tasks` → **3925 tests, 0 fallos**. ⚠️ Sin `--rerun-tasks`
  salía verde en 19 s **sin ejecutar nada**: los guards leen ficheros que Gradle no rastrea como
  entrada, así que da la tarea por UP-TO-DATE. Un verde rápido aquí no es un verde.
- **El doc nuevo movió el recuento de `docs/backlog/README.md`** (803 → 804 ficheros; al cerrarlo, 717 cerrados y 78 abiertos), como avisa el flujo de ticket.

## El guard, falsificado

`WebSiteParityGuardrailTest · the site draws a product concept with the app's own glyph`. Tres
preguntas, las tres rotas a propósito y las tres cazadas:

| Se rompe así | ¿lo caza? |
|---|---|
| la web vuelve a **declarar** `spot` en `icons.mjs` | 🔴 sí |
| se **edita a mano** el módulo generado (o cambia la app y nadie regenera) | 🔴 sí |
| se regenera y **no se reconstruye** el sitio | 🔴 sí |

⚠️ Y una trampa que salió al falsificar, no al razonar: el generador escribía el módulo con **CRLF**
—`write_text` traduce en Windows— y eso bastaba para que el guard no reconociera el fichero que el
propio exportador acababa de escribir. El resto de `web/lib` es LF. Ahora el exportador fuerza LF.
