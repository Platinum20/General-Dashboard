// Strumento a parte (non fa parte dell'app): il motore del blocco pubblicità su un computer,
// per provarlo su pagine vere con Chromium (vedi live.js e il workflow "Verifica dal vivo").
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
}
rootProject.name = "adblock-check"
