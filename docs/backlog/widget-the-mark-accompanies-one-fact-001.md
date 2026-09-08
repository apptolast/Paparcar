# WIDGET-THE-MARK-ACCOMPANIES-ONE-FACT-001 · La marca sangraba la primera fila de la lista y dejaba las demás pegadas al borde

**Estado:** ✅ Done — en master (squash 08-09-2026)

## Problema

En el widget alto con dos coches, la marca se pintaba **solo en la primera fila** — decisión
deliberada de `WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001` D5 ("la marca es del widget, no de cada
coche"). El efecto en pantalla no era el que se buscaba: como va **delante del nombre**, la primera
fila queda sangrada 22 dp y las demás arrancan en el borde. Una lista con un escalón.

Observado por el user mirando el emulador: *"el icono de Paparcar solo está en uno de los coches"*.

## Doctrina

La regla que faltaba, y que ahora vive junto a `BrandMark`:

> **La marca acompaña a UN hecho. Una lista no la necesita.**

Se pinta cuando el widget muestra un solo bloque —widget corto, `Empty`, viaje sin coches
aparcados debajo— y desaparece en cuanto hay lista.

## Alternativas descartadas, y por qué

| Opción | Por qué no |
|---|---|
| Repetirla en cada coche | La convierte en un **badge del vehículo**, que es lo contrario de lo que es |
| Reservar su hueco (22 dp) en todas las filas | Alinea, pero **le quita ancho a la calle, que YA se trunca hoy** con dos coches ("Av. de la Concepción, 21 · 9:08 P…"). Era mi propuesta y la medición en pantalla la descarta |
| Sacarla a una cabecera propia | Lo más limpio en abstracto, pero **cuesta una fila de alto**, y con el cubo alto en 150 dp esa fila echa a un coche fuera: pagas identidad con información |

## Consecuencia asumida

Con dos coches y el widget estirado, en el launcher no se ve la marca. Es aceptable porque:
- el plan gratis es **un coche** [FREE-LIMITS-001], así que ese caso es Plus + redimensionado a
  propósito — la marca sigue viéndose en la práctica casi siempre;
- ahí el contenido ya dice de quién es: dos coches con sus calles y sus acciones no son de nadie más;
- y el estado que de verdad necesitaba identidad —`Empty`, el rectángulo gris de un widget recién
  anclado— la conserva entera.

## Criterio de éxito

- Widget corto (un bloque) → marca, como hasta ahora.
- Widget alto con **una** fila → marca.
- Widget alto con **dos o más** → sin marca y **todos los nombres en la misma columna**.
- Viaje con coches aparcados debajo → la línea del viaje pierde la marca; sola, la conserva.
- `Empty` → marca intacta.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `CarRow(showBrand)` | **cerrado** — `index == 0 && visible.size == 1` |
| `TitleAndHint(showBrand)` | **cerrado** — gana el flag; `Empty` lo deja en `true` por defecto |
| `Driving` | **cerrado** — la marca depende de si cuelgan filas debajo (`alsoShown.isEmpty()`) |
| `BrandMark` | **exento** — sigue siendo el mismo glifo; lo que cambió es QUIÉN lo pide |

## Verificación

- `:app:compileProdDebugKotlin` + `:app:assembleMockDebug` ✅ · `:shared:testDebugUnitTest` ✅
- Comprobado en el emulador en las dos caras: corto con marca ("Mi Seat +1 more") y alto con los dos
  nombres alineados y sin marca.
- ⏳ En mano: los dos móviles.

## Notas

Sin dominio, sin strings, sin detección. Tercer retoque de D5 encontrado **mirando la pantalla**,
no con tests — el modelo llevaba verde desde el principio.
