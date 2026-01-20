import com.google.protobuf.gradle.id

plugins {
  application
  jacoco

  id("com.google.protobuf") version "0.9.4"
  id("org.pkl-lang") version "0.29.0"
  id("com.ncorti.ktfmt.gradle") version "0.24.0"

  kotlin("jvm")
}

group = "ac.at.uibk.dps.cirrina"

version = rootProject.file("version.txt").readText().trim()

application { mainClass = "at.ac.uibk.dps.cirrina.cirrina.CirrinaKt" }

ktfmt { googleStyle() }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

jacoco { toolVersion = "0.8.11" }

pkl {
  javaCodeGenerators {
    register("pklGenJava") {
      sourceModules.addAll(
        "src/main/resources/pkl/csm/csml.pkl",
        "src/main/resources/pkl/csm/bindings.pkl",
      )
      generateGetters.set(true)
      generateJavadoc.set(true)
    }
  }
}

protobuf {
  generateProtoTasks {
    all().forEach { task ->
      task.builtins {
        id("python")
        id("cpp")
      }
    }
  }
}

dependencies {
  implementation("com.ecwid.consul:consul-api:1.4.5")

  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.1")
  implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.15.1")
  implementation("com.fasterxml.jackson.module:jackson-module-jsonSchema:2.15.1")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.15.1")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.1")

  implementation("com.google.flogger:flogger:0.9")
  implementation("com.google.flogger:flogger-system-backend:0.9")

  implementation("com.google.guava:guava:33.0.0-jre")

  implementation("com.google.protobuf:protobuf-java:4.32.0")

  implementation("io.etcd:jetcd-core:0.8.5")

  implementation("io.nats:jnats:2.17.3")

  implementation(platform("io.opentelemetry:opentelemetry-bom:1.38.0"))
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("io.opentelemetry:opentelemetry-sdk")
  implementation("io.opentelemetry:opentelemetry-exporter-logging")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp")
  implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.25.0-alpha")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")

  implementation("org.apache.commons:commons-lang3:3.19.0")

  implementation("org.apache.httpcomponents.client5:httpclient5:5.3.1")

  implementation("org.glassfish.expressly:expressly:5.0.0")

  implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
  implementation("org.hibernate:hibernate-validator-cdi:8.0.1.Final")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

  implementation("org.jgrapht:jgrapht-core:1.5.2")

  implementation("org.pkl-lang:pkl-config-java:0.29.0")
  implementation("org.pkl-lang:pkl-codegen-java:0.29.0")

  implementation("org.springframework:spring-expression:7.0.3")

  testImplementation(platform("org.junit:junit-bom:5.9.1"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit-pioneer:junit-pioneer:2.2.0")

  testImplementation("org.mockito:mockito-core:5.11.0")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")

  implementation(kotlin("stdlib-jdk8"))
}

repositories {
  mavenCentral()
  gradlePluginPortal()
  maven(url = "https://repository.cloudera.com/artifactory/cloudera-repos/")
}

tasks.compileKotlin { dependsOn(tasks.ktfmtFormat) }

tasks.distZip { archiveFileName.set("${project.name}.zip") }

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required = true
    html.required = false
    csv.required = false
  }
}

tasks.withType<Jar> {
  manifest {
    attributes["Main-Class"] = "at.ac.uibk.dps.cirrina.main.Main"
    attributes["Implementation-Version"] = version
  }
}
