# Prompt para arrancar ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001

> Copiar de la línea de abajo hasta el final y pegarlo tal cual en una sesión nueva.
> Está escrito para que no haga falta contexto previo: todo lo que hay que saber está dentro.

---

Implementa `ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001` en Paparcar. La ficha está en
`docs/backlog/onb-the-map-answers-more-than-spots-001.md` — léela entera antes de tocar nada, y lee
también `docs/backlog/ui-one-skeleton-one-emphasis-one-green-001.md`, que es el sistema visual con el
que tiene que nacer esto.

**Empieza invocando la skill `nuevo-ticket`** (worktree aislado + rama + doc de backlog en tiempo
real). ⛔ No commitees ni mergees sin permiso explícito.

## El problema, en una frase

La app responde ya a TRES preguntas —¿hay una plaza libre?, ¿dónde aparco pagando?, ¿dónde reposto y
a cuánto?— y el onboarding solo presenta la primera. En una zona sin usuarios, el día 1 el mapa de un
recién llegado está vacío de plazas mientras las dos capas con dato mundial (parkings públicos,
gasolineras) existen, están a un toque y nadie se lo ha dicho. F6 se justificó como *"lo que evita el
mapa vacío el primer día"*, y ese argumento solo se cobra si el usuario sabe que puede preguntarlo.

## Lo que hay hoy

- `OnboardingScreen` — 2 páginas: bienvenida + "cómo funciona" con tres líneas (flecha que se mueve,
  coche que descansa, megáfono que avisa). Las tres hablan de detección y comunidad.
- `FirstStepsCard` en Home — 4 pasos (`MARK_PARKING`, `UNDERSTAND_WATCH`, `FORTIFY_WATCH`,
  `FIND_SPOT`), otra vez todos de detección y comunidad. Modelo en
  `domain/onboarding/FirstSteps.kt`.
- El sheet de Home ya tiene un **carril con pestañas**: Plazas · Parkings · Gasolineras
  (`HomeNearbyLensRow`, `HomeNearbyLens`). Ese gesto ES "pregúntale al mapa otra cosa", y es donde
  vive la oportunidad de contarlo.

## Qué construir

**A · En el sitio, la primera vez que se abre cada carril complementario (obligatorio).**
La primera visita a *Parkings* y a *Gasolineras* deja UNA línea que dice de dónde sale el dato y qué
NO promete: precio oficial con su hora; ocupación solo donde el municipio la publica. Cero páginas
nuevas, se cuenta cuando el usuario ha demostrado interés, y es el mismo principio con el que se
vendió la detección en vez de exigirla (`ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001`).

**B · Un cuarto paso de conocimiento en el checklist.**
`FirstStep.EXPLORE_NEARBY`, con `completesOnEngage = true` (es CONOCIMIENTO, no un acto), y su CTA
pone la lente en el complemento que tenga dato en esa zona. ⛔ Decide y justifica en el doc si
sustituye a `FIND_SPOT` o si son cinco pasos — cinco es un checklist largo y el que se añade es el
menos importante de los cinco.

**C · Ilustraciones, solo si A y B las piden.** Ver reglas abajo.

## Reglas que NO puedes romper

- ⛔ **Nada de una página 3 de catálogo.** `ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001` retiró la
  tercera página porque era una lección antes de dejar entrar. Contar antes de que haga falta es el
  error de fondo, y añadir una página de "mira todo lo que hago" lo repite con otro tema.
- ⛔ **Un paso nuevo nunca bloquea** (`ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001`): necesita
  su "ahora no".
- ⛔ **Un paso de conocimiento se cierra al leerlo/abrirlo**; uno de acto, solo haciéndolo
  (`ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001`). No se le puede exigir a nadie repostar.
- ⛔ **Vocabulario** (`COPY-SPOT-IS-NOT-A-PARKING-001`): *plaza* (comunidad) ≠ *aparcamiento* (tu
  sesión) ≠ *parking público / car park* (instalación de un tercero). En inglés el público es **car
  park**, nunca "parking" — esa palabra ya es tu sesión.
- ⛔ **Copy sin mecánica interna ni jerga**: causa + consecuencia + remedio.
- ⛔ **Strings en los 9 locales en la MISMA tarea** (`values` EN base, `-es -it -pt -fr -de -nl -pl
  -ro`). Faltar NO crashea: sale en inglés en silencio. Lo vigila `LocaleParityGuardrailTest`, que
  también caza keys muertas.
- ⛔ **Apóstrofos crudos** (`'`, nunca `\'`) y **`%` literal se escribe `%`, nunca `%%`**: Compose
  Resources no desescapa lo primero y no formatea lo segundo.
- ⛔ **Dev Catalog al día en la misma tarea**: pantalla nueva → `ScreenGroup` en
  `StateGalleryScreen.kt`; estado nuevo → variante; condición de routing → `MockScenario`.
  Y **la variante tiene que EJERCITAR su caso** — una variante con los valores por defecto que no
  reproduce el estado es peor que no tenerla (`DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001`).

## Si hacen falta ilustraciones

- **Nivel 3** del sistema de iconos: vector propio, multicolor, **NO se tiñe**; el estado se escribe,
  no se colorea.
- SVG compatible con VectorDrawable (**solo `path`**) → `composeResources/drawable/`, **con variante
  `_dark`**. Si necesita `stroke-dasharray`, nested-svg, filtros o texto → **Compose Canvas** en
  commonMain: VectorDrawable no soporta trazos discontinuos.
- Hay familia propia ya establecida y conviene no estrenar otra: `PaparcarIcons` (marcas in-house
  rellenas, 24×24, `EvenOdd`: `SpotBayMark`, `CarParkMark`, `FuelPumpMark`, `SpotPin`), los
  pictogramas isométricos de coche (`vehicleIconPainter`, con paleta y borde blanco, sensibles al
  tema) y `illus_onb_*` para los héroes.
- **Presupuesto de verde**: como mucho DOS por pantalla — la acción primaria y la identidad de un
  coche vigilado. Todo lo demás, neutro. El ámbar es solo caducidad.
- Un solo esqueleto de fila: `PapListItem` dentro de `PapOutlinedCard` con `PapIconTile` (neutro por
  defecto). Cuatro radios: `row 12` · `card 16` · `sheet 20` · `chip 999`.

## Verificación (toda, antes de decir que está hecho)

```bash
./gradlew :shared:testDebugUnitTest --rerun-tasks   # ⛔ --rerun-tasks o los guards Konsist salen UP-TO-DATE sin ejecutarse
./gradlew :app:assembleProdDebug :app:assembleMockDebug
```
Y **verlo en device**: `assembleProdDebug` + `adb install -r`, abrir el sheet, entrar en Parkings y en
Gasolineras por primera vez. En este proyecto las cosas se deciden en mano, no en la galería — el
último ticket encontró así dos fallos que la galería no enseñaba.

## Lo que tienes que preguntarme a mí antes de dar por cerrado

1. ¿A + B, o solo A de momento?
2. Si B: ¿`EXPLORE_NEARBY` sustituye a `FIND_SPOT` o son cinco pasos?
3. Las ilustraciones nuevas, antes de integrarlas.
