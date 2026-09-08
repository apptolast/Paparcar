# ─────────────────────────────────────────────────────────────────────────────
# PAPARCAR — ProGuard / R8 rules (release)
# ─────────────────────────────────────────────────────────────────────────────

# Crashlytics — keep source line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Allow Crashlytics to obfuscate but keep enough info to deobfuscate later via mapping.txt.
-renamesourcefileattribute SourceFile

# ─────────────────────────────────────────────────────────────────────────────
# kotlinx.serialization
# ─────────────────────────────────────────────────────────────────────────────
# ⛔ NO escribir aquí reglas de serialización: `kotlinx-serialization-core` publica las suyas como
# consumer rules y ya vienen aplicadas — acotadas con `-if @kotlinx.serialization.Serializable
# class **`, o sea a las clases ANOTADAS. Las que había aquí cubrían `com.rndeveloper.paparcar.**`
# ENTERO (Companion, serializer(), $$serializer) para proteger 11 ficheros con @Serializable.
# [R8-RULES-THE-LIBRARY-ALREADY-SHIPS-001]
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ─────────────────────────────────────────────────────────────────────────────
# Koin
# ─────────────────────────────────────────────────────────────────────────────
# ⛔ El grafo de DI NO necesita reglas. Lo dice la consumer rule del propio Koin: la resolución de
# koinViewModel() es en COMPILACIÓN (get() inline reificado), no hay reflexión sobre constructores.
-keep class com.rndeveloper.paparcar.data.datasource.remote.FirebaseDataSourceImpl { *; }
-keepnames class kotlin.coroutines.Continuation

# ─────────────────────────────────────────────────────────────────────────────
# Room
# ─────────────────────────────────────────────────────────────────────────────
# ⛔ Tampoco lleva reglas nuestras. `room-runtime` publica `-keep class * extends RoomDatabase
# { void <init>(); }`, que es justo lo que tapa el `Class.forName(<nombre> + "_Impl")` de
# `Room.databaseBuilder`: ancla `AppDatabase` y, por herencia, su `_Impl`. Entidades, DAOs y
# converters los referencia el código generado directamente, sin reflexión.

# ─────────────────────────────────────────────────────────────────────────────
# Firestore DTOs — used by reflection when (de)serialising documents.
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.rndeveloper.paparcar.data.datasource.remote.dto.** { *; }
-keep class com.rndeveloper.paparcar.domain.model.** { *; }

# GitLive Firebase SDK (Kotlin Multiplatform wrappers).
-keep class dev.gitlive.firebase.** { *; }
-dontwarn dev.gitlive.firebase.**

# Google Firebase native (Crashlytics, Firestore, Auth, GMS) — keep public API.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ─────────────────────────────────────────────────────────────────────────────
# WorkManager
# ─────────────────────────────────────────────────────────────────────────────
# Sí hay reflexión aquí — `WorkerFactory` instancia por el NOMBRE de clase que guarda en su propia
# base de datos — pero ya está tapada sin nosotros, y por dos vías que se complementan:
#   · `work-runtime` publica `-keepnames class * extends ListenableWorker` → el nombre sobrevive.
#   · los 13 workers se encolan con literal reificado (`OneTimeWorkRequestBuilder<X>()`) → la clase
#     sobrevive, que es lo que `-keepnames` por sí solo NO garantiza.
# ⛔ Si algún día un worker se encolara SOLO por string, esa segunda pata desaparece y hará falta
# anclarlo explícitamente. Comprobar los 13 antes de tocar nada de esto.

# ─────────────────────────────────────────────────────────────────────────────
# BaseLogin
# ─────────────────────────────────────────────────────────────────────────────
# ⛔ NO lleva `-keep`. El que hubo aquí decía "keep public API so reflection-based init works", y
# ese init reflexivo NO EXISTE: auditada la librería en su repo, no hay un solo `Class.forName`,
# `newInstance`, `kotlin.reflect` ni `ServiceLoader`. Del lado de acá, los usos son imports directos
# de tipos (AuthRepository, AuthState, LoginRoute, authRoutesFlow, IdentityProvider, appContext):
# enlace estático, que R8 sigue solo. Anclaba 357 clases, de las que 164 eran UI de auth que no se
# renderiza. [R8-BASELOGIN-IS-LINKED-NOT-REFLECTED-001]
-dontwarn com.apptolast.baselogin.**

# ─────────────────────────────────────────────────────────────────────────────
# Compose runtime
# ─────────────────────────────────────────────────────────────────────────────
# ⛔ Los @Composable NO se keepean. El plugin del compilador reescribe cada llamada en COMPILACIÓN
# (les inyecta el Composer y la clave de grupo): no hay lookup por nombre en runtime. La regla que
# había aquí anclaba 2.122 métodos, y en Kotlin todos los composables de un fichero comparten la
# misma clase `…Kt` — bastaba con que UNO se usara para que los demás no se pudieran podar.
-keep class kotlin.Metadata { *; }
