# DATA-A-DB-IT-CANNOT-OPEN-MUST-NOT-DEAD-END-THE-USER-001 · Cuando Room no puede abrir la base, la app se queda inarrancable para siempre

**Estado:** ✅ Done · rama `bugfix/DATA-A-DB-IT-CANNOT-OPEN-MUST-NOT-DEAD-END-THE-USER-001-db-recovery` · worktree `../Paparcar-db-recovery`
> Se arregló el **bucle** (punto 1 del diseño). El cinturón de `dataExtractionRules` y el copy
> quedan fuera, con motivo, en *Lo que NO se hizo*.
> Encontrado el **2026-09-03** en el `/run` de master `6fd0b7c9` sobre Oppo y Redmi, mientras se
> verificaba otra cosa. No lo causa ningún ticket: es un hueco de robustez preexistente.

## Problema

En los dos móviles de banco, con el build de master, la app arrancaba así:

```
E SplashViewModel: Room wipe failed
E SplashViewModel: java.lang.IllegalStateException: Room cannot verify the data integrity.
  Expected identity hash: e8bc446bd23663f9f0febbeda06c375e
  found:                  03390c9c44e62b92f2c0e85cf92e5eaa
```

El usuario ve **"No se pudo cargar tu cuenta · Algo salió mal al cargar tus datos. Por favor, vuelve
a iniciar sesión"**, se le desloguea, y al volver a entrar pasa exactamente lo mismo. **No hay salida
desde dentro de la app**: la única cura es borrar los datos desde los ajustes del sistema, algo que
un usuario normal no sabe hacer y que nadie le dice.

Dos hechos separados, y conviene no mezclarlos:

1. **Por qué la base era ilegible.** Es el caso 3 documentado en
   `DATA-ROOM-RETURNS-TO-VERSION-ONE-001`: un v1 VIEJO, mismo número de versión y distinto
   `identityHash`. Room decide por VERSIÓN, así que ni migra ni lo distingue. Estaba previsto y su
   remedio era `pm clear` en los móviles de banco.
2. **Por qué la app no se recuperó.** Esto NO estaba previsto. Room intentó su borrado destructivo
   y **falló** (`Room wipe failed`), y el `catch` de arriba se limita a desloguear y enseñar un
   diálogo. Un fallo al abrir la base debería terminar en *base borrada y app arrancando*, no en un
   callejón sin salida.

## Doctrina violada

`feedback_error_handling_mandatory` pide causa + consecuencia + remedio y cero callejones. Aquí el
copy es correcto en tono pero **el remedio que ofrece es falso**: dice "vuelve a iniciar sesión", y
volver a iniciar sesión no arregla nada porque el problema no es la sesión.

## Señales / datos disponibles — lo MEDIDO el 03-09

| Hecho | Cómo se midió |
|---|---|
| Oppo llevaba el hash viejo `03390c9c…` | logcat en device |
| El borrado de emergencia de Room falló | `E SplashViewModel: Room wipe failed` |
| El Redmi **no tenía** `com.rndeveloper.paparcar` instalado… | `pm list packages` (solo `io.apptolast.paparcar.test` y `…paparcar.mock`) |
| …y aun así arrancó con la base vieja | logcat tras la primera instalación |
| Auto-restore está activo y el paquete es participante | `dumpsys backup` → *Auto-restore is enabled* + `com.rndeveloper.paparcar` en la lista |
| `android:allowBackup="true"` | `app/src/main/AndroidManifest.xml:52` |

⇒ La copia automática de Android **restaura la base vieja en una instalación PRIMERA**. Eso corrige
lo que `DATA-ROOM-RETURNS-TO-VERSION-ONE-001` daba por sentado ("para Play es inocuo, el primer
install no tiene fichero previo").

⚠️ **No medido**: que la restauración cruce firmas distintas. Android exige el mismo certificado, y
de ahí sale el juicio de más abajo — pero es razonamiento, no una comprobación de este `/run`.

## ¿Bloquea el lanzamiento?

**No, y conviene decirlo con su porqué en vez de asumirlo.** Una copia solo se restaura sobre una
instalación firmada con el MISMO certificado. Los builds de campo van con la clave de debug; el de
Play irá con el keystore de release (`CN=Paparcar`) más la firma de Play App Signing. Ningún usuario
real puede tener hoy una copia restaurable de este paquete hecha por otro firmante.

Lo que sí queda vivo tras el lanzamiento, y es la razón de que el ticket exista:

- Cualquier base ilegible **por cualquier motivo** (corrupción de disco, un cambio de esquema futuro
  sin bump de versión) deja al usuario sin app y sin remedio a mano.
- A partir de 1.0.0, las reinstalaciones ya son release↔release: misma firma, y entonces sí restaura.

## Diseño propuesto

1. **El arreglo que importa** — que un fallo al abrir la base sea recuperable: si Room no abre,
   borrar el fichero y reintentar UNA vez antes de rendirse. La app arranca vacía (offline-first: la
   verdad está en Firestore y se resincroniza) en vez de morir. Hoy `Room wipe failed` no tiene
   plan B.
2. **Cinturón, barato** — sacar la base de la copia automática con `dataExtractionRules`
   (`<exclude domain="database" path="paparcar.db"/>` y sus `-wal`/`-shm`). Sin esto, una copia
   puede resucitar una base incompatible en una instalación limpia. Con `allowBackup="true"` intacto
   para el resto (prefs, sesión), que es lo que el usuario agradece al cambiar de móvil.
3. **El copy** deja de prometer un remedio que no cura: si la app no ha podido abrir sus datos
   locales, decir eso y ofrecer el reintento que sí los borra.

## Criterio de éxito

- Test: con un fichero de base ilegible en disco, la app **arranca** (no lanza) y la base queda
  vacía. Extiende `AppDatabaseV1BaselineTest`, que ya construye los tres ficheros de partida.
- En device: reproducir el caso 3 y comprobar que el segundo arranque entra, sin `pm clear`.
- `dumpsys backup` deja de listar la base entre lo respaldado.

## Lo construido

El arreglo vive en **`buildAppDatabase`**, que ya se declaraba *"el único sitio que dice CÓMO se abre
la base"*. Ahí:

1. Se abre el fichero **ahora** (`openHelper.writableDatabase`) en vez de en la primera consulta.
   `build()` no toca el disco; `writableDatabase` ejecuta el `onOpen` de Room, que es donde vive el
   chequeo de identidad y donde revienta.
2. Si no abre → se cierra, `context.deleteDatabase(name)` (se lleva `-wal`/`-shm`/`-journal`) y se
   reconstruye. La base local es una **caché**: su verdad se re-baja de Firestore en el siguiente
   bootstrap.
3. El segundo intento **no se sondea**. Un fichero recién creado que aun así se niegue es un fallo
   real, y debe aflorar donde siempre (la primera consulta) en vez de convertirse en un error del
   grafo de Koin que se lee como otra cosa.

Se captura `Throwable`, no `Exception`: hoy llega como `IllegalStateException`, pero lo que importa
es *"el fichero no abrió"*, y estrechar el tipo dejaría que la próxima versión de Room reintrodujera
el callejón por una vía que no predijimos.

**Por qué ahí y no en `SplashViewModel`**: el mismo error rompía el bootstrap **y** el
`clearAllTables()` del sign-out (`Room wipe failed`) **y** al `GeofenceJanitorWorker`. Parchear el
arranque habría dejado los otros dos. Un solo sitio, todos los consumidores.

⚠️ **Coste asumido**: la apertura se adelanta al momento en que Koin resuelve el `single`, unos ms
de disco que antes ocurrían en la primera llamada a un DAO.

## Lo que NO se hizo, y por qué

- **iOS** (`IosPlatformModule`) tiene la MISMA forma y ninguna recuperación. No se toca: Kotlin/Native
  de iOS no compila desde Windows, y escribir a ciegas código que borra ficheros del usuario es peor
  que dejar el hueco anotado. **Debe hacerse cuando iOS sea real**, y el `single` de allí es el sitio.
- **`dataExtractionRules`** (sacar la base de la copia automática): sigue mereciendo la pena como
  cinturón, pero ya no es lo que separa al usuario del bucle. Toca el manifiesto justo antes de
  publicar; decisión aparte.
- **El copy del diálogo**: con la base auto-reparándose, ese diálogo ya solo se alcanza por fallos de
  bootstrap donde *"vuelve a iniciar sesión"* sí es un remedio plausible. Cambiarlo ahora sería
  arreglar la frase para un caso que dejó de existir.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `SplashViewModel` — el `catch` que desloguea y su `localSessionCache.wipe()` | **Cubierto sin tocarlo**: la base ya abre, así que ni el bootstrap ni el `clearAllTables()` vuelven a fallar. Era el síntoma, no el sitio |
| `GeofenceJanitorWorker` | **Cubierto sin tocarlo** — misma excepción, mismo origen, misma cura |
| `App.kt` `BootstrapFatalDialog` | **Cerrado como fuera de alcance** — ver arriba |
| `buildAppDatabase` / `ALL_MIGRATIONS` | **Tocado**: el registro de migraciones queda intacto; solo se envuelve la apertura |
| `AppDatabaseV1BaselineTest` caso 3 | **Invertido**: afirmaba "rechazado para siempre"; ahora afirma "borrado → abre vacío". Su KDoc ya avisaba de que si alguien lo hacía abrir, el fallo sería buena noticia |
| `AppDatabaseDowngradeTest` | **Verde sin cambios** — el camino de downgrade no se ha tocado |
| `IosPlatformModule` | **Hueco conocido, documentado arriba** |

## Estado de verificación

```
:shared:testDebugUnitTest --rerun-tasks → 2175 tests · 0 failures · 0 errors
  AppDatabaseV1BaselineTest: 4 casos (el 3º invertido, +1 nuevo "no borrar lo sano")
:app:compileMockDebugKotlin :app:compileProdDebugKotlin → BUILD SUCCESSFUL
```

⏳ **Falta en device**: reproducir el caso 3 en un móvil y ver que el segundo arranque entra sin
`pm clear`. Los dos del banco están ya limpios, así que hay que sembrar el fichero viejo a mano.
