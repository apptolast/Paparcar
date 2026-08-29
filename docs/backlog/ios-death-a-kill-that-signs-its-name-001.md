# IOS-DEATH-A-KILL-THAT-SIGNS-ITS-NAME-001 · Las muertes de proceso iOS no se estampan

**Estado:** ⚪ Pendiente · sin rama
**Origen:** fila 6 de `ios-a-parity-tail-001.md` — declarada con ticket propio porque exige diseño,
no port.

## Problema
`ProcessDeathAttributor` (androidMain, [DET-MEMORY-LIMITER-IS-AN-ATTRIBUTABLE-KILL-001]) estampa la
CAUSA de cada muerte de proceso como `DetectionEvent.ProcessDeath` vía
`ActivityManager.getHistoricalProcessExitReasons()`. En iOS no existe nada equivalente: una sesión
de detección que muere en background no deja rastro de POR QUÉ murió, y el argumento del KDoc
("un arranque que reporta `self_exit`, o ninguna muerte nueva, devuelve el bug a nuestro lado de la
red") no aplica en la mitad iOS del producto.

## Por qué no fue un port mecánico
- **No hay puerto común**: es una clase concreta androidMain inyectada por tipo, llamada solo desde
  `PaparcarApp.kt` (`:app`). El diseño actual no puede EXPRESAR un impl iOS — primero hay que
  promover un puerto a commonMain.
- **La API iOS es otra semántica**: lo más cercano es MetricKit (`MXAppExitMetric` /
  `MXCrashDiagnostic`), que distingue salidas normales, jetsam por presión de memoria y
  watchdog — pero entrega **next-day** por lotes, no al siguiente arranque. El "watermark de
  SharedPreferences + reporte al arrancar" de Android no se traduce 1:1.

## Diseño (a decidir con el user)
1. Puerto común (p. ej. `ProcessDeathReporter` en `domain/diagnostics/`) con la semántica mínima
   que ambas plataformas puedan honrar: "reporta las muertes NUEVAS desde el último reporte, con
   causa y timestamp aproximado".
2. Android: el impl actual detrás del puerto (sin cambio de comportamiento).
3. iOS: subscriber `MXMetricManager` que persiste los payloads y los vuelca como
   `DetectionEvent.ProcessDeath` en el siguiente drain — asumiendo y documentando el lag de un día.
4. El consumidor (`PaparcarApp` hoy) pasa a llamarse desde ambos bootstraps
   (`PaparcarIosBootstrap.ensureStarted()` ya existe como choke point iOS).

## Criterio de éxito
Una muerte por jetsam en iOS aparece en la telemetría de sesiones con causa, aunque sea al día
siguiente; el replay de Android no cambia ni un byte.
