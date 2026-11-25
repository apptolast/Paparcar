# RELEASE-1-8-1-001 · la atribución de instalaciones llega a Play

**Estado:** 🔵 En progreso · rama `chore/RELEASE-1-8-1-001-install-attribution` · worktree `.artifacts/Paparcar-release-1-8-1`

## Alcance

Play Console confirma 1.8.0 (vc19) en producción. Su AAB se cortó antes de integrar la atribución
de instalación y su corrección de arranque. La 1.8.1 (vc20) parte de `e4d8a8ea4` e incluye el
lector de Play Install Referrer, los canales de TikTok y la publicación del canal al iniciar sesión.
El backend de Meta y los enlaces etiquetados ya se desplegaron en Firebase. El corte también
asegura que el perfil exista antes de reintentar la escritura, que Meta se descifre con sesión y
que Android persista juntos el resultado pendiente y el marcador de lectura.

El AAB está trazado al commit `321100e03` del worktree de release. La integración en `master`
conserva los cambios posteriores de invitaciones y se validó aparte con
`testAll :app:compileProdDebugKotlin --rerun-tasks` (105 tareas ejecutadas).

## El AAB de esta versión

<!-- cut:measured -->

| Comprobación | Resultado |
|---|---|
| SHA-256 | `82e21b7a71f2e73c3a68502bc3ac6d5bd94c445c03adb1d475c54376fb8cc242` |
| Tamaño | 24.384.909 B · 1570 entradas |
| Firma | Upload key vigente |
| Versión | AAB con `versionName 1.8.1`. Manifest de empaquetado con `versionCode 20`. |
| Suite | `testAll --rerun-tasks` completado, incluido mock Android y compilación klib iOS Simulator |
| Bundle | `:app:bundleProdRelease --rerun-tasks` completado |
| Web | Regenerada y comprobada sin cambios en `hosting/public/`. |
| Notas | Nueve locales validados. La más larga tiene 93 caracteres. |
| Archivo | `.artifacts/paparcar-1.8.1-vc20.aab` desde la raíz principal del repositorio |

<!-- /cut:measured -->

## Notas de versión

`docs/release/play-listing/paste/release-notes-v1.8.1.txt` contiene las notas para los nueve locales.

## Estado en Play · 26-11-2026

Google Play aceptó el vc20 y se releyó como `internal draft`, con notas en nueve idiomas.
`production` seguía en vc19. La ficha de los nueve idiomas coincidía con el repositorio.
La etiqueta pública de Seguridad de los datos mostraba solo el historial de compras como
compartido y solo Analítica para las interacciones. El SDK de Meta comparte interacciones
y el identificador publicitario cuando el usuario consiente. Para corregir el formulario,
hace falta el CSV actual de Play Console o acceso de edición al formulario; el CSV de
importación sobrescribe las respuestas. La cuenta de servicio usada publica en canales de
prueba y la promoción a producción necesita una cuenta de Play Console con ese permiso.

## Política pública y revisión

El 26-11-2026 Firebase Hosting publicó la versión `fad42e16aea896c7`. Se verificó que
solo cambiaron las páginas de privacidad y borrado entre los 25.242 archivos. Ambas
se releyeron desde el dominio público. La versión publicada describe el referrer almacenado,
lo que recibe Analytics y la posible retención del identificador interno en una plaza
comunitaria. La cláusula de invitaciones del siguiente build se conserva en el repositorio
para cuando se publique ese flujo.

La revisión encontró tres comprobaciones de producción pendientes.

- Revisar la declaración de ubicación en segundo plano y su vídeo. El diálogo previo al
  permiso explica los pasos de Android, mientras que la finalidad de la recogida está en
  el texto de la pantalla anterior.
- Confirmar las cuentas habilitadas para diagnóstico de campo y su consentimiento. El
  logger puede conservar en memoria el indicador de la cuenta anterior al cambiar de sesión.
- Verificar la limpieza o desvinculación de `reportedBy` en las plazas al eliminar una
  cuenta. La aclaración pública refleja la retención actual del registro.

## Criterio de éxito

- [x] Generar el AAB firmado y validar la suite agregada y las notas.
- [x] Subir el AAB a Internal y comprobar el borrador con las notas en nueve idiomas.
- [ ] Actualizar Seguridad de los datos y comprobar el acceso de revisión en Play Console.
- [ ] Publicar el AAB en producción y comprobar el código de versión servido.
- [ ] Instalar desde un enlace etiquetado en un dispositivo, retrasar el inicio de sesión y verificar que se registra el canal.
