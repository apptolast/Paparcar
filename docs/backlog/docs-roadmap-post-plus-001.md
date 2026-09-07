# DOCS-ROADMAP-POST-PLUS-001 · El plan de después de Plus no vivía en ningún sitio entero

**Estado:** ✅ Done — mergeado a master (2026-09-07), con la segunda pasada del 07-09 dentro.

> **07-09 · segunda pasada — fusión con la ola 2.** El user trajo `PLUS-ROADMAP-001` (plan de
> ejecución salido de Claude web: límites free, combustible, estadísticas, gasolineras, parkings,
> tiempo de búsqueda, coche compartido) y tomó 4 decisiones: (1) el orden F1→F8 manda, con
> analytics 1a y el Widget colándose al arranque como A1/A2; (2) SER queda aparcada TRAS la ola,
> a propósito; (3) la dirección del modelo del coche compartido se decide ANTES de F2; (4) dos
> docs, una tabla — la ejecución en [`plus-roadmap-001.md`](plus-roadmap-001.md), el horizonte en
> [`ROADMAP-POST-PLUS.md`](../ROADMAP-POST-PLUS.md) con su §4 reescrito.
> ⚠️ Desviación deliberada del F0 original de ese plan: `money-001.md` NO se mueve a `docs/archive/`
> — los tickets cerrados se quedan en el backlog [DOCS-BACKLOG-TRUTH-002]; lleva puntero de
> superseded.
> Rama rebasada sobre master `466a10c9` el 07-09 (limpio, sin conflictos; era 1 commit de docs).

## Problema

MONEY-001 cierra las 7 fases y deja la pregunta *"¿y ahora qué?"* sin un sitio donde contestarse.
Lo que había estaba repartido en tres documentos que no se citan entre sí:

- [`ROADMAP.md § 🔮 Futuro`](../ROADMAP.md) — ocho ideas **sin coste, sin riesgo y sin orden**.
- [`backlog/zone-subscribe-001.md`](zone-subscribe-001.md) — la entrega en background de las alertas
  de zona, que MONEY-001 F4 dejó fuera a propósito.
- `backlog/money-001.md` (en su rama) — "fuera de alcance": promo Fundadores, AdMob, paywall propio.

El 06-09-2026 el user trae `paparcar-roadmap-features.md` (sesión de producto del 5-6 sep): ocho
features **con** facilidad, complejidad, riesgo, dependencias y datasets. Sin fusionarlo, el proyecto
tiene dos planes de futuro que se ignoran mutuamente — y el nuevo, escrito desde producto, no puede
ver contra qué choca en el código.

## Doctrina violada

Ninguna de código. La de documentación sí: **un plan que vive fuera del repo es un plan que el repo
no puede leer** — el mismo agujero que motivó `IOS-SOCIAL-LOGIN-001` (*una spec que solo vive en una
rama es una spec que el backlog no puede leer*).

⛔ Y una restricción que este ticket **respeta a propósito**: nada de aquí está definido. El doc es
una lista ordenada, **no un permiso** para empezar. Cada feature se define paso a paso con el user
antes de que exista una línea de código.

## Señales / datos disponibles

Verificado contra master `9eb66de0` mientras se escribía el doc:

- `Spot.confidence: Float` (0..1, tiñe el anillo del marcador) y `SpotStatus.CONFIRMED` (= *salida
  presenciada*) **ya existen** — el enum de "confianza" del fichero nuevo colisiona con los dos.
- `enRouteCount` se lee en Room, DTO, mappers y `HomeSpotRows`, pero **ningún camino lo incrementa**.
- `libs.versions.toml` no declara Firebase Analytics (solo Crashlytics).
- `firebase.json` declara `firestore` + `hosting` y **ninguna** `functions`.
- `legal/DATA-SAFETY-FORM.md` ya declara propósito *Analytics* para ubicación y diagnósticos.

## Diseño

Un doc vivo nuevo, [`ROADMAP-POST-PLUS.md`](../ROADMAP-POST-PLUS.md), con cabecera fecha + commit
[DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001] y cuatro capas:

1. **Qué había ya** y por qué los dos planes casi no se pisan.
2. **§3 · Contra qué choca** — siete choques con el código y las reglas de hoy, cada uno con su
   evidencia. Es la capa que el fichero de producto no podía tener.
3. **§4 · Prioridad fusionada** P0-P10, con la razón de cada movimiento respecto al orden original.
4. **§5-§7** — las ocho features íntegras, decisiones de lanzamiento y descartados, cada feature con
   su línea *⚠️ Al definirla*.

`ROADMAP.md` no se duplica: recibe un puntero y **tres correcciones** en su §🔮 — el Widget sube a
P2, el modo "voy de camino" pasa de extra a prerrequisito, y *"detección server-side/ML"* queda
tachada porque las features 1+3+8 dicen lo mismo con coste y orden.

## Criterio de éxito

- La pregunta *"¿qué se hace después de Plus?"* se contesta abriendo **un** fichero.
- Los tres hallazgos que cambian el coste quedan escritos donde se leerán al definir cada feature, no
  en una conversación: el enum que colisiona, los tres eventos sin hecho que los produzca, y el
  primer backend propio.
- Ninguna feature queda abierta como ticket: el doc no autoriza trabajo.

## Consumidores auditados

Docs que hablan de futuro y qué pasa con cada uno:

| Doc | Veredicto |
|---|---|
| `ROADMAP.md § 🔮` | **actualizado** — puntero + 3 líneas corregidas |
| `HYPOTHESIS.md` | exento: retrospectiva fechada de mayo, se conserva tal cual por regla propia |
| `backlog/zone-subscribe-001.md` | citado desde §3.4 (su Cloud Function amortiza la de la feature 2). Sin cambios: sigue abierto y su alcance no se mueve |
| `backlog/money-001.md` (rama) | citado; AdMob y la promo Fundadores siguen fuera, y el fichero nuevo pone los parkings públicos **por delante** de AdMob |
| `backlog/README.md` | no se toca: este ticket no abre ninguno de los ocho |
| `legal/DATA-SAFETY-FORM.md` | **no** se edita aquí — la fila nueva se escribe en la tarea de la feature 1, cuando exista el evento. Anotado en §3.3 |
