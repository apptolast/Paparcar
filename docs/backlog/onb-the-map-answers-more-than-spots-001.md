# ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001 · el onboarding vende media app: el mapa ya responde tres preguntas y solo se presenta una

**Estado:** 🟡 Propuesta — sin rama, sin worktree, sin código. Abierta desde
`HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001`, que es el ticket que hace visibles los
complementos y por tanto el que deja este desfase a la vista.

## Problema

Paparcar entró en producción respondiendo UNA pregunta: *¿hay una plaza libre que alguien acaba de
dejar?* Con F5 y F6 en master responde tres, y las otras dos no dependen de la comunidad:

| pregunta | fuente | ¿la presenta el onboarding? |
|---|---|---|
| ¿hay una plaza libre cerca? | comunidad (usuarios) | sí — es lo único que presenta |
| ¿dónde aparco pagando? (parking público) | OSM + adaptadores municipales | **no** |
| ¿dónde reposto y a cuánto? | Ministerio / OpenVan / prix-carburants | **no** |

Lo que el usuario nuevo ve hoy:

- `OnboardingScreen` — **2 páginas**: bienvenida + "cómo funciona" con tres líneas (la flecha que
  se mueve, el coche que descansa, el megáfono que avisa). Las tres hablan de detección y comunidad.
- `FirstStepsCard` en Home — **4 pasos** (`MARK_PARKING`, `UNDERSTAND_WATCH`, `FORTIFY_WATCH`,
  `FIND_SPOT`), otra vez todos de detección y comunidad.

Consecuencia medible: el día 1, en una zona sin usuarios, el mapa de un recién llegado está vacío
de plazas — y las dos capas que SÍ tienen dato mundial (parkings, gasolineras) existen, están a un
toque y nadie le ha dicho que están ahí. F6 se justificó en el roadmap precisamente como *"lo que
evita el mapa vacío el primer día"*, y ese argumento solo se cobra si el usuario sabe que puede
preguntarlo.

## Doctrina en juego

- ⛔ `ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001` retiró la 3.ª página del onboarding porque era
  una **lección de seguridad** antes de dejar entrar. La lección se fue; el límite de 2 páginas no
  es dogma, pero **añadir una página de catálogo repetiría el error de fondo**: contar antes de que
  haga falta. Cualquier diseño aquí tiene que pasar ese filtro.
- ⛔ `ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001` — nada de lo que se añada puede bloquear
  el checklist, y todo paso nuevo necesita su "ahora no".
- ⛔ `ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001` — un paso de CONOCIMIENTO se cierra al
  leerlo/abrirlo (`completesOnEngage`); uno de ACTO, solo haciéndolo. Los complementos son
  conocimiento: no se le puede exigir a nadie repostar para cerrar un paso.
- ⛔ `COPY-SPOT-IS-NOT-A-PARKING-001` (+ la fila nueva de `PARKINGS-ON-THE-MAP-001`): plaza ≠
  aparcamiento ≠ parking público / car park. El copy nuevo tiene que nombrar las tres cosas
  distinto, en los 9 locales.
- ⛔ Iconos: los tres glifos de la página 2 se eligieron por el significado que ya tienen DENTRO de
  la app (`UI-ONBOARDING-STEP-ICONS-SAY-WHAT-THE-APP-SAYS-001`). Un glifo nuevo hereda esa regla —
  y ⛔ ojo con la "P": ya está adjudicada al parking público como POI, y por eso se echó del paso
  del aparcamiento propio.

## Opciones

**A · En el sitio, la primera vez que se abre el carril (recomendada).**
La lente de `HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001` ya es el gesto de "pregúntale al
mapa otra cosa". La primera visita a Gasolineras y a Parkings deja una línea que dice de dónde sale
el dato y qué NO promete (precio oficial con su hora; ocupación solo donde el municipio la publica).
Cero páginas nuevas, se cuenta exactamente cuando el usuario ha demostrado interés, y es el mismo
principio con el que se vendió la detección en vez de exigirla.
· Coste: bajo. · Riesgo: quien nunca toque un chip no se enterará — se cubre con B.

**B · Un cuarto paso de conocimiento en el checklist: "el mapa responde tres preguntas".**
`FirstStep.EXPLORE_NEARBY`, `completesOnEngage = true`, CTA que pone la lente en el complemento con
dato en esa zona. Es el hueco natural: el checklist ya es el sitio donde la app se explica sola, y
`FIND_SPOT` ya tiene dos puertas según lo que la comunidad pueda ofrecer.
· Coste: medio (paso + explainer + progreso + tests + galería + ×9 locales).
· ⚠️ Decidir si sustituye o acompaña a `FIND_SPOT`: cinco pasos es un checklist largo, y el que se
añade es el menos importante de los cinco.

**C · Reescribir la página 2 para que nombre el mapa entero** (cuarta línea, o cambiar el megáfono
por "y además: parkings y gasolineras con precio").
· Coste: bajo (copy + glifo + ×9). · Riesgo: es la página que menos se lee, y la que ya intentó
contar todo. Sirve como refuerzo de A, nunca como la solución sola.

**D · Página nueva de catálogo.** ⛔ Descartada de entrada: es la página 3 otra vez, con otro tema.

**Recomendación: A + B**, y C solo si al medir en device la página 2 se queda claramente corta.
A entra con el ticket de la lente si el user quiere; B es su propio ticket porque toca el modelo del
checklist (`FirstSteps.kt`, progreso persistido, explainer, galería).

## Criterio de éxito

- Un usuario nuevo en una zona sin plazas de comunidad sabe, sin leer nada más, que el mapa puede
  responderle dónde hay parking y a cuánto está el litro.
- Ningún paso nuevo puede bloquear el checklist ni exigir un acto (repostar, aparcar de pago).
- El copy nombra plaza / aparcamiento / parking público con las tres palabras distintas, ×9 locales.

## Pendiente de decidir por el user

1. ¿A + B, o solo A de momento?
2. Si B: ¿`EXPLORE_NEARBY` sustituye a `FIND_SPOT` o son cinco pasos?
