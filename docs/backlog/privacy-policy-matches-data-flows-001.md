# PRIVACY-POLICY-MATCHES-DATA-FLOWS-001 — la política describe la 1.6.0, no la 1.5.0

**Estado:** ✅ Done · mergeado en master junto con MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001 (tocan el mismo §2.10) · rama `chore/PRIVACY-POLICY-MATCHES-DATA-FLOWS-001` · worktree `.worktrees/privacy-policy-review`

## Por qué

La ficha de Play se movió para el próximo corte (1.6.0). La política (`hosting/public/privacy-policy.html`)
se tocó por última vez en `3cec44eb4` (03-11, SDK de Meta). Desde entonces entraron 20 commits en
master; barridos contra el código, **dos** cambian lo que la política promete:

1. **Parkings: la app ya no pregunta a Overpass, pregunta a `paparcar.com`** (`357360579`,
   [PARKINGS-ONE-CALL-MERGED-001]). `ApiPublicParkingsSource` manda `?cell=<banda>_<columna>&v=…`
   — una celda de **3 km** (`ParkingZoneGrid.CELL_METERS`), sin cuenta ni identificador; la función
   `mergeCell` es anónima y su respuesta la cachea el CDN. Overpass queda **detrás**, como fallback.
   La política decía que los parkings los servía OpenStreetMap con «un punto y un radio», y que las
   consultas al mapa «no las guardamos en absoluto» — `mergeCell` registra la celda servida
   (`logger.info('parkings cell served', { cell, … })`), sin nada que identifique a quien pregunta.
2. **La puerta del consentimiento de Meta cambió de forma** (`b99e0536b`, login rehecho, #140).
   Antes: la casilla «Acepto la política» bloqueaba TODOS los botones de acceso. Ahora: la casilla
   solo existe en el **registro con email**; Google y el login de una cuenta existente muestran un
   aviso («al continuar aceptas…») y **no** crean `hasAcceptedLegalConsent`. Meta solo arranca con
   esa marca. La política decía «hasta que aceptas esta política en la pantalla de acceso», que ya
   no describe dónde ni cómo.

Revisados y **sin efecto** en la política: correcciones de aparcamiento (`573c9f695`, todo local),
`BtConnectionStore` (prefs locales), ocupación de Madrid (dato abierto, servidor → cliente, sin
datos de usuario), `firestore.rules` (`publicParkings` read-only, sin datos personales), manifest
(sin cambios de permisos desde `3cec44eb4`).

## Qué se cambia

- `hosting/public/privacy-policy.html`, EN y ES simétricos (lo exige `PrivacyPolicyParityGuardrailTest`):
  fecha, §2.8 (dónde miras), §2.10 (la puerta de Meta), §4 (fila nueva: servidor propio; OSM pasa a
  fallback de parkings), §5 (qué guarda nuestro servidor de una consulta al mapa).
- `docs/legal/DATA-SAFETY-FORM.md`: el argumento «servicios públicos de datos» y la fila de Meta.

## ⚠️ Abierto para el user (no es de esta tarea)

- **Un usuario nuevo que entra con Google crea cuenta sin marcar nada** — solo lee el aviso. Es un
  patrón aceptado («al continuar aceptas»), pero es un cambio respecto a
  [AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001], y deja a todo usuario de Google **fuera de la
  atribución de Meta** para siempre (nunca obtiene la marca). La política describe lo que hace el
  código; si el user prefiere otra cosa, el cambio es de producto.
- El formulario de Data Safety: la fila de ubicación aproximada ya está cubierta por Analytics; la
  celda de 3 km enviada a nuestro servidor no añade tipo de dato nuevo, pero el user debe
  revisarlo al subir la 1.6.0.
- Desplegar (`firebase deploy --only hosting`) **en el corte de la 1.6.0**, no antes: la 1.5.0
  publicada sigue preguntando a Overpass.
