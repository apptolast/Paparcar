# GIT-A-CLEAN-REPO-SHOWS-ITS-WORK-001 · higiene de git: lo que el repo enseña de sí mismo

**Estado:** ✅ Done · rama `chore/GIT-A-CLEAN-REPO-SHOWS-ITS-WORK-001-hygiene` · worktree `../Paparcar-git-hygiene`

## Problema
Auditoría completa de git/GitHub pedida por el user (24-09). El barrido del 23-09
([GIT-BARRIDO](git_swept_clean_2026_09_23)) dejó las ramas y las issues en orden, pero no miró
**dentro** del árbol ni de la configuración. Lo que quedaba:

1. **Tres ficheros scratch trackeados**: `.artifacts/7a492c30-…/scratch/commit_msg{,_tabs,_ui}.txt`,
   borradores de mensaje de commit colados en `41b2c8d72` por una sesión `[GEMINI]`. `.artifacts/`
   no estaba en `.gitignore`, así que nada impedía que volviera a pasar.
2. **MACs Bluetooth reales en 10 ficheros versionados**: la del estéreo del Kamiq (12 veces), la del
   manos libres aftermarket (2) y la de unos auriculares (1). El commit `7f99cf2b9` sacó los logs
   crudos de campo justo por esto, pero las MACs citadas en el análisis se quedaron.
3. **Sin `.gitattributes`**: el índice está 100 % LF, pero nada lo garantiza. Es la causa de raíz
   del ruido CRLF que en `BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001` hizo que un atajo para
   silenciarlo acabara **revirtiendo salida de verdad**.
4. **Cuatro identidades de autor para dos personas** — cada una firmando además con el correo
   `noreply` de GitHub.
5. **`esquema-refactor.html`** (jun-26, DET-001) huérfano en la raíz del repo.

## Doctrina violada
*Sistemas, no parches.* Los cinco puntos son el mismo patrón: un invariante sin sitio donde vivir.
Nadie había escrito "el scratch no se versiona" (→ `.gitignore`), ni "el índice es LF"
(→ `.gitattributes`), ni "una persona, una firma" (→ `.mailmap`). Sin el fichero que lo sostiene,
la regla depende de que nadie se despiste, y el despiste ya había ocurrido.

## Diseño
Cada invariante pasa a tener **un** fichero que lo sostiene, y ninguno reescribe historial:

| Invariante | Dónde vive ahora |
|---|---|
| El scratch de agente no se versiona | `.gitignore` → `.artifacts/` |
| El índice guarda LF, pase lo que pase en disco | `.gitattributes` → `* text=auto eol=lf` |
| Una persona, una firma | `.mailmap` (4 identidades → 2) |
| Un dato de campo no viaja al repo | MACs → tokens `BT-CAR-KAMIQ`, `BT-HANDSFREE-AFTERMARKET`, `BT-HEADPHONES` |

**Los UIDs y correos de `.claude/skills/field-test/SKILL.md` se quedan a propósito**: la skill los
usa para consultar `diagnostics/{uid}/sessions`, tokenizarlos rompería una herramienta viva, y el
correo del user ya es el campo `author` de 1.430 commits — borrarlo de cuatro docs no oculta nada.

## Criterio de éxito
- `git grep` de las tres MACs reales → 0 resultados.
- `git shortlog -sne --all` → 2 autores, no 4.
- `git status` limpio tras `git add --renormalize .` (nada que renormalizar: el índice ya era LF).

## Consumidores auditados
- **Fixtures de test con MAC** (`AA:BB:CC:…`, `11:22:33:…`, `00:02:5C:…`): **exentas**, son inventadas.
- **`AudioRouteBtIdentityTest.kt:22`** (`04:88:E2:2B:E2:A0`): **exenta** — constante de test que
  ejercita la insensibilidad a mayúsculas; tocarla es tocar aserciones, y no es un dato de campo.
- **`.claude/skills/field-test/SKILL.md`**: MAC tokenizada (era descriptiva, 1 sola cita); UIDs y
  correos conservados por lo dicho en *Diseño*.

## Fuera de alcance (decisión explícita del user)
- **Reescritura de historial**: 14 logs crudos de campo y ~110 MB de artefactos de build siguen
  alcanzables desde `master`. Sacarlos exige `git filter-repo` → rompe 1.520 SHAs, invalida los
  worktrees vivos y deja huérfana la PR #29. **No se hace mientras el repo sea privado.**
- **CI**: lleva fallando desde el 13-09 por **facturación**, no por el workflow
  (*"The job was not started because recent account payments have failed"*). Lo lleva otra sesión.
- **Gobernanza** (`LICENSE`, `CONTRIBUTING.md`, `SECURITY.md`, plantillas de PR/issue): irrelevante
  en un repo privado, imprescindible el día que se abra.
