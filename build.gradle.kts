// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

val copyDebugApk =
  tasks.register<Copy>("copyDebugApk") {
    from(project(":app").layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.projectDirectory.dir("build-outputs"))
  }

subprojects {
  tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(copyDebugApk) }
}

