# -*- coding: utf-8 -*-
"""Las verificaciones del corte, FALSIFICADAS una por una.

    python tools/release/test_cut.py

El camino verde de un script de release se prueba solo: lo corres y sale el AAB. Lo que no se
prueba solo es que cada comprobacion **falle** cuando le quitas lo que comprueba, y eso es lo unico
que convierte el script en un guard. Un chequeo que nunca se vio en rojo no se sabe si mira.

⛔ Esto NO vive dentro de `./gradlew testAll`, por la misma razon que
`tools/backlog/branch_tell.py`: es Python puro sobre ficheros y git, y meterlo en la suite de Gradle
obligaria a un wrapper de tarea que no aporta nada. Se corre a mano, y el corte lo invoca el user.

[RELEASE-THE-CUT-MUST-BE-ONE-COMMAND-001]
"""
import hashlib
import pathlib
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import cut  # noqa: E402

GRADLE_SAMPLE = """android {
    defaultConfig {
        applicationId = "com.rndeveloper.paparcar"
        versionCode = 16
        versionName = "1.5.0"
    }
    buildTypes {
        debug {
            versionNameSuffix = "-mock"
        }
    }
}
"""

WEB_SAMPLE = """export const MIN_ANDROID = 26;
export const APP_VERSION = '1.5.0';
export const SUPPORT_EMAIL = 'hola@paparcar.com';
"""

# Salida real de `keytool -printcert -jarfile paparcar-1.5.0-vc16.aab`, recortada.
PRINTCERT = """Owner: CN=Paparcar
Certificate fingerprints:
\t SHA1: 45:40:9F:1F:A8:8A:B2:A3:BB:25:15:CB:FB:78:F6:FD:E0:E1:BF:97
\t SHA256: 66:8A:A1:4E:1D:98:0D:07:63:80:E9:D7:8F:AC:53:BC:0F:04:85:9F:11:36:ED:6E:5F:B5:55:7C:EE:68:BD:86
"""

# Lineas de un `testAll --rerun-tasks` real (26-10-2026): 93 actionable, 93 ejecutadas.
SUITE_OK = [
    "> Task :shared:preBuild UP-TO-DATE",
    "> Task :mockkit:copyNonXmlValueResourcesForCommonMain NO-SOURCE",
    "> Task :shared:compileKotlinIosSimulatorArm64",
    "> Task :app:testMockDebugUnitTest",
    "> Task :shared:testDebugUnitTest",
    "BUILD SUCCESSFUL in 1m 4s",
    "93 actionable tasks: 93 executed",
]


class Patched:
    """Apunta los ficheros y ayudantes del modulo a un directorio temporal."""

    def __init__(self, **attrs):
        self.attrs = attrs
        self.old = {}

    def __enter__(self):
        for name, value in self.attrs.items():
            self.old[name] = getattr(cut, name)
            setattr(cut, name, value)
        return self

    def __exit__(self, *exc):
        for name, value in self.old.items():
            setattr(cut, name, value)
        return False


def temp_tree(stack, gradle=GRADLE_SAMPLE, web=WEB_SAMPLE):
    root = pathlib.Path(stack.enter_context(tempfile.TemporaryDirectory()))
    (root / "app").mkdir()
    (root / "web").mkdir()
    gradle_file = root / "app" / "build.gradle.kts"
    web_file = root / "web" / "config.mjs"
    gradle_file.write_text(gradle, encoding="utf-8")
    web_file.write_text(web, encoding="utf-8")
    return root, gradle_file, web_file


def fake_aab(path, manifest_bytes, extra_entries=3):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("base/manifest/AndroidManifest.xml", manifest_bytes)
        for i in range(extra_entries):
            zf.writestr("base/dex/classes%d.dex" % i, b"x" * 2048)
    return path


class BumpPhase(unittest.TestCase):
    """fase bump -- el numero vive en dos sitios."""

    def setUp(self):
        import contextlib
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.root, self.gradle, self.web = temp_tree(self.stack)
        # `declared_ad_networks()` lee el `ROOT` REAL (sin parchear aqui), no el arbol temporal de
        # arriba -- asi que, con una red publicitaria de verdad en el catalogo (Meta, TikTok),
        # `check_listing_names_the_ad_network` exigiria que la ficha de CUALQUIER version del
        # fixture ("1.6.0") la nombrase. Esta clase prueba el resto de `phase_bump` (version,
        # duplicados, escritura en los dos sitios), no esa puerta -- que tiene su propia
        # verificacion, hecha a mano contra el repo real. [MKT-TIKTOK-APP-EVENTS-SDK-001]
        self.patch = self.stack.enter_context(Patched(
            GRADLE_FILE=self.gradle, WEB_CONFIG=self.web, git=lambda *a: (0, ""),
            declared_ad_networks=lambda: []))

    def test_reads_the_version_and_not_the_suffix(self):
        # El trampolin real: `versionNameSuffix = "-mock"` esta tres lineas mas abajo.
        self.assertEqual(cut.read_gradle_versions(), (16, "1.5.0"))
        self.assertEqual(cut.read_web_version(), "1.5.0")

    def test_writes_both_sites(self):
        out = cut.phase_bump("1.6.0")
        self.assertEqual((out["version"], out["code"], out["previous"]), ("1.6.0", 17, "1.5.0"))
        self.assertEqual(cut.read_gradle_versions(), (17, "1.6.0"))
        self.assertEqual(cut.read_web_version(), "1.6.0")
        self.assertIn('versionNameSuffix = "-mock"', self.gradle.read_text(encoding="utf-8"))

    def test_fails_when_the_two_sites_already_disagree(self):
        self.web.write_text(WEB_SAMPLE.replace("1.5.0", "1.4.0"), encoding="utf-8")
        with self.assertRaises(cut.Fail) as bad:
            cut.phase_bump("1.6.0")
        self.assertIn("ya discrepan", str(bad.exception))

    def test_fails_when_the_version_is_the_one_already_written(self):
        with self.assertRaises(cut.Fail):
            cut.phase_bump("1.5.0")

    def test_fails_when_the_version_is_already_tagged(self):
        with Patched(git=lambda *a: (0, "v1.6.0" if a[0] == "tag" else "")):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_bump("1.6.0")
        self.assertIn("ya esta etiquetada", str(bad.exception))
        # Y no deja el arbol a medias.
        self.assertEqual(cut.read_gradle_versions(), (16, "1.5.0"))

    def test_fails_when_a_commit_already_wrote_that_version(self):
        def git(*a):
            return (0, "abc1234 chore: bump" if a[0] == "log" else "")
        with Patched(git=git):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_bump("1.6.0")
        self.assertIn("ya se escribio en el historial", str(bad.exception))


class SuitePhase(unittest.TestCase):
    """fase suite -- que la suite MIRARA, no solo que saliera verde."""

    def test_green_run_passes(self):
        with Patched(gradle=lambda tasks: (0, SUITE_OK)):
            self.assertEqual(cut.phase_suite(), {"suite_tasks": 93})

    def test_fails_when_gradle_saved_work(self):
        lines = SUITE_OK[:-1] + ["93 actionable tasks: 80 executed, 13 up-to-date"]
        with Patched(gradle=lambda tasks: (0, lines)):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_suite()
        self.assertIn("se salto trabajo", str(bad.exception))

    def test_fails_when_gradle_never_said_what_it_ran(self):
        with Patched(gradle=lambda tasks: (0, ["BUILD SUCCESSFUL in 2s"])):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_suite()
        self.assertIn("no puedo afirmar que mirara", str(bad.exception))

    def test_fails_when_a_leaf_came_back_up_to_date(self):
        lines = [l for l in SUITE_OK if "testDebugUnitTest" not in l]
        lines.insert(0, "> Task :shared:testDebugUnitTest FROM-CACHE")
        with Patched(gradle=lambda tasks: (0, lines)):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_suite()
        self.assertIn("FROM-CACHE", str(bad.exception))

    def test_fails_when_testall_stopped_including_a_leaf(self):
        # El fallo medido de [TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001]: `:app` fuera.
        lines = [l for l in SUITE_OK if ":app:testMockDebugUnitTest" not in l]
        with Patched(gradle=lambda tasks: (0, lines)):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_suite()
        self.assertIn("no aparece en el log", str(bad.exception))

    def test_fails_when_the_build_failed(self):
        with Patched(gradle=lambda tasks: (1, ["BUILD FAILED"])):
            with self.assertRaises(cut.Fail):
                cut.phase_suite()


class SignPhase(unittest.TestCase):
    """fase sign -- el fallo del 03-09-2026: firmado con el certificado equivocado."""

    def setUp(self):
        import contextlib
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.root, self.gradle, self.web = temp_tree(self.stack)
        self.export = pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory()))
        (self.export / "paparcar-1.5.0-vc16.aab").write_bytes(b"not really a bundle")
        self.stack.enter_context(Patched(GRADLE_FILE=self.gradle, EXPORT_DIR=self.export))

    def test_parses_sha1_and_not_sha256(self):
        self.assertEqual(cut.parse_sha1(PRINTCERT), [cut.UPLOAD_KEY_SHA1])

    def test_passes_with_the_current_upload_key(self):
        with Patched(printcert_sha1=lambda aab: [cut.UPLOAD_KEY_SHA1]):
            self.assertEqual(cut.phase_sign()["sha1"], cut.UPLOAD_KEY_SHA1)

    def test_fails_when_signed_by_another_key(self):
        other = ":".join(["AB"] * 20)
        with Patched(printcert_sha1=lambda aab: [other]):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_sign()
        self.assertIn("OTRA clave", str(bad.exception))

    def test_fails_when_the_bundle_is_not_signed(self):
        with Patched(printcert_sha1=lambda aab: []):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_sign()
        self.assertIn("no esta firmado", str(bad.exception))

    def test_fails_when_there_is_no_bundle_to_check(self):
        (self.export / "paparcar-1.5.0-vc16.aab").unlink()
        with self.assertRaises(cut.Fail) as bad:
            cut.phase_sign()
        self.assertIn("corre antes la fase bundle", str(bad.exception))


class MeasurePhase(unittest.TestCase):
    """fase measure -- que el binario sea de ESTE arbol."""

    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())

    def test_measures_and_reads_the_manifest(self):
        aab = fake_aab(self.tmp / "a.aab", b"\n\x12\x05" + b"1.6.0" + b"\x1a\x03app")
        out = cut.measure_aab(aab, "1.6.0", "1.5.0")
        self.assertEqual(out["entries"], 4)
        self.assertEqual(out["bytes"], aab.stat().st_size)
        self.assertEqual(out["sha256"], hashlib.sha256(aab.read_bytes()).hexdigest())
        self.assertEqual(out["manifest_without"], "1.5.0")

    def test_fails_when_the_manifest_does_not_say_this_version(self):
        aab = fake_aab(self.tmp / "b.aab", b"\x12\x05" + b"1.5.0")
        with self.assertRaises(cut.Fail) as bad:
            cut.measure_aab(aab, "1.6.0", "1.5.0")
        self.assertIn("no es de este arbol", str(bad.exception))

    def test_fails_when_the_previous_version_is_still_in_there(self):
        aab = fake_aab(self.tmp / "c.aab", b"1.6.0 y tambien 1.5.0")
        with self.assertRaises(cut.Fail) as bad:
            cut.measure_aab(aab, "1.6.0", "1.5.0")
        self.assertIn("todavia dice 1.5.0", str(bad.exception))

    @unittest.skipUnless((cut.EXPORT_DIR / "paparcar-1.5.0-vc16.aab").exists(),
                         "el AAB de 1.5.0 no esta en disco")
    def test_matches_the_numbers_published_for_1_5_0(self):
        # Contra el AAB real: las cifras tienen que salir las MISMAS que RELEASE-1-5-0-001 publico.
        out = cut.measure_aab(cut.EXPORT_DIR / "paparcar-1.5.0-vc16.aab", "1.5.0", "1.4.0")
        self.assertEqual(out["bytes"], 23437417)
        self.assertEqual(out["entries"], 1562)
        self.assertTrue(out["sha256"].startswith("6a02f226"))


class PreviousVersion(unittest.TestCase):
    """De donde sale "la version anterior", que es contra quien se compara el bundle."""

    def git(self, head="1.5.0", tags=()):
        def fake(*args):
            if args[0] == "show":
                return 0, 'versionName = "%s"' % head
            if args[0] == "tag":
                return 0, "\n".join(tags)
            return 0, ""
        return Patched(git=fake)

    def test_prefers_what_is_committed_in_head(self):
        with self.git(head="1.5.0", tags=["v1.0.0"]):
            self.assertEqual(cut.previous_version("1.6.0"), ("1.5.0", "HEAD"))

    def test_falls_back_to_the_newest_older_tag(self):
        with self.git(head="1.6.0", tags=["v1.6.0", "v1.5.0", "v1.0.0"]):
            self.assertEqual(cut.previous_version("1.6.0"), ("1.5.0", "etiqueta v1.5.0"))

    def test_never_takes_a_tag_newer_than_the_version_being_cut(self):
        # Ordenar y coger "la primera que no sea esta" devolveria 1.7.0, que no es la anterior.
        with self.git(head="1.6.0", tags=["v1.7.0", "v1.6.0", "v1.0.0"]):
            self.assertEqual(cut.previous_version("1.6.0"), ("1.0.0", "etiqueta v1.0.0"))

    def test_says_so_instead_of_inventing_one(self):
        with self.git(head="1.6.0", tags=[]):
            previous, source = cut.previous_version("1.6.0")
        self.assertIsNone(previous)
        self.assertIn("sin etiqueta anterior", source)

    def test_ignores_tags_that_are_not_versions(self):
        with self.git(head="1.6.0", tags=["prerebase/feature/X", "v1.5.0"]):
            self.assertEqual(cut.previous_version("1.6.0")[0], "1.5.0")


class NotesPhase(unittest.TestCase):
    """fase notes -- nueve locales, cada uno bajo el tope de Play."""

    def full(self, **overrides):
        notes = {loc: "nota" for loc in cut.LOCALES}
        notes.update(overrides)
        return notes

    def test_nine_locales_under_the_limit_pass(self):
        self.assertEqual(cut.check_notes(self.full()), [])

    def test_fails_when_a_locale_is_missing(self):
        notes = self.full()
        del notes["pl-PL"]
        self.assertIn("pl-PL: falta", cut.check_notes(notes))

    def test_fails_when_a_locale_is_over_500(self):
        problems = cut.check_notes(self.full(**{"fr-FR": "x" * 501}))
        self.assertIn("fr-FR: 501 caracteres > 500", problems)

    def test_fails_when_a_locale_is_empty(self):
        self.assertIn("de-DE: vacio", cut.check_notes(self.full(**{"de-DE": ""})))

    def test_fails_on_romanian_written_as_play_does_not(self):
        notes = self.full()
        notes["ro-RO"] = notes.pop("ro")
        problems = cut.check_notes(notes)
        self.assertIn("ro: falta", problems)
        self.assertTrue(any("ro-RO" in p for p in problems))

    def test_500_is_the_edge_and_not_a_failure(self):
        self.assertEqual(cut.check_notes(self.full(**{"ro": "x" * 500})), [])

    @unittest.skipUnless((cut.PASTE / "release-notes-v1.5.0.txt").exists(),
                         "no estan las notas de 1.5.0")
    def test_parses_the_real_notes_including_romanian(self):
        # La regresion que importa: `<ro>` no lleva region, y una regex que exija `xx-XX` se lo
        # salta EN SILENCIO -- que es lo que le pasa hoy a iosApp/fastlane/tools/metadata_from_play.py.
        notes = cut.parse_notes(cut.PASTE / "release-notes-v1.5.0.txt")
        self.assertEqual(sorted(notes), sorted(cut.LOCALES))
        self.assertEqual(cut.check_notes(notes), [])


class DocPhase(unittest.TestCase):
    """fase doc -- las cifras del doc, medidas y no copiadas a mano."""

    def setUp(self):
        import contextlib
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.backlog = pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory()))
        self.stack.enter_context(Patched(BACKLOG=self.backlog))
        self.state = {"version": "1.6.0", "code": 17, "sha256": "deadbeef", "bytes": 23437417,
                      "entries": 1562, "overhead_pct": 1.3, "sha1": cut.UPLOAD_KEY_SHA1,
                      "manifest_without": "1.5.0", "notes_file": "release-notes-v1.6.0.txt",
                      "notes_top": "fr-FR 455/500", "suite_tasks": 93, "aab": "/tmp/x.aab"}

    def test_creates_the_doc_with_the_measured_numbers(self):
        cut.phase_doc(self.state)
        text = (self.backlog / "release-1-6-0-001.md").read_text(encoding="utf-8")
        self.assertIn("deadbeef", text)
        self.assertIn("23.437.417 B", text)
        self.assertIn("`1.5.0` **ausente**", text)
        self.assertIn(cut.DOC_OPEN, text)

    def test_replaces_only_the_marked_region_of_an_existing_doc(self):
        path = self.backlog / "release-1-6-0-001.md"
        path.write_text("# titulo\n\nprosa que el user escribio\n\n%s\nviejo\n%s\n\n## cola\n"
                        % (cut.DOC_OPEN, cut.DOC_CLOSE), encoding="utf-8")
        cut.phase_doc(self.state)
        text = path.read_text(encoding="utf-8")
        self.assertIn("prosa que el user escribio", text)
        self.assertIn("## cola", text)
        self.assertNotIn("viejo", text)
        self.assertIn("deadbeef", text)

    def test_fails_instead_of_guessing_where_to_write(self):
        path = self.backlog / "release-1-6-0-001.md"
        path.write_text("# un doc a mano, sin marcadores\n", encoding="utf-8")
        with self.assertRaises(cut.Fail) as bad:
            cut.phase_doc(self.state)
        self.assertIn("marcadores", str(bad.exception))
        self.assertEqual(path.read_text(encoding="utf-8"), "# un doc a mano, sin marcadores\n")


class TagPhase(unittest.TestCase):
    """fase tag -- la etiqueta contesta 'que hay en produccion'."""

    def setUp(self):
        import contextlib
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.root, self.gradle, self.web = temp_tree(
            self.stack, gradle=GRADLE_SAMPLE.replace("1.5.0", "1.6.0").replace("16", "17"))
        self.stack.enter_context(Patched(GRADLE_FILE=self.gradle))
        self.calls = []

    def git(self, head="1.6.0", dirty="", tag_exists=False, tag_rc=0):
        def fake(*args):
            self.calls.append(args)
            if args[0] == "show":
                return 0, GRADLE_SAMPLE.replace('versionName = "1.5.0"',
                                                'versionName = "%s"' % head)
            if args[0] == "status":
                return 0, dirty
            if args[0] == "rev-parse" and "--verify" in args:
                return (0 if tag_exists else 1), ""
            if args[0] == "tag":
                return tag_rc, "" if tag_rc == 0 else "fatal: no se pudo"
            return 0, "abc1234"
        return Patched(git=fake)

    def test_tags_the_commit_that_carries_the_bump(self):
        with self.git():
            self.assertEqual(cut.phase_tag("1.6.0")["tag"], "v1.6.0")
        self.assertIn(("tag", "-a", "v1.6.0", "-m", "Paparcar 1.6.0 (versionCode 17)"), self.calls)

    def test_fails_when_the_bump_is_not_committed_yet(self):
        with self.git(head="1.5.0"):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_tag("1.6.0")
        self.assertIn("commitea el bump antes de etiquetar", str(bad.exception))

    def test_fails_when_the_bump_is_half_committed(self):
        with self.git(dirty=" M app/build.gradle.kts"):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_tag("1.6.0")
        self.assertIn("a medio commitear", str(bad.exception))

    def test_fails_when_the_tag_already_exists(self):
        with self.git(tag_exists=True):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_tag("1.6.0")
        self.assertIn("ya existe", str(bad.exception))

    def test_fails_when_asked_for_a_version_the_tree_does_not_carry(self):
        with self.git():
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_tag("1.7.0")
        self.assertIn("el arbol dice 1.6.0", str(bad.exception))


class WebPhase(unittest.TestCase):
    """fase web -- avisa si el corte obliga a desplegar."""

    def setUp(self):
        import contextlib
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.hosting = pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory()))
        (self.hosting / "index.html").write_text("<p>hola</p>", encoding="utf-8")
        self.stack.enter_context(Patched(HOSTING=self.hosting))

    def node(self, writes=None, rc=0, check_rc=0):
        class Done:
            def __init__(self, code):
                self.returncode, self.stdout, self.stderr = code, "", ""

        def fake(cmd, **kwargs):
            if "--check" in cmd:
                return Done(check_rc)
            if writes:
                (self.hosting / "index.html").write_text(writes, encoding="utf-8")
            return Done(rc)

        class FakeSub:
            run = staticmethod(fake)
        return Patched(subprocess=FakeSub)

    def test_says_nothing_to_deploy_when_the_output_is_identical(self):
        with self.node():
            self.assertEqual(cut.phase_web(), {"web_changed": []})

    def test_reports_the_files_that_changed(self):
        with self.node(writes="<p>1.6.0</p>"):
            out = cut.phase_web()
        self.assertEqual(out["web_changed"], ["index.html"])

    def test_fails_when_the_build_fails(self):
        with self.node(rc=1):
            with self.assertRaises(cut.Fail):
                cut.phase_web()

    def test_fails_when_check_says_the_output_is_stale(self):
        with self.node(check_rc=1):
            with self.assertRaises(cut.Fail) as bad:
                cut.phase_web()
        self.assertIn("--check", str(bad.exception))


if __name__ == "__main__":
    unittest.main(verbosity=2)
