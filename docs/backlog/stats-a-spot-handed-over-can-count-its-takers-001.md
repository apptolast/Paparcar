# STATS-A-SPOT-HANDED-OVER-CAN-COUNT-ITS-TAKERS-001 · Cuántos conductores fueron a por tus plazas

**Estado:** 🔵 Abierto, sin empezar · nace como follow-up de
[COPY-THE-APP-NEVER-SAYS-WHAT-IT-IS-FOR-001](copy-the-app-never-says-what-it-is-for-001.md) (user, 25-09)

## Problema

Hoy la única cifra de aportación del usuario es `spotsReleased` — **cuántas plazas publicó**. Dice lo
que él dio, no lo que sirvió. La pregunta que un usuario haría —*«¿alguien la cogió?»*— no tiene
respuesta en la app, y es la que convertiría la aportación en un hecho con destinatario.

## Por qué esto SÍ y un «CO₂ ahorrado» NO

`Spot.enRouteCount` (`Spot.kt:29`, *«users currently navigating here — community pressure signal»*)
es **una medida real**: conductores que pusieron rumbo a esa plaza. Con eso, *«3 conductores fueron a
por tus plazas»* es un hecho, no un supuesto.

El contador de CO₂ evitado, en cambio, es un **contrafactual** y queda descartado en el ticket padre:
exige saber cuántas vueltas habría dado quien la cogió, y eso no se mide en ninguna parte.

## Señales / datos disponibles

- ✅ `Spot.enRouteCount: Int` — existe y se mantiene vivo mientras la plaza está publicada.
- ❌ **No vuelve a la sesión.** `grep -rn 'enRoute' domain/stats/ domain/model/UserParking.kt` → vacío.
  Cuando la plaza caduca, el dato se va con ella.
- ❌ No hay `PeriodStats` que lo agregue.

## Diseño (a decidir, NO resuelto)

El problema real es **dónde se congela el dato**, porque `enRouteCount` es un valor vivo que baja
cuando alguien desiste. Lo que importa registrar es el **máximo alcanzado** o el **acto de tomarla**,
y eso hay que decidirlo antes de tocar un campo:

1. ¿Máximo de `enRouteCount` durante la vida de la plaza, congelado al caducar?
2. ¿O un hecho más fuerte —«alguien aparcó aquí»— que hoy no detectamos en la plaza ajena?

⛔ La opción 2 es mucho más valiosa y mucho más cara. No empezar por el campo: empezar por decidir
cuál de las dos preguntas se contesta.

⛔ Cuidado con el mismo agujero de `sync_the_workdata_field_list_is_a_contract_nobody_checks`: si el
dato viaja a Firestore, el campo entra en los serializadores end-to-end y en la paridad de DTOs.
⛔ Y si viaja al espejo del coche compartido, decidir si es del grupo o del autor — el espejo tiene
lista blanca por TIPO, no por `if` (`VEH-SHARED-001`).

## Criterio de éxito

- Una cifra nueva que cite su evidencia, en la línea de `docs/analytics-data-contract.md`.
- Y si la respuesta honesta resulta ser «no se puede medir sin mentir», **cerrar el ticket diciendo
  eso**, que es un resultado válido.
