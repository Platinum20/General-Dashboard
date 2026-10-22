# Readability4j usa slf4j per i log e jsoup ha dipendenze opzionali: su Android non servono.
-dontwarn org.slf4j.**
-dontwarn com.google.re2j.**

# I metodi chiamati dal JavaScript dei WebView (WebFetcher) non vanno rinominati né rimossi.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
