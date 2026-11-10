# -*- coding: utf-8 -*-
"""Cliente minimo de la Google Play Developer API. Sin dependencias.

⛔ **Sin `google-auth` ni `gcloud`, a proposito.** No estan en esta maquina, y Gradle Play Publisher
traeria un plugin con su propia config y su propia forma de leer la credencial. Aqui el JWT se firma
con `openssl dgst -sha256 -sign` (openssl SI esta) y las llamadas van con `urllib`. La logica del
release sigue viviendo en un sitio que se puede ejecutar hoy, en local, sin CI.

⛔ **La credencial no esta en el repo y no puede estarlo**: `google-application-credentials-play.json`
en la raiz, tapada por el patron `**/google-application-credentials*.json` del `.gitignore`. Es la
cuenta de servicio `paparcar-publisher@pap-26.iam.gserviceaccount.com`, autorizada en Play con
permiso de *ver info de la app* y *versiones en canales de prueba* sobre Paparcar, y nada mas.

## Que es una "edicion" (edit), que es lo unico que hay que entender de esta API

Una **transaccion de scratch** sobre la ficha y los canales. Se abre (`POST /edits`), se le cuelgan
cambios, y:
  · `:commit` los aplica de golpe -- es la UNICA llamada que cambia algo de verdad;
  · `DELETE` la descarta y no queda rastro;
  · si no se toca, caduca sola.
Por eso la fase `verify` puede abrir una edicion para LEER sin riesgo: lee y la descarta.

[RELEASE-THE-CUT-REACHES-THE-STORE-001]
"""
import base64
import json
import pathlib
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

PACKAGE = "com.rndeveloper.paparcar"
CREDENTIAL = "google-application-credentials-play.json"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s" % PACKAGE
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/%s" % PACKAGE

# El canal al que sube el corte. ⛔ `internal` y en BORRADOR: publicar es del user, en el Console.
UPLOAD_TRACK = "internal"
DRAFT = "draft"


class PlayError(Exception):
    """Una respuesta de Play que no se puede interpretar como exito."""


def _b64u(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=")


def _sign(private_key_pem, payload):
    """Firma RS256 con openssl. La clave va a un fichero temporal que se borra siempre."""
    folder = pathlib.Path(tempfile.mkdtemp())
    pem = folder / "key.pem"
    pem.write_text(private_key_pem, encoding="utf-8")
    try:
        done = subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(pem)],
                              input=payload, capture_output=True)
        if done.returncode != 0:
            raise PlayError("openssl no pudo firmar el JWT: %s"
                            % done.stderr.decode("utf-8", "replace").strip())
        return done.stdout
    finally:
        pem.unlink(missing_ok=True)
        folder.rmdir()


class Play:
    """Lo justo para leer el estado de la tienda y subir un bundle a un canal de prueba."""

    def __init__(self, credential_path):
        path = pathlib.Path(credential_path)
        if not path.exists():
            raise PlayError(
                "no encuentro la credencial en %s.\n"
                "     Es la clave JSON de la cuenta de servicio de Play. No esta en el repo a "
                "proposito;\n     se descarga de Google Cloud y se deja en la raiz con ese nombre."
                % path)
        self.cred = json.loads(path.read_text(encoding="utf-8"))
        for field in ("client_email", "private_key", "token_uri"):
            if not self.cred.get(field):
                raise PlayError("la credencial %s no trae `%s`" % (path.name, field))
        self._token = None

    @property
    def account(self):
        return self.cred["client_email"]

    def token(self):
        if self._token:
            return self._token
        now = int(time.time())
        header = _b64u(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
        claims = _b64u(json.dumps({
            "iss": self.cred["client_email"],
            "scope": SCOPE,
            "aud": self.cred["token_uri"],
            "iat": now,
            "exp": now + 3600,
        }).encode())
        signing_input = header + b"." + claims
        assertion = signing_input + b"." + _b64u(_sign(self.cred["private_key"], signing_input))
        body = urllib.parse.urlencode({
            "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion": assertion.decode(),
        }).encode()
        status, payload = self.request("POST", self.cred["token_uri"], body=body,
                                       content_type="application/x-www-form-urlencoded", auth=False)
        if status != 200 or "access_token" not in payload:
            raise PlayError("Google no dio token (%s): %s" % (status, _message(payload)))
        self._token = payload["access_token"]
        return self._token

    def request(self, method, url, body=None, content_type=None, auth=True):
        """Devuelve (status, dict). No lanza por un status de error: eso lo decide quien llama."""
        headers = {}
        if auth:
            headers["Authorization"] = "Bearer " + self.token()
        if content_type:
            headers["Content-Type"] = content_type
        req = urllib.request.Request(url, data=body, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req) as answer:
                raw = answer.read().decode("utf-8")
                return answer.status, (json.loads(raw) if raw.strip() else {})
        except urllib.error.HTTPError as bad:
            raw = bad.read().decode("utf-8", "replace")
            try:
                return bad.code, json.loads(raw)
            except ValueError:
                return bad.code, {"raw": raw[:500]}

    # -------------------------------------------------------------- ediciones

    def open_edit(self):
        status, payload = self.request("POST", BASE + "/edits")
        if status != 200:
            raise PlayError(_diagnose(status, payload))
        return payload["id"]

    def discard_edit(self, edit_id):
        self.request("DELETE", "%s/edits/%s" % (BASE, edit_id))

    def commit_edit(self, edit_id):
        """Aplica la edicion. ⛔ NUNCA cancela una revision en curso.

        Sin `changesInReviewBehavior`, Play hace `CANCEL_IN_REVIEW_AND_SUBMIT`: retira lo que este
        en revision y lo reenvia junto con esta edicion. Subir una ficha con la 1.6.0 en revision de
        produccion le habria reiniciado la revision sin que nadie lo pidiera. Con
        `ERROR_IF_IN_REVIEW` Play rechaza el commit y no cambia nada: se reintenta cuando la revision
        acabe. [PLAY-THE-LISTING-MOVES-TO-1-6-0-001]
        """
        url = "%s/edits/%s:commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW" % (BASE, edit_id)
        status, payload = self.request("POST", url)
        if status != 200:
            raise PlayError("el commit de la edicion fallo (%s): %s" % (status, _message(payload)))
        return payload

    def validate_edit(self, edit_id):
        """Le pide a Play que valide la edicion sin aplicarla."""
        status, payload = self.request("POST", "%s/edits/%s:validate" % (BASE, edit_id))
        if status != 200:
            raise PlayError("Play no valida la edicion (%s): %s" % (status, _message(payload)))
        return payload

    def put_listing(self, edit_id, listing):
        """Sustituye la ficha de UN idioma (titulo y descripciones) dentro de la edicion.

        No toca capturas ni graficos: esos viven en `/listings/<lang>/<imageType>`, otra ruta.
        """
        url = "%s/edits/%s/listings/%s" % (BASE, edit_id, urllib.parse.quote(listing["language"]))
        status, payload = self.request("PUT", url, body=json.dumps(listing).encode("utf-8"),
                                       content_type="application/json; charset=utf-8")
        if status != 200:
            raise PlayError("Play rechazo la ficha %s (%s): %s"
                            % (listing["language"], status, _message(payload)))
        return payload

    def tracks(self, edit_id):
        status, payload = self.request("GET", "%s/edits/%s/tracks" % (BASE, edit_id))
        if status != 200:
            raise PlayError("no pude leer los canales (%s): %s" % (status, _message(payload)))
        return payload.get("tracks", [])

    def listings(self, edit_id):
        status, payload = self.request("GET", "%s/edits/%s/listings" % (BASE, edit_id))
        if status != 200:
            raise PlayError("no pude leer la ficha (%s): %s" % (status, _message(payload)))
        return payload.get("listings", [])

    def upload_bundle(self, edit_id, aab):
        """Sube el .aab. Devuelve el versionCode que Play dice haber recibido."""
        data = pathlib.Path(aab).read_bytes()
        url = "%s/edits/%s/bundles?uploadType=media" % (UPLOAD, edit_id)
        status, payload = self.request("POST", url, body=data,
                                       content_type="application/octet-stream")
        if status != 200:
            raise PlayError("Play rechazo el bundle (%s): %s" % (status, _message(payload)))
        return payload.get("versionCode")

    def set_track(self, edit_id, track, version_code, notes):
        """Deja el canal con esa version en BORRADOR y sus notas por locale."""
        body = json.dumps({
            "track": track,
            "releases": [{
                "status": DRAFT,
                "versionCodes": [str(version_code)],
                "releaseNotes": [{"language": loc, "text": text} for loc, text in notes],
            }],
        }).encode()
        status, payload = self.request("PUT", "%s/edits/%s/tracks/%s" % (BASE, edit_id, track),
                                       body=body, content_type="application/json")
        if status != 200:
            raise PlayError("no pude configurar el canal %s (%s): %s"
                            % (track, status, _message(payload)))
        return payload


def _message(payload):
    if isinstance(payload, dict):
        error = payload.get("error")
        if isinstance(error, dict):
            return error.get("message", json.dumps(error)[:300])
        if "raw" in payload:
            return payload["raw"]
    return json.dumps(payload)[:300]


def _diagnose(status, payload):
    """Un status de esta API no se interpreta solo: cada uno significa algo distinto aqui."""
    hints = {
        401: "la cuenta de servicio no esta autorizada en la cuenta de desarrollador, o el permiso "
             "aun no ha propagado (tarda un rato largo). Reintentar mas tarde.",
        403: "autenticada, pero SIN permiso de versiones sobre esta app. Revisar Usuarios y "
             "permisos en Play Console.",
        404: "Play no conoce %s para esta cuenta. Revisar el nombre del paquete." % PACKAGE,
    }
    extra = hints.get(status, "")
    return "no pude abrir una edicion (%s): %s%s" % (status, _message(payload),
                                                     "\n     " + extra if extra else "")
