# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class ai.onnxruntime.** { *; }
-keep class com.ahmadullahpk.alldocumentreader.** { *; }
-keep class org.apache.harmony.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn com.github.luben.zstd.**
-keep class com.raival.compose.file.explorer.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-keep class org.joni.** { *; }
-keep class android.content.** { *; }
-keep class com.android.apksig.** { *; }

-keepnames interface * { *; }

# Suppress warnings from missing classes in PDFBox and SSHJ/EdDSA
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
-dontwarn sun.security.x509.X509Key