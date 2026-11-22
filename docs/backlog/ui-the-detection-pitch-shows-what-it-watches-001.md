# UI-THE-DETECTION-PITCH-SHOWS-WHAT-IT-WATCHES-001 · La invitación a la detección enseña lo que vigila

**Estado:** ✅ Done.

## Problema

La invitación a la detección automática —la pantalla que vende la función estrella del producto—
abría con `Icons.Rounded.Sensors` dentro del plato circular: el glifo genérico de "hay un sensor".
Justo antes, en la misma cadena de primera entrada, la bienvenida de Fundadores abre con una ESCENA
([UI-THE-FOUNDERS-GIFT-EARNS-ITS-VOLUME-001]). Dos superficies seguidas, una con escena y otra con
un pictograma de sistema, y la que se queda corta es precisamente la que tiene que convencer.

## Doctrina

Regla de iconos de `CLAUDE.md`: *plumbing de UI → Material; concepto de Paparcar → vector propio*.
Que la detección recuerde dónde aparcas no es plumbing: es **el** concepto. Nivel 3 —ilustración,
multicolor, trae su color, no se tiñe— y `PapAlertDialog.heroIllustration`, el slot que sustituye
el plato en vez de meter algo dentro de él.

## Diseño

Render 3D del user: el coche aparcado en su plaza, la zona que la app vigila marcada con línea
discontinua en el suelo, y el marcador emitiendo sobre el techo. Dice las tres cosas sin una
palabra —requisito, porque una ilustración con texto no viaja a nueve idiomas—: **qué se vigila**
(el coche), **dónde** (la plaza pintada) y **quién** (el marcador de la app).

### ⛔ Es el único ráster del set, y no es pereza

Sus cinco hermanas se dibujan en Compose Canvas porque son planas y usan trazos discontinuos que un
VectorDrawable no soporta. Ésta es un render con luz propia, reflejos y degradados de superficie, y
**eso no se dibuja con paths**. Se intentó dos veces antes de aceptarlo, extruyendo un coche
low-poly sobre los mismos ejes del regalo:

| intento | qué se vio en el Pixel 8 |
|---|---|
| cuerpo + cabina extruidos | **tarta de pisos** — sin ruedas, sin morro, cabina casi tan ancha como el cuerpo |
| morro biselado, carrocería flotando, ruedas asomando | **máquina industrial** — mejor, pero seguía sin ser un coche |

La causa no era el detalle que faltaba: **a este tamaño un coche se reconoce por su PERFIL** —capó,
parabrisas tumbado, arcos de rueda— y eso es CURVATURA, que un poliedro no da a ningún precio. Una
caja de regalo sí es una caja; un coche no. Un tercer intento, ya de perfil y con curvas, sí leía
como coche — y es el que el render del user sustituye con mejor acabado.

### ⛔ Un solo asset para los dos temas

Sus hermanas llevan variante `_dark` porque son dibujos de línea que se apagan contra el fondo.
Ésta trae su propio suelo —la losa de asfalto— y su propia luz: es un OBJETO sobre la superficie del
diálogo, no un dibujo sobre ella. Verificado en claro y en oscuro.

WebP y no PNG: **56 KB** frente a los 272 KB del maestro, sin pérdida visible a este tamaño.

### El héroe de un diálogo es más grande que el de un estado vacío

El user lo vio antes de que lo midiera nadie: *«queda como un poco aislado y separado del resto de
contenido»*. En un diálogo la ilustración comparte ancho con un título, un cuerpo de texto y un
botón que ocupan TODO el ancho útil, y a 140×120 dp quedaba flotando en el centro — se leía como una
viñeta pegada encima, no como la cabecera del bloque. En un estado vacío no pasa, porque allí la
ilustración no compite con nada.

`HERO_ILLUSTRATION_W/H` = **184 × 156 dp**, la proporción del render (880×747), y la comparten las
**dos** heroínas de diálogo: el regalo se dibuja en un viewBox 140×120 cuyo transform escala
preservando aspecto, así que entra en la misma caja sin deformarse. ⛔ Se agranda el LIENZO, nunca
los márgenes del diálogo.

## Consumidores auditados

- ✅ `HomeEntryPromptHost` — la rama `DETECTION` pasa a `heroIllustration`. `icon` se queda como
  respaldo SEMÁNTICO, igual que en la bienvenida del regalo: la ilustración es lo que se dibuja, el
  glifo es lo que la superficie significa.
- ✅ `FoundersGiftIllustration` — entra en la caja de héroe compartida.
- ✅ Dev Catalog y previews — el grupo «Home · inicio secuencial» y `HomeEntryPromptPreviews` montan
  el host real, así que enseñan la ilustración nueva sin tocar nada.
- ✅ Las cuatro ilustraciones de estado vacío mantienen su 140×120: no comparten el problema.
- ✅ Nueve locales: no hay texto nuevo. La ilustración no lleva palabras, a propósito.

## Validación

- `./gradlew testAll --rerun-tasks`: **BUILD SUCCESSFUL**, 95/95 tareas ejecutadas, 0 fallos.
- Pixel 8 (API 37), APK mock con sha256 verificado en device, preset «Primer Home · cadena
  completa»: la invitación y la bienvenida vistas en **tema oscuro y tema claro**.

## ⚠️ La trampa que me comí, por tercera vez en este repo

Al quitar un elemento de la ilustración anclé el parche en `// ── La plaza ──`, que existe **también**
dentro de `FoundersGiftIllustration`: el corte se llevó por delante el dibujo del regalo entero. Es
exactamente lo que su propio ticket dejó escrito —*anclar por el SÍMBOLO, no por una línea de dibujo
que puede repetirse*— y aun así volvió a pasar. Lo cazó el compilador, `git checkout --` lo deshizo
y se re-aplicó con anclas únicas. La regla no necesita reescribirse; necesita cumplirse.
