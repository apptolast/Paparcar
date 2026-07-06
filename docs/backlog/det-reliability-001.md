# DET-RELIABILITY-001 — Fiabilidad de detección como sistema (exención de batería opcional)

**Fecha:** 2026-07-06 · **Estado:** F1–F3 ✅ implementadas en la rama; F4 DIFERIDA (gate = telemetría de campo)
**Rama:** `feature/DET-RELIABILITY-001-detection-reliability`
**Nota de implementación:** gran parte del plumbing YA existía y se reutilizó tal cual —
`OemBackgroundReliabilityManager` (detección de OEM agresivo + deep-links autostart/Hans, con cards
ya presentes en el tier Opcional de permisos), `AppPermissionState.isBatteryOptimizationExempt`,
el intent de exención en `PermissionsScreen.android.kt` y las filas BT/batería de Settings. Lo
nuevo es el NIVEL como fuente única (`EvaluateDetectionReliabilityUseCase` +
`ObserveDetectionReliabilityUseCase`), la fila ámbar de Settings, el callout honesto del
onboarding, y el escenario mock (`MockScenario.aggressiveOem` + preset + 2 variantes de galería).
**Origen:** investigación de mercado 2026-07-06 (memoria `reference_market_research_parking_detection.md`)
+ field-tests 2026-07-04/05 (kills de ColorOS con batería llena).
**Relacionado:** `[OEM-KILL-001]` `[BATTERY-ASK-001]` `[GEOF-RESTORE-001]`, SETTINGS-REMODEL-001
(sección "Detección y permisos"), PERM-TIMELINE-ICONS-001 (pantalla de permisos).

---

## Decisión de diseño

**La exención de optimización de batería es SIEMPRE opcional.** La detección funciona con o sin
ella; lo que cambia es *cuánta confianza* puede tener el usuario en que funcione, y eso se modela
explícitamente como un **nivel de fiabilidad** — un evaluador puro de dominio, una sola verdad,
tres superficies que lo leen. Nunca un bloqueante, por tres razones duras:

1. **Play policy** prohíbe condicionar la app a la exención (nuestro caso está en la tabla
   "Acceptable" — la detección en tiempo real no puede hacerse vía FCM — pero pedirla como
   requisito es motivo de rechazo).
2. **Sería mentir**: la exención libra de Doze/App-Standby (AOSP) pero NO ata a los killers
   propietarios (PowerGenie, screen-off-kill de ColorOS, blacklist de autostart de MIUI).
   "Concede esto y funcionará" es una promesa que no podemos cumplir; el lenguaje honesto es de
   fiabilidad, no de garantía.
3. **El usuario con BT emparejado casi no la necesita**: el receiver de manifest ACL es el trigger
   más resistente (revive proceso muerto, sin registro que perder, sin diferimiento Doze
   documentado). Obligar sería fricción gratuita. Es el estándar unánime de la industria
   (Sentiance, DriveQuant, Zendrive, Life360): pedir + educar + pantalla de salud, nunca exigir.

## ⛔ Regla de copy — sin mecánica interna

El texto al usuario NUNCA expone internals (workers, procesos, frecuencias, "despertamos cada
15 min" — genera más preguntas de las que responde). El copy vive en el nivel del usuario:

> *"Los móviles como el tuyo restringen las apps en segundo plano para ahorrar batería. Esto puede
> impedir que Paparcar detecte cuándo te vas de tu plaza. Permite la actividad en segundo plano —
> o empareja el Bluetooth de tu coche, la opción más fiable."*
> (Implementado con "como el tuyo" genérico; interpolar `Build.MANUFACTURER` queda como polish.)

Tres piezas: (1) es política del fabricante, no un fallo de Paparcar; (2) la consecuencia que le
importa (perder la detección de su salida); (3) los dos remedios. Nada más.

## El evaluador (dominio, Kotlin puro)

```kotlin
enum class DetectionReliability { OPTIMAL, GOOD, REDUCED }

class EvaluateDetectionReliabilityUseCase {
    operator fun invoke(input: DetectionReliabilityInput): DetectionReliabilityReport
}

data class DetectionReliabilityInput(
    val hasBluetoothPairedVehicle: Boolean,  // algún Vehicle.bluetoothDeviceId != null
    val isBatteryExemptionGranted: Boolean,  // PowerManager.isIgnoringBatteryOptimizations
    val isAggressiveOem: Boolean,            // Build.MANUFACTURER ∈ lista (oppo, xiaomi, vivo, huawei, realme, oneplus, honor, meizu)
)

data class DetectionReliabilityReport(
    val level: DetectionReliability,
    val issues: List<ReliabilityIssue>,      // NO_BT_PAIRING, BATTERY_OPTIMIZATION_ACTIVE — cada uno con su Fix
)
```

Matriz (BT = pata fuerte; exención = pata media; OEM benigno = entorno favorable):

| BT | Exención | OEM agresivo | Nivel |
|----|----------|--------------|-------|
| ✅ | ✅ | — | OPTIMAL |
| ✅ | ❌ | ❌ | OPTIMAL |
| ✅ | ❌ | ✅ | GOOD (issue: batería) |
| ❌ | ✅ | — | GOOD (issue: BT) |
| ❌ | ❌ | ❌ | GOOD (issues: BT, batería) |
| ❌ | ❌ | ✅ | **REDUCED** (issues: BT, batería) |

Es una FUNCIÓN PURA del estado actual — no toma permisos (eso es la salud existente de
"Detección y permisos": permisos rotos = detección BLOQUEADA, que precede y anula el nivel de
fiabilidad) ni telemetría (fase 2).

## Superficies (las tres leen el MISMO evaluador)

### F1 — Dominio + puertos plataforma
- `EvaluateDetectionReliabilityUseCase` + modelos (commonMain, tests de la matriz completa).
- Puerto `DeviceEnvironment` (o extender `PermissionManager`): `isBatteryExemptionGranted` (ya
  existe plumbing de batería en Settings vía `isBatteryOptimizationRelevant`), `isAggressiveOem`.
  iOS: `false`/`false` (no aplica — gate ya existente).
- Flow reactivo de "hay vehículo con BT" desde `VehicleRepository`.

### F2 — Settings → Detección y permisos
- Fila de nivel de fiabilidad (reutilizar lenguaje visual de salud existente: verde/ámbar).
- REDUCED/GOOD con issues → filas "Arreglar": exención (intent
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, requiere permiso de manifest
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) y emparejar BT (ruta existente de vehículo).
- **Autostart per-OEM** (pata independiente de la exención): deep-link al startup manager del
  fabricante — tabla de intents conocida (patrón AutoStarter). Best-effort: si el intent no
  resuelve, no se muestra. No es legible si está concedido → fila setup-once, sin estado.

### F3 — Onboarding (pantalla de permisos)
- Al resolver los permisos, si nivel == REDUCED → paso adicional con el copy de arriba
  (una card estilo timeline existente, NO pantalla nueva si el flujo lo permite).
- Dos CTAs: **Emparejar mi coche** (preferente) · **Permitir en segundo plano**. "Ahora no" sin
  castigo. Se muestra UNA vez (flag en preferences); Settings queda como superficie permanente.

### F4 — Contextual post-daño (DIFERIDA, gate = field-test)
- "Earn the ask" cableado a daño real: `ForceStopConfirmed`/`BackgroundKillSuspected` correlacionado
  con una salida perdida (sesión limpiada por re-park tardío o liberación manual) → UNA notificación
  con deep-link al fix. Necesita la telemetría acumulando datos primero. NO implementar aún.

## Mock/Dev Catalog (⛔ regla CLAUDE.md)
- `MockScenario`: variantes de fiabilidad (REDUCED con/sin issues) para el fake del puerto.
- Galería: variantes de la card de onboarding + fila de Settings en sus grupos existentes.

## Strings
- Keys `detection_reliability_*` en EN + ES mínimo. Nombre del fabricante interpolado
  (`Build.MANUFACTURER` capitalizado), con fallback genérico "tu móvil".

## Tests
- Matriz completa del evaluador (6 filas) + naming `should_..._when_...`.
- Fakes: extender `FakePermissionManager` / nuevo `FakeDeviceEnvironment`.
