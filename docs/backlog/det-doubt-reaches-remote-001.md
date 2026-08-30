# DET-DOUBT-REACHES-REMOTE-001 · una zona que en remoto parece un pin exacto no se puede diagnosticar

**Estado:** ✅ Done · rama `feature/DET-DOUBT-REACHES-REMOTE-001-doubt-remote` · worktree
`../Paparcar-doubt-remote` · apilada sobre `DET-STARVED-PROMPT-HAS-NO-WITNESS-001`

## Problema

`zoneRadiusMeters` era **local-only**. No por descuido: la razón estaba escrita
(`[DET-HONEST-CLOSE-001]`, *«an unrefined approximate zone stays on the device that detected it»*).

Lo que no estaba escrito era el coste, y ya se ha pagado dos veces: **en Firestore una zona de 250 m
es indistinguible de un pin exacto**. El diagnóstico del pin a 142 m del 30-08 hubo que hacerlo
sacando la base de datos Room del móvil por cable, porque el documento remoto no decía en ningún
sitio que aquello fuera un área. `isApproximate` se deriva de este campo (`zoneRadiusMeters != null`),
así que se perdían los dos a la vez.

**Una duda que la app mide y luego esconde de su propio diagnóstico es una duda de la que no puede
aprender.** Y la puerta para mejorar la detección son los logs.

## Alcance — lo que este ticket NO hace

Esto es `parkingHistory`, **la colección del propio usuario**. `spots` —lo comunitario, lo que ven
los demás— **no se toca**. La decisión original protegía a otros dispositivos de una zona sin
refinar; sincronizarla dentro de la cuenta del usuario no publica nada a nadie.

## Diseño

Campo `zoneRadiusMeters` en `ParkingHistoryDto`, mapeado en **las dos direcciones** (subida y
restauración). Documentos anteriores a este ticket no lo traen → `null` → pin exacto, que es
justamente lo que eran.

## Consumidores auditados

| sitio | clasificación |
|---|---|
| `UserParking.toParkingHistoryDto` (subida) | **cerrado** |
| `ParkingHistoryDto.toEntity` (restauración) | **cerrado** |
| `RemoteUserProfileDataSourceImpl` (**deserializador manual**) | **cerrado** — lo cazó `FirestoreDeserializerParityTest`, que se puso rojo con *«every Firestore manual deserializer assigns every property of its target DTO»* en cuanto el campo entró en el DTO. El guardarraíl del repo funcionando |
| `UserParking.toSpot()` | **exento**: la plaza comunitaria no hereda la duda del aparcamiento. Otra decisión, y sigue abierta como follow-up del 28-08 (*una sesión-ZONA publica plaza en el centro de la zona sin marcar la duda*) |
| Room (`toEntity` / `toDomain`) | ya lo llevaban |

## Criterio de éxito

- ✅ Ida y vuelta completas; **1.816 tests en verde**.
- ⏳ Un pin de zona nuevo debe verse en Firestore con su `zoneRadiusMeters` — **por verificar en campo**.

## Follow-up

Este campo es también lo que el **marcador "?"** necesitará para significar algo fuera del móvil que
lo detectó. El marcador se está construyendo en otra sesión; cuando aterrice, el dato ya viaja.
