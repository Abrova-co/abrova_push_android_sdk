# Consumer ProGuard rules for Abrova Push SDK
-keep class ir.abrova.push.sdk.** { *; }

# Keep PN Protocol classes
-keep class ir.abrova.protocol.** { *; }

# Coil image loading library
-dontwarn coil.**
-keep class coil.** { *; }
-dontwarn coil.bitmap.**
