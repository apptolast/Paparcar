# DIAG-A-ROW-MUST-SAY-WHICH-PLATFORM-WROTE-IT-001 · Firestore lo comparten las dos plataformas y ningún documento decía de cuál venía

**Estado:** ✅ Done · `testAll` verde · iOS compila · guards con `--rerun-tasks` verdes · 3 testigos
falsificados

## Problema

Petición del user, nacida de diagnosticar la issue #125: *«comparto Firestore con usuarios iOS y
Android, se podría incluir algún tipo de dato que me dijera cuál es cuál si no lo hay ya»*.

No lo había. Comprobado: ni `users/{uid}` ni `parkingHistory/{id}` llevan plataforma. Lo único que
la lleva es `DeviceInfoProvider` (`deviceModel`, `appVersion`, `osVersion`), y sólo viaja dentro de
**diagnósticos**, que están detrás de un gate por cuenta — para el 99 % de los usuarios la pregunta
«¿esto es un iPhone?» no tenía respuesta.

⛔ **Y no se puede deducir.** La tentación es mirar un campo que se le parezca, y el campo que se le
parece miente: la cuenta de la issue #125 es un iPhone y publica `batteryOptimizationExempt: false`,
igual que cualquier Android sin exención. Deducir la plataforma de un campo que habla de otra cosa
es exactamente cómo se construye un dato falso que nadie vuelve a cuestionar.

## Doctrina violada

Ninguna, y conviene decirlo: esto no arregla un bug, **añade el dato que faltaba para poder
diagnosticar**. La regla que sí aplica es la de siempre — *un diagnóstico sin provenance no está
terminado* —, y la provenance de una fila empieza por quién la escribió.

## Diseño

**Cada documento dice de qué plataforma salió, sin gastar una sola escritura extra.**

1. **`expect val platformName`** (`Platform.kt`) → `"android"` / `"ios"`. Minúsculas y sin versión:
   es una **clave de agrupación**, no una frase para leer.
2. **`users/{uid}.platform` + `.appVersion`**, escritos por `PublishDetectionStateUseCase`, que ya
   publica ahí un snapshot **deduplicado**. Cero escrituras nuevas: viaja en la que ya existía.
   - La versión **entra en la huella** (`remoteFingerprint`). Sin eso, una cuenta publicaría para
     siempre la versión con la que se instaló — una respuesta falsa a «¿con qué versión pasó esto?»,
     que es peor que no tener respuesta. Cuesta una escritura por cuenta y actualización.
   - ⛔ `reliabilityReduced` sigue FUERA de la huella: no es un campo persistido y meterlo gastaría
     escrituras para no cambiar nada. El testigo que lo fija sigue verde.
3. **`parkingHistory/{id}.platform`**, estampado al construir el DTO — que es siempre en el
   dispositivo que sube. Es el que contesta la pregunta útil en un diagnóstico: mirar un pin raro y
   saber de qué carril salió.
   - Dice **quién lo escribió la última vez**, no quién lo creó, y el KDoc lo dice con esas palabras.
     Para lo que sirve, el último escritor es la respuesta correcta: es quien tocó la fila.
   - **Write-only y sin migración de Room**: se lee de vuelta para que un `set()` no lo borre, y no
     baja a la entidad. La fila local no necesita saberlo — ya está EN esa plataforma.

⛔ **Lo que NO entra: `osVersion` y `deviceModel` en el documento de cada cuenta.** Plataforma y
versión de app no son datos personales y ya están implícitos en Play/App Store; modelo y versión de
SO de todas las cuentas es otra cosa, y tocaría la ficha de Data Safety. Se quedan donde estaban:
en diagnósticos, con su gate y con el informe que el usuario manda a propósito.

⛔ **Los dos campos se pasan, nunca se leen de las constantes dentro del use case.** Un valor de
plataforma metido por la puerta de atrás hace que el mismo test afirme cosas distintas en JVM y en
iOS, que es la forma más cara de no probar nada.

## Testigos (los tres falsificados)

- `DetectionStatePublicationTest`: una app actualizada **vuelve a publicar**; la misma cuenta
  contestando desde la otra plataforma, también. Más el que fija la otra mitad: mover el eje que
  sólo es de analytics **no** cuesta una escritura.
- `ParkingSessionMapperTest`: el DTO sale estampado con la plataforma que escribe — comparado contra
  la constante, no contra un literal, porque un test no puede afirmar en qué plataforma corre.

`FirestoreDeserializerParityTest` cubre gratis la otra mitad: añadir un campo al DTO **falla** hasta
que el deserializador manual lo lee. Forzado con `--rerun-tasks`, que es como se ejecutan los guards
de Konsist en este repo.

## Consumidores auditados

| Sitio | Qué pasa | Estado |
|---|---|---|
| `UserProfileDto` | gana dos campos en la sección write-only que ya existía | ✅ leídos de vuelta |
| `ParkingHistoryDto` | gana `platform`, write-only | ✅ leído de vuelta; NO baja a Room |
| `ParkingHistoryDto.toEntity()` | ignora el campo | ✅ deliberado: sin columna, sin migración |
| `UserParkingReconcile` | compara entidades, no DTOs | ✅ no le afecta |
| `DetectionStateSnapshot` | pasa a ser la foto del INSTALL, no sólo de la intención | ✅ ya cargaba `batteryOptimizationExempt`, que es un hecho del dispositivo |
| 6 dobles de test de `RemoteUserProfileDataSource` / `UserProfileRepository` | firma nueva | ✅ actualizados |
| Reglas de Firestore | los campos nuevos viven en documentos que el dueño ya podía escribir | ✅ sin cambios |
