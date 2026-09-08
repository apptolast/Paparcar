# R8-BASELOGIN-IS-LINKED-NOT-REFLECTED-001 · BaseLogin se enlaza, no se refleja — y su `-keep` de paquete entero sobra

**Estado:** ✅ Done · Paso 3 de 7 del plan de R8

Paso 3 de 7 del plan de R8. Los Pasos 1 (`f5edf09b`) y 2 (`aeacc493`) están en master.

## Problema

`app/proguard-rules.pro` ancla la librería de login entera:

```proguard
# BaseLogin (JitPack lib) — keep public API so reflection-based init works.
-keep class com.apptolast.baselogin.** { *; }
-dontwarn com.apptolast.baselogin.**
```

Radio medido con el R8 Configuration Analyzer: **357 clases, 2.569 métodos, 1.052 campos**. En el
DEX son **168 KB de bytecode**, y de esos **125 KB son `com.apptolast.baselogin.presentation`** (164
clases): pantallas y componentes Compose de auth que viajan enteros aunque la app solo use unos
pocos slots. `usage.txt` lo confirma: R8 elimina **0** clases de `com.apptolast.baselogin`.

El comentario que justifica la regla — *"keep public API so reflection-based init works"* — **es
falso, y ahora está comprobado por los dos lados**.

## Doctrina violada

La misma del Paso 2, y por eso este ticket existe: *el `-keep` puesto por si acaso*. Nadie midió si
hacía falta; se escribió una frase plausible en el comentario y ahí lleva desde entonces. Un
comentario que afirma un mecanismo (*"reflection-based init"*) sin que ese mecanismo exista es peor
que no tener comentario: convierte una suposición en documentación, y la siguiente persona ya no la
cuestiona.

## Señales / datos disponibles

**Lado consumidor** — los 20 usos de BaseLogin en `shared/src` + `app/src` son imports directos de
tipos: `AuthRepository`, `AuthState`, `LoginRoute`, `NavTransitions`, `authRoutesFlow`,
`IdentityProvider`, `DefaultAuthContainer`, `LoginScreenSlots`, `appContext`. Enlace estático puro.

**Lado librería** — auditada en `../BaseLogin` en solo lectura (HEAD `5d17339`, *"the session photo
follows the provider…"*, que es el cambio que da nombre a la 2.0.1 que pinea el catálogo; ⚠️ **no
hay tag `2.0.1`**, el último es `2.0.0`, así que la lectura es del árbol local, no de la publicación
exacta). Grep de `Class.forName`, `newInstance`, `getDeclaredConstructor`, `getDeclaredMethod`,
`getDeclaredFields`, `kotlin.reflect`, `ServiceLoader`:

**Cero resultados. BaseLogin no usa reflexión por dentro.**

**Hallazgo colateral que da valor al follow-up:** BaseLogin **no publica ninguna consumer rule** —
ni un `.pro`, ni `consumerProguardFiles` en su `build.gradle.kts`. Por eso cada consumidor suyo se
ve obligado a inventarse reglas a mano, que es exactamente cómo nació la que este ticket borra.

## Diseño

Borrar el `-keep`. Se queda el `-dontwarn`, que no cuesta tamaño y sigue siendo útil mientras la
librería arrastre dependencias opcionales.

No se sustituye por una versión acotada: **no hay nada que acotar**, porque no hay reflexión que
tapar. Lo que mantenga vivas las clases de BaseLogin será, correctamente, su uso real desde el
código.

**Follow-up deliberadamente fuera de alcance** (ticket propio, en el repo de BaseLogin y en su
sesión — ⛔ no se toca desde Paparcar): que BaseLogin publique sus `consumerProguardFiles`. Hoy no
necesita ninguna regla, así que el fichero correcto es el que declaran `androidx.credentials` y
`work-runtime-ktx`: **vacío con un comentario diciendo que la librería es segura de shrinkear**.
Eso convierte "no hace falta ninguna regla" en un hecho versionado, en vez de en algo que cada
consumidor tiene que volver a descubrir.

## Criterio de éxito

1. `:shared:testDebugUnitTest` en verde.
2. Ambos flavors compilan con `allWarningsAsErrors`.
3. El DEX baja y `usage.txt` deja de reportar 0 eliminadas en `com.apptolast.baselogin`.
4. **En el Oppo, con cuenta real de Google** (`rndeveloper…@gmail.com`), sobre el APK **minificado**:
   login con Google, logout, relogin, foto y nombre de perfil, y la pantalla de consentimiento
   previa al login. Es el criterio que manda: lo que se borra es una librería de **auth**, y su modo
   de fallo es quedarse fuera de la sesión, no un crash al arrancar.
5. Que la pantalla de login siga **pintándose** — sus slots son Compose de la librería, y el Paso 2
   ya soltó la retención de composables.

## Consumidores auditados

| sitio | veredicto |
|---|---|
| 20 imports de `com.apptolast.baselogin.*` en `shared/src` + `app/src` | **cubierto** — tipos referenciados estáticamente; R8 los mantiene vivos por alcanzabilidad |
| `appContext` (`BatteryOptimizationRequest.kt:7`) | **cubierto** — propiedad de la librería, enlace directo |
| Slots de UI (`PaparcarAuthSlotsPreviews.kt`, `App.kt`) | **cubierto** — composables llamados desde nuestro árbol |
| Reflexión **dentro** de BaseLogin | **no existe** — grep en `../BaseLogin`, cero resultados |
| Consumer rules de BaseLogin | **no existen** — de ahí el follow-up |

## Medición

Dos `:app:bundleProdRelease` sobre el mismo commit base (`93ee6b12`), en el worktree de la tarea.

| | antes | después | delta |
|---|---|---|---|
| DEX sin comprimir | 18.691.696 B (17,8 MB) | **18.344.524 B (17,5 MB)** | **−347.172 B (−339 KB)** |
| clases en el DEX | 21.812 | **21.632** | −180 |
| **ofuscación** | 42,2 % | **43,4 %** | **+1,2 pp** |
| `com.apptolast.baselogin` eliminadas por R8 | **0** | **71** | +71 |
| `com.apptolast.baselogin` vivas en el DEX | 354 | 243 | −111 |
| …de ellas, **sin ofuscar** | **354** | **1** | −353 |
| `…baselogin.presentation` eliminadas | 0 | 25 | +25 |

Aquí la ofuscación **sube**, al revés que en el Paso 2, y por la razón simétrica: se desanclan 354
clases, así que las que sobreviven pasan del lado "sin ofuscar" al lado "renombrada" y además 71
desaparecen. Numerador y denominador se mueven a favor. Es la primera vez en el plan que la métrica
que Play enseña en rojo mejora de verdad, y es la prueba de que los Pasos 5-6 —que desanclan bloques
20 veces mayores— son los que van a moverla.

**La única clase de BaseLogin que sigue sin ofuscar es `FirebaseAuthFailure`**, y no la retiene nada
suyo: la ancla **nuestra** `-keep public class * extends java.lang.Exception` (línea 7 del `.pro`),
que es justo la que toca el Paso 6. Confirmación incidental de su alcance real.

## Verificación

| criterio | resultado |
|---|---|
| `:shared:testDebugUnitTest` | ✅ **2.407 tests, 0 fallos, 0 errores** |
| ambos flavors con `allWarningsAsErrors` | ✅ |
| `:app:bundleProdRelease` | ✅ |
| **sesión ya iniciada sobrevive al upgrade** | ✅ Oppo, `install -r` sobre la sesión existente (sha device ↔ local `2e8f45f4…`), `force-stop` + arranque en frío → **directo a Home** con vehículo y sesión de aparcamiento. BaseLogin restaura la sesión sin el `-keep`. |
| perfil completo | ✅ nombre, email y **foto de avatar cargada** (Coil sobre el `photoUrl` de `providerData`) |
| **logout** | ✅ vuelve a la pantalla de login — y sin volver a pedir el consentimiento, que ya estaba aceptado |
| **relogin con Google** | ✅ selector de cuentas → Home con el vehículo y la sesión restaurados de Firestore |
| errores en nuestro proceso | ✅ **cero** `FATAL` / `NoClassDefFound` / `ClassNotFound` / `no definition found` en todo el ciclo |

⚠️ **Ruido de GMS que NO es nuestro y no debe confundirse con una regresión:** el log trae
`Phenotype.API is not available` y `GoogleCertificatesRslt: not allowed: pkg=com.rndeveloper.paparcar,
sha256=[668aa14e…]`. Es el servicio de flags interno de GMS rechazando la **llave de subida** con la
que se firman los builds locales; la app de Play va firmada por Play y no lo ve. No afecta al login
—verificado dos veces de punta a punta— ni tiene relación con R8. Aparecía ya antes de este ticket.
