plugins { `java-library` }

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    api(project(":starter"))
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
}
