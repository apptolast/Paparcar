# RELEASE-1-4-0-001 · el AAB de 1.4.0 (versionCode 15)

**Estado:** ✅ **Done** (30-09-2026) · rama `chore/RELEASE-1-4-0-001-bundle` · worktree
`../Paparcar-release-140` — ambos se retiran al cerrar.

⛔ **El AAB NO entra en el repo, y no es un olvido**: `.gitignore` lo excluye por tres reglas
(`build/`, `**/build/`, `*.aab`) y la regla de CLAUDE.md es *no commitear archivos de build*. Un
bundle es **derivado**: sale de un commit, no lo sustituye — 22 MB por release en un `.git` que ya
pesa 210 MB, y regenerable con un comando. Lo que se versiona es lo que lo produce, que es
exactamente este commit. Misma doctrina que los gráficos por idioma y las 72 capturas.

**El AAB de esta versión**, para que conste de dónde salió:

| | |
|---|---|
| sha256 | `00742f17ebdb02d8f9ba1cdfb15f4087…` |
| tamaño | 23.387.151 B (22,30 MB) · 1.562 entradas · 1,3 % de overhead de zip |
| firma | `CN=Paparcar, O=Paparcar, C=ES` · SHA-1 `45:40:9F:1F:A8:8A:B2:A3:BB:25:15:CB:FB:78:F6:FD:E0:E1:BF:97` |
| leído del bundle | `versionCode 15` · `versionName 1.4.0` (`1.3.0` ya no aparece en su manifest) |

## Problema

`versionCode 14` / `1.3.0` **está vivo en producción** desde el corte del 17-09. Un AAB con ese
número lo rechaza Play al subirlo, así que no hay «exportar el bundle» sin bumpear antes.

Desde ese corte hay más de 100 commits con resultado visible que nunca han salido del repo, entre
ellos los dos de hoy: la marca de 2026 en las 21 superficies y el arte de la ficha regenerado.

## Qué entra en el bump

| | 1.3.0 | 1.4.0 |
|---|---|---|
| `versionCode` | 14 | **15** |
| `versionName` | 1.3.0 | **1.4.0** |

⛔ **El número vive en DOS sitios y hay un test que lo comprueba.** `WebSiteParityGuardrailTest`
compara `APP_VERSION` de `web/lib/config.mjs` contra el `versionName` de `app/build.gradle.kts`:
bumpear sólo el Gradle deja la suite roja. Y como la web hornea sus hechos en las páginas, después
hay que **reconstruir** (`node web/build.mjs`) o se despliega lo anterior.

## Efecto lateral que conviene entender antes de verlo en el diff

Al reconstruir, **tres ficheros de `hosting/public/assets/` cambian de nombre**:

```
legal.223eea16.css → legal.c62e5db9.css
site.166264b3.css  → site.fa24a943.css
site.af2f7e02.js   → site.9feafadd.js
```

No lo provoca el bump. Los nombres llevan el hash del contenido, y los de master eran los del
contenido **en CRLF**: su `hosting/public` se construyó ANTES de que `.gitattributes` fijara LF en
el índice. Los nuevos son los del contenido en LF — es decir, **por primera vez el output es el que
un rebuild limpio reproduce**. Comprobado: los tres hashes nuevos coinciden con los que ya se
midieron a mano el 29-09 al descartar el ruido de CRLF
(`git show :<f> | tr -d '\r' | sha256sum`). Las 7 páginas los referencian de forma coherente y
`node web/build.mjs --check` sale OK.

## Criterio de éxito

- `./gradlew testAll --rerun-tasks` verde, con `WebSiteParityGuardrailTest` ejecutado de verdad.
- `:app:bundleProdRelease` produce un AAB **firmado** con `keystore/release.jks`.
- El AAB declara `versionCode 15` y `versionName 1.4.0` (leído del bundle, no del Gradle).
- ⛔ Medida de tamaño sólo sobre empaquetado limpio: `--rerun-tasks`, porque AGP actualiza el zip
  en sitio y deja padding dentro — ya mintió en **megas** una vez
  [BUILD-DEPS-AT-LATEST-STABLE-002].

## Las notas de versión — ✅ hechas, y de dónde salen

`paste/release-notes-v1.4.0.txt`, los 9 idiomas, a mano (⛔ `make_paste.py` **no** las genera: sólo
genera la ficha). Seis noticias, todas sacadas de los **116 commits** reales desde `4a480500d`:

| Noticia | Commit |
|---|---|
| Contestar un viaje desde la pantalla de inicio (widget) | `d5696b774` |
| El aviso de plaza cerca de ti, y deja de ser de pago | `9af333109` + `f355d1182` |
| El alto del coche: un gálibo desconocido nunca es un «cabes» | `a0f57bf1a` |
| El carril de gasolineras filtrado por lo que echas | `cda6f03d8` |
| Los parkings con su tarifa en el mapa | `8fcf038ff` |
| Una plaza deducida deja de verse como una medida | `caa54cc2d` |

Medidas contra el tope de Play (500 por locale): la más apretada es `fr-FR` con **488**. Respetan
el vocabulario del proyecto — *plaza* para lo comunitario, *parking / car park* para la instalación
[COPY-SPOT-IS-NOT-A-PARKING-001].

**Nombre de la versión** para el Console (48/50): `1.4.0 (15) - Widget, avisos cerca de ti y gálibo`

## ✅ PUBLICADA en Play el 13-10-2026 (dicho por el user)

El ⏳ que este doc arrastraba —subir el AAB y pegar las notas— está **hecho**. Lo que desbloquea, y
que hasta hoy la regla de la ficha prohibía expresamente:

- La ficha puede nombrar por fin el **widget**, el **gálibo** y el **acierto de la detección**:
  llevan en master desde el 19–21-09 y hasta ahora no estaban en el build publicado.
- ⛔ El **aviso «cerca de ti»** sigue FUERA, y no por la versión: hoy solo salta dentro de una zona
  guardada, que es el aviso de Plus. Su ticket sigue abierto.
- La **web** entra en la misma tarea: `web/content/{es,en}.json` no nombra ninguna de las tres.

→ `docs/backlog/play-the-listing-moves-to-the-published-build-001.md`

## Fuera de alcance, y hacía falta antes de publicar (histórico)

- **La ficha**: `LISTING-v1.3.0.md` sigue describiendo el build publicado. Hasta que 1.4.0 esté en
  producción, la ficha no debe nombrar el widget, el gálibo ni el acierto de la detección
  (⛔ *la ficha describe el build PUBLICADO, no master* — `docs/release/play-listing/README.md`).
- **Data Safety**: revisar las filas contra lo que cambió desde 1.2.0.
