# UX-PARKED-STATE-001 — Replantear el estado "vehículo aparcado" (peek, liberar, re-aparcar)

> **Estado**: ✅ IMPLEMENTADO EN MASTER (`d575d83b`, squash de UX-PARK-FLOW-001, 07-08). Decisiones §4
> resueltas con el user. Diseño final = editar entra a colocar pin y se decide al confirmar
> (corregir / he aparcado en otro sitio / borrar), NO menú ⋮ — ver §2.4b. PENDIENTE FIELD/DEVICE:
> el sheet de edición (3 botones) + diálogo de borrado no se han visto en móvil (revisar vía mock
> release → Dev Catalog → galería "edit parking"). Huérfano por limpiar: `home_add_parking_confirm_edit`.
> Hijo de UX-PARK-FLOW-001. Nace de la revisión en device del 06-08 y del feedback del user 07-08.

## 1. AS-IS — qué muestra hoy el peek de sesión aparcada (`ParkingPeek.kt`)

| Zona | Contenido | Observación |
|---|---|---|
| Eyebrow | "TU KAMIQ APARCADO" (verde, o azul si BT) | OK |
| Título | nombre del sitio / dirección | OK |
| Meta | distancia andando + "Aparcado hace X" + **icono editar** (lápiz) | editar = mover el pin DE ESTA sesión (`editingParkingId`) |
| Acción primaria | **"Me voy · libero mi plaza"** FILLED con icono **Logout** | ver §2.1 |
| Acción secundaria | **"Cómo llegar (andando)"** OUTLINED full-width | ver §2.2 |
| Al pulsar "Me voy" | diálogo publicar / solo liberar + `detectionNote` "se inicia la detección…" | ver §2.3 |

Dos botones full-width apilados = peek alto; el 90 % de las aperturas del peek no acaban en
ninguno de los dos (se abre para VER dónde está el coche).

## 2. Defectos señalados (user, 07-08) y propuesta

### 2.1 "Me voy · libero mi plaza" con icono Logout
- **Problema**: Logout es metáfora de SESIÓN DE CUENTA (cerrar sesión), no de dejar una plaza.
  Label largo (dos ideas: me voy + libero).
- **Propuesta**: icono `Icons.Rounded.TimeToLeave` (frontal de coche con "hora de salir" — es
  literalmente este concepto en Material) y label corto **"Me voy"** — la consecuencia (publicar
  o solo liberar) ya la explica el diálogo, no hace falta adelantarla en el botón.

### 2.2 "Cómo llegar" al estilo Google Maps
- **Problema**: hoy es un botón outlined full-width apilado — pesa mucho para ser un intent
  externo, y estira el peek.
- **¿Logo de Google para avisar de que abre Maps?** (pregunta user 07-08) — **NO recomendado**:
  (a) el intent `geo:` abre la app de mapas POR DEFECTO del usuario (puede ser Waze u otra) —
  poner el logo de Google prometería algo que no controlamos; (b) usar el logo/pin de Google
  está sujeto a sus brand guidelines (solo contextos "works with" aprobados) — fricción legal
  gratuita. **Alternativa que comunica lo mismo sin marca**: icono `Directions` (el rombo de
  direcciones que todo Android lee como "navegar") + micro-glifo `OpenInNew` como trailing —
  el patrón estándar de "esto te saca de la app".
- **Propuesta inicial**: fila horizontal de acciones (patrón Maps: pills lado a lado):
  `[ Cómo llegar · tonal, Directions + OpenInNew ] [ Me voy · filled, TimeToLeave ]`.
- **DECISIÓN FINAL (user, device-review 07-08)**: "Cómo llegar" es un **icono redondo**, así que
  queda mejor **junto al de editar**, no en la fila del CTA. → los DOS iconos redondos gemelos
  (`Directions` navegar + `EditLocationAlt` corregir pin) se agrupan en el `metaAction` a la
  derecha de la meta; **"Me voy" recupera el full-width en solitario** como único CTA. Directions
  va primero (alcance más frecuente), editar queda anclado al extremo derecho donde siempre estuvo.
  Implementado: `PapSheetRoundIconButton` (38dp círculo reutilizable) sustituye al `PapIconActionButton`
  (48dp chip, BORRADO por quedar huérfano). [UX-PARKED-STATE-001]

### 2.3 "Me voy" — label, promesa de detección y el caso SIN permisos
- **Qué hace de verdad**: libera la sesión (siempre funciona) + publica si eliges + re-arma la
  vigilancia del coche. La TERCERA pata es la única condicionada a permisos/flag — el botón
  nunca "no funciona del todo", lo que puede fallar es la promesa de re-vigilar.
- **Propuesta (sistema, no parche)**: el `detectionNote` del diálogo se resuelve con el MISMO
  `DetectionStory`/readiness del relato único, con 3 variantes:
  1. detección armable → "Seguiremos pendientes de tu próximo aparcamiento";
  2. coche pasa a activo → "Pasaremos a vigilar este coche";
  3. detección apagada/bloqueada → "No podremos detectar tu próximo aparcamiento por nuestra
     cuenta" — SIN CTA en el diálogo: al liberar, la superficie de relato de Home mostrará ya
     su fila "Activa la detección" (el sistema se auto-explica en el sitio correcto).
- **Label**: alternativas valoradas — "Me voy" (corto, natural, momento exacto),
  "Liberar plaza" (más funcional, menos humano), "Dejar la plaza" (ambiguo con cancelar).
  Recomendación: **"Me voy"** + icono `TimeToLeave`.

### 2.4b Estructura: corregir pin vs re-aparcar vs borrar — ✅ IMPLEMENTADO 07-08 (decidir al confirmar)
> **Estado**: ✅ implementado en la rama (07-08). **Giro de diseño respecto al menú "⋮"**: como corregir
> y re-aparcar son la MISMA herramienta (colocar el pin) y solo difieren en lo que SIGNIFICA el confirmar,
> se decide **al final, con el pin ya colocado**, no en un menú a ciegas (idea del user). Implementación:
> - El meta del peek vuelve a **UN solo icono redondo lápiz** (`EditLocationAlt`, junto a Directions) que
>   entra directo a `AddingParking` en modo edición (`editingParkingId`).
> - El pie del sheet de edición ofrece las 3 salidas: **Corregir ubicación** (filled → `ConfirmAddParking(asNewSession=false)`,
>   misma sesión), **He aparcado en otro sitio** (outlined → `ConfirmAddParking(asNewSession=true)`, sesión
>   nueva mismo vehículo), **Borrar registro** (rojo, detrás de `PapAlertDialog` Destructive).
> - `ConfirmAddParking` pasa a `data class(asNewSession)`; el VM resuelve el vehículo de la sesión editada y
>   llama a `saveManualParking(editingParkingId=null, targetVehicleId=…)` que ya **suplanta en silencio**.
>
> Decisiones user: (1) decidir al confirmar (2 botones + borrar) — SÍ; (2) re-aparcar = **reemplazar en
> silencio** (el modelo ya suplanta por vehículo en `ConfirmParkingUseCase.kt:225-241`; NO publica la vieja).
> Strings en 9 locales (menu_correct/repark/delete + delete_confirm_*; `home_parking_menu_cd` del ⋮ borrado).
> Trade-off aceptado: borrar exige entrar a edición primero (= comportamiento original; acción rara).
> Falta: device + galería (sheet de edición con 3 botones + diálogo). NOTA: `home_add_parking_confirm_edit`
> quedó huérfano (limpiar en pasada de strings).

El user señala el riesgo exacto: corregir y re-aparcar "son casi lo mismo" a ojos del usuario
(dos iconos parecidos = error garantizado). Mejor práctica: **no obligar a elegir herramienta
a ciegas; hacer la pregunta en el momento de la decisión**:

- El peek mantiene **UNA sola utilidad** de gestión: el menú "⋮" (sustituye al lápiz), con tres
  entradas CON texto y subtítulo de una línea — el label desambigua, no el glifo:
  1. ✏️ **Corregir ubicación** — "El pin no está donde aparqué" → edit actual (`editingParkingId`).
  2. 📍+ **He aparcado en otro sitio** — "Cierro esta plaza y marco la nueva" → re-park (§2.4).
  3. 🗑️ **Borrar registro** (rojo) — "Este aparcamiento nunca existió" → hoy vive dentro del
     sheet de editar; sube al menú como destructiva explícita.
- Jerarquía resultante del peek (frecuencia → prominencia):
  navegar (frecuente) y me-voy (el cierre del ciclo) = fila de pills;
  gestión del registro (rara) = un solo "⋮" con menú explicado;
  borrar (destructiva) = última del menú, en rojo, con confirmación.
- Alternativa descartada: lápiz + segundo icono `AddLocationAlt` lado a lado — compacto pero
  ambiguo justo donde el propio user prevé la confusión.

### 2.4 Re-aparcar — "he aparcado en otro sitio" SIN tocar la sesión actual
- **Problema real**: la detección puede perder un aparcamiento nuevo (OEM-kill, Doze). Hoy el
  único remedio en el peek es EDITAR, que **mueve el pin de la sesión vieja**: falsea el
  histórico (una sesión con dos sitios) y, peor, **se salta el ciclo de liberar** — la plaza
  vieja nunca se publica a la comunidad.
- **Propuesta**: acción nueva "He aparcado en otro sitio" (icono `AddLocationAlt`, junto al
  lápiz — el clúster de utilidades del meta) que entra en `AddingParking` para el MISMO vehículo
  **sin** `editingParkingId`. Al confirmar, el modelo debe **suplantar**: cerrar la sesión vieja
  (con su pregunta de publicar la plaza que dejas) + crear la nueva. Regla mental:
  *editar = "el pin está mal"; re-aparcar = "el coche se ha movido de verdad".*
- **Trabajo de modelo requerido** (verificar antes de UI): ¿qué hace hoy
  `SaveManualParkingUseCase`/`ConfirmParkingUseCase` si el vehículo ya tiene sesión activa?
  El supersede-por-distancia existe en la detección automática; el camino manual hay que
  auditarlo y darle la misma semántica (release viejo → confirm nuevo, sin ventana de 2 activos).
- **AUDITORÍA 07-08 + DECISIÓN user**: `saveNewParkingSession()` YA suplanta por vehículo
  (`ConfirmParkingUseCase.kt:225-241`: borra la fila vieja + quita su geocerca huérfana), pero
  **en silencio** — no dispara release/publish. El user eligió **reemplazar en silencio (como hoy)**:
  re-aparcar solo cablea `EnterAddParkingMode(editingParkingId=null, targetVehicleId=parking.vehicleId,
  initialGps=userGps)`, CERO trabajo de modelo. La plaza vieja NO se publica (asumido). Si más
  adelante se quiere honrar el ciclo comunitario, reabrir con la variante "liberar con opción de publicar".

## 3. Simular TODO en contexto real (Dev Catalog)

La galería enseña los composables aislados (útil para claro/oscuro y copys), pero el user
necesita ver los estados EN el grafo real. Faltan tres palancas en `MockScenario` +
`DevCatalogScreen`:

1. **"Sesión propia aparcada"** — semilla en `FakeUserParkingRepository` una sesión activa del
   vehículo activo → en Home real: línea "Vigilando tu…", card del coche aparcado, `ParkingPeek`
   al tocarla, diálogo de liberar, re-aparcar cuando exista.
2. **"Confirmación pendiente"** — dispara el sheet "¿Has aparcado el X?" con su cuenta atrás
   dentro de Home real (vía el mismo camino que usa el detector para `ShowParkingConfirmation`).
3. **"BT armado"** — vehículo activo con `bluetoothDeviceId` + BT on → línea "Vigilando (BT)".
+ presets rápidos "Aparcado (vigilando)" y "Confirmación pendiente".

## 4. Decisiones para el user (ronda 2, 07-08)

1. Fila horizontal `[Cómo llegar · Directions+OpenInNew][Me voy · TimeToLeave]` — ¿OK?
   (logo de Google descartado por Claude: abre la app de mapas por defecto, no necesariamente
   GMaps, y el logo tiene fricción legal — §2.2).
2. Menú "⋮" con las 3 entradas explicadas (corregir / re-aparcar / borrar) en vez de iconos
   sueltos — ¿OK? (§2.4b).
3. `detectionNote` del diálogo sensible a permisos (3 variantes, sin CTA — la superficie de
   relato pide los permisos en Home tras liberar) — ¿OK? (§2.3).
4. La línea del relato (UXP-a): ¿card silenciosa (opción A de `ux-detection-story-001.md` §7)?

## 5. Secuenciación propuesta
1. Palancas de simulación (§3) — desbloquea revisar TODO en device de verdad.
2. Redesign visual del peek (§2.1-2.3) — solo UI + strings.
3. Re-aparcar (§2.4) — necesita auditoría del use case + posible trabajo de modelo.
Cada pieza con galería/escenarios en sync (regla ⛔) y strings en 9 locales.
