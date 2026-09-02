# Play Console — Data Safety form (guía de respuestas)

> Estas respuestas tienen que decir LO MISMO que la política publicada. Si cambia lo que la app
> recoge, se actualizan los dos en la MISMA tarea. Inventario verificado contra el manifest
> prodRelease y los data sources de Firestore el 28-08-2026.
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

| Tipo (Play) | Qué es en Paparcar | Obligatorio/Opcional | Finalidad a marcar |
|---|---|---|---|
| Location → **Precise location** | Pin de aparcamiento, plazas, ruta, ubicación en mapa. **Incluye background** | Obligatorio para detección automática (opcional si solo uso manual → marcar "opcional" solo si el flujo manual es real sin permiso) | App functionality, Analytics (diagnóstico de detección) |
| Personal info → **Email address** | Cuenta Firebase Auth | Obligatorio | App functionality, Account management |
| Personal info → **Name** | displayName del proveedor | Obligatorio | Account management |
| Photos → **Photos** | photoUrl del perfil Google (solo URL, no subimos fotos) — declarar como Personal info/other o Photos según criterio; lo defendible: **Personal info → Other info** | Opcional | Account management |
| Health & fitness → **Physical activity** | Activity Recognition + pasos (se procesa en device; el RESUMEN viaja en diagnósticos/armEvidence → se declara) | Obligatorio para el carril Coordinator | App functionality |
| App activity → **Other user-generated content** | Vehículos (marca/modelo/matrícula opcional), zonas privadas, plazas avisadas | Opcional | App functionality |
| App info & performance → **Crash logs** | Crashlytics | Obligatorio | Analytics |
| App info & performance → **Diagnostics** | Telemetría de detección (Firestore `diagnostics/`) + parkdiag | Obligatorio | Analytics |
| Device or other IDs → **Device or other IDs** | MAC/nombre del BT del coche del usuario; ids de instalación de Firebase | Opcional (BT) | App functionality |

**No se recoge** (no declarar): contactos, SMS, archivos, micrófono, cámara, historial web,
información financiera, datos de salud médicos.

## Declaraciones adicionales del Play Console (fuera del Data Safety)

1. **Permissions declaration — `ACCESS_BACKGROUND_LOCATION`**: exige vídeo demo del flujo con el
   *prominent disclosure* en la app ANTES del diálogo de permiso, y que la funcionalidad principal
   lo justifique (aquí sí: detección automática). Verificar que el onboarding muestra ese aviso
   destacado — Play lo rechaza si solo está en la política.
2. **Foreground service permissions** (targetSdk 34+): declarar el uso de
   `FOREGROUND_SERVICE_LOCATION` con vídeo. ⚠️ El manifest fusionado lleva también
   `FOREGROUND_SERVICE_DATA_SYNC` inyectado por AGP (no eliminable — ver
   DET-SAFETY-NET-FGS-IS-TYPED-DATA-SYNC-001): habrá que declararlo o justificarlo si el Console
   lo detecta y pregunta.
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

- [ ] «Eliminar cuenta borra … y diagnósticos» — ⛔ verificado 28-08 que HOY es falso:
      `DeleteAccountUseCase` no borra `diagnostics/{uid}` ni `diagnostics_config/{uid}`.
      Ticket abierto: `docs/backlog/account-delete-sweeps-diagnostics-001.md`.
- [ ] Prominent disclosure de background location: base existente en onboarding/permisos; ajustar
      wording («closed or not in use») y quitar el «never shared with third parties» falso.
      Ticket abierto: `docs/backlog/onb-disclosure-matches-data-safety-001.md`. Con ese flujo se
      graba el vídeo de la Permissions Declaration.
- [x] Enlace de política EN-APP: Ajustes apuntaba al dominio inventado `paparcar.app` → rama
      `bugfix/SET-LINKS-POINT-AT-A-LIVE-POLICY-001-settings-links` (privacy → URL viva, mailto →
      Gmail real; licenses sigue muerta, su fix es AboutLibraries in-app).
- [ ] Nombre del responsable: sustituir «Paparcar (desarrollador independiente)» por el nombre
      legal real. **Declaración de trader (DSA)**: la primera release va sin monetizar → puede
      declararse **no-trader**; ANTES de activar cualquier monetización hay que cambiar a
      **trader** (verificación + nombre y dirección legales PÚBLICOS en la ficha de Play).
- [x] Email de contacto: `support@paparcar.com` (forwarding de Porkbun → Gmail; solo recepción).
      ⚠️ Verificar que el alias del forwarding es exactamente `support@` antes de pegarlo en el
      Console — un alias distinto pierde solicitudes en silencio.
- [ ] Si algún día se activa FCM/push remoto o publicidad, política y formulario se quedan cortos.
