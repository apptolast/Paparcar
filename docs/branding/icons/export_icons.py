#!/usr/bin/env python3
"""
Exporta a SVG/PNG los iconos con los que la app nombra sus seis conceptos.

No hay un solo path escrito a mano aqui. Cada dibujo se DERIVA de su fuente:

  - las dos marcas propias, de `PaparcarIcons.kt` (sus constantes `SPOT_BAY_PATH` /
    `GLYPH_P_PATH` y, para la P, las mismas cuentas con que su grupo lleva la rejilla de
    48 al cuadro de 24);
  - los Material, del sources jar de `org.jetbrains.compose.material:material-icons-extended`
    en la version que resuelve el build -- el MISMO artefacto que compila la app, asi que lo
    exportado es lo que se renderiza, no un parecido bajado de un catalogo web.

Uso:  python docs/branding/icons/export_icons.py
Salida: docs/branding/icons/{svg,png}/
"""
from __future__ import annotations

import html
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

OUT = Path(__file__).resolve().parent
REPO = OUT.parents[2]
ICONS_KT = REPO / "shared/src/commonMain/kotlin/com/rndeveloper/paparcar/ui/icons/PaparcarIcons.kt"
CATALOG = REPO / "gradle/libs.versions.toml"
GRADLE_CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"
CHROME = Path("C:/Program Files/Google/Chrome/Application/chrome.exe")

# Las tintas son las que estas superficies llevan de verdad en la app, no una paleta de muestra:
# el glifo del puck de plaza va en blanco sobre su disco de frescura, el de parking en el verde de
# relleno de tema claro, y un Nivel 1/2 suelto hereda `onSurfaceVariant`.
TINTS = {
    "ink": "#0D1117",        # PapInk -- sobre papel
    "white": "#FFFFFF",      # sobre disco de color (puck de mapa)
    "green": "#009F5E",      # PapGreenLight -- el verde de relleno en claro
    "muted": "#8EA0B4",      # PapOnDarkMuted -- onSurfaceVariant en oscuro
}
PNG_SIZE = 512

# El repo es LF. En Windows `write_text` traduce a CRLF por defecto, y eso basta para que el guard
# de paridad no reconozca el fichero que el propio exportador acaba de escribir.
LF = "\n"

# -- Kotlin ImageVector DSL -> SVG path ---------------------------------------
CMD = {
    "moveTo": "M", "moveToRelative": "m",
    "lineTo": "L", "lineToRelative": "l",
    "horizontalLineTo": "H", "horizontalLineToRelative": "h",
    "verticalLineTo": "V", "verticalLineToRelative": "v",
    "curveTo": "C", "curveToRelative": "c",
    "reflectiveCurveTo": "S", "reflectiveCurveToRelative": "s",
    "quadTo": "Q", "quadToRelative": "q",
    "reflectiveQuadTo": "T", "reflectiveQuadToRelative": "t",
    "arcTo": "A", "arcToRelative": "a",
    "close": "Z",
}


def _num(tok: str) -> str:
    tok = tok.strip().split("=")[-1].strip()
    if tok in ("true", "false"):
        return "1" if tok == "true" else "0"
    return f"{float(tok.rstrip('fF')):.10g}"


def _blocks(src: str, opener: re.Pattern) -> list[tuple[str, str]]:
    """Cada `materialPath { ... }`, con sus argumentos y su cuerpo balanceado."""
    out = []
    for m in opener.finditer(src):
        depth, i = 1, m.end()
        while depth and i < len(src):
            depth += {"{": 1, "}": -1}.get(src[i], 0)
            i += 1
        out.append((m.group(1) or "", src[m.end():i - 1]))
    return out


def kotlin_paths(src: str) -> list[dict]:
    """Traduce los `materialPath` de un fichero de Material Icons a paths SVG."""
    paths = []
    for args, body in _blocks(src, re.compile(r"materialPath\s*(\([^)]*\))?\s*\{")):
        d = []
        for name, raw in re.findall(r"\b([a-zA-Z]+)\s*\(([^)]*)\)", body):
            if name not in CMD:
                continue
            d.append(CMD[name] + " ".join(_num(t) for t in raw.split(",") if t.strip()))
        paths.append({"d": " ".join(d), "rule": "evenodd" if "EvenOdd" in args else None})
    if not paths:
        raise SystemExit("no materialPath blocks found")
    return paths


def material_source(simple_name: str, sources_jar: Path) -> str:
    member = f"commonMain/androidx/compose/material/icons/rounded/{simple_name}.kt"
    return subprocess.run(
        ["unzip", "-p", str(sources_jar), member],
        capture_output=True, check=True, text=True,
    ).stdout


def find_sources_jar() -> Path:
    found = re.search(r'^materialIconsExtended\s*=\s*"([^"]+)"',
                      CATALOG.read_text(encoding="utf-8"), re.M)
    if not found:
        raise SystemExit("materialIconsExtended missing from the version catalog")
    ver = found.group(1)
    root = GRADLE_CACHE / "org.jetbrains.compose.material/material-icons-extended" / ver
    jars = sorted(root.glob(f"*/material-icons-extended-{ver}-sources.jar"))
    if not jars:
        raise SystemExit(f"sources jar for material-icons-extended {ver} not in the Gradle cache")
    return jars[0]


# -- Las dos marcas propias, leidas de PaparcarIcons.kt -----------------------
def paparcar_marks() -> dict[str, list[dict]]:
    src = ICONS_KT.read_text(encoding="utf-8")

    def const_str(name: str) -> str:
        m = re.search(rf'{name}\s*(?::\s*String\s*)?=\s*((?:"[^"]*"\s*\+?\s*)+)', src)
        if not m:
            raise SystemExit(f"{name} not found in PaparcarIcons.kt")
        return "".join(re.findall(r'"([^"]*)"', m.group(1)))

    def const_num(name: str) -> float:
        m = re.search(rf"\b{name}\s*=\s*([0-9.]+)f", src)
        if not m:
            raise SystemExit(f"{name} not found in PaparcarIcons.kt")
        return float(m.group(1))

    # La P vive en una rejilla de 48 y su icono la encuadra con un grupo. Se rehacen aqui las
    # MISMAS cuentas del fichero (mancha de P_TARGET_HEIGHT centrada en 24), para que el SVG
    # herede su encaje en vez de inventarse otro.
    scale = const_num("P_TARGET_HEIGHT") / const_num("P_INK_HEIGHT")
    dx = 12 - const_num("P_INK_CENTER_X") * scale
    dy = 12 - const_num("P_INK_CENTER_Y") * scale
    return {
        "spot-community": [{"d": const_str("SPOT_BAY_PATH"), "rule": None}],
        "car-park": [{
            "d": const_str("GLYPH_P_PATH"),
            "rule": None,
            "transform": f"translate({dx:.6g} {dy:.6g}) scale({scale:.6g})",
        }],
    }


# -- Salida -------------------------------------------------------------------
def svg(paths: list[dict], title: str) -> str:
    body = []
    for p in paths:
        attrs = ' fill-rule="evenodd" clip-rule="evenodd"' if p.get("rule") else ""
        attrs += f' transform="{p["transform"]}"' if p.get("transform") else ""
        body.append(f'  <path{attrs} d="{p["d"]}"/>')
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"\n'
        f'     role="img" aria-label="{html.escape(title)}" fill="currentColor">\n'
        + "\n".join(body) + "\n</svg>\n"
    )


def rasterize(svg_text: str, dest: Path, size: int, color: str) -> None:
    """PNG por Chrome headless -- el mismo rasterizador con el que ya se hornea el banner."""
    page = (
        "<style>html,body{margin:0;background:transparent}"
        f"svg{{display:block;width:{size}px;height:{size}px;color:{color}}}</style>"
        + svg_text
    )
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "icon.html"
        src.write_text(page, encoding="utf-8")
        subprocess.run(
            [str(CHROME), "--headless", "--disable-gpu", "--hide-scrollbars",
             "--default-background-color=00000000", f"--window-size={size},{size}",
             f"--screenshot={Path(tmp) / 'out.png'}", src.as_uri()],
            capture_output=True, check=True, timeout=180,
        )
        shutil.copyfile(Path(tmp) / "out.png", dest)


# Los conceptos que la web NOMBRA, con la clave que ya usa su copy (`web/content/*.json`), para que
# cambiar el dibujo no obligue a tocar el texto. [ICON-A-CONCEPT-KEEPS-ONE-GLYPH-ON-EVERY-SURFACE-001]
WEB_GLYPHS = {
    "spot": "spot-community",
    "parking": "car-park",
    "fuel": "fuel-station",
    "sharedCar": "shared-car",
    "history": "history",
    "figures": "figures",
}


def write_web_module(drawings: dict[str, list[dict]], dest: Path) -> None:
    """Escribe `web/lib/glyphs.generated.mjs` -- la web consume, no redibuja.

    El carril es el mismo que ya usa el logo (`mark.generated.mjs`, de `export_surfaces.py`):
    el dibujo se genera desde la fuente de la app y la web lo importa. Antes de esto la web
    declaraba sus propios `spot` y `parking` a mano, y servia dos glifos que la app habia
    RETIRADO -- en dos idiomas, y sin que ningun grep lo viera.
    """
    lines = [
        "// GENERADO por docs/branding/icons/export_icons.py -- no editar a mano.",
        "//",
        "// Los glifos con los que la app nombra sus conceptos, derivados de `PaparcarIcons.kt` y",
        "// del sources jar de material-icons-extended que compila la app. Editar un path aqui no",
        "// cambia la app: la deja diciendo una cosa y a la web diciendo otra, que es justo el",
        "// agujero que este fichero vino a cerrar.",
        "//",
        "// Cada entrada es la LISTA de trazos del glifo (BarChart son tres barras sueltas) y cada",
        "// trazo puede traer su `transform`: la P del parking vive en una rejilla de 48 y es su",
        "// icono quien la encuadra en el cuadro de 24.",
        "export const CONCEPT_GLYPHS = {",
    ]
    for key, slug in WEB_GLYPHS.items():
        lines.append(f"  {key}: [")
        for p in drawings[slug]:
            bits = [f"d: '{p['d']}'"]
            if p.get("transform"):
                bits.append(f"transform: '{p['transform']}'")
            if p.get("rule"):
                bits.append(f"rule: '{p['rule']}'")
            lines.append("    { " + ", ".join(bits) + " },")
        lines.append("  ],")
    lines.append("};\n")
    dest.write_text("\n".join(lines), encoding="utf-8", newline=LF)


def contact_sheet(slugs: list[str], png_dir: Path, dest: Path, cell: int = 180) -> None:
    """Un pliego para mirar los ocho de una vez: una exportacion que no se ve no esta verificada."""
    from PIL import Image

    cols = 4
    rows = -(-len(slugs) // cols)
    sheet = Image.new("RGBA", (cell * cols, cell * rows), (255, 255, 255, 255))
    for i, slug in enumerate(slugs):
        glyph = Image.open(png_dir / f"{slug}-ink.png").convert("RGBA")
        tile = Image.new("RGBA", (cell, cell), (255, 255, 255, 255))
        tile.alpha_composite(glyph.resize((cell, cell), Image.LANCZOS))
        sheet.paste(tile, ((i % cols) * cell, (i // cols) * cell))
    sheet.save(dest)


# `(slug, concepto, token)` -- el token es el simbolo del codigo, no un nombre de fichero.
MATERIAL = [
    ("fuel-station", "Gasolineras", "LocalGasStation"),
    ("shared-car", "Coche compartido", "Group"),
    ("history", "Historial", "History"),
    ("figures", "Cifras (propuesta)", "BarChart"),
    ("figures-alt-insights", "Cifras - alternativa descartada", "Insights"),
    ("figures-alt-leaderboard", "Cifras - alternativa descartada", "Leaderboard"),
]
OWN = [
    ("spot-community", "Plazas de la comunidad"),
    ("car-park", "Parkings publicos"),
]


def main() -> int:
    jar = find_sources_jar()
    marks = paparcar_marks()
    svg_dir, png_dir = OUT / "svg", OUT / "png"
    svg_dir.mkdir(exist_ok=True)
    png_dir.mkdir(exist_ok=True)

    items = [(slug, title, marks[slug]) for slug, title in OWN]
    items += [(slug, title, kotlin_paths(material_source(token, jar)))
              for slug, title, token in MATERIAL]

    for slug, title, paths in items:
        text = svg(paths, title)
        (svg_dir / f"{slug}.svg").write_text(text, encoding="utf-8", newline=LF)
        for tint, color in TINTS.items():
            rasterize(text, png_dir / f"{slug}-{tint}.png", PNG_SIZE, color)
        print(f"  {slug:26s} {len(paths)} path(s)  ->  svg + {len(TINTS)} png")

    contact_sheet([slug for slug, _, _ in items], png_dir, OUT / "preview.png")

    web_module = REPO / "web/lib/glyphs.generated.mjs"
    write_web_module({slug: paths for slug, _, paths in items}, web_module)
    print(f"\n  {len(WEB_GLYPHS)} glifos -> {web_module.relative_to(REPO)}")

    print(f"\n{len(items)} iconos -> {svg_dir.relative_to(REPO)} + {png_dir.relative_to(REPO)}")
    print(f"Material desde: {jar.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
