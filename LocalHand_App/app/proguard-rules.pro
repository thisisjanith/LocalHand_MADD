# Moshi's reflective adapter (moshi-kotlin, no codegen) needs Kotlin
# metadata, kotlin-reflect's own machinery, and the DTOs' constructors/field
# names all intact to match JSON keys — moshi-kotlin ships as a plain .jar
# (not an .aar), so it carries no consumer ProGuard rules of its own; these
# are Moshi's officially documented rules plus this app's DTO package.
-keep,allowobfuscation,allowshrinking interface kotlin.Metadata
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
    @com.squareup.moshi.JsonQualifier <methods>;
}
-keep @com.squareup.moshi.JsonQualifier interface *
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.jvm.internal.**
-dontwarn org.jetbrains.kotlin.**

-keepclassmembers class com.example.localhand_new.data.remote.dto.** {
    <fields>;
    <init>(...);
}
-keep class com.example.localhand_new.data.remote.dto.** { *; }

# Retrofit/OkHttp: standard rules for reflection-based interface proxies and
# generic signatures used by Retrofit's call adapters.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
