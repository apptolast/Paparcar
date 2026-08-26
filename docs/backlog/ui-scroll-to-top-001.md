# UI-SCROLL-TO-TOP-001 · Botón "volver arriba" en las listas largas

**Estado:** ✅ EN MASTER (`1bb5b45d`) · validado en **Redmi** en su día; el Oppo no se pudo
actualizar entonces (ver abajo), pero ambos móviles llevan builds posteriores desde entonces.

## Qué

Con la cabecera colapsable ([UI-TOPBAR-COLLAPSE-001]) el historial de un vehículo se recorre entero
sin chrome que estorbe, pero volver al principio son decenas de arrastres. Botón flotante que
aparece cuando ya se ha bajado lo suficiente y devuelve la lista arriba de un salto.

## Decisiones (usuario, 14-08)

- **Forma**: círculo con flecha, sin texto — convención universal, no compite con el contenido y no
  hay copy que traducir a 9 locales (solo el `contentDescription`).
- **Alcance**: solo donde hay listas largas → **Vehículos** (historial) y **Ajustes**. El formulario
  de vehículo y la config Bluetooth caben casi en una pantalla; un botón ahí sería ruido.

## Cómo — `ui/components/PapScrollToTopButton.kt`

- Extensión de `BoxScope`: se coloca solo abajo a la derecha; la pantalla solo le pasa el
  `bottomPadding` que ya recibe de su scaffold (hueco de la barra inferior).
- Visibilidad por **distancia recorrida**, medida en pantallas (`> 1.5` viewports) dentro de un
  `derivedStateOf`. Se probó primero por número de items y **no vale**: una tarjeta de ajustes mide
  el triple que una fila de historial, así que con el umbral calibrado para el historial en Ajustes
  no aparecía ni tras dos pantallas. La distancia se estima con el tamaño medio de lo visible, que
  se auto-normaliza para ambas listas.
- **No usa `GlassSurface`**: el cristal es para lo que flota sobre el MAPA, donde los tiles tienen
  que asomar. Aquí flota sobre una lista opaca → superficie normal (`surfaceContainerHigh` +
  `outlineSubtle`). Color neutro a propósito: es plumbing de UI, no una acción de marca.

## El detalle que no es obvio

Un salto programático (`animateScrollToItem`) **no pasa por el nested scroll**, así que la cabecera
colapsable no se entera y se quedaría retirada sobre una lista que ya está en su inicio — un hueco
donde debería estar el título. Por eso cada pantalla incrementa un contador que viaja al
`expandKey` del scaffold:

- Ajustes: `expandKey = expandHeader`.
- Vehículos: `expandKey = pagerState.settledPage to expandHeader` (comparte la llave con el cambio
  de página del pager, que ya la usaba).

En Vehículos la lista vive dentro de `HistoryContent` (una por página del pager), así que el aviso
sube por `HistoryContent → VehiclePageContent → VehiclesPager → VehiclesContent`.

## i18n

`common_scroll_to_top_cd` en los 9 locales (EN/ES/IT/PT/FR/DE/NL/PL/RO).

## Verificado en device (Redmi 2201117TY, horizontal)

- Ajustes: aparece tras ~1,5 pantallas; al pulsarlo la lista salta arriba, la cabecera se despliega
  y el botón desaparece.
- Vehículos (historial): igual, con el título y las pestañas de vuelta al llegar arriba.

## Pendiente

- **Oppo sin actualizar**: tiene instalado un `1.0.0-beta02` firmado con OTRA clave (instalado a las
  03:06), así que `adb install -r` da `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Reemplazarlo exige
  desinstalar → se pierde la sesión y el historial local: decisión del usuario, no se toca.
- MIUI: el botón flotante de navegación del sistema queda justo encima del nuestro en horizontal.
  Es chrome del OEM sobre la esquina, no un problema del layout, pero conviene mirarlo en vertical.
