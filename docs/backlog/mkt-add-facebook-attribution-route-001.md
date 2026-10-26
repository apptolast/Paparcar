# MKT-ADD-FACEBOOK-ATTRIBUTION-ROUTE-001 · una ruta de atribución para el enlace de Facebook

**Estado:** ✅ Done (22-10-2026) · rama y worktree borrados al cerrar
· ⏳ **queda desplegar y verlo UNA vez en device** — hasta entonces la atribución está escrita, no
demostrada (igual que su predecesor, `mkt-an-install-must-say-where-it-came-from-001`)

## Problema
`MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001` (14-10) resolvió la atribución de instalaciones
para Instagram con seis rutas `/descargar/*` en `firebase.json`, cada una con su `referrer`
percent-encoded. Faltaba el mismo mecanismo para un enlace propio de Facebook (página), que hasta
ahora no tenía ruta: cualquier clic desde ahí a `paparcar.com/descargar` se habría atribuido a
Instagram (mismo `utm_source` hardcodeado para todas las rutas).

## Doctrina violada
Ninguna de detección. Es una ampliación del sistema de atribución de marketing
(`WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001` / `MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001`):
el mismo principio — un clic tiene que decir de dónde viene — aplicado a una segunda fuente.

⚠️ **Decisión ya tomada y que esto NO contradice**: el conjunto de anuncios PAGADO de Instagram
excluye explícitamente el placement de Facebook (`INSTAGRAM-GROWTH-STRATEGY.md` §9.3, "la
creatividad es vertical y el resto diluye el aprendizaje"). Esta ruta es para el enlace ORGÁNICO de
la página de Facebook, no para un placement de pago — son dos cosas distintas.

## Señales / datos disponibles
`WebSiteParityGuardrailTest.kt` (`every download shortcut carries an encoded install referrer
naming its own campaign`) ya comprobaba automáticamente las seis rutas de Instagram, pero asumía un
único `UTM_SOURCE = "instagram"` para TODAS las rutas bajo `/descargar` — el propio comentario del
código lo anticipaba: *"a second source gets its own constant"*.

## Diseño
- Nueva ruta en `firebase.json`: `/descargar/fb` → Play con
  `referrer=utm_source%3Dfacebook%26utm_medium%3Dsocial%26utm_campaign%3Dfb`.
- El guard se generaliza en vez de ganar una excepción: `UTM_SOURCE_OF_PATH` (map ruta → fuente
  esperada), con `UTM_SOURCE` como valor por defecto para todo lo que no esté en el map. Así una
  tercera fuente futura se añade con una entrada en el map, no con un `if` nuevo.
- `utm_campaign=fb` porque el guard deriva el campaign esperado del slug de la ruta
  (`source.removePrefix("$DOWNLOAD_PATH/")`), igual que las rutas de territorio existentes.
- `utm_medium=social` porque no es una zona pagada (el prefijo `z-` sigue siendo la única marca de
  `paid`).
- `docs/marketing/INSTAGRAM-GROWTH-STRATEGY.md` actualizado (siete rutas, no seis) para que el doc
  vivo no quede diciendo lo de antes.

## Criterio de éxito
- `./gradlew :shared:testDebugUnitTest --tests '*WebSiteParity*' --rerun-tasks` en verde con la
  ruta nueva.
- La URL `/descargar/fb`, una vez desplegada, resuelve un 301 con el `referrer` correcto
  (verificación pendiente de despliegue — no se despliega sin permiso explícito).

## Consumidores auditados
- `firebase.json` · `hosting.redirects` — ruta añadida.
- `shared/.../architecture/WebSiteParityGuardrailTest.kt` — único sitio que lee y valida ese bloque;
  generalizado.
- `docs/marketing/INSTAGRAM-GROWTH-STRATEGY.md` §7.1 — lista de rutas actualizada.
- `web/lib/config.mjs` — no toca `PLAY_URL` ni ningún hecho declarado ahí; no aplica.
- Grep de `UTM_SOURCE` y `instagram` fuera de este fichero de test: sin otros consumidores que
  asuman una única fuente.
