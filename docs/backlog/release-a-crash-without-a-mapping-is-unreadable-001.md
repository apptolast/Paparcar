# RELEASE-A-CRASH-WITHOUT-A-MAPPING-IS-UNREADABLE-001 · Producción lleva crasheando desde 1.0.0 sin mapping en Crashlytics

**Estado:** ✅ Done · rama `chore/RELEASE-A-CRASH-WITHOUT-A-MAPPING-IS-UNREADABLE-001-mapping`

## Problema

El crash del 10-09-2026 llegó a Crashlytics así, y así se queda:

```
java.lang.IllegalStateException: The ComposeView was measured to have a width or height of zero.
at od2.m (SourceFile:2)
at mh9.f (SourceFile:774)
at it4.invoke (SourceFile:557)
…
```

`blameFrame: symbol: od2.m, owner: THIRD_PARTY` — o sea que Crashlytics ni siquiera sabe si el frame
culpable es nuestro. **La app lleva publicada desde el 03-09 (1.0.0) y ningún crash de producción se
puede leer.**

La causa está escrita en el propio build, `app/build.gradle.kts:230-237`:

```kotlin
// [build] La subida del mapping … bloqueada por SSL inspection en esta red de desarrollo (PKIX)…
tasks.matching { it.name.startsWith("uploadCrashlyticsMappingFile") }.configureEach {
    enabled = project.findProperty("uploadCrashlyticsMapping") == "true"
}
```

Se desactivó por un motivo real —Norton rompe el TLS de esta máquina, ya documentado— pero el
apagado quedó **por defecto**, así que hay que acordarse de `-PuploadCrashlyticsMapping=true` en cada
release. No se ha acordado nadie.

Y no hay copia de repuesto:

- `app/build/outputs/mapping/prodRelease/mapping.txt` existe (137 MB, 10-09 03:46) pero **es de otra
  compilación**: sus renombres no cuadran con el stack (ahí `od2` es `kotlinx.coroutines.EventLoopImplBase`,
  que no puede lanzar esto). R8 no renombra igual entre ejecuciones, así que un mapping solo sirve
  para SU build.
- El AAB de `app/build/outputs/bundle/prodRelease/` (08-09) **no lleva el mapping dentro**: en sus
  `BUNDLE-METADATA` solo hay `debugsymbols`, `app-metadata.properties` y `dependencies.pb`. No hay
  `com.android.tools.build.obfuscation/proguard.map`.

O sea: del evento del 10-09 **no existe mapping en ninguna parte**, y de los futuros tampoco existirá.

## Doctrina violada

> Una build que no se puede diagnosticar no está monitorizada, está observada.

Crashlytics está integrado, informa, y lo que informa no se puede usar. Es el mismo patrón que este
proyecto ya nombró en `project_test_a_green_suite_must_prove_it_looked`: **algo que parece un chequeo
y no mira**.

## Diseño

**Medido primero, el 10-09-2026, desde esta misma máquina**: `uploadCrashlyticsMappingFileProdRelease`
**funciona** (`BUILD SUCCESSFUL`, tarea ejecutada). El error PKIX se fue con el certificado raíz de
Norton en el truststore del JBR, que se hizo para Gradle. O sea: **el guard estaba sobreviviendo a su
causa**, y la mitad del ticket sobraba. La otra mitad no.

El invariante: **una build que se instala en un móvil ajeno sube su mapping, o no es una build de
release**. Encendida por defecto, y lo que hay que escribir es el apagado:

```
./gradlew :app:bundleProdRelease -PskipCrashlyticsMapping=true   # una release que NO va a un móvil
```

Y la subida que falla **sigue tumbando el build, a propósito**: una release cuyo mapping no llegó a
Firebase es una release que no se puede diagnosticar, y enterarse en el build es justo el punto. No
hay ponerse al día después — R8 no renombra igual entre ejecuciones, así que un mapping solo sirve
para SU build y un crash sin mapping subido no se lee nunca más.

⛔ La exclusión del flavor `mock` hay que repetirla aquí, no basta con el `afterEvaluate` de arriba:
`configureEach` corre al REALIZARSE la tarea, que es después, así que encender el defecto sin ese
guard volvería a activar la subida de mock y rompería `assembleMockRelease` en el último paso.

## Criterio de éxito

Las tres conductas, verificadas con ejecuciones reales (no `--dry-run`, que marca todo SKIPPED y no
mira `enabled`):

| comando | tarea |
|---|---|
| `:app:uploadCrashlyticsMappingFileProdRelease` | ✅ **se ejecuta** |
| …`-PskipCrashlyticsMapping=true` | ✅ SKIPPED |
| `:app:uploadCrashlyticsMappingFileMockRelease` | ✅ SKIPPED (la exclusión aguanta) |

⏳ Falta la prueba final, que solo puede darla una release nueva: provocar un crash en una build
firmada y que Crashlytics lo enseñe con nombres de Paparcar y número de línea. **El crash del 10-09
no se recupera** — su mapping no existe en ninguna parte.

## Consumidores auditados

- `app/build.gradle.kts:120-130` apaga a propósito `uploadCrashlyticsMappingFile` **del flavor mock**
  (su `applicationId` lleva sufijo y no está en `google-services.json`). ✅ Correcto, se queda.
- No hay ninguna otra vía que suba símbolos: `nativeSymbolUploadEnabled` no aparece en el build.
