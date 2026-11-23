# MONEY-PLUS-CAN-SEE-ITS-OWN-BENEFITS-001 · Un Plus pagado no tiene dónde repasar qué compró

**Estado:** ✅ Done — mergeado a master.

## Problema

Un usuario con Paparcar Plus activo y comprado (no un regalo con fecha de fin) no tiene **ningún**
sitio en la app donde repasar qué incluye su suscripción:

- La fila de Plus en Ajustes deja de ser pulsable en cuanto es Plus sin promo
  (`SettingsScreen.kt:474`: `onClick = if (state.isPlus && state.plusPromoEndsAtMillis == null)
  null else { ... }`) — queda solo el badge "ACTIVO", sin chevron.
- Si de todos modos se llegase a abrir el paywall, se auto-cierra al instante
  (`PaywallState.kt:64`: `dismissesItself = isPlus && plusPromoEndsAtMillis == null`).
- La lista de ventajas (`PlusBenefits()`, `PaywallScreen.kt:451`) solo se renderiza dentro de esa
  pantalla de venta — inalcanzable para quien ya pagó.

## Doctrina violada

Ninguna regla de código. Es una confusión de producto: "ya no hay nada que vender"
(`PaywallState.dismissesItself`) se leyó como "ya no hay nada que mostrar", y son cosas distintas.
La doctrina Plus vs free (`CLAUDE.md`) no dice nada sobre esto — es un hueco, no una violación.

## Señales / datos disponibles

- `PlusBenefits()` ya existe como composable reusable (`PaywallScreen.kt:451`), hoy privado al
  fichero (`private fun PlusBenefits()`).
- `SettingsState.isPlus` + `SettingsState.plusPromoEndsAtMillis` ya distinguen los tres estados
  (free / Plus comprado / Plus-regalo con fecha fin) — la fila de Ajustes ya lee esto.
- `PaywallReason` (sealed, en `domain/entitlement/`) es el canal existente para decir SOBRE QUÉ
  moción se abrió el paywall — necesitará un caso nuevo o un flag para distinguir "vista de solo
  lectura" de "paywall de venta", si se reutiliza la misma pantalla/ViewModel.
- `storeRedeemCodeUrl()` y la gestión de suscripción de la tienda (Play) ya son el patrón para
  "esto lo gestiona la tienda, nosotros abrimos su puerta" — aplicable aquí para "gestionar
  suscripción".

## Diseño

**No un paywall con el botón de compra escondido — una vista de solo lectura distinta,** para no
arrastrar estado de compra/precios/ofertas a un sitio que no vende nada:

- Nueva pantalla (o estado dentro de la existente, a decidir en implementación) que reutiliza
  `PlusBenefits()` extraído a público/compartido, sin `PapPremiumButton` de compra, sin tarjetas de
  plan, sin `RedeemCode`.
- Trailing: fecha de renovación si se puede leer de `ObservePlusGrantUseCase` / `CustomerInfo`
  (RevenueCat ya expone expiración), y un enlace a gestionar la suscripción en Play (intent a
  `https://play.google.com/store/account/subscriptions` o el deep link con el SKU, patrón ya usado
  para `storeRedeemCodeUrl`).
- La fila de Ajustes, cuando `state.isPlus && state.plusPromoEndsAtMillis == null`, deja de tener
  `onClick = null` y pasa a abrir esta vista nueva (recupera el chevron). El resto de casos (free,
  Plus-promo con fecha fin) siguen exactamente igual que hoy.
- Analítica: no dispara `PaywallShown` (eso es específico del funnel de venta) — si se quiere medir
  el uso de esta vista, evento propio más adelante, fuera de alcance de este ticket salvo que el
  user lo pida aquí mismo.

## Criterio de éxito

- Usuario Plus comprado (sin promo): la fila de Ajustes vuelve a ser pulsable, abre la vista de
  solo lectura con las ventajas + fecha de renovación (si disponible) + acceso a gestionar en Play.
- Usuario free: sin cambios, sigue abriendo el paywall de venta normal.
- Usuario con Plus-promo (fecha de fin): sin cambios, sigue abriendo el paywall de decisión
  (comprar antes de que se acabe el regalo).
- Test unitario de `SettingsViewModel`/estado cubriendo los tres casos del `onClick`.
- Dev Catalog: nuevo estado en la galería (o `MockScenario`) para los tres casos de Plus, si aún no
  existe paridad completa.
- Suite completa (`testAll`) + Konsist en verde.

## Consumidores auditados

- `SettingsScreen.kt` (fila de Plus) — el único call site que hoy bloquea con `onClick = null`.
- `PaywallScreen.kt` / `PaywallViewModel.kt` / `PaywallState.kt` — origen de `PlusBenefits()` y de
  `dismissesItself`; a decidir si esta vista nueva vive en el mismo paquete `presentation/paywall/`
  o en uno propio `presentation/plusbenefits/`.
- Otros `OpenPlusPaywall` (Vehículos/Historial, zonas) — estos son todos entradas de usuario FREE
  tocando un límite; no aplican aquí, pero confirmar que ninguno puede dispararse ya siendo Plus
  (si puede, también cae en la vista nueva en vez del paywall de venta). ✔ Auditado: `PlusHistoryFooter`
  sólo se pinta cuando hay historial oculto por el límite free (`hiddenOlderCount`), así que un Plus
  real nunca lo ve — no hace falta tocarlo.

## Verificado (23-11)

- `SettingsViewModelTest` (73/73), `PlusStatusViewModelTest` (3/3 nuevo), `PaywallViewModelTest`
  (9/9) — en verde.
- Konsist: `TypographyGuardrailTest`, `ColorGuardrailTest`, `LocaleParityGuardrailTest`,
  `ComposeResourcesEscapeGuardrailTest`, `BacklogStatusGuardrailTest` (cifra del índice corregida
  a 863/771/83/9) — en verde.
- `MockKoinModuleVerifyTest` — en verde (el grafo mock resuelve `PlusStatusViewModel`).
- `:app:assembleMockDebug` + `:app:assembleProdDebug` — compilan y empaquetan los dos.
- ⏳ **`./gradlew testAll` completo NO se pudo medir**: dos intentos murieron por falta de memoria
  del sistema (con otra sesión de Gradle corriendo en paralelo en otro worktree). Lo que NO cubren
  las verificaciones de arriba: la suite de Room/WorkManager (`androidUnitTest`, no tocada por este
  ticket) y el klib de iOS (bloqueado en esta máquina por Windows App Control, issue conocida y
  ajena a este cambio). Repetir cuando no haya contención de memoria.
- Sin verificar en un móvil real — pendiente si se quiere antes de considerar esto "visto en mano".
