# -*- coding: utf-8 -*-
"""El chivato de la rama: un doc abierto que cita una rama BORRADA con su commit en master miente.

[DOCS-THE-BACKLOG-STATUS-LOOP-MUST-BE-A-GUARD-001]

    python tools/backlog/branch_tell.py            # 0 = limpio, 1 = hay docs que mienten
    python tools/backlog/branch_tell.py --verbose   # ademas, que rama cita cada doc abierto

## Por que esto NO vive dentro de `./gradlew testAll`

`BacklogStatusGuardrailTest` cubre todo lo que se puede comprobar leyendo ficheros: que cada doc
tenga linea de estado clasificable, que ninguno sea binario, y que las cifras del README sean las
medidas. Este chequeo es el unico que necesita **git**: la lista completa de refs y el historial
completo de `master`.

Y ahi esta el limite medido: el CI hace `actions/checkout@v4` con su `fetch-depth` por defecto, que
es **1**. En un clon asi `git log master` trae un commit, `refs/heads` casi nada, y un guard que
preguntara eso concluiria «no hay commit de implementacion» y se pondria **verde para todos los
docs**. Eso es la forma exacta en la que un guard deja de existir sin que nadie lo retire
[CI-A-GUARD-THAT-NEVER-STARTS-IS-NOT-A-GUARD-001].

Asi que este script **se niega a correr** cuando no puede ver el historial, en vez de mentir. Si
algun dia el CI clona entero (`fetch-depth: 0`), este fichero puede pasar a ser un test; hasta
entonces se corre a mano al cerrar un ticket o al auditar el backlog, y
`BacklogStatusGuardrailTest.verifyBranchTellToolIsPresent` vigila que siga existiendo y siga
negandose.
"""
import io
import os
import re
import subprocess
import sys

BACKLOG = os.path.join("docs", "backlog")

# La consola de Windows es cp1252 y los marcadores son emoji: sin esto el script PETA justo al
# imprimir el hallazgo, que es el unico momento en el que importa. Medido el 07-10.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):  # Python < 3.7 o stream ya envuelto
        pass

# Mismo vocabulario y mismo orden de deteccion que BacklogStatusGuardrailTest. Si uno cambia,
# cambia el otro: son dos lecturas de la MISMA convencion, y divergir es el bug.
CLOSED = ("✅", "❌", "\U0001f6ab")
ANALYSIS = "\U0001f4da"
VOCABULARY = (
    "✅", "❌", "\U0001f6ab", "\U0001f535", "\U0001f7e1",
    "\U0001f4cb", "⚪", "\U0001f9ca", "\U0001f534", "\U0001f7e0", ANALYSIS,
)
NOT_TICKETS = (
    "README.md",
    "map-a-remembered-pin-looks-like-a-live-one-PROMPT.md",
    "onb-the-map-answers-more-than-spots-PROMPT.md",
    "ui-a-neutral-tile-must-not-mute-an-action-PROMPT.md",
)

STATUS_RE = re.compile(r"^[ \t]*>?[ \t]*\*{0,2}(Estado|Status)")
DATE_RE = re.compile(r"^[ \t]*>?[ \t]*\*{0,2}Fecha")
BRANCH_RE = re.compile(r"(feature|bugfix|fix|refactor|chore|experiment)/[A-Za-z0-9._/-]+")

# ⛔ La rama que cuenta es la del BLOQUE DE ESTADO, no la que el doc mencione en cualquier parte.
#
# La primera version leia las 20 primeras lineas y produjo un falso positivo inmediato:
# `ios-apple-sign-in-on-baselogin-3-001.md` esta 🟡 con razon —el codigo entro en master y lo que
# queda es compilarlo en un Mac— y nombra su rama en un parrafo que explica que **se borro**. Eso no
# es el defecto: el defecto es una linea de estado que presenta una rama muerta como si el trabajo
# siguiera ahi («🔵 En revision · rama X»). Hablar de una rama en prosa es legitimo.
#
# El bloque es la linea de estado mas sus continuaciones hasta la primera linea en blanco, porque
# varias la parten en dos (`… · rama `X` ·` / `worktree `../Paparcar-…``).

# Suelo de historial: por debajo de esto el clon no sirve para este chequeo. El repo tenia ~1.575
# commits el 07-10; un clon shallow trae 1.
HISTORY_FLOOR = 200


def git(*args):
    out = subprocess.run(
        ("git",) + args, capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if out.returncode != 0:
        die("`git %s` fallo:\n%s" % (" ".join(args), (out.stderr or "").strip()))
    return out.stdout


def die(msg):
    sys.stderr.write("ERROR: %s\n" % msg)
    sys.exit(2)


def repo_root():
    here = os.path.abspath(os.path.dirname(__file__))
    while True:
        if os.path.isfile(os.path.join(here, "settings.gradle.kts")):
            return here
        parent = os.path.dirname(here)
        if parent == here:
            die("no encuentro la raiz del repo desde %s" % os.path.dirname(__file__))
        here = parent


def require_full_history():
    """Se niega a correr sobre un clon sin historial, en vez de dar un verde vacio."""
    if git("rev-parse", "--is-shallow-repository").strip() == "true":
        die(
            "this is a shallow clone, so the branch tell cannot run.\n"
            "       Un clon shallow no tiene historial de `master`, asi que TODO doc parece no tener\n"
            "       commit de implementacion y el chequeo saldria verde sin haber mirado nada.\n"
            "       Arreglo: `git fetch --unshallow` (o en CI, `fetch-depth: 0`)."
        )
    if "master" not in branches():
        die(
            "no hay rama `master` local. Este chequeo compara contra ella; sin master no hay\n"
            "       nada contra lo que comparar y un verde no significaria nada."
        )
    depth = len(git("log", "--format=%H", "master").split())
    if depth < HISTORY_FLOOR:
        die(
            "`master` solo tiene %d commits, por debajo del suelo de %d: esto parece un clon\n"
            "       truncado, no un repo. Igual que un shallow clone, daria un verde vacio."
            % (depth, HISTORY_FLOOR)
        )
    return depth


_branches = None


def branches():
    global _branches
    if _branches is None:
        names = set()
        for line in git("for-each-ref", "--format=%(refname:short)", "refs/heads", "refs/remotes").splitlines():
            name = line.strip()
            if not name:
                continue
            names.add(name)
            if name.startswith("origin/"):
                names.add(name[len("origin/"):])
        _branches = names
    return _branches


def status_block(text):
    """Devuelve (linea de estado, bloque entero) o (None, None).

    `Estado`/`Status` manda y `Fecha` es solo respaldo, en ese orden y por una razon medida: cinco
    docs abren con `**Fecha:**` y llevan su `**Estado:**` en la linea siguiente, mientras
    `ios-f0-001.md` lleva las dos cosas en la MISMA linea. Mirar la fecha primero clasifica mal a
    esos cinco; no mirarla nunca pierde a `ios-f0-001`.
    """
    lines = text.splitlines()
    index = next((i for i, l in enumerate(lines) if STATUS_RE.match(l)), None)
    if index is None:
        index = next((i for i, l in enumerate(lines) if DATE_RE.match(l)), None)
    if index is None:
        return None, None
    end = index
    while end < len(lines) and lines[end].strip():
        end += 1
    return lines[index], "\n".join(lines[index:end])


def first_marker(line):
    found = [(line.index(m), m) for m in VOCABULARY if m in line]
    return min(found)[1] if found else None


def main():
    verbose = "--verbose" in sys.argv[1:]
    root = repo_root()
    os.chdir(root)
    depth = require_full_history()

    subjects = git("log", "--format=%s", "master").splitlines()
    known = branches()

    docs = sorted(f for f in os.listdir(BACKLOG) if f.endswith(".md"))
    if not docs:
        die("%s no tiene docs: o se movio el directorio, o este chequeo lleva sin mirar nada." % BACKLOG)

    liars, checked = [], 0
    for name in docs:
        if name in NOT_TICKETS:
            continue
        with io.open(os.path.join(BACKLOG, name), "r", encoding="utf-8", errors="replace") as fh:
            text = fh.read()
        line, block = status_block(text)
        marker = first_marker(line) if line else None
        if marker is None or marker in CLOSED or marker == ANALYSIS:
            continue

        cited = sorted({m.group(0).rstrip(".,`)") for m in BRANCH_RE.finditer(block)})
        if not cited:
            continue
        checked += 1
        if verbose:
            print("  %s %s -> %s" % (marker, name, ", ".join(cited)))

        gone = [b for b in cited if b not in known]
        if not gone:
            continue

        ticket = os.path.splitext(name)[0].upper()
        impl = [s for s in subjects if ("[%s]" % ticket) in s and not s.startswith("docs(")]
        if impl:
            liars.append((name, marker, gone, impl))

    print(
        "Revisados %d docs abiertos que citan rama, sobre %d ficheros y %d commits de master."
        % (checked, len(docs), depth)
    )
    if not liars:
        print("OK: ningun doc abierto cita una rama borrada cuyo trabajo ya esta en master.")
        return 0

    print("\n%d doc(s) MIENTEN — su rama ya no existe y su commit esta en master:\n" % len(liars))
    for name, marker, gone, impl in liars:
        print("  %s %s" % (marker, name))
        print("      rama citada, inexistente: %s" % ", ".join(gone))
        for subject in impl[:3]:
            print("      en master: %s" % subject[:110])
    print(
        "\nArreglo: pasar su linea de estado a `**Estado:** <tick> Done - master `<hash>``, citando el\n"
        "commit verificado. Un cierre RETROACTIVO si puede citar su hash: la regla de que un doc no\n"
        "cita el suyo aplica al cierre simultaneo, donde el hash todavia no existe."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
