# PLAY-THE-LISTING-MOVES-TO-1-6-0-001 · la ficha 1.6.0 (la que nombra a Meta) publicada, y el corte ya sabe subirla y comprobarla

**Estado:** ✅ Done (10-11-2026)

## Problema
Con la 1.6.0 (vc17) en producción, la ficha de Play seguía siendo la **1.5.0** en los 9 idiomas:
la del 09-11 (texto reescrito y capturas de Madrid, subida con Codex), que dice «Sin anuncios» a
secas y no nombra a Meta. El Data Safety y la política ya declaran que la app comparte con Meta.

Y nadie lo veía: `cut.py --phases verify` decía **«la tienda y el repo cuadran»** porque sólo
comparaba título y descripción corta, que no cambian de 1.5.0 a 1.6.0. Todo el cambio vive en la
completa.

## Doctrina violada
La ficha es consumidora de la verdad del producto y describe el build PUBLICADO
[DOCS-THE-STORE-LISTING-IS-A-CONSUMER-TOO-001]. Y «un verde que no mira nada»: el guard de la
tienda no miraba el campo donde estaba el cambio.

## Diseño
- `published-listing.txt` → `LISTING-v1.6.0.md` y `paste/` regenerado con `make_paste.py`.
  Contra lo publicado, el cambio es SÓLO el bloque de privacidad (fuera «Estadísticas…» y
  «Sin anuncios», dentro «Sin anuncios en la app. Sólo si lo permites, Meta…»), más frases
  condensadas en it/fr/de/nl para el tope de 4000. Título, corta y capturas, iguales.
- `check_against_store` compara también `fullDescription`, y enseña la **primera línea** que
  difiere en `ascii()`: volcar 4000 caracteres no dice dónde está el cambio, y la «ł» polaca
  tumbaba el script en la consola cp1252.
- Fase nueva `listing` (manual, como `upload`): sube título y descripciones desde `paste/`, sólo
  de los idiomas que difieren, valida, commitea y **relee** lo publicado con el mismo
  `check_against_store`. No toca capturas.
- ⛔ `play_api.commit_edit` pasa `changesInReviewBehavior=ERROR_IF_IN_REVIEW` **siempre**. Sin
  él, la API hace `CANCEL_IN_REVIEW_AND_SUBMIT`: subir la ficha con la 1.6.0 en revisión le habría
  reiniciado la revisión. Vale también para `upload`, que tenía el mismo riesgo.
- `store_listing.mjs` (Codex) no se usó: exige producción en vc16 y resube las 72 capturas.

## Resultado medido
| Paso | Resultado |
|---|---|
| `verify` nuevo ANTES de subir | **falla** en los 9 idiomas, `<locale>/full`, con la línea exacta |
| `--phases listing` | 9 idiomas preparados (de 3807 a 3995/4000), validado, commit aceptado, releído = repo |
| `verify` DESPUÉS | «la tienda y el repo cuadran», ahora comparando la completa |

## Consumidores auditados
| Consumidor | Estado |
|---|---|
| `PlayListingPasteParityGuardrailTest` | lee el puntero: ahora vigila 1.6.0 |
| `cut.py` `verify` | cerrado: compara la completa |
| `cut.py` `upload` | cubierto por `commit_edit` con `ERROR_IF_IN_REVIEW` |
| `store_listing.mjs` | exento: su baseline es vc16 a propósito; para capturas, no para texto |
| `docs/release/play-listing/README.md` | sección nueva «Subir sólo el TEXTO» |
