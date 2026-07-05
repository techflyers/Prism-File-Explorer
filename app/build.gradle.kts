import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.raival.compose.file.explorer"
    compileSdk = 36
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "com.raival.compose.file.explorer"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "2.0.0"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        viewBinding = true
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { localProperties.load(it) }
            }
            val storeFilePath = localProperties.getProperty("signing.storeFilePath")
            if (!storeFilePath.isNullOrEmpty()) {
                storeFile = file(storeFilePath)
                storePassword = localProperties.getProperty("signing.storePassword")
                keyAlias = localProperties.getProperty("signing.keyAlias")
                keyPassword = localProperties.getProperty("signing.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
        apiVersion = "1.9"
    }

    baselineProfile {
        dexLayoutOptimization = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    "baselineProfile"(project(":baselineprofile"))
    implementation(libs.androidx.profileinstaller)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Local/File-based dependencies
    implementation(files("libs/APKEditor.jar"))

    // AndroidX - Core & Lifecycle
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.ui.tooling.preview.android)

    // Other Jetpack & Android Libraries
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.palette.ktx)

    // Sora Code Editor
    implementation(libs.sora.editor)
    implementation(libs.sora.editor.language.java)
    implementation(libs.sora.editor.language.textmate)

    // Image Loading - Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)
    implementation(libs.zoomable.image.coil3)
    implementation(libs.okio)

    // Third-Party UI/Compose Utilities
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.cascade.compose)
    implementation(libs.compose.swipebox)
    implementation(libs.grid)
    implementation(libs.lazycolumnscrollbar)
    implementation(libs.reorderable)
    implementation(libs.zoomable)

    // Third-Party General Utilities
    implementation(libs.apksig)
    implementation(libs.commons.net)
    implementation(libs.gson)
    implementation(libs.storage)
    implementation(libs.zip4j)

    // Markdown Rendering
    implementation(libs.markwon.core)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.latex)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.tasklist)

    // Networking (Convertio API)
    implementation(libs.okhttp)

    // ONNX Runtime (FileAI Semantic Search)
    implementation(libs.onnxruntime.android)

    // PDF Text Extraction & Decryption (kept for password handling + reflow)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Jetpack PDF viewer — native text selection + native Find-in-document
    implementation("androidx.pdf:pdf-viewer-fragment:1.0.0-alpha19")
    implementation("androidx.fragment:fragment-compose:1.8.5")

    implementation(libs.sshj)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // QR Code Generation (offline)
    implementation("com.google.zxing:core:3.5.3")

    // Shizuku (privileged file access)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}

configurations.all {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk15to18")
    exclude(group = "org.bouncycastle", module = "bcutil-jdk15to18")
}