# WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001 · la regla de cabeceras se desplegó limpia y no casaba con nada

**Estado:** ✅ **Done** — sin commitear, a la espera de permiso. Rama
`fix/WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001-header-glob` · worktree
`../Paparcar-header-glob`

Follow-up inmediato de
[WEB-THE-FAVICON-IS-THE-ONE-ASSET-THAT-CAN-GO-STALE-001](web-the-favicon-is-the-one-asset-that-can-go-stale-001.md)
(`6289ed535`), descubierto al verificar aquel despliegue en vivo.

## Problema

Aquel ticket añadió a `firebase.json` la regla que faltaba para las tres rutas que no pueden llevar
hash. Se escribió como **una sola regla con una alternancia**:

```json
"source": "@(/favicon.ico|/favicon.svg|/site.webmanifest)"
```

Desplegó sin un solo error. Y medido contra producción, **no aplica**:

```
/favicon.ico        HTTP 200   max-age=3600     <- el defecto de Hosting, no la regla
/favicon.svg        HTTP 200   max-age=3600
/site.webmanifest   HTTP 200   max-age=3600
/assets/img/favicon.<hash>.svg  public, max-age=31536000, immutable   <- esta si
```

Las tres puertas responden 200 —el `.ico` y el manifest existen, que era la otra mitad del ticket
anterior— pero siguen con **el `max-age=3600` por defecto**. O sea que el ticket arregló el
contenido y **no** la caducidad, que era la mitad del motivo por el que se abrió.

⛔ **Y el guard lo dio por bueno.** Su chequeo decía: *`firebase.json` contiene los tres nombres Y
la cadena `must-revalidate`*. Una regla que no casa con nada cumple las dos cosas. Un testigo que
comprueba que un texto **está escrito** no comprueba que **haga algo**.

## Doctrina violada

- ***Prohibición sin TESTIGO no es chequeo*** — y su corolario, que este ticket añade: **un testigo
  que sólo mira texto tampoco lo es**. El guard se falsificó en sus tres casos y aun así dejó pasar
  una regla inerte, porque ninguno de los tres casos rompía *la forma* de la regla.
- *Verificar SIEMPRE los comandos que usamos, no sólo que los tests pasen* (CLAUDE.md, a propósito
  de `firebase deploy`). Aquí vale igual: un `deploy` verde no dice que una regla case.

## Señales / datos disponibles

- El propio `firebase.json` ya tenía la respuesta: su regla que **sí** funciona
  (`"source": "/assets/manifest.json"`) usa una **ruta plana**. Las otras usan globs (`/assets/**`,
  `**/*.@(html|xml|txt)`) donde la alternancia `@(…)` califica una **extensión**, no una ruta
  completa. La forma que se inventó —alternancia de rutas absolutas— no aparece en ningún sitio.
- La comprobación que lo destapó no fue un test: fue `curl -sI` leyendo `Cache-Control` de la URL
  en vivo, después de desplegar.

## Diseño

1. **Una regla por ruta**, con `source` igual a la ruta exacta — la forma que ya funciona en este
   fichero. Nada de alternancias de rutas completas.
2. **El chequeo 7 del guard deja de buscar texto y PARSEA `firebase.json`** (el fichero ya importa
   `kotlinx.serialization.json`): para cada puerta, busca la regla cuyo `source` es *exactamente*
   esa ruta y comprueba que su `Cache-Control` lleve `must-revalidate`. Con eso, la regla inerte
   que se desplegó se pone roja.

⚠️ Lo que este testigo **sigue sin poder saber** es si Hosting interpreta un `source` como uno
espera: eso sólo lo dice la cabecera en vivo. Lo que sí garantiza es que la forma usada sea la que
este repo tiene comprobada.

## Criterio de éxito

- Las tres puertas responden `public, max-age=0, must-revalidate` **medido con `curl` contra
  producción**, no sólo en el fichero.
- El guard se pone rojo con la alternancia que se desplegó.
- `./gradlew testAll --rerun-tasks` verde.

## Consumidores auditados

| | Antes | Después |
|---|---|---|
| `firebase.json` · puertas | 1 regla con alternancia, inerte | 3 reglas, una por ruta |
| `firebase.json` · resto | 9 reglas, sin tocar | sin cambio |
| `WebSiteParityGuardrailTest` chequeo 7 | busca texto | parsea el JSON y exige una regla por puerta |

⚠️ **Aparte, y anotado aquí porque se vio en el mismo rato**: correr `node web/build.mjs` en el
**árbol principal** renombra `legal.c62e5db9.css` de vuelta a `legal.223eea16.css` — su checkout
tiene **CRLF** donde los worktrees nuevos tienen LF. Es el mismo defecto de
[`web-a-derived-asset-must-match-its-generator-001.md`](web-a-derived-asset-must-match-its-generator-001.md),
que lo daba por cerrado «porque master lo corrigió»: no estaba cerrado, estaba **alternando** según
quién construya. Se revirtió a mano; el arreglo real (normalizar a LF lo que escribe el build) sigue
sin hacer.


---

## Verificado

- `firebase.json` declara **una regla por puerta**, con `source` igual a la ruta exacta — la forma
  que ya funcionaba en este mismo fichero para `/assets/manifest.json`.
- El chequeo 7 **parsea** el JSON y exige, para cada puerta, una regla propia cuyo `Cache-Control`
  lleve `must-revalidate`.
- **Falsificado con la regla REAL que se desplegó**: volver a poner la alternancia
  `@(/favicon.ico|/favicon.svg|/site.webmanifest)` pone el guard en rojo, igual que dejar una
  puerta con regla propia pero sin `must-revalidate`. Si la alternancia no lo hubiera puesto rojo,
  este arreglo no valdría nada.
- `./gradlew testAll --rerun-tasks` verde · `node web/build.mjs --check` limpio.
- ⏳ **Falta la única prueba que cuenta**: `curl -sI` contra producción después de desplegar. Un
  fichero correcto no es una cabecera correcta — ese fue exactamente el error de origen.

## Errores propios, por si vuelven

- ⛔⛔ **Se empezó a arreglar esto EN EL ÁRBOL PRINCIPAL**, saltándose el worktree. Se revirtió por
  completo (`git checkout --` sobre ficheros que estaban todos commiteados en master, comprobado
  antes) y se rehízo aquí. De paso quedó a la vista que correr el build allí **renombra**
  `legal.c62e5db9.css` a `legal.223eea16.css`: su checkout tiene CRLF.
- ⛔ Un parche que **recorta por posiciones contadas a ojo** (`s.find('        }
    }')`) se llevó
  el cierre de una función y dejó la clase sin compilar, con «Unresolved reference» desde la primera
  línea. Se sustituye siempre un bloque **delimitado por dos anclas exactas**.
