# I18N-A-DEAD-KEY-PASSES-EVERY-PARITY-CHECK-001 · 23 strings que no lee nadie, y ningún guardarraíl los ve

**Estado:** 🟡 Abierto, sin rama · detectado el 30-08-2026 al cerrar
`I18N-PLURAL-CATEGORIES-FOLLOW-THE-LANGUAGE-NOT-ENGLISH-001`

## Problema

Barrido de todas las keys de la base contra todos los `.kt` del repo:

| Superficie | Keys | Sin referencia en `.kt` |
|---|---|---|
| `shared/…/composeResources` | 542 | **22** |
| `app/src/main/res` | 46 | **1** |

```
home_parking_release · home_peek_no_spots · home_peek_spot_compatible · home_peek_spot_incompatible
home_peek_spot_occupied · home_peek_spot_compat_moto/_small/_medium/_large/_van
home_stats_free_spots_badge · home_vehicle_card_status_empty · home_vehicle_chip_badge_active
home_vehicle_chip_badge_bt · home_vehicle_chip_status_driving · home_vehicle_chip_status_parked
home_zone_private_badge · my_car_active_vehicle · my_car_set_active · settings_profile_member_since
vehicle_bt_cd · vehicle_status_inactive_cd
app/src/main/res: attribution_detection_label
```

Se agrupan solas en dos familias — el bloque de compatibilidad del peek (7 keys) y el chip de
vehículo (4) — o sea que huelen a dos rediseños que se llevaron el código y dejaron el texto.

## Doctrina violada

Ninguna, y ese es el punto. `LocaleParityGuardrailTest` comprueba que una key esté en los 9 idiomas
y que sus plurales declaren las categorías del idioma. **Una key muerta pasa las dos**: está muerta
en los nueve por igual, así que la paridad la ve perfecta. El coste no es el byte del APK, es que
**cada idioma nuevo obliga a traducirla** y cada auditoría futura la cuenta como viva.

Precedente inmediato: `history_weekly_title` + `history_weekly_subtitle` sobrevivieron así hasta que
se buscó *uso*, no *paridad*. Se borraron en el ticket anterior.

## Qué hay que medir antes de borrar

⚠️ El barrido es **búsqueda por subcadena** sobre `.kt`. Da falso positivo si una key se compone
dinámicamente o se referencia desde un sitio que no se miró. Antes de borrar cada una:

- `grep` de la key y del sufijo suelto (p. ej. `_compat_` para el grupo del peek)
- mirar si el composable que la usaba sigue vivo con otro texto o murió entero
- ojo con `attribution_detection_label` (`app/src/main/res`): las keys de Android res se pueden
  referenciar desde XML (layouts, manifest, `@string/…`), no solo desde Kotlin — **ampliar el
  barrido a `.xml` antes de tocarla**

## Diseño (propuesto, sin decidir)

Dos piezas, y la segunda es la que importa:

1. Borrar las que se confirmen muertas, en los 9 ficheros a la vez.
2. **Que el guardarraíl mire uso, no solo paridad**: un test que falle cuando una key de la base no
   aparece en ningún `.kt`. Necesita una allowlist para lo que se referencie de forma no literal, y
   esa allowlist hay que justificarla key a key — si no, se convierte en el agujero de siempre.

## Criterio de éxito

- Cero keys sin referencia, o cada excepción en la allowlist con su razón escrita.
- El guardarraíl **se ha visto fallar** al declarar una key nueva sin usarla.
