# SET-LINKS-POINT-AT-A-LIVE-POLICY-001 · Los enlaces de Ajustes apuntan a un dominio que no existe

**Estado:** ✅ Done (29-08-2026) — privacy y contacto apuntan a destinos vivos; las 3 URLs son
constantes del companion. `LICENSES_URL` sigue muerta a propósito (su fix es AboutLibraries
in-app, follow-up de SETTINGS-AUDIT-REMEDIATION-001).

> 📌 Actualización 03-09-2026: `LICENSES_URL` ya no existe. La fila abre una pantalla in-app
> generada desde el grafo de Gradle — ver `set-licenses-are-shown-in-the-app-001.md`.

## Problema
Los tres enlaces externos de Ajustes salían a `paparcar.app` — un dominio **inventado en su día
como placeholder y nunca registrado**: `https://paparcar.app/privacy`,
`https://paparcar.app/licenses` y `mailto:hola@paparcar.app`. El usuario que tocara "Política de
privacidad" o "Contacto" acababa en un error de DNS o escribiendo a un buzón que no existe.
Detectado en la auditoría de settings (follow-up abierto) y desbloqueado el 28-08-2026 al publicar
la política real en Firebase Hosting.

## Doctrina violada
Ningún control de Ajustes puede prometer un destino que no existe (mismo espíritu que
SETTINGS-AUDIT-REMEDIATION-001: cada control dice la verdad). Además, Play revisa el enlace
de política **dentro de la app** — un enlace muerto es motivo de rechazo.

## Señales / datos disponibles
- Política publicada y verificada: `https://pap-26.web.app/privacy-policy` (site por defecto del
  proyecto pap-26; fuente `hosting/public/privacy-policy.html`, PLAY-PRIVACY-POLICY-001).
- Correo de contacto operativo: `rndeveloper11501@gmail.com` (el de la cuenta de Play/Firebase).

## Diseño
Las tres URLs pasan de literales inline a constantes del `companion object` del
`SettingsViewModel` (regla de magic values): `PRIVACY_POLICY_URL`, `LICENSES_URL`,
`CONTACT_MAILTO`. Privacy y contacto apuntan a destinos vivos. Si algún día se registra un
dominio propio, se conecta como dominio custom del MISMO site de Hosting y esta constante no
necesita cambiar (la URL vieja sigue sirviendo).

**Fuera de alcance a propósito:** `LICENSES_URL` sigue muerta — su arreglo real es una pantalla
de licencias in-app (AboutLibraries, toca build config), ya rastreado como follow-up de
SETTINGS-AUDIT-REMEDIATION-001. Queda anotado con ⚠️ en el companion.

## Criterio de éxito
Tocar "Política de privacidad" en Ajustes abre la política publicada; "Contacto" abre el cliente
de correo con la dirección real. Compila prod+mock y los tests de settings pasan.

## Consumidores auditados
`grep paparcar\.app` en `src/`:
- `SettingsViewModel.kt` (privacy/licenses/mailto) → **cerrado aquí** (licenses exenta, ver Diseño).
- `SettingsPreviews.kt`, `CardSurfaceOptionsPreviews.kt`, `StateGalleryScreen.kt` →
  `user@paparcar.app` como email de EJEMPLO en previews/galería mock — exentos (dato ficticio de
  un perfil, no un destino navegable).
- `commonTest` → cero referencias.
