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
firebase firestore:delete "zoneAlertSubs/<uid>" --project pap-26 --force
firebase firestore:delete "users/<uid>" -r --project pap-26 --force
firebase firestore:delete "diagnostics/<uid>" -r --project pap-26 --force
firebase firestore:delete "diagnostics_config/<uid>" --project pap-26 --force
```

Cubre: alertas de zona y **los tokens de push** (`zoneAlertSubs/{uid}`), perfil (`users/{uid}`) +
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
2. **Coche suyo** → se disuelve el grupo entero, como hace `stopSharing`:
   ```bash
   firebase firestore:delete "sharedCars/<carId>" -r --project pap-26 --force
   ```
   No es daño colateral: el espejo es una proyección de SU coche, y nadie pierde su historial —
   cada miembro conserva sus aparcamientos en su propia cuenta.
3. **Coche prestado** → se va solo lo suyo, como hace `leave`. Las sesiones **primero**:
   ```bash
   # en sharedCars/<carId>/sessions, borrar los docs con authorUid == <uid>
   firebase firestore:delete "sharedCars/<carId>/members/<uid>" --project pap-26 --force
   ```
4. Dejar constancia en la respuesta si se disolvió algún grupo: los demás miembros verán
   desaparecer el coche compartido de su garaje (su fila queda desvinculada, no borrada).

## 3 · Borrar la cuenta de Auth
Firebase Console → Authentication → Users → ⋮ en el uid → **Delete account**.
(Va después de Firestore: mientras exista la cuenta, las rules permiten re-escrituras del dueño.)

## 4 · Verificar y confirmar
- Re-consultar las rutas del paso 2 (Console o MCP): **0 documentos**. Incluido `zoneAlertSubs/<uid>`
  y, por cada grupo del paso 2.1, que no quede ni su ficha de miembro ni ninguna sesión suya.
- Responder al email confirmando el borrado. Sin plantilla legal: hecho + fecha.
- ⚠️ Si el usuario tenía sesión en otro dispositivo, su token puede escribir hasta ~1 h después
  del delete de Auth — re-verificar al día siguiente si el caso lo merece.
