import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("org.sonarqube") version "3.0"
    id("maven-publish")
}

group = "de.gmuth.ipp"
version = "1.7.1-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

defaultTasks("clean", "build")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.6"
    }
}

tasks.withType<Jar> {
    archiveBaseName.set("ipp-client")
    archiveClassifier.set("")
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("ipp-client-fat")
    archiveClassifier.set("")
    manifest {
        attributes(mapOf("Main-Class" to "de.gmuth.ipp.tool.PrintFileKt"))
    }
}

tasks {
    build {
        // dependsOn(shadowJar)
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages" // Must match regex [A-Za-z0-9_\-.]+.
            url = uri("https://maven.pkg.github.com/gmuth/ipp-client-kotlin")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
//    publications {
//        register("gpr") {
//            from(components["java"])
//        }
//    }
    publications {
        create<MavenPublication>("ipp-client") {
            from(components["java"])
            pom {
                name.set("ipp client library")
                url.set("https://github.com/gmuth/ipp-client-kotlin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://raw.githubusercontent.com/gmuth/ipp-client-kotlin/master/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("gmuth")
                        name.set("Gerhard Muth")
                        email.set("gerhard.muth@gmx.de")
                    }
                }
            }
        }
    }

}

sonarqube {
    properties {
        property("sonar.projectKey", "gmuth_ipp-client-kotlin")
        property("sonar.organization", "gmuth")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}
