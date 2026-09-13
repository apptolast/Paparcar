# IOS-DIAG-A-LOG-THAT-SURVIVES-TO-BE-SHIPPED-001 · «Report a problem» en iOS sube cabeceras sin parkdiag

**Estado:** ⚪ Pendiente · sin rama
**Origen:** fila 4 de `ios-a-parity-tail-001.md` — declarada con ticket propio porque el hueco no
es el snapshot, es el SINK que no existe.

## Problema
`LocalDiagnosticsLog.snapshotGzip()` alimenta el informe de soporte
[SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]. En iOS no está bindeado → el informe llega con
`logGzip = null`: cabeceras y mensaje, cero contexto de detección. Y el `parkdiag.log` es
exactamente lo que Firestore pierde (memoria `project_det_2208_trips_become_replays_001`).

## Por qué no fue un port mecánico
El impl Android (40 LOC) solo gzipea dos generaciones de un fichero que escribe `FileAntilog`
(androidMain). iosMain **no tiene paquete `logging/`**: no hay antilog de fichero, así que no hay
nada que snapshotear. El trabajo real es:
1. Un `FileAntilog` iOS: escritor rotante (2 generaciones, mismo límite ~10 MB) sobre
   NSFileManager, instalado en `PaparcarIosBootstrap` junto al resto del arranque.
2. Gzip en K/N: Foundation no trae `GZIPOutputStream`. Opciones: cinterop a zlib (viene en el
   SDK de Xcode) o compresión en Kotlin puro. Decidir ANTES de escribir.
3. `IosLocalDiagnosticsLog` (el port de 40 LOC, ahora sí trivial) + binding.

## Criterio de éxito
Un informe enviado desde iOS llega a Firestore con su gzip del parkdiag, con la descripción en la
cabecera como en Android; el tamaño queda en el mismo orden (~1 MB).
