import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}

// The global ~/.gradle/init.gradle applies Spotless with a Java target of fileTree(rootDir)
// that doesn't exclude build/. That overlaps with instrumentCode's output (build/instrumented),
// which Gradle's task validation flags as an undeclared dependency. Exclude build/ here.
// withGroovyBuilder is used instead of a typed `configure<SpotlessExtension>` because Spotless is
// applied by the init script, and referencing its extension class from this script's own
// classpath would load a distinct (classloader-mismatched) copy of the same class.
extensions.getByName("spotless").withGroovyBuilder {
    "java" {
        "targetExclude"("build/**")
    }
}

repositories {
    mavenCentral()

    // The global ~/.gradle/init.gradle (Asys company config) declares its own project-level
    // repositories block, which takes priority over settings.gradle.kts's
    // dependencyResolutionManagement repos. Re-declare the JetBrains repo here so it's actually used.
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("com.google.code.gson:gson:2.14.0")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdeaCommunity("2025.2.2")
        bundledPlugin("Git4Idea")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        // IPGP's default changeNotes wiring falls back to changelog.getLatest(), which throws
        // "Version is missing" until a version-specific section exists in CHANGELOG.md. Wiring
        // getUnreleased() explicitly avoids that until this plugin's first tagged release.
        changeNotes = providers.provider {
            with(changelog) {
                renderItem(
                    getUnreleased().withHeader(false).withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }
}
