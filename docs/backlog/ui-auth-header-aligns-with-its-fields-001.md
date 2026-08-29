# UI-AUTH-HEADER-ALIGNS-WITH-ITS-FIELDS-001 · El header del login se alinea con sus campos, y el botón de proveedor tiene la forma de la app

**Estado:** ✅ Done · verificado en device (emulador + Redmi) el 29-08-2026

## Problema

Dos cosas se ven mal en la pantalla de login (capturas del user, 29-08):

1. **El header flota centrado.** El bloque logo + "Paparcar" + tagline está centrado
   horizontalmente, mientras que todo lo que hay debajo (Email, Password, Sign In) ocupa el ancho
   completo de la columna. El logo empieza ~70 dp a la derecha del borde izquierdo de los campos,
   así que la pantalla tiene dos ejes de lectura: uno centrado arriba y uno a la izquierda abajo.
   El logo, el título y el subtítulo tienen que quedar fijados a la izquierda, al mismo borde que
   los campos de texto.

2. **El botón "Sign in with Google" no es un botón de la app.** Es el
   `SocialLoginButtonsSection` por defecto de BaseLogin: rectángulo de 12 dp de radio, 56 dp de
   alto, `MaterialTheme.typography.labelLarge`. Justo encima, "Sign In" es un `PapPrimaryButton`
   (píldora, `PaparcarType.cta`, padding del sistema). Dos botones apilados, misma jerarquía
   visual, dos formas distintas: se lee como si el de Google fuera de otra app.

## Doctrina violada

- **Sistemas, no parches** — el estilo de botón de la app existe en `ui/components/PapButton.kt`;
  la pantalla de auth se lo salta y hereda el default de la librería. Adoptar la forma no es
  maquillar este botón: es que el botón de proveedor entre en la familia, para login y registro a
  la vez.
- **Tipografía por ROL** — `MaterialTheme.typography.labelLarge` (dentro de la librería) no es un
  rol de `PaparcarType`; el rol de una etiqueta de acción es `cta`.
- **Color · Nivel 3 (ilustración/marcadores)** — la G multicolor de Google es un glifo de marca:
  **no se tinta**. Se dibuja con `Image`, nunca con `Icon(tint = …)`.
- ⛔ **BaseLogin no se toca desde Paparcar**: todo se arregla desde los slots que la librería ya
  expone (`AuthScreenSlots`), no editando la librería.

## Señales / datos disponibles

- `PaparcarAuthSlots.kt` ya sustituye header, campos, submit y "forgot password"; el único slot que
  seguía en su default era `socialProviders`.
- La librería publica `Res` como `public` (`login.custom_login.generated.resources`), así que
  Paparcar puede reutilizar sus glifos de marca y sus etiquetas ya traducidas
  (`login_google_button`, …) sin duplicar strings ni drawables → **cero strings nuevos**, cero
  riesgo de locale incompleto.
- `DefaultAuthContainer` (librería) coloca a los hijos en una `Column` con `padding(horizontal =
  24.dp)` y alineación `CenterHorizontally`. Como el header es un hijo `fillMaxWidth()`, basta con
  que **su** alineación interna sea `Start` para caer al mismo borde que los campos.

## Diseño

**1 · La alineación vive en el header, no en cada texto.** `PaparcarAuthHeader` pasa de
`Alignment.CenterHorizontally` a `Alignment.Start`. Un solo cambio: el header es compartido por
login y registro, así que las dos pantallas se corrigen juntas. Los `Text` ya declaraban
`TextAlign.Start`; lo que estaba centrado era la caja, no el texto.

**2 · `PapProviderButton` entra en la familia de botones.** Componente nuevo en
`ui/components/PapButton.kt`, junto a `PapPrimaryButton`, con el que comparte literalmente las
constantes de padding y de tamaño de icono, y `ButtonDefaults.shape` — la misma píldora del botón
relleno, no un radio copiado a ojo que se desincroniza en el siguiente bump de Material.
Diferencias deliberadas frente al primario: contorno neutro (`outlineVariant`) en vez de relleno
verde, porque no es el CTA de la pantalla, e icono `Image` sin tintar, porque el logo del proveedor
es identidad ajena.

Por qué un componente y no un `OutlinedButton` suelto dentro del fichero de slots: es el segundo de
los dos casos en los que un icono se gana su sitio en un botón
([UI-BUTTON-ICONS-EARN-THEIR-PLACE-001] ya lo nombra: *"provider identity"*), y su sitio natural es
donde vive esa doctrina.

**3 · El slot `socialProviders` lo sirve Paparcar.** `PaparcarSocialProviders` mapea cada
`IdentityProvider` a su par (glifo, etiqueta) de la librería y los pinta todos con el mismo
`PapProviderButton`. El `when` es exhaustivo sobre el sealed: si mañana se activa otro proveedor en
`paparcarLoginConfig`, sale con la forma correcta, no en blanco.

## Criterio de éxito

- El logo, "Paparcar" y la tagline empiezan exactamente en la x del borde izquierdo de los campos
  Email/Password, en login y en registro.
- "Sign in with Google" es una píldora del mismo alto y radio que "Sign In", con la etiqueta en
  `PaparcarType.cta` y la G a todo color.
- `TypographyGuardrailTest` y `ColorGuardrailTest` (Konsist) siguen verdes: sin `fontSize` inline,
  sin `MaterialTheme.typography.*`, sin `Color(0x…)`.
- Previews y galería mock reflejan el cambio sin tocarlas: las dos renderizan
  `paparcarAuthSlots()` real.

## Verificación

- `:composeApp:compileProdDebugKotlinAndroid` ✅ (con `-Werror`: sin warnings nuevos)
- `:composeApp:compileMockDebugKotlinAndroid` ✅ — la galería sigue compilando sin tocarla
- `:composeApp:testProdDebugUnitTest` ✅ — incluidos `TypographyGuardrailTest`, `ColorGuardrailTest`
  y `DividerGuardrailTest` (Konsist)
**En device (29-08, emulador `emulator-5556`, flavor mock, escenario Deslogueado → login real):**

- Header alineado ✅ — medido con `uiautomator dump`, no a ojo: el campo Email empieza en `x=72 px`
  y el bloque de marca también (logo 72 dp = 216 px, luego el título en `x=330`, que es
  72 + 216 + 42 exactos).
- Misma forma y mismo tamaño que el CTA ✅ — "Sign In" ocupa `[72,1691][1272,1835]` y
  "Sign in with Google" `[72,1971][1272,2115]`: **idéntico ancho e idéntica altura (144 px = 48 dp)**.
  La impresión de que el botón de proveedor era más bajo era ilusión del contorno frente al relleno.
- Claro y oscuro ✅ — la G se mantiene multicolor sobre ambos fondos y el contorno se lee en los dos.
- El Redmi (`5f8991cb`) recibió el mismo APK (sha256 verificado en device) y arranca sin `FATAL`.

⏳ **Registro sin ver en device**: comparte header y botón por construcción (mismo composable), pero
no se pudo abrir — pulsar "Sign Up" mata el flavor mock por un agujero de DI **preexistente y ajeno
a este ticket**, documentado aparte en `mock-auth-screens-need-their-viewmodels-001.md`. Producción
no está afectada.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `PaparcarAuthSlots.kt` · `login.socialProviders` | default de la librería | **cerrado** — usa `PaparcarSocialProviders` |
| `PaparcarAuthSlots.kt` · `register.socialProviders` | default de la librería | **cerrado** — mismo componente |
| `PaparcarAuthSlots.kt` · `PaparcarAuthHeader` | centrado | **cerrado** — `Start`, y lo heredan login y registro |
| `PaparcarAuthSlotsPreviews.kt` | invoca `paparcarAuthSlots().login` | **cubierto** — sin cambios, refleja el nuevo estilo |
| `StateGalleryScreen.kt` · grupo "Login (BaseLogin)" | invoca `paparcarAuthSlots().login` | **cubierto** — sin cambios |
| `AuthScreenSlots.reauth.socialSection` | default `IconSocialLoginButtonsSection` | **exento** — Paparcar no monta hoy la pantalla de reauth; si se monta, usará el mismo componente |
| `PaparcarLoginConfig.kt` | declara la oferta (solo Google) | **exento** — este ticket no toca qué proveedores se ofrecen |
