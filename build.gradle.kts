plugins {
  application
  java
  id("com.github.johnrengelman.shadow") version "7.1.2"
}

application {
  mainClassName = "ch15.Ch15Examples"
}

tasks.jar {
  manifest {
    attributes("Main-Class" to application.mainClassName)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(files("lib/jfr-analytics.jar"))
  implementation("org.apache.calcite:calcite-core:1.29.0")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
  testImplementation("org.mockito:mockito-core:4.1.0")

  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.named<Test>("test") {
  useJUnitPlatform()
}