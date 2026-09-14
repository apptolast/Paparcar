# iOS · fastlane + TestFlight — guía paso a paso

Ticket padre: [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001](../backlog/ios-apple-sign-in-on-baselogin-3-001.md)
(sin iPhone propio: la prueba real la hace un compañero desde TestFlight).

**Regla:** un paso cada vez. Cada paso termina en **Confirmar**; hasta que no llega esa confirmación
no se empieza el siguiente. Un paso se marca ✅ solo cuando está confirmado.

## Referencia: Munición (`~/StudioProjects/Municion/iosApp/fastlane`)

Es la app del equipo con fastlane funcionando para iOS KMP, y comparte con Paparcar **todo lo
que cuesta conseguir**:

| Pieza | Munición | Paparcar |
|---|---|---|
| Team ID | `3NXH5U7C5A` | `3NXH5U7C5A` (el mismo) |
| Repo `match` (certificados cifrados) | `apptolast/ios-certificates` | se reutiliza: el certificado de distribución ya está dentro y se comparte |
| API key de App Store Connect (`.p8`) | key de equipo | se reutiliza: una key de ASC vale para todas las apps del equipo |
| Perfil de App Store | `match AppStore al.ahgitdevelopment.municion` | **nuevo**: `match AppStore com.rndeveloper.paparcar` |
| Lanes | `bootstrap_match`, `match_certificates`, `build`, `beta` | los mismos, adaptados |

**Por qué hoy no sube con la firma automática** (leído en `project.pbxproj`): la configuración
**Release** de Paparcar tiene `CODE_SIGN_STYLE = Manual`, identidad `Apple Development` y
`PROVISIONING_PROFILE_SPECIFIER` vacío. Con eso no se puede exportar para App Store. Munición en
Release usa `Manual` + `Apple Distribution` + el perfil de `match`.

⚠️ **Secretos:** el `.env` de Munición tiene `MATCH_PASSWORD`, la ruta del `.p8` y un PAT de GitHub.
Se copian a `iosApp/fastlane/.env` de Paparcar, que ignora `.gitignore` (`.env`). Nunca al repo, ni a
este documento, ni a un ticket.

⚠️ **Base de datos:** TestFlight sube un build **Release**, y Release apunta a la Firestore de
**producción** (en debug se usa la base de debug, ver `FirestoreDatabaseTest`). Lo que haga el
compañero en TestFlight escribe datos reales.

---

## Paso 1 · Prerrequisitos y decisiones — ✅ (15-09: user decide reutilizar repo `match` y API key de Munición; 1-3 verificados por Claude)

Comprobar y responder:
1. `cd ~/StudioProjects/Municion && bundle exec fastlane --version` funciona (en este Mac ya hay
   `ruby 3.4.9` con rbenv y `fastlane` en el PATH).
2. `gh repo view apptolast/ios-certificates` → tienes acceso (es privado).
3. El `.p8` de la API key de ASC sigue en la ruta que apunta el `.env` de Munición (Dropbox).
4. **Decisión:** ¿reutilizamos el repo `match` y la API key de Munición? (recomendado: sí; lo
   contrario es crear otro certificado de distribución, y Apple solo da 2 por equipo).
5. App Store Connect → la app Paparcar (`6811710268`) existe con bundle `com.rndeveloper.paparcar`
   (✅ ya confirmado).

**Confirmar:** los 5 puntos y la decisión del 4.

## Paso 2 · Ficheros de fastlane (lo hace Claude) — ✅ (15-09, revisado por el user)

- `Gemfile` en la raíz (`gem "fastlane"`), igual que Munición.
- `iosApp/fastlane/Appfile`, `Matchfile`, `Fastfile` y `.env.example`, adaptados de Munición con el
  bundle `com.rndeveloper.paparcar`. Sin lanes de metadata ni de tag/CI por ahora: solo
  `bootstrap_match`, `match_certificates`, `build` y `beta`.
- `iosApp/fastlane/.env` local, con los valores del de Munición (gitignored; se comprueba con
  `git check-ignore` antes de escribirlo).
- `.gitignore`: añadir `*.p8`, `**/fastlane/.env` y `**/fastlane/README.md` (lo regenera fastlane).
- Diferencia deliberada con Munición: el número de build **no** se sube con `increment_build_number`
  (agvtool edita el pbxproj, y Paparcar declara `CURRENT_PROJECT_VERSION` en `Config.xcconfig`, así que
  se quedaría en 1 y ASC rechazaría la segunda subida). Se pasa a xcodebuild como
  `CURRENT_PROJECT_VERSION=<n>`. Munición no lo sufre porque lo tiene duplicado en el pbxproj.
- Verificado: `ruby -c` OK y `bundle exec fastlane lanes` lista las 4 lanes.

**Confirmar:** que has revisado el diff y los ficheros te parecen bien.

## Paso 3 · Crear el perfil de App Store con `match` — ✅ (15-09, lo ejecutó Claude a petición del user)

> Resultado: commit `[fastlane] Updated appstore and platform ios` en `apptolast/ios-certificates`
> (`profiles/appstore/AppStore_com.rndeveloper.paparcar.mobileprovision`). Perfil instalado en
> `~/Library/Developer/Xcode/UserData/Provisioning Profiles/`: nombre `match AppStore com.rndeveloper.paparcar`,
> caduca **07-06-2027**, con `com.apple.developer.applesignin = Default`, `beta-reports-active = true` y
> `get-task-allow = false`. El certificado de distribución compartido no se recreó.

```bash
cd ~/StudioProjects/Gonzalo/PaparcarKMP
bundle install
cd iosApp && bundle exec fastlane ios bootstrap_match
```
- Reutiliza el certificado de distribución del repo y crea el perfil
  `match AppStore com.rndeveloper.paparcar`. Como el App ID ya tiene **Sign in with Apple** (paso 1 de
  la guía de login), el perfil nace con esa capability.
- Te pedirá la passphrase de `match` si no la lee del `.env`.

**Confirmar:** que termina en verde, y pegar las últimas ~20 líneas (sin secretos).

## Paso 4 · Firma de Release en Xcode (lo hace Claude) — ✅ (15-09, confirmado en Xcode sin avisos)

> Aplicado: Release → `CODE_SIGN_IDENTITY = "Apple Distribution"` + `[sdk=iphoneos*] = "iPhone Distribution"`,
> `PROVISIONING_PROFILE_SPECIFIER[sdk=iphoneos*] = "match AppStore com.rndeveloper.paparcar"` (Manual ya estaba).
> `xcodebuild -showBuildSettings -configuration Release -sdk iphoneos` lo resuelve así. Debug intacto.
> `ITSAppUsesNonExemptEncryption = false` añadido al final del `Info.plist` (diff de 2 líneas; `plutil -insert`
> reordena el fichero entero, no usarlo). Los entitlements de Release solo llevan `applesignin`: sin
> `aps-environment`, así que no hay que separar un `.entitlements` de Release como Munición.

En `project.pbxproj`, **solo la configuración Release** del target `iosApp`, como Munición:
`CODE_SIGN_STYLE = Manual`, `CODE_SIGN_IDENTITY[sdk=iphoneos*] = "iPhone Distribution"`,
`PROVISIONING_PROFILE_SPECIFIER[sdk=iphoneos*] = "match AppStore com.rndeveloper.paparcar"`.
Debug se queda en automática para seguir probando en el simulador.
Además, `ITSAppUsesNonExemptEncryption = false` en `Info.plist`: si falta, cada build se queda en
TestFlight esperando la pregunta de cifrado.

**Confirmar:** en Xcode → *Signing & Capabilities* → pestaña **Release**: perfil
`match AppStore com.rndeveloper.paparcar` sin avisos rojos y capability *Sign in with Apple*.

## Paso 5 · Build local sin subir — ⏸️

```bash
cd ~/StudioProjects/Gonzalo/PaparcarKMP/iosApp && bundle exec fastlane ios build
```
Compila el framework KMP en Release, firma y genera el `.ipa`. Aquí salen los errores de firma sin
gastar un número de build.

**Confirmar:** ruta del `.ipa` generado, o el error exacto.

## Paso 6 · Subir a TestFlight — ⏸️

```bash
cd ~/StudioProjects/Gonzalo/PaparcarKMP/iosApp && bundle exec fastlane ios beta
```
El número de build es el último de TestFlight + 1. No hace falta tocar `CURRENT_PROJECT_VERSION`.

**Confirmar:** App Store Connect → Paparcar → **TestFlight**: el build aparece (primero *Processing*,
10–30 min después *Ready to Submit / Testing*).

## Paso 7 · Dar acceso al compañero — ⏸️

1. App Store Connect → **Users and Access** → invitar al compañero (rol *Developer* o *Marketing*
   basta para testing interno). Tiene que aceptar la invitación.
2. TestFlight → **Internal Testing** → crear grupo `Equipo` → añadirle → activar el build.
3. En su iPhone/iPad: instalar la app **TestFlight** → aceptar → instalar Paparcar.

**Confirmar:** que la tiene instalada y arranca.

## Paso 8 · Pruebas en dispositivo real — ⏸️

Las del *Criterio de éxito* del ticket padre (Google, Apple, cerrar sesión y cambiar de cuenta,
borrar una cuenta de Apple). Un build Release **no tiene logs de Napier** (solo en debug): si algo
falla, capturas de pantalla + la hora exacta, y se mira Crashlytics.
