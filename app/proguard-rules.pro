# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve Line Numbers and Source File for crash reporting
-keepattributes SourceFile,LineNumberTable

# Domain models and Moshi serialization rules for backup/restore
-keep class com.suguru.expensetracker.domain.model.** { *; }
-keepclassmembers class com.suguru.expensetracker.domain.model.** { *; }
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Room Database & SQLite
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

