#!/usr/bin/env python
"""Saca de un .osm.pbf los `amenity=parking` con la FORMA que ya sabe leer la app.

[OSM-PRECOOKED-SNAPSHOT-001]

Este script hace **geometria y plumbing, y nada mas**. Lo que decide que es un parking para
Paparcar —el bordillo (`parking=street_side|lane`), los ocho valores de `access` cerrado, la
excepcion medida de `access=customers`, los dos unicos valores de `fee` que afirman algo, el
`maxheight` que deja null lo imperial— vive en `OsmPublicParkingParser` y se EJECUTA sobre la
salida de aqui (`./gradlew :shared:parkingSnapshotTool`). ⛔ No se replica aqui ni una linea de
eso: una segunda implementacion de la doctrina deriva en silencio.

Por eso la salida lleva **todos los tags**, no una lista blanca: elegir que tags importan ya seria
doctrina, y el dia que el parser mire un tag nuevo el pipeline lo perderia sin avisar.

⛔ El punto de cada elemento es el **centro de su bounding box**, no su centroide, porque es lo que
devuelve `out center` de Overpass — y la app lleva en produccion leyendo eso. Un centroide movería
los pines respecto de lo publicado.

⛔ Un extracto por llamada, a proposito: un territorio NO cabe en un fichero de Geofabrik (las
Canarias viven en `africa/`, no en `europe/spain`) y re-extraer una region que no ha cambiado es
tirar minutos. El que junta las partes es el tool de Kotlin, que acepta varios JSON y los
**deduplica por id** — los recortes de Geofabrik se solapan en las fronteras.

Uso:
    python tools/parkings/osm_extract.py <extracto.osm.pbf> <salida.json>
"""

from __future__ import annotations

import json
import sys
import time

import osmium

#: La clave OSM de la cosa que buscamos. Es el unico selector que este script conoce, y es el
#: mismo con el que `OsmPublicParkingParser.buildQuery` pregunta a Overpass.
AMENITY_KEY = "amenity"
AMENITY_VALUE = "parking"

# France is ~4.8 GB compressed. Keep the node-location index disk-backed so a CI runner does
# not need RAM proportional to every OSM node. libosmium owns and removes the temporary file.
# Geometry and filtering are unchanged. [PARKINGS-FRANCE-SNAPSHOT-001]
LOCATION_STORAGE = "sparse_file_array"

#: Los tipos de miembro de los que SI sabemos sacar geometria. Una relacion dentro de otra no se
#: resuelve: se cuenta, para que la limitacion sea un numero y no una suposicion.
RELATION_MEMBER_TYPES = ("n", "w")


def member_refs(members) -> list[tuple[str, int]]:
    """Los miembros de los que sabemos sacar geometria, como `(tipo, id)`.

    ⛔ Publica y aparte para que tenga test: el bug que costo un parking municipal del centro de
    Madrid era exactamente esta linea filtrando solo `w`. Ver `test_osm_extract.py`.
    """
    return [(m.type, m.ref) for m in members if m.type in RELATION_MEMBER_TYPES]


def _bbox_centre(locations: list[tuple[float, float]]) -> tuple[float, float] | None:
    """El centro de la caja envolvente, que es lo que `out center` de Overpass devuelve."""
    if not locations:
        return None
    lats = [lat for lat, _ in locations]
    lons = [lon for _, lon in locations]
    return (min(lats) + max(lats)) / 2.0, (min(lons) + max(lons)) / 2.0


def _way_locations(way: osmium.osm.Way) -> list[tuple[float, float]]:
    out: list[tuple[float, float]] = []
    for node in way.nodes:
        try:
            out.append((node.location.lat, node.location.lon))
        except osmium.InvalidLocationError:
            # Un nodo fuera del extracto: el borde del recorte. La caja sigue siendo la de los
            # nodos que SI estan, y un parking a medio cortar es mejor que ninguno.
            continue
    return out


def extract(pbf_path: str) -> list[dict]:
    """Los elementos `amenity=parking` del fichero, con la forma de una respuesta de Overpass."""
    tag_filter = osmium.filter.TagFilter((AMENITY_KEY, AMENITY_VALUE))

    # ── Pasada 1 · nodes y ways, que son el 99,5 % ───────────────────────────
    elements: list[dict] = []
    # Las relations necesitan la geometria de sus ways miembros, que esta en OTRA pasada: aqui solo
    # se apunta quien es miembro de quien.
    relation_members: dict[int, list[tuple[str, int]]] = {}
    relation_tags: dict[int, dict[str, str]] = {}
    relation_nested: dict[int, int] = {}
    # ⛔ Lo que se tira se CUENTA. Tirarlo en silencio es como «Aparcamiento Plaza de Espana»
    # desaparecio del snapshot sin que nada lo dijera: un `continue` mudo convierte un bug en un
    # dato que falta. [MEDIDO 02-11-2026]
    dropped: dict[str, int] = {}

    processor = osmium.FileProcessor(pbf_path).with_locations(LOCATION_STORAGE).with_filter(tag_filter)
    for obj in processor:
        kind = obj.type_str()
        tags = {tag.k: tag.v for tag in obj.tags}
        if kind == "n":
            elements.append(
                {"type": "node", "id": obj.id, "lat": obj.location.lat, "lon": obj.location.lon,
                 "tags": tags},
            )
        elif kind == "w":
            centre = _bbox_centre(_way_locations(obj))
            if centre is None:
                dropped["way"] = dropped.get("way", 0) + 1
                continue
            elements.append(
                {"type": "way", "id": obj.id, "center": {"lat": centre[0], "lon": centre[1]},
                 "tags": tags},
            )
        elif kind == "r":
            relation_tags[obj.id] = tags
            # ⛔ NODOS TAMBIEN, y no es un detalle: las relaciones `type=site` de parking suelen
            # tener sus accesos como NODOS, y algunas no tienen ni un way. Recogiendo solo ways,
            # «Aparcamiento Plaza de Espana» (EMT Madrid, 5 nodos y CERO ways) se quedaba sin
            # geometria y DESAPARECIA del snapshot, y «Plaza Mayor» (5 nodos + 2 ways) salia 15 m
            # desplazado. Lo cazo la comparacion contra Overpass vivo: 193 de 194, y las dos
            # discrepancias eran estas. `out center` de Overpass es la caja de TODOS los miembros.
            # [MEDIDO 02-11-2026]
            relation_members[obj.id] = member_refs(obj.members)
            nested = sum(1 for m in obj.members if m.type == "r")
            if nested:
                # Una relacion dentro de otra: no se resuelve, se CUENTA. Asi la limitacion es un
                # numero en la salida y no una suposicion.
                relation_nested[obj.id] = nested

    del processor  # Release the first location index before allocating the second.

    # ── Pasada 2 · solo si hay relations ─────────────────────────────────────
    if relation_members:
        wanted_ways = {ref for members in relation_members.values() for kind, ref in members if kind == "w"}
        wanted_nodes = {ref for members in relation_members.values() for kind, ref in members if kind == "n"}
        boxes: dict[tuple[str, int], list[tuple[float, float]]] = {}
        # ⛔ NODE siempre: ademas de ser miembros por derecho propio, sin leer los nodos pyosmium no
        # puede cachear localizaciones y `with_locations()` lanza. [MEDIDO al abrirlo]
        member_pass = osmium.FileProcessor(pbf_path, osmium.osm.NODE | osmium.osm.WAY)
        # Location caching runs BEFORE these native filters. Way geometry still sees all nodes,
        # but Python only receives the relation members we actually need, not the whole country.
        member_pass.with_locations(LOCATION_STORAGE)
        member_pass.with_filter(osmium.filter.IdFilter(wanted_nodes).enable_for(osmium.osm.NODE))
        member_pass.with_filter(osmium.filter.IdFilter(wanted_ways).enable_for(osmium.osm.WAY))
        for obj in member_pass:
            kind = obj.type_str()
            if kind == "w" and obj.id in wanted_ways:
                boxes[("w", obj.id)] = _way_locations(obj)
            elif kind == "n" and obj.id in wanted_nodes:
                boxes[("n", obj.id)] = [(obj.location.lat, obj.location.lon)]
        for rel_id, members in relation_members.items():
            locations = [loc for member in members for loc in boxes.get(member, ())]
            centre = _bbox_centre(locations)
            if centre is None:
                dropped["relation"] = dropped.get("relation", 0) + 1
                continue
            elements.append(
                {"type": "relation", "id": rel_id,
                 "center": {"lat": centre[0], "lon": centre[1]}, "tags": relation_tags[rel_id]},
            )

    if dropped:
        print(f"AVISO: sin geometria recuperable, descartados {dropped}")
    if relation_nested:
        print(
            f"AVISO: {len(relation_nested)} relaciones con miembros RELATION, sin resolver "
            f"(ids: {sorted(relation_nested)[:5]})",
        )
    return elements


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__)
        return 2
    pbf_path, out_path = argv[1], argv[2]

    started = time.time()
    elements = extract(pbf_path)
    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump({"elements": elements}, handle, ensure_ascii=False)

    by_kind: dict[str, int] = {}
    for element in elements:
        by_kind[element["type"]] = by_kind.get(element["type"], 0) + 1
    print(
        f"{len(elements)} elementos {AMENITY_KEY}={AMENITY_VALUE} "
        f"({', '.join(f'{k}={v}' for k, v in sorted(by_kind.items()))}) "
        # ASCII en stdout a proposito: la consola de Windows es cp1252 y una flecha Unicode
        # mata el script DESPUES de haber escrito el fichero. [MEDIDO al abrirlo]
        f"-> {out_path} en {time.time() - started:.1f}s",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
