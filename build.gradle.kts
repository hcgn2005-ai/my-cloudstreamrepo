
plugins {
    id("com.android.library")
    kotlin("android")
    id("com.lagradost.cloudstream3.gradle.cloudstream-extension")
}

cloudstream {
    // metadata
    name = "Anikai"
    label = "Anikai"
    description = "CloudStream provider for Anikai.to"
    authors = listOf("hcgn2005-ai")
    recommends = listOf("app.cloudstream3.android")
    
    // technical
    versionCode = 1
    versionName = "1.0.0"
}

android {
    namespace = "com.hcgn2005ai.anikai"
    defaultConfig {
        minSdk = 21
        compileSdk = 33
    }
}

dependencies {
    implementation(repositories.cloudstream)
    implementation("org.jsoup:jsoup:1.15.3")
}
