# RELEASE-THE-CUT-MUST-BE-ONE-COMMAND-001 · el corte de una versión cabe en un comando

**Estado:** ✅ **Done** (26-10-2026) — `tools/release/cut.py` + `test_cut.py` en master; la rama
`chore/RELEASE-THE-CUT-MUST-BE-ONE-COMMAND-001-cut-script` y su worktree se retiran al cerrar.
Abierto el 14-10-2026 desde `RELEASE-1-5-0-001`.

⏳ **Residual, y es externo**: el primer corte completo de verdad se verá en el **próximo release**
(hoy no se corta ninguno a propósito — el disparador es el user), y el workflow que invoque al
script sigue esperando a que Actions vuelva a arrancar.

⛔ **El corte lo dispara el USER, nunca un merge** (dicho el 26-10-2026). No es un detalle de
implementación: es lo que descarta el disparador obvio —`push: master`— y deja el script como un
comando que se invoca a mano. Master recibe docenas de commits entre dos releases; cortar versión en
cada uno sería quemar `versionCode` por nada. Y vale para el CD futuro: la fase que suba a Play
tampoco se dispara sola.

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

Lo que hace, en una invocación — `python tools/release/cut.py 1.6.0` (el `versionCode` sale de
leer el actual y sumar 1, que es lo que se ha hecho en los 8 cortes anteriores):

| Fase | Qué comprueba |
|---|---|
| bump | escribe los **dos** sitios y falla si esa versión ya se cortó (etiqueta o commit que la escribiera), o si los dos sitios ya discrepaban |
| web | `node web/build.mjs` + `--check`, y **avisa si `hosting/public/` cambió** (en 1.5.0 no cambió: el número no se hornea) |
| suite | `testAll --rerun-tasks`, y comprueba que Gradle **no se salvó trabajo** (ver corrección abajo) |
| bundle | `:app:bundleProdRelease --rerun-tasks`, copia a `paparcar-<ver>-vc<code>.aab` |
| firma | `keytool -printcert -jarfile` y **compara la SHA-1 contra la upload key esperada**, que es lo que faltaba el 03-09 |
| medida | sha256, bytes, entradas, overhead, y el `versionName` leído del manifest del bundle — con el anterior **ausente** |
| notas | parsea `release-notes-v<ver>.txt`, exige **9 locales** y cada uno **≤ 500**; es lo único que no escribe, solo valida |
| doc | rellena `docs/backlog/release-<ver>-001.md` con las cifras medidas, entre marcadores |
| tag | etiqueta anotada `v<ver>` — **fase aparte**, porque necesita que el commit del bump exista |

### Tres correcciones al diseño, todas medidas al implementarlo (26-10-2026)

⛔ **«Falla si alguna tarea sale UP-TO-DATE» era inimplementable.** Medido con `--rerun-tasks`
puesto y el build correcto: **11 UP-TO-DATE** (los `preBuild`), **12 SKIPPED** y **52 NO-SOURCE**.
Ese chequeo habría dado rojo SIEMPRE, y un guard que siempre falla se acaba ignorando — que es el
fallo que este ticket existe para no repetir. Lo que sí mide Gradle es su propio resumen:
`93 actionable tasks: 93 executed`, que con trabajo salvado diría `80 executed, 13 up-to-date`. Eso
es lo que se comprueba, más que las **tres hojas** de `testAll` salieran ejecutadas y no ausentes.
📌 Afirmar que las tres hojas corrieron NO es deletrear la lista de tareas (el pecado de
[TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001]): se **invoca** `testAll`, y si alguien saca una
hoja de ahí, esto lo caza.

⛔ **La huella esperada de la upload key va al REPO, no se lee de `keystore.properties`** — aunque
ese fichero se declare «fuente canónica de la firma» y traiga el SHA-1 en un comentario. Dos
razones: está gitignoreado (en otra máquina no existe) y es el **mismo fichero que configura la
firma**. Una expectativa que viaja con lo que comprueba no comprueba nada: el 03-09 el fallo fue
justo que la configuración local apuntaba a otro keystore, así que leer la expectativa de ahí habría
salido **verde**. Es una huella pública, no un secreto.

⛔ **El `versionCode` del bundle no se verifica leyendo el bundle, y se declara.** El manifest del
AAB es protobuf; el `versionName` viaja como cadena UTF-8 y se puede leer por bytes, pero un número
de dos cifras aparece por casualidad en cualquier parte — buscarlo sería un chequeo que siempre
pasa. Se verifica el fichero de Gradle, que es de donde sale.

⛔ **Lo que el script NO hace, a propósito:** subir nada a Play. La publicación se queda en manos del
user — es la acción irreversible y de cara al público, y no se delega a un script sin que alguien
mire. El script deja el AAB verificado y las cifras; el botón sigue siendo humano.

## Por qué no es un workflow — y por qué eso no bloqueaba el script

⛔ El bloqueo **era del workflow, nunca del script**, y es la razón de haber puesto la lógica en
Python: se implementó entero el 26-10-2026 con Actions todavía muerto. Lo que sigue pendiente de
Actions es el disparador, que no es lo caro.


⛔ **El CI no arranca desde el 13-09-2026, y es facturación, no YAML.** Org `apptolast` en plan
free + repo privado desde el 08-09 → los minutos de Actions se miden y se agotaron (la prueba está
en la anotación del check-run, no en los logs). Un workflow de release es otro workflow: tampoco
arrancaría, y hay regla explícita de **no montar otro guard encima del que no arranca**
(`CI-A-GUARD-THAT-NEVER-STARTS-IS-NOT-A-GUARD-001`).

**Desbloqueo, que es del user:** método de pago y límite de gasto en
`github.com/organizations/apptolast/settings/billing`.

## Qué hay en producción — etiquetas, no `develop`/`main`

Se evaluó git-flow el **26-10-2026** y se descartó con una razón, no por inercia: la mitad que
aporta aquí es un **puntero a lo publicado**, y eso lo dan las etiquetas sin tocar nada. Git-flow
completo exigiría renombrar `master`, decidir si `main` recibe merge commits —rompiendo el histórico
lineal y el `merge --squash` de un commit por ticket— y barrer seis consumidores que hoy dicen
master (`branch_tell.py`, `BacklogStatusGuardrailTest`, `push: master` del CI, las skills
`nuevo-ticket` y `rebase-rama`, y la memoria).

El problema que lo motivaba está **medido**: 1.4.0 se publicó el 13-10 con el árbol del 30-09, y
nada en el repo lo decía. Con la fase `tag`: producción = `git describe --tags --abbrev=0`, y un
hotfix sale de esa etiqueta.

⚠️ **Hoy solo existe `v1.0.0`** (más 6 etiquetas locales `prerebase/`): de 1.2.0 a 1.5.0 nadie
etiquetó. Mientras eso siga así, la «versión anterior» que la fase `measure` usa para comprobar la
ausencia en el manifest sale de HEAD en un corte normal, y de la etiqueta solo al reejecutar — por
eso el script **dice de dónde la saca** en vez de afirmarla a secas.

## Criterio de éxito

- [x] las verificaciones **fallan ruidosamente** cuando se les quita lo que comprueban, probado
      falsificándolas una a una en `tools/release/test_cut.py` — **45 tests**, no solo el camino
      verde: `python tools/release/test_cut.py`
- [x] las fases `sign`, `measure` y `notes` corren contra el **AAB real de 1.5.0** y devuelven las
      cifras **idénticas** a las que `RELEASE-1-5-0-001` publicó: sha256 `6a02f226…`, 23.437.417 B,
      1.562 entradas, 1,3 % de overhead, `fr-FR` como techo con 455
- [x] la fase `doc` **se niega** a escribir en un doc sin marcadores en vez de adivinar dónde
      (verificado contra el doc real de 1.5.0: md5 idéntico antes y después)
- [ ] un corte completo de verdad — se verá en el próximo release; hoy no se corta ninguno a
      propósito, que para eso el disparador es el user
- [ ] (cuando Actions reviva) un workflow `release.yml` que solo invoque al script y publique el
      AAB como artefacto — **sin** subirlo a Play

## Consumidores auditados

`grep -rln "bundleProdRelease" docs/ .github/ tools/ web/`:

| Consumidor | Veredicto |
|---|---|
| `docs/release/RELEASE-PROCESS.md` | **barrido**. §1.0 pasa a citar el comando con una tabla de qué evita cada fase; §1.1 se queda solo con lo editorial; §1.3 cede el AAB y la firma al script y mantiene el APK del smoke test; §1.6 pide la etiqueta por la fase `tag`. 📌 La prueba de que repetir el procedimiento se desincroniza estaba **dentro del propio doc**: §1.1 decía «hoy va por 14 / 1.3.0» con el árbol en 16 / 1.5.0 |
| `docs/release/PROMPT-SACAR-VERSION-A-PRODUCCION.md` | **exento**: no deletrea el corte, es el prompt de la ficha de Play (y lo gobierna `PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001`) |
| `docs/backlog/release-1-4-0-001.md`, `release-1-5-0-001.md` | **exentos**: son el registro de un corte pasado, no el procedimiento. Reescribirlos sería falsear lo que se hizo |
| los 7 `docs/backlog/r8-*.md` y `money-001.md`, `map-cluster-…`, `build-a-lane-…`, `release-a-crash-…` | **exentos**: citan `bundleProdRelease` para medir R8 o un tamaño, no para cortar |

## Follow-ups que salieron al implementarlo

- 🔴 **`iosApp/fastlane/tools/metadata_from_play.py` se salta el rumano, dos veces y en silencio**
  — medido: su regex exige `<xx-XX>`, así que de las notas lee **8 de 9** (`<ro>` no lleva región);
  y busca `play-ro-RO.txt`, que no existe (el fichero es `play-ro.txt`). La ficha de App Store en
  rumano se queda sin notas y sin descripción. Mismo invariante que la fase `notes` de aquí, otro
  consumidor. ⛔ No se toca en este ticket: es la ficha de Apple, y entra con su propio ID.
- 🔵 **Etiquetar hacia atrás 1.2.0 → 1.5.0**, que se publicaron sin etiqueta. Son 4 etiquetas
  anotadas sobre sus commits de bump (`git log -S 'versionName = "1.4.0"'`). Es una acción sobre el
  repo del user, así que se propone, no se hace.
