# RELEASE-THE-AAB-MUST-CARRY-AD-ID-001 · el corte comprueba que el AAB lleva `AD_ID` (y no las APIs de segmentación)

**Estado:** ✅ Done (10-11-2026)

## Resultado medido
| Caso | AAB | Resultado |
|---|---|---|
| Negativo | el del 17-09 (sin Meta ni `AD_ID`) | **falla**: «el build lleva Meta y el AAB NO declara `AD_ID`» |
| Positivo | `:app:bundleProdRelease` de master `c0c99db1e` (1.6.0), 24,2 MB | pasa: `AD_ID` presente; Topics y Custom Audience ausentes |
| Negativo sintético | el positivo + `ACCESS_ADSERVICES_TOPICS` añadido al manifest | **falla**: «el AAB declara `ACCESS_ADSERVICES_TOPICS`» |

El positivo es además la prueba que faltaba de que la vc17 subida a Play lleva `AD_ID`: el AAB de
ese corte vivía en un worktree ya borrado.

## Problema
El 10-11-2026, al crear la versión de prueba interna de la 1.6.0, la Console de Play dio un
**error que bloquea**: «Tu declaración de ID de publicidad indica que tu app usa un ID de
publicidad. Un archivo de manifiesto de uno de tus artefactos activos no incluye
`com.google.android.gms.permission.AD_ID`».

El artefacto era la **vc16 (1.5.0)**, aún activa en producción y sin Meta, así que el error era
cierto pero inofensivo. La única salida fue **desactivar los errores de la versión**. Con la vc17
ya activa la casilla dejó de verse en la Console, y no hay forma de saber —la API de Play no expone
esa declaración— si el error volvió a quedar activo.

Consecuencia: un corte futuro que saliera **sin** `AD_ID` podría pasar la Console sin bloquear,
con el identificador de publicidad a ceros y la atribución de Meta muerta, sin crash ni log.

## Doctrina violada
«Nada sale sin el sí» y «el binario no puede más de lo que dice el documento»
[MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]: la declaración de la Console, el Data Safety y
la política dicen que la app usa el GAID y **no** Topics ni Custom Audience. Eso se comprobaba en el
manifest del CÓDIGO (`AdvertisingDisclosureGuardrailTest`), nunca en el BINARIO que lee Play.

## Señales / datos disponibles
El manifest del AAB (`base/manifest/AndroidManifest.xml`) es protobuf, pero sus cadenas viajan como
UTF-8 crudo: buscar los bytes del nombre del permiso es fiable. Comprobado sobre un AAB real: la
búsqueda encuentra `INTERNET` y `ACCESS_BACKGROUND_LOCATION`.

## Diseño
El invariante vive en `AD_SDKS` de `tools/release/cut.py`, junto a la clave de la red: cada red
declara `requires` (permisos que el AAB TIENE que llevar) y `forbids` (los que NO puede llevar).
`check_aab_permissions_match_the_declaration` corre en la fase `bundle`, justo después de
`check_aab_carries_the_ad_key`, y sólo si el árbol mete la red en el binario
(`declared_ad_networks`). Una red nueva hereda el chequeo con rellenar sus dos tuplas.

Entre el manifest del código y el del AAB está el merge de manifiestos: una dependencia o un flavor
con `tools:node="remove"` puede quitar el permiso sin tocar una línea nuestra. Por eso el chequeo
del código no basta y éste no lo sustituye: son dos testigos de dos cosas distintas.

## Criterio de éxito
- Falsificado en negativo: el AAB del 17-09 (sin Meta ni `AD_ID`) **falla** con el mensaje de
  `AD_ID`.
- Pasa en positivo con un AAB de la 1.6.0 construido desde master.

## Consumidores auditados
| Consumidor | Qué mira | Estado |
|---|---|---|
| `AdvertisingDisclosureGuardrailTest` | manifest del código, política, Data Safety | ya cubierto, no cambia |
| `check_aab_carries_the_ad_key` | la clave de Meta dentro del AAB | ya cubierto, no cambia |
| `check_aab_permissions_match_the_declaration` | permisos dentro del AAB | **nuevo** |
| Declaración de ID de publicidad de la Console | permiso de los artefactos activos | no se puede leer por API: por eso existe este chequeo |
