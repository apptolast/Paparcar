# UI-PAYWALL-THE-PITCH-IS-OURS-THE-PRICE-IS-THE-STORES-001 · Paparcar dibuja su propio paywall

**Estado:** ✅ Done · rama `bugfix/UI-PLUS-A-BRAND-FILL-IS-NOT-A-METALLIC-FINISH-001-metallic-finish`
(comparte rama con el ticket del acabado metálico: son la misma superficie y el mismo turno de trabajo)

## Problema

Petición del user (07-09): en la pantalla de pago quiere ver la **comparativa Free vs Plus** —una
card oscura y otra metalizada, enfrentadas— y **las dos suscripciones (1 mes y 1 año)**.

Nada de eso era posible con lo que había, y la razón no era de diseño sino de arquitectura: la
pantalla **cedía la superficie entera a la plantilla remota de RevenueCat** (`purchases-kmp-ui`) y
solo dibujaba algo propio cuando no había tienda. Es decir: el diseño que se pidiera solo lo vería
quien NO pudiera comprar.

## La decisión, y por qué

Había dos caminos de verdad. El razonamiento importa porque el default de la industria es el
contrario al que elegimos:

### A · Seguir con la plantilla remota
- ✅ Precios, planes, textos del trial y layout se cambian desde el dashboard **sin release**.
- ✅ RevenueCat resuelve restaurar compras y los textos legales de tienda.
- ❌ **No puede renderizar esto.** Dos cards enfrentadas, una vestida con `PapPremiumSurface`, son
  material propio de la app; el sistema de plantillas del dashboard no tiene forma de alcanzarlo.
- ❌ Todas las demás superficies de Plus (Ajustes, pie del historial) son metálicas. La única
  pantalla que tiene que **cerrar la venta** sería la única que no parece Paparcar.

### B · Dibujarlo nosotros ← **elegido**
- ✅ Es lo único que entrega lo pedido.
- ✅ **La plantilla no estaba creada** (seguía pendiente en el dashboard), así que lo que un usuario
  de pago habría visto era el *fallback genérico del SDK*. La elección real no era «nuestro diseño
  contra una plantilla diseñada», era **«nuestro diseño contra un placeholder»** — y de paso
  desaparece una tarea pendiente del user.
- ❌ Un cambio de layout ahora exige release. El dashboard podía haberlo hecho en remoto.
- ❌ Restaurar compras pasa a ser nuestro.

> ⛔ **Lo que NO cambia es quién manda en el dinero.** Precio, moneda, trial, hoja de pago y recibo
> siguen siendo íntegramente de la tienda: se leen **vivos en cada apertura**, nunca se hardcodean y
> nunca se cachean entre sesiones. La regla de MONEY-001 —«RevenueCat es la única fuente de verdad
> de la suscripción»— es sobre el ESTADO y el PRECIO, no sobre quién pinta los píxeles.

## Diseño del layout, y por qué así

También aquí hubo elección real:

| | Pro | Contra |
|---|---|---|
| **Dos cards enfrentadas** ← elegido | El tier Plus es un **objeto entero** al que señalar; el acabado solo vende así | ~150 dp de ancho por card en un móvil de 360 dp: todo tiene que sobrevivir al wrap en 9 idiomas |
| Tabla con etiqueta compartida | Más escaneable, sin riesgo de wrap | Reduce la superficie premium a una franja vertical |
| Filas apiladas (lo que había) | Lo más seguro | No es lo pedido, y persuade menos |

Se eligió cards porque **esto es una pantalla de VENTA, no una ficha técnica**. El coste —la anchura—
se pagó **reescribiendo el copy**, no dejándolo desbordar: los valores pasaron de frases
(«todos tus aparcamientos, sin límite de fecha») a fragmentos («Tu historial completo»). El layout
condiciona el copy; si no se acepta eso, el layout se rompe en alemán.

Los **planes**, en cambio, van apilados a ancho completo y no enfrentados: una fila de plan carga
periodo, precio, equivalente mensual y badge de ahorro, y cuatro cosas no caben en medio móvil. Los
dos layouts difieren porque diferente es lo que sostienen.

## Lo construido

- `PlusOffer` (dominio) — plan, precio formateado por la tienda, `amountMicros`, equivalente
  mensual y días de trial **leídos del producto**, no asumidos.
- `annualSavingsPercent` — función pura. Calcula sobre `amountMicros`, **nunca parseando el precio
  formateado**: «1,99 €» no se parsea en 9 locales con separadores distintos. Devuelve null si no
  hay ahorro real o si es demasiado pequeño para un badge (<5 %) — un descuento que resulta ser 0 %
  cuesta más confianza de la que gana.
- `EntitlementRepository.loadOffers()` + `GetPlusOffersUseCase`. Lista vacía es respuesta NORMAL
  (sin key, sin productos, o fallo de red): la pantalla enseña la comparativa **sin ningún precio**.
- Paywall MVI completo: seleccionar plan, comprar, estado de compra en curso y de compra fallida.
- Galería de estados: 5 variantes nuevas (con planes, mensual elegido, comprando, fallida, sin tienda).

### Borrado limpio

Al dejar de usarse la plantilla remota quedó código muerto, y se retiró entero:
`purchases-kmp-ui` (dependencia + catálogo), `IsStoreAvailableUseCase`, y
`EntitlementRepository.isStoreAvailable` con sus cuatro implementaciones. Eso además **elimina una
señal duplicada**: antes la pantalla preguntaba dos cosas («¿hay tienda?» y «¿hay ofertas?») cuando
solo una es accionable. Hoy pregunta una: **si no hay ofertas, no hay precios**.

## Criterio de éxito

- Ni un precio escrito en código de producción. El único literal vive en los fakes del flavor mock.
- Sin ofertas → ni un precio en pantalla, y una línea honesta.
- El badge de ahorro solo aparece si el ahorro es real y ≥5 %.
- Verificado en device.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `presentation/paywall/*` | ✅ reescrito (Screen, State, Intent, ViewModel) |
| `data/repository/RevenueCatEntitlementRepositoryImpl` | ✅ `loadOffers` + mapeo de precio y trial |
| `fakes/…/FakeEntitlementRepository` (mock) y `commonTest/…` | ✅ ambos implementan `loadOffers` |
| `di/DomainModule` | ✅ alta de `GetPlusOffersUseCase`, baja de `IsStoreAvailableUseCase` |
| `dev/StateGalleryScreen` | ✅ 5 variantes nuevas |
| `commonTest/…/PaywallViewModelTest` | ✅ actualizado a las dependencias nuevas |
| Strings | ✅ 16 keys nuevas × 9 locales; 9 retiradas × 9 locales |

## Restaurar compras — incluido en este ticket

Al dejar de usar la plantilla remota, restaurar pasó a ser responsabilidad nuestra. Se hizo aquí
mismo en vez de aplazarlo, porque **Play lo exige para suscripciones** y sin ello el ticket dejaba
la app en peor estado del que la encontró.

Una suscripción pertenece a la **cuenta de Google** que la compró, no al teléfono ni a la
instalación. Hay situaciones normales donde un usuario que YA paga abre la app y esta no lo sabe:
reinstalar, móvil nuevo, borrar datos, o haber comprado antes de iniciar sesión. Sin un botón de
restaurar, su única salida visible es **pagar dos veces**.

- Cadena completa: `RevenueCatDataSource.restore()` (vía `awaitRestore()`) → repositorio →
  `RestorePurchasesUseCase` → intent en el paywall.
- Devuelve **Boolean**, no Unit: "esta cuenta no tiene nada" es una respuesta NORMAL que merece una
  frase llana, no un error. Por eso `restoreFoundNothing` no se pinta en rojo.
- Un fallo de red y una cuenta vacía dan **el mismo mensaje** a propósito: para quien está en un
  aparcamiento, ambos significan "no hemos podido devolverte nada", y una segunda redacción para el
  error de red no ayuda a nadie.
- Un restore con éxito **no necesita flag**: el entitlement emite y la pantalla se cierra sola,
  igual que tras una compra.
- 3 tests: encuentra Plus / no encuentra nada / la llamada falla (y no concede nada).

📌 Ojo al mantenerlo: `logIn(uid)` ata la identidad de la tienda a la cuenta de Paparcar, así que en
la mayoría de casos Plus vuelve solo al iniciar sesión. Restaurar es la red debajo, no el mecanismo
principal.

## Correcciones tras verlo en device

- **Footer fijo — y luego más ligero.** Primero se anclaron planes + botón + nota, con el argumento
  de que un precio que se va de pantalla obliga a decidir de memoria. En el móvil ese footer se
  comía media pantalla y **aplastaba la comparativa, que es la venta**. Ahora scrollea todo lo que
  se LEE (comparativa y planes) y solo se ancla la ACCIÓN. 🔑 Lo que lo hace seguro es que el botón
  **lleva el precio en su propia etiqueta** («Consigue Plus por 13,99 €»): lo que tiene que estar
  siempre visible es lo que vas a pagar, y eso viaja con el botón.
- **El botón de compra lleva el METAL** (`PapPremiumButton`), no el relleno plano de marca. Es la
  única acción de la app que *es* el producto, así que va hecha del producto. ⛔ No se añadió como
  un tercer `PapButtonTone`: un tone es un SIGNIFICADO (marca, destructivo), y "premium" no es un
  significado que un botón pueda tener en cualquier pantalla — sería invitar a `tone = Premium` en
  botones que no venden nada.
- **`−41%%`**: el badge imprimía el doble porcentaje. Compose Resources **no es `String.format`** —
  leído en `StringResourcesUtils.kt` de la 1.12, es un único `Regex("""%(\d+)\$[ds]""").replace()`
  que no toca nada más. Un `%` suelto es seguro. Documentado en CLAUDE.md como
  `COPY-PERCENT-IS-NOT-ESCAPED-001`.
- **El logo, con su disco y a su tamaño**. Tres errores encadenados, todos míos:
  1. Usé el iconmark **sin disco** — y el logo siempre lo lleva.
  2. La marca tenía **su propio degradado simplificado** en vez de la receta de bandas del card; un
     glifo pequeño solo cruza el centro de un gradiente, así que salía verde plano. Ahora ambos
     usan `metallicSweep()`.
  3. **Dos insets apilados**: `paparcar_iconmark_white` ya trae el 0,8 del logo real, y yo le
     aplicaba encima otro 0,58 → el glifo quedaba al ~0,46 del disco.
  Además el tamaño era inventado (60 dp) en vez del canónico. Ahora existe `PAPARCAR_LOGO_SIZE`
  (96 dp) y lo comparten `PaparcarLogo` y el escudo de Plus: un logo que mide 96 en una pantalla y
  60 en la siguiente no es el mismo logo.
  4. Y el glifo seguía **fino y pequeño dentro del disco**: el dibujo solo abarca ~54 % de su propio
     lienzo, así que con `size == disco` el coche quedaba holgado y sus ruedas (trazo de 6) finas.
     Se resuelve con `GLYPH_OVERSCAN = 1.22`, que llena más el disco **y engorda el trazo a la vez**.
     ⛔ Techo medido ≈1.35: pasado eso la esquina de las ruedas cruza el círculo y el disco la
     recorta. La alternativa —subir el `strokeWidth` del vector— habría creado una copia del logo
     solo para premium, divergente del splash, la insignia de Auth y el launcher.
- **El medallón vuelve al principio del título** (Ajustes y pie de historial). Lo había eliminado en
  vez de recolocarlo. ⚠️ Va en TINTA, no en verde: esas filas viven sobre el lecho metálico, que ES
  verde marca — el mismo motivo por el que el CTA del historial va relleno de tinta.

## ¿Y el resto de la app? — verificado, no había nada que propagar

Pregunta del user al final: aplicar el acabado nuevo «al resto de componentes». **Ya estaba
aplicado**, y esa es justamente la prueba de que la receta única funciona: el metal se define en
`PapPremiumSurface`, así que los cambios de este ticket (bandas múltiples, grano, bisel, croma más
baja) llegaron solos a Ajustes y al pie del historial sin tocar esos ficheros.

`grep` de superficies Plus en toda la app → **exactamente tres**, y las tres al día:

| Superficie | Estado |
|---|---|
| `presentation/paywall/PaywallScreen` | ✅ |
| `presentation/settings/SettingsScreen` (fila de Plus) | ✅ heredado |
| `presentation/vehicles/HistoryContent` (pie gateado) | ✅ heredado |

No hay un cuarto sitio: en Home, el toque de un usuario free sobre la campana **enruta al paywall**,
no pinta superficie propia.

`PapPremiumMark` y `PapPremiumButton` siguen siendo solo del paywall a propósito: el escudo es el
héroe de UNA pantalla, y el botón metálico es la única acción que *es* el producto.

### Verificado en los DOS temas (07-09, Oppo)

El tema claro no se había visto nunca en device, y aquí es donde la restricción se invierte (tinta
blanca ⇒ el brillo es el stop peligroso). Comprobado: lecho verde profundo, glifo y texto en
blanco, CTA blanco sobre el metal en el pie del historial. Legible y coherente con el oscuro.

## Pendiente

- Nada de código. Falta que el user cree los productos en Play y revise los precios por país.

### Nota de facturación · dónde vive

«Se renueva automáticamente. Cancela cuando quieras» se movió del footer fijo a **debajo de las dos
filas de plan**. No es solo por altura: la frase explica **qué compromete elegir un periodo** —cómo
se renueva y cómo se para—, así que responde una pregunta que el lector se está haciendo justo ahí.
El footer fijo queda en lo estrictamente irrenunciable: el botón (que lleva el precio en su
etiqueta) y la vía de vuelta para quien ya pagó.

### Inventario final de superficies Plus

Auditado por `grep` al cerrar: **todo composable que le habla de Plus al usuario pasa por la
superficie premium**, sin excepciones.

| Composable | Pieza premium que usa |
|---|---|
| `presentation/paywall/PaywallScreen` | `PapPremiumMark` (escudo) + `PapPremiumSurface` (card Plus) + `PapPremiumButton` (comprar) |
| `presentation/settings/SettingsScreen` | `PapPremiumSurface` (fila de Plus) |
| `presentation/vehicles/HistoryContent` | `PapPremiumSurface` (pie) + `PapPremiumCta` (CTA sobre metal) |

📌 `PapPremiumButton` y `PapPremiumCta` son **dos piezas distintas a propósito**, no una duplicada:
la primera ES el metal (va sobre superficie normal), la segunda va SOBRE el metal y por eso se
invierte a pastilla de tinta. Un botón metálico sobre un card metálico desaparecería.
