plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "8.3.10"
}

group = "at.vl"
version = "1.0.0"

repositories {
    mavenCentral()

    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://repo.oraxen.com/releases")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    // InvUI
    implementation("xyz.xenondevs.invui:invui:2.3.0")

    // Scoreboard Library
    implementation("net.megavex:scoreboard-library-api:2.8.1")
    runtimeOnly("net.megavex:scoreboard-library-implementation:2.8.1")

    // Packet Events
    implementation("com.github.retrooper:packetevents-spigot:2.13.0")

    // Model Engine 4 — provided by the server as a separate plugin at runtime, so compileOnly only.
    compileOnly("com.ticxo.modelengine:ModelEngine:R4.1.0")

    // Oraxen — provided by the server as a separate plugin at runtime, so compileOnly only.
    compileOnly("io.th0rgal:oraxen:1.218.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    // Normal JAR
    jar {
        archiveClassifier.set("original")
    }

    // Shadow/fat JAR
    shadowJar {
        archiveClassifier.set("")

        // InvUI is bundled into your plugin JAR.
        // Relocate InvUI to prevent conflicts with other plugins.
        relocate(
            "xyz.xenondevs.invui",
            "at.vl.jjk.libs.invui"
        )

        // PacketEvents
        relocate(
            "com.github.retrooper",
            "at.vl.jjk.libs.packetevents"
        )

    }

    // Make build produce the shadow JAR
    build {
        dependsOn(shadowJar)
    }

    // Paper test server
    runServer {
        minecraftVersion("26.2")

        jvmArgs(
            "-Xms2G",
            "-Xmx2G"
        )
    }

    // Process plugin.yml
    processResources {
        val props = mapOf(
            "version" to version
        )

        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}