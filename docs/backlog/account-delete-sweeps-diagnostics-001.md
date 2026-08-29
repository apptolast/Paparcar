# ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001 · Eliminar la cuenta debe borrar también la telemetría

**Estado:** ✅ Done (29-08-2026) · 1.737 tests verdes · rules DESPLEGADAS en pap-26 (29-08) ·
barrido único de huérfanos legacy EJECUTADO (29-08): 11.407 docs de eventos en 454 sesiones
huérfanas, 6 uids, verificado 0 restantes. ⚠️ Los móviles con APK viejo siguen creando huérfanos
hasta instalar este build (`ensureParentHeader` corta la sangría en el device); si pasa mucho
tiempo hasta el /run, repetir el barrido con el script (recreable desde este doc) o vía MCP.

## Problema
La política de privacidad publicada (PLAY-PRIVACY-POLICY-001, sección 6) promete que eliminar la
cuenta borra «perfil, vehículos, historial, zonas **y diagnósticos**». Verificado en código el
28-08: `DeleteAccountUseCase` barre los 4 `UserScopedRepository` (UserParking, Vehicle,
UserProfile, Zone) + caché local de spots + cuenta Auth, pero **nadie borra
`diagnostics/{uid}`** (sesiones + eventos + `uiLocation`) **ni `diagnostics_config/{uid}`** en
Firestore. Esa telemetría contiene ubicaciones vinculadas al uid → incumplimiento del derecho de
supresión que nosotros mismos declaramos (RGPD art. 17) y discrepancia política↔código.

## Doctrina violada
La política es un contrato: lo que promete al usuario tiene que ser verdad en el código, igual que
cada control de Ajustes dice la verdad. También el KDoc del propio use case se autodenomina
«GDPR right-to-erasure» sin cubrir la colección más sensible.

## Señales / datos disponibles
- Colecciones: `diagnostics/{uid}/sessions/{id}/events/*`, `diagnostics/{uid}/uiLocation/*`,
  `diagnostics_config/{uid}` (constantes que vivían duplicadas en `FirestoreDetectionEventLogger` /
  `FirestoreUiLocationLogger`).
- El patrón existente: `UserScopedRepository.deleteAllData(userId)` — la lista ordenada se cablea
  en `DomainModule` y el KDoc del use case ya exige «implementar la interfaz Y añadirse a la lista».

## Diseño (decidido al abrir, 29-08)
**Cliente, no Cloud Function**: el proyecto no tiene Functions desplegadas y el volumen es acotado
(telemetría de un uid, ya capada por la retención de 7 días; solo emiten los uids opt-in). El
patrón de iterar `sessions → events` doc a doc ya existía en la retención del propio logger.

1. **`DiagnosticsRepository : UserScopedRepository`** (domain, solo puerto de borrado) +
   `DiagnosticsRepositoryImpl` (data): barre `sessions` (+ subcolección `events` de cada una),
   `uiLocation`, y borra `diagnostics_config/{uid}`. El doc padre `diagnostics/{uid}` nunca se
   crea (missing parent) → no hay nada que borrar en esa ruta. Binding en `DataModule` + añadido
   **al final** de la lista de `DomainModule` (justo antes del delete de Auth, para minimizar la
   ventana en la que un evento rezagado re-cree un doc) + binding fake en `MockModule` (el mock no
   incluye `dataModule`; sin binding, resolver el use case crashea).
2. **Schema compartido** `DiagnosticsFirestoreSchema` (data/datasource/remote): los nombres de
   colección estaban duplicados en los 2 loggers y ahora los consume también el eraser — regla
   CLAUDE.md de constantes compartidas por 2+ clases. La duplicación deliberada del GATE se queda
   como estaba.
3. **El leak de los huérfanos se cierra en su sitio** (`ensureParentHeader` en
   `FirestoreDetectionEventLogger.writeEvent`): las lanes departure/sentry escriben eventos bajo
   `sessionId = geofenceId` sin doc padre → invisibles para CUALQUIER query sobre `sessions`, o
   sea inalcanzables tanto para la retención [DIAG-RETENTION-001] como para este barrido de
   cuenta. El invariante «todo evento tiene un padre real alcanzable» se arregla UNA vez,
   generalizando el patrón del trigger-ledger [DET-EVERY-TRIGGER-LEAVES-A-TRACE-001]: 1 `get` por
   sessionId por proceso, header mínimo `strategy = TRACE_HEADER` solo si de verdad falta (nunca
   pisa el header real de una conducción). Bonus: un `SESSION_ENDED` visto tras reinicio de
   proceso ya no pierde su doc (antes `updateFields` sobre header ausente tiraba el evento).
4. **Rules**: `diagnostics_config/{uid}` tenía `allow write: if false` → el barrido del cliente
   fallaría. Ahora `allow delete` para el dueño; `create/update` siguen admin-only (el toggle no
   es del cliente). ⚠️ Requiere deploy (`firebase deploy --only firestore:rules` vía CLI — el MCP
   deploy es no-op silencioso).

**Fuera de alcance, con motivo**: el `parkdiag.log` local es un fichero en el dispositivo del
usuario (muere con la desinstalación; la promesa de la política es sobre lo que guardamos
nosotros). Los huérfanos escritos ANTES del fix 3 siguen siendo inalcanzables por query — barrido
manual único vía MCP/Admin en los 2 uids opt-in (producción no emite: gate por defecto false).

## Criterio de éxito
Test unitario del use case con un fake que registre el barrido de diagnósticos ✅; tras eliminar
cuenta, `diagnostics/{uid}` y `diagnostics_config/{uid}` quedan vacíos (verificable vía MCP
Firestore en un uid de prueba).

## Consumidores auditados
Grep de `COLLECTION_DIAGNOSTICS` / `diagnostics_config` (29-08):
- **Escritores**: `FirestoreDetectionEventLogger` (sessions/events) y `FirestoreUiLocationLogger`
  (uiLocation) — ambos re-leen `getCurrentSession()` por evento en su consumer; tras el delete de
  Auth la sesión local es null → dejan de escribir. **Cubierto.** Ventana residual asumida: (a)
  eventos drenados entre el barrido y el delete de Auth (sub-segundo, por eso diagnostics va
  última en la lista); (b) otro dispositivo con la misma cuenta logueada puede escribir hasta que
  su token caduque (≤1 h) — residual, sin remedio de cliente, documentado.
- **Lectores**: ninguno en la app (el análisis se hace vía MCP externo). **Exento.**
- **`diagnostics_config`**: solo lo leen los 2 gates; nadie lo escribe desde el cliente (rules
  admin-only) → nada lo re-crea. **Cerrado.**
- Copy de Ajustes del delete de cuenta: no enumera colecciones (grep `diagnos` en strings = 0) →
  sin cambios de copy. **Exento.**
