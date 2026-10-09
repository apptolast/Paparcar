# DEMO-THE-SEED-MUST-REACH-TODAY-001 · la cuenta de review se queda atrás sola

**Estado:** ✅ Done · el clon más reciente cae siempre HOY, no el día en que el user condujo por última vez

## Problema

La cuenta de demo se sembró el **30-09** con 86 días de corpus. El **08-10** el user reportó que no
había datos **ni de los últimos 7 días, ni de esta semana, ni del último mes** — y la fila de
periodos del panel **abre en «7 días»** [STATS-FIRST-CHIP-ANSWERS-NOW-001], así que lo primero que
se ve estaba vacío.

La causa no era la siembra: es que **las ventanas desplazan desde las fechas del corpus de ORIGEN**.
`w0` desplaza 0 días, así que el clon más nuevo **hereda el día en que el user condujo por última
vez**. Sembrado el 30-09, el corpus de la demo terminaba el 30-09 para siempre.

⛔ **Y re-ejecutarlo no era el arreglo**, aunque el 08-10 lo pareciera: funcionó sólo porque el user
había conducido ese mismo día. Medido el 09-10 tras re-sembrar: la sesión activa salía de hoy (va
anclada a `nowMs`), pero **la última sesión CERRADA seguía siendo de ayer**, porque el corpus de
origen no tenía nada de hoy. Un corpus de demo que depende de que el dueño haya conducido esta
mañana no es un corpus de demo.

## Doctrina violada

Ninguna del código de producto. La de la propia demo, escrita en
`DEMO-A-REVIEW-ACCOUNT-MUST-LOOK-LIVED-IN-001`: la cuenta existe para que **se vea lo que la app
sabe hacer**. Una que abre en un periodo vacío enseña lo contrario, y además lo enseña al primer
golpe de vista.

## Señales / datos disponibles

- `WINDOWS` desplaza `w0: 0 d`, `w1: -30 d`, `w2: -60 d` **sobre el `timestamp` de la sesión de
  origen**, no sobre `nowMs`. `nowMs` sólo lo usan la sesión activa y los metadatos.
- El corpus de origen crece: el 30-09 llegaba al 29-09, el 08-10 al 08-10. Pero llega **hasta donde
  el user condujo**, no hasta hoy.

## Diseño

Un solo número, calculado una vez: **el desplazamiento de frescura**.

```js
const freshShiftDays = Math.max(0, Math.floor((nowMs - newestSrcMs) / MS_DAY))
```

y se suma al de cada ventana: `shiftMs: (w.shiftDays + freshShiftDays) * MS_DAY + jitter`. Las tres
ventanas se mueven juntas, así que la forma del corpus —los tres meses, los huecos, los racimos de
zona— se conserva intacta; lo único que cambia es dónde termina.

⛔ **En días ENTEROS a propósito.** Desplazar 1,2 días movería la hora de cada aparcamiento, y un
corpus que aparca a las 4 de la mañana no se parece a nadie. La hora del día se conserva: lo que se
mueve es la fecha. Es también lo que mantiene honesta la rejilla día × banda, que agrupa por hora
local.

## Criterio de éxito — medido

Plan del 09-10, con el corpus de origen terminando **ayer**:

| | |
|---|---|
| rango | 06-07 → **09-10 10:54** (hoy) |
| sesiones en los últimos 7 días | **36** |
| en los últimos 30 | **109** |
| en los últimos 90 | **272** de 282 |
| días con actividad en la última semana | **8 de 8** |

Antes del cambio, con el mismo corpus de origen: la última cerrada era de ayer y los periodos
abrían con lo que hubiera caído por azar.

## ⛔⛔ Y el desplazamiento rompió la sesión viva — lo vi leyendo Firestore, no razonando

Tras sembrar, las dos sesiones CERRADAS más nuevas quedaban **por encima** de la activa: el coche
«aparcado ahora» aparecía tercero, debajo de dos aparcamientos ya terminados. No puedes haber
cerrado una sesión después de la que sigue abierta.

La causa es el propio empujón: la activa se anclaba a `nowMs - 4 h - jitter`, y los clones empujados
hacia hoy pueden caer **después** de esa hora. El arreglo ancla la viva encima del clon cerrado más
nuevo, sin dejarla nunca en el futuro:

```js
f.timestamp = min(nowMs - 20 min, max(nowMs - 4 h - jitter, newestClosedMs + 45 min))
```

`newestClosedMs` se calcula antes del bucle y es exacto porque **`w0` no lleva jitter** (su
`shiftDays` es 0), así que es la penúltima sesión de origen empujada los mismos días enteros.

📌 El defecto no lo encontró el plan ni el dry-run: lo encontró **releer Firestore después de
escribir**. Las cuatro más recientes del plan ahora son `ACTIVA · cerrada · cerrada · cerrada`.

## Consumidores auditados

- La sesión ACTIVA — ⛔ **sí la toca**, ver arriba.
- Las **zonas** se derivan de racimos de aparcamientos, no de fechas — ✔ sin efecto.
- `createdAt`/`updatedAt` de zonas y vehículos usan `nowMs` — ✔ sin efecto.
- `demoSearchTimeMs()` toma la banda de la hora local del `timestamp` ya desplazado — ✔ correcto por
  construcción, y por eso el desplazamiento tiene que ser de días enteros.

## Lo que sigue sin resolver

El corpus de la demo es un **clon** del real: si el user no conduce en una semana, los días nuevos
se rellenan desplazando viajes viejos, no inventando nuevos. Es honesto para una demo y no pretende
ser otra cosa. Lo que ya no pasa es que la pantalla abra vacía.
