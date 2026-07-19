# Pocket AI Studio ProGuard Rules
# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Keep Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep AI engine
-keep class com.pocketai.studio.ai.** { *; }
# Keep JNI bridge classes — R8 must not rename or strip native methods
-keep class com.pocketai.studio.ai.jni.LlamaBridge { *; }
-keepclassmembers class com.pocketai.studio.ai.jni.LlamaBridge {
    private native <methods>;
}

# Keep ML Kit
-keep class com.google.mlkit.** { *; }

# Keep iText
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.bouncycastle.**
-dontwarn com.itextpdf.bouncycastleconnector.**
-dontwarn com.itextpdf.barcodes.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn org.slf4j.**

# Keep BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep Jackson (transitive dep from iText)
-dontwarn com.fasterxml.jackson.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn javax.xml.**
-dontwarn org.xml.sax.**
