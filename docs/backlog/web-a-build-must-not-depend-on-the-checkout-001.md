# WEB-A-BUILD-MUST-NOT-DEPEND-ON-THE-CHECKOUT-001 · el hash de un asset depende de con qué finales de línea lo tenga quien construya

**Estado:** ✅ **Done** — sin commitear, a la espera de permiso. Rama
`fix/WEB-A-BUILD-MUST-NOT-DEPEND-ON-THE-CHECKOUT-001-lf-normalize` · worktree `../Paparcar-lf`

Cierra de verdad lo que
[`web-a-derived-asset-must-match-its-generator-001.md`](web-a-derived-asset-must-match-its-generator-001.md)
dio por resuelto «porque master lo corrigió», y que el 30-09 resultó estar **alternando**.

## Problema

`publishAssets()` publica cada asset como `nombre.<sha256[0:8]>.<ext>` hasheando **los bytes tal
como estén en el disco de quien construye**. Así que el nombre de un fichero de texto depende de
los finales de línea de ese checkout, no de su contenido.

Medido el 30-09-2026, el mismo `legal.css` en dos sitios del mismo repo:

| | CR | bytes | hash publicado |
|---|---|---|---|
| Árbol principal (working tree) | **140** | 4895 | `223eea16` |
| Índice de git, y cualquier worktree nuevo | 0 | 4755 | `c62e5db9` |

Los dos contenidos son **el mismo CSS**. `.gitattributes` (`* text=auto eol=lf`) hace que el índice
guarde LF y que un checkout nuevo dé LF — por eso los worktrees recién creados construyen
`c62e5db9`. Pero el árbol principal arrastra ese fichero con CRLF desde antes de que existiera el
`.gitattributes`, y git **no lo marca como modificado** porque normaliza al comparar.

Consecuencia real, no teórica: **construir desde el árbol principal renombra un asset que nadie ha
tocado**, y el commit de quien pasara por ahí se lo llevaría. Pasó dos veces:
- en `f4181114e` entró el renombrado a `c62e5db9` (y se documentó como «master lo corrigió»);
- el 30-09, al construir en el árbol principal, volvió a `223eea16`. Se revirtió a mano.

⛔ Hoy master está **consistente**: los 65 assets publicados casan con el hash de su contenido. Lo
que falla no es el estado, es que **nada impide que se rompa otra vez**.

## Doctrina violada

- *Un derivado tiene que corresponder a su generador* — y para eso el generador tiene que ser
  **determinista**. Hoy depende del entorno de quien lo ejecuta.
- El `.gitattributes` del repo declara la intención (`eol=lf`) y el build no la respeta: hashea lo
  que haya en disco.
- *Prohibición sin TESTIGO no es chequeo*: el doc anterior pidió este guard y no se escribió,
  precisamente porque el síntoma se había «arreglado solo».

## Señales / datos disponibles

- `git check-attr text eol -- web/assets/css/legal.css` → `text: auto`, `eol: lf`. La intención está
  declarada; lo que falta es que el build la aplique.
- De los diez ficheros de texto de `web/assets`, **sólo `legal.css`** está con CRLF en el árbol
  principal. O sea: no es una configuración de OS, es UN fichero que quedó atrás. Y con uno basta.
- Los assets **binarios** (png, jpg, webp, woff2) no tienen este problema y **no pueden tocarse**:
  normalizar bytes ahí los corrompería.

## Diseño

**El build normaliza a LF lo que escribe, y un testigo comprueba que cada nombre corresponda a su
contenido.**

1. **`publishAssets()` normaliza CRLF → LF en los assets de TEXTO antes de hashear y escribir.** La
   lista de extensiones de texto es explícita (`css`, `js`, `svg`, `json`, `map`, `txt`,
   `webmanifest`, `xml`, `html`): lo que no esté en ella se copia byte a byte, porque normalizar un
   binario lo destruye. Hashear **después** de normalizar es lo que hace el nombre independiente del
   checkout.
2. **`emit()` normaliza igual** lo que escribe en `hosting/public`. Las páginas salen de literales
   de plantilla en ficheros `.mjs`, así que un `.mjs` con CRLF metería `\r` en el HTML por la misma
   puerta.
3. **Testigo**: para cada fichero publicado bajo `hosting/public/assets`, el hash de 8 caracteres de
   su nombre tiene que ser el sha256 de su contenido. Es el cálculo que el build ya hace, leído al
   revés, y cubre a la vez el CRLF y cualquier renombrado a mano. Se exceptúa `manifest.json`, que
   es el único no hasheado y ya lleva su nota en `firebase.json`.

⛔ **No se toca el `legal.css` del árbol principal.** Con el build normalizando deja de importar, y
el árbol principal es del user.

## Criterio de éxito

- Convertir un asset de texto a CRLF y construir da **el mismo nombre y los mismos bytes** que con
  LF. Esa es la prueba directa del invariante.
- El testigo se pone rojo si un asset publicado no corresponde a su nombre.
- `node web/build.mjs --check` limpio y `./gradlew testAll --rerun-tasks` verde.
- El árbol construido no cambia respecto a master: si el arreglo fuera correcto pero moviera algún
  nombre, sería un rediseño, no un arreglo.

## Consumidores auditados

| | Antes | Después |
|---|---|---|
| `publishAssets()` | hashea los bytes del disco | normaliza texto a LF y **luego** hashea |
| `emit()` | escribe lo que le dan | normaliza a LF |
| assets binarios (png/jpg/webp/woff2) | copia byte a byte | **sin cambio**, exentos por extensión |
| `assets/manifest.json` | no hasheado | **sin cambio**, exento en el testigo |
| `.gitattributes` | declara `eol=lf` | sin cambio: la intención ya estaba, faltaba aplicarla |


---

## Verificado

- **La prueba directa del invariante**: se convirtió `legal.css` a CRLF (4895 bytes, 140 CR), se
  construyó, y el árbol construido salió **sin un solo cambio** respecto a la build desde LF. Mismo
  nombre, mismos bytes. Eso es exactamente lo que el ticket promete.
- **Y no rediseña nada**: construir con el arreglo puesto, desde el checkout normal, deja
  `hosting/public` **idéntico a master**. Si hubiera movido un nombre, sería otra cosa.
- **Testigo falsificado en sus TRES casos**: un asset renombrado a mano, uno con los bytes de un
  checkout CRLF bajo su nombre viejo, y uno publicado sin hash.
- `./gradlew testAll --rerun-tasks` verde · `node web/build.mjs --check` limpio.

## Lo que este ticket NO hace

⛔ **No toca el `legal.css` del árbol principal**, que sigue con CRLF en disco. Con el build
normalizando deja de importar —el nombre publicado ya no depende de él— y ese árbol es del user. Si
alguna vez se quiere limpiar: `git rm --cached web/assets/css/legal.css && git checkout -- web/assets/css/legal.css`.

⚠️ El testigo comprueba el árbol **construido que está commiteado**. No puede saber si alguien
construyó y no commiteó; para eso está `node web/build.mjs --check` antes de desplegar.
