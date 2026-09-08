# R8-REVENUECAT-ALREADY-NARROWED-ITS-RULES-001 · No hacía falta escribirles: lo arreglaron el 1 de septiembre y solo hay que subir la versión

**Estado:** ✅ Done · Paso 7 de 7 — plan de R8 COMPLETO

Paso 7 y último. Pasos 1-6 en master (`f5edf09b`, `aeacc493`, `9904b72e`, `348a40ab`, `16cea83e`,
`0362a547`): DEX 20,19 → 13,40 MB, ofuscación 43,6 → 72,8 %.

## Problema

El AAR de `com.revenuecat.purchases:purchases:10.19.1` que llega a la app trae como primera línea de
sus consumer rules:

```proguard
-keep class com.revenuecat.** { *; }
```

Medido sobre el DEX de master `0362a547`:

| | |
|---|---|
| clases vivas | **2.919** |
| clases eliminadas por R8 | **0** |
| clases sin ofuscar | **2.893** |
| bytecode | **1.015.648 B en 17.362 métodos** |
| **share del bytecode de la app** | **14,3 %** |
| **share de lo que aún viaja sin ofuscar** | **2.893 de 4.361 = 66 %** |

Tras los seis pasos anteriores, es con diferencia el mayor bloque no reducible que queda.

## El giro: el plan estaba equivocado sobre qué hacer

Este paso estaba planificado desde el principio como *"no se puede tocar desde la app, hay que abrir
un issue upstream"*. **Era falso**, y lo era desde antes de empezar el plan.

Al ir a redactar ese issue —su plantilla de bug pide haber buscado issues existentes, y al hacerlo
apareció— se encontró el PR **[#3557 «Narrow consumer R8 rules»](https://github.com/RevenueCat/purchases-android/pull/3557),
mergeado el 01-09-2026**, cuyo propio cuerpo cita nuestra línea:

> *The SDK's consumer Proguard rules kept the entire SDK: `-keep class com.revenuecat.** { *; }`.
> This means that all the code of the SDK is kept and included in our customers apps, which is not
> great. This PR attempts to reduce what we keep to a few strategic locations…*

Descargado el AAR de **`purchases:10.20.0`** y leído su `proguard.txt`: **el `-keep` de paquete
completo ya no existe.** En su lugar, exactamente lo quirúrgico, con su porqué escrito al lado:

```proguard
# EnumDeserializerWithDefault matches JSON against enum CONSTANT NAMES read reflectively via
# Class.enumConstants … Keep enum constant names so an obfuscated name can't turn into a silent
# wrong-value bug.
-keepclassmembers enum com.revenuecat.** { <fields>; public static **[] values(); public static ** valueOf(java.lang.String); }

# The framework instantiates Parcelables reflectively through the CREATOR field…
-keepclassmembers class com.revenuecat.** implements android.os.Parcelable { public static final ** CREATOR; }
```

También han retirado su bloque `-if @kotlinx.serialization.Serializable`, porque hoy lo publica
`kotlinx-serialization-core` — el mismo razonamiento que aplicó nuestro Paso 2.

## Doctrina violada

Ninguna del proyecto. La que falló es de método, y merece quedar escrita porque costó seis pasos de
suposición: **antes de redactar un issue upstream, comprobar si ya está arreglado.** Buscar en sus
issues y PRs *cerrados*, y leer el fichero real del artefacto publicado, no el que resuelve tu build.
Aquí bastaba con `unzip -p purchases-10.20.0.aar proguard.txt`.

## Señales / datos disponibles

- `purchases-kmp-core-android:3.7.0` (publicada el 03-09, la última) pina `purchases:10.19.1` con
  **`<scope>runtime</scope>`**. No hay versión más nueva del wrapper KMP.
- Que sea `runtime` importa: el wrapper compila contra la API de 10.19.1, pero la clase que se carga
  en el dispositivo es la que resuelva Gradle. Subir el artefacto de Android por debajo del wrapper
  es un cambio de **resolución**, no de compilación.
- 10.19.1 → 10.20.0 es un salto **menor dentro de la misma major**, donde se espera compatibilidad
  binaria.

## Diseño

Declarar explícitamente el artefacto de Android en `androidMain`, con su alias en el catálogo, para
que Gradle resuelva 10.20.0 en vez de la 10.19.1 transitiva. No se toca `purchases-kmp-core`.

Cuando `purchases-kmp` publique una versión que ya pine ≥10.20.0, esta declaración explícita **debe
retirarse**: es un puente, no un destino. Queda anotado en el propio `libs.versions.toml`, que es
donde lo verá quien suba la versión.

## Criterio de éxito

1. `:shared:testDebugUnitTest` en verde y ambos flavors con `allWarningsAsErrors`.
2. `./gradlew :app:dependencies` resuelve `purchases:10.20.0`.
3. El DEX baja y `usage.txt` deja de reportar 0 eliminadas en `com.revenuecat`.
4. **En el Oppo, sobre el APK minificado** — y aquí el listón es más alto que en los pasos
   anteriores, porque **un fallo aquí es dinero**:
   - el SDK configura al arrancar sin excepción,
   - **el paywall abre y muestra el precio real traído de Play Billing** (13,99 €), que es la prueba
     de que la comunicación con la tienda sigue viva.

## Consumidores auditados

| sitio | veredicto |
|---|---|
| `purchases-kmp-core` en `commonMain` | **no se toca** — se mantiene 3.7.0; solo se sube el artefacto Android que resuelve por debajo |
| Uso del SDK en `:app` (`Purchases.configure` en el arranque) | **a verificar en device** — es el punto donde una incompatibilidad binaria se manifestaría |
| `PaywallScreen` y el repositorio de entitlements | **a verificar en device** — precio real desde Play Billing |
| Reglas nuestras sobre RevenueCat en el `.pro` | **no existen** — nunca escribimos ninguna; el `-keep` era enteramente suyo |

## Medición

Dos `:app:bundleProdRelease` sobre el mismo commit base (`ff1a8c9b`).

| | antes (10.19.1) | **después (10.20.0)** | delta |
|---|---|---|---|
| DEX sin comprimir | 14.050.028 B — **2 ficheros** | **11.330.364 B (10,8 MB) — 1 fichero** | **−2.719.664 B (−2,59 MB)** |
| clases en el DEX | 16.035 | **14.343** | −1.692 |
| **ofuscación** | 72,8 % | **89,7 %** | **+16,9 pp** |
| `com.revenuecat` vivas | 2.919 | **1.263** | −1.656 |
| `com.revenuecat` eliminadas por R8 | **0** | **1.046** | |
| `com.revenuecat` sin ofuscar | 2.893 | **13** | −2.880 |

**El DEX colapsa de dos ficheros a uno.** No es cosmético: significa que la app vuelve a caber bajo
el límite de referencias de un solo DEX, y es la señal más clara de cuánto código muerto estaba
viajando.

**Es el paso más rentable de los siete** — 2,59 MB y casi 17 puntos de ofuscación — y no cambió ni
una línea de reglas nuestras. Solo una versión.

## Verificación

Sobre el APK minificado en el Oppo, cuenta real, sha device ↔ local `da67ee40…`.

| criterio | resultado |
|---|---|
| `:shared:testDebugUnitTest` | ✅ **2.452 tests, 0 fallos** |
| ambos flavors con `allWarningsAsErrors` | ✅ |
| Gradle resuelve la versión | ✅ `com.revenuecat.purchases:purchases:10.19.1 -> 10.20.0` en `prodReleaseRuntimeClasspath` |
| arranque y Home | ✅ sesión, vehículo, dirección y distancia |
| **el SDK configura y conecta con la tienda** | ✅ `BillingClient: Client is already in the process of connecting to billing service` desde **nuestro** proceso |
| **el paywall trae el precio REAL** | ✅ **«Consigue Plus por 13,99 €»** + «¿Ya estás suscrito? Restaurar» |
| **compatibilidad binaria del bump** | ✅ **cero** `NoSuchMethod`, `AbstractMethodError`, `IncompatibleClassChange` o `NoClassDefFound` en todo el flujo — que son exactamente los errores que produciría forzar una versión que el wrapper KMP no declara |
| detección intacta | ✅ `CoordinatorDetectionService` en primer plano y `ParkingSafetyNetWorker` con `SUCCESS` |

La fila de compatibilidad binaria es la que justifica haberlo probado en device y no solo compilado:
el riesgo de este ticket **no era de compilación** —el wrapper resuelve en `runtime`— sino de que una
firma cambiada reventara al ejecutar. No pasa.

## Resultado del plan completo

| paso | commit | DEX | ofuscación |
|---|---|---|---|
| — | punto de partida | 20,19 MB | 43,6 % |
| 1 · GeoFire | `f5edf09b` | 19,80 MB | |
| 2 · duplicados de consumer rules | `aeacc493` | 18,69 MB | 42,2 % |
| 3 · BaseLogin | `9904b72e` | 18,34 MB | 43,4 % |
| 4 · GitLive | `348a40ab` | 17,96 MB | 44,3 % |
| 5 · Firebase + GMS | `16cea83e` | 13,70 MB | 71,5 % |
| 6 · restos propios | `0362a547` | 13,40 MB | 72,8 % |
| **7 · RevenueCat 10.20.0** | este | **10,80 MB** | **89,7 %** |

Play reportaba **18,6 MB de DEX y 43 %** en el bundle 11.
