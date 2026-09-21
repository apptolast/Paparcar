package com.rndeveloper.paparcar.domain.vehicle

import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize

/**
 * Cuánto mide de alto un coche, **en banda y no en cifra**.
 * [VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001]
 *
 * ⛔ El ticket pedía inferir la altura de marca + modelo, «por la misma vía que `inferBodyType()`».
 * Se miró y **no se puede hacer honestamente**: el catálogo tiene miles de modelos y ninguna altura,
 * así que rellenarlo sería escribir a mano miles de cifras sin fuente — y luego dar un veredicto
 * («cabes») que se comprueba contra una barra de acero.
 *
 * Lo que sí sabemos de verdad es el CUERPO, y un cuerpo no da una cifra: da un **rango**. Un
 * utilitario mide entre 1,45 y 1,60; una furgoneta comercial, entre 1,95 y 2,70. Esa diferencia ya
 * contesta la única pregunta que importa —*¿paso por debajo de 1,80?*— en los casos que importan, y
 * **se calla** cuando la banda cruza la barrera, que es exactamente donde nadie debería pronunciarse.
 *
 * Y es donde el campo manual cobra sentido: declarar tu altura **colapsa la banda a un punto** y
 * convierte un «no lo sé» en un veredicto. Una baca o un cofre no están en ningún catálogo del
 * mundo, sólo en la cabeza de quien los montó.
 */
data class HeightBand(val lowMeters: Float, val highMeters: Float) {
    /** Una altura declarada es una banda de anchura cero: sabemos exactamente cuánto mide. */
    constructor(exact: Float) : this(exact, exact)
}

/**
 * La banda del cuerpo. Valores de gálibo real de coches de calle, redondeados hacia fuera — si nos
 * equivocamos que sea **ensanchando la duda**, nunca estrechándola.
 *
 * ⚠️ `FAMILY_LONG` es la más ancha a propósito: en la taxonomía cabe tanto un familiar de 1,48 como
 * un monovolumen compacto de 1,70, y fingir que son lo mismo colocaría a la mitad de ellos en el
 * lado equivocado de una barrera de 1,80.
 */
fun CarbodyType.heightBand(): HeightBand = when (this) {
    CarbodyType.HATCHBACK_SMALL -> HeightBand(1.40f, 1.60f)
    CarbodyType.SUV_SMALL -> HeightBand(1.50f, 1.68f)
    CarbodyType.HATCHBACK_MEDIUM -> HeightBand(1.40f, 1.55f)
    CarbodyType.SUV_MEDIUM -> HeightBand(1.58f, 1.75f)
    CarbodyType.SEDAN -> HeightBand(1.38f, 1.52f)
    CarbodyType.FAMILY_LONG -> HeightBand(1.44f, 1.75f)
    CarbodyType.SUV_LARGE -> HeightBand(1.65f, 1.85f)
    CarbodyType.VAN_LIGHT -> HeightBand(1.75f, 1.95f)
    CarbodyType.VAN_COMMERCIAL -> HeightBand(1.90f, 2.80f)
    CarbodyType.PICKUP -> HeightBand(1.75f, 2.00f)
}

/**
 * Lo que sabemos del alto de ESTE vehículo: su altura declarada si la hay, si no la banda de su
 * cuerpo, y null cuando no hay ni una cosa ni la otra.
 *
 * ⛔ Lo declarado MANDA sobre el catálogo, siempre. Quien escribe 2,05 en el campo es quien lleva el
 * cofre puesto, y ningún cuerpo inferido sabe eso.
 */
fun Vehicle.heightBand(): HeightBand? = when {
    heightMeters != null -> HeightBand(heightMeters)
    // Una moto pasa por cualquier barrera de coches, y no tiene carrocería que consultar.
    sizeCategory == VehicleSize.MOTORCYCLE -> HeightBand(1.00f, 1.60f)
    else -> carbodyType?.heightBand()
}

/** Si este coche pasa por debajo de una barrera. [VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001] */
enum class ClearanceVerdict {
    /** Pasa con holgura. Es el único valor que autoriza a decirle al usuario que cabe. */
    FITS,

    /** No pasa. Vale para ESCONDER un parking, nunca para prometer nada. */
    DOES_NOT_FIT,

    /**
     * No nos pronunciamos: falta la altura del coche, falta la del parking, o la banda cruza la
     * barrera y decidir sería tirar una moneda.
     *
     * ⛔ **Un gálibo desconocido no es «cabes»**, igual que una tarifa desconocida no es «gratuito»
     * (#69, #46). Ante la duda no se promete: el que se equivoca aquí se lo encuentra en forma de
     * barra de acero.
     */
    UNKNOWN,
}

/**
 * ⚠️ **El margen no es simétrico, y no es un descuido.** Para decir que CABES exigimos que sobren
 * [CLEARANCE_MARGIN_METERS]; para decir que NO cabes basta con que el mínimo ya no quepa. Es la
 * doctrina de fallo asimétrico del proyecto aplicada a una barra de acero: equivocarse escondiendo
 * un parking cuesta un rodeo, y equivocarse prometiendo que cabes cuesta el techo del coche.
 */
fun Vehicle.clearanceUnder(barrierMeters: Double?): ClearanceVerdict {
    if (barrierMeters == null || barrierMeters <= 0.0) return ClearanceVerdict.UNKNOWN
    val barrier = barrierMeters.toFloat()
    val band = heightBand() ?: return ClearanceVerdict.UNKNOWN
    return when {
        band.highMeters + CLEARANCE_MARGIN_METERS <= barrier -> ClearanceVerdict.FITS
        band.lowMeters > barrier -> ClearanceVerdict.DOES_NOT_FIT
        else -> ClearanceVerdict.UNKNOWN
    }
}

/**
 * Los 5 cm que tienen que sobrar para prometer que cabes.
 *
 * Un gálibo señalizado se mide hasta el punto más bajo del dintel, y un coche no siempre mide lo que
 * pone en su ficha: neumáticos nuevos, suspensión cargada, una antena. Cinco centímetros es lo que
 * separa «cabe en el papel» de «cabe con el coche cargado un domingo».
 */
const val CLEARANCE_MARGIN_METERS = 0.05f
