# Diagnóstico 31 mayo 2026

Redmi Note 11 (5f8991cb) + Oppo CPH2371 (LNRCMZ8H6HBITWNJ).  
Contexto: Redmi no detectó aparcamiento; Oppo falso positivo en hospital ~19:14 + comportamiento extraño.

---

## Redmi Note 11 — no funcionó

**Resumen:** El coordinador se inició a las 19:09:51 pero no confirmó ningún aparcamiento. La notificación de Medium apareció con 4+ minutos de retraso y el vehículo ya estaba en movimiento cuando se mostró.

**Timeline:**
- `19:09:51` — IN_VEHICLE ENTER, coordinador iniciado, vehicleId=bff5c760 (Cupra Born)
- `19:10:16–19:10:28` — 3 fixes de driving-speed rechazados por `acc > 50m` (62, 71, 107m). `hasEverMoved` bloqueado ~80s
- `19:10:28` — `hasEverMoved=true` vía fallback distancia (speed rechazado pero dist≥150m)
- `19:11:55` — GPS spike a 236m, coordinador en Low/NotYet
- `19:12:25–19:14:09` — Score=Low repetidamente, **notificación suprimida** por ausencia de `vehicleExit` O `STILL` (BUG-2, ~4 min supresión)
- `19:13:50` — IN_VEHICLE EXIT
- `19:14:10` — Notificación Low/Medium finalmente mostrada (tarde)
- `19:14:35` — Score=Medium(0.65), stopped=59s
- `19:14:42` — Vehículo en movimiento (1.5 m/s) → sesión cancelada
- `20:34:41` — Nueva sesión, `maxNoMovementMs guard hit` sin confirmación (BUG-4)

**Causa raíz:** Combinación de GPS accuracy pobre inicial + notificación suprimida indefinidamente sin STILL/exit. Cuando exit llegó, notif llegó demasiado tarde y el vehículo ya se movía.

---

## Oppo CPH2371 — falso positivo hospital

**Resumen:** Sesión iniciada a las 19:08:14. A las 19:14:01 el coordinador alcanzó HIGH(0.75) y auto-confirmó aparcamiento con GPS excelente (acc=3.1m) pero **sin confirmación de Activity Recognition** (still=false). El usuario estaba parado ~30s en la entrada del hospital.

**Timeline sesión falsa:**
- `19:08:14` — IN_VEHICLE ENTER, vehicleId=3f921b0d
- `19:08:17` — loc#1 speed=9.17m/s, acc=2.7m → `hasEverMoved=true` rápido
- `19:08:17–19:13:26` — Conducción normal, coordinador monitorea stops
- `19:13:26` — IN_VEHICLE EXIT (usuario fuera del vehículo)
- `19:13:31–19:14:01` — Speeds bajos (0.04–0.3 m/s), accuracy excelente (2.0–4.6m), stoppedSince activo
- `19:14:01` — **HIGH(score=0.75)** con: speed=0.10m/s, stopped=30042ms, acc=3.1m, exit=true, **still=false**
- `19:14:01` → CANDIDATE phase, notificación enviada
- `19:14:05` — ConfirmParking.invoke, reliability=0.9, AUTO_DETECTED
- `19:14:06` — ✓ saveSession, spotType AUTO_DETECTED (FALSO POSITIVO)

**Causa raíz (BUG-3):** El scoring alcanza HIGH con solo `exit=true + 30s stopped + acc<5m`, **sin requerir `still=true`**. Un usuario que para 30s en la entrada de urgencias con GPS preciso activa auto-confirmación incorrectamente.

---

## Bugs identificados

### BUG-DIAG-310501 — GPS accuracy guard sin fallback (Redmi)
- GPS accuracy > 50m bloquea `hasEverMoved` ~80s. Si el GPS no mejora nunca, la sesión queda en limbo.
- Fix: permitir `hasEverMoved=true` si `distance ≥ 150m` aunque accuracy > 50m (ya ocurre como fallback pero muy tarde).

### BUG-DIAG-310502 — Notificación Low/Medium suprimida sin timeout (Redmi)
- Log: `⊘ Low/Medium notif suppressed — no vehicleExit/STILL signal yet [BUG-3]` durante 4+ minutos.
- Fix: mostrar MEDIUM confirmation tras 60–90s con Low score, independientemente de STILL/exit.

### BUG-DIAG-310503 — HIGH auto-confirm sin STILL cuando exit=true (Oppo, falso positivo hospital)
- Valores: speed=0.10m/s, stopped=30s, acc=3.1m, exit=true, still=**false** → HIGH(0.75) → auto-confirm.
- Fix: requerir `still=true` OR elevar `stoppedDur` mínimo a 90–120s cuando `still=false`.

### BUG-DIAG-310504 — maxNoMovementMs guard sin notificación (Redmi)
- `20:34:41`: sesión termina silenciosamente por `maxNoMovementMs`. Usuario no sabe que se perdió una detección.
- Menor gravedad, documentar.

---

## Acciones sugeridas

| Bug | Impacto | Fix sugerido |
|---|---|---|
| BUG-310503 | CRÍTICO — falso positivo hospital | Requerir `still=true` O `stoppedDur≥90s` para HIGH sin STILL |
| BUG-310502 | ALTO — detección perdida Redmi | Timeout 60–90s en notif-suppression |
| BUG-310501 | MEDIO — GPS bloqueado Redmi | Fallback distance-only para hasEverMoved |
| BUG-310504 | BAJO | Documentar / log visible |
