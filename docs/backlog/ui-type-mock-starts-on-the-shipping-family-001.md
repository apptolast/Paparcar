# UI-TYPE-MOCK-STARTS-ON-THE-SHIPPING-FAMILY-001 · El Dev Catalog abría con la tipografía que ya no enviamos

**Estado:** ✅ **Done** (29-08) · rama `bugfix/UI-TYPE-MOCK-STARTS-ON-THE-SHIPPING-FAMILY-001-dev-default`

## Problema

El selector de familia del Dev Catalog arrancaba en `DevFontChoice.Current`, que resolvía a
`legacyFontSet()` — **Outfit + Inter + Barlow**, la tipografía anterior a
`UI-TYPE-FAMILY-CANDIDATES-001`.

Producción ya enviaba Plus Jakarta Sans, así que **cualquiera que abriera el mock veía la familia
vieja** sin saberlo, y cualquier captura tomada de ahí documentaba algo que la app ya no hace. Se
detectó justo antes de empezar el barrido de pantallas pendientes: de no haberlo mirado, las diez
capturas habrían retratado la tipografía equivocada.

## Doctrina violada

- **`CLAUDE.md` § Sistema de pruebas mock** — el flavor `mock` existe para *probar lo que la app
  hace*. Si su estado inicial no es el de producción, deja de ser una herramienta de verificación y
  pasa a ser una fuente de conclusiones falsas.

## Diseño

El enum se reordena para que **el primer valor sea el que se envía**, y ese es el estado inicial:

```
Shipping("Jakarta (app)")   ← default, resuelve a defaultFontSet()
Legacy("Outfit + Inter")
JakartaWithBarlow("Jakarta + Barlow")
Archivo("Archivo")
```

`Shipping` apunta a `defaultFontSet()`, no a una copia: si mañana cambia la familia de producción,
el catálogo la sigue sin tocar nada. El fallo original era exactamente eso — un valor que decía
"actual" y estaba clavado a una familia concreta.

## Criterio de éxito

1. ✅ El catálogo abre en Jakarta; comprobado en device antes del barrido.
2. ✅ `assembleMockDebug` verde.
3. ✅ **Barrido de las 10 pantallas pendientes** en el Redmi con la familia correcta: login,
   onboarding, permisos (rationale y GPS off), registro de vehículo, fiabilidad REDUCED, y los
   peeks de aparcado / zona aproximada / Bluetooth / "¿Has aparcado?". **Sin desbordes, truncados
   ni desalineaciones.**

## Consumidores auditados

`app/src/mock/.../dev/DevFontChoice.kt` · `DevRoot.kt` · `DevCatalogScreen.kt`. Ningún otro sitio
lee el enum.

## Lo que este barrido NO cubre

- **Modo claro** y **Oppo**: todo lo medido es tema oscuro en el Redmi.
- Los 9 idiomas con las etiquetas de dos palabras de las stats — alemán (`Sitzungen gesamt`) y
  neerlandés (`plekken gedeeld`) son los candidatos a desbordar una celda de un tercio de card.
  Requiere cambiar el idioma del device, que no se toca sin permiso del user.
- Sigue abierto `UI-TYPE-RETIRE-THE-OLD-FAMILIES-001`: 2,14 MB de fuentes sin usar en el APK.
