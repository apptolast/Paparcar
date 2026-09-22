# IOS-A-FEED-WITHOUT-FORWARD-SECRECY-IS-REFUSED-001 · El ministerio no tiene forward secrecy, y iOS no habla con quien no la tiene

**Estado:** ✅ Done · mergeado en master el 22-09-2026

## Problema

Issue **#77**: *«IOS: Gasolineras sin precios estando en España, revisar»*.

En un iPhone, en España, el carril de gasolineras muestra surtidores pero **ningún precio**. En
Android, en el mismo punto, los precios salen. El código es el mismo: `MineturStationsSource` vive
en `commonMain`, habla Ktor y se registra en `DataModule` para las dos plataformas.

## Causa raíz — medida, no supuesta

**El ministerio no ofrece ni una suite con forward secrecy, y App Transport Security exige una.**

Handshake real contra `sedeaplicaciones.minetur.gob.es`, 22-09-2026:

```
DEFAULT          → TLSv1.2, AES256-GCM-SHA384      (= TLS_RSA_WITH_AES_256_GCM_SHA384)
-cipher ECDHE    → Cipher: 0000                    (handshake rechazado)
-cipher DHE      → Cipher: 0000                    (handshake rechazado)
-tls1_3          → unexpected eof                  (no habla TLS 1.3)
certificado      → sha256WithRSAEncryption, RSA 2048
```

O sea: **intercambio de claves RSA plano**. ATS, por defecto, pide TLS ≥ 1.2 (✓), certificado
SHA-256 con RSA-2048 (✓) y **una suite con forward secrecy de su lista ECDHE (✗)**. El motor Darwin
de Ktor es `NSURLSession`, así que en iOS la petición no llega a salir; en Android OkHttp no tiene
esa exigencia y sale.

Y a partir de ahí el comportamiento es **correcto y por eso invisible**: `FuelStationsRegistry` ve
fallar a la fuente nacional, cae al mundial, y el usuario recibe gasolineras sin precio con el
cartelito honesto de `didFallBack`. Nadie ve un error: se ve un país sin precios.

⚠️ `iosApp/iosApp/Info.plist` **no declara `NSAppTransportSecurity`**, así que rigen los valores por
defecto. No es que la excepción esté mal puesta: es que no hay ninguna.

## Por qué es España y sólo España

Probados los siete hosts a los que habla la app, misma tarde:

| Host | | Forward secrecy |
|---|---|---|
| **`sedeaplicaciones.minetur.gob.es`** | ES · precios | **NINGUNA** ⛔ |
| `carburanti.mise.gov.it` | IT · precios | ECDHE-RSA-AES128-GCM-SHA256 ✓ |
| `creativecommons.tankerkoenig.de` | DE · precios | ECDHE-RSA-AES256-GCM-SHA384 ✓ |
| `data.economie.gouv.fr` | FR · precios | TLS 1.3 ✓ |
| `openvan.camp` | medias | TLS 1.3 ✓ |
| `overpass-api.de` | OSM · parkings y surtidores | TLS 1.3 ✓ |
| `paparcar.com` | la web | TLS 1.3 ✓ |

Por eso el síntoma dice «estando en España» y por eso Italia y Alemania, que se probaron después,
no lo enseñaron. **El único feed roto en iOS es el único al que le falta la exigencia de Apple.**

## Doctrina violada

Ninguna de código: la caída al feed sin precios es exactamente lo que
[FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001] diseñó, y funcionó. Lo que falla es
que **`commonMain` puede compilar, pasar los tests y aun así no ejecutarse en una plataforma**,
porque lo que lo bloquea no está en el código sino en la política del sistema operativo. Un feed que
contesta no es un feed alcanzable.

## Diseño

Una excepción ATS **de un solo host y de un solo atributo**, en `iosApp/iosApp/Info.plist`:

```xml
<key>NSAppTransportSecurity</key>
<dict>
  <key>NSExceptionDomains</key>
  <dict>
    <key>sedeaplicaciones.minetur.gob.es</key>
    <dict>
      <key>NSExceptionRequiresForwardSecrecy</key>
      <false/>
    </dict>
  </dict>
</dict>
```

⛔ **Lo que NO se hace, y por qué importa:**
- **Nada de `NSAllowsArbitraryLoads`.** Apagaría ATS para los siete hosts —seis de los cuales están
  perfectos— para arreglar uno. Y es lo que Apple pregunta en la revisión.
- **Nada de `NSExceptionAllowsInsecureHTTPLoads`** ni `NSExceptionMinimumTLSVersion`: el ministerio
  sirve HTTPS con TLS 1.2 y certificado SHA-256 válido. Lo único que le falta es la forward secrecy,
  y es lo único que se releva. Se sigue validando cadena, host y versión.
- **Sin `NSIncludesSubdomains`**: el host es exacto.

## Testigo

ATS no se puede ejecutar en un test JVM, así que el testigo no puede ver el handshake. Lo que sí es
comprobable en frío es **la relación entre los hosts que el código llama y las excepciones que el
plist declara**, que es donde esto se volverá a romper: alguien añade un país, o alguien ensancha la
excepción para salir del paso.

`AtsExceptionsGuardrailTest` (androidUnitTest, como los demás guards que leen ficheros):

1. el plist **nunca** declara `NSAllowsArbitraryLoads`;
2. cada host de `ATS_EXCEPTIONS_REQUIRED` está declarado en `NSExceptionDomains`;
3. cada dominio declarado sigue **citado por el código** — una excepción huérfana es un permiso que
   nadie revisa;
4. ninguna excepción releva más de lo que su fila documenta.

## Criterio de éxito

- En un iPhone, en España, el carril de gasolineras trae precios y la hora del feed.
- `didFallBack` deja de encenderse en España.
- Los otros seis hosts siguen bajo ATS estricto.

## Pendiente

⏳ **Verificarlo en un iPhone real.** No hay Mac ni device en esta sesión, y el fallo es de runtime
de la plataforma: los tests de este repo no pueden verlo. La medida del handshake es firme; que la
excepción lo desbloquee hay que verlo.
