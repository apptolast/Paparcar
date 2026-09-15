# Paparcar — Roadmap

> **Doc vivo.** Responde a *"¿qué queda por hacer?"* sin auditar `git log` primero.
> Verificado contra master `f1ddd2a5` el **2026-09-15**: cada línea 🚧/📋 se comprobó con
> `git log master --grep=<TICKET-ID>` **y leyendo el código** (el `fichero:línea` de cada bug abierto
> vive en su doc de backlog), distinguiendo un commit de código de un `docs(backlog):` que solo ABRE
> el ticket. Sustituye a `Paparcar_Roadmap_*.md` (en `docs/archive/`).
>
> ⛔ **Regla de este doc y de todos los vivos:** cabecera con **fecha + commit** contra el que se
> verificó. Un doc sin eso no se distingue de uno que nadie ha mirado en tres meses.
> [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]

---

## Dónde está el proyecto

**Publicada.** La **1.0.0 salió a producción el 05-09-2026**; lo que se hace cada día es medir la
detección en dos móviles reales (Oppo + Redmi) con telemetría remota y cerrar el fallo que salga.
Política *fix-forward*. En curso hacia la **1.1.0**: la ola 2 de Plus (casi cerrada) y el pivote de
**trayectos como producto principal** (14-09), que convierte la detección en un servicio que mide
cada viaje —km, gasto, batería, tiempo buscando plaza— y no solo en un colocador de pines.

Lo que queda para la 1.1.0 no es todo código: siguen pendientes acciones de cuenta de Play
(§ *Bloqueantes*) y las capturas localizadas.

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
| Ficha de Play (9 idiomas, icono, gráfico) | ✅ Commiteada; faltan capturas |
| Detección iOS | 🧊 **Aparcada por el user (07-09)**. Fases 0 y 2 viven en `feature/IOS-F0-001-fase0`, sin mergear |
| Room & Keystore | ✅ Room KMP en **v10** (v8 ruta del viaje · v9 postal · v10 `searchTimeMs`) y claves cifradas |
| **Ola 2 (Plus, Widget, Fuel)** | 🟡 **casi cerrada**: A1–F5 y F7 ✅ (Alemania entró el 15-09, `9afbc5e5`; sigue Italia). Quedan **F6b** (ocupación real de parkings) y **F8** (coche compartido: **etapa 1 en rama** desde el 15-09, etapas 2-3 sin empezar) → [`backlog/plus-roadmap-001.md`](./backlog/plus-roadmap-001.md) |
| Hosting de detección | ✅ **un solo FGS y una sola notificación** desde `d7880ac7`: `BluetoothDetectionService` borrado, el carril BT entra por el intake del sentinel en slots propios [DET-ONE-SERVICE-TWO-LANES-001] |
| **Pivote trayectos** (14-09, 8 fases) | 🟢 **fases 1-5, 7 y 8 ✅ en master** (`b3d8168d` … `ddb448e4`): ruta en Room sin tope, muestreo Cruise, grabador BT, park BT confirmado con ruta medida, batería medida, tiempo-buscando-plaza, geometría local-first. Queda la fase 6 (vía A pin-a-pin) ⛔ gated por el viaje del **Kamiq** |

---

## 🚧 En vuelo — ramas sin mergear

### Independientes

> ⚠️ **Esta tabla caduca en horas.** El 15-09 master se movió **seis veces** mientras se auditaba el
> backlog (`9afbc5e5` → `d7880ac7` → `851b00ff` → `3ffb7eae` → `f1ddd2a5`), cerrando por el camino
> cuatro ramas que estaban listadas aquí como vivas. Léela junto a `git worktree list`, nunca en su
> lugar.

| Rama (ahead/behind vs master, 15-09 `f1ddd2a5`) | Ticket | Estado |
|---|---|---|
| `feature/FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001-fuel-alerts` (0 / 0) | alertas de precio de combustible | 🔵 worktree `../Paparcar-fuel-alerts`, recién abierta y limpia |
| `feature/ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001-background-delivery` (0 / 0) | entrega en background de las alertas de zona | 🔵 worktree `../Paparcar-zone-bg`, recién abierta y limpia |
| `chore/PLAY-A-SCREENSHOT-…-shots-110` (0 / 66) | `PLAY-A-SCREENSHOT-MUST-SPEAK-THE-READERS-LANGUAGE-001` | 🟡 su commit ya está en master (0 commits propios); lo vivo son **4 ficheros sin commitear** + su doc sin trackear en `../Paparcar-play-shots-110`. Capturas localizadas de la 1.1.0 |
| `feature/IOS-F0-001-fase0` (16 / 149) | `IOS-F0-001` + `IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001` | 🧊 aparcada. PR #3 draft mergeable, pero **149 commits por detrás**; mergear = abrir un gate de campo entero |

> Cerradas el 15-09 y ya sin rama: `PRIV-A-TRACE-IS-NOT-A-STAT-001` (`ddb448e4`) ·
> `UI-A-CLOSE-X-MUST-CLOSE-SOMETHING-001` (`851b00ff`) · `UI-A-PARKED-CARD-…` (`3ffb7eae`) ·
> `HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001` (`f1ddd2a5`).

---

## 📋 Abierto en el backlog

> 📌 **El inventario completo y verificado vive en [`backlog/README.md`](./backlog/README.md)**
> (15-09, contra `ddb448e4`): ~54 tickets abiertos, agrupados por detección / bugs de UI verificados
> en código / build / arquitectura / producto / iOS. Aquí solo van los que mandan.

### Detección — con evidencia, esperando implementación
| Ticket | Qué falla |
|---|---|
| `DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001` | 🔴🔴 mitad 2: la plaza de **A puede publicarse con el coche ya en B**. Es el más grave abierto |
| `DET-BLIND-AFTER-LOST-PARK-001` | 🔴 ciego tras perder un park. Auditoría 03-09: honest close no lo cubre |
| `DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001` | 🔴 un solo fix abre la sesión. Las 2 barras candidatas se midieron y **las dos pierden un viaje real** |
| `SPOT-A-PROMOTION-INHERITS-THE-AGE-OF-ITS-DEPARTURE-001` | 🟠 (14-09) promover una salida deducida publica como nueva una plaza vieja: TTL de 2 h anclado a la promoción |
| `DET-A-DEPARTURE-HAS-NO-PROVENANCE-001` | 🟠 (14-09) el pin lleva procedencia y **la marcha no**: la mitad de salida no se puede contar |
| `DET-BT-PIN-GRADE-…-001` + `DET-COARSE-FIX-DRIVE-PROOF-001` | ⚠️ **el mismo tornillo en sentidos opuestos**: `minGpsAccuracyForDriving = 50f` decide a la vez con qué fix se CREE una conducción y con cuál se COLOCA un pin. Se deciden juntos o no se tocan |
| `DET-A-MANUAL-RELEASE-…-IN-THE-BUS-001` | «Me voy» no resetea `DepartureEventBus` (los otros dos cierres sí): tras liberar a mano, alejarse andando puede confirmar una salida falsa |

### Bugs de UI abiertos y verificados en código (15-09)
| Ticket | Qué falla |
|---|---|
| `MAP-THE-HANDLER-MAP-MUST-BE-REBUILT-WITH-THE-MARKER-LIST-001` | al cambiar la vigilancia del viaje, salida y llegada **pierden su dibujo** (`PaparcarMapView.kt:1202` no keya por `watchKey`) |
| `UI-A-CAPTURED-NAME-NEEDS-ITS-CEILING-001` | el nombre de zona no tiene techo y viaja entero a Firestore |
| `DEV-AN-EDIT-VARIANT-MUST-EDIT-A-CAR-001` | las variantes «Edición» de la galería mock pintan una moto |
| `DB-A-ROOM-EXPECT-OBJECT-BREAKS-THE-METADATA-COMPILATION-001` | `compileCommonMainKotlinMetadata` no compila (no rompe APK ni tests; rompe el IDE) |

### Detección — Merged (Pila de 10 ramas)
✅ `DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001`
✅ `DET-DRIVING-EVIDENCE-VALUE-OBJECT-001`
✅ `DET-NO-CLOCK-PLANTS-A-PIN-001`
✅ `DET-THE-EVIDENCE-MUST-REACH-THE-TRACE-001`
✅ `DET-STARVED-PROMPT-HAS-NO-WITNESS-001`
✅ `DET-DOUBT-REACHES-REMOTE-001`
✅ `DET-DETECTION-PATH-IS-A-TYPE-001`
✅ `DET-FAIL-CLOSED-BY-CONSTRUCTION-001`
✅ `DET-TWO-TIER-SENTRY-001`
✅ `DET-DOUBT-MUST-REACH-THE-SCREEN-001`

---

## 🔴 Bloqueantes / acciones del usuario (no código)

1. **Google Play Console** — declarar *trader status*, completar verificaciones de identidad.
2. **Capturas de pantalla** — rama `shots-110`, con trabajo sin commitear.
3. **Clave de Maps** — ✅ Cifrada. Pendiente rotación en GCP si se cambia SHA-1.
4. **Reglas de Firestore** — desplegar vía CLI (⚠️ el MCP `firebase_deploy` es un no-op silencioso).
5. **Dominio y correo** — ✅ `paparcar.com` vivo con la política publicada.
6. **Data Safety de Play** — ⏳ revisar contra la §4 nueva de la política **y contra el anexo de
   `PRIV-A-TRACE-IS-NOT-A-STAT-001`** (la geometría de ruta ya es local-first con consentimiento).

---

## 🔮 Futuro (post-lanzamiento, no ahora)

> 📌 **La ola 2 de Plus ya casi no es futuro**: de
> [`backlog/plus-roadmap-001.md`](./backlog/plus-roadmap-001.md) (07-09-2026) solo quedan **F6b**
> (ocupación real de parkings: Madrid+Málaga, primer backend propio) y **F8** (coche compartido).
> Analytics, widget, límites free (7 d · 1 coche · 2 zonas), combustible, estadísticas, gasolineras y
> la base de parkings están ✅ en master; el tiempo de búsqueda lo entregó el pivote de trayectos.
> El horizonte de DESPUÉS vive en
> [`ROADMAP-POST-PLUS.md`](./ROADMAP-POST-PLUS.md) (embudo de búsqueda, agregación por celda,
> alertas SER — aparcada a propósito —, mapa de probabilidad), con los choques contra el código que
> hay que leer al definir cualquiera. Las ideas de aquí abajo que sobreviven siguen listadas.

- ~~**Widget Android** del aparcamiento activo~~ → ✅ **hecho** (fase A2 de la ola, `d0f06451`) con
  tres secuelas también en master: acciones, widget alto que lista los coches, y el viaje que deja de
  leerse como un widget vacío. Siguen abiertas dos ideas del user: `ONB-WIDGET-IS-THE-LAST-STEP-001`
  y `WIDGET-MAP-SNAPSHOT-003`.
- **Android Auto** — soltar la plaza desde el infotainment al desconectar.
- **WearOS / Apple Watch** — countdown del TTL de la última plaza publicada.
- **Detección vía CarPlay / Android Auto pairing** — señal tan fuerte como el BT, para quien no
  empareja el móvil pero sí conecta multimedia.
- **Modo "voy de camino"** con ETA, apoyado en el `enRouteCount` que ya existe en el modelo.
  ⬆️ **Es el P1 de después de la ola 2**: deja de ser un extra y pasa a ser **prerrequisito** de la instrumentación — sin
  este flujo no existe el hecho "búsqueda" ni "plaza encontrada", que es la métrica del piloto.
  ⚠️ `enRouteCount` se lee y se pinta, pero **nadie lo incrementa** (verificado en master).
- **4º tab Comunidad/Perfil** — posible, deliberadamente aplazado.
- **Modularización** más allá de F7 — solo si el monolito escala mal.
- ~~**Detección server-side / ML** con eventos confirmados y denegados (privacy-first).~~ →
  **sustituida** por las features 1 + 3 + 8 de [`ROADMAP-POST-PLUS.md`](./ROADMAP-POST-PLUS.md)
  (instrumentar → agregar por celda → estimar), que dicen lo mismo con coste y orden.

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
