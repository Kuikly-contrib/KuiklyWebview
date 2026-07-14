plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
}

android {
    namespace = "com.tencent.kuiklybase.android"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":KuiklyWebview"))
    implementation("com.tencent.kuikly-open:core-render-android:${Version.getKuiklyVersion()}")
    implementation("androidx.webkit:webkit:1.6.1")
}

group = findProperty("GROUP_ID")?.toString() ?: "com.tencent.kuiklybase"
version = findProperty("MAVEN_VERSION")?.toString()
    ?: System.getenv("kuiklyBizVersion")
    ?: "1.0.0"

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "KuiklyWebviewAndroid"
            }
        }
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
}


