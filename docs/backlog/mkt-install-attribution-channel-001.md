# MKT-INSTALL-ATTRIBUTION-CHANNEL-001 · cada instalación dice de qué canal vino

**Estado:** ✅ Done (24-11-2026) — en master (hash en `MEMORY.md`, por construcción del cierre).
Función `decryptMetaInstallReferrer` desplegada y verificada (401 sin token, 200 por `firebase.json`
rewrite), secreto `META_INSTALL_REFERRER_DECRYPTION_KEY` en Secret Manager con acceso concedido
automáticamente al desplegar, política de privacidad §2.11 y snapshot de parkings `2026-W48`
(regenerado a mano para poder desplegar hosting sin el workflow semanal) en producción.

## Problema

El 22-23/11/2026 se lanzó una campaña de Meta Ads ("Paparcar | Instalaciones Android | ES",
conjunto 52526341673741) dirigida a Madrid + 11 municipios. GA4 mostró 25 instalaciones con
`source` `apps.instagram.com`/`apps.facebook.com` (campañas `ig4a`/`fb4a`), repartidas por toda
España — Meta Ads Manager sólo se atribuye 11 de esas 25 (ventana de 1 día tras clic/view/
interacción). El resto son indistinguibles hoy: pueden ser alcance orgánico del mismo Reel/post
(publicado el 22/11 21:30 en `paparcar_oficial` IG y la página de FB "Paparcar") o instalaciones
de otro origen que GA4 etiqueta igual porque el click se abrió dentro de la app de Instagram/
Facebook, pagado o no.

No hay manera de saber, por cuenta, de qué canal vino: anuncio de pago, publicación orgánica,
enlace propio (web/bio/QR) o búsqueda en Play.

## Doctrina violada

Ninguna doctrina de detección — es analítica/marketing. Pero sí aplica
[MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001]: nada de esto manda datos a Meta; es la dirección
contraria — **leer** lo que Google Play ya entregó sobre cómo llegó la instalación, nunca enviar
nada nuevo a un tercero. Por eso NO depende del consentimiento de medición de anuncios (§2.10 de
la política): ese consentimiento gobierna lo que SALE hacia Meta, no lo que YA llegó de Google
Play por una API estándar de Android.

## Señales / datos disponibles

- GA4/BigQuery (`pap-26.analytics_524957242`): `collected_traffic_source` da `apps.instagram.com`/
  `apps.facebook.com` + campaña `ig4a`/`fb4a`, pero es a nivel de DISPOSITIVO anónimo — no hay
  `setUserId` en Analytics, así que no se puede cruzar con la cuenta.
- Meta Ads Manager (confirmado por el user): la campaña es real, targeting 100% confinado a la
  Comunidad de Madrid (desglose por región), tipo de ubicación "vive / ha estado / va a menudo" —
  no explica por sí solo la dispersión geográfica medida.
- `web/lib/config.mjs` YA implementa el etiquetado de enlaces propios desde
  [MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001] (`d53d9b812`): `DOWNLOAD_SHORTCUTS`
  (`/descargar*` → `utm_source=instagram|facebook&utm_medium=social&utm_campaign=bio|parte|...`)
  y `playUrl()` (botones del propio sitio → `utm_source=web&utm_medium=owned&utm_campaign=<página>
  &utm_content=<placement>`). Esto YA resuelve la parte "enlaces propios" del punto 5 del encargo —
  no hace falta tocar `web/`, sólo añadir el canal `owned_link` al lado de la app que LEE ese
  referrer.
- `UserProfileDto` + `RemoteUserProfileDataSourceImpl` ya tienen el patrón exacto a seguir:
  campos WRITE-ONLY, nullable, actualizados con `update()` field-targeted, nunca consumidos por el
  dominio (`DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001`, `DIAG-A-ROW-MUST-SAY-WHICH-PLATFORM-WROTE-IT-001`).
  `PublishDetectionStateUseCase` es el use case gemelo (remote-only, best-effort, idempotente por
  fingerprint local en `AppPreferences`).

## Diseño

**El sistema**: un solo use case, `ResolveInstallAttributionUseCase`, corre UNA vez por instalación
(gate en `AppPreferences`), orquesta:

1. Leer el Google Play Install Referrer (`InstallReferrerPort`, Android-only; NoOp en iOS — no
   existe Play Store ahí).
2. Parsear la query del referrer (función pura, común, testable: `ParseReferrerParams`).
3. Si `utm_content` contiene el bloque cifrado de Meta (JSON con `source.data`/`source.nonce`),
   pedir al backend que lo descifre (`MetaReferrerDecryptPort` → Cloud Function
   `decryptMetaInstallReferrer`, AES-256-GCM con la "Install Referrer Decryption Key" de Meta for
   Developers — **vive sólo en Secret Manager, nunca en el APK**).
4. Clasificar el canal con una función pura (`ClassifyInstallChannel`): `META_PAID` (hay bloque
   Meta descifrable) · `META_ORGANIC_IG`/`META_ORGANIC_FB` (`apps.instagram.com`/`apps.facebook.com`
   sin bloque, o nuestro propio `utm_medium=social`) · `OWNED_LINK` (`utm_source=web&utm_medium=owned`)
   · `PLAY_ORGANIC` (`utm_source=google-play&utm_medium=organic` o referrer vacío) · `UNKNOWN`.
5. Publicar en Firestore (`UserProfileRepository.publishInstallAttribution`, mismo patrón que
   `publishDetectionState`: remote-only, best-effort, nunca leído por el dominio) + GA4
   (`AnalyticsUserProperty.InstallChannel` + evento `InstallAttributionResolved`).

⛔ **No se mezcla con `AdAttributionTracker`** (el carril de Meta App Events, que manda eventos
HACIA Meta): esto es sólo lectura hacia dentro. `AdAttribution.kt` no cambia su vocabulario cerrado.

## ⚠️ Lo que NO está verificado

El formato exacto de descifrado de Meta (`utm_content.source.data`/`.nonce` en hex, AES-256-GCM,
clave de 64 caracteres = 32 bytes) sale de documentación de terceros (Airbridge, Singular) — la
página oficial de developers.facebook.com que lo describía byte a byte no resolvió (404) al
buscarla el 24-11-2026. La función `decryptMetaInstallReferrer` está escrita contra la convención
más documentada (tag GCM de 16 bytes al final de `data`), pero **falla cerrado** (devuelve `null`,
nunca lanza) si el payload real no encaja — eso cae a `META_ORGANIC_*`, el lado seguro del fallo
asimétrico. **Antes de confiar en `META_PAID` para decidir nada**, validar con un payload real
(punto 7 del plan de pruebas, abajo).

## Criterio de éxito

- `ClassifyInstallChannel` tiene test por cada rama del vocabulario.
- `ResolveInstallAttributionUseCase` tiene test con fakes: idempotencia (segunda llamada no vuelve
  a leer), retry en fallo de conexión del servicio de Play, fallo de decrypt no bloquea la
  clasificación orgánica.
- `functions/src/installReferrer.test.ts` prueba el parseo + el roundtrip AES-GCM de la propia
  función (no prueba que coincida con el payload real de Meta — ver aviso arriba).
- `MockKoinModuleVerifyTest` sigue en verde con los nuevos puertos registrados en `mockModule`.
- Consulta de BigQuery entregada que, sobre lo que ya existe hoy (sin esperar código nuevo),
  separa `manual_content` con bloque Meta de sin bloque.

## Consumidores auditados

- `SplashViewModel.bootstrap()` — único punto de arranque tras login; dispara el resolve
  fire-and-forget, no bloquea la ruta de entrada.
- `UserProfileDto` / `toUserProfileDto()` — parity guardada por `FirestoreDeserializerParityTest`.
- `mockModule` / `MockKoinModuleVerifyTest` — los dos puertos nuevos necesitan fake.
- `AndroidPlatformModule` (impl real) vs `IosMockModule`/iOS (NoOp) vs `mockModule` (fake).
- Política de privacidad (`hosting/public/privacy-policy.html`) — nueva §2.11, draft para revisión
  del user antes de desplegar.

## Consulta BigQuery — lo que ya hay hoy, sin esperar código nuevo

Sobre `events_20261122` en adelante, marca si `manual_content` lleva el bloque de Meta (JSON con
`source.data`/`source.nonce`) — útil para separar, dentro de las instalaciones etiquetadas
`apps.instagram.com`/`apps.facebook.com`, cuáles YA traían el bloque descifrable (reales de pago,
por lo menos en las futuras que SÍ pase por el lector) de cuáles no:

```sql
SELECT
  event_date,
  COALESCE(collected_traffic_source.manual_source, traffic_source.source) AS source,
  COALESCE(collected_traffic_source.manual_medium, traffic_source.medium) AS medium,
  collected_traffic_source.manual_campaign_name AS campaign,
  collected_traffic_source.manual_content AS content,
  -- Sniff barato, igual que el cliente: ¿el content PARECE el bloque de Meta?
  REGEXP_CONTAINS(
    COALESCE(collected_traffic_source.manual_content, ''),
    r'"source"\s*:\s*\{[^}]*"data"[^}]*"nonce"'
  ) AS looks_like_meta_block,
  COUNT(*) AS installs
FROM `pap-26.analytics_524957242.events_*`
WHERE _TABLE_SUFFIX >= '20261122'
  AND event_name = 'first_open'
GROUP BY event_date, source, medium, campaign, content, looks_like_meta_block
ORDER BY event_date, installs DESC
```

⚠️ Esto es un `REGEXP_CONTAINS` sobre lo que YA está en BigQuery — nunca descifra nada (eso exige
la clave, que sólo vive en Secret Manager). Sirve para contar cuántas de las instalaciones
`apps.instagram.com`/`apps.facebook.com` llevaban el bloque, no para saber qué anuncio fue.

## Enlaces a etiquetar (operativo, no código)

Lo que `playUrl()` ya resuelve (botones del sitio) no hace falta tocarlo. Lo que sigue sin pasar
por un enlace propio:

| Sitio | Enlace hoy | Acción |
|---|---|---|
| Bio de Instagram (`paparcar_oficial`) | `paparcar.com/descargar` | Ya correcto — no tocar |
| Texto de un post/Reel de Instagram | el enlace que Instagram pone solo (su CTA nativo) | No se puede sustituir por uno propio — ese CTA es de Meta |
| Posts normales de la página de Facebook "Paparcar" | enlace a Play a pelo, o `paparcar.com/descargar/fb` | Usar siempre `/descargar/fb`, nunca Play a pelo |
| Sticker de enlace en una historia de Instagram | — | Usar `paparcar.com/descargar` (mismo que la bio) |
| Cualquier futura pieza nueva (otro Reel, otra sección de la web) | — | Pedir un `campaign` nuevo en `SITE_CAMPAIGNS`/`DOWNLOAD_SHORTCUTS` antes de publicarlo — nunca un enlace a Play a pelo |

## Plan de pruebas

1. **Lector de Android, en vacío**: instalar el APK desde `adb install`, abrir, comprobar en
   Logcat (`PlayInstallReferrer`) que `onInstallReferrerSetupFinished` corre y el documento
   `users/{uid}` recibe `installChannel` — con un emulador/dispositivo sin Play real esto cae
   normalmente a `FEATURE_NOT_SUPPORTED` → `installChannel` queda sin escribir y el flag local se
   cierra igual; es el comportamiento esperado, no un fallo.
2. **Enlace propio**: generar un enlace con `playUrl()` (o pegar uno de los ya desplegados,
   `paparcar.com/descargar/z-mostoles`), instalar desde ÉL en un dispositivo real, confirmar
   `installChannel = "owned_link"` y que `installCampaign` coincide con la ruta.
3. **Play directo**: desinstalar, buscar "Paparcar" en Play Store, instalar, confirmar
   `installChannel = "play_organic"`.
4. **`META_PAID`, cómo probarlo sin esperar a la próxima campaña real**: Meta Ads Manager tiene
   una "Vista previa de anuncio" que genera un enlace de Play real con el bloque cifrado puesto —
   usar ESE enlace para instalar en un dispositivo de prueba. Si `decryptMetaInstallReferrer`
   devuelve `decrypted:false` ahí, el formato asumido en `installReferrer.ts` está mal y hay que
   revisarlo ANTES de confiar en cualquier `META_PAID` de producción (ver el aviso de arriba).
5. **Idempotencia**: repetir la apertura de la app varias veces seguidas tras cualquiera de los
   tres casos anteriores — `installChannel` no debe volver a escribirse (comprobar
   `installAttributionResolvedAt` no cambia).

## Pendiente fuera de alcance de este ticket (operativo, no código)

- Generar la "Install Referrer Decryption Key" en Meta for Developers (Basic Settings → Android) y
  cargarla en Secret Manager como `META_INSTALL_REFERRER_DECRYPTION_KEY`.
- Añadir `referrer=` a los enlaces que ya existen fuera del sitio web (bio de Instagram si no usa
  ya `/descargar`, texto de posts de FB, stickers de historias) — lista entregada en el chat.
- Validar `decryptMetaInstallReferrer` contra un payload real (vista previa de anuncio) antes de
  confiar en `META_PAID`.
