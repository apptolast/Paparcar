---
name: nuevo-ticket
description: Abrir y cerrar una tarea de código en Paparcar con el flujo correcto — worktree aislado + doc en docs/backlog + rama, y al terminar rebase + squash/ff + limpieza + memoria. Usar al empezar CUALQUIER tarea de código no trivial ("vamos con X", "arregla Y", "implementa Z"), al aparcar trabajo a medias, o cuando el usuario diga que quiere mergear una rama de tarea.
---

# Abrir y cerrar una tarea en Paparcar

## A · ABRIR — worktree, doc, rama

### A.1 ⛔ Worktree nuevo, SIEMPRE. Primer paso, antes de tocar código.

**Las ramas de git NO aíslan el working tree.** El árbol principal
`C:/Users/rndev/Documents/AndroidProjects/Paparcar` suele tener trabajo del user sin commitear.

```bash
git worktree add -b <rama> ../Paparcar-<tarea> master
cp local.properties ../Paparcar-<tarea>/
cp composeApp/google-services.json ../Paparcar-<tarea>/composeApp/
```

Sin esos dos ficheros gitignored el build falla con *"SDK location not found"* /
*"google-services.json is missing"*.

Desde ese momento, **editar, compilar e instalar SIEMPRE desde el worktree**, nunca desde el
principal. Nada de `git stash` / `git checkout --` / cambios de rama en el árbol principal.

> Por qué es regla firme: el 10-08-2026 se hizo el badge honesto solo con una rama en el árbol
> principal, que ya tenía el route-line del user sin commitear. Quedaron entremezclados en
> `PresentationModule`, `HomeViewModel`, `HomeViewModelTest`, `DomainModule`, y el badge dependía
> a nivel de compilación del route-line. El user tuvo que desenredarlo a mano.

Si el árbol ya está entremezclado, no improvisar: `git diff -- <mis ficheros> > p`, worktree nuevo,
`git apply p` + copiar untracked, y limpiar el principal con `git checkout -- <ficheros>`.

### A.2 Naming

```
feature/<TICKET-ID>-<slug>      bugfix/<TICKET-ID>-<slug>
refactor/<TICKET-ID>-<slug>     chore/<TICKET-ID>-<slug>     experiment/<TICKET-ID>-<slug>
```

El ID debe ser **autoexplicativo** (`DET-SENTRY-COOLDOWN-001`, no `DET-042`): que se entienda qué
hace sin abrir el doc.

### A.3 `docs/backlog/<ticket-id>.md` — se crea AL ABRIR, no al final

```markdown
# <TICKET-ID> · <título en una línea>

**Estado:** 🔵 En progreso · rama `feature/…` · worktree `../Paparcar-…`

## Problema
Qué falla, con el dato real que lo demuestra (sesión de diagnóstico, hora, pin, captura).

## Doctrina violada
Qué invariante o regla del proyecto se rompe. Si no rompe ninguno, decirlo.

## Señales / datos disponibles
Qué tenemos ya en la telemetría o en el estado para decidir.

## Diseño
El SISTEMA, no el parche. Dónde vive el invariante y por qué ahí.

## Criterio de éxito
Cómo sabremos que está resuelto (test, comportamiento observable en campo).

## Consumidores auditados
Grep de todos los sitios que asumen lo contrario del invariante → cerrado / cubierto / exento.
```

## B · DURANTE

- **Sistemas, no parches.** Un bug recurrente = un invariante mal ubicado. Arreglarlo en UN sitio.
  No apilar un guard "por si acaso" sobre otro guard. El user tiene tiempo y prefiere hacerlo bien.
- **Barrido de consumidores.** Al arreglar un invariante, `grep` de todos los sitios que asumen lo
  contrario y clasificarlos en el doc. Cerrar solo la vía donde mordió no basta.
- **Doc en tiempo real.** `docs/backlog/<ticket>.md` se actualiza mientras avanza el trabajo, no al
  final. El user lo usa como fuente de verdad entre sesiones.
- **Compilar y testear desde el worktree, vía la herramienta Bash** (el repo no tiene `gradlew.bat`;
  en PowerShell `.\gradlew` sale exit 0 **sin compilar nada** — trampa conocida):
  ```bash
  ./gradlew :composeApp:testProdDebugUnitTest --console=plain
  ./gradlew :composeApp:compileMockDebugKotlinAndroid :composeApp:compileProdDebugKotlinAndroid --console=plain
  ./gradlew :composeApp:installProdDebug     # verificar "Installed on 1 device"
  ```
- **Strings nuevos → los 9 locales** en la misma tarea: `values` (EN base), `values-es`, `-it`,
  `-pt`, `-fr`, `-de`, `-nl`, `-pl`, `-ro`. Si la traducción no está clara, poner el texto inglés
  antes que omitir la key — Compose Resources **crashea** con una key ausente en el locale activo.
- **Pantalla / estado / flujo nuevo → Dev Catalog en la MISMA tarea** (ver skill `det-change` §4 si
  además toca detección): `ScreenGroup` en `StateGalleryScreen.kt`, variantes en paridad con el
  `*Previews.kt`, y si afecta a routing → `MockScenario` + fake + preset en `DevCatalogScreen.kt`.
  Verificar `assembleMockDebug`.
- **Toda UseCase nueva lleva test unitario.** Fakes, no mocks. Naming
  `should_expectedBehavior_when_condition`.

## C · CERRAR

### C.1 ⛔ Nunca commitear ni pushear sin permiso explícito **de este turno**

Estado por defecto tras editar = **sin commitear**. Reportar qué cambió y parar. Un "haremos un
commit por tarea" dicho antes **no** es permiso permanente: preguntar cada vez.

### C.2 Merge — histórico lineal

```bash
git merge-base --is-ancestor master <rama>   # ¿master avanzó?
# si falla → rebase de la rama sobre master DESDE SU WORKTREE
```

- **Por defecto: `git merge --squash <rama>` + commit manual.** Un ticket = un commit en master.
- **Si la rama tiene >5 commits, o el diff >500 líneas, o hay unidades de trabajo separables** →
  PARAR y preguntar: `--squash` (plano) / `--no-ff` (preserva) / `rebase + ff-only`.
- Nunca mergear sin aprobación del user.

Mensaje de commit — Conventional Commits con el ticket entre corchetes:

```
feat(detection): sentry-wake storm damper cools the sensor re-arm after refuted walks [DET-SENTRY-COOLDOWN-001]
```

⚠️ PowerShell 5.1 rompe `git commit -m` con comillas → usar `git commit -F <fichero>`.

### C.3 Limpieza y cierre

```bash
git worktree remove ../Paparcar-<tarea>
git branch -d <rama>
```

- Marcar el doc `docs/backlog/<ticket>.md` como **✅ Done** con el hash del commit.
- **Actualizar la memoria**: la línea de `MEMORY.md` pasa a ✅ master (+ hash), y editar las
  memorias antiguas que listaban esto como pendiente. No dejar pendientes zombis, y no recitar
  "pendiente" de entradas viejas sin contrastar contra `git log`.
- Si el ticket generó follow-ups deliberadamente fuera de alcance → entrada nueva en
  `docs/backlog/`, no solo mencionarlos en el chat.
