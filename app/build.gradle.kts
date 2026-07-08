import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.flowfuel.app"
    compileSdk = 35
    base.archivesName.set("flowfuel-app")

    defaultConfig {
        applicationId = "com.flowfuel.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())
    val sentryDsn = localProps.getProperty("sentry.dsn", "")

    val releaseKeystorePath = localProps.getProperty("release.keystore.path", "")

    signingConfigs {
        if (releaseKeystorePath.isNotBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = localProps.getProperty("release.keystore.storePassword", "")
                keyAlias = localProps.getProperty("release.keystore.alias", "")
                keyPassword = localProps.getProperty("release.keystore.keyPassword", "")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            val apiUrl = localProps.getProperty("dev.api.url", "http://10.0.2.2:8090/api/v1/")
            buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
            buildConfigField("String", "SENTRY_DSN", "\"\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystorePath.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "API_BASE_URL", "\"https://flowfuel-api.fly.dev/api/v1/\"")
            buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.biometric)
    implementation(libs.play.services.location)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.sentry.android)
    implementation(libs.car.app)
    implementation(libs.car.app.projected)
    testImplementation(libs.car.app.testing)
    testImplementation(libs.androidx.test.core)
    debugImplementation(libs.chucker)
    releaseImplementation(libs.chucker.no.op)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
