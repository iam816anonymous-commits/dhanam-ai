# Project specific ProGuard rules for Dhanam AI

# Optimization & Stacktrace
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Moshi & JSON Models
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class * implements com.squareup.moshi.JsonAdapter { *; }
-keep class com.example.ai.GeminiRequest { *; }
-keep class com.example.ai.GeminiResponse { *; }
-keep class com.example.ai.Candidate { *; }
-keep class com.example.ai.ResponseContent { *; }
-keep class com.example.ai.ContentPartContainer { *; }
-keep class com.example.ai.TextPart { *; }
-keep class com.example.ai.GenerationConfig { *; }
-keep class com.example.ai.ParsedItem { *; }
-keep class com.example.ai.ParsedVoiceBatch { *; }
-keep class com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory { *; }
-dontwarn com.squareup.moshi.**

# Retrofit
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep,allowobfuscation interface com.example.ai.GeminiApi { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Room Database & Entities
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.data.model.** { *; }
-keep class * implements androidx.room.migration.Migration

# WorkManager
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# AndroidX Biometric & Security
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Firebase & App Check
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
