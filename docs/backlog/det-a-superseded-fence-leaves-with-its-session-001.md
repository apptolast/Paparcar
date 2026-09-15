# DET-A-SUPERSEDED-FENCE-LEAVES-WITH-ITS-SESSION-001 · la valla vieja se va con su sesión, y iOS también vigila al grupo

**Estado:** ✅ **Done** (pendiente de mergear) · rama
`feature/DET-A-SUPERSEDED-FENCE-LEAVES-WITH-ITS-SESSION-001` · base master `fdcf70be`

> Cola directa de [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]. Lo abre una pregunta del user
> (15-09): *«si ya tenemos geovalla en shared car, ¿se reemplaza por la que habría antes de que
> fuera un shared car?»*. La respuesta corta era «sí»; la larga, «a medias», y eso es este ticket.

## Problema

**Tres huecos, y la pregunta destapó los dos primeros.**

1. **La valla de la sesión superada se quedaba registrada.** Cuando el grupo demuestra que el coche
   se lo llevó otra persona, cerramos mi sesión — pero su valla sigue viva en el sistema operativo
   (`NEVER_EXPIRE`). Se cura sola en el siguiente disparo, porque el barrido la lee como huérfana,
   y hasta entonces despierta el servicio cada vez que su dueño pasa por aquella calle. Así que sí,
   la valla del grupo **reemplaza** a la de antes — pero la vieja no se iba en el mismo gesto, se
   iba cuando alguien tropezaba con ella.

2. **En iOS la valla del grupo no habría sobrevivido a un reconcile.** `reconcileFences` compara las
   regiones monitorizadas contra las sesiones activas y **borra todo lo que no tenga sesión detrás**.
   Una valla compartida no tiene ninguna por construcción: nacía y moría entre dos reconciles. Era
   el cuarto sitio que decide por «¿qué sesión respalda esta valla?» y el más destructivo, porque no
   espera al disparo. El ticket anterior lo clasificó como *no cableado* — cierto para registrar,
   falso para borrar.

3. **iOS no reconciliaba las vallas del grupo en absoluto**, que es lo que el user pidió cablear.

## Doctrina

Sin cambios respecto al ticket padre: la valla del grupo es de **ubicación, no de liberación**, y la
decisión sigue viviendo en `EvaluateSharedCarWatchUseCase`. Aquí solo se completa la ejecución.

## Diseño

- **`reconcileFences`** excluye los ids `shared:` de `toRemove`. No de `toRegister`: la valla del
  grupo no sustituye a la de una sesión propia, son dos cosas distintas y la propia se sigue
  echando en falta si falta (tiene su test).
- **`SharedCarFenceReconciler`** quita la valla de la sesión que acaba de superar, en el mismo
  gesto en que la cierra. La nueva ya está en su sitio; esta es la que sobra.
- **`IosDetectionController.reconcileRegions`** llama al reconciliador del grupo al terminar con las
  propias — mismo sitio que en Android (dentro del janitor) y por el mismo motivo: dos pasadas
  distintas sobre el mismo inventario acaban discrepando. Nunca hace fallar el reconcile propio.

## Criterio de éxito

1. Al superar mi sesión, su valla desaparece **en la misma pasada**, no en el siguiente tropiezo.
2. Un reconcile de iOS deja la valla del grupo en pie y sigue barriendo las huérfanas de verdad.
3. iOS registra y mueve la valla del grupo como Android.
4. `./gradlew testAll` en verde + klib iOS.

## Consumidores auditados

El barrido del ticket padre listó cuatro sitios que deciden por «¿qué sesión respalda esta valla?».
Este cierra el que faltaba:

| Sitio | Antes | Ahora |
|---|---|---|
| `EvaluateGeofenceExitUseCase` | ✅ vetado | sin cambios |
| `cleanOrphanEnterFences` | ✅ vetado | sin cambios |
| `GeofenceJanitorWorker` | ✅ reconcilia | sin cambios |
| `reconcileFences` (iOS) | ⚠️ **habría borrado la valla en cada pasada** | ✅ vetado, con test |
| `IosDetectionController` | ⚠️ no cableado | ✅ reconcilia el grupo tras las propias |

## Bitácora

- **15-09** · Abierto y cerrado en rama. 3 tests nuevos; suite entera en verde; klib iOS compila.
  Lo que sigue sin estar: el despertar instantáneo por FCM — sigue siendo del ticket del push.
