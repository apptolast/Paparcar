# PRIV-LEGAL-FOLLOW-UPS-FOR-1-2-1-001 · Los cinco hallazgos de la revisión legal que el user aparcó para la 1.2.1, verificados uno a uno

**Estado:** 🔵 Abierto — 2026-09-16, desde master `aa0be232`. **Aplazados a la 1.2.1 por el user.**
**Método:** cada punto viene de la revisión de Claude web y **está comprobado contra el código o las
reglas antes de anotarlo**, porque un hallazgo de otro agente no es un hecho hasta que se mide.
Los dos comentarios desfasados de la lista original ya NO están aquí: se arreglaron en
`priv-leaving-a-group-must-clear-your-name-from-its-head-001`.

---

## 1 · «Estadísticas anónimas» debería decir «seudónimas», y Crashlytics va ligado al uid

**Confirmado.** `CrashReporter.kt:15` llama a `FirebaseCrashlytics.setUserId(userId ?: "")`, así que
los informes de fallo **llevan tu uid**. La política (§2.7) dice «estadísticas de uso anónimas» de
Analytics —que es defendible, van por app-instance id— pero **no dice nada del uid en Crashlytics**,
y los enumera en la misma sección, lo que invita a leer «anónimo» sobre los tres.

Arreglo: en §2.7, separar los dos y llamar a lo de Analytics **seudónimo** (hay un identificador
estable, aunque no sea tu nombre), y decir que los informes de fallo van ligados a tu cuenta para
poder responder a un problema que hayas reportado. Data Safety: la fila de *Crash logs* pasa a
declarar que puede vincularse a la identidad del usuario.

## 2 · Rutas al grupo, encendidas por defecto

**Confirmado**: `Vehicle.shareRoutesInGroup = true` y la migración de Room lo estampa a 1, con el
KDoc diciendo que *el default ES la decisión* (user, 15-09). Desde
`PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001` el interruptor ya impide SUBIR, no solo mostrar, así
que la política lo describe bien hoy.

Lo que queda es de producto, no de exactitud: **¿debería nacer apagado?** A favor de apagarlo: la
geometría es el dato más sensible que maneja la app y su hermana personal nace apagada. En contra:
el grupo se comparte precisamente para verse, y un default apagado deja la función muerta hasta que
alguien la descubra. **Decisión del user, no técnica.**

## 3 · Los miembros de un grupo pueden leer los tokens de push de los demás

**Confirmado.** `firestore.rules`, `match /members/{uid}`: `allow read: if` eres miembro del coche.
No hay separación por documento, así que cualquier miembro puede leer la ficha entera de los demás
— **`tokens` incluidos**.

Qué se puede hacer con eso: nada directo (enviar un push exige la clave del servidor), pero es un
identificador de dispositivo de otra persona expuesto a un grupo entero, y no hace falta para nada
que la app haga: el reparto lo hace la Cloud Function con permisos de servidor.

Arreglo propuesto: sacar `tokens` de `members/{uid}` a una subcolección que solo el dueño de la
ficha escriba y **nadie del grupo lea** (`members/{uid}/push/{deviceId}`), o marcarlo como campo
prohibido en la regla de lectura. ⚠️ Toca la función (`functions/src/index.ts:205` los lee con
permisos de admin, así que le da igual dónde estén) y una migración de los tokens ya escritos.

## 4 · El uid de quien libera una plaza lo lee cualquier usuario autenticado

**Confirmado.** `spots`: `allow read: if request.auth != null`, y `SpotDto.reportedBy` lleva el uid.
La política ya avisa de que la plaza va ligada a «un identificador interno» y de que nadie ve tu
nombre, correo ni foto — o sea que no miente. Pero cualquiera con cuenta puede **agrupar plazas por
`reportedBy`** y reconstruir por dónde aparca una persona, que es exactamente el perfilado que el
geohash de Analytics se molestó en evitar.

Arreglo propuesto: que el cliente no reciba `reportedBy`. Hace falta para: (a) que el dueño pueda
editar/borrar la suya, y (b) el `!=` de la regla de update. Se puede sostener con una regla que
compare `resource.data.reportedBy` sin exponerlo… lo que en Firestore **no se puede** (leer el doc
es leer todos sus campos). Alternativa real: un hash con sal por plaza, o mover la edición a una
función. **No es un cambio de una línea: merece su propio ticket cuando se aborde.**

## 5 · El flag de diagnóstico lo activa un admin, no el usuario

**Confirmado.** `diagnostics_config/{uid}`: `allow create, update: if false`; el usuario solo puede
**leer** y **borrar** el suyo. O sea: la telemetría de detección se enciende desde fuera y el
usuario no puede encenderla ni apagarla desde la app — solo eliminar el documento.

No es ilegal por sí mismo (la base es el interés legítimo en la fiabilidad, y se declara), pero la
política dice «default OFF» sin explicar **quién** lo enciende. Arreglo mínimo: decirlo — que se
activa a petición o para diagnosticar un problema concreto, y que el usuario puede retirarlo.

---

## Orden sugerido

3 y 4 son los que exponen datos de terceros: primero. 1 es media hora de copy. 5 es una frase.
2 es una decisión que no se puede tomar sin el user.
