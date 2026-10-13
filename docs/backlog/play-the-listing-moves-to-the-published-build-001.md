# PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001 · 1.4.0 está en Play, la ficha sigue describiendo 1.3.0

**Estado:** 📋 Abierto (13-10-2026) · sin empezar · **desbloqueado** por la publicación de 1.4.0

## Problema

`1.4.0` está en Google Play desde el **13-10-2026**. La ficha sigue siendo `LISTING-v1.3.0.md`, y la
regla del propio proyecto es **⛔ la ficha describe el build PUBLICADO, no master**
(`docs/release/play-listing/README.md`). Hasta hoy esa regla *prohibía* nombrar tres funciones que
llevan en master desde el 19–21 de septiembre; desde hoy la misma regla **obliga** a nombrarlas.

Las tres que entran, con la fecha en que aterrizaron:

- **Widget del launcher** para contestar un viaje sin abrir la app (`ParkedWidget.kt`, 21-09).
  ⚠️ Play tiene **casilla propia para widgets**: además del copy hay que marcarla.
- **Gálibo del coche** contra el del parking — un alto desconocido nunca es un «cabes» (21-09).
- **Acierto de la detección**, en porcentaje y en anillo (19-09).

⛔ **La cuarta NO entra, y no es por la versión.** El aviso in-app con el mapa delante solo salta
hoy para plazas **dentro de una zona guardada**, que es el aviso de Plus, no el de «cerca de ti» que
se pidió. Anunciarlo sería vender lo que la app no hace todavía — ver
[`det-an-alert-near-you-is-not-an-alert-in-your-zone-001`](det-an-alert-near-you-is-not-an-alert-in-your-zone-001.md).

## Doctrina violada

Ninguna, **todavía**: la ficha era correcta ayer. Lo que hay es una regla del proyecto que acaba de
cambiar de lado, y una ficha que se queda atrás **sola** si nadie la mueve — exactamente la forma de
`PLAY-A-LISTING-MUST-MATCH-THE-BUILD-IN-PRODUCTION-001`, que nació porque `paste/` se quedó siendo
la ficha del 29-08 durante 29 días.

## Señales / datos disponibles

- `docs/release/play-listing/LISTING-v1.3.0.md` — la **fuente única** de los nueve idiomas.
- ⛔ Su nombre está **cableado en un guard**:
  `PlayListingPasteParityGuardrailTest.LISTING_SOURCE = "$LISTING_DIR/LISTING-v1.3.0.md"`. Crear el
  fichero de 1.4.0 sin mover la constante deja el guard verificando una ficha **retirada**, que es
  un verde que no mira nada.
- `paste/play-<locale>.txt` + `paste/play-listing-import.md` se **generan**:
  `python docs/release/play-listing/tools/make_paste.py`.
- La **web** no nombra ninguna de las tres: `web/content/{es,en}.json`, los dos idiomas, en la misma
  tarea — es la regla de *la web es un consumidor más de la verdad del producto*.

## Diseño

1. `LISTING-v1.4.0.md` desde el de 1.3.0, con las tres funciones en los **nueve** idiomas. El copy
   respeta el vocabulario: **plaza** es de la comunidad, **aparcamiento** es tuyo, y un parking
   público es un **car park** en inglés.
2. Mover `LISTING_SOURCE` en el guard, y pasarlo con `--rerun-tasks` (no toca bytecode).
3. Regenerar `paste/` y comprobar que el guard sigue verde por la ficha NUEVA.
4. `web/content/{es,en}.json` + `node web/build.mjs --check`.
5. Marcar la **casilla de widget** en el Console (paso del user).

⛔ No se tocan a mano los ficheros de `paste/`: son salida.

## Criterio de éxito

- `PlayListingPasteParityGuardrailTest` verde leyendo `LISTING-v1.4.0.md`, y **rojo** si se le deja
  apuntando al de 1.3.0 mientras existe el nuevo.
- Las tres funciones nombradas en los 9 idiomas de la ficha y en los 2 de la web.
- El aviso «cerca de ti» **sin nombrar** en ninguna de las dos superficies.

## Consumidores auditados

- `PlayListingPasteParityGuardrailTest` — ⛔ **hay que moverlo**, ver arriba.
- `docs/release/play-listing/README.md` — ✔ ya actualizado: su regla dice qué desbloqueó 1.4.0.
- `docs/backlog/release-1-4-0-001.md` — ✔ ya actualizado.
- `WebSiteParityGuardrailTest` — ⚠️ vigila paridad de claves ES/EN y los hechos, **no** que una
  función nueva tenga sección. Eso es lectura, no testigo.
- `LISTING.md` y `LISTING-i18n.md` — ⏳ sin auditar aquí: hay que ver si son históricos o fuentes
  vivas antes de tocarlos.
