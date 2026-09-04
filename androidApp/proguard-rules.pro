# R8 keep rules. Every rule here exists because something resolves a name at
# runtime that R8 cannot see statically. Nothing broader than it has to be.

# Generic type reconstruction. kotlinx.serialization and Ktor's
# ContentNegotiation both rebuild a KType at runtime from these.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep class kotlin.Metadata { *; }

# --- kotlinx.serialization -------------------------------------------------
# Generated serializers are reached through the companion by name, never by a
# call R8 can trace. Upstream's canonical block.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor ------------------------------------------------------------------
# ArcanaApiClient builds HttpClient with no explicit engine in production, so
# the engine arrives through ServiceLoader: META-INF/services names
# AndroidEngineContainer, which nothing references statically.
-keep class io.ktor.client.engine.android.AndroidEngineContainer { *; }
-keep class io.ktor.client.engine.android.** { *; }
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# --- Coroutines ------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-dontwarn kotlinx.coroutines.**

# --- Tink (androidx.security-crypto) ---------------------------------------
# Tink references Error Prone's compile-only annotations, which are not on the
# runtime classpath by design.
-dontwarn com.google.errorprone.annotations.**
