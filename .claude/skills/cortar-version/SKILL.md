---
name: cortar-version
description: Sacar una versión de Paparcar a Google Play — preflight contra la tienda, notas en 9 idiomas, el corte con tools/release/cut.py de una sola tanda en Windows, y una parada con permiso explícito antes de cada paso irreversible (commit, etiqueta, subida al canal interno, despliegues, push, ficha). Usar cuando el user diga "saca la versión", "corta la 1.7.0", "release", "sube a Play", "¿qué hay publicado?", o cuando haya que mover la ficha de Play a un build recién publicado. NO usar para instalar en los móviles (eso es instalar-apk) ni para cambiar el propio cut.py (eso es un ticket normal con nuevo-ticket).
---

# Cortar una versión

⛔ **Esta skill orquesta; no implementa.** Los pasos mecánicos son `tools/release/cut.py` y el
procedimiento es [`docs/release/RELEASE-PROCESS.md`](../../../docs/release/RELEASE-PROCESS.md). Si
algo de aquí contradice a esos dos, **ganan ellos**, y se corrige esta skill. Si hace falta una
comprobación nueva, va a `cut.py` (con su test en `test_cut.py`), nunca solo aquí: Codex convive en
el repo y no ejecuta skills de Claude.

> `docs/release/PROMPT-SACAR-VERSION-A-PRODUCCION.md` **no es este flujo**: es un prompt para
> claude.ai con el conector del Console, y sus datos son de la 1.1.0.

## ⛔ Invariantes

- **El corte lo dispara el user.** Nunca por un merge, un hook o un evento.
- **Cada paso irreversible lleva su propio «sí» en ese momento**: commit, etiqueta, `upload`,
  `listing`, despliegues de Firebase y push. Un «sí, saca la versión» al principio autoriza
  preparar, **no** autoriza los seis. Ver §4.
- **Producción no la toca la skill.** `upload` deja la versión en **borrador en `internal`**.
  Publicar y promover se hace en el Console, a mano. La cuenta de servicio tampoco tiene permiso
  para más.
- **Un estado de la tienda no se recuerda, se pregunta.** `--phases verify` antes de afirmar qué
  hay publicado. La 1.5.0 estuvo 13 días publicada mientras repo y memoria la daban por pendiente.

## 0 · Entorno: Git Bash, con dos variables

Desde **PowerShell** el corte no corre: no encuentra `bash` (fase `suite`) ni `openssl` (`verify`,
`upload`, `listing`). Todo va por la herramienta **Bash**, con:

```bash
export JAVA_TOOL_OPTIONS='-Duser.language=en'   # keytool -printcert revienta con locale español
```

⚠️ En 1.6.0, desde Git Bash, `notes` contó las notas **largas** (Python leyó con otra
codificación) y `notes` + `doc` se corrieron desde PowerShell. `cut.py` lee con `encoding="utf-8"`
explícito, así que la causa no está cerrada. Si vuelve a pasar, probar primero `PYTHONUTF8=1`,
luego PowerShell para esas dos fases, y **anotar lo que funcionó en esta sección**.

## 1 · Worktree y preflight (solo lectura)

El corte escribe ficheros (bump, notas, doc del release), así que va en **su propio worktree**,
como cualquier tarea (`nuevo-ticket` A.1). Rama `chore/RELEASE-<x-y-z>-001-cut`. Los ficheros
gitignored que necesita son **cinco**:

```bash
git worktree add -b chore/RELEASE-1-7-0-001-cut ../Paparcar-release master
cp local.properties keystore.properties google-application-credentials-play.json ../Paparcar-release/
cp app/google-services.json ../Paparcar-release/app/
cp -r keystore ../Paparcar-release/
```

`keystore.properties` sin `keystore/` hace que el build falle en vez de salir sin firmar. Sin
`FACEBOOK_APP_ID` en `local.properties`, la fase `bundle` rechaza el AAB, y está bien que lo haga.

Desde el worktree:

```bash
python tools/release/cut.py --phases verify     # qué hay en cada canal y si la ficha viva = paste/
git describe --tags --abbrev=0                   # la última versión etiquetada
git log --oneline v<anterior>..master            # lo que entra
git diff --stat v<anterior>..master -- firestore.rules firestore.indexes.json functions/
```

- `verify` en rojo porque **la tienda va por delante del repo** → parar y contarlo al user.
- Un borrador ya en `internal` → preguntar qué hacer con él antes de subir otro.
- Si el `diff` de reglas, índices o funciones no está vacío, este corte lleva despliegue de
  Firebase **antes** del binario (§3).
- Proponer el número (semver a partir del log) y **dejar que el user lo elija**.

## 2 · Lo editorial, ANTES del corte

Lo escribe Claude, lo aprueba el user. El script solo lo valida.

1. **Notas de Play**: `docs/release/play-listing/paste/release-notes-v<ver>.txt`, nueve bloques
   `<en-US>…</en-US>`. ⛔ El rumano es `<ro>`, no `<ro-RO>`.
   - Se redactan desde el `git log`: **lo que nota el usuario**, causa y consecuencia, sin mecánica
     interna ni nombres de componentes. Se respeta el vocabulario plaza/aparcamiento/car park de
     `CLAUDE.md`.
   - Se enseñan **ES y EN primero**. Con el OK, los otros siete.
   - Tope 500 por idioma, y se mide al escribir. Los que más se acercan son `fr-FR` e `it-IT`.
2. **Changelog del repo**: `distribution/release-notes.txt`.
3. **Nombre de versión para el Console**, ≤50: `1.6.0 (17) - <lo principal>`.
4. **¿La ficha tiene que cambiar?** La ficha describe el **build publicado**. Si este build cambia
   lo que promete, se prepara `LISTING-v<ver>.md` ahora, pero `published-listing.txt` **no se
   mueve** hasta que Play publique (§5). Si el build lleva una red publicitaria, `bump` exige que
   esa ficha la nombre en los nueve idiomas. Antes de escribir, medir en FR y DE, que son los que
   van al borde de 4000.

## 3 · El corte

```bash
python tools/release/cut.py <ver>
```

Por defecto corre `bump, web, suite, bundle, sign, measure, notes, doc`.

- **Reanudar con la lista de lo que falta**, nunca con el comando entero. `bump` ya hecho falla con
  «es la versión que ya hay escrita». Ejemplo: `--phases suite,bundle,sign,measure,notes,doc`.
- ⛔ **`notes` valida las notas de la versión que dice Gradle**, no la del argumento. Ejecutada
  sola **antes** del bump, da verde con las notas de la versión anterior.
- Si `web` dice que `hosting/public/` cambió, este corte necesita desplegar la web (§4).
- Al terminar, el AAB está en `../paparcar-<ver>-vc<N>.aab` y las cifras en
  `docs/backlog/release-<x-y-z>-001.md`. Si ese doc se creó nuevo, rellenar su «Problema» con lo
  que entra.
- **Smoke test** (`RELEASE-PROCESS.md` §1.4): se propone, no se impone. Un móvil con el build de
  Play no acepta un APK nuestro (`instalar-apk` §1).

## 4 · Las paradas, en orden

Cada una se pide por separado, diciendo **qué cambia fuera** si el user dice que sí:

| # | Paso | Comando | Qué cambia fuera |
|---|---|---|---|
| a | commit del corte → master | `--squash` según `nuevo-ticket` C.2 | el historial de master |
| b | etiqueta | `python ../Paparcar/tools/release/cut.py <ver> --phases tag` | nada hasta el push. Se niega si el bump no está en HEAD |
| c | reglas, índices y funciones (solo si §1 lo dijo) | `RELEASE-PROCESS.md` §1.2b, en su orden y **releyendo** lo desplegado | producción de Firebase |
| d | subir a Play | `python tools/release/cut.py --phases upload` | borrador en `internal` |
| e | web (solo si `web` lo dijo) | `firebase deploy --only hosting --project pap-26` | paparcar.com |
| f | push | `git push origin master v<ver>` | GitHub |

- **(b) va sobre MASTER, no sobre la rama.** El `--squash` crea un commit nuevo, así que la
  etiqueta puesta en la rama se quedaría colgando. `cut.py` resuelve el repo desde su propia ruta:
  se ejecuta **el del árbol principal**, tras el merge. Etiquetar no toca el índice.
- **(d) puede agotar el timeout de la herramienta y haber llegado igual.** Antes de reintentar,
  `--phases verify`. Si `internal` ya tiene el `versionCode`, **no se reintenta**.
- **(e)** El guard `predeploy` aborta si faltan las celdas de parkings (gitignored). Se arregla
  **espejando lo publicado**; no se regenera desde OSM. Está en la memoria de 1.6.0.
- Lo que queda del user, y se le dice tal cual: publicar el borrador de `internal` y promoverlo a
  producción en el Console, **Data Safety** si la versión cambió qué datos se recogen o para qué
  (`docs/legal/DATA-SAFETY-FORM.md`), y pegar el nombre de versión.

## 5 · Cuando Play ya lo ha publicado (otro día, normalmente)

```bash
python tools/release/cut.py --phases verify            # production debe decir el vc nuevo
```

Solo con eso en verde, y solo si en §2.4 se preparó ficha nueva:

1. Apuntar `docs/release/play-listing/published-listing.txt` a `LISTING-v<ver>.md`.
2. `python docs/release/play-listing/tools/make_paste.py` (⛔ `paste/` nunca se edita a mano).
3. **Pedir permiso** y ejecutar `python tools/release/cut.py --phases listing`. Sube texto, no
   capturas, y no cancela una revisión en curso.
4. `--phases verify` otra vez: tiene que decir «cuadran».

Esto es un commit propio, con su ticket.

## 6 · Cerrar

- `docs/backlog/release-<x-y-z>-001.md` → ✅ Done, con lo pendiente del user escrito en su
  criterio de éxito. **Recontar `docs/backlog/README.md`**: el guard lo compara.
- En `MEMORY.md`, la línea del release: hash de master, etiqueta, el estado leído de `verify` y lo
  pendiente del user.
- **Si algo de §0-§5 no funcionó como dice aquí, se corrige aquí, en la misma tarea.** La receta
  de Windows de 1.6.0 vivió solo en memoria, y por eso este fichero existe.
