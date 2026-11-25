# Play Console — Data Safety form (guía de respuestas)

> Estas respuestas tienen que decir LO MISMO que la política publicada. Si cambia lo que la app
> recoge, se actualizan los dos en la MISMA tarea. Inventario verificado contra el manifest
> prodRelease y los data sources de Firestore el 28-08-2026; ampliado el 08-09-2026 con los
> eventos de producto de Firebase Analytics [ANALYTICS-EVENTS-001]; **re-verificado contra el
> código el 16-09-2026 para la 1.2.0** — coche compartido, push al grupo, rutas y cuatro fuentes
> de datos más [LEGAL-DATA-SAFETY-ANSWERS-FOR-1-2-0-001]; **reabierto el 02-11-2026 con el SDK de
> Meta** — la primera vez que algo de esta app sale hacia un tercero con fin PUBLICITARIO, y por
> eso lo primero que cambia no es una fila, es el «Compartido = No» de abajo
> [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001].
>
> **Política publicada (28-08-2026): <https://paparcar.com/privacy-policy>** — dominio custom
> conectado el 02-09-2026 al site por defecto de pap-26 (`pap-26.web.app` sigue sirviendo lo mismo
> como fallback). Fuente: `hosting/public/privacy-policy.html`, se despliega con
> `firebase deploy --only hosting` (config en `firebase.json`).

## Atribución de instalaciones · 1.8.1

La app lee el referrer de Google Play y registra el canal y los datos de campaña disponibles en
el perfil de Firebase. El backend de Paparcar descifra el bloque de Meta cuando corresponde.
GA4 recibe el canal como evento y propiedad de instalación, sin el identificador de cuenta ni
los identificadores de campaña de Meta. La atribución de instalación y el envío de eventos de
medición a Meta son flujos distintos; el segundo sigue sujeto a su consentimiento específico.

Para revisar en Play Console, contrastar **App activity → App interactions** con los propósitos
**Analytics** y **Advertising or marketing**. La recogida de atribución procede de Google Play
y se registra en la infraestructura del desarrollador. La transferencia separada al SDK de Meta
sigue reflejada en la declaración de datos compartidos. Revisar asimismo el uso del identificador
de cuenta para vincular la atribución al perfil. La política pública cubre la finalidad y el
vínculo con la cuenta en §2.11.

La etiqueta pública, consultada el 26-11-2026, mostraba **Purchase history** como único dato
compartido. Mostraba **App interactions** e **identificadores de dispositivo** como recogidos,
sin **Advertising or marketing**. Actualizar las dos categorías para declarar la compartición
opcional con Meta con finalidad publicitaria. Conservar las demás respuestas del formulario
actual al aplicar el cambio. La etiqueta pública no permite reconstruir todas las respuestas
de Play Console.

## Preguntas generales

| Pregunta | Respuesta |
|---|---|
| ¿Recoge o comparte datos de usuario? | **Sí** |
| ¿Todos los datos en tránsito van cifrados? | **Sí** (TLS — Firebase/HTTPS) |
| ¿Ofrece un mecanismo para solicitar el borrado? | **Sí** — borrado en-app (Ajustes → Eliminar cuenta) + web. **URL de borrado de cuenta (campo del formulario): `https://paparcar.com/delete-account`** (viva desde 29-08; runbook admin en `ACCOUNT-DELETE-RUNBOOK.md`). Marcar también que se puede pedir borrado PARCIAL sin cerrar la cuenta (la página lo ofrece) |
| ¿App dirigida a menores? | No |

## Tipos de datos a declarar

Para cada uno: **Recogido = Sí**. «Compartido» en el sentido de Play = transferido a terceros
— Firebase/Crashlytics son *service providers* del desarrollador, eso **no** cuenta como
"compartido". Los registros de plazas comunitarias incluyen un identificador interno de cuenta.
Antes de confirmar la excepción de compartición iniciada por el usuario, revisar que cubra tanto
los avisos manuales como la publicación automática y la lectura del identificador por otros usuarios con sesión.

⛔⛔ **Ese «Compartido = No en todo» se acabó el 02-11-2026.** Meta Platforms recibe tres nombres
de evento y el identificador de publicidad del dispositivo, con fin publicitario, y eso es
exactamente lo que Play llama *shared*: ni es un *service provider* nuestro (Meta decide qué hace
con lo que recibe) ni es funcionalidad que inicie el usuario. → **Compartido = Sí** en las dos
filas que lo tocan (App interactions · Device or other IDs), **No** en todas las demás.
[MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]

⚠️ Los dos argumentos de abajo siguen en pie para el resto, y siguen siendo dos, no uno (16-09):
1. **Entre usuarios** — plazas y coche compartido. Unirse a un grupo es un acto explícito y
   reversible (decisión del user, 16-09: *«compartimos si el usuario quiere»*), así que es
   funcionalidad iniciada por el usuario. Pero el coche compartido va **más lejos que una plaza**:
   el grupo ve dónde está el coche AHORA y quién lo dejó. Si el Console pregunta, la respuesta es
   esa, no «solo se comparten plazas anónimas».
2. **Con servicios públicos de datos** — Overpass/OSM, los feeds de precios y el geocodificador.
   Reciben **dónde miras**, nunca quién eres. Medido el 16-09: a Overpass no le llega un recuadro
   aproximado sino `around:$radio,$lat,$lon` — **punto exacto y radio** (`OsmFuelStationParser.kt:40`,
   `OsmPublicParkingParser.kt:53`). No viaja cuenta, identificador ni historial. Es defendible como
   no-compartido, pero la política tiene que describirlo tal cual, sin suavizarlo.
   ⚠️ **Desde la 1.6.0 los parkings ya no van a Overpass, van a NUESTRO servidor**
   (`paparcar.com/api/parkings.json`, función `mergeCell`) con una celda de **3 km**, anónima y
   cacheada por el CDN; Overpass queda de fallback [PARKINGS-ONE-CALL-MERGED-001]. Es el
   desarrollador quien recibe la celda, así que no es «compartido»; sí es ubicación aproximada que
   sale del móvil, ya cubierta por la fila de Location. `mergeCell` registra la celda servida, sin
   identidad: la política §5 ya no dice «no guardamos las consultas en absoluto»
   [PRIVACY-POLICY-MATCHES-DATA-FLOWS-001].

| Tipo (Play) | Qué es en Paparcar | Obligatorio/Opcional | Finalidad a marcar |
|---|---|---|---|
| Location → **Precise location** | Pin de aparcamiento, plazas, ubicación en mapa. **Incluye background**. Desde la 1.2.0, además: (a) **geometría de la ruta** del viaje — se queda en el móvil salvo consentimiento explícito de Plus (`RemoteUserProfileDataSourceImpl.kt:102`); (b) en un **coche compartido**, la ubicación del coche y la sesión espejada viajan al grupo (`sharedCars/{carId}/state/current` y `/sessions/*`), y **la ruta solo si el interruptor del coche está encendido** — son DOS puertas con permiso propio, ninguna sube nada apagada [PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001, `e8baaf35`] | Obligatorio para detección automática (opcional si solo uso manual → marcar "opcional" solo si el flujo manual es real sin permiso). Ruta en la nube y coche compartido: **opcionales** | App functionality, Analytics (diagnóstico de detección) |
| Personal info → **Email address** | Cuenta Firebase Auth | Obligatorio | App functionality, Account management |
| Personal info → **Name** | displayName del proveedor | Obligatorio | Account management |
| Photos → **Photos** | photoUrl del perfil Google (solo URL, no subimos fotos) — declarar como Personal info/other o Photos según criterio; lo defendible: **Personal info → Other info** | Opcional | Account management |
| Health & fitness → **Physical activity** | Activity Recognition + pasos (se procesa en device; el RESUMEN viaja en diagnósticos/armEvidence → se declara) | Obligatorio para el carril Coordinator | App functionality |
| App activity → **App interactions** · ⚠️ **Compartido = Sí** | Eventos de producto (Firebase Analytics): aparcamiento confirmado/cerrado, plaza liberada/avisada. La ubicación viaja SOLO como celda geohash de ~150 m (nunca coordenada — invariante en `FirebaseAnalyticsTracker`, único traductor); sin uid en los params, identificados por el app-instance id de Firebase. Destino: Firebase Analytics (+ export a BigQuery cuando se active en consola); retención GA por defecto (14 meses). [ANALYTICS-EVENTS-001]<br><br>**Y, desde la 1.6.0, a Meta**: `parking_confirmed`, `spot_released` y `paywall_shown` — **sólo el nombre**, sin un solo parámetro (ni celda, ni `detectionPath`, ni `armEvidence`). La lista la decide un `when` exhaustivo en `domain/analytics/AdAttribution.kt`, no un `if` en el sink. Nada sale antes de `hasAcceptedLegalConsent` [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001] — ⚠️ **desde la 1.6.0 la puerta es un consentimiento PROPIO** (`AdMeasurementConsent`): se pregunta una vez tras el login, sea con correo o con Google, con «No permitir» igual de visible que «Permitir», y se revoca en Ajustes → Medición de anuncios. La casilla de «acepto la política» del registro desapareció y su marca vieja no se migra. → En el Console esta fila es **opcional** (el usuario puede negarse) [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] | Obligatorio | Analytics, **Advertising or marketing** |
| App activity → **Other user-generated content** | Vehículos (marca/modelo/matrícula opcional), zonas privadas, plazas avisadas. **Coche compartido**: nombre visible del coche, tu nombre de perfil ante el grupo, código de invitación e historial del grupo (km, tiempo buscando plaza, plaza liberada) — `sharedCars/{carId}` + `members/{uid}` + `sessions/*`. ⛔ El espejo NO tiene campo para matrícula, `armEvidence` ni `detectionPath`: la lista blanca es el TIPO | Opcional (solo si compartes un coche) | App functionality |
| Financial info → **Purchase history** | Estado de la suscripción Plus. La compra la procesa Google Play; RevenueCat recibe **tu uid** como `appUserID` (`RevenueCatEntitlementRepositoryImpl.kt:92`) y el estado del entitlement — nunca ubicación, aparcamientos ni vehículos. ⚠️ Ya declarado en el Console (la app vende en producción); esta fila existe para que la chuleta deje de omitirlo | Opcional | App functionality, Account management |
| App info & performance → **Crash logs** | Crashlytics | Obligatorio | Analytics |
| App info & performance → **Diagnostics** | Telemetría de detección (Firestore `diagnostics/`) + parkdiag | Obligatorio | Analytics |
| Device or other IDs → **Device or other IDs** · ⚠️ **Compartido = Sí** | **Identificador de publicidad (GAID)** — lo recoge el SDK de Meta y SOLO él: el permiso `com.google.android.gms.permission.AD_ID` vuelve al manifest por ese carril, y Firebase Analytics lo sigue teniendo prohibido con `google_analytics_adid_collection_enabled=false`, así que sus eventos siguen anclados al app-instance id [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]. Además: MAC/nombre del BT del coche del usuario; ids de instalación de Firebase (incluye el app-instance id de Analytics); **token de registro FCM/APNs** del dispositivo, guardado en **DOS** sitios: `zoneAlertSubs/{uid}_{zoneId}` —**una fila por zona**, no una por usuario— mientras las alertas de zona estén activas [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001] **y `sharedCars/{carId}/members/{uid}.tokens` mientras compartas un coche** (`functions/src/index.ts:205`) [VEH-SHARED-001] | Opcional (BT, y token: solo con alertas de zona o coche compartido) | App functionality, Analytics |

⛔ **Advertising ID: SÍ se declara desde la 1.6.0, y conviene saber qué NO cambia con ello.**
Hasta la 1.5.0 el manifest borraba los tres permisos con `tools:node="remove"` y esta chuleta decía
lo contrario de lo que dice hoy [ANALYTICS-EVENTS-001]. Vuelven porque sin el GAID Meta no puede
atribuir una instalación al anuncio que la pagó, que es para lo que entra el SDK.

- Lo recoge **un solo carril**, y sólo con el sí a la medición de anuncios: `MetaAdAttribution.activate`.
  Un «no» en Ajustes lo apaga en el momento (`deactivate`), no en el próximo arranque.
  → En el Data Safety, *Device or other IDs* pasa a **opcional** por la misma razón.
  [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001]
- **Firebase Analytics sigue sin tocarlo** (`google_analytics_adid_collection_enabled=false`).
- ⛔ **Y entran TRES permisos, no cinco.** `facebook-core` fusiona sola, sin que nadie la pida, las
  dos APIs de Privacy Sandbox que sirven para **segmentar**: `ACCESS_ADSERVICES_TOPICS` (los
  intereses que el sistema infiere) y `ACCESS_ADSERVICES_CUSTOM_AUDIENCE` (remarketing). Medido en
  el manifiesto fusionado de prodDebug el 02-11-2026. Las dos se **retiran** con
  `tools:node="remove"`: la política declara tres nombres de evento y el identificador de
  publicidad «y nada más», y un binario que puede más de lo que dice el documento es justo el
  defecto que esto viene a cerrar. Lo vigila `AdvertisingDisclosureGuardrailTest`.
- La pregunta **«¿tu app contiene anuncios?» del Play Console sigue siendo NO**, y no es un matiz:
  Paparcar no muestra ni un anuncio dentro. Lo que hay es medición de los anuncios que ponemos
  FUERA. Las dos cosas se declaran en sitios distintos del Console y confundirlas es lo que
  convierte una respuesta honesta en una infracción.
[MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]

**No se recoge** (no declarar): contactos, SMS, archivos, micrófono, cámara, historial web,
información financiera, datos de salud médicos.

## Declaraciones adicionales del Play Console (fuera del Data Safety)

1. **App content → Advertising ID** (declaración propia, FUERA del Data Safety): marcar que la
   app **usa** el identificador de publicidad, y sus finalidades: *Advertising or marketing* +
   *Analytics*. Play cruza esta página con el permiso `AD_ID` del manifest y con la fila de Data
   Safety: si las tres no dicen lo mismo, la publicación se para.
   ⛔ En esa misma sección, **«Does your app contain ads?» → No**. Ver el ⛔ de arriba.
   [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]
2. **Permissions declaration — `ACCESS_BACKGROUND_LOCATION`**: exige vídeo demo del flujo con el
   *prominent disclosure* en la app ANTES del diálogo de permiso, y que la funcionalidad principal
   lo justifique (aquí sí: detección automática). Verificar que el onboarding muestra ese aviso
   destacado — Play lo rechaza si solo está en la política.
3. **Foreground service permissions** (targetSdk 34+): declarar el uso de
   `FOREGROUND_SERVICE_LOCATION` con vídeo, y **nada más** — es el único tipo de FGS que la app usa
   y el único que pide. ✅ Verificado el 16-09-2026 sobre el AAB de la 1.2.0, los dos APK
   (`aapt2 dump permissions`) y los manifiestos fusionados regenerados con `--rerun-tasks`: los
   únicos permisos FGS son `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_LOCATION`.
   `FOREGROUND_SERVICE_DATA_SYNC` salió de nuestro manifiesto en
   DET-SAFETY-NET-FGS-IS-TYPED-DATA-SYNC-001 y **ya no lo reinyecta AGP** desde el bump a 9.4.0
   (con 9.3.2 sí lo hacía, y era ineliminable) — ver
   FGS-DATA-SYNC-IS-NO-LONGER-INJECTED-001. No hay nada que justificar por ese permiso.
4. **Account deletion URL**: obligatoria desde 2024 — apuntar a la política publicada (ancla de la
   sección 6) o página propia.
5. **URL de la política**: ✅ publicada en `https://paparcar.com/privacy-policy` (pública, sin
   login; dominio custom del site pap-26, con `pap-26.web.app` de fallback — no romper la URL
   declarada en el Console). ⚠️ El MCP `firebase_deploy` es no-op: desplegar siempre con la CLI
   (`firebase deploy --only hosting`).

## Cuándo se usa este doc
Al **subir la app al Play Console** (primera vez y cada vez que cambie qué datos se recogen):
abrir este fichero al lado del formulario *App content → Data safety* y copiar las respuestas de
las tablas de arriba. No es un doc público — es la chuleta para que formulario y política digan
lo mismo.

## Pendientes que la política asume como ciertos (verificar antes de publicar)

- [x] «Eliminar cuenta borra … y diagnósticos» — **cerrado**: `DiagnosticsRepository` ya está en
      `userScopedRepos` (`DomainModule.kt:66`), verificado 16-09.
- [x] El barrido se había quedado en cinco repositorios con dos colecciones nuevas fuera
      (`sharedCars/{carId}/members/{uid}` con los tokens dentro, y `zoneAlertSubs/{uid}_{zoneId}`) — **cerrado
      en `2624b7f0`**, que además se lleva las sesiones que firmaste al salir de un grupo. §6 de la
      política ya lo dice. ⛔ **Las reglas nuevas se despliegan ANTES que el APK**: sin ellas el
      barrido de un miembro falla con permiso denegado y §6 vuelve a prometer lo que no hace.
- [x] La traza salía por dos puertas con reglas opuestas — **cerrado en `e8baaf35`**: cada puerta
      pide su permiso (`syncRouteGeometry` para tu historial, `shareRoutesInGroup` para el grupo) y
      ninguna sube nada apagada. El consentimiento de Plus **no** es techo del grupo, a propósito:
      hacerlo techo convertiría compartir rutas en función de Plus.
- [ ] Prominent disclosure de background location: base existente en onboarding/permisos; ajustar
      wording («closed or not in use») y quitar el «never shared with third parties» falso.
      Ticket abierto: `docs/backlog/onb-disclosure-matches-data-safety-001.md`. Con ese flujo se
      graba el vídeo de la Permissions Declaration.
- [x] Enlace de política EN-APP: Ajustes apuntaba al dominio inventado `paparcar.app` → rama
      `bugfix/SET-LINKS-POINT-AT-A-LIVE-POLICY-001-settings-links` (privacy → URL viva, mailto →
      Gmail real; licenses sigue muerta, su fix es AboutLibraries in-app).
- [x] Nombre del responsable y **declaración de trader (DSA)**: hechos y **aceptados por Google**
      (user, 16-09) — la app vende Plus en producción. Esta entrada decía todavía «la primera
      release va sin monetizar»: era el doc el que iba por detrás, no el Console.
- [x] Email de contacto: `support@paparcar.com` (forwarding de Porkbun → Gmail; solo recepción).
      ⚠️ Verificar que el alias del forwarding es exactamente `support@` antes de pegarlo en el
      Console — un alias distinto pierde solicitudes en silencio.
- [x] «Si algún día se activa FCM/push remoto, política y formulario se quedan cortos» — **pasó**,
      dos veces: alertas de zona (15-09) y reparto al grupo (16-09). Ambas filas ya están arriba.
      La advertencia sigue viva para **publicidad**: si entra AdMob, cambian esta chuleta, la
      política y el Advertising ID en la MISMA tarea.
- [ ] Terceros que reciben **dónde miras** y no estaban en la tabla de la política: Italia (MIMIT),
      Alemania (Tankerkönig) y el geocodificador **photon.komoot.io**. Añadidos a
      `privacy-policy.html` el 16-09, **sin desplegar todavía**.
