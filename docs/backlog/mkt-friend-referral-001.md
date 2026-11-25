# MKT-FRIEND-REFERRAL-001 · Compartir Paparcar con un amigo

**Estado:** ✅ Done · implementación Android validada localmente.

## Problema

El usuario no tiene una acción para compartir un enlace de instalación atribuible a su invitación.

## Señales y contratos

Play Install Referrer ya clasifica antes del login y conserva un resultado pendiente para publicar al iniciar sesión. Ver [canal de instalación](mkt-install-attribution-channel-001.md) y [publicación tras login](mkt-a-referrer-read-before-login-must-survive-001.md).

## Diseño

- Fila en Ajustes y hoja nativa Android. El enlace HTTPS apunta a la ficha de Google Play con un `referrer` codificado que contiene un código de invitación pseudónimo. Play entrega ese valor a la instalación nueva. Para abrir la ficha e instalar basta el mecanismo existente, sin una ruta web nueva ni Android App Links.
- El código es estable por cuenta. Android calcula SHA-256 con un espacio de nombres de Paparcar y usa los primeros 32 caracteres hexadecimales. El enlace lleva ese valor y los UTM fijos, sin incluir el UID original. El código es pseudónimo y no acredita la identidad ni la validez de una invitación.
- El resolver clasifica `friend_referral` y emite el evento existente `install_attribution_resolved` con el canal. En ese carril, `installRawReferrer` guarda sólo el referrer canónico formado por el código validado y los UTM fijos. Un código ausente o inválido deja el canal en `unknown` y no conserva el contenido del enlace etiquetado. La precedencia de un bloque Meta válido se conserva.
- El evento permite contar instalaciones por canal. En el perfil, `installChannel` y `installRawReferrer` permiten agrupar las cuentas atribuidas por código. El perfil guarda el resultado de atribución más reciente de la cuenta, por lo que ese segundo recuento mide cuentas, no un historial de reinstalaciones.
- Android persiste el resultado pendiente y el cierre de lectura en una transacción DataStore. El resolver serializa resolución y publicación para que un login concurrente no duplique el intento. Conserva el pendiente si falla la publicación y lo limpia tras publicarlo.
- Alcance de producto: compartir y contar. Un descuento, días de Plus para una parte o días para ambas exigirían decidir elegibilidad, fraude, coste y revocación antes de diseñar su validación en servidor. Cualquier incentivo requiere decisión explícita de producto.
- iOS pendiente: recepción/atribución en App Store y UX para invitar desde iOS. El enlace de este ticket instala Android.

## Consumidores auditados

- `SettingsScreen` y la navegación de Ajustes usan el UID de la sesión autenticada. Los dos adaptadores de plataforma resuelven la disponibilidad de la acción. Dev Catalog y preview incluyen la fila.
- `ClassifyInstallChannel`, `ResolveInstallAttributionUseCase` y el pendiente existente comparten el contrato del referrer. Se mantienen en paridad DataStore, NSUserDefaults, el fake de pruebas y el fake de mockkit.
- `FirebaseAnalyticsTracker` ya serializa el canal mediante su valor de protocolo. `AdAttribution.kt` excluye el evento de instalación del carril publicitario.
- La política de privacidad local, §2.11 en inglés y español, describe el código y su finalidad. Su publicación está pendiente del proceso de release.

## Criterio de éxito

El selector comparte el enlace localizado. Una instalación de Play conserva el canal y código hasta el login, emite el canal de analítica y preserva los canales existentes. Pruebas de generación, clasificación, entrega diferida y build prod/mock. La entrega real requiere instalación desde Google Play en un dispositivo de prueba.

## Validación local

- `./gradlew testAll :app:assembleProdDebug :app:assembleMockDebug --console=plain --rerun-tasks --offline` terminó con código 0. Se ejecutaron 4.248 pruebas de shared, 4 pruebas del grafo mock y la compilación klib de iOS.
- `node web/build.mjs --check` y `git diff --check` terminaron con código 0.
- La vista previa de Ajustes compila y existe una variante en el Dev Catalog. El render del IDE devolvió `File blocked by aiexclude`. La prueba en el Pixel 8 mock confirmó la fila de Ajustes y la apertura del selector nativo con el enlace. Queda pendiente verificar el referrer real en una instalación de prueba desde Play.
