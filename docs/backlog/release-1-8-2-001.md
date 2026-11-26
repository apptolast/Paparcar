# RELEASE-1-8-2-001 · el AAB con Meta y TikTok

**Estado:** 🔵 En progreso · rama `chore/RELEASE-1-8-2-001-tiktok`

## Alcance

El código 20 se cortó antes del SDK de TikTok. Este corte propone 1.8.2 (código 21) desde
`master` con el ticket MKT-TIKTOK-APP-EVENTS-SDK-001, incluido su trabajo local de consentimiento,
ficha y privacidad. Los cambios originales se conservaron en su worktree.

El SDK conserva los identificadores de la fuente y la puerta de consentimiento. El App Secret
queda fuera del AAB. Las pruebas previas de Events Manager usaron otra configuración, así que
queda por verificar la recepción de instalación y apertura con este binario.

<!-- cut:measured -->

AAB firmado `.artifacts/paparcar-1.8.2-vc21.aab`, 24.568.615 bytes.
SHA-256 `014a9eb633287149bdbfa69f1449f9fc00405542bc433f23bed5343ce0bb6535`.
`testAll --rerun-tasks` pasó (95 tareas). El corte verificó firma, ambos SDK y notas en nueve idiomas.

<!-- /cut:measured -->

## Criterio de éxito

- [x] Suite agregada y AAB firmado.
- [ ] Contrastar la ficha candidata y las declaraciones definitivas de Play antes de publicar.
- [ ] Verificar consentimiento y eventos de TikTok en Android y actualizar las declaraciones de Play.
- [x] Subir el código 21 y verificar el borrador en Play Internal con notas en nueve idiomas.
- [x] Publicar y releer la política bilingüe en Hosting (`9c076fe442d36d5f`). Solo cambió `/privacy-policy.html`.
- [ ] Promover el código 21. La última lectura de Play confirma el código 20 en producción.
