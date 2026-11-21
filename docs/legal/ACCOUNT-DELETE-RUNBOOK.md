# Runbook — borrado manual de cuenta (solicitud web/email)

> Ejecuta la promesa de `paparcar.com/delete-account`: solicitud por email → borrado completo
> en ≤30 días + respuesta de confirmación. Espejo del `DeleteAccountUseCase` in-app — si aquel
> cambia (repos nuevos en `DomainModule`), este runbook cambia en la MISMA tarea.

## 1 · Verificar al solicitante
- La solicitud debe llegar **desde el email de la cuenta**. Buscar ese email en
  Firebase Console → Authentication → Users → copiar el **uid**.
- Si el email no existe en Auth → responder que no hay ninguna cuenta con esa dirección (no
  revelar nada más). Si llega desde OTRA dirección → pedir que reenvíen desde la de la cuenta.

## 2 · Borrar datos (Firestore) — mismo orden que `DeleteAccountUseCase`
Con la CLI (borra subcolecciones recursivamente, cosa que el cliente no puede):

```bash
firebase firestore:delete "users/<uid>" -r --project pap-26 --force
firebase firestore:delete "diagnostics/<uid>" -r --project pap-26 --force
firebase firestore:delete "diagnostics_config/<uid>" --project pap-26 --force
firebase firestore:delete "diagnostics_reports/<uid>" -r --project pap-26 --force
```

⛔ **`zoneAlertSubs` NO se borra por ruta**: sus documentos se llaman `{uid}_{zoneId}`, uno por zona
(`ZoneAlertSubscription.kt`), así que `zoneAlertSubs/<uid>` no existe y el comando saldría bien sin
borrar nada — con los **tokens de push** dentro. Hay que filtrar por campo: en la consola de
Firestore, `zoneAlertSubs` → filtrar `uid == <uid>` y borrar los documentos que salgan.

Cubre: perfil (`users/{uid}`) +
`parkingHistory` + `vehicles` + `zones` (subcolecciones) + telemetría (`sessions/*/events`,
`uiLocation`) + config de diagnósticos.
Los **spots** publicados (`spots`, campo `reportedBy = uid`) NO se tocan: caducan solos por TTL
y no llevan identidad — es lo que declara la política (§2.6, §5).

### 2.1 · Coches compartidos — el paso que no se puede scriptear a ciegas
[PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001]

`sharedCars` **no está indexado por uid**, así que no hay una ruta que borrar: hay que encontrar los
grupos y tratarlos distinto según el papel. La app hace exactamente esto, y aquí se repite a mano.

1. Localizar los coches ANTES de borrar `users/<uid>`: en `users/<uid>/vehicles`, las filas con
   `sharedCarId != null`. Anotar cada `sharedCarId` y si `ownerUid` es null (**el coche es suyo**)
   o trae otro uid (**es un coche prestado**).
2. **Coche suyo** → se disuelve el grupo entero, como hace `stopSharing`. ⛔ **El código de
   invitación vivo va ANTES**: vive en una colección raíz, `sharedCarInvites`, así que el `-r` del
   coche no lo alcanza y quedaría un código huérfano apuntando a un grupo que ya no existe. Se lee
   en el propio coche (`sharedCars/<carId>.inviteCode`):
   ```bash
   firebase firestore:delete "sharedCarInvites/<code>" --project pap-26 --force   # si inviteCode != null
   firebase firestore:delete "sharedCars/<carId>" -r --project pap-26 --force
   ```
   No es daño colateral: el espejo es una proyección de SU coche, y nadie pierde su historial —
   cada miembro conserva sus aparcamientos en su propia cuenta.
3. **Coche prestado** → se va solo lo suyo, como hace `leave`. Las sesiones **primero**, y después
   su nombre en la cabecera, que es la tarjeta que el grupo mira:
   ```bash
   # 1) en sharedCars/<carId>/sessions, borrar los docs con authorUid == <uid>
   # 2) en sharedCars/<carId>/state/current, si authorUid == <uid> → poner authorName a null;
   #    si movedByUid == <uid> → poner movedByUid y movedByName a null.
   #    ⛔ La POSICIÓN no se toca: dónde está el coche es del grupo.
   firebase firestore:delete "sharedCars/<carId>/members/<uid>" --project pap-26 --force
   ```
4. **Si el dueño EXPULSA a alguien a mano**, hace exactamente lo mismo que el paso 3 con el uid
   del expulsado: sus sesiones y su nombre en la cabecera antes de borrar su ficha. Al expulsado se
   le cierra la puerta detrás —su fila se desvincula y su propio borrado de cuenta ya no encuentra
   el grupo—, así que lo que no se limpie aquí no lo limpia nadie nunca.
   [PRIV-A-REMOVED-MEMBER-LEAVES-THEIR-TRIPS-BEHIND-001]
5. Dejar constancia en la respuesta si se disolvió algún grupo: los demás miembros verán
   desaparecer el coche compartido de su garaje (su fila queda desvinculada, no borrada).

### 2.2 · Conservar los documentos de la promo «Fundadores»
[MONEY-FOUNDERS-PLUS-PROMO-001]

Dejar intactos `grants/<uid>` y `foundersClaims/<uid>`. El trigger `releaseFoundersSlot` los
gestiona al borrar la cuenta de Auth. `grants/<uid>` permite saber si hay una plaza que devolver
y si queda pendiente revocar el regalo en RevenueCat.

Si alguien ya borró un documento del apartado, tratarlo como una incidencia de la promoción.
Conciliar la concesión en RevenueCat y el cupo con la evidencia del servidor antes de corregir el
contador.

## 3 · Borrar la cuenta de Auth
Firebase Console → Authentication → Users → ⋮ en el uid → **Delete account**.
(Va después de Firestore: mientras exista la cuenta, las rules permiten re-escrituras del dueño.)

Este borrado individual dispara `releaseFoundersSlot`. Si la concesión estaba en curso o RevenueCat
falla, comprobar el resultado del reintento antes de dar por terminado el borrado.

## 4 · Verificar y confirmar
- Re-consultar las rutas del paso 2 (Console o MCP): **0 documentos**. Incluida la consulta `zoneAlertSubs` con `uid == <uid>`
  y, por cada grupo del paso 2.1, que no quede ni su ficha de miembro ni ninguna sesión suya.
- Comprobar que `grants/<uid>` y `foundersClaims/<uid>` están ausentes. Si la cuenta ocupaba una
  plaza, verificar que quedó devuelta en `firebase functions:log --only releaseFoundersSlot --project pap-26`
  y que el regalo está revocado en RevenueCat. Una cuenta sin plaza no cambia el cupo. El total
  agregado puede variar por otras altas o bajas simultáneas. Si queda una revocación pendiente,
  seguir el reintento hasta confirmar su resolución.
- Responder al email confirmando el borrado. Sin plantilla legal: hecho + fecha.
- ⚠️ Si el usuario tenía sesión en otro dispositivo, su token puede escribir hasta ~1 h después
  del delete de Auth — re-verificar al día siguiente si el caso lo merece.
