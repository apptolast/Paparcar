# BUG-FAB-001 — HomeMapFabsLayer: padding must be non-negative (Nothing Phone A001, OS 16)

**Estado:** OPEN  
**Prioridad:** P2  
**Detectado:** 2026-06-05 (Crashlytics beta01)  
**Afecta:** 1 evento / 1 usuario  
**Dispositivo:** Nothing Phone A001, Android 16 (API 36)  

---

## Síntoma

`java.lang.IllegalArgumentException: Padding must be non-negative`

Stack trace:
```
InlineClassHelper.kt:34   throwIllegalArgumentException
Padding.kt:483            PaddingElement.<init>
Padding.kt:76             PaddingElement.<init>
Padding.kt:55             padding-qDBjuR0
Padding.kt:53             padding-qDBjuR0$default        ← default params → padding(end = 14.dp)
HomeMapSection.kt:91      HomeMapFabsLayer-AFY4PWA       ← bytecode line, fuente ~94
HomeScreen.kt:547         HomeContent lambda
```

## Blame frame localizado

`composeApp/src/commonMain/.../home/sections/map/HomeMapSection.kt:94`

```kotlin
modifier = modifier.padding(end = 14.dp),   // dentro de HomeMapFabsLayer
```

## Análisis

- El valor `14.dp` es un literal hardcoded — no puede ser negativo por sí solo.
- Compose 1.8.0 **cambió comportamiento**: antes clampeaba padding negativo silenciosamente, ahora lanza excepción. Si el crash ya existía en versiones anteriores, no era visible.
- Solo se produjo en **Nothing Phone A001 con Android 16**, que introdujo cambios en la API de WindowInsets.
- Hipótesis más probable: algún valor de insets o padding en el árbol padre llega negativo en ese dispositivo/OS y corrompe el `modifier` chain antes de que llegue a `HomeMapFabsLayer`.
- No se pudo confirmar la causa exacta por lectura de código estático — la cadena `scaffoldPadding` → `BoxWithConstraints.padding()` → `HomeMapFabsLayer` no muestra un path obvio a valor negativo.

## Para reproducir

- Dispositivo: Nothing Phone A001
- OS: Android 16
- Acción: desconocida — 1 único evento, posiblemente durante animación de entrada/salida de la bottom nav

## Siguiente paso

1. Si el crash reaparece en más usuarios (especialmente Android 16), priorizar.
2. Activar R8 mapping upload en la build pipeline para tener líneas de fuente exactas en Crashlytics.
3. Revisar si `scaffoldPadding` del `Scaffold` interno en `HomeContent` puede tener valores negativos en Android 16 con `contentWindowInsets = WindowInsets(0)`.
4. Como guard provisional si se repite: `coerceAtLeast(0.dp)` en cualquier Dp calculado dinámicamente que alimente padding en `HomeContent` (NO en el literal `14.dp`).

## Nota de contexto

Los otros crashes de beta01 (#1 ActivityTransitionReceiver, #2 ParkingDetectionService SecurityException, #3 DetectionHeartbeatWorker expedited, #4 ParkingConfirmationReceiver) ya están corregidos por refactors previos del flujo de detección.
