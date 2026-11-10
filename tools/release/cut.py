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
  listing  sube a Play el TEXTO de la ficha vigente (`published-listing.txt` -> `paste/`) en los
           nueve idiomas: titulo, descripcion corta y completa. No toca capturas. Solo escribe los
           idiomas que difieren, y nunca cancela una revision en curso: si hay algo en revision,
           Play rechaza el commit y no cambia nada. [PLAY-THE-LISTING-MOVES-TO-1-6-0-001]

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
import os
import pathlib
import re
import subprocess
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import play_api  # noqa: E402
import play_locales  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
GRADLE_FILE = ROOT / "app" / "build.gradle.kts"
WEB_CONFIG = ROOT / "web" / "lib" / "config.mjs"
LISTING_DIR = ROOT / "docs" / "release" / "play-listing"
PASTE = LISTING_DIR / "paste"
BACKLOG = ROOT / "docs" / "backlog"
HOSTING = ROOT / "hosting" / "public"

# El AAB sale del repo a proposito: esta gitignoreado por `build/`, `**/build/` y `*.aab`, y un
# bundle es derivado -- sale de un commit, no lo sustituye.
EXPORT_DIR = ROOT.parent

# Tope de Play por locale en las notas de version. Los NUEVE codigos y como se parsean viven en
# play_locales.py, con la doctrina del rumano (`ro`, sin region) y el regex que la respeta.
NOTE_LIMIT = 500
LOCALES = play_locales.PLAY_LOCALES

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

PHASES = ["bump", "web", "suite", "bundle", "sign", "measure", "notes", "doc", "tag",
          "verify", "upload", "listing"]
# ⛔ `tag` necesita que el commit del bump exista, y `verify`/`upload` hablan con la tienda: las
# tres se piden a mano. Subir a Play no puede ser el efecto de teclear el comando del corte.
DEFAULT_PHASES = [p for p in PHASES if p not in ("tag", "verify", "upload", "listing")]

DOC_OPEN = "<!-- cut:measured -->"
DOC_CLOSE = "<!-- /cut:measured -->"

VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")

# Las redes publicitarias que el binario puede llevar dentro. Por cada una: la clave del catalogo
# de versiones, los nombres con los que la ficha tiene que nombrarla, la propiedad que lleva su
# identificador de app y el `meta-data` del manifest donde ese identificador tiene que aterrizar.
# [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]
AD_SDKS = {
    "facebook-core": {
        "names": ("Meta", "Facebook", "Instagram"),
        "prop": "FACEBOOK_APP_ID",
        "meta": "com.facebook.sdk.ApplicationId",
        # Lo que Play cruza con la declaracion de ID de publicidad: sin `AD_ID` en el AAB, la
        # Console da un ERROR que solo se ve al crear la version, y el identificador sale a ceros.
        # [RELEASE-THE-AAB-MUST-CARRY-AD-ID-001]
        "requires": ("com.google.android.gms.permission.AD_ID",),
        # Las dos APIs de segmentacion que el SDK fusiona solo y el manifest retira: la politica
        # dice tres nombres de evento y el GAID, "y nada mas".
        "forbids": (
            "android.permission.ACCESS_ADSERVICES_TOPICS",
            "android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE",
        ),
    },
}


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


def declared_ad_networks():
    """Las redes publicitarias que ESTE arbol mete en el binario, por su nombre publico.

    No basta con que la clave este en el catalogo: `libs.versions.toml` puede declarar una
    dependencia que nadie usa. Lo que cuenta es que algun `build.gradle.kts` la pida.
    """
    builds = "\n".join(
        f.read_text(encoding="utf-8")
        for f in sorted(ROOT.glob("*/build.gradle.kts"))
    )
    catalog = (ROOT / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
    out = []
    for key, spec in AD_SDKS.items():
        alias = key.replace("-", ".")
        if ("%s = {" % key) in catalog and ("libs.%s" % alias) in builds:
            out.append((key, spec))
    return out


def build_prop(key):
    """Lo que `prop()` de `app/build.gradle.kts` resolveria para `key`, con su MISMO orden.

    keystore.properties -> local.properties -> variable de entorno. Replicarlo aqui es a proposito:
    este chequeo tiene que leer el valor por donde lo lee el build, no por donde nos convenga.
    """
    for name in ("keystore.properties", "local.properties"):
        f = ROOT / name
        if not f.exists():
            continue
        for line in f.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("#") or "=" not in line:
                continue
            k, _, v = line.partition("=")
            if k.strip() == key and v.strip():
                return v.strip()
    return (os.environ.get(key) or "").strip() or None


def check_listing_names_the_ad_network(version):
    """Un build que lleva una red publicitaria no se corta con una ficha que no la nombra.

    ⛔ Esto NO es el guard de la web ni el de la politica: esos dos describen el PRODUCTO y se
    mueven en la tarea que cambia el producto. La ficha describe el BUILD PUBLICADO, asi que su
    momento es exactamente este -- el corte -- y ni uno antes: nombrar en la tienda algo que la
    app todavia no hace es el defecto contrario. [DOCS-THE-STORE-LISTING-IS-A-CONSUMER-TOO-001]

    El bloque de PRIVACIDAD de la ficha enumera que pasa con tus datos y termina en «sin
    anuncios». Cada frase suya puede seguir siendo literalmente cierta con el SDK dentro -- no se
    muestran anuncios, y la ubicacion no sale hacia ninguna red -- y aun asi el bloque entero se
    lee como «nada tuyo se usa para publicidad», que deja de serlo. Por eso lo que se exige es que
    la red se NOMBRE, no que desaparezca una frase: una prohibicion de frases se esquiva
    reescribiendola. [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]
    """
    networks = declared_ad_networks()
    if not networks:
        return
    listing = LISTING_DIR / ("LISTING-v%s.md" % version)
    nombres = ", ".join(spec["names"][0] for _, spec in networks)
    if not listing.exists():
        raise Fail(
            "el build lleva %s y no existe %s.\n"
            "    La ficha describe el build publicado: la que se va a publicar tiene que\n"
            "    nombrar la red, en los nueve idiomas. Margen medido en la ficha anterior:\n"
            "    fr-FR 9 libres y de-DE/nl-NL 18, asi que hay que CONDENSAR antes de escribir."
            % (nombres, listing.name))
    text = listing.read_text(encoding="utf-8")
    try:
        blocks = listing_locale_blocks(text)
    except ValueError as exc:
        raise Fail("no se pudo leer %s: %s" % (listing.name, exc))
    falta = play_locales.missing(blocks)
    if falta:
        raise Fail("%s no tiene descripcion completa para: %s" % (listing.name, ", ".join(falta)))
    mudos = []
    for key, spec in networks:
        for loc in LOCALES:
            if not any(name in blocks[loc] for name in spec["names"]):
                mudos.append("%s (%s)" % (loc, key))
    if mudos:
        raise Fail(
            "el build lleva %s y la ficha no lo nombra en: %s.\n"
            "    Un bloque de PRIVACIDAD que termina en «sin anuncios» sin decir que el movil\n"
            "    manda el identificador de publicidad a esa red se lee como lo contrario de lo\n"
            "    que hace el binario. [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]"
            % (nombres, ", ".join(mudos)))
    say("   ficha: %s nombra la red publicitaria en los nueve idiomas" % listing.name)


LISTING_BLOCK_RE = re.compile(
    r"^### (%s)\s*\n+```\n(.*?)\n```" % play_locales.LOCALE_PATTERN, re.S | re.M)


def listing_locale_blocks(text):
    """{locale: descripcion completa} de una ficha `LISTING-v*.md`.

    Solo la seccion de la descripcion completa: el nombre y la breve tienen sus propios bloques y
    nadie va a nombrar una red publicitaria en 80 caracteres.
    """
    try:
        start = text.index("## Descripci\u00f3n completa")
    except ValueError:
        raise ValueError("no tiene seccion «Descripcion completa»")
    body = text[start:]
    blocks = {m.group(1): m.group(2) for m in LISTING_BLOCK_RE.finditer(body)}
    if not blocks:
        raise ValueError("la seccion «Descripcion completa» no tiene bloques por idioma")
    return blocks


def phase_bump(version):
    check_listing_names_the_ad_network(version)
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
    check_aab_carries_the_ad_key(target)
    check_aab_permissions_match_the_declaration(target)
    return {"aab": str(target)}


def check_aab_carries_the_ad_key(aab):
    """Un AAB que lleva la red publicitaria dentro y NO lleva su clave no se sube.

    ⛔ Es el fallo mas caro de todos los de este ticket, porque es el unico que no se ve: la app
    funciona igual, no hay crash, no hay log, y la atribucion esta muerta. O sea que pagas anuncios
    sin poder medir ni uno, y lo descubres en el panel de Meta semanas despues.
    Pasa con solo construir el corte en una maquina sin la clave en `local.properties` -- que es
    exactamente lo que hacen CI y un clon limpio, a proposito
    [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001].

    Se mide en DOS pasos, y el segundo es el que tiene dientes:

      1. la clave esta configurada donde el build la lee (`build_prop`, mismo orden que Gradle);
      2. **ese valor aparece dentro del AAB**. El manifiesto de un AAB va en protobuf, pero las
         cadenas viajan como UTF-8 crudo, asi que buscar los bytes del valor prueba que el
         `manifestPlaceholder` aterrizo de verdad. Comprobar solo (1) seria creerle al input: si
         el placeholder se rompe, el AAB sale sin clave y (1) sigue diciendo que todo bien.
    """
    networks = declared_ad_networks()
    if not networks:
        return
    manifest = aab_manifest_bytes(aab)
    for key, spec in networks:
        value = build_prop(spec["prop"])
        if not value:
            raise Fail(
                "el build lleva %s y %s esta vacia o no esta.\n"
                "    Un AAB sin ella sube con la atribucion MUERTA y sin que nada se queje: la\n"
                "    app va igual y los anuncios se pagan sin poder medirlos. Ponla en\n"
                "    local.properties (o como variable de entorno) y vuelve a correr `bundle`."
                % (spec["names"][0], spec["prop"]))
        if value.encode("utf-8") not in manifest:
            raise Fail(
                "%s esta configurada pero NO aparece en el manifiesto de %s.\n"
                "    El `manifestPlaceholder` de `%s` no aterrizo: el AAB se subiria con la\n"
                "    clave vacia aunque tu maquina la tenga puesta."
                % (spec["prop"], aab.name, spec["meta"]))
        say("   %s: clave presente en el AAB (%d chars, %s...)"
            % (spec["names"][0], len(value), value[:4]))


def check_aab_permissions_match_the_declaration(aab):
    """El AAB lleva los permisos que la Console declara, y no los que la politica niega.

    ⛔ El manifest del CODIGO ya lo vigila `AdvertisingDisclosureGuardrailTest`; esto mira el
    BINARIO, que es lo que Play lee. Entre uno y otro esta el merge de manifiestos: una dependencia
    o un flavor con `tools:node="remove"` puede quitar `AD_ID` sin tocar una linea nuestra.
    El 10-11-2026 la Console bloqueo la 1.6.0 por un artefacto activo sin `AD_ID` (era la vc16,
    sin Meta) y la unica forma de seguir fue desactivar ese error -- que, desactivado, ya no
    avisaria si un corte futuro saliera sin el permiso. Este chequeo es el que queda.
    [RELEASE-THE-AAB-MUST-CARRY-AD-ID-001]
    """
    networks = declared_ad_networks()
    if not networks:
        return
    manifest = aab_manifest_bytes(aab)
    for key, spec in networks:
        missing = [p for p in spec.get("requires", ()) if p.encode("utf-8") not in manifest]
        if missing:
            raise Fail(
                "el build lleva %s y el AAB NO declara %s.\n"
                "    Play lo cruza con la declaracion de ID de publicidad (\"la app lo usa\"): la\n"
                "    version se bloquea en la Console, o sale con el identificador a ceros si alguien\n"
                "    desactiva ese error. Busca quien lo retira en el manifest fusionado de prodRelease."
                % (spec["names"][0], ", ".join(missing)))
        present = [p for p in spec.get("forbids", ()) if p.encode("utf-8") in manifest]
        if present:
            raise Fail(
                "el AAB declara %s.\n"
                "    La politica promete el identificador de publicidad y tres eventos, nada mas: estas\n"
                "    APIs de segmentacion las fusiona %s solo y el manifest tiene que retirarlas."
                % (", ".join(present), spec["names"][0]))
        say("   %s: permisos del AAB = declaracion (%s presente; %s ausentes)"
            % (spec["names"][0],
               ", ".join(p.rsplit(".", 1)[-1] for p in spec.get("requires", ())),
               ", ".join(p.rsplit(".", 1)[-1] for p in spec.get("forbids", ()))))


def aab_manifest_bytes(aab):
    """El manifiesto del modulo base de un AAB, en crudo (protobuf)."""
    with zipfile.ZipFile(aab) as z:
        entry = "base/manifest/AndroidManifest.xml"
        if entry not in z.namelist():
            raise Fail("%s no tiene %s: el formato del AAB cambio y este chequeo no mira nada"
                       % (aab.name, entry))
        return z.read(entry)


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
    """`release-notes-<version>.txt` trae cada locale en etiquetas `<xx-XX>` -- o `<ro>`."""
    return play_locales.tagged_blocks(path.read_text(encoding="utf-8"))


def check_notes(notes):
    problems = []
    for loc in play_locales.missing(notes):
        problems.append("%s: falta" % loc)
    for loc in LOCALES:
        if loc not in notes:
            continue
        if not notes[loc]:
            problems.append("%s: vacio" % loc)
        elif len(notes[loc]) > NOTE_LIMIT:
            problems.append("%s: %d caracteres > %d" % (loc, len(notes[loc]), NOTE_LIMIT))
    for loc in play_locales.unexpected(notes):
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


# -------------------------------------------------------------- fase verify


def live_state(play):
    """Lee la tienda y descarta la edicion. Solo lectura: no hay `:commit` en ningun camino."""
    edit = play.open_edit()
    try:
        return play.tracks(edit), play.listings(edit)
    finally:
        play.discard_edit(edit)


def check_against_store(version, code, tracks, listings, paste_dir):
    """Lo que la tienda contradice del repo. Devuelve (lineas, problemas).

    ⛔ Que el repo vaya por DELANTE es normal: se acaba de bumpear y aun no se ha subido. Lo que no
    puede pasar es lo contrario -- la tienda publicando un versionCode que el repo no conoce --
    porque entonces el arbol no es el de produccion y nadie se habia enterado. Paso: la memoria dio
    1.5.0 por "pendiente de subir" durante 13 dias estando ya publicada.
    """
    lines, problems = [], []
    for track in tracks:
        for release in track.get("releases", []):
            codes = [int(c) for c in (release.get("versionCodes") or [])]
            langs = sorted(n.get("language") for n in (release.get("releaseNotes") or []))
            lines.append("%-11s %-10s %s  notas en %d idiomas"
                         % (track.get("track"), release.get("status"),
                            codes or "sin version", len(langs)))
            for live in codes:
                if live > code:
                    problems.append(
                        "%s publica versionCode %d y el repo va por %d: el arbol NO es el de ahi"
                        % (track.get("track"), live, code))
            if codes:
                for absent in play_locales.missing(langs):
                    problems.append("%s: la release viva no tiene notas en %s"
                                    % (track.get("track"), absent))
    live_locales = sorted(l.get("language") for l in listings)
    lines.append("ficha publicada en %d idiomas: %s" % (len(live_locales), " ".join(live_locales)))
    for absent in play_locales.missing(live_locales):
        problems.append("la ficha publicada no tiene %s" % absent)

    for listing in listings:
        locale = listing.get("language")
        source = paste_dir / ("play-%s.txt" % locale)
        if not source.exists():
            problems.append("%s: la tienda lo publica y el repo no tiene %s" % (locale, source.name))
            continue
        want = play_locales.paste_sections(source.read_text(encoding="utf-8"))
        # ⛔ La descripcion COMPLETA tambien: es donde cambia casi todo. Comparar solo titulo y
        # corta dio "cuadran" el 10-11 con la ficha 1.6.0 sin subir. [PLAY-THE-LISTING-MOVES-TO-1-6-0-001]
        for field, live_value in (("name", listing.get("title")),
                                 ("short", listing.get("shortDescription")),
                                 ("full", listing.get("fullDescription"))):
            live_text = (live_value or "").strip()
            if live_text != want[field]:
                shown_live, shown_repo = first_difference(live_text, want[field])
                # ascii(): la salida va a una consola cp1252 y el polaco o el rumano la tumban.
                problems.append("%s/%s: la ficha viva no es la del repo\n       tienda: %s\n"
                                "       repo  : %s" % (locale, field, ascii(shown_live),
                                                       ascii(shown_repo)))
    return lines, problems


def first_difference(live, repo):
    """La primera LINEA en que difieren: volcar 4000 caracteres no dice donde esta el cambio."""
    live_lines, repo_lines = live.splitlines(), repo.splitlines()
    for index in range(max(len(live_lines), len(repo_lines))):
        a = live_lines[index] if index < len(live_lines) else "<fin>"
        b = repo_lines[index] if index < len(repo_lines) else "<fin>"
        if a != b:
            return a, b
    return live, repo


def phase_verify():
    code, version = read_gradle_versions()
    play = play_api.Play(ROOT / play_api.CREDENTIAL)
    say("   cuenta de servicio  %s" % play.account)
    say("   el repo va por      %s (vc%d)" % (version, code))
    try:
        tracks, listings = live_state(play)
    except play_api.PlayError as bad:
        raise Fail(str(bad))

    lines, problems = check_against_store(version, code, tracks, listings, PASTE)
    for line in lines:
        say("   " + line)
    if problems:
        raise Fail("la tienda contradice al repo:\n" +
                   "\n".join("     %s" % p for p in problems))
    say("   la tienda y el repo cuadran")
    return {}


# -------------------------------------------------------------- fase upload


def phase_upload():
    code, version = read_gradle_versions()
    aab = aab_path(version, code)
    if not aab.exists():
        raise Fail("no existe %s: corre antes la fase bundle" % aab)

    notes_path = PASTE / ("release-notes-v%s.txt" % version)
    if not notes_path.exists():
        raise Fail("faltan las notas: %s" % notes_path.relative_to(ROOT))
    notes = parse_notes(notes_path)
    problems = check_notes(notes)
    if problems:
        raise Fail("las notas no estan listas, no subo nada:\n" +
                   "\n".join("     %s" % p for p in problems))

    play = play_api.Play(ROOT / play_api.CREDENTIAL)
    say("   cuenta de servicio  %s" % play.account)
    say("   subiendo %s (%s MB) al canal %s"
        % (aab.name, "{:.1f}".format(aab.stat().st_size / 1024 / 1024), play_api.UPLOAD_TRACK))

    try:
        edit = play.open_edit()
    except play_api.PlayError as bad:
        raise Fail(str(bad))
    try:
        got = play.upload_bundle(edit, aab)
        if int(got) != code:
            raise Fail("Play dice haber recibido el versionCode %s y el AAB es el %d" % (got, code))
        say("   bundle aceptado: versionCode %s" % got)
        play.set_track(edit, play_api.UPLOAD_TRACK, code,
                       [(loc, notes[loc]) for loc in play_locales.PLAY_LOCALES])
        say("   canal %s configurado en BORRADOR con las notas de los 9 locales"
            % play_api.UPLOAD_TRACK)
        # ⛔ El commit es lo unico que cambia algo en la cuenta. Si cualquier paso de arriba falla,
        # la edicion se descarta y en Play no queda nada a medias.
        play.commit_edit(edit)
    except play_api.PlayError as bad:
        play.discard_edit(edit)
        raise Fail("%s\n     La edicion se ha descartado: en Play no queda nada a medias." % bad)
    except Exception:
        play.discard_edit(edit)
        raise

    say("   subido y en borrador.")
    say("   OJO: NO esta publicado. Promoverlo a produccion es del user, en el Console.")
    return {"uploaded_to": play_api.UPLOAD_TRACK}


# ------------------------------------------------------------- fase listing

LISTING_LIMITS = (("name", "title", 30), ("short", "shortDescription", 80),
                  ("full", "fullDescription", 4000))


def phase_listing():
    """Sube el texto de la ficha vigente a Play, idioma a idioma, y lo relee para comprobarlo."""
    play = play_api.Play(ROOT / play_api.CREDENTIAL)
    say("   cuenta de servicio  %s" % play.account)
    try:
        edit = play.open_edit()
    except play_api.PlayError as bad:
        raise Fail(str(bad))
    try:
        live = {l["language"]: l for l in play.listings(edit)}
        missing = play_locales.missing(sorted(live))
        if missing:
            raise Fail("la tienda no tiene ficha en %s: este script solo actualiza, no crea"
                       % ", ".join(missing))
        changed = []
        for locale in play_locales.PLAY_LOCALES:
            source = PASTE / ("play-%s.txt" % locale)
            want = play_locales.paste_sections(source.read_text(encoding="utf-8"))
            body = dict(live[locale])
            for key, field, limit in LISTING_LIMITS:
                if not want[key] or len(want[key]) > limit:
                    raise Fail("%s/%s: %d caracteres, el tope es %d"
                               % (locale, key, len(want[key]), limit))
                body[field] = want[key]
            if body == live[locale]:
                say("   %-6s igual que la tienda" % locale)
                continue
            play.put_listing(edit, body)
            changed.append(locale)
            say("   %-6s preparado (completa %d/4000)" % (locale, len(want["full"])))
        if not changed:
            play.discard_edit(edit)
            say("   nada que subir: la tienda ya publica este texto")
            return {}
        play.validate_edit(edit)
        play.commit_edit(edit)
    except play_api.PlayError as bad:
        play.discard_edit(edit)
        raise Fail("%s\n     La edicion se ha descartado: en Play no ha cambiado nada." % bad)
    except Exception:
        play.discard_edit(edit)
        raise

    # La prueba no es que Play aceptara el commit, es leer lo que publica ahora.
    code, version = read_gradle_versions()
    tracks, listings = live_state(play)
    _, problems = check_against_store(version, code, tracks, listings, PASTE)
    if problems:
        raise Fail("Play acepto el commit pero lo publicado no es el repo:\n" +
                   "\n".join("     %s" % p for p in problems))
    say("   subido y releido: la ficha de la tienda es la del repo en %d idioma(s): %s"
        % (len(changed), " ".join(changed)))
    return {"listing_changed": changed}


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
        "verify": phase_verify,
        "upload": phase_upload,
        "listing": phase_listing,
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
    if state.get("uploaded_to"):
        say("   OJO: esta en BORRADOR en el canal %s. Publicar es del user, en el Console."
            % state["uploaded_to"])
    elif state.get("listing_changed"):
        say("   OJO: el texto de la ficha YA esta publicado en Play (%s)."
            % " ".join(state["listing_changed"]))
    else:
        say("   OJO: subir a Play y publicar es del user. Este script no sube nada.")
    if "tag" not in wanted and "bump" in wanted:
        say("   tras commitear el bump:  python tools/release/cut.py %s --phases tag"
            % state.get("version", "<version>"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
