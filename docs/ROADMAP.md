# Paparcar — Roadmap

> **Doc vivo.** Responde a *"¿qué queda por hacer?"* sin auditar `git log` primero.
> Verificado contra master `46621e7f` el **2026-08-30**: cada línea 🚧/📋 se comprobó con
> `git log master --grep=<TICKET-ID>`, distinguiendo un commit de código de un `docs(backlog):` que
> solo ABRE el ticket. Sustituye a `Paparcar_Roadmap_*.md` (en `docs/archive/`).
>
> ⛔ **Regla de este doc y de todos los vivos:** cabecera con **fecha + commit** contra el que se
> verificó. Un doc sin eso no se distingue de uno que nadie ha mirado en tres meses.
> [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]

---

## Dónde está el proyecto

**Pre-lanzamiento, en field-testing diario.** El producto está completo y en master; lo que se hace
cada día es medir la detección en dos móviles reales (Oppo + Redmi) con telemetría remota, y cerrar
el fallo que salga. Política *fix-forward*.

Lo que **falta para publicar** no es código de producto: es la ficha de Play, las verificaciones de
cuenta de desarrollador y el endurecimiento de claves (§ *Bloqueantes de lanzamiento*).

| Área | Estado |
|---|---|
| Detección dual (BT determinista + Coordinator AR-first) | ✅ En master, field-test continuo |
| Procedencia del pin (`detectionPath` + `armEvidence` en cada sesión) | ✅ |
| Red de seguridad (worker 15 min + reconcile de salidas perdidas) | ✅ |
| Sync offline-first + reconcile LWW (vehículos, zonas, sesiones) | ✅ |
| Votos de comunidad sobre plazas (retirar / rejuvenecer) | ✅ `1781af0a` |
| Frescura de plaza por EDAD + TTL 2 h | ✅ `d0fb3427` |
| Design system: color por método, tipografía por rol, guardarraíles Konsist | ✅ |
| Dev Catalog (flavor `mock`, sin backend) | ✅ |
| Telemetría de diagnóstico remota (Firestore, con gate) | ✅ |
| Split `:app` + `:shared` | ✅ `b949efa1` |
| Puck del mapa: es del VIAJE, no del carril que lo detectó | ✅ `46621e7f` |
| CI: build + tests + **compilación iOS en `macos-latest`** | ✅ `02a29f62` |
| Superficie legal (política de privacidad, borrado de cuenta por web, data safety) | ✅ código; ⏳ acciones de cuenta |
| Ficha de Play (9 idiomas, icono, gráfico) | 🟡 escrita, **sin commitear**; faltan capturas |
| Detección iOS | 🟡 Fase 0 en rama sin mergear; nativos listos, wiring pendiente |

---

## 🚧 En vuelo — ramas sin mergear

> ⚠️ **Estas ramas llevan su propio `docs/backlog/<id>.md` dentro**, así que el backlog de master
> **no las ve**. Es el mismo agujero que motivó `IOS-SOCIAL-LOGIN-001`: una spec que solo vive en una
> rama es una spec que el backlog no puede leer. Aquí quedan listadas para que no se pierdan.

### La pila de detección — 10 ramas, cada una contiene a la anterior

Verificado con `git merge-base --is-ancestor` eslabón a eslabón: es **una sola cadena lineal**, no
diez ramas paralelas. Mergear la de más abajo arrastra todas las de arriba.

| # | Ticket | Qué hace |
|---|---|---|
| 1 | `DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001` | el confirm silencioso pregunta **qué es** el armado, no recita la lista de armados que ya nos quemaron |
| 2 | `DET-DRIVING-EVIDENCE-VALUE-OBJECT-001` | un solo veredicto contesta *"¿condujo?"*, con el listón **medido** en vez de supuesto |
| 3 | `DET-NO-CLOCK-PLANTS-A-PIN-001` | un reloj agotándose no es evidencia; la zona se centra en el reposo que vio la sesión |
| 4 | `DET-THE-EVIDENCE-MUST-REACH-THE-TRACE-001` | una línea de diagnóstico que nombra la causa equivocada es peor que ninguna |
| 5 | `DET-STARVED-PROMPT-HAS-NO-WITNESS-001` | un prompt cuyo stream muere **igualmente llega a un veredicto** |
| 6 | `DET-DOUBT-REACHES-REMOTE-001` | la duda viaja con el pin, o ningún diagnóstico remoto puede verla |
| 7 | `DET-DETECTION-PATH-IS-A-TYPE-001` | la procedencia de un pin es un **tipo**, no un string que tres sitios re-parsean |
| 8 | `DET-FAIL-CLOSED-BY-CONSTRUCTION-001` | lo que no se puede medir **no puede contar como medido** |
| 9 | `DET-TWO-TIER-SENTRY-001` | un despertar compra **un fix, no una sesión** |
| 10 | `DET-DOUBT-MUST-REACH-THE-SCREEN-001` | la app medía la duda, la guardaba… y luego dibujaba un punto |

Ramas: `feature/<TICKET>-<slug>`. La cabeza de la pila es la #10
(`…-doubt-on-screen`, 11 commits sobre master).

### Independientes

| Rama | Ticket | Estado |
|---|---|---|
| `feature/IOS-F0-001-fase0` | `IOS-F0-001` | Fase 0 del port iOS: puertos, capacidades y harness. 🔵 lo valida un compañero con Mac |

---

## 📋 Abierto en el backlog

Verificado uno a uno: aparecen en `git log` **solo** por su commit `docs(backlog):` de apertura.

### Detección — con evidencia, esperando implementación
| Ticket | Qué falla |
|---|---|
| `DET-STARVED-HOLD-HAS-NO-WITNESS-001` | 🔴 la rama que planta un pin sin fix detrás no tiene test |
| `DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001` | el prompt sale del gate BT y el log nombra otra causa — ⛔ diseño sin decidir |
| `DET-EXPLAINED-RIDE-ASKS-NO-OTHER-CAR-001` | un trayecto ya explicado sigue preguntando por otro coche |
| `DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001` | la conducción por desplazamiento no sobrevive al fix siguiente |
| `PARK-RETRACTED-BACKFILL-MUST-LEAVE-NO-PIN-001` | un backfill retractado no debe dejar pin |
| `DET-BT-PIN-GRADE-IS-NOT-A-DRIVING-THRESHOLD-001` | destapado por `DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001` (30-08) |
| `DET-BLIND-AFTER-LOST-PARK-001` | ciego tras perder un park |
| `DET-BIKE-DEPARTURE-RELEASE-001` | follow-up de `DET-BIKE-NOT-A-CAR-001` |
| `DET-COARSE-FIX-DRIVE-PROOF-001` · `DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001` · `DET-BT-BOARDING-ANCHOR-001` | hallazgos de análisis, sin código |
| `DET-EDGE-MARKERS-TO-THE-TAP-001` | desbloqueado — su prerequisito ya está |

### Detección — bloqueados por MEDICIÓN, no por código
No esperan a un viaje: esperan a que vuelva a fallar lo que los originó.

- `DET-BROADCAST-QUEUE-STALL-001` + `DET-HEARTBEAT-LANE-REPAIR-001` — esperan a que el **Oppo** falle otra vez.
- `DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001` — necesita otra cadencia real.
- `DET-BT-AUTONOMOUS-REPAIRING-ANDROID-17-001` + `DET-MEMORY-LIMITER-IS-AN-ATTRIBUTABLE-KILL-001` — necesitan un móvil con **Android 17**.

### Arquitectura y limpieza
| Ticket | Estado |
|---|---|
| `ARCH-HEALTH-001` | 🔵 plan por fases · **F7 (split `:app`+`:shared`) ✅ ejecutada el 29-08** |
| `DET-COORDINATOR-NO-OPTIONAL-DEPS-001` · `DET-KOIN-MODULE-VERIFY-001` | follow-ups de `DET-DI-DETECTION-MODULE-001` |
| `INFRA-DATASTORE-MIGRATION-001` | 📋 propuesto 10-08, no urgente |
| `PIPE-004` | ⏸ diferido — colapsar `EnrichParkingSessionWorker` + `UpdateParkingSessionAddressAndPlaceWorker` |

### UI / copy / mock
| Ticket | Estado |
|---|---|
| `MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001` | 🔴 en `mock`, "Sign Up" mata la app (prod sana). Lo arregla publicar BaseLogin |
| `COPY-NOTIFICATION-LAYER-STILL-SAYS-PLAZA-001` | 🟡 la capa de notificaciones usa "plaza" para lo que es un APARCAMIENTO |
| `UI-BUTTON-ONE-CANONICAL-CTA-001` | follow-up de `UI-TYPE-SYSTEM-HYGIENE-001` |
| `UI-APPROXIMATE-ZONE-IN-HISTORY-001` | la zona aproximada no se dibuja en el historial |

### iOS
| Ticket | Estado |
|---|---|
| `IOS-F0-001` | 🟡 Fase 0 en rama `feature/IOS-F0-001-fase0`, sin mergear |
| `IOS-SOCIAL-LOGIN-001` | 🔵 bloqueado hasta tener un Mac |
| BGTaskScheduler + App Distribution iOS | 📋 ver [`IOS_PLAN.md`](./IOS_PLAN.md) |

---

## 🔴 Bloqueantes de lanzamiento (acciones del usuario, no código)

1. **Google Play Console** — declarar *trader status*, completar verificaciones de identidad y fijar
   el nombre legal. Sin esto no hay publicación, con la app terminada o no.
2. **Ficha de Play** — copy en 9 idiomas + icono + gráfico destacado están escritos pero **sin
   commitear**; faltan las **capturas**. ⛔ La ficha promete solo *"sabe dónde aparcaste"*: no se
   promete detección infalible.
3. **Clave de Maps** — rotarla y aplicar restricciones en GCP (package + SHA-1 debug/release, y solo
   *Maps SDK for Android*). Checklist en [`release/RELEASE-SECURITY.md`](./release/RELEASE-SECURITY.md).
4. **Reglas de Firestore** — desplegar y probar las mínimas documentadas en `RELEASE-SECURITY.md §2`.
   ⚠️ el MCP `firebase_deploy` es un **no-op silencioso**: hay que usar la CLI.
5. **Dominio y correo** — `paparcar.com` comprado; falta conectarlo a Hosting y barrer del repo el
   Gmail personal.
6. **Room** — el primer release público convierte v1 en línea base real: a partir de ahí **todo cambio
   de esquema exige `Migration` + schema exportado**. Hoy sigue el destructivo.

---

## 🔮 Futuro (post-lanzamiento, no ahora)

- **Widget Android** del aparcamiento activo (dirección + tiempo transcurrido + tap al mapa).
- **Android Auto** — soltar la plaza desde el infotainment al desconectar.
- **WearOS / Apple Watch** — countdown del TTL de la última plaza publicada.
- **Detección vía CarPlay / Android Auto pairing** — señal tan fuerte como el BT, para quien no
  empareja el móvil pero sí conecta multimedia.
- **Modo "voy de camino"** con ETA, apoyado en el `enRouteCount` que ya existe en el modelo.
- **4º tab Comunidad/Perfil** — posible, deliberadamente aplazado.
- **Modularización** más allá de F7 — solo si el monolito escala mal.
- **Detección server-side / ML** con eventos confirmados y denegados (privacy-first).

---

## Convenciones

Ramas `feature/` · `bugfix/` · `refactor/` · `chore/` · `experiment/` + `<TICKET-ID>-<slug>`, con ID
**autoexplicativo**. Commits Conventional con el ticket entre corchetes:

```
feat(home): implement bottom sheet with nearby spots [HOME-002]
```

Un ticket = **un** commit en master (`--squash`), y su doc de backlog viaja **dentro** de ese commit.
Flujo completo en la skill `nuevo-ticket`. Backlog vivo en [`backlog/README.md`](./backlog/README.md)
— índice de lo abierto, con el método para verificar que la lista no miente.
