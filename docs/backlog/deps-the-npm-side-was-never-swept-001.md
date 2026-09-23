# DEPS-THE-NPM-SIDE-WAS-NEVER-SWEPT-001 · El barrido de dependencias nunca miró el lado npm

**Estado:** ✅ Done · rama `chore/DEPS-THE-NPM-SIDE-WAS-NEVER-SWEPT-001-npm-deps` · worktree
`../Paparcar-npm-deps`

## Problema

GitHub avisa de **24 vulnerabilidades** en `master` — 2 críticas, 9 altas, 13 medias. Salió al
pushear el 23-09; el aviso lleva saliendo en cada push desde hace tiempo y nadie lo había abierto.

Las 24 son **npm**, y **ninguna toca la app**:

| Dónde | Alertas | Qué es | ¿Se despliega? |
|---|---|---|---|
| `firestore-rules-tests/` | **23** | `devDependencies` del harness de los 30 tests de reglas [TEST-RULES-ARE-EXECUTED-NOT-READ-001] | No — y ningún workflow de `.github/workflows/` lo referencia: se corre a mano |
| `functions/` | **1** | `uuid@9.0.1`, transitivo bajo `gaxios` ← `google-auth-library` ← `firebase-admin` | Sí: es lo único que llega a producción |

Por paquete: **tar ×12**, vite ×3, vitest ×2, uuid ×2, esbuild, csv-parse, stream-json,
`@opentelemetry/core`.

## Doctrina violada

[BUILD-DEPS-AT-LATEST-STABLE-002] dejó el catálogo de Gradle en última estable y escribió en
CLAUDE.md cómo comprobarlo (`maven-metadata.xml`, `group-index.xml`, nunca `search.maven.org`). Ese
barrido **sólo miró `gradle/libs.versions.toml`**. El repo tiene además dos proyectos Node con su
propio `package-lock.json`, y a esos no los mira nadie: el stack «al día» lo estaba sólo en el lado
que alguien se acordó de auditar.

⛔ No es una vulneración de seguridad, es una de **alcance**: la regla decía «el stack está al día» y
resultó significar «la mitad del stack que sabíamos que existía».

## Señales / datos disponibles — por qué NINGUNA de las 24 muerde

El dato que importa no es la severidad que imprime GitHub, es si el camino de ataque existe aquí.

- **#18 · tar, CRÍTICA, «Decompression/parse DoS via unlimited input».** El `tar` de primer nivel es
  **6.2.1**; el `7.5.22` que aparece al mirar por encima es una copia anidada bajo `node-gyp` y
  despista. Quien descomprime es `firebase-tools`, y lo que descomprime son los binarios del emulador
  que **baja de Google**. No entra un archivo de un tercero. Las otras once de tar son la misma
  cascada: el mismo paquete con parches sucesivos, una alerta por versión.
- **#11 · vitest, CRÍTICA, «When Vitest UI server is listening».** El script es `vitest run`, sin
  `--ui` ni watch — comprobado, es el único sitio del repo que invoca vitest. Ese servidor no se
  levanta nunca.
- **vite ×3 y esbuild ×1**: ataques al **dev server** desde una web maliciosa. `vitest run` no lo abre.
- **uuid ×2, la única en producción**: el fallo es un bounds check que sólo se alcanza llamando a
  `v3`/`v5`/`v6` y **pasando un buffer propio**. `functions/src/` no usa `uuid` en absoluto — lo
  arrastra `gaxios` para ids de petición.

**Riesgo real para usuarios: ninguno. Deuda de higiene: real.**

## Diseño

El sistema no es «parchear los ocho paquetes que salen en la lista»: es **poner las dos raíces npm en
última estable**, porque todas las alertas son transitivas y desaparecen solas al subir el padre que
las arrastra. Parchear hijos con `overrides` sería el parche; subir los padres es el arreglo.

Las seis raíces, todas una major por detrás:

| Proyecto | Paquete | Ahora | Última |
|---|---|---|---|
| `firestore-rules-tests` | `vitest` | ^2.1.0 | 5.0.1 |
| | `firebase-tools` | ^14.0.0 | 15.30.2 |
| | `firebase` | ^11.0.0 | 12.19.0 |
| | `@firebase/rules-unit-testing` | ^4.0.1 | 5.0.2 |
| `functions` | `firebase-admin` | ^13.0.0 | 14.4.0 |
| | `firebase-functions` | ^6.0.0 | 7.4.0 |

⛔ **No es un `npm audit fix`.** `vitest` sube **tres majors** y `firebase-admin`/`firebase-functions`
una cada uno: el criterio de éxito no es que la alerta desaparezca, es que los 30 tests sigan
pasando y que las functions sigan compilando.

## Criterio de éxito

1. `npm test` en `firestore-rules-tests/` → ✅ **31 tests en verde** (5 ficheros) con vitest 5.0.1
   contra el emulador de Firestore 1.22.0. El doc de apertura decía 30: eran 31.
2. `npm run lint` (`tsc --noEmit`) + `npm test` en `functions/` → ✅ compila y **26/26**.
3. `npm audit` → ✅ **0** en `functions`; 🟡 **1 advisory** (contada 2 veces, por dos rutas) en
   `firestore-rules-tests`, dejada a propósito — ver abajo.
4. Los dos `package-lock.json` regenerados. ⛔ El de `firestore-rules-tests` hubo que **borrarlo y
   rehacerlo**: con el viejo puesto, npm ancla la resolución en `vitest@2.1.9` y `npm install` muere
   en `ERESOLVE` contra el peer `vite` del vitest nuevo. En un barrido de majors, el lockfile no se
   actualiza: se regenera.

## Resultado

**24 alertas → 1.** Las 2 críticas y las 9 altas se fueron con el bump de los padres; no hizo falta
tocar ni una de ellas por su nombre.

| | Antes | Después |
|---|---|---|
| `firestore-rules-tests` | 23 (2 críticas, 9 altas, 12 medias) | 1 media |
| `functions` | 1 media | 0 |

Subidas: `vitest` 2.1.9→5.0.1 · `firebase-tools` 14→15.30.2 · `firebase` 11→12.19.0 ·
`@firebase/rules-unit-testing` 4→5.0.2 · `firebase-admin` 13→14.4.0 · `firebase-functions` 6→7.4.0.
Ni un cambio de código: la API que usan los tests (`describe`/`test`/`expect`, `defineConfig` con
`fileParallelism`, el modular de `firebase/firestore`, `initializeTestEnvironment`) es la misma en
las seis.

## ⛔ Lo que queda, y por qué se queda

Las 4 que sobrevivían al bump son deuda **transitiva de `firebase-tools`**, que ya está en su última:
no hay padre que subir. `npm audit fix --force` propone **bajar firebase-tools a 10.1.1**, cinco
majores atrás — eso no es un arreglo, es tirar la herramienta.

El lever que queda es `overrides`, y **hay que probarlo, no razonarlo**:

| Paquete | Override | Resultado |
|---|---|---|
| `uuid` (bajo `gaxios`) | `^11.1.1` | ✅ Aplicado en LOS DOS proyectos. `gaxios` sólo llama a `uuid.v4()` para un boundary multipart —comprobado en su `build/src/gaxios.js:417`—, y el fallo está en `v3/v5/v6` con buffer. Verificado que `v4` sigue existiendo en CJS |
| `csv-parse` | `^7.0.2` | ✅ Aplicado |
| `@opentelemetry/core` | `^2.8.0` | ✅ Aplicado |
| `stream-json` | `^3.5.0` | ⛔ **REVERTIDO — rompe firebase-tools.** 3.5.0 reorganizó sus ficheros y `require('stream-json/src/filters/Pick')` deja de resolver: `firebase deploy --help` y `emulators:exec --help` mueren con «An unexpected error has occurred». `firebase --version` seguía respondiendo, que es justo lo que hace que este fallo se cuele |

Por eso queda **GHSA-528h-pc64-c93x** (`stream-json` ≤3.4.0, media): DoS por JSON anidado
maliciosamente. Aquí no hay JSON de un tercero — sólo corremos `emulators:exec` y `deploy`. Se cierra
cuando firebase-tools suba su propia dependencia.

## Consumidores auditados

- `functions/src/` — **no usa `uuid`** (grep vacío). Lo arrastran `gaxios`/`google-gax`/`teeny-request`.
  Su API de `firebase-functions` es toda `v2` (`onDocumentCreated`, `onDocumentWritten`,
  `setGlobalOptions`, `logger`) y la de `firebase-admin` toda modular (`app`, `firestore`,
  `messaging`): ningún símbolo tocado por las majors.
- `firestore-rules-tests/test/` — 5 ficheros, ningún símbolo retirado en vitest 3/4/5.
- **`firebase-tools` no es sólo del harness**: con él se despliegan `hosting` (paparcar.com) y
  `functions`. Por eso la verificación no es «los tests pasan», es además que `firebase deploy --help`
  y `emulators:exec --help` sigan cargando — que es lo que cazó el override de `stream-json`.
- `.github/workflows/` — ninguno de los tres (ci, distribute-alpha, distribute-beta) referencia
  `firestore-rules-tests/` ni `functions/`: nada de CI se apoya en estas versiones.

## Follow-up

Ninguno abierto. La lección de alcance —que el barrido de dependencias sólo miraba Gradle— queda
escrita en CLAUDE.md junto a [BUILD-DEPS-AT-LATEST-STABLE-002].

