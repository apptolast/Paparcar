#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""El corte de una version de Paparcar cabe en un comando.

    python tools/release/cut.py 1.6.0                  # el corte entero
    python tools/release/cut.py 1.6.0 --phases notes    # una sola fase, sin rehacer el bundle
    python tools/release/cut.py 1.6.0 --phases tag      # DESPUES de commitear el bump

Hasta ahora el corte eran nueve pasos escritos en prosa dentro de los docs de `RELEASE-*`, que un
humano reejecutaba de memoria. Ocho de ellos son mecanicos y verificables; el noveno --las notas en
nueve idiomas-- es editorial y no lo automatiza nada, asi que aqui solo se VALIDA.

[RELEASE-THE-CUT-MUST-BE-ONE-COMMAND-001]

## Por que un script y no un workflow

La logica del corte vive en un sitio que se puede ejecutar HOY, sin depender de nadie. El CI no
arranca desde el 13-09-2026 (facturacion de Actions, no YAML), y si la logica naciera dentro del
YAML seguiria sin poder correr en local. El CD futuro queda reducido a un disparador que llama a
esto.

⛔ **El corte lo dispara el user, nunca un merge a master.** Entre dos releases master recibe
docenas de commits; cortar version en cada uno quemaria `versionCode` por nada.

⛔ **Esto NO sube nada a Play.** Deja el AAB verificado y las cifras medidas; publicar es la accion
irreversible y de cara al publico, y sigue siendo humana.

## Las fases, y el fallo real que cada una evita

  bump     el numero vive en DOS sitios (Gradle y la web) y `WebSiteParityGuardrailTest` es el
           unico aviso si te dejas uno.
  web      reconstruye y avisa si `hosting/public/` cambio. Medido en 1.5.0: `APP_VERSION` no se
           hornea en ninguna pagina, asi que normalmente un bump no obliga a desplegar la web. Es
           una medida, no una ley: si algun dia cambia, que lo diga el script y no la web en
           produccion.
  suite    `testAll --rerun-tasks`. Los guards de Konsist no cambian bytecode, asi que sin
           `--rerun-tasks` Gradle los da por hechos y no llega a mirarlos.
  bundle   `bundleProdRelease --rerun-tasks` y copia el AAB fuera de `build/`, que esta gitignoreado
           por tres reglas: sin copia con nombre de version, el siguiente build lo pisa. Un bundle
           incremental ademas miente por megas al medirlo.
  sign     `keytool -printcert` contra la huella ESPERADA. El 03-09-2026 salio un AAB firmado con el
           certificado equivocado y la comprobacion que se uso --`jarsigner -verify`, "jar
           verified."-- sale verde con cualquier certificado.
  measure  sha256, bytes, entradas, overhead, y el `versionName` leido del manifest DENTRO del
           bundle, con el anterior ausente. Es la prueba de que el binario es de este arbol.
  notes    nueve locales, cada uno bajo el tope de 500 de Play. El techo historico es `fr-FR`.
  doc      rellena el doc del release con las cifras MEDIDAS, que es donde se cuela un numero viejo
           al copiarlo de la terminal a mano.
  tag      etiqueta anotada `v<version>` sobre el commit del bump. Es la respuesta a "que hay en
           produccion": 1.4.0 se publico el 13-10 con el arbol del 30-09 y nada en el repo lo decia.
           Va aparte porque necesita que el commit EXISTA, y commitear es del user.

## Limites declarados, para que nadie los descubra creyendo que estaban cubiertos

  · El `versionCode` del bundle no se verifica leyendo el bundle. El manifest del AAB es protobuf y
    un numero de dos cifras aparece por casualidad en cualquier parte; buscarlo por bytes seria un
    chequeo que siempre pasa. Lo que se verifica es el fichero de Gradle, que es de donde sale.
  · La fase `bump` pregunta a git si esa version ya se corto (etiqueta o commit que la escribiera).
    En un clon shallow --el `fetch-depth: 1` del CI-- esa pregunta se contesta a ciegas, asi que
    este script asume clon completo, como `tools/backlog/branch_tell.py`.
"""
import argparse
import hashlib
import pathlib
import re
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
GRADLE_FILE = ROOT / "app" / "build.gradle.kts"
WEB_CONFIG = ROOT / "web" / "lib" / "config.mjs"
PASTE = ROOT / "docs" / "release" / "play-listing" / "paste"
BACKLOG = ROOT / "docs" / "backlog"
HOSTING = ROOT / "hosting" / "public"

# El AAB sale del repo a proposito: esta gitignoreado por `build/`, `**/build/` y `*.aab`, y un
# bundle es derivado -- sale de un commit, no lo sustituye.
EXPORT_DIR = ROOT.parent

# Tope de Play por locale en las notas de version, y los NUEVE codigos de locale de Play.
# ⛔ El rumano es `ro`, NO `ro-RO`: es un codigo de Play, no de Android
# [PLAY-A-LOCALE-CODE-IS-PLAYS-NOT-ANDROIDS-001].
NOTE_LIMIT = 500
LOCALES = ["en-US", "es-ES", "it-IT", "pt-PT", "fr-FR", "de-DE", "nl-NL", "pl-PL", "ro"]

# Huella SHA-1 de la upload key vigente (`keystore/release.jks`, alias `paparcar-release`).
#
# ⛔ Vive AQUI, en el repo, y no se lee de `keystore.properties` aunque ese fichero se declare
# "fuente canonica de la firma" y traiga la huella en un comentario. Dos razones medidas:
#   1. esta gitignoreado, asi que en otra maquina no existe; y
#   2. es el MISMO fichero que configura la firma. Una expectativa que viaja con lo que comprueba no
#      comprueba nada: el 03-09-2026 el fallo fue justo que la configuracion local apuntaba a otro
#      keystore. Si la huella esperada saliera de ahi, habria pasado verde.
# Es una huella publica, no un secreto.
UPLOAD_KEY_SHA1 = "45:40:9F:1F:A8:8A:B2:A3:BB:25:15:CB:FB:78:F6:FD:E0:E1:BF:97"

# Las tres hojas que `testAll` existe para correr. Se INVOCA `testAll`, nunca esta lista -- eso es
# lo que arreglo [TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001], cuando cinco consumidores
# deletreaban sus tareas y uno se dejo `:app` fuera. Pero AFIRMAR que las tres se ejecutaron es lo
# contrario de deletrear la lista: si alguien las saca de `testAll`, esto lo caza.
SUITE_LEAVES = [
    ":shared:testDebugUnitTest",
    ":app:testMockDebugUnitTest",
    ":shared:compileKotlinIosSimulatorArm64",
]

PHASES = ["bump", "web", "suite", "bundle", "sign", "measure", "notes", "doc", "tag"]
DEFAULT_PHASES = [p for p in PHASES if p != "tag"]

DOC_OPEN = "<!-- cut:measured -->"
DOC_CLOSE = "<!-- /cut:measured -->"

VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")


class Fail(Exception):
    """Una fase que no puede garantizar lo suyo. Para el corte entero."""


def say(line=""):
    print(line, flush=True)


def phase_banner(name, index, total):
    say()
    say("== fase %d/%d  %s %s" % (index, total, name, "=" * (46 - len(name))))


# ---------------------------------------------------------------- git y gradle


def git(*args):
    done = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")
    return done.returncode, done.stdout.strip()


def gradle(tasks):
    """Corre gradlew y devuelve (codigo, lineas). Las lineas de tarea se van viendo al pasar.

    ⛔ Via `bash gradlew`: el repo no tiene `gradlew.bat` y en PowerShell `.\\gradlew` sale exit 0
    SIN compilar nada.
    """
    cmd = ["bash", "gradlew", *tasks, "--console=plain"]
    say("   $ " + " ".join(cmd))
    proc = subprocess.Popen(cmd, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding="utf-8", errors="replace", bufsize=1)
    lines = []
    for raw in proc.stdout:
        line = raw.rstrip("\n")
        lines.append(line)
        if line.startswith("> Task") or line.startswith("BUILD ") or "FAILED" in line:
            say("   " + line)
    return proc.wait(), lines


# ------------------------------------------------------------------- versiones


def read_gradle_versions():
    text = GRADLE_FILE.read_text(encoding="utf-8")
    code = re.search(r"^\s*versionCode = (\d+)\s*$", text, re.M)
    # ⛔ `versionName = "` con la comilla: `versionNameSuffix` esta tres lineas mas abajo y una
    # regex perezosa lo pilla.
    name = re.search(r'^\s*versionName = "([^"]+)"\s*$', text, re.M)
    if not code or not name:
        raise Fail("no encuentro versionCode/versionName en %s" % GRADLE_FILE.name)
    return int(code.group(1)), name.group(1)


def read_web_version():
    text = WEB_CONFIG.read_text(encoding="utf-8")
    found = re.search(r"^export const APP_VERSION = '([^']+)';\s*$", text, re.M)
    if not found:
        raise Fail("no encuentro APP_VERSION en %s" % WEB_CONFIG.name)
    return found.group(1)


def version_at_head():
    code, text = git("show", "HEAD:app/build.gradle.kts")
    if code != 0:
        return None
    found = re.search(r'^\s*versionName = "([^"]+)"\s*$', text, re.M)
    return found.group(1) if found else None


def aab_path(version, code):
    return EXPORT_DIR / ("paparcar-%s-vc%d.aab" % (version, code))


# ----------------------------------------------------------------- fase 1 bump


def phase_bump(version):
    old_code, old_name = read_gradle_versions()
    web_old = read_web_version()
    say("   ahora: Gradle %s (vc%d) / web %s" % (old_name, old_code, web_old))

    if old_name != web_old:
        raise Fail("los dos sitios ya discrepan: Gradle %s vs web %s. Arreglalo antes de cortar."
                   % (old_name, web_old))
    if version == old_name:
        raise Fail("%s es la version que ya hay escrita: nada que bumpear" % version)

    rc, tags = git("tag", "--list", "v%s" % version)
    if rc == 0 and tags:
        raise Fail("v%s ya esta etiquetada: esa version se corto ya" % version)
    rc, hits = git("log", "--oneline", "-S", 'versionName = "%s"' % version,
                   "--", "app/build.gradle.kts")
    if rc == 0 and hits:
        raise Fail("%s ya se escribio en el historial:\n%s" % (version, hits))

    new_code = old_code + 1
    text = GRADLE_FILE.read_text(encoding="utf-8")
    text = re.sub(r"^(\s*versionCode = )\d+\s*$", r"\g<1>%d" % new_code, text, count=1, flags=re.M)
    text = re.sub(r'^(\s*versionName = ")[^"]+(")\s*$', r"\g<1>%s\g<2>" % version, text,
                  count=1, flags=re.M)
    GRADLE_FILE.write_text(text, encoding="utf-8")

    web = WEB_CONFIG.read_text(encoding="utf-8")
    web = re.sub(r"^(export const APP_VERSION = ')[^']+(';)\s*$", r"\g<1>%s\g<2>" % version, web,
                 count=1, flags=re.M)
    WEB_CONFIG.write_text(web, encoding="utf-8")

    got_code, got_name = read_gradle_versions()
    if (got_code, got_name, read_web_version()) != (new_code, version, version):
        raise Fail("el bump no cuajo: Gradle %s (vc%d) / web %s"
                   % (got_name, got_code, read_web_version()))
    say("   escrito: %s (vc%d) en los DOS sitios" % (version, new_code))
    return {"version": version, "code": new_code, "previous": old_name}


# ------------------------------------------------------------------ fase 2 web


def phase_web():
    before = {}
    if HOSTING.exists():
        before = {p: p.read_bytes() for p in sorted(HOSTING.rglob("*")) if p.is_file()}

    done = subprocess.run(["node", "web/build.mjs"], cwd=ROOT, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")
    if done.returncode != 0:
        raise Fail("node web/build.mjs fallo:\n%s%s" % (done.stdout, done.stderr))

    after = {p: p.read_bytes() for p in sorted(HOSTING.rglob("*")) if p.is_file()}
    changed = [p for p in after if before.get(p) != after[p]] + [p for p in before if p not in after]

    done = subprocess.run(["node", "web/build.mjs", "--check"], cwd=ROOT, capture_output=True,
                          text=True, encoding="utf-8", errors="replace")
    if done.returncode != 0:
        raise Fail("node web/build.mjs --check fallo:\n%s%s" % (done.stdout, done.stderr))

    # Relativas a hosting/public, no a la raiz: es donde viven todas y asi la fase se puede probar
    # apuntando HOSTING a un directorio temporal.
    names = sorted(str(p.relative_to(HOSTING)).replace("\\", "/") for p in changed)
    if names:
        say("   !! hosting/public cambio en %d fichero(s):" % len(names))
        for name in names[:10]:
            say("       hosting/public/%s" % name)
        say("   -> este corte SI necesita `firebase deploy --only hosting` (con permiso del user)")
    else:
        say("   hosting/public igual byte a byte: este corte no obliga a desplegar la web")
    return {"web_changed": names}


# ---------------------------------------------------------------- fase 3 suite


def actionable_summary(lines):
    for line in reversed(lines):
        found = re.search(r"^(\d+) actionable tasks?: (.+)$", line.strip())
        if found:
            return int(found.group(1)), found.group(2)
    return None, None


def not_executed_leaves(lines):
    """Las hojas de SUITE_LEAVES que no salieron ejecutadas, con el motivo.

    Gradle imprime `> Task :x` a secas cuando corrio, y `> Task :x UP-TO-DATE` (o FROM-CACHE /
    SKIPPED / NO-SOURCE) cuando no. Ausente del log = ni se intento.
    """
    problems = {}
    for leaf in SUITE_LEAVES:
        hits = [l for l in lines if l == "> Task %s" % leaf or l.startswith("> Task %s " % leaf)]
        if not hits:
            problems[leaf] = "no aparece en el log"
        elif all(" " in h[len("> Task "):] for h in hits):
            problems[leaf] = hits[-1][len("> Task "):].split(" ", 1)[1]
    return problems


def phase_suite():
    code, lines = gradle(["testAll", "--rerun-tasks"])
    if code != 0:
        raise Fail("testAll fallo (exit %d)" % code)

    total, summary = actionable_summary(lines)
    if total is None:
        raise Fail("gradle no dijo cuantas tareas ejecuto: no puedo afirmar que mirara")
    # ⛔ Aqui NO vale "falla si alguna tarea sale UP-TO-DATE". Medido el 26-10-2026 con
    # `--rerun-tasks` puesto: 11 UP-TO-DATE (los `preBuild`), 12 SKIPPED y 52 NO-SOURCE, y el build
    # correcto. Un chequeo asi daria rojo SIEMPRE, y un guard que siempre falla se ignora. Lo que
    # Gradle si mide es su propio resumen: con trabajo salvado diria `N executed, M up-to-date`.
    if "up-to-date" in summary or "from-cache" in summary:
        raise Fail("gradle se salto trabajo: %d actionable tasks: %s" % (total, summary))
    say("   %d actionable tasks: %s" % (total, summary))

    problems = not_executed_leaves(lines)
    if problems:
        raise Fail("las hojas que testAll existe para correr no se ejecutaron:\n" +
                   "\n".join("     %s -> %s" % kv for kv in problems.items()))
    say("   ejecutadas las %d hojas de testAll (shared, app y el klib de iOS)" % len(SUITE_LEAVES))
    return {"suite_tasks": total}


# --------------------------------------------------------------- fase 4 bundle


def phase_bundle():
    code, version = read_gradle_versions()
    target = aab_path(version, code)

    rc, lines = gradle([":app:bundleProdRelease", "--rerun-tasks"])
    if rc != 0:
        raise Fail(":app:bundleProdRelease fallo (exit %d)" % rc)

    built = sorted((ROOT / "app" / "build" / "outputs" / "bundle").rglob("*.aab"))
    built = [p for p in built if "prodRelease" in str(p)]
    if not built:
        raise Fail("el build no dejo ningun AAB de prodRelease en app/build/outputs/bundle")
    if len(built) > 1:
        raise Fail("hay %d AAB de prodRelease y no se cual es el de este corte:\n%s"
                   % (len(built), "\n".join("     %s" % p for p in built)))

    target.write_bytes(built[0].read_bytes())
    say("   %s -> %s" % (built[0].relative_to(ROOT), target))
    return {"aab": str(target)}


# ----------------------------------------------------------------- fase 5 sign


def parse_sha1(printcert_output):
    """Las huellas SHA-1 que keytool imprime, tal cual: `\\t SHA1: 45:40:...` (20 bytes en hex).

    ⛔ Solo SHA1, y ancladas a linea entera: la salida trae tambien SHA256, que empieza igual al
    ojo y no sirve para esta comparacion.
    """
    return re.findall(r"^\s*SHA1:\s*((?:[0-9A-F]{2}:){19}[0-9A-F]{2})\s*$", printcert_output, re.M)


def printcert_sha1(aab):
    done = subprocess.run(["keytool", "-printcert", "-jarfile", str(aab)], capture_output=True,
                          text=True, encoding="utf-8", errors="replace")
    if done.returncode != 0:
        raise Fail("keytool -printcert fallo:\n%s%s" % (done.stdout, done.stderr))
    return parse_sha1(done.stdout)


def phase_sign():
    code, version = read_gradle_versions()
    aab = aab_path(version, code)
    if not aab.exists():
        raise Fail("no existe %s: corre antes la fase bundle" % aab)

    found = printcert_sha1(aab)
    if not found:
        raise Fail("keytool no devolvio ninguna huella SHA-1: el AAB no esta firmado")
    if UPLOAD_KEY_SHA1 not in found:
        raise Fail("firmado con OTRA clave.\n     esperada: %s\n     en el AAB: %s"
                   % (UPLOAD_KEY_SHA1, " / ".join(found)))
    say("   firmado con la upload key vigente (SHA-1 %s...)" % UPLOAD_KEY_SHA1[:17])
    return {"sha1": UPLOAD_KEY_SHA1}


# -------------------------------------------------------------- fase 6 measure


def as_tuple(version):
    return tuple(int(n) for n in version.split("."))


def previous_version(version):
    """La version anterior, y de donde sale -- (version, fuente) o (None, motivo).

    En un corte normal la respuesta buena es la que hay COMMITEADA en HEAD: el bump ya escribio la
    nueva en el arbol y HEAD todavia lleva la vieja. La etiqueta solo entra al reejecutar una fase
    cuando el bump ya esta commiteado, y entonces se exige que sea ESTRICTAMENTE anterior: ordenar
    etiquetas y coger la primera que no sea esta cogeria una mas nueva si existiera.
    """
    at_head = version_at_head()
    if at_head and at_head != version:
        return at_head, "HEAD"
    rc, tags = git("tag", "--list", "v*", "--sort=-v:refname")
    older = []
    for tag in tags.splitlines() if rc == 0 else []:
        name = tag.strip()[1:]
        if VERSION_RE.match(name) and as_tuple(name) < as_tuple(version):
            older.append(name)
    if older:
        return max(older, key=as_tuple), "etiqueta v%s" % max(older, key=as_tuple)
    return None, "sin etiqueta anterior ni version distinta en HEAD"


def measure_aab(aab, version, previous):
    data = aab.read_bytes()
    with zipfile.ZipFile(aab) as zf:
        infos = zf.infolist()
        manifest = zf.read("base/manifest/AndroidManifest.xml")
    stored = sum(i.compress_size for i in infos)
    out = {
        "sha256": hashlib.sha256(data).hexdigest(),
        "bytes": len(data),
        "entries": len(infos),
        "overhead_pct": (len(data) - stored) / len(data) * 100,
    }
    # El manifest del bundle es protobuf, pero los valores de cadena viajan como UTF-8 plano, asi
    # que la presencia del versionName se puede leer por bytes. El versionCode NO: dos cifras
    # aparecen por casualidad en cualquier parte (ver "Limites declarados").
    if version.encode() not in manifest:
        raise Fail("el manifest del bundle no dice %s: ese AAB no es de este arbol" % version)
    out["manifest_says"] = version
    if previous and previous != version:
        if previous.encode() in manifest:
            raise Fail("el manifest del bundle todavia dice %s" % previous)
        out["manifest_without"] = previous
    return out


def phase_measure():
    code, version = read_gradle_versions()
    aab = aab_path(version, code)
    if not aab.exists():
        raise Fail("no existe %s: corre antes la fase bundle" % aab)

    previous, source = previous_version(version)
    out = measure_aab(aab, version, previous)
    say("   sha256   %s" % out["sha256"])
    say("   tamano   %s B  |  %d entradas  |  %.1f %% de overhead de zip"
        % ("{:,}".format(out["bytes"]).replace(",", "."), out["entries"], out["overhead_pct"]))
    if previous:
        say("   manifest versionName %s presente, %s ausente (anterior segun %s)"
            % (version, previous, source))
    else:
        say("   manifest versionName %s presente (%s, asi que no se comprueba la ausencia de la"
            " anterior)" % (version, source))

    for candidate in sorted(EXPORT_DIR.glob("paparcar-%s-vc*.aab" % previous)) if previous else []:
        delta = out["bytes"] - candidate.stat().st_size
        out["vs_previous"] = "%+d B (%+.2f %%) vs %s" % (
            delta, delta / candidate.stat().st_size * 100, candidate.name)
        say("   vs anterior  %s" % out["vs_previous"])
        break
    return out


# ---------------------------------------------------------------- fase 7 notes


def parse_notes(path):
    """`release-notes-<version>.txt` trae cada locale en etiquetas `<xx-XX>` -- o `<ro>`.

    ⛔ El rumano NO lleva region, asi que una regex que exija `xx-XX` se lo salta en silencio.
    """
    text = path.read_text(encoding="utf-8")
    return {m.group(1): m.group(2).strip()
            for m in re.finditer(r"<([a-z]{2}(?:-[A-Z]{2})?)>\n(.*?)\n</\1>", text, re.S)}


def check_notes(notes):
    problems = []
    for loc in LOCALES:
        if loc not in notes:
            problems.append("%s: falta" % loc)
        elif not notes[loc]:
            problems.append("%s: vacio" % loc)
        elif len(notes[loc]) > NOTE_LIMIT:
            problems.append("%s: %d caracteres > %d" % (loc, len(notes[loc]), NOTE_LIMIT))
    for loc in sorted(set(notes) - set(LOCALES)):
        problems.append("%s: locale que Play no espera (el rumano es `ro`, no `ro-RO`)" % loc)
    return problems


def phase_notes():
    _, version = read_gradle_versions()
    path = PASTE / ("release-notes-v%s.txt" % version)
    if not path.exists():
        raise Fail("faltan las notas: %s\n     Se escriben a mano, en los %d locales."
                   % (path.relative_to(ROOT), len(LOCALES)))

    notes = parse_notes(path)
    problems = check_notes(notes)
    if problems:
        raise Fail("las notas no estan listas:\n" + "\n".join("     %s" % p for p in problems))

    # La cuenta es del cuerpo sin el salto final, que es lo que se pega en el Console.
    counts = " | ".join("%s %d" % (loc, len(notes[loc])) for loc in LOCALES)
    top = max(LOCALES, key=lambda l: len(notes[l]))
    say("   %s" % counts)
    say("   techo %s con %d, %d de margen sobre %d"
        % (top, len(notes[top]), NOTE_LIMIT - len(notes[top]), NOTE_LIMIT))
    return {"notes_file": path.name, "notes_counts": counts,
            "notes_top": "%s %d/%d" % (top, len(notes[top]), NOTE_LIMIT)}


# ------------------------------------------------------------------ fase 8 doc


def doc_table(state):
    rows = [
        ("sha256", "`%s`" % state.get("sha256", "?")),
        ("tamano", "%s B · %s entradas · %.1f %% de overhead de zip" % (
            "{:,}".format(state.get("bytes", 0)).replace(",", "."),
            state.get("entries", "?"), state.get("overhead_pct", 0.0))),
        ("vs. anterior", state.get("vs_previous", "sin AAB anterior en disco para comparar")),
        ("firma", "SHA-1 `%s` (upload key vigente)" % state.get("sha1", "?")),
        ("leido del bundle", "`versionName %s` presente en `base/manifest/AndroidManifest.xml`%s" % (
            state.get("version", "?"),
            "; `%s` **ausente**" % state["manifest_without"] if state.get("manifest_without")
            else "")),
        ("notas", "`%s` · %s" % (state.get("notes_file", "?"), state.get("notes_top", "?"))),
        ("suite", "`testAll --rerun-tasks` · %s tareas ejecutadas" % state.get("suite_tasks", "?")),
        ("web", "`hosting/public/` %s" % ("cambio en %d fichero(s): DESPLEGAR"
                                          % len(state["web_changed"]) if state.get("web_changed")
                                          else "igual byte a byte: no hace falta desplegar")),
        ("exportado en", "`%s`" % state.get("aab", "?")),
    ]
    body = ["| | |", "|---|---|"] + ["| %s | %s |" % r for r in rows]
    return "\n".join([DOC_OPEN, ""] + body + ["", DOC_CLOSE])


def phase_doc(state):
    version = state.get("version") or read_gradle_versions()[1]
    slug = "release-%s-001.md" % version.replace(".", "-")
    path = BACKLOG / slug
    table = doc_table(state)

    if path.exists():
        text = path.read_text(encoding="utf-8")
        if DOC_OPEN not in text or DOC_CLOSE not in text:
            raise Fail("%s existe pero no tiene los marcadores %s / %s donde escribir las cifras"
                       % (slug, DOC_OPEN, DOC_CLOSE))
        head, rest = text.split(DOC_OPEN, 1)
        _, tail = rest.split(DOC_CLOSE, 1)
        path.write_text(head + table + tail, encoding="utf-8")
        say("   cifras medidas actualizadas en %s" % slug)
    else:
        path.write_text(
            "# RELEASE-%s-001 · el AAB de %s (versionCode %s)\n\n"
            "**Estado:** 🔵 En progreso · cortado con `python tools/release/cut.py %s`\n\n"
            "## Problema\n\n"
            "<!-- TODO: que lleva en produccion el build anterior, desde cuando, y que entra en\n"
            "este. `git log --oneline <tag anterior>..HEAD` -->\n\n"
            "## El AAB de esta version\n\n"
            "%s\n\n"
            "## Notas de version\n\n"
            "`docs/release/play-listing/paste/%s` — nueve locales, **a mano**.\n\n"
            "## Criterio de exito\n\n"
            "- [x] bump en los **dos** sitios · suite verde con `--rerun-tasks` · firma verificada\n"
            "      con `-printcert` · notas en los 9 locales bajo 500\n"
            "- [ ] **Del user**: subir el AAB a Play, pegar las notas y publicar\n"
            % (version.replace(".", "-"), version, state.get("code", "?"), version, table,
               state.get("notes_file", "?")),
            encoding="utf-8")
        say("   %s creado con las cifras medidas" % slug)
    return {"doc": slug}


# ------------------------------------------------------------------ fase 9 tag


def phase_tag(version):
    code, at_file = read_gradle_versions()
    if at_file != version:
        raise Fail("el arbol dice %s, no %s" % (at_file, version))
    at_head = version_at_head()
    if at_head != version:
        raise Fail("HEAD todavia dice %s: commitea el bump antes de etiquetar" % at_head)

    rc, dirty = git("status", "--porcelain", "--", "app/build.gradle.kts", "web/lib/config.mjs")
    if rc == 0 and dirty:
        raise Fail("el bump esta a medio commitear:\n%s" % dirty)
    rc, _ = git("rev-parse", "-q", "--verify", "refs/tags/v%s" % version)
    if rc == 0:
        raise Fail("v%s ya existe" % version)

    rc, out = git("tag", "-a", "v%s" % version, "-m", "Paparcar %s (versionCode %d)" % (version, code))
    if rc != 0:
        raise Fail("git tag fallo: %s" % out)
    _, head = git("rev-parse", "--short", "HEAD")
    say("   v%s -> %s" % (version, head))
    say("   produccion = `git describe --tags --abbrev=0`; hotfix = rama desde esa etiqueta")
    return {"tag": "v%s" % version}


# ------------------------------------------------------------------------ main


def main():
    ap = argparse.ArgumentParser(description="El corte de una version, en un comando.")
    ap.add_argument("version", nargs="?", help="versionName nuevo, p.ej. 1.6.0")
    ap.add_argument("--phases", default=",".join(DEFAULT_PHASES),
                    help="fases a correr, por coma. Todas menos tag por defecto: %s"
                         % ",".join(DEFAULT_PHASES))
    args = ap.parse_args()

    wanted = [p.strip() for p in args.phases.split(",") if p.strip()]
    unknown = [p for p in wanted if p not in PHASES]
    if unknown:
        ap.error("fase(s) desconocida(s): %s. Hay: %s" % (", ".join(unknown), ", ".join(PHASES)))
    wanted = [p for p in PHASES if p in wanted]

    if "bump" in wanted or "tag" in wanted:
        if not args.version:
            ap.error("las fases bump y tag necesitan la version: cut.py 1.6.0")
        if not VERSION_RE.match(args.version):
            ap.error("version '%s': se espera x.y.z" % args.version)

    state = {}
    if args.version:
        state["version"] = args.version

    runners = {
        "bump": lambda: phase_bump(args.version),
        "web": phase_web,
        "suite": phase_suite,
        "bundle": phase_bundle,
        "sign": phase_sign,
        "measure": phase_measure,
        "notes": phase_notes,
        "doc": lambda: phase_doc(state),
        "tag": lambda: phase_tag(args.version),
    }

    say("corte de version -- repo %s" % ROOT)
    for index, name in enumerate(wanted, start=1):
        phase_banner(name, index, len(wanted))
        try:
            state.update(runners[name]() or {})
        except Fail as bad:
            say()
            # ⛔ Salida en ASCII puro, a proposito: al redirigir a un fichero Windows usa cp1252 y
            # un emoji aqui mataria el script con UnicodeEncodeError justo al reportar el fallo.
            say("FALLO en la fase %s: %s" % (name, bad))
            say()
            say("  nada mas se ha hecho. Arregla eso y reanuda con:")
            say("    python tools/release/cut.py %s --phases %s"
                % (args.version or "<version>", ",".join(wanted[index - 1:])))
            return 1
        if "version" not in state:
            state["version"] = read_gradle_versions()[1]

    say()
    say("== listo " + "=" * 52)
    if "bundle" in wanted:
        say("   AAB: %s" % state.get("aab", "?"))
    say("   OJO: subir a Play y publicar es del user. Este script no sube nada.")
    if "tag" not in wanted and "bump" in wanted:
        say("   tras commitear el bump:  python tools/release/cut.py %s --phases tag"
            % state.get("version", "<version>"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
