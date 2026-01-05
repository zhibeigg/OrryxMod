import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecraftforge.gradle.userdev.UserDevExtension
import org.spongepowered.asm.gradle.plugins.MixinExtension

// =============================================================================
// Build Script
// =============================================================================

buildscript {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven { url = uri("https://repo.spongepowered.org/maven/") }
    }
    dependencies {
        classpath("net.minecraftforge.gradle:ForgeGradle:5.+")
        classpath("org.spongepowered:mixingradle:0.7-SNAPSHOT")
        classpath("com.github.jengelman.gradle.plugins:shadow:6.1.0")
    }
}

// =============================================================================
// Plugins
// =============================================================================

plugins {
    kotlin("jvm") version "1.9.0"
    `maven-publish`
}

apply(plugin = "net.minecraftforge.gradle")
apply(plugin = "org.spongepowered.mixin")
apply(plugin = "com.github.johnrengelman.shadow")

// =============================================================================
// Property Accessors
// =============================================================================

val modGroup: String by project
val modVersion: String by project
val minecraftVersion: String by project
val forgeVersion: String by project
val mappingsChannel: String by project
val mappingsVersion: String by project
val kotlinVersion: String by project
val kotlinxCoroutinesVersion: String by project
val mixinVersion: String by project
val jomlVersion: String by project
val reflectionsVersion: String by project
val jetbrainsAnnotationsVersion: String by project
val baritoneVersion: String by project
val build: String by project

// =============================================================================
// Project Configuration
// =============================================================================

version = modVersion
group = modGroup
layout.buildDirectory.set(file(build))

// =============================================================================
// Java & Kotlin Compiler Settings
// =============================================================================

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
    // Disable build cache to ensure refmap is always rebuilt
    outputs.upToDateWhen { false }
}

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xlambdas=indy",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts"
        )
    }
}

// =============================================================================
// Repositories
// =============================================================================

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spongepowered.org/maven/") }
    maven { url = uri("https://impactdevelopment.github.io/maven/") }
    maven { url = uri("https://jitpack.io") }
}

// =============================================================================
// Minecraft Configuration
// =============================================================================

configure<UserDevExtension> {
    mappings(mappingsChannel, mappingsVersion)

    runs {
        create("client") {
            workingDirectory(file("run"))
            property("fml.coreMods.load", "io.github.orryxmod.OrryxCoreMod")
            property("mixin.env.disableRefMap", "true")
            property("forge.logging.markers", "SCAN,REGISTRIES,REGISTRYDUMP")
            property("forge.logging.console.level", "debug")
        }
    }
}

// =============================================================================
// Configurations
// =============================================================================

val jarLibs: Configuration by configurations.creating
val onlyJarLibs: Configuration by configurations.creating

configurations.configureEach {
    resolutionStrategy {
        force("org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209")
    }
}

// =============================================================================
// Dependencies
// =============================================================================

dependencies {
    // --- Minecraft Forge ---
    "minecraft"("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    // --- Mixin ---
    jarLibs("org.spongepowered:mixin:$mixinVersion") {
        exclude(module = "commons-io")
        exclude(module = "gson")
        exclude(module = "guava")
    }
    annotationProcessor("org.spongepowered:mixin:$mixinVersion:processor") {
        exclude(module = "gson")
    }

    // --- Kotlin ---
    jarLibs("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion") {
        exclude(module = "kotlin-stdlib-common")
        exclude(module = "annotations")
    }
    jarLibs("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion") {
        exclude(module = "kotlin-stdlib")
    }
    jarLibs("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion") {
        exclude(module = "kotlin-stdlib-jdk8")
        exclude(module = "kotlin-stdlib-common")
    }
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion")
    compileOnly("org.jetbrains:annotations:$jetbrainsAnnotationsVersion")

    // --- Libraries ---
    // Keep 0.9.12 for Future compatibility
    jarLibs("org.reflections:reflections:$reflectionsVersion")
    jarLibs("org.joml:joml:$jomlVersion")

    // --- Baritone ---
    implementation("cabaletta:baritone-deobf-unoptimized-mcp-dev:$baritoneVersion") {
        isChanging = true
    }
    onlyJarLibs("cabaletta:baritone-api:$baritoneVersion") {
        isChanging = true
    }

    // --- Final Setup ---
    implementation(jarLibs)
    compileOnly(fileTree("libs"))
}

// =============================================================================
// Mixin Configuration
// =============================================================================

configure<MixinExtension> {
    defaultObfuscationEnv = "searge"
    add(sourceSets.main.get(), "mixins.orryxmod.refmap.json")
    config("mixins.orryxmod.json")
}

// =============================================================================
// Resource Processing
// =============================================================================

tasks.processResources {
    exclude("**/rawimagefiles")

    from(sourceSets.main.get().resources.srcDirs) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        include("mcmod.info")
        expand("version" to version, "mcversion" to minecraftVersion)
    }
}

// =============================================================================
// Build Tasks
// =============================================================================

tasks.withType<Jar>().configureEach {
    destinationDirectory.set(layout.buildDirectory)
}

tasks.register<Jar>("buildApiSource") {
    group = "build"
    description = "Assemble API library source archive"
    archiveClassifier.set("api-source")
    from(sourceSets.main.get().allSource)
}

tasks.register<Jar>("buildApi") {
    group = "build"
    description = "Assemble API library archive"
    archiveClassifier.set("api")
    from(sourceSets.main.get().output)
}

tasks.register("buildAll") {
    group = "build"
    description = "Assemble all jars"
    dependsOn("buildApi", "buildApiSource", "build")
}

// =============================================================================
// Shadow JAR Configuration
// =============================================================================

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    mustRunAfter("jar")

    // Manifest
    manifest {
        attributes(
            "Manifest-Version" to 1.0,
            "MixinConfigs" to "mixins.orryxmod.json",
            "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
            "FMLCorePluginContainsFMLMod" to "true",
            "FMLCorePlugin" to "io.github.orryxmod.OrryxCoreMod",
            "ForceLoadAsMod" to "true"
        )
    }

    // Include configurations
    configurations = listOf(
        project.configurations.getByName("jarLibs"),
        project.configurations.getByName("onlyJarLibs")
    )

    // Relocate Kotlin to avoid conflicts
    relocate("kotlin", "io.github.orryxmod.shadow.kotlin")
    relocate("kotlinx", "io.github.orryxmod.shadow.kotlinx")

    // Exclude unnecessary files
    exclude(
        "**/module-info.class",
        "DebugProbesKt.bin",
        "META-INF/proguard/**",
        "META-INF/versions/**",
        "META-INF/**.RSA",
        "META-INF/com.android.tools/**",
        "META-INF/*.kotlin_module",
        "META-INF/*.version",
        "kotlin/**/*.kotlin_metadata",
        "kotlin/**/*.kotlin_builtins"
    )

    // Minimize but keep required dependencies
    minimize {
        exclude(dependency("cabaletta:baritone-api:.*"))
        exclude(dependency("org.spongepowered:mixin:.*"))
    }
}

// =============================================================================
// Reobfuscation
// =============================================================================

// ForgeGradle reobf configuration for shadowJar
project.extensions.configure<NamedDomainObjectContainer<net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace>>("reobf") {
    maybeCreate("shadowJar").run {
        dependsOn(tasks.named("shadowJar"))
    }
}
