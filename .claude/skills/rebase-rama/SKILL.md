---
name: rebase-rama
description: Poner una rama de tarea al día sobre master en Paparcar — inventario de ramas candidatas (ahead/behind, worktree, limpieza), el user elige cuál, y el rebase se ejecuta SIEMPRE desde el worktree de esa rama, con backup, resolución de conflictos y verificación de build/tests. Usar cuando el user diga "rebasea la rama", "actualiza X con master", "ponla al día", "¿qué ramas están desfasadas?", o cuando al cerrar un ticket el ff-only falle porque master avanzó.
---

# Rebasear una rama sobre master

Esta skill cubre **solo el rebase**. El merge a master, el squash y la limpieza final del ticket
viven en la skill `nuevo-ticket` §C — si el user pide "mergear", ir allí.

## ⛔ Invariantes

- **El rebase se hace desde el worktree de la rama, nunca desde el árbol principal.** Las ramas de
  git NO aíslan el working tree, y `C:/Users/rndev/Documents/AndroidProjects/Paparcar` casi siempre
  tiene trabajo del user sin commitear. Prohibido `git checkout <rama>` / `git stash` en el principal.
- **Nada de rebase sobre un worktree sucio.** Si el worktree de la rama tiene cambios sin commitear,
  PARAR y preguntar (commitear / stash *en ese worktree* / abortar). No decidir por el user.
- **Backup antes de reescribir historia.** El rebase reescribe SHAs; una rama ya pusheada queda
  divergente de su `origin/`.
- **`git push --force-with-lease` exige permiso explícito de ESTE turno**, como cualquier push.
  Rebasear no lo autoriza.

## 1 · Inventario de ramas candidatas

```bash
git fetch origin --prune
git for-each-ref --format='%(refname:short)' refs/heads | grep -v '^master$' | while read b; do
  wt=$(git worktree list --porcelain | awk -v t="branch refs/heads/$b" '/^worktree /{w=$2} $0==t{print w}')
  set -- $(git rev-list --left-right --count "master...$b")   # $1 = behind, $2 = ahead
  merged=$(git merge-base --is-ancestor "$b" master && echo "YA-EN-MASTER" || echo "-")
  dirty="-"; [ -n "$wt" ] && [ -n "$(git -C "$wt" status --porcelain)" ] && dirty="SUCIO"
  up=$(git for-each-ref --format='%(upstream:short)' "refs/heads/$b")
  echo "$b | behind:$1 ahead:$2 | wt:${wt:-—} | ${dirty} | up:${up:-—} | $merged"
done
```

Clasificar antes de enseñar nada:

| Caso | Qué hacer |
|---|---|
| `behind:0` | Ya está al día — **no rebasear**, decirlo y quitarla de la lista |
| `YA-EN-MASTER` + limpia | Rama fusionada; no se rebasea, se **borra** (`git branch -d`) — proponerlo |
| `YA-EN-MASTER` + `SUCIO` | ⚠️ NO borrar: el trabajo vive sin commitear en su worktree (caso UI-TOPBAR-COLLAPSE-001). Para ponerla al día basta commitear y volver a mirar, o `git -C <wt> rebase master` que aquí es un fast-forward |
| `ahead:0` y behind>0 | Sin trabajo propio: `git branch -f <rama> master` es más honesto que un rebase |
| `SUCIO` | Candidata, pero bloqueada hasta que el user decida qué hacer con esos cambios |
| resto | Candidata real |

## 2 · Elegir rama

- **0 candidatas** → decirlo y parar. No inventar trabajo.
- **1 candidata** → confirmarla en una línea (con su ahead/behind) y seguir.
- **2+** → `AskUserQuestion`, una opción por rama, con `behind/ahead` + worktree + estado sucio en la
  `description` para que la decisión se tome con el dato delante. Si el user ya nombró la rama en su
  mensaje, no preguntar.

## 3 · Preparar la base

master debe estar al día antes de rebasear encima:

```bash
git rev-list --count master..origin/master     # ¿0? → ya al día
git merge --ff-only origin/master              # solo en el árbol principal; ff-only no toca los ficheros sucios del user
```

Si el ff-only falla (master local divergente) → **PARAR y preguntar**; eso es un problema de master,
no de la rama, y arreglarlo a ciegas puede tirar commits del user.

Si la rama no tiene worktree, crearlo (nunca hacerla checkout en el principal):

```bash
git worktree add ../Paparcar-<slug> <rama>
cp local.properties ../Paparcar-<slug>/
cp app/google-services.json ../Paparcar-<slug>/app/
```

Anotar que ese worktree lo creó la skill: si no había trabajo vivo, se retira al final.

## 3.bis · ¿Stack? Detectarlo ANTES de rebasear

Si hay varias ramas candidatas con el mismo merge-base y `ahead` creciente, comprobar si se contienen
entre sí — un stack se rebasea **una vez por la punta**, no rama a rama (rama a rama duplica cada
commit tantas veces como ramas, y deja copias divergentes del mismo trabajo):

```bash
for x in $BRANCHES; do for y in $BRANCHES; do [ "$x" = "$y" ] && continue
  git merge-base --is-ancestor "$x" "$y" && echo "  $x ⊂ $y"; done; done
```

Si sale una cadena (`01 ⊂ 02 ⊂ 05 ⊂ …`), rebasear **solo la punta** con `--update-refs` (git ≥2.38):
reescribe de paso todas las refs intermedias del stack.

```bash
git -C <worktree-de-la-punta> rebase --update-refs master
```

Verificar después que cada rama sigue con **su mismo `ahead` de antes** y `behind:0`, y que la cadena
de `⊂` se mantiene. Si algún `ahead` creció, hubo duplicación → abortar y revisar.

## 4 · Rebase

```bash
git tag prerebase/<rama> <rama>          # backup; se borra al verificar
git -C <worktree> rebase master
```

> ⛔ El backup va en un **tag**, no en una rama. `rebase --update-refs` reescribe *todas* las refs de
> `refs/heads` que apunten al stack — incluida una rama `<rama>-prerebase`, que se mueve con él y deja
> de ser un backup. Descubierto el 14-08-2026 rebaseando el stack IOS-F0 (los 8 backups se movieron;
> se salvó porque `origin/*` aún tenía los SHAs viejos, que es la otra red disponible si la rama está
> pusheada).

Si hay conflictos:

- Resolver aplicando la doctrina del proyecto (CLAUDE.md + el doc del ticket en `docs/backlog/`),
  fichero a fichero. **Nunca `-X ours` / `-X theirs` en bloque**: aplasta trabajo real sin mirarlo.
- Si el conflicto toca código que NO es del ticket (trabajo del user entremezclado, o dos tickets que
  se pisan) o no está claro qué lado gana → `git -C <worktree> rebase --abort` y preguntar. Abortar
  es gratis; resolver mal se descubre en campo.
- Tras cada tanda: `git -C <worktree> add <ficheros>` + `git -C <worktree> rebase --continue`.
- Reportar al final **cuántos commits se replicaron y qué ficheros tuvieron conflicto** — el user
  necesita saber dónde mirar.

## 5 · Verificar (obligatorio, un rebase limpio compila mal a menudo)

Un rebase sin conflictos NO garantiza que compile: la rama puede llamar a una API que master renombró.
Siempre vía la herramienta **Bash** y **desde el worktree** (el repo no tiene `gradlew.bat`; en
PowerShell `.\gradlew` sale exit 0 sin compilar nada):

```bash
cd <worktree>
./gradlew :shared:testDebugUnitTest --console=plain
./gradlew :app:compileMockDebugKotlin :app:compileProdDebugKotlin --console=plain
```

Si el ticket toca detección, además el bloque de la skill `det-change`. Si rompe algo, arreglarlo en
la rama ya rebasada (commit nuevo o `--amend` del commit culpable, con permiso) — no revertir el rebase.

## 6 · Cierre

- Verde → borrar el backup: `git tag -d prerebase/<rama>`. Rojo o dudas → dejarlo y decir que existe,
  es la vía de vuelta (junto con `git reflog` y, si la rama estaba pusheada, `origin/<rama>`).
- Worktree creado por la skill y ya sin uso → `git worktree remove ../Paparcar-<slug>`.
- Rama con upstream (`up:` no vacío) → ahora diverge de `origin/`. Decirlo y **preguntar** antes de
  `git push --force-with-lease origin <rama>`. Sin respuesta afirmativa en este turno, no se pushea.
- Si el ticket tiene doc en `docs/backlog/`, anotar el rebase en una línea (sobre qué master, si hubo
  conflictos). Esa nota es la que explica meses después por qué los SHAs no cuadran.
- Si el rebase era el paso previo a mergear → continuar con la skill `nuevo-ticket` §C.2, que exige
  aprobación aparte para el merge.
