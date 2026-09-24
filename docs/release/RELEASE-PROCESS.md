# Paparcar — Release process

> **Doc vivo.** Verificado contra master `ad101968a` el **2026-09-24** (`app/build.gradle.kts`,
> `:app:tasks --all`, `git tag -l`, `docs/release/play-listing/`).

Single source of truth para cortar una release firmada y publicarla en **Google Play**. Mantener
este archivo en sync con la realidad — si cambias la config de signing o el flujo de publicación,
actualízalo en el mismo PR.

⛔ **El canal es Play, y solo Play.** Este doc describió durante meses un carril de betas por
**Firebase App Distribution** (grupos `alpha-paparcar` / `beta-paparcar`, ramas `alpha` y `beta`).
Ese carril nunca llegó a usarse: **las ramas `alpha` y `beta` no existen** —ni locales ni en
`origin`— así que sus dos workflows no han disparado una sola vez. Lo que llega a un móvil ajeno
llega por la tienda; lo que llega a los nuestros, por `/run` (ver `.claude/skills/instalar-apk`).
El plugin, los dos workflows y su config se retiraron del árbol en
[BUILD-A-LANE-WITH-NO-BRANCH-IS-NOT-A-LANE-001]; esta nota es lo único que queda, para que no vuelva
a entrar por inercia.

---

## 0. Pre-requisitos (una sola vez)

### 0.1 Keystore release

El keystore vive en `keystore/release.jks`, **dentro** del árbol del proyecto pero **fuera del
control de versiones** (lo tapan `*.jks` y `keystore/` en `.gitignore`). Esta frase decía antes
"vive fuera del repo" y luego daba una ruta de dentro: se contradecía, y es la clase de ambigüedad
que acaba en dos keystores (ver el aviso de §0.2).

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
Pierdes el keystore → pierdes la app: Play no acepta re-firma con otra key.
Hacer backup en al menos dos sitios offline (USB encriptado + gestor de contraseñas).

> ⚠️ El `keytool` de JBR 25 **crashea con locale español** (`MissingFormatArgumentException` en
> `-list -v`): invocarlo con `-J-Duser.language=en`.

### 0.2 `keystore.properties` — credenciales de signing

⛔ **Van en `keystore.properties`, NO en `local.properties`.** `app/build.gradle.kts` resuelve cada
clave en este orden:

```kotlin
keystoreProps.getProperty(key)      // keystore.properties  ← GANA
    ?: localProps.getProperty(key)  // local.properties     ← solo si falta arriba
    ?: System.getenv(key)           // CI
```

Si las pones en `local.properties` y existe un `keystore.properties` con valores viejos, el build
firma con el **keystore viejo sin avisar**. Pasó el 2026-09-03: la upload key nueva se configuró en
`local.properties`, su SHA-1 se registró en Firebase, y el AAB salió firmado con un keystore de mayo.
No se detecta mirando el build — `jar verified.` sale verde con cualquier keystore válido. La única
comprobación que sirve es la de §1.3.

Regla: las 4 claves de firma existen **en un solo fichero**. Si alguna vez aparecen en los dos,
sobra la de `local.properties`.

Añadir (al `keystore.properties` raíz, **nunca al repo**):

```properties
RELEASE_KEYSTORE_FILE=keystore/release.jks
RELEASE_KEYSTORE_PASSWORD=<store-password>
RELEASE_KEY_ALIAS=paparcar-release
RELEASE_KEY_PASSWORD=<key-password>
```

Sin estas 4 vars, el build de release genera un artefacto **sin firmar**
(`app/build.gradle.kts` avisa con un warning explícito).

### 0.3 Play Console

La app existe en Play Console con el `applicationId` `com.rndeveloper.paparcar` y **Play App
Signing** activo: lo que subimos es la *upload key* de §0.1; la clave con la que Google firma para
el usuario la guarda Play. El AAB **se compila y se sube a mano** — no hay lane automatizada.

La ficha (título, descripciones, capturas, gráfico) se mantiene en los 9 idiomas; el procedimiento
para revisarla y rellenarla campo por campo está en
[`PROMPT-SACAR-VERSION-A-PRODUCCION.md`](./PROMPT-SACAR-VERSION-A-PRODUCCION.md), y el copy
versionado en [`play-listing/`](./play-listing/).

### 0.4 Huellas de la upload key en Firebase

El SHA-1 **y** el SHA-256 de la upload key tienen que estar dados de alta en la app Android del
proyecto Firebase, o **Google Sign-In deja de funcionar en el build firmado** aunque todo lo demás
compile. Se leen con:

```bash
keytool -J-Duser.language=en -list -v -keystore keystore/release.jks -alias paparcar-release
```

⛔ Y hay una segunda huella que registrar: la que **Play App Signing** genera al firmar para el
usuario (Play Console → Integridad de la app). **No es la upload key**, y sin ella el login falla
solo en lo descargado de la tienda, que es justo donde no estás mirando. Esa misma huella va
además a la restricción de la **Maps API key** en GCP (paquete + SHA-1), o el mapa sale en blanco
con el mismo patrón: bien en tu APK, muerto en el de Play. Detalle en
[`RELEASE-SECURITY.md`](./RELEASE-SECURITY.md).

---

## 1. Cortar una release

### 1.0 La suite, antes de nada

```bash
./gradlew testAll
```

⛔ **`testAll`, nunca `:shared:testDebugUnitTest`**: esa deja fuera el verify del grafo del flavor
`mock` (vive en `:app`) y la compilación del klib de iOS
[TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001]. Y si tocaste algo que solo vigila un guard de
Konsist (imports, color, tipografía, copy, locales, web), `--rerun-tasks`: un import no cambia el
bytecode, así que Gradle da el test por UP-TO-DATE y no llega a ejecutarlo.

### 1.1 Bump de versión

En `app/build.gradle.kts → defaultConfig`:

- `versionCode` — entero, **siempre incrementar** entre releases (regla de Play). Hoy va por **14**.
- `versionName` — string visible, `MAJOR.MINOR.PATCH`. Hoy, **1.3.0**.

### 1.2 Release notes

Dos ficheros, dos destinatarios:

- **Play** — `docs/release/play-listing/paste/release-notes-v<versión>.txt`, con los 9 locales en
  bloques `<en-US>…</en-US>`. ⛔ **Tope de 500 caracteres por locale**, y Play corta sin avisar:
  medirlos uno a uno antes de pegar.
- **Changelog del repo** — [`distribution/release-notes.txt`](../../distribution/release-notes.txt),
  texto plano, la versión vigente.

### 1.2b Reglas, índices y funciones — ANTES del binario

⛔ **El orden es reglas e índices → funciones → verificación → app → hosting**, y la verificación no
es opcional: el 16-09 se desplegó una consulta sin su índice compuesto y **ningún test lo vio** —
los de `functions` prueban lógica pura y **el emulador de Firestore no exige índices**. Habría
dejado sin alertas de zona a todo el mundo, en silencio.

```bash
npm --prefix firestore-rules-tests test      # las reglas se EJECUTAN, no se leen
firebase deploy --only firestore:rules,firestore:indexes --project pap-26
# esperar a que los índices estén READY (uno recién creado tarda, y hasta entonces falla igual)
firebase deploy --only functions --project pap-26
npm --prefix functions run verify:queries    # contra la base REAL: la única que sabe la verdad
```

Y **releer** lo desplegado —reglas e índices— en vez de fiarse del `success` del deploy.

⛔ Si la app sale antes que las reglas, un barrido de sesiones falla con permiso denegado, el
`runCatching` se lo traga **a propósito** (no puede bloquear el borrado de cuenta) y la política de
privacidad vuelve a prometer lo que el código no hace.

### 1.3 Build firmado

```bash
./gradlew :app:bundleProdRelease     # AAB — es lo que sube a Play
./gradlew :app:assembleProdRelease   # APK — solo para el smoke test de §1.4
```

Salidas: `app/build/outputs/bundle/prodRelease/app-prod-release.aab` y
`app/build/outputs/apk/prod/release/app-prod-release.apk`.

Verifica **con qué keystore** se firmó — no que esté firmado, sino cuál:

```bash
keytool -J-Duser.language=en -printcert -jarfile app/build/outputs/apk/prod/release/app-prod-release.apk
```

El SHA-1 que imprima tiene que ser el de §0.4. Es la única comprobación que caza el fallo de §0.2.

⛔ **El mapping de R8 se sube solo, y así tiene que quedarse**
[RELEASE-A-CRASH-WITHOUT-A-MAPPING-IS-UNREADABLE-001]. Estuvo al revés —apagado salvo que pasaras
un flag— y nadie recuerda un flag en cada release: la 1.0.0 salió a Play el 03-09-2026 y **todos los
crashes de producción desde entonces llegaron ofuscados**. R8 no renombra igual en dos ejecuciones,
así que un mapping solo vale para SU build: no hay ponerse al día después. La vía de escape existe
para un release que no va a ningún móvil (medir R8, reproducir un bug minificado) y cuesta escribirla:

```bash
./gradlew :app:bundleProdRelease -PskipCrashlyticsMapping=true
```

Que el upload falle **tumba el build a propósito**: una release que no se puede diagnosticar es
mejor descubrirla aquí.

### 1.4 Smoke test local (manual)

Instalar el **APK prodRelease** en un dispositivo real y comprobar:

- App arranca sin crash inmediato (R8 no rompió clases reflexivas).
- Login con Google funciona (Auth + Firestore no minificados de más, huellas de §0.4 puestas).
- Aparcar manual + reabrir app → la sesión persiste (Room intacto).
- Lista de zonas se carga (DTOs de Firestore no rotos por R8).
- Crashlytics: forzar un crash de prueba → aparece en consola con el stack **desofuscado**.

Si algo de esto falla con minify-on y funciona con minify-off, falta un keep en
`proguard-rules.pro`.

### 1.5 Subir a Play

1. Play Console → **Producción** (o *Prueba interna* si quieres una pasada previa) → nueva versión.
2. Subir el `.aab` de §1.3.
3. Pegar las notas de §1.2, locale por locale.
4. Revisar que **Contenido de la app** siga al día — Data Safety incluido — si la versión cambió qué
   datos se recogen o para qué. Ver [`../legal/DATA-SAFETY-FORM.md`](../legal/DATA-SAFETY-FORM.md).
5. Lanzar. Con rollout por fases, anotar el porcentaje aquí no sirve de nada: mirarlo en Console.

### 1.6 Tag + hosting

```bash
git tag -a v1.3.0 -m "Release 1.3.0"
git push origin v1.3.0
```

Y si la versión cambió algo que la web nombra (límites del plan free, `versionName`, una feature,
la política), **reconstruir y desplegar** — la web es un consumidor más de la verdad del producto
[WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001]:

```bash
node web/build.mjs --check && node web/build.mjs
firebase deploy --only hosting --project pap-26
```

---

## 2. Troubleshooting

### "Release build is UNSIGNED"

Faltan vars en `keystore.properties` (ver §0.2). El warning aparece al iniciar el build.
Reproducible: borra una key, vuelve a correr — verás el warning.

### El login con Google falla solo en el build de la tienda

Falta la huella de **Play App Signing** en Firebase (§0.4). En el APK que firmas tú funciona,
porque esa huella sí está: son dos claves distintas y hacen falta las dos.

### Crash en release que no aparece en debug

Casi seguro R8 minificando algo reflexivo. Pasos:
1. `./gradlew :app:assembleProdRelease --no-daemon -Pandroid.enableR8.fullMode=false`
2. Si el crash desaparece → R8 fullMode estaba siendo agresivo. Añade keeps.
3. Si persiste → mira el stack trace (Crashlytics) y añade un keep para la clase mencionada en
   `proguard-rules.pro`.

### El build muere subiendo el mapping a Crashlytics

Es la red, no el build: el upload sale a Firebase y una inspección SSL de por medio lo tumba con un
PKIX error. Se arregla metiendo la root CA del antivirus en el truststore del JDK que usa Gradle
—no apagando el upload—. Si necesitas ese build YA y no va a ningún móvil, §1.3 tiene la vía de
escape.

### Un stack trace de producción llega ofuscado

El mapping de ESA build no se subió. No se puede arreglar a posteriori: R8 no renombra igual dos
veces. Comprobar que nadie ha dejado `-PskipCrashlyticsMapping=true` en un alias o en CI.
