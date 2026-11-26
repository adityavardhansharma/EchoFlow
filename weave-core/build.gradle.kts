// Echo UI language core (docs/echo-ui/05-runtime.md §1). Plain Kotlin/JVM: no Android imports,
// so the parser, checker, evaluator and prompt generator run and are tested on the JVM, and the
// model eval harness (07 §10) can drive them directly.
plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(21) }

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.junit)
}

tasks.test {
  // The harness (07 §10) is a separate task; unit tests are 07 §1–4, §7, §9.
  systemProperty("weave.updateSnapshots", System.getProperty("weave.updateSnapshots") ?: "false")
}
