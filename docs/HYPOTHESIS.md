# Paparcar — Hipótesis: ¿cómo lo haría desde cero?

> Reflexión crítica y honesta tras la auditoría del **2026-05-24**. No es un plan de migración — es un ejercicio de "qué he aprendido" para informar decisiones futuras y para evitar repetir errores si algún día se reescribe.

---

## Decisiones que **mantendría**

1. **KMP + Compose Multiplatform** — la apuesta cross-platform aguanta. El 70% del código vive en `commonMain` y eso es real.
2. **Clean Architecture estricta** — `domain` puro Kotlin sin imports de Android es una decisión que sigue dando dividendos cada vez que se añade un test.
3. **Offline-first con Room como SoT** — la app debe funcionar sin cobertura (parking subterráneo). Sin esto, no hay producto.
4. **Estrategia dual de detección** — la separación BT determinista / Coordinator probabilístico es correcta. Cada una resuelve un caso de uso real con la herramienta adecuada.
5. **Koin** — pragmático para KMP. Hilt no soporta KMP, kodein-di es más pesado.
6. **Napier** — single logger funcional en Android e iOS con interop a Crashlytics. No tiene rival real.

---

## Decisiones que **cambiaría**

### 1. Foreground service como pilar único de detección
Android está endureciendo cada año las restricciones de servicios en background. En Android 15+ el coste de mantener `FOREGROUND_SERVICE_LOCATION` empieza a notarse en UX y batería. MIUI/HyperOS ya nos pega golpes hoy.

**Alternativa:** arquitectura híbrida — geofence pasivo + AR transitions como triggers, y el service solo en ventanas activas (cuando estamos en estado CANDIDATE). El resto del tiempo, sin proceso vivo.

### 2. Persistencia del estado del coordinator
Hoy todo el estado vive en memoria del singleton. Si el service muere y `START_STICKY` lo reanuda, perdemos `bestStopLocation` y `lastVehicleEnteredAt`. Es un fallo silencioso que solo se ve en producción.

**Alternativa:** snapshot del coordinator en Room cada vez que cambia el estado. Rehidratar al arrancar.

### 3. Una sola implementación de `AppPreferences`
Hoy hay dos (SharedPreferences legacy + DataStore) sin claridad sobre cuál es canónica. La migración no se completó. Coste de refactor: 2 h. Coste de no hacerlo: bugs intermitentes durante años.

### 4. No exponer la Maps API key vía manifest
Aunque se inyecta por placeholder, acaba en el APK. Hubiese encapsulado el render del mapa en un endpoint propio (proxied) o aceptado desde el día uno que las restricciones GCP por SHA-1 son la única defensa real.

### 5. Migraciones Room desde el día uno
Empezamos sin `Migration` definidos y ya vamos por v3. Toda subida de versión es una potencial bomba. Hoy habría incorporado `MigrationTestHelper` desde la v1→v2.

### 6. Decidir antes el patrón de retorno: `Result<T>` vs `Flow<T>`
**Resuelto [ARCH-CLEANUP-001].** El estándar es `kotlin.Result<T>` (one-shot) + `Flow<T>` (observables) + value objects en evaluadores puros. El `AppResult` sealed que figuraba en los docs nunca llegó a existir en el código (cero referencias); se eliminó de la documentación en lugar de migrar ~30 UseCases a un wrapper propio. `PaparcarError` (sealed) cubre los errores de negocio de cara a la UI.

### 7. iOS no como "target futuro" sino como ciudadano de primera
Hoy iOS tiene 8 implementaciones reales pero el wire AR → coordinator está incompleto. Esto pasó porque iOS fue siempre "lo haré después". Resultado: el después llegó y hay deuda.

**Alternativa:** desde el día uno, todo `expect/actual` debe tener test en ambas plataformas o no merge. Brutal pero sostenible.

---

## Features que **quitaría o simplificaría**

### 1. Glassmorphism (GlassSurface)
Esfuerzo de diseño alto, retorno bajo. Hoy es solo opacidad + borde. Implementar blur real es complicado (RenderEffect API 31+ + UIVisualEffectView en iOS). Para v1 hubiera optado por superficies opacas claras con buen contraste. Ya estamos haciendo eso en la "DS unification Phase A".

### 2. Onboarding largo
Onboarding + permisos + rationale + GPS disclaimer + vehicle registration → cinco pantallas antes de ver el mapa. Es excesivo. Simplificaría a: **una sola pantalla pre-permission con visual + bullets + un CTA "Continuar"**, y los permisos se piden en contexto cuando se necesiten.

### 3. Múltiples colores accent por vehículo
`VehicleAccentPalette` con 8 colores tematizando cada vehículo. Cool concept, pero cognitivamente costoso. Bastaría con badges de iniciales del modelo.

### 4. Histórico como tab separada
Es una vista relativamente poco usada que ocupa el mismo espacio que Vehicles o Settings. Habría preferido **integrarla como modal accesible desde el Home** (pulgada sobre el handle del bottom sheet expandido a full).

---

## Features que **añadiría que no existen**

### 1. Widget Android de parking activo
Caso de uso: "¿dónde aparqué hace 3 días?". Un widget en la home con la dirección + minutos transcurridos + tap → abrir mapa centrado. **Coste estimado: 1 día. Valor: enorme.**

### 2. Android Auto integration
Mostrar "Reportar plaza libre" en la pantalla del coche al desconectar BT. Cero fricción para el usuario que más nos interesa (el que aparca cada día en el mismo barrio).

### 3. Apple Watch / WearOS complication
TTL countdown del último spot publicado, glance del parking activo.

### 4. Confidence dashboard interno (debug)
Pantalla de debug accesible vía 5-taps en Settings: gráfica en vivo de speed/accuracy/AR + score actual + último reset reason. **Vital para diagnosticar false negatives reportados por usuarios** sin tener que pedirles logs.

### 5. Modo "Estoy llegando" + ETA
Si el usuario seleccionó un spot del mapa, anunciar al resto que está en route — refuerza el `enRouteCount` ya en el modelo. Aumenta competencia y engagement.

### 6. Detección sin BT vía CarPlay/Android Auto pairing
Cuando CarPlay/AA se desconectan, es una señal igual de fuerte que BT. iOS expone `MPRemoteCommandCenter`/`MPNowPlayingInfoCenter`. Android tiene `CarConnection`. Open the door para usuarios sin BT pero con multimedia conectado.

---

## Stack tecnológico hoy

Si el proyecto empezara hoy (mayo 2026):

| Capa | Mantener | Cambiar |
|------|----------|---------|
| UI | Compose Multiplatform | — |
| DI | Koin | — |
| DB | Room KMP | — |
| Firebase | GitLive SDK | **Considerar** Firebase Android oficial vía expect/actual propio si GitLive se queda atrás |
| Maps | kmp-maps (Software Mansion) | Probar Mapbox KMP si saliera, para mayor control sobre estilo dark mode |
| Auth | BaseLogin (propia) | Mantener pero exigir mismas garantías que cualquier dep externa (releases, semver) |
| Async | Coroutines + Flow | — |
| Logging | Napier | — |
| CI | GitHub Actions | — |
| Distribution | Firebase App Distribution | TestFlight en paralelo cuando exista iOS beta |

Stack se mantiene en líneas generales. Los principales cambios son **discipina de proceso**, no de tecnología.

---

## Los 3 riesgos técnicos más grandes hoy

### 🔴 Riesgo #1 — Detección background en OEMs agresivos
**Probabilidad:** alta. **Impacto:** core de la app deja de funcionar para % importante del mercado español (Xiaomi tiene ~30% share local).

Mitigación: onboarding específico por OEM + telemetría de muertes de service + heartbeat worker + considerar arquitectura híbrida (geofence + AR + service ondemand).

### 🔴 Riesgo #2 — Restricciones de Google Play sobre `BACKGROUND_LOCATION` y `FOREGROUND_SERVICE_LOCATION`
**Probabilidad:** media (Google sigue endureciendo). **Impacto:** si nos rechazan la app por uso de location en background sin justificación suficientemente clara, retraso indefinido.

Mitigación: documentar el caso de uso meticulosamente, vídeo demo del valor para el usuario, declarar en Play Console con justificación detallada, opt-in explícito y revocable in-app.

### 🔴 Riesgo #3 — iOS al 70% como "feature B"
**Probabilidad:** alta de quedarse atrás. **Impacto:** si lanzamos Android y el feedback es positivo, vamos a perder usuarios iOS pidiéndola, y el "después" del iOS se aleja indefinidamente.

Mitigación: cerrar §3 y §14 de `BUGS_AND_DEBT.md` **antes** de Android beta público, no después. Forzar paridad como condición de release.

---

## Resumen

El proyecto está **mejor que la media** para una app KMP de complejidad similar. Las decisiones arquitectónicas grandes son correctas. La deuda real está en:

1. Disciplina de detalles (`runBlocking`, doble `AppPreferences`, falta migraciones)
2. iOS como ciudadano de segunda
3. Defensas frente a OEMs agresivos
4. Persistencia del estado del coordinator

Ninguno de los 4 es catastrófico. Todos son atacables con 1-3 semanas de trabajo focused. La sensación es de proyecto **maduro pero no terminado** — que es exactamente donde tendría que estar para llegar a beta en las próximas semanas.
