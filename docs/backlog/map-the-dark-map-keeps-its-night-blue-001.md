# MAP-THE-DARK-MAP-KEEPS-ITS-NIGHT-BLUE-001 · El mapa oscuro recupera su azul de noche

**Estado:** ✅ Done · rama `bugfix/MAP-THE-DARK-MAP-KEEPS-ITS-NIGHT-BLUE-001-night-blue`

## Problema

`MAP-A-STYLE-THAT-NEVER-RAN-001` hizo que el estilo por fin se aplicara, y con eso el tema oscuro
pasó a ser lo que `DARK_MAP_STYLE` llevaba pidiendo desde siempre: **gris neutro `#212121`**. Nadie
lo había visto nunca, porque nunca se había ejecutado.

Y visto, no es lo que queremos. El user: *«el mapa en tema oscuro quiero el que teníamos antes»* —
el **azul de noche nativo de Google**, que es el que ha estado en pantalla todos estos meses.

⚠️ No es un cambio de opinión: está escrito desde el 09-09 en
`map-style-is-silently-ignored-001.md`, que ya recogía la decisión — *«el tono actual —el nativo
azulado— pero SIN los POI»*. Ese ticket concluyó que conservarlo obligaba a imitar el tema de Google
a mano (su «salida B») o a montar un Map ID de Cloud. Con la causa real ya arreglada, imitar el
nativo es sencillamente **repintar el JSON**, que es lo que se hace aquí.

## Doctrina

No la contradice, la matiza. [UI-COLOR-DOCTRINE-001] dice que el color del mapa es nuestro; esto
sigue siéndolo — lo elegimos, lo escribimos y lo mantenemos nosotros. Lo que cambia es el criterio:
**un mapa nocturno es un plano bajo luz de noche, y el azul es lo que lo hace leer como noche.** El
gris neutro lo convierte en una radiografía.

## Medido, y el gusto coincide con el número

La hoja del peek en oscuro es `#131A24`. Contra ella:

| Suelo | Contraste vs la hoja |
|---|---|
| `#212121` (el gris que se mergeó) | 1,09:1 |
| **`#1c2a40`** (el azul nativo) | **1,21:1** |

O sea que el tono que el user pide **además separa mejor** el mapa de la modal — el mismo eje que
motivó el repintado del tema claro. No hay que elegir entre lo que le gusta y lo que mide.

## Diseño

Paleta muestreada píxel a píxel de la captura del Oppo del 14-09 **antes** del arreglo, o sea del
tema nativo real, no de una imitación a ojo:

| | Nativo medido | `DARK_MAP_STYLE` antes | Ahora |
|---|---|---|---|
| Suelo | `#1c2a40` (31 % del mapa) | `#212121` | **`#1c2a40`** |
| Agua | `#144a57` (15 %) | `#000000` | **`#144a57`** |
| Parques | `#196453` | `#181818` | **`#196453`** |
| Calle local | `#31435e` | `#2c2c2c` | **`#31435e`** |
| Arteria | `#354d68` | `#373737` | **`#354d68`** |
| Autovía | `#3e5a77` (7 %) | `#3c3c3c` | **`#3e5a77`** |

Las etiquetas pasan de grises neutros a azulados para que se apoyen en ese suelo.

⛔ **Lo que NO cambia: los POI.** `labels.icon: off` y `transit: off` siguen igual en los dos temas.
Lo que se recupera es el TONO, no las píldoras moradas — que es exactamente lo que pedía la decisión
del 09-09, y la misma decisión «iconos fuera, nombres dentro» que se tomó para el claro.

Y el tema claro **no se toca**: `#cdd8ea` con agua y parques con color se quedan como están.

## Criterio de éxito

- En device, tema oscuro: el suelo muestrea `#1c2a40`, el agua `#144a57`, los parques `#196453`.
- Ni un icono de POI ni transporte en oscuro.
- Suite verde (`MapStylesTest` cubre los dos temas).

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `DARK_MAP_STYLE` | repintado |
| `LIGHT_MAP_STYLE` | **sin tocar** |
| `MapStylesTest` | sus tres casos siguen valiendo: los de hex y POI recorren AMBOS temas; el del suelo es solo del claro |
| `MapType` / preferencias | sin tocar — este ticket es solo color |
