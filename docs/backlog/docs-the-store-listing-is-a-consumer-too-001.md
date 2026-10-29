# DOCS-THE-STORE-LISTING-IS-A-CONSUMER-TOO-001 · la ficha de Play no estaba en la tabla que la obliga

**Estado:** ✅ **Done** (27-10-2026) · rama `docs/DOCS-THE-STORE-LISTING-IS-A-CONSUMER-TOO-001-table`
· worktree `../Paparcar-consumers` — ambos se retiran al cerrar.

## Problema

`CLAUDE.md` tiene una tabla que dice qué hay que mover cuando cambia la verdad del producto: *«La web
es un consumidor más de la verdad del producto»*. Dispara con un límite del plan free, una feature
que la web nombra, el logo, la política… y obliga a que la web entre **en la misma tarea**.

**La ficha de Play no aparecía en esa tabla**, y promete lo mismo a más gente.

Lo que costó, medido: el 14-10 el marco público de las gasolineras pasó a «lo de alrededor es gratis,
lo que tú eliges es Plus». La web se movió en esa misma tarea, como manda la tabla. La ficha no
estaba en la lista, así que no se movió — y la tienda siguió **trece días** vendiendo como Plus unos
avisos que el plan gratuito ya recibía (`PLAY-A-FREE-ALERT-READS-AS-PAID-001`).

📌 Ese ticket arregló el síntoma —la ficha ya describe 1.5.0—, y lo dejó escrito como follow-up: *la
causa es que la ficha no está en la tabla*. Esto es la causa.

## Doctrina aplicada

*Sistemas, no parches.* Mover la ficha una vez no evita la siguiente: lo que falla es **la lista de
consumidores**, y vive en un sitio — esa sección de `CLAUDE.md`.

## Diseño

La sección pasa a nombrar **las dos superficies públicas** y la ficha gana su bloque. ⛔ Lo que **no**
se hace es añadir una fila más a la tabla de la web, y la razón es el error contrario: **los dos
disparadores no son iguales**.

- La web describe **el producto**: se mueve en la misma tarea que el cambio.
- La ficha describe **el build PUBLICADO**: nombrar algo que aún no ha salido sería vender lo que la
  app no hace. De ahí los dos únicos casos, que es lo que el bloque nuevo deletrea: si el cambio ya
  está publicado y la ficha promete otra cosa, se mueve **ahora**; si no lo está, entra en el corte
  de esa versión y no en la tarea.

Y como ahora hay forma de saber qué está publicado sin abrir el navegador, el bloque cita
`cut.py --phases verify` (de `RELEASE-THE-CUT-REACHES-THE-STORE-001`) como la respuesta a esa
pregunta. Antes de hoy no existía, y por eso el caso «ya está publicado» no se podía comprobar.

Se llevan al bloque, además, las dos reglas que ya se habían pagado dos veces y sólo vivían en los
docs de la ficha: el **tope de 4000 se mide antes de escribir, en FR y DE y nunca en en-US**, y la
cadena `published-listing.txt → LISTING-v<ver>.md → paste/` no se edita a mano.

## Criterio de éxito

- [x] la sección de `CLAUDE.md` nombra las dos superficies, y la ficha tiene su bloque con los dos
      casos, su cadena, su guard, su checklist y el tope de 4000
- [x] ⛔ **nada parsea `CLAUDE.md`** — verificado: ningún test de `shared/src` ni `app/src` lo lee, así
      que reestructurar la sección no puede romper un guard
- [x] `./gradlew testAll --rerun-tasks` verde

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `CLAUDE.md`, sección de la web | ✅ es el cambio |
| `WebSiteParityGuardrailTest.kt` · `docs/release/RELEASE-PROCESS.md` | ✅ **exentos**: citan el ticket-ID `WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001` en prosa, no el formato del encabezado |
| `web/README.md` · `docs/release/play-listing/README.md` | ✅ siguen siendo el detalle de cada superficie; la sección de `CLAUDE.md` enlaza los dos |
