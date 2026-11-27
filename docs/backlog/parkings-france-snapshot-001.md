# PARKINGS-FRANCE-SNAPSHOT-001 · Ampliar el snapshot semanal a Francia conservando España

**Estado:** ✅ Cerrado el 27-11-2026 · ampliación publicada y verificada en producción; integración en master y publicación semanal autorizadas.

## Problema
Revisión del 27-11-2026: API pública de París (1812_57) devuelve HTTP 200, serve=true y cero parkings. Madrid (1499_-105) devuelve 937. El snapshot publicado 2026-W48 contiene 70.177 parkings y 12.587 celdas. El cliente acepta vacíos sin fallback, aunque el territorio no esté importado.

## Doctrina violada
Ausencia de cobertura no equivale a ausencia de parkings. Una ampliación debe conservar España y Canarias; Hosting espeja el directorio completo. Las URLs de celdas declaradas inmutables no pueden reutilizarse para contenido distinto.

## Señales / datos disponibles
Workflow semanal activo, ejecución del 23-11 publicada. Extractor Python de geometría, filtro Kotlin compartido y deduplicación por ID existentes. Francia metropolitana disponible en Geofabrik (~4,8 GB); verificar Córcega y documentar exclusión de ultramar. Combustible francés ya operativo y fuera del cambio funcional de este ticket. Baseline previo: 30 pruebas dirigidas (FranceStationParserTest, FranceFuelStationsSourceTest, ApiPublicParkingsSourceTest), cero fallos.

## Decisiones implementadas
- Unión fija ES + Canarias + Francia en el workflow. No admite publicar un extracto parcial.
- Versión única por run/attempt; generador rechaza sobrescrituras y cualquier input vacío.
- Lector de backend verifica índice/recuentos/versiones. Si falta un archivo esperado, refresca
  puntero y reintenta una vez solo si cambió de versión; no mezcla generaciones.
- Bloque entero sin datos: serve=false, fallback conservador. No se inventa cobertura territorial
  por cajas; un bloque vacío dentro de un país también usa respaldo.
- Validador común a CI y predeploy: índice/celdas/bulk coherentes, IDs únicos, 14 zonas testigo.
  CI contrasta con el bulk publicado y aborta si desaparece más del 2 % de sus IDs.
- Snapshot francés metropolitano, ultramar excluido; no añade ocupación en vivo francesa.
- Índices de coordenadas del extractor en disco para limitar presión de RAM. Canarias dio el
  mismo SHA-256 en modo RAM y disco: 546bfe4090b274fb7ce153579f977d91c77a00407b6c5685dccac3d6176bd69b.
- La publicación semanal ampliada se mantuvo pendiente tras el rechazo inicial de auto-review.
  El usuario autorizó después explícitamente commit, integración en master, push y activación
  semanal conjunta. El cron publica la unión completa; la ejecución manual sigue siendo opt-in.
- Usuario autorizó explícitamente copiar local.properties y app/google-services.json al worktree;
  copia local realizada, sin abrir contenido ni añadirlos a Git.

## Validación durante implementación
- Pruebas dirigidas de SnapshotToolMain, rejilla y cliente API: BUILD SUCCESSFUL.
- Functions: npm ci local (sin scripts), npm run build y node --test lib/*.test.js:
  120 pruebas aprobadas, incluida transición con caché caliente, sin mezcla de versiones y 80
  peticiones concurrentes en frío compartiendo una sola descarga del índice. Node local 24; runtime declarado 22. Sin tests emulados de promos,
  no modificadas por este cambio.
- Validador de publicación: 7 pruebas aprobadas (incluyen pérdida de base anterior y corrupción).
- Extractor: 7 pruebas aprobadas (incluyen fixture XML real para orden caché/filtros); Canarias real: 7.269 elementos, 11,6 s (RAM) / 11,7 s (disco).
- testAll: primer intento 4.258 pruebas shared, fallo único por cifras del backlog. Se actualizaron
  a las medidas por el guard: 881/784/88/9. Segundo intento BUILD SUCCESSFUL; shared ejecutado,
  app mock e iOS klib ya validados por el intento anterior, UP-TO-DATE en el segundo.
  No equivale a enlace ni ejecución iOS en Mac.
- Cierre: `./gradlew testAll --console=plain` exit 0 (tests/klib UP-TO-DATE); para validar los
  documentos modificados se ejecutó `:shared:testDebugUnitTest --tests '*BacklogStatusGuardrailTest'
  --rerun-tasks --console=plain`: BUILD SUCCESSFUL, tarea de test ejecutada, cifras 881/785/87/9.
  `python tools/backlog/branch_tell.py`: exit 0, ningún doc abierto cita una rama inexistente.
- node web/build.mjs --check: correcto, sin diferencias de páginas generadas.
- Lector nuevo probado en lectura contra una celda real de Madrid en Hosting: W48, 51 registros.
- Generación completa realizada con el filtro Kotlin: versión `2026-W48-france-20261127-01`.
  ES 65.509 + Canarias 4.667 + FR 321.064, menos 153 IDs solapados = **391.087 parkings**,
  **48.008 celdas**. Celdas ~40 MB, bulk ODbL ~6,6 MB. Generación 20,9 s.
- Francia: 550.214 elementos OSM extraídos en 308,7 s. Limitación previa del extractor:
  2 relaciones sin geometría recuperable y 2 con miembros relación no resueltos (contados).
  No se afirma exhaustividad de OSM ni cobertura de ultramar.
- Optimización de segunda pasada: Canarias 1,7 s, España 123,7 s contra 335,4 s anteriores.
  España idéntica byte a byte: ca83497675f4786eb48a95f965690bf932fc34adb2dc3be2e0301a8f4bb54098.
- Validador íntegro + guard predeploy ejecutados, ambos exit 0 antes del despliegue. Baseline vivo
  W48: conserva 70.153 / 70.177 IDs (99,9658 %). Se investigaron los 24 ausentes:
  10 no están en los extractos actuales; 9 ahora son roadside/lane, 4 access=customers sin pago,
  1 access=private. Los 14 últimos los excluye el filtro de producto PREEXISTENTE.
- Zonas testigo (radio 3 km, registros del snapshot): Madrid 195, Barcelona 269, Cuenca 88,
  Las Palmas 169, Palma 154, Ceuta 73, Melilla 82; París 114, Lyon 180, Toulouse 190,
  Ajaccio 67, Bourges 245, Sancerre 23, Perpiñán 152.
- Contraste París con la consulta generada por Kotlin y Overpass vivo (27-11):
  114 / 114 IDs coincidentes; cero exclusivos y cero desplazamientos >10 m.
- Prueba integrada local con ParkingSnapshotReader + blockAround + responseFor y archivos reales:
  bloque París 596, Madrid 938, ambos serve=true; bloque sin datos 0_0 serve=false.
  Sin consultas remotas a Firestore ni instalación en dispositivo durante esta prueba local.
- Artefacto generado (gitignored): `hosting/public/parkings/v1/2026-W48-france-20261127-01/`.
  Datos descargados y comparaciones en `.artifacts/parkings/`, también ignorados.
- Un intento de comando Gradle sufrió comillas de PowerShell; no llegó a generar datos.
  Comandos con --args se ejecutaron correctamente con CMD y Git Bash (C:\\Progra~1\\Git\\bin\\bash.exe).

## Publicación y comprobación real (27-11-2026)
- Usuario autorizó publicar `mergeCell` y Hosting, incluyendo el copy Meta/TikTok ya presente en
  master. Este ticket no editó ese copy. También autorizó commit, integración, push y cron conjunto.
- `npx --yes firebase-tools@latest deploy --only functions:mergeCell --project pap-26 --non-interactive`:
  correcto; actualización acotada a `mergeCell`, runtime Node 22, región europe-west1.
- Durante preflight se reprodujo una descarga del índice por cada una de 80 peticiones en frío.
  Se añadió coalescencia por promesa, prueba de regresión y se redesplegó la función: correcto.
- `npx --yes firebase-tools@latest deploy --only hosting --project pap-26 --non-interactive --message PARKINGS-FRANCE-SNAPSHOT-001`:
  correcto, exit 0. Guard íntegro previo aprobado. No se desplegaron reglas ni otras funciones.
- Puntero público HTTP 200: `2026-W48-france-20261127-01`, 391.087 parkings / 48.008 celdas;
  Cache-Control `public, max-age=0, must-revalidate`. Celda francesa HTTP 200 con gzip y
  `public, max-age=31536000, immutable`.
- API pública comprobada con URLs normales del cliente, sin parámetros para saltar caché:
  14/14 zonas devuelven HTTP 200, serve=true y la nueva versión. Recuentos por bloque:
  Madrid 938, Barcelona 657, Cuenca 99, Las Palmas 307, Palma 326, Ceuta 78, Melilla 84;
  París 596, Lyon 685, Toulouse 934, Ajaccio 182, Bourges 454, Sancerre 42, Perpiñán 521.
  Son bloques de la API, no los radios de 3 km del contraste anterior. Madrid conserva TTL de
  disponibilidad en vivo; las otras muestras, TTL estático de 300/3600 s.
- Release previa de Hosting para reversión: versión `9c076fe442d36d5f`, release
  `1795654292668000` (26-11-2026). Procedimiento y TTL en tools/parkings/README.md.
- No se ha ejecutado un nuevo run completo de GitHub Actions ni probado un APK en dispositivo.
  La nueva publicación semanal queda configurada para los lunes a las 04:17 UTC al entrar en master.
- Se conserva el worktree con el snapshot y las comparaciones locales ignoradas para revisión o
  recuperación; no se borran estos artefactos al integrar.

## Diseño previsto
- Reutilizar el pipeline y filtro existentes con España, Canarias y Francia en una misma generación semanal.
- Versionar cada generación sin colisiones dentro de la misma semana y revisar transición entre versiones/cachés.
- Impedir publicación parcial y distinguir cobertura ausente de una celda legítimamente vacía.
- Auditar workflow, extractor, generador, manifiesto, backend, cliente, guard de Hosting y documentación/licencia.
- Generar y validar localmente antes de cualquier operación remota. No modificar detección ni ampliar el alcance a refactorizar combustible.

## Criterio de éxito
- Francia genera parkings utilizables en ciudades, rural, frontera y Córcega.
- España y Canarias conservan cobertura, sin duplicados por solapamiento.
- Reejecución de la misma semana no sobrescribe URLs inmutables.
- Pruebas dirigidas y suites proporcionales ejecutadas desde este worktree; resultados y límites registrados.
- Artefacto conjunto revisable y procedimiento de publicación/reversión documentado.
- Comprobación en producción completada: puntero, compresión/caché y API conjunta en 14 zonas.

## Consumidores auditados
| Consumidor | Resultado |
|---|---|
| parkings-snapshot.yml | Cubierto: unión fija, versión única, validación y artefacto revisable; publicación semanal autorizada |
| osm_extract.py | Cubierto: misma geometría/filtro; índices en disco, selección nativa de miembros; hashes ES y Canarias idénticos |
| SnapshotToolMain | Cubierto: rechaza sobrescritura y cada extracto vacío; deduplicación y celdas sin cambio de contrato |
| PublicParkingSnapshot | Cubierto: solo aclaración de versionado; serialización compatible |
| index.ts / ParkingSnapshotReader / parkingsCell.ts | Cubierto: índice, celdas esperadas, transición y fallback conservador; tests unitarios |
| ApiPublicParkingsSource | Exento de cambio: ya entiende serve=false; pruebas existentes ejecutadas |
| Cachés memoria/Room | Exentas de cambio: TTL y lectura en vivo vigentes; latencia de propagación documentada |
| guard_before_hosting_deploy.py | Cubierto: integridad, regiones e IDs contra baseline vivo, también fuera del workflow |
| README / LICENSE | Cubiertos: proceso, descarga ODbL, alcance metropolitano y reversión |
| Web ES/EN | Revisado: copy sin promesa de país concreto ni ocupación francesa; no requiere cambio de copy; build --check correcto |
| Combustible / detección / Firebase rules | Exentos: no se modifican |

## Autorización y límites
El usuario autorizó el worktree separado, copia local de configuración, despliegue de backend y
Hosting (incluido el copy de master), commit, integración en master, push y cron semanal conjunto.
Los cambios ajenos del árbol principal se conservan. La evidencia anterior verifica conservación
ES/Canarias y cobertura francesa metropolitana; no acredita ultramar ni ocupación francesa en vivo.
