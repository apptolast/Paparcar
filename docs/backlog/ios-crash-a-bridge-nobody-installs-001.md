# IOS-CRASH-A-BRIDGE-NOBODY-INSTALLS-001 · Crashlytics deja de ser un no-op silencioso en iOS

**Estado:** ✅ Done (06-09-2026) · plegado en la rama iOS (PR #3) · ⏳ declarado: non-fatal de
prueba visible en la consola de Crashlytics desde device (compañero del Mac).

## Problema

El `CrashReporter` de iosMain es un bridge cuyo `bridge` nadie asigna: todo
`recordNonFatal`/`setUserId` se pierde en silencio. El propio KDoc documenta desde junio el wiring
Swift exacto que falta, y `FirebaseCrashlytics` **ni siquiera está entre los productos SPM
linkados** en el pbxproj (solo Core, Auth, Firestore, AppCheck). Detectado en la auditoría de
paridad (03-09).

## Doctrina violada

Ninguna de detección. La de diagnóstico: los non-fatals de detección son parte del contrato de
provenance ([[feedback_detection_trigger_provenance]] a nivel de crash) — en iOS hoy se evaporan.

## Diseño

1. **pbxproj**: producto `FirebaseCrashlytics` añadido al paquete SPM ya declarado
   (`firebase-ios-sdk` 11.8.0): `XCSwiftPackageProductDependency` + `PBXBuildFile` + entrada en
   la fase Frameworks + `packageProductDependencies` del target.
2. **`iOSApp.swift`**: `SwiftCrashReporterBridge: CrashReporterBridge` (la implementación que el
   KDoc del contrato dicta, adaptada) e instalación
   `CrashReporter.shared.bridge = SwiftCrashReporterBridge()` justo tras
   `FirebaseApp.configure()`.
3. Fuera de alcance, anotado: la subida de dSYMs para simbolizar crashes NATIVOS es asunto del
   carril de release (fase de build + upload-symbols) — este ticket cablea los non-fatals de
   Kotlin, que viajan como `NSError` y no necesitan dSYM.

## Criterio de éxito

- El job `apple` del PR #3: `xcodebuild` resuelve el producto SPM nuevo y compila el Swift del
  bridge contra los headers del framework — la única verificación posible sin Mac humano.
- En device (compañero): un non-fatal de prueba aparece en la consola de Crashlytics.

## Consumidores auditados

- `CrashReporter.recordNonFatal/setUserId`: llamadores en commonMain sin cambios — el objeto ya
  hacía de fachada; solo se enchufa el destino.
- `CrashReporterBridge`: primer implementador (Swift). El contrato no cambia.
- pbxproj: solo entradas ADITIVAS (ids nuevos verificados sin colisión).
