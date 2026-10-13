# UI-THE-LIGHT-THEME-IS-LIGHTER-WITHOUT-LOSING-ITS-EDGES-001 · El tema claro, más claro

**Estado:** ✅ Done · master `fe576b3e5` · cierra la **issue #48** · ⏳ sin ver en device

⛔ **Cierre RETROACTIVO el 13-10-2026.** Esta línea decía «🔵 En revisión · rama
`feature/UI-THE-LIGHT-THEME-…`», y esa rama ya no existe. El trabajo está en master y se puede
señalar con el dedo: `fe576b3e5` mueve los **cuatro** tokens del tema claro —`PapVariantLight`
`#D8E0EE`→`#E4EAF5`, `PapSurfaceLight`, `PapAzure`, `PapAzureLow`— y escribe la medida de la
segunda vuelta en el KDoc de `Color.kt`.

⛔ Y aquí está por qué nadie lo cazó: el asunto de ese commit empieza por **`docs(ui)`**, y
`tools/backlog/branch_tell.py` descartaba los `docs(` por considerarlos prosa. *Un commit `docs(`
que toca algo que no es un `.md` no es un commit de docs*
[DOCS-A-DEAD-BRANCH-IN-A-STATUS-LINE-IS-ALWAYS-STALE-001].

## Problema

Pedido por el product owner (#48): *«UI en tema claro se ve algo gris, podríamos aclarar algo más
los tonos claros y el mapa un poquito también **sin perder el contraste entre componentes y
mapa**.»*

Son dos peticiones que tiran en sentidos opuestos, así que esto se midió antes de tocar nada.

## Lo medido

| Token | Antes | Ahora | ΔL* |
|---|---|---|---|
| `surfaceVariant` (cama de tiles y chips) | #D8E0EE (89,0) | **#E4EAF5** (92,5) | **+3,6** |
| `surfaceContainer` (hoja, nav) | #ECF0F9 (94,7) | **#F0F4FB** (96,1) | +1,3 |
| `background` | #F0F4FB (96,1) | **#F4F7FC** (97,1) | +1,1 |
| `surfaceContainerLow/High` | #F4F6FC (96,9) | **#F7F9FD** (97,9) | +1,0 |

El que de verdad se leía gris era **`surfaceVariant`** — la cama de los tiles y los chips, casi 6
puntos de L* por debajo del resto de la familia. Se lleva el triple de subida que los demás.

## Qué pasa con los contrastes

| Par | Antes | Ahora |
|---|---|---|
| hoja vs suelo del mapa | 1,260 | **1,303** ✅ |
| texto (`onSurfaceVariant`) sobre `surfaceVariant` | 7,33:1 | **8,06:1** ✅ |
| `onSurface` sobre el fondo | 15,79:1 | **16,22:1** ✅ |
| card blanca sobre el fondo | 1,103 | 1,074 ⚠️ |

Lo único que cede es el salto card-blanca/fondo, y es el precio inevitable de aclarar un fondo que
ya era casi blanco. Lo que separa una card no es ese salto: es su contorno (`PapBorders.thin`),
que es la regla de esta paleta desde que se escribió.

## ⛔ El mapa NO sube

Aunque se pidiera «un poquito también». El suelo #cdd8ea está elegido con dos medidas —1,26:1 y
ΔE00 6,8 contra la hoja— precisamente para separarse de ella. Subirlo en paralelo a la UI devolvía
**1,221: peor de lo que había**, que es exactamente lo que la issue pedía no perder.

Aclarando sólo la UI, la separación **mejora sola** (1,303). Si aun así se quiere un mapa más claro,
hay que bajar la hoja o cambiarle el tono al suelo, y eso es otra medida.

## Criterio de éxito

- En device, la UI clara se lee menos gris — sobre todo los tiles y los chips.
- La hoja sigue despegándose del mapa (mejor que antes, de hecho).
- Ninguna card desaparece contra el fondo: su contorno sigue ahí.

## Segunda vuelta: «la card blanca sobre el fondo, ¿subir o bajar?»

**Ninguna de las dos, y no es una evasiva: la pregunta se cae al medir el par correcto.**

**Subir no existe.** `PapCardLight` es `#FFFFFF` — blanco puro, techo absoluto. Y bajar la card la
acercaría al fondo, que es lo contrario de separar.

### Lo que de verdad dibuja el límite de una card

Su **borde**: `outline` `#7A8FA0` al `DEFAULT_OUTLINE_ALPHA` de 0,4 sobre el blanco de la card = un
trazo efectivo de **`#CAD2D9`**. Contra el fondo:

| | Fondo | Contraste del BORDE |
|---|---|---|
| Antes del ticket | `#F0F4FB` | 1,385:1 |
| Después | `#F4F7FC` | **1,423:1** |

El borde es más oscuro que los dos fondos, así que **aclarar el fondo le SUBE el contraste**: el filo
de la card se lee un 3 % mejor que antes de tocar nada.

El 1,074 que aparecía como regresión es el salto **relleno contra relleno**, que en esta paleta nunca
fue lo que separaba una card — el `Theme.kt` ya lo dice desde que se escribió: *«Inner-chip
differentiation comes from their `PapBorders.thin` outline, not fill color»*.

### Decisión

**No se toca nada.** La medida queda escrita en el KDoc de `Color.kt` para que no se vuelva a
plantear como una regresión que hay que compensar.

### El mando, por si en device se ve flotando

No es el blanco de la card: es bajar el **fondo** a `#F2F5FC` (L* 96,6), que recupera 1,09 de relleno
conservando dos tercios de la subida. No se aplica a priori porque hoy no hay nada medible que lo
pida — sería cambiar un número por una sensación, y este ticket nació justamente de medir en vez de
discutir.

⏳ **Sin captura del estado final**: no hay device conectado (`PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001`).
