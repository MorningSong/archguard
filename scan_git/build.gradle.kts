group = "com.thougthworks.archguard.scanners"

repositories {
    mavenCentral()
}

plugins {
    id("antlr")
    id("com.thougthworks.archguard.java-conventions")
    kotlin("jvm") version "1.6.10"
}

dependencies {
    antlr("org.antlr:antlr4:4.9.3")

    implementation("com.github.ajalt:clikt:2.5.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.3.1.201904271842-r")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.30")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.30")
    implementation("org.slf4j:slf4j-jdk14:1.7.13")
    implementation("org.antlr:antlr4-runtime:4.9.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

sourceSets.main {
    java.srcDirs("${project.buildDir}/generated-src")
}

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf("-package", "dev.evolution") + listOf("-visitor", "-long-messages")
    outputDirectory  = file("${project.buildDir}/generated-src/dev/evolution")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
