# WEB-BLACKFRIDAY-FOUNDERS-LANDING-001 · paparcar.com/blackfriday anuncia el regalo de Fundadores

**Estado:** ✅ Done (22-11-2026) · mergeada en master por squash.

> El despliegue (`firebase deploy --only hosting`) queda fuera de este cierre: el user pidió no
> desplegar sin aprobarlo aparte, puede que quiera coordinarlo con el corte de versión. La página
> vive en `hosting/public/` del repo, lista para desplegar cuando lo diga.

## Problema

La promo de Black Friday (`MONEY-FOUNDERS-PLUS-PROMO-001` + su gate de cuenta nueva,
`MONEY-FOUNDERS-PROMO-REQUIRES-A-NEW-ACCOUNT-001`, ambas ✅ Done y desplegadas en producción el
22-11) no tenía ninguna página pública que la anunciara. `paparcar.com/blackfriday` devolvía 404 —
constatado en `project_money_founders_promo_requires_a_new_account_001` de la memoria del agente.

## Doctrina violada

Ninguna regla rota — esto es la web actuando como consumidor de una verdad del producto que ya
existe (`CLAUDE.md` § *la web y la ficha de Play son consumidores de la verdad del producto*), para
una feature que hasta hoy no tenía superficie pública.

## Hechos que la página declara (verificados en el repo, no en Firestore en vivo)

- Cupo de 2.000 cuentas y duración de 3 meses: coinciden con los guardas de
  `functions/src/foundersPromo.ts::decideReservation` (`promo.cap > 2_000` y
  `promo.durationMonths !== 3` fallan cerrado si no son esos valores).
- Cierre: `FOUNDERS_CLOSE_MILLIS = Date.UTC(2026, 10, 30, 23)` en el mismo fichero — el mismo
  instante que `FOUNDERS_PROMO.endsAtUtc` en `web/lib/config.mjs`.
- Requisito de cuenta NUEVA: texto explícito en la FAQ, citando la cuenta que ya existía no
  cuenta — es justo lo que `MONEY-FOUNDERS-PROMO-REQUIRES-A-NEW-ACCOUNT-001` forzó en el servidor.
- CTA → `PLAY_URL` (ya existente en `config.mjs`, deriva de `applicationId`). Sin enlace a App
  Store: iOS no está publicado.
- No hay contador en vivo de cuántas cuentas quedan: el sitio es estático, y un número que cambia
  en Firestore se habría quedado desactualizado en la primera hora.

## Diseño

- `web/lib/config.mjs`: nuevo bloque `FOUNDERS_PROMO` (cap/duración/fecha de cierre, con comentario
  citando `functions/src/foundersPromo.ts` como fuente) + 4 tokens nuevos en `COPY_TOKENS`
  (`foundersCap`, `foundersMonths`, `foundersDeadlineEs`, `foundersDeadlineEn` — dos variantes de
  fecha porque `COPY_TOKENS` no está particionado por locale y la fecha sí cambia de idioma;
  ambas se derivan con `Intl.DateTimeFormat` del MISMO instante UTC, nunca escritas a mano) + ruta
  `blackfriday` en `routes()`.
- `web/content/{es,en}.json`: clave nueva `blackfriday` con la misma forma en los dos idiomas
  (comprobado a mano con un script de paridad de `keyPaths`, antes de correr el test real).
- `web/pages/blackfriday.mjs`: página nueva, compuesta ÍNTEGRAMENTE de componentes que ya existían
  (`sectionHead`, `button`, `tierColumn`, `faqItem`, `icon`) — cero HTML nuevo salvo el montaje de
  secciones. Sin ilustración: `FoundersGiftIllustration` (la caja 3D del regalo) está dibujada con
  Compose `Canvas`, no un VectorDrawable, así que el pipeline `illus_* → SVG` de
  `tools/prepare-assets.py` no puede extraerla — redibujarla a mano en SVG habría sido un segundo
  dibujo independiente de la misma pieza, justo lo que las reglas de marca prohíben. La página es
  tipográfica en su lugar.
- `web/build.mjs`: `blackfriday` añadida al mapa `PAGES`.
- `web/assets/css/site.css`: 5 modificadores pequeños, todos reutilizando tokens existentes —
  `.hero--centered`, `.proof__grid--3`, `.tiers--single`, `.cta__inner--centered`, `.fineprint`.
  Los tres primeros usan selectores compuestos (`.a.b`) a propósito: es la única forma de que un
  modificador gane a las reglas responsive existentes (`.hero__inner`, `.tiers`, `.cta__inner`) sin
  importar el orden de aparición en la hoja de estilos.

## Criterio de éxito

- [x] `node web/build.mjs --check` en verde (el primer fallo, dos enlaces muertos a
      `/en/blackfriday`, es un artefacto conocido y ya existente del propio chequeo: comprueba
      contra disco en medio de la construcción, y la primera vez que una página nueva se construye
      para los dos locales, el locale que se escribe primero no encuentra todavía el fichero del
      segundo. Una segunda pasada, con el fichero ya en disco, sale limpia — comprobado).
- [x] `./gradlew :shared:testDebugUnitTest --tests '*WebSiteParity*' --rerun-tasks` en verde.
- [x] Capturas en 390/1440 (ES) y 390 (EN) revisadas — hero, proof, pasos, tarjeta de Plus, FAQ y
      CTA final se ven correctamente en los tres anchos.
- [x] Permiso del user para commit + merge ("adelante", 22-11-2026).
- [ ] Despliegue (`firebase deploy --only hosting`) — explícitamente fuera de esta tarea hasta que
      el user lo apruebe; puede querer coordinarlo con el corte de versión. Esto NO bloquea el
      cierre: la página está construida y en `hosting/public/`, lista para desplegar aparte.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `web/lib/config.mjs` (`FOUNDERS_PROMO`, `COPY_TOKENS`, `routes()`) | **implementa** |
| `web/content/es.json` / `en.json` (`blackfriday`) | **implementa**, paridad de claves verificada |
| `web/pages/blackfriday.mjs` | **implementa** |
| `web/build.mjs` (`PAGES`) | **implementa** |
| `web/assets/css/site.css` | **implementa** (5 modificadores nuevos, sin tocar reglas existentes) |
| Ficha de Google Play | **exenta, a propósito** — `CLAUDE.md` § *la ficha describe el build
  PUBLICADO*; esto es una página web, no un cambio del build. Si el user quiere anunciarlo también
  en la ficha, es tarea aparte. |
| `WebSiteParityGuardrailTest` | **no necesita cambios** — sus comprobaciones de `FREE_LIMITS` /
  `PLUS_FIGURES` no conocen el bloque `FOUNDERS_PROMO`, y no se ha añadido un guarda nuevo para él
  (fuera del alcance pedido: el ticket es "crear la página", no "automatizar su paridad futura"). |
