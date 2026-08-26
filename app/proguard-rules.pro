# SecureGuard Enterprise – R8/ProGuard-Regeln (Release-Minify aktiv)
# ==================================================================
# Ziel: Tree-Shaking/Obfuskation darf KEINE reflexiv deserialisierten
# Modelle entfernen oder umbenennen (Gson nutzt Reflection auf Felder).

# ---------- App-Serialisierungsmodelle ----------
# Room-Entities, DTOs, SearchResult & Co. (Gson/Room reflektieren Felder
# und Spaltennamen).
-keep class com.secureguard.enterprise.data.model.** { *; }

# MCP/JSON-RPC-Payloads (MCPClient.InboxData, OTPData, MagicLinkData –
# Gson deserialisiert sie reflektiv aus content[0].text).
-keep class com.secureguard.enterprise.mcp.** { *; }

# Agent-Modelle (AgentSettings/AgentStatus/AgentCycleResult) landen als
# JSON in MQTT/WS-Nachrichten.
-keep class com.secureguard.enterprise.services.AgentModels$* { *; }

# MQTT-/WebSocket-Ereignis-Wrapper (verschachtelte Gson-Modelle).
-keep class com.secureguard.enterprise.services.MqttService$* { *; }
-keep class com.secureguard.enterprise.services.WebSocketService$* { *; }

# Enums werden NAMENTREU gebraucht: Room-TypeConverter (Enum.valueOf),
# MQTT-Befehle (ALARM, MOTOR_OFF, ...) und Intent-Extras.
-keepclassmembers enum com.secureguard.enterprise.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    *;
}

# ---------- Gson-Reflektion allgemein ----------
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
# JSON-RPC-Aufbau in MCPClient
-keep class com.google.gson.JsonObject { *; }
-dontwarn com.google.errorprone.annotations.**

# ---------- Retrofit (8 API-Interfaces) ----------
-keepattributes Signature
-keepattributes InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ---------- Bibliotheken: Warnungen unterdrücken ----------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.eclipse.paho.**
-dontwarn org.osmdroid.**
-dontwarn com.google.zxing.**
-dontwarn com.hoho.android.usbserial.**
-dontwarn rx.**
-dontwarn javax.annotation.**

# zxing-embedded CaptureActivity wird aus dem Manifest gestartet
# (wird von R8 automatisch behalten); Core-Klassen explizit sichern.
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# USB-Serial-Treiber (usb-serial-for-android Prober-Tabelle)
-keep class com.hoho.android.usbserial.driver.** { *; }

# osmdroid (Tile-Provider nutzt Reflection)
-keep class org.osmdroid.** { *; }

# ---------- Crash-Reports zuordenbar bleiben ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- Bestehende Regeln (beibehalten) ----------
# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
