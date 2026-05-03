plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

group = "com.atomgo"
version = "0.1.0"

application {
    mainClass.set("com.atomgo.backend.ApplicationKt")
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.0.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.0.0")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.0.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("ch.qos.logback:logback-classic:1.5.8")
    implementation("org.postgresql:postgresql:42.7.4")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.0.0")
    testImplementation("io.ktor:ktor-client-core-jvm:3.0.0")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.0.0")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    environment("ATOMGO_USE_INMEMORY", "true")
}
