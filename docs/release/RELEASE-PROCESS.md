# Paparcar — Release process

> **Doc vivo.** Verificado contra master `e9078c6e2` el **2026-10-26** (`app/build.gradle.kts`,
> `git tag -l`, `docs/release/play-listing/`, `tools/release/`).
>
> ⛔ Este doc **no repite los pasos mecánicos del corte**: los ejecuta `tools/release/cut.py` (§1.0).
> Un procedimiento escrito dos veces se desincroniza, y aquí ya pasó.

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
el usuario la guarda Play. El AAB se compila con el corte (§1.0) y **se sube con `--phases
upload`** a un canal de prueba, en borrador (§1.5); **publicar sigue siendo a mano**, y a
propósito.

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

### 0.5 Credencial de la Play Developer API (para §1.5)

Cuenta de servicio **`paparcar-publisher@pap-26.iam.gserviceaccount.com`**, creada el 27-10-2026.
Su clave JSON vive en `google-application-credentials-play.json` en la raíz del repo, **fuera del
control de versiones** (la tapa el patrón `**/google-application-credentials*.json`). Trátala como
el keystore: con ella se puede subir a tu cuenta de Play.

- Se crea en **Google Cloud → Cuentas de servicio** (sin ningún rol de GCP) y se le genera una clave
  JSON. Los permisos que importan son los de Play.
- ⛔ **Se autoriza invitándola por su correo en Play Console → Usuarios y permisos**, como una
  persona — así está la de RevenueCat. La página «Acceso a la API» solo sirve para vincular el
  proyecto de Cloud, y `pap-26` ya estaba vinculado.
- Permisos dados, y nada más: *ver información de la app* + *administrar versiones en canales de
  prueba*, **solo** sobre Paparcar. Sin permisos de cuenta. Por eso `upload` sube a `internal` y no
  puede tocar producción aunque alguien se lo pida.
- ⚠️ Los cambios de permisos tardan en propagar: un 401 recién hecho no significa que esté mal.

---

## 1. Cortar una release

### 1.0 ⛔ El corte es UN COMANDO, y lo dispara el user

```bash
python tools/release/cut.py 1.6.0          # el corte entero
python tools/release/cut.py 1.6.0 --phases notes   # reanudar una fase suelta
```

Los ocho pasos mecánicos viven en [`tools/release/cut.py`](../../tools/release/cut.py), no en este
doc [RELEASE-THE-CUT-MUST-BE-ONE-COMMAND-001]. **Esta sección ya no los deletrea a propósito**: lo
que un humano reejecuta de memoria se olvida por un paso distinto cada vez, y la prueba de que pasa
estaba aquí mismo — §1.1 decía «hoy va por 14 / 1.3.0» con el árbol en 16 / 1.5.0.

| El comando… | …y el fallo que evita |
|---|---|
| escribe `versionCode`+`versionName` en los **dos** sitios (Gradle y `web/lib/config.mjs`) | dejarse el de la web deja `WebSiteParityGuardrailTest` rojo, y es el único aviso |
| reconstruye la web y avisa **si `hosting/public/` cambió** | desplegar lo de antes, o no desplegar lo que sí cambió |
| `testAll --rerun-tasks` y comprueba que Gradle **no se salvó trabajo** | un guard de Konsist no cambia bytecode: sin `--rerun-tasks` sale UP-TO-DATE y no mira |
| `bundleProdRelease --rerun-tasks` y copia el AAB fuera de `build/` | un bundle incremental miente por megas; y `*.aab` está gitignoreado, el siguiente build lo pisa |
| `keytool -printcert` contra la huella **esperada y versionada** | el fallo del 2026-09-03 (§0.2), que `jar verified.` no podía ver |
| sha256, bytes, entradas y el `versionName` leído **dentro** del bundle, con el anterior ausente | subir un binario de otro árbol |
| valida las notas: 9 locales, cada uno ≤ 500 | que Play las corte sin avisar |
| rellena `docs/backlog/release-<ver>-001.md` con las cifras **medidas** | el número viejo que se cuela al copiar de la terminal |

⛔ **Lo que el comando NO hace, a propósito: subir nada a Play.** Deja el AAB verificado y las
cifras; publicar es irreversible y de cara al público, y es §1.5, a mano.

Y hay tres fases **fuera de la lista por defecto**, que se piden a mano. La primera porque
necesita que el commit del bump EXISTA (commitear es del user); las otras dos porque hablan
con la tienda y viven en §1.5:

```bash
python tools/release/cut.py 1.6.0 --phases tag
```

Las verificaciones están probadas **falsificándolas una a una** —no solo por el camino verde— en
[`tools/release/test_cut.py`](../../tools/release/test_cut.py): `python tools/release/test_cut.py`.

### 1.1 Lo editorial, que ningún script escribe

- **Notas de Play** — `docs/release/play-listing/paste/release-notes-v<versión>.txt`, los 9 locales
  en bloques `<en-US>…</en-US>`. ⛔ El rumano es `<ro>`, **no `ro-RO`**: es un código de Play
  [PLAY-A-LOCALE-CODE-IS-PLAYS-NOT-ANDROIDS-001]. El corte las **valida**, nunca las escribe.
- **Changelog del repo** — [`distribution/release-notes.txt`](../../distribution/release-notes.txt),
  texto plano, la versión vigente.
- **Nombre de la versión para el Console** — 50 caracteres, estilo `1.5.0 (16) - Precios cerca y
  detección más fina`.

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

**El AAB y su verificación de firma son §1.0** — el corte los hace y compara el SHA-1 contra la
huella versionada en `cut.py`, que es la única comprobación que caza el fallo de §0.2.

Lo que sigue a mano es el **APK**, y solo porque el smoke test de §1.4 necesita algo instalable:

```bash
./gradlew :app:assembleProdRelease   # -> app/build/outputs/apk/prod/release/app-prod-release.apk
```

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

```bash
python tools/release/cut.py --phases verify   # qué hay publicado AHORA, y si cuadra con el repo
python tools/release/cut.py --phases upload   # sube el AAB al canal `internal`, en BORRADOR
```

⛔ **Ninguna de las dos está en las fases por defecto**: se piden a mano, porque subir a Play no
puede ser el efecto de teclear el comando del corte [RELEASE-THE-CUT-REACHES-THE-STORE-001]. Y
`upload` deja la versión en **borrador en un canal de prueba**: promover a producción es humano, en
el Console, mirando. Necesitan la credencial de la cuenta de servicio
(`google-application-credentials-play.json` en la raíz, gitignorada; ver §0.5).

📌 **`verify` existe por un fallo real**: el 27-10 la primera lectura de la tienda destapó que 1.5.0
llevaba **13 días publicada** mientras el repo y la memoria la daban por «pendiente de subir». Un
«⏳ pendiente» sobre algo que vive en un sistema externo no se puede creer; ahora se pregunta.

Si lo haces a mano en vez de con el comando, el orden es el mismo:

1. Play Console → **Producción** (o *Prueba interna* si quieres una pasada previa) → nueva versión.
2. Subir el `.aab` de §1.3.
3. Pegar las notas de §1.2, locale por locale.
4. Revisar que **Contenido de la app** siga al día — Data Safety incluido — si la versión cambió qué
   datos se recogen o para qué. Ver [`../legal/DATA-SAFETY-FORM.md`](../legal/DATA-SAFETY-FORM.md).
5. Lanzar. Con rollout por fases, anotar el porcentaje aquí no sirve de nada: mirarlo en Console.

### 1.6 Tag + hosting

La etiqueta es la respuesta a **«qué hay en producción»**, y hacía falta: 1.4.0 se publicó el 13-10
con el árbol del 30-09 y nada en el repo lo decía. La pone el corte, que **se niega** si el bump no
está commiteado o si la etiqueta ya existe:

```bash
python tools/release/cut.py 1.6.0 --phases tag
git push origin v1.6.0
```

⛔ Etiquetas, **no** `develop`/`main`: se evaluó git-flow el 26-10-2026 y la mitad que aporta aquí
—un puntero a lo publicado— la dan las etiquetas sin romper el histórico lineal ni el
`merge --squash` de un commit por ticket. `git describe --tags --abbrev=0` dice qué hay en
producción; un hotfix sale de esa etiqueta. ⚠️ Solo existe `v1.0.0`: de 1.2.0 a 1.5.0 nadie etiquetó.

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
