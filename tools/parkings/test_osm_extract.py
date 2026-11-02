# -*- coding: utf-8 -*-
"""Que una relacion de parking no pierda su geometria por el tipo de sus miembros.

    python tools/parkings/test_osm_extract.py

El 02-11-2026 la comparacion contra el Overpass vivo dio **193 de 194** en el centro de Madrid, y
las dos discrepancias eran relaciones:

- `relation/14451679` -- *Aparcamiento Plaza de Espana*, de la EMT de Madrid: `type=site` con
  **5 nodos y CERO ways**. El extractor recogia solo miembros `w`, asi que se quedaba sin un solo
  punto, caia en un `continue` mudo y **desaparecia del snapshot**. Un parking municipal en el
  centro de la capital, perdido sin que nada lo dijera.
- `relation/21382798` -- *Aparcamiento Plaza Mayor*: 5 nodos + 2 ways. Con solo los ways el centro
  salia **15 m desplazado**.

`out center` de Overpass es la caja de **todos** los miembros, y la app lleva en produccion leyendo
eso. Con los nodos dentro, la comparacion paso a **194 de 194**.

⛔ Estos tests cubren la SELECCION de miembros y el calculo de la caja, que es donde estaba el bug y
lo unico que se puede probar sin un `.osm.pbf` de fixture. Que el conjunto entero coincida con la
realidad lo comprueba otra cosa, y es de red: `./gradlew :shared:parkingSnapshotTool --args="--compare ..."`.

[OSM-PRECOOKED-SNAPSHOT-001]
"""
import pathlib
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import osm_extract  # noqa: E402


class FakeMember:
    """Un miembro de relacion con lo unico que el extractor le mira: su tipo y su id."""

    def __init__(self, kind: str, ref: int) -> None:
        self.type = kind
        self.ref = ref


class MemberSelectionTest(unittest.TestCase):

    def test_keeps_node_members_of_a_site_relation(self):
        """El caso de Plaza de Espana: cinco nodos, ni un way."""
        members = [FakeMember("n", 100 + i) for i in range(5)]
        self.assertEqual(
            [("n", 100), ("n", 101), ("n", 102), ("n", 103), ("n", 104)],
            osm_extract.member_refs(members),
            "una relacion de solo nodos tiene geometria, y perderla la borra del snapshot",
        )

    def test_keeps_both_kinds_and_their_order(self):
        """El caso de Plaza Mayor: nodos Y ways, y los dos cuentan para la caja."""
        members = [FakeMember("n", 1), FakeMember("w", 2), FakeMember("n", 3), FakeMember("w", 4)]
        self.assertEqual(
            [("n", 1), ("w", 2), ("n", 3), ("w", 4)],
            osm_extract.member_refs(members),
        )

    def test_drops_nested_relations(self):
        """Una relacion dentro de otra no se resuelve: se descarta aqui y se CUENTA en la salida."""
        members = [FakeMember("n", 1), FakeMember("r", 9), FakeMember("w", 2)]
        self.assertEqual([("n", 1), ("w", 2)], osm_extract.member_refs(members))


class BboxCentreTest(unittest.TestCase):

    def test_is_the_centre_of_the_box_not_the_average_of_the_points(self):
        """`out center` de Overpass es la caja envolvente, no el centroide.

        Con tres puntos amontonados al sur y uno al norte, el centroide se va al sur y el centro de
        la caja se queda en medio. La app lleva en produccion leyendo lo segundo.
        """
        points = [(40.0, -3.0), (40.0, -3.0), (40.0, -3.0), (41.0, -2.0)]
        self.assertEqual((40.5, -2.5), osm_extract._bbox_centre(points))

    def test_has_no_centre_when_there_is_no_geometry(self):
        """Sin un solo punto no hay centro, y el extractor lo cuenta en vez de inventarlo."""
        self.assertIsNone(osm_extract._bbox_centre([]))

    def test_a_single_node_is_its_own_centre(self):
        self.assertEqual((40.4168, -3.7038), osm_extract._bbox_centre([(40.4168, -3.7038)]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
