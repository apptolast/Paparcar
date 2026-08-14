---
name: field-test
description: Diagnosticar un field-test de detección de aparcamiento en Paparcar. Usar cuando el usuario reporte un viaje real ("he hecho un viaje", "otro falso positivo", "no me detectó el aparcamiento", "mira los diagnósticos", "el pin salió mal"), pegue una hora/lugar de un trayecto, o pida revisar la telemetría de detección. NO usar para bugs de UI ni para cambios de algoritmo sin datos de campo (para eso, det-change).
---

# Diagnóstico de field-test de detección

Ritual fijo. El orden importa: **primero los datos, después el código**. Nunca especular sobre la
lógica antes de haber leído la telemetría real de la sesión.

## 0 · Contexto que NO hay que volver a preguntar

**Setup de dispositivos (INTENCIONAL, decisión del user 10-08-2026).** En cada trayecto lleva los
dos móviles a la vez para probar ambas estrategias en el mismo viaje físico:

| Móvil | uid | Cuenta | Vehículo | Estrategia que prueba |
|---|---|---|---|---|
| **Oppo** (CPH2371) | `fiypNbElGlfFexLMpU9sNaMjRMD3` | rndeveloper11501@gmail.com | Skoda Kamiq real, BT `50:26:EF:16:1D:C0` | **BLUETOOTH** |
| **Redmi** (2201117TY) | `WZB7oftWLDY1toGJrDwoRHnnYHx2` | otra cuenta | Citroën C5 Aircross **ficticio**, sin BT | **COORDINATOR** puro |
| 3er móvil (Carlos) | `sUGo7EYl16XDtosI8Ei7LFeAo2E2` | cardomfer97 | — | Coordinator sin BT |

⛔ **No reportar como bugs:** que el Redmi esté en otra cuenta (no sugerir re-login), que el Redmi
atribuya el viaje al C5 Aircross (el coche físico es el Kamiq — "coche equivocado" es por
definición ahí), ni que el Redmi no tenga eventos BT (no hay emparejamiento).
✅ El Redmi **sí** debe grabar `routePolyline` en cada pin — es el control positivo del pipeline de rutas.

**Field-testing al mínimo:** el user prueba sin exención de batería a propósito, para aislar
OEM-kill/Doze como causa única de falsos negativos.

## 1 · Leer la telemetría PRIMERO (Firestore, proyecto `pap-26`, vía Firebase MCP)

```
diagnostics_config/{uid}                     → {enabled, note}  (gate de logging)
diagnostics/{uid}/sessions/{sessionId}       → cabecera de sesión
diagnostics/{uid}/sessions/{id}/events/{ay}  → eventos crudos
```

- Listar sesiones con `firestore_list_documents`, `parent=...documents/diagnostics/{uid}`,
  `collectionId=sessions`, `orderBy` **`startedAt desc`**. Empezar por las más recientes.
- **Leer `summary` (rollup de `SESSION_ENDED`) ANTES de bajar eventos** — trae `endedAt`,
  `maxSpeedKmh`, `drivingFixes`, `fixCount`, `maxStepCount`, `finalLat/finalLon`, `deviceModel`,
  `appVersion`. Casi siempre basta para clasificar.
- Cabecera: `strategy` (COORDINATOR/BLUETOOTH), `outcome` (`confirmed_steps+egress`, null si abierta
  o abortada), `vehicleType`. Los docs `arm_<ms>` traen el evento de armado empaquetado en el
  string `strategy`: `"ARM:GEOFENCE_EXIT (geof=… d=… acc=… exitLoc=… dep=…)"`.
- Eventos: `type=STEP` (`stepCount`, `stopped`), `type=LOCATION_FIX` (`lat/lon`, `speed` m/s,
  `accuracy`, `stoppedDurationMs`), + `activity`, `transition`, `geofenceId`, `deviceAddress`,
  `confidence`, `pathLabel`, `phase`, `action`, `event`, `outcome`.

**Gotchas del MCP:**
- `firestore_query_collection` **NO** acepta paths de subcolección (`a/b/c` → 400 "contains /").
  Usar `firestore_list_documents` con `parent` + `collectionId` + `orderBy`/`mask`.
- `diagnostics/{uid}` es un doc "missing" (solo subcolección) → `showMissing:true` o listar la
  subcolección directa.
- No hay `jq` en la máquina. Volcados grandes → parsear con PowerShell `ConvertFrom-Json`.

Los pins viven en `users/{uid}/parkingHistory`; las plazas comunitarias en `spots` (top-level).

## 2 · Provenance de CADA pin — obligatorio

⛔ Para todo aparcamiento implicado, decir **explícitamente quién lo puso**. Los campos están en
Firestore desde DET-PIN-PROVENANCE-001 (`9e9e3f48`), no hace falta triangular:

- **`detectionPath`** → `steps+egress` · `kinematic+egress` · `vehicle-exit` · `unattended_timeout`
  · `user` · `bt` · `manual` · `safety_net_backfill` · `nudge`
- **`armEvidence`** → qué armó la sesión: `GEOFENCE_EXIT` · `AR_VEHICLE_ENTER` · `MANUAL` · `BT` · safety-net

Un diagnóstico sin provenance no está terminado. Nunca deducir el origen solo de
`detectionReliability` (0.5 cubre backfill Y unattended-timeout; 0.9 cubre varios paths).

## 3 · Clasificar con el contrato de detección

El contrato (dictado por el user, 2026-07-08):

1. **Todo trigger dispara SIEMPRE**, aunque llegue tarde, y tiene verificación tardía. Sin
   excepciones. Un evento viejo pierde autoridad directa (pasa al evaluador), nunca se ignora.
2. **La única excusa aceptable es el OS** (OEM force-stop, Doze extremo) — y debe ser detectable a
   posteriori.
3. **Fallo asimétrico:** mejor falso negativo que falso positivo. Ante la duda se PREGUNTA, no se
   planta un pin.

Árbol de decisión ante un aparcamiento perdido:

```
¿hubo ALGÚN despertar en la ventana (heartbeat/eventos en el parkdiag)?
├─ NO  → causa = OS (OEM-kill / Doze). No es bug de lógica.
│         Salida: telemetría OEM-KILL + mitigación al user (BT si lo tiene,
│         si no exención de batería) con copy causa+consecuencia+remedio,
│         SIN mecánica interna.
└─ SÍ  → hubo despertares CON datos y aun así se perdió → BUG NUESTRO.
          Arreglar el invariante en UN sitio (no apilar guards).
```

Para un falso positivo: reconstruir qué evidencia se aceptó como "conducción medida" y por qué no
debió serlo (fix fantasma, mirage Doppler, precisión mala, re-entrega de evento…).

## 4 · Salida al usuario

Tabla, una fila por sesión/pin:

| Hora | Móvil/uid | strategy | armEvidence | detectionPath | outcome | Veredicto |
|---|---|---|---|---|---|---|

Debajo, por cada anomalía: **causa raíz** (una frase) → **invariante violado** → **ticket propuesto**
(id + una línea). Si procede abrir ticket, seguir la skill `nuevo-ticket`.

## 5 · Antes de dar por cerrado

- [ ] ¿Cada pin tiene provenance atribuida explícitamente?
- [ ] ¿La clasificación OS-vs-lógica está justificada con datos, no con suposición?
- [ ] Si el fix toca un invariante: **grep de TODOS los consumidores de esa señal** y clasificarlos
      en el ticket (cerrado / cubierto por convergencia / exento con razón). Cerrar solo la vía
      donde mordió NO basta — corolario aprendido con DET-DRIVE-PROOF-001 → DET-DEPART-PROOF-001.
- [ ] Actualizar la memoria del field-test correspondiente y `docs/backlog/` si hay ticket nuevo.
