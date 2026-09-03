# Backlog — índice de lo que sigue abierto

> **346 ficheros en esta carpeta, 316 de ellos cerrados.** Este índice lista **solo lo que queda**,
> para que la pregunta *"¿qué falta?"* se conteste leyendo y no listando el directorio.
> Verificado contra master `6a04e119` el **2026-09-03** [DOCS-BACKLOG-TRUTH-002].
>
> **Los cerrados no se borran y no son ruido**: cada uno guarda *por qué existe un guard concreto*, y
> es lo que consultan [`../detection/PARKING-DETECTION.md`](../detection/PARKING-DETECTION.md) y la
> skill `det-change` antes de tocar detección. Para buscar uno: `grep -rl <TICKET-ID> docs/backlog/`.

## La regla: **todo doc lleva su línea de estado**

Desde el barrido del 03-09 **no queda ni un doc sin estado** (0 de 346), así que el directorio se
puede leer con un `grep`. Un doc nuevo sin esa línea es un doc que este índice no puede ver.

```bash
# ¿cuántos siguen abiertos? (la línea de estado, en sus DOS formas: al margen y en blockquote)
grep -rlE '^[ \t]*>?[ \t]*\*{0,2}(Estado|Status)' docs/backlog/*.md | wc -l   # → 346, todos
grep -rLE '✅|❌|🚫' $(grep -rlE '^[ \t]*>?[ \t]*\*{0,2}Estado' docs/backlog/*.md)  # → los no cerrados
```

⚠️ **Marcar cerrado con ✅, nunca con 🟢**: el barrido encontró cuatro docs terminados que decían
"🟢 Hecho" / "🟢 En master" y por eso salían en la lista de abiertos.

## Cómo se verifica que esta lista es verdad

Un doc puede mentir de dos formas y solo una se ve con un `grep` del estado:

```bash
# 1) lo que dice de sí mismo
grep -rniE '^[ \t]*>?[ \t]*\*{0,2}(Estado|Status)' docs/backlog/*.md

# 2) el chivato que no depende de la redacción: ¿existe la rama que cita?
git show-ref --verify --quiet refs/heads/<rama> || echo "rama borrada → el doc miente"
```

La (2) es la buena. El barrido del 30-08 encontró con ella **15 tickets** que decían *"sin commitear"*
/ *"EN CURSO en rama"* llevando semanas en master; el del 03-09 encontró **dos planes que citan ramas
que ya no existen** (`DET-AUDIT-REMEDIATION-001`, `audit-a12-001`) y **19 docs sin estado ninguno**.

---

## 🔴 Detección — abiertos con evidencia

| Ticket | Qué falla |
|---|---|
| [`det-blind-after-lost-park-001`](det-blind-after-lost-park-001.md) | perder un aparcamiento deja la app **ciega** para el viaje siguiente. ⚠️ **Auditado 03-09**: el honest close NO lo cubre (exige un pin previo que liberar) — la vía 1 pide una **decisión de producto** |
| [`det-lone-sample-is-not-a-drive-001`](det-lone-sample-is-not-a-drive-001.md) | un solo fix de velocidad abre la sesión entera. ⚠️ **Medido 03-09**: las 2 barras candidatas ya se implementaron y **las dos pierden un viaje real** (`CASA_GAP_ANCHOR`) — no repetir el experimento |
| [`det-bt-pin-grade-is-not-a-driving-threshold-001`](det-bt-pin-grade-is-not-a-driving-threshold-001.md) | un fix de 50 m coloca el pin a fiabilidad 0,95. ⚠️ **Auditado 03-09**: la opción barata es un **no-op** (BT acepta ≤50 m, la duda degrada >60 m) — lo que pide es bajar el suelo GLOBAL |
| [`det-coarse-fix-drive-proof-001`](det-coarse-fix-drive-proof-001.md) | un móvil con accuracy crónicamente mala no puede probar **nunca** una conducción. Su sub-hallazgo (*el resumen miente*) ✅ cerrado aparte |
| [`det-bike-departure-release-001`](det-bike-departure-release-001.md) | un paseo en bici sigue declarando que el coche se ha ido |
| [`det-bt-boarding-anchor-001`](det-bt-boarding-anchor-001.md) | distinguir *"aparcó a mi lado"* de *"pasó por mi lado"* |
| [`det-bt-veto-must-not-orphan-a-session-001`](det-bt-veto-must-not-orphan-a-session-001.md) | ¿puede cerrarse una sesión cuya vía dueña no corre? ⛔ premisa a medias, **diseño sin decidir** |
| [`det-a-bt-drive-leaves-no-trace-001`](det-a-bt-drive-leaves-no-trace-001.md) | un viaje BT no deja ruta ni distancia. ⛔ **diseño sin decidir a propósito** (decisión del user, 31-08) |
| [`det-a-walk-reporting-zero-is-still-a-walk-001`](det-a-walk-reporting-zero-is-still-a-walk-001.md) | 🧊 **APARCADO, no es tarea**: hoy el caso ya PREGUNTA, que es lo correcto |

## ⏸ Detección — bloqueados por MEDICIÓN, no por código

No esperan a un viaje: esperan a que vuelva a fallar lo que los originó.

| Ticket | Qué hace falta |
|---|---|
| [`det-broadcast-queue-stall-001`](det-broadcast-queue-stall-001.md) · [`det-heartbeat-lane-repair-001`](det-heartbeat-lane-repair-001.md) | que el **Oppo** vuelva a atascar su cola de broadcasts |
| [`det-pedal-cadence-cannot-convict-a-car-in-traffic-001`](det-pedal-cadence-cannot-convict-a-car-in-traffic-001.md) | otra cadencia real de coche lento en ciudad |
| [`det-bt-autonomous-repairing-android-17-001`](det-bt-autonomous-repairing-android-17-001.md) | un móvil con **Android 17** |
| [`det-resume-reconcile-001-2026-07-02`](det-resume-reconcile-001-2026-07-02.md) | ⚠️ **premisa CADUCADA** — releer antes de implementar nada |

## 🧱 Arquitectura y limpieza

| Ticket | Estado |
|---|---|
| [`arch-health-001`](arch-health-001.md) | 🔵 plan por fases · **F7 (split `:app`+`:shared`) ✅ ejecutada el 29-08** |
| [`DET-AUDIT-REMEDIATION-001`](DET-AUDIT-REMEDIATION-001.md) · [`audit-a12-001`](audit-a12-001.md) | 📋 planes de auditoría · ⚠️ **las ramas que citan ya no existen**; su contenido sigue vigente, sus punteros no |
| [`infra-datastore-migration-001`](infra-datastore-migration-001.md) | 📋 propuesto 10-08. No urgente |
| `PIPE-004` | ⏸ diferido — colapsar `EnrichParkingSessionWorker` + `UpdateParkingSessionAddressAndPlaceWorker`. ⚠️ **no tiene doc propio**: solo se le nombra en [`../refactors/PIPE-001-confirm-parking-pipeline.md`](../refactors/PIPE-001-confirm-parking-pipeline.md) |
| [`det-verdict-not-predicate-001`](det-verdict-not-predicate-001.md) | regla ya en `CLAUDE.md`; la consolidación del código sigue pendiente |

## 🧪 Salud de build / KMP

| Ticket | Qué falla |
|---|---|
| `db-a-room-expect-object-breaks-the-metadata-compilation-001` | `compileCommonMainKotlinMetadata` no compila. ⚠️ **su doc aún no está commiteado** (vive suelto en el árbol de la sesión de Play), así que este índice no puede enlazarlo |
| `test-a-kmp-suite-that-only-runs-on-jvm-is-half-a-suite-001` | `commonTest` nunca compiló para iOS. ⚠️ mismo caso: doc sin commitear |

## 🎨 UI · copy

| Ticket | Estado |
|---|---|
| [`bug-home-fab-padding-2026-06-05`](bug-home-fab-padding-2026-06-05.md) | padding negativo en `HomeMapFabsLayer` (Nothing Phone A001). 1 evento, sin reproducir: su propio doc dice priorizar **solo si reaparece** |
| [`vehicles-multimarker-2026-05-19`](vehicles-multimarker-2026-05-19.md) | 🟡 sprint PARCIAL — los marcadores existen; el resto quedó diferido |

## 📐 Specs sin arrancar (piden decisión de producto, no un fix)

| Ticket | Nota |
|---|---|
| [`ux-park-flow-001`](ux-park-flow-001.md) | placeholder POR DEFINIR · análisis hecho en [`ux-park-flow-001-analysis`](ux-park-flow-001-analysis.md) y [`home-flow-analysis`](home-flow-analysis.md) |
| [`snap-to-park-001`](snap-to-park-001.md) | sacar el ancla de dentro de edificios. Sin código y **sin rama** |
| [`zone-subscribe-001`](zone-subscribe-001.md) | *"avísame cuando haya plaza aquí"* — idea de producto, 0 commits |
| *(sin ticket)* | **estilo de mapa**: el default sigue siendo `MapType.TERRAIN` y el JSON de marca solo rinde sobre `NORMAL`. El defecto vive documentado en [`home-flow-analysis` §H2](home-flow-analysis.md) |
| [`moving-car-native-marker-2026-07-01`](moving-car-native-marker-2026-07-01.md) | spike; el fork de kmp-maps que salió de aquí **ya está en Maven Central** |

## 🍎 iOS

| Ticket | Estado |
|---|---|
| `IOS-F0-001` | 🟡 Fase 0 en la rama `feature/IOS-F0-001-fase0`, **sin mergear**. Su doc viaja en la rama |
| [`ios-social-login-001`](ios-social-login-001.md) | 🔵 bloqueado hasta tener un Mac |

## 🚀 Play Store (sesión paralela)

| Ticket | Estado |
|---|---|
| [`auth-a-sign-in-asks-for-consent-first-001`](auth-a-sign-in-asks-for-consent-first-001.md) | ⚠️ su doc dice *"🔵 En progreso · rama…"* pero **su commit ya está en master** (`91d35b7c`) — cerrar la línea |
| `auth-email-verification-as-an-opt-in-setting-001` | 🟡 idea anotada el 03-09, sin rama ni código. ⚠️ doc sin commitear todavía |

---

## 📚 Docs que NO son tickets

No tienen estado global porque no lo tienen que tener: son análisis de campo con su estado POR BUG
dentro, o histórico. Se listan para que nadie los cuente como trabajo pendiente.

[`detection-departure-bugs-2026-06-05`](detection-departure-bugs-2026-06-05.md) ·
[`parking-detection-real-world-2026-05-28`](parking-detection-real-world-2026-05-28.md) ·
[`detection-improvements-2026-05-27`](detection-improvements-2026-05-27.md) ·
[`home-flow-analysis`](home-flow-analysis.md) ·
[`ux-park-flow-001-analysis`](ux-park-flow-001-analysis.md)

## ✅ Cerrados que arrastran un ⏳ residual

No son trabajo pendiente: son confirmaciones que faltan. Se listan para que nadie los reabra por error.

| Ticket | Qué falta |
|---|---|
| [`det-reliability-001`](det-reliability-001.md) | F1–F3 en master (`7350f358`); **F4 sigue diferida** a propósito |
| [`det-memory-limiter-is-an-attributable-kill-001`](det-memory-limiter-is-an-attributable-kill-001.md) | implementado (`e4fcac6c`); falta **cosechar** un force-stop y un `am memory-limiter` en Android 17 |
| [`det-parkdiag-keep-more-history-001`](det-parkdiag-keep-more-history-001.md) | instalado solo en el Redmi — el Oppo se desenchufó a mitad |
| [`geo-cache-answers-nearby-001`](geo-cache-answers-nearby-001.md) | ⏳ sin ver en device |
| [`data-room-starts-at-version-one-001`](data-room-starts-at-version-one-001.md) | confirmación en device del downgrade — **ya medido** en `AppDatabaseDowngradeTest` |
| [`sync-reconcile-userparking-deferred-2026-07-03`](sync-reconcile-userparking-deferred-2026-07-03.md) | el reconcile cerrado; **Profile** sigue diferido |
| [`settings-remodel-001-followups-2026-07-03`](settings-remodel-001-followups-2026-07-03.md) | validación en plataforma, sin código |
| [`det-no-device-mute-in-remote-001`](det-no-device-mute-in-remote-001.md) · [`det-retract-denied-forever-001`](det-retract-denied-forever-001.md) | hechos; el doc existe para que la regla tenga dónde vivir |

## 🚧 Ojo: tickets cuyo doc **no está aquí**

Las ramas sin mergear llevan su `docs/backlog/<id>.md` **dentro de la rama**, así que este índice no
las ve. Están listadas en [`../ROADMAP.md § En vuelo`](../ROADMAP.md). Es el agujero que motivó
`IOS-SOCIAL-LOGIN-001`: *una spec que solo vive en una rama es una spec que el backlog no puede leer*.
