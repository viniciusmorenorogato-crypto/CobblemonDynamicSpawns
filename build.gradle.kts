import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("dev.architectury.loom") version("1.11-SNAPSHOT")
    id("architectury-plugin") version("3.4-SNAPSHOT")
    kotlin("jvm") version("2.2.20")
}

group = "com.cobblemontest"
version = "1.1.1"

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()
}

repositories {
    mavenCentral()
    maven("https://artefacts.cobblemon.com/releases/")
}

dependencies {
    minecraft("net.minecraft:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.17.2")

    modRuntimeOnly("net.fabricmc.fabric-api:fabric-api:0.116.6+1.21.1")
    modImplementation(fabricApi.module("fabric-command-api-v2", "0.116.6+1.21.1"))
    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", "0.116.6+1.21.1"))
    modImplementation(fabricApi.module("fabric-networking-api-v1", "0.116.6+1.21.1"))

    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.6+kotlin.2.2.20")
    modCompileOnly("com.cobblemon:mod:1.7.3+1.21.1") { isTransitive = false }
    modImplementation("com.cobblemon:fabric:1.7.3+1.21.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(project.properties)
        }
    }

    java {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    compileJava {
        options.release = 21
    }

    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}
