package com.rndeveloper.paparcar.permissions

import platform.Foundation.NSUserDefaults

/**
 * Lo ÚNICO de esta pantalla que iOS no sabe contestar: si la escalada a «Siempre» ya se presentó.
 *
 * `requestAlwaysAuthorization()` solo levanta el diálogo una vez desde `WhenInUse`; a partir de ahí
 * el sistema lo descarta **en silencio** y el único camino son los Ajustes. No hay API pública que
 * lo diga, así que lo anotamos nosotros al pedirlo, y sobrevive al proceso — igual que la decisión
 * del usuario. [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
 *
 * Un solo dueño de la clave: lo escribe [com.rndeveloper.paparcar.ios.permissions.IosPermissionRequester]
 * al pedirlo y lo lee [IosPermissionManagerImpl] al componer el estado. Dos copias de este `String`
 * serían dos opiniones sobre si al usuario ya se le preguntó.
 */
internal object AlwaysLocationAsk {

    private const val KEY = "paparcar.permissions.alwaysLocationAsked"

    val wasPresented: Boolean
        get() = NSUserDefaults.standardUserDefaults.boolForKey(KEY)

    fun markPresented() {
        NSUserDefaults.standardUserDefaults.setBool(true, KEY)
    }
}
