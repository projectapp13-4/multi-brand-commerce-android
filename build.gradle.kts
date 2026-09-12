plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.apollo) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("projectFiles") {
        target("*.md", "*.yml", "*.yaml", "*.toml", ".gitignore", ".gitattributes", ".editorconfig")
        targetExclude("**/build/**", "docs/preparation/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
