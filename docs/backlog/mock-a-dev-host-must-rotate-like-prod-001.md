# MOCK-A-DEV-HOST-MUST-ROTATE-LIKE-PROD-001 · El banco de pruebas no puede ser más destructivo que la app

**Estado:** ✅ Done.

## Problema

Rotar la pantalla dentro del Dev Catalog **destruye todos los ViewModels de la app**. No es una
sospecha: la causa está en una línea.

```kotlin
// app/src/mock/kotlin/com/rndeveloper/paparcar/dev/DevRoot.kt:88-91
else -> key(session) {
    val owner = remember(session) {           // ⛔ remember, no rememberSaveable
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
```

Un `ViewModelStoreOwner` creado con `remember` dentro de la composición no puede sobrevivir a una
recreación de Activity, y un `ViewModelStore` tampoco es guardable en `savedInstanceState`. Así que
cada rotación en mock entrega un store vacío: `HomeViewModel`, `AppViewModel` y el resto nacen de
cero.

En producción **no pasa**: `MainActivity` llama a `App()` sin proveer `LocalViewModelStoreOwner`
(`app/src/main/kotlin/com/rndeveloper/paparcar/MainActivity.kt:95-97`), así que el dueño es la
propia Activity y sus ViewModels sobreviven al cambio de configuración, como en cualquier app
Android.

Resultado: **el banco de pruebas es más duro que el producto**, y en la dirección que más engaña —
un comportamiento que en mock se ve "roto" puede estar perfectamente bien en un móvil, y al revés,
lo que uno valida rotando en mock no es lo que el usuario vivirá.

### El dato que lo destapó (22-11, verificación en frío de HOME-FIRST-ENTRY-FLOW-001)

Recorriendo la cadena de primera entrada en el emulador (Pixel_8, API 37, APK mock de
`f257ff766`), rotando a mitad de la cadena con el preset «Primer Home · cadena completa»:

| prompt pendiente al rotar | qué pasa | por qué |
|---|---|---|
| regalo de Fundadores | **sobrevive** | se re-deriva del entitlement + la memoria de bienvenida |
| editor del primer aparcamiento | **se retoma** | memoria persistida `started`/`completed` |
| invitación a detección | **se pierde**, y la cadena salta al editor | `showDetectionPitch` vive SOLO en `HomeViewModel` |

El tercero es reproducible y se midió dos veces. En un móvil real con la app de verdad no ocurriría
al rotar — ocurriría sólo tras una muerte de proceso, que es otra cosa y mucho menos frecuente.

⚠️ Y hubo un coste real de diagnóstico: durante esa verificación esta diferencia produjo un falso
positivo que costó varias pasadas descartar («el regalo ha vuelto a salir»), porque el host mock
estaba mezclando su propio reseteo con el comportamiento del producto.

## Doctrina violada

No es una regla escrita de `CLAUDE.md`, es la **premisa** del flavor mock: el Dev Catalog existe
para *«probar pantallas y estados en device»*, y `MOCK-THE-DEV-CHIPS-MUST-NOT-COVER-THE-APPS-CONTROLS-001`
ya estableció el principio del que esto es el mismo caso — **el andamiaje de dev no puede tapar ni
deformar lo que un build de dev existe para mirar**. Allí tapaba controles; aquí deforma el ciclo
de vida.

Y se cruza con [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001] por el lado contrario: aquí el banco
no miente diciendo que todo va bien, miente diciendo que algo va mal.

## Señales / datos disponibles

- `DevRoot.kt:88-91` — el owner y su `remember(session)`.
- `DevRoot.kt:55-60` — `inApp`, `showGallery`, `session` y `darkOverride` **sí** son
  `rememberSaveable`: la intención de sobrevivir a la rotación ya está ahí, sólo que el store se
  quedó fuera.
- `DevRoot.kt:97` — `DisposableEffect(session) { onDispose { owner.viewModelStore.clear() } }`, que
  es lo que hay que conservar: limpiar al CAMBIAR de sesión, no al recomponer.
- `MainActivity.kt:95-97` — el contraste con producción.
- `StateGalleryScreen.kt` — la galería tiene el mismo síntoma por otra vía (sus variantes usan
  `remember`, así que rotar devuelve a la lista y cambia de variante). Entra en el barrido.

## Diseño

El invariante: **el `ViewModelStoreOwner` del App montado en mock dura lo que dura la SESIÓN del
catálogo, no lo que dura la composición.** La sesión ya tiene identidad (`session: Int`,
`rememberSaveable`); lo que falta es colgar el store de algo que sobreviva a la recreación.

Dos caminos, a decidir al implementar (el ticket se abre sin cerrar la elección):

1. **Retener el store fuera de la composición** — un contenedor por `session` sostenido por la
   Activity (p. ej. un `ViewModel` propio del `DevMainActivity` que guarde el mapa
   `session → ViewModelStore`, que es el patrón con el que Navigation Compose resuelve exactamente
   este problema). Sobrevive a la rotación y sigue limpiándose al cambiar de preset.
2. **Usar el owner de la Activity y recrear por otra vía** — dejar que el App use el store de
   `DevMainActivity` y conseguir el "grafo nuevo por entrada" limpiando ese store al entrar desde
   el catálogo. Más simple, pero hay que comprobar que el reseteo por preset sigue siendo total.

⛔ Lo que **no** vale: `rememberSaveable` sobre el store (no es serializable) ni quitar el
`onDispose` (volvería el grafo viejo al cambiar de preset, que es justo lo que el owner por sesión
existe para evitar).

## Criterio de éxito

- Rotar con la invitación a detección abierta en el preset «Primer Home · cadena completa»
  **la conserva**, una sola vez, y al contestarla la cadena sigue por el editor.
- Rotar con el regalo abierto y con el editor abierto los conserva (hoy el primero sobrevive por
  re-derivación y el segundo por re-oferta: después deben sobrevivir por lo mismo que en prod —
  porque el ViewModel sigue vivo).
- Cambiar de preset desde el catálogo sigue dando un grafo **nuevo**: sin sesión de demo heredada,
  sin memorias de la ejecución anterior. Es la propiedad que el owner por sesión protege y que no
  se puede perder al arreglar esto.
- La galería de estados conserva su variante al rotar.
- `./gradlew testAll` en verde y `assembleMockDebug` sin tocar prod.

## Consumidores auditados

- ✅ `DevRoot.kt` — el owner pasa a [DevSessionStores], un `ViewModel` de la Activity. La limpieza
  deja de colgar de la composición: `retireAllBut(session)` al ENTRAR en una sesión y `retireAll()`
  en el botón DEV, que es el acto de salir.
- ✅ `DevCatalogScreen.kt` — `onEnter { session++ }` no cambia: sigue siendo quien define "sesión
  nueva", y es justo lo que mantiene el grafo limpio por preset.
- ✅ `StateGalleryScreen.kt` — la variante abierta vivía en un `remember` sobre un `Variant` (que
  lleva un composable y no es guardable). Pasa a `rememberSaveable` sobre su DIRECCIÓN —
  `"<grupo>§<nombre>"`—, resuelta contra `galleryGroups`. `isolated` va por el mismo camino.
- ✅ `DevMainActivity` — `ShotPreset.applyTo` sólo en la creación PRIMERA
  (`savedInstanceState == null`). Re-aplicarlo en una recreación reseteaba el escenario debajo de
  una sesión de capturas ya avanzada [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001].
- ✅ `MockMainViewController` (`:mockkit`) — monta `ComposeUIViewController { App() }` sin proveer
  `LocalViewModelStoreOwner`, así que no tiene el defecto. Nada que cambiar.

## Fuera de alcance (deliberado)

**`showDetectionPitch` no se persiste en este ticket.** Que una invitación pendiente muera con el
proceso es coherente con su diseño — es un *one-shot* en el momento de la concesión, y el banner y
el checklist siguen siendo los dueños del ask, verificado en device el 22-11. Si se decide que debe
sobrevivir a una muerte de proceso, es un ticket de producto aparte, no un arreglo del banco.

## Resultado

`DevSessionStores` es un `ViewModel` de `DevMainActivity` que guarda un `ViewModelStore` por
sesión del catálogo. Sobrevivir a la recreación lo da ser ViewModel de la Activity; la otra mitad
del contrato —entrar desde el catálogo da grafo NUEVO— la sigue dando la clave de sesión.

⛔ Lo que NO se ha hecho, a propósito: persistir `showDetectionPitch`. Que una invitación pendiente
muera con el proceso es coherente con su diseño (es un *one-shot* en el momento de la concesión) y
el banner y el checklist siguen siendo los dueños del ask. Si se decide lo contrario, es un ticket
de producto, no del banco.

## Validación

- `./gradlew testAll --rerun-tasks`: **BUILD SUCCESSFUL**, 95/95 tareas ejecutadas, 0 fallos.
- Emulador Pixel 8 (API 37), APK mock con sha256 verificado **en device**, y la rotación confirmada
  midiendo las dimensiones de la captura en cada paso — con el ajuste de `settings` a secas la
  rotación se revertía sola y las pruebas salían vacías sin avisar:
  - preset «Primer Home · cadena completa», invitación a detección abierta → rotar y volver: **el
    diálogo sigue, una sola vez**. Es el caso que fallaba.
  - editor del primer aparcamiento abierto → rotar y volver: **sigue**, y no reaparece ni el regalo
    ni la detección ya contestados.
  - cambiar de preset desde el catálogo → **grafo nuevo**, sin sesión de demo heredada.
  - galería de estados, variante abierta → rotar y volver: **sigue en la variante**.

⚠️ Dos trampas de banco que costaron tiempo y conviene no redescubrir:
1. **`adb install -r` dijo `Success` sin reemplazar nada.** El emulador había revertido `/data` al
   arrancar en frío y seguía corriendo un APK viejo; las pruebas de la galería «fallaban» contra
   código que no estaba instalado. Lo cazó comparar el sha256 del APK local con el del device —
   la misma precaución que ya estaba escrita para MIUI, que resulta que no es sólo de MIUI.
2. **`uiautomator dump` puede petar él solo** (`registerUiTestAutomationService`) y dejar el volcado
   anterior en disco: los `grep` devuelven datos viejos y parecen resultados. Con animaciones en
   curso pasa a menudo; la captura de pantalla no miente.
