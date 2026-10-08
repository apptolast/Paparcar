# -*- coding: utf-8 -*-
"""El chivato del `description:`: una afirmacion sobre git tiene que ser verificable contra git.

[MEM-A-DESCRIPTION-MUST-BE-VERIFIABLE-AGAINST-GIT-001]

    python tools/memory/description_tell.py                    # 0 = limpio, 1 = hay fichas que mienten
    python tools/memory/description_tell.py --verbose           # ademas, cada hash comprobado
    python tools/memory/description_tell.py --memory-dir <ruta> # otra carpeta de memoria

## Que comprueba, y por que estos cuatro

El `description:` del frontmatter es lo que decide la RELEVANCIA al recordar una ficha. Una que se
describe como «trabajo sin mergear» se recupera —o se descarta— sobre una premisa falsa, y el cuerpo
correcto no salva nada si nadie llega a abrirlo. El barrido del 07/08-10-2026 encontro **44 de 565**
mintiendo, todas con el mismo origen: el frontmatter se redacta al ABRIR el ticket y al mergear se
actualiza el cuerpo pero no el description. `UI-SHEET-001` llevaba asi desde el 12-07.

  C1  falta el `description:` o esta vacio.
  C2  cita una RAMA o un WORKTREE que ya no existe.
  C3  afirma «sin mergear / sin commitear» y el cuerpo dice master.
  C5  el HASH que cita no esta en master (o no existe ni como objeto).

C5 es el que nadie habria hecho a mano: caza los hashes **pre-squash / pre-rebase**, que existen en
ESTE clon y en ninguna otra parte. `PROFILE-PHOTO` decia `a456617a` cuando master lleva `f7f2a8b34`.

## Por que NO es un test de `./gradlew testAll`

Dos razones, las dos medidas, las dos iguales a las de `tools/backlog/branch_tell.py`:

1. **La carpeta de memoria vive FUERA del repo** (`~/.claude/projects/<proyecto>/memory`). En CI no
   existe, asi que un test la encontraria vacia y pasaria sin mirar nada.
2. **C5 necesita el historial completo de `master`**, y el CI hace `actions/checkout@v4` con su
   `fetch-depth` por defecto = 1.

Un guard que no puede ver lo que juzga no es un guard: es un verde gratis
[CI-A-GUARD-THAT-NEVER-STARTS-IS-NOT-A-GUARD-001]. Asi que este script **se niega** (exit 2) cuando
no puede ver ni la memoria ni el historial, y `BacklogStatusGuardrailTest` vigila que siga existiendo
y siga negandose.

## Las tres trampas que mordieron al escribirlo

- **`acababa` encaja en `[0-9a-f]{7,}`** — y `decidida`, `cafetera`, `adecuada`. Un detector de
  hashes por regex marca palabras espanolas: de ahi `NOT_A_HASH`.
- **El ID de ticket no prueba que algo aterrizo.** `git log --grep=ARCH-HEALTH-F7` da 0 y el split
  esta en master. Por eso C3 compara con el CUERPO de la ficha, no con el log por ID.
- **La nota de correccion vuelve a disparar el detector.** Al arreglar una ficha se escribe
  *«⛔ el description decia «sin mergear»»*, y eso contiene la mentira. Solo cuenta la PRIMERA
  clausula (lo anterior al primer ⛔): lo que va detras es registro historico, no afirmacion.
"""
import argparse
import io
import os
import re
import subprocess
import sys

for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass

BRANCH_RE = re.compile(r"(?:feature|bugfix|fix|refactor|chore|experiment)/[A-Za-z0-9._/-]+")
WORKTREE_RE = re.compile(r"\.\./(Paparcar[A-Za-z0-9._-]*)")
HASH_RE = re.compile(r"\b([0-9a-f]{7,40})\b")
DESC_RE = re.compile(r"^description:(.*)$", re.M)

# ⛔ Palabras que encajan en [0-9a-f]{7,} y NO son hashes. Medido el 08-10: `acababa` salia dos
# veces y `ed25519` (el algoritmo de firma, en la ficha de publicar en Maven Central) una.
NOT_A_HASH = {
    "acababa", "acabada", "acabade", "adecuada", "cafetera", "decidida", "defecto", "defacto",
    "efectada", "facade", "fachada", "deadbeef", "ed25519",
}

# ⛔⛔ Un hash solo se juzga si la ficha lo PRESENTA como estando en master.
#
# Es la diferencia entre una afirmacion y una mencion, y sin ella el chequeo marca cuatro fichas que
# no mienten: `plegado en la rama iOS 424bf25a (pre-rebase f205773f)` dice la verdad —ese hash es de
# una rama, no de master— y `commit-hash 35a5e15fc4` es de OTRO repo (BaseLogin). Lo que es un
# defecto es «en master `X`» con X fuera de master, asi que se exige que el contexto inmediato
# anterior al hash hable de master.
MASTER_CONTEXT = ("master", "MASTER")
MASTER_WINDOW = 40

# ⛔ Allowlist DECLARADA (nunca deducida) de fichas cuyo SUJETO es la higiene de git: su description
# cita «sin commitear» porque es lo que encontro, no porque describa su propio estado. Un detector
# no puede distinguir «mi trabajo esta sin mergear» de «encontre trabajo sin mergear», asi que la
# excepcion se escribe aqui con su razon — y se comprueba que el fichero existe, porque una
# allowlist sobre algo que no existe no es una excepcion, es un agujero.
NARRATIVE_FICHAS = {
    "project_git_tree_cleanup_2026_10_07.md":
        "su hallazgo ES una rama con cero commits cuyo trabajo vivia sin commitear",
    "project_git_a_local_branch_may_already_be_merged_by_pr_001.md":
        "su tema ES que «sin mergear» no se puede deducir del log ni de la PR",
}

# \u26d4\u26d4 Todo en minuscula, y comparado en minuscula. El chequeo era sensible a mayusculas y una
# mentira escrita \u00abSIN mergear\u00bb pasaba limpia aunque \u00absin mergear\u00bb estuviera en la lista. Lo
# destapo la FALSIFICACION del 08-10 \u2014plantar la mentira y ver que no saltaba\u2014, no el razonamiento.
NOT_LANDED = (
    "sin commitear", "sin plegar", "sin mergear", "sin merge", "staged sin", "rama lista",
    "rama propia", "implementado sin", "sin commit en",
)
LANDED = ("\u2705", "en master", "master `", "plegado", "mergeado")

# Suelos: por debajo, lo que hay no es un corpus sino un directorio equivocado o un clon truncado.
MEMORY_FLOOR = 200     # 568 fichas el 08-10-2026
HISTORY_FLOOR = 200    # ~1.580 commits el 08-10-2026


def die(msg):
    sys.stderr.write("ERROR: %s\n" % msg)
    sys.exit(2)


def git(*args):
    out = subprocess.run(("git",) + args, capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    if out.returncode != 0:
        die("`git %s` fallo:\n%s" % (" ".join(args), (out.stderr or "").strip()))
    return out.stdout


def repo_root():
    here = os.path.abspath(os.path.dirname(__file__))
    while True:
        if os.path.isfile(os.path.join(here, "settings.gradle.kts")):
            return here
        up = os.path.dirname(here)
        if up == here:
            die("no encuentro la raiz del repo desde %s" % os.path.dirname(__file__))
        here = up


def main_worktree():
    """El arbol PRINCIPAL, no el worktree desde el que se corre.

    ⛔ En este proyecto se trabaja siempre desde un worktree por tarea, y la carpeta de memoria
    cuelga de la ruta del arbol PRINCIPAL. Derivar el slug de `repo_root()` daba
    `…-Paparcar-desc-tell/memory`, que no existe, y el script se negaba a correr siempre.
    `git worktree list` lista el principal primero.
    """
    for line in git("worktree", "list", "--porcelain").splitlines():
        if line.startswith("worktree "):
            return line.split(" ", 1)[1].strip()
    return repo_root()


def default_memory_dir():
    """`C:/Users/x/Documents/AndroidProjects/Paparcar` -> `C--Users-x-...-Paparcar`."""
    slug = os.path.abspath(main_worktree()).replace(":", "-").replace("\\", "-").replace("/", "-")
    return os.path.join(os.path.expanduser("~"), ".claude", "projects", slug, "memory")


def require_memory(path):
    if not os.path.isdir(path):
        die(
            "no existe la carpeta de memoria:\n       %s\n"
            "       Esta FUERA del repo, asi que en un checkout limpio o en CI no esta. Sin ella el\n"
            "       chequeo no puede mirar nada, y un verde no significaria nada.\n"
            "       Pasa la ruta con --memory-dir si la tienes en otro sitio." % path
        )
    files = sorted(f for f in os.listdir(path)
                   if f.endswith(".md") and f not in ("MEMORY.md", "MEMORY-ARCHIVE.md"))
    if len(files) < MEMORY_FLOOR:
        die(
            "solo %d fichas en %s, por debajo del suelo de %d: esto no parece el corpus de memoria.\n"
            "       Igual que una carpeta vacia, dari\u0301a un verde sin haber mirado."
            % (len(files), path, MEMORY_FLOOR)
        )
    return files


def require_history():
    if git("rev-parse", "--is-shallow-repository").strip() == "true":
        die(
            "this is a shallow clone, so the hash check cannot run.\n"
            "       Sin historial de `master` NINGUN hash parece estar dentro, y el chequeo marcaria\n"
            "       todas las fichas o ninguna segun como se escriba. Arreglo: `git fetch --unshallow`."
        )
    depth = len(git("log", "--format=%H", "master").split())
    if depth < HISTORY_FLOOR:
        die("`master` solo tiene %d commits, por debajo del suelo de %d: clon truncado."
            % (depth, HISTORY_FLOOR))
    return depth


def lead(text):
    """Solo la primera clausula afirma; lo que va tras el primer ⛔ es registro historico."""
    return text.split("\u26d4")[0]


def main():
    ap = argparse.ArgumentParser(add_help=True)
    ap.add_argument("--memory-dir")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    root = repo_root()
    os.chdir(root)
    mem = os.path.abspath(args.memory_dir) if args.memory_dir else default_memory_dir()
    files = require_memory(mem)
    for allowed in NARRATIVE_FICHAS:
        if allowed not in files:
            die("la allowlist nombra %s y no existe en %s.\n"
                "       Una excepcion sobre algo que no existe no es una excepcion, es un agujero:\n"
                "       quitala, o corrige el nombre." % (allowed, mem))
    depth = require_history()

    refs = set()
    for line in git("for-each-ref", "--format=%(refname:short)", "refs/heads", "refs/remotes").splitlines():
        name = line.strip()
        if name:
            refs.add(name)
            if name.startswith("origin/"):
                refs.add(name[len("origin/"):])
    worktrees = {os.path.basename(l.split(" ", 1)[1].strip().rstrip("/")).lower()
                 for l in git("worktree", "list", "--porcelain").splitlines()
                 if l.startswith("worktree ")}

    findings = {"C1": [], "C2": [], "C3": [], "C5": []}
    checked_hashes = 0
    mentioned = 0

    for name in files:
        text = io.open(os.path.join(mem, name), encoding="utf-8", errors="replace").read()
        m = DESC_RE.search(text)
        if not m:
            findings["C1"].append((name, "no hay linea `description:`"))
            continue
        raw = m.group(1).strip()
        if not raw or raw in ('""', "''"):
            findings["C1"].append((name, "`description:` vacio"))
            continue

        desc = lead(raw)
        parts = text.split("---")
        body = "---".join(parts[2:]) if len(parts) > 2 else text
        head = lead(" ".join([l.strip() for l in body.split("\n") if l.strip()][:3]))

        dead_refs = sorted({b.rstrip(".,`)") for b in BRANCH_RE.findall(desc)} - refs)
        dead_wts = sorted({w for w in WORKTREE_RE.findall(desc) if w.lower() not in worktrees})
        if dead_refs or dead_wts:
            findings["C2"].append((name, "rama(s) %s · worktree(s) %s"
                                   % (dead_refs or "-", dead_wts or "-")))

        if (name not in NARRATIVE_FICHAS
                and any(w in desc for w in NOT_LANDED) and any(w in head for w in LANDED)):
            findings["C3"].append((name, desc[:110]))

        for m2 in HASH_RE.finditer(desc):
            h = m2.group(1)
            if h in NOT_A_HASH:
                continue
            before = desc[max(0, m2.start() - MASTER_WINDOW):m2.start()]
            if not any(k in before for k in MASTER_CONTEXT):
                mentioned += 1          # mencion, no afirmacion: se cuenta y no se juzga
                continue
            checked_hashes += 1
            if args.verbose:
                print("  %s afirma master en %s" % (name, h))
            exists = subprocess.run(("git", "cat-file", "-e", h + "^{commit}"),
                                    capture_output=True).returncode == 0
            if not exists:
                findings["C5"].append((name, "dice «master %s» y ese hash no existe ni como objeto" % h))
            elif subprocess.run(("git", "merge-base", "--is-ancestor", h, "master"),
                                capture_output=True).returncode != 0:
                findings["C5"].append((name, "dice «master %s» y ese hash NO es ancestro de master "
                                             "(pre-squash/pre-rebase)" % h))

    total = sum(len(v) for v in findings.values())
    print("Revisadas %d fichas en %s" % (len(files), mem))
    print("  %d hashes AFIRMADOS en master, comprobados contra %d commits · %d solo MENCIONADOS "
          "(rama, upstream, otro repo): esos no se juzgan."
          % (checked_hashes, depth, mentioned))
    if not total:
        print("OK: ningun `description:` afirma algo que git desmienta.")
        return 0

    TITLES = {
        "C1": "sin `description:` o vacio",
        "C2": "cita una rama o worktree que YA NO EXISTE",
        "C3": "dice «sin aterrizar» y el cuerpo dice master",
        "C5": "cita un hash que NO esta en master",
    }
    print("\n%d hallazgo(s):" % total)
    for key in ("C1", "C2", "C3", "C5"):
        if findings[key]:
            print("\n  %s \u00b7 %s (%d)" % (key, TITLES[key], len(findings[key])))
            for name, why in findings[key]:
                print("      %s\n          %s" % (name, why))
    print(
        "\nArreglo: reescribir el `description:` con el hash que SI esta en master, y dejar escrito\n"
        "que decia antes tras un \u26d4 — asi se ve por que cambio y el detector no lo vuelve a marcar\n"
        "(solo lee la primera clausula)."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
