# DET-TIERS-001 — Niveles de detección honestos + Bluetooth como árbitro

- **Rama:** `feature/DET-TIERS-001-detection-tiers`
- **Origen:** decisión estratégica del usuario 2026-07-11 tras dos semanas de field-tests:
  "estaría bien asumir que la detección sin bluetooth es semi automática… replantear el
  sistema de permisos para darle al usuario certeza… regla de oro: mejor falsos negativos
  que positivos advirtiendo al usuario… bluetooth como validador definitivo y verídico".
- **Base ya en master:** DET-RELIABILITY-001 (nivel OPTIMAL/GOOD/REDUCED de evaluador único,
  batería opcional, callout REDUCED en Permissions/Settings) + toda la cadena de detección
  (freeze, kinematic egress, safety-net, reconcile).

## 1. El replanteo

Dejar de prometer detección automática universal. Tres niveles HONESTOS, comunicados con
causa + consecuencia + remedio (nunca mecánica interna):

| Nivel | Condición | Promesa al usuario |
|---|---|---|
| **Automático** | BT del coche emparejado | "Tu plaza se marca y libera sola" |
| **Asistido +** | Sin BT, con exención de batería (+ ajustes OEM) | "Casi siempre solo; alguna vez te preguntaremos" |
| **Asistido** | Sin BT ni exención | "Detectamos lo que el móvil nos deja; cuando dudemos, te preguntamos" |

Regla de oro transversal: **mejor falso negativo (preguntar) que falso positivo (pin/plaza
fantasma)** — ya encarnada en freeze/kinematic/nudge; aquí se convierte en contrato de producto.

## 2. Bluetooth como árbitro determinista (NO señal de scoring)

Respeta la regla CLAUDE.md "no mezclar señales BT en el Coordinator scoring". El BT no
puntúa: **supersede**.

- **BT disconnect** durante una sesión del coordinator → confirma el park AHÍ
  (fiabilidad `reliabilityBluetooth`), cancelando escalera/prompt en curso.
- **BT connect** con sesión de detección viva → veto: el usuario está EN el coche
  (descarta candidato/prompt; re-sella el ancla — parte ya existe vía DET-RETURN-ANCHOR-001).
- Prerrequisito de calidad: cerrar T2/T3/T4 de `DET-AUDIT-REMEDIATION-001` (gate de
  velocidad, tests, timeout del walk-away) — el árbitro tiene que ser digno de la autoridad.

## 3. Pantalla de permisos re-encuadrada (niveles-con-consecuencia)

- Presentar los 3 niveles y DÓNDE está el usuario ahora; cada permiso/acción muestra qué
  nivel desbloquea ("Empareja el BT de tu coche → automático"; "Permite actividad en
  segundo plano → menos preguntas").
- Exención de batería = **mejora de rendimiento opcional** del nivel asistido, nunca
  bloqueante. En MIUI documentar la pareja whitelist AOSP + "Sin restricciones"/autostart.
- Copy siempre causa+consecuencia+remedio; el nivel vivo se lee de
  `ObserveDetectionReliabilityUseCase` (ya en master).
- Sync obligatorio del Dev Catalog: escenarios por nivel + galería (regla MOCKQA-001).

## 4. Fuera de alcance (anotado, no incluido)

- AR `WALKING/ON_FOOT` ENTER como tercera nominación de egress (misma latencia MIUI que
  los pasos — solo acelerador). Sensor de pasos wake-up (experimento hardware/batería).
- iOS: todos quedan en nivel "asistido" (BT clásico invisible para apps de terceros);
  la matriz de niveles debe contemplarlo desde el diseño.
