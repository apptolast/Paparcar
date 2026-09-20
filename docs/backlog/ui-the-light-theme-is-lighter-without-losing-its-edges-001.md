# UI-THE-LIGHT-THEME-IS-LIGHTER-WITHOUT-LOSING-ITS-EDGES-001 · El tema claro, más claro

**Estado:** 🔵 En revisión · rama `feature/UI-THE-LIGHT-THEME-IS-LIGHTER-WITHOUT-LOSING-ITS-EDGES-001` ·
cierra la **issue #48**

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
