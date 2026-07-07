import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.17.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        pycharmCommunity(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        zipSigner()
    }
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("javaVersion").get().toInt()))
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "251.*"
        }
    }

    pluginVerification {
        ides {
            // Explicitly target PyCharm Community to avoid Gradle 9.x configuration container issues with recommended()
            ide(providers.gradleProperty("platformType").get(), providers.gradleProperty("platformVersion").get())
        }
    }
}

ktlint {
    version.set("1.3.1")
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(true) // Do not fail the build on style/formatting issues
}

tasks {
    wrapper {
        gradleVersion = "8.10.2"
    }

    patchPluginXml {
        version = providers.gradleProperty("pluginVersion").get()
        sinceBuild = "243"
        untilBuild = "251.*"
    }

    withType<JavaCompile> {
        sourceCompatibility = providers.gradleProperty("javaVersion").get()
        targetCompatibility = providers.gradleProperty("javaVersion").get()
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    named("build") {
        dependsOn("ktlintCheck")
    }
}
