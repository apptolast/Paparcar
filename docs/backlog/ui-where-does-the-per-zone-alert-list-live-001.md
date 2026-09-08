# UI-WHERE-DOES-THE-PER-ZONE-ALERT-LIST-LIVE-001 · ¿la elección de QUÉ zonas avisan se queda en cada zona o sube a Ajustes?

**Estado:** 🔵 Abierto — **decisión de producto pendiente**, sin código y sin rama. No es deuda
técnica: hoy la función está completa y accesible; la pregunta es dónde debe vivir cuando haya más
de un tipo de aviso.

## Origen

Follow-up declarado fuera de alcance al cerrar
[`UI-ZONE-ALERTS-ARE-CONFIGURED-IN-SETTINGS-001`](ui-zone-alerts-are-configured-in-settings-001.md)
(master `bf85acde`, 09-09-2026), donde se retiró la campana de la cabecera de Home porque —dicho por
el user— *habrá más tipos de aviso y todos se configurarán desde Ajustes*.

> Se abre esta ficha porque el follow-up se había quedado **solo dentro del doc del ticket cerrado**,
> y un pendiente que no es un fichero del backlog no se puede encontrar con un `grep`. Es la misma
> regla que este repo ya tiene escrita: *tarea pendiente = rama + doc de backlog*.

## Estado actual (no es un hueco)

Los avisos de plaza en tus zonas se gobiernan hoy en **dos sitios, y ambos funcionan**:

- **maestro** — Ajustes → Notificaciones (`settings_notif_zone_alerts`), soft-gate a Plus, leído por
  `ObserveZoneSpotAlertsUseCase`
- **por zona** — el modal de cada zona (`ZoneNotifyToggle` → `SetZoneNotify`), que escribe
  `Zone.notifyOnSpot`

La lectura que sostiene el reparto actual: **la zona se configura donde se edita la zona**. Su radio,
su icono, su privacidad y su aviso viven juntos, y no hace falta ir a otra pantalla para tocar uno.

## La decisión

Cuando exista el **segundo** tipo de aviso, ¿qué pasa con la lista por-zona?

- **(a) Se queda donde está.** Ajustes gobierna *si* la clase de aviso suena; cada zona gobierna *si
  ella participa*. Dos niveles, cada uno donde su objeto vive. Cero trabajo.
- **(b) Sube a Ajustes** como pantalla propia (Ajustes → Avisos), con la lista de zonas y su switch.
  Centraliza, pero **duplica** el control que ya está en el modal de la zona — y duplicar el sitio
  donde se escribe un flag es justo lo que se acaba de eliminar al retirar la campana.
- **(c) Sube a Ajustes y SALE del modal de la zona.** Sin duplicado, pero configurar una zona pasa a
  requerir dos pantallas.

⚠️ Antes de elegir (b), releer por qué murió la campana: no fue por estar en Home, fue por ser una
**tercera superficie** para un flag que ya se escribía desde donde vive la zona.

## Criterio de éxito

No lo hay hasta que exista el segundo tipo de aviso: hoy no hay nada que centralizar. Esta ficha
existe para que la pregunta esté escrita cuando llegue, no para hacer trabajo ahora.

## Relacionado

- `UI-ZONE-ALERTS-ARE-CONFIGURED-IN-SETTINGS-001` — la retirada de la campana y la auditoría de
  Ajustes (de las 14 notificaciones de `AppNotificationManager`, solo dos deben poder silenciarse y
  las dos tienen interruptor)
- `ZONE-SUBSCRIBE-001` — la suscripción por zona en sí; queda abierta su decisión del **TTL**
