# Backlog — índice de lo que sigue abierto

> **285 ficheros en esta carpeta, 195 de ellos ✅ cerrados.** Este índice lista **solo lo que queda**,
> para que la pregunta *"¿qué falta?"* se conteste leyendo y no listando el directorio.
> Verificado contra master `46621e7f` el **2026-08-30** [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001].
>
> **Los cerrados no se borran y no son ruido**: cada uno guarda *por qué existe un guard concreto*, y
> es lo que consultan [`../detection/PARKING-DETECTION.md`](../detection/PARKING-DETECTION.md) y la
> skill `det-change` antes de tocar detección. Para buscar uno: `grep -rl <TICKET-ID> docs/backlog/`.

## Cómo se verifica que esta lista es verdad

Un doc puede mentir de dos formas y solo una se ve con un `grep` del estado:

```bash
# 1) el estado, en sus DOS formas (al margen y dentro de blockquote)
grep -rniE '^\s*>?\s*\*\*(Estado|Status)' docs/backlog/*.md

# 2) el chivato que no depende de la redacción: ¿existe la rama que cita?
git show-ref --verify --quiet refs/heads/<rama> || echo "rama borrada → el doc miente"
```

La (2) es la buena. El barrido del 30-08 encontró con ella **15 tickets** que decían *"sin commitear"*
/ *"EN CURSO en rama"* / *"CODE-COMPLETE"* llevando semanas en master.

---

## 🔴 Detección — con evidencia, listos para implementar

| Ticket | Qué falla |
|---|---|
| [`det-starved-hold-has-no-witness-001`](det-starved-hold-has-no-witness-001.md) | la rama que planta un pin **sin un fix detrás** no tiene un solo test. Sale de un `//FIXME` del user |
| [`det-bt-veto-must-not-orphan-a-session-001`](det-bt-veto-must-not-orphan-a-session-001.md) | ¿puede cerrarse una sesión cuya vía dueña no corre? ⛔ premisa a medias, **diseño sin decidir** |
| [`det-explained-ride-asks-no-other-car-001`](det-explained-ride-asks-no-other-car-001.md) | una salida ya atribuida a un coche sigue preguntando por otro |
| [`det-displacement-drive-must-survive-its-next-fix-001`](det-displacement-drive-must-survive-its-next-fix-001.md) | hay guard para refutar una parada por su traza, no para sostener la conducción |
| [`park-retracted-backfill-must-leave-no-pin-001`](park-retracted-backfill-must-leave-no-pin-001.md) | la plaza que la app se desdice sigue en el historial |
| [`det-bt-pin-grade-is-not-a-driving-threshold-001`](det-bt-pin-grade-is-not-a-driving-threshold-001.md) | la precisión con la que se CREE un fix no es la que decide que se condujo. Destapado el 30-08 |
| [`det-blind-after-lost-park-001`](det-blind-after-lost-park-001.md) | perder un aparcamiento deja la app **ciega** para el viaje siguiente |
| [`det-lone-sample-is-not-a-drive-001`](det-lone-sample-is-not-a-drive-001.md) | un solo fix de velocidad abre la sesión entera |
| [`det-a-walk-reporting-zero-is-still-a-walk-001`](det-a-walk-reporting-zero-is-still-a-walk-001.md) | 🧊 **APARCADO, no es tarea**: medido y **sin nada que implementar** — hoy el caso ya PREGUNTA, que es lo correcto. ⛔ abrir birth quitaría la pregunta y plantaría pines. Revisar al sacar diagnósticos |
| [`det-coarse-fix-drive-proof-001`](det-coarse-fix-drive-proof-001.md) | un móvil con accuracy crónicamente mala no puede probar **nunca** una conducción |
| [`det-bike-departure-release-001`](det-bike-departure-release-001.md) | un paseo en bici sigue declarando que el coche se ha ido |
| [`det-bt-boarding-anchor-001`](det-bt-boarding-anchor-001.md) | distinguir *"aparcó a mi lado"* de *"pasó por mi lado"* |
| [`det-edge-markers-to-the-tap-001`](det-edge-markers-to-the-tap-001.md) | los marcadores de flanco, a su dueño — **desbloqueado** |

## ⏸ Detección — bloqueados por MEDICIÓN, no por código

No esperan a un viaje: esperan a que vuelva a fallar lo que los originó.

| Ticket | Qué hace falta |
|---|---|
| [`det-broadcast-queue-stall-001`](det-broadcast-queue-stall-001.md) · [`det-heartbeat-lane-repair-001`](det-heartbeat-lane-repair-001.md) | que el **Oppo** vuelva a atascar su cola de broadcasts |
| [`det-pedal-cadence-cannot-convict-a-car-in-traffic-001`](det-pedal-cadence-cannot-convict-a-car-in-traffic-001.md) | otra cadencia real de coche lento en ciudad |
| [`det-bt-autonomous-repairing-android-17-001`](det-bt-autonomous-repairing-android-17-001.md) · [`det-memory-limiter-is-an-attributable-kill-001`](det-memory-limiter-is-an-attributable-kill-001.md) | un móvil con **Android 17** |

## 🧱 Arquitectura y limpieza

| Ticket | Estado |
|---|---|
| [`arch-health-001`](arch-health-001.md) | 🔵 plan por fases · **F7 (split `:app`+`:shared`) ✅ ejecutada el 29-08** |
| [`det-coordinator-no-optional-deps-001`](det-coordinator-no-optional-deps-001.md) · [`det-koin-module-verify-001`](det-koin-module-verify-001.md) | follow-ups de `DET-DI-DETECTION-MODULE-001` |
| [`infra-datastore-migration-001`](infra-datastore-migration-001.md) | 📋 propuesto 10-08. No urgente |
| `PIPE-004` | ⏸ diferido — colapsar `EnrichParkingSessionWorker` + `UpdateParkingSessionAddressAndPlaceWorker`. ⚠️ **no tiene doc propio**: solo se le nombra en [`../refactors/PIPE-001-confirm-parking-pipeline.md`](../refactors/PIPE-001-confirm-parking-pipeline.md) y en [`worker-bugs-2026-05-25`](worker-bugs-2026-05-25.md) |
| [`det-resume-reconcile-001-2026-07-02`](det-resume-reconcile-001-2026-07-02.md) | 📋 preparada, no empezada |

## 🎨 UI · copy · mock

| Ticket | Estado |
|---|---|
| [`mock-auth-screens-need-their-viewmodels-001`](mock-auth-screens-need-their-viewmodels-001.md) | 🔴 en `mock`, "Sign Up" mata la app (prod sana). Lo arregla publicar BaseLogin |
| [`copy-notification-layer-still-says-plaza-001`](copy-notification-layer-still-says-plaza-001.md) | la capa de notificaciones dice "plaza" para lo que es un APARCAMIENTO |
| [`ui-button-one-canonical-cta-001`](ui-button-one-canonical-cta-001.md) | tres botones de la app no pasan por el botón de la app |
| [`ui-approximate-zone-in-history-001`](ui-approximate-zone-in-history-001.md) | Home y el historial pintan un área como si fuera un punto |
| [`bug-home-fab-padding-2026-06-05`](bug-home-fab-padding-2026-06-05.md) | padding negativo en `HomeMapFabsLayer` (Nothing Phone A001) |

## 📐 Specs sin arrancar

| Ticket | Nota |
|---|---|
| [`ux-park-flow-001`](ux-park-flow-001.md) | placeholder POR DEFINIR · análisis hecho en [`ux-park-flow-001-analysis`](ux-park-flow-001-analysis.md) y [`home-flow-analysis`](home-flow-analysis.md) |
| [`snap-to-park-001`](snap-to-park-001.md) | sacar el ancla de dentro de edificios. Sin código y **sin rama** |
| [`zone-subscribe-001`](zone-subscribe-001.md) | *"avísame cuando haya plaza aquí"* — excluido de UI-SHEET-001 por no haber backend |
| *(sin ticket)* | **estilo de mapa**: el default sigue siendo `MapType.TERRAIN` y el JSON de marca solo rinde sobre `NORMAL`. `MAP-TYPES-001` se borró el 30-08 por describir un popup retirado; el defecto vive documentado en [`home-flow-analysis` §H2](home-flow-analysis.md) |
| [`moving-car-native-marker-2026-07-01`](moving-car-native-marker-2026-07-01.md) | spike; el fork de kmp-maps que salió de aquí **ya está en Maven Central** |

## 🍎 iOS

| Ticket | Estado |
|---|---|
| `IOS-F0-001` | 🟡 Fase 0 en la rama `feature/IOS-F0-001-fase0`, **sin mergear**. Su doc viaja en la rama |
| [`ios-social-login-001`](ios-social-login-001.md) | 🔵 bloqueado hasta tener un Mac |

---

## ✅ Cerrados que arrastran un ⏳ residual

No son trabajo pendiente: son confirmaciones que faltan. Se listan para que nadie los reabra por error.

| Ticket | Qué falta |
|---|---|
| [`det-reliability-001`](det-reliability-001.md) | F1–F3 en master (`7350f358`); **F4 sigue diferida** a propósito |
| [`det-parkdiag-keep-more-history-001`](det-parkdiag-keep-more-history-001.md) | instalado solo en el Redmi — el Oppo se desenchufó a mitad |
| [`geo-cache-answers-nearby-001`](geo-cache-answers-nearby-001.md) | ⏳ sin ver en device |
| [`data-room-starts-at-version-one-001`](data-room-starts-at-version-one-001.md) | confirmación en device del downgrade — **ya medido** en `AppDatabaseDowngradeTest` |
| [`sync-reconcile-userparking-deferred-2026-07-03`](sync-reconcile-userparking-deferred-2026-07-03.md) | el reconcile cerrado; **Profile** sigue diferido |
| [`settings-remodel-001-followups-2026-07-03`](settings-remodel-001-followups-2026-07-03.md) | validación en plataforma, sin código |
| [`det-no-device-mute-in-remote-001`](det-no-device-mute-in-remote-001.md) · [`det-retract-denied-forever-001`](det-retract-denied-forever-001.md) | hechos; el doc existe para que la regla tenga dónde vivir |

## 🚧 Ojo: tickets cuyo doc **no está aquí**

**Once** ramas sin mergear llevan su `docs/backlog/<id>.md` **dentro de la rama**, así que este índice
no las ve — diez de ellas son una **pila lineal** de detección. Están listadas en
[`../ROADMAP.md § En vuelo`](../ROADMAP.md). Es el agujero que motivó `IOS-SOCIAL-LOGIN-001`: *una
spec que solo vive en una rama es una spec que el backlog no puede leer*.
