# Solo per voicecheck.yml (-PreleaseCheck=true): la prova sull'emulatore chiama queste classi
# dall'APK di test, quindi restano coi loro nomi; sherpa-onnx segue le stesse regole della release.
-keepnames class com.platinum20.dashboard.news.voice.** { *; }
-keep class com.platinum20.dashboard.news.voice.VoiceStore { *; }
-keep class com.platinum20.dashboard.news.voice.NeuralVoice { *; }
-keep class com.platinum20.dashboard.news.voice.ReadingVoice { *; }
-keep class com.platinum20.dashboard.news.voice.VoiceFiles { *; }
-keep class com.platinum20.dashboard.news.RecapSpeaker { *; }
-keep class com.platinum20.dashboard.news.RecapListening { *; }
-keep class com.platinum20.dashboard.settings.AppSettings { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn **
