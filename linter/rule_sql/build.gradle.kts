plugins {
    kotlin("jvm") version "1.6.10"
}

dependencies {
    implementation(project(":rule_core"))

    implementation("com.github.jsqlparser:jsqlparser:4.4")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.10")
}
