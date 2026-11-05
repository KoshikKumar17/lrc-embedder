# TagLib (JNI) - keep bindings and native method names
-keep class com.kyant.taglib.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Ktor / coroutines / slf4j warnings
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn java.lang.management.**
-dontwarn kotlinx.coroutines.debug.**

# Keep Kotlin metadata/annotations
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
