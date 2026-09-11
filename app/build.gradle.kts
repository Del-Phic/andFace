import java.io.File
import java.security.MessageDigest
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val andfaceReleaseKeystore = System.getenv("ANDFACE_RELEASE_KEYSTORE")
val andfaceReleaseStorePassword = System.getenv("ANDFACE_RELEASE_STORE_PASSWORD")
val andfaceReleaseKeyAlias = System.getenv("ANDFACE_RELEASE_KEY_ALIAS")
val andfaceReleaseKeyPassword = System.getenv("ANDFACE_RELEASE_KEY_PASSWORD")
val andfaceReleaseCertSha256 = System.getenv("ANDFACE_RELEASE_CERT_SHA256")
    ?.replace(":", "")
    ?.replace(" ", "")
    ?.uppercase()
val faceLandmarkerModelSha256 = "64184E229B263107BC2B804C6625DB1341FF2BB731874B0BCC2FE6544E0BC9FF"
val faceLandmarkerModelAsset = layout.projectDirectory.file("src/main/assets/face_landmarker.task")
val hasAndfaceReleaseSigningConfig = listOf(
    andfaceReleaseKeystore,
    andfaceReleaseStorePassword,
    andfaceReleaseKeyAlias,
    andfaceReleaseKeyPassword
).all { !it.isNullOrBlank() }
val hasAndfaceReleaseSigning = hasAndfaceReleaseSigningConfig && !andfaceReleaseCertSha256.isNullOrBlank()

fun buildConfigString(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    file.inputStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02X".format(byte) }
}

val verifyFaceLandmarkerModelAsset by tasks.registering {
    inputs.file(faceLandmarkerModelAsset)
    doLast {
        val actual = sha256Hex(faceLandmarkerModelAsset.asFile).uppercase(Locale.US)
        check(actual == faceLandmarkerModelSha256) {
            "face_landmarker.task SHA-256 mismatch. Expected $faceLandmarkerModelSha256 but was $actual. " +
                "Update the model deliberately and refresh faceLandmarkerModelSha256, then re-enroll users."
        }
    }
}

android {
    namespace = "dev.andface.galaxy"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.andface.galaxy"
        minSdk = 26
        targetSdk = 35
        versionCode = 195
        versionName = "2.01-server-s195"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasAndfaceReleaseSigningConfig) {
            create("operatorRelease") {
                storeFile = file(andfaceReleaseKeystore!!)
                storePassword = andfaceReleaseStorePassword
                keyAlias = andfaceReleaseKeyAlias
                keyPassword = andfaceReleaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            buildConfigField("boolean", "OPERATOR_RELEASE_SIGNED", "false")
            buildConfigField("boolean", "REQUIRE_LOCK_TASK_FOR_ACCESS", "false")
            buildConfigField("String", "FACE_LANDMARKER_MODEL_SHA256", buildConfigString(faceLandmarkerModelSha256))
            buildConfigField("String", "OPERATOR_RELEASE_CERT_SHA256", buildConfigString(""))
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "OPERATOR_RELEASE_SIGNED", hasAndfaceReleaseSigning.toString())
            buildConfigField("boolean", "REQUIRE_LOCK_TASK_FOR_ACCESS", "true")
            buildConfigField("String", "FACE_LANDMARKER_MODEL_SHA256", buildConfigString(faceLandmarkerModelSha256))
            buildConfigField("String", "OPERATOR_RELEASE_CERT_SHA256", buildConfigString(andfaceReleaseCertSha256.orEmpty()))
            if (hasAndfaceReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("operatorRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        disable += "NullSafeMutableLiveData"
    }
}

tasks.named("preBuild") {
    dependsOn(verifyFaceLandmarkerModelAsset)
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")

    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

    implementation("com.google.mediapipe:tasks-vision:0.10.35")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}



































