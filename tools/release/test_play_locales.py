# -*- coding: utf-8 -*-
"""Que los TRES consumidores de los locales de Play lean lo mismo -- y que el rumano llegue.

    python tools/release/test_play_locales.py

El 30-09-2026 Play rechazo las notas de 1.4.0 por `ro-RO`, se arreglo, y **un cuarto consumidor
siguio roto 27 dias**: `metadata_from_play.py` leia 8 de 9 locales porque su regex exigia region, y
buscaba un `play-ro-RO.txt` que no existe. No lo vio nadie porque cada tool tenia su propia copia
de la lista Y de la regex. Estos tests son el testigo de que ahora hay un solo dueno.

⛔ Lo que estos tests NO cubren, a proposito: `PlayListingPasteParityGuardrailTest.kt` mantiene su
lista aparte porque es el testigo de `make_paste.py` -- si leyera `play_locales` compararia el
generador consigo mismo. Que esa copia siga coincidiendo lo comprueba `test_the_kotlin_witness_still_agrees`,
LEYENDO el fichero, que es distinto de compartir la lista.

[IOS-THE-STORE-METADATA-SKIPS-ROMANIAN-001]
"""
import importlib.util
import pathlib
import re
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))

import play_locales  # noqa: E402


def load(path, name):
    """Importa un tool por ruta: ninguno de los tres es un paquete."""
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class TheList(unittest.TestCase):
    def test_romanian_is_lowercase_and_has_no_region(self):
        # El detalle que costo un rechazo del Console. Si alguien lo "corrige" a ro-RO, esto cae.
        self.assertIn("ro", play_locales.PLAY_LOCALES)
        self.assertNotIn("ro-RO", play_locales.PLAY_LOCALES)
        self.assertEqual(len(play_locales.PLAY_LOCALES), 9)

    def test_the_pattern_can_match_a_locale_without_region(self):
        full = re.compile("^%s$" % play_locales.LOCALE_PATTERN)
        for loc in play_locales.PLAY_LOCALES:
            self.assertTrue(full.match(loc), "%s no casa con LOCALE_PATTERN" % loc)

    def test_the_old_broken_pattern_is_what_it_fixes(self):
        # La causa real de 2026-09-30: este patron NO puede ver el rumano.
        self.assertIsNone(re.compile(r"^[a-z]{2}-[A-Z]{2}$").match("ro"))

    def test_missing_and_unexpected_name_the_problem(self):
        self.assertEqual(play_locales.missing(["en-US"]), play_locales.PLAY_LOCALES[1:])
        self.assertEqual(play_locales.unexpected(["ro-RO", "ro"]), ["ro-RO"])


class TheNotesParser(unittest.TestCase):
    def test_reads_a_locale_without_region(self):
        text = "<en-US>\nhola\n</en-US>\n<ro>\nsalut\n</ro>\n"
        self.assertEqual(play_locales.tagged_blocks(text), {"en-US": "hola", "ro": "salut"})

    def test_does_not_pair_mismatched_tags(self):
        self.assertEqual(play_locales.tagged_blocks("<ro>\nsalut\n</pl-PL>\n"), {})

    @unittest.skipUnless((ROOT / "docs/release/play-listing/paste/release-notes-v1.5.0.txt").exists(),
                         "no estan las notas de 1.5.0")
    def test_the_real_notes_carry_all_nine(self):
        notes = play_locales.tagged_blocks(
            (ROOT / "docs/release/play-listing/paste/release-notes-v1.5.0.txt")
            .read_text(encoding="utf-8"))
        self.assertEqual(sorted(notes), sorted(play_locales.PLAY_LOCALES))


class TheAppStoreTable(unittest.TestCase):
    """metadata_from_play.py -- el consumidor que estaba roto."""

    def setUp(self):
        self.mod = load(ROOT / "iosApp/fastlane/tools/metadata_from_play.py", "metadata_from_play")

    def test_covers_every_play_locale_and_invents_none(self):
        self.assertEqual(self.mod.locale_coverage(), {"sin cubrir": [], "inventados": []})

    def test_romanian_maps_to_romanian(self):
        self.assertEqual(self.mod.LOCALES["ro"], "ro")

    def test_the_bug_would_be_caught_now(self):
        # Exactamente la tabla que vivio 27 dias: `ro-RO` como clave.
        broken = dict(self.mod.LOCALES)
        broken["ro-RO"] = broken.pop("ro")
        self.mod.LOCALES = broken
        self.assertEqual(self.mod.locale_coverage(),
                         {"sin cubrir": ["ro"], "inventados": ["ro-RO"]})

    def test_it_reads_romanian_notes_now(self):
        path = pathlib.Path(tempfile.mkdtemp()) / "release-notes-v9.9.9.txt"
        path.write_text("<ro>\nsalut\n</ro>\n", encoding="utf-8")
        self.assertEqual(self.mod.release_notes(path), {"ro": "salut"})

    def test_the_source_file_it_looks_for_exists(self):
        # El segundo fallo: buscaba play-ro-RO.txt. El fichero real es play-ro.txt.
        for play_locale in self.mod.LOCALES:
            name = "play-%s.txt" % play_locale.split("|")[0]
            self.assertTrue((ROOT / "docs/release/play-listing/paste" / name).exists(),
                            "falta %s" % name)


class TheGenerator(unittest.TestCase):
    """make_paste.py -- la ficha de Play."""

    def test_reads_the_shared_list(self):
        mod = load(ROOT / "docs/release/play-listing/tools/make_paste.py", "make_paste")
        self.assertEqual(mod.LOCALES, play_locales.PLAY_LOCALES)
        self.assertIs(mod.LOCALE_PATTERN, play_locales.LOCALE_PATTERN)


class TheCut(unittest.TestCase):
    """cut.py -- la fase de notas del corte."""

    def test_reads_the_shared_list(self):
        import cut
        self.assertIs(cut.LOCALES, play_locales.PLAY_LOCALES)
        self.assertEqual(cut.parse_notes.__module__, "cut")
        self.assertEqual(
            cut.check_notes({loc: "x" for loc in play_locales.PLAY_LOCALES}), [])

    def test_still_rejects_romanian_with_a_region(self):
        import cut
        notes = {loc: "x" for loc in play_locales.PLAY_LOCALES}
        notes["ro-RO"] = notes.pop("ro")
        problems = cut.check_notes(notes)
        self.assertIn("ro: falta", problems)
        self.assertTrue(any("ro-RO" in p for p in problems))


class TheWitness(unittest.TestCase):
    """El guard de Kotlin NO comparte la lista, y por eso hay que vigilar que coincida."""

    GUARD = ("shared/src/androidUnitTest/kotlin/com/rndeveloper/paparcar/architecture/"
             "PlayListingPasteParityGuardrailTest.kt")

    def test_the_kotlin_witness_still_agrees(self):
        text = (ROOT / self.GUARD).read_text(encoding="utf-8")
        found = re.search(r"val LOCALES = listOf\(([^)]*)\)", text)
        self.assertIsNotNone(found, "no encuentro la lista del guard")
        locales = re.findall(r'"([^"]+)"', found.group(1))
        self.assertEqual(locales, play_locales.PLAY_LOCALES,
                         "la copia del testigo se ha desviado de play_locales")


if __name__ == "__main__":
    unittest.main(verbosity=2)
