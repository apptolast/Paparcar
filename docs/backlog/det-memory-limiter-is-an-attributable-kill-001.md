# DET-MEMORY-LIMITER-IS-AN-ATTRIBUTABLE-KILL-001 · Por qué murió el proceso, preguntado al sistema

**Estado:** ✅ Done · rama `feature/DET-MEMORY-LIMITER-IS-AN-ATTRIBUTABLE-KILL-001-exitinfo`
(el hash del merge vive en `MEMORY.md`; este doc viaja dentro de ese commit)

## Resolución

Tal y como pedía el esbozo de diseño, con tres desviaciones que se explican solas:

- **`ProcessDeathAttributor`** (`androidMain/diagnostics/`, vecino de `AndroidDeviceInfoProvider`),
  registrado en `androidDetectionModule` e invocado desde `PaparcarApp.onCreate`. Consulta
  `getHistoricalProcessExitReasons` (API 30+), filtra al proceso PRINCIPAL, deduplica contra un
  watermark persistido (el timestamp del exit más nuevo ya reportado) y emite **un evento por
  muerte, exactamente una vez** entre arranques.
- **Evento nuevo `DetectionEvent.ProcessDeath`** → `PROCESS_DEATH` en el wire, colgado del **ledger
  diario de triggers** (padre real, barrible por el sweep). Solo columnas existentes: vocabulario en
  `reason`, registro crudo de plataforma en `source`, edad de la muerte en `enterAgeMs`.
- **Vocabulario ampliado** sobre el esbozo (decía "a confirmar al implementar"): `force_stop` ·
  `low_memory` · `memory_limiter` · `crash` · `anr` + **`excessive_resource`**, **`self_exit`** (el
  negativo valioso: el proceso NO fue matado — devuelve el FN a nuestro tejado) y **`other`** (con
  el registro crudo siempre en `detail`, nada se pierde). `unknown` queda para lo que el esbozo
  decía: por debajo de API 30 el log local lo DICE en vez de fingir, y no se emite nada remoto.
- El caso "no hay muertes nuevas" también **se afirma** en el parkdiag ("no unreported process
  deaths") — la mitad más valiosa del criterio de éxito, dicha explícitamente.

Tests (`ProcessDeathAttributorTest`, 7): tabla de atribución completa (incl. el string
`MemoryLimiter:AnonSwap` de Android 17 y su degradación a `other`), watermark estricto, orden
cronológico de emisión, un-evento-por-muerte entre dos arranques, y el negativo. El
`KoinModuleVerifyTest` de la tarea anterior verifica el binding nuevo sin tocar nada.

## Consumidores auditados

- `DetectionEvent` exhaustivo: `typeName()` y `toDto()` tienen su rama (el compilador lo fuerza);
  `accumulate()` del logger lo ignora a propósito (no aporta rollup de sesión).
- `ForceStopConfirmed` (ApplicationStartInfo, 16+, gated por sesión) y `BackgroundKillSuspected`
  (heurística de heartbeat) **se quedan**: tres carriles complementarios, documentados en
  `PARKING-DETECTION.md`.
- Criterios de campo pendientes de cosechar (no bloquean el cierre): force-stop manual en el Oppo →
  `force_stop` en el siguiente arranque; `am memory-limiter manual` en un device con Android 17 →
  `memory_limiter`.

## Problema

Llevamos meses atribuyendo falsos negativos a "el OEM mató el proceso" **sin poder demostrarlo**.
El diagnóstico de campo distingue hoy entre Doze, kill del fabricante y muerte por memoria… por
descarte, mirando huecos en el `parkdiag`. Un hueco no dice quién lo hizo.

`reference_market_research_parking_detection` resume el estado del arte: el deep-kill de los OEM
equivale a un `force-stop`, y las geocercas se borran y hay que re-registrarlas. Pero saber que eso
*existe* no es lo mismo que saber que *pasó anoche en el Oppo*.

Y ahora hay una causa más que antes no existía: **Android 17 introduce límites de RAM por
dispositivo** y mata apps que los exceden. Un servicio de detección en primer plano durante un viaje
largo es exactamente el perfil que puede tocar ese techo.

## Lo que cambia: el sistema por fin lo cuenta

`ApplicationExitInfo` permite preguntar, al arrancar, **por qué murió el proceso anterior**:

- `getHistoricalProcessExitReasons(...)` devuelve el histórico de salidas.
- `REASON_USER_REQUESTED` / `REASON_USER_STOPPED` ⇒ force-stop, que es lo que hace el deep-kill OEM.
- `REASON_LOW_MEMORY`, `REASON_CRASH`, `REASON_ANR`, `REASON_EXCESSIVE_RESOURCE_USAGE` ⇒ causas
  distinguibles.
- **Android 17**: `REASON_OTHER` con `getDescription()` conteniendo `"MemoryLimiter:AnonSwap"` ⇒
  muerte por el límite de RAM nuevo.

Verificado sobre el árbol actual: **no lo usamos en ningún sitio.**

```
grep ApplicationExitInfo | getHistoricalProcessExitReasons  →  0 hits
```

## Doctrina en juego

*Un parking perdido con datos es un bug NUESTRO.* La contrapartida honesta es que un parking perdido
**porque el sistema nos mató** no lo es — pero hoy no podemos separar los dos casos, así que cada FN
arrastra la duda. Esto convierte una hipótesis recurrente en un dato citable.

También encaja con `DET-HEARTBEAT-MISS-IS-EVIDENCE-001` (`0a0832cf`): allí se decidió que **la
ausencia de una señal es evidencia**. Aquí se da un paso más — la ausencia deja de ser anónima y pasa
a tener causa.

## Diseño (esbozo, a confirmar al implementar)

1. Un lector en `androidMain/diagnostics/` que al arrancar consulte las salidas desde el último
   arranque registrado. Vecino natural de `AndroidDeviceInfoProvider.kt`.
2. Traducir cada salida a un vocabulario de diagnóstico **citable**, igual que `detectionPath` o
   `sessionOutcome`: `force_stop`, `low_memory`, `memory_limiter`, `crash`, `anr`, `unknown`.
3. Emitirlo al `parkdiag` local y a Firestore, de modo que al abrir una sesión con un hueco se pueda
   leer directamente **qué lo causó** en vez de deducirlo.
4. ⛔ **No** es un caso de uso nuevo por sí solo: es una señal que alimenta el diagnóstico. Si acaba
   cambiando un veredicto (p. ej. "no preguntes al usuario, te mataron"), *entonces* sí.
   Ver `feedback_usecase_per_verdict`.

## Criterio de éxito

- Tras un force-stop manual en el Oppo, el siguiente arranque registra `force_stop` con su timestamp.
- Tras una muerte por memoria en Android 17, registra `memory_limiter`.
- Un FN de campo puede citar la causa de la muerte, o decir explícitamente que el proceso **no**
  murió — que es la mitad más valiosa: descarta la excusa y devuelve el bug a nuestro tejado.

## Notas de alcance

- **`minSdk 26`**: `ApplicationExitInfo` es API 30+. Por debajo, la señal simplemente no existe y el
  vocabulario debe decir `unknown` en vez de fingir.
- La descripción `"MemoryLimiter:AnonSwap"` es una **cadena**, no una constante de la API: si cambia
  en una release futura, la detección degrada a `REASON_OTHER` genérico. Aceptable, pero que quede
  escrito para que nadie la trate como contrato.
- Android 17 trae comandos `adb` para forzar el caso y no tener que esperarlo:
  `am memory-limiter manual <pid> <limit>`, `am memory-limiter status`. Eso hace el criterio de éxito
  **medible sin depender de la suerte**.
