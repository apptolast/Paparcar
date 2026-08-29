# UI-SPOT-ROW-SAYS-WHAT-DECIDES-001 · La fila de plaza dice lo que decide; lo demás lo cuenta su modal

**Estado:** ✅ **Done** (29-08) · rama `bugfix/UI-TYPE-LOCALE-SWEEP-001-nine-locales`

## Problema

La meta-line de la fila de plaza **se cortaba en los nueve idiomas**, español incluido:

| | |
|---|---|
| ES | `PROBABLE · 1 min en coche ·` |
| DE | `WAHRSCHEINLICH · 1 Min. mit d…` |
| PL | `PRAWDOPODOBNE · 1 min sa…` |

Casi lo archivo como un problema de traducciones largas: mi captura de ES venía de un build anterior
y cabía. Recapturada con el build del día, ES truncaba igual. **No era el idioma.**

### Por qué pasó

Dos cambios entraron en master el mismo día y se pisaron:

1. `UI-TYPE-TWO-VOICES-ONE-ROW-001` subió la distancia a la línea del nombre, *liberando el
   trailing*.
2. `SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001` puso en ese mismo trailing un chip de edad de
   ~190 px.

Ninguno está mal por separado. Es la combinación, y ninguna de las dos tareas podía verla.

### Pero el fondo era otro

La fila cargaba **siete datos en dos líneas**: nombre, distancia, tiempo en coche, edad, fiabilidad,
gente en camino y sin-confirmar. Repartir píxeles entre siete cosas sólo cambia cuál se corta.

## Diseño — decidido por el user (29-08)

La regla: **la fila dice lo que hace falta para DECIDIR desde la lista; lo que describe la plaza una
vez elegida vive en su modal.**

- **La fiabilidad sale.** El color del puck ya la comunica (`SpotPuckIcon` recibe `SpotFreshness` y
  la codifica en color, anillo y badge), y el modal la explica con su medidor `FRESCURA` de cinco
  segmentos.
- **`SIN CONFIRMAR` sale.** El modal lo cuenta entero y con los dos botones que lo resuelven. En la
  fila era una palabra larga sin salida. [DET-HANDOFF-NOT-MANUAL-001 §B.3]
- **La gente en camino se queda, en icono + cifra** (`👥 2`). Es la señal de que la plaza puede estar
  cogida al llegar, y como glifo cuesta un tercio de lo que costaba escrita.

⚠️ Comprobado antes de mover nada: **el modal ya pintaba las dos cosas**. La fila las estaba
repitiendo, así que esto no traslada información — deja de duplicarla.

- **Los metros bajan a la meta**, entre el tiempo y la gente. La línea del nombre entera queda para
  el NOMBRE, que es lo que se escanea: en la fila del Mercadona pasa de `Mercadona Álvar…` a
  `Mercadona Álvaro Dom…`. Al quedarse sin consumidores, el rol `rowDistance` se retira.
- **El espaciado agrupa.** Tiempo y distancia son el mismo dato dicho de dos formas, así que van
  pegados por su separador; la gente en camino es otra cosa y se despega (14 dp). Un `spacedBy`
  uniforme ponía los tres a la misma distancia y se leían como tres datos sueltos.

La fila queda:

```
[puck]  Calle Larga 14                    [🕐 Hace 3 min]
        1 min en coche · 179 m      👥 2
```

## Criterio de éxito

1. ✅ **Los 9 idiomas sin truncar**, medido en el Redmi con per-app locale: EN, ES, IT, PT, FR, DE,
   NL, PL, RO. El peor (`1 Min. mit dem Auto 👥 2`) entra con holgura.
2. ✅ El modal sigue diciendo todo lo retirado: `PLAZA RECIÉN LIBERADA`, `Publicada hace 4 min`,
   `2 en camino`, y `FRESCURA` con su medidor.
3. ✅ `:shared:testDebugUnitTest`, `:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin`.

## Descartadas por el camino (medidas, no supuestas)

- **Quitar el badge y dejar el chip**: cabía, pero seguía sin resolver que la fila dijera de más.
- **Quitar el chip y dejar el badge**: el alemán (`WAHRSCHEINLICH`) seguía truncando.
- **Subir el chip junto al nombre**: cabía, pero el chip le come ancho al nombre de la calle, que es
  lo que se escanea.
- **Subir la palabra junto al nombre**: en ES daba el nombre más largo de todas, pero en DE
  (`VIELLEICHT`, `WAHRSCHEINLICH`) se comía la línea. El ancho de una palabra cualitativa varía de 5
  a 14 letras entre idiomas; el de un chip con cifra, no.

## Pendiente

`expiresAt` sigue llegando de Firestore y **no lo pinta nadie** desde que se retiró el `TTLIndicator`.
Si en algún momento hace falta decir cuánto le queda a una plaza — distinto de cuánto lleva
publicada — el dato está y el sitio natural es el modal.
