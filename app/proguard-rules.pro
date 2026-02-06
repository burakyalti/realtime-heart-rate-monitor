# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Ktor
-keep class io.ktor.** { *; }
-keepclassmembernames class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class net.hrapp.hr.**$$serializer { *; }
-keepclassmembers class net.hrapp.hr.** {
    *** Companion;
}
-keepclasseswithmembers class net.hrapp.hr.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# BLESSED BLE
-keep class com.welie.blessed.** { *; }
