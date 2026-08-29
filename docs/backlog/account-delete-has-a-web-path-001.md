# ACCOUNT-DELETE-HAS-A-WEB-PATH-001 · Borrar la cuenta sin tener la app instalada

**Estado:** ✅ Done (29-08-2026) — página VIVA en `https://pap-26.web.app/delete-account` (misma
identidad que la política, EN+ES, mailto con asunto/cuerpo prellenados, opción de borrado
parcial), enlazada desde la política §6 (EN y ES, redesplegada) y runbook admin en
`docs/legal/ACCOUNT-DELETE-RUNBOOK.md` (CLI `firestore:delete -r`, orden espejo del use case).
`DATA-SAFETY-FORM.md` actualizado con la URL para el campo del Console.
⏳ Queda la solicitud de prueba end-to-end (email → runbook → uid borrado, verificable vía MCP)
— operacional, cuando haya una cuenta sacrificable.

## Problema
Google Play (Data Safety → *Data deletion*) exige, para toda app que permite CREAR cuenta, una
**URL pública donde el usuario pueda solicitar el borrado de su cuenta y sus datos sin necesidad
de reinstalar la app**. Hoy el único camino es el botón de Ajustes (in-app). Sin esa URL el
formulario de Data Safety no se puede completar honestamente y la ficha puede ser rechazada.

## Doctrina violada
Ninguna interna — es un requisito externo de Play (y buena práctica RGPD: el derecho de supresión
no puede depender de tener el APK instalado).

## Señales / datos disponibles
- Hosting ya operativo: `pap-26.web.app` (privacy-policy servida desde `hosting/public/`).
- El borrado in-app ya barre TODO (perfil, vehículos, historial, zonas, diagnósticos —
  ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001 `0309755d`): la mecánica de barrido existe y está testada.
- Email operativo: el Gmail del proyecto (la política ya lo publica como contacto).

## Diseño (propuesta, decidir al abrir)
Play acepta un flujo de **solicitud** (no exige self-service inmediato). Mínimo viable honesto:
1. Página `pap-26.web.app/delete-account` (misma identidad visual que la política): explica qué
   se borra (la lista de la política §6), y ofrece dos caminos:
   - **Si aún tienes la app**: Ajustes → Eliminar cuenta (inmediato, in-app).
   - **Si no**: solicitud por email (mailto con asunto prellenado) desde la dirección de la
     cuenta; el borrado lo ejecuta el admin vía Admin SDK (bypassa rules) en ≤30 días.
2. Enlazar la página desde la política (§6) y rellenar la URL en el Console (Data Safety).
3. Documentar el runbook admin del borrado manual (qué colecciones, en qué orden — espejo de
   `DeleteAccountUseCase`) en `docs/legal/` para que la promesa de la página sea ejecutable.
Fuera de alcance (decidir coste/beneficio): automatizarlo con Cloud Function + formulario
(requiere Functions/Blaze; con el volumen pre-beta no se justifica).

## Criterio de éxito
URL viva y enlazada (política + Console); una solicitud de prueba termina con el uid borrado de
Auth y de todas las colecciones (verificable vía MCP), sin app instalada por medio.

## Consumidores auditados
Pendiente al abrir: la política §6 (añadir el enlace), `DATA-SAFETY-FORM.md` (campo de la URL),
y los enlaces de Ajustes (no cambian: el in-app sigue siendo el camino primario).
