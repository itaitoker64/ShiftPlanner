# kotlinx.serialization keeps the generated serializers on the serializable classes themselves.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.shiftly.planner.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.shiftly.planner.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
