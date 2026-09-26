plugins {
    id("java-library")
    id("maven-publish")
}

group = "com.wimbli.WorldBorder"
version = "1.19-patriam.1"
val paperApiVersion = "26.3.build.26-alpha"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.mikeprimm.com/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.bluecolored.de/releases")
}

dependencies {
    compileOnly(group = "io.papermc.paper", name = "paper-api", version = paperApiVersion)
    compileOnly(group = "us.dynmap", name = "dynmap-api", version = "3.1") {
        isTransitive = false // Its old Bukkit dependency conflicts with Paper's provided Bukkit API.
    }
    compileOnly("de.bluecolored:bluemap-api:2.8.0")
    compileOnly("com.flowpowered:flow-math:1.0.3")
    testImplementation(group = "io.papermc.paper", name = "paper-api", version = paperApiVersion)
    testImplementation("de.bluecolored:bluemap-api:2.8.0")
    testImplementation("com.flowpowered:flow-math:1.0.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.3")
}

defaultTasks("clean", "build")

tasks {
    register("verifyPaperApi") {
        doLast {
            val paperArtifacts = configurations.getByName("compileClasspath").resolvedConfiguration
                .resolvedArtifacts.filter {
                    it.moduleVersion.id.group == "io.papermc.paper" && it.name == "paper-api" && it.type == "jar"
                }
            if (paperArtifacts.size != 1 || paperArtifacts.single().moduleVersion.id.version != paperApiVersion) {
                throw GradleException("Expected exactly one paper-api:jar:$paperApiVersion on compileClasspath; found $paperArtifacts")
            }
            println("paper-api:jar:${paperArtifacts.single().moduleVersion.id.version}")
        }
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        val placeholders = mapOf(
            "name" to project.name,
            "group" to project.group,
            "version" to project.version
        )
        filesMatching("plugin.yml") {
            expand(placeholders)
        }
    }

    jar {
        archiveFileName.set("${project.name}.jar")
        from("LICENSE") {
            into("META-INF")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "${project.group}"
            artifactId = project.name
            version = "${project.version}"
            from(components["java"])
        }
    }
}
