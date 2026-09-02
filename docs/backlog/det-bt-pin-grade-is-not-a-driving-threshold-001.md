# DET-BT-PIN-GRADE-IS-NOT-A-DRIVING-THRESHOLD-001 · la precisión con la que se CREE un fix no es la precisión con la que se COLOCA un pin

**Estado:** 🟡 Abierto, sin rama · destapado por [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001] (30-08)
· **auditado el 03-09: la opción 2 tal como está escrita es un NO-OP** (ver abajo)

> ## Auditoría 03-09 — ⛔ «adoptar la duda que ya existe» no cambia nada, y eso reencuadra el ticket
>
> La opción 2 parecía la barata y doctrinal: reutilizar `inferredPinDoubtRadius` para guardar el fix
> BT borroso como ZONA en vez de punto. **Medido: no dispara nunca.**
>
> | | valor |
> |---|---|
> | Puerta del candidato BT (`evaluateCandidateFix`) | `accuracy ≤ minGpsAccuracyForDriving` = **50 m** |
> | Suelo de `inferredPinDoubtRadius` (`honestCloseMinZoneRadiusMeters`) | **60 m** |
>
> Como el carril BT **solo acepta fixes ≤50 m** y la política de duda solo degrada **>60 m**, cablearla
> ahí sería código inalcanzable — y este repo ya tiene escrito que una excepción sobre código que no
> se ejecuta *«no es una excepción, es un agujero»* [UI-TYPE-SYSTEM-HYGIENE-001].
>
> **Lo que eso revela**: el 50 m del carril BT no es un umbral suelto y arbitrario, está **por dentro**
> de la política global de la app sobre qué accuracy puede sostener un punto (60 m, calibrada y
> compartida por TODAS las vías inferidas). O sea que el BT no es inconsistente con el resto: es que
> la app entera acepta un punto exacto con 45 m de accuracy.
>
> Por tanto el criterio de éxito de abajo —*«un fix de 45 m no puede producir un punto exacto»*— **no
> es un arreglo del carril BT**: es pedir bajar el suelo global de 60 m, que afecta a todas las vías
> inferidas y es exactamente la calibración que este doc dice que no se hace sin la distribución real
> de `accuracy` del Kamiq. Lo que SÍ es específico de BT y sigue en pie es la **fiabilidad**: 0,95 es
> la más alta que damos, y sostenerla con la misma accuracy con la que otras vías dan 0,9 es una
> decisión que nadie tomó explícitamente.
>
> ⏳ Sigue bloqueado por la misma medición de siempre (un aparcamiento real del Kamiq), y ahora se
> sabe qué preguntar a esos datos: **la distribución de accuracy del primer fix tras el corte**, para
> decidir el suelo — no un umbral nuevo solo para BT.

## Problema

`EvaluateBtParkUseCase.evaluateCandidateFix` acepta como **pin** cualquier fix con
`accuracy ≤ config.minGpsAccuracyForDriving` — **50 m**. El KDoc del detector lo llama "pin-grade";
la constante no lo es. Su KDoc dice literalmente para qué se creó:

> *GPS horizontal accuracy at or below which a **high-speed fix is trusted as evidence of genuine
> driving**… Default 50 m — generous enough that urban GPS noise still counts.* [LOC-002]

Es un umbral **de credibilidad**, y generoso a propósito: se calibró para no descartar pruebas de
conducción en hardware ruidoso (Redmi Note 11). Reutilizarlo para decidir *dónde se planta el coche*
mete hasta 50 m de error en el pin **por definición**, antes de contar ningún otro efecto.

Las dos preguntas son opuestas: creer una prueba pide un umbral laxo (mejor no perder evidencia);
colocar una posición pide uno estricto (mejor no colocar que colocar mal — fallo asimétrico).

El mismo gate se usa además en `evaluateWalkAway` para descartar fixes degradados, donde la laxitud
sí es correcta (ahí sólo decide si el fix *opina*, no dónde cae nada).

## Doctrina violada

*Fallo asimétrico: mejor falso negativo que falso positivo.* Un pin a 50 m de donde está el coche no
es un aparcamiento guardado, es una búsqueda a pie por la manzana — y el carril BT lo estampa con
`reliabilityBluetooth = 0.95`, la fiabilidad más alta que damos.

Y el corolario de [DET-INFERRED-PIN-CARRIES-ITS-DOUBT-001], que resolvió exactamente esta forma en
las vías inferidas (`inferredPinDoubtRadius`: pasado el suelo de 60 m el pin se guarda como ZONA del
radio de su propio fix) y **dejó el carril BT fuera con razón explícita**: *«the BT lane, which never
mixes with Coordinator machinery — if the field ever shows a fuzzy BT fix pinning exactly, that is
its own ticket»*. Este es ese ticket, abierto por lectura del código en vez de por campo.

## Señales / datos disponibles

- `ParkingDetectionConfig` **no tiene** constante de precisión para colocar: hay que decidirla, no
  buscarla. `honestZoneRadius` / `inferredPinDoubtRadius` (suelo 60 m) son el precedente de cómo se
  dibuja la duda cuando el fix no da para un punto.
- El campo dirá qué precisión trae de verdad un fix de aparcamiento del Kamiq: con
  [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001] el candidato se muestrea segundos después del corte de
  contacto, así que la distribución de `accuracy` de esos primeros fixes es **desconocida** hoy y es
  justo el dato que decide el umbral.

## Diseño (a decidir, no cerrado)

Dos formas, y conviene no elegir a ciegas:

1. **Umbral propio**: `minGpsAccuracyForPin` estricto (¿15-20 m?) en el gate del candidato. Simple,
   pero convierte un aparcamiento en garaje o entre edificios en un `bt_gps_timeout` — cambia FP por
   FN, que es la dirección correcta pero tiene coste.
2. **Adoptar la duda que ya existe**: aceptar el fix y guardarlo como ZONA del radio de su propia
   accuracy, igual que las vías inferidas. No pierde el aparcamiento y no miente sobre la precisión.

La 2 encaja mejor con la doctrina (*no se descarta, se estampa la duda*) y con lo ya construido; la 1
es más barata. Probablemente sea la 2 con un suelo, pero **no se decide sin la distribución real de
`accuracy`** del primer fix tras el corte de contacto — o se calibra a ojo otra constante más.

## Criterio de éxito

- Un fix de 45 m no puede producir un punto exacto a fiabilidad 0,95.
- Un aparcamiento en garaje sigue guardando la sesión propia (aunque sea como zona): no se cambia un
  FP por perder "dónde está mi coche".
- Test que fije la elección, y un `grep` que confirme que `minGpsAccuracyForDriving` queda sólo donde
  su KDoc dice (creer conducción / descartar fixes degradados), nunca donde se coloca.

## Consumidores a auditar

Todo uso de `minGpsAccuracyForDriving`, clasificado en *decide si un fix OPINA* (correcto) vs *decide
DÓNDE cae algo* (a cerrar).
