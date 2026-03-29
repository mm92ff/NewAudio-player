# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line numbers in stack traces for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Room ---
# Room generates its own ProGuard rules via consumer-rules.pro,
# but keep entity/DAO classes in case of reflection-based tooling
-keep class com.example.newaudio.data.database.** { *; }

# --- Domain Models ---
# Prevent stripping of serializable data classes used across layers
-keep class com.example.newaudio.domain.model.** { *; }

# --- Hilt ---
# Hilt generates keep rules automatically via the Hilt Gradle plugin,
# but add explicit protection for custom qualifiers and modules
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# --- Kotlin Coroutines ---
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# --- Kotlinx Serialization (used for Navigation routes) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    static **$$serializer INSTANCE;
}
-keep,includedescriptorclasses class com.example.newaudio.**$$serializer { *; }
-keepclassmembers class com.example.newaudio.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Timber ---
-dontwarn org.jetbrains.annotations.**
