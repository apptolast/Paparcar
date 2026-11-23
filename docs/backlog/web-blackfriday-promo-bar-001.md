# WEB-BLACKFRIDAY-PROMO-BAR-001 · Desde la home no se llegaba a /blackfriday

**Estado:** ✅ Done (23-11-2026) · mergeada en master por squash.

## Problema

`WEB-BLACKFRIDAY-FOUNDERS-LANDING-001` publicó `paparcar.com/blackfriday`, pero ni el header, ni
el footer, ni el cuerpo de la home la mencionan (`grep -rn blackfriday web/pages/home.mjs
web/lib/layout.mjs` → sin resultados antes de este ticket). Sólo se llega escribiendo la URL
directamente o por un enlace externo (anuncio, redes). El user lo detectó al preguntar "¿cómo
llego a Black Friday desde la página principal?".

## Doctrina violada

Ninguna regla escrita — es un hueco de descubribilidad, no una regla rota. Decisión de producto
del user: una barra de anuncio encima del header, en TODO el sitio, confirmada vía
`AskUserQuestion` en la misma conversación (opción "Barra superior").

## Diseño

- `web/lib/layout.mjs`: nueva función `promoBar(t, pageKey, base)`, llamada dentro de `page()`
  justo antes de `header()`. Se omite cuando `pageKey === 'blackfriday'` — un banner que enlaza a
  la página en la que ya estás es ruido, no ayuda. Todo el bloque es un único `<a>` (mejor diana
  táctil en móvil que un enlace suelto dentro de una frase), con `chevronRight` al final, mismo
  icono que ya usa `faqItem`/`button` para "ver más".
- Copy nuevo: clave top-level `promoBar` en `content/{es,en}.json` (no anidada bajo
  `blackfriday`, porque es chrome del sitio, igual que `nav`/`footer`), reutilizando los tokens
  YA existentes `{{foundersMonths}}` y `{{foundersCap}}` — cero cifras nuevas que verificar.
- CSS: `.promo-bar` nuevo en `web/assets/css/site.css`, reutilizando tokens de color existentes
  (`--brand-muted` de fondo, `--brand` de acento) — ningún color inventado.
- ⛔ Sin fecha de corte automática: el bloque desaparece a mano cuando el user lo pida (quitar la
  llamada a `promoBar()` en `layout.mjs` es un cambio de una línea) — un rebuild programado no
  existe en este proyecto (`node web/build.mjs` es manual), así que un gate por fecha en el propio
  HTML generado no se auto-aplicaría sin un redeploy de todas formas. Se deja anotado aquí para
  que el cierre de la campaña (30-11-2026) incluya quitar esta barra.

## Criterio de éxito

- [x] La barra aparece en home/support/terms/404, pero NO en blackfriday.html ni en
      en/blackfriday.html (comprobado con `grep -c promo-bar` en los 9 ficheros construidos).
- [x] `node web/build.mjs --check` en verde.
- [x] `./gradlew :shared:testDebugUnitTest --tests '*WebSiteParity*' --rerun-tasks` en verde
      (paridad ES/EN de la nueva clave `promoBar`).
- [x] Capturas revisadas en móvil y escritorio, ES y EN — y en `/blackfriday` confirmando su
      ausencia.
- [x] Permiso explícito del user para commit + merge ("Sí, commit + merge", 23-11-2026).
- [ ] Despliegue — pendiente, el user no lo pidió en esta pregunta.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `web/lib/layout.mjs` (`page()`) | **implementa** |
| `web/content/es.json` / `en.json` (`promoBar`) | **implementa** |
| `web/assets/css/site.css` | **implementa** |
| Ficha de Play / `hosting/public/{privacy-policy,delete-account}.html` | **exentos** — las legales
  son hand-written fuera del build; la ficha ya se trató como consumidor aparte en el ticket de la
  landing. |
