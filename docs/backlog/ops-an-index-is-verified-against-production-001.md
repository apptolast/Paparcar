# OPS-AN-INDEX-IS-VERIFIED-AGAINST-PRODUCTION-001 · Un índice solo existe si producción lo dice

**Estado:** ✅ Done (2026-09-16) — el hash vive en `MEMORY.md`.
**Origen:** 4.ª vuelta, punto 3. Nace del índice que faltaba y habría silenciado todas las alertas.

## Problema

`OPS-A-COUNT-WITHOUT-ITS-INDEX-SILENCES-EVERY-ZONE-001` se desplegó sin índice compuesto y ningún
test lo vio: los de `functions` prueban `verdictFor`, que es puro, y **el emulador de Firestore no
exige índices**. O sea que las dos redes que tenemos dan verde a una consulta que en producción
revienta con `FAILED_PRECONDITION` y tumba el trigger entero.

## Tres capas, de la más barata a la única que dice la verdad

1. **`functions/src/queries.ts`** — cada consulta que necesita índice compuesto declara el suyo.
2. **`indexes.test.ts`** — comprueba que ese índice está en `firestore.indexes.json`. Tonto y
   gratis: caza el olvido de declararlo, que es el caso normal.
3. **`scripts/verify-prod-queries.ts`** — ejecuta las consultas **contra la base real** y sale con
   código 1 si alguna no puede correr. Es la única que sabe si el índice **existe y está READY**,
   porque un índice recién desplegado tarda en construirse y hasta entonces falla igual.

Y el log del fallo pasa a ser un `Error` de verdad, para que Error Reporting lo agrupe en vez de
dejarlo en una línea que nadie lee.

## Cómo se usa

En `docs/release/RELEASE-PROCESS.md`, tras desplegar índices y funciones: esperar a `READY` y
ejecutar `npm --prefix functions run verify:queries`. **Antes del APK.**
