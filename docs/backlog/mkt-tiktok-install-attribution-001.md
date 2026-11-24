# MKT-TIKTOK-INSTALL-ATTRIBUTION-001 · TikTok se suma al canal de instalación, vía enlace propio

**Estado:** ✅ Done (24-11-2026) — en master (hash en `MEMORY.md`, por construcción del cierre).

## Problema

[MKT-INSTALL-ATTRIBUTION-CHANNEL-001] (mergeado hoy, `f2ae17f7a`) ya distingue Meta pagado/orgánico,
enlace propio y Play orgánico. El user pide ahora lo mismo para TikTok: saber qué cuentas llegaron
desde un anuncio y cuáles desde una publicación orgánica.

## Doctrina violada

Ninguna de detección. Misma doctrina de marketing que el ticket padre: leer lo que Play ya entrega,
nunca mandar nada nuevo a un tercero.

## Investigación — por qué el diseño NO es "igual que Meta"

El truco de Meta (Instagram/Facebook etiquetan solas cualquier enlace a Play abierto dentro de su
app, con `apps.instagram.com`/`apps.facebook.com`) **no existe en TikTok**: su navegador in-app no
inyecta ningún referrer — confirmado por varias fuentes de terceros (ninguna documentación oficial
de TikTok lo contradice). Sin un enlace ya etiquetado, Play no sabe que la instalación vino de ahí.

Para el objetivo nativo **"App Install"** de TikTok Ads Manager, el paso de "App Attribution" exige
conectar un **MMP** (AppsFlyer/Adjust/Kochava/Singular) — sin eso TikTok ni siquiera se atribuye sus
propios anuncios. Paparcar no tiene ningún MMP hoy, y adoptar uno es una decisión de coste/alcance
aparte, fuera de este ticket.

**Decisión del user (24-11-2026, vía pregunta directa):** tanto el anuncio como la publicación
orgánica usarán SIEMPRE un enlace propio (`paparcar.com/descargar/tiktok` ·
`paparcar.com/descargar/z-tiktok`) en vez del flujo nativo de TikTok Ads. Esto hace el diseño mucho
más simple que el de Meta: nada de bloque cifrado, nada de Cloud Function, nada de secreto nuevo —
es la MISMA tabla `DOWNLOAD_SHORTCUTS` que ya resolvía Instagram/Facebook, con dos filas más.

## Diseño

**El sistema no cambia — gana dos filas y dos ramas de un `when`:**

1. `web/lib/config.mjs` → `DOWNLOAD_SHORTCUTS`:
   - `/descargar/tiktok` → `{ source: 'tiktok', medium: 'social', campaign: 'tiktok' }` (bio,
     descripción de vídeo/post — orgánico).
   - `/descargar/z-tiktok` → `{ source: 'tiktok', medium: 'paid', campaign: 'z-tiktok' }` (CTA del
     anuncio). El prefijo `z-` no es geográfico aquí — lo era para `z-mostoles`, una zona de
     flyers — pero `WebSiteParityGuardrailTest` sólo exige la relación MECÁNICA slug-prefijo ↔
     `medium=paid`, no que el slug sea una zona; se reutiliza la misma convención en vez de abrir
     una segunda.
   - Mismas dos filas, mirror manual, en `firebase.json` → `hosting.redirects` (no puede importar
     el `.mjs`).
2. `InstallAttribution.kt` → dos entradas nuevas en `InstallChannel`: `TIKTOK_PAID("tiktok_paid")`,
   `TIKTOK_ORGANIC("tiktok_organic")`.
3. `ClassifyInstallChannel.kt` → `source == "tiktok"` clasifica por `medium` (`paid` → pagado,
   cualquier otra cosa → orgánico) — a diferencia de Instagram/Facebook, aquí no hace falta mirar
   un bloque cifrado porque **sólo nuestro propio enlace produce este `source`**.
4. `WebSiteParityGuardrailTest` → `UTM_SOURCE_OF_PATH` gana las dos rutas nuevas apuntando a
   `UTM_SOURCE_TIKTOK`, igual que hizo Facebook.

⛔ No se tocó `ResolveInstallAttributionUseCase`, `PlayInstallReferrerDataSource`,
`UserProfileRepository.publishInstallAttribution` ni la Cloud Function — ninguno sabe ni necesita
saber qué `source` concreto llegó; sólo consumen el `InstallChannel` ya clasificado. El invariante
sigue en UN sitio (`ClassifyInstallChannel`).

## Criterio de éxito

- `ClassifyInstallChannelTest` cubre `TIKTOK_PAID`/`TIKTOK_ORGANIC`, incluido el caso sin `medium`.
- `WebSiteParityGuardrailTest` sigue en verde con las dos filas nuevas (comprueba forma, paridad
  `config.mjs` ↔ `firebase.json`, y el slug-prefijo ↔ medium).
- `node web/build.mjs --check` sin diffs.

## Consumidores auditados

- `ResolveInstallAttributionUseCase` — no necesita cambios, ya es agnóstico al vocabulario.
- `AnalyticsTracker`/`FirebaseAnalyticsTracker` — usan `.wire`, no `when` exhaustivo. Sin cambios.
- `UserProfileDto`/`FirestoreDeserializerParityTest` — el campo es un `String` libre (`channel`),
  no un enum server-side. Sin cambios.
- `MockKoinModuleVerifyTest` — no se añadió ningún puerto/clase nueva a Koin. Sin cambios.
- `firestore.rules` — sin allowlist de valores de canal. Sin cambios.
- Ningún `when` exhaustivo sobre `InstallChannel` fuera de `ClassifyInstallChannel` (grep limpio).

## Pendiente fuera de alcance de este ticket (operativo, no código)

- Dar de alta la cuenta de TikTok del negocio si no existe, y poner `paparcar.com/descargar/tiktok`
  en su bio.
- Configurar el/los primeros anuncios de TikTok Ads con el CTA apuntando a
  `paparcar.com/descargar/z-tiktok` — **nunca** al flujo nativo "direct to store", que requeriría
  un MMP.
- Si en el futuro se decide adoptar un MMP (AppsFlyer/Adjust/…) para atribución nativa de TikTok
  Ads (y de cualquier otra red), es un ticket propio — cambia la arquitectura (SDK nuevo, posible
  nuevo carril de consentimiento de medición, `AdAttribution.kt` ganaría una red más en su lista
  blanca). No asumir que este ticket lo deja listo.
