# ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001 · el foco del tutorial contesta hasta que actúas

**Estado:** ✅ Done · rama `fix/ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001` · issue #82

## Problema
Reporte del user, literal:
> «Cuando vamos al paso 2 del tutorial de agregar zona, se ilumina con un recuadro verde pero poco
> tiempo y no capta la atención del usuario, y no debería de cerrarse modal, con que llame más la
> atención y que capte la atención de user vale»

Tres cosas: la marca no se ve, dura poco, y la hoja se cierra de golpe.

## Doctrina violada
**Un guía que contesta «¿dónde está?» durante seis segundos no contesta.** El foco sólo se enciende
cuando el usuario lo PIDE desde el checklist, así que ponerle un reloj es la app decidiendo cuánto
tiempo necesita mirar.

## Decisión del user (21-09)
Preguntado explícitamente, con las tres salidas sobre la mesa:
1. **La hoja sigue bajando** —es la única forma de que el chip exista— **pero se anuncia antes**.
2. **El foco no caduca**: se apaga al actuar.

## Diseño

### 1 · La marca late en TAMAÑO, no en color
Era un contorno de 2 dp cuyo alpha iba de 0,25 a 1 cada 700 ms. Sobre un chip de cristal que flota
encima de un mapa lleno de marcadores verdes, eso no se ve: **un borde que aparece y desaparece
compite con todo lo que hay debajo**. Algo que se MUEVE no compite con nada, porque el movimiento es
lo único que un mapa quieto no tiene.

Ahora: respiro de escala (1 → **1,06** en 1.100 ms) y contorno **fijo, opaco y de 3 dp**.
*El movimiento llama; el borde señala.* Antes el borde intentaba las dos cosas y no hacía ninguna.

⚠️ `scale` es un `graphicsLayer`: no cambia la medida, así que el chip marcado **no empuja a sus
vecinos**. El 6 % son 2 dp sobre los 34 de alto — se ve de sobra y no invade el aire de 8 dp de la
fila. Lo único que podría recortarlo es un padre que clipee, y el header flotante no lo hace.
⚠️ 1.100 ms, no 700: a esa velocidad un cambio de TAMAÑO se lee como un temblor, no como un respiro.

### 2 · Sin reloj
`FIRST_STEP_GUIDE_MS = 6_000L` **se borra**. El foco se apaga:

| Condición | Cómo |
|---|---|
| pulsa el chip | `EnterAddZoneMode` saca a Home de `Browse` → el guard de modo que ya existía |
| completa el paso | el paso deja de ser `firstSteps.current` |
| lo aplaza («ahora no») | **la misma condición** |
| se va a otra cosa | el guard de modo |

⛔ Completar y aplazar son **una sola condición**, no dos: la pregunta es la misma —*«¿sigue siendo
esto lo que te pido?»*— y separarlas dejaría señalado un paso aplazado, que es discutirle al usuario
un «no» que acaba de dar.

⛔ No puede quedarse encendido para siempre **porque sólo se enciende cuando el usuario lo pide**.

### 3 · El movimiento se anuncia antes de ocurrir
Al pulsar el paso, su línea deja de explicar el paso y dice dónde mirar; **900 ms después** baja la
hoja. Sin ese respiro la hoja daba un salto sin causa visible, y leer el motivo DESPUÉS del
movimiento es leerlo tarde.

⛔ Se reutiliza la línea de subtítulo que el paso ya tenía en vez de añadir una segunda: un paso que
crece justo antes de esconderse es ruido.

## Criterio de éxito
Es un cambio de animación y de ciclo de vida: **no hay testigo unitario que lo pruebe** — el repo no
tiene tests de Compose UI, y un assert sobre `GUIDE_SCALE_MAX` sólo probaría que la constante vale lo
que vale. Se verifica en la **galería de estados** («Paso 2 · señalando el chip de zona») y en mano.
Dicho aquí para no fingir cobertura que no existe.

Suite completa verde con `--rerun-tasks`. Strings en los 9 locales.

## Consumidores auditados
`grep` de `papGuideHighlight` — **4** sitios (chip de zona, pestañas de carril, tarjeta de avisar,
carril de plazas). Los cuatro heredan la marca nueva, que es lo que se quiere: era **una** marca mal
calibrada, no cuatro decisiones. `FIRST_STEP_GUIDE_MS` no tenía más lectores que su propio `delay`.
