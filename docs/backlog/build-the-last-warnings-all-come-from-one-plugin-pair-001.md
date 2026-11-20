# BUILD-THE-LAST-WARNINGS-ALL-COME-FROM-ONE-PLUGIN-PAIR-001 · Los avisos que quedan son UNO, y el que lo apaga nos borra los recursos

**Estado:** 📋 Abierto, sin rama · follow-up declarado de
[BUILD-THE-CATALOG-SAYS-ONLY-WHAT-THE-BUILD-USES-001]

## Problema

Con Gradle 9.9.2 + AGP 9.5.1, `./gradlew help --warning-mode=all` deja **cinco** avisos de
configuración, y ninguno es un fichero nuestro:

```
WARNING: The option setting 'android.builtInKotlin=false' is deprecated.
WARNING: The option setting 'android.newDsl=false' is deprecated.
WARNING: API 'applicationVariants' / 'testVariants' / 'unitTestVariants' is obsolete  (:app)
WARNING: API 'libraryVariants' is obsolete                                           (:shared, :mockkit)
w: Deprecated 'org.jetbrains.kotlin.android' plugin usage                            (:app)
w: The 'org.jetbrains.kotlin.multiplatform' plugin deprecated compatibility with
   Android Gradle plugin: 'com.android.library'                                      (:shared, :mockkit)
```

Los cinco salen de **una sola decisión**: `:shared` y `:mockkit` usan
`com.android.library` + `androidTarget()` en vez de `com.android.kotlin.multiplatform.library`, y
`:app` usa `org.jetbrains.kotlin.android` en vez del Kotlin integrado de AGP. Las dos cosas las
sostienen los dos flags de `gradle.properties` (`android.builtInKotlin=false`, `android.newDsl=false`),
que son justo los dos que AGP 9.5 ya marca como deprecados.

La traza lo confirma (medida con `-Pandroid.debug.obsoleteApi=true`): quien llama a la API obsoleta
no es Paparcar, es KGP —
`KotlinAndroidSourceSets.applyKotlinAndroidSourceSetLayout` → `LibraryExtension.getLibraryVariants`.

## Doctrina violada

Ninguna todavía: [BUILD-ZERO-WARNINGS-IS-ENFORCED-001] puso el contador del **compilador** a cero
(`allWarningsAsErrors` en el target Android de los tres módulos) y el de **configuración de Gradle**
nunca se midió. Esto es el hueco escrito, no un incumplimiento.

## Por qué no se arregla todavía — está MEDIDO

`gradle.properties` lo tiene desde `ARCH-HEALTH-001 F7`, y se re-leyó al abrir este doc: bajo
`com.android.kotlin.multiplatform.library`, Compose Multiplatform registra
`copyAndroidMainComposeResourcesToAndroidAssets` **sin `outputDirectory`**, y TODOS los
`composeResources` (drawables, fuentes, los 9 locales de strings) desaparecen del APK en silencio.
Medido en el Redmi el 29-08: compila, instala, pasa la suite y **muere en el primer frame** con
`MissingResourceException`. Borrar los flags hoy rompe el build directamente.

## Criterio de éxito

Cuando se abra, el criterio no es «compila»: compilar es exactamente lo que ya hacía mientras se
comía los recursos. Es, en este orden:

1. `:shared` y `:mockkit` con `com.android.kotlin.multiplatform.library`, `:app` sin
   `org.jetbrains.kotlin.android`, y los dos flags **borrados** de `gradle.properties`.
2. `./gradlew help --warning-mode=all` sin un solo aviso.
3. **Los recursos SIGUEN en el binario**, contado sobre el APK, no sobre el build log — es la
   comprobación que el ticket original de 2026-08 no tenía:
   ```bash
   ./gradlew :app:assembleProdDebug
   python -c "import zipfile;z=zipfile.ZipFile('app/build/outputs/apk/prod/debug/app-prod-debug.apk');\
   print(sum(1 for n in z.namelist() if 'composeResources' in n and 'values' in n))"
   # 20-11-2026, con los flags puestos: 18. Si sale 0, el bug de CMP sigue vivo.
   ```
4. `./gradlew testAll --rerun-tasks` verde y la app arrancando en un móvil de verdad (el fallo de
   este bug es de primer frame, y ninguna suite lo ve).

## Señal de que ha llegado el momento

Una release de Compose Multiplatform cuyas notas nombren el plugin
`com.android.kotlin.multiplatform.library` o la tarea
`copyAndroidMainComposeResourcesToAndroidAssets`. Mientras eso no exista, este doc se queda abierto
a propósito: es el hueco declarado, no una tarea pendiente de hacer.
