# MOCK-SETTINGS-TAB-CRASHES-WITHOUT-DEVICE-INFO-001 · Ajustes mataba la app en el build mock

**Estado:** ✅ **Done** (29-08)

## Problema

Abrir el tab de **Ajustes** en el flavor `mock` cerraba la app. Koin lanzaba
`NoDefinitionFoundException` para `DeviceInfoProvider`, que resuelve la ruta del reporte de soporte
[SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001].

Sólo lo ata `androidPlatformModule`, y `MockPaparcarApp` carga `presentationModule + domainModule +
mockModule` — ese no. Producción está sana: allí sí se carga.

## Cómo apareció

Intentando capturar Ajustes para la comparativa tipográfica: la app caía al launcher en las tres
familias. Parecía un guion de UI frágil y no lo era — sólo se vio al mirar `logcat -b crash`.

**Lo que significa de verdad:** el Dev Catalog **no podía ejercitar Ajustes en absoluto**, que es
justo para lo que existe el flavor mock. Un tab entero fuera del set probable sin que nada avisara.

## Doctrina violada

- **`CLAUDE.md` § Sistema de pruebas mock** — *"No añadir pantalla/estado/flujo nuevo sin actualizar
  el sistema de pruebas mock"*. Al añadir el reporte de soporte a Ajustes se introdujo una
  dependencia de plataforma sin su contraparte en `mockModule`.
- Mismo patrón que [MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001]: una dependencia que sólo existe
  en un módulo que el mock no carga.

## Diseño

El fallback de dominio ya existía (`UnknownDeviceInfoProvider`, pensado para "cuando no hay binding
de plataforma"), así que el arreglo es una línea en `mockModule`. No hace falta un fake nuevo: los
datos de device no significan nada en mock.

## Criterio de éxito

1. ✅ Ajustes abre en `mock` sin crash, verificado en el Redmi en las tres familias tipográficas.
2. ✅ `assembleMockDebug` verde; producción intacta.

## Consumidores auditados

`app/src/mock/.../di/MockModule.kt` — único sitio a tocar.

⚠️ **Follow-up sugerido, no hecho:** nada impide que vuelva a pasar. Cada dependencia que
`androidPlatformModule` ata y `mockModule` no, es un crash latente en la pantalla que la use. Un
test que compare las claves de ambos módulos lo convertiría en un fallo de build en vez de en una
pantalla que se cierra.
