# ML Kit rules
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep models for Gson serialization
-keepclassmembers class com.example.stickerwidget.data.** { *; }
