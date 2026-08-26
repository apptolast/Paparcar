# DET-RETRACT-DENIED-FOREVER-001 · una retractación que Firestore niega 256 veces y nadie ve

**Estado:** 🔴 Abierto, sin código · hallado el 26-08 leyendo la captura
`diagnostics/2026-08-26/oppo-cph2371*.log` · **no bloquea el field-test**

## Qué se midió

En 5 días de traza continua del Oppo (uid `fiypNbElGlfFexLMpU9sNaMjRMD3`):

| Fichero | Ventana | Fallos |
|---|---|---|
| `oppo-cph2371.old.log` | 08-22 14:09 → 08-25 22:12 | **235** |
| `oppo-cph2371.log` | 08-25 22:12 → 08-26 21:12 | **21** |

**256 en total, y las 256 son el mismo spot y la misma operación:**

```
08-26 01:17:50.682 W PARKDIAG/RetractDeducedDeparture: retract failed for spot=a786c135 — the short TTL still bounds it
  com.google.firebase.firestore.FirebaseFirestoreException: PERMISSION_DENIED: Missing or insufficient permissions.
```

`grep -oE "retract failed for spot=[a-f0-9]+" | sort | uniq -c` → **una sola entrada**, `a786c135`.
Y `PERMISSION_DENIED` **no aparece en ninguna otra operación** de la app: 21 de 21 en el día son de
`RetractDeducedDeparture`. No es un problema general de reglas de Firestore; es este camino.

Cae siempre justo al **terminar** una sesión — detrás de un `takeWhile=false`, un
`⊘ false-ENTER abort` o un `⚑ no-movement guard hit`. Es la rama `[DET-HANDOFF-NOT-MANUAL-001 §B.3]`
del `finally` del coordinator: *"la sesión terminó y nunca midió conducción, así que la salida
deducida queda refutada → retirar la plaza provisional"*.

## Por qué importa aunque esté "manejado"

El fallo **no rompe nada**: es un `W`, va dentro de su `runCatching`, y la línea se consuela sola —
*"the short TTL still bounds it"*. Pero:

1. **No converge.** La retractación nunca ocurre, así que la condición que la dispara no se limpia
   nunca: 256 intentos en 5 días. Un bucle que no termina no es un fallo acotado, es uno silencioso.
2. **Se va a volver invisible justo ahora.** Las 256 salen de sesiones armadas contra `a786c135`, el
   pin del Kamiq — o sea, son el tercer efecto en cadena de
   `DET-BT-CAR-CANNOT-NOMINATE-A-COORDINATOR-SESSION-001`. Con ese fix en master (`5d6a941f`,
   instalado en el Oppo a las 20:54 del 26; **0 armados contra `a786c135` después de esa hora**) los
   reintentos deberían caer a casi nada. La incapacidad de retractar **se queda igual, pero deja de
   verse** — que es exactamente cómo un bug sobrevive a su síntoma.
3. **El consuelo del TTL es una suposición del cliente**, no una verificación. Nadie comprueba que la
   plaza haya caducado de verdad; se asume porque el TTL es corto.

## Lo que NO se sabe todavía

⛔ **No se ha mirado la causa**, y hay al menos tres candidatas que la traza no distingue:

- La plaza `a786c135` **nunca existió como documento remoto** (nace de un pin **manual** del 21-08),
  y las reglas niegan el borrado de algo que no está / no le pertenece a este uid.
- Las reglas de `spots` no contemplan el borrado por el publicador, sólo la creación.
- El id que se usa para retractar no es el id del documento de la plaza — nótese que el ticket
  `UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001` ya avisó de que **la plaza reutiliza a propósito el id
  de su sesión**, y que lo peligroso era resolver el TIPO por id.

Antes de tocar código: mirar `firestore.rules` y comprobar si el documento existe.

## Criterio de éxito

- O la retractación funciona, o **falla una vez y deja de reintentar**, dejando dicho por qué.
- Un `PERMISSION_DENIED` en esta vía deja rastro en el trace **remoto**, no sólo en el `parkdiag`:
  hoy es una rama que decide algo (retirar o no una plaza publicada) y sólo habla en local.
  Es el invariante de `DET-EVERY-TRIGGER-LEAVES-A-TRACE-001` aplicado a los efectos.

## Relacionado

- `DET-BT-CAR-CANNOT-NOMINATE-A-COORDINATOR-SESSION-001` — la causa de que se disparara 256 veces.
- `DET-HANDOFF-NOT-MANUAL-001 §B.3` — la rama que intenta la retractación.
- `UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001` — la relación id-plaza / id-sesión.
