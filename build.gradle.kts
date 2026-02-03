import com.google.protobuf.gradle.id

plugins {
  application

  id("com.google.protobuf") version "0.9.4"
  id("com.ncorti.ktfmt.gradle") version "0.24.0"
  id("org.pkl-lang") version "0.30.2"

  kotlin("kapt") version "2.3.0"
  kotlin("jvm") version "2.3.0"
}

group = "ac.at.uibk.dps.cirrina"

version = rootProject.file("version.txt").readText().trim()

application { mainClass = "at.ac.uibk.dps.cirrina.cirrina.CirrinaKt" }

ktfmt { googleStyle() }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }

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
  implementation("com.google.dagger:dagger:2.59")
  kapt("com.google.dagger:dagger-compiler:2.59")

  implementation("com.google.guava:guava:33.0.0-jre")

  implementation("com.google.protobuf:protobuf-java:4.32.0")

  implementation("com.lmax:disruptor:4.0.0")

  implementation("io.etcd:jetcd-core:0.8.5")

  implementation("io.github.microutils:kotlin-logging:3.0.5")

  implementation("io.micrometer:micrometer-core:1.17.0-M1")
  implementation("io.micrometer:micrometer-registry-influx:1.17.0-M1")
  implementation("io.micrometer:micrometer-tracing:1.7.0-M1")
  implementation("io.micrometer:micrometer-tracing-bridge-otel:1.7.0-M1")

  implementation("io.opentelemetry:opentelemetry-exporter-zipkin:1.58.0")
  implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.39.0")

  implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")

  implementation("org.apache.commons:commons-jexl3:3.3")

  implementation("org.eclipse.zenoh:zenoh-kotlin:1.7.2")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

  implementation("org.jgrapht:jgrapht-core:1.5.2")

  implementation("org.pkl-lang:pkl-config-java:0.29.0")
  implementation("org.pkl-lang:pkl-codegen-java:0.29.0")

  implementation("org.slf4j:slf4j-jdk14:2.0.12")

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

val jvmArgs =
  listOf("-XX:+UseZGC", "-XX:+AlwaysPreTouch", "-Xms4G", "-Xmx4G", "-XX:MaxDirectMemorySize=1G")

tasks.compileKotlin { dependsOn(tasks.ktfmtFormat) }

tasks.distZip { archiveFileName.set("${project.name}.zip") }

tasks.test {
  useJUnitPlatform()
  jvmArgs(jvmArgs)

  systemProperty(
    "java.util.logging.config.file",
    "${project.projectDir}/src/test/resources/logging.properties",
  )
  testLogging { showStandardStreams = true }
}

tasks.withType<JavaExec> { jvmArgs(jvmArgs) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    freeCompilerArgs.addAll("-Xlambdas=indy", "-Xemit-jvm-type-annotations")
  }
}

tasks.withType<Jar> {
  manifest {
    attributes["Main-Class"] = "at.ac.uibk.dps.cirrina.main.Main"
    attributes["Implementation-Version"] = version
  }
}
