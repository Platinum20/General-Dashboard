# Solo per voicecheck.yml (-PminifyDebug=true): la prova sull'emulatore chiama queste classi
# dall'APK di test, quindi restano; sherpa-onnx segue le stesse regole della release.
-keep class com.platinum20.dashboard.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn **
