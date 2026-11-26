// The model eval harness (docs/echo-ui/07-conformance.md §10). Plain JVM, drives :weave-core.
// Models are reached through files, not an API key: `prepare` writes one request per task,
// any agent or provider writes the replies, and `score` grades them.
plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

kotlin { jvmToolchain(21) }

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}

application { mainClass.set("com.echoflow.weave.eval.HarnessKt") }

tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }

dependencies {
  implementation(project(":weave-core"))
  testImplementation(libs.junit)
}
