# DET-PARKDIAG-KEEP-MORE-HISTORY-001 · una sola rotación no era una política de retención

**Estado:** 🟢 En master · ⏳ instalado sólo en el **Redmi** (el Oppo se desenchufó a mitad)

## Problema

`FileAntilog` guardaba **una** rotación: al pasar de 5 MB, `parkdiag.log` → `parkdiag.log.old`, y lo
anterior se borraba. Eso no es retención, es una moneda al aire: la ventana que sobrevive depende de
cuándo se llene el fichero, no de cuándo ocurrió el incidente.

**Dos veces en la misma semana la evidencia del incidente investigado ya se había caído por el final:**

| Fecha | Qué se perdió | Con qué hubo que trabajar |
|---|---|---|
| 24-08 | El `parkdiag` del FP del semáforo del hospital (Xiaomi) | El fichero del móvil, sacado a mano — ese uid **no tiene `diagnostics_config`**, así que a Firestore no llegó nada |
| 25-08 | El `parkdiag` del FN del Oppo ya había rotado | Firestore, que **no lleva todo lo que lleva el log del device** |

El 25-08 el trace del replay `Trace_Gondola2508Supersede.kt` lo dice en su propio KDoc: *"The device's
own `parkdiag.log` had already rotated when this was pulled"*.

## Doctrina

No cambia ninguna decisión de detección — cambia el **instrumento**. Pero es la misma familia que
`DET-EVERY-TRIGGER-LEAVES-A-TRACE-001`: una rama que decide una sesión y no deja rastro legible es
indiagnosticable, y da igual que el rastro no se escribiera nunca o que se escribiera y se borrara
antes de leerlo. El coste asimétrico también aplica: 25 MB de almacenamiento privado no valen nada al
lado de perder el único viaje que importaba.

## Cambio

`FileAntilog` desplaza generaciones en vez de tirar la única que hay:

```
parkdiag.log.4 → .5     (la .5 vieja es lo único que se descarta)
parkdiag.log.3 → .4
…
parkdiag.log   → .1
```

- `keptRotations = 5` (constante en el `companion object` privado, parametrizable por constructor).
- `parkdiag.log.1` es la rotación **más reciente**; `.5` la más vieja.
- 5 MB × 6 ficheros ≈ **150 h** de tráfico PARKDIAG, frente a ~30 h antes.

**Legacy:** un móvil actualizado en sitio puede conservar un `parkdiag.log.old` de un build anterior.
No se lee ni se escribe nunca más, y **no se borra solo** — borrar evidencia en silencio sería el
mismo error en pequeño. Se documenta para que se saque una vez y se limpie a mano.

## Barrido de consumidores

Invariante: *cómo se llama el fichero rotado y cómo se saca del móvil*.

| Sitio | Clasificación |
|---|---|
| `androidMain/…/logging/FileAntilog.kt` — rotación + KDoc EN y ES | ✅ **cerrado** (es el origen) |
| `diagnostics/README.md` — pull PowerShell, pull Bash, limpiar | ✅ **cerrado**, los tres bloques |
| `Trace_*.kt` (4 ficheros) | ⬜ exentos: citan `files/parkdiag.log` (el activo), que no cambia de nombre |
| `docs/backlog/det-2208-trips-become-replays-001.md` | ⬜ exento: relato histórico, cita el activo |

`grep` de `parkdiag.log.old` tras el cambio: sólo el aviso de legacy que lo explica.

## Cómo se saca ahora

El comando por defecto ya no es "el activo y a ver si hay .old", es **el historial entero en orden
cronológico en un solo fichero** — un incidente puede caer justo en el corte de una rotación:

```bash
adb shell run-as io.apptolast.paparcar sh -c \
  'cat files/parkdiag.log.5 files/parkdiag.log.4 files/parkdiag.log.3 \
       files/parkdiag.log.2 files/parkdiag.log.1 files/parkdiag.log 2>/dev/null' \
  > parkdiag-full.log
```

Limpiar antes de un test: `adb shell run-as io.apptolast.paparcar sh -c 'rm -f files/parkdiag.log*'`
(se lleva también el `.old` legacy).

## Criterio de éxito

- Campo: tras varios días sin sacar logs, sigue existiendo la ventana del incidente.
- Verificable a ojo en el móvil: `adb shell run-as io.apptolast.paparcar ls files/` debe llegar a
  mostrar `parkdiag.log.1` … `.5` conforme se vayan llenando.

## Pendiente

- ⏳ **Instalar en el Oppo.** Se desenchufó a mitad de la instalación del 26-08 y se quedó con el
  build anterior: su `parkdiag` sigue con una sola rotación.
- El agujero hermano sigue abierto y es de datos, no de código: **los uids sin
  `diagnostics_config`** no mandan nada a Firestore (la Xiaomi hoy, y cualquier cuenta nueva tras el
  reset de Room). Este ticket sólo arregla el lado local.
