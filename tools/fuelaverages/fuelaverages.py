#!/usr/bin/env python3
"""Regenerate the embedded national-average fuel price table from OpenVan.camp.

[FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]

The table this writes is the app's FLOOR: what the spend figure uses when there is no network and
nothing has been refreshed yet. It used to be typed by hand — eight countries and a promise to
review them every release, a promise that drifted 16-20% off the real Spanish pump price. Now it is
generated, so refreshing it is a command instead of somebody's memory.

    python tools/fuelaverages/fuelaverages.py

Writes shared/src/commonMain/kotlin/com/rndeveloper/paparcar/domain/fuel/EmbeddedAverageFuelPrices.kt

WHAT THIS SCRIPT DECIDES, and why it is here and not in Kotlin:

  * Gallons become litres. Fourteen countries quote per gallon (US) or per imperial gallon (four
    Caribbean ones). The domain must never see a gallon, so the unit is resolved at the boundary --
    here for the embedded table, and in OpenVanFuelPricesParser for the network one. Both use the
    same two constants, and a unit neither of them knows is DROPPED, never assumed to be litres.
  * Rows the feed itself distrusts (`is_excluded`) are dropped.
  * Stale rows are dropped: one country (FM) was four months old while the other 167 were same-day.
    A price with no fresh date is a number somebody made up.

Only the LITRE half is generated. The kWh half of StaticAverageFuelPrices stays hand-written
because OpenVan publishes no electricity price at all -- that is a measured absence, not an
oversight. See the ticket doc.
"""

from __future__ import annotations

import json
import sys
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path

ENDPOINT = "https://openvan.camp/api/fuel/prices?source=paparcar.com"

# Litres in one unit. The domain never sees these: they are spent here.
LITRES_PER_UNIT = {
    "liter": 1.0,
    "gallon": 3.785411784,
    "imperial_gallon": 4.54609,
}

# A row older than this against the feed's own `updated_at` is not a current price.
MAX_ROW_AGE = timedelta(days=30)

OUTPUT = (
    Path(__file__).resolve().parents[2]
    / "shared/src/commonMain/kotlin/com/rndeveloper/paparcar/domain/fuel/EmbeddedAverageFuelPrices.kt"
)

HEADER = '''package com.rndeveloper.paparcar.domain.fuel

/**
 * The national averages shipped inside the binary. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * ⛔ GENERATED FILE — do not edit by hand. Regenerate with:
 *
 *     python tools/fuelaverages/fuelaverages.py
 *
 * This is the floor of the price ladder: the answer when there is no network and no refresh has
 * landed yet. It is Kotlin rather than a JSON asset on purpose — reading `composeResources` is a
 * suspend call, and the spend figure is computed synchronously from thirteen call sites, so an
 * asset would have forced the whole calculation to become asynchronous to buy nothing. A generated
 * table costs one file and keeps the invariant absolute: **no I/O and no network at compute time.**
 *
 * Every price is PER LITRE and in the country's own currency. Gallons were converted by the
 * generator; the domain never sees one.
 *
 * Source: OpenVan.camp, CC BY 4.0 — https://openvan.camp/
 */
internal object EmbeddedAverageFuelPrices {

    /** When the feed last refreshed the figures below. Shown as "average" and dated, never as fact. */
    const val AS_OF: String = "%(as_of)s"

    /** %(count)d countries. Absent fuels are null: a country that sells no LPG has no LPG price. */
    val ROWS: Map<String, CountryFuelPrices> = mapOf(
'''

FOOTER = """    )
}
"""


def fetch() -> dict:
    request = urllib.request.Request(ENDPOINT, headers={"Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        if response.status != 200:
            sys.exit(f"OpenVan answered {response.status}")
        return json.loads(response.read().decode("utf-8"))


def literal(value: float | None) -> str:
    """A Kotlin Double literal, or null. Four decimals is what the feed itself carries."""
    return "null" if value is None else f"{round(value, 4)}"


def main() -> None:
    payload = fetch()
    if not payload.get("success"):
        sys.exit("OpenVan reported success=false")

    updated_at = datetime.fromisoformat(payload["meta"]["updated_at"].replace(" ", "T"))
    rows, dropped = {}, []

    for code, country in sorted(payload["data"].items()):
        if country.get("is_excluded"):
            dropped.append((code, "feed marks it excluded"))
            continue

        factor = LITRES_PER_UNIT.get(country.get("unit"))
        if factor is None:
            dropped.append((code, f"unknown unit {country.get('unit')!r}"))
            continue

        fetched_at = datetime.fromisoformat(country["fetched_at"]).replace(tzinfo=None)
        if updated_at - fetched_at > MAX_ROW_AGE:
            dropped.append((code, f"stale, fetched {fetched_at.date()}"))
            continue

        prices = country["prices"]

        def per_litre(key: str) -> float | None:
            raw = prices.get(key)
            return None if raw is None else raw / factor

        gasoline, diesel = per_litre("gasoline"), per_litre("diesel")
        if gasoline is None and diesel is None:
            dropped.append((code, "neither gasoline nor diesel"))
            continue

        # LPG and CNG are one FuelType here (LPG_CNG), and LPG is the one a car actually finds.
        rows[code] = (
            country["currency"],
            gasoline,
            diesel,
            per_litre("lpg") if prices.get("lpg") is not None else per_litre("cng"),
        )

    body = "".join(
        f'        "{code}" to CountryFuelPrices('
        f'"{currency}", {literal(gasoline)}, {literal(diesel)}, {literal(lpg)}),\n'
        for code, (currency, gasoline, diesel, lpg) in rows.items()
    )

    OUTPUT.write_text(
        HEADER % {"as_of": updated_at.date().isoformat(), "count": len(rows)} + body + FOOTER,
        encoding="utf-8",
    )

    print(f"wrote {OUTPUT.relative_to(Path(__file__).resolve().parents[2])}")
    print(f"  {len(rows)} countries, as of {updated_at.date()}")
    for code, why in dropped:
        print(f"  dropped {code}: {why}")


if __name__ == "__main__":
    main()
