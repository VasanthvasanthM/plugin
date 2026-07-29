import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

group = "com.github.vasanthvasanthm"
version = "1.0.1"

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("com.jcraft:jsch:0.1.55")

    intellijPlatform {
        intellijIdeaCommunity("2024.3") // Changed from intellijIdea("2024.3") to target Community Edition
        testFramework(TestFrameworkType.Platform)
    }
}