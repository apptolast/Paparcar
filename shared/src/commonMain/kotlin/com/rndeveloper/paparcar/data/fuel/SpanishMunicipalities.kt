package com.rndeveloper.paparcar.data.fuel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns the TOWN a parking is in into the ids the Spanish feed wants.
 * [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
 *
 * ### Why the town and not the province
 * Because the province is the field the app does not reliably have. `MineturStationsSource` asked
 * only by province, resolved from `address.province ?: address.region`, and on a real phone in El
 * Puerto de Santa María that came through as **«Andalucía»** — the autonomous community, not the
 * province. No province matched, the source threw, the registry fell back to OSM, and the lane told
 * the user that Spain does not publish fuel prices. It does: measured the same day, the ministry
 * answered that exact town with prices stamped 16:38.
 *
 * The town, on the other hand, is the one part of an address that is always there — it is what the
 * peek prints under the street. And the ministry's own municipality list carries `IDProvincia`
 * beside `IDMunicipio`, so ONE lookup yields both keys: the town for a precise, smaller query, and
 * the province as the wider net behind it.
 *
 * ### ⛔ The article moves, and stripping accents is not enough
 * The ministry writes the article LAST, in brackets — «Puerto de Santa María (El)» — while a
 * geocoder writes «El Puerto de Santa María». Matching those two needs the article removed from
 * BOTH ends, not just a lowercase and an accent fold. That single detail is the difference between
 * this file working and it failing exactly like the province path it replaces.
 *
 * ### ⛔ The id field is spelled differently in the two listings
 * `/Listados/Provincias/` says **`IDPovincia`** (no 'r' — the ministry's own typo, see
 * [SpanishProvinces]); `/Listados/Municipios/` says **`IDProvincia`**, with it. Verified live on
 * 10-09-2026. Reading the wrong one returns null for every row, silently.
 */
object SpanishMunicipalities {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The two ids a town yields: itself, and the province it belongs to. */
    data class Entry(val municipalityId: String, val provinceId: String)

    /** Parses `/Listados/Municipios/` into entry-by-canonical-town-name. */
    fun parse(body: String): Map<String, Entry> = buildMap {
        json.parseToJsonElement(body.removePrefix(BOM)).jsonArray.forEach { element ->
            val row = element.jsonObject
            val municipalityId = row[FIELD_MUNICIPALITY_ID]?.jsonPrimitive?.content?.trim().orEmpty()
            val provinceId = row[FIELD_PROVINCE_ID]?.jsonPrimitive?.content?.trim().orEmpty()
            val name = row[FIELD_NAME]?.jsonPrimitive?.content?.trim().orEmpty()
            if (municipalityId.isNotEmpty() && name.isNotEmpty()) {
                // ⛔ `putIfAbsent` semantics: two towns can canonicalise to the same key once their
                // articles are gone. The FIRST wins rather than the last, so the mapping is stable
                // across runs — and a wrong guess is caught downstream, where a town whose stations
                // are nowhere near the user hands over to the province.
                val key = canonical(name)
                if (key.isNotEmpty() && !containsKey(key)) {
                    put(key, Entry(municipalityId = municipalityId, provinceId = provinceId))
                }
            }
        }
    }

    /** The entry for [cityName] as the geocoder wrote it, or null when no town matches. */
    fun find(cityName: String?, municipalities: Map<String, Entry>): Entry? =
        cityName?.takeIf { it.isNotBlank() }?.let { municipalities[canonical(it)] }

    /**
     * One spelling for a town however it was written: no case, no accents, and **no article** at
     * either end. «El Puerto de Santa María», «Puerto de Santa María (El)» and «PUERTO DE SANTA
     * MARIA (EL)» all land on `puerto de santa maria`.
     *
     * The article list covers the languages the ministry's own file uses — Spanish, Catalan,
     * Galician and Basque town names all appear in it verbatim.
     */
    private fun canonical(value: String): String {
        val folded = value.trim().lowercase().map { ACCENTS[it] ?: it }.joinToString("")
        val withoutTrailing = TRAILING_ARTICLE.replace(folded, "").trim()
        val withoutLeading = LEADING_ARTICLE.replace(withoutTrailing, "").trim()
        return SPACES.replace(withoutLeading, " ")
    }

    /** «puerto de santa maria (el)» → «puerto de santa maria». */
    private val TRAILING_ARTICLE = Regex("""\s*\((?:el|la|los|las|l'|els|es|sa|s'|a|o|as|os)\)\s*$""")

    /** «el puerto de santa maria» → «puerto de santa maria». */
    private val LEADING_ARTICLE = Regex("""^(?:el|la|los|las|els|es|sa|a|o|as|os)\s+|^(?:l'|s')""")

    private val SPACES = Regex("""\s+""")

    private val ACCENTS: Map<Char, Char> = mapOf(
        'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a',
        'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
        'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o',
        'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u',
        'ñ' to 'n', 'ç' to 'c',
    )

    private const val BOM = "﻿"
    private const val FIELD_MUNICIPALITY_ID = "IDMunicipio"

    /** ⛔ WITH the 'r', unlike the province listing's `IDPovincia`. Verified live 10-09-2026. */
    private const val FIELD_PROVINCE_ID = "IDProvincia"
    private const val FIELD_NAME = "Municipio"
}
