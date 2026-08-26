# Add project specific ProGuard rules here.
-keep class com.secureguard.enterprise.data.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Gson / Moshi models used via reflection
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.secureguard.enterprise.services.apis.** { *; }
-keep class com.secureguard.enterprise.mcp.** { *; }
-keep class com.secureguard.enterprise.services.CrowdSighting { *; }

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**

# Paho MQTT
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# SQLCipher
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**
