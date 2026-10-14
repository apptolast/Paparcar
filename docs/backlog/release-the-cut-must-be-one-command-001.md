# RELEASE-THE-CUT-MUST-BE-ONE-COMMAND-001 · el corte de una versión cabe en un comando

**Estado:** 🔵 **En progreso** — diseño escrito, implementación **bloqueada por una causa externa**
(ver §Bloqueo). Abierto el 14-10-2026 desde `RELEASE-1-5-0-001`.

## Problema

Cortar una versión son hoy **nueve pasos a mano**, y el orden importa: si te saltas uno, o lo haces
en el orden equivocado, lo que se sube no es lo que crees. Medido cortando 1.5.0:

1. `versionCode` y `versionName` en `app/build.gradle.kts`
2. `APP_VERSION` en `web/lib/config.mjs` — **si falta, la suite se pone roja** y es el único aviso
3. `node web/build.mjs`
4. `./gradlew testAll --rerun-tasks` — sin `--rerun-tasks` los guards de Konsist salen UP-TO-DATE
5. `./gradlew :app:bundleProdRelease --rerun-tasks` — sin él, la medida de tamaño miente por megas
6. copiar el AAB fuera de `build/` con el nombre de la versión
7. `keytool -printcert -jarfile` — ⛔ y **no** `jarsigner -verify`, que da verde con el certificado
   equivocado
8. sha256, tamaño, entradas, y leer el `versionName` del manifest del bundle
9. las notas en 9 locales, cada una bajo 500 caracteres

De los nueve, **ocho son mecánicos y verificables**. El noveno (las notas) es editorial y no lo
automatiza nada.

## Doctrina violada

*Sistemas, no parches.* El procedimiento existe —está escrito en los docs de `RELEASE-*`— pero vive
como **prosa que un humano reejecuta**, así que cada corte lo vuelve a derivar y cada corte puede
olvidarse un paso distinto. Un invariante que solo vive en la memoria de quien lo hizo la última vez
no es un invariante.

📌 Y hay precedente de que muerde: el 03-09 salió un AAB firmado con el certificado equivocado
porque `local.properties` pierde la precedencia frente a `keystore.properties`, y la comprobación
que se usó («jar verified.») no podía verlo.

## Diseño — dónde vive el invariante

**Un script local, `tools/release/cut.py`**, no un workflow. Esa es la decisión de diseño:

- la **lógica** del corte (los 8 pasos mecánicos, en orden, con sus verificaciones) vive en **un
  sitio** que se puede ejecutar hoy, sin depender de nadie;
- el CD futuro queda reducido a **un disparador** que lo llama. Si la lógica naciera dentro del YAML,
  seguiría sin poder ejecutarse en local y el día que Actions se cayera volveríamos a la prosa.

Lo que hace, en una invocación — `python tools/release/cut.py 1.5.0` (el `versionCode` sale de
leer el actual y sumar 1, que es lo que se ha hecho en los 8 cortes anteriores):

| Fase | Qué comprueba |
|---|---|
| bump | escribe los **dos** sitios y falla si el `versionName` pedido ya está en master |
| web | `node web/build.mjs` + `--check`, y **avisa si `hosting/public/` cambió** (en 1.5.0 no cambió: el número no se hornea) |
| suite | `testAll --rerun-tasks`, y **falla si alguna tarea sale UP-TO-DATE** |
| bundle | `:app:bundleProdRelease --rerun-tasks`, copia a `paparcar-<ver>-vc<code>.aab` |
| firma | `keytool -printcert -jarfile` y **compara la SHA-1 contra la upload key esperada**, que es lo que faltaba el 03-09 |
| medida | sha256, bytes, entradas, overhead, y el `versionName` leído del manifest del bundle — con `1.<anterior>` **ausente** |
| notas | parsea `release-notes-v<ver>.txt`, exige **9 locales** y cada uno **≤ 500**; es lo único que no escribe, solo valida |
| doc | rellena el esqueleto de `docs/backlog/release-<ver>-001.md` con las cifras medidas |

⛔ **Lo que el script NO hace, a propósito:** subir nada a Play. La publicación se queda en manos del
user — es la acción irreversible y de cara al público, y no se delega a un script sin que alguien
mire. El script deja el AAB verificado y las cifras; el botón sigue siendo humano.

## Bloqueo — por qué no es un workflow hoy

⛔ **El CI no arranca desde el 13-09-2026, y es facturación, no YAML.** Org `apptolast` en plan
free + repo privado desde el 08-09 → los minutos de Actions se miden y se agotaron (la prueba está
en la anotación del check-run, no en los logs). Un workflow de release es otro workflow: tampoco
arrancaría, y hay regla explícita de **no montar otro guard encima del que no arranca**
(`CI-A-GUARD-THAT-NEVER-STARTS-IS-NOT-A-GUARD-001`).

**Desbloqueo, que es del user:** método de pago y límite de gasto en
`github.com/organizations/apptolast/settings/billing`.

## Criterio de éxito

- [ ] `python tools/release/cut.py <ver>` corta una versión completa y **falla ruidosamente** en
      cada una de las 8 verificaciones si se las salta (probado falsificando las 8, no solo el
      camino verde)
- [ ] el doc del release sale rellenado con cifras **medidas**, no escritas a mano
- [ ] (cuando Actions reviva) un workflow `release.yml` que solo invoque al script y publique el
      AAB como artefacto — **sin** subirlo a Play

## Consumidores a auditar al implementarlo

`grep -rl "bundleProdRelease" docs/ .github/ tools/` — los docs de `RELEASE-1-0-0` … `RELEASE-1-5-0`
deletrean el procedimiento cada uno por su cuenta; al nacer el script, todos pasan a **citarlo** en
vez de repetirlo, que es el mismo barrido que se hizo con `testAll` en el CI.
