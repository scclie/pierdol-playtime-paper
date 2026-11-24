plugins {
    java
}

group = "dev.sccl.pierdolplaytime"
version = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT").get()

repositories {
    mavenCentral()
    maven(url = "https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.253-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("pierdol-playtime-paper")
    manifest.attributes["Implementation-Version"] = project.version
}

// Explicitly opt-in, isolated from main and the normal release/assemble artifact.
val localOfflineDev = sourceSets.create("localOfflineDev")
configurations[localOfflineDev.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())

tasks.named<ProcessResources>(localOfflineDev.processResourcesTaskName) {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.register<Jar>("localOfflineDevJar") {
    group = "build"
    description = "Build the local-only offline AFK demo (no Auth or playtime crediting)."
    archiveBaseName.set("pierdol-playtime-paper")
    archiveClassifier.set("local-offline-dev")
    from(localOfflineDev.output)
    manifest.attributes["Implementation-Version"] = project.version
}
