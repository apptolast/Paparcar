# Play Console — Data Safety form (guía de respuestas)

> Estas respuestas tienen que decir LO MISMO que la política publicada. Si cambia lo que la app
> recoge, se actualizan los dos en la MISMA tarea. Inventario verificado contra el manifest
> prodRelease y los data sources de Firestore el 28-08-2026; ampliado el 08-09-2026 con los
> eventos de producto de Firebase Analytics [ANALYTICS-EVENTS-001]; **re-verificado contra el
> código el 16-09-2026 para la 1.2.0** — coche compartido, push al grupo, rutas y cuatro fuentes
> de datos más [LEGAL-DATA-SAFETY-ANSWERS-FOR-1-2-0-001].
>
> **Política publicada (28-08-2026): <https://paparcar.com/privacy-policy>** — dominio custom
> conectado el 02-09-2026 al site por defecto de pap-26 (`pap-26.web.app` sigue sirviendo lo mismo
> como fallback). Fuente: `hosting/public/privacy-policy.html`, se despliega con
> `firebase deploy --only hosting` (config en `firebase.json`).

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
"compartido"; las plazas visibles para otros usuarios son funcionalidad iniciada por el usuario,
tampoco. → **Compartido = No** en todo.

⚠️ **Ese «No» hay que sostenerlo con dos argumentos distintos, no con uno** (16-09):
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

| Tipo (Play) | Qué es en Paparcar | Obligatorio/Opcional | Finalidad a marcar |
|---|---|---|---|
| Location → **Precise location** | Pin de aparcamiento, plazas, ubicación en mapa. **Incluye background**. Desde la 1.2.0, además: (a) **geometría de la ruta** del viaje — se queda en el móvil salvo consentimiento explícito de Plus (`RemoteUserProfileDataSourceImpl.kt:102`); (b) en un **coche compartido**, la ubicación del coche y la sesión espejada viajan al grupo (`sharedCars/{carId}/state/current` y `/sessions/*`), y **la ruta solo si el interruptor del coche está encendido** — son DOS puertas con permiso propio, ninguna sube nada apagada [PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001, `e8baaf35`] | Obligatorio para detección automática (opcional si solo uso manual → marcar "opcional" solo si el flujo manual es real sin permiso). Ruta en la nube y coche compartido: **opcionales** | App functionality, Analytics (diagnóstico de detección) |
| Personal info → **Email address** | Cuenta Firebase Auth | Obligatorio | App functionality, Account management |
| Personal info → **Name** | displayName del proveedor | Obligatorio | Account management |
| Photos → **Photos** | photoUrl del perfil Google (solo URL, no subimos fotos) — declarar como Personal info/other o Photos según criterio; lo defendible: **Personal info → Other info** | Opcional | Account management |
| Health & fitness → **Physical activity** | Activity Recognition + pasos (se procesa en device; el RESUMEN viaja en diagnósticos/armEvidence → se declara) | Obligatorio para el carril Coordinator | App functionality |
| App activity → **App interactions** | Eventos de producto (Firebase Analytics): aparcamiento confirmado/cerrado, plaza liberada/avisada. La ubicación viaja SOLO como celda geohash de ~150 m (nunca coordenada — invariante en `FirebaseAnalyticsTracker`, único traductor); sin uid en los params, identificados por el app-instance id de Firebase. Destino: Firebase Analytics (+ export a BigQuery cuando se active en consola); retención GA por defecto (14 meses). [ANALYTICS-EVENTS-001] | Obligatorio | Analytics |
| App activity → **Other user-generated content** | Vehículos (marca/modelo/matrícula opcional), zonas privadas, plazas avisadas. **Coche compartido**: nombre visible del coche, tu nombre de perfil ante el grupo, código de invitación e historial del grupo (km, tiempo buscando plaza, plaza liberada) — `sharedCars/{carId}` + `members/{uid}` + `sessions/*`. ⛔ El espejo NO tiene campo para matrícula, `armEvidence` ni `detectionPath`: la lista blanca es el TIPO | Opcional (solo si compartes un coche) | App functionality |
| Financial info → **Purchase history** | Estado de la suscripción Plus. La compra la procesa Google Play; RevenueCat recibe **tu uid** como `appUserID` (`RevenueCatEntitlementRepositoryImpl.kt:92`) y el estado del entitlement — nunca ubicación, aparcamientos ni vehículos. ⚠️ Ya declarado en el Console (la app vende en producción); esta fila existe para que la chuleta deje de omitirlo | Opcional | App functionality, Account management |
| App info & performance → **Crash logs** | Crashlytics | Obligatorio | Analytics |
| App info & performance → **Diagnostics** | Telemetría de detección (Firestore `diagnostics/`) + parkdiag | Obligatorio | Analytics |
| Device or other IDs → **Device or other IDs** | MAC/nombre del BT del coche del usuario; ids de instalación de Firebase (incluye el app-instance id de Analytics); **token de registro FCM/APNs** del dispositivo, guardado en **DOS** sitios: `zoneAlertSubs/{uid}_{zoneId}` —**una fila por zona**, no una por usuario— mientras las alertas de zona estén activas [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001] **y `sharedCars/{carId}/members/{uid}.tokens` mientras compartas un coche** (`functions/src/index.ts:205`) [VEH-SHARED-001] | Opcional (BT, y token: solo con alertas de zona o coche compartido) | App functionality, Analytics |

⛔ **Advertising ID: NO se declara y NO se recoge.** `play-services-measurement` (lo arrastra
Firebase Analytics) fusiona el permiso `com.google.android.gms.permission.AD_ID`; el manifest lo
elimina con `tools:node="remove"` — Paparcar no muestra anuncios. Si algún día entra AdMob, esto
cambia aquí y en la política en la MISMA tarea. [ANALYTICS-EVENTS-001]

**No se recoge** (no declarar): contactos, SMS, archivos, micrófono, cámara, historial web,
información financiera, datos de salud médicos.

## Declaraciones adicionales del Play Console (fuera del Data Safety)

1. **Permissions declaration — `ACCESS_BACKGROUND_LOCATION`**: exige vídeo demo del flujo con el
   *prominent disclosure* en la app ANTES del diálogo de permiso, y que la funcionalidad principal
   lo justifique (aquí sí: detección automática). Verificar que el onboarding muestra ese aviso
   destacado — Play lo rechaza si solo está en la política.
2. **Foreground service permissions** (targetSdk 34+): declarar el uso de
   `FOREGROUND_SERVICE_LOCATION` con vídeo, y **nada más** — es el único tipo de FGS que la app usa
   y el único que pide. ✅ Verificado el 16-09-2026 sobre el AAB de la 1.2.0, los dos APK
   (`aapt2 dump permissions`) y los manifiestos fusionados regenerados con `--rerun-tasks`: los
   únicos permisos FGS son `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_LOCATION`.
   `FOREGROUND_SERVICE_DATA_SYNC` salió de nuestro manifiesto en
   DET-SAFETY-NET-FGS-IS-TYPED-DATA-SYNC-001 y **ya no lo reinyecta AGP** desde el bump a 9.4.0
   (con 9.3.2 sí lo hacía, y era ineliminable) — ver
   FGS-DATA-SYNC-IS-NO-LONGER-INJECTED-001. No hay nada que justificar por ese permiso.
3. **Account deletion URL**: obligatoria desde 2024 — apuntar a la política publicada (ancla de la
   sección 6) o página propia.
4. **URL de la política**: ✅ publicada en `https://paparcar.com/privacy-policy` (pública, sin
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
