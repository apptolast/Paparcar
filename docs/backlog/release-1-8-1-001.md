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

## Criterio de éxito

- [x] Generar el AAB firmado y validar la suite agregada y las notas.
- [ ] Publicar el AAB en Google Play y comprobar el código de versión servido.
- [ ] Instalar desde un enlace etiquetado en un dispositivo, retrasar el inicio de sesión y verificar que se registra el canal.
