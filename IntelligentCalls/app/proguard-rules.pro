# IntelligentCalls ProGuard & R8 Optimization Rules

# Preserve Kotlin Metadata and Coroutines
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Retain Gson DTO Models
-keep class com.example.appcall.data.model.** { *; }
-keepclassmembers class com.example.appcall.data.model.** { *; }
-keepclassmembers enum * { *; }

# Retain Retrofit & OkHttp
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Retain Dagger / Hilt
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.internal.TestSingletonComponent { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Retain Shizuku Privileged API
-keep class rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.** { *; }

# Retain AndroidX Biometric & Compose
-keep class androidx.biometric.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }

# Preserve App Database Entities
-keep class com.example.appcall.data.local.** { *; }