# PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001 · la ficha de Play sigue enseñando la marca que ya no existe

**Estado:** 🔵 Abierto · sin rama · follow-up sacado a propósito del alcance de
`BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001` (mergeado el 29-09-2026).

## Problema

La marca nueva entró en las 21 superficies del producto —los tres iconmarks, los adaptive icons,
`PaparcarLogo`, la cresta de Plus, el favicon y la web— pero **los PNG ya renderizados de la ficha
de Play no se han vuelto a generar en ninguna vuelta del ticket de marca**. Siguen siendo el render
de `PLAY-LISTING-001`, o sea la marca de 2022 con disco:

- `docs/release/play-listing/assets/play-icon-512.png`
- los feature graphics: el base y los nueve locales de
  `assets/screenshots/{en,es,it,pt,fr,de,nl,pl,ro}/feature-graphic-1024x500.png`, más las
  alternativas de la raíz.

Las **fuentes** (`assets/icon.html`, `assets/feature.html`) ya llevan la marca nueva: su grupo del
glifo lo reescribe `export_surfaces.py`. Lo que falta es ejecutar el render.

## Doctrina violada

*La web es un consumidor más de la verdad del producto* — y la ficha de Play también lo es: la
marca cambió en el repo y su escaparate sigue prometiendo la anterior. Es exactamente el fallo que
`PLAY-A-LISTING-MUST-MATCH-THE-BUILD-IN-PRODUCTION-001` cerró para el **texto** de la ficha, sin
cerrarlo para la **imagen**.

## Diseño

No hay decisión de diseño pendiente: la marca ya está elegida y las fuentes HTML ya la llevan. Es
ejecución, y va **antes** de volver a tocar la ficha de Play (si no, se sube una ficha con icono
viejo y capturas nuevas, que es peor que no tocarla).

```bash
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
# icono y feature: comando exacto en docs/release/play-listing/README.md §Re-renderizar los gráficos
```

⚠️ Los feature graphics están **localizados a los 9 idiomas** desde el 02-09: son diez renders, no
uno. Y el icono de 512 se renderiza aparte del adaptive icon de la app — comparten la marca, no el
encuadre.

## Criterio de éxito

- `play-icon-512.png` y los diez feature graphics muestran la marca de 2026 (sin disco).
- Un diff visual contra el `paparcar_iconmark_*.xml` de master: mismo dibujo, mismo canal de la
  rueda derecha.
- La ficha no se sube a Play hasta que los tres —icono, feature, capturas— hablen de la misma build.

## Consumidores auditados

Pendiente de barrer al abrirlo: `grep -rl "play-icon-512\|feature-graphic" docs/ web/` para saber
qué más referencia esos bytes (el README de release y, posiblemente, el generador de la ficha).
