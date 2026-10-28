# Abrova Push SDK ProGuard Rules

# Keep Abrova Push SDK classes
-keep class ir.abrova.push.sdk.** { *; }

# Keep PN Protocol classes
-keep class ir.abrova.protocol.** { *; }

# Keep MQTT classes
-keep class org.eclipse.paho.** { *; }

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
