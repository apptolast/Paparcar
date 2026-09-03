# SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001 · el informe de problema lleva la descripción del usuario

**Estado:** ✅ Done (03-09-2026) — mergeado a master por squash; el hash vive en `MEMORY.md`.
2.196 tests verdes · `mock` y `prod` compilan · techo y end-to-end verificados en el Oppo.
⏳ Queda por medir en mano el arreglo del teclado (punto 6) y un diálogo alto sin campo.

## Problema
[SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] dejó el canal de soporte montado: la fila "Informar de un
problema" sube el `parkdiag.log` del device a `diagnostics_reports/{uid}/reports/{createdAtMs}`.
Pero sube **solo evidencia, sin queja**: llega un gzip de hasta ~1 MB (10 MB crudos, horas de
trazas de varios viajes) y ni una palabra sobre qué falló.

Sin descripción, el informe no es accionable:
- No se sabe **qué viaje** mirar. El log cubre todas las sesiones recientes; sin "ayer sobre las
  9:15" hay que leerlas todas.
- No se sabe **qué esperaba el usuario**. Un pin en la calle anterior es un FP de repark para
  nosotros y "la app no funciona" para él; sin su frase no se puede clasificar.
- No se distingue un informe de detección de uno de UI, de sync o de un botón que no responde —
  y para esos tres el `parkdiag` no dice absolutamente nada.

## Doctrina violada
«Fallo asimétrico: ante la duda se PREGUNTA» es la doctrina de detección, y aquí se incumple en el
propio canal de diagnóstico: el sistema recoge evidencia del usuario sin darle jamás la oportunidad
de decir qué le pasó. El informe llega mudo, y el que tiene que adivinar es el que lo lee.

Y el ticket original ya lo dejó escrito sin llegar a cerrarlo: *«el registro de la queja vale por sí
mismo»* — pero hoy no hay queja que registrar, solo un log.

## Señales / datos disponibles
- `DiagnosticsReport` (dominio) + `DiagnosticsReportDto` (cabecera Firestore) ya viajan: `userId`,
  `createdAt`, `deviceModel`, `appVersion`, `osVersion`, `chunkCount`, `gzipBytes`.
- `PapAlertDialog` ya tiene el slot `content` para "un confirm que además CAPTURA algo"
  ([UI-ZONE-MANAGE-001], el nombre de zona) + `primaryEnabled`. El molde existe; no hace falta
  pantalla ni sheet nuevos.
- `PapTextField` es el campo de texto de marca, pero **no sabe de límites**: ni corta ni cuenta.
- Rules `diagnostics_reports/{uid}/{document=**}` ya permiten el campo nuevo (write del dueño sobre
  el doc entero); no hay que redesplegar nada.

## Diseño

### 1 · La descripción va en la CABECERA, no en los chunks
`DiagnosticsReport.message` (dominio) → `DiagnosticsReportDto.message` (Firestore). Tres razones:

1. **Es lo que se lista.** Un `collectionGroup("reports") orderBy createdAt desc` con Admin SDK/MCP
   enseña la queja de todos los uids sin bajar un solo chunk. Dentro del gzip habría que
   reconstruir el log para saber si merece la pena leerlo — justo al revés.
2. **La descripción es el ÍNDICE del log.** Convierte un volcado de horas en una búsqueda acotada.
3. Cero esquema nuevo: mismas rules, mismo barrido de cuenta
   ([ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001] borra `diagnostics_reports/{uid}` entero), misma
   declaración de privacidad.

### 2 · El límite es política de DOMINIO, no un detalle de UI
`DiagnosticsReport.MAX_MESSAGE_CHARS = 500`, en el companion del modelo de dominio (commonMain), y
se aplica **dos veces**:
- en el `TextField`, que simplemente no acepta más (tope duro, sin estado de error: escribir de más
  no es equivocarse);
- en `SendDiagnosticsReportUseCase`, que hace `trim().take(MAX)` antes de subir.

Si el filtro de la UI fuera el único, el día que el reporte se construya por otro camino (iOS, un
worker, un test) no habría tope. El invariante «un mensaje de informe cabe en 500 caracteres» vive
en UN sitio y lo respetan sus dos consumidores.

**Por qué 500**: da para 3–4 frases en móvil (qué esperaba / qué pasó / cuándo), y es lo bastante
corto para que nadie pegue un log dentro — el log ya viaja aparte, por su propio carril.

### 3 · Opcional, nunca bloqueante
El primario sigue habilitado con el campo vacío. Un usuario que no sabe explicarlo sigue mandando
la evidencia, que es lo que no se puede recuperar después. La descripción mejora el informe; no es
su peaje.

### 4 · El contador y el corte viven en `PapTextField`
Parámetro `maxChars: Int? = null` que (a) corta en `onValueChange` y (b) pinta el restante en
`supportingText` **solo cuando aprieta** (últimos `COUNTER_VISIBLE_FROM` caracteres). Un `0/500`
permanente es ruido. Se hace en el componente de marca, no en el call site, para que el siguiente
campo con límite no vuelva a inventárselo.

### 5 · El texto sobrevive al error
`SettingsState.diagnosticsMessage` se limpia al cerrar el diálogo y al éxito, **pero no al fallo**:
perder lo escrito en el reintento es lo que hace que el usuario no reintente.

### 6 · El teclado tapaba las acciones — se arregla en `PapAlertDialog`, no aquí
Medido en el Oppo con el campo lleno: **"Cancelar" caía por debajo del IME** y "Enviar" quedaba a
medias. Es el riesgo de meter un campo en un `BasicAlertDialog`, y se manifestó a la primera.

**Lo que NO funcionó, y por qué importa saberlo:** `Modifier.imePadding()` dentro del diálogo es un
**no-op** — los bounds salieron idénticos byte a byte. Un diálogo tiene su PROPIA ventana, y por
defecto esa ventana "encaja" los system windows ella sola, así que dentro no llega ningún inset de
IME que padear. `verticalScroll` tampoco ayuda por sí solo: sin altura acotada la columna se mide a
su tamaño natural y nunca hay nada que desplazar.

**Lo que sí:** `DialogProperties(decorFitsSystemWindows = false)` en el `BasicAlertDialog`. Existe
en el `DialogProperties` de **commonMain** de CMP 1.12 (verificado compilando, no supuesto). Saca a
la ventana del encaje automático y a partir de ahí `imePadding()` significa algo; el
`verticalScroll` queda de red para cuando el contenido no cabe igualmente (escala de fuente grande).

Va en `PapAlertDialog`, así que lo heredan los **10 ficheros** que lo usan — que es lo correcto: el
que quedó al descubierto fue este diálogo, pero el agujero era del molde.

### 7 · Copy: no es un buzón con respuesta
El diálogo dice que no se responde por ahí y apunta a `support@paparcar.com` (la fila de contacto ya
existe justo al lado). Prometer una conversación que no existe es peor que no ofrecerla.

## Criterio de éxito
- Tests del use case: el mensaje viaja a la cabecera · se trimea · se corta a 500 · vacío no rompe.
- Tests del ViewModel: el intent actualiza el borrador · éxito limpia · **error conserva** ·
  **dismiss conserva**.
- En device: escribir 3 frases, enviar, y que el doc de `diagnostics_reports/{uid}/reports/{id}`
  traiga `message` legible vía MCP.

## Resultado
**2.196 tests verdes, 0 fallos** (`:shared:testDebugUnitTest`, 196 clases), con 5 casos nuevos en
`SendDiagnosticsReportUseCaseTest` (13 en total) y 5 en `SettingsViewModelTest` (38). Compilan
`:app:compileMockDebugKotlin` y `:app:compileProdDebugKotlin` con `--rerun-tasks`.

⚠️ **El corte de la UI no tiene test**: el repo no tiene infraestructura de Compose UI test
(`createComposeRule` / `runComposeUiTest` no aparecen en ningún fuente). Lo que sí está fijado por
tests es el techo que manda —el del dominio, en el use case—; el filtro del `TextField` se verifica
a ojo con la variante "Diagnóstico al límite" de la galería. Es exactamente la razón por la que el
tope se puso en dominio y no solo en el campo.

### Verificado en device (Oppo CPH2371, Android 13)
- **Techo duro**: metidos 520 caracteres por `adb input text`, el campo se queda en **500** exactos
  y el contador dice `Quedan 0 caracteres`. Con 58 caracteres no aparece contador — correcto.
- **End-to-end**: enviado un informe real; llegó a
  `diagnostics_reports/{uid}/reports/1788460455535` con
  `message = "Prueba del campo nuevo: el pin salio en la calle anterior."`, `deviceModel`,
  `appVersion`, `chunkCount=1`, `gzipBytes=3377`. Leído por MCP y **borrado después** (cabecera +
  chunk), junto con un segundo informe de prueba del user.
- **El teclado tapando "Cancelar"**: reproducido y medido (`Cancelar` en y≈1716 con el IME
  empezando en ≈1596) — es el hallazgo que originó el punto 6.

⏳ **Pendiente de ver en mano**: que el arreglo del punto 6 mueva de verdad las acciones por encima
del teclado. Instalado en el Oppo (sha `716062f9…`, device↔local verificado) pero **no medido**: el
user estaba usando el móvil y los taps se pisaban. Conviene mirar también un diálogo alto sin campo
(Eliminar cuenta), porque `decorFitsSystemWindows = false` cambia el encaje de los 10.

## Consumidores auditados
- `FirestoreDiagnosticsReportUploader` — único escritor de la cabecera; campo nuevo en el DTO.
- `DiagnosticsRepositoryImpl` / `DeleteAccountUseCase` — barren `diagnostics_reports/{uid}` por
  path completo, no por lista de campos → **cubierto sin tocar**.
- `FirestoreDeserializerParityTest` — no cubre `DiagnosticsReportDto`: el cliente solo ESCRIBE esta
  cabecera, nunca la deserializa (la lee un humano por MCP). Exento por construcción.
- `SettingsViewModelTest` — constructor sin tocar, pero el flujo crece → tests nuevos.
- `StateGalleryScreen` + `SettingsPreviews` — el diálogo cambia de forma → variantes en paridad.
- `PapTextField` — se le añade un parámetro opcional con default; los call sites existentes no
  cambian de comportamiento.
- Nombre de zona (`AddingZonePeek`) — es un `OutlinedTextField` crudo y **sin límite**; mismo
  agujero, distinto sitio. **Fuera de alcance a propósito**: ver
  `ui-a-captured-name-needs-its-ceiling-001.md`.
- `firestore.rules` — `{document=**}` con write del dueño; un campo más no exige redespliegue.
- Skill `field-test` — fuente de datos que crece → documentado el campo nuevo.
