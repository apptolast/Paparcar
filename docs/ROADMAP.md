# Paparcar — Roadmap

> **Doc vivo.** Responde a *"¿qué queda por hacer?"* sin auditar `git log` primero.
> Verificado contra master `46621e7f` el **2026-09-11**: cada línea 🚧/📋 se comprobó con
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
| Ficha de Play (9 idiomas, icono, gráfico) | ✅ Commiteada; faltan capturas |
| Detección iOS | 🟡 Fase 0 en rama sin mergear; nativos listos, wiring pendiente |
| Room & Keystore | ✅ Migrado a Room KMP y claves cifradas en local.properties |
| Ola 2 (Plus, Widget, Fuel) | ✅ Implementado: Widget activo, Gasolineras (ES/FR) y Paywall |

---

## 🚧 En vuelo — ramas sin mergear

### Independientes

| Rama | Ticket | Estado |
|---|---|---|
| `feature/IOS-F0-001-fase0` | `IOS-F0-001` | Fase 0 del port iOS: puertos, capacidades y harness. 🔵 lo valida un compañero con Mac |
| `chore/PLAY-A-SCREENSHOT-MUST-SPEAK-THE-READERS-LANGUAGE-001-shots-110` | — | Generación de capturas localizadas para la v1.1.0 |

---

## 📋 Abierto en el backlog

### Detección — con evidencia, esperando implementación
| Ticket | Qué falla |
|---|---|
| `DET-BLIND-AFTER-LOST-PARK-001` | 🔴 ciego tras perder un park. Auditoría 03-09: honest close no lo cubre. |
| `DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001` | 🔴 un solo fix abre la sesión. Arquitectura `provisionalCrossing` en master, pero refutada por tests. |
| `DET-BT-PIN-GRADE-IS-NOT-A-DRIVING-THRESHOLD-001` | precisión BT vs fiabilidad. Suelo global de 60m a auditar. |

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

## 🔴 Bloqueantes de lanzamiento (acciones del usuario, no código)

1. **Google Play Console** — declarar *trader status*, completar verificaciones de identidad.
2. **Capturas de pantalla** — Rama `shots-110` en curso.
3. **Clave de Maps** — ✅ Cifrada. Pendiente rotación en GCP si se cambia SHA-1.
4. **Reglas de Firestore** — Desplegar vía CLI.
5. **Dominio y correo** — `paparcar.com` hosting pendiente.

---

## 🔮 Futuro (post-lanzamiento, no ahora)

> 📌 **La ejecución inmediata es la ola 2 de Plus**:
> [`backlog/plus-roadmap-001.md`](./backlog/plus-roadmap-001.md) (07-09-2026) — analytics, widget,
> límites free (7 d · 1 coche · 2 zonas), combustible, estadísticas, gasolineras, parkings públicos
> (Madrid+Málaga), tiempo de búsqueda y coche compartido. El horizonte de DESPUÉS vive en
> [`ROADMAP-POST-PLUS.md`](./ROADMAP-POST-PLUS.md) (embudo de búsqueda, agregación por celda,
> alertas SER — aparcada a propósito —, mapa de probabilidad), con los choques contra el código que
> hay que leer al definir cualquiera. Las ideas de aquí abajo que sobreviven siguen listadas.

- **Widget Android** del aparcamiento activo (dirección + tiempo transcurrido + tap al mapa).
  ⬆️ **Entra como fase A2 al arranque de la ola 2** (07-09): 1 día, cero backend, y refuerza justo
  la promesa que se vende ("encuentra tu coche").
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
