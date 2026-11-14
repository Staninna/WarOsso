# Readable stack traces from the release build.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The app is GPL and has nothing to hide; unobfuscated names keep crash reports readable.
-dontobfuscate
