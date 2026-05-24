# ─────────────────────────────────────────────────────────────────────────────
# PAPARCAR — ProGuard / R8 rules (release)
# ─────────────────────────────────────────────────────────────────────────────

# Crashlytics — keep source line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Allow Crashlytics to obfuscate but keep enough info to deobfuscate later via mapping.txt.
-renamesourcefileattribute SourceFile

# ─────────────────────────────────────────────────────────────────────────────
# kotlinx.serialization — keep @Serializable classes and their generated companions.
# ─────────────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class io.apptolast.paparcar.**$$serializer { *; }
-keepclassmembers class io.apptolast.paparcar.** {
    *** Companion;
}
-keepclasseswithmembers class io.apptolast.paparcar.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─────────────────────────────────────────────────────────────────────────────
# Koin — keep DI module declarations and the classes they construct via reflection.
# ─────────────────────────────────────────────────────────────────────────────
-keep class io.apptolast.paparcar.di.** { *; }
-keep class io.apptolast.paparcar.data.datasource.remote.FirebaseDataSourceImpl { *; }
-keepnames class kotlin.coroutines.Continuation

# ─────────────────────────────────────────────────────────────────────────────
# Room — keep entities, DAOs, and the generated `_Impl` classes.
# ─────────────────────────────────────────────────────────────────────────────
-keep class io.apptolast.paparcar.data.datasource.local.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ─────────────────────────────────────────────────────────────────────────────
# Firestore DTOs — used by reflection when (de)serialising documents.
# ─────────────────────────────────────────────────────────────────────────────
-keep class io.apptolast.paparcar.data.datasource.remote.dto.** { *; }
-keep class io.apptolast.paparcar.domain.model.** { *; }

# GitLive Firebase SDK (Kotlin Multiplatform wrappers).
-keep class dev.gitlive.firebase.** { *; }
-dontwarn dev.gitlive.firebase.**

# Google Firebase native (Crashlytics, Firestore, Auth, GMS) — keep public API.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# GeoFire — used by location proximity queries.
-keep class com.firebase.geofire.** { *; }
-dontwarn com.firebase.geofire.**

# ─────────────────────────────────────────────────────────────────────────────
# WorkManager — workers are instantiated via reflection by name.
# ─────────────────────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker

# ─────────────────────────────────────────────────────────────────────────────
# BaseLogin (JitPack lib) — keep public API so reflection-based init works.
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.apptolast.baselogin.** { *; }
-dontwarn com.apptolast.baselogin.**

# ─────────────────────────────────────────────────────────────────────────────
# Compose runtime — already covered by default rules, but kotlin metadata is needed.
# ─────────────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}
