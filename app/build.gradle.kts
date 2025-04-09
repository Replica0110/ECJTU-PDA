import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone.getDefault
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")

}
fun getGitOutput(command: String): String? {
    return try {
        val parts = command.split("\\s".toRegex())
        val process = ProcessBuilder(*parts.toTypedArray())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            println("Warning: Git command timed out: $command")
            process.destroy()
            return null
        }

        if (process.exitValue() != 0) {
            val errorOutput = process.errorStream.bufferedReader().readText().trim()
            println("Warning: Git command failed with exit code ${process.exitValue()}: $command")
            if (errorOutput.isNotEmpty()) {
                println("Error output: $errorOutput")
            }
            return null
        }
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        println("Warning: Failed to execute git command '$command': ${e.message}")
        null
    }
}


fun getVersionCodeFromGit(): Int {
    val count = getGitOutput("git rev-list --count HEAD")
    return count?.toIntOrNull() ?: 1
}

fun getVersionNameFromGit(): String {
    var tagName = getGitOutput("git describe --tags --abbrev=0")

    if (tagName.isNullOrBlank()) {
        tagName = getGitOutput("git describe --tags")
    }

    return tagName?.ifBlank { null } ?: "0.1.0-SNAPSHOT"
}
android {
    namespace = "com.lonx.ecjtu.pda"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lonx.ecjtu.pda"
        minSdk = 26
        targetSdk = 35
        versionCode = getVersionCodeFromGit()
        versionName = getVersionNameFromGit()
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply {
            timeZone = getDefault()
        }.format(Date())

        // 设置输出文件名
        applicationVariants.all {
            val variant = this
            variant.outputs
                .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
                .forEach { output ->
                    val outputFileName = "ECJTU-PDA-${variant.versionName}.apk"
                    println("OutputFileName: $outputFileName")
                    output.outputFileName = outputFileName
                }
        }

        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs["debug"]
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            val detailedVersion = getGitOutput("git describe --tags --dirty") ?: getVersionNameFromGit()
            versionNameSuffix = ".${getVersionCodeFromGit()}-${detailedVersion.replaceFirst(getVersionNameFromGit(), "").trimStart('-')}.debug"
            applicationIdSuffix = ".debug"
        }
    }
    signingConfigs {
        val keystore = rootProject.file("signing.properties")
        if (keystore.exists()) {
            create("release") {
                val prop = Properties().apply {
                    keystore.inputStream().use(this::load)
                }

                storeFile = rootProject.file("release.keystore")
                storePassword = prop.getProperty("keystore.password")!!
                keyAlias = prop.getProperty("key.alias")!!
                keyPassword = prop.getProperty("key.password")!!
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.haze)
    implementation(libs.miuix)
    implementation(libs.koin.androidx.compose)
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.persistentcookiejar)
    implementation(libs.slimber)
    implementation(libs.koin.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.runtime.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.library)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.preference.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}