# WEB-POLICY-URLS-MOVE-TO-PAPARCAR-COM-001 · La superficie legal se muda a su propio dominio

**Estado:** ✅ Done (03-09-2026)

## Problema
`paparcar.com` quedó conectado a Firebase Hosting el 02/03-09-2026 (verificado: A `199.36.158.100`,
`https://paparcar.com/privacy-policy` y `/delete-account` responden 200 con certificado, http→https
301). Pero toda la superficie pública sigue nombrando la infraestructura, no el producto:
- La política y la página de borrado se autorreferencian como `pap-26.web.app` y dan como contacto
  el Gmail personal del desarrollador (`rndeveloper11501@gmail.com`, 9 apariciones en los 2 HTML).
- Ajustes (`SettingsViewModel`) abre la política en `pap-26.web.app` y el mailto va al Gmail.
- `LICENSES_URL` apunta a `paparcar.app/licenses` — un dominio SIN REGISTRAR que cualquiera podría
  comprar y servir contenido bajo "nuestro" enlace de licencias.
El user creó `support@paparcar.com` como redirección (forwarding de Porkbun → su Gmail), así que ya
existe contacto de dominio QUE RECIBE.

## Doctrina violada
- «Copy sin mecánica interna»: `pap-26.web.app` es el nombre del proyecto Firebase, jerga de
  infraestructura filtrada al usuario final y a la ficha de Play.
- El barrido estaba anotado como pendiente desde el 29-08 en la memoria del dominio
  ([[project-domain-and-email-setup-001]]): "cuando la web responda".

## Señales / datos disponibles
- `pap-26.web.app` sigue sirviendo en paralelo (Firebase no lo apaga): los enlaces viejos NO se
  rompen. Cambiar la canónica es seguro.
- `www.paparcar.com` está ROTO (302 al forwarding de Porkbun `paparcar-com.l.ink` → 404). Fuera de
  alcance de este ticket: se arregla en las consolas (Firebase: añadir `www` como redirect; Porkbun:
  quitar el URL forwarding). Anotado abajo como pendiente del user.

## Diseño
Un solo dominio canónico en TODA la superficie: `https://paparcar.com`. El invariante vive en dos
sitios inevitables (los HTML servidos y las companion constants de `SettingsViewModel`); todo lo
demás (docs legales, ficha de Play) son copias documentales que se barren en la misma pasada.
- `support@paparcar.com` sustituye al Gmail en todo lo público. ⚠️ Verificar con el user que el
  alias del forwarding de Porkbun es EXACTAMENTE `support@` (no `soporte@`) — un alias que no
  coincide pierde solicitudes de borrado RGPD en silencio.
- `LICENSES_URL` pasa a `https://paparcar.com/licenses`: sigue siendo 404 a propósito (el fix real
  es AboutLibraries), pero deja de apuntar a un dominio hijackeable.
- Redeploy de hosting (`firebase deploy --only hosting` — el MCP es no-op) para que el site = repo.

## Criterio de éxito
- `curl https://paparcar.com/privacy-policy` servida sin rastro de `pap-26.web.app` ni del Gmail.
- Grep del repo: `rndeveloper11501@gmail.com` solo en exentos (SKILL de field-test = cuenta de
  device); `pap-26.web.app` solo en docs históricos cerrados.
- `:shared` compila (las constants no tienen test propio; el enlace se verifica en mano).

## Consumidores auditados
Grep `pap-26\.web\.app|rndeveloper11501@gmail\.com|paparcar\.app` (02-09):
- `hosting/public/privacy-policy.html` (6 gmail + 2 textuales web.app) → ✅ cambiado
- `hosting/public/delete-account.html` (3 gmail) → ✅ cambiado
- `SettingsViewModel.kt` (3 constants + 2 comentarios) → ✅ cambiado
- `docs/legal/DATA-SAFETY-FORM.md` (URLs del formulario + email) → ✅ cambiado
- `docs/legal/ACCOUNT-DELETE-RUNBOOK.md` (mención textual) → ✅ cambiado
- `distribution/release-notes.txt` (email de bugs) → ✅ cambiado
- `docs/release/play-listing/**` → ✅ barrido en el árbol principal (03-09, sed; los ficheros solo
  existen allí, sin commitear — viajarán con el commit de la ficha de Play)
- `.claude/skills/field-test/SKILL.md` → exento (Gmail = login de la cuenta del Oppo, no contacto)
- Fakes/previews `alex@paparcar.app`, `user@paparcar.app` → exentos (emails ficticios de ejemplo)
- Backlog cerrado (`account-delete-has-a-web-path-001`, `set-links-point-at-a-live-policy-001`,
  `settings-audit-remediation-001`) → exentos (registro histórico)

## Pendiente del user (consolas, no repo)
- Arreglar `www.paparcar.com` (Firebase redirect + quitar el forwarding de Porkbun).
- Play Console: pegar las URLs nuevas (política, borrado, sitio web) y `support@paparcar.com`.
- ✅ Alias `support@` confirmado por el user (03-09).

## Follow-up ya encargado por el user (ticket aparte)
Checkbox de aceptación de la política en el login (con enlace) — lo típico de consentimiento en el
alta. El enlace en Ajustes YA existe (`SET-LINKS-POINT-AT-A-LIVE-POLICY-001`).
