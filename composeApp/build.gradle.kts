
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.composeHotReload)
}
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

android {
    namespace = "com.xenonware.cloudremote"
    compileSdk {
        version = release(36)
    }
    defaultConfig {
        applicationId = "com.xenonware.cloudremote"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "XENON_COMMONS_VERSION", "\"${libs.versions.xenonCommons.get()}\"")
        buildConfigField("String", "XENON_UI_VERSION", "\"${libs.versions.xenonUi.get()}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-d"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "XENON_COMMONS_VERSION", "\"${libs.versions.xenonCommons.get()}\"")
            buildConfigField("String", "XENON_UI_VERSION", "\"${libs.versions.xenonUi.get()}\"")
        }

        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "XENON_COMMONS_VERSION", "\"${libs.versions.xenonCommons.get()}\"")
            buildConfigField("String", "XENON_UI_VERSION", "\"${libs.versions.xenonUi.get()}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.xenon.commons)
    implementation(libs.androidx.material3.window.size.class1.android)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.firebase.firestore)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.haze)
    implementation(libs.androidx.animation.graphics)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.haze.materials)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.ui)
    implementation(libs.androidx.appcompat)
    implementation(libs.mathparser.org.mxparser)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    // Updated alias: was libs.androidx.lifecycle.viewmodel.compose (clashed with KMP alias)
    implementation(libs.androidx.lifecycle.viewmodel.compose.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.gson)
}

compose.desktop {
    application {
        mainClass = "com.xenonware.cloudremote.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.xenonware.cloudremote"
            packageVersion = "1.0.0"
        }
    }
}