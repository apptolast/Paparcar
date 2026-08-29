# SETTINGS-LANGUAGE-PICKER-NEVER-APPLIED-001 · El selector de idioma no cambia el idioma, y nunca lo ha hecho

**Estado:** 🔴 Diagnosticado, SIN implementar · sin rama · causa MEDIDA en device (29-08-2026)

## Problema

Ajustes → Apariencia → Idioma guarda la elección (el label pasa a "English") y la pantalla
parpadea, pero **toda la app sigue en el idioma anterior**. El usuario lo reporta como "nunca me
ha funcionado", y la medición le da la razón: no falla a veces, no funciona nunca, en ninguna
versión de Android.

Medido en el Redmi (2201117TY, Android 13 / API 33) con la build de master `d4ecebc6`:

| Paso | Resultado |
|---|---|
| Ajustes → Idioma → *English* | label "English" ✅, `logcat`: `Schedule relaunch activity: MainActivity` ✅ |
| Toda la UI tras la recreación | **sigue en español** ❌ |
| `adb shell cmd locale get-app-locales io.apptolast.paparcar` | `[]` — **el sistema no tiene ninguna locale de la app** |
| `adb shell cmd locale set-app-locales … --locales en` | la app se traduce **entera** al inglés al instante ✅ |

La última fila es la que cierra el diagnóstico: los 9 locales, Compose Resources y el pipeline de
strings **funcionan perfectamente**. Lo único roto es la llamada con la que la app pide el cambio.

## Causa raíz

`presentation/util/LocaleApplier.android.kt` aplica el idioma con
`AppCompatDelegate.setApplicationLocales(...)`. En AppCompat 1.8.0 ese método es, en API 33+:

```java
public static void setApplicationLocales(@NonNull LocaleListCompat locales) {
    if (Build.VERSION.SDK_INT >= 33) {
        Object localeManager = getLocaleManagerForApplication();
        if (localeManager != null) { …aplica… }      // ← si es null, NO HACE NADA, sin log ni excepción
    } else { … sRequestedAppLocales + applyLocalesToActiveDelegates() … }
}

static Object getLocaleManagerForApplication() {
    for (WeakReference<AppCompatDelegate> activeDelegate : sActivityDelegates) { … }
    return null;                                     // ← lista vacía ⇒ null
}
```

`sActivityDelegates` **sólo se puebla cuando existe un `AppCompatDelegate`**, es decir cuando la
Activity es una `AppCompatActivity`. **`MainActivity` extiende `ComponentActivity`**, así que esa
lista está siempre vacía: `getLocaleManagerForApplication()` devuelve `null` y la llamada se
descarta en silencio. Por eso el sistema reporta `[]`.

La rama de API < 33 muere igual de silenciosamente: `applyLocalesToActiveDelegates()` itera esa
misma lista vacía. **Está roto en todas las versiones de Android**, no sólo en 33+.

Dos consecuencias, no una:
1. **Cambiar el idioma en Ajustes** no hace nada (sólo el `recreate()` posterior, que repinta lo
   mismo — de ahí el parpadeo).
2. **El bootstrap de `MainActivity.onCreate`** que restaura el idioma guardado al arrancar en frío
   (`[BUG-LANG-002]`, "Done 2026-05-26" en el ROADMAP) llama al mismo no-op: **ese fix nunca
   funcionó**. El comentario que lo acompaña ("la única API que actualiza `Locale.getDefault()` en
   todas las versiones") describe algo que en esta app no ocurre.

## Doctrina violada

Ninguna de código; sí una de producto: un ajuste que se guarda y no hace nada miente al usuario.
Y la de diagnóstico — el ticket se dio por cerrado en mayo sin comprobarlo en device.

## Diseño propuesto (a decidir al implementar)

El invariante: *quien manda el idioma es el sistema (API 33+) o la `Configuration` de la app
(<33), y la app debe hablar con ellos directamente, no a través de un delegate que no existe.*

- **API 33+** → `context.getSystemService(LocaleManager::class.java).applicationLocales =
  LocaleList.forLanguageTags(tag)` (vacío para "auto"). El sistema persiste y recrea él solo, así
  que el `ActivityHolder…recreate()` sobra. **Medido: con esto la app se traduce entera.**
- **API 26–32** → aplicar el locale a la `Configuration` de los recursos + `Locale.setDefault` +
  recrear la Activity, o migrar `MainActivity` a `AppCompatActivity` (más invasivo: arrastra tema
  AppCompat) para que la ruta de AppCompat pase a tener delegate.
- **Extra recomendable en el mismo ticket**: `res/xml/locales_config.xml` + `android:localeConfig`
  en el manifest. Sin eso, Paparcar no aparece en *Ajustes de Android → Idiomas de la aplicación*,
  que es donde mucha gente lo busca en Android 13+. No es la causa del bug (el sistema acepta la
  locale sin él, medido), pero es la mitad que falta de la función.
- Revisar de paso el bootstrap de `MainActivity` y el efecto `AppEffect.ApplyLocale`: con la ruta
  del sistema, el bootstrap probablemente sobra (el sistema ya restaura la locale al arrancar).

## Criterio de éxito

- Elegir un idioma en Ajustes traduce la app entera sin reiniciar a mano, en Android 13+ y en un
  device con API < 33.
- `adb shell cmd locale get-app-locales io.apptolast.paparcar` refleja lo elegido.
- Matar y abrir la app conserva el idioma.
- "Automático" vuelve al idioma del sistema.
- ⚠️ El **flavor mock no sirve para probar esto**: su fake de preferencias es en memoria y no
  aplica el override. Verificar en `prodDebug`.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `LocaleApplier.android.kt` → `applyAppLocale` | **la causa** — hay que reescribirlo |
| `MainActivity.onCreate` (bootstrap `[BUG-LANG-002]`) | mismo no-op → revisar / probablemente borrar |
| `AppViewModel.SetLanguage` → `AppEffect.ApplyLocale` → `App.kt:168` | correcto, sólo transporta |
| `AppPreferences.selectedLanguage` (DataStore) | correcto, persiste bien (medido) |
| `LocaleApplier.ios.kt` | fuera de alcance |
| `docs/ROADMAP.md` BUG-LANG-002 "Done 2026-05-26" | **miente**: hay que corregir esa línea |
