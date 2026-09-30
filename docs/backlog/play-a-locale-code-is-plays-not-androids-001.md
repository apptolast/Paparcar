# PLAY-A-LOCALE-CODE-IS-PLAYS-NOT-ANDROIDS-001 · el rumano en Play es `ro`, y el parser no podía ni verlo

**Estado:** ✅ **Done** (30-09-2026) · rama `chore/PLAY-A-LOCALE-CODE-IS-PLAYS-NOT-ANDROIDS-001-ro`
· worktree `../Paparcar-ro-locale`

## Problema

Al pegar las notas de 1.4.0, el Play Console rechazó el bloque entero:

> Se ha encontrado la traducción a un idioma no admitido: **ro-RO**. Elimina esta traducción para
> continuar.

`ro-RO` **no es un código de ficha de Play**. La lista oficial de idiomas de Play da el rumano como
**`ro`**, a secas — es el único de nuestros nueve que no lleva región. El user confirmó en el
Console que con `<ro>` entra.

## Doctrina violada

*Sistemas, no parches.* El error salía en un `.txt`, pero la causa estaba en el **código**, y en
forma de suposición estructural. Los dos regex que leen la fuente del copy eran:

```python
r"^\|\s*([a-z]{2}-[A-Z]{2})\s*\|\s*`(.+?)`\s*\|"
r"^### ([a-z]{2}-[A-Z]{2})\s*\n+```\n(.*?)\n```"
```

**`[a-z]{2}-[A-Z]{2}` no puede casar `ro`.** O sea que aunque alguien hubiese escrito el código bien
en `LISTING-v1.3.0.md`, el generador habría dicho «no tiene: nombre/ro» y habría parado. El fichero
de datos y el parser estaban de acuerdo **en el mismo error**, que es justo por lo que nadie lo vio.

⛔ Y hay una confusión de fondo que da nombre al ticket: `ro-RO` **sí** es un locale válido de
Android —de hecho `docs/backlog/i18n-plural-categories-…` mide plurales con el emulador en
`ro-RO`—, y la carpeta de recursos es `values-ro`. Play tiene su propia lista y no es ninguna de las
dos. *Un código de locale de Play es de Play, no de Android.*

## Diseño

El invariante es «la región es OPCIONAL», y se arregla en los dos sitios donde vive la lista:

- `tools/make_paste.py` — `LOCALES` y sus dos regex → `[a-z]{2}(?:-[A-Z]{2})?`.
- `PlayListingPasteParityGuardrailTest.kt` — lo mismo. ⛔ **El guard duplica la lista a propósito**:
  si leyera la del generador no comprobaría nada [project: *prohibición sin testigo no es chequeo*].
  Por eso se corrigen los dos, no se factoriza en uno.

Lo demás es consecuencia: `paste/` se **regenera** (`play-ro-RO.txt` → `play-ro.txt`,
`play-listing-import.md`), y las tablas se ponen al día.

## Criterio de éxito

- `make_paste.py --check` limpio y `play-ro.txt` en su sitio.
- `./gradlew testAll --rerun-tasks` verde con el guard ejecutado de verdad.
- `grep -rn "ro-RO" docs/ shared/` sólo devuelve el doc de plurales, que habla del locale de
  **Android** en un emulador y ahí `ro-RO` es correcto.

## Consumidores auditados

| Sitio | Qué era | Estado |
|---|---|---|
| `tools/make_paste.py` | `LOCALES` + 2 regex con región obligatoria | ✅ arreglado — es la causa |
| `PlayListingPasteParityGuardrailTest.kt` | la misma lista y los mismos 2 regex | ✅ arreglado |
| `LISTING-v1.3.0.md` | `\| ro-RO \|` en dos tablas y `### ro-RO` | ✅ es la fuente del copy |
| `paste/play-ro-RO.txt` | pegable con el nombre mal | ✅ borrado → `play-ro.txt` |
| `paste/play-listing-import.md` | cabecera `## Locale: ro-RO` | ✅ regenerado |
| `paste/release-notes-v1.4.0.txt` | `<ro-RO>` — **el que dio el error** | ✅ `<ro>` |
| `paste/release-notes-{v1,v1.2.0,v1.3.0}.txt` | mismo fallo, pegables históricos | ✅ corregidos: si alguien los repega, fallarían igual |
| `README.md` · `LISTING-i18n.md` · `PROMPT-SACAR-VERSION-A-PRODUCCION.md` | tablas | ✅ |
| `docs/backlog/i18n-plural-categories-…` | `ro-RO` de un emulador **Android** | ✅ exento, ahí es correcto |
| `composeResources/values-ro`, `screenshots/ro/` | ya usaban `ro` | ✅ intactos |

## Follow-up

⏳ **1.3.0 y 1.2.0 se publicaron con notas que Play no pudo haber aceptado en rumano.** Nadie
comprobó después si el rumano quedó dentro. Merece una mirada al Console: si esas versiones se
publicaron sin notas en rumano, los usuarios rumanos llevan dos releases leyéndolas en inglés.
