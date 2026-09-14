package com.rndeveloper.paparcar.data.fuel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns the province a parking is in into the id the Spanish feed wants.
 * [FUEL-STATIONS-LIST-001]
 *
 * The feed is queried BY PROVINCE — the whole country is 12 MB, which is not something to download
 * on a phone to draw six rows. The id comes from the ministry's own `/Listados/Provincias/`, so the
 * list stays right without us maintaining a table of 52 names; what we do own is the matching,
 * because the name we hold comes from a reverse geocoder ("Cádiz") and the feed shouts its own
 * ("CÁDIZ"), and neither is going to change for the other.
 *
 * ⛔ The id field is spelled **`IDPovincia`** — no 'r'. That is the ministry's typo, verified live
 * on 08-09-2026, and reading `IDProvincia` returns null for every row. Ours to live with.
 */
object SpanishProvinces {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parses `/Listados/Provincias/` into id-by-normalised-name. */
    fun parse(body: String): Map<String, String> = buildMap {
        json.parseToJsonElement(body.removePrefix(BOM)).jsonArray.forEach { element ->
            val row = element.jsonObject
            val id = row[FIELD_ID]?.jsonPrimitive?.content?.trim().orEmpty()
            val name = row[FIELD_NAME]?.jsonPrimitive?.content?.trim().orEmpty()
            if (id.isNotEmpty() && name.isNotEmpty()) put(normalise(name), id)
        }
    }

    /**
     * The id for [regionName] as the geocoder wrote it, or null when it is not a Spanish province
     * — which is the normal answer everywhere else in the world, not a failure.
     */
    fun idFor(regionName: String?, provinces: Map<String, String>): String? =
        regionName?.let { provinces[normalise(it)] }

    /**
     * El id de provincia que ya lleva dentro un código postal español, o null.
     * [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
     *
     * Los ids del ministerio son los **códigos INE** —verificado en vivo contra
     * `/Listados/Provincias/` el 14-09-2026: 52 filas, ids `01`…`52`— y los códigos INE son por
     * construcción los **dos primeros dígitos del código postal**. Así que esto no es una
     * heurística ni una tabla que mantener: es cómo está hecho el sistema postal español.
     *
     * ⛔ No necesita el catálogo. Es la única de las tres claves que no descarga nada y la única que
     * no empareja NOMBRES — ni acentos, ni «Puerto de Santa María (El)», ni comunidades autónomas
     * donde debería ir una provincia.
     *
     * Valida la forma antes de creérselo: cinco dígitos y un prefijo dentro de `01`..`52`. Un postal
     * extranjero (o un campo con basura) no puede convertirse en una provincia española por
     * accidente.
     */
    fun idFromPostalCode(postalCode: String?): String? {
        val digits = postalCode?.trim()?.takeIf { it.length == POSTAL_CODE_LENGTH && it.all(Char::isDigit) }
            ?: return null
        return digits.take(2).takeIf { it.toInt() in FIRST_PROVINCE..LAST_PROVINCE }
    }

    private const val POSTAL_CODE_LENGTH = 5
    private const val FIRST_PROVINCE = 1
    private const val LAST_PROVINCE = 52

    /**
     * Case and accents removed, because "Cádiz", "CÁDIZ" and "Cadiz" are one province and only one
     * of those three spellings is going to reach us on any given day. Explicit character mapping
     * rather than a locale-aware fold: this runs on Android and on iOS, and the two do not agree
     * about what uppercasing an accented letter means.
     */
    private fun normalise(value: String): String = value.trim().lowercase()
        .map { ACCENTS[it] ?: it }
        .joinToString("")

    private val ACCENTS: Map<Char, Char> = mapOf(
        'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a',
        'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
        'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o',
        'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u',
        'ñ' to 'n', 'ç' to 'c',
    )

    private const val BOM = "﻿"
    private const val FIELD_ID = "IDPovincia"
    private const val FIELD_NAME = "Provincia"
}
