# DET-DOUBT-MUST-REACH-THE-SCREEN-001 · la app calculaba la duda y la tiraba

**Estado:** 🔵 En progreso · rama `feature/DET-DOUBT-MUST-REACH-THE-SCREEN-001-doubt-on-screen` ·
worktree `../Paparcar-doubt-screen` · apilada sobre `DET-TWO-TIER-SENTRY-001`

Pieza 6 del rediseño (§1.5).

## Problema

`zoneRadiusMeters` se calcula, se guarda en Room y —desde `DET-DOUBT-REACHES-REMOTE-001`— se
sincroniza. Pero **el detalle de Historial no lo leía en ningún sitio**:

- la hoja no decía que aquello fuera un área,
- el mapa dibujaba el anillo de duda **sólo** por la vía de `parkedVehicles` (la de Home), no por la
  del pin único que usa esta pantalla,
- y debajo había un botón **«Navegar a esta ubicación»**.

Es decir: un pin que el propio sistema marcó como **zona de 250 m con fiabilidad 0.5** se presentaba
como un punto exacto con una promesa de precisión encima. *La app sabía que dudaba y no lo decía.*

## Doctrina violada

Fallo asimétrico aplicado a lo que se le CUENTA al usuario, y la regla de copy: **causa +
consecuencia + remedio**, sin mecánica interna. Prometer «esta ubicación» sobre una zona es afirmar
una precisión que la propia app ya se negó a afirmar.

## Diseño

1. **La hoja lo dice**, reutilizando `ApproximateZoneRow` — el mismo componente que ya usa el peek de
   Home. No se re-implementa una fila «icono + texto» [UI-LIST-ITEM-001], y el copy es el que ya
   estaba traducido a los 9 idiomas.
2. **El mapa dibuja su anillo** también para el marcador de pin único. Mismos tokens y misma regla de
   color que el anillo de `parkedVehicles`: la insignia del coche **no se mueve**, el círculo dice
   «en algún punto de aquí». Sin información de Bluetooth en esta vía, el tono es el de dos:
   verde vigilado mientras la sesión está activa, `outline` cuando ha terminado.
3. **El botón dice a qué navega.** `parking_detail_navigate_area_action` («Navegar a la zona»), en
   los **9 locales** en esta misma tarea.

## Consumidores auditados

| superficie que muestra un pin | estado |
|---|---|
| Peek de Home (`ParkingPeek`) | ya lo pintaba — es de donde sale el componente |
| Mapa, vía `parkedVehicles` | ya pintaba su anillo |
| **Mapa, vía pin único (Historial)** | **cerrado** |
| **Detalle de Historial (hoja + botón)** | **cerrado** |
| `UserParking.toSpot()` | **exento y ya anotado**: la plaza comunitaria no hereda la duda del aparcamiento. Sigue abierto como follow-up del 28-08 — *una sesión-ZONA publica plaza en el centro de la zona sin marcar la duda* |

## Y un consumidor que se le escapó a `DET-DETECTION-PATH-IS-A-TYPE-001`

La galería mock tenía la variante **«Activa · Asistido (verde)»** construida con
`detectionPath = "steps=3 kinematicFixes=7"` — justo la jerga de diagnóstico que ese ticket demostró
que producción **nunca** escribe. Tras el tipo, esa cadena resuelve a `Unknown`: **la variante decía
una cosa y habría pintado otra**. Corregida para leer el camino del tipo.

Es exactamente el fallo que el tipo existe para impedir, escondido en el sitio donde menos se mira.

## Dev Catalog

Variante nueva **«Aproximada · zona con su duda»** (`unattended_zone_gap_anchor`, fiabilidad 0.5,
radio 250 m) en la misma tarea, como exige la regla del set de pruebas mock.

## Criterio de éxito

- ✅ `:shared:testDebugUnitTest` **1.826 en verde**, `:app:compileProdDebugKotlin` y
  `:app:compileMockDebugKotlin` en verde.
- ✅ String nuevo en los 9 locales.
- ⏳ **Sin ver en device** — la comprobación real es abrir la variante de la galería y el histórico.
