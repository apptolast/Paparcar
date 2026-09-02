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
```

Cubre: perfil (`users/{uid}`) + `parkingHistory` + `vehicles` + `zones` (subcolecciones) +
telemetría (`sessions/*/events`, `uiLocation`) + config de diagnósticos.
Los **spots** publicados (`spots`, campo `reportedBy = uid`) NO se tocan: caducan solos por TTL
y no llevan identidad — es lo que declara la política (§2.6, §5).

## 3 · Borrar la cuenta de Auth
Firebase Console → Authentication → Users → ⋮ en el uid → **Delete account**.
(Va después de Firestore: mientras exista la cuenta, las rules permiten re-escrituras del dueño.)

## 4 · Verificar y confirmar
- Re-consultar las tres rutas del paso 2 (Console o MCP): **0 documentos**.
- Responder al email confirmando el borrado. Sin plantilla legal: hecho + fecha.
- ⚠️ Si el usuario tenía sesión en otro dispositivo, su token puede escribir hasta ~1 h después
  del delete de Auth — re-verificar al día siguiente si el caso lo merece.
