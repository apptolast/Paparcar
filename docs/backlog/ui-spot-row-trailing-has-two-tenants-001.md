# UI-SPOT-ROW-TRAILING-HAS-TWO-TENANTS-001 · La fila de plaza trunca su meta-line en los 9 idiomas

**Estado:** 🔴 Abierto, sin implementar · detectado el 29-08 barriendo idiomas en el Redmi

## Problema

La meta-line de la fila de plaza **se corta en los nueve idiomas**, español incluido:

| | |
|---|---|
| EN | `LIKELY FREE · 1 min drive · 2 en r…` |
| ES | `PROBABLE · 1 min en coche ·` |
| DE | `WAHRSCHEINLICH · 1 Min. mit d…` |
| NL | `WAARSCHIJNLIJK · 1 min rij…` |
| PL | `PRAWDOPODOBNE · 1 min sa…` |

(IT, PT, FR y RO igual.) Sólo se salvaba ES en una captura de un build **anterior**, lo que casi me
hace archivarlo como un problema de traducciones largas. Recapturado con el build actual, ES trunca
también: **no es un problema de idioma, es de layout.**

## Causa: dos tickets que no se vieron

Dos cambios entraron en master el mismo día y se pisan en el mismo hueco:

1. **`UI-TYPE-TWO-VOICES-ONE-ROW-001`** subió la distancia (`179 m`) a la línea del nombre,
   *liberando el trailing* — que es lo que evitaba que los nombres largos se truncaran.
2. **`SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001`** metió en ese mismo trailing un chip de frescura
   (`Hace 3 min`), de ~190 px y alto de fila completa.

Resultado: el trailing tiene dos inquilinos. La columna central pierde ese ancho y la meta-line, que
es la que cede, se corta.

Ninguno de los dos está mal por separado. Es la combinación, y por eso no lo vio ninguna de las dos
tareas.

## Y hay una redundancia debajo

`PROBABLE` (el badge de la meta) y `Hace 3 min` (el chip) **dicen lo mismo**. Desde
`SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001`, la fiabilidad ES la edad: una sola rampa
🟢≤10 min 🟡≤30 🔴>30. La fila gasta dos elementos y ~190 px en decir un único dato dos veces, con
dos vocabularios distintos (una palabra cualitativa y una cifra).

## Opciones (a decidir por el user, no obvias)

1. **El chip sustituye al badge.** La meta se queda con tiempo de coche y en-camino; la frescura
   vive sólo en el chip, que ya lleva su color. Es la que elimina la redundancia en vez de repartir
   el espacio.
2. **El badge se queda y el chip se va**, dejando la frescura como palabra + color.
3. **Acortar el chip** a `3 min` sin el "Hace" — alivia, no resuelve: DE y NL siguen justos.
4. Dejarlo: la meta se corta por el final, que es donde está el token menos crítico.

⚠️ La 1 y la 2 tocan el diseño de `SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001`, recién mergeado. No
se toca sin decidirlo.

## Lo que sí quedó comprobado en este barrido

✅ **Las etiquetas de las stats caben en los 9 idiomas.** Era el riesgo que estaba anotado
(alemán `SITZUNGEN GESAMT`, neerlandés `PLEKKEN GEDEELD`, polaco `WSZYSTKIE SESJE`): las tres
celdas entran en dos líneas sin cortarse en ningún locale. En italiano `POSTI CEDUTI` cabe en una
sola línea, así que esa fila queda algo asimétrica — cosmético.

## Cómo reproducir sin tocar los ajustes del móvil

Per-app locale (Android 13+), que cambia sólo la app:
```
adb shell cmd locale set-app-locales com.rndeveloper.paparcar.mock --locales de-DE
adb shell cmd locale set-app-locales com.rndeveloper.paparcar.mock --locales ""   # restaurar
```
