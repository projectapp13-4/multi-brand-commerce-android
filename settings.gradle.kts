pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "multi-brand-commerce-android"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":app",
    ":synthetic",
    ":trial",
    ":mobile-core",
    ":foundation",
    ":storefront",
    ":account",
    ":checkout",
    ":firebase"
)

project(":synthetic").projectDir = file("apps/synthetic")
project(":trial").projectDir = file("apps/trial")
