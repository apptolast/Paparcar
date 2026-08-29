# UI-TYPE-SYSTEM-HYGIENE-001 · Lo que la auditoría tipográfica destapó y no toca el sistema de voces

**Estado:** 🟡 Abierto, sin rama · follow-up de `UI-TYPE-TWO-VOICES-ONE-ROW-001`

Hallazgos verificados en el árbol (`b949efa1`, post F7) durante la auditoría tipográfica del 29-08.
Ninguno afecta al reparto de familias, por eso salen de aquel ticket. Rutas relativas a
`shared/src/commonMain/kotlin/com/rndeveloper/paparcar/` salvo indicación.

## 1 · `PaparcarBottomActionBar` está muerto y el guardrail lo protege

`ui/components/PaparcarBottomActionBar.kt` no lo instancia **ninguna** pantalla de producción: sus
únicos consumidores son dos previews en
`shared/src/androidMain/.../HomeSheetPreviews.kt:471, 479`.

Y está en `INLINE_SP_ALLOWLIST` de `TypographyGuardrailTest`, o sea **el test exime de la regla a
código que no se renderiza**. Borrar el componente y su entrada en la allowlist.

## 2 · `ConnectivityBanner` se pinta con la fuente del sistema

`ui/components/ConnectivityBanner.kt:103` — el `Text` no declara `style` ni `fontFamily`. Su
contenedor es un `Surface` de M3, que provee `LocalContentColor` pero **no** text style, y no hay ni
un `ProvideTextStyle` en todo el repo. Cae en `TextStyle.Default` → `FontFamily.Default` → Roboto.

Es una cuarta familia colada en la app, y está allowlisted como *"chrome tokenizado"* cuando lo que
pasa es que no tiene familia. Mismo problema en `PaparcarBottomActionBar.kt:71` (que se va por el
punto 1).

**No afecta a** `AppBottomNavigation.kt:59`: `NavigationBarItem` sí aplica `labelMedium` a su label,
así que sale en Inter. Sólo overridea el tamaño, y ese es su token propio.

## 3 · Un CTA fuera de la convención de botones

`presentation/home/.../HomeLocationBlockedState.kt:88` — `Button` de M3 con un `Text` sin `style`,
así que hereda `labelLarge` (Inter Medium **14**). Todos los demás CTAs de la app usan el rol `cta`
(Inter SemiBold **15**). Es el botón rojo de activar ubicación. Pasarlo por `PapButton`.

## 4 · El rol `eyebrow` casi no se usa, y donde tocaría se usa otro

`eyebrow` (Inter Bold 11, tracking 1.2) tiene **un solo** consumidor:
`presentation/home/.../PapSheet.kt:147`.

Mientras tanto `presentation/permissions/DetectionTierStatusCard.kt:66-70` pinta un eyebrow de
manual —string llamado `permissions_tier_status_eyebrow`, `.uppercase()` aplicado— con el rol
`label` (tracking 0.5 en vez de 1.2).

Relacionado: el default `overlineStyle = badge` de `ui/components/PapListItem.kt:56` apunta a
Barlow donde el rol dedicado dice Inter. Hoy no muerde porque el único call site con `overline` pasa
`eyebrow` explícito, pero el default está mal apuntado. *(Este último lo arregla
`UI-TYPE-TWO-VOICES-ONE-ROW-001` al migrar `badge` a Inter — verificar que quede en `eyebrow`.)*

## 5 · Documentación desalineada con el código

- **`ui/theme/PaparcarType.kt:76`** — `cta` se documenta como *"labelLarge weight-bumped to Bold"*.
  El código (`:174-177`) es **Inter SemiBold 15sp**; `labelLarge` es Medium 14sp. Ni peso ni tamaño.
  Es el **único** rol cuyo docstring no cuadra con su valor (los otros 21 verificados uno a uno).
- **`ui/components/PapSectionHeader.kt:21`** — describe la receta como *"labelMedium + ExtraBold +
  1sp tracking"*, redacción anterior al sistema de roles. Los valores coinciden; la frase está
  caducada.
- **`ui/theme/Typography.kt:154`** — la extensión `Typography.appBarTitle` sólo la consume
  `shared/src/androidMain/.../TypographyPreviews.kt`. Duplica el rol `screenTitle`. Retirar.

## 6 · `counterUnit` a 8.5sp

`ui/theme/PaparcarType.kt:229-232` — único valor fraccionario del sistema y el más pequeño con
diferencia (el siguiente es `chartValue` a 10sp). Es el `LIBRES` bajo el contador del sheet. Por
debajo de 9sp y con `fontScale` bajo es frágil; conviene medirlo en device antes de dejarlo.

## 7 · Los roles Barlow no declaran `lineHeight`

`badge`, `sizeToken`, `distance`, `chartLabel`, `chartValue` no lo declaran; todos los demás roles
sí. Heredan las métricas de la fuente. Inconsistente, no roto — pero hace que su caja de texto
dependa del TTF en vez del sistema.

## 8 · `CLAUDE.md` dice 19 roles; hay 22

No lista `eyebrow`, `counter` ni `counterUnit`. *(Lo corrige
`UI-TYPE-TWO-VOICES-ONE-ROW-001` al reescribir la sección — anotado aquí por si aquel se aparca.)*
