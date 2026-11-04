import com.vanniktech.maven.publish.DeploymentValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
}

group = "dev.seungmin.nav3"
// The release workflow sets nav3.version from the pushed tag, so this default applies only to local builds.
version = providers.gradleProperty("nav3.version").getOrElse("0.1.0-SNAPSHOT")

mavenPublishing {
    // The upload task returns once Maven Central has published the deployment, so the release workflow creates
    // the GitHub release only for versions that consumers can resolve.
    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.PUBLISHED)
    // Without a key, publishToMavenLocal works unsigned; the Central Portal rejects unsigned uploads.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name = "nav3-uikit"
        description = "Native UIKit navigation for Compose Multiplatform apps built on Navigation 3"
        inceptionYear = "2026"
        url = "https://github.com/ysmsm/nav3-uikit"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "ysmsm"
                name = "Seungmin Yang"
                email = "me@seungmin.dev"
                url = "https://github.com/ysmsm"
            }
        }
        scm {
            url = "https://github.com/ysmsm/nav3-uikit"
            connection = "scm:git:https://github.com/ysmsm/nav3-uikit.git"
            developerConnection = "scm:git:ssh://git@github.com/ysmsm/nav3-uikit.git"
        }
    }
}

kotlin {
    explicitApi()

    // The dumps in nav3-uikit/api make every public API change show up in review; CI fails when they are stale.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    android {
        namespace = "dev.seungmin.nav3.uikit"
        compileSdk = 37
        minSdk = 23
    }
    jvm()

    // Every platform without UIKit delegates to NavDisplay through the nonIos source set.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonIos") {
                withJvm()
                withCompilations { it.target.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm }
            }
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.navigation3.ui)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
