# AUDIT-RULES-001 — Contrato Firestore de `spots` (C4 + A4-rules)

- **Rama:** `fix/AUDIT-RULES-001-firestore-contract`
- **Origen:** auditoría 2026-07-04 (`docs/audits/AUDIT-2026-07-04-full.md`), re-verificado 12-07.

## Los dos agujeros (verificados contra `firestore.rules` actual)

1. **C4 · Nadie puede borrar spots** → la colección crece sin límite.
   `allow delete` exige `resource.data.reportedBy == request.auth.uid` (con rama `"anonymous"`),
   pero el cliente escribe `reportedBy = displayName` (`ReportSpotReleasedUseCase`) o `""`
   (`ReportSpotWorker`, `IosReportSpotScheduler`) — nunca el uid ni `"anonymous"`. Field-test
   30-06: los `PERMISSION_DENIED` eran esto. Sin TTL server-side, los spots caducados solo
   desaparecen del cliente.
2. **A4-rules · Cualquier autenticado puede REESCRIBIR spots ajenos.**
   `allow update: if request.auth != null` sin diff de claves — un cliente malicioso puede
   mover/alterar cualquier spot de la comunidad.

## Diseño propuesto

- **Escritura:** `reportedBy` pasa a ser SIEMPRE el `uid` (campo de identidad, no de display);
  el nombre visible viaja en campo propio (`reporterName`). Migración: los spots viejos con
  displayName/"" caducan solos por TTL — no hace falta migrar datos.
- **Rules:**
  - `create`: `request.resource.data.reportedBy == request.auth.uid` + validación de shape.
  - `update`: solo el dueño, o diff limitado a claves inocuas (`enRouteCount`) para el resto.
  - `delete`: dueño, O spot caducado (`resource.data.expiresAt < request.time`) por cualquiera
    — el TTL server-side barato sin Cloud Functions.
- **Cliente:** tocar los 3 escritores (`ReportSpotReleasedUseCase`, `ReportSpotWorker`,
  `IosReportSpotScheduler`) + deserializadores; test de paridad Firestore ya existe
  (`FirestoreDeserializerParityTest`) — ampliarlo.
- **Validación:** Rules Playground + E2E con las dos cuentas de campo (fiyp/WZB7).

## Estado

Sin empezar. Depende de decidir el shape de `reportedBy` (uid) — cambio de contrato de datos.
