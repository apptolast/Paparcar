# PRIV-A-SPOT-NOBODY-LOOKS-AT-NEVER-DIES-001 · Una plaza en una calle que nadie mira no caduca nunca

**Estado:** 🔵 Abierto — 1.2.1. **Decisión del user tomada (16-09): opción (a), TTL nativa.**
**Origen:** revisión independiente, 3.ª vuelta — M4.

## Problema

El borrado de plazas caducadas es **pasivo y lo hace el cliente**: `SpotRepositoryImpl` borra las del
recuadro que alguien está mirando. Funciona donde hay usuarios y **no ocurre nunca** donde no los
hay: una plaza publicada en una calle que nadie vuelve a visitar se queda en Firestore para siempre.

La política §5 dice que las plazas «caducan y desaparecen automáticamente en minutos u horas». Para
el usuario es cierto —ningún cliente las enseña, el filtro las descarta al leer— pero **el documento
sigue existiendo**, con su `reportedBy` dentro. Es retención sin propósito, y crece sola.

## Decisión: TTL nativa de Firestore

Gratis y sin nada que mantener, frente a una función programada que hay que vigilar.

⛔ **Exige un campo `Timestamp`**: la TTL de Firestore no admite un entero, y `expiresAt` es un
`Long` de milisegundos. Hay que escribir un **segundo campo** (`expireAt`, Timestamp) junto al
existente durante una transición, y solo después apuntar la política de TTL a él.

⚠️ Y no es solo escribirlo: la regla de `create` valida `expiresAt`, así que el campo nuevo entra en
esa validación o se puede escribir uno que no coincida con el otro.

## Criterio de éxito

Una plaza caducada desaparece de Firestore sin que nadie mire su calle, y los dos campos no pueden
divergir.
