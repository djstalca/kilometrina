# Keep useful source information in local crash diagnostics while allowing R8 optimization.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin coroutines may reference generic signatures through reflection in diagnostics.
-keepattributes Signature

# Room and AndroidX ship consumer ProGuard rules; no broad keep rules are needed here.
