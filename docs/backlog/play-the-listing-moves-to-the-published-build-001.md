# PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001 · 1.4.0 está en Play, la ficha sigue describiendo 1.3.0

**Estado:** ✅ Done (13-10-2026) · ⏳ queda el **paso del user en el Console**: pegar los 9 idiomas
desde `paste/` y marcar la **casilla de widget**

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
[`zone-an-alert-near-you-is-not-an-alert-in-your-zone-001`](zone-an-alert-near-you-is-not-an-alert-in-your-zone-001.md).

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

## ⚠️ Lo que el diseño no vio: el tope de 4000 no daba

Medido ANTES de escribir una palabra, leyendo `paste/`: **el francés entraba con 11 caracteres
libres de 4000 y el alemán con 44**. O sea que «las tres funciones en los nueve idiomas» era
imposible tal cual, y el ticket no lo sabía.

La salida no fue recortar las funciones nuevas, sino **quitar lo que se decía dos veces**:

- El bullet del historial presumía de *«cuántos aparcamientos detectó la app»*, que es exactamente
  la cifra nueva del acierto. Esa mitad **se muda** al bloque de cifras, en los nueve idiomas: el
  neto es casi cero y la frase queda donde se entiende.
- En FR y DE hubo además 11 y 10 condensaciones más (el reparto de coche, «ver nunca se paga», el
  borrado de cuenta, la cabecera de gasolineras…). **Ninguna promesa cae ni cambia una cifra**: se
  dicen más corto. ⛔ El bloque de **ubicación en segundo plano no se toca** — es texto que Play
  exige, y recortarlo para hacer sitio a marketing sería exactamente la decisión equivocada.

Margen final medido (de `paste/`, no estimado): en-US 202 · es-ES 96 · it-IT 73 · pt-PT 95 ·
**fr-FR 14** · **de-DE 9** · nl-NL 28 · pl-PL 167 · ro 121.

## 🔑 El nombre de la ficha pasa a vivir en UN sitio

El diseño decía «mover `LISTING_SOURCE`». Al abrirlo resultó que el nombre estaba cableado **dos
veces**: en el guard y en `tools/make_paste.py` (y allí, tres veces: la constante, el banner y el
mensaje de error). Mover una y olvidar la otra es un verde que no mira nada, así que el arreglo es
el invariante en un sitio: **`docs/release/play-listing/published-listing.txt`**, una línea, que
leen el generador y el guard.

Y el puntero por sí solo no basta: si se mueve y nadie regenera, `paste/` sigue siendo el de antes.
Por eso el banner de los generados **nombra la ficha** (`GENERADO … desde LISTING-v1.4.0.md`) y el
guard exige ese nombre, no sólo la palabra `GENERADO`. Falsificado: mover el puntero sin regenerar
pone rojos dos chequeos; apuntarlo a una ficha inexistente, los cuatro.

## Criterio de éxito

- ✅ `PlayListingPasteParityGuardrailTest` verde leyendo `LISTING-v1.4.0.md` (4 tests, leídos del XML
  del informe, no del exit code), y **rojo** al devolver el puntero a 1.3.0 sin regenerar `paste/`.
- Las tres funciones nombradas en los 9 idiomas de la ficha y en los 2 de la web.
- El aviso «cerca de ti» **sin nombrar** en ninguna de las dos superficies.

## Consumidores auditados

- `PlayListingPasteParityGuardrailTest` — ⛔ **hay que moverlo**, ver arriba.
- `docs/release/play-listing/README.md` — ✔ ya actualizado: su regla dice qué desbloqueó 1.4.0.
- `docs/backlog/release-1-4-0-001.md` — ✔ ya actualizado.
- `WebSiteParityGuardrailTest` — ⚠️ vigila paridad de claves ES/EN y los hechos, **no** que una
  función nueva tenga sección. Eso es lectura, no testigo.
- `LISTING.md` y `LISTING-i18n.md` — ✔ **auditados: son históricos, y se quedan.** El primero es la
  ficha del 29-08 (en-US + es-ES) y el segundo sus 7 traducciones; `LISTING-v1.3.0.md` ya decía en su
  cabecera que **sustituye a sus bloques de descripción**, y lo que sigue vivo allí es el
  *razonamiento* de cada nombre de app, que no se pega en ningún sitio. Ninguno de los dos lo lee ni
  el generador ni el guard, así que no hay nada que mover: la cadena viva es
  `published-listing.txt → LISTING-v1.4.0.md → paste/`.
- `LISTING-v1.3.0.md` — ✔ **se queda como histórico**, por lo mismo. Y es donde vive ahora la tabla
  descartada del 29-08: en 1.4.0 **no se reproduce**, porque era una segunda tabla en la misma
  sección y una vez GANÓ por ser la última que el parser encontraba.
- `web/content/{es,en}.json` — ✔ hecho, los dos idiomas. Sin tarjeta nueva: las seis de
  `features.cards` son una rejilla y una séptima la deja coja, así que cada función entró en la
  tarjeta que ya hablaba de eso (el coche localizado, la detección automática, los parkings
  públicos) y el **alto** se suma a los datos del vehículo. Reconstruida con `node web/build.mjs`.
- Enlace roto arrastrado: este doc, el README de la ficha y la ficha nueva apuntaban a
  `det-an-alert-near-you-…`, y el fichero real empieza por `zone-`. Corregido en los tres.
