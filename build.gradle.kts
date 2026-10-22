plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "com.slicc"
version = "1.5.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // To use a locally installed IDE (faster builds, no download needed):
        //   Set ide.localPath in gradle.properties to your IDE installation directory.
        //   Example: ide.localPath=C:/Program Files/JetBrains/CLion 2026.1
        // If ide.localPath is not set, CLion 2026.1 will be downloaded automatically.
        val localPath: String? = findProperty("ide.localPath") as String?
        if (localPath != null) {
            local(localPath)
        } else {
            // Change to rider("2026.1") if you only have Rider installed
            clion("2026.1")
        }
        plugin("com.github.catppuccin.jetbrains", "3.6.0")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }
    }
}

tasks {
    runIde {
        jvmArgs("-Dide.ui.theme=Catppuccin Latte")
    }
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }
    withType<ProcessResources> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

sourceSets {
    main {
        java { srcDirs("src/main/java", "src/main/gen") }
        resources { srcDirs("src/main/resources") }
    }
}
