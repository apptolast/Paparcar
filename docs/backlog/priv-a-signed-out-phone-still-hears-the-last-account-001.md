# PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001 · Cerrar sesión no retira los tokens, y el siguiente usuario del móvil recibe avisos ajenos

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Origen:** revisión independiente, 2.ª vuelta, sobre `afbd917c` — hallazgo C1. **Verificado.**

## Problema

El `signOut` vive en dos sitios (`SplashViewModel.kt:304`, `SettingsViewModel.kt:289`) y limpia
geovallas, Room, RevenueCat y preferencias. **Lo único del repo que vacía tokens de push es
`ZoneAlertSubscriptionRepository.deleteAllData`, que solo corre al BORRAR LA CUENTA**
[PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001].

Así que al cerrar sesión quedan vivos, apuntando a ESTE móvil:

- `zoneAlertSubs/{uid}_{zoneId}.tokens` — las zonas que el que se fue vigilaba
- `sharedCars/{carId}/members/{uid}.tokens` — los grupos a los que pertenecía

El servidor sigue repartiendo a ese token, y el móvil lo sigue teniendo. Peor: el reparto no se
apaga solo, porque los dos sincronizadores **no hacen nada sin uid** (`SharedCarMemberSync.kt:69`,
`ZoneAlertSubscriptionSync.kt:86-90`) — sin sesión no hay quien retire nada.

Y el móvil los **muestra**: `PushMessageHandler` pinta `zone_spot` sin comprobar que la zona exista
(`:61-72`), y `shared_car_parked` igual — `isMyOwnTrip` devuelve false sin sesión (`:129-130`), que
es justo la rama que deja pasar el aviso.

Resultado: quien entre después en ese móvil —o el mismo dueño tras cerrar sesión— recibe **dónde ha
aparcado el coche de un grupo al que no pertenece** y **plazas de zonas que no son suyas**. Las
suscripciones caducan a los 30 días (`ZoneAlertSubscription.kt`), así que la ventana es larga.

## Doctrina violada

`SESSION-ISOLATION-001` cubre Room, geovallas y preferencias, y dejó fuera el único dato que vive
en OTRO dispositivo lógico: el token. Y es la tercera vez este mes que la erasure resulta ser una
LISTA que nadie amplía.

## Diseño

**Dos mitades, y hacen falta las dos** — una retira el permiso y la otra no se fía de él:

1. **Al salir**, antes de `authRepository.signOut()` y mientras el uid todavía existe:
   `replaceAll(uid, emptyList())`, `setMemberTokens(carId, uid, emptyList())` por cada coche
   compartido, y `deleteToken()` del proveedor de push.
   ⛔ El orden importa por lo mismo que en el barrido de cuenta: sin sesión, las reglas ya no dejan
   escribir.
2. **Al recibir**, `PushMessageHandler` deja de creerse el mensaje: sin sesión → `Ignored`; una
   zona que no está en `ZoneRepository` → `Ignored`; un coche que no está en `SharedCarDao` →
   `Ignored`. Un token retirado tarde —o un mensaje ya en vuelo— deja de tener superficie.

## Criterio de éxito

Cerrar sesión con alertas de zona y un coche compartido deja las dos filas **sin tokens**, y un
mensaje de cualquiera de los dos tipos llegado después **no se pinta**.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `SplashViewModel:304` | 🔧 punto de salida real |
| `SettingsViewModel:289` | 🔧 el otro |
| `SharedCarMemberSync:69` · `ZoneAlertSubscriptionSync:86-90` | ✅ correctos: no pueden limpiar lo que ya no tiene uid |
| Borrado de cuenta | ✅ ya cubierto |
