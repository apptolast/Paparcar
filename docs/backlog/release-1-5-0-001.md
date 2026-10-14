# RELEASE-1-5-0-001 · el AAB de 1.5.0 (versionCode 16)

**Estado:** ✅ **Done** (14-10-2026) · rama `chore/RELEASE-1-5-0-001-bump-and-aab` · worktree
`../Paparcar-release-150` — ambos se retiran al cerrar.

## Problema

1.4.0 está publicada desde el **13-10**, pero su AAB se construyó el **30-09 a las 02:16** — doce
minutos **antes** del commit del bump (`8c45ce88a`, 02:28). Entre ese binario y master hay
**43 commits, 21 de ellos `feat`/`fix` de usuario**, y el primero posterior al bundle es de las
16:36 de ese mismo día. O sea: lo que corre en producción es el árbol del 30-09 de madrugada, y
**nada de lo del 6 al 14 de octubre ha salido**.

📌 El dato que conviene no volver a confundir: *la fecha que uno recuerda de un release es la de la
SUBIDA, no la de la compilación*. Aquí van catorce días de diferencia, y es lo que hace que «43
commits» suene a demasiados.

Lo más caro de esos 43, por orden de lo que nota el usuario:

| Qué | Ticket |
|---|---|
| Gasolineras: lo que sube o baja a tu alrededor se avisa **gratis**; lo que SIGUES es Plus | `FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001` |
| Seguir una gasolinera se **veía** en su fila, y su precio se medía donde estabas tú | `FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001` |
| Tres falsos positivos de detección cazados en campo: el silencio recompraba un pin degradado, un coche al ralentí contaba como egreso, y una muestra suelta se leía como viaje | `DET-A-DEGRADED-PROMPT-MUST-NOT-AUTO-CONFIRM-001` · `DET-A-MANEUVERING-CAR-IS-NOT-AN-EGRESS-001` · `DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001` |
| Un Plus caducado seguía subiendo rutas — y un `set()` sin el campo las **borraba** | `PRIV-A-LAPSED-PLUS-STILL-UPLOADS-ROUTES-001` |
| El correo de la cuenta dice si está confirmado, y se puede confirmar | `AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001` |
| Un login fallido nombra la otra puerta en vez de culpar a la contraseña | `AUTH-A-FAILED-SIGN-IN-MUST-NAME-THE-OTHER-DOOR-001` |
| Una llegada directa ya no se lee «0 min» | `STATS-A-DIRECT-ARRIVAL-DOES-NOT-READ-AS-ZERO-MINUTES-001` |

## Doctrina aplicada

⛔ **El AAB NO entra en el repo, y no es un olvido**: `.gitignore` lo excluye por tres reglas
(`build/`, `**/build/`, `*.aab`). Un bundle es **derivado** — sale de un commit, no lo sustituye, y
se regenera con un comando. Lo que se versiona es lo que lo produce, que es exactamente este
commit. Consecuencia que sí importa: **el bump tiene que entrar en master aunque el binario no**, o
el AAB que se sube salió de un árbol que no existe en ningún sitio.

⛔ **El número vive en DOS sitios y hay un test que lo caza.** `WebSiteParityGuardrailTest` compara
`APP_VERSION` de `web/lib/config.mjs` contra el `versionName` de `app/build.gradle.kts`: bumpear
solo el Gradle deja la suite roja.

🟢 **Medido esta vez, y ahorra un paso**: `APP_VERSION` **no se hornea en ninguna página**.
`node web/build.mjs` tras el bump dejó `hosting/public/` byte a byte igual (`git status` limpio
ahí), así que ese valor solo lo consume el guard. **Este release no obliga a desplegar la web** — y
si se desplegara antes de publicar en Play, la web no mentiría, porque no dice el número.

## El AAB de esta versión

| | |
|---|---|
| sha256 | `6a02f2262189047ace3478ba747d732392bb92d8d28038ab3bba3927ba2506ee` |
| tamaño | 23.437.417 B (22,35 MB) · 1.562 entradas · 1,3 % de overhead de zip |
| vs. 1.4.0 | +50.266 B (**+0,21 %**) con las mismas 1.562 entradas |
| firma | SHA-1 `45:40:9F:1F:A8:8A:B2:A3:BB:25:15:CB:FB:78:F6:FD:E0:E1:BF:97` (upload key vigente) |
| leído del bundle | `versionName 1.5.0` presente en `base/manifest/AndroidManifest.xml`; `1.4.0` **ausente** |
| exportado en | `C:\Users\rndev\Documents\AndroidProjects\paparcar-1.5.0-vc16.aab` |

⛔⛔ **La firma solo se comprueba con `-printcert`, NUNCA con «jar verified.»** — el 03-09 salió un
AAB firmado con el certificado equivocado (`local.properties` pierde la precedencia frente a
`keystore.properties`) y el «verified» salía verde igual. Comando usado:
`keytool -printcert -jarfile <aab>`.

⛔ Empaquetado con `--rerun-tasks`, la única forma de que una medida de tamaño valga: un APK/AAB
incremental miente por megas.

## Notas de versión

`docs/release/play-listing/paste/release-notes-v1.5.0.txt` — **9 locales, a mano** (⛔ `make_paste.py`
NO las genera, solo genera la ficha). Siete noticias, cada una con su commit detrás.

Tope de Play **500 por locale**; medido con el mismo parser que lee los tags:

```
en-US 421 · es-ES 420 · it-IT 423 · pt-PT 422 · fr-FR 456 · de-DE 446 · nl-NL 425 · pl-PL 434 · ro 419
```

El techo sigue siendo `fr-FR`, pero con **44 de margen** frente a los 11 de 1.4.0.
⚠️ El rumano es `<ro>`, **no `ro-RO`** — es un código de Play, no de Android
(`PLAY-A-LOCALE-CODE-IS-PLAYS-NOT-ANDROIDS-001`).

**Nombre de versión para el Console** (47/50): `1.5.0 (16) - Precios cerca y detección más fina`

## Criterio de éxito

- [x] `versionCode 15→16`, `versionName 1.4.0→1.5.0` en los **dos** sitios
- [x] `./gradlew testAll --rerun-tasks` verde — 93 tareas **ejecutadas**, no UP-TO-DATE
- [x] AAB firmado con la upload key vigente, verificado con `-printcert`
- [x] Notas en los 9 locales, todas bajo 500
- [ ] **Del user**: subir el AAB a Play, pegar las notas y publicar

## Pendiente del user — y una cosa que NO se toca todavía

⛔ **La ficha describe el build PUBLICADO.** Hasta que 1.5.0 esté en producción, el carril free de
gasolineras y los arreglos de detección **no entran en la ficha**
(`PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001`). Cuando lo esté, ese es el momento de moverla.

Y revisar **Data Safety** contra lo que cambió desde 1.2.0 — `PRIV-A-LAPSED-PLUS…` toca justo lo que
se sube y cuándo.

## Follow-up abierto deliberadamente

**CD del release.** Se propuso en esta sesión y se descartó por ahora con una razón medida, no por
pereza: **el CI no arranca desde el 13-09 por facturación de Actions** (org `apptolast` en plan
free, repo privado desde el 08-09), así que un workflow de release tampoco arrancaría, y hay regla
explícita de *no montar otro guard encima del que no arranca*. Además lo caro de un release no es la
mecánica — son las notas en 9 idiomas, que ningún CD escribe. Doc:
`docs/backlog/release-the-cut-must-be-one-command-001.md`.
