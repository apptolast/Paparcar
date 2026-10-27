# RELEASE-THE-CUT-REACHES-THE-STORE-001 · el corte habla con la tienda

**Estado:** ✅ **Done** (27-10-2026) · rama
`feature/RELEASE-THE-CUT-REACHES-THE-STORE-001-upload` · worktree `../Paparcar-store` — ambos se
retiran al cerrar.

⏳ **Residual, y no se puede cerrar hoy**: el `:commit` de la edición —el único paso que cambia algo
en la cuenta— no se ha ejercitado contra Play, porque exigiría subir un `versionCode` nuevo y hoy no
se corta ninguna versión a propósito. Se verá en el **próximo corte**. Todo lo demás del camino sí
está probado contra la API real (ver criterio).
Sale de `RELEASE-THE-CUT-MUST-BE-ONE-COMMAND-001`, que dejó el AAB verificado y las cifras medidas
pero sin ningún camino a Play.

## Problema

Dos agujeros, y el segundo es el que de verdad dolía.

**1 · Subir era enteramente a mano.** El corte deja un AAB firmado y nueve bloques de notas
validados, y a partir de ahí: navegador, arrastrar el bundle, pegar nueve veces. Nada de eso falla
ruidosamente si se hace a medias.

**2 · Nada en el repo sabía qué hay publicado, y por eso el repo se equivocó durante 13 días.**
Medido el 27-10-2026, la primera vez que se pudo preguntar a la tienda: `production` tenía
**`completed` versionCode 16 (1.5.0)** con notas en los 9 idiomas, `ro` incluido — mientras
`docs/backlog/release-1-5-0-001.md` y la memoria seguían diciendo *«⏳ pendiente del user: subir el
AAB, pegar las notas y publicar»* desde el 14-10. El user ya lo había subido.

📌 La lección no es el dato suelto: **un «⏳ pendiente del user» sobre algo que vive en un sistema
externo no se puede creer**. Hasta hoy no había forma de falsificarlo sin abrir el navegador; ahora
cuesta una llamada.

Y lo mismo desbloqueó otra cosa que llevaba semanas esperando: la ficha **sigue siendo
`LISTING-v1.4.0.md` byte a byte** (comparado con la tienda), con 1.5.0 ya en producción. Es
exactamente la condición que `PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001` esperaba.

## Doctrina aplicada

⛔ **El corte lo dispara el user y la publicación sigue siendo humana**
[[feedback_release_cut_is_user_triggered]]. Así que `upload` **no entra en las fases por defecto**:
hay que pedirla, sube al canal **`internal`** y la deja en **borrador**. Promover a producción se
hace en el Console, mirando.

⛔ **Sin dependencias nuevas.** No hay `gcloud` ni `google-auth` en esta máquina, y meter Gradle Play
Publisher traería un plugin con su propia configuración y su propia forma de leer el JSON. El token
se firma con `openssl dgst -sha256 -sign` y las llamadas van con `urllib`: lo mismo que ya se probó
funcionando. La lógica sigue en un sitio que se puede ejecutar hoy.

## Diseño

`tools/release/play_api.py` — cliente mínimo: JWT → token → `edits`. Una **edición** de la Play
Developer API es una transacción de scratch: mientras no se hace `:commit` no cambia nada, y
`DELETE` la descarta. `verify` nunca commitea.

Dos fases nuevas en `cut.py`, ninguna en la lista por defecto:

| Fase | Qué hace |
|---|---|
| `verify` | **solo lee**. Qué versionCode corre en cada canal, en qué idiomas están sus notas, y si la ficha publicada coincide con la del repo. Falla si la tienda va por DELANTE del repo, si a la release viva le falta un locale de notas, o si la ficha viva difiere de `paste/` |
| `upload` | sube el AAB al canal `internal` como **borrador**, con las notas de los 9 locales ya validadas por la fase `notes` |

## Criterio de éxito

- [x] `verify` corre **contra la tienda real** y dice la verdad: `production` e `internal` en
      `completed` con vc16, notas en 9 idiomas, ficha en 9 idiomas, y «la tienda y el repo cuadran»
- [x] `upload` probado end-to-end contra la API real salvo el `:commit`: autenticó, subió los
      **22,4 MB**, y Play lo rechazó con su propio mensaje — *«Version code 16 has already been
      used»* — que es lo correcto, porque 16 ya está publicado. La edición se **descartó** y un
      `verify` posterior mostró la tienda idéntica, sin borradores sueltos
- [x] **27 tests** en `tools/release/test_play_api.py`, con el transporte sustituido, falsificando
      lo que una ejecución buena no enseña: un 401/403/404 se **explica** en vez de reventar, la
      petición del canal lleva `draft` + los 9 locales + los versionCodes como cadenas, un fallo a
      mitad **descarta** la edición y no commitea, y `verify` canta cuando la tienda va por delante,
      cuando a una release le falta un locale de notas y cuando la ficha viva se desvía del repo
- [x] ninguna de las dos entra en las fases por defecto (`DEFAULT_PHASES` excluye `tag`, `verify` y
      `upload`)

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `docs/release/RELEASE-PROCESS.md` | **barrido**: §1.5 pasa a abrir con los dos comandos y deja el camino manual debajo; §0.5 nueva documenta la credencial y por qué los permisos son los mínimos; §0.3 ya no dice «no hay lane automatizada» |
| `play_locales.paste_sections()` | `verify` necesitaba leer los pegables y `metadata_from_play.py` ya lo hacía: el parser pasa al módulo común en vez de nacer un segundo. Es el mismo invariante de `IOS-THE-STORE-METADATA-SKIPS-ROMANIAN-001`, un día después |
| `PROMPT-SACAR-VERSION-A-PRODUCCION.md` | **exento**: es el prompt de la ficha para Claude.ai con el conector de Play, otro camino y otro ticket |

## Lo que esto desbloquea

- `PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001`: su condición era «no tocar la ficha hasta que
  1.5.0 esté en producción». **Lo está** desde hace 13 días, y la ficha sigue siendo la de 1.4.0.
- La duda de `PLAY-A-LOCALE-CODE-IS-PLAYS-NOT-ANDROIDS-001` sobre el rumano: 1.5.0 **sí** salió con
  `ro`. ⚠️ La API solo devuelve las releases vigentes, así que 1.2.0 y 1.3.0 siguen sin poderse
  comprobar por aquí.
