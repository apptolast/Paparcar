# Paparcar — Release security checklist

Acciones obligatorias **antes** del primer release público. Cubre el modelo de seguridad de las API keys de Google + reglas de Firestore.

> Trackea [SEC-001] y [AUDIT-FIRESTORE-001] en `docs/ROADMAP.md`.

---

## Modelo de seguridad — fundamentos

Las API keys de Google **siempre** acaban en el APK que se distribuye. No son secretos en el sentido tradicional: son identificadores. Cualquiera que descompile el APK puede extraerlas. **La protección real vive en restricciones del lado del servidor**, no en esconder la key.

Por tanto:

1. **GCP API key restrictions** — Restringir cada key por `package name + SHA-1` y por API scope. Sin esto, alguien puede extraer la key y agotar nuestra quota mensual.
2. **Firebase Security Rules** — Reglas Firestore/Auth que validan `request.auth.uid` en cada documento. Sin esto, cualquiera con la API key de Firebase puede leer/escribir toda la base.
3. **App Check** (no habilitado todavía) — Attestation que prueba que el tráfico viene del APK firmado real y no de un script. Recomendado para v1.x.

---

## §1 · Maps API key — restricciones GCP [SEC-001]

**Project:** `pap-26` (number `431876996213`)
**Key actual:** REDACTADA de este doc [AUDIT-INFRA-001 A8] — el valor sigue siendo recuperable
del histórico git (este doc y el AndroidManifest antiguo), razón de más por la que la ROTACIÓN
de §1.1 es la única mitigación real. La key activa se consulta en GCP Console → Credentials.

### Acciones obligatorias en [GCP Console → Credentials](https://console.cloud.google.com/apis/credentials?project=pap-26)

#### 1.1 Rotar la key

La key actual estuvo hardcodeada en `AndroidManifest.xml` en commits previos. Aunque hoy se inyecta vía `manifestPlaceholders`, el valor sigue siendo recuperable via `git log --all -p AndroidManifest.xml`. **Rotar antes del primer beta público.**

1. GCP Console → APIs & Services → Credentials
2. Crear API key nueva (botón "Create credentials" → "API key")
3. Anotar la nueva key en `local.properties` como `MAPS_API_KEY=AIza...`
4. Reemplazar también en GitHub Actions secrets si aplica (`MAPS_API_KEY`)
5. Borrar la key antigua **tras** verificar que la nueva funciona en debug

#### 1.2 Application restrictions

En la nueva key → "Application restrictions" → **Android apps**:

- Package name: `io.apptolast.paparcar`
- SHA-1 fingerprint del keystore debug + release. Obtener con:
  ```bash
  # Debug
  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
  # Release
  keytool -list -v -keystore keystore/release.jks -alias paparcar-release | grep SHA1
  ```
- Añadir ambas entradas (debug + release).

#### 1.3 API restrictions

En la misma key → "API restrictions" → **Restrict key**:

- Habilitar **solo**: `Maps SDK for Android` (y `Places API` si se usa en el futuro).
- Bloquear el resto. Si en algún momento se necesita Geocoding API o Directions API, añadirlas explícitamente.

#### 1.4 Verificación

Tras aplicar restricciones, esperar ~5 minutos (propagación) y verificar:

- Debug build en emulador/dispositivo: mapa carga correctamente.
- Release build firmado: mapa carga correctamente.
- Una build con la key correcta pero sin restricciones SHA-1 actualizadas debería fallar — útil para verificar que las restricciones están activas.

---

## §2 · Firebase Security Rules — auditoría [AUDIT-FIRESTORE-001]

**Project:** `pap-26` → [Firestore → Rules](https://console.firebase.google.com/project/pap-26/firestore/rules)

### Reglas mínimas requeridas

Cada colección debe filtrar por `request.auth.uid`:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Vehicles — el usuario solo puede ver/editar los suyos
    match /vehicles/{vehicleId} {
      allow read, write: if request.auth != null && resource.data.userId == request.auth.uid;
      allow create: if request.auth != null && request.resource.data.userId == request.auth.uid;
    }

    // UserParkings — sesiones de parking propias
    match /userParkings/{parkingId} {
      allow read, write: if request.auth != null && resource.data.userId == request.auth.uid;
      allow create: if request.auth != null && request.resource.data.userId == request.auth.uid;
    }

    // Zones — favoritos del usuario
    match /zones/{zoneId} {
      allow read, write: if request.auth != null && resource.data.userId == request.auth.uid;
      allow create: if request.auth != null && request.resource.data.userId == request.auth.uid;
    }

    // Spots — plazas públicas (modelo comunitario)
    //   - Lectura libre para cualquier auth (todos ven plazas cercanas)
    //   - Escritura: solo el creador puede editar/eliminar su propio spot
    match /spots/{spotId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null && request.resource.data.publishedByUserId == request.auth.uid;
      allow update, delete: if request.auth != null && resource.data.publishedByUserId == request.auth.uid;
    }

    // UserProfile — solo el dueño
    match /userProfiles/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

### Verificación

1. [Firestore → Rules → Rules playground](https://console.firebase.google.com/project/pap-26/firestore/rules) — simular operaciones autenticadas y no autenticadas.
2. **Test con auth nulo:** todas las operaciones deben fallar (excepto las marcadas como `allow read: if request.auth != null` — fallarán por auth).
3. **Test con auth de UserA leyendo doc de UserB:** debe fallar.

---

## §3 · App Check (futuro, no bloqueante para beta)

Cuando salgamos de beta cerrada → activar [Firebase App Check](https://firebase.google.com/docs/app-check) con Play Integrity. Garantiza que el tráfico viene del APK firmado real y no de scripts. Out of scope para el primer release a 10 testers.

---

## Checklist pre-release

Antes de subir el primer APK a App Distribution público:

- [ ] §1.1 — Maps API key rotada
- [ ] §1.2 — Application restrictions aplicadas (package + SHA-1 debug + release)
- [ ] §1.3 — API restrictions limitadas a Maps SDK for Android
- [ ] §1.4 — Verificación: debug y release cargan el mapa correctamente
- [ ] §2 — Reglas Firestore desplegadas y testeadas en Rules playground
- [ ] Verificación E2E: usuario A no puede leer datos de usuario B
- [ ] (Opcional) §3 App Check activado
