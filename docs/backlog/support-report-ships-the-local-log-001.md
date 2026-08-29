# SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001 · "Informar de un problema" envía el parkdiag del device

**Estado:** ✅ Done (29-08-2026) · 1.762 tests verdes (prod+mock+release compilan) · rules
DESPLEGADAS en pap-26. ⏳ Sin ver en device: falta una prueba real de envío (tocar la fila y
verificar el doc en `diagnostics_reports/{uid}` vía MCP + reconstruir el gzip).

## Problema
No existe soporte de usuario: cuando a un tester (o usuario de beta) le falla la detección, no hay
manera de que nos haga llegar la evidencia. El flag remoto (`diagnostics_config`) solo captura
viajes FUTUROS (se lee al arrancar el proceso) y exige intervención de admin; el `parkdiag.log`
local — que tiene MÁS que Firestore — solo se puede extraer con cable y `adb run-as`. El viaje
donde YA pasó el bug es irrecuperable sin el móvil en la mano.

## Doctrina violada
«Conducir es el recurso escaso, no el código»: un bug de campo con evidencia local que se pierde
por no poder subirla es un FN de nuestro propio sistema de diagnóstico.

## Señales / datos disponibles
- `FileAntilog` (androidMain): `parkdiag.log` + 5 rotaciones × 5 MB en `filesDir`. ⚠️ Hoy solo se
  registra con `BuildConfig.DEBUG` → en release el log NO existe.
- ⛔ **pap-26 NO tiene bucket de Storage** (verificado 29-08: 404 en `pap-26.firebasestorage.app`
  y `pap-26.appspot.com`) y provisionar el bucket por defecto hoy exige plan Blaze → Storage
  descartado.
- Firestore ya tiene el patrón de datos por-uid + rules por dueño + el barrido de cuenta
  (ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001).

## Diseño
El log viaja por **Firestore en chunks base64** (sin dependencia nueva, mismas rules y mismo
barrido de cuenta que el resto de la telemetría):

1. **Dominio**: `LocalDiagnosticsLog` (port; snapshot gzip del log reciente — activo + rotación
   `.1`, ≤10 MB crudos ≈ ~1 MB gz) · `DiagnosticsReportUploader` (port) ·
   `SendDiagnosticsReportUseCase` (uid + snapshot + metadata device → upload; `Result<Unit>`).
   En iOS `LocalDiagnosticsLog` no se bindea (`getOrNull`): el reporte viaja sin log, con
   metadata — el registro de la queja vale por sí mismo.
2. **Data**: `FirestoreDiagnosticsReportUploader` →
   `diagnostics_reports/{uid}/reports/{createdAtMs}` (header: device, versión, os, chunkCount,
   gzipBytes) + `…/chunks/{n}` (base64 ~600 KB crudos/chunk < 1 MiB doc). El doc padre
   `diagnostics_reports/{uid}` no se crea (missing parent, como `diagnostics`).
3. **Barrido de cuenta**: `DiagnosticsRepositoryImpl` barre TAMBIÉN `diagnostics_reports/{uid}`
   (reports → chunks) — «todo dato con dueño se borra con el dueño»; la política §6 lo cubre como
   diagnósticos.
4. **Release**: `FileAntilog` se registra SIEMPRE (antes solo DEBUG) — sin log local no hay nada
   que enviar en producción. Coste: fichero privado de la app (≤30 MB, muere con la
   desinstalación); `DebugAntilog` sigue solo en debug.
   ⚠️ **Consecuencia que obligó a un segundo arreglo**: al correr en release, el write path pasa a
   ser código de producción, y era un `appendText` = open+write+close **por línea**, con la
   detección emitiendo ~47 líneas por fix. Ahora mantiene UN stream abierto y hace `flush` por
   entrada: una syscall en vez de tres, y el fichero sigue sobreviviendo a un kill de proceso
   (`flush` entrega los bytes al SO) — que es justo lo que necesita un diagnóstico de OEM-kill.
   Dos tests nuevos lo fijan (lectura inmediata + el contador de bytes se siembra del fichero
   existente, para que un reinicio no resetee el reloj de rotación). El guard
   `PaparcarLogger.d { }` ya NO corta en Android release: es el precio de que el fichero exista.
5. **UI (Ajustes, sección About)**: fila "Informar de un problema" → diálogo de consentimiento
   que dice QUÉ se envía (registro técnico reciente con ubicaciones aproximadas) → spinner en el
   diálogo → snackbar de éxito/error. Strings en los 9 locales; variante en la galería mock.
6. **Rules**: `diagnostics_reports/{uid}/{document=**}` read/write solo el dueño; deploy.

## Criterio de éxito
Test unitario del use case (fakes). En device: tocar la fila sube el reporte y aparece en
`diagnostics_reports/{uid}` vía MCP con el gzip reconstruible; con el uid, la traza del viaje
del bug se identifica sin cable.

## Consumidores auditados
- `DeleteAccountUseCase` / `DiagnosticsRepositoryImpl`: cubierto en el punto 3 (misma tarea) —
  `diagnostics_reports/{uid}` se barre con la cuenta, reports + chunks.
- `SettingsViewModel`: constructor tocado → `SettingsViewModelTest` actualizado (era el único
  otro call site) + 5 tests del flujo nuevo.
- `PaparcarLogger.d { }`: su KDoc afirmaba que en release no hay antilog; ya no es cierto en
  Android → corregido en el mismo commit (una KDoc que miente es deuda, no comentario).
- `FileAntilog` KDoc: decía «debug builds only — do NOT add this sink to release builds»;
  corregido, es exactamente lo que hace ahora.
- Skill `field-test`: nueva fuente de datos → documentada (`diagnostics_reports`, cómo
  reconstruir el gzip, y que una sesión sin `createTime` ya solo puede venir de un APK viejo).
- Política de privacidad: los reportes son telemetría de diagnóstico por uid, ya declarada; el
  envío es además acción explícita del usuario con diálogo de consentimiento previo.
- Galería mock + `SettingsPreviews`: 2 variantes nuevas (diálogo + enviando), en paridad.
