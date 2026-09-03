# SET-LICENSES-ARE-SHOWN-IN-THE-APP-001 · Las licencias open source se enseñan en la app, no en un 404

**Estado:** ✅ Done (03-09-2026) — mergeado a master por squash; el hash vive en `MEMORY.md`.
Verde en `:shared:testDebugUnitTest` (2188 tests, `--rerun-tasks`), `assembleMockDebug` y
`assembleProdDebug` · ✅ visto en el Oppo (sha device↔local verificado).

## Problema

La fila **"Licencias de código abierto"** de Ajustes no lleva a ninguna parte. La cadena completa,
verificada en master `c06cad27`:

- `SettingsScreen.kt:456-460` — `PapNavRow` → `SettingsIntent.OpenLicenses`
- `SettingsViewModel.kt:174-175` — `sendEffect(SettingsEffect.OpenUrl(LICENSES_URL))`
- `SettingsScreen.kt:223` — `uriHandler.openUri(...)` → navegador
- `SettingsViewModel.kt:238` — `LICENSES_URL = "https://paparcar.com/licenses"`

`hosting/public/` contiene `index.html`, `privacy-policy.html`, `delete-account.html` y
`favicon.svg`. **No hay `licenses.html`: nunca existió.** El usuario sale de la app y aterriza en un
404 de nuestro propio dominio.

Está muerto **a sabiendas** — la constante lo declara:

```kotlin
/** ⚠️ Deliberately dead (404 on our own domain) — the real fix is an in-app licenses
 *  screen (AboutLibraries), tracked as a settings-audit follow-up. */
```

Historial de por qué sigue vivo el agujero:
- **SET-LINKS-POINT-AT-A-LIVE-POLICY-001** (29-08) cableó privacy y contacto a destinos vivos y dejó
  ésta **fuera de alcance a propósito**, por tocar build config.
- **WEB-POLICY-URLS-MOVE-TO-PAPARCAR-COM-001** solo la movió de `paparcar.app` (dominio **sin
  registrar**, que cualquiera podía comprar y servir bajo "nuestro" enlace de licencias) a
  `paparcar.com`. Dejó de ser secuestrable; siguió siendo 404.

Es el último cabo suelto de **SETTINGS-AUDIT-REMEDIATION-001**.

## Doctrina violada

1. **SETTINGS-AUDIT-REMEDIATION-001 — cada control de Ajustes dice la verdad, o se borra.** Una fila
   que promete un documento y entrega un 404 es exactamente el tipo de control que ese ticket barrió.
2. **Atribución legal.** Casi todo el stack es Apache-2.0 (AndroidX, Room, Coil, Ktor, Koin,
   Firebase, Napier, kmp-maps…), y la licencia obliga a **entregar con la distribución** copia de la
   licencia y los avisos. Hoy no se entregan por ninguna vía: ni in-app, ni en web, ni en la ficha.
   La obligación nace con la **primera build pública**, no con la primera queja.
3. **Copy honesto** (`feedback_no_silent_overload`): al quitar un botón se borra limpio; lo que no se
   hace es dejarlo enseñando la nada.

⚠️ **Relación con la release 1.0.0(4) en curso**: la obligación de atribución aplica desde el primer
build público. Decisión del user: retener 1.0 hasta que esto entre, o publicar una página estática
`/licenses` como puente (ticket aparte — ver *Fuera de alcance*).

## Señales / datos disponibles

- **El grafo de dependencias de Gradle ya es la fuente de verdad.** No hay que inventar ni transcribir
  nada: el conjunto resuelto (`libs.versions.toml` → configuraciones resueltas) contiene coordenadas,
  versión y, en la mayoría de los POM, la licencia declarada.
- **Precedente de navegación ya en el código**: `Routes.BT_CONFIG` (`App.kt:113`) + el callback
  `onNavigateToBluetoothConfig` que `App.kt:619` pasa a `SettingsScreen`. Una sub-pantalla de Ajustes
  ya tiene forma canónica en este repo.
- **AboutLibraries 15.2.0** es estable en Maven Central (`com.mikepenz:aboutlibraries-core`, publicado
  el 28-08-2026) y su rama 15.x soporta KMP / Compose Multiplatform.

## Diseño

**El invariante**: *la lista de licencias es una PROYECCIÓN del grafo de dependencias, no un documento
mantenido a mano.* Todo lo demás se deriva de ahí.

1. **Datos generados en build.** Plugin de AboutLibraries sobre `:shared` (+ `:app`), exportando el
   JSON a `composeResources/files/`. La pantalla lo lee con `Res.readBytes(...)` y lo parsea con
   `aboutlibraries-core`. Consecuencia buscada: **subir una versión o añadir una dependencia actualiza
   la lista sin que nadie se acuerde de hacerlo.** Es lo que descarta de plano una página HTML escrita
   a mano — derivado mantenido a mano = desfase silencioso (`project_repo_is_public_no_field_data`:
   versionar la FUENTE, regenerar lo derivado).
2. **⛔ NO usamos su UI.** `aboutlibraries-compose-m3` pinta con `MaterialTheme.typography` y su propia
   paleta: se salta `PaparcarType` y la doctrina de color, y ni siquiera podría pasar
   `TypographyGuardrailTest` si viviera en nuestro código. Consumimos **solo el core (los datos)** y
   pintamos con lo nuestro: `PapOutlinedCard` + `PapListItem`, `rowName` para el nombre de la
   librería, `meta`/`caption` para versión y licencia. Nivel 1 de iconos (`Icons.Rounded.Description`).
3. **Destino de navegación propio, no un diálogo.** `Routes.LICENSES` + `onNavigateToLicenses`,
   espejo exacto de `onNavigateToBluetoothConfig`. Nada de modal sobre modal
   (`feedback_no_modal_over_modal`), y el texto completo de una licencia no cabe en un dialog.
4. **`LICENSES_URL` se RETIRA**, no se reapunta. `SettingsIntent.OpenLicenses` deja de emitir
   `SettingsEffect.OpenUrl` y pasa por el callback de navegación. Una constante muerta que sobrevive
   "por si acaso" es la que vuelve a colarse en la UI.
5. **Huecos de metadata — el riesgo real.** Artefactos cuyo POM no declara licencia salen como
   *Unknown*. **Una lista incompleta sigue siendo una mentira**, así que: revisar el JSON generado,
   completar los huecos por el mecanismo de overrides del plugin, y dejar un **guardarraíl que falle
   si alguna entrada queda sin licencia** — no vale confiar en mirarlo una vez
   (`project_test_a_green_suite_must_prove_it_looked_001`: prohibición sin testigo de población no es
   chequeo; el test debe además probar que la lista no está vacía).
6. **Verificar antes de comprometerse** que el plugin traga Kotlin 2.4.10 / AGP 9.3.2 / Gradle 9.7.1 /
   CMP 1.12. **Plan B si no**: tarea Gradle propia que serialice el dependency set resuelto al mismo
   JSON. Misma pantalla, misma garantía de frescura, sin plugin de terceros — el diseño no depende de
   AboutLibraries, solo se apoya en él para no escribir el extractor.

## Criterio de éxito

1. ✅ Pulsar la fila abre una pantalla **dentro de la app**, **sin red**, con todas las dependencias.
2. **Ninguna entrada sin licencia**, y el guardarraíl falla si aparece una (y si la lista viene vacía).
3. Subir la versión de una dependencia + rebuild → la lista cambia **sin tocar código de UI**.
4. `LICENSES_URL` no existe en el árbol (`grep` limpio).
5. Higiene de la casa, en la MISMA tarea:
   - strings nuevos en los **9 locales** (`values` EN base + 8);
   - `ScreenGroup` en `StateGalleryScreen.kt` en paridad con su `*Previews.kt`;
   - verde en `:shared:testDebugUnitTest`, `assembleMockDebug` y `assembleProdDebug`.

## Lo que se construyó (y lo que hubo que medir por el camino)

El diseño se sostuvo entero, con **cinco correcciones que solo aparecieron al medir** — la última,
ya en el móvil. Cada una está comentada en el sitio del código donde muerde.

**1 · `customDirectory` SUSTITUYE el directorio del source set; no lo añade.** Registrar el
directorio generado en `commonMain` dejó sin resolver de golpe las ~700 keys de strings y todos los
drawables — el `Res` generado se quedó sin accessors. Está en `ResourcesDSL.kt:55`:
`customResourceDirectories[sourceSet.name] ?: default`. Por eso el build **fusiona** (`Sync`) las
fuentes reales y el JSON generado en un árbol único, y es ESE el que ve Compose Resources.
Fusionar, y no registrar el dir generado en `androidMain`/`iosMain` (vacíos hoy), evita que crear
`src/androidMain/composeResources` mañana desactive esto en silencio.

**2 · `fetchRemoteLicense` medido y RECHAZADO.** Parecía obligatorio para cumplir Apache-2.0 §4.
Baja el `LICENSE` de cada repo, así que las **7** licencias distintas se convierten en **18**
entradas con ONCE copias byte-a-byte del mismo Apache-2.0: +100 KB en el APK y una pantalla que
anuncia 18 licencias donde hay 7. Sin él, el mapeo a SPDX ya trae el texto íntegro de las tres
licencias OSS reales (Apache-2.0, MIT, BSD-3-Clause). Las otras cuatro son términos propietarios de
Google (Android SDK, Play ×2, Go): no son redistribuibles ni fetchables, y se enlazan — que es lo
que hace el propio Google.

**3 · El parser de Android se traga los errores.** `Libs.Builder().build()` está escrito sobre
`org.json` en su variante Android (`AndroidParser.kt`) y termina en
`catch (t: Throwable) { Log.e(...) }` devolviendo **lista vacía**. Un fichero corrupto NO llega como
excepción: llega como "no hay librerías". Dos consecuencias: la pantalla trata lista vacía como
error (`failedToLoad || libraries.isEmpty()`), y el guardarraíl corre bajo Robolectric — bajo JVM
pelado, `org.json` es un stub que revienta.

**4 · El plugin metía una SEGUNDA copia del JSON en `res/raw/`** (128 KB) por su vía de Android,
para su propia UI, que no usamos. `prepareLibraryDefinitions*` queda desactivado. Verificado en el
APK: una sola copia, en `assets/composeResources/…/files/aboutlibraries.json`.

**5 · La prosa no puede pasar bajo el reloj, y la excepción se ganó en device.** El scaffold
[UI-TOPBAR-COLLAPSE-001] retira la cabecera **entera** —franja de status bar incluida— y deja que el
cuerpo siga subiendo bajo el reloj. Con filas-tarjeta eso se lee como profundidad. Con 10 000
caracteres de Apache-2.0 corridos, no: los glifos se parten contra el reloj y la batería y el texto
deja de leerse. Primer intento, meter el texto en `PapOutlinedCard`: **no basta** —la tarjeta es más
alta que la pantalla, así que su primera línea acaba igual bajo el reloj—. El arreglo es
`windowInsetsPadding(WindowInsets.statusBars)` en el `LazyColumn` del detalle: **la única pantalla
de la app que se recorta contra la status bar en vez de pasar por debajo**, y la única con un muro
de prosa. La tarjeta se queda igualmente, por superficie. Verificado en el Oppo (`313f5479…`).

**Estado medido hoy:** 282 librerías · 7 licencias · **0 sin licencia** · JSON de 131 KB.

**Guardarraíles, en dos capas y sin duplicar dueño.** En el build,
`library { requireLicense = true }` rompe la compilación si una dependencia no declara licencia. En
tests, `OpenSourceAttributionGuardrailTest` lee **los bytes que se empaquetan** (el árbol fusionado)
con **el parser de la app**, y comprueba **dos** cosas: que el fichero existe y trae ≥100 librerías
(testigo de población — un export roto no puede pasar por éxito), y que toda licencia es legible:
texto propio, o allowlist **razonada** de términos que solo se enlazan (con su enlace). Falsificado
el 03-09 subiendo el suelo a 10 000 y quitando una entrada de la allowlist: ambos caen, y el mensaje
cita las 282 librerías reales.

> Se cayeron tres tests que había al principio, y por qué importa: *"toda librería declara
> licencia"* ya lo impone `requireLicense` en el build —un segundo dueño para un invariante que ya
> tiene uno es ceremonia, no seguridad—; *"ninguna licencia sin texto NI enlace"* se plegó dentro
> del test de legibilidad; y *"toda licencia referenciada está declarada"* no es alcanzable, porque
> el parser resuelve esas referencias al construir el modelo.

**Lo que NO lleva** (recortado a propósito el 03-09, tras verlo en mano):
- **La sección "Librerías con esta licencia"** del detalle. Era agradable y no aportaba nada: quien
  llega ahí venía de una librería concreta y quiere leer SU licencia. Con ella se fue su string en
  los 9 locales y `LicensesState.librariesUnder()`.
- **Buscador** en la lista de 282 filas. Si escuece al usarlo, es una línea de estado y un `filter`.

## Consumidores auditados

- `grep -rn "LICENSES_URL"` sobre `shared/src app/src` → **0**. Retirada, no reapuntada. Queda citada
  en 3 docs de backlog, que son historia y se anotan, no se reescriben.
- `grep -rn "OpenLicenses"` → 3: intent, fila de Ajustes, reducer. El reducer ahora emite
  `SettingsEffect.NavigateToLicenses`; `OpenUrl` sigue vivo para privacidad y contacto.
- `grep -rn "settings_licenses"` → 12 ficheros: 9 `strings.xml` + `SettingsScreen` + las dos
  pantallas nuevas, que **reutilizan la misma key** como título (una fila y su pantalla no pueden
  llamarse distinto).
- `hosting/public/` → **0** referencias a `/licenses`. La web no promete nada que no sirva.
- Galería mock + `LicensesPreviews.kt` → las mismas 5 variantes en ambos sitios.

## Fuera de alcance (a propósito)

- **La página web `/licenses` NO se crea.** El destino es in-app. Si hace falta un puente antes de
  publicar 1.0, es un ticket propio y explícitamente temporal.
- Licencias de **assets** (fuentes, iconografía, mapas) — si Plus Jakarta Sans y compañía necesitan
  aviso propio, sale como follow-up con su ticket.
