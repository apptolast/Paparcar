# ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001 · El último paso exige avisar de una plaza, y si no hay ninguna no hay forma de cerrarlo

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash)
> ⏳ Verificado en device (Redmi) durante toda la iteración; falta la vuelta desde una explicación
> que CIERRA el checklist, que es lo último que se arregló.

## Problema

Dicho por el user el 03-09, probando en el Redmi el checklist recién mergeado
(`8e4b90bc`): *«el paso 4, avisar una plaza, es obligatorio para el usuario, o sea hasta que no la
reporte no cumple el paso, y esto no debería ser porque si no ve una plaza no debería tener que
ponerla»*.

`FirstStep.FIND_SPOT` se completa por dos vías, y las dos dependen de que **exista una plaza ahí
fuera**:

| Vía | Señal | Cuándo es imposible |
|---|---|---|
| Abrir una plaza de la comunidad | `hasTouchedSpots = selection is HomeSelection.Spot` | no hay ninguna cerca — el caso normal en día uno |
| Avisar de una | latch al publicar (`CompleteFirstStep` en `reportSpot.onSuccess`) | el usuario no ve ningún hueco libre |

Un usuario sin plazas cerca y sin un hueco que avisar **no tiene forma de cerrar el paso**. Y las
únicas salidas que le quedan son malas: inventarse un reporte falso —que ensucia el mapa de los
demás con una plaza que no existe— o dejar el checklist abierto para siempre.

## Doctrina violada

Es EXACTAMENTE el defecto que `ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001` acaba de
arreglar en el paso 2, entrando por otra puerta. Allí la regla salió así: *un paso que pide un
permiso nunca bloquea*. Aquí el paso no pide un permiso, pide **un acto que depende del mundo**: que
haya una plaza libre y que el usuario la vea. Es la misma clase de exigencia.

Y choca con lo que el paso dice ser. `FIND_SPOT` enseña **que existe la mitad comunitaria** — que
las plazas que otros dejan salen en el mapa, y que si ves un hueco puedes avisar. Eso es
CONOCIMIENTO. Medirlo con una publicación es medir otra cosa.

## Señales / datos disponibles

- El paso ya tiene su propia explicación (pantalla `FirstStepExplainerScreen`), que cuenta las dos
  mitades: la plaza que alguien deja y la que tú avisas.
- `HomeIntent.CompleteFirstStep` ya existe para los pasos cuya señal es un MOMENTO y no un estado —
  es como se banca hoy el reporte.
- La distinción ya está implícita en el checklist: `MARK_PARKING` y `FORTIFY_WATCH` son ACTOS (marcar
  el coche, conceder un permiso); leerlos no los hace. `FIND_SPOT` no.

## Diseño

**Tocar el paso lo completa — pero solo el paso que se completa tocándolo.** Decisión del user:
*«con que abra la modal ya debería marcarse como bueno»*, y después, viéndolo en pantalla:
*«cualquier botón o click al contenido darán el paso por hecho»*. Así que cuenta **leer su
explicación Y pulsar su botón**, publique o no publique: quien abrió el flujo de avisar ya ha
conocido la mitad comunitaria, que es lo que el paso enseña.

La regla vive en el dominio, como una propiedad del propio paso
(`FirstStep.completesOnEngage`), no como un `if (step == FIND_SPOT)` suelto en el composable: qué clase
de paso es cada uno es una pregunta del modelo, y escrita ahí un paso futuro tiene que declararse.

- `FIND_SPOT` → **true**. Cuenta leer su explicación **y** pulsar su CTA: pulsar «Avisar de una
  plaza» mete al usuario DENTRO del flujo que el paso enseña —el pin, el mapa, el gesto entero— y eso
  se lo enseña aunque luego cancele. Sus dos vías reales siguen vivas: abrir una plaza de la
  comunidad o publicar una lo completan igual.
- `MARK_PARKING`, `UNDERSTAND_WATCH`, `FORTIFY_WATCH` → **false**. Un toque no aparca el coche ni
  enciende la detección. El primero en particular es **OBLIGATORIO** y se completa solo con un
  aparcamiento que exista de verdad, marcado desde el checklist **o desde el flujo normal de la app**
  — es lo que arma todo lo demás. Marcarlos por tocarlos sería la mentira que el checklist lleva todo
  el ticket evitando.

**El mapa completo tras esta tanda** (estructura confirmada por el user):

| Paso | Se completa con | Salida |
|---|---|---|
| 1 · Marcar aparcamiento | un aparcamiento REAL, venga de donde venga | ninguna — obligatorio |
| 2 · La vigilancia | la detección encendida | «Aún no» |
| 2.5 · Reforzarla | que no quede nada que reforzar | «Aún no» |
| 3 · La comunidad | leerlo, pulsar su CTA, o abrir/publicar una plaza | el propio «Cómo funciona» |

**Y la explicación necesitaba una PUERTA visible.** La fila ya era tocable, pero al lado de un CTA
verde relleno nadie toca prosa: *«el usuario tiende a darle al botón avisar, no al contenido»*. Cada
paso gana un glifo de ayuda en su `trailing` — un GLIFO y no un tercer botón, porque ese slot es para
afijos compactos y una palabra ahí competiría con la acción del paso [UI-LIST-ITEM-001].

## Criterio de éxito

- Abrir la explicación del último paso —o pulsar su botón— lo marca hecho, y el checklist puede
  cerrarse sin haber publicado nada.
- La explicación tiene una entrada VISIBLE en cada paso, no solo un área tocable invisible.
- Ningún otro paso se completa por tocarlo — con test, porque es justo lo que alguien aflojaría al
  añadir el quinto paso.
- Tests verdes y los dos flavors compilando.

## Consumidores auditados

- `onOpenStep` en `homeSheetItems` — el único sitio que abre una explicación desde el checklist.
- Las otras dos vías de `FIND_SPOT` (`hasTouchedSpots` y el latch del reporte) no se tocan: se suma
  una tercera, no se sustituyen.
- El replay desde Ajustes limpia `done` y `deferred`, así que un checklist reproducido vuelve a pedir
  el paso — leerlo otra vez vuelve a cerrarlo, que es lo coherente.
