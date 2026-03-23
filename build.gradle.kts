plugins {
  application
  id("com.ncorti.ktfmt.gradle") version "0.24.0"
  id("org.pkl-lang") version "0.30.2"
  kotlin("kapt") version "2.3.0"
  kotlin("jvm") version "2.3.0"
}

group = "ac.at.uibk.dps.cirrina"

version = rootProject.file("version.txt").readText().trim()

val jdk25SecurityArgs =
  listOf("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")

val allRuntimeArgs = jdk25SecurityArgs

application {
  mainClass.set("at.ac.uibk.dps.cirrina.CirrinaKt")

  applicationDefaultJvmArgs = allRuntimeArgs
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }

ktfmt { googleStyle() }

dependencies {
  implementation("com.google.dagger:dagger:2.59")
  kapt("com.google.dagger:dagger-compiler:2.59")

  implementation("com.google.guava:guava:33.4.0-jre")
  implementation("org.apache.commons:commons-jexl3:3.3")
  implementation("org.jgrapht:jgrapht-core:1.5.2")
  implementation("org.jgrapht:jgrapht-io:1.5.2")

  implementation("org.apache.fory:fory-core:0.15.0")
  implementation("org.apache.fory:fory-kotlin:0.15.0")

  implementation("io.etcd:jetcd-core:0.8.6")
  implementation("org.eclipse.zenoh:zenoh-kotlin:1.7.2")

  implementation("io.netty:netty-common:4.1.121.Final")

  implementation("io.github.microutils:kotlin-logging:3.0.5")
  implementation("io.micrometer:micrometer-core:1.16.4")
  implementation("org.slf4j:slf4j-jdk14:2.0.12")
  implementation("io.dropwizard.metrics:metrics-core:4.2.38")

  implementation("org.pkl-lang:pkl-config-java:0.30.2")
  implementation("org.pkl-lang:pkl-codegen-java:0.30.2")

  implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
  implementation(kotlin("stdlib-jdk8"))

  testImplementation(platform("org.junit:junit-bom:5.11.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit-pioneer:junit-pioneer:2.3.0")
  testImplementation("org.mockito:mockito-core:5.14.2")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

repositories {
  mavenCentral()
  gradlePluginPortal()
  maven(url = "https://repository.cloudera.com/artifactory/cloudera-repos/")
}

sourceSets {
  main {
    kotlin {
      srcDirs("src/main/kotlin")

      srcDir("build/generated/pkl/pklGenJava/java")
    }
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    freeCompilerArgs.addAll("-Xjdk-release=25", "-Xlambdas=indy", "-Xemit-jvm-type-annotations")
  }
}

tasks.compileKotlin { dependsOn(tasks.ktfmtFormat) }

tasks.test {
  useJUnitPlatform()
  jvmArgs(allRuntimeArgs)
  systemProperty(
    "java.util.logging.config.file",
    "${project.projectDir}/src/test/resources/logging.properties",
  )
  testLogging { showStandardStreams = true }
}

tasks.withType<JavaExec> { jvmArgs(allRuntimeArgs) }

tasks.distZip { archiveFileName.set("${project.name}.zip") }

tasks.withType<Jar> {
  manifest {
    attributes["Main-Class"] = "at.ac.uibk.dps.cirrina.CirrinaKt"
    attributes["Implementation-Version"] = version
    attributes["Enable-Native-Access"] = "ALL-UNNAMED"
  }
}

pkl {
  javaCodeGenerators {
    register("pklGenJava") {
      sourceModules.addAll("src/main/resources/pkl/csm/csml.pkl")
      generateGetters.set(true)
      generateJavadoc.set(true)
    }
  }
}
