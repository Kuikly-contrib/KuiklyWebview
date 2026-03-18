plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("maven-publish")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        publishLibraryVariants("release")
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    ohosArm64 {
        binaries.sharedLib {
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuikly-open:core-annotations:${Version.getKuiklyOhosVersion()}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }
}

group = "com.tencent.kuiklybase"
version = System.getenv("kuiklyBizVersion") ?: "1.0.0"

publishing {
    repositories {
        maven {
            val repoUrl = findProperty("MAVEN_REPO_URL")?.toString()
                ?: System.getenv("mavenUrl") ?: ""
            url = if (repoUrl.isNotEmpty()) {
                uri(repoUrl)
            } else {
                uri(layout.buildDirectory.dir("repo"))
            }
            val username = findProperty("MAVEN_USERNAME")?.toString()
                ?: System.getenv("mavenUserName") ?: ""
            val password = findProperty("MAVEN_PASSWORD")?.toString()
                ?: System.getenv("mavenPassword") ?: ""
            if (username.isNotEmpty()) {
                credentials {
                    this.username = username
                    this.password = password
                }
            }
        }
    }
}

android {
    namespace = "com.tencent.kuiklybase"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
}
