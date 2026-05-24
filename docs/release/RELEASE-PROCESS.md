# Paparcar — Release process

Single source of truth para cortar releases firmadas y subir betas a
Firebase App Distribution. Mantener este archivo en sync con la realidad —
si cambias la config de signing/distribution, actualízalo en el mismo PR.

---

## 0. Pre-requisitos (una sola vez)

### 0.1 Keystore release

El keystore vive **fuera** del repo. Por convención local: `keystore/release.jks`
dentro del proyecto (gitignored por `*.jks` en `.gitignore`).

Generar con `keytool`:

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/release.jks \
  -alias paparcar-release \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype JKS
```

Responde a las preguntas (CN, OU, O, L, ST, C). **Guarda contraseñas en un gestor.**
Pierdes el keystore → pierdes el app: Play Store no acepta re-firma con otra key.
Hacer backup en al menos dos sitios offline (USB encriptado + iCloud/1Password).

### 0.2 `local.properties` — credenciales de signing

Añadir (al `local.properties` raíz, **nunca al repo**):

```properties
RELEASE_KEYSTORE_FILE=keystore/release.jks
RELEASE_KEYSTORE_PASSWORD=<store-password>
RELEASE_KEY_ALIAS=paparcar-release
RELEASE_KEY_PASSWORD=<key-password>

# Opcional — solo si vas a subir desde CI:
# APP_DISTRIBUTION_CREDENTIALS_FILE=keystore/firebase-service-account.json
```

Sin estas 4 vars, el build de release genera un APK **sin firmar**
(`composeApp/build.gradle.kts` avisa con un warning explícito).

### 0.3 Firebase App Distribution — auth local

Para subir desde tu máquina (no CI):

```bash
npm install -g firebase-tools
firebase login
```

El plugin de Gradle usa esa sesión. Para CI, descarga un Service Account JSON
con rol "Firebase App Distribution Admin" desde Google Cloud Console y
referéncialo en `APP_DISTRIBUTION_CREDENTIALS_FILE`.

### 0.4 Grupo de testers en Firebase Console

En Firebase Console → App Distribution → Testers & Groups, crea el grupo
`beta-paparcar` y añade los emails de los testers. El plugin lo referencia
por nombre en `firebaseAppDistribution { groups = "beta-paparcar" }`.

---

## 1. Cortar una release

### 1.1 Bump de versión

En `composeApp/build.gradle.kts → defaultConfig`:

- `versionCode` — entero, **siempre incrementar** entre releases (regla Play Store).
- `versionName` — string visible. Convención: `MAJOR.MINOR.PATCH[-beta##]`.
  - Betas: `1.0.0-beta01`, `1.0.0-beta02`…
  - Releases: `1.0.0`, `1.0.1`…

### 1.2 Release notes

Editar `distribution/release-notes.txt` — texto plano, una versión por archivo.
Lo lee el plugin de App Distribution y lo muestra a los testers.

### 1.3 Build firmado

```bash
./gradlew :composeApp:assembleRelease
```

Output: `composeApp/build/outputs/apk/release/composeApp-release.apk`.
Verifica que esté firmado:

```bash
keytool -printcert -jarfile composeApp/build/outputs/apk/release/composeApp-release.apk
```

### 1.4 Smoke test local (manual)

Instalar el APK en un dispositivo real y comprobar:

- App arranca sin crash inmediato (R8 no rompió clases reflexivas).
- Login con Google funciona (Firestore + Auth no minificados de más).
- Aparcar manual + reabrir app → la sesión persiste (Room intacto).
- Lista de zonas se carga (Firestore DTOs no rotos por R8).
- Crashlytics: forzar un crash de prueba → aparece en consola Firebase.

Si algo de esto falla con minify-on y funciona con minify-off, falta un keep
en `proguard-rules.pro`.

### 1.5 Subir a App Distribution

```bash
./gradlew :composeApp:appDistributionUploadRelease
```

Tareas disponibles:
- `appDistributionUploadRelease` — sube el APK firmado al grupo `beta-paparcar`.
- `appDistributionAddTestersRelease` — añade testers ad-hoc por email.
- `appDistributionRemoveTestersRelease` — elimina testers.

Los testers reciben email automático con el link de descarga (requiere
instalar la app "Firebase App Distribution" en su dispositivo Android).

### 1.6 Tag + commit

```bash
git tag -a v1.0.0-beta01 -m "Release beta01"
git push origin v1.0.0-beta01
```

---

## 2. Troubleshooting

### "Release build is UNSIGNED"

Faltan vars en `local.properties` (ver §0.2). El warning aparece al iniciar
el build. Reproducible: borra una key, vuelve a correr — verás el warning.

### Crash en release que no aparece en debug

Casi seguro R8 minificando algo reflexivo. Pasos:
1. `./gradlew :composeApp:assembleRelease --no-daemon -Pandroid.enableR8.fullMode=false`
2. Si el crash desaparece → R8 fullMode estaba siendo agresivo. Añade keeps.
3. Si persiste → mira el stack trace (Crashlytics dashboard) y añade un keep
   para la clase mencionada en `proguard-rules.pro`.

### Service Account JSON 401 en CI

El JSON necesita el rol **"Firebase App Distribution Admin"** + el proyecto
debe tener "Firebase App Distribution API" habilitada en Google Cloud Console.
