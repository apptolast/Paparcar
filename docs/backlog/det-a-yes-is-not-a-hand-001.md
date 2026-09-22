# DET-A-YES-IS-NOT-A-HAND-001 · Un «sí» no es una mano

**Estado:** ✅ Done · mergeado en master el 22-09-2026

## Problema

Issue **#121**: *«revisa bien lo de aviso manual, ¿por qué? No saqué el móvil del bolsillo.»*

## El dato de campo

Sesión ACTIVA del usuario (`rndeveloper11501@gmail.com`, base `(default)`,
uid `0l7X59ptXeQ4WUMlpFOZHLPIJAI3`), leída el 22-09-2026:

```
users/…/parkingHistory/3d95ffc8-66d1-4de7-871e-46d29c9a4fa7
  detectionPath        "user"
  detectionReliability 1.0
  armEvidence          "verified_late"     ← la APP armó y vigiló
  spotType             HOME_GEOFENCE
  timestamp            21-09-2026 22:26    ← anoche
  isActive             true                ← es la que está en pantalla
```

`"user"` → `ParkingDetectionSource.Manual` → la ficha imprime **«Aviso manual»**.

⛔ **El documento se contradice a sí mismo**: `armEvidence = verified_late` dice que la app lo
detectó, y `detectionPath = user` dice que lo puso el usuario. Las dos cosas no pueden ser.

Y no es un pin suelto: **tres** con `"user"` en el corpus vivo — 18-09 15:28, 21-09 20:31 y el
activo de anoche.

## Doctrina violada — la tiene escrita el propio fichero

`DetectionPath.UserAnswered` dice, palabra por palabra:

> *«Answering the real question does NOT stamp it either, and that is correct:
> `onUserConfirmedParking()` only marks the session as confirmed (`confirmation.userSaidYes()`), and
> the coordinator then plants the pin with the path of HOW it was detected.
> **A "Yes" confirms a detection, it does not replace it.»***

Lo reemplaza. `UserConfirmStage.evaluate()` se dispara con `state.confirmation.userConfirmed` —el
flag exacto que pone ese `userSaidYes()`— y planta el pin con `pathLabel = "user"`.

⛔ **Y el mismo KDoc afirma que ese label no tiene escritor:**

> *«⚠️ Nothing writes this label today, and no pin is missing because of it… so `"user"` had already
> stopped being stamped long before that sheet was deleted on 2026-09-03.»*

Falso, y falsificable con los tres pines de arriba, todos posteriores al 03-09. La doc describía el
diseño pretendido y el código hacía lo contrario; nadie podía cazarlo porque **la afirmación vivía
en un comentario, que no se ejecuta**.

## Causa raíz

`ParkingDetectionSource` mete en un solo cajón dos hechos distintos:

| Paths | Qué pasó de verdad | Hoy |
|---|---|---|
| `manual`, `user_moved` | **la mano del usuario** puso o arrastró el pin | `Manual` |
| `user`, `nudge`, `nudge_*` | **la app lo nominó y el usuario dijo que sí** | `Manual` |

Siendo un enum, colapsarlos sólo puede hacerse **borrando** el segundo hecho — que es justo lo que
`DET-PIN-PROVENANCE-001` existe para conservar, y lo que `UserMovedPin` ya dejó escrito en su propio
KDoc: *«un trace que lee `manual` no distingue "la app nunca vio este park" de "la app lo vio y se
equivocó", y sólo lo segundo es un fallo de detección que perseguir»*.

Y al usuario se le dice que hizo algo que no hizo.

## Diseño

**Nace `ParkingDetectionSource.Confirmed`**: *la app lo detectó, el usuario lo confirmó*.

- `Manual` se queda para **la mano**: `manual` (colocado) y `user_moved` (arrastrado).
- `Confirmed` recoge **la respuesta**: `user` y `nudge`/`nudge_*`.
- Es un miembro más de un enum que ya consumen tres sitios con `when` exhaustivo, así que **ninguno
  compila sin clasificarlo** — la misma propiedad que `DetectionPath` buscaba al dejar de ser un
  String.

⛔ **No se toca `UserConfirmStage` ni el label `"user"`.** Lo que ese pin cuenta es cierto: la app
preguntó y el usuario contestó. El defecto no era el camino, era el CAJÓN en el que caía y la frase
que salía de él. Cambiar el label rompería además la lectura de los pines ya escritos.

⛔ **Se corrige el KDoc mentiroso** con el dato de campo, y pasa a nombrar a su escritor real.

## Consumidores auditados

`grep -rn "ParkingDetectionSource\." shared/src app/src`:

| Consumidor | Qué hace con `Manual` | Veredicto |
|---|---|---|
| `ParkingHistoryDetailScreen.DetectionRow` | imprime «Aviso manual» | **el bug** → rama nueva + copy |
| `ComputePeriodStatsUseCase:160` `autoDetectedCount` | `!= Manual` ⇒ cuenta como auto-detectado | **cambia, para NO cambiar**: ver abajo |
| `DetectionPath.<cada path>.source` | declara el cajón | **el arreglo** |
| `EnrichParkingSessionWorker.isPinToPinEligible` | ramifica por **PATH**, `when` exhaustivo | exento — no mira `source` |
| `ParkingDetectionSourceTest`, `DetectionPathTest` | fijan el mapeo | actualizados + testigo nuevo |

### ⛔ La estadística: el cambio que existe para que NADA cambie

`autoDetectedCount` cuenta `source != Manual`. Con `Confirmed` fuera de `Manual`, una sesión que la
app **tuvo que preguntar** pasaría a contar como detectada sola, y el tile «auto-detectado %» se
inflaría.

Eso sería mentir en la otra dirección. Si la app preguntó, **no lo detectó sola**. La condición pasa
a excluir los dos cajones, así que la cifra que ve el usuario es idéntica a la de antes — y ahora
por una razón escrita, no por un empate accidental entre dos hechos distintos.

## Criterio de éxito

- La ficha de un pin nacido de un «sí» deja de decir «Aviso manual».
- La ficha de un pin colocado o arrastrado a mano lo sigue diciendo.
- El tile de «auto-detectado %» da el MISMO número que antes del cambio.
- El KDoc de `UserAnswered` nombra a su escritor real.
