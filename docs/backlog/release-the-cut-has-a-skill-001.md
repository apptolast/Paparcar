# RELEASE-THE-CUT-HAS-A-SKILL-001 · sacar versión tiene su skill, y la skill no duplica `cut.py`

**Estado:** ✅ Done · skill en `.claude/skills/cortar-version/` · ⏳ se estrena en el próximo corte real

## Problema

La parte mecánica del corte ya es un comando (`tools/release/cut.py`,
[RELEASE-THE-CUT-MUST-BE-ONE-COMMAND-001]) y habla con la tienda
([RELEASE-THE-CUT-REACHES-THE-STORE-001]). Lo que sigue sin dueño es **todo lo que rodea al
comando**, y el corte de 1.6.0 (10-11-2026) lo enseñó entero:

- se ejecutó **en tres tandas** por tres fricciones de Windows: PowerShell no encuentra `bash` ni
  `openssl`, `keytool` revienta con locale español y, desde Git Bash, las notas se contaron largas.
  La receta que funcionó vive en una memoria privada de Claude y en la prosa de
  `release-1-6-0-001.md`, no en un sitio que se ejecute;
- `phase_notes` valida las notas de la versión **que dice Gradle**, no la del argumento: antes del
  bump da verde con las notas de la versión anterior;
- la subida puede agotar el timeout de la herramienta **y haber llegado**: reintentar a ciegas es
  pedirle a Play un `versionCode` que ya tiene;
- el orden de las partes irreversibles (commit, etiqueta, subida, despliegues, push, ficha) solo
  estaba repartido entre `RELEASE-PROCESS.md` §1.0-§1.6, el README de la ficha y la memoria.

## Doctrina violada

Ninguna regla del producto. La que se rozaba es la del harness: *antes de improvisar un flujo,
invocar la skill que lo cubre*. Para sacar versión no había skill, así que cada corte se
improvisaba.

## Diseño

Una skill `.claude/skills/cortar-version/` que **orquesta y no implementa**:

- **La lógica se queda en `cut.py`** y el procedimiento en `docs/release/RELEASE-PROCESS.md`. Codex
  convive en el repo y lee `.claude/skills/` solo como referencia
  [AGENTS-CODEX-COEXISTS-WITH-CLAUDE-001]: una regla que viviera solo en la skill dejaría dos
  verdades. La skill cita secciones, no repite pasos.
- Lo que sí es suyo: el **preflight** (qué hay publicado, qué ha cambiado desde la última etiqueta),
  la **parte editorial** (notas en 9 idiomas, changelog, nombre de versión del Console, si la ficha
  se mueve), la **receta de Windows** que hace correr el corte de una tanda, y las **paradas**: cada
  paso irreversible se pide por separado, en su momento.
- Se descartó un workflow multiagente (el corte es lineal: cada fase depende de la anterior) y un
  workflow de GitHub con Claude dentro (exigiría subir a GitHub la credencial de Play y el keystore,
  y actuaría sin el user delante).

## Criterio de éxito

- La skill aparece en la lista de skills de `CLAUDE.md` y se dispara con «saca la versión»,
  «corta la 1.7.0», «sube a Play».
- El próximo corte se hace con ella, de una tanda, y lo que falle se corrige **en la skill o en
  `cut.py`**, no en la memoria.

## Consumidores auditados

- `CLAUDE.md` § skills → añade la línea.
- `docs/release/RELEASE-PROCESS.md` → sin cambios: la skill lo cita; sigue siendo la fuente.
- `docs/release/PROMPT-SACAR-VERSION-A-PRODUCCION.md` → exento: es para claude.ai con el conector
  del Console, otro sitio; la skill lo menciona para que nadie lo confunda con el procedimiento.
- `AGENTS.md` / `docs/agents/` → **no se tocan** (son de Codex). Ya apuntan a `.claude/skills/`
  como referencia.
- Ningún guard lee `.claude/skills/` (grep sobre `*.kt`, `*.py`, `*.mjs`).
