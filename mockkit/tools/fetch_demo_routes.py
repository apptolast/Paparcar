# -*- coding: utf-8 -*-
"""Genera `DemoStreets.kt`: las calles del mundo mock con trayectos REALES hasta ellas.

[MOCK-A-DEMO-ROUTE-MUST-FOLLOW-THE-STREETS-001]

Por que esto es un script y no codigo Kotlin: una ruta que va por calles no se puede
calcular en el dispositivo sin el grafo de carreteras. Se pide UNA vez a OSRM (el mismo
formato de polilinea precision-5 que usa `PolylineCodec`, asi que la cadena entra
literal, sin traducir) y se versiona el resultado junto a la fuente que lo pidio.

Uso:
    python mockkit/tools/fetch_demo_routes.py

Servicios (ambos publicos, sin clave): Nominatim para situar la calle, OSRM para el
trayecto. Se les habla despacio a proposito: son gratis y de la comunidad.
"""
import json
import math
import os
import time
import urllib.parse
import urllib.request

UA = "paparcar-mock-routes/1.0 (+https://paparcar.com)"
NOMINATIM = "https://nominatim.openstreetmap.org/search"
OSRM = "https://router.project-osrm.org"

# ── EL MUNDO DE DEMOSTRACION ────────────────────────────────────────────────────────────
# [MOCK-THE-DEMO-WORLD-MOVES-TO-SPAIN-001]
# Mover la ciudad es cambiar ESTAS constantes y volver a correr el script. Antes estaba
# cableada en tres sitios distintos (la consulta al geocodificador, los sufijos de tramo y
# el comentario del fichero generado), que es como una mudanza se queda a medias.
CITY = "Madrid"
COUNTRY = "España"

# Sufijos con los que reintentar cuando el geocodificador no da con la CALZADA. En Dublin
# hacian falta («Camden Street Lower»); en Madrid el nombre va completo y no se parte, asi
# que basta el nombre tal cual. Si una ciudad nueva los necesita, se anaden aqui.
NAME_VARIANTS = ("",)

# Las calles del catalogo, tal cual las escribe FakeUserParkingRepository.
LEON_STREETS = [
    "Calle de Fuencarral", "Calle de Hortaleza", "Calle de Atocha", "Calle Mayor",
    "Calle de Toledo", "Calle de Serrano", "Calle de Alcalá", "Calle de Génova",
    "Calle de Sagasta",
]
TRANSIT_STREETS = ["Paseo de la Castellana", "Calle de Bravo Murillo", "Avenida de América"]

# Tres aproximaciones por calle: el mismo destino alcanzado desde rumbos distintos, que es
# como se llega de verdad a la calle de siempre. Rumbo en grados, distancia en km.
APPROACHES = [(35.0, 2.6), (160.0, 1.9), (280.0, 3.2)]

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "commonMain", "kotlin", "com", "rndeveloper", "paparcar",
                   "fakes", "data", "DemoStreets.kt")


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def geocode(street):
    q = urllib.parse.urlencode(
        {"q": "%s, %s, %s" % (street, CITY, COUNTRY), "format": "json", "limit": 1})
    res = get(NOMINATIM + "?" + q)
    if not res:
        return None
    return float(res[0]["lat"]), float(res[0]["lon"])


def nearest(lat, lon, want=None, number=25):
    """Punto de la red de CONDUCCION mas cercano.

    ⛔ Si `want` es un nombre de calle, NO vale el candidato mas cercano a secas: el
    geocodificador cae a veces en el tramo peatonal (Capel Street) o en el cruce de al lado
    (Baggot Street), y la calzada mas proxima entonces es OTRA calle. Un pin en Waterloo Road
    rotulado «Baggot Street» reintroduce por la puerta de atras el desajuste que este ticket
    viene a cerrar. Se acepta el sufijo de tramo —«Camden Street Lower» ES Camden Street—, y
    si ninguno de los 25 candidatos lleva el nombre, se dice y no se inventa.
    """
    res = get("%s/nearest/v1/driving/%s,%s?number=%d" % (OSRM, lon, lat, number))
    wps = res.get("waypoints", [])
    if not wps:
        raise SystemExit("!! OSRM no engancha %s,%s a ninguna calzada" % (lat, lon))
    if want:
        for w in wps:
            n = w.get("name") or ""
            if n == want or n.startswith(want + " "):
                return w["location"][1], w["location"][0], n
        return None
    w = wps[0]
    return w["location"][1], w["location"][0], w.get("name")


def offset(lat, lon, bearing_deg, km):
    b = math.radians(bearing_deg)
    dlat = (km / 111.32) * math.cos(b)
    dlon = (km / (111.32 * math.cos(math.radians(lat)))) * math.sin(b)
    return lat + dlat, lon + dlon


def route(from_lat, from_lon, to_lat, to_lon):
    url = ("%s/route/v1/driving/%s,%s;%s,%s?overview=full&geometries=polyline"
           % (OSRM, from_lon, from_lat, to_lon, to_lat))
    res = get(url)
    if res.get("code") != "Ok" or not res.get("routes"):
        return None
    r = res["routes"][0]
    return r["geometry"], r["distance"]


def decode(enc):
    """Misma precision 5 que `PolylineCodec.decode` — aqui solo para comprobar el final."""
    pts, i, lat, lon = [], 0, 0, 0
    while i < len(enc):
        for which in range(2):
            shift, result = 0, 0
            while True:
                b = ord(enc[i]) - 63
                i += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            d = ~(result >> 1) if result & 1 else (result >> 1)
            if which == 0:
                lat += d
            else:
                lon += d
        pts.append((lat / 1e5, lon / 1e5))
    return pts


def haversine(a, b):
    R = 6371000.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp = p2 - p1
    dl = math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


def destination(street):
    """Punto de la CALZADA de `street` donde acabaran los trayectos.

    Se prueban las formas de [NAME_VARIANTS] porque en algunas ciudades la misma calle se
    parte en tramos (en Dublin, `Lower`/`Upper`) y el primer resultado del geocodificador
    puede ser el peatonal.
    """
    seed = None
    for q in (street + v for v in NAME_VARIANTS):
        g = geocode(q)
        time.sleep(1.1)                       # Nominatim pide <=1 peticion/s
        if g is None:
            continue
        seed = seed or g
        hit = nearest(g[0], g[1], want=street)
        if hit:
            return hit
    # El geocodificador puede caer en el tramo PEATONAL (Capel Street es el caso): la parte
    # por la que se circula esta a cien metros. Se rodea el punto antes de rendirse.
    if seed:
        for km in (0.08, 0.16, 0.26):
            for bearing in range(0, 360, 45):
                plat, plon = offset(seed[0], seed[1], float(bearing), km)
                hit = nearest(plat, plon, want=street)
                time.sleep(0.2)
                if hit:
                    return hit
    raise SystemExit("!! ningun tramo de calzada se llama: " + street)


def collect(streets):
    out = []
    for street in streets:
        dlat, dlon, snapped = destination(street)
        print("- %-22s -> %.6f,%.6f  (calzada: %s)" % (street, dlat, dlon, snapped))
        legs = []
        for bearing, km in APPROACHES:
            olat, olon = offset(dlat, dlon, bearing, km)
            slat, slon, _ = nearest(olat, olon)
            r = route(slat, slon, dlat, dlon)
            time.sleep(0.4)
            if r is None:
                print("   !! sin ruta desde ese rumbo, se omite")
                continue
            poly, dist = r
            pts = decode(poly)
            gap = haversine(pts[-1], (dlat, dlon))
            if gap > 40:
                print("   !! la ruta termina a %.0f m del pin, se omite" % gap)
                continue
            if not (600 <= dist <= 7000):
                print("   !! %.0f m fuera de rango, se omite" % dist)
                continue
            print("   rumbo %5.1f deg  %6.0f m  %3d puntos" % (bearing, dist, len(pts)))
            legs.append((poly, dist))
        if not legs:
            raise SystemExit("!! sin ni una ruta valida: " + street)
        out.append((street, dlat, dlon, legs))
    return out


def block(name, rows, indent="    "):
    L = [indent + "val " + name + ": List<DemoStreet> = listOf("]
    for street, lat, lon, legs in rows:
        L.append(indent + "    DemoStreet(")
        L.append(indent + '        name = "' + street + '",')
        L.append(indent + "        endLat = %.6f," % lat)
        L.append(indent + "        endLon = %.6f," % lon)
        L.append(indent + "        approaches = listOf(")
        for poly, dist in legs:
            esc = poly.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
            L.append(indent + '            DemoDrive("' + esc + '", %.0ff),' % dist)
        L.append(indent + "        ),")
        L.append(indent + "    ),")
    L.append(indent + ")")
    return "\n".join(L)


HEADER = '''package com.rndeveloper.paparcar.fakes.data

/**
 * Las calles del mundo de demostracion, con trayectos que van POR LA CALZADA.
 * [MOCK-A-DEMO-ROUTE-MUST-FOLLOW-THE-STREETS-001]
 *
 * GENERADO por `mockkit/tools/fetch_demo_routes.py` — no editar a mano. Las polilineas son
 * respuestas de OSRM sobre el grafo real de %(city)s, en el mismo formato precision-5 que lee
 * `PolylineCodec`, asi que entran literales.
 *
 * La RUTA manda sobre el pin, no al reves. Antes el catalogo inventaba una coordenada y
 * dibujaba hacia ella una curva recta que cruzaba manzanas enteras — y encima la firmaba con
 * `routeSnapped = true`, que es justo la afirmacion que la linea desmentia en pantalla. Ahora
 * el coche acaba donde de verdad acaba el trayecto: `endLat`/`endLon` SON el ultimo punto de
 * la polilinea, y la calle es la de esa calzada. No quedan dos fuentes que se puedan
 * desincronizar, que era el motivo por el que esto se generaba en el dispositivo.
 */
internal class DemoStreet(
    val name: String,
    val endLat: Double,
    val endLon: Double,
    /** Llegadas a la misma calle desde rumbos distintos: la calle de siempre, no el mismo viaje. */
    val approaches: List<DemoDrive>,
)

/** Un trayecto real: su polilinea y los metros que mide OSRM sobre la calzada. */
internal class DemoDrive(val polyline: String, val meters: Float)

internal object DemoStreets {
'''


def emit(leon, transit):
    body = block("leon", leon) + "\n\n" + block("transit", transit)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write((HEADER % {"city": CITY}) + body + "\n}\n")
    print("\nescrito: " + OUT)


if __name__ == "__main__":
    print("== Seat Leon ==")
    leon_rows = collect(LEON_STREETS)
    print("\n== Ford Transit ==")
    transit_rows = collect(TRANSIT_STREETS)
    emit(leon_rows, transit_rows)
