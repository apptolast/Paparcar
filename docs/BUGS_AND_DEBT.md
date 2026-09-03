# Paparcar — Bugs y deuda técnica

> **Doc vivo.** Inventario de lo que **sigue abierto hoy**. Verificado contra master `46621e7f` el
> **2026-08-30**: cada punto se comprobó en el árbol (`grep`/`ls`), no de memoria.
> Sustituye a `Gemini_Potential_Fixes.md`, **borrado** en este mismo barrido por estar reemplazado
> por completo (recuperable en el histórico de git).
>
> **Qué NO vive aquí:** los bugs de detección con ticket propio — esos están en
> [`ROADMAP.md`](./ROADMAP.md) y en [`backlog/`](./backlog/), que es donde se trabajan. Este doc
> recoge la deuda **estructural**: la que no es un ticket porque no tiene arreglo de una tarde.
>
> La versión de mayo de este inventario (17 secciones, 15 ya cerradas) queda en el histórico de git:
> `git show 9946ae94:docs/BUGS_AND_DEBT.md`. No se conserva aquí porque un doc vivo lleno de ✅ de
> hace tres meses no informa, estorba.

---

## 1 · iOS no arranca Firebase — falta `GoogleService-Info.plist`

**Severidad:** alta (para iOS) · **Verificado:** `ls iosApp/iosApp/` no lo contiene.

`iOSApp.swift` llama a `FirebaseApp.configure()` y el plist no está en el proyecto: en runtime la
inicialización falla **en silencio**. Cualquier build iOS que se distribuya hoy arranca sin Auth ni
Firestore y sin decirlo.

**Cierre:** Firebase Console → Add iOS app (bundle `com.rndeveloper.paparcar`) → descargar el plist →
añadir al target `iosApp` con *Copy items if needed*. ~20 min, bloqueado solo por hacerlo.

---

## 2 · Los schedulers de iOS no sobreviven a la muerte del proceso

**Severidad:** alta (para iOS) · **Verificado:** `iosMain/detection/IosParkingSyncScheduler.kt`,
`IosParkingEnrichmentScheduler.kt`, `IosReportSpotScheduler.kt`.

Son coroutine + retry sobre un scope propio. Funcionan mientras el proceso viva; si iOS lo mata a
mitad de un sync, **el trabajo se pierde sin reintento**. En Android ese papel lo hace WorkManager,
que persiste.

**Cierre:** `BGProcessingTask` con `requiresNetworkConnectivity`, identifier registrado en
`Info.plist` (`BGTaskSchedulerPermittedIdentifiers`) y el `sessionId` pendiente en `NSUserDefaults`
para sobrevivir al kill. Estimado ~4 h — ver [`IOS_PLAN.md`](./IOS_PLAN.md).

> ✅ Ya **no quedan stubs** en `iosMain`: las 11 piezas de detección tienen implementación nativa
> real. Lo que falta no es "rellenar un stub", es el ciclo de vida.

---

## 3 · iOS no tiene quien alimente al coordinator

**Severidad:** alta (para iOS) · **Estado:** Fase 0 en la rama `feature/IOS-F0-001-fase0`, sin mergear.

Las señales existen (`CLLocation`, `CMMotion`, geocercas, pasos), pero no hay equivalente al
foreground service que en Android empuja el stream de GPS al detector. Sin ese lazo, la estrategia
probabilística no corre en iOS aunque todas sus piezas estén.

`IOS-F0-001` prepara el terreno (puertos, capacidades, harness). 🔵 **Lo valida un compañero con Mac**:
desde este entorno Windows no se puede compilar K/N para iOS — de ahí que el CI tenga desde `02a29f62`
un job `macos-latest` que al menos compila `iosMain` en cada push.

---

## 4 · Room vive de un destructivo que caduca el día del lanzamiento

**Severidad:** media hoy, **crítica el día 1** · **Verificado:** `AppDatabase.kt` (`version = 1`),
`AndroidAppDatabase.kt:27` y `IosPlatformModule.kt:40` (`fallbackToDestructiveMigration(dropAllTables = true)`).

La cadena v2..v20 y sus 16 esquemas se borraron a conciencia: describían upgrades de bases que solo
existieron en nuestros propios móviles. v1 es la línea base y el destructivo es **correcto mientras no
haya usuarios**. [DATA-ROOM-STARTS-AT-VERSION-ONE-001]

⚠️ **El primer release público invierte el signo de esta decisión.** Desde ahí hay datos que deben
sobrevivir y todo cambio de esquema necesita `Migration` + schema exportado + `MigrationTestHelper`.
No hay recordatorio automático de eso: está escrito en el KDoc de `AppDatabase` y aquí.

> Ya se ensayó una vez: `DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001` bumpeó a v2 el 01-09 con los
> móviles de banco como "usuarios", y `DATA-ROOM-RETURNS-TO-VERSION-ONE-001` lo retiró el 03-09 al
> borrarse esos datos. La tubería (`ALL_MIGRATIONS` compartido, registrado en los dos builders)
> sobrevivió vacía a propósito: lo caro no era la migración, era descubrir dónde va.

> El comportamiento del downgrade está **medido**, no supuesto: `AppDatabaseDowngradeTest` demuestra
> que no crashea. Ese test existe precisamente porque la documentación de Room dice lo contrario.
> `AppDatabaseV1BaselineTest` mide además el agujero que ningún destructivo tapa: **misma versión +
> hash distinto** rechaza el fichero en cada open, para siempre.

---

## 5 · Clave de Maps sin rotar y sin restringir

**Severidad:** alta · **Parte código ✅ / parte consola ⏳ usuario.**

El build ya falla rápido en release si falta `MAPS_API_KEY`, y el modelo de seguridad está escrito en
[`release/RELEASE-SECURITY.md`](./release/RELEASE-SECURITY.md). Lo que falta no se arregla con código:

- **Rotar** la clave — estuvo hardcodeada en commits antiguos y sigue siendo recuperable con `git log`.
- **Application restrictions**: package `com.rndeveloper.paparcar` + SHA-1 de debug y release.
- **API restrictions**: solo *Maps SDK for Android*.

Y desplegar las reglas mínimas de Firestore de `RELEASE-SECURITY.md §2`. ⚠️ El MCP `firebase_deploy`
es un **no-op silencioso**: hay que usar la CLI y verificar.

---

## 6 · El flavor `mock` se rompe en las pantallas de auth

**Severidad:** media (solo desarrollo) · **Ticket:** `MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001`.

En `mock`, pulsar "Sign Up" mata la app; en `prod` está sana. La causa es de dependencias de BaseLogin
que el módulo mock no bindea. Lo arregla de verdad **publicar BaseLogin 2.0.0 en Maven Central** — que
es una sesión propia y aparte, porque el rename cambia TODOS los imports del proyecto.

> ⛔ BaseLogin no se toca desde este repo.

---

## Riesgos estructurales (no son bugs hoy)

### R1 · La detección en background depende de que el OEM la deje vivir
**Probabilidad alta · impacto en el core.** Xiaomi/Redmi y ColorOS estrangulan servicios y
WorkManager; un force-stop borra además las geocercas. Ya hemos medido muertes reales en campo.

Mitigación **ya construida**: nudge de exención de batería, instrucciones de autostart por OEM,
heartbeat exacto, `ParkingSafetyNetWorker` + sensor de movimiento, y `GeofenceJanitorWorker` para
restaurar tras reboot/reinstall. Ninguna lo elimina — lo acotan.

Mitigación **pendiente de medida**: `DET-BROADCAST-QUEUE-STALL-001` y `DET-HEARTBEAT-LANE-REPAIR-001`
esperan a que el Oppo vuelva a fallar para decidir con datos.

### R2 · Google Play y el permiso de ubicación en background
**Probabilidad media · impacto: retraso indefinido.** Play endurece cada año la justificación de
`ACCESS_BACKGROUND_LOCATION` + `FOREGROUND_SERVICE_LOCATION`.

Mitigación: la disclosure del onboarding ya dice exactamente lo que declara el formulario de Data
Safety [ONB-DISCLOSURE-MATCHES-DATA-SAFETY-001], la política de privacidad está publicada en hosting
propio, el borrado de cuenta tiene ruta web, y la ficha promete **solo** *"sabe dónde aparcaste"*.
Prometer detección infalible en la ficha sería el error caro.

### R3 · GitLive es un wrapper de terceros sobre Firebase
Si Google mueve el SDK oficial y GitLive no sigue, nos quedamos atrás. Plan B: `expect/actual` propio
sobre el SDK oficial de Android. No hay señal de que haga falta hoy.

### R4 · El fork propio de kmp-maps
`io.github.rndevelo.kmpmaps:core:0.9.1-puck4` es nuestro (upstream 0.9.1 + PR #170 sin mergear). Nos
da el marker de id estable que el flicker exigía, y a cambio **la versión la mantenemos nosotros**: si
upstream avanza, el rebase del fork es trabajo nuestro.

### R5 · Specs que solo viven en una rama
Once ramas sin mergear llevan **dentro** su `docs/backlog/<id>.md`, de modo que el backlog de master
no las ve. Listadas en [`ROADMAP.md § En vuelo`](./ROADMAP.md) para que no desaparezcan. Es el mismo
agujero que motivó `IOS-SOCIAL-LOGIN-001`.

---

## Higiene medida (para no repetir auditorías)

| Métrica | Valor el 2026-08-30 |
|---|---|
| `TODO(` / `FIXME` en `shared/src` | **0** |
| Stubs en `iosMain` | **0** |
| Ficheros de test | 191 en `commonTest` + 16 en `androidUnitTest` |
| Guardarraíles Konsist | 10 (`architecture/`) |
| Ficheros Kotlin `:shared` + `:app` | ~809 |
| Warnings de compilación | 0 — `-Werror` activo [BUILD-ZERO-WARNINGS-IS-ENFORCED-001] |
