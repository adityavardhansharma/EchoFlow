// Optional QuickJS engine for Echo UI tier 4 `js fn` (docs/echo-ui/05-runtime.md §1, 03 §10.12).
// Plain Kotlin/JVM like :weave-core, so its tests run on the JVM with quickjs-kt's desktop
// natives; the app resolves quickjs-kt's Android variant.
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
  api(project(":weave-core"))
  implementation(libs.quickjs.kt)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.junit)
}
