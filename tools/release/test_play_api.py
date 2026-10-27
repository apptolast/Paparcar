# -*- coding: utf-8 -*-
"""El cliente de Play y las dos fases que hablan con la tienda, falsificadas.

    python tools/release/test_play_api.py

Aqui NO se llama a Google: el transporte se sustituye. Lo que se prueba es lo que no se puede ver
mirando una ejecucion que sale bien -- que un 401 se explica en vez de reventar, que la peticion del
canal lleva BORRADOR y los nueve locales, que si algo falla a mitad la edicion se DESCARTA y no se
commitea nada, y que `verify` canta cuando la tienda contradice al repo.

[RELEASE-THE-CUT-REACHES-THE-STORE-001]
"""
import base64
import json
import pathlib
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import cut  # noqa: E402
import play_api  # noqa: E402
import play_locales  # noqa: E402

CRED = {
    "type": "service_account",
    "project_id": "pap-26",
    "client_email": "paparcar-publisher@pap-26.iam.gserviceaccount.com",
    "private_key": "-----BEGIN PRIVATE KEY-----\nno-es-una-clave\n-----END PRIVATE KEY-----\n",
    "token_uri": "https://oauth2.googleapis.com/token",
}


def credential(tmp, **overrides):
    data = dict(CRED)
    data.update(overrides)
    path = pathlib.Path(tmp) / play_api.CREDENTIAL
    path.write_text(json.dumps(data), encoding="utf-8")
    return path


class Fake(play_api.Play):
    """Un Play con el transporte sustituido: cada llamada devuelve lo que diga `answers`."""

    def __init__(self, path, answers):
        super().__init__(path)
        self.answers = answers
        self.calls = []
        self._token = "token-de-prueba"

    def request(self, method, url, body=None, content_type=None, auth=True):
        self.calls.append((method, url, body))
        for pattern, answer in self.answers:
            if pattern in url and (method == "ANY" or True):
                return answer
        return 200, {}


class Credential(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()

    def test_says_where_the_file_should_be_instead_of_a_traceback(self):
        with self.assertRaises(play_api.PlayError) as bad:
            play_api.Play(pathlib.Path(self.tmp) / "no-esta.json")
        self.assertIn("no encuentro la credencial", str(bad.exception))
        self.assertIn("no esta en el repo a proposito", str(bad.exception).lower())

    def test_rejects_a_credential_without_a_key(self):
        path = credential(self.tmp, private_key="")
        with self.assertRaises(play_api.PlayError) as bad:
            play_api.Play(path)
        self.assertIn("private_key", str(bad.exception))

    def test_exposes_which_account_it_is_about_to_use(self):
        self.assertEqual(play_api.Play(credential(self.tmp)).account, CRED["client_email"])


class Token(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.signed = []
        self.real_sign = play_api._sign
        play_api._sign = lambda pem, payload: self.signed.append(payload) or b"firma"
        self.addCleanup(lambda: setattr(play_api, "_sign", self.real_sign))

    def test_mints_a_jwt_with_the_right_claims(self):
        play = Fake(credential(self.tmp), [("token", (200, {"access_token": "abc"}))])
        play._token = None
        self.assertEqual(play.token(), "abc")
        # El cuerpo firmado es header.claims; las claims dicen quien pide que, y a quien.
        header, claims = self.signed[0].split(b".")
        pad = lambda s: s + b"=" * (-len(s) % 4)
        self.assertEqual(json.loads(base64.urlsafe_b64decode(pad(header)))["alg"], "RS256")
        body = json.loads(base64.urlsafe_b64decode(pad(claims)))
        self.assertEqual(body["iss"], CRED["client_email"])
        self.assertEqual(body["scope"], play_api.SCOPE)
        self.assertEqual(body["aud"], CRED["token_uri"])
        self.assertEqual(body["exp"] - body["iat"], 3600)

    def test_explains_a_refused_token_instead_of_keyerror(self):
        play = Fake(credential(self.tmp), [("token", (400, {"error": "invalid_grant"}))])
        play._token = None
        with self.assertRaises(play_api.PlayError) as bad:
            play.token()
        self.assertIn("no dio token", str(bad.exception))


class Edits(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()

    def play(self, answers):
        return Fake(credential(self.tmp), answers)

    def test_opens_an_edit(self):
        self.assertEqual(self.play([("/edits", (200, {"id": "42"}))]).open_edit(), "42")

    def test_a_401_blames_authorisation_not_the_code(self):
        play = self.play([("/edits", (401, {"error": {"message": "The current user has "
                                                                "insufficient permissions"}}))])
        with self.assertRaises(play_api.PlayError) as bad:
            play.open_edit()
        text = str(bad.exception)
        self.assertIn("no esta autorizada", text)
        self.assertIn("propagado", text)

    def test_a_403_blames_the_app_permission(self):
        play = self.play([("/edits", (403, {"error": {"message": "nope"}}))])
        with self.assertRaises(play_api.PlayError) as bad:
            play.open_edit()
        self.assertIn("SIN permiso de versiones", str(bad.exception))

    def test_a_404_blames_the_package_name(self):
        play = self.play([("/edits", (404, {"error": {"message": "not found"}}))])
        with self.assertRaises(play_api.PlayError) as bad:
            play.open_edit()
        self.assertIn(play_api.PACKAGE, str(bad.exception))

    def test_a_non_json_answer_does_not_crash_the_parser(self):
        play = self.play([("/edits", (502, {"raw": "<html>bad gateway</html>"}))])
        with self.assertRaises(play_api.PlayError) as bad:
            play.open_edit()
        self.assertIn("bad gateway", str(bad.exception))


class TrackRequest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()

    def test_asks_for_a_draft_with_every_locale(self):
        play = Fake(credential(self.tmp), [("/tracks/", (200, {}))])
        notes = [(loc, "nota de %s" % loc) for loc in play_locales.PLAY_LOCALES]
        play.set_track("42", play_api.UPLOAD_TRACK, 17, notes)
        method, url, body = play.calls[-1]
        self.assertEqual(method, "PUT")
        self.assertIn("/tracks/internal", url)
        sent = json.loads(body)
        release = sent["releases"][0]
        # ⛔ BORRADOR, no `completed`: subir no es publicar.
        self.assertEqual(release["status"], play_api.DRAFT)
        # Play quiere los versionCodes como CADENAS.
        self.assertEqual(release["versionCodes"], ["17"])
        self.assertEqual([n["language"] for n in release["releaseNotes"]],
                         play_locales.PLAY_LOCALES)

    def test_reports_what_play_said_when_it_refuses(self):
        play = Fake(credential(self.tmp),
                    [("/tracks/", (400, {"error": {"message": "Version code 16 has already been "
                                                              "used"}}))])
        with self.assertRaises(play_api.PlayError) as bad:
            play.set_track("42", "internal", 16, [])
        self.assertIn("already been used", str(bad.exception))


class UploadPhase(unittest.TestCase):
    """La fase entera, con un Play de mentira: lo que importa es qué NO hace al fallar."""

    class Spy:
        def __init__(self, fail_on=None, version_code=17):
            self.fail_on, self.version_code = fail_on, version_code
            self.did = []
            self.account = "spy@pap-26.iam.gserviceaccount.com"

        def _maybe_fail(self, step):
            self.did.append(step)
            if self.fail_on == step:
                raise play_api.PlayError("Play dijo no en %s" % step)

        def open_edit(self):
            self._maybe_fail("open")
            return "edit-1"

        def upload_bundle(self, edit, aab):
            self._maybe_fail("upload")
            return self.version_code

        def set_track(self, edit, track, code, notes):
            self._maybe_fail("track")

        def commit_edit(self, edit):
            self._maybe_fail("commit")

        def discard_edit(self, edit):
            self.did.append("discard")

    def setUp(self):
        import contextlib
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        root = pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory()))
        (root / "app").mkdir()
        gradle = root / "app" / "build.gradle.kts"
        gradle.write_text('android {\n    defaultConfig {\n        versionCode = 17\n'
                          '        versionName = "1.6.0"\n    }\n}\n', encoding="utf-8")
        paste = root / "paste"
        paste.mkdir()
        notes = "".join("<%s>\nnota\n</%s>\n" % (loc, loc) for loc in play_locales.PLAY_LOCALES)
        (paste / "release-notes-v1.6.0.txt").write_text(notes, encoding="utf-8")
        export = pathlib.Path(self.stack.enter_context(tempfile.TemporaryDirectory()))
        (export / "paparcar-1.6.0-vc17.aab").write_bytes(b"x" * 1024)
        self.spy = self.Spy()
        self.stack.enter_context(_Patch(cut, GRADLE_FILE=gradle, PASTE=paste, EXPORT_DIR=export,
                                        ROOT=root))
        self.stack.enter_context(_Patch(play_api, Play=lambda path: self.spy))

    def test_uploads_sets_the_track_and_commits(self):
        out = cut.phase_upload()
        self.assertEqual(self.spy.did, ["open", "upload", "track", "commit"])
        self.assertEqual(out["uploaded_to"], play_api.UPLOAD_TRACK)

    def test_discards_the_edit_when_the_bundle_is_refused(self):
        self.spy.fail_on = "upload"
        with self.assertRaises(cut.Fail) as bad:
            cut.phase_upload()
        self.assertIn("descartado", str(bad.exception))
        self.assertIn("discard", self.spy.did)
        self.assertNotIn("commit", self.spy.did)

    def test_discards_the_edit_when_the_track_is_refused(self):
        self.spy.fail_on = "track"
        with self.assertRaises(cut.Fail):
            cut.phase_upload()
        self.assertEqual(self.spy.did, ["open", "upload", "track", "discard"])

    def test_refuses_when_play_received_another_version_code(self):
        self.spy.version_code = 99
        with self.assertRaises(cut.Fail) as bad:
            cut.phase_upload()
        self.assertIn("99", str(bad.exception))
        self.assertNotIn("commit", self.spy.did)

    def test_does_not_even_open_an_edit_without_the_notes(self):
        (cut.PASTE / "release-notes-v1.6.0.txt").unlink()
        with self.assertRaises(cut.Fail) as bad:
            cut.phase_upload()
        self.assertIn("faltan las notas", str(bad.exception))
        self.assertEqual(self.spy.did, [])

    def test_does_not_upload_notes_that_are_not_ready(self):
        bad_notes = "".join("<%s>\n%s\n</%s>\n" % (loc, "x" * 501, loc)
                            for loc in play_locales.PLAY_LOCALES)
        (cut.PASTE / "release-notes-v1.6.0.txt").write_text(bad_notes, encoding="utf-8")
        with self.assertRaises(cut.Fail) as bad:
            cut.phase_upload()
        self.assertIn("no subo nada", str(bad.exception))
        self.assertEqual(self.spy.did, [])

    def test_does_not_upload_a_bundle_that_is_not_there(self):
        (cut.EXPORT_DIR / "paparcar-1.6.0-vc17.aab").unlink()
        with self.assertRaises(cut.Fail) as bad:
            cut.phase_upload()
        self.assertIn("corre antes la fase bundle", str(bad.exception))
        self.assertEqual(self.spy.did, [])


class VerifyChecks(unittest.TestCase):
    """`verify` -- el chivato que faltaba: la tienda contra el repo."""

    def setUp(self):
        self.paste = pathlib.Path(tempfile.mkdtemp())
        for loc in play_locales.PLAY_LOCALES:
            (self.paste / ("play-%s.txt" % loc)).write_text(
                "FICHA\n--- 1. NOMBRE ---\nPaparcar\n--- 2. BREVE ---\nDonde aparcaste\n"
                "--- 3. COMPLETA ---\nTexto largo\n", encoding="utf-8")
        self.listings = [{"language": loc, "title": "Paparcar",
                          "shortDescription": "Donde aparcaste"}
                         for loc in play_locales.PLAY_LOCALES]
        self.tracks = [{"track": "production", "releases": [{
            "status": "completed", "versionCodes": ["16"],
            "releaseNotes": [{"language": loc, "text": "n"} for loc in play_locales.PLAY_LOCALES],
        }]}]

    def check(self, version="1.5.0", code=16):
        return cut.check_against_store(version, code, self.tracks, self.listings, self.paste)

    def test_a_store_in_sync_raises_nothing(self):
        lines, problems = self.check()
        self.assertEqual(problems, [])
        self.assertTrue(any("production" in l for l in lines))

    def test_the_repo_being_ahead_is_normal(self):
        # Justo despues del bump: el repo va por 17 y la tienda sigue en 16. No es un problema.
        self.assertEqual(self.check(version="1.6.0", code=17)[1], [])

    def test_the_store_being_ahead_is_the_one_that_screams(self):
        # El caso real: produccion con 16 mientras el repo cree ir por 15.
        problems = self.check(version="1.4.0", code=15)[1]
        self.assertTrue(any("NO es el de ahi" in p for p in problems), problems)

    def test_catches_a_live_release_missing_a_locale(self):
        notes = self.tracks[0]["releases"][0]["releaseNotes"]
        self.tracks[0]["releases"][0]["releaseNotes"] = [n for n in notes if n["language"] != "ro"]
        self.assertTrue(any("notas en ro" in p for p in self.check()[1]))

    def test_catches_a_listing_that_drifted_from_the_repo(self):
        self.listings[0]["shortDescription"] = "otra cosa"
        problems = self.check()[1]
        self.assertTrue(any("no es la del repo" in p for p in problems), problems)

    def test_catches_a_locale_the_store_has_and_the_repo_does_not(self):
        self.listings.append({"language": "ja-JP", "title": "x", "shortDescription": "y"})
        self.assertTrue(any("ja-JP" in p for p in self.check()[1]))

    def test_catches_a_locale_missing_from_the_published_listing(self):
        self.listings = [l for l in self.listings if l["language"] != "pl-PL"]
        self.assertTrue(any("no tiene pl-PL" in p for p in self.check()[1]))

    def test_an_empty_draft_track_is_not_a_problem(self):
        self.tracks.append({"track": "beta", "releases": [{"status": "draft"}]})
        self.assertEqual(self.check()[1], [])


class _Patch:
    def __init__(self, module, **attrs):
        self.module, self.attrs, self.old = module, attrs, {}

    def __enter__(self):
        for name, value in self.attrs.items():
            self.old[name] = getattr(self.module, name)
            setattr(self.module, name, value)
        return self

    def __exit__(self, *exc):
        for name, value in self.old.items():
            setattr(self.module, name, value)
        return False


if __name__ == "__main__":
    unittest.main(verbosity=2)
