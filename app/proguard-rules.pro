# Room, DataStore, OkHttp and Compose ship their own consumer rules.
# OkHttp optional TLS providers are not bundled with this app.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep line numbers for readable crash reports from side-loaded release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
