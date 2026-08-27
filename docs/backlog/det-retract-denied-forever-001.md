# DET-RETRACT-DENIED-FOREVER-001 · una retractación que Firestore niega 256 veces y nadie ve

**Estado:** 🟢 Arreglado · **1.668 tests** (1.664 + 4) · hallado el 26-08 leyendo la captura
`diagnostics/2026-08-26/oppo-cph2371*.log` · ⏳ sin conducir

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

## Causa — diagnosticada el 26-08, cadena completa

**La plaza nunca se publicó, pero se marcó como si se hubiera publicado.**

`spots/a786c135-2500-42c4-8adc-dd7d695ae0d8` **no existe** en `pap-26` (comprobado por MCP:
`Document ... not found`). Y la traza dice que nunca llegó a existir:

```
08-23 04:10:34.759 D PARKDIAG/Depart: preconfirmed by parked-state reconcile — skipping live speed re-check (geof=a786c135)
08-23 04:10:34.760 D PARKDIAG/Depart: stale departure (age=60min) — clearing WITHOUT publishing (geof=a786c135)
08-23 15:45:45.702 W PARKDIAG/RetractDeducedDeparture: retract failed for spot=a786c135 …   ← y 255 más
```

**1 · El marcador se pone en una rama que decidió NO publicar.**
`RunDepartureCheckUseCase:159-165` calcula `publishSpot = exitAgeMs <= spotPublishMaxAgeMs`
([DET-RECONCILE-001], correcto: una salida vieja no debe anunciar una plaza que ya no está) y llama a
`processConfirmedDeparture(publishSpot = false)`. Dentro:

| Línea | Qué hace | Condición |
|---|---|---|
| `ProcessConfirmedDepartureUseCase:83` | **publica** la plaza | `publishSpot && !alreadyPublished && …` |
| `ProcessConfirmedDepartureUseCase:109-112` | **marca** `provisionalDepartureAtMs` | `proof == Deduced` — **y nada más** |

La publicación está condicionada; la marca **no**. Con `publishSpot = false` la sesión queda marcada
como "esta deducción ya gastó su publicación" sin que exista publicación ninguna.

**2 · Y el log lo afirma en falso.** La línea que se emite justo después (`:113-117`) dice
*"deduced departure — spot published PROVISIONALLY"* también cuando `publishSpot` era `false`. Un
diagnóstico que asegura un hecho que no ocurrió: mismo pecado que el bloque de traza inventado que se
corrigió en `6b9cf6df`, pero éste lo comete la app.

**3 · Por qué el error es `PERMISSION_DENIED` y no `NOT_FOUND`.** `retractSpot` es un **update**
(`SpotRepositoryImpl:125-133` — cambia estado + `expiresAt`; a propósito no es un delete, ver
`SpotStatus`). En `firestore.rules:22-30` **las dos ramas del `allow update` dereferencian
`resource.data`** (`resource.data.reportedBy`, y `diff(resource.data)` en la otra). Sobre un
documento que no existe, `resource` es nulo, así que la regla no puede evaluarse a `true` y Firestore
responde denegando. **El código de error enmascara la causa real**, que es "no está".

**4 · Por qué no para nunca.** `provisionalDepartureAtMs` no se limpia **por diseño** y está
documentado en `RetractDeducedDepartureUseCase:36-41`: limpiarlo dejaría a la red de seguridad
re-deducir la misma salida cada 15 min. Impecable para el caso que se publicó — pero convierte el
caso que **no** se publicó en un bucle infinito.

## Arreglo aplicado

**1 · La retirada se acota sola** (`RetractDeducedDepartureUseCase`). Pasado
`SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS` (12 min) no queda documento que retirar — la caducidad ya lo
hizo, que es justo lo que la rama de fallo llevaba afirmando. Ahora lo dice en una línea en vez de
escribir contra algo que no está. El reloj pasa a **inyectarse** (`nowMs`, igual que
`RunDepartureCheckUseCase`): leerlo inline es lo que hacía el límite intestable, y por eso nació sin
límite.

⚠️ **El marcador sigue sin limpiarse, a propósito.** No se puede: `FinalizeDeducedDepartureUseCase`
lo usa como "hay una deducción pendiente" para **liberar la sesión** si más tarde se mide conducción.
Borrarlo dejaría el coche aparcado para siempre. El defecto de fondo — **un campo respondiendo a dos
preguntas distintas** ("¿hay deducción pendiente?" y "¿hay plaza publicada ahí fuera?"), la misma
familia que `DET-DEPARTURE-IS-NOT-ARRIVAL-001` — **NO se ha reestructurado**: separarlo pide un campo
nuevo y, por tanto, esquema.

> **DECIDIDO 27-08: no se separa.** El bound se llevó el coste real — de 256 intentos en 5 días a
> como mucho un par dentro de la ventana de 12 min — y lo que queda es latente. Una columna nueva
> (más DTO, mapper, fakes y tests) no se paga con eso, ni siquiera aprovechando que Room acaba de
> quedarse en v1 y no costaría migración: sin usuarios, tampoco costará dentro de un mes.
> **Se reabre si el campo demuestra que el residuo muerde.**

**2 · El log deja de mentir** (`ProcessConfirmedDepartureUseCase`). La condición de publicar estaba
escrita **tres veces** en tres formas distintas (la rama, la línea local y el evento remoto — y la
copia remota se dejaba la comprobación de coordenadas). Ahora se decide una vez en `publishesNow` y
la leen los tres; la línea dice cuál de los dos casos ocurrió en vez de afirmar siempre
*"spot published PROVISIONALLY"*.

### Verificación — y qué NO cubre

| Test | Neutralización | ¿Discrimina? |
|---|---|---|
| `should_not_attempt_a_withdrawal_once_the_provisional_ttl_has_run_out` | quitar el bound | ✅ **rojo** |
| `should_still_withdraw_on_the_last_millisecond_of_the_provisional_window` | — | pin del borde |
| `should_not_claim_a_publication_when_the_departure_was_too_stale_to_publish_one` | volver a la condición vieja | ❌ **sigue verde** |

⛔ **Dicho sin adornos: el cambio 2 no está protegido por la suite.** Con `publishSpot = false` la
condición vieja y la nueva coinciden, así que el test es de caracterización, no discriminante; y lo
que de verdad mentía era **la cadena de log**, que la suite no mira (lección ya escrita en
`feedback_document_parking_detection_changes` y en el commit `6b9cf6df`). La única diferencia
observable que el cambio 2 corrige de verdad es el `published` del evento remoto cuando faltan
coordenadas — inalcanzable hoy, porque `location` no es nulo si la sesión existe.

### Dónde arreglarlo

El invariante es de una línea: **el marcador de "esta deducción gastó su publicación" no lo puede
poner una rama que se negó a publicar.** Es decir, `:109-112` debe compartir la condición de `:83`,
no ir suelto. La regla de Firestore **no** hay que tocarla: negar un update sobre algo que no existe
es correcto; el que miente es el cliente.

⚠️ Al arreglarlo, comprobar el otro consumidor del marcador (`FinalizeDeducedDepartureUseCase`) y la
línea de log del `:113-117`, que debe decir la verdad en las dos ramas.

## Criterio de éxito

- O la retractación funciona, o **falla una vez y deja de reintentar**, dejando dicho por qué.
- Un `PERMISSION_DENIED` en esta vía deja rastro en el trace **remoto**, no sólo en el `parkdiag`:
  hoy es una rama que decide algo (retirar o no una plaza publicada) y sólo habla en local.
  Es el invariante de `DET-EVERY-TRIGGER-LEAVES-A-TRACE-001` aplicado a los efectos.

## Relacionado

- `DET-BT-CAR-CANNOT-NOMINATE-A-COORDINATOR-SESSION-001` — la causa de que se disparara 256 veces.
- `DET-HANDOFF-NOT-MANUAL-001 §B.3` — la rama que intenta la retractación.
- `UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001` — la relación id-plaza / id-sesión.
