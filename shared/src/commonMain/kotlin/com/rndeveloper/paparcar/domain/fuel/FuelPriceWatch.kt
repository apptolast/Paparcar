@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.fuel

import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * `source|id` — the identity of a forecourt across the whole app.
 *
 * ⛔ [FuelStation.id] is stable only WITHIN its feed, and Spain's `IDEESS`, France's official ref
 * and OSM element ids all coexist: the bare id would let Spain's station `1234` be the same thing as
 * an OSM `1234` a thousand kilometres away. The cache learned that first and kept the rule private
 * in its companion; from here it is also what a FOLLOW is keyed by and what an alert names, so it
 * lives where both halves can reach it — a predicate shared by two verdicts is a top-level function,
 * not a private method of whichever class needed it first. [DET-VERDICT-NOT-PREDICATE-001]
 */
fun fuelStationKey(source: String, id: String): String = "$source|$id"

/**
 * A station the user asked to keep an eye on.
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * ⛔ Not a cache row and not derived from one: this is the only thing in the fuel layer that the
 * USER put there. The layer follows the camera [FUEL-LAYER-FOLLOWS-THE-CAMERA-001], so without a
 * followed set there is no station that keeps existing when you look away — and no subject for the
 * sentence the alert is made of («8 c/l less than where you usually refuel»).
 *
 * Geography is copied in rather than joined: a followed station 300 km away must still be
 * answerable when its province has fallen out of the cache disc, and the alert needs to name and
 * place it without a round trip.
 */
data class FollowedFuelStation(
    /** `source|id` — the same composite key the cache uses, for the same collision reason. */
    val stationKey: String,
    val source: String,
    val id: String,
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val followedAtMs: Long,
)

/**
 * El surtidor del que se guarda una serie, **sin decir por qué**.
 * [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
 *
 * ⛔ Existe porque desde el 13-10 hay **dos** razones para guardar el precio de ayer, y el veredicto
 * que compara las dos lecturas no tiene que saber cuál de ellas aplica:
 *  · la estrellaste tú → [FollowedFuelStation], serie de 30 días, carril Plus;
 *  · estabas al lado → una de tu alrededor, serie corta, carril free.
 *
 * Antes el veredicto tomaba `List<FollowedFuelStation>` y por tanto **afirmaba un follow** sobre
 * cada estación que juzgaba. Meter ahí una de tu alrededor —aunque los campos encajaran— sería
 * decirle al resto del código que el usuario la estrelló. La lista blanca es el TIPO, no un `if`.
 *
 * Lleva solo lo que hace falta para comparar y para NOMBRAR el resultado: la clave que identifica
 * la serie, y el nombre y el sitio que la notificación necesita para escribir su frase y abrir el
 * mapa donde toca.
 */
data class WatchedForecourt(
    /** `source|id` — la clave de la serie. Ver [fuelStationKey]. */
    val stationKey: String,
    val name: String?,
    val latitude: Double,
    val longitude: Double,
)

/** El surtidor que el usuario estrelló, como sujeto de su serie. */
fun FollowedFuelStation.asWatched() = WatchedForecourt(
    stationKey = stationKey,
    name = name,
    latitude = latitude,
    longitude = longitude,
)

/**
 * One reading of one pump on one day: what it charged, when the FEED said so, and when we looked.
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * Two hours, and collapsing them is the mistake this whole ticket exists to avoid:
 * - [reportedAtMs] is the feed's own stamp. It is what decides whether a price MOVED — Italy carries
 *   69% of its prices two to seven days old with a tail to 2013, so «today's row differs from
 *   yesterday's row» is not the same statement as «the price changed».
 * - [observedAtMs] is when the app looked, and [observedDay] is the day that reading belongs to.
 *
 * ⛔ A sample never holds a null price. An absent price is not a sample of zero — the feeds report
 * "does not sell it" as an empty string, and a 0,000 € row would win every ranking there is.
 */
data class FuelPriceSample(
    val stationKey: String,
    val fuelType: FuelType,
    val perUnit: Money,
    /** The feed's stamp; null when the source publishes none. Null is a real answer. */
    val reportedAtMs: Long?,
    val observedAtMs: Long,
    /** Local day of [observedAtMs] — see [localDayOf]. The key that makes this a SERIES, not a log. */
    val observedDay: Long,
    /**
     * Which pump this reading was taken from, where the source distinguishes.
     * [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
     *
     * ⛔ It travels with the sample for exactly the reason the currency does: an Italian station
     * that stops quoting `self` still quotes `servito`, and the two are measured up to 0,35 €/L
     * apart — so yesterday's self reading against today's attended one is not a price that moved.
     * Same product, same station, two different questions.
     */
    val isSelf: Boolean? = null,
)

/**
 * The local day an instant falls on, as days since the epoch.
 *
 * Local and not UTC on purpose: the series exists to say «yesterday it cost less», and yesterday is
 * a word about the user's calendar, not about Greenwich. A driver in Madrid reading a price at
 * 00:30 has to see it land on the day they would call it.
 *
 * ⛔ It is also what stops the history from becoming a log: eight readings in one afternoon are one
 * row, and the last one wins. A user who opens the app a lot must not end up with a denser — and
 * therefore differently-shaped — history than one who opens it once.
 */
fun localDayOf(epochMs: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone).date.toEpochDays()
