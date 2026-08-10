# DET-BATTERY-EXEMPTION-NUDGE-001 — Banner Home + re-pregunta cuando falta la exención de batería

> Estado: 🚫 SUBSUMIDO por [[det-watch-honest-001]] (decisión user 2026-08-10) — NO se pinta banner
> separado. Creado 2026-08-08. El backbone (flag `showBatteryOptimizationNudge` + intent/effect
> `RequestBatteryExemption`→`RequestBatteryOptimizationExemption`) se REUSA por el badge honesto y se
> queda; solo se descarta la UI de banner persistente.

## ⛔ Decisión (2026-08-10): NO construir el banner separado
El badge honesto (`DET-WATCH-HONEST-001`) ya cubre el caso frágil **en contexto**: aparcado + sin
exención + OEM agresivo → el peek muestra *"Vigilando — puede detenerse → Activar"* con el mismo CTA
(`RequestBatteryExemption`). Un banner global persistente sería redundante y más ruidoso.

Razones:
- **Contextual > global.** El badge habla donde el usuario mira su coche aparcado; el banner descontextualiza.
- **El backbone no se tira.** El intent/effect lo usa el CTA del badge; el flag `showBatteryOptimizationNudge`
  alimenta el `isReliabilityReduced` del badge. Todo se reusa.
- **Un banner NO da proactividad real.** El riesgo de la exención es que la vigilancia muere en silencio
  con la app cerrada → cuando ves badge o banner, quizá ya perdiste la plaza. Ambos son igual de reactivos
  (exigen abrir la app). El banner no protege más.
- **Setup-time ya tiene casa:** onboarding (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) + fila REDUCED
  en Ajustes.

## 🔜 Candidato de seguimiento (si el field-test lo pide): push al detectar sentry-kill
La proactividad real NO es un banner sino una **notificación en el momento en que se detecta que el OEM
mató el centinela** — llega con la app cerrada, en el instante que importa. Maquinaria ya existente:
`SentryResidenceStore` + `resolveSentryKillVerdict` detectan el kill; `ParkingSafetyNetWorker` corre y
puede postear la notif. Abrir ticket propio SOLO si el campo muestra que se pierden plazas por kills
silenciosos (respetar la doctrina "mejor FN que FP", copy causa+consecuencia+remedio, sin jerga).

---

> (Histórico del spec original, conservado por contexto — NO ejecutar la parte de "pintar banner".)

## Problema (field-test 08-08 Málaga)
El diagnóstico forense demostró que Paparcar era **la única** de las apps de detección instaladas en el
Oppo (ColorOS) y Redmi (MIUI) que **NO estaba en la lista blanca de batería (Doze whitelist)** —
Driversnote, TripLog, Everlance, eDriving Mentor sí lo están (`adb shell dumpsys deviceidle whitelist`
→ todas con `user,`). Sin esa exención, el OEM **congela el proceso**: el FGS residente muere, las
alarmas exactas se cancelan (`reason=alarm_cancelled`), y el AR/geocerca llega horas tarde
(`lag=36418627ms` = 10 h). Detalle: [[project_det_battery_whitelist_2026_08_08]].

**Causa de fondo (no técnica):** el onboarding **SÍ** pide `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
(`PermissionsScreen.android.kt:153`), pero está modelado como **OPCIONAL** (`AppPermissionState.kt:18`
`[DOZE-001]`) y el footer permite **"Continuar"** sin concederlo (`PermissionsContent.kt:385-417`).
Se detecta el estado después (`PermissionManagerImpl.isBatteryOptimizationExempt()`), pero solo se
refleja como una fila pasiva "Configurar" en Ajustes + ámbar REDUCED. **No hay nudge en Home ni
re-pregunta.** El usuario lo omitió y quedó fuera en silencio.

El autoarranque ("Inicio automático") es **red herring**: las competidoras lo tienen OFF. La palanca
es la whitelist de batería (+ en ColorOS, "Permitir actividad en primer plano").

## Decisión de producto (user, 08-08)
**Banner en Home + re-pregunta, NO bloqueante.** Reaparece hasta concederlo. Respeta la doctrina de
no bloquear Home. Descartado: semi-bloqueante en onboarding, y "solo Ajustes".

## Diseño
**Disparo:** mostrar el banner cuando TODO se cumple:
- detección habilitada (toggle ON) y hay/habrá sesión que vigilar,
- `isBatteryOptimizationExempt == false`,
- OEM agresivo (reusar la señal ya existente de `requiresOemBatteryFreeze` / aggressive-OEM que
  ya alimenta `EvaluateDetectionReliabilityUseCase` → `DetectionReliability.REDUCED` con issue
  `BATTERY_OPTIMIZATION_ACTIVE`).

**Fuente de verdad:** enganchar al `DetectionReliability`/readiness que YA se calcula, no inventar
estado nuevo. La condición "REDUCED por BATTERY_OPTIMIZATION_ACTIVE en OEM agresivo" es el gate.

**UI:** banner en Home (patrón banner existente, cf. CONN-BANNER / GlassSurface flotante si va sobre
mapa). No bloquea interacción. CTA **"Activar ahora"**:
1. Re-lanza el diálogo del sistema (reusar `PermissionsEffect.RequestBatteryOptimizationExemption`).
2. En ColorOS/MIUI, además, deep-link a la pantalla OEM de "Permitir actividad en primer plano"
   (helper de deep-link OEM; ya hay referencias OEM en el tier OPTIONAL de `PermissionsContent`).

**Reaparición:** persistente entre sesiones hasta que `isBatteryOptimizationExempt == true`.
Dismiss suave (ocultar el resto de la sesión) permitido, pero reaparece en el próximo arranque.
NO contador de rechazos visible al usuario (evitar tono culpabilizador).

**Copy (regla: causa+consecuencia+remedio, sin mecánica interna):**
- Título: p.ej. "La detección automática puede fallar en tu móvil".
- Cuerpo: "Tu móvil puede cerrar Paparcar en segundo plano y perderías la plaza al aparcar."
- CTA: "Activar ahora".
- Keys: `home_battery_nudge_title`, `home_battery_nudge_body`, `home_battery_nudge_cta`.

## Piezas a tocar
- `HomeViewModel` (subscribeDetectionReadiness ~línea 792+): derivar `showBatteryNudge` del readiness;
  añadir al `HomeState`; Intent `HomeIntent.RequestBatteryExemption` → Effect que reusa el intent.
- `HomeState`/`HomeIntent`/`HomeEffect`: nueva bandera + intent + effect (sealed classes).
- Composable de banner en Home (reusar patrón; extender por params, no fichero paralelo).
- Helper deep-link OEM (ColorOS "primer plano" / MIUI autostart) en androidMain.
- Strings en **9 locales** (en/es/it/pt/fr/de/nl/pl/ro).
- **Dev Catalog / galería:** nueva variante de estado Home "banner batería" en `StateGalleryScreen`
  + escenario en `MockScenario`/fake (permiso batería no concedido + OEM agresivo).
- Tests: use case / lógica de "should_showBatteryNudge_when_reduced_by_battery_on_aggressive_oem".

## Secuencia
1. ⏳ **Primero confirmar la hipótesis en campo**: con Paparcar ya metida en la whitelist por adb
   (`+io.apptolast.paparcar`), repetir trayecto en Oppo y verificar que el FGS sobrevive y el AR llega
   fresco. Si se confirma → este ticket es el fix correcto en producto.
2. Implementar banner + re-pregunta + deep-link OEM.
3. Field-test del banner en Oppo/Redmi (que reaparezca y que el CTA lleve a la pantalla correcta).

## Relacionados
- [[project_det_battery_whitelist_2026_08_08]] · det-resident-fgs-001 · det-exact-heartbeat-001 ·
  det-tiers-001 · det-reliability-001 · det-nudge-persist-001.
