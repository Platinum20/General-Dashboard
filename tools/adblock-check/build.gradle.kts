plugins {
    kotlin("jvm") version "2.1.0"
    application
}

// Gli stessi file del motore usati dall'app: solo quelli senza Android.
val engine = file("../../app/src/main/java/com/platinum20/dashboard/adblock")
sourceSets["main"].kotlin.srcDir(engine)
sourceSets["main"].kotlin.exclude("**/AdBlocker.kt")


application {
    mainClass.set("com.platinum20.dashboard.adblock.check.ServerKt")
}
