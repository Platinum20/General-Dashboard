# Readability4j usa slf4j per i log e jsoup ha dipendenze opzionali: su Android non servono.
-dontwarn org.slf4j.**
-dontwarn com.google.re2j.**

# I metodi chiamati dal JavaScript dei WebView (WebFetcher) non vanno rinominati né rimossi.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# NewPipe Extractor (player nativo di YouTube): usa riflessione e Rhino per il JavaScript di YouTube.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn org.jsoup.**
-dontwarn com.google.errorprone.annotations.**

# sherpa-onnx (voce Piper): il codice nativo legge i campi delle configurazioni per nome.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# commons-compress: formati opzionali che l'app non usa (solo tar.bz2).
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
