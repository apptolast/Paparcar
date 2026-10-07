# BRAND-THE-DISCARDED-DISC-MARK-STILL-SHIPS-IN-THE-REPO-001 · la marca descartada seguía versionada

**Estado:** ✅ Done · rama `chore/BRAND-THE-DISCARDED-DISC-MARK-STILL-SHIPS-IN-THE-REPO-001-drop-disc`
· worktree `../Paparcar-drop-disc`

## Problema

`logos/paparcar_disc_mark.svg` y `logos/paparcar_disc_mark.xml` —la **marca de disco**: un disco
verde con una ranura y un pie— entraron en master con `6970b4065` y ahí siguen, aunque el dibujo
es una propuesta **descartada**. [BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001] lo cerró con todas las
letras (*«⛔⛔ FUERA EL DISCO»*) y CLAUDE.md lo recoge: *«El logo NO lleva disco»*.

Lo destapó el vídeo del Shipaton, no una auditoría: su plano de cierre dibujaba esos ficheros, y el
user lo vio en el montaje — *«un logo que no tiene nada que ver»*. El plano ya se rehizo contra el
maestro de verdad (`docs/branding/mark/paparcar-mark-compas-b.svg`); lo que queda es la causa de que
fuera posible elegirlo, que es que el dibujo muerto siga estando ahí para que alguien lo abra.

## Doctrina violada

⛔ **El logo se GENERA, no se copia** — fuente única `docs/branding/master/`. Estos dos ficheros son
justo lo contrario: una copia suelta, fuera del exportador, que ninguna superficie consume y que
ningún guard vigila. Mientras exista, *«la marca»* tiene dos respuestas en el repo, y la equivocada
es la que sale antes por nombre de carpeta (`logos/` frente a `docs/branding/mark/`).

## Señales / datos disponibles

Barrido completo sobre el árbol, no sobre el log:

| Búsqueda | Resultado |
|---|---|
| `git grep "paparcar_disc_mark\|disc_mark\|disc-mark"` | **0** aciertos |
| `git grep -l "M49.11,50.81\|M52.55,38.87\|M17.36,66.44"` (su geometría) | solo los **dos** ficheros |
| `git grep "logos/"` | **0** aciertos |
| `git ls-files logos/` | **solo** esos dos |

O sea: cero consumidores, y la carpeta entera se va con ellos.

## Diseño

No hay sistema que arreglar: el sistema ya existe y es `docs/branding/master/` → `export_surfaces.py`.
Lo que sobra es el residuo que lo contradice. **Se borra, no se archiva** — guardarlo «por si acaso»
es guardar un camino de vuelta al dibujo que se descartó, que es exactamente lo que acaba de pasar.

## Criterio de éxito

- `git ls-files logos/` vacío; la carpeta deja de existir.
- La única marca del repo es la que sale de `build_marks.py` / `export_surfaces.py`.
- Nada deja de compilar ni de resolverse: no había consumidores (ver la tabla).

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| Código Kotlin / recursos Android | **cerrado** — ni una referencia |
| `web/` y `hosting/public/` | **cerrado** — hornean el logo desde `web/lib/mark.generated.mjs` |
| `docs/branding/` | **cerrado** — su maestro es `mark/paparcar-mark-compas-b.svg` |
| `docs/release/play-listing/` | **cerrado** — sus dos HTML llevan el path a mano, pero el de la marca nueva |
| Vídeo del Shipaton (fuera del repo) | **cubierto** — `proyecto/logo.py` ya lee el maestro de `docs/branding/mark/`, con verificación de firma para que el disco no pueda volver a colarse |

## Follow-up detectado (fuera de alcance)

`CLAUDE.md:181` dice que el lienzo de la marca es **`256 × 248`**. El maestro
(`docs/branding/mark/paparcar-mark-compas-b.svg`) declara `viewBox="0 0 244 236"` y
`web/lib/mark.generated.mjs` publica `MARK_W = 244` / `MARK_H = 236`. El número del doc no casa con
ninguna de las dos fuentes — y está en la línea que precisamente advierte de no asumir medidas.
