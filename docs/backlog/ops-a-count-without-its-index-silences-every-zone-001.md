# OPS-A-COUNT-WITHOUT-ITS-INDEX-SILENCES-EVERY-ZONE-001 · El tope anti-megáfono se comió el aviso entero

**Estado:** ✅ Done (2026-09-16) — índice y función **desplegados**; el hash vive en `MEMORY.md`.
**Origen:** revisión independiente, 3.ª vuelta — hallazgo A1. **Y es un fallo introducido el mismo
día por `0fdf4514`.**

## Problema

`SEC-A-SPOT-IS-NOT-A-MEGAPHONE-001` añadió un conteo de plazas vivas por autor antes de anunciar:

```ts
.where('reportedBy', '==', spot.reportedBy).where('expiresAt', '>', now).count()
```

**Igualdad + rango sobre campos distintos exige un índice compuesto en Firestore**, y no existía:
`firestore.indexes.json` tenía `"indexes": []` y la API de índices devolvía `{}` para `spots`. Sin
él la consulta falla, la excepción tumba el trigger y **el reparto no llega a ocurrir**.

O sea: una capa añadida para que un abusón no pudiera gritar **habría silenciado a todo el mundo**,
y en la superficie que menos se nota — nadie echa de menos un aviso que no llega.

⏱ No llegó a morder: el último reparto en Logging es de las **18:00** y las funciones subieron a las
**22:07**, así que ninguna plaza pasó por el código nuevo antes del arreglo.

## Arreglo, en dos piezas que no son intercambiables

1. **El índice** (`firestore.indexes.json` + `firebase deploy --only firestore:indexes`). ⚠️ Un
   índice recién creado tarda en construirse: hasta que la API lo da por `READY`, la consulta sigue
   fallando. Desplegarlo **no** es arreglarlo.
2. **Que su fallo no cueste el aviso.** El conteo pasa a `.catch` → `null` → **se anuncia igual**.
   Decisión del user: la plaza ya pasó la regla —caducidad válida, estado válido, firmada—, así que
   el anti-flood es una segunda capa; que un fallo de infraestructura silencie el producto entero es
   peor que dejar pasar una ráfaga rara.

## La lección, que es la que vale para la próxima

**Una consulta nueva en una Cloud Function es infraestructura, no código.** Los tests de `functions`
pasaron los 25 en verde porque prueban `verdictFor`, que es puro; lo que faltaba no estaba en el
código sino en el proyecto. Cualquier `where` nuevo con igualdad + rango, o con `orderBy` sobre otro
campo, **necesita su índice declarado en el mismo commit**.

## Criterio de éxito

`firestore_list_indexes` sobre `spots` devuelve el índice en `READY`, y una plaza publicada vuelve a
anunciarse. Comprobado releyendo la API, no el fichero local.
