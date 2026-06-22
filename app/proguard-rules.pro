-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class com.flowfuel.app.**$$serializer { *; }
-keepclassmembers class com.flowfuel.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.flowfuel.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
