# UI-A-CAPTURED-NAME-NEEDS-ITS-CEILING-001 · el nombre de zona no tiene techo

**Estado:** 🔵 Abierto, sin código

## Problema
El diálogo que captura el nombre de una zona (`AddingZonePeek.kt`, `ZoneNameDialog`) es un
`OutlinedTextField` crudo **sin límite de caracteres**: ni corta ni cuenta. Lo que se teclee ahí
—sin tope— viaja a Firestore como nombre de la zona y se pinta en filas y peeks que asumen una
línea corta.

No ha mordido todavía porque nadie escribe 4.000 caracteres para llamar a "Casa". Es un agujero
tranquilo, no uno inexistente: no hay nada en el camino que lo impida.

## Doctrina violada
«Sistemas, no parches»: [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001] puso el techo y el contador
dentro de `PapTextField` (`maxChars`) precisamente para que el siguiente campo acotado no se
inventara su propia mitad del comportamiento. Este campo es el "siguiente", y hoy no usa
`PapTextField` en absoluto.

## Señales / datos disponibles
- `PapTextField(maxChars = …)` ya corta en `onValueChange` y pinta los caracteres restantes cerca
  del techo (`common_chars_left`, plural en los 9 locales). No hay que construir nada.
- `ZoneNameDialog` usa hoy `OutlinedTextField` directo, con su propio `leadingIcon` (el icono de la
  zona, tintado `PapColor.brandData`) y su `trailingIcon` de limpiar — ambos expresables con
  `PapTextField`, que ya trae el botón de limpiar de serie.

## Diseño
Migrar `ZoneNameDialog` a `PapTextField` con un `maxChars` propio del dominio de zonas (constante en
el modelo de zona, no un número suelto en el diálogo), quedándose con el `leadingIcon` y el
autofocus que ya tiene. Ojo: **aquí el autofocus sí es correcto** —el diálogo no pide consentimiento
de nada, su único trabajo es capturar—, al revés que en el informe de problema.

Antes de fijar la cifra, medir dónde rompe el layout: el nombre se pinta en la fila de zona y en el
peek, y el techo debería ser el del sitio más estrecho, no un número redondo.

## Criterio de éxito
Un nombre por encima del techo no se puede teclear, y el mismo tope se aplica fuera de la UI (el
sitio donde se cree la zona, no solo el diálogo).

## Consumidores auditados
Pendiente — al abrirlo, barrer todos los sitios que pintan `zone.name` para elegir el techo por el
más estrecho.
