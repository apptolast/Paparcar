# SETTINGS-LANGUAGE-LIVES-IN-THE-SYSTEM-001 · El idioma lo elige el sistema, no una pantalla nuestra

**Estado:** ✅ Done · en master (squash 29-08-2026)

Sustituye a `settings-language-picker-never-applied-001.md` (el diagnóstico del bug), que se borra:
no vamos a arreglar el selector, lo retiramos.

## Problema

Ajustes → Apariencia → Idioma guardaba la elección y no cambiaba nada. Medido en el Redmi
(Android 13) sobre master `d4ecebc6`, el 29-08-2026:

| Paso | Resultado |
|---|---|
| Elegir *English* en la app | label "English" ✅, `logcat`: `Schedule relaunch activity` ✅ |
| La UI tras recrearse | **sigue en español** ❌ |
| `adb shell cmd locale get-app-locales io.apptolast.paparcar` | `[]` — el sistema no tiene locale de la app |
| `adb shell cmd locale set-app-locales … --locales en` | la app se traduce **entera** al instante ✅ |

**Causa** (leída en el fuente de AppCompat 1.8.0):
`AppCompatDelegate.setApplicationLocales()` resuelve el `LocaleManager` recorriendo
`sActivityDelegates`, lista que **sólo se puebla si existe una `AppCompatActivity`**.
`MainActivity` extiende `ComponentActivity` ⇒ la lista está vacía ⇒
`getLocaleManagerForApplication()` devuelve `null` ⇒ el método **no hace nada, sin log ni
excepción**. La rama de API < 33 muere igual (itera la misma lista), así que estaba roto en **todas**
las versiones de Android. El usuario lo resumió como "nunca me ha funcionado", y tenía razón
literal.

Daño colateral: el bootstrap de `MainActivity.onCreate` que restauraba el idioma al arrancar en
frío (`[BUG-LANG-002]`, *Done 2026-05-26* en el ROADMAP) llamaba al mismo no-op. **Ese fix nunca
funcionó** y el ROADMAP lo daba por bueno.

## Decisión: retirar, no arreglar

Tres razones, en orden de peso:

1. **La mitad difícil no se puede probar.** Arreglar API 33+ es una llamada a `LocaleManager`; la
   parte fea es API 26–32 (`Configuration` + `Locale.setDefault` + recreate + confiar en que CMP
   re-resuelva). **Los dos móviles del banco son Android 13**, así que ese código se escribiría a
   ciegas. Código frágil, no medible, para una función que nadie ha echado de menos en meses.
2. **Un selector de idioma dentro de la app es una trampa sin salida** (observación del usuario, y
   es el mejor argumento): quien elige rumano por error tiene que encontrar la fila "Idioma"
   leyendo rumano. El selector del sistema no tiene ese problema — vive en un sitio conocido, su UI
   está en el idioma del dispositivo y siempre hay camino de vuelta.
3. **Precedente propio**: SETTINGS-AUDIT-REMEDIATION-001 ya borró los toggles que persistían
   preferencias que ningún código leía. Este es el mismo caso un grado peor: no sólo no hacía nada,
   además decía que sí.

## Doctrina violada (por lo que había, no por lo que hacemos)

- Un ajuste que se guarda y no se aplica **miente al usuario** — la doctrina de copy honesto.
- Y una de método: el ticket se cerró en mayo sin comprobarlo en device.
- Al retirar, aplica [[feedback_no_silent_overload]]: **se borra limpio**, sin plegar la conducta en
  otro control ni dejar la preferencia huérfana en el DataStore del contrato.

## Diseño

*El idioma de una app lo administra el sistema operativo. Nuestro trabajo es declarar qué idiomas
hablamos, no construir el selector.*

- **Se declara `res/xml/locales_config.xml`** con los 9 locales + `android:localeConfig` en el
  manifest. Con eso Paparcar aparece en **Ajustes de Android → Idiomas de la aplicación** (API 33+),
  que es donde el sistema entrena a la gente a buscarlo. No es la causa del bug (el sistema acepta
  la locale sin ese fichero, medido), es la mitad que faltaba de la función.
- **Se borra toda la maquinaria propia**: fila de Ajustes, dropdown, intent, efecto, preferencia,
  `LocaleApplier` (los 3 ficheros, iOS incluido) y el bootstrap de `MainActivity`.
- API 26–32 se queda con el idioma del sistema, que es exactamente lo que tiene hoy de facto.
- **`AppEffect` desaparece**: `ApplyLocale` era su ÚNICO caso, así que `AppViewModel` pasa a
  `BaseViewModel<AppState, AppIntent, Nothing>`. `Nothing` hace que el compilador prohíba emitir
  efectos, que es la verdad de este ViewModel; no es una excepción a "cada pantalla con sus tres
  sealed", porque `AppViewModel` no es una pantalla sino el ViewModel raíz.
- iOS coherente: allí el idioma por app también lo da el sistema desde iOS 13, y
  `LocaleApplier.ios.kt` ya era un stub vacío.

## Criterio de éxito

- Ajustes → Apariencia contiene sólo Tema. Ni rastro de idioma en los 9 locales.
- `assembleProdDebug`/`assembleMockDebug` verdes y la suite unitaria entera pasa.
- En el Redmi (Android 13): *Ajustes de Android → Aplicaciones → Paparcar → Idioma* ofrece los 9
  idiomas, y elegir uno traduce la app.
- `docs/ROADMAP.md` deja de afirmar que BUG-LANG-002 está resuelto.

## Verificación hecha (Redmi, Android 13, `prodDebug` instalado con sha256 verificado)

- `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid` + `assembleMockDebug` ✅
- `testProdDebugUnitTest` completo ✅
- `am start -a android.settings.APP_LOCALE_SETTINGS -d package:io.apptolast.paparcar` → **Android
  abre "Idioma de la aplicación" para Paparcar** con *Predeterminado del sistema* marcado y la
  lista de idiomas declarados ✅ (antes de este ticket esa pantalla no tenía nada que ofrecer)
- Elegir *English (United Kingdom)* → `get-app-locales` devuelve `[en-GB]` y **la app arranca
  entera en inglés** ✅ · restaurado a *Predeterminado del sistema* al terminar.
- El diff neto: **+32 / −234 líneas en 26 ficheros**.

## Consumidores auditados

`grep -rl "selectedLanguage\|SetLanguage\|ApplyLocale\|applyAppLocale\|LanguageDropdownRow"` →
15 ficheros, todos cerrados en esta tarea:

| Fichero | Trato |
|---|---|
| `presentation/util/LocaleApplier.kt` / `.android.kt` / `.ios.kt` | **borrados** (3 ficheros) |
| `presentation/app/AppEffect.kt` | **borrado** — `ApplyLocale` era su único caso |
| `presentation/app/AppIntent.kt` | fuera `SetLanguage` |
| `presentation/app/AppState.kt` | fuera `selectedLanguage` |
| `presentation/app/AppViewModel.kt` | fuera la rama, la lectura inicial y el tipo de efecto |
| `App.kt` | fuera el `collect` de efectos y el paso de `selectedLanguage`/`onSetLanguage` |
| `presentation/settings/SettingsScreen.kt` | fuera la fila, `LanguageDropdownRow` y sus 2 params |
| `domain/preferences/AppPreferences.kt` | fuera del contrato |
| `preferences/AndroidDataStoreAppPreferences.kt` | fuera override + key + `LANGUAGE_AUTO` |
| `ios/preferences/IosAppPreferences.kt` | ídem |
| `fakes/FakeAppPreferences.kt` · `fakes/data/repository/FakeOtherDataSources.kt` | ídem |
| `MainActivity.kt` | fuera el bootstrap `[BUG-LANG-002]` + imports AppCompat |
| `composeResources/values*/strings.xml` (9) | fuera 3 keys × 9 locales |
| `docs/ROADMAP.md` | corregida la línea de BUG-LANG-002 |

Nota sobre la clave del DataStore: `selected_language` puede quedar escrita en el fichero de
preferencias de instalaciones existentes. Queda **inerte**, como las otras que dejó
SETTINGS-AUDIT-REMEDIATION-001; ningún código la lee.
