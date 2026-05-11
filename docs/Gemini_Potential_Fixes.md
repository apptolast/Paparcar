# 🚀 Sugerencias de Mejora y Fixes (Gemini)

Este documento centraliza las recomendaciones de arquitectura, código y buenas prácticas sugeridas durante el desarrollo.

---

## 🛠️ Core & Repositories

### 1. Manejo de `CancellationException` en `runCatching`
**Contexto:** Se usa `runCatching` en los repositorios para capturar errores de Room y Firebase.
**Riesgo:** `runCatching` captura incluso las excepciones de cancelación de corrutinas, lo que puede romper el flujo de cancelación del ciclo de vida de la UI.
**Sugerencia:** Asegurarse de re-lanzar la excepción si es de tipo cancelación.
```kotlin
runCatching {
    // ... lógica ...
}.onFailure { if (it is CancellationException) throw it }
```

### 2. Tratamiento de Errores de Room
**Contexto:** Los errores en Room son raros pero catastróficos (disco lleno, corrupción).
**Sugerencia:** Diferenciar errores de persistencia local vs errores de red en el bloque `onFailure` para mostrar mensajes más precisos al usuario o logs técnicos.

---

## 🏗️ Arquitectura & Modularización

### 1. Estrategia de Modularización Híbrida (Capas + Features)
**Contexto:** Actualmente el proyecto es un monolito en `composeApp`.
**Propuesta:** Dividir el proyecto en módulos para mejorar tiempos de compilación, encapsulamiento y testeabilidad.

#### Estructura propuesta:
- **`:core`**: Módulos transversales sin lógica de negocio.
    - `:core:ui`: Componentes comunes, tema y recursos.
    - `:core:network` / `:core:database`: Configuraciones base.
    - `:core:model`: Entidades compartidas (GpsPoint, etc).
- **`:shared`**: El corazón de la lógica KMP.
    - `:shared:domain`: Casos de uso e interfaces (Pure Kotlin).
    - `:shared:data`: Implementación de repositorios (Room/Firebase).
- **`:feature`**: Módulos por funcionalidad (MVI + UI).
    - `:feature:home`, `:feature:detection`, `:feature:history`, `:feature:settings`.

#### Plan de ejecución incremental:
1. Extraer componentes visuales a `:core:ui`.
2. Aislar la lógica pura en `:shared:domain` (eliminando dependencias de frameworks).
3. Migrar funcionalidades aisladas a módulos `:feature`.

