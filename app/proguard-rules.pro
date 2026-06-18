# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pokelegoguy.**$$serializer { *; }
-keepclassmembers class com.pokelegoguy.** {
    *** Companion;
}
-keepclasseswithmembers class com.pokelegoguy.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
