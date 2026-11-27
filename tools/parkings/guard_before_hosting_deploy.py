#!/usr/bin/env python
"""Impide un `firebase deploy --only hosting` que BORRE el snapshot de parkings publicado.

[PARKINGS-LIVE-OCCUPANCY-001] · nace de un incidente real, no de una precaucion.

## Lo que paso el 03-11-2026

Un `firebase deploy --only hosting` desde un worktree que no tenia las celdas generadas
**borro las 12.566 publicadas**: Hosting ESPEJA el directorio, asi que lo que no esta en local
desaparece de produccion. Resultado: `paparcar.com/parkings/v1/latest.json` en 404 y la funcion
`mergeCell` devolviendo 503 a cada celda.

Lo que lo tapo fue el diseno aditivo: la app cae a Overpass sola, asi que ningun usuario vio un mapa
vacio. Pero el snapshot estuvo muerto sin que nada lo dijera, y **las celdas estan en `.gitignore`**
—son salida de build— asi que *cualquier* worktree limpio esta a un comando de repetirlo.

⛔ Estaba escrito en dos docs y en una memoria, y se repitio igual. Una trampa que se evita
recordandola no esta evitada: este guard la convierte en un fallo ruidoso ANTES de subir nada.

## Que comprueba

Si hay puntero local, valida celdas, manifiesto, bulk y cobertura ES/Canarias/FR con el mismo
validador que CI. Un puntero aislado no acredita la base. Si solo produccion tiene puntero,
aborta para impedir el borrado. [PARKINGS-FRANCE-SNAPSHOT-001]

Se engancha como `predeploy` de hosting en `firebase.json`. Para saltarlo a proposito
(p. ej. retirar el snapshot de verdad): `PAPARCAR_ALLOW_SNAPSHOT_DELETE=1`.
"""

from __future__ import annotations

import json
import os
import pathlib
import subprocess
import tempfile
import sys
import urllib.error
import urllib.request

POINTER_PATH = "parkings/v1/latest.json"
LIVE_POINTER = "https://paparcar.com/parkings/v1/latest.json"
PUBLIC_DIR = "hosting/public"
ESCAPE_HATCH = "PAPARCAR_ALLOW_SNAPSHOT_DELETE"
TIMEOUT_S = 15


def live_pointer() -> dict | None:
    """Lo que produccion sirve ahora, o None si no sirve nada que se pueda leer.

    ⛔ Un fallo de red NO aborta el deploy: dejaria el despliegue de la web a merced de una
    desconexion, y este guard existe para evitar un borrado, no para ser un segundo punto de fallo.
    """
    try:
        with urllib.request.urlopen(LIVE_POINTER, timeout=TIMEOUT_S) as response:
            if response.status != 200:
                return None
            return json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, json.JSONDecodeError, TimeoutError, OSError):
        return None


def main() -> int:
    root = pathlib.Path(__file__).resolve().parents[2]
    local = root / PUBLIC_DIR / POINTER_PATH

    if local.is_file():
        # A pointer alone does not prove that the countries or even the cells exist.
        # Compare with the base live NOW, including manual deploys outside the weekly workflow.
        validator = str(root / "tools/parkings/validate-snapshot.mjs")
        with tempfile.TemporaryDirectory(prefix="paparcar-snapshot-") as scratch:
            baseline = str(pathlib.Path(scratch) / "previous.ndjson.gz")
            download = subprocess.run(
                ["node", validator, "--download-baseline", baseline], check=False,
            )
            if download.returncode:
                return download.returncode
            return subprocess.run(
                ["node", validator, str(root / PUBLIC_DIR), "--require-es-fr",
                 f"--baseline={baseline}"],
                check=False,
            ).returncode

    published = live_pointer()
    if published is None:
        # Produccion no tiene snapshot (o no se pudo leer): no hay nada que borrar.
        return 0

    if os.environ.get(ESCAPE_HATCH) == "1":
        print(f"AVISO: {ESCAPE_HATCH}=1 -> se permite retirar el snapshot publicado")
        return 0

    version = published.get("version", "?")
    cells = published.get("cells", "?")
    print(
        "\n".join(
            [
                "",
                "DEPLOY ABORTADO: este deploy borraria el snapshot de parkings publicado.",
                "",
                f"  produccion sirve : {version}  ({cells} celdas)",
                f"  en local NO hay  : {PUBLIC_DIR}/{POINTER_PATH}",
                "",
                "Hosting ESPEJA el directorio: lo que no este en local desaparece de produccion, y",
                "las celdas estan en .gitignore porque son salida de build. Paso el 03-11-2026 y dejo",
                "`mergeCell` devolviendo 503 a cada celda.",
                "",
                "Regenerar antes de desplegar (ver tools/parkings/README.md):",
                "  python tools/parkings/osm_extract.py <extracto.osm.pbf> /tmp/raw.json",
                '  ./gradlew :shared:parkingSnapshotTool --args="--snapshot hosting/public <ver> /tmp/raw.json"',
                "",
                f"Si de verdad quieres RETIRARLO: {ESCAPE_HATCH}=1 firebase deploy --only hosting",
                "",
            ],
        ),
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
