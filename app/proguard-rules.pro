# SecureGuard Enterprise - app-level ProGuard rules
# SPDX-License-Identifier: Apache-2.0

# --- Keep Room entities and generated implementations ---
-keep class com.secureguard.enterprise.data.model.** { *; }
-keep class com.secureguard.enterprise.data.database.** { *; }

# --- Keep Hilt injected members ---
-keep class dagger.hilt.** { *; }
-keepclassmembers class * {
    @dagger.hilt.android.internal.lifecycle.HiltViewModelFactory *;
    @javax.inject.Inject <init>(...);
}

# --- Retrofit / OkHttp / Gson ---
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# --- BLE (Nordic) ---
-dontwarn no.nordicsemi.android.**
-keep class no.nordicsemi.android.** { *; }

# --- Kotlinx Coroutines ---
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Parcelable ---
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
