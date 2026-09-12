# Vídeo de la declaración de ACCESS_BACKGROUND_LOCATION — guion v2 (tras el rechazo del 05-09-2026)

**Por qué existe esta v2.** Play rechazó el envío #1 con «No se ha podido verificar la función en
segundo plano». El rechazo NO cuestiona la app (el prominent disclosure y el flujo de permisos
cumplen desde `d4ecebc6`): cuestiona el **vídeo**. El revisor exige ver CUATRO cosas, y la clave
que faltaba es la primera — la función **funcionando**, no solo pidiendo permisos:

1. La funcionalidad de la función declarada, en la app.
2. Cómo esa función usa la ubicación **en segundo plano** (app cerrada / no en uso).
3. Cómo el usuario llega al **prominent disclosure**.
4. El diálogo de **permiso runtime** con el consentimiento del usuario.

**Arreglo = regrabar el vídeo + reenviar el formulario de declaración.** Ni código nuevo, ni AAB
nuevo, ni tocar el manifest.

---

## Reglas de grabación

- **Build prod** (`prodDebug` vale para la demo funcional; el flavor `mock` FINGE permisos y no
  sirve). Device **físico** — la demo del aparcamiento exige un viaje real.
- ⛔ **NO usar el Oppo ni el Redmi del banco de pruebas**: su setup de field-test es intencional
  (ambos COORDINATOR, DB reseteada) y no se tocan sus ajustes. Usar el móvil personal.
- **Idioma del móvil en INGLÉS** durante la grabación: el revisor tiene que leer el disclosure.
- Grabar con la **grabadora de pantalla del propio móvil** (sin tether adb en el coche);
  `adb screenrecord` corta a 3 min. Montar/recortar en **Clipchamp** (viene con Windows 11;
  no hay ffmpeg en la máquina).
- Duración objetivo: **60–120 s**. Rótulos breves en EN sobre cada elemento ayudan al revisor
  («Prominent disclosure», «App is closed», «Parking detected in background»).
- Subir a **YouTube unlisted** y pegar el enlace en el formulario.

## Segmento A — disclosure + permisos (~30 s, instalación limpia)

Borrar datos de la app (móvil personal, no banco) para pasar por onboarding.

1. Onboarding **pág. 3** («Why we need your permissions»): pausa 3–4 s sobre el subtítulo
   *«Paparcar collects location data even when the app is closed or not in use…»*. → elemento 3.
2. Pantalla de permisos: encuadrar la fila **Background location — «Required to detect parking
   even when the app is closed or not in use»**. Pausa. → elemento 3.
3. Conceder ubicación → diálogo del sistema → **«While using the app»**. → elemento 4.
4. Botón **«Allow background activity»** → diálogo guía («Enable background location») →
   **«Open settings»** → pantalla de Ajustes del sistema → seleccionar **«Allow all the time»**
   → back a Paparcar. → elemento 4 (en Android 11+ este paso por Ajustes es obligatorio y el
   revisor lo pide explícitamente).

## Segmento B — la función usando background location (~45–60 s) ← lo que faltaba

Demo determinista: **estrategia Bluetooth** (coche emparejado por MAC en Vehículos, BT activo).
BT disconnect → fix GPS → alejarse ≥30 m → confirmación. Si el móvil personal no tiene coche
con BT, misma escena vía Coordinator (conducción real + aparcar + caminar), menos predecible.

5. Home con el vehículo vigilado (identidad azul BT). Rótulo: *«Automatic parking detection is
   armed»*. → elemento 1.
6. **Cerrar la app** (recientes → swipe) y quedarse en el launcher: que se VEA que no está en
   uso. Bajar la shade y encuadrar la **notificación del servicio de detección** si está
   presente. Rótulo: *«App closed — detection keeps running»*. → elemento 2.
7. Escena real: aparcar, apagar motor (BT se desconecta), salir y caminar ≥30 m **con la
   pantalla del launcher grabándose** (la app sigue cerrada).
8. Llega la **notificación de aparcamiento detectado** con la app cerrada. Encuadrarla y
   pausar. → elementos 1+2 (esto es literalmente «la función usa ubicación en background»).
9. Tap en la notificación → Home con el **pin en la calle real** donde quedó el coche.
   Rótulo: *«Your parking spot, saved automatically»*. → elemento 1.

El mismo vídeo cubre la declaración de `FOREGROUND_SERVICE_LOCATION` (la notificación del FGS
del paso 6 es lo que esa declaración pide ver).

## Reenvío en el Console

Play Console → **Contenido de la app → Permisos de apps sensibles → Permisos de ubicación**:
sustituir el enlace del vídeo y **reenviar el formulario**. Texto de la función declarada
(mantener consistente con ficha + Data Safety + política):

> **Automatic parking detection.** Paparcar's core feature: it detects when the user parks
> their car and saves the spot automatically, and detects when they drive away so the freed
> spot can be shared with nearby users. Detection happens precisely when the app is closed or
> not in use (the user is driving), which is why ACCESS_BACKGROUND_LOCATION is required. The
> prominent disclosure is shown during onboarding and on the permissions screen before any
> system dialog. Video timestamps: 0:00 disclosure · 0:20 runtime permission ·
> 0:35 "Allow all the time" · 0:50 feature working with the app closed.

(Ajustar los timestamps al montaje final.)

## Checklist antes de subir

- [ ] El vídeo muestra los 4 elementos (repasar la lista de arriba contra el montaje).
- [ ] Móvil en inglés, textos legibles (no acelerar los momentos de lectura).
- [ ] Se ve la app CERRADA antes de que llegue la notificación (elemento 2 — el más citado en
      rechazos repetidos).
- [ ] YouTube unlisted, enlace probado en incógnito.
- [ ] Formulario reenviado y nota en la memoria del proyecto.
