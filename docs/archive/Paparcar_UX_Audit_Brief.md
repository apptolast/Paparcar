# Paparcar — UX Audit Brief

> Este documento es un brief de auditoría puntual para una sesión de Claude Code.
> NO es documentación permanente del proyecto.
> Para reglas del proyecto → ver CLAUDE.md
> Para arquitectura técnica → ver Paparcar_Arquitectura.md

---

## CÓMO USAR ESTE ARCHIVO

Invócalo desde Claude Code con:

```
@Paparcar_UX_Audit_Brief.md @CLAUDE.md @Paparcar_Arquitectura.md

[Instrucción de la sesión concreta — ver secciones al final]
```

---

## ROL

Actúas como experto senior en UI/UX Design para apps móviles nativas Android,
con dominio de Kotlin Multiplatform, Compose Multiplatform, Material Design 3
y apps de utilidad que combinan servicios en background con interacción frecuente
mientras el usuario conduce. Conoces en profundidad Google Maps, Waze, Parkopedia
y SpotHero como referentes de interacción.

Antes de cualquier análisis o acción: **lee el código fuente completo** del proyecto.
No asumas nada que no esté en el código. Si algo está documentado en la
arquitectura pero no implementado, indícalo explícitamente.

Tómate el tiempo que necesites. Precisión y solidez sobre brevedad.

---

## CONTEXTO CRÍTICO: ESTADO REAL DEL PROYECTO

### Lo que está confirmado como implementado
(Verifica en el código antes de asumir nada)
- Arquitectura base: Clean Architecture + MVI + Koin + Room + Firebase
- BaseViewModel, State/Intent/Effect en commonMain
- Dual detection strategy: BluetoothDetectionStrategy + CoordinatorDetectionStrategy
- HomeScreen con su propio mapa integrado
- HistoryScreen
- MapScreen → **VER NOTA CRÍTICA ABAJO**

### Lo que NO está implementado aún (no analices como si existiera)
- VehicleRegistration (pantalla pendiente)
- Onboarding formal (pendiente)
- Pantalla de Permissions (pendiente como pantalla propia)
- Settings / Ajustes (pendiente)
- Perfil de usuario (pendiente)
- Integración completa de Firebase real-time spots

### NOTA CRÍTICA — MapScreen.kt (naming confuso, requiere refactor)
**MapScreen.kt NO es el mapa principal de la app.**
- **HomeScreen** tiene su propio mapa integrado donde se ven los spots en tiempo real.
- **MapScreen.kt** es una pantalla de detalle que muestra la ubicación
  geográfica de UN spot específico, invocada desde HistoryScreen.
- El nombre `MapScreen` es engañoso y viola el principio de naming descriptivo.
- Esto debe ser uno de los primeros puntos del análisis de refactoring.

---

## MODELOS DE DATOS CLAVE

```kotlin
Spot: location, type (AUTO_DETECTED/MANUAL_REPORT), status, confidence,
      enRouteCount, TTL
UserParking: vehicleId, location, geofenceId, isActive, detectionMethod
Vehicle: brand, model, licensePlate?, bluetoothDeviceId?, isDefault
UserProfile: userId, email, displayName, photoUrl
```

---

## SISTEMA DE DETECCIÓN — DUAL STRATEGY

```kotlin
// Estrategia 1 — Determinista (usuario con coche BT emparejado)
BluetoothDetectionStrategy:
  BT disconnect → GPS fix → distance > 30m → auto-confirm
  BT connect → DetectDepartureUseCase
  Sin scoring, sin Activity Recognition. Directo.

// Estrategia 2 — Probabilístico (fallback sin BT)
CoordinatorDetectionStrategy:
  Activity Recognition + GPS stream → confidence score
  HIGH (>=0.75) → auto-confirm
  MEDIUM (>=0.55) → pregunta al usuario
  LOW → reset

// Resolución en runtime
if (vehicle.bluetoothDeviceId != null && isBluetoothEnabled)
    → BluetoothDetectionStrategy
else
    → CoordinatorDetectionStrategy
```

---

## NAVEGACIÓN ACTUAL

```
Splash → Auth (BaseLogin, librería externa) → [VehicleRegistration — PENDIENTE]
→ [Onboarding — PENDIENTE] → [Permissions — PENDIENTE] → Home

BottomNav (4 tabs declarados): Mapa | Historial | Mi Coche | Ajustes
```

---

## PRINCIPIOS DE CÓDIGO QUE APLICA ESTE PROYECTO
(Cualquier sugerencia de refactor debe respetar estas reglas — ver CLAUDE.md completo)

- Strings: NUNCA hardcoded → strings.xml, `stringResource(Res.string.key)`
- Magic numbers: NUNCA inline → `companion object` con UPPER_SNAKE_CASE
- Nombres de pantallas: sufijo `Screen` obligatorio
- Interfaces antes que implementaciones concretas
- Conventional Commits para cualquier cambio sugerido
- Sin `println`, usar Logger con tag
- Sin wildcard imports
- Domain layer: Kotlin puro, sin imports Android/iOS
- `Clock.System.now()` en lugar de `System.currentTimeMillis()`

---

## SESIÓN 1 — INVENTARIO REAL + ACTUALIZACIÓN DE ROADMAP

### Objetivo
Reconciliar el estado real del código con el roadmap documentado.
El archivo `Paparcar_Roadmap_Completo.md` puede estar desactualizado respecto
a lo implementado en Android Studio. Necesitamos un inventario preciso.

### Instrucciones

1. **Lee TODO el código fuente** de:
   - `composeApp/src/commonMain/`
   - `composeApp/src/androidMain/`
   - `CLAUDE.md`
   - `Paparcar_Arquitectura.md`
   - `Paparcar_Roadmap_Completo.md`

2. **Genera un inventario de estado real** con tres categorías:

   **✅ IMPLEMENTADO Y CORRECTO**
   Lista cada clase/feature con su ruta de archivo.

   **⚠️ IMPLEMENTADO PERO INCOMPLETO O CON DEUDA TÉCNICA**
   Lista qué falta o qué está mal, con referencia a línea/archivo si es posible.

   **❌ DOCUMENTADO PERO NO IMPLEMENTADO**
   Lista lo que está en la arquitectura/roadmap pero no existe en el código.

3. **Identifica deuda técnica existente** siguiendo los principios de CLAUDE.md:
   - Strings hardcoded
   - Magic numbers inline
   - Nombres de clases o archivos confusos o que no siguen las convenciones
   - Interfaces que debería haber y no están
   - Cualquier otro principio de CLAUDE.md que se esté incumpliendo

4. **Propón un roadmap actualizado** basado en el estado real.
   Estructura en fases priorizadas por impacto, siguiendo el formato de
   Conventional Commits para los nombres de las tareas.
   Indica qué tareas del roadmap original están ya completas y cuáles
   hay que replantear.

5. **No modifiques ningún archivo todavía.** Solo análisis en esta sesión.
   El output es un informe de estado con el roadmap actualizado propuesto.

---

## SESIÓN 2 — REFACTORING DE NOMBRES Y DEUDA TÉCNICA

### Objetivo
Aplicar principios de clean code al naming y estructura existente.
Esta sesión ejecuta los cambios, no solo los propone.

### Instrucciones

1. **Renombrar MapScreen.kt** (prioridad máxima):
   - Analiza qué hace exactamente la pantalla viendo su código.
   - Propón el nombre más descriptivo y preciso según su responsabilidad real.
   - Candidatos posibles (confirma cuál encaja mejor con el código real):
     `SpotDetailMapScreen`, `SpotLocationScreen`, `SpotHistoryDetailScreen`
   - Aplica el rename: archivo, clase, referencias en navegación, strings.xml,
     DI si aplica.

2. **Audita todos los nombres de clases, archivos y paquetes** buscando:
   - Nombres genéricos que no describen la responsabilidad
     (ej: `Manager` sin contexto, `Helper`, `Utils` sin especificidad)
   - Inconsistencias entre el nombre y lo que hace la clase
   - Sufijos faltantes o incorrectos (`Screen`, `ViewModel`, `UseCase`,
     `Repository`, `DataSource`)
   - Nombres en español mezclados con inglés
   - Cualquier nombre que requiera leer el cuerpo de la clase para entender
     qué hace (viola el principio de "nombre autodescriptivo")

3. **Para cada renombre propuesto:**
   - Antes / Después
   - Justificación en una línea
   - Impacto: qué otros archivos cambian
   - Rama sugerida: `refactor/FND-XXX-rename-classname`

4. **Aplica los cambios aprobados** o genera el diff si prefieres revisarlos antes.

5. **Actualiza strings.xml** si hay strings hardcoded encontrados en la Sesión 1.

6. **Extrae magic numbers** encontrados en la Sesión 1 a sus `companion object`.

---

## SESIÓN 3 — ANÁLISIS UX: NAVEGACIÓN Y FLUJOS

### Objetivo
Tomar decisiones de diseño de producto sólidas y justificadas.
Al final de esta sesión debe haber recomendaciones definitivas, no opciones abiertas.

### Instrucciones

#### 3A — Flujo de usuario completo

Traza el user journey completo desde descarga hasta usuario recurrente:

**Primer uso (FTUE):**
- ¿El orden Splash → Auth → VehicleRegistration → Onboarding → Permissions → Home
  es correcto? ¿Genera fricción innecesaria?
- ¿El registro de vehículo debe ser obligatorio en el onboarding o puede diferirse?
  ¿Qué pasa si el usuario quiere buscar plazas sin registrar coche?
- ¿Cuándo pedir cada permiso? (ubicación, actividad, Bluetooth, notificaciones)
  Razona con el principio de "pedir cuando se necesita, no antes".
- ¿Qué permisos son bloqueantes y cuáles son opcionales?

**Usuario recurrente:**
- ¿Cuál debe ser la primera pantalla al abrir la app?
- ¿Cómo gestionar el estado del background service al relanzar la app?

**Flujo de detección (core de la app — dos caminos):**
- BT path: el usuario ni toca el teléfono. ¿Cómo se le notifica?
- AR path: confidence MEDIUM → el sistema pregunta. ¿Cómo? ¿Bottom sheet,
  notification action, dialog? ¿Cuánto tiempo tiene para responder?

**Flujo de búsqueda de plaza:**
- ¿Qué hace el usuario activamente buscando aparcamiento?
- ¿Cómo interactúa con spots en el mapa de HomeScreen?
- ¿Qué información necesita ver de un Spot? (distancia, TTL, enRouteCount,
  confidence, type, dirección aproximada)

#### 3B — Decisión de navegación (emite recomendación definitiva)

Evalúa estas opciones y elige UNA con justificación completa:

- **A) BottomNav 4 tabs actual:** Mapa | Historial | Mi Coche | Ajustes
- **B) BottomNav 3 tabs:** reorganización (ej: Home+Mapa fusionados | Historial | Perfil)
- **C) Mapa central con overlays:** sin BottomNav visible en mapa, resto accesible
  via FAB menu o bottom sheet. Estilo Google Maps/Waze.
- **D) Híbrido:** BottomNav en pantallas secundarias, mapa fullscreen en Home.

Para cada opción considera:
- ¿El mapa debe estar siempre visible o puede ser una tab?
- ¿Cuál es la jerarquía real de uso de cada sección?
- ¿Cómo afecta al foreground service si el usuario navega entre tabs?
- Nota: HomeScreen ya tiene su propio mapa integrado. MapScreen (que renombraremos)
  es un detalle desde Historial. Esto afecta la decisión.

**Recomendación definitiva requerida.**

#### 3C — Decisión sobre Bluetooth + Activity Recognition en UX

¿Cómo debe el usuario percibir el sistema de detección dual?

- **A) Transparente (actual):** el sistema decide internamente, el usuario solo
  ve "Detección activa".
- **B) Informativo:** "Detectando via Bluetooth — Alta precisión" /
  "Detectando via sensores — Empareja tu coche para mejorar"
- **C) Configurable:** toggle en ajustes "Usar Bluetooth si disponible (recomendado)"
- **D) Score unificado:** combinar señales BT + AR + GPS en un único confidence
  score en lugar de estrategias mutuamente excluyentes.

Considera: ¿la pantalla Mi Coche (VehicleRegistration cuando exista) debe mostrar
el modo de detección activo? ¿Cómo motivar al usuario a emparejar el coche BT?

**Recomendación definitiva requerida.**

---

## SESIÓN 4 — COMPONENTES UI Y SISTEMA VISUAL

### Objetivo
Definir qué componentes custom hay que construir, cuáles son MD3 estándar,
y dar dirección visual coherente con el producto.

### Instrucciones

#### 4A — Análisis de componentes existentes

Para cada composable significativo que encuentres en el código:
- ¿Está bien nombrado y estructurado?
- ¿Tiene sus propios State/Intent/Effect si lo necesita?
- ¿Viola algún principio de CLAUDE.md?
- ¿Es reutilizable o está acoplado a una pantalla específica?

#### 4B — Componentes clave a definir o revisar

Para cada uno: composición exacta, estados (default, loading, error, empty),
variantes, y si es MD3 estándar o custom. Incluye qué datos del modelo de dominio
necesita cada uno:

- **SpotCard** — item en lista y bottom sheet del mapa
  Datos: distancia, TTL restante, enRouteCount, type (AUTO/MANUAL),
  dirección aproximada, confidence
- **DetectionStatusBanner** — banner cuando isDetectionActive = true
  ¿Qué muestra? ¿Estrategia activa (BT/AR)? ¿Tiempo desde activación?
- **SpotMarker** — marcador en el mapa de HomeScreen
  ¿Diferenciación visual por TTL, confidence, enRouteCount?
- **ConfirmationBottomSheet** — cuando confidence es MEDIUM, pide confirmación
  ¿Timeout visual? ¿Acciones? ¿Cómo llega al usuario si tiene la app cerrada?
- **VehicleCard** — en Mi Coche (pendiente de implementar)
  ¿Estado de detección activo? ¿BT emparejado visible?
- **TTLIndicator** — tiempo de vida restante de un spot
  ¿Barra de progreso, color degradado, texto countdown, opacidad?
- **EnRouteIndicator** — usuarios en camino hacia esa plaza
  ¿Icono con número? ¿Solo cuando > 0?

#### 4C — Dirección visual

- ¿Dark mode, light mode o ambos? ¿Cuál por defecto para una app de uso nocturno?
- Material Dynamic Color vs paleta fija de marca: ¿cuál encaja mejor?
- Propón dirección de color primario con justificación para una app de movilidad
  urbana y conducción.
- ¿Fuente custom o system font? Razona legibilidad con golpe de vista (glanceability)
  para uso mientras se conduce.

---

## OUTPUT ESPERADO POR SESIÓN

- **Sesión 1:** Informe de estado (✅/⚠️/❌) + Roadmap actualizado propuesto en markdown.
- **Sesión 2:** Lista de renombres con before/after + diffs o cambios aplicados.
- **Sesión 3:** Recomendaciones definitivas de navegación + flujos documentados.
- **Sesión 4:** Especificación de componentes + dirección visual.
