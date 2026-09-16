# SEC-A-SPOT-IS-NOT-A-MEGAPHONE-001 · Cualquier cuenta puede empujar notificaciones a quien tiene alertas de zona

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Origen:** revisión independiente, 2.ª vuelta — hallazgo C4. **Verificado.**

## Problema

Publicar una plaza dispara el reparto a todo el que vigila esa celda (`functions/src/index.ts`). La
regla de `create` de `spots` exige **solo** que `reportedBy` sea tu uid (`firestore.rules:19-20`):
ni que la plaza caduque en un plazo razonable, ni que su `status` sea uno de los que el cliente
escribe. Y el filtro del reparto anuncia una plaza con `expiresAt == 0` (`fanout.ts`), que es justo
lo que queda si no se pone el campo.

Con una cuenta y un script: plazas donde quieras, cuantas quieras, y cada una es una notificación en
el móvil de un desconocido. No hay App Check.

## Diseño — tres capas, porque ninguna sola basta

1. **Regla**: `expiresAt` entero, en el futuro y dentro del techo que el cliente usa de verdad —hay
   que LEER el TTL real antes de fijar el margen, no inventarlo— y `status` dentro del conjunto que
   la app escribe.
2. **Reparto**: `expiresAt` ausente o 0 → **saltar** con motivo `already-expired`. Una regla nueva
   no repara las filas que ya existen; esto sí.
3. **Ritmo**: si el mismo `reportedBy` creó más de N plazas en pocos minutos, no se anuncia (y se
   registra). El tope no impide publicar — impide **gritar**.

## Criterio de éxito

Una plaza sin `expiresAt` válido no se crea; si ya existía, no se anuncia. Test en `fanout.test.ts`.

⛔ **Reglas y funciones desplegadas y RELEÍDAS antes del APK.** Un deploy no se da por bueno con su
propio `success`.

## Lo medido al implementar

El techo de la regla sale de `SpotTtlPolicy`: `AUTO_SPOT_TTL_MS` son **2 h** y `PROVISIONAL_SPOT_TTL_MS`
12 min, así que el límite es **3 h** — sitio para el desfase de reloj del cliente, que es quien
calcula `expiresAt`, sin dejar publicar una plaza que grite un mes. `status` se acota a los dos que
el cliente escribe (`CONFIRMED`, `PROVISIONAL`); `RETRACTED` no se crea, se llega a él.

Y el ritmo: **5 plazas vivas** por autor. Una plaza dura 2 h y nadie libera cinco a la vez; un
script sí. No impide publicar — impide que el aviso salga.
