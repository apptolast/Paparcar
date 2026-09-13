# PARK-A-MANUAL-RELEASE-MUST-RESPECT-THE-PRIVATE-ZONE-001 · Liberar a mano un aparcamiento en zona privada no debería publicar la plaza

**Estado:** 🔵 Pendiente · sin rama · seguimiento de
[`first-steps-a-private-garage-is-a-private-zone-001.md`](first-steps-a-private-garage-is-a-private-zone-001.md) (PR #24)

## Problema
Una zona privada evita publicar la plaza al salir: `ConfirmParkingUseCase` guarda `privateZoneId` al
aparcar y las salidas automáticas lo respetan (`ProcessConfirmedDepartureUseCase`,
`FinalizeDeducedDepartureUseCase`, `RetractDeducedDepartureUseCase`).

La liberación MANUAL no: `ReleaseActiveParkingSessionUseCase` decide
`publishes = reason.publishesSpot && shape != Withheld` sin mirar `privateZoneId`, así que «Me voy →
publicar» en tu propio garaje publica una plaza que nadie puede usar. El paso del tutorial «Protege tu
garaje» promete «al salir no publicaremos una plaza libre» sin distinguir.

## Doctrina en juego
- Fallo asimétrico: mejor no publicar que publicar una plaza fantasma.
- Un invariante en UN sitio: la regla «zona privada ⇒ no se publica» debería vivir donde viven las
  demás reglas de publicación (`SpotReleasePolicy`), no repetida en cada caso de uso.

## Opciones (a decidir)
1. No publicar nunca desde zona privada, también a mano (el diálogo solo ofrece «Solo liberar»).
2. Publicar solo si el usuario lo confirma explícitamente en el diálogo, avisando de que está en zona
   privada.

## Relacionado
Una zona creada con el coche ya aparcado dentro no protege ese aparcamiento (se evalúa al confirmar).
Si se aborda este ticket, conviene decidir también si al crear la zona se re-evalúa la sesión activa.
