plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Tek surum kaynagi: proje kokundeki VERSION.txt (ornek: 1.0.43)
val appVersionName: String = rootProject.file("VERSION.txt").readText().trim().ifEmpty { "1.0.0" }
val appVersionCode: Int = run {
    val parts = appVersionName.split('.')
    require(parts.size == 3 && parts.all { it.toIntOrNull() != null }) {
        "VERSION.txt gecersiz: '$appVersionName' (beklenen: 1.0.43)"
    }
    parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
}

android {
    namespace = "com.makay.cleaner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.makay.cleaner"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"makaydestek\"")
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"makay-cleaner\"")
    }

    buildTypes {
        release {
            // Sideload kurulum boyutu / kararliligi icin R8 + resource shrink
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "**/kotlin-tooling-metadata.json"
            )
        }
        jniLibs {
            // Tek APK; gereksiz .so kopyalarini azalt
            useLegacyPackaging = false
        }
    }

    // ABI split kapali: tek sideload APK (tum mimariler)
    splits {
        abi {
            isEnable = false
        }
    }
}

// Teslimat yalnizca proje/APK — ara cikti app/build altinda kalir, kurulum dosyasi APK\
// Klasorde yalnizca: MakayCleaner_vX.Y.Z.apk + ayni adli .sha256 (eski surumler silinir)
tasks.register("publishReleaseApk") {
    group = "distribution"
    description = "Release APK + SHA-256'yi APK/ klasorune yazar; eski dosyalari temizler"
    doLast {
        val src = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        check(src.exists()) { "APK bulunamadi: ${src.absolutePath}" }
        val apkDir = rootProject.layout.projectDirectory.dir("APK").asFile
        apkDir.mkdirs()
        val ver = rootProject.file("VERSION.txt").readText().trim()
        val named = apkDir.resolve("MakayCleaner_v$ver.apk")
        val shaFile = apkDir.resolve("MakayCleaner_v$ver.apk.sha256")

        // Onceki surumler + latest / CURRENT / log — hepsi temizlenir
        apkDir.listFiles()?.forEach { f ->
            if (f.isFile) f.delete()
        }

        src.copyTo(named, overwrite = true)
        val ps = listOf(
            "powershell", "-NoProfile", "-Command",
            "\$h=(Get-FileHash -Algorithm SHA256 -Path '${named.absolutePath.replace("'", "''")}').Hash.ToLower(); " +
                "Set-Content -Path '${shaFile.absolutePath.replace("'", "''")}' -Value (\$h + '  MakayCleaner_v$ver.apk') -Encoding ascii; " +
                "Write-Output \$h"
        )
        val proc = ProcessBuilder(ps).redirectErrorStream(true).start()
        val hex = proc.inputStream.bufferedReader().readText().trim()
        check(proc.waitFor() == 0 && hex.length == 64) { "SHA-256 olusturulamadi: $hex" }
        println("Published: ${named.absolutePath}")
        println("SHA-256  : ${shaFile.absolutePath}")
        println("Version  : $ver (versionCode=$appVersionCode)")
        println("Fingerprint: $hex")
    }
}

afterEvaluate {
    tasks.named("assembleRelease").configure {
        finalizedBy("publishReleaseApk")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // extended yerine core: Settings/Refresh yeterli (~MB kazanci)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
