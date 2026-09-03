# SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001 · Entrar a avisar de una plaza sin sitio publicaba en el Golfo de Guinea

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash)

## Problema

Detectado al arreglar `UI-HOME-MUST-NOT-OFFER-WHAT-IT-CANNOT-DO-YET-001`, y anotado allí como
follow-up. La entrada al modo reporte se inventaba una coordenada cuando no tenía ninguna:

```kotlin
HomeIntent.EnterReportMode(
    lat = uiController.cameraLat ?: state.userGpsPoint?.latitude ?: 0.0,
    lon = uiController.cameraLon ?: state.userGpsPoint?.longitude ?: 0.0,
)
```

`0.0, 0.0` es el Golfo de Guinea. La ventana en la que se alcanza es pequeña —los primeros frames de
un arranque en frío, antes de que el mapa dibuje y antes del primer fix— pero **lo que produce es
permanente y ajeno**: una plaza publicada en mitad del océano, en el mapa de todos los demás.

## Doctrina violada

- **Una superficie no ofrece lo que no puede cumplir** [UI-HOME-MUST-NOT-OFFER-WHAT-IT-CANNOT-DO-YET-001].
- Y el agravante: esto no degrada la experiencia de quien lo pulsa, **ensucia el dato de terceros**.
  Un fallback silencioso es aceptable cuando el coste lo paga quien lo provoca; aquí lo paga la
  comunidad.

## Diseño

**Una sola resolución del punto de partida de un pin**, y `null` como respuesta legítima:

```kotlin
internal fun pinStartPoint(uiController, state): Pair<Double, Double>?   // cámara → último fix → null
```

- El handler de `RequestReportMode` no entra en modo si es `null`.
- El CTA «Avisar de una plaza» de la sheet se apaga mientras no hay sitio, así que el guard es el
  suelo y no el mensaje: no hay nada que explicarle a alguien que no pudo pulsar nada.
- El del checklist ya quedó cubierto por el ticket anterior.

## Criterio de éxito

- `PinStartPointTest`: cámara → cámara; sin cámara → fix; **sin ninguno → null**, que es el caso por
  el que existe el fichero.
- Ningún camino puede entrar en modo reporte con una coordenada que nadie eligió.

## Consumidores auditados

- `RequestReportMode` tiene tres orígenes: el CTA del final de la sheet, el estado vacío de plazas y
  el paso comunitario del checklist. Los tres pasan por el mismo handler, ahora guardado.
- `EnterAddParkingMode` conserva su `initialGps` nullable: entrar a marcar sin fix ya estaba cerrado
  por el CTA deshabilitado del ticket anterior, y su confirm rechaza un pin sin coordenadas
  (`reportPinMissing`). No se toca aquí.
