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

**Decidido por el user (16-09): opción C — ni encendido ni apagado, se PREGUNTA** al compartir y al
unirse. Sale de aquí a su propio ticket, `copy-a-card-that-promises-routes-stay-home-001`, porque
arrastra el copy de la tarjeta que hoy promete lo contrario.

Lo que se descartó: **apagado** (la geometría es el dato más sensible que maneja la app y su hermana
personal nace apagada — pero un default apagado deja la función muerta hasta que alguien la
descubra) y **encendido**, el de hoy, que obliga al copy a disculpar lo que ya está hecho.

## 3 · Los miembros de un grupo pueden leer los tokens de push de los demás

**Confirmado.** `firestore.rules`, `match /members/{uid}`: `allow read: if` eres miembro del coche.
No hay separación por documento, así que cualquier miembro puede leer la ficha entera de los demás
— **`tokens` incluidos**.

Qué se puede hacer con eso: nada directo (enviar un push exige la clave del servidor), pero es un
identificador de dispositivo de otra persona expuesto a un grupo entero, y no hace falta para nada
que la app haga: el reparto lo hace la Cloud Function con permisos de servidor.

### Arreglo concreto (2.ª vuelta de la revisión) — ~1 día

1. Subcolección `sharedCars/{carId}/members/{uid}/devices/{installationId}` con
   `allow read, write: if request.auth.uid == uid`. **Nadie del grupo la lee**; la función sí, con
   permisos de admin, así que le da igual dónde vivan.
2. La función lee la subcolección **y, durante una versión, también el campo viejo**: sin ese
   solape, los móviles que aún no han migrado dejan de recibir avisos en silencio — que es
   exactamente el fallo que el KDoc de la regla de `update` de `members` ya avisa que existe.
3. El cliente migra y **borra** `tokens` de su ficha.
4. Y de paso, la regla de `update` de `members` pasa a acotar QUÉ campos toca:
   `request.resource.data.diff(resource.data).affectedKeys().hasOnly(['displayName','photoUrl','sharesRoutes'])`.
   Hoy un miembro puede reescribir su propio `role` o su `inviteCode`.

⛔ **El vector completo, que es peor que «se leen entre ellos»**: `zoneAlertSubs` acepta tokens
ajenos (mientras no entre `sec-a-zone-alert-row-is-only-rewritten-by-its-owner-001`) y
`PushMessageHandler` los pinta sin comprobar que la zona sea tuya. Leer el token de un compañero de
grupo y meterlo en una fila de alertas propia = **spam a su móvil durante 30 días**, que es lo que
dura la suscripción. Los tres agujeros son pequeños por separado y una cadena completa juntos.

## 4 · El uid de quien libera una plaza lo lee cualquier usuario autenticado

**Confirmado.** `spots`: `allow read: if request.auth != null`, y `SpotDto.reportedBy` lleva el uid.
La política ya avisa de que la plaza va ligada a «un identificador interno» y de que nadie ve tu
nombre, correo ni foto — o sea que no miente. Pero cualquiera con cuenta puede **agrupar plazas por
`reportedBy`** y reconstruir por dónde aparca una persona, que es exactamente el perfilado que el
geohash de Analytics se molestó en evitar.

### Arreglo elegido (user, 16-09): **B — colección aparte** — ~1 día

En Firestore leer un documento es leer **todos** sus campos, así que «no devolver `reportedBy`» no
existe como opción. Las dos que sí:

- **B (elegida)** · `spotOwners/{spotId}` con `allow read: if false`. Se crea **en el mismo batch**
  que la plaza (la regla de `create` de `spots` lo exige con `getAfter`, que es lo que impide
  publicar sin dueño registrado). El `update`/`delete` del dueño compara contra esa colección; la
  función la lee para su auto-exclusión.
- **A (descartada)** · `ownerKey = sha256(uid/spotId)` comprobado con `hashing.sha256(...)` en
  reglas. Más barata, pero **deja el identificador en el documento público**: no lo elimina, lo
  disfraza, y basta con probar tu propio uid contra cada plaza para reconocer las tuyas.

⏱ Las plazas duran **horas**, así que la migración es trivial: una ventana de un día aceptando las
dos formas y la colección vieja se vacía sola por TTL.

## 5 · El flag de diagnóstico lo activa un admin, no el usuario

**Confirmado.** `diagnostics_config/{uid}`: `allow create, update: if false`; el usuario solo puede
**leer** y **borrar** el suyo. O sea: la telemetría de detección se enciende desde fuera y el
usuario no puede encenderla ni apagarla desde la app — solo eliminar el documento.

No es ilegal por sí mismo (la base es el interés legítimo en la fiabilidad, y se declara), pero la
política dice «default OFF» sin explicar **quién** lo enciende. Arreglo mínimo: decirlo — que se
activa a petición o para diagnosticar un problema concreto, y que el usuario puede retirarlo.

**Y darle el botón**, que la regla ya permite: `diagnostics_config/{uid}` deja `delete` al dueño. O
sea que «dejar de enviar diagnóstico» es hoy una línea de UI que nadie ha escrito, no un permiso que
falte. Con indicador de que está activo, porque un usuario no puede retirar lo que no sabe que
existe.

---

## Orden sugerido (revisado tras la 3.ª vuelta)

1. **#4 `spotOwners`** junto con `priv-a-spot-nobody-looks-at-never-dies-001`: tocan la misma
   colección y la misma regla, así que hacerlos por separado es pagar dos veces la migración.
2. **#1 (seudónimas + Crashlytics) y #5 (el botón de diagnóstico)** — media hora entre los dos, y
   los dos son promesas publicadas que hoy no se cumplen. **Proponer meterlos en la 1.2.0.**
3. **#3 `devices/`** baja de prioridad: el filtro por `zoneId`/`carId` del handler
   [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001] ya neutraliza el spam que hacía
   peligrosa la lectura de tokens ajenos. Sigue siendo exposición innecesaria, no una vía de ataque.
4. **#2 · CERRADO** por la decisión 5C: la pregunta de rutas sustituyó al default y vive en
   `copy-a-card-that-promises-routes-stay-home-001`.
