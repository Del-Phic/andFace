# Keep MediaPipe Task APIs and generated/protobuf metadata reachable in minified
# release builds. The app code itself can still be shrunk and obfuscated.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class org.tensorflow.lite.** { *; }

# Optional MediaPipe graph-profiling/template proto classes are referenced by
# library APIs but are not packaged in the Android task runtime.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate

# JNI entry points may be discovered by native libraries.
-keepclasseswithmembernames class * {
    native <methods>;
}
