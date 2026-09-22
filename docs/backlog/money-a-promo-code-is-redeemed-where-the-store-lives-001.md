# MONEY-A-PROMO-CODE-IS-REDEEMED-WHERE-THE-STORE-LIVES-001 · Canjear un código de promoción

**Estado:** ✅ Done · mergeado en master el 22-09-2026
que es `feature/MONEY-A-PROMO-CODE-IS-REDEEMED-WHERE-THE-STORE-LIVES-001` puesta sobre master) ·
cierra la **issue #61**

## Puesta al día (22-09-2026)

La rama original se quedó **37 commits por detrás de master**. Traída sobre master con
`merge --squash`: **cero conflictos**, y verde entera — `testAll`, `compileKotlinIosSimulatorArm64`
y los dos flavors de Android.

⛔ **Y venían 53 ficheros que no son de esta feature**: `iosApp/fastlane/report.xml`, el `README.md`
generado y **51 capturas de iPhone**, todo dentro de un cambio de tres pantallas. Fuera.

La causa no era despiste, era el `.gitignore`: tenía el bloque de fastlane, pero escrito
`fastlane/report.xml` — **y un patrón con una barra en medio está anclado a la raíz del repo**,
mientras que el fastlane de este proyecto vive en `iosApp/`. Las cuatro reglas no tapaban nada.
Pasan a `**/fastlane/…` (y se añade el `README.md`, que tampoco es nuestro), así que esto no puede
repetirse — comprobado creando los ficheros y viendo que `git status` ya no los ve.

## Problema

Pedido por el product owner (#61): poder **canjear un código de promoción** de Plus.

## Diseño

**Un código no lo canjea la app: lo canjea la TIENDA.** Nosotros abrimos su puerta y esperamos a
que la suscripción entre por donde entra cualquier compra — el `CustomerInfo` que empuja RevenueCat.

- `PaywallIntent.RedeemCode` → `PaywallEffect.OpenUrl` → `LocalUriHandler`, el mismo patrón que
  `SettingsEffect.OpenUrl` usa para la política de privacidad.
- La fila («Tengo un código») va **pegada a Restaurar compras**, no al botón de comprar: las dos son
  *«ya tengo derecho a esto, dámelo»*, no *«cóbrame»*.
- ⛔ **Sin campo de código propio.** La URL de Play admite `?code=`, pero eso obligaría a pedirlo
  antes en un campo nuestro: dos sitios donde teclear lo mismo, y el nuestro sin la validación ni
  los mensajes de error de Play. Con la URL desnuda, Play pide, valida, canjea y explica.
- ⛔ **Sin refresco a mano al volver.** El paywall ya se cierra solo cuando el entitlement pasa a
  PLUS (llegue por compra, por restore o desde otro dispositivo), y el SDK refresca al volver a
  primer plano. Añadir un `restore()` al volver duplicaría ese camino y enseñaría el spinner y el
  «no hemos encontrado nada» de restaurar en un sitio donde no significan lo mismo.
- **Puerto por plataforma** (`storeRedeemCodeUrl()`): Android devuelve la pantalla de canje de Play;
  **iOS devuelve `null` y la fila no se enseña**, porque allí un código de oferta se canjea con una
  hoja de StoreKit (`presentCodeRedemptionSheet`), que es una llamada nativa y no una dirección —
  y porque hoy iOS ni siquiera tiene el SDK configurado, así que no hay nada que canjear.

## Criterio de éxito

- Con la tienda disponible, «Tengo un código» abre Play; al volver con el código canjeado, el
  paywall se cierra solo y las features Plus quedan abiertas.
- En iOS la fila no existe (no hay puerta que abrir).

## Preguntas (para la revisión)

1. **¿La fila también en Ajustes?** Hoy sólo está en el paywall, que es donde alguien con un código
   acaba llegando. Si quieres que Ajustes → Plus la tenga también, es un `OpenUrl` más.
2. **iOS queda fuera hasta que tenga tienda.** Cuando la tenga, `storeRedeemCodeUrl` deja de ser una
   URL y pasa a ser un puerto con `actual` nativo (la hoja de StoreKit).
